package dev.soundworks.client.screen;

import dev.soundworks.menu.RecordBoxMenu;
import dev.soundworks.net.Net;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Record box (and the whole wall of touching boxes): 54-slot window, mouse wheel scrolling, artist and title filter. */
public class RecordBoxScreen extends EditContainerScreen<RecordBoxMenu> {
	private EditBox artistBox, titleBox;
	private String lastArtist = "", lastTitle = "";

	public RecordBoxScreen(RecordBoxMenu menu, Inventory inv, Component title) {
		super(menu, inv, title, 176, 240);
	}

	private void view(int offset) {
		ClientPlayNetworking.send(new Net.BoxView(Math.max(0, offset), artistBox.getValue(), titleBox.getValue()));
	}

	private void filterChanged() {
		String a = artistBox.getValue(), t = titleBox.getValue();
		if (!a.equals(lastArtist) || !t.equals(lastTitle)) {
			lastArtist = a;
			lastTitle = t;
			view(0);
		}
	}

	@Override
	protected void init() {
		super.init();
		titleLabelY = 10000;
		inventoryLabelY = 10000;
		artistBox = edit(Gui.field(font, leftPos + 8, topPos + 18, 76, 14, Component.translatable("gui.soundworks.artist"),
			lastArtist, 64, null));
		artistBox.setHint(Component.translatable("gui.soundworks.artist"));
		artistBox.setResponder(text -> filterChanged());
		titleBox = edit(Gui.field(font, leftPos + 88, topPos + 18, 80, 14, Component.translatable("gui.soundworks.song_title"),
			lastTitle, 64, null));
		titleBox.setHint(Component.translatable("gui.soundworks.song_title"));
		titleBox.setResponder(text -> filterChanged());
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (scrollY != 0) {
			view(menu.offset() + (scrollY > 0 ? -9 : 9));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		Gui.panel(g, leftPos, topPos, imageWidth, imageHeight, menu);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		extractBackground(g, mouseX, mouseY, delta);
		super.extractRenderState(g, mouseX, mouseY, delta);
		g.text(font, title, leftPos + 8, topPos + 6, Gui.TEXT, false);
		int total = menu.total(), off = menu.offset();
		int from = total == 0 ? 0 : off + 1;
		int to = Math.min(total, off + 54);
		String range = Component.translatable("gui.soundworks.box.range", from, to, total).getString();
		g.text(font, Component.literal(range), leftPos + imageWidth - 8 - font.width(range), topPos + 6, Gui.GRAY, false);
		extractTooltip(g, mouseX, mouseY);
	}
}
