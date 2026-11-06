package dev.soundworks.client.screen;

import dev.soundworks.IpUtil;
import dev.soundworks.PlaybackState;
import dev.soundworks.block.Controllable;
import dev.soundworks.block.NetNodeBlockEntity;
import dev.soundworks.block.RadioBlockEntity;
import dev.soundworks.net.Net;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * Terminal (list only) and radio (list, click to play, stop, volume, speaker frequency).
 * The password is only sent to the server; the screen never gets it back.
 */
public class NetScreen extends Screen {
	/** The currently open screen (set while open), used by the network receiver. */
	static NetScreen current;

	private static final int W = 280, ROWS = 6, ROW_H = 16, LIST_X = 8, LIST_Y = 82, LIST_W = 244;
	private final BlockPos pos;
	private final boolean radio;
	private final int h;
	private List<Net.NetListing.Entry> entries = List.of();
	private final List<Net.NetListing.Entry> shown = new ArrayList<>();
	private int status = Net.ST_NOT_CONNECTED;
	private int offset = 0;
	private long lastQuery = 0;
	private EditBox ip, password, search, freq;
	private final Button[] rows = new Button[ROWS];
	private Button upButton, downButton;
	private SettingSlider volumeSlider, speedSlider;

	public NetScreen(BlockPos pos, boolean radio) {
		super(Component.translatable(radio ? "block.soundworks.radio" : "block.soundworks.terminal"));
		this.pos = pos;
		this.radio = radio;
		this.h = radio ? 242 : 200;
	}

	private NetNodeBlockEntity node() {
		var level = Minecraft.getInstance().level;
		return level != null && level.getBlockEntity(pos) instanceof NetNodeBlockEntity n ? n : null;
	}

	private RadioBlockEntity radioBe() {
		return node() instanceof RadioBlockEntity r ? r : null;
	}

	private int left() { return width / 2 - W / 2; }
	private int top() { return Math.max(4, height / 2 - h / 2); }

	/** Called by the client receiver when the server answers a query. */
	public static void onListing(Net.NetListing l) {
		NetScreen s = current;
		if (s == null || !s.pos.equals(l.pos())) return;
		s.status = l.status();
		s.entries = l.status() == Net.ST_OK ? l.entries() : List.of();
		if (l.status() == Net.ST_OK && s.ip != null && s.ip.getValue().isEmpty()) s.ip.setValue(l.ip());
		s.refilter();
	}

	private void query(boolean useSaved) {
		lastQuery = System.currentTimeMillis();
		ClientPlayNetworking.send(new Net.NetQuery(pos, useSaved, ip == null ? "" : ip.getValue(),
			password == null ? "" : password.getValue()));
	}

	private void refilter() {
		String q = search == null ? "" : search.getValue().strip().toLowerCase(Locale.ROOT);
		shown.clear();
		for (Net.NetListing.Entry e : entries) {
			if (q.isEmpty() || (e.server() + " " + e.artist() + " " + e.title()).toLowerCase(Locale.ROOT).contains(q)) shown.add(e);
		}
		offset = Math.max(0, Math.min(offset, shown.size() - ROWS));
		updateRows();
	}

	private String text(Net.NetListing.Entry e) {
		String artist = e.artist().isBlank() ? "" : e.artist() + " - ";
		return e.server() + ": " + artist + e.title();
	}

	private void updateRows() {
		if (rows[0] == null) return;
		for (int i = 0; i < ROWS; i++) {
			int idx = offset + i;
			boolean has = idx < shown.size();
			rows[i].visible = radio && has;
			rows[i].active = radio && has;
			rows[i].setMessage(has ? Component.literal(Gui.fit(font, text(shown.get(idx)), LIST_W - 12)) : Component.empty());
		}
		boolean scroll = shown.size() > ROWS;
		upButton.visible = scroll;
		downButton.visible = scroll;
		upButton.active = scroll && offset > 0;
		downButton.active = scroll && offset < shown.size() - ROWS;
	}

