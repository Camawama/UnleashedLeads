package net.camacraft.unleashedlead.tether;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Finding the knot on a fence, on the server, wherever the fence is.
 *
 * <p>Vanilla finds a knot with an entity query around the fence ({@code getOrCreateKnot}). That
 * fails for a fence on a Valkyrien Skies ship: VS rewrites every entity query over a shipyard box
 * into the ship's box in the world, so the knot, which lives in the shipyard, is never found and a
 * second knot is made on the same fence. This index is fed by the level's own join and leave
 * events instead, so it finds knots on ships and in the world alike, without loading anything.
 */
public final class Knots {
    private static final Map<Level, Map<BlockPos, LeashFenceKnotEntity>> INDEX = new WeakHashMap<>();

    private Knots() {}

    /** A knot entered a server level (spawned, or re-made by a mob loading with its lead). */
    public static void onJoin(Entity entity) {
        if (!(entity instanceof LeashFenceKnotEntity knot) || knot.level().isClientSide) return;
        Map<BlockPos, LeashFenceKnotEntity> byPos = INDEX.computeIfAbsent(knot.level(), l -> new HashMap<>());
        LeashFenceKnotEntity current = byPos.get(knot.getPos());
        if (current == null || current.isRemoved()) byPos.put(knot.getPos().immutable(), knot);
    }

    /** A knot left its level (broken, taken down or unloaded). */
    public static void onLeave(Entity entity) {
        if (!(entity instanceof LeashFenceKnotEntity knot) || knot.level().isClientSide) return;
        Map<BlockPos, LeashFenceKnotEntity> byPos = INDEX.get(knot.level());
        if (byPos != null) byPos.remove(knot.getPos(), knot);
    }

    /** The live knot on the fence at {@code pos}, or null. Never loads a chunk. */
    @Nullable
    public static LeashFenceKnotEntity find(Level level, BlockPos pos) {
        Map<BlockPos, LeashFenceKnotEntity> byPos = INDEX.get(level);
        LeashFenceKnotEntity indexed = byPos == null ? null : byPos.get(pos);
        if (indexed != null && !indexed.isRemoved()) return indexed;
        // not indexed (the client, or a knot from before this mod saw the level): vanilla's search
        for (LeashFenceKnotEntity knot : level.getEntitiesOfClass(LeashFenceKnotEntity.class, new AABB(pos).inflate(1.0))) {
            if (knot.getPos().equals(pos) && !knot.isRemoved()) return knot;
        }
        return null;
    }

    /** The knot on the fence at {@code pos}, made if there is none (server side). */
    public static LeashFenceKnotEntity getOrCreate(Level level, BlockPos pos) {
        LeashFenceKnotEntity knot = find(level, pos);
        if (knot != null) return knot;
        knot = new LeashFenceKnotEntity(level, pos.immutable());
        level.addFreshEntity(knot);
        return knot;
    }

    public static void clear() {
        INDEX.clear();
    }
}
