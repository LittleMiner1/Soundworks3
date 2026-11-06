package dev.soundworks.menu;

import dev.soundworks.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class ServerMenu extends BaseMenu {
	public static final int SLOT_Y = 28, PLAYER_Y = 132;

	public ServerMenu(int id, Inventory inv, BlockPos pos) {
		this(id, inv, new SimpleContainer(9), pos);
	}

	public ServerMenu(int id, Inventory inv, Container c, BlockPos pos) {
		super(ModRegistry.SERVER_MENU, id, c, pos);
		for (int col = 0; col < 9; col++) {
			addSlot(new Slot(c, col, 8 + col * 18, SLOT_Y) {
				@Override public boolean mayPlace(ItemStack s) { return s.is(ModRegistry.DISC); }
			});
		}
		addPlayerSlots(inv, 8, PLAYER_Y);
	}
}
