package net.camacraft.unleashedlead.mixin;

import net.camacraft.unleashedlead.leash.BoatLeash;
import net.camacraft.unleashedlead.leash.BoatLeashes;
import net.camacraft.unleashedlead.leash.LeashTicker;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Leads on boats, chest boats and bamboo rafts (Java 1.21, Bedrock): the {@link BoatLeash} state,
 * its save data, the server upkeep and the pull, which is applied after the boat's own buoyancy
 * and friction and before it moves, on whichever side moves it.
 *
 * <p>Not applied when Vanilla Backport is installed: it leashes boats itself.
 */
@Mixin(Boat.class)
public abstract class BoatMixin extends Entity implements BoatLeash {
    @Unique
    @Nullable
    private Entity unleashedlead$holder;
    @Unique
    private int unleashedlead$delayedHolderId;
    @Unique
    @Nullable
    private CompoundTag unleashedlead$pendingTag;

    protected BoatMixin(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Override
    public @Nullable Entity ul$holder() {
        return unleashedlead$holder;
    }

    @Override
    public void ul$setHolder(@Nullable Entity holder) {
        unleashedlead$holder = holder;
    }

    @Override
    public int ul$delayedHolderId() {
        return unleashedlead$delayedHolderId;
    }

    @Override
    public void ul$setDelayedHolderId(int id) {
        unleashedlead$delayedHolderId = id;
    }

    @Override
    public @Nullable CompoundTag ul$pendingTag() {
        return unleashedlead$pendingTag;
    }

    @Override
    public void ul$setPendingTag(@Nullable CompoundTag tag) {
        unleashedlead$pendingTag = tag;
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void unleashedlead$serverTick(CallbackInfo ci) {
        if (!this.level().isClientSide) LeashTicker.boatServerTick((Boat) (Object) this, this);
    }

    @Inject(method = "tick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/vehicle/Boat;floatBoat()V", shift = At.Shift.AFTER))
    private void unleashedlead$pull(CallbackInfo ci) {
        LeashTicker.boatMotion((Boat) (Object) this);
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void unleashedlead$save(CompoundTag tag, CallbackInfo ci) {
        BoatLeashes.save(this, tag);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void unleashedlead$load(CompoundTag tag, CallbackInfo ci) {
        BoatLeashes.load(this, tag);
    }
}
