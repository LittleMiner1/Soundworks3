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

	// ---- Speakers: one player per frequency at the nearest loaded speaker ----
	private static final double SENDER_FULL = 8.0, SENDER_FADE = 56.0;
	private static final double SPEAKER_FULL = 24.0, SPEAKER_FADE = 72.0;
	private final Map<String, PlaybackState> speakerStates = new HashMap<>();
	private final Map<String, Double> curBest = new HashMap<>();
	private final Map<String, BlockPos> curPos = new HashMap<>();
	private final Map<String, Double> prevBest = new HashMap<>();

	private static long speakerKey(String freq) {
		return 0x7E00000000000000L ^ freq.hashCode();
	}

	/** A sender (turntable, jukebox, radio). It is muted when a speaker of its frequency is closer to the player. */
	@Override
	public void tick(long key, BlockPos pos, PlaybackState st, long gameTime, String freq) {
		boolean muted = false;
		Minecraft mc = Minecraft.getInstance();
		if (freq != null && !freq.isEmpty() && mc.player != null) {
			Double speakerDist = prevBest.get(freq);
			if (speakerDist != null) {
				muted = speakerDist < mc.player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
			}
		}
		play(key, pos, st, gameTime, SENDER_FULL, SENDER_FADE, muted);
	}

	/** Called every client tick by each loaded speaker that has a frequency. */
	@Override
	public void speakerTick(String freq, BlockPos pos) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		double d = mc.player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
		Double best = curBest.get(freq);
		if (best == null || d < best) {
			curBest.put(freq, d);
			curPos.put(freq, pos);
		}
	}

	/** Server -> client: what a frequency is playing right now. */
	public void onSpeakerSync(Net.SpeakerSync s) {
		if (!s.playing() || s.hash().isEmpty()) {
			speakerStates.remove(s.freq());
			stop(speakerKey(s.freq()));
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

	/** Once per client tick, after all speakers have reported: plays each frequency at its nearest speaker. */
	public void flushSpeakers(long gameTime) {
		for (var e : speakerStates.entrySet()) {
			BlockPos p = curPos.get(e.getKey());
			long key = speakerKey(e.getKey());
			if (p == null) stop(key);
			else play(key, p, e.getValue(), gameTime, SPEAKER_FULL, SPEAKER_FADE, false);
		}
		prevBest.clear();
		prevBest.putAll(curBest);
		curBest.clear();
		curPos.clear();
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
		curBest.clear();
		curPos.clear();
		prevBest.clear();
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
