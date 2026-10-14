package net.camacraft.unleashedlead.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Forge's persistent data on an entity, read without creating it: {@code getPersistentData()} makes an
 * empty tag on first use, which every entity would then save. {@code leash.LeadTiers} asks every entity a
 * player starts seeing.
 */
@Mixin(Entity.class)
public interface EntityDataAccessor {
    @Accessor(value = "persistentData", remap = false)
    @Nullable
    CompoundTag unleashedlead$persistentData();
}
