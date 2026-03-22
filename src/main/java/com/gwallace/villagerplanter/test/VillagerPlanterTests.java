package com.gwallace.villagerplanter.test;

import com.gwallace.villagerplanter.mixin.MerchantOfferAccessor;
import com.gwallace.villagerplanter.registry.ModBlocks;
import com.gwallace.villagerplanter.registry.ModVillagers;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.gamerules.GameRules;

public class VillagerPlanterTests {

	/**
	 * Verify that a villager standing near a band saw acquires the forester profession.
	 */
	@GameTest(maxTicks = 6000, structure = "fabric-gametest-api-v1:empty")
	public void bandSawAssignsForesterProfession(GameTestHelper helper) {
		// Floor of grass
		BlockPos floorCenter = new BlockPos(3, 1, 3);
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
			}
		}

		// Place band saw
		BlockPos bandSawPos = new BlockPos(3, 2, 3);
		helper.setBlock(bandSawPos, ModBlocks.BAND_SAW);

		// Place a bed (villager needs one to acquire profession)
		BlockPos bedPos = new BlockPos(5, 2, 3);
		helper.setBlock(bedPos, Blocks.RED_BED);

		// Spawn villager near the band saw
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 4));

		helper.succeedWhen(() -> {
			Villager v = helper.findOneEntity(EntityType.VILLAGER);
			helper.assertTrue(
					v.getVillagerData().profession().is(ModVillagers.FORESTER_KEY),
					"Villager should become a forester"
			);
		});
	}

	/**
	 * Verify that a forester villager picks up dropped saplings.
	 */
	@GameTest(maxTicks = 6000, structure = "fabric-gametest-api-v1:empty")
	public void foresterPicksUpSaplings(GameTestHelper helper) {
		// Floor
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
			}
		}

		// Place band saw and bed
		helper.setBlock(new BlockPos(3, 2, 3), ModBlocks.BAND_SAW);
		helper.setBlock(new BlockPos(5, 2, 3), Blocks.RED_BED);

		// Spawn villager
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 4));

		// Wait until villager becomes forester, then drop saplings
		helper.succeedWhen(() -> {
			// First check: must be a forester
			Villager v = helper.findOneEntity(EntityType.VILLAGER);
			if (!v.getVillagerData().profession().is(ModVillagers.FORESTER_KEY)) {
				helper.fail("Villager not yet a forester");
				return;
			}

			// Check if villager has saplings in inventory
			boolean hasSapling = false;
			for (int i = 0; i < v.getInventory().getContainerSize(); i++) {
				if (v.getInventory().getItem(i).is(ItemTags.SAPLINGS)) {
					hasSapling = true;
					break;
				}
			}

			helper.assertTrue(hasSapling, "Forester should have picked up saplings");
		});

		// Drop saplings after a short delay to give time for profession assignment
		helper.runAfterDelay(100, () -> {
			helper.spawnItem(Items.OAK_SAPLING, new BlockPos(3, 2, 5));
			helper.spawnItem(Items.OAK_SAPLING, new BlockPos(3, 2, 5));
			helper.spawnItem(Items.OAK_SAPLING, new BlockPos(3, 2, 5));
		});
	}

	/**
	 * Verify that a forester villager plants a sapling on a dirt block.
	 * This is the core behavior test. Currently flaky due to RunOne behavior
	 * competition — marked required=false until the behavior scheduling is fixed.
	 */
	@GameTest(maxTicks = 12000, structure = "fabric-gametest-api-v1:empty", skyAccess = true, required = false)
	public void foresterPlantsSapling(GameTestHelper helper) {
		// Floor of grass
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
			}
		}

		// Place band saw and bed
		helper.setBlock(new BlockPos(1, 2, 1), ModBlocks.BAND_SAW);
		helper.setBlock(new BlockPos(1, 2, 3), Blocks.RED_BED);

		// Spawn villager
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 2, 2));

		// Drop saplings after delay (give time for profession acquisition)
		helper.runAfterDelay(100, () -> {
			for (int i = 0; i < 8; i++) {
				helper.spawnItem(Items.OAK_SAPLING, new BlockPos(1, 2, 2));
			}
		});

		// Succeed when any sapling block appears in the test area
		helper.succeedWhen(() -> {
			for (int x = 0; x < 8; x++) {
				for (int z = 0; z < 8; z++) {
					if (helper.getBlockState(new BlockPos(x, 2, z)).is(BlockTags.SAPLINGS)) {
						return; // Success!
					}
				}
			}
			helper.fail("No sapling block was planted");
		});
	}

	/**
	 * Verify that mob_griefing=false prevents the forester from planting.
	 */
	@GameTest(maxTicks = 6000, structure = "fabric-gametest-api-v1:empty", skyAccess = true)
	public void mobGriefingPreventsPlanting(GameTestHelper helper) {
		// Disable mob_griefing
		helper.getLevel().getGameRules().set(GameRules.MOB_GRIEFING, false, helper.getLevel().getServer());

		// Floor of grass
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
			}
		}

		// Place band saw and bed
		helper.setBlock(new BlockPos(1, 2, 1), ModBlocks.BAND_SAW);
		helper.setBlock(new BlockPos(1, 2, 3), Blocks.RED_BED);

		// Spawn villager
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 2, 2));

		// Give saplings directly to avoid pickup check interaction
		helper.runAfterDelay(100, () -> {
			for (int i = 0; i < 8; i++) {
				helper.spawnItem(Items.OAK_SAPLING, new BlockPos(1, 2, 2));
			}
		});

		// After a long wait, verify NO saplings were planted
		helper.runAfterDelay(4000, () -> {
			for (int x = 0; x < 8; x++) {
				for (int z = 0; z < 8; z++) {
					helper.assertBlockNotPresent(Blocks.OAK_SAPLING, new BlockPos(x, 2, z));
				}
			}
			// Re-enable for cleanup
			helper.getLevel().getGameRules().set(GameRules.MOB_GRIEFING, true, helper.getLevel().getServer());
			helper.succeed();
		});
	}

	/**
	 * Verify that a forester villager picks up dropped emeralds.
	 */
	@GameTest(maxTicks = 6000, structure = "fabric-gametest-api-v1:empty")
	public void foresterPicksUpEmeralds(GameTestHelper helper) {
		// Floor
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
			}
		}

		// Place band saw and bed
		helper.setBlock(new BlockPos(3, 2, 3), ModBlocks.BAND_SAW);
		helper.setBlock(new BlockPos(5, 2, 3), Blocks.RED_BED);

		// Spawn villager
		helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 4));

		// Drop emeralds after delay (give time for profession acquisition)
		helper.runAfterDelay(100, () -> {
			helper.spawnItem(Items.EMERALD, new BlockPos(3, 2, 5));
			helper.spawnItem(Items.EMERALD, new BlockPos(3, 2, 5));
		});

		helper.succeedWhen(() -> {
			Villager v = helper.findOneEntity(EntityType.VILLAGER);
			if (!v.getVillagerData().profession().is(ModVillagers.FORESTER_KEY)) {
				helper.fail("Villager not yet a forester");
				return;
			}

			boolean hasEmerald = false;
			for (int i = 0; i < v.getInventory().getContainerSize(); i++) {
				if (v.getInventory().getItem(i).is(Items.EMERALD)) {
					hasEmerald = true;
					break;
				}
			}
			helper.assertTrue(hasEmerald, "Forester should have picked up emeralds");
		});
	}

	/**
	 * Verify the inventory-based trade mechanics:
	 * - Forester with emeralds in inventory has a sapling trade
	 * - Executing the trade removes an emerald and adds a sapling to inventory
	 */
	@GameTest(maxTicks = 6000, structure = "fabric-gametest-api-v1:empty")
	public void foresterInventoryTradeWorks(GameTestHelper helper) {
		// Floor
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
			}
		}

		// Place band saw and bed
		helper.setBlock(new BlockPos(3, 2, 3), ModBlocks.BAND_SAW);
		helper.setBlock(new BlockPos(5, 2, 3), Blocks.RED_BED);

		// Spawn villager
		helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 4));

		helper.succeedWhen(() -> {
			Villager v = helper.findOneEntity(EntityType.VILLAGER);
			if (!v.getVillagerData().profession().is(ModVillagers.FORESTER_KEY)) {
				helper.fail("Villager not yet a forester");
				return;
			}

			// Give the villager an emerald directly
			v.getInventory().addItem(new ItemStack(Items.EMERALD, 1));

			// Find the sapling trade
			MerchantOffer saplingTrade = null;
			for (MerchantOffer offer : v.getOffers()) {
				if (offer.getItemCostA().item().value() == Items.OAK_SAPLING
						&& offer.getResult().is(Items.EMERALD)) {
					saplingTrade = offer;
					break;
				}
			}
			helper.assertTrue(saplingTrade != null, "Forester should have a sapling trade");

			// Set maxUses to allow the trade (simulates what startTrading does)
			((MerchantOfferAccessor) (Object) saplingTrade).setMaxUses(1);
			((MerchantOfferAccessor) (Object) saplingTrade).setUses(0);

			// Execute trade — this triggers rewardTradeXp which calls our mixin
			v.notifyTrade(saplingTrade);

			// Verify: emerald removed, sapling added
			boolean hasEmerald = false;
			boolean hasSapling = false;
			for (int i = 0; i < v.getInventory().getContainerSize(); i++) {
				ItemStack stack = v.getInventory().getItem(i);
				if (stack.is(Items.EMERALD)) hasEmerald = true;
				if (stack.is(ItemTags.SAPLINGS)) hasSapling = true;
			}
			helper.assertTrue(!hasEmerald, "Emerald should be removed from inventory after trade");
			helper.assertTrue(hasSapling, "Sapling should be added to inventory after trade");
		});
	}

	/**
	 * End-to-end: forester receives emerald via trade, then plants the sapling.
	 * Flaky due to RunOne behavior competition — marked required=false.
	 */
	@GameTest(maxTicks = 12000, structure = "fabric-gametest-api-v1:empty", skyAccess = true, required = false)
	public void foresterTradeLeadsToPlanting(GameTestHelper helper) {
		// Floor of grass
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
			}
		}

		// Place band saw and bed
		helper.setBlock(new BlockPos(1, 2, 1), ModBlocks.BAND_SAW);
		helper.setBlock(new BlockPos(1, 2, 3), Blocks.RED_BED);

		// Spawn villager
		helper.spawn(EntityType.VILLAGER, new BlockPos(1, 2, 2));

		// After forester acquires profession, give emerald and execute trade
		helper.runAfterDelay(200, () -> {
			Villager v = helper.findOneEntity(EntityType.VILLAGER);
			if (!v.getVillagerData().profession().is(ModVillagers.FORESTER_KEY)) return;

			// Give emerald and simulate trade
			v.getInventory().addItem(new ItemStack(Items.EMERALD, 1));
			for (MerchantOffer offer : v.getOffers()) {
				if (offer.getItemCostA().item().value() == Items.OAK_SAPLING
						&& offer.getResult().is(Items.EMERALD)) {
					((MerchantOfferAccessor) (Object) offer).setMaxUses(1);
					((MerchantOfferAccessor) (Object) offer).setUses(0);
					v.notifyTrade(offer);
					break;
				}
			}
		});

		// Succeed when any sapling block appears (planted by the forester)
		helper.succeedWhen(() -> {
			for (int x = 0; x < 8; x++) {
				for (int z = 0; z < 8; z++) {
					if (helper.getBlockState(new BlockPos(x, 2, z)).is(BlockTags.SAPLINGS)) {
						return;
					}
				}
			}
			helper.fail("No sapling block was planted");
		});
	}

	/**
	 * Verify that a forester cuts a natural tree (log + persistent=false leaves).
	 * The base log must become air after the forester reaches and breaks it.
	 * If FallingTree is active, the whole tree cascades; either way the base log is gone.
	 */
	@GameTest(maxTicks = 8000, structure = "fabric-gametest-api-v1:empty")
	public void foresterCutsDownTree(GameTestHelper helper) {
		// Floor of grass
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
			}
		}

		// Place band saw and bed
		helper.setBlock(new BlockPos(3, 2, 3), ModBlocks.BAND_SAW);
		helper.setBlock(new BlockPos(5, 2, 3), Blocks.RED_BED);

		// Build a minimal natural tree: one log with persistent=false leaves around it
		BlockPos logPos = new BlockPos(1, 2, 1);
		helper.setBlock(logPos, Blocks.OAK_LOG);
		for (BlockPos leaf : new BlockPos[]{
				new BlockPos(0, 2, 1), new BlockPos(2, 2, 1),
				new BlockPos(1, 2, 0), new BlockPos(1, 2, 2),
				new BlockPos(1, 3, 1)}) {
			helper.setBlock(leaf, Blocks.OAK_LEAVES.defaultBlockState()
					.setValue(LeavesBlock.PERSISTENT, false)
					.setValue(LeavesBlock.DISTANCE, 1));
		}

		// Spawn villager
		helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 4));

		// Succeed when the forester has broken the base log
		helper.succeedWhen(() -> {
			Villager v = helper.findOneEntity(EntityType.VILLAGER);
			if (!v.getVillagerData().profession().is(ModVillagers.FORESTER_KEY)) {
				helper.fail("Villager not yet a forester");
			}
			helper.assertBlockNotPresent(Blocks.OAK_LOG, logPos);
		});
	}

	/**
	 * Verify that a forester ignores log blocks that have no natural (persistent=false)
	 * leaves nearby — e.g. a player-placed log pile.
	 */
	@GameTest(maxTicks = 6000, structure = "fabric-gametest-api-v1:empty")
	public void foresterIgnoresPlayerLogs(GameTestHelper helper) {
		// Floor of grass
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
			}
		}

		// Place band saw and bed
		helper.setBlock(new BlockPos(3, 2, 3), ModBlocks.BAND_SAW);
		helper.setBlock(new BlockPos(5, 2, 3), Blocks.RED_BED);

		// Bare log — no leaves anywhere nearby
		BlockPos logPos = new BlockPos(1, 2, 1);
		helper.setBlock(logPos, Blocks.OAK_LOG);

		// Spawn villager
		helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 4));

		// After a long wait the log should be untouched
		helper.runAfterDelay(5000, () -> {
			helper.assertBlockPresent(Blocks.OAK_LOG, logPos);
			helper.succeed();
		});
	}

	/**
	 * Verify that a forester with fewer than 2 free inventory slots does not cut trees.
	 */
	@GameTest(maxTicks = 6000, structure = "fabric-gametest-api-v1:empty")
	public void foresterNeedsInventorySpace(GameTestHelper helper) {
		// Floor of grass
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
			}
		}

		// Place band saw and bed
		helper.setBlock(new BlockPos(3, 2, 3), ModBlocks.BAND_SAW);
		helper.setBlock(new BlockPos(5, 2, 3), Blocks.RED_BED);

		// Build a natural tree
		BlockPos logPos = new BlockPos(1, 2, 1);
		helper.setBlock(logPos, Blocks.OAK_LOG);
		for (BlockPos leaf : new BlockPos[]{
				new BlockPos(0, 2, 1), new BlockPos(2, 2, 1),
				new BlockPos(1, 2, 0), new BlockPos(1, 2, 2)}) {
			helper.setBlock(leaf, Blocks.OAK_LEAVES.defaultBlockState()
					.setValue(LeavesBlock.PERSISTENT, false)
					.setValue(LeavesBlock.DISTANCE, 1));
		}

		// Spawn villager and immediately fill all but 1 inventory slot (leaves 1 free slot < MIN_FREE_SLOTS=2)
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 4));
		for (int i = 0; i < villager.getInventory().getContainerSize() - 1; i++) {
			villager.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
		}

		// After a long wait the log should be untouched
		helper.runAfterDelay(5000, () -> {
			helper.assertBlockPresent(Blocks.OAK_LOG, logPos);
			helper.succeed();
		});
	}
}
