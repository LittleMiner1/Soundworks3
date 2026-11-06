package dev.soundworks.block;

import dev.soundworks.Hooks;
import dev.soundworks.IpUtil;
import dev.soundworks.ModRegistry;
import dev.soundworks.net.Net;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Plays whatever the sender with the same frequency plays. Several speakers may share a frequency. */
public class SpeakerBlockEntity extends BlockEntity implements Configurable {
	public String freq = "";

	public SpeakerBlockEntity(BlockPos pos, BlockState state) {
		super(ModRegistry.SPEAKER_BE, pos, state);
	}

	@Override
	protected void saveAdditional(ValueOutput out) {
		super.saveAdditional(out);
		out.putString("freq", freq);
	}

	@Override
	protected void loadAdditional(ValueInput in) {
		super.loadAdditional(in);
		freq = in.getStringOr("freq", "");
	}

	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return saveWithoutMetadata(registries);
	}

	/** Client only: tells the audio engine that this speaker is loaded. */
	public static void clientTick(Level level, BlockPos pos, BlockState state, SpeakerBlockEntity be) {
		if (!be.freq.isEmpty()) Hooks.audio.speakerTick(be.freq, pos);
	}

	@Override
	public void configure(ServerPlayer player, String field, String s1, String s2, String s3) {
		if (!field.equals("speaker")) return;
		String n = IpUtil.normalize(s1);
		if (n == null) {
			Net.overlay(player, Component.translatable("gui.soundworks.msg.bad_ip"));
			return;
		}
		freq = n;
		setChanged();
		if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
		Net.overlay(player, Component.translatable("gui.soundworks.msg.saved"));
	}
}
