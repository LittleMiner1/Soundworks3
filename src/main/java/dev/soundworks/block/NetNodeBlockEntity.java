package dev.soundworks.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Base of radio and terminal: remembers the network (IP + password) it connected to. The password stays on the server. */
public abstract class NetNodeBlockEntity extends BlockEntity {
	public String ip = "";
	public String password = "";
	/** Client-side mirror: the password itself is never sent. */
	public boolean hasPw = false;

	protected NetNodeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	protected void saveAdditional(ValueOutput out) {
		super.saveAdditional(out);
		out.putString("ip", ip);
		if (!Secrets.hidden()) out.putString("password", password);
		out.putBoolean("hasPw", !password.isEmpty());
	}

	@Override
	protected void loadAdditional(ValueInput in) {
		super.loadAdditional(in);
		ip = in.getStringOr("ip", "");
		password = in.getStringOr("password", "");
		hasPw = in.getBooleanOr("hasPw", !password.isEmpty());
	}

	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return Secrets.hide(() -> ClientboundBlockEntityDataPacket.create(this));
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return Secrets.hide(() -> saveWithoutMetadata(registries));
	}

	protected void sync() {
		setChanged();
		if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
	}

	/** Called after a successful connection. */
	public void setConnection(String newIp, String newPassword) {
		ip = newIp;
		password = newPassword;
		hasPw = !newPassword.isEmpty();
		sync();
	}
}
