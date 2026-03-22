package com.gwallace.villagerplanter;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VillagerPlanterMod implements ModInitializer {
	public static final String MOD_ID = "villagerplanter";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info(MOD_ID + " initialized!");
	}
}
