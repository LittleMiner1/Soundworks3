package dev.soundworks.audio;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.regex.Pattern;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/** Server-side storage: &lt;world&gt;/soundworks/&lt;sha256&gt;.mp3 (+ .len with the length, + .artist with the ID3 artist). */
public final class AudioStore {
	public static final int MAX_BYTES = 25 * 1024 * 1024;
	private static final Pattern HASH = Pattern.compile("[0-9a-f]{64}");

	private AudioStore() {}

	public static boolean isValidHash(String h) {
		return h != null && HASH.matcher(h).matches();
	}

	private static Path dir(MinecraftServer server) throws IOException {
		Path p = server.getWorldPath(LevelResource.ROOT).resolve("soundworks");
		Files.createDirectories(p);
		return p;
	}

	public static boolean has(MinecraftServer server, String hash) {
		try {
			return isValidHash(hash) && Files.exists(dir(server).resolve(hash + ".mp3"));
		} catch (IOException e) {
			return false;
		}
	}

	public static int lengthSec(MinecraftServer server, String hash) {
		try {
			return Integer.parseInt(Files.readString(dir(server).resolve(hash + ".len")).trim());
		} catch (Exception e) {
			return 0;
		}
	}

	/** Artist of a stored MP3 (read from the ID3 tag; cached next to the file). Empty if unknown. */
	public static String artist(MinecraftServer server, String hash) {
		try {
			Path f = dir(server).resolve(hash + ".artist");
			if (Files.exists(f)) return Files.readString(f, StandardCharsets.UTF_8).strip();
			Path mp3 = dir(server).resolve(hash + ".mp3");
			if (!Files.exists(mp3)) return "";
			String a = Mp3Info.artist(Files.readAllBytes(mp3));
			Files.writeString(f, a, StandardCharsets.UTF_8);
			return a;
		} catch (IOException | RuntimeException e) {
			return "";
		}
	}

	public static byte[] read(MinecraftServer server, String hash) throws IOException {
		return Files.readAllBytes(dir(server).resolve(hash + ".mp3"));
	}

	/** Verifies hash and MP3 validity, then stores the file. Returns true on success. */
	public static boolean store(MinecraftServer server, String hash, byte[] data) {
		try {
			if (!isValidHash(hash) || data.length == 0 || data.length > MAX_BYTES) return false;
			if (!sha256(data).equals(hash)) return false;
			int len = Mp3Info.durationSec(data);
			if (len <= 0) return false;
			Path d = dir(server);
			Files.write(d.resolve(hash + ".mp3"), data);
			Files.writeString(d.resolve(hash + ".len"), Integer.toString(len), StandardCharsets.UTF_8);
			Files.writeString(d.resolve(hash + ".artist"), Mp3Info.artist(data), StandardCharsets.UTF_8);
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	public static String sha256(byte[] data) {
		try {
			byte[] h = MessageDigest.getInstance("SHA-256").digest(data);
			StringBuilder sb = new StringBuilder();
			for (byte b : h) sb.append(String.format("%02x", b));
			return sb.toString();
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}
}
