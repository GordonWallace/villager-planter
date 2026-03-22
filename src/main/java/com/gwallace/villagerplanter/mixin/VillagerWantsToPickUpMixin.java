package com.gwallace.villagerplanter.mixin;

import com.gwallace.villagerplanter.registry.ModVillagers;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gamerules.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Villager.class)
public class VillagerWantsToPickUpMixin {

	@Inject(method = "wantsToPickUp", at = @At("HEAD"), cancellable = true)
	private void allowSaplingPickup(ServerLevel level, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		if (stack.is(ItemTags.SAPLINGS)) {
			Villager villager = (Villager) (Object) this;
			if (villager.getVillagerData().profession().is(ModVillagers.FORESTER_KEY)
					&& level.getGameRules().get(GameRules.MOB_GRIEFING)) {
				cir.setReturnValue(true);
			}
		}
	}
}
