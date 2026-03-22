package com.gwallace.villagerplanter.registry;

import com.gwallace.villagerplanter.VillagerPlanterMod;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class ModBlocks {
	public static final ResourceKey<Block> BAND_SAW_KEY = ResourceKey.create(
			Registries.BLOCK, Identifier.fromNamespaceAndPath(VillagerPlanterMod.MOD_ID, "band_saw"));

	public static final ResourceKey<Item> BAND_SAW_ITEM_KEY = ResourceKey.create(
			Registries.ITEM, Identifier.fromNamespaceAndPath(VillagerPlanterMod.MOD_ID, "band_saw"));

	public static final Block BAND_SAW = new Block(
			BlockBehaviour.Properties.of()
					.strength(2.0f)
					.sound(SoundType.WOOD)
					.setId(BAND_SAW_KEY));

	public static final BlockItem BAND_SAW_ITEM = new BlockItem(BAND_SAW,
			new Item.Properties().setId(BAND_SAW_ITEM_KEY));

	public static void register() {
		Registry.register(BuiltInRegistries.BLOCK, BAND_SAW_KEY, BAND_SAW);
		Registry.register(BuiltInRegistries.ITEM, BAND_SAW_ITEM_KEY, BAND_SAW_ITEM);

		ResourceKey<CreativeModeTab> functionalTab = ResourceKey.create(
				Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("functional_blocks"));
		ItemGroupEvents.modifyEntriesEvent(functionalTab).register(entries -> entries.accept(BAND_SAW_ITEM));
	}
}
