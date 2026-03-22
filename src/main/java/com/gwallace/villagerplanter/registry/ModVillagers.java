package com.gwallace.villagerplanter.registry;

import com.google.common.collect.ImmutableSet;
import com.gwallace.villagerplanter.VillagerPlanterMod;
import net.fabricmc.fabric.api.object.builder.v1.world.poi.PointOfInterestHelper;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

public class ModVillagers {
	public static final ResourceKey<PoiType> FORESTER_POI_KEY = ResourceKey.create(
			Registries.POINT_OF_INTEREST_TYPE,
			Identifier.fromNamespaceAndPath(VillagerPlanterMod.MOD_ID, "forester"));

	public static final ResourceKey<VillagerProfession> FORESTER_KEY = ResourceKey.create(
			Registries.VILLAGER_PROFESSION,
			Identifier.fromNamespaceAndPath(VillagerPlanterMod.MOD_ID, "forester"));

	public static void register() {
		PointOfInterestHelper.register(
				Identifier.fromNamespaceAndPath(VillagerPlanterMod.MOD_ID, "forester"),
				1, 1, ModBlocks.BAND_SAW);

		Registry.register(BuiltInRegistries.VILLAGER_PROFESSION, FORESTER_KEY,
				new VillagerProfession(
						Component.translatable("entity.villagerplanter.villager.forester"),
						holder -> holder.is(FORESTER_POI_KEY),
						holder -> holder.is(FORESTER_POI_KEY),
						ImmutableSet.of(),
						ImmutableSet.of(),
						null));
	}
}
