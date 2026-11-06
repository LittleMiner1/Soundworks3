package dev.soundworks.block;

import dev.soundworks.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Display only: shows the song list of a network. Checks every 10 ticks that its support still exists. */
public class TerminalBlockEntity extends NetNodeBlockEntity {
	public TerminalBlockEntity(BlockPos pos, BlockState state) {
		super(ModRegistry.TERMINAL_BE, pos, state);
	}

	public static void tick(Level level, BlockPos pos, BlockState state, TerminalBlockEntity be) {
		if (level.isClientSide() || level.getGameTime() % 10 != 0) return;
		if (!state.canSurvive(level, pos)) level.destroyBlock(pos, true);
	}
}
