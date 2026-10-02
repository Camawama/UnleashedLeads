package net.camacraft.unleashedlead.leash;

import com.google.common.collect.MapMaker;
import net.camacraft.camafy.physics.PhysicsAspect;
import net.camacraft.camafy.physics.PhysicsOwnership;
import net.camacraft.unleashedlead.UlConfig;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Chains of mobs: which end of a lead between two mobs is dragged.
 *
 * <p>A lead between a player (or a fence knot) and a mob only ever pulls the mob. Between two mobs
 * it pulls either end: a chain of mobs follows whichever of them is being moved, and the mob tied
 * to it is just another link. So for each lead between two mobs ({@link #role}), the end nearer
 * whatever drives the chain stands still for this lead and the far end is pulled toward it, the
 * way a mob follows the player holding it. What drives a chain:
 * <ul>
 *   <li>a mob held by a player, a knot or anything else that isn't a mob (one step away from what
 *   holds it);</li>
 *   <li>a mob that a lead can't move at all ({@link #immovable}): one another mod moves (Physgun
 *   Unbound's grab, an MKW kart), a sitting pet, a ride a player steers, a mob with no AI (vanilla
 *   never moves those), a mob riding something.</li>
 * </ul>
 * The distance counts leads along the chain, both ways, from the nearest driver. A lead between
 * two mobs equally far from any driver (a free chain wandering about, or a tug of war) pulls both
 * ends toward each other, each by the other's share of their mass, so a pig tied to a cow mostly
 * goes where the cow goes.
 *
 * <p>Holders don't know what they hold, so every leash tick between two mobs is recorded here
 * ({@link #record}) and the chain is read from the last two ticks of records; each mob's own holder
 * is read live. A chain's distances are worked out once a tick and kept for its other leads. Server
 * thread only.
 */
public final class LeashChains {
    /** What one lead between two mobs does this tick. */
    public enum Role {
        /** The leashed entity is pulled toward its holder (the only role outside mob chains). */
        ENTITY,
        /** The holder is pulled toward the entity it holds: the entity's side drives the chain. */
        HOLDER,
        /** Both ends are pulled toward each other, split by mass. */
        BOTH
    }

    /** Most mobs one chain is searched through. */
    private static final int MAX_CHAIN = 256;
    /** No driver anywhere along the chain. */
    private static final int FAR = Integer.MAX_VALUE;

    /** Holder -> the mobs seen tied to it, with the tick each was last seen. Weak both ways. */
    private static final Map<Entity, Map<Entity, Long>> CHILDREN = new MapMaker().weakKeys().makeMap();
    /** Each chain mob's distance from the nearest driver, as worked out on {@link Distance#tick}. */
    private static final Map<Entity, Distance> DISTANCES = new MapMaker().weakKeys().makeMap();

    private record Distance(long tick, int steps) {}

    private LeashChains() {}

    /** Whether this lead is between two mobs a chain treats as links (server, mob chains on). */
    public static boolean chained(Entity entity, Entity holder) {
        return entity instanceof Mob && holder instanceof Mob && !entity.level().isClientSide
                && UlConfig.on(UlConfig.MOB_CHAINS);
    }

    /** Notes that {@code entity} is tied to {@code holder} this tick (any leash tick between two mobs). */
    public static void record(Entity entity, Entity holder) {
        if (!(entity instanceof Mob) || !(holder instanceof Mob) || entity.level().isClientSide) return;
        CHILDREN.computeIfAbsent(holder, h -> new WeakHashMap<>()).put(entity, entity.level().getGameTime());
    }

    /** Which end of the lead between {@code entity} and its {@code holder} is pulled this tick. */
    public static Role role(Entity entity, Entity holder) {
        if (!chained(entity, holder)) return Role.ENTITY;
        long now = entity.level().getGameTime();
        int e = steps(entity, now), h = steps(holder, now);
        if (h < e) return Role.ENTITY;
        if (e < h) return Role.HOLDER;
        return Role.BOTH;
    }

    /**
     * Whether {@code holder} comes to {@code entity} rather than the other way round this tick
     * (the entity's side of the chain is being moved): the entity then doesn't walk after its holder.
     */
    public static boolean holderFollows(Entity entity, Entity holder) {
        return role(entity, holder) == Role.HOLDER;
    }

    /**
     * Whether a lead can't move this entity at all: another mod owns its motion, it sits, a player
     * steers it, vanilla doesn't move it (no AI) or it rides something (its vehicle moves it).
     */
    public static boolean immovable(Entity entity) {
        if (PhysicsOwnership.claimedByOther(entity, PhysicsAspect.MOTION, LeashPhysics.OWNER)) return true;
        if (entity instanceof TamableAnimal tamable && tamable.isInSittingPose()) return true;
        if (entity instanceof Mob mob && mob.isNoAi()) return true;
        return entity.isPassenger() || entity.getControllingPassenger() instanceof Player;
    }

    /** How many leads {@code mob} is from the nearest driver of its chain ({@link #FAR}: none). */
    private static int steps(Entity mob, long now) {
        Distance known = DISTANCES.get(mob);
        if (known != null && known.tick == now) return known.steps;
        List<Entity> chain = chainOf(mob, now);
        Map<Entity, Integer> steps = new HashMap<>();
        ArrayDeque<Entity> queue = new ArrayDeque<>();
        // drivers first (0), then mobs held by something that isn't a mob (1): a breadth-first walk
        // from both keeps every distance the shortest
        for (Entity e : chain) {
            if (immovable(e)) {
                steps.put(e, 0);
                queue.add(e);
            }
        }
        for (Entity e : chain) {
            if (!steps.containsKey(e) && heldByAnchor(e)) {
                steps.put(e, 1);
                queue.add(e);
            }
        }
        while (!queue.isEmpty()) {
            Entity e = queue.poll();
            int next = steps.get(e) + 1;
            for (Entity n : neighbours(e, now)) {
                Integer s = steps.get(n);
                if (s == null || s > next) {
                    steps.put(n, next);
                    queue.add(n);
                }
            }
        }
        for (Entity e : chain) DISTANCES.put(e, new Distance(now, steps.getOrDefault(e, FAR)));
        return steps.getOrDefault(mob, FAR);
    }

    /** Held by a player, a knot, a boat: something that isn't pulled back by what it holds. */
    private static boolean heldByAnchor(Entity e) {
        Entity holder = Leashes.holder(e);
        return holder != null && !(holder instanceof Mob);
    }

    /** Every mob tied to {@code start} through leads between mobs, start included. */
    private static List<Entity> chainOf(Entity start, long now) {
        List<Entity> chain = new ArrayList<>();
        Map<Entity, Boolean> seen = new HashMap<>();
        ArrayDeque<Entity> queue = new ArrayDeque<>();
        queue.add(start);
        seen.put(start, true);
        while (!queue.isEmpty() && chain.size() < MAX_CHAIN) {
            Entity e = queue.poll();
            chain.add(e);
            for (Entity n : neighbours(e, now)) {
                if (seen.put(n, true) == null) queue.add(n);
            }
        }
        return chain;
    }

    /** The mobs one lead away from {@code e}: its holder if that is a mob, and the mobs it holds. */
    private static List<Entity> neighbours(Entity e, long now) {
        List<Entity> out = new ArrayList<>(2);
        Entity holder = Leashes.holder(e);
        if (holder instanceof Mob && holder.isAlive() && holder.level() == e.level()) out.add(holder);
        Map<Entity, Long> children = CHILDREN.get(e);
        if (children != null) {
            Iterator<Map.Entry<Entity, Long>> it = children.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Entity, Long> c = it.next();
                Entity child = c.getKey();
                if (now - c.getValue() > 2L || !child.isAlive() || Leashes.holder(child) != e) {
                    it.remove();
                } else {
                    out.add(child);
                }
            }
        }
        return out;
    }

    /**
     * The chain from {@code mob} up through its holders while they are mobs: {@code mob}, its
     * holder, that one's holder... The last one is held by something that isn't a mob, or by
     * nothing. Null if the walk loops or runs past {@link #MAX_CHAIN}.
     */
    @Nullable
    public static List<Entity> pathUp(Entity mob) {
        List<Entity> path = new ArrayList<>();
        Entity cursor = mob;
        while (cursor instanceof Mob) {
            if (path.contains(cursor) || path.size() >= MAX_CHAIN) return null;
            path.add(cursor);
            Entity next = Leashes.holder(cursor);
            if (!(next instanceof Mob)) break;
            cursor = next;
        }
        return path;
    }
}
