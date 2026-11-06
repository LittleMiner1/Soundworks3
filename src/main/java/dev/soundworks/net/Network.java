package dev.soundworks.net;

import dev.soundworks.DiscData;
import dev.soundworks.IpUtil;
import dev.soundworks.ModRegistry;
import dev.soundworks.PlaybackState;
import dev.soundworks.block.AntennaBlockEntity;
import dev.soundworks.block.NetNodeBlockEntity;
import dev.soundworks.block.RadioBlockEntity;
import dev.soundworks.block.Sender;
import dev.soundworks.block.ServerBlock;
import dev.soundworks.block.ServerBlockEntity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Server-side bookkeeping of the sound network: servers (storage), antennas (online check), senders (frequencies).
 * The sets only contain loaded block entities (filled on load, emptied on unload / server stop).
 * Everything runs on the server thread.
 */
public final class Network {
	private Network() {}

	private static <T> Set<T> identitySet() {
		return Collections.newSetFromMap(new IdentityHashMap<>());
	}

	private static final Set<ServerBlockEntity> SERVERS = identitySet();
	private static final Set<AntennaBlockEntity> ANTENNAS = identitySet();
	private static final Set<Sender> SENDERS = identitySet();

	/** Result of an access check: status (Net.ST_*) and, if OK, the online servers of the network. */
	public record Access(int status, List<ServerBlockEntity> servers) {}

	/** A song found on a server. */
	public record Hit(ServerBlockEntity server, DiscData data) {}

	public static void init() {
		ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.register((be, level) -> {
			if (be instanceof ServerBlockEntity srv) SERVERS.add(srv);
			if (be instanceof AntennaBlockEntity ant) ANTENNAS.add(ant);
			if (be instanceof Sender snd) SENDERS.add(snd);
		});
		ServerBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register((be, level) -> {
			if (be instanceof ServerBlockEntity srv) SERVERS.remove(srv);
			if (be instanceof AntennaBlockEntity ant) ANTENNAS.remove(ant);
			if (be instanceof Sender snd) {
				SENDERS.remove(snd);
				if (!snd.getSpeakerIp().isEmpty()) broadcast(level.getServer(), snd.getSpeakerIp(), new PlaybackState());
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			SERVERS.clear();
			ANTENNAS.clear();
			SENDERS.clear();
		});
		ServerTickEvents.END_SERVER_TICK.register(Network::tick);
	}

	// ------------------------------------------------------------------ lookups

	public static List<ServerBlockEntity> servers() {
		return new ArrayList<>(SERVERS);
	}

	public static List<ServerBlockEntity> serversOf(String ip) {
		List<ServerBlockEntity> list = new ArrayList<>();
		for (ServerBlockEntity s : SERVERS) if (!s.isRemoved() && s.ip.equals(ip)) list.add(s);
		list.sort(Comparator.comparing((ServerBlockEntity s) -> s.name.toLowerCase()).thenComparingLong(s -> s.getBlockPos().asLong()));
		return list;
	}

	public static boolean hasAntenna(String ip) {
		if (ip.isEmpty()) return false;
		for (AntennaBlockEntity a : ANTENNAS) if (!a.isRemoved() && a.ip.equals(ip)) return true;
		return false;
	}

	/** Checks IP and password; on success returns the servers of the network that are online. */
	public static Access access(String rawIp, String password) {
		String ip = IpUtil.normalize(rawIp);
		if (ip == null || ip.isEmpty()) return new Access(Net.ST_INVALID, List.of());
		List<ServerBlockEntity> all = serversOf(ip);
		if (all.isEmpty()) return new Access(Net.ST_NO_NETWORK, List.of());
		String pw = password == null ? "" : password;
		List<ServerBlockEntity> allowed = new ArrayList<>();
		for (ServerBlockEntity s : all) if (s.password.isEmpty() || s.password.equals(pw)) allowed.add(s);
		if (allowed.isEmpty()) return new Access(Net.ST_WRONG_PW, List.of());
		List<ServerBlockEntity> online = new ArrayList<>();
		for (ServerBlockEntity s : allowed) if (s.isOnline()) online.add(s);
		if (online.isEmpty()) return new Access(Net.ST_OFFLINE, List.of());
		return new Access(Net.ST_OK, online);
	}

	/** Finds a server of the (accessible, online) network that stores the song. */
	public static Hit findSong(String ip, String password, String hash) {
		Access a = access(ip, password);
		if (a.status() != Net.ST_OK) return null;
		for (ServerBlockEntity s : a.servers()) {
			for (int i = 0; i < ServerBlockEntity.SLOTS; i++) {
				DiscData d = s.getItem(i).get(ModRegistry.DISC_DATA);
				if (d != null && d.hash().equals(hash)) return new Hit(s, d);
			}
		}
		return null;
	}

	// ------------------------------------------------------------------ speakers

	/** Sends the state of a frequency to all players (speakers and senders use it on the client). */
	public static void broadcast(MinecraftServer server, String freq, PlaybackState ps) {
		if (server == null || freq.isEmpty()) return;
		Net.SpeakerSync p = new Net.SpeakerSync(freq, ps.hash, ps.title, ps.lengthMs, ps.startTick, ps.basePosMs,
			ps.speed, ps.volume, ps.session, ps.playing);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) ServerPlayNetworking.send(player, p);
	}

