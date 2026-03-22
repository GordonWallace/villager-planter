package com.gwallace.villagerplanter.mixin;

import com.google.common.collect.ImmutableList;
import com.gwallace.villagerplanter.behavior.PlantSaplingBehavior;
import com.gwallace.villagerplanter.registry.ModVillagers;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.RunOne;
import net.minecraft.world.entity.ai.behavior.ShufflingList;
import net.minecraft.world.entity.ai.behavior.VillagerGoalPackages;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(VillagerGoalPackages.class)
public class VillagerGoalPackagesMixin {

	@SuppressWarnings({"unchecked", "rawtypes"})
	@Inject(method = "getWorkPackage", at = @At("RETURN"))
	private static void addPlantSaplingToWork(
			Holder<VillagerProfession> profession, float speed,
			CallbackInfoReturnable<ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>>> cir) {
		if (!profession.is(ModVillagers.FORESTER_KEY)) return;

		for (Pair<Integer, ? extends BehaviorControl<? super Villager>> pair : cir.getReturnValue()) {
			if (pair.getSecond() instanceof RunOne<?> runOne) {
				((ShufflingList) ((GateBehaviorAccessor) runOne).getBehaviors()).add(new PlantSaplingBehavior(), 10);
				return;
			}
		}
	}
}
