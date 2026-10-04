package net.camacraft.unleashedlead.leash;

import net.camacraft.camafy.physics.PhysicsAspect;
import net.camacraft.camafy.physics.PhysicsOwnership;
import net.camacraft.unleashedlead.UlConfig;
import net.camacraft.unleashedlead.compat.VanillaBackport;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The one leash model the rest of the mod talks to, whatever actually stores the leash:
 * <ul>
 *   <li>mobs: vanilla's own {@code Mob} leash (Vanilla Backport uses the same fields);</li>
 *   <li>boats, chest boats and rafts: Vanilla Backport's {@code Leashable} when it is installed,
 *   else this mod's {@link BoatLeash};</li>
 *   <li>Automobility's vehicles: this mod's {@link VehicleLeash}, with or without Vanilla Backport
 *   ({@link VehicleLeashes});</li>
 *   <li>Vanilla Backport's happy ghast: a mob, so the first case.</li>
 * </ul>
 * Attaching and detaching broadcast to clients; neither plays a sound (callers pick the sound).
 */
public final class Leashes {
    private Leashes() {}

    /** Whether this kind of entity can carry a lead at all (right now, with the current config). */
    public static boolean isLeashable(Entity entity) {
        if (entity instanceof Mob) return true;
        if (VanillaBackport.isLeashable(entity)) return true;
        if (entity instanceof VehicleLeash) return UlConfig.on(UlConfig.LEASH_VEHICLES);
        return entity instanceof BoatLeash && UlConfig.on(UlConfig.LEASH_BOATS);
    }

    /** Who holds {@code entity}'s lead, or null. */
    @Nullable
    public static Entity holder(Entity entity) {
        if (entity instanceof Mob mob) return mob.getLeashHolder();
        if (VanillaBackport.isLeashable(entity)) return VanillaBackport.holder(entity);
        if (entity instanceof BoatLeash leash) return BoatLeashes.holder(entity, leash);
        return null;
    }

    public static boolean isLeashed(Entity entity) {
        return holder(entity) != null;
    }

    /** Ties {@code entity} to {@code holder} (server side). Replaces any current holder without dropping a lead. */
    public static void attach(Entity entity, Entity holder) {
        if (entity.level().isClientSide) return;
        if (entity instanceof Mob mob) {
            mob.setLeashedTo(holder, true);
        } else if (VanillaBackport.isLeashable(entity)) {
            VanillaBackport.attach(entity, holder);
        } else if (entity instanceof BoatLeash leash) {
            BoatLeashes.attach(entity, leash, holder);
            VehicleLeashes.changed(entity);
        }
    }

    /** Unties {@code entity} (server side), dropping a lead item at it if {@code dropItem}. */
    public static void detach(Entity entity, boolean dropItem) {
        if (entity.level().isClientSide) return;
        if (entity instanceof Mob mob) {
            mob.dropLeash(true, dropItem);
        } else if (VanillaBackport.isLeashable(entity)) {
            VanillaBackport.detach(entity, dropItem);
        } else if (entity instanceof BoatLeash leash) {
            BoatLeashes.detach(entity, leash, dropItem);
            VehicleLeashes.changed(entity);
        }
    }

    /**
     * Every entity within {@code radius} of {@code holder} whose lead {@code holder} holds. The
     * search is centred on the holder's world position, so a knot on a Valkyrien Skies ship
     * (which lives in shipyard coordinates) still finds the mobs tied to it.
     */
    public static List<Entity> leashedTo(Entity holder, double radius) {
        Vec3 c = LeashGeometry.centre(holder);
        AABB box = new AABB(c.x - radius, c.y - radius, c.z - radius, c.x + radius, c.y + radius, c.z + radius);
        List<Entity> out = new ArrayList<>();
        for (Entity e : holder.level().getEntities((Entity) null, box, e -> e != holder && isLeashable(e))) {
            if (holder(e) == holder) out.add(e);
        }
        return out;
    }

    /** Whether {@code entity} holds anyone's lead within {@code radius}. */
    public static boolean holdsAny(Entity holder, double radius) {
        return !leashedTo(holder, radius).isEmpty();
    }

    /**
     * Whether Unleashed Lead runs this entity's whole leash tick. With Vanilla Backport installed,
     * its tick runs for the entities it adds leash behaviour to ({@code PathfinderMob}s and boats)
     * and Unleashed Lead supplies the pull through its hooks instead ({@code mixin.VbLeashableMixin}),
     * so there is still exactly one spring per lead.
     */
    public static boolean ownsPhysics(Entity entity) {
        // present(), not active(): its mixins apply whether or not our bridge to it links up
        if (!VanillaBackport.present()) return true;
        return !(entity instanceof PathfinderMob) && !(entity instanceof Boat) && !VanillaBackport.isLeashable(entity);
    }

    /**
     * Whether another mod forbids leashing {@code entity}: the {@code neverLeash} list, or any
     * other mod owning its motion (MKW karts and their drivers, a player on a grappling rope).
     */
    public static boolean refused(Entity entity) {
        if (UlConfig.neverLeash(entity.getType())) return true;
        // MKW Mobility claims the motion of every vehicle it drives, and takes a lead's pull through Camafy's
        // Impulses as its own speed; only a vehicle it holds outright (in a race) is refused
        if (entity instanceof VehicleLeash) return PhysicsOwnership.heldOutrightByOther(entity, LeashPhysics.OWNER);
        return PhysicsOwnership.claimedByOther(entity, PhysicsAspect.MOTION, LeashPhysics.OWNER);
    }

    /** Whether {@code entity} may hold other entities' leads (Bedrock: not babies, not players' mounts). */
    public static boolean canHold(Entity entity) {
        if (!entity.isAlive()) return false;
        if (entity instanceof LivingEntity living && living.isBaby()) return false;
        return entity instanceof Mob || entity instanceof LeashFenceKnotEntity || isLeashable(entity);
    }

    /** Whether tying {@code entity} to {@code holder} would close a loop (A holds B holds A). */
    public static boolean wouldLoop(Entity entity, Entity holder) {
        Entity cursor = holder;
        for (int depth = 0; cursor != null && depth < 64; depth++) {
            if (cursor == entity) return true;
            cursor = holder(cursor);
        }
        return false;
    }

    /** Whether {@code entity} is held by a player other than {@code player}. */
    public static boolean heldByOtherPlayer(Entity entity, Player player) {
        Entity holder = holder(entity);
        return holder instanceof Player && holder != player;
    }
}
