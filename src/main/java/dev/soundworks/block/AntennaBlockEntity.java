package dev.soundworks.block;

import dev.soundworks.IpUtil;
import dev.soundworks.ModRegistry;
import dev.soundworks.net.Net;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** An antenna only has an IP. A server is online if a loaded antenna has the same IP. */
public class AntennaBlockEntity extends BlockEntity implements Configurable {
	public String ip = "";

	public AntennaBlockEntity(BlockPos pos, BlockState state) {
		super(ModRegistry.ANTENNA_BE, pos, state);
	}

	@Override
	protected void saveAdditional(ValueOutput out) {
		super.saveAdditional(out);
		out.putString("ip", ip);
	}

	@Override
	protected void loadAdditional(ValueInput in) {
		super.loadAdditional(in);
		ip = in.getStringOr("ip", "");
	}

	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return saveWithoutMetadata(registries);
	}

	@Override
	public void configure(ServerPlayer player, String field, String s1, String s2, String s3) {
		if (!field.equals("antenna")) return;
		String n = IpUtil.normalize(s1);
		if (n == null) {
			Net.overlay(player, Component.translatable("gui.soundworks.msg.bad_ip"));
			return;
		}
		ip = n;
		setChanged();
		if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
		Net.overlay(player, Component.translatable("gui.soundworks.msg.saved"));
	}
}
