package net.camacraft.unleashedlead.mixin;

import net.camacraft.unleashedlead.leash.LeashTicker;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Two small additions to every mob's leash:
 * <ul>
 *   <li>after {@code tickLeash} (whoever implemented it: vanilla, {@code PathfinderMobMixin} or
 *   Vanilla Backport) hand over to {@link LeashTicker#afterMobTickLeash}, which gives
 *   non-pathfinding mobs a pull at all, applies the fall-damage and Full Stop extras, and undoes
 *   whatever the lead did to the velocity of a mob whose motion another mod owns;</li>
 *   <li>save a lead held by something that is neither living nor a knot (a boat: mob-to-boat
 *   leads), which vanilla's save silently drops.</li>
 * </ul>
 */
@Mixin(Mob.class)
public abstract class MobMixin {
    @Shadow
    public abstract Entity getLeashHolder();

    @Unique
    private Vec3 unleashedlead$motionBeforeLeash = Vec3.ZERO;

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Mob;tickLeash()V"))
    private void unleashedlead$beforeTickLeash(CallbackInfo ci) {
        this.unleashedlead$motionBeforeLeash = ((Mob) (Object) this).getDeltaMovement();
    }

    @Inject(method = "tick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Mob;tickLeash()V", shift = At.Shift.AFTER))
    private void unleashedlead$afterTickLeash(CallbackInfo ci) {
        LeashTicker.afterMobTickLeash((Mob) (Object) this, this.unleashedlead$motionBeforeLeash);
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void unleashedlead$saveOtherHolder(CompoundTag tag, CallbackInfo ci) {
        Entity holder = this.getLeashHolder();
        if (holder == null || holder instanceof LivingEntity || holder instanceof HangingEntity || tag.contains("Leash")) {
            return;
        }
        CompoundTag leash = new CompoundTag();
        leash.putUUID("UUID", holder.getUUID());
        tag.put("Leash", leash);
    }
}
