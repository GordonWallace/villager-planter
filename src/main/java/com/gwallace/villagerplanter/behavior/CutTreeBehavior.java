package com.gwallace.villagerplanter.behavior;

import com.google.common.collect.ImmutableMap;
import com.gwallace.villagerplanter.VillagerPlanterMod;
import com.gwallace.villagerplanter.registry.ModVillagers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;

public class CutTreeBehavior extends Behavior<Villager> {

	private static final int SEARCH_RANGE = 16;
	private static final int SEARCH_Y_UP = 8;
	private static final int SEARCH_Y_DOWN = 2;
	private static final int LEAF_CHECK_RADIUS = 6;
	private static final int MAX_TREE_LOGS = 64;
	private static final int MIN_FREE_SLOTS = 2;
	private static final float SPEED = 0.5F;
	private static final int REACH = 3;
	private static final int COOLDOWN_TICKS = 400;
	private static final int MAX_DURATION = 800;

	private BlockPos targetLog;
	private Set<BlockPos> treeLogsToBreak;
	private long nextOkStartTime;

	public CutTreeBehavior() {
		super(ImmutableMap.of(
				MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT,
				MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
		), MAX_DURATION);
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		if (level.getGameTime() < nextOkStartTime) return false;
		if (!villager.getVillagerData().profession().is(ModVillagers.FORESTER_KEY)) return false;
		if (!level.getGameRules().get(GameRules.MOB_GRIEFING)) return false;
		if (countFreeSlots(villager) < MIN_FREE_SLOTS) return false;
		targetLog = findTreeBase(level, villager);
		if (targetLog == null) return false;
		// Find all logs in the tree
		treeLogsToBreak = collectAllTreeLogs(level, targetLog);
		return !treeLogsToBreak.isEmpty();
	}

	@Override
	protected void start(ServerLevel level, Villager villager, long gameTime) {
		BehaviorUtils.setWalkAndLookTargetMemories(villager, targetLog, SPEED, REACH - 1);
	}

	@Override
	protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
		return targetLog != null;
	}

	@Override
	protected void tick(ServerLevel level, Villager villager, long gameTime) {
		if (targetLog == null || treeLogsToBreak == null || treeLogsToBreak.isEmpty()) {
			targetLog = null;
			treeLogsToBreak = null;
			return;
		}

		// Remove already-broken logs (in case other sources broke them)
		treeLogsToBreak.removeIf(pos -> !level.getBlockState(pos).is(BlockTags.LOGS_THAT_BURN));

		if (villager.blockPosition().closerThan(targetLog, REACH)) {
			VillagerPlanterMod.LOGGER.info("[villager-planter] Villager cutting tree at {} ({} logs)", targetLog, treeLogsToBreak.size());
			// Break all logs in the tree (from top to bottom by breaking in descending Y order)
			treeLogsToBreak.stream()
				.sorted((a, b) -> Integer.compare(b.getY(), a.getY())) // descending Y order (top to bottom)
				.forEach(pos -> level.destroyBlock(pos, true, villager));
			targetLog = null;
			treeLogsToBreak = null;
		} else {
			// Re-apply walk target if cleared or overridden (same pattern as PlantSaplingBehavior)
			Optional<WalkTarget> currentWalk = villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET);
			if (currentWalk.isEmpty() || !currentWalk.get().getTarget().currentBlockPosition().equals(targetLog)) {
				BehaviorUtils.setWalkAndLookTargetMemories(villager, targetLog, SPEED, REACH - 1);
			}
		}
	}

	@Override
	protected void stop(ServerLevel level, Villager villager, long gameTime) {
		nextOkStartTime = gameTime + COOLDOWN_TICKS;
		targetLog = null;
		treeLogsToBreak = null;
	}

	private static BlockPos findTreeBase(ServerLevel level, Villager villager) {
		BlockPos villagerPos = villager.blockPosition();

		// Collect log blocks in range that have natural (non-persistent) leaves nearby
		List<BlockPos> logCandidates = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(
				villagerPos.offset(-SEARCH_RANGE, -SEARCH_Y_DOWN, -SEARCH_RANGE),
				villagerPos.offset(SEARCH_RANGE, SEARCH_Y_UP, SEARCH_RANGE))) {
			if (level.getBlockState(pos).is(BlockTags.LOGS_THAT_BURN)
					&& hasNaturalLeafNearby(level, pos)) {
				logCandidates.add(pos.immutable());
			}
		}
		if (logCandidates.isEmpty()) return null;

		// Randomise so foresters spread out across multiple trees
		Collections.shuffle(logCandidates);

		for (BlockPos seed : logCandidates) {
			if (!level.getBlockState(seed).is(BlockTags.LOGS_THAT_BURN)) continue;
			// BFS to find the whole connected-log component, return the lowest log
			return floodFillLowestLog(level, seed);
		}
		return null;
	}

	// BFS flood-fill across 6-face-connected LOGS_THAT_BURN blocks; returns all members.
	private static Set<BlockPos> collectAllTreeLogs(ServerLevel level, BlockPos seed) {
		Set<BlockPos> visited = new HashSet<>();
		Queue<BlockPos> queue = new ArrayDeque<>();
		queue.add(seed);
		visited.add(seed);

		while (!queue.isEmpty() && visited.size() <= MAX_TREE_LOGS) {
			BlockPos current = queue.poll();
			for (Direction dir : Direction.values()) {
				BlockPos neighbor = current.relative(dir);
				if (!visited.contains(neighbor)
						&& level.getBlockState(neighbor).is(BlockTags.LOGS_THAT_BURN)) {
					visited.add(neighbor);
					queue.add(neighbor);
				}
			}
		}
		return visited;
	}

	// BFS flood-fill across 6-face-connected LOGS_THAT_BURN blocks; returns the lowest-Y member.
	private static BlockPos floodFillLowestLog(ServerLevel level, BlockPos seed) {
		Set<BlockPos> visited = new HashSet<>();
		Queue<BlockPos> queue = new ArrayDeque<>();
		queue.add(seed);
		visited.add(seed);
		BlockPos lowest = seed;

		while (!queue.isEmpty() && visited.size() <= MAX_TREE_LOGS) {
			BlockPos current = queue.poll();
			if (current.getY() < lowest.getY()) {
				lowest = current;
			}
			for (Direction dir : Direction.values()) {
				BlockPos neighbor = current.relative(dir);
				if (!visited.contains(neighbor)
						&& level.getBlockState(neighbor).is(BlockTags.LOGS_THAT_BURN)) {
					visited.add(neighbor);
					queue.add(neighbor);
				}
			}
		}
		return lowest;
	}

	// Returns true if at least one non-persistent (naturally grown) leaf exists within radius.
	private static boolean hasNaturalLeafNearby(ServerLevel level, BlockPos pos) {
		for (BlockPos nearby : BlockPos.betweenClosed(
				pos.offset(-LEAF_CHECK_RADIUS, -LEAF_CHECK_RADIUS, -LEAF_CHECK_RADIUS),
				pos.offset(LEAF_CHECK_RADIUS, LEAF_CHECK_RADIUS, LEAF_CHECK_RADIUS))) {
			BlockState state = level.getBlockState(nearby);
			if (state.is(BlockTags.LEAVES)
					&& state.hasProperty(LeavesBlock.PERSISTENT)
					&& !state.getValue(LeavesBlock.PERSISTENT)) {
				return true;
			}
		}
		return false;
	}

	private static int countFreeSlots(Villager villager) {
		SimpleContainer inventory = villager.getInventory();
		int free = 0;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			if (inventory.getItem(i).isEmpty()) {
				free++;
			}
		}
		return free;
	}
}
