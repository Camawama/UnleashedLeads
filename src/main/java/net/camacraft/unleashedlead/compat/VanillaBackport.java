package net.camacraft.unleashedlead.compat;

import net.camacraft.camafy.compat.ModPresence;
import net.camacraft.camafy.util.log.LogOnce;
import net.camacraft.unleashedlead.UnleashedLead;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Vanilla Backport (ItsBlackGear, mod id {@code vanillabackport}) backports 1.21.6 leads too:
 * a {@code Leashable} interface on {@code PathfinderMob}, {@code Boat} and its happy ghast, boat
 * leashing, leash physics, quad leashes (a happy ghast carrying a boat on four ropes) and a
 * thicker leash renderer.
 *
 * <p>When it is installed it owns the leash <em>data model</em> for the entities it handles
 * (who holds a boat, how that is saved and synced) and runs their leash tick; Unleashed Lead
 * reads and writes that model through this bridge, so both mods always agree on who holds what,
 * and plugs its own pull and distances into that tick ({@code mixin.VbLeashableMixin}).
 * Everything here is reflection on its {@code vb$}-prefixed methods, which are not obfuscated:
 * no compile dependency, and it keeps working when Vanilla Backport moves the interface (its 1.2
 * line puts it in another package; both names are tried, then any interface declaring the
 * methods). A failure is logged once and the bridge switches itself off.
 */
public final class VanillaBackport {
    public static final String MOD_ID = "vanillabackport";

    private static final String[] INTERFACE_NAMES = {
            "com.blackgear.vanillabackport.common.api.leash.Leashable",
            "com.blackgear.vanillabackport.common.api.modules.leash_behavior.Leashable",
    };

    private static volatile boolean resolved;
    private static volatile boolean failed;
    @Nullable private static Class<?> leashable;
    private static MethodHandle getHolder, setLeashedTo, dropLeash, supportQuad, supportQuadHolder,
            quadOffsets, quadHolderOffsets, snapDistance, elasticDistance, notifyHolder;

    /** Calls handed back to its own (quad-lead) physics by {@code mixin.VbLeashableMixin}; for the tests. */
    private static final java.util.concurrent.atomic.AtomicInteger DELEGATED = new java.util.concurrent.atomic.AtomicInteger();
    private static volatile boolean defaultsFailed;

    private VanillaBackport() {}

    /** {@code VbLeashableMixin} handed a call to one of its own default hooks. */
    public static void countDelegated() {
        DELEGATED.incrementAndGet();
    }

    public static int delegatedCalls() {
        return DELEGATED.get();
    }

    /** {@code VbLeashableMixin} could not reach its default hooks and fell back to ours. */
    public static void markDefaultsFailed() {
        defaultsFailed = true;
    }

    public static boolean defaultsFailed() {
        return defaultsFailed;
    }

    private static final String HAPPY_GHAST = "com.blackgear.vanillabackport.common.level.entities.happyghast.HappyGhast";
    @Nullable private static volatile Class<?> happyGhast;
    private static volatile boolean happyGhastLooked;

    /** Whether {@code entity} is Vanilla Backport's happy ghast (false without Vanilla Backport, or if it moved the class). */
    public static boolean isHappyGhast(Entity entity) {
        if (!happyGhastLooked) {
            happyGhastLooked = true;
            if (present()) {
                try {
                    happyGhast = Class.forName(HAPPY_GHAST, false, VanillaBackport.class.getClassLoader());
                } catch (Throwable t) {
                    UnleashedLead.LOGGER.info("Vanilla Backport's happy ghast not found at {}; its facing fixes stay off", HAPPY_GHAST);
                }
            }
        }
        Class<?> c = happyGhast;
        return c != null && c.isInstance(entity);
    }

    /** Whether Vanilla Backport is installed (safe at any point in loading). */
    public static boolean present() {
        return ModPresence.isLoaded(MOD_ID);
    }

    /** Whether the bridge to its leash model is usable right now. */
    public static boolean active() {
        return present() && resolve();
    }

    /** Whether {@code entity} carries Vanilla Backport's leash model. */
    public static boolean isLeashable(Entity entity) {
        return active() && leashable.isInstance(entity);
    }

    @Nullable
    public static Entity holder(Entity entity) {
        if (!isLeashable(entity)) return null;
        try {
            return (Entity) getHolder.invoke(entity);
        } catch (Throwable t) {
            fail("reading a leash holder", t);
            return null;
        }
    }

    public static void attach(Entity entity, Entity holder) {
        if (!isLeashable(entity)) return;
        try {
            setLeashedTo.invoke(entity, holder, true);
        } catch (Throwable t) {
            fail("attaching a leash", t);
        }
    }

    public static void detach(Entity entity, boolean dropItem) {
        if (!isLeashable(entity)) return;
        try {
            dropLeash.invoke(entity, true, dropItem);
        } catch (Throwable t) {
            fail("dropping a leash", t);
        }
    }

    /** Whether {@code entity} hangs from a quad-capable holder on four ropes. */
    public static boolean usesQuadLeash(Entity entity, Entity holder) {
        if (!isLeashable(entity) || !leashable.isInstance(holder)) return false;
        try {
            return (boolean) supportQuad.invoke(entity) && (boolean) supportQuadHolder.invoke(holder);
        } catch (Throwable t) {
            fail("checking a quad leash", t);
            return false;
        }
    }

