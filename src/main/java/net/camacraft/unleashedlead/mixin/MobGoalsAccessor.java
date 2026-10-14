package net.camacraft.unleashedlead.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A mob's goal selector, so a led mob's movement goals can be let run while it walks after its holder ({@code leash.LeashTicker}). */
@Mixin(Mob.class)
public interface MobGoalsAccessor {
    @Accessor("goalSelector")
    GoalSelector unleashedlead$goalSelector();
}