	@Override
	protected void init() {
		current = this;
		int l = left(), t = top();
		var n = node();
		ip = addRenderableWidget(Gui.field(font, l + 8, t + 38, 90, 16, Component.translatable("gui.soundworks.ip"),
			n == null ? "" : n.ip, IpUtil.MAX_IP, IpUtil::typing));
		password = addRenderableWidget(Gui.field(font, l + 102, t + 38, 90, 16, Component.translatable("gui.soundworks.password"),
			"", IpUtil.MAX_PASSWORD, null));
		password.setHint(Component.translatable(n != null && n.hasPw ? "gui.soundworks.server.pw_set" : "gui.soundworks.server.pw_none"));
		addRenderableWidget(Button.builder(Component.translatable("gui.soundworks.connect"), b -> query(false))
			.bounds(l + 196, t + 37, 76, 18).build());
		search = addRenderableWidget(Gui.field(font, l + 8, t + 60, 264, 16, Component.translatable("gui.soundworks.search"),
			"", 64, null));
		search.setHint(Component.translatable("gui.soundworks.search"));
		search.setResponder(s -> { offset = 0; refilter(); });
		for (int i = 0; i < ROWS; i++) {
			final int idx = i;
			rows[i] = addRenderableWidget(Button.builder(Component.empty(), b -> {
				int k = offset + idx;
				if (radio && k < shown.size()) ClientPlayNetworking.send(new Net.RadioPlay(pos, shown.get(k).hash()));
			}).bounds(l + LIST_X, t + LIST_Y + i * ROW_H, LIST_W, ROW_H - 1).build());
		}
		upButton = addRenderableWidget(Button.builder(Component.literal("^"), b -> { offset = Math.max(0, offset - 1); updateRows(); })
			.bounds(l + 256, t + LIST_Y, 16, 14).build());
		downButton = addRenderableWidget(Button.builder(Component.literal("v"), b -> {
			offset = Math.max(0, Math.min(shown.size() - ROWS, offset + 1)); updateRows(); })
			.bounds(l + 256, t + LIST_Y + 16, 16, 14).build());
		if (radio) {
			var r = radioBe();
			addRenderableWidget(Button.builder(Component.translatable("gui.soundworks.stop"),
				b -> ClientPlayNetworking.send(new Net.DeviceAction(pos, Controllable.ACTION_TOGGLE, 0f)))
				.bounds(l + 8, t + 196, 60, 18).build());
			speedSlider = addRenderableWidget(new SettingSlider(l + 72, t + 196, 98, 18, "gui.soundworks.speed",
				PlaybackState.SPEED_MIN, PlaybackState.SPEED_MAX, 0.01f, 0.02f, r == null ? 1f : r.ps.speed,
				v -> ClientPlayNetworking.send(new Net.DeviceAction(pos, Controllable.ACTION_SPEED, v))));
			volumeSlider = addRenderableWidget(new SettingSlider(l + 174, t + 196, 98, 18, "gui.soundworks.volume",
				PlaybackState.VOLUME_MIN, PlaybackState.VOLUME_MAX, 0.01f, 0.04f, r == null ? 1f : r.ps.volume,
				v -> ClientPlayNetworking.send(new Net.DeviceAction(pos, Controllable.ACTION_VOLUME, v))));
			freq = addRenderableWidget(Gui.field(font, l + 8, t + 218, 196, 16, Component.translatable("gui.soundworks.speaker_ip"),
				r == null ? "" : r.speakerIp, IpUtil.MAX_IP, IpUtil::typing));
			freq.setHint(Component.translatable("gui.soundworks.speaker_ip"));
			addRenderableWidget(Button.builder(Component.translatable("gui.soundworks.set"),
				b -> ClientPlayNetworking.send(new Net.ConfigSet(pos, "speaker_ip", freq.getValue(), "", "")))
				.bounds(l + 208, t + 217, 64, 18).build());
		}
		updateRows();
		if (n != null && !n.ip.isEmpty()) query(true);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (scrollY != 0 && shown.size() > ROWS) {
			offset = Math.max(0, Math.min(shown.size() - ROWS, offset + (scrollY > 0 ? -1 : 1)));
			updateRows();
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == 257 || event.key() == 335) { // Enter in the IP/password fields connects
			if ((ip != null && ip.isFocused()) || (password != null && password.isFocused())) {
				query(false);
				return true;
			}
		}
		return super.keyPressed(event);
	}

	@Override
	public void removed() {
		super.removed();
		if (current == this) current = null;
		if (volumeSlider != null) volumeSlider.flush(true);
		if (speedSlider != null) speedSlider.flush(true);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractBackground(g, mouseX, mouseY, delta);
		int l = left(), t = top();
		Gui.panel(g, l, t, W, h, null);
		Gui.inset(g, l + LIST_X - 1, t + LIST_Y - 1, l + LIST_X + LIST_W + 1, t + LIST_Y + ROWS * ROW_H);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		var n = node();
		if (n == null) { onClose(); return; }
		if (status == Net.ST_OK && System.currentTimeMillis() - lastQuery > 4000) query(true);
		RadioBlockEntity r = radioBe();
		if (volumeSlider != null && r != null) {
			volumeSlider.syncFrom(r.ps.volume);
			volumeSlider.flush(false);
		}
		if (speedSlider != null && r != null) {
			speedSlider.syncFrom(r.ps.speed);
			speedSlider.flush(false);
		}
		super.extractRenderState(g, mouseX, mouseY, delta);
		int l = left(), t = top();
		g.text(font, title, l + 8, t + 8, Gui.TEXT, false);
		String st = Component.translatable("gui.soundworks.net.status." + status).getString();
		g.text(font, Component.literal(Gui.fit(font, st, W - 16)), l + 8, t + 20, status == Net.ST_OK ? Gui.GOOD
			: status == Net.ST_NOT_CONNECTED ? Gui.GRAY : Gui.BAD, false);
		g.text(font, Component.translatable("gui.soundworks.ip"), l + 8, t + 29, Gui.GRAY, false);
		g.text(font, Component.translatable("gui.soundworks.password"), l + 102, t + 29, Gui.GRAY, false);
		if (!radio) {
			// terminal: plain text lines inside the inset
			for (int i = 0; i < ROWS && offset + i < shown.size(); i++) {
				g.text(font, Component.literal(Gui.fit(font, text(shown.get(offset + i)), LIST_W - 8)),
					l + LIST_X + 3, t + LIST_Y + i * ROW_H + 4, 0xFFFFFFFF, false);
			}
		}
		if (status == Net.ST_OK && shown.isEmpty()) {
			g.text(font, Component.translatable("gui.soundworks.no_songs"), l + LIST_X + 4, t + LIST_Y + 5, 0xFFFFFFFF, false);
		}
		if (radio && r != null) {
			String np = r.ps.playing ? Component.translatable("gui.soundworks.now_playing", r.ps.title).getString()
				: Component.translatable("gui.soundworks.no_disc_radio").getString();
			g.text(font, Component.literal(Gui.fit(font, np, W - 16)), l + 8, t + 182, Gui.TEXT, false);
		} else if (status == Net.ST_OK) {
			g.text(font, Component.translatable("gui.soundworks.songs", shown.size()), l + 8, t + 182, Gui.GRAY, false);
		}
	}
}
