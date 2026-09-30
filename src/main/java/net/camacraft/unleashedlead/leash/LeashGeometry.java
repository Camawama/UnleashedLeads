package net.camacraft.unleashedlead.leash;

import net.camacraft.camafy.compat.gravity.Gravity;
import net.camacraft.camafy.compat.gravity.GravityFrame;
import net.camacraft.camafy.compat.scale.EntityScale;
import net.camacraft.camafy.compat.vs.ShipRef;
import net.camacraft.camafy.compat.vs.Ships;
import net.camacraft.unleashedlead.compat.VanillaBackport;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Where a lead's ends are, in world space.
 *
 * <p>Two kinds of point:
 * <ul>
 *   <li>{@link #centre}: the centre of an entity's bounding box. Leash physics and the snap
 *   distance use these (Java 1.21.6 and Bedrock both measure centre to centre), through
 *   {@link #span}, which takes off what an enlarged body (Pehkui) adds past its normal size.</li>
 *   <li>{@link #holderPoint} / {@link #leashedPoint}: where the rope is drawn from, the holder's
 *   hand or knot and the leashed entity's collar, at a partial tick. Both follow a resized
 *   entity: a collar is built from the eye height and box width, which Pehkui scales, and a
 *   player's hand is scaled here (vanilla's is a fixed 1 block below the top of the box).</li>
 * </ul>
 * Both go through Valkyrien Skies (via Camafy): a knot on a fence on a ship is an entity in
 * shipyard coordinates, thousands of blocks from where the ship is drawn, so every holder point
 * is taken to world space first. While its ship isn't loaded it has no place in the world at all
 * ({@link #knownCentre}), and the lead waits. The drawn points also follow Gravity Unbound's
 * frames, so a collar on a mob walking on a wall sits where the model shows it.
 */
public final class LeashGeometry {
    private LeashGeometry() {}

    /** Bounding-box centre in world space. */
    public static Vec3 centre(Entity entity) {
        return Ships.toWorld(entity.level(), entity.getBoundingBox().getCenter());
    }

    /**
     * {@link #centre}, or null while the entity has no known place in the world: it lives in the
     * shipyard of a Valkyrien Skies ship that isn't loaded (a knot on a ship's fence while the ship
     * is out of range, or still loading after a player comes back). {@link #centre} would give its
     * raw shipyard position, millions of blocks from the ship, and a lead measured to that snaps at
     * once. VS loads a ship by a player's distance to the ship, while the mob tied to it is an
     * ordinary entity in the world, loaded and ticking by distance to itself.
     */
    @Nullable
    public static Vec3 knownCentre(Entity entity) {
        Level level = entity.level();
        Vec3 c = entity.getBoundingBox().getCenter();
        if (!Ships.inShipyard(level, c.x, c.y, c.z)) return c;
        ShipRef ship = Ships.managing(level, c.x, c.y, c.z);
        return ship == null ? null : ship.toWorld(c);
    }

    /** Whether {@code entity} has a known place in the world (see {@link #knownCentre}). */
    public static boolean placed(Entity entity) {
        Level level = entity.level();
        Vec3 c = entity.getBoundingBox().getCenter();
        return !Ships.inShipyard(level, c.x, c.y, c.z) || Ships.managing(level, c.x, c.y, c.z) != null;
    }

    /**
     * How much lead is in use between {@code entity} and its holder: what the free length, the
     * pull and the snap distance are measured against. It is the distance between the two centres,
     * less how far each end's body reaches toward the other past its normal size
     * ({@link #oversize}). At normal size or smaller that is nothing, so it is exactly centre to
     * centre; a pig Pehkui grows to 4 has its centre 1.35 blocks further inside it, and a lead
     * held at its side would otherwise be half used up before anyone walked away.
     *
     * @param holder the holding entity, or null for a bare point (a fence being tied to)
     */
    public static double span(Entity entity, Vec3 entityCentre, @Nullable Entity holder, Vec3 holderCentre) {
        Vec3 between = holderCentre.subtract(entityCentre);
        double d = between.length();
        if (d < 1.0E-9) return d;
        Vec3 n = between.scale(1.0 / d);
        double reach = oversize(entity, n) + (holder == null ? 0.0 : oversize(holder, n));
        return Math.max(0.0, d - reach);
    }

    /** {@link #span} between two entities' current centres. */
    public static double span(Entity entity, Entity holder) {
        return span(entity, centre(entity), holder, centre(holder));
    }

    /**
     * How far past its type's normal size an entity's body reaches from its centre along
     * {@code dir} (a unit vector, either sign): 0 at normal size or smaller (babies, a mob
     * shrunk by Pehkui), more for an enlarged one. The body is an upright cylinder the size of its
     * box, compared with one the size of its type's normal box.
     */
    public static double oversize(Entity entity, Vec3 dir) {
        EntityDimensions normal = entity.getType().getDimensions();
        double wider = Math.max(0.0, (entity.getBbWidth() - normal.width) * 0.5);
        double taller = Math.max(0.0, (entity.getBbHeight() - normal.height) * 0.5);
        if (wider == 0.0 && taller == 0.0) return 0.0;
        double extra = Math.sqrt(dir.x * dir.x + dir.z * dir.z) * wider + Math.abs(dir.y) * taller;
        return Double.isFinite(extra) ? extra : 0.0;
    }

    /** Where the rope leaves the holder at {@code partialTick}: a player's hand, a knot, a mob's collar. */
    public static Vec3 holderPoint(Entity holder, float partialTick) {
        Vec3 base = holder.getPosition(partialTick);
        Vec3 hold = holder.getRopeHoldPosition(partialTick);
        if (holder instanceof Player player) hold = base.add(sizedHand(player, hold.subtract(base)));
        GravityFrame frame = Gravity.renderFrame(holder, partialTick);
        Vec3 point = frame.isDefault() ? hold : base.add(frame.localToWorld(hold.subtract(base)));
        return renderToWorld(holder.level(), point);
    }

    /**
     * Vanilla's player hand offset ({@code Player.getRopeHoldPosition}) at the player's size. Its
     * constants are for a normal player: 0.22 to the side and 1 block below the top of the box,
     * which for a player Pehkui shrinks to 1/16 is almost a block under its feet, and for one grown
     * to 16 is up by its head. Standing or crouching, the side and forward offsets scale and the
     * hand sits {@code heightFactor} below the top; swimming or gliding, the whole offset scales.
     */
    private static Vec3 sizedHand(Player player, Vec3 offset) {
        double sw = EntityScale.widthFactor(player), sh = EntityScale.heightFactor(player);
        if (Math.abs(sw - 1.0) < 1.0E-4 && Math.abs(sh - 1.0) < 1.0E-4) return offset;
        boolean upright = !player.isFallFlying() && !player.isAutoSpinAttack() && !player.isVisuallySwimming();
        double y = upright ? offset.y + 1.0 - sh : offset.y * sh;
        return new Vec3(offset.x * sw, y, offset.z * sw);
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

    /**
     * Gravity on a rope between these two entities, as a multiple of normal: the average of both
     * ends' pull directions, scaled by the average of their surroundings' gravity strength
     * ({@code Gravity.ambientStrength}: 0 in VS Genesis's space or an Ad Astra orbit, a sixth on
     * the Moon, a Gravity Unbound field's where one reaches). The ends' own no-gravity flags don't
     * count: a flying bee is flagged, and the rope to it still hangs. Unit length normally.
     */
    public static Vec3 ropeGravity(Entity a, Entity b) {
        double strength = 0.5 * (Gravity.ambientStrength(a) + Gravity.ambientStrength(b));
        return strength == 1.0 ? ropeDown(a, b) : ropeDown(a, b).scale(strength);
    }

    /** The unit direction a rope between these two entities hangs (the average of both ends' pulls). */
    public static Vec3 ropeDown(Entity a, Entity b) {
        Vec3 pa = Gravity.pullDirection(a);
        Vec3 pb = Gravity.pullDirection(b);
        Vec3 sum = pa.add(pb);
        double len = sum.length();
        return len < 1.0E-6 ? pa : sum.scale(1.0 / len);
    }
}
