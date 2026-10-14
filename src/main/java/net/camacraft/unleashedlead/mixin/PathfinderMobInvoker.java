package net.camacraft.unleashedlead.mixin;

import net.minecraft.world.entity.PathfinderMob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * A pathfinding mob's own leash answers, for {@code leash.LeashTicker}'s walk after the holder (which runs in Vanilla
 * Backport's leash tick too, where {@code PathfinderMobMixin} isn't applied): whether it follows a lead at all (an allay
 * doesn't) and its own pace on one (a llama's is twice the usual).
 */
@Mixin(PathfinderMob.class)
public interface PathfinderMobInvoker {
    @Invoker("shouldStayCloseToLeashHolder")
    boolean unleashedlead$shouldStayCloseToLeashHolder();

    @Invoker("followLeashSpeed")
    double unleashedlead$followLeashSpeed();
}
