package dev.soundworks.client.screen;

import dev.soundworks.menu.RecordBoxMenu;
import dev.soundworks.net.Net;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Record box (and the whole wall of touching boxes): 54-slot window with scroll buttons and a search field. */
public class RecordBoxScreen extends EditContainerScreen<RecordBoxMenu> {
	private EditBox search;
	private String lastQuery = "";

	public RecordBoxScreen(RecordBoxMenu menu, Inventory inv, Component title) {
		super(menu, inv, title, 176, 240);
	}

	private void view(int offset) {
		ClientPlayNetworking.send(new Net.BoxView(Math.max(0, offset), search.getValue()));
	}

	@Override
	protected void init() {
		super.init();
		titleLabelY = 10000;
		inventoryLabelY = 10000;
		search = edit(Gui.field(font, leftPos + 8, topPos + 18, 80, 14, Component.translatable("gui.soundworks.search"),
			lastQuery, 64, null));
		search.setHint(Component.translatable("gui.soundworks.search"));
		search.setResponder(text -> {
			if (!text.equals(lastQuery)) {
				lastQuery = text;
				ClientPlayNetworking.send(new Net.BoxView(0, text));
			}
		});
		String[] labels = {"<<", "<", ">", ">>"};
		int[] steps = {-54, -9, 9, 54};
		for (int i = 0; i < 4; i++) {
			final int step = steps[i];
			addRenderableWidget(Button.builder(Component.literal(labels[i]), b -> view(menu.offset() + step))
				.bounds(leftPos + 92 + i * 19, topPos + 18, 18, 14).build());
		}
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
