package net.camacraft.unleashedlead.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.camacraft.unleashedlead.leash.LeadTiers;
import net.camacraft.unleashedlead.leash.LeashTicker;
import net.camacraft.unleashedlead.tether.Knots;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.game.ClientboundSetEntityLinkPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Small additions to every mob's leash:
 * <ul>
 *   <li>after {@code tickLeash} (whoever implemented it: vanilla, {@code PathfinderMobMixin} or
 *   Vanilla Backport) hand over to {@link LeashTicker#afterMobTickLeash}, which gives
 *   non-pathfinding mobs a pull at all, applies the fall-damage and Full Stop extras, and undoes
 *   whatever the lead did to the velocity of a mob whose motion another mod owns;</li>
 *   <li>save a lead held by something that is neither living nor a knot (a boat: mob-to-boat
 *   leads), which vanilla's save silently drops;</li>
 *   <li>a lead to a knot on a Valkyrien Skies ship's fence survives the ship going out of range
 *   and coming back: it waits for the ship as a saved lead instead of dropping, and is tied back
 *   once the ship is loaded (see {@link Knots#presence}). Vanilla Backport's leash tick for
 *   pathfinding mobs runs vanilla's {@code Mob.tickLeash} first, so this applies with it too.</li>
 *   <li>a lead taken off with no item (creative, a dimension change) takes its upgrade tier with it
 *   ({@code leash.LeadTiers}); one that drops as an item takes it along that way.</li>
 * </ul>
 */
@Mixin(Mob.class)
public abstract class MobMixin {
    @Shadow
    public abstract Entity getLeashHolder();

    @Shadow
    @Nullable
    private Entity leashHolder;
    @Shadow
    @Nullable
    private CompoundTag leashInfoTag;

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

    /**
     * A lead saved to a fence on a ship (loaded with the mob, or kept below when its knot unloaded)
     * is tied back only once the ship and the fence's chunk are loaded, and waits for that as long
     * as it takes: vanilla makes a knot at once, which on a ship went into a shipyard chunk that
     * wasn't loaded yet, with no place in the world. It is dropped when the ship no longer exists,
     * after the 100 ticks vanilla waits for a holder it can't find. A fence in the world goes
     * vanilla's way.
     */
    @Inject(method = "restoreLeashFromSave", at = @At("HEAD"), cancellable = true)
    private void unleashedlead$restoreToShipFence(CallbackInfo ci) {
        Mob self = (Mob) (Object) this;
        CompoundTag saved = this.leashInfoTag;
        if (saved == null || saved.hasUUID("UUID") || !(self.level() instanceof ServerLevel level)) return;
        if (!saved.contains("X", 99) || !saved.contains("Y", 99) || !saved.contains("Z", 99)) return;
        BlockPos fence = NbtUtils.readBlockPos(saved);
        Knots.Presence presence = Knots.presence(level, fence);
        if (presence == Knots.Presence.WORLD) return;
        ci.cancel();
        if (presence == Knots.Presence.HERE) {
            self.setLeashedTo(Knots.getOrCreate(level, fence), true); // finds knots on ships too
        } else if (presence == Knots.Presence.GONE && self.tickCount > 100) {
            self.spawnAtLocation(Items.LEAD);
            this.leashInfoTag = null;
        }
    }

    @Inject(method = "dropLeash", at = @At("HEAD"))
    private void unleashedlead$forgetLeadTier(boolean broadcast, boolean dropItem, CallbackInfo ci) {
        Mob self = (Mob) (Object) this;
        if (!dropItem && this.leashHolder != null && !self.level().isClientSide && !LeadTiers.keeping()) LeadTiers.clear(self);
    }

    /**
     * Vanilla drops the lead, and a lead item, the moment its holder is gone. A knot on a ship's
     * fence is also "gone" when its ship goes out of range and Valkyrien Skies unloads the ship's
     * chunks, while the mob tied to it, in the world, can go on ticking. That lead is kept as the
     * saved lead the mob would have written ({@code Leash: X, Y, Z}) and tied back when the ship
     * is loaded again ({@link #unleashedlead$restoreToShipFence}). Anything else is dropped as before.
     */
    @WrapOperation(method = "tickLeash", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Mob;dropLeash(ZZ)V"))
    private void unleashedlead$keepLeadToUnloadedShip(Mob self, boolean broadcast, boolean dropItem, Operation<Void> original) {
        BlockPos fence = this.leashHolder == null || !self.isAlive() ? null : Knots.unloadedFromShip(this.leashHolder);
        if (fence == null) {
            original.call(self, broadcast, dropItem);
            return;
        }
        this.leashHolder = null;
        this.leashInfoTag = NbtUtils.writeBlockPos(fence);
        if (self.level() instanceof ServerLevel level) {
            level.getChunkSource().broadcast(self, new ClientboundSetEntityLinkPacket(self, null)); // clients let go too
        }
    }
}
