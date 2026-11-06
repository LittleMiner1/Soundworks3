package dev.soundworks.client.audio;

import dev.soundworks.Hooks;
import dev.soundworks.PlaybackState;
import dev.soundworks.net.Net;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;

/** Manages running playbacks (one per device) and the local MP3 cache. */
public final class ClientAudio implements Hooks.Audio {
	public static final ClientAudio INSTANCE = new ClientAudio();
	private final Map<Long, Mp3Player> players = new HashMap<>();
	private final Set<String> requested = new HashSet<>();
	private final Map<String, ByteArrayOutputStream> downloads = new HashMap<>();
	private final Map<Long, BlockPos> vanillaPos = new HashMap<>();
	private final Map<Long, PlaybackState> vanillaStates = new HashMap<>();
	private Object lastLevel;

	public static Path cacheDir() {
		Path p = FabricLoader.getInstance().getGameDir().resolve("soundworks_cache");
		try { Files.createDirectories(p); } catch (IOException ignored) {}
		return p;
	}

	public static Path musicDir() {
		Path p = FabricLoader.getInstance().getGameDir().resolve("music");
		try { Files.createDirectories(p); } catch (IOException ignored) {}
		return p;
	}

	// ---- Speakers: ONE player per frequency, so senders and speakers can never echo each other ----
	private static final double SENDER_FULL = 8.0, SENDER_FADE = 56.0;
	private static final double SPEAKER_FULL = 24.0, SPEAKER_FADE = 72.0;

	/** The loudest source (sender or speaker) of a frequency for this client tick. */
	private record Src(BlockPos pos, double full, double fade, double score) {}

	private final Map<String, PlaybackState> speakerStates = new HashMap<>(); // from the server (SpeakerSync)
	private final Map<String, PlaybackState> senderStates = new HashMap<>();  // loaded senders, refilled every tick
	private final Map<String, Src> best = new HashMap<>();
	private Set<String> active = new HashSet<>();

	private static long speakerKey(String freq) {
		return 0x7E00000000000000L ^ freq.hashCode();
	}

	private void offer(String freq, BlockPos pos, double full, double fade) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		double dist = Math.sqrt(mc.player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
		double score = Math.max(0.0, 1.0 - Math.max(0.0, dist - full) / fade) - dist * 1e-6;
		Src cur = best.get(freq);
		if (cur == null || score > cur.score()) best.put(freq, new Src(pos, full, fade, score));
	}

	/**
	 * A sender (turntable, jukebox, radio). Without a frequency it plays by itself. With a frequency it does not
	 * play on its own: it is just one more place the shared player of that frequency can come from.
	 */
	@Override
	public void tick(long key, BlockPos pos, PlaybackState st, long gameTime, String freq) {
		if (freq == null || freq.isEmpty()) {
			play(key, pos, st, gameTime, SENDER_FULL, SENDER_FADE, false);
			return;
		}
		stop(key);
		if (st.playing) senderStates.put(freq, st);
		offer(freq, pos, SENDER_FULL, SENDER_FADE);
	}

	/** Called every client tick by each loaded speaker that has a frequency. */
	@Override
	public void speakerTick(String freq, BlockPos pos) {
		offer(freq, pos, SPEAKER_FULL, SPEAKER_FADE);
	}

	/** Server -> client: what a frequency is playing right now. */
	public void onSpeakerSync(Net.SpeakerSync s) {
		if (!s.playing() || s.hash().isEmpty()) {
			speakerStates.remove(s.freq());
			return;
		}
		PlaybackState st = speakerStates.computeIfAbsent(s.freq(), k -> new PlaybackState());
		st.hash = s.hash();
		st.title = s.title();
		st.lengthMs = s.lengthMs();
		st.startTick = s.startTick();
		st.basePosMs = s.basePosMs();
		st.speed = s.speed();
		st.volume = s.volume();
		st.session = s.session();
		st.playing = true;
	}

	/** Once per client tick, after all senders and speakers have reported: plays each frequency exactly once. */
	public void flushSpeakers(long gameTime) {
		Set<String> freqs = new HashSet<>(speakerStates.keySet());
		freqs.addAll(senderStates.keySet());
		for (String f : freqs) {
			Src src = best.get(f);
			PlaybackState st = senderStates.containsKey(f) ? senderStates.get(f) : speakerStates.get(f);
			long key = speakerKey(f);
			if (src == null || st == null) stop(key);
			else play(key, src.pos(), st, gameTime, src.full(), src.fade(), false);
		}
		for (String f : active) {
			if (!freqs.contains(f)) stop(speakerKey(f));
		}
		active = freqs;
		senderStates.clear();
		best.clear();
	}

