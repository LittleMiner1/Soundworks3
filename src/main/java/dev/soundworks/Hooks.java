package dev.soundworks;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;

/** Bridge between common code and client code (set by the client entrypoint). */
public final class Hooks {
	private Hooks() {}

	public interface Audio {
		/** Called every client tick for a sender. freq = its speaker frequency ("" = none). */
		void tick(long key, BlockPos pos, PlaybackState state, long gameTime, String freq);
		void stop(long key);
		/** Called every client tick by each loaded speaker with a frequency. */
		void speakerTick(String freq, BlockPos pos);
	}

	public static Audio audio = new Audio() {
		@Override public void tick(long key, BlockPos pos, PlaybackState state, long gameTime, String freq) {}
		@Override public void stop(long key) {}
		@Override public void speakerTick(String freq, BlockPos pos) {}
	};
	public static Consumer<BlockPos> openTurntable = p -> {};
	/** Antenna / speaker settings screen. */
	public static Consumer<BlockPos> openConfig = p -> {};
	/** Terminal (false) or radio (true) screen. */
	public static BiConsumer<BlockPos, Boolean> openNet = (p, radio) -> {};
}
