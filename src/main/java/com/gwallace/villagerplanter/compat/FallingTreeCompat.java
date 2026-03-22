package com.gwallace.villagerplanter.compat;

import com.gwallace.villagerplanter.VillagerPlanterMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Compatibility layer for FallingTree mod.
 * 
 * When FallingTree is installed:
 *   - Uses FallingTree's tree detection and breaking handlers for full cascading tree fells
 * 
 * When FallingTree is NOT installed:
 *   - Falls back to simple single-block destruction
 * 
 * This is implemented via reflection to avoid hard compile-time dependency.
 */
public class FallingTreeCompat {
	private static final boolean FALLINGTREE_AVAILABLE = checkFallingTreeAvailable();
	private static FallingTreeHandler handler;

	/**
	 * Attempts to break a tree at the given position, using FallingTree if available.
	 * 
	 * @param level The server level
	 * @param villager The villager entity performing the break
	 * @param blockPos The position of the log block to break
	 * @param blockState The state of the block being broken
	 */
	public static void breakTree(ServerLevel level, Villager villager, BlockPos blockPos, BlockState blockState) {
		if (FALLINGTREE_AVAILABLE && handler != null) {
			try {
				handler.breakTree(level, villager, blockPos, blockState);
				return;
			} catch (Exception e) {
				VillagerPlanterMod.LOGGER.warn("[villager-planter] FallingTree integration failed", e);
			}
		}
		// Fallback: simple single-block destruction
		level.destroyBlock(blockPos, true, villager);
	}

	private static boolean checkFallingTreeAvailable() {
		try {
			// Check if Fabric's FallingTree mod is present
			Class.forName("fr.rakambda.fallingtree.fabric.FallingTree");
			handler = new FabricFallingTreeHandler();
			VillagerPlanterMod.LOGGER.info("[villager-planter] FallingTree integration enabled!");
			return true;
		} catch (ClassNotFoundException e) {
			VillagerPlanterMod.LOGGER.info("[villager-planter] FallingTree not detected, using simple tree breaking");
			return false;
		} catch (Exception e) {
			VillagerPlanterMod.LOGGER.warn("[villager-planter] Failed to initialize FallingTree handler", e);
			return false;
		}
	}

	/**
	 * Abstraction for FallingTree integration. Allows for different implementations
	 * (Fabric, Forge, Neoforge) if needed.
	 */
	private interface FallingTreeHandler {
		void breakTree(ServerLevel level, Villager villager, BlockPos blockPos, BlockState blockState) throws Exception;
	}

	/**
	 * Fabric implementation of FallingTree handler using reflection.
	 */
	private static class FabricFallingTreeHandler implements FallingTreeHandler {
		private final Class<?> modClass;
		private final Object modInstance;
		private final Object treeHandler;

		FabricFallingTreeHandler() throws Exception {
			modClass = Class.forName("fr.rakambda.fallingtree.fabric.FallingTree");
			modInstance = modClass.getField("INSTANCE").get(null);
			treeHandler = modClass.getMethod("getTreeHandler").invoke(modInstance);
		}

		@Override
		public void breakTree(ServerLevel level, Villager villager, BlockPos blockPos, BlockState blockState) throws Exception {
			// Wrap the level and player using Fabric wrappers
			Class<?> levelWrapperClass = Class.forName("fr.rakambda.fallingtree.fabric.common.wrapper.ServerLevelWrapper");
			Class<?> playerWrapperClass = Class.forName("fr.rakambda.fallingtree.fabric.common.wrapper.PlayerWrapper");
			Class<?> posWrapperClass = Class.forName("fr.rakambda.fallingtree.fabric.common.wrapper.BlockPosWrapper");
			Class<?> stateWrapperClass = Class.forName("fr.rakambda.fallingtree.fabric.common.wrapper.BlockStateWrapper");

			Object levelWrapper = levelWrapperClass.getConstructor(ServerLevel.class).newInstance(level);
			Object playerWrapper = playerWrapperClass.getConstructor(villager.getClass()).newInstance(villager);
			Object posWrapper = posWrapperClass.getConstructor(BlockPos.class).newInstance(blockPos);
			Object stateWrapper = stateWrapperClass.getConstructor(BlockState.class).newInstance(blockState);

			// Call treeHandler.breakTree(false, levelWrapper, playerWrapper, posWrapper, stateWrapper, null)
			// The 'false' parameter means this is not from a cancelled event (we handle cancellation ourselves)
			var breakTreeMethod = treeHandler.getClass().getMethod("breakTree", 
					boolean.class, levelWrapperClass, playerWrapperClass, posWrapperClass, stateWrapperClass, Object.class);
			breakTreeMethod.invoke(treeHandler, false, levelWrapper, playerWrapper, posWrapper, stateWrapper, null);
		}
	}
}