	private void play(long key, BlockPos pos, PlaybackState st, long gameTime, double full, double fade, boolean muted) {
		Mp3Player cur = players.get(key);
		if (!st.playing || st.hash.isEmpty()) {
			if (cur != null) stop(key);
			return;
		}
		if (cur != null && (cur.session != st.session || !cur.hash.equals(st.hash))) {
			stop(key);
			cur = null;
		}
		if (cur == null) {
			Path f = cacheDir().resolve(st.hash + ".mp3");
			if (!Files.exists(f)) {
				if (requested.add(st.hash) && ClientPlayNetworking.canSend(Net.AudioRequest.TYPE)) {
					ClientPlayNetworking.send(new Net.AudioRequest(st.hash));
				}
				return;
			}
			// Join the song at the position everyone else is currently at.
			long start = Math.max(0, st.posMs(gameTime));
			if (start >= st.lengthMs) return;
			cur = new Mp3Player(f, st.hash, st.session, start);
			players.put(key, cur);
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		double dx = pos.getX() + 0.5 - mc.player.getX();
		double dz = pos.getZ() + 0.5 - mc.player.getZ();
		double dist = Math.sqrt(mc.player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
		double atten = Math.max(0.0, 1.0 - Math.max(0.0, dist - full) / fade);
		double vol = mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).get()
			* mc.options.getSoundSourceOptionInstance(SoundSource.RECORDS).get();
		float base = muted ? 0f : (float) (st.volume * atten * vol);

		// Stereo panning: -1 = fully left, +1 = fully right. Close sources stay centered.
		double yaw = Math.toRadians(mc.player.getYRot());
		double rightX = -Math.cos(yaw), rightZ = -Math.sin(yaw);
		double len = Math.sqrt(dx * dx + dz * dz);
		double pan = len < 0.001 ? 0 : (dx * rightX + dz * rightZ) / len;
		pan *= Math.min(1.0, len / 4.0);
		cur.gainL = base * (float) (pan > 0 ? 1.0 - 0.9 * pan : 1.0);
		cur.gainR = base * (float) (pan < 0 ? 1.0 + 0.9 * pan : 1.0);
		cur.speed = st.speed;
	}

	@Override
	public void stop(long key) {
		Mp3Player p = players.remove(key);
		if (p != null) p.stop();
	}

	public void stopAll() {
		players.values().forEach(Mp3Player::stop);
		players.clear();
		requested.clear();
		downloads.clear();
		vanillaPos.clear();
		vanillaStates.clear();
		speakerStates.clear();
		senderStates.clear();
		best.clear();
		active = new HashSet<>();
	}

	/** Incoming download chunk from the server. */
	public void onChunk(Net.AudioChunk c) {
		ByteArrayOutputStream buf = downloads.computeIfAbsent(c.hash(), h -> new ByteArrayOutputStream());
		buf.writeBytes(c.data());
		if (c.index() == c.total() - 1) {
			downloads.remove(c.hash());
			try {
				Files.write(cacheDir().resolve(c.hash() + ".mp3"), buf.toByteArray());
			} catch (IOException e) {
				requested.remove(c.hash());
			}
		}
	}

	// ---- Vanilla jukeboxes playing Soundworks records (state comes from the server) ----

	public void onVanillaSync(Net.JukeboxSync s) {
		long key = s.pos().asLong() ^ 0x5A5A5A5A5A5AL;
		if (!s.playing()) {
			vanillaStates.remove(key);
			vanillaPos.remove(key);
			stop(key);
			return;
		}
		PlaybackState st = vanillaStates.computeIfAbsent(key, k -> new PlaybackState());
		st.hash = s.hash();
		st.lengthMs = s.lengthMs();
		st.playing = true;
		st.session = s.session();
		st.startTick = s.startTick();
		st.basePosMs = 0;
		vanillaPos.put(key, s.pos());
	}

	public void tickVanilla(long gameTime) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level != lastLevel) { // dimension or world changed
			for (Long key : vanillaStates.keySet()) stop(key);
			vanillaStates.clear();
			vanillaPos.clear();
			lastLevel = mc.level;
		}
		for (var e : vanillaStates.entrySet()) {
			play(e.getKey(), vanillaPos.get(e.getKey()), e.getValue(), gameTime, SENDER_FULL, SENDER_FADE, false);
		}
	}
}
