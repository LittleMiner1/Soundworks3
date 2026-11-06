package dev.soundworks.block;

import dev.soundworks.DiscData;
import dev.soundworks.Hooks;
import dev.soundworks.ModRegistry;
import dev.soundworks.PlaybackState;
import dev.soundworks.net.Net;
import dev.soundworks.net.Network;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * 1940s radio: connects to a network (IP + password) and plays any song stored on its servers, from anywhere.
 * Can also act as a sender for speakers.
 */
public class RadioBlockEntity extends NetNodeBlockEntity implements Controllable, Configurable, Sender {
	public final PlaybackState ps = new PlaybackState();
	public String speakerIp = "";

	public RadioBlockEntity(BlockPos pos, BlockState state) {
		super(ModRegistry.RADIO_BE, pos, state);
	}

	@Override public PlaybackState state() { return ps; }
	@Override public String getSpeakerIp() { return speakerIp; }
	@Override public void setSpeakerIp(String ip) { speakerIp = ip; }

	@Override
	protected void saveAdditional(ValueOutput out) {
		super.saveAdditional(out);
		ps.save(out);
		out.putString("speakerIp", speakerIp);
	}

	@Override
	protected void loadAdditional(ValueInput in) {
		super.loadAdditional(in);
		ps.load(in);
		speakerIp = in.getStringOr("speakerIp", "");
	}

	@Override
	protected void sync() {
		super.sync();
		Network.pushSpeaker(this);
	}

	@Override
	public void setRemoved() {
		super.setRemoved();
		if (level != null && level.isClientSide()) Hooks.audio.stop(worldPosition.asLong());
	}

	public static void tick(Level level, BlockPos pos, BlockState state, RadioBlockEntity be) {
		if (level.isClientSide()) {
			Hooks.audio.tick(pos.asLong(), pos, be.ps, level.getGameTime(), be.speakerIp);
			return;
		}
		if (!be.ps.playing) return;
		long now = level.getGameTime();
		if (be.ps.posMs(now) >= be.ps.lengthMs) {
			be.ps.stop();
			be.sync();
			return;
		}
		if (now % 10 == 0) {
			// the song must still be stored in the network; then that server counts as streaming
			Network.Hit hit = Network.findSong(be.ip, be.password, be.ps.hash);
			if (hit == null) {
				be.ps.stop();
				be.sync();
			} else {
				hit.server().markStreaming(now);
			}
		}
	}

	/** Starts a song from the network (checked by Network.handleRadioPlay). */
	public void play(DiscData d) {
		if (level == null) return;
		ps.start(d, level.getGameTime());
		sync();
	}

	@Override
	public void handleAction(ServerPlayer player, int action, float value) {
		if (level == null) return;
		switch (action) {
			case ACTION_TOGGLE -> ps.stop();
			case ACTION_VOLUME -> ps.volume = PlaybackState.clampVolume(value);
			default -> { return; }
		}
		sync();
	}

	@Override
	public void configure(ServerPlayer player, String field, String s1, String s2, String s3) {
		if (!field.equals("speaker_ip")) return;
		if (Network.setSpeakerIp(this, player, s1)) sync();
	}
}
