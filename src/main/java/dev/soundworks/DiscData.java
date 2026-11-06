package dev.soundworks;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Data of a pressed record: MP3 hash (SHA-256), title, artist (from the MP3's ID3 tag), length in seconds. */
public record DiscData(String hash, String title, String artist, int lengthSec) {
	public static final Codec<DiscData> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.STRING.fieldOf("hash").forGetter(DiscData::hash),
		Codec.STRING.fieldOf("title").forGetter(DiscData::title),
		Codec.STRING.optionalFieldOf("artist", "").forGetter(DiscData::artist),
		Codec.INT.fieldOf("length").forGetter(DiscData::lengthSec)
	).apply(i, DiscData::new));

	public static final StreamCodec<ByteBuf, DiscData> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.STRING_UTF8, DiscData::hash,
		ByteBufCodecs.STRING_UTF8, DiscData::title,
		ByteBufCodecs.STRING_UTF8, DiscData::artist,
		ByteBufCodecs.VAR_INT, DiscData::lengthSec,
		DiscData::new
	);

	/** "Artist - Title" (or just the title if the artist is unknown). */
	public String display() {
		return artist == null || artist.isBlank() ? title : artist + " - " + title;
	}
}
