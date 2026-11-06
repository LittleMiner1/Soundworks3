package dev.soundworks;

import dev.soundworks.block.*;
import dev.soundworks.menu.*;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class ModRegistry {
	private ModRegistry() {}

	public static DataComponentType<DiscData> DISC_DATA;
	public static SoundEvent SILENT;

	public static Item BLANK_DISC, DISC, PRESS_ITEM, TURNTABLE_ITEM, JUKEBOX_ITEM;
	public static Item RECORD_BOX_ITEM, SERVER_ITEM, ANTENNA_ITEM, SPEAKER_ITEM, RADIO_ITEM, TERMINAL_ITEM;
	public static Block PRESS, TURNTABLE, JUKEBOX;
	public static Block RECORD_BOX, SERVER, ANTENNA, SPEAKER, RADIO, TERMINAL;
	public static BlockEntityType<PressBlockEntity> PRESS_BE;
	public static BlockEntityType<TurntableBlockEntity> TURNTABLE_BE;
	public static BlockEntityType<JukeboxMachineBlockEntity> JUKEBOX_BE;
	public static BlockEntityType<RecordBoxBlockEntity> RECORD_BOX_BE;
	public static BlockEntityType<ServerBlockEntity> SERVER_BE;
	public static BlockEntityType<AntennaBlockEntity> ANTENNA_BE;
	public static BlockEntityType<SpeakerBlockEntity> SPEAKER_BE;
	public static BlockEntityType<RadioBlockEntity> RADIO_BE;
	public static BlockEntityType<TerminalBlockEntity> TERMINAL_BE;
	public static MenuType<ServerMenu> SERVER_MENU;
	public static MenuType<RecordBoxMenu> RECORD_BOX_MENU;
	public static MenuType<PressMenu> PRESS_MENU;
	public static MenuType<JukeboxMenu> JUKEBOX_MENU;

	private static Block block(String name, java.util.function.Function<BlockBehaviour.Properties, Block> f) {
		return block(name, f, false);
	}

	/** metal = iron-like blocks (server, antenna, terminal, speaker), otherwise wooden. */
	private static Block block(String name, java.util.function.Function<BlockBehaviour.Properties, Block> f, boolean metal) {
		Identifier id = SoundworksMod.id(name);
		Block b = f.apply(BlockBehaviour.Properties.of().strength(metal ? 3.0f : 2.5f)
			.sound(metal ? SoundType.METAL : SoundType.WOOD).noOcclusion()
			.setId(ResourceKey.create(Registries.BLOCK, id)));
		Registry.register(BuiltInRegistries.BLOCK, id, b);
		Registry.register(BuiltInRegistries.ITEM, id, new BlockItem(b, new Item.Properties()
			.setId(ResourceKey.create(Registries.ITEM, id)).useBlockDescriptionPrefix()));
		return b;
	}

	private static Item item(Block b) {
		return BuiltInRegistries.ITEM.getValue(BuiltInRegistries.BLOCK.getKey(b));
	}

	/** Hides the vanilla "custom" jukebox song line so that only the artist (lore) is shown. */
	private static TooltipDisplay hiddenJukeboxTooltip() {
		return TooltipDisplay.CODEC
			.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"hidden_components\":[\"minecraft:jukebox_playable\"]}"))
			.result().orElse(TooltipDisplay.DEFAULT);
	}

	public static void init() {
		Identifier compId = SoundworksMod.id("disc_data");
		DISC_DATA = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, compId,
			DataComponentType.<DiscData>builder().persistent(DiscData.CODEC).networkSynchronized(DiscData.STREAM_CODEC).build());

		Identifier silentId = SoundworksMod.id("disc_custom");
		SILENT = Registry.register(BuiltInRegistries.SOUND_EVENT, silentId, SoundEvent.createVariableRangeEvent(silentId));

		Identifier blankId = SoundworksMod.id("blank_disc");
		BLANK_DISC = Registry.register(BuiltInRegistries.ITEM, blankId,
			new Item(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, blankId))));

		Identifier discId = SoundworksMod.id("disc");
		DISC = Registry.register(BuiltInRegistries.ITEM, discId,
			new Item(new Item.Properties().stacksTo(1)
				.jukeboxPlayable(ResourceKey.create(Registries.JUKEBOX_SONG, SoundworksMod.id("custom")))
				.component(DataComponents.TOOLTIP_DISPLAY, hiddenJukeboxTooltip())
				.setId(ResourceKey.create(Registries.ITEM, discId))));

		PRESS = block("press", PressBlock::new);
		TURNTABLE = block("turntable", TurntableBlock::new);
		JUKEBOX = block("jukebox", JukeboxMachineBlock::new);
		RECORD_BOX = block("record_box", RecordBoxBlock::new);
		SERVER = block("server", ServerBlock::new, true);
		ANTENNA = block("antenna", AntennaBlock::new, true);
		SPEAKER = block("speaker", SpeakerBlock::new);
		RADIO = block("radio", RadioBlock::new);
		TERMINAL = block("terminal", TerminalBlock::new, true);
		PRESS_ITEM = item(PRESS);
		TURNTABLE_ITEM = item(TURNTABLE);
		JUKEBOX_ITEM = item(JUKEBOX);
		RECORD_BOX_ITEM = item(RECORD_BOX);
		SERVER_ITEM = item(SERVER);
		ANTENNA_ITEM = item(ANTENNA);
		SPEAKER_ITEM = item(SPEAKER);
		RADIO_ITEM = item(RADIO);
		TERMINAL_ITEM = item(TERMINAL);

		PRESS_BE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, SoundworksMod.id("press"),
			FabricBlockEntityTypeBuilder.create(PressBlockEntity::new, PRESS).build());
		TURNTABLE_BE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, SoundworksMod.id("turntable"),
			FabricBlockEntityTypeBuilder.create(TurntableBlockEntity::new, TURNTABLE).build());
		JUKEBOX_BE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, SoundworksMod.id("jukebox"),
			FabricBlockEntityTypeBuilder.create(JukeboxMachineBlockEntity::new, JUKEBOX).build());
		RECORD_BOX_BE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, SoundworksMod.id("record_box"),
			FabricBlockEntityTypeBuilder.create(RecordBoxBlockEntity::new, RECORD_BOX).build());
		SERVER_BE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, SoundworksMod.id("server"),
			FabricBlockEntityTypeBuilder.create(ServerBlockEntity::new, SERVER).build());
		ANTENNA_BE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, SoundworksMod.id("antenna"),
			FabricBlockEntityTypeBuilder.create(AntennaBlockEntity::new, ANTENNA).build());
		SPEAKER_BE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, SoundworksMod.id("speaker"),
			FabricBlockEntityTypeBuilder.create(SpeakerBlockEntity::new, SPEAKER).build());
		RADIO_BE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, SoundworksMod.id("radio"),
			FabricBlockEntityTypeBuilder.create(RadioBlockEntity::new, RADIO).build());
		TERMINAL_BE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, SoundworksMod.id("terminal"),
			FabricBlockEntityTypeBuilder.create(TerminalBlockEntity::new, TERMINAL).build());

		SERVER_MENU = Registry.register(BuiltInRegistries.MENU, SoundworksMod.id("server"),
			new ExtendedMenuType<>(ServerMenu::new, BlockPos.STREAM_CODEC.cast()));
		RECORD_BOX_MENU = Registry.register(BuiltInRegistries.MENU, SoundworksMod.id("record_box"),
			new ExtendedMenuType<>(RecordBoxMenu::new, BlockPos.STREAM_CODEC.cast()));
		PRESS_MENU = Registry.register(BuiltInRegistries.MENU, SoundworksMod.id("press"),
			new ExtendedMenuType<>(PressMenu::new, BlockPos.STREAM_CODEC.cast()));
		JUKEBOX_MENU = Registry.register(BuiltInRegistries.MENU, SoundworksMod.id("jukebox"),
			new ExtendedMenuType<>(JukeboxMenu::new, BlockPos.STREAM_CODEC.cast()));

		// One creative tab for everything in the mod.
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, SoundworksMod.id("main"),
			FabricCreativeModeTab.builder()
				.title(Component.translatable("itemGroup.soundworks"))
				.icon(() -> new ItemStack(TURNTABLE_ITEM))
				.displayItems((params, out) -> {
					for (Item i : new Item[] {BLANK_DISC, PRESS_ITEM, TURNTABLE_ITEM, JUKEBOX_ITEM, RECORD_BOX_ITEM,
							SERVER_ITEM, ANTENNA_ITEM, SPEAKER_ITEM, RADIO_ITEM, TERMINAL_ITEM}) {
						out.accept(i);
					}
				})
				.build());
	}

	/** RGB color of a dye, or -1 if the stack is not a dye. */
	public static int dyeColor(ItemStack stack) {
		if (stack.isEmpty()) return -1;
		Identifier key = BuiltInRegistries.ITEM.getKey(stack.getItem());
		String path = key.getPath();
		if (!key.getNamespace().equals("minecraft") || !path.endsWith("_dye")) return -1;
		String color = path.substring(0, path.length() - 4);
		for (DyeColor d : DyeColor.values()) {
			if (d.getSerializedName().equals(color)) return d.getTextureDiffuseColor() & 0xFFFFFF;
		}
		return -1;
	}
}
