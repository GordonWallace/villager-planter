package com.gwallace.villagerplanter.mixin;

import com.gwallace.villagerplanter.VillagerPlanterMod;
import com.gwallace.villagerplanter.registry.ModVillagers;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Villager.class)
public class ForesterTradeMixin {

	/**
	 * When a forester acquires trades (on profession assignment or level-up),
	 * add our custom "buy saplings with emeralds" trade.
	 */
	@Inject(method = "updateTrades", at = @At("RETURN"))
	private void addForesterTrades(ServerLevel level, CallbackInfo ci) {
		Villager villager = (Villager) (Object) this;
		if (!villager.getVillagerData().profession().is(ModVillagers.FORESTER_KEY)) return;

		MerchantOffers offers = villager.getOffers();

		// Only add if not already present (avoid duplicates on restock/level-up)
		for (MerchantOffer offer : offers) {
			if (offer.getItemCostA().item().value() == Items.OAK_SAPLING
					&& offer.getResult().is(Items.EMERALD)) {
				return;
			}
		}

		// Forester buys 1 oak sapling from the player for 1 emerald.
		// maxUses starts at 0 — dynamically set when the trade screen opens
		// based on how many emeralds the villager actually has in inventory.
		offers.add(new MerchantOffer(
				new ItemCost(Items.OAK_SAPLING, 1),
				new ItemStack(Items.EMERALD, 1),
				0,    // uses
				0,    // maxUses (updated dynamically in startTrading)
				0.05f // priceMultiplier
		));
		VillagerPlanterMod.LOGGER.info("[villager-planter] Added forester sapling trade");
	}

	/**
	 * When a player opens the trade screen, update the forester trade's
	 * maxUses to match the number of emeralds in the villager's real inventory.
	 */
	@Inject(method = "startTrading", at = @At("HEAD"))
	private void updateForesterTradeStock(Player player, CallbackInfo ci) {
		Villager villager = (Villager) (Object) this;
		if (!villager.getVillagerData().profession().is(ModVillagers.FORESTER_KEY)) return;

		int emeraldCount = countEmeraldsInInventory(villager);

		for (MerchantOffer offer : villager.getOffers()) {
			if (offer.getItemCostA().item().value() == Items.OAK_SAPLING
					&& offer.getResult().is(Items.EMERALD)) {
				((MerchantOfferAccessor) (Object) offer).setMaxUses(emeraldCount);
				((MerchantOfferAccessor) (Object) offer).setUses(0);
				VillagerPlanterMod.LOGGER.info(
						"[villager-planter] Trade stock updated: {} emeralds available", emeraldCount);
				break;
			}
		}
	}

	/**
	 * After a forester trade completes, move items between the virtual trade
	 * system and the villager's real SimpleContainer inventory:
	 * - Remove 1 emerald (the "payment" to the player)
	 * - Add 1 sapling (the item the player traded in)
	 */
	@Inject(method = "rewardTradeXp", at = @At("TAIL"))
	private void handleForesterTradeInventory(MerchantOffer offer, CallbackInfo ci) {
		Villager villager = (Villager) (Object) this;
		if (!villager.getVillagerData().profession().is(ModVillagers.FORESTER_KEY)) return;

		// Only handle our specific sapling-for-emerald trade
		if (offer.getItemCostA().item().value() != Items.OAK_SAPLING
				|| !offer.getResult().is(Items.EMERALD)) {
			return;
		}

		SimpleContainer inventory = villager.getInventory();

		// Remove 1 emerald from real inventory (villager "spent" it)
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(Items.EMERALD)) {
				stack.shrink(1);
				break;
			}
		}

		// Add the sapling to real inventory (villager "received" it for planting)
		inventory.addItem(new ItemStack(Items.OAK_SAPLING, 1));

		VillagerPlanterMod.LOGGER.info("[villager-planter] Trade completed: -1 emerald, +1 oak sapling");
	}

	@Unique
	private static int countEmeraldsInInventory(Villager villager) {
		SimpleContainer inventory = villager.getInventory();
		int count = 0;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(Items.EMERALD)) {
				count += stack.getCount();
			}
		}
		return count;
	}
}
