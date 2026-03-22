package com.gwallace.villagerplanter;

import com.gwallace.villagerplanter.registry.ModBlocks;
import com.gwallace.villagerplanter.registry.ModVillagers;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VillagerPlanterMod implements ModInitializer {
	public static final String MOD_ID = "villagerplanter";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModBlocks.register();
		ModVillagers.register();
		LOGGER.info(MOD_ID + " initialized!");
	}
}
