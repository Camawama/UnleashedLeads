package net.camacraft.unleashedlead.mixin.compat;

import net.camacraft.unleashedlead.leash.VehicleLeash;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;

/**
 * Leads on Automobility's automobiles (and every vehicle that extends them, RIAutomobility's): the
 * leash state only. No injection into the automobile's own methods, so nothing here names an
 * Automobility type or method: the ticks, the pull and the save are {@code leash.VehicleLeashes}'.
 * Only applied when Automobility is installed ({@code UlMixinPlugin}).
 */
@Pseudo
@Mixin(targets = "io.github.foundationgames.automobility.entity.AutomobileEntity", remap = false)
public abstract class AutomobileLeashMixin implements VehicleLeash {
    @Unique
    @Nullable
    private Entity unleashedlead$holder;
    @Unique
    private int unleashedlead$delayedHolderId;
    @Unique
    @Nullable
    private CompoundTag unleashedlead$pendingTag;

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
}
