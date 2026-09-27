package net.camacraft.unleashedlead.leash;

import net.camacraft.unleashedlead.network.LeashLinkMessage;
import net.camacraft.unleashedlead.network.UlNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Unleashed Lead's own leash model for boats (used only without Vanilla Backport): attach and
 * detach with a sync message, save and load under {@code unleashedlead:Leash}, and the
 * server-side upkeep a mob gets from vanilla's {@code Mob.tickLeash} (re-attach after loading,
 * let go of a dead holder).
 */
public final class BoatLeashes {
    public static final String SAVE_KEY = "unleashedlead:Leash";
    /** Ticks a loaded leash may wait for its holder before it gives up and drops the lead. */
    private static final int RESTORE_PATIENCE = 100;

    private BoatLeashes() {}

    @Nullable
    public static Entity holder(Entity boat, BoatLeash leash) {
        Entity holder = leash.ul$holder();
        if (holder == null && boat.level().isClientSide && leash.ul$delayedHolderId() != 0) {
            holder = boat.level().getEntity(leash.ul$delayedHolderId());
            if (holder != null) leash.ul$setHolder(holder);
        }
        return holder;
    }

    public static void attach(Entity boat, BoatLeash leash, Entity holder) {
        leash.ul$setHolder(holder);
        leash.ul$setPendingTag(null);
        if (!boat.level().isClientSide) UlNetwork.CHANNEL.toTracking(boat, new LeashLinkMessage(boat.getId(), holder.getId()));
    }

    public static void detach(Entity boat, BoatLeash leash, boolean dropItem) {
        if (leash.ul$holder() == null && leash.ul$pendingTag() == null) return;
        leash.ul$setHolder(null);
        leash.ul$setPendingTag(null);
        leash.ul$setDelayedHolderId(0);
        if (!boat.level().isClientSide) {
            if (dropItem) boat.spawnAtLocation(Items.LEAD);
            UlNetwork.CHANNEL.toTracking(boat, new LeashLinkMessage(boat.getId(), 0));
        }
    }

    /** A player started tracking the boat: tell them who holds it. */
    public static void sendTo(ServerPlayer player, Entity boat, BoatLeash leash) {
        Entity holder = leash.ul$holder();
        if (holder != null) UlNetwork.CHANNEL.toPlayer(player, new LeashLinkMessage(boat.getId(), holder.getId()));
    }

    /** Client: a sync message arrived. */
    public static void applySync(Entity boat, BoatLeash leash, int holderId) {
        leash.ul$setDelayedHolderId(holderId);
        leash.ul$setHolder(holderId == 0 ? null : boat.level().getEntity(holderId));
    }

    public static void save(BoatLeash leash, CompoundTag tag) {
        Entity holder = leash.ul$holder();
        if (holder != null) {
            CompoundTag leashTag = new CompoundTag();
            if (holder instanceof HangingEntity hanging) {
                BlockPos pos = hanging.getPos();
                leashTag.putInt("X", pos.getX());
                leashTag.putInt("Y", pos.getY());
                leashTag.putInt("Z", pos.getZ());
            } else {
                leashTag.putUUID("UUID", holder.getUUID());
            }
            tag.put(SAVE_KEY, leashTag);
        } else if (leash.ul$pendingTag() != null) {
            tag.put(SAVE_KEY, leash.ul$pendingTag().copy());
        }
    }

    public static void load(BoatLeash leash, CompoundTag tag) {
        leash.ul$setPendingTag(tag.contains(SAVE_KEY, 10) ? tag.getCompound(SAVE_KEY).copy() : null);
    }

    /** Server, start of the boat's tick: re-attach a loaded leash, let go of a dead holder. */
    public static void serverTick(Entity boat, BoatLeash leash) {
        if (!(boat.level() instanceof ServerLevel level)) return;
        CompoundTag pending = leash.ul$pendingTag();
        if (pending != null) {
            Entity holder = null;
            if (pending.hasUUID("UUID")) {
                UUID uuid = pending.getUUID("UUID");
                holder = level.getEntity(uuid);
            } else if (pending.contains("X", 99) && pending.contains("Y", 99) && pending.contains("Z", 99)) {
                holder = LeashFenceKnotEntity.getOrCreateKnot(level, NbtUtils.readBlockPos(pending));
            }
            if (holder != null) {
                attach(boat, leash, holder);
            } else if (boat.tickCount > RESTORE_PATIENCE) {
                leash.ul$setPendingTag(null);
                boat.spawnAtLocation(Items.LEAD);
            }
        }
        Entity holder = leash.ul$holder();
        if (holder != null && (!boat.isAlive() || !holder.isAlive() || holder.level() != boat.level())) {
            detach(boat, leash, true);
        }
    }
}
