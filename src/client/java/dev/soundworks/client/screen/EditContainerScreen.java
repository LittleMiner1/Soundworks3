package dev.soundworks.client.screen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

/** Container screen with text fields: typing (e.g. the inventory key "E") must not close the screen. */
abstract class EditContainerScreen<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> {
	private final List<EditBox> boxes = new ArrayList<>();

	EditContainerScreen(T menu, Inventory inv, Component title, int w, int h) {
		super(menu, inv, title, w, h);
	}

	protected final <E extends EditBox> E edit(E box) {
		boxes.add(box);
		return addRenderableWidget(box);
	}

	@Override
	protected void init() {
		boxes.clear();
		super.init();
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() != 256) { // 256 = Escape
			for (EditBox b : boxes) {
				if (b.isFocused()) return b.keyPressed(event) || b.canConsumeInput();
			}
		}
		return super.keyPressed(event);
	}
}
