package net.camacraft.unleashedlead.leash;

import com.google.common.collect.MapMaker;
import net.camacraft.camafy.compat.gravity.Gravity;
import net.camacraft.camafy.compat.gravity.GravityFrame;
import net.camacraft.camafy.compat.scale.EntityScale;
import net.camacraft.camafy.physics.PhysicsAspect;
import net.camacraft.camafy.physics.PhysicsOwnership;
import net.camacraft.camafy.weight.Weights;
import net.camacraft.unleashedlead.UlConfig;
import net.camacraft.unleashedlead.UlSounds;
import net.camacraft.unleashedlead.UnleashedLead;
import net.camacraft.unleashedlead.compat.VanillaBackport;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * The leash as a force: Bedrock's elastic lead, which Java 1.21.6 also moved to.
 *
 * <p>Within the free length (5 blocks) the lead does nothing and pathfinding mobs simply walk
 * after their holder, as in vanilla. Past it the lead is taut:
 * <ul>
 *   <li>a <b>spring</b> pulls toward the holder, harder the further it is stretched (capped);</li>
 *   <li>a <b>damper</b> removes part of the speed the entity has <em>away</em> from the holder
 *   (relative to the holder's own motion, so a moving holder drags it along).</li>
 * </ul>
 * Speed across the rope is never touched, so a mob hanging from a knot over a drop swings like a
 * pendulum and a boat on a lead arcs around its holder. At the snap distance (12) the lead breaks.
 *
 * <p>Resized entities (Pehkui, which Genesis bundles) lead like any other. Distances are
 * {@link LeashGeometry#span}s in real blocks (centre to centre, less what an enlarged body adds).
 * The lead is sized for its two ends ({@link LeadScale}, {@code rope.RopeScale}): between a player
 * and a pig both shrunk to 1/16 it is a lead at 1/16, free for 5/16 of a block and snapping past
 * 12/16; with a normal-sized end (or a fence knot) it keeps the normal lengths. The spring
 * ({@code stiffness} per block of stretch) needs no scaling: the stretch is already in the lead's
 * size. {@code maxPull} stays in real blocks per tick: it is a cap against throwing an entity, not a
 * length, and a small lead held by something fast (a tiny rider on a horse) should still keep up.
 * A size mod scales how far an entity's own velocity moves it (Pehkui moves a pig shrunk to 1/16 by
 * 1/16 of its {@code deltaMovement}), so the pull is converted with Camafy's
 * {@link EntityScale#motion}: across the ground it is in real blocks per tick, up and down in the
 * entity's own units ({@link #toLeadUnits}). Without that a tiny pig was pulled 16 times too weakly,
 * fell behind a walking player and snapped its lead, and a big one was pulled 4 times too hard.
 *
 * <p>Velocities go through Camafy's {@link Gravity} so the pull is right in Gravity Unbound's
 * rotated frames, and nothing is applied to an entity whose MOTION another mod has claimed
 * (MKW Mobility karts and drivers, Grapple Unbound players). While the rope is catching, the entity
 * gets a short G_FORCE claim so Full Stop doesn't turn the catch into g-force effects; IMPACT is
 * deliberately left alone, so a mob swung into a wall still takes Full Stop's impact damage.
 */
public final class LeashPhysics {
    /** Owner id for Camafy physics claims. */
    public static final ResourceLocation OWNER = UnleashedLead.id("leash");
    /** How long a G_FORCE claim outlives the last taut tick. */
    private static final int CLAIM_TICKS = 4;
    /** Degrees per tick a dragged entity turns toward its holder. */
    private static final float TURN_RATE = 8.0F;
    /**
     * The smallest motion scale the pull makes up for in full: an entity Pehkui shrinks further
     * still follows, only more slowly, rather than being given an absurd {@code deltaMovement}.
     */
    private static final double MIN_MOTION_SCALE = 1.0 / 64.0;

    /** Last tick's centres of both ends per leashed entity, for their velocities. Identity-keyed. */
    private static final Map<Entity, HolderTrack> TRACKS = new MapMaker().weakKeys().makeMap();

    private record HolderTrack(Entity holder, long tick, Vec3 centre, Vec3 entityCentre) {}

    private LeashPhysics() {}

    /**
     * What one tick of leash found. {@code reach} is the lead's length scale ({@link LeadScale#reach}):
     * 1 for a normal lead, 1/16 for one between two ends shrunk to 1/16.
     */
    public record Tick(double distance, boolean taut, boolean snapped, double reach) {
        static final Tick NONE = new Tick(0.0, false, false, 1.0);

        /** The distance as it would be on a normal lead (vanilla's thresholds, such as a horse's 6, compare against this). */
        public double normalDistance() {
            return distance / reach;
        }
    }

    /** How far vanilla walks a leashed mob toward its holder: to 2 blocks short of it, on a normal lead. */
    public static final double WALK_STOP = 2.0;

    /**
     * The free length of a normal-sized lead on {@code entity}: the config's (or Vanilla Backport's
     * for its happy ghast). {@link #freeLength(Entity, Entity)} is the one for an actual lead.
     */
    public static double freeLength(Entity entity) {
        double vb = VanillaBackport.present() ? VanillaBackport.elasticDistance(entity) : Double.NaN;
        return Double.isNaN(vb) ? UlConfig.num(UlConfig.FREE_LENGTH) : Math.max(vb - 1.0, 1.0);
    }

    /** The snap distance of a normal-sized lead on {@code entity} (see {@link #freeLength(Entity)}). */
    public static double snapDistance(Entity entity) {
        double vb = VanillaBackport.present() ? VanillaBackport.snapDistance(entity) : Double.NaN;
        return Double.isNaN(vb) ? UlConfig.num(UlConfig.SNAP_DISTANCE) : vb;
    }

    /** The free length of the lead between {@code entity} and {@code holder} (null: a fence), sized for both ends. */
    public static double freeLength(Entity entity, @Nullable Entity holder) {
        return freeLength(entity) * LeadScale.reach(entity, holder);
    }

    /** The snap distance of the lead between {@code entity} and {@code holder} (null: a fence), sized for both ends. */
    public static double snapDistance(Entity entity, @Nullable Entity holder) {
        return snapDistance(entity) * LeadScale.reach(entity, holder);
    }

    /**
     * The radius around its holder a leashed mob's home is restricted to (vanilla's
     * {@code restrictTo}, which measures to the mob's position): the free length, plus what an
     * enlarged body adds sideways, since the lead is measured from the body (see {@link LeashGeometry#span}).
     * At least 1: a lead at 1/16 has 5/16 of a block free, and a radius of 0 would leave the mob no
     * home at all.
     */
    public static int homeRadius(Entity entity, @Nullable Entity holder) {
        return Math.max(1, (int) (freeLength(entity, holder) + LeashGeometry.oversize(entity, SIDEWAYS)));
    }

    private static final Vec3 SIDEWAYS = new Vec3(1.0, 0.0, 0.0);

    /**
     * One tick of the lead between {@code entity} and {@code holder}: snap past the snap
     * distance, else pull (when {@code pull} and past the free length). Server side for anything
     * the server simulates; the controlling client for a boat a player drives. While either end has
     * no known place in the world (a knot on a Valkyrien Skies ship that isn't loaded,
     * {@link LeashGeometry#knownCentre}) the lead waits: no pull, no snap.
     */
    public static Tick tick(Entity entity, Entity holder, boolean pull) {
        return tick(entity, holder, pull, pull);
    }

    /**
     * As {@link #tick(Entity, Entity, boolean)}, with the two ends apart. {@code pullEntity}: the
     * lead may move the leashed entity (not a sitting pet). {@code pullHolder}: this is the lead's
     * one pulling call this tick, so between two mobs it may move the holder too: a chain of mobs
     * follows whichever of them is moved ({@link LeashChains}). A player, a knot or a boat holding
     * the lead is never moved by it.
     */
    public static Tick tick(Entity entity, Entity holder, boolean pullEntity, boolean pullHolder) {
        if (holder.level() != entity.level()) return Tick.NONE;
        LeashChains.record(entity, holder);
        Vec3 a = LeashGeometry.knownCentre(entity);
        Vec3 h = LeashGeometry.knownCentre(holder);
        if (a == null || h == null) return Tick.NONE;
        Track track = track(entity, holder, a, h);
        double d = LeashGeometry.span(entity, a, holder, h);
        double reach = LeadScale.reach(entity, holder); // 1 unless both ends are small
        if (d > snapDistance(entity) * reach) {
            if (!entity.level().isClientSide) snap(entity, h);
            return new Tick(d, false, true, reach);
        }
        double free = freeLength(entity) * reach;
        if (d <= free) return new Tick(d, false, false, reach);
        // A ride a player steers (a horse, a strider) is theirs to steer; boats decide for themselves.
        if (pullEntity && !(entity instanceof Boat) && entity.getControllingPassenger() instanceof Player) pullEntity = false;
        LeashChains.Role role = pullHolder ? LeashChains.role(entity, holder) : LeashChains.Role.ENTITY;
        switch (role) {
            case ENTITY -> {
                if (pullEntity) applyPull(entity, a, h, d, free, track.holderVelocity);
            }
            case HOLDER -> {
                // the entity's side of the chain is being moved: its holder follows it
                applyPull(holder, h, a, d, free, track.entityVelocity);
                afterTaut(holder, entity, h, a, reach);
                LeashTicker.walkToHolder(holder, entity);
            }
            case BOTH -> {
                if (pullBoth(entity, holder, a, h, d, free, pullEntity)) afterTaut(holder, entity, h, a, reach);
            }
        }
        afterTaut(entity, holder, a, h, reach);
        return new Tick(d, true, false, reach);
    }

    /** The lead snaps: dropped at the entity, the snap heard at the holder (Bedrock 1.21.100). */
    public static void snap(Entity entity, Vec3 holderCentre) {
        Leashes.detach(entity, true);
        UlSounds.play(entity.level(), holderCentre, UlSounds.LEAD_BREAK, entity);
    }

    /**
     * Pulls {@code entity} (centre {@code a}) toward the other end of its lead (centre {@code h},
     * moving at {@code holderVelocity}), which stands still for this lead: its holder, or in a
     * chain of mobs whichever end is nearer what moves the chain.
     */
    private static void applyPull(Entity entity, Vec3 a, Vec3 h, double d, double free, Vec3 holderVelocity) {
        if (PhysicsOwnership.claimedByOther(entity, PhysicsAspect.MOTION, OWNER)) return;
        Vec3 out = a.subtract(h);
        double centres = out.length();
        if (centres < 1.0E-9) return;
        Vec3 n = out.scale(1.0 / centres); // outward, holder to entity
        GravityFrame frame = Gravity.frame(entity);
        Vec3 own = Gravity.getWorldVelocity(entity, frame);
        double motion = Math.max(EntityScale.motion(entity), MIN_MOTION_SCALE);
        // how much of its (equally scaled) weight the lead lifts: none in zero gravity
        double weight = motion == 1.0 ? 1.0 : Mth.clamp(Gravity.strength(entity), 0.0, 1.0);
        Vec3 v = toLeadUnits(own, frame, motion, weight);

        if (!UlConfig.on(UlConfig.PHYSICS)) {
            // 1.20.1's own pull, kept for anyone who turns the elastic physics off
            Vec3 toward = n.scale(-1.0);
            Vec3 kick = new Vec3(Math.copySign(toward.x * toward.x * 0.4, toward.x),
                    Math.copySign(toward.y * toward.y * 0.4, toward.y),
                    Math.copySign(toward.z * toward.z * 0.4, toward.z));
            Gravity.setWorldVelocity(entity, own.add(toOwnUnits(kick, frame, motion, weight)), frame);
            return;
        }

        double maxPull = UlConfig.num(UlConfig.MAX_PULL);
        double radial = v.subtract(holderVelocity).dot(n); // > 0: moving away from the holder
        double dv = -Math.min(UlConfig.num(UlConfig.STIFFNESS) * (d - free), maxPull);
        if (radial > 0.0) dv -= radial * UlConfig.num(UlConfig.RADIAL_DAMPING);
        // never add pull once the entity already closes on the holder at maxPull
        dv = Math.max(dv, Math.min(0.0, -maxPull - radial));
        if (dv >= 0.0) return;
        Gravity.setWorldVelocity(entity, own.add(toOwnUnits(n.scale(dv), frame, motion, weight)), frame);
    }

    /**
     * A lead between two mobs neither of which is nearer what moves their chain (two free mobs, or
     * a tug of war): the same spring and damper, on how fast the two part, shared out by mass so
     * the lighter end moves more. An end the lead can't move ({@link LeashChains#immovable}, or the
     * entity when {@code pullEntity} is false) leaves it all to the other. Returns whether the
     * holder was moved.
     */
    private static boolean pullBoth(Entity entity, Entity holder, Vec3 a, Vec3 h, double d, double free, boolean pullEntity) {
        boolean moveEntity = pullEntity && !LeashChains.immovable(entity);
        boolean moveHolder = !LeashChains.immovable(holder);
        if (!moveEntity && !moveHolder) return false;
        if (!UlConfig.on(UlConfig.PHYSICS)) {
            // 1.20.1's one-way kick, kept as it was for anyone who turns the elastic physics off
            if (moveEntity) applyPull(entity, a, h, d, free, Vec3.ZERO);
            return false;
        }
        Vec3 out = a.subtract(h);
        double centres = out.length();
        if (centres < 1.0E-9) return false;
        Vec3 n = out.scale(1.0 / centres); // outward, holder to entity
        End e = End.of(entity), o = End.of(holder);
        double maxPull = UlConfig.num(UlConfig.MAX_PULL);
        double radial = e.lead.subtract(o.lead).dot(n); // > 0: the ends are parting
        double dv = -Math.min(UlConfig.num(UlConfig.STIFFNESS) * (d - free), maxPull);
        if (radial > 0.0) dv -= radial * UlConfig.num(UlConfig.RADIAL_DAMPING);
        dv = Math.max(dv, Math.min(0.0, -maxPull - radial));
        if (dv >= 0.0) return false;
        double me = Math.max(Weights.massWithPassengersKg(entity), MIN_MASS_KG);
        double mh = Math.max(Weights.massWithPassengersKg(holder), MIN_MASS_KG);
        double entityShare = !moveHolder ? 1.0 : !moveEntity ? 0.0 : mh / (me + mh);
        if (entityShare > 0.0) e.add(n.scale(dv * entityShare));
        if (entityShare < 1.0) o.add(n.scale(-dv * (1.0 - entityShare)));
        return entityShare < 1.0;
    }

    /** The least mass an end is taken to have, so a weightless one doesn't take the whole pull by a division. */
    private static final double MIN_MASS_KG = 0.01;

    /** One end of a lead with its velocity in lead units ({@link #toLeadUnits}). */
    private record End(Entity entity, GravityFrame frame, double motion, double weight, Vec3 own, Vec3 lead) {
        static End of(Entity entity) {
            GravityFrame frame = Gravity.frame(entity);
            Vec3 own = Gravity.getWorldVelocity(entity, frame);
            double motion = Math.max(EntityScale.motion(entity), MIN_MOTION_SCALE);
            double weight = motion == 1.0 ? 1.0 : Mth.clamp(Gravity.strength(entity), 0.0, 1.0);
            return new End(entity, frame, motion, weight, own, toLeadUnits(own, frame, motion, weight));
        }

        /** Adds a change of velocity, given in lead units. */
        void add(Vec3 lead) {
            Gravity.setWorldVelocity(entity, own.add(toOwnUnits(lead, frame, motion, weight)), frame);
        }
    }

    /**
     * An entity's velocity in the units the lead works in. A size mod scales how far the entity's
     * own velocity moves it ({@code motion}: Pehkui moves a pig shrunk to 1/16 by 1/16 of its
     * {@code deltaMovement}, falling included). Across the ground the lead works in real blocks per
     * tick, so a resized mob keeps up with its holder. Up and down it works in the entity's own
     * units, so the lead lifts it against its own (equally scaled) weight exactly as it lifts a
     * normal-sized one: a tiny pig is dragged along the ground, not flown behind you like a kite
     * (its weight is 1/16 in real terms), and it hangs from a knot like any pig. Where it weighs
     * nothing (zero gravity: {@code weight} 0) there is no weight to lift, so the lead works in
     * real blocks along every axis; low gravity blends the two. Identity at normal size.
     */
    static Vec3 toLeadUnits(Vec3 own, GravityFrame frame, double motion, double weight) {
        if (motion == 1.0) return own;
        Vec3 across = frame.horizontalPart(own);
        return own.subtract(across).scale(Mth.lerp(weight, motion, 1.0)).add(across.scale(motion));
    }

    /** The inverse of {@link #toLeadUnits}: a change in lead units as a change of {@code deltaMovement}. */
    static Vec3 toOwnUnits(Vec3 lead, GravityFrame frame, double motion, double weight) {
        if (motion == 1.0) return lead;
        Vec3 across = frame.horizontalPart(lead);
        return lead.subtract(across).scale(1.0 / Mth.lerp(weight, motion, 1.0)).add(across.scale(1.0 / motion));
    }

    private static void afterTaut(Entity entity, Entity holder, Vec3 a, Vec3 h, double reach) {
        // A taut lead carries the weight: no fall damage builds up while hanging or being lowered.
        if (UlConfig.on(UlConfig.NO_FALL_DAMAGE_WHILE_HELD)) entity.resetFallDistance();
        // Catches and jerks are not g-force events for Full Stop (impacts still are).
        PhysicsOwnership.claim(entity, OWNER, PhysicsAspect.G_FORCE.bit, CLAIM_TICKS);
        for (Entity passenger : entity.getPassengers()) {
            PhysicsOwnership.claim(passenger, OWNER, PhysicsAspect.G_FORCE.bit, CLAIM_TICKS);
        }
        if (UlConfig.on(UlConfig.FACE_HOLDER)) faceHolder(entity, a, h, reach);
    }

    /**
     * Dragged things turn toward whoever drags them (1.21.6); a boat turns its bow. Not within half a
     * block of the holder (times the lead's length scale: a whole lead at 1/16 is shorter than that).
     */
    private static void faceHolder(Entity entity, Vec3 a, Vec3 h, double reach) {
        if (entity.getControllingPassenger() instanceof Player) return;
        boolean dragged = entity instanceof Boat
                || (entity instanceof Mob mob && (mob.isNoAi() || !mob.onGround()));
        if (!dragged) return;
        double dx = h.x - a.x, dz = h.z - a.z;
        if (dx * dx + dz * dz < 0.25 * reach * reach) return;
        float target = (float) (Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG);
        float yaw = entity.getYRot();
        float turned = yaw + Mth.clamp(Mth.wrapDegrees(target - yaw), -TURN_RATE, TURN_RATE);
        entity.setYRot(turned);
        if (entity instanceof LivingEntity living) {
            living.setYBodyRot(turned);
            living.setYHeadRot(turned);
        }
    }

    /** Both ends' velocities (blocks/tick, world), from their centres last tick; zero on the first tick. */
    private record Track(Vec3 holderVelocity, Vec3 entityVelocity) {
        static final Track STILL = new Track(Vec3.ZERO, Vec3.ZERO);
    }

    private static Track track(Entity entity, Entity holder, Vec3 entityCentre, Vec3 holderCentre) {
        long now = entity.level().getGameTime();
        HolderTrack last = TRACKS.put(entity, new HolderTrack(holder, now, holderCentre, entityCentre));
        if (last == null || last.holder != holder || now - last.tick != 1L) return Track.STILL;
        return new Track(velocity(holderCentre, last.centre), velocity(entityCentre, last.entityCentre));
    }

    private static Vec3 velocity(Vec3 now, Vec3 before) {
        Vec3 v = now.subtract(before);
        // a teleport or a ship snapping into place is not a velocity
        return v.lengthSqr() > 4.0 ? Vec3.ZERO : v;
    }
}
