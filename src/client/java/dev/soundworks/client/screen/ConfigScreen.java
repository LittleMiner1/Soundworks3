package dev.soundworks.client.screen;

import dev.soundworks.IpUtil;
import dev.soundworks.block.AntennaBlockEntity;
import dev.soundworks.block.SpeakerBlockEntity;
import dev.soundworks.net.Net;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Antenna (IP) and speaker (frequency) settings. */
public class ConfigScreen extends Screen {
	private static final int W = 220, H = 92;
	private final BlockPos pos;
	private final boolean antenna;
	private EditBox box;

	public ConfigScreen(BlockPos pos) {
		super(Component.empty());
		this.pos = pos;
		var level = Minecraft.getInstance().level;
		this.antenna = level != null && level.getBlockEntity(pos) instanceof AntennaBlockEntity;
	}

	private String current() {
		var level = Minecraft.getInstance().level;
		BlockEntity be = level == null ? null : level.getBlockEntity(pos);
		if (be instanceof AntennaBlockEntity a) return a.ip;
		if (be instanceof SpeakerBlockEntity s) return s.freq;
		return "";
	}

	private void apply() {
		ClientPlayNetworking.send(new Net.ConfigSet(pos, antenna ? "antenna" : "speaker", box.getValue(), "", ""));
	}

	@Override
	protected void init() {
		int cx = width / 2, top = height / 2 - H / 2;
		box = addRenderableWidget(Gui.field(font, cx - 100, top + 36, 140, 18,
			Component.translatable(antenna ? "gui.soundworks.ip" : "gui.soundworks.frequency"), current(), IpUtil.MAX_IP, IpUtil::typing));
		setFocused(box);
		box.setFocused(true);
		addRenderableWidget(Button.builder(Component.translatable("gui.soundworks.set"), b -> apply())
			.bounds(cx + 44, top + 35, 56, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
			.bounds(cx - 50, top + 64, 100, 20).build());
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == 257 || event.key() == 335) { // Enter / keypad Enter
			apply();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractBackground(g, mouseX, mouseY, delta);
		Gui.panel(g, width / 2 - W / 2, height / 2 - H / 2, W, H, null);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		var level = Minecraft.getInstance().level;
		if (level == null || level.getBlockEntity(pos) == null) { onClose(); return; }
		super.extractRenderState(g, mouseX, mouseY, delta);
		int cx = width / 2, top = height / 2 - H / 2;
		Component name = Component.translatable(antenna ? "block.soundworks.antenna" : "block.soundworks.speaker");
		g.centeredText(font, name, cx, top + 8, Gui.TEXT);
		g.text(font, Component.translatable(antenna ? "gui.soundworks.ip" : "gui.soundworks.frequency"), cx - 100, top + 25, Gui.GRAY, false);
	}
}
