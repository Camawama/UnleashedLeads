package net.camacraft.unleashedlead.leash;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * Leash state for entities 1.20.1 has no leash for (boats, chest boats, bamboo rafts), mixed into
 * {@code Boat} by {@code mixin.BoatMixin} when Vanilla Backport isn't installed. Only
 * {@link BoatLeashes} and {@link Leashes} use these accessors; everything else goes through
 * {@link Leashes}, which also covers mobs and Vanilla Backport's model.
 */
public interface BoatLeash {
    /** The holder as last resolved on this side (may be null on a client until the holder is tracked). */
    @Nullable
    Entity ul$holder();

    void ul$setHolder(@Nullable Entity holder);

    /** Client: the holder's network id from the last sync, 0 for none. */
    int ul$delayedHolderId();

    void ul$setDelayedHolderId(int id);

    /** Server: the saved leash waiting to be re-attached after loading, or null. */
    @Nullable
    CompoundTag ul$pendingTag();

    void ul$setPendingTag(@Nullable CompoundTag tag);
}
