package dev.soundworks.block;

import dev.soundworks.PlaybackState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** A device that can broadcast its playback to speakers on the same frequency (turntable, jukebox, radio). */
public interface Sender {
	PlaybackState state();

	String getSpeakerIp();

	void setSpeakerIp(String ip);

	BlockPos getBlockPos();

	Level getLevel();
}
