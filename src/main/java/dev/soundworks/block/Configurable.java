package dev.soundworks.block;

import net.minecraft.server.level.ServerPlayer;

/** A block entity that accepts settings from a client GUI (see Net.ConfigSet). */
public interface Configurable {
	void configure(ServerPlayer player, String field, String s1, String s2, String s3);
}
