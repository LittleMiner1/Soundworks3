package dev.soundworks.audio;

import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Header;

public final class Mp3Info {
	private Mp3Info() {}

	/** Length in seconds (rounded up) or -1 if the data is not a valid MP3. */
	public static int durationSec(byte[] data) {
		double ms = 0;
		int frames = 0;
		Bitstream bs = new Bitstream(new ByteArrayInputStream(data));
		try {
			Header h;
			while ((h = bs.readFrame()) != null && frames < 400_000) {
				ms += h.ms_per_frame();
				frames++;
				bs.closeFrame();
			}
		} catch (Exception ignored) {
			// ignore broken trailing frames
		}
		return frames < 10 ? -1 : (int) Math.ceil(ms / 1000.0);
	}

	/** Artist from the ID3v2 tag (or ID3v1 as fallback). Empty string if the file has none. */
	public static String artist(byte[] d) {
		try {
			String a = id3v2Artist(d);
			if (a == null || a.isBlank()) a = id3v1Artist(d);
			return clean(a);
		} catch (RuntimeException e) {
			return "";
		}
	}

	private static String clean(String s) {
		if (s == null) return "";
		s = s.replace('\u0000', ' ').replaceAll("\\s+", " ").strip();
		return s.length() > 48 ? s.substring(0, 48).strip() : s;
	}

	private static int syncsafe(byte[] d, int p) {
		return ((d[p] & 0x7F) << 21) | ((d[p + 1] & 0x7F) << 14) | ((d[p + 2] & 0x7F) << 7) | (d[p + 3] & 0x7F);
	}

	private static int be32(byte[] d, int p) {
		return ((d[p] & 0xFF) << 24) | ((d[p + 1] & 0xFF) << 16) | ((d[p + 2] & 0xFF) << 8) | (d[p + 3] & 0xFF);
	}

	private static String id3v2Artist(byte[] d) {
		if (d.length < 10 || d[0] != 'I' || d[1] != 'D' || d[2] != '3') return null;
		int ver = d[3] & 0xFF;
		if (ver < 2 || ver > 4) return null;
		int flags = d[5] & 0xFF;
		int end = Math.min(d.length, 10 + syncsafe(d, 6));
		int p = 10;
		if ((flags & 0x40) != 0 && ver >= 3 && p + 4 <= end) {
			p += ver == 4 ? syncsafe(d, p) : be32(d, p) + 4;
		}
		int idLen = ver == 2 ? 3 : 4;
		int hdr = ver == 2 ? 6 : 10;
		String lead = null, band = null;
		while (p + hdr <= end && d[p] != 0) {
			String id = new String(d, p, idLen, StandardCharsets.ISO_8859_1);
			int size;
			if (ver == 2) size = ((d[p + 3] & 0xFF) << 16) | ((d[p + 4] & 0xFF) << 8) | (d[p + 5] & 0xFF);
			else if (ver == 4) size = syncsafe(d, p + 4);
			else size = be32(d, p + 4);
			int start = p + hdr;
			if (size < 0 || start + size > end) break;
			if (size > 1) {
				if (id.equals("TPE1") || id.equals("TP1")) lead = text(d, start, size);
				else if (id.equals("TPE2") || id.equals("TP2")) band = text(d, start, size);
			}
			p = start + size;
		}
		return lead != null && !lead.isBlank() ? lead : band;
	}

	private static String text(byte[] d, int off, int len) {
		int enc = d[off] & 0xFF;
		Charset cs = switch (enc) {
			case 0 -> StandardCharsets.ISO_8859_1;
			case 1 -> StandardCharsets.UTF_16;
			case 2 -> StandardCharsets.UTF_16BE;
			default -> StandardCharsets.UTF_8;
		};
		String t = new String(d, off + 1, len - 1, cs);
		int nul = t.indexOf('\u0000');
		return nul >= 0 ? t.substring(0, nul) : t;
	}

	private static String id3v1Artist(byte[] d) {
		int n = d.length;
		if (n < 128 || d[n - 128] != 'T' || d[n - 127] != 'A' || d[n - 126] != 'G') return null;
		return new String(d, n - 128 + 33, 30, StandardCharsets.ISO_8859_1);
	}
}
