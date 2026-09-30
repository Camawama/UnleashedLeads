package net.camacraft.unleashedlead.leash;

import net.camacraft.camafy.physics.PhysicsAspect;
import net.camacraft.camafy.physics.PhysicsOwnership;
import net.camacraft.unleashedlead.UlConfig;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.vehicle.Boat;

/**
 * The per-tick entry points the mixins call. Kept here, not in the mixins, so the rules read in
 * one place:
 * <ul>
 *   <li>{@code PathfinderMob}: {@code mixin.PathfinderMobMixin} replaces vanilla's pull (and its
 *   10-block snap) with {@link LeashPhysics}, keeping vanilla's walk-to-the-holder behaviour.</li>
 *   <li>Any other {@code Mob}: {@link #afterMobTickLeash}; 1.20.1 gives them no pull at all.</li>
 *   <li>Boats: {@link #boatServerTick} for upkeep and the snap, {@link #boatMotion} for the pull,
 *   on whichever side moves the boat.</li>
 * </ul>
 * With Vanilla Backport installed its leash tick runs {@code PathfinderMob}s and boats; the pull
 * comes back here through {@link #vanillaBackportPull} (its elastic hook), and the extras (fall
 * reset, Full Stop claim) through {@link #afterMobTickLeash}. See {@link Leashes#ownsPhysics}.
 */
public final class LeashTicker {
    private LeashTicker() {}

    /**
     * Server, right after {@code Mob.tickLeash} ran (vanilla's, ours or Vanilla Backport's), with
     * the mob's velocity from just before it.
     */
    public static void afterMobTickLeash(Mob mob, Vec3 motionBefore) {
        if (mob.level().isClientSide) return;
        Entity holder = mob.getLeashHolder();
        if (holder == null || !mob.isAlive()) return;
        boolean ours = Leashes.ownsPhysics(mob);
        if (!ours && PhysicsOwnership.claimedByOther(mob, PhysicsAspect.MOTION, LeashPhysics.OWNER)) {
            // Another mod owns this mob's motion but someone else's leash code (Vanilla Backport,
            // which doesn't know about claims) just pulled it: take that back, and the walk too.
            mob.setDeltaMovement(motionBefore);
            stopLeashWalk(mob, holder);
        }
        if (mob instanceof PathfinderMob && ours) return; // fully handled in tickLeash
        // non-pathfinding mobs (ours), or anything Vanilla Backport pulled (extras only)
        LeashPhysics.tick(mob, holder, ours);
    }

    /**
     * Vanilla Backport's elastic-pull hook, answered with this mod's physics (see
     * {@code mixin.VbLeashableMixin}). True when the lead is taut and pulled, which is what makes
     * it run its own after-pull step (the horse and camel reactions, the slow-fall reset).
     */
    public static boolean vanillaBackportPull(Entity entity, Entity holder) {
        boolean pull = !(entity.getControllingPassenger() instanceof Player); // never a player-steered ride
        LeashPhysics.Tick tick = LeashPhysics.tick(entity, holder, pull);
        return pull && tick.taut();
    }

    /**
     * Walks a pathfinding mob toward its holder's world position, like vanilla's leash (stopping 2
     * blocks short, times the lead's length scale: 2/16 of a block on a lead at 1/16).
     */
    public static void walkToHolder(Entity entity, Entity holder) {
        if (!(entity instanceof PathfinderMob mob) || !mob.onGround() && !mob.isInWater()) return;
        if (PhysicsOwnership.claimedByOther(mob, PhysicsAspect.MOTION, LeashPhysics.OWNER)) return;
        Vec3 to = LeashGeometry.knownCentre(holder);
        if (to == null) return; // a knot on a ship that isn't loaded: nowhere to walk to yet
        Vec3 from = LeashGeometry.centre(mob);
        double d = LeashGeometry.span(mob, from, holder, to);
        double stop = LeashPhysics.WALK_STOP * LeadScale.reach(mob, holder);
        if (d <= stop || (mob.tickCount % 5 != 0 && !mob.getNavigation().isDone())) return;
        Vec3 step = to.subtract(from).normalize().scale(d - stop);
        mob.getNavigation().moveTo(mob.getX() + step.x, mob.getY() + step.y, mob.getZ() + step.z, 1.0);
    }

    /** Stops a path the lead started (one ending near the holder), leaving any other path alone. */
    private static void stopLeashWalk(Mob mob, Entity holder) {
        Path path = mob.getNavigation().getPath();
        if (path == null || path.getTarget() == null) return;
        Vec3 target = Vec3.atCenterOf(path.getTarget());
        if (target.distanceTo(LeashGeometry.centre(holder)) <= 4.0) mob.getNavigation().stop();
    }

    /** Server, start of a boat's tick (our model only): re-attach after loading, drop for a dead holder, snap. */
    public static void boatServerTick(Boat boat, BoatLeash leash) {
        BoatLeashes.serverTick(boat, leash);
        Entity holder = leash.ul$holder();
        if (holder == null) return;
        // The pull itself happens in boatMotion on the side that moves the boat; here only the snap,
        // which the server decides even for a boat a player drives on their own client.
        if (!boat.isControlledByLocalInstance()) {
            LeashPhysics.tick(boat, holder, false);
        }
    }

    /**
     * Just before a boat moves, on the side that simulates it: the server for an empty or
     * mob-crewed boat, the driving player's client for a player-driven one.
     */
    public static void boatMotion(Boat boat) {
        Entity holder = Leashes.holder(boat);
        if (holder == null || !Leashes.ownsPhysics(boat)) return;
        boolean playerDriven = boat.getControllingPassenger() instanceof Player;
        // Bedrock and Java 1.21.6: a lead has no effect on a boat a player is steering.
        boolean pull = !playerDriven || UlConfig.on(UlConfig.PULL_PLAYER_BOATS);
        LeashPhysics.tick(boat, holder, pull);
    }
}
