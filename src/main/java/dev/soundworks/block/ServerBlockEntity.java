package dev.soundworks.block;

import dev.soundworks.IpUtil;
import dev.soundworks.ModRegistry;
import dev.soundworks.menu.ServerMenu;
import dev.soundworks.net.Net;
import dev.soundworks.net.Network;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Stores up to 9 records. Servers with the same IP (and the same password) form one network; the network
 * is online as soon as a loaded antenna has the same IP.
 */
public class ServerBlockEntity extends RandomizableContainerBlockEntity implements ExtendedMenuProvider<BlockPos>, Configurable {
	public static final int SLOTS = 9;
	private NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
	public String ip = "";
	public String name = "Server";
	public String password = "";
	public boolean hasPw = false;
	private long lastStream = -1000;

	public ServerBlockEntity(BlockPos pos, BlockState state) {
		super(ModRegistry.SERVER_BE, pos, state);
	}

	@Override protected NonNullList<ItemStack> getItems() { return items; }
	@Override protected void setItems(NonNullList<ItemStack> list) { this.items = list; }
	@Override protected Component getDefaultName() { return Component.translatable("block.soundworks.server"); }
	@Override public int getContainerSize() { return SLOTS; }
	@Override public BlockPos getScreenOpeningData(ServerPlayer player) { return worldPosition; }
	@Override public boolean canPlaceItem(int slot, ItemStack stack) { return stack.is(ModRegistry.DISC); }

	@Override
	protected AbstractContainerMenu createMenu(int id, Inventory inv) {
		return new ServerMenu(id, inv, this, worldPosition);
	}

	@Override
	protected void loadAdditional(ValueInput in) {
		super.loadAdditional(in);
		items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
		if (!tryLoadLootTable(in)) ContainerHelper.loadAllItems(in, items);
		ip = in.getStringOr("ip", "");
		name = in.getStringOr("name", "Server");
		password = in.getStringOr("password", "");
		hasPw = in.getBooleanOr("hasPw", !password.isEmpty());
	}

	@Override
	protected void saveAdditional(ValueOutput out) {
		super.saveAdditional(out);
		if (!trySaveLootTable(out)) ContainerHelper.saveAllItems(out, items);
		out.putString("ip", ip);
		out.putString("name", name);
		if (!Secrets.hidden()) out.putString("password", password);
		out.putBoolean("hasPw", !password.isEmpty());
	}

	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return Secrets.hide(() -> ClientboundBlockEntityDataPacket.create(this));
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return Secrets.hide(() -> saveWithoutMetadata(registries));
	}

	private void sync() {
		setChanged();
		if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
	}

	/** True if a loaded antenna uses this server's IP. */
	public boolean isOnline() {
		return !ip.isEmpty() && Network.hasAntenna(ip);
	}

	/** Called by a radio that is streaming from this server. */
	public void markStreaming(long now) {
		lastStream = now;
	}

	public boolean isStreaming(long now) {
		return now - lastStream < 25;
	}

	@Override
	public void configure(ServerPlayer player, String field, String s1, String s2, String s3) {
		if (!field.equals("server")) return;
		String newIp = IpUtil.normalize(s1);
		if (newIp == null) {
			Net.overlay(player, Component.translatable("gui.soundworks.msg.bad_ip"));
			return;
		}
		String newName = IpUtil.name(s2, name.isEmpty() ? "Server" : name);
		String newPw = s3 == null || s3.isEmpty() ? password : s3.equals(Net.CLEAR_PW) ? "" : IpUtil.password(s3);
		if (!newIp.isEmpty()) {
			for (ServerBlockEntity other : Network.servers()) {
				if (other != this && newIp.equals(other.ip) && !newPw.equals(other.password)) {
					Net.overlay(player, Component.translatable("gui.soundworks.msg.wrong_password"));
					return;
				}
			}
		}
		ip = newIp;
		name = newName;
		password = newPw;
		hasPw = !newPw.isEmpty();
		sync();
		Net.overlay(player, Component.translatable("gui.soundworks.msg.saved"));
	}
}
