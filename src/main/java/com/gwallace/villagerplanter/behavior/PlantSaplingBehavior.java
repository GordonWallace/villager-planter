package com.gwallace.villagerplanter.behavior;

import com.google.common.collect.ImmutableMap;
import com.gwallace.villagerplanter.VillagerPlanterMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import com.gwallace.villagerplanter.registry.ModVillagers;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class PlantSaplingBehavior extends Behavior<Villager> {

	private static final int SEARCH_RANGE = 16;
	private static final int SEARCH_HEIGHT = 4;
	private static final int SAPLING_SPACING = 4;
	private static final double DOOR_AVOIDANCE_SQ = 10.0 * 10.0;
	private static final double PATH_AVOIDANCE_SQ = 5.0 * 5.0;
	private static final float SPEED = 0.5F;
	private static final int PLANT_REACH = 2;
	private static final int COOLDOWN_TICKS = 200;
	private static final int MAX_DURATION = 600;
	// Expanded scan range to cover door avoidance from the edges of the search area
	private static final int OBSTACLE_SCAN_RANGE = SEARCH_RANGE + 10;
	private static final int OBSTACLE_SCAN_HEIGHT = SEARCH_HEIGHT + 4;

	private BlockPos targetPos;
	private long nextOkStartTime;

	public PlantSaplingBehavior() {
		super(ImmutableMap.of(
				MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT,
				MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
		), MAX_DURATION);
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		if (level.getGameTime() < nextOkStartTime) return false;
		if (!villager.getVillagerData().profession().is(ModVillagers.FORESTER_KEY)) {
			return false;
		}
		if (!level.getGameRules().get(GameRules.MOB_GRIEFING)) {
			VillagerPlanterMod.LOGGER.info("[villager-planter] Planting blocked: mob_griefing is off");
			return false;
		}

		ItemStack saplingStack = findSaplingInInventory(villager);
		if (saplingStack.isEmpty()) {
			VillagerPlanterMod.LOGGER.info("[villager-planter] Planting blocked: no saplings in inventory");
			return false;
		}

		targetPos = findPlantingSpot(level, villager, saplingStack);
		if (targetPos == null) {
			VillagerPlanterMod.LOGGER.info("[villager-planter] Planting blocked: no valid spot found");
		} else {
			VillagerPlanterMod.LOGGER.info("[villager-planter] Found planting spot at {}", targetPos);
		}
		return targetPos != null;
	}

	@Override
	protected void start(ServerLevel level, Villager villager, long gameTime) {
		VillagerPlanterMod.LOGGER.info("[villager-planter] Behavior started, walking to {}", targetPos);
		BehaviorUtils.setWalkAndLookTargetMemories(villager, targetPos, SPEED, PLANT_REACH - 1);
	}

	@Override
	protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
		return targetPos != null;
	}

	@Override
	protected void tick(ServerLevel level, Villager villager, long gameTime) {
		if (targetPos == null) return;

		if (villager.blockPosition().closerThan(targetPos, PLANT_REACH)) {
			placeSapling(level, villager);
			targetPos = null;
		} else {
			// Re-apply walk target if it was cleared by MoveToTargetSink (on arrival/path failure)
			// or overridden by SetWalkTargetFromBlockMemory (priority 2 redirects to job site)
			Optional<WalkTarget> currentWalk = villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET);
			if (currentWalk.isEmpty() || !currentWalk.get().getTarget().currentBlockPosition().equals(targetPos)) {
				VillagerPlanterMod.LOGGER.info("[villager-planter] Walk target lost, re-applying toward {}", targetPos);
				BehaviorUtils.setWalkAndLookTargetMemories(villager, targetPos, SPEED, PLANT_REACH - 1);
			}
		}
	}

	@Override
	protected void stop(ServerLevel level, Villager villager, long gameTime) {
		nextOkStartTime = gameTime + COOLDOWN_TICKS;
		targetPos = null;
	}

	private void placeSapling(ServerLevel level, Villager villager) {
		SimpleContainer inventory = villager.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(ItemTags.SAPLINGS) && stack.getItem() instanceof BlockItem blockItem) {
				Block saplingBlock = blockItem.getBlock();
				BlockState state = saplingBlock.defaultBlockState();
				if (level.getBlockState(targetPos).isAir() && state.canSurvive(level, targetPos)) {
					level.setBlockAndUpdate(targetPos, state);
					stack.shrink(1);
					level.playSound(null, targetPos,
							state.getSoundType().getPlaceSound(),
							net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
					VillagerPlanterMod.LOGGER.info("[villager-planter] Villager planted sapling at {}", targetPos);
					return;
				}
			}
		}
	}

	private static ItemStack findSaplingInInventory(Villager villager) {
		SimpleContainer inventory = villager.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(ItemTags.SAPLINGS)) {
				return stack;
			}
		}
		return ItemStack.EMPTY;
	}

	private static BlockPos findPlantingSpot(ServerLevel level, Villager villager, ItemStack saplingStack) {
		if (!(saplingStack.getItem() instanceof BlockItem blockItem)) return null;
		Block saplingBlock = blockItem.getBlock();
		BlockState saplingState = saplingBlock.defaultBlockState();
		BlockPos villagerPos = villager.blockPosition();

		// Pre-scan for obstacles (doors, paths) once to avoid re-scanning per candidate
		List<BlockPos> doorPositions = new ArrayList<>();
		List<BlockPos> pathPositions = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(
				villagerPos.offset(-OBSTACLE_SCAN_RANGE, -OBSTACLE_SCAN_HEIGHT, -OBSTACLE_SCAN_RANGE),
				villagerPos.offset(OBSTACLE_SCAN_RANGE, OBSTACLE_SCAN_HEIGHT, OBSTACLE_SCAN_RANGE))) {
			BlockState state = level.getBlockState(pos);
			if (state.is(BlockTags.DOORS)) {
				doorPositions.add(pos.immutable());
			} else if (state.is(Blocks.DIRT_PATH)) {
				pathPositions.add(pos.immutable());
			}
		}

		// Collect valid planting candidates
		List<BlockPos> candidates = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(
				villagerPos.offset(-SEARCH_RANGE, -SEARCH_HEIGHT, -SEARCH_RANGE),
				villagerPos.offset(SEARCH_RANGE, SEARCH_HEIGHT, SEARCH_RANGE))) {
			if (isValidPlantingSpot(level, pos, saplingState)) {
				candidates.add(pos.immutable());
			}
		}

		Collections.shuffle(candidates);

		for (BlockPos pos : candidates) {
			if (!hasSaplingTooClose(level, pos)
					&& !nearAny(pos, doorPositions, DOOR_AVOIDANCE_SQ)
					&& !nearAny(pos, pathPositions, PATH_AVOIDANCE_SQ)) {
				return pos;
			}
		}

		return null;
	}

	private static boolean isValidPlantingSpot(ServerLevel level, BlockPos pos, BlockState saplingState) {
		if (!level.getBlockState(pos).isAir()) return false;
		if (!level.getBlockState(pos.above()).isAir()) return false;
		return saplingState.canSurvive(level, pos);
	}

	private static boolean hasSaplingTooClose(ServerLevel level, BlockPos pos) {
		for (BlockPos nearby : BlockPos.betweenClosed(
				pos.offset(-SAPLING_SPACING, -2, -SAPLING_SPACING),
				pos.offset(SAPLING_SPACING, 2, SAPLING_SPACING))) {
			if (nearby.equals(pos)) continue;
			if (level.getBlockState(nearby).is(BlockTags.SAPLINGS)) {
				return true;
			}
		}
		return false;
	}

	private static boolean nearAny(BlockPos pos, List<BlockPos> obstacles, double distSq) {
		for (BlockPos obstacle : obstacles) {
			if (pos.distSqr(obstacle) <= distSq) {
				return true;
			}
		}
		return false;
	}
}
