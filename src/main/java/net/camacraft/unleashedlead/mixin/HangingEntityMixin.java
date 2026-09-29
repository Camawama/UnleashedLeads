package net.camacraft.unleashedlead.mixin;

import net.camacraft.unleashedlead.leash.LeashRules;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Knots check their fence every tick instead of every 100 ticks, so a knot breaks the moment its
 * fence is broken, pushed or replaced; and an abandoned knot disappears (see
 * {@link LeashRules#knotTick}). Other hanging entities are untouched.
 *
 * <p>Nothing is cancelled: once {@code knotTick} has removed the knot, the rest of vanilla's tick
 * has nothing left to do for it (its own survival check skips removed entities), and other mods
 * inject into this same method (Pehkui at its head), so it always runs to its end.
 */
@Mixin(HangingEntity.class)
public abstract class HangingEntityMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void unleashedlead$knotTick(CallbackInfo ci) {
        if ((Object) this instanceof LeashFenceKnotEntity knot && !knot.level().isClientSide) LeashRules.knotTick(knot);
    }
}
