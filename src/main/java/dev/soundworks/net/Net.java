package dev.soundworks.net;

import dev.soundworks.SoundworksMod;
import dev.soundworks.audio.AudioStore;
import dev.soundworks.block.Configurable;
import dev.soundworks.block.Controllable;
import dev.soundworks.block.PressBlockEntity;
import dev.soundworks.menu.RecordBoxMenu;
import io.netty.buffer.ByteBuf;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerPlayer;

public final class Net {
	private Net() {}

	/** C2S packets are limited to 32 KiB, so chunks are small. */
	public static final int UPLOAD_CHUNK = 30_000;
	/** S2C packets may be up to 1 MiB. */
	public static final int DOWNLOAD_CHUNK = 500_000;

	public record UploadChunk(String hash, int index, int total, byte[] data) implements CustomPacketPayload {
		public static final Type<UploadChunk> TYPE = new Type<>(SoundworksMod.id("upload"));
		public static final StreamCodec<ByteBuf, UploadChunk> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, UploadChunk::hash,
			ByteBufCodecs.VAR_INT, UploadChunk::index,
			ByteBufCodecs.VAR_INT, UploadChunk::total,
			ByteBufCodecs.BYTE_ARRAY, UploadChunk::data,
			UploadChunk::new);
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	public record PressRequest(BlockPos pos, String hash, String title, boolean copy) implements CustomPacketPayload {
		public static final Type<PressRequest> TYPE = new Type<>(SoundworksMod.id("press"));
		public static final StreamCodec<ByteBuf, PressRequest> CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, PressRequest::pos,
			ByteBufCodecs.STRING_UTF8, PressRequest::hash,
			ByteBufCodecs.STRING_UTF8, PressRequest::title,
			ByteBufCodecs.BOOL, PressRequest::copy,
			PressRequest::new);
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	public record DeviceAction(BlockPos pos, int action, float value) implements CustomPacketPayload {
		public static final Type<DeviceAction> TYPE = new Type<>(SoundworksMod.id("action"));
		public static final StreamCodec<ByteBuf, DeviceAction> CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, DeviceAction::pos,
			ByteBufCodecs.VAR_INT, DeviceAction::action,
			ByteBufCodecs.FLOAT, DeviceAction::value,
			DeviceAction::new);
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	public record AudioRequest(String hash) implements CustomPacketPayload {
		public static final Type<AudioRequest> TYPE = new Type<>(SoundworksMod.id("audio_request"));
		public static final StreamCodec<ByteBuf, AudioRequest> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, AudioRequest::hash, AudioRequest::new);
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	public record AudioChunk(String hash, int index, int total, byte[] data) implements CustomPacketPayload {
		public static final Type<AudioChunk> TYPE = new Type<>(SoundworksMod.id("audio_chunk"));
		public static final StreamCodec<ByteBuf, AudioChunk> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, AudioChunk::hash,
			ByteBufCodecs.VAR_INT, AudioChunk::index,
			ByteBufCodecs.VAR_INT, AudioChunk::total,
			ByteBufCodecs.BYTE_ARRAY, AudioChunk::data,
			AudioChunk::new);
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	/** Server -> clients: state of a vanilla jukebox that plays a Soundworks record. */
	public record JukeboxSync(BlockPos pos, String hash, int lengthMs, long startTick, int session, boolean playing) implements CustomPacketPayload {
		public static final Type<JukeboxSync> TYPE = new Type<>(SoundworksMod.id("jukebox_sync"));
		public static final StreamCodec<ByteBuf, JukeboxSync> CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, JukeboxSync::pos,
			ByteBufCodecs.STRING_UTF8, JukeboxSync::hash,
			ByteBufCodecs.VAR_INT, JukeboxSync::lengthMs,
			ByteBufCodecs.VAR_LONG, JukeboxSync::startTick,
			ByteBufCodecs.VAR_INT, JukeboxSync::session,
			ByteBufCodecs.BOOL, JukeboxSync::playing,
			JukeboxSync::new);
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}


	/** Sentinel for ConfigSet: remove the password of a server. */
	public static final String CLEAR_PW = "\u0001CLEAR";

	/** Status codes of a network query (see lang keys gui.soundworks.net.status.N). */
	public static final int ST_OK = 0, ST_NO_NETWORK = 1, ST_WRONG_PW = 2, ST_OFFLINE = 3, ST_INVALID = 4, ST_NOT_CONNECTED = 5;

	// ---- small helpers for hand-written codecs (records with many fields) ----
	static String rs(ByteBuf b) { return ByteBufCodecs.STRING_UTF8.decode(b); }
	static void ws(ByteBuf b, String v) { ByteBufCodecs.STRING_UTF8.encode(b, v); }
	static int ri(ByteBuf b) { return ByteBufCodecs.VAR_INT.decode(b); }
	static void wi(ByteBuf b, int v) { ByteBufCodecs.VAR_INT.encode(b, v); }
	static long rl(ByteBuf b) { return ByteBufCodecs.VAR_LONG.decode(b); }
	static void wl(ByteBuf b, long v) { ByteBufCodecs.VAR_LONG.encode(b, v); }
	static float rf(ByteBuf b) { return ByteBufCodecs.FLOAT.decode(b); }
	static void wf(ByteBuf b, float v) { ByteBufCodecs.FLOAT.encode(b, v); }
	static boolean rz(ByteBuf b) { return ByteBufCodecs.BOOL.decode(b); }
	static void wz(ByteBuf b, boolean v) { ByteBufCodecs.BOOL.encode(b, v); }

	/** Client -> server: generic setting of a block (field name + up to three strings). */
	public record ConfigSet(BlockPos pos, String field, String s1, String s2, String s3) implements CustomPacketPayload {
		public static final Type<ConfigSet> TYPE = new Type<>(SoundworksMod.id("config_set"));
		public static final StreamCodec<ByteBuf, ConfigSet> CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, ConfigSet::pos,
			ByteBufCodecs.STRING_UTF8, ConfigSet::field,
			ByteBufCodecs.STRING_UTF8, ConfigSet::s1,
			ByteBufCodecs.STRING_UTF8, ConfigSet::s2,
			ByteBufCodecs.STRING_UTF8, ConfigSet::s3,
			ConfigSet::new);
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	/** Client -> server: scroll position and artist/title filter of the open record box menu. */
	public record BoxView(int offset, String artist, String title) implements CustomPacketPayload {
		public static final Type<BoxView> TYPE = new Type<>(SoundworksMod.id("box_view"));
		public static final StreamCodec<ByteBuf, BoxView> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, BoxView::offset,
			ByteBufCodecs.STRING_UTF8, BoxView::artist,
			ByteBufCodecs.STRING_UTF8, BoxView::title,
			BoxView::new);
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	/** Client -> server: list the songs of a network (terminal or radio at pos). */
	public record NetQuery(BlockPos pos, boolean useSaved, String ip, String password) implements CustomPacketPayload {
		public static final Type<NetQuery> TYPE = new Type<>(SoundworksMod.id("net_query"));
		public static final StreamCodec<ByteBuf, NetQuery> CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, NetQuery::pos,
			ByteBufCodecs.BOOL, NetQuery::useSaved,
			ByteBufCodecs.STRING_UTF8, NetQuery::ip,
			ByteBufCodecs.STRING_UTF8, NetQuery::password,
			NetQuery::new);
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	/** Server -> client: answer to a NetQuery. The password is never sent, only whether one is stored. */
	public record NetListing(BlockPos pos, int status, String ip, boolean hasPw, List<Entry> entries) implements CustomPacketPayload {
		public record Entry(String server, String hash, String title, String artist, int lengthSec) {}

		public static final Type<NetListing> TYPE = new Type<>(SoundworksMod.id("net_listing"));
		public static final StreamCodec<ByteBuf, NetListing> CODEC = new StreamCodec<>() {
			@Override public NetListing decode(ByteBuf b) {
				BlockPos pos = BlockPos.STREAM_CODEC.decode(b);
				int status = ri(b);
				String ip = rs(b);
				boolean hasPw = rz(b);
				int n = Math.min(ri(b), 5000);
				List<Entry> list = new ArrayList<>(n);
				for (int i = 0; i < n; i++) list.add(new Entry(rs(b), rs(b), rs(b), rs(b), ri(b)));
				return new NetListing(pos, status, ip, hasPw, list);
			}

			@Override public void encode(ByteBuf b, NetListing v) {
				BlockPos.STREAM_CODEC.encode(b, v.pos());
				wi(b, v.status());
				ws(b, v.ip());
				wz(b, v.hasPw());
				wi(b, v.entries().size());
				for (Entry e : v.entries()) {
					ws(b, e.server());
					ws(b, e.hash());
					ws(b, e.title());
					ws(b, e.artist());
					wi(b, e.lengthSec());
				}
			}
		};
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	/** Client -> server: the radio at pos should play this song from its network. */
	public record RadioPlay(BlockPos pos, String hash) implements CustomPacketPayload {
		public static final Type<RadioPlay> TYPE = new Type<>(SoundworksMod.id("radio_play"));
		public static final StreamCodec<ByteBuf, RadioPlay> CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, RadioPlay::pos,
			ByteBufCodecs.STRING_UTF8, RadioPlay::hash,
			RadioPlay::new);
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	/** Server -> all players: state of the sender that uses this frequency (speakers play it). */
	public record SpeakerSync(String freq, String hash, String title, int lengthMs, long startTick, long basePosMs,
			float speed, float volume, int session, boolean playing) implements CustomPacketPayload {
		public static final Type<SpeakerSync> TYPE = new Type<>(SoundworksMod.id("speaker_sync"));
		public static final StreamCodec<ByteBuf, SpeakerSync> CODEC = new StreamCodec<>() {
			@Override public SpeakerSync decode(ByteBuf b) {
				return new SpeakerSync(rs(b), rs(b), rs(b), ri(b), rl(b), rl(b), rf(b), rf(b), ri(b), rz(b));
			}

			@Override public void encode(ByteBuf b, SpeakerSync v) {
				ws(b, v.freq());
				ws(b, v.hash());
				ws(b, v.title());
				wi(b, v.lengthMs());
				wl(b, v.startTick());
				wl(b, v.basePosMs());
				wf(b, v.speed());
				wf(b, v.volume());
				wi(b, v.session());
				wz(b, v.playing());
			}
		};
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	/** Shows a short message above the hotbar (replaces the removed Player#displayClientMessage). */
	public static void overlay(ServerPlayer player, Component message) {
		player.connection.send(new ClientboundSetActionBarTextPacket(message));
	}

	/** True if the player is within 8 blocks of the block (squared distance <= 64). */
	public static boolean inReach(ServerPlayer player, BlockPos pos) {
		return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
	}

	public static void initCommon() {
		PayloadTypeRegistry.serverboundPlay().register(UploadChunk.TYPE, UploadChunk.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(PressRequest.TYPE, PressRequest.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(DeviceAction.TYPE, DeviceAction.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(AudioRequest.TYPE, AudioRequest.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ConfigSet.TYPE, ConfigSet.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(BoxView.TYPE, BoxView.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(NetQuery.TYPE, NetQuery.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(RadioPlay.TYPE, RadioPlay.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(NetListing.TYPE, NetListing.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SpeakerSync.TYPE, SpeakerSync.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(AudioChunk.TYPE, AudioChunk.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(JukeboxSync.TYPE, JukeboxSync.CODEC);
	}

	private static String cut(String s) {
		return s == null ? "" : s.length() > 64 ? s.substring(0, 64) : s;
	}

	private static final Map<String, ByteArrayOutputStream> UPLOADS = new ConcurrentHashMap<>();

	public static void initServer() {
		// MP3 upload from a client (in chunks)
		ServerPlayNetworking.registerGlobalReceiver(UploadChunk.TYPE, (p, ctx) -> {
			ServerPlayer player = ctx.player();
			var server = ctx.server();
			if (!AudioStore.isValidHash(p.hash()) || p.total() <= 0
				|| (long) p.total() * UPLOAD_CHUNK > AudioStore.MAX_BYTES + UPLOAD_CHUNK) return;
			String key = player.getUUID() + ":" + p.hash();
			if (AudioStore.has(server, p.hash())) { UPLOADS.remove(key); return; }
			if (p.index() == 0) UPLOADS.put(key, new ByteArrayOutputStream());
			ByteArrayOutputStream buf = UPLOADS.get(key);
			if (buf == null) return;
			if (p.data().length > UPLOAD_CHUNK) { UPLOADS.remove(key); return; }
			buf.writeBytes(p.data());
			if (buf.size() > AudioStore.MAX_BYTES) {
				UPLOADS.remove(key);
				overlay(player, Component.translatable("gui.soundworks.msg.too_big"));
				return;
			}
			if (p.index() == p.total() - 1) {
				UPLOADS.remove(key);
				if (!AudioStore.store(server, p.hash(), buf.toByteArray())) {
					overlay(player, Component.translatable("gui.soundworks.msg.bad_mp3"));
				}
			}
		});

		// Operate the press (press / copy)
		ServerPlayNetworking.registerGlobalReceiver(PressRequest.TYPE, (p, ctx) -> {
			ServerPlayer player = ctx.player();
			if (!inReach(player, p.pos())) return;
			if (player.level().getBlockEntity(p.pos()) instanceof PressBlockEntity be) {
				if (p.copy()) be.copy(player);
				else be.press(player, p.hash(), p.title());
			}
		});

		// Operate turntable / jukebox
		ServerPlayNetworking.registerGlobalReceiver(DeviceAction.TYPE, (p, ctx) -> {
			ServerPlayer player = ctx.player();
			if (!inReach(player, p.pos())) return;
			if (player.level().getBlockEntity(p.pos()) instanceof Controllable c) {
				c.handleAction(player, p.action(), p.value());
			}
		});


		// Generic settings (server block, antenna, speaker, sender frequency)
		ServerPlayNetworking.registerGlobalReceiver(ConfigSet.TYPE, (p, ctx) -> {
			ServerPlayer player = ctx.player();
			if (!inReach(player, p.pos())) return;
			if (p.field().length() > 32 || p.s1().length() > 64 || p.s2().length() > 64 || p.s3().length() > 64) return;
			if (player.level().getBlockEntity(p.pos()) instanceof Configurable c) {
				c.configure(player, p.field(), p.s1(), p.s2(), p.s3());
			}
		});

		// Record box: scrolling / searching
		ServerPlayNetworking.registerGlobalReceiver(BoxView.TYPE, (p, ctx) -> {
			if (ctx.player().containerMenu instanceof RecordBoxMenu m) {
				m.setView(p.offset(), cut(p.artist()), cut(p.title()));
			}
		});

		// Terminal / radio: song list of a network
		ServerPlayNetworking.registerGlobalReceiver(NetQuery.TYPE, (p, ctx) -> {
			if (p.ip().length() > 64 || p.password().length() > 64) return;
			Network.handleQuery(ctx.player(), p);
		});

		// Radio: start a song
		ServerPlayNetworking.registerGlobalReceiver(RadioPlay.TYPE, (p, ctx) -> Network.handleRadioPlay(ctx.player(), p));

		// Client is missing an MP3 -> send it in chunks
		ServerPlayNetworking.registerGlobalReceiver(AudioRequest.TYPE, (p, ctx) -> {
			ServerPlayer player = ctx.player();
			if (!AudioStore.has(ctx.server(), p.hash())) return;
			try {
				byte[] data = AudioStore.read(ctx.server(), p.hash());
				int total = Math.max(1, (data.length + DOWNLOAD_CHUNK - 1) / DOWNLOAD_CHUNK);
				for (int i = 0; i < total; i++) {
					int from = i * DOWNLOAD_CHUNK;
					int to = Math.min(data.length, from + DOWNLOAD_CHUNK);
					ServerPlayNetworking.send(player, new AudioChunk(p.hash(), i, total, java.util.Arrays.copyOfRange(data, from, to)));
				}
			} catch (IOException e) {
				SoundworksMod.LOGGER.warn("Could not read {}", p.hash(), e);
			}
		});
	}
}
