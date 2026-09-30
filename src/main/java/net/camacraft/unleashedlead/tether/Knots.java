package net.camacraft.unleashedlead.tether;

import net.camacraft.camafy.compat.vs.Ships;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.level.ChunkPos;
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
 *
 * <p>It also says when a lead saved to a fence on a ship can be tied back ({@link #presence}), and
 * which knots on ships were only unloaded with their ship ({@link #unloadedFromShip}): a lead to
 * one of those waits for the ship instead of dropping.
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

    /** Where the fence a lead was saved to stands, for tying the lead back after a load ({@link #presence}). */
    public enum Presence {
        /** In the world (or no Valkyrien Skies): its knot is found or made at once, as vanilla does. */
        WORLD,
        /** On a loaded ship, with the fence's chunk and its entities loaded: its knot is found or made now. */
        HERE,
        /** On a ship that isn't loaded, or whose fence's chunk (or the entities in it) hasn't loaded yet: wait. */
        AWAY,
        /** On a ship that no longer exists (deleted, or disassembled back into the world). */
        GONE
    }

    /**
     * Where the fence at {@code pos}, which a lead was saved to, stands (see {@link Presence}). A
     * knot on a ship is found or made only once the ship and the fence's chunk are loaded. Vanilla
     * makes one at once, which on a ship puts it in a shipyard chunk that may not be loaded yet,
     * where nothing can place it in the world: Valkyrien Skies loads a ship and its chunks by a
     * player's distance to the ship, apart from the world chunks the mob tied to it stands in, so
     * the mob often loads first. Never loads anything.
     */
    public static Presence presence(ServerLevel level, BlockPos pos) {
        if (!Ships.inShipyard(level, pos)) return Presence.WORLD;
        if (Ships.managingAny(level, pos) == null) return Presence.GONE;
        if (Ships.managing(level, pos) == null) return Presence.AWAY; // the ship isn't loaded
        if (!level.isLoaded(pos) || !level.areEntitiesLoaded(ChunkPos.asLong(pos))) return Presence.AWAY;
        return Presence.HERE;
    }

    /**
     * The fence of {@code holder} if it is a knot on a Valkyrien Skies ship's fence that was only
     * unloaded with its chunk (the ship went out of range), not broken, cut or taken down; else
     * null. What was tied to it keeps its lead as a saved one, tied back once the ship is loaded
     * again ({@link #presence}), rather than dropping it: a ship's chunks unload by distance to the
     * ship, so a mob tied to it can go on ticking in the world after its knot is gone.
     */
    @Nullable
    public static BlockPos unloadedFromShip(Entity holder) {
        Entity.RemovalReason reason = holder.getRemovalReason();
        if (!(holder instanceof LeashFenceKnotEntity knot) || reason == null || reason.shouldDestroy()) return null;
        return Ships.inShipyard(knot.level(), knot.getPos()) ? knot.getPos() : null;
    }

    public static void clear() {
        INDEX.clear();
    }
}
