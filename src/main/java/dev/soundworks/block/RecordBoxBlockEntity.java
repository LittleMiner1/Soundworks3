package dev.soundworks.block;

import dev.soundworks.ModRegistry;
import dev.soundworks.menu.CombinedBox;
import dev.soundworks.menu.RecordBoxMenu;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** 27 record slots. All adjacent record boxes form one big storage (see CombinedBox). */
public class RecordBoxBlockEntity extends RandomizableContainerBlockEntity implements ExtendedMenuProvider<BlockPos> {
	public static final int SLOTS = 27;
	private NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);

	public RecordBoxBlockEntity(BlockPos pos, BlockState state) {
		super(ModRegistry.RECORD_BOX_BE, pos, state);
	}

	@Override protected NonNullList<ItemStack> getItems() { return items; }
	@Override protected void setItems(NonNullList<ItemStack> list) { this.items = list; }
	@Override protected Component getDefaultName() { return Component.translatable("block.soundworks.record_box"); }
	@Override public int getContainerSize() { return SLOTS; }
	@Override public BlockPos getScreenOpeningData(ServerPlayer player) { return worldPosition; }
	@Override public boolean canPlaceItem(int slot, ItemStack stack) { return stack.is(ModRegistry.DISC); }

	@Override
	protected AbstractContainerMenu createMenu(int id, Inventory inv) {
		return new RecordBoxMenu(id, inv, CombinedBox.of(level, worldPosition), worldPosition);
	}

	@Override
	protected void loadAdditional(ValueInput in) {
		super.loadAdditional(in);
		items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
		if (!tryLoadLootTable(in)) ContainerHelper.loadAllItems(in, items);
	}

	@Override
	protected void saveAdditional(ValueOutput out) {
		super.saveAdditional(out);
		if (!trySaveLootTable(out)) ContainerHelper.saveAllItems(out, items);
	}
}
