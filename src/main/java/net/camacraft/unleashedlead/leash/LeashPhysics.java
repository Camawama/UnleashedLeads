package net.camacraft.unleashedlead.leash;

import com.google.common.collect.MapMaker;
import net.camacraft.camafy.compat.gravity.Gravity;
import net.camacraft.camafy.compat.gravity.GravityFrame;
import net.camacraft.camafy.physics.PhysicsAspect;
import net.camacraft.camafy.physics.PhysicsOwnership;
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
 * <p>Velocities go through Camafy's {@link Gravity} so the pull is right in Gravity Unbound's
 * rotated frames, and nothing is applied to an entity whose MOTION another mod has claimed
 * (MKW Mobility karts and drivers, GrappleVS players). While the rope is catching, the entity
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

    /** Last tick's holder centre per leashed entity, for the holder's velocity. Identity-keyed. */
    private static final Map<Entity, HolderTrack> TRACKS = new MapMaker().weakKeys().makeMap();

    private record HolderTrack(Entity holder, long tick, Vec3 centre) {}

    private LeashPhysics() {}

    /** What one tick of leash found. */
    public record Tick(double distance, boolean taut, boolean snapped) {
        static final Tick NONE = new Tick(0.0, false, false);
    }

    public static double freeLength(Entity entity) {
        double vb = VanillaBackport.present() ? VanillaBackport.elasticDistance(entity) : Double.NaN;
        return Double.isNaN(vb) ? UlConfig.num(UlConfig.FREE_LENGTH) : Math.max(vb - 1.0, 1.0);
    }

    public static double snapDistance(Entity entity) {
        double vb = VanillaBackport.present() ? VanillaBackport.snapDistance(entity) : Double.NaN;
        return Double.isNaN(vb) ? UlConfig.num(UlConfig.SNAP_DISTANCE) : vb;
    }

    /**
     * One tick of the lead between {@code entity} and {@code holder}: snap past the snap
     * distance, else pull (when {@code pull} and past the free length). Server side for anything
     * the server simulates; the controlling client for a boat a player drives.
     */
    public static Tick tick(Entity entity, Entity holder, boolean pull) {
        if (holder.level() != entity.level()) return Tick.NONE;
        Vec3 a = LeashGeometry.centre(entity);
        Vec3 h = LeashGeometry.centre(holder);
        Vec3 holderVelocity = holderVelocity(entity, holder, h);
        double d = a.distanceTo(h);
        if (d > snapDistance(entity)) {
            if (!entity.level().isClientSide) snap(entity, h);
            return new Tick(d, false, true);
        }
        double free = freeLength(entity);
        if (d <= free) return new Tick(d, false, false);
        // A ride a player steers (a horse, a strider) is theirs to steer; boats decide for themselves.
        if (pull && !(entity instanceof Boat) && entity.getControllingPassenger() instanceof Player) pull = false;
        if (pull) applyPull(entity, a, h, d, free, holderVelocity);
        afterTaut(entity, holder, a, h);
        return new Tick(d, true, false);
    }

    /** The lead snaps: dropped at the entity, the snap heard at the holder (Bedrock 1.21.100). */
    public static void snap(Entity entity, Vec3 holderCentre) {
        Leashes.detach(entity, true);
        UlSounds.play(entity.level(), holderCentre, UlSounds.LEAD_BREAK, entity);
    }

    private static void applyPull(Entity entity, Vec3 a, Vec3 h, double d, double free, Vec3 holderVelocity) {
        if (PhysicsOwnership.claimedByOther(entity, PhysicsAspect.MOTION, OWNER)) return;
        Vec3 n = a.subtract(h).scale(1.0 / d); // outward, holder to entity
        GravityFrame frame = Gravity.frame(entity);
        Vec3 v = Gravity.getWorldVelocity(entity, frame);

        if (!UlConfig.on(UlConfig.PHYSICS)) {
            // 1.20.1's own pull, kept for anyone who turns the elastic physics off
            Vec3 toward = n.scale(-1.0);
            Vec3 kick = new Vec3(Math.copySign(toward.x * toward.x * 0.4, toward.x),
                    Math.copySign(toward.y * toward.y * 0.4, toward.y),
                    Math.copySign(toward.z * toward.z * 0.4, toward.z));
            Gravity.setWorldVelocity(entity, v.add(kick), frame);
            return;
        }

        double maxPull = UlConfig.num(UlConfig.MAX_PULL);
        double radial = v.subtract(holderVelocity).dot(n); // > 0: moving away from the holder
        double dv = -Math.min(UlConfig.num(UlConfig.STIFFNESS) * (d - free), maxPull);
        if (radial > 0.0) dv -= radial * UlConfig.num(UlConfig.RADIAL_DAMPING);
        // never add pull once the entity already closes on the holder at maxPull
        dv = Math.max(dv, Math.min(0.0, -maxPull - radial));
        if (dv >= 0.0) return;
        Gravity.setWorldVelocity(entity, v.add(n.scale(dv)), frame);
    }

    private static void afterTaut(Entity entity, Entity holder, Vec3 a, Vec3 h) {
        // A taut lead carries the weight: no fall damage builds up while hanging or being lowered.
        if (UlConfig.on(UlConfig.NO_FALL_DAMAGE_WHILE_HELD)) entity.resetFallDistance();
        // Catches and jerks are not g-force events for Full Stop (impacts still are).
        PhysicsOwnership.claim(entity, OWNER, PhysicsAspect.G_FORCE.bit, CLAIM_TICKS);
        for (Entity passenger : entity.getPassengers()) {
            PhysicsOwnership.claim(passenger, OWNER, PhysicsAspect.G_FORCE.bit, CLAIM_TICKS);
        }
        if (UlConfig.on(UlConfig.FACE_HOLDER)) faceHolder(entity, a, h);
    }

    /** Dragged things turn toward whoever drags them (1.21.6); a boat turns its bow. */
    private static void faceHolder(Entity entity, Vec3 a, Vec3 h) {
        if (entity.getControllingPassenger() instanceof Player) return;
        boolean dragged = entity instanceof Boat
                || (entity instanceof Mob mob && (mob.isNoAi() || !mob.onGround()));
        if (!dragged) return;
        double dx = h.x - a.x, dz = h.z - a.z;
        if (dx * dx + dz * dz < 0.25) return;
        float target = (float) (Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG);
        float yaw = entity.getYRot();
        float turned = yaw + Mth.clamp(Mth.wrapDegrees(target - yaw), -TURN_RATE, TURN_RATE);
        entity.setYRot(turned);
        if (entity instanceof LivingEntity living) {
            living.setYBodyRot(turned);
            living.setYHeadRot(turned);
        }
    }

    /** The holder's velocity (blocks/tick, world), from its centre last tick; zero on the first tick. */
    private static Vec3 holderVelocity(Entity entity, Entity holder, Vec3 centre) {
        long now = entity.level().getGameTime();
        HolderTrack last = TRACKS.put(entity, new HolderTrack(holder, now, centre));
        if (last == null || last.holder != holder || now - last.tick != 1L) return Vec3.ZERO;
        Vec3 v = centre.subtract(last.centre);
        // a teleport or a ship snapping into place is not a velocity
        return v.lengthSqr() > 4.0 ? Vec3.ZERO : v;
    }
}
