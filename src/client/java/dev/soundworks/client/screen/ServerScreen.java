package dev.soundworks.client.screen;

import dev.soundworks.IpUtil;
import dev.soundworks.block.ServerBlock;
import dev.soundworks.block.ServerBlockEntity;
import dev.soundworks.menu.ServerMenu;
import dev.soundworks.net.Net;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Server block: 9 record slots, IP, name and optional password. */
public class ServerScreen extends EditContainerScreen<ServerMenu> {
	private static final int FIELD_X = 58, FIELD_W = 110;
	private EditBox ip, name, pw;

	public ServerScreen(ServerMenu menu, Inventory inv, Component title) {
		super(menu, inv, title, 176, 216);
	}

	private ServerBlockEntity be() {
		var level = Minecraft.getInstance().level;
		return level != null && level.getBlockEntity(menu.pos) instanceof ServerBlockEntity s ? s : null;
	}

	private void send(String pass) {
		ClientPlayNetworking.send(new Net.ConfigSet(menu.pos, "server", ip.getValue(), name.getValue(), pass));
	}

	@Override
	protected void init() {
		super.init();
		titleLabelY = 10000;
		inventoryLabelY = 10000;
		var s = be();
		ip = edit(Gui.field(font, leftPos + FIELD_X, topPos + 50, FIELD_W, 14, Component.translatable("gui.soundworks.ip"),
			s == null ? "" : s.ip, IpUtil.MAX_IP, IpUtil::typing));
		name = edit(Gui.field(font, leftPos + FIELD_X, topPos + 68, FIELD_W, 14, Component.translatable("gui.soundworks.name"),
			s == null ? "Server" : s.name, IpUtil.MAX_NAME, null));
		pw = edit(Gui.field(font, leftPos + FIELD_X, topPos + 86, FIELD_W, 14, Component.translatable("gui.soundworks.password"),
			"", IpUtil.MAX_PASSWORD, null));
		addRenderableWidget(Button.builder(Component.translatable("gui.soundworks.apply"), b -> send(pw.getValue()))
			.bounds(leftPos + 8, topPos + 106, 78, 16).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.soundworks.clear_pw"), b -> send(Net.CLEAR_PW))
			.bounds(leftPos + 90, topPos + 106, 78, 16).build());
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		Gui.panel(g, leftPos, topPos, imageWidth, imageHeight, menu);
	}

	private void label(GuiGraphicsExtractor g, String key, int y) {
		g.text(font, Component.literal(Gui.fit(font, Component.translatable(key).getString(), FIELD_X - 10)),
			leftPos + 8, topPos + y + 3, Gui.TEXT, false);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		var s = be();
		pw.setHint(Component.translatable(s != null && s.hasPw ? "gui.soundworks.server.pw_set" : "gui.soundworks.server.pw_none"));
		extractBackground(g, mouseX, mouseY, delta);
		super.extractRenderState(g, mouseX, mouseY, delta);
		g.text(font, title, leftPos + 8, topPos + 6, Gui.TEXT, false);
		int lamp = s == null ? 0 : s.getBlockState().getValue(ServerBlock.LAMP);
		String key = lamp == 0 ? "gui.soundworks.server.offline" : lamp == 1 ? "gui.soundworks.server.online" : "gui.soundworks.server.streaming";
		g.text(font, Component.literal(Gui.fit(font, Component.translatable(key).getString(), imageWidth - 16)),
			leftPos + 8, topPos + 17, lamp == 0 ? Gui.BAD : Gui.GOOD, false);
		label(g, "gui.soundworks.ip", 50);
		label(g, "gui.soundworks.name", 68);
		label(g, "gui.soundworks.password", 86);
		extractTooltip(g, mouseX, mouseY);
	}
}
