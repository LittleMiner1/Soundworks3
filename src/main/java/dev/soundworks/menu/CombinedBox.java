package dev.soundworks.menu;

import dev.soundworks.block.RecordBoxBlockEntity;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** All record boxes that touch each other (6 directions) as one big storage, ordered by (y, x, z). Server side only. */
public final class CombinedBox {
	public static final int MAX_BOXES = 20000;

	private final List<RecordBoxBlockEntity> boxes;

	private CombinedBox(List<RecordBoxBlockEntity> boxes) {
		this.boxes = boxes;
	}

	public static CombinedBox of(Level level, BlockPos start) {
		List<RecordBoxBlockEntity> found = new ArrayList<>();
		Set<Long> seen = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		queue.add(start);
		seen.add(start.asLong());
		while (!queue.isEmpty() && found.size() < MAX_BOXES) {
			BlockPos p = queue.poll();
			if (!level.hasChunkAt(p) || !(level.getBlockEntity(p) instanceof RecordBoxBlockEntity box)) continue;
			found.add(box);
			for (Direction d : Direction.values()) {
				BlockPos n = p.relative(d);
				if (seen.add(n.asLong())) queue.add(n);
			}
		}
		found.sort(Comparator.<RecordBoxBlockEntity>comparingInt(b -> b.getBlockPos().getY())
			.thenComparingInt(b -> b.getBlockPos().getX())
			.thenComparingInt(b -> b.getBlockPos().getZ()));
		return new CombinedBox(found);
	}

	public int size() {
		return boxes.size() * RecordBoxBlockEntity.SLOTS;
	}

	private boolean valid(int i) {
		return i >= 0 && i < size();
	}

	public ItemStack get(int i) {
		if (!valid(i)) return ItemStack.EMPTY;
		return boxes.get(i / RecordBoxBlockEntity.SLOTS).getItem(i % RecordBoxBlockEntity.SLOTS);
	}

	public void set(int i, ItemStack stack) {
		if (!valid(i)) return;
		boxes.get(i / RecordBoxBlockEntity.SLOTS).setItem(i % RecordBoxBlockEntity.SLOTS, stack);
	}

	/** Removes and returns the stack in slot i (no change notification for the caller to forget: it is done here). */
	public ItemStack take(int i) {
		if (!valid(i)) return ItemStack.EMPTY;
		ItemStack s = get(i);
		set(i, ItemStack.EMPTY);
		return s;
	}

	public ItemStack split(int i, int amount) {
		if (!valid(i)) return ItemStack.EMPTY;
		ItemStack r = get(i).split(amount);
		boxes.get(i / RecordBoxBlockEntity.SLOTS).setChanged();
		return r;
	}
}
