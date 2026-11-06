package dev.soundworks.block;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

final class BlockUtil {
	private BlockUtil() {}

	/** Box in 1/16 units for a block whose front faces north; rotated to the given facing. */
	static VoxelShape rotated(Direction facing, double x0, double y0, double z0, double x1, double y1, double z1) {
		double ax = x0, az = z0, bx = x1, bz = z1;
		switch (facing) {
			case EAST -> { ax = 16 - z1; bx = 16 - z0; az = x0; bz = x1; }
			case SOUTH -> { ax = 16 - x1; bx = 16 - x0; az = 16 - z1; bz = 16 - z0; }
			case WEST -> { ax = z0; bx = z1; az = 16 - x1; bz = 16 - x0; }
			default -> { }
		}
		return Shapes.box(ax / 16.0, y0 / 16.0, az / 16.0, bx / 16.0, y1 / 16.0, bz / 16.0);
	}
}
