package net.camacraft.unleashedlead.leash;

import com.google.common.collect.MapMaker;
import net.camacraft.camafy.physics.PhysicsAspect;
import net.camacraft.camafy.physics.PhysicsOwnership;
import net.camacraft.unleashedlead.UlConfig;
import net.camacraft.unleashedlead.mixin.MobGoalsAccessor;
import net.camacraft.unleashedlead.mixin.PathfinderMobInvoker;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.vehicle.Boat;

import java.util.Map;

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
        LeashPhysics.Tick tick = LeashPhysics.tick(entity, holder, pull, true);
        // Vanilla Backport walks a mob after its holder only while the lead is slack: on a taut one it stood and was
        // dragged dead, so a led horse fell behind a walking player and held them back. A tamed one keeps walking (an
        // untamed one is dragged: wrangling it is work), and so does anything a vehicle tows (it runs rather than be dragged).
        if (pull && tick.taut() && walksWhenTaut(entity, holder)) followHolder(entity, holder);
        return pull && tick.taut();
    }

    /**
     * Vanilla Backport's walk after the holder, at the led mob's pace ({@link #followSpeed}): a slack lead's walk
     * ({@code VbLeashableMixin}) and a taut one's ({@link #vanillaBackportPull}). Vanilla's own (without Vanilla
     * Backport) is {@code PathfinderMobMixin}'s, at the same pace.
     */
    public static void followHolder(Entity entity, Entity holder) {
        if (!walksAfterLead(entity) || LeashChains.holderFollows(entity, holder)) return;
        ((MobGoalsAccessor) entity).unleashedlead$goalSelector().enableControlFlag(Goal.Flag.MOVE);
        walkToHolder(entity, holder);
    }

    /**
     * Whether {@code entity} is a mob that walks after whoever holds its lead: a pathfinding mob with AI that follows
     * leads at all (an allay doesn't), not riding anything, not a sitting pet, not moved by another mod.
     */
    public static boolean walksAfterLead(Entity entity) {
        if (!(entity instanceof PathfinderMob mob) || mob.isNoAi() || mob.isPassenger()) return false;
        if (mob instanceof TamableAnimal pet && pet.isInSittingPose()) return false;
        if (!((PathfinderMobInvoker) mob).unleashedlead$shouldStayCloseToLeashHolder()) return false;
        return !PhysicsOwnership.claimedByOther(mob, PhysicsAspect.MOTION, LeashPhysics.OWNER);
    }

    /**
     * Whether {@code entity} is led by nature: a tamed pet ({@code TamableAnimal}), a tamed horse, donkey, mule, llama or
     * camel, or anything in {@code physics.ledByNature} (an allay, a camel, Vanilla Backport's happy ghast). Such a mob
     * keeps its holder's pace and puts {@code ledWeight} of its weight on the lead; an untamed one walks at vanilla's pace
     * and weighs {@code untamedLedWeight} (the owner, 2026-10-13: "Only tamed animals on a lead should walk with the player").
     */
    public static boolean ledByNature(Entity entity) {
        if (entity instanceof TamableAnimal pet && pet.isTame()) return true;
        if (entity instanceof AbstractHorse horse && horse.isTamed()) return true;
        return UlConfig.ledByNature(entity.getType());
    }

    /** Whether a led mob keeps walking after {@code holder} on a taut lead: tamed, or towed by a vehicle (untamedTowedHurry). */
    static boolean walksWhenTaut(Entity entity, Entity holder) {
        if (ledByNature(entity)) return UlConfig.num(UlConfig.LED_HURRY) > 1.0;
        return holder instanceof VehicleLeash && UlConfig.num(UlConfig.UNTAMED_TOWED_HURRY) > 1.0;
    }

    /** A walking mob's top speed on ordinary ground, in blocks per tick, is about this times the square of its effective speed. */
    private static final double WALK_SPEED_PER_SQUARE = 1.6;
    /** The effective speed (attribute times modifier) at which a mob walks about as fast as a sprinting player (5.6 m/s). */
    private static final double SPRINT_PACE = 0.43;

    /**
     * The speed modifier a led mob walks after its holder at: its own leash pace ({@code base}, vanilla's
     * {@code followLeashSpeed}), sped up to the holder's speed plus a little to close any gap past where it stops, but
     * no more than {@code ledHurry} times its own pace. Tamed horses (and donkeys, mules, llamas, camels) keep up with a
     * sprinting player whatever their own speed, as a led horse does. Vanilla's pace alone left a cow at 1.3 m/s behind
     * a player walking at 4.3.
     */
    public static double followSpeed(PathfinderMob mob, Entity holder, double distance, double stop, double base) {
        // tamed: the holder's pace; untamed: vanilla's, unless a vehicle tows it (it runs rather than be dragged)
        boolean tamed = ledByNature(mob);
        double hurry = tamed ? UlConfig.num(UlConfig.LED_HURRY)
                : holder instanceof VehicleLeash ? UlConfig.num(UlConfig.UNTAMED_TOWED_HURRY) : 1.0;
        double attribute = mob.getAttributeValue(Attributes.MOVEMENT_SPEED);
        if (hurry <= 1.0 || !(attribute > 1.0E-4)) return base;
        Vec3 v = holderVelocity(holder);
        // the holder's pace, and the gap past the walk's stop closed over about two seconds
        double target = Math.sqrt(v.x * v.x + v.z * v.z) + Math.max(0.0, distance - stop) / 40.0;
        double needed = 1.1 * Math.sqrt(target / WALK_SPEED_PER_SQUARE) / attribute;
        double cap = base * hurry;
        if (tamed && mob instanceof AbstractHorse) cap = Math.max(cap, SPRINT_PACE / attribute);
        return Mth.clamp(needed, base, Math.max(base, cap));
    }

    /** Where each holder was on the last tick it was asked about, and how fast it moved then. */
    private static final Map<Entity, Pace> PACES = new MapMaker().weakKeys().makeMap();

    private record Pace(long tick, Vec3 at, Vec3 velocity) {}

    /** How fast {@code holder} moved over the last tick (blocks per tick, world axes): zero when not known, or after a jump of over 2 blocks. */
    static Vec3 holderVelocity(Entity holder) {
        long now = holder.level().getGameTime();
        Vec3 at = holder.position();
        Pace last = PACES.get(holder);
        if (last != null && last.tick == now) return last.velocity;
        Vec3 velocity = Vec3.ZERO;
        if (last != null && now - last.tick == 1L) {
            Vec3 moved = at.subtract(last.at);
            if (moved.lengthSqr() <= 4.0) velocity = moved;
        }
        PACES.put(holder, new Pace(now, at, velocity));
        return velocity;
    }

    /** A led horse that is eating or rearing stops when its holder walks off (vanilla only stopped one eating, past 6 blocks). */
    private static void rouse(Mob mob) {
        if (mob instanceof AbstractHorse horse) {
            if (horse.isEating()) horse.setEating(false);
            if (horse.isStanding()) horse.setStanding(false);
        }
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
        if (d <= stop) return;
        double speed = paceFor(mob, holder, d, stop, ((PathfinderMobInvoker) mob).unleashedlead$followLeashSpeed());
        if (mob.tickCount % WALK_RENEW_TICKS != 0 && !mob.getNavigation().isDone()) {
            mob.getNavigation().setSpeedModifier(speed); // keep pace between the path's renewals
            return;
        }
        Vec3 step = walkStep(holder, from, to, d, stop);
        mob.getNavigation().moveTo(mob.getX() + step.x, mob.getY() + step.y, mob.getZ() + step.z, speed);
    }

    /** How often a led mob's walk after its holder is renewed (ticks), unless its path is done first. */
    public static final int WALK_RENEW_TICKS = 5;

    /**
     * Where a led mob at {@code from} walks to, from where it stands: {@code stop} short of where its holder (now at
     * {@code to}, {@code d} away as the lead measures) will be when the walk is next renewed. Aimed at where the holder
     * is now, a mob keeping a car's pace reached the end of its path before the next one and turned round to step back
     * onto it: cows towed behind a car spun round every second or so.
     */
    public static Vec3 walkStep(Entity holder, Vec3 from, Vec3 to, double d, double stop) {
        Vec3 ahead = to.add(holderVelocity(holder).scale(WALK_RENEW_TICKS));
        Vec3 toward = ahead.subtract(from);
        double extra = toward.length() - to.subtract(from).length(); // how much further the holder will be
        return toward.normalize().scale(Math.max(d + extra - stop, 0.0));
    }

    /**
     * The pace a led mob walks after its holder at ({@link #followSpeed}); a horse eating or rearing is roused first
     * once the holder is a block past where the walk stops. Also {@code PathfinderMobMixin}'s, without Vanilla Backport.
     */
    public static double paceFor(PathfinderMob mob, Entity holder, double distance, double stop, double base) {
        if (distance > stop + 1.0) rouse(mob);
        return followSpeed(mob, holder, distance, stop, base);
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