	public static void pushSpeaker(Sender s) {
		if (s.getSpeakerIp().isEmpty() || !(s.getLevel() instanceof ServerLevel level)) return;
		broadcast(level.getServer(), s.getSpeakerIp(), s.state());
	}

	/**
	 * Changes the frequency a sender transmits on. A frequency is used by at most one sender (any dimension);
	 * several speakers may listen to it. Returns true if the sender was changed.
	 */
	public static boolean setSpeakerIp(Sender s, ServerPlayer player, String raw) {
		String ip = IpUtil.normalize(raw);
		if (ip == null) {
			Net.overlay(player, Component.translatable("gui.soundworks.msg.bad_ip"));
			return false;
		}
		if (!ip.isEmpty()) {
			for (Sender other : SENDERS) {
				if (other != s && ip.equals(other.getSpeakerIp())) {
					Net.overlay(player, Component.translatable("gui.soundworks.msg.freq_taken"));
					return false;
				}
			}
		}
		String old = s.getSpeakerIp();
		s.setSpeakerIp(ip);
		if (!old.isEmpty() && !old.equals(ip) && s.getLevel() instanceof ServerLevel level) {
			broadcast(level.getServer(), old, new PlaybackState());
		}
		Net.overlay(player, Component.translatable("gui.soundworks.msg.saved"));
		return true;
	}

	// ------------------------------------------------------------------ ticking

	private static void tick(MinecraftServer server) {
		int t = server.getTickCount();
		if (t % 10 == 0) {
			boolean blink = (t / 10) % 2 == 0;
			for (ServerBlockEntity s : new ArrayList<>(SERVERS)) {
				Level level = s.getLevel();
				if (level == null || s.isRemoved()) continue;
				BlockState st = s.getBlockState();
				if (!st.hasProperty(ServerBlock.LAMP)) continue;
				int lamp = !s.isOnline() ? 0 : s.isStreaming(level.getGameTime()) ? (blink ? 2 : 3) : 1;
				if (st.getValue(ServerBlock.LAMP) != lamp) {
					level.setBlock(s.getBlockPos(), st.setValue(ServerBlock.LAMP, lamp), Block.UPDATE_ALL);
				}
			}
		}
		if (t % 100 == 0) {
			for (Sender s : new ArrayList<>(SENDERS)) pushSpeaker(s);
		}
	}

	// ------------------------------------------------------------------ terminal / radio requests

	public static void handleQuery(ServerPlayer player, Net.NetQuery q) {
		if (!Net.inReach(player, q.pos())) return;
		if (!(player.level().getBlockEntity(q.pos()) instanceof NetNodeBlockEntity node)) return;
		String ip;
		String pw;
		if (q.useSaved()) {
			ip = node.ip;
			pw = node.password;
			if (ip.isEmpty()) {
				reply(player, q, Net.ST_NOT_CONNECTED, "", false, List.of());
				return;
			}
		} else {
			ip = IpUtil.normalize(q.ip());
			pw = IpUtil.password(q.password());
			// empty password field + same IP as before = keep using the stored password
			if (pw.isEmpty() && ip != null && ip.equals(node.ip)) pw = node.password;
		}
		Access a = access(ip, pw);
		if (a.status() != Net.ST_OK) {
			reply(player, q, a.status(), ip == null ? "" : ip, !node.password.isEmpty(), List.of());
			return;
		}
		if (!q.useSaved()) node.setConnection(ip, pw);
		List<Net.NetListing.Entry> entries = new ArrayList<>();
		outer:
		for (ServerBlockEntity srv : a.servers()) {
			for (int i = 0; i < ServerBlockEntity.SLOTS; i++) {
				ItemStack stack = srv.getItem(i);
				DiscData d = stack.get(ModRegistry.DISC_DATA);
				if (d == null) continue;
				entries.add(new Net.NetListing.Entry(srv.name, d.hash(), d.title(), d.artist(), d.lengthSec()));
				if (entries.size() >= 3000) break outer;
			}
		}
		reply(player, q, Net.ST_OK, ip, !pw.isEmpty(), entries);
	}

	private static void reply(ServerPlayer player, Net.NetQuery q, int status, String ip, boolean hasPw, List<Net.NetListing.Entry> entries) {
		ServerPlayNetworking.send(player, new Net.NetListing(q.pos(), status, ip, hasPw, entries));
	}

	public static void handleRadioPlay(ServerPlayer player, Net.RadioPlay r) {
		if (!Net.inReach(player, r.pos())) return;
		if (!(player.level().getBlockEntity(r.pos()) instanceof RadioBlockEntity radio)) return;
		Access a = access(radio.ip, radio.password);
		if (a.status() != Net.ST_OK) {
			Net.overlay(player, Component.translatable("gui.soundworks.net.status." + a.status()));
			return;
		}
		Hit hit = findSong(radio.ip, radio.password, r.hash());
		if (hit == null) {
			Net.overlay(player, Component.translatable("gui.soundworks.msg.song_missing"));
			return;
		}
		radio.play(hit.data());
	}
}