    // Each accessor below links the bridge (isLeashable) BEFORE reading its handle: the handle
    // fields are only set by resolve(), so reading one first handed a null handle to the very first
    // call of a session, which then switched the whole bridge off.

    /** The four attachment offsets on the leashed entity (entity-local, before yaw), or null. */
    @Nullable
    public static Vec3[] quadOffsets(Entity entity) {
        return isLeashable(entity) ? offsets(entity, quadOffsets) : null;
    }

    /** The four attachment offsets on the holder (entity-local, before yaw), or null. */
    @Nullable
    public static Vec3[] quadHolderOffsets(Entity holder) {
        return isLeashable(holder) ? offsets(holder, quadHolderOffsets) : null;
    }

    /** Its snap distance for this entity (12, or 16 for a happy ghast), or NaN. */
    public static double snapDistance(Entity entity) {
        return isLeashable(entity) ? number(entity, snapDistance) : Double.NaN;
    }

    /** Its elastic (free) distance for this entity (6, or 10 for a happy ghast), or NaN. */
    public static double elasticDistance(Entity entity) {
        return isLeashable(entity) ? number(entity, elasticDistance) : Double.NaN;
    }

    /** Its {@code Leashable} interface, or null when the bridge isn't active. */
    @Nullable
    public static Class<?> leashableInterface() {
        return active() ? leashable : null;
    }

    /**
     * Tells {@code holder} that {@code entity} is tied to it this tick (its happy ghast uses this
     * to show its rope harness). What Vanilla Backport's own {@code vb$whenLeashedTo} does after
     * restricting the entity's home, which Unleashed Lead replaces.
     */
    public static void notifyHolder(Entity holder, Entity entity) {
        if (!isLeashable(holder) || !leashable.isInstance(entity)) return;
        try {
            notifyHolder.invoke(holder, entity);
        } catch (Throwable t) {
            fail("notifying a leash holder", t);
        }
    }

    @Nullable
    private static Vec3[] offsets(Entity entity, MethodHandle handle) {
        if (!isLeashable(entity)) return null;
        try {
            Vec3[] out = (Vec3[]) handle.invoke(entity);
            return out != null && out.length == 4 ? out : null;
        } catch (Throwable t) {
            fail("reading quad leash offsets", t);
            return null;
        }
    }

    private static double number(Entity entity, MethodHandle handle) {
        if (!isLeashable(entity)) return Double.NaN;
        try {
            return (double) handle.invoke(entity);
        } catch (Throwable t) {
            fail("reading a leash distance", t);
            return Double.NaN;
        }
    }

    private static boolean resolve() {
        if (failed) return false;
        if (resolved) return true;
        synchronized (VanillaBackport.class) {
            if (resolved || failed) return resolved;
            try {
                Class<?> iface = find();
                MethodHandles.Lookup lookup = MethodHandles.publicLookup();
                getHolder = lookup.findVirtual(iface, "vb$getLeashHolder", MethodType.methodType(Entity.class));
                setLeashedTo = lookup.findVirtual(iface, "vb$setLeashedTo",
                        MethodType.methodType(void.class, Entity.class, boolean.class));
                dropLeash = lookup.findVirtual(iface, "vb$dropLeash",
                        MethodType.methodType(void.class, boolean.class, boolean.class));
                supportQuad = lookup.findVirtual(iface, "vb$supportQuadLeash", MethodType.methodType(boolean.class));
                supportQuadHolder = lookup.findVirtual(iface, "vb$supportQuadLeashAsHolder",
                        MethodType.methodType(boolean.class));
                quadOffsets = lookup.findVirtual(iface, "vb$getQuadLeashOffsets", MethodType.methodType(Vec3[].class));
                quadHolderOffsets = lookup.findVirtual(iface, "vb$getQuadLeashHolderOffsets",
                        MethodType.methodType(Vec3[].class));
                snapDistance = lookup.findVirtual(iface, "vb$leashSnapDistance", MethodType.methodType(double.class));
                elasticDistance = lookup.findVirtual(iface, "vb$leashElasticDistance",
                        MethodType.methodType(double.class));
                notifyHolder = lookup.findVirtual(iface, "vb$notifyLeashHolder", MethodType.methodType(void.class, iface));
                leashable = iface;
                resolved = true;
                UnleashedLead.LOGGER.info("Linked to Vanilla Backport's leash model ({})", iface.getName());
            } catch (Throwable t) {
                fail("linking to its Leashable interface", t);
            }
            return resolved;
        }
    }

    private static Class<?> find() throws ClassNotFoundException {
        ClassLoader loader = VanillaBackport.class.getClassLoader();
        for (String name : INTERFACE_NAMES) {
            try {
                return Class.forName(name, false, loader);
            } catch (ClassNotFoundException ignored) {
                // try the next known location
            }
        }
        // Moved again: find the interface a boat implements that declares the method.
        for (Class<?> iface : net.minecraft.world.entity.vehicle.Boat.class.getInterfaces()) {
            for (java.lang.reflect.Method m : iface.getMethods()) {
                if (m.getName().equals("vb$getLeashHolder")) return iface;
            }
        }
        throw new ClassNotFoundException("Vanilla Backport's Leashable interface");
    }

    private static void fail(String what, Throwable t) {
        failed = true;
        resolved = false;
        LogOnce.error(UnleashedLead.LOGGER, "unleashedlead-vb-" + what,
                "Vanilla Backport bridge failed while {}; Unleashed Lead stops touching the leads it manages "
                        + "(boats, happy ghasts) for this session",
                what, t);
    }
}
