package net.camacraft.unleashedlead.mixin;

import net.camacraft.camafy.compat.vs.Ships;
import net.camacraft.unleashedlead.UlConfig;
import net.camacraft.unleashedlead.UnleashedLead;
import net.camacraft.unleashedlead.compat.VanillaBackport;
import net.camacraft.unleashedlead.leash.LeadScale;
import net.camacraft.unleashedlead.leash.LeashGeometry;
import net.camacraft.unleashedlead.leash.LeashPhysics;
import net.camacraft.unleashedlead.leash.LeashTicker;
import net.camacraft.camafy.util.log.LogOnce;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * With Vanilla Backport installed, its {@code Leashable} interface (on {@code PathfinderMob} and
 * {@code Boat}) runs the leash tick. Its hooks are interface default methods, so declaring
 * methods of the same name here overrides them, with no compile dependency on it:
 * <ul>
 *   <li>{@code vb$leashDistanceTo}: measured between world-space centres ({@code LeashGeometry.span},
 *   which also allows for an enlarged body). Its own measures raw positions, and a knot on a
 *   Valkyrien Skies ship lives in shipyard coordinates, thousands of blocks away: every lead tied
 *   to a ship's fence snapped the moment it was tied. It is handed over as the distance on a
 *   normal-sized lead (divided by {@code LeadScale.reach}), so every threshold of its own (the snap
 *   and elastic distances, whether a lead can be attached) applies to a lead between two ends
 *   shrunk to 1/16 at 1/16, exactly as this mod's own physics does. Unchanged for normal leads.</li>
 *   <li>{@code vb$leashSnapDistance} / {@code vb$leashElasticDistance}: this mod's config (same
 *   defaults, 12 and 5 + 1), so one setting governs both mods. Its happy ghast keeps its own
 *   10 / 16, declared on the ghast's class.</li>
 *   <li>{@code vb$whenLeashedTo}: the home restriction around the holder's world position.</li>
 *   <li>{@code vb$checkElasticInteractions}: a single lead pulls with Unleashed Lead's physics
 *   (the Bedrock pendulum; respects other mods' motion claims; world space), so a lead behaves
 *   the same with or without Vanilla Backport. Quad leads (a happy ghast carrying a boat or a
 *   horse on four ropes) keep Vanilla Backport's own four-point physics, called through the
 *   interface default.</li>
 *   <li>{@code vb$closeRangeLeashBehavior}: walking after a holder on a ship aims at its world
 *   position, and on a lead smaller than normal stops short by the lead's scale (its own walks to
 *   2 real blocks short, further than a lead at 1/16 reaches); otherwise Vanilla Backport's own.</li>
 *   <li>A knot on a ship that isn't loaded has no place in the world yet: the distance to it is
 *   handed over as 0 and no home is set, so Vanilla Backport neither snaps nor pulls the lead, and
 *   the walk after it stands down, until the ship is loaded again.</li>
 * </ul>
 * Only applied when Vanilla Backport is installed ({@code UlMixinPlugin}).
 */
@Mixin({PathfinderMob.class, Boat.class})
public abstract class VbLeashableMixin {
    @Unique
    @Nullable
    private static MethodHandle unleashedlead$vbElastic;
    @Unique
    @Nullable
    private static MethodHandle unleashedlead$vbCloseRange;
    @Unique
    private static boolean unleashedlead$looked;

    public double vb$leashDistanceTo(Entity holder) {
        Entity self = (Entity) (Object) this;
        // a knot on a ship that isn't loaded has no place yet: as if at hand, so nothing snaps or pulls
        if (!LeashGeometry.placed(holder) || !LeashGeometry.placed(self)) return 0.0;
        return LeashGeometry.span(self, holder) / LeadScale.reach(self, holder);
    }

    public double vb$leashSnapDistance() {
        return UlConfig.num(UlConfig.SNAP_DISTANCE);
    }

    public double vb$leashElasticDistance() {
        return UlConfig.num(UlConfig.FREE_LENGTH) + 1.0;
    }

    public void vb$whenLeashedTo(Entity holder) {
        Entity self = (Entity) (Object) this;
        Vec3 at = LeashGeometry.knownCentre(holder);
        if (self instanceof Mob mob && at != null) {
            mob.restrictTo(BlockPos.containing(at), LeashPhysics.homeRadius(self, holder));
        }
        VanillaBackport.notifyHolder(holder, self);
    }

    public boolean vb$checkElasticInteractions(Entity holder) {
        Entity self = (Entity) (Object) this;
        if (VanillaBackport.usesQuadLeash(self, holder) && !unleashedlead$onShip(holder)) {
            MethodHandle theirs = unleashedlead$default(true);
            if (theirs != null) {
                try {
                    boolean pulled = (boolean) theirs.invoke(self, holder);
                    VanillaBackport.countDelegated();
                    return pulled;
                } catch (Throwable t) {
                    unleashedlead$failed(t);
                }
            }
        }
        return LeashTicker.vanillaBackportPull(self, holder);
    }

    public void vb$closeRangeLeashBehavior(Entity holder) {
        Entity self = (Entity) (Object) this;
        if (unleashedlead$onShip(holder) || LeadScale.reach(self, holder) < 1.0) {
            LeashTicker.walkToHolder(self, holder);
            return;
        }
        MethodHandle theirs = unleashedlead$default(false);
        if (theirs == null) {
            LeashTicker.walkToHolder(self, holder);
            return;
        }
        try {
            theirs.invoke(self, holder);
            VanillaBackport.countDelegated();
        } catch (Throwable t) {
            unleashedlead$failed(t);
        }
    }

    @Unique
    private static boolean unleashedlead$onShip(Entity holder) {
        Vec3 c = holder.getBoundingBox().getCenter();
        return Ships.inShipyard(holder.level(), c.x, c.y, c.z);
    }

    /**
     * Vanilla Backport's interface default for a hook this class now overrides, as a handle
     * that bypasses the override (an invokespecial, which only the implementing class itself may
     * create: this method is merged into {@code PathfinderMob} / {@code Boat}, so its lookup is
     * theirs).
     */
    @Unique
    @Nullable
    private static MethodHandle unleashedlead$default(boolean elastic) {
        if (!unleashedlead$looked) {
            unleashedlead$looked = true;
            Class<?> iface = VanillaBackport.leashableInterface();
            if (iface != null) {
                try {
                    MethodHandles.Lookup lookup = MethodHandles.lookup();
                    unleashedlead$vbElastic = lookup.findSpecial(iface, "vb$checkElasticInteractions",
                            MethodType.methodType(boolean.class, Entity.class), lookup.lookupClass());
                    unleashedlead$vbCloseRange = lookup.findSpecial(iface, "vb$closeRangeLeashBehavior",
                            MethodType.methodType(void.class, Entity.class), lookup.lookupClass());
                } catch (Throwable t) {
                    unleashedlead$failed(t);
                }
            }
        }
        return elastic ? unleashedlead$vbElastic : unleashedlead$vbCloseRange;
    }

    @Unique
    private static void unleashedlead$failed(Throwable t) {
        unleashedlead$vbElastic = null;
        unleashedlead$vbCloseRange = null;
        VanillaBackport.markDefaultsFailed();
        LogOnce.error(UnleashedLead.LOGGER, "unleashedlead-vb-default",
                "Could not reach Vanilla Backport's own leash behaviour; Unleashed Lead's runs instead", t);
    }
}
