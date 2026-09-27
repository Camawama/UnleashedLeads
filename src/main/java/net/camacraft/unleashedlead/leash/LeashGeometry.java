package net.camacraft.unleashedlead.leash;

import net.camacraft.camafy.compat.gravity.Gravity;
import net.camacraft.camafy.compat.gravity.GravityFrame;
import net.camacraft.camafy.compat.vs.ShipRef;
import net.camacraft.camafy.compat.vs.Ships;
import net.camacraft.unleashedlead.compat.VanillaBackport;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Where a lead's ends are, in world space.
 *
 * <p>Two kinds of point:
 * <ul>
 *   <li>{@link #centre}: the centre of an entity's bounding box. Leash physics and the snap
 *   distance use these (Java 1.21.6 and Bedrock both measure centre to centre).</li>
 *   <li>{@link #holderPoint} / {@link #leashedPoint}: where the rope is drawn from, the holder's
 *   hand or knot and the leashed entity's collar, at a partial tick.</li>
 * </ul>
 * Both go through Valkyrien Skies (via Camafy): a knot on a fence on a ship is an entity in
 * shipyard coordinates, thousands of blocks from where the ship is drawn, so every holder point
 * is taken to world space first. The drawn points also follow Gravity Unbound's frames, so a
 * collar on a mob walking on a wall sits where the model shows it.
 */
public final class LeashGeometry {
    private LeashGeometry() {}

    /** Bounding-box centre in world space. */
    public static Vec3 centre(Entity entity) {
        return Ships.toWorld(entity.level(), entity.getBoundingBox().getCenter());
    }

    /** Where the rope leaves the holder at {@code partialTick}: a player's hand, a knot, a mob's collar. */
    public static Vec3 holderPoint(Entity holder, float partialTick) {
        Vec3 base = holder.getPosition(partialTick);
        Vec3 hold = holder.getRopeHoldPosition(partialTick);
        GravityFrame frame = Gravity.renderFrame(holder, partialTick);
        Vec3 point = frame.isDefault() ? hold : base.add(frame.localToWorld(hold.subtract(base)));
        return renderToWorld(holder.level(), point);
    }

    /** Where the rope meets the leashed entity at {@code partialTick}. */
    public static Vec3 leashedPoint(Entity entity, float partialTick) {
        return attachPoint(entity, leashOffset(entity, partialTick), partialTick);
    }

    /**
     * An entity-local offset (x right, y up, z forward, before the entity's yaw) as a world
     * point at {@code partialTick}, following its gravity frame and any ship it lives on.
     */
    public static Vec3 attachPoint(Entity entity, Vec3 localOffset, float partialTick) {
        Vec3 offset = localOffset.yRot(-bodyYaw(entity, partialTick) * Mth.DEG_TO_RAD);
        GravityFrame frame = Gravity.renderFrame(entity, partialTick);
        Vec3 base = entity.getPosition(partialTick);
        Vec3 point = base.add(frame.isDefault() ? offset : frame.localToWorld(offset));
        return renderToWorld(entity.level(), point);
    }

    /** The collar offset. Boats get one at the bow unless Vanilla Backport gives them its own. */
    public static Vec3 leashOffset(Entity entity, float partialTick) {
        if (entity instanceof Boat && !VanillaBackport.present()) {
            return new Vec3(0.0, entity.getBbHeight() * 0.9, entity.getBbWidth() * 0.55);
        }
        return entity.getLeashOffset(partialTick);
    }

    /** The yaw the model is drawn with: body yaw for living things, plain yaw otherwise. */
    public static float bodyYaw(Entity entity, float partialTick) {
        if (entity instanceof LivingEntity living) {
            return Mth.lerp(partialTick, living.yBodyRotO, living.yBodyRot);
        }
        return Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
    }

    /** A shipyard position through its ship's drawn pose; anything else unchanged. */
    public static Vec3 renderToWorld(Level level, Vec3 point) {
        if (!Ships.inShipyard(level, point.x, point.y, point.z)) return point;
        ShipRef ship = Ships.managing(level, point.x, point.y, point.z);
        if (ship == null) return point;
        Vec3 world = ship.toWorld(point, ShipRef.Pose.RENDER);
        return world == null ? point : world;
    }

    /** The unit direction gravity pulls a rope between these two entities (the average of both ends). */
    public static Vec3 ropeGravity(Entity a, Entity b) {
        Vec3 pa = Gravity.pullDirection(a);
        Vec3 pb = Gravity.pullDirection(b);
        Vec3 sum = pa.add(pb);
        double len = sum.length();
        return len < 1.0E-6 ? pa : sum.scale(1.0 / len);
    }
}
