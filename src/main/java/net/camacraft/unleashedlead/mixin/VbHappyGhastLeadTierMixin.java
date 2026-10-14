package net.camacraft.unleashedlead.mixin;

import net.camacraft.unleashedlead.leash.LeadTiers;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * An upgraded lead on Vanilla Backport's happy ghast snaps later, as on any other mob ({@code leash.LeadTiers}):
 * the ghast declares its own snap distance (16), which {@code VbLeashableMixin}'s answer for every other
 * pathfinding mob doesn't reach, and Vanilla Backport's leash tick snaps the ghast's lead at it. Applied only
 * with Vanilla Backport ({@code UlMixinPlugin}); does nothing if the ghast or the method moves.
 */
@Pseudo
@Mixin(targets = "com.blackgear.vanillabackport.common.level.entities.happyghast.HappyGhast", remap = false)
public abstract class VbHappyGhastLeadTierMixin {
    @Inject(method = "vb$leashSnapDistance", at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private void unleashedlead$upgradedLeadSnapsLater(CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(LeadTiers.vanillaBackportSnap((Entity) (Object) this, cir.getReturnValueD()));
    }
}
