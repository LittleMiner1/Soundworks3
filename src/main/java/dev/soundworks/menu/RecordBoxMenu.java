package dev.soundworks.menu;

import dev.soundworks.DiscData;
import dev.soundworks.ModRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 54-slot window over the combined storage of all touching record boxes. Scroll position and search text are
 * set by the client (Net.BoxView). Synced state: data[0..1] = number of visible entries, data[2..3] = offset
 * (each as high/low 15 bits, because menu data slots are shorts).
 */
public class RecordBoxMenu extends BaseMenu {
	public static final int WINDOW_X = 8, WINDOW_Y = 34, PLAYER_Y = 158;

	private final CombinedBox box;       // null on the client
	private final WindowContainer window;
	private final ContainerData data = new SimpleContainerData(4);
	private int offset = 0;
	private String artistQuery = "", titleQuery = "";

	/** Client constructor. */
	public RecordBoxMenu(int id, Inventory inv, BlockPos pos) {
		this(id, inv, null, pos);
	}

	/** Server constructor. */
	public RecordBoxMenu(int id, Inventory inv, CombinedBox box, BlockPos pos) {
		this(id, inv, box, new WindowContainer(box), pos);
	}

	private RecordBoxMenu(int id, Inventory inv, CombinedBox box, WindowContainer window, BlockPos pos) {
		super(ModRegistry.RECORD_BOX_MENU, id, window, pos);
		this.box = box;
		this.window = window;
		for (int r = 0; r < 6; r++) {
			for (int c = 0; c < 9; c++) {
				final int index = c + r * 9;
				addSlot(new Slot(window, index, WINDOW_X + c * 18, WINDOW_Y + r * 18) {
					@Override public boolean mayPlace(ItemStack s) { return s.is(ModRegistry.DISC) && window.mapped(index); }
					@Override public boolean mayPickup(Player p) { return window.mapped(index); }
				});
			}
		}
		addPlayerSlots(inv, 8, PLAYER_Y);
		addDataSlots(data);
		if (box != null) setView(0, "", "");
	}

	/** Visible entries (matches of the search, or all slots) and the current scroll offset. */
	public int total() { return (data.get(0) << 15) | data.get(1); }
	public int offset() { return (data.get(2) << 15) | data.get(3); }

	/** Server: recompute which storage slots the window shows (artist and title filters are both optional, both must match). */
	public void setView(int newOffset, String newArtist, String newTitle) {
		if (box == null) return;
		artistQuery = newArtist == null ? "" : newArtist.strip().toLowerCase(Locale.ROOT);
		titleQuery = newTitle == null ? "" : newTitle.strip().toLowerCase(Locale.ROOT);
		boolean filtered = !artistQuery.isEmpty() || !titleQuery.isEmpty();
		List<Integer> visible = new ArrayList<>();
		int n = box.size();
		for (int i = 0; i < n; i++) {
			if (!filtered) {
				visible.add(i);
				continue;
			}
			ItemStack s = box.get(i);
			if (s.isEmpty()) continue;
			DiscData d = s.get(ModRegistry.DISC_DATA);
			String artist = d == null || d.artist() == null ? "" : d.artist().toLowerCase(Locale.ROOT);
			String title = d != null ? d.title().toLowerCase(Locale.ROOT) : s.getHoverName().getString().toLowerCase(Locale.ROOT);
			if (artist.contains(artistQuery) && title.contains(titleQuery)) visible.add(i);
		}
		int maxOffset = visible.size() <= WindowContainer.SIZE ? 0 : ((visible.size() - 1) / 9 - 5) * 9;
		offset = Math.max(0, Math.min(newOffset - Math.floorMod(newOffset, 9), maxOffset));
		int[] map = new int[WindowContainer.SIZE];
		for (int i = 0; i < map.length; i++) map[i] = offset + i < visible.size() ? visible.get(offset + i) : -1;
		window.setMap(map);
		data.set(0, (visible.size() >> 15) & 0x7FFF);
		data.set(1, visible.size() & 0x7FFF);
		data.set(2, (offset >> 15) & 0x7FFF);
		data.set(3, offset & 0x7FFF);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = slots.get(index);
		if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;
		ItemStack stack = slot.getItem();
		ItemStack copy = stack.copy();
		if (index < WindowContainer.SIZE) {
			if (!moveItemStackTo(stack, WindowContainer.SIZE, slots.size(), true)) return ItemStack.EMPTY;
		} else {
			// inventory -> first free slot of the whole storage (not only the visible window)
			if (box == null || !stack.is(ModRegistry.DISC)) return ItemStack.EMPTY;
			boolean placed = false;
			for (int i = 0; i < box.size() && !placed; i++) {
				if (box.get(i).isEmpty()) {
					box.set(i, stack.copyWithCount(1));
					stack.shrink(1);
					placed = true;
				}
			}
			if (!placed) return ItemStack.EMPTY;
			setView(offset, artistQuery, titleQuery);
		}
		if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
		else slot.setChanged();
		return copy;
	}
}
