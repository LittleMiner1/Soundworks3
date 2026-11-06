package dev.soundworks.menu;

import java.util.Arrays;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

/**
 * A 54-slot window onto a (much bigger) CombinedBox. On the server every window slot is mapped to a slot of
 * the storage (or -1 = nothing). On the client (backing == null) it is a plain local container whose items
 * are filled by the normal menu synchronisation.
 */
public final class WindowContainer extends SimpleContainer {
	public static final int SIZE = 54;
	private final CombinedBox backing;
	private final int[] map = new int[SIZE];

	public WindowContainer(CombinedBox backing) {
		super(SIZE);
		this.backing = backing;
		Arrays.fill(map, -1);
	}

	public void setMap(int[] newMap) {
		for (int i = 0; i < SIZE; i++) map[i] = i < newMap.length ? newMap[i] : -1;
	}

	/** Client: always true. Server: only slots that show something of the storage. */
	public boolean mapped(int i) {
		return backing == null || (i >= 0 && i < SIZE && map[i] >= 0);
	}

	@Override
	public ItemStack getItem(int i) {
		if (backing == null) return super.getItem(i);
		return i < 0 || i >= SIZE || map[i] < 0 ? ItemStack.EMPTY : backing.get(map[i]);
	}

	@Override
	public void setItem(int i, ItemStack stack) {
		if (backing == null) {
			super.setItem(i, stack);
			return;
		}
		if (i >= 0 && i < SIZE && map[i] >= 0) backing.set(map[i], stack);
	}

	@Override
	public ItemStack removeItem(int i, int amount) {
		if (backing == null) return super.removeItem(i, amount);
		return i < 0 || i >= SIZE || map[i] < 0 ? ItemStack.EMPTY : backing.split(map[i], amount);
	}

	@Override
	public ItemStack removeItemNoUpdate(int i) {
		if (backing == null) return super.removeItemNoUpdate(i);
		return i < 0 || i >= SIZE || map[i] < 0 ? ItemStack.EMPTY : backing.take(map[i]);
	}

	@Override
	public boolean isEmpty() {
		if (backing == null) return super.isEmpty();
		for (int i = 0; i < SIZE; i++) if (!getItem(i).isEmpty()) return false;
		return true;
	}
}
