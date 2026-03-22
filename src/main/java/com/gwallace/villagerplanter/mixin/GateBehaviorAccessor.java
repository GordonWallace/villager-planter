package com.gwallace.villagerplanter.mixin;

import net.minecraft.world.entity.ai.behavior.GateBehavior;
import net.minecraft.world.entity.ai.behavior.ShufflingList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GateBehavior.class)
public interface GateBehaviorAccessor {
	@Accessor("behaviors")
	ShufflingList<?> getBehaviors();
}
