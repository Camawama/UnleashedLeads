package net.camacraft.unleashedlead.tether;

import net.camacraft.camafy.compat.vs.PhysBody;
import net.camacraft.camafy.compat.vs.ShipForces;
import net.camacraft.camafy.compat.vs.ShipRef;
import net.camacraft.camafy.compat.vs.Ships;
import net.camacraft.unleashedlead.UnleashedLead;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The pull of a rope on a Valkyrien Skies ship, computed on VS's physics thread from the ship's
 * live pose and velocity (through Camafy's single physics hook, {@link ShipForces#registerHook}):
 * a force held for a whole game tick would lag the motion and pump energy into a stiff rope.
 *
 * <p>The rope is slack up to its length. Past it the tension is a spring plus a damper along the
 * rope, {@code T = k·stretch + c·stretchSpeed} (never negative: a rope only pulls), applied at the
 * knot, so a ship tied at its bow swings around the knot. Between two ships each ship's own hook
 * computes the same tension from the same physics step and applies its own half, so the pair is
 * equal and opposite.
 * <ul>
 *   <li>{@code k = stiffness · √(mass / 1 t)}: a heavier ship gets a stiffer rope, but less than
 *   in proportion, so a big ship stretches it further than a small boat (which is held firmly).
 *   Between two ships the mass is their reduced mass.</li>
 *   <li>{@code c = 2·damping·√(k·m)}, with {@code m} the mass the rope actually feels at the knot
 *   (less than the ship's when the knot is off the centre of mass and the pull also turns it).</li>
 *   <li>Both are capped so no rope rings faster than {@link #OMEGA_MAX}, which keeps the explicit
 *   spring stable at VS's 60 Hz step however light the ship.</li>
 *   <li>Sway: while taut, the sideways motion of one knot against the other is damped too, at
 *   {@code tethers.swayDamping} per second of the mass that motion meets at the knots, equal and
 *   opposite on the two ends. The spring and its damper act only along the rope, so before this a
 *   chain of ships hung from a frozen one swung and snatched at each other for good (the owner:
 *   "they all start moving sporadically and the movement of the lead never settle"); air drag on
 *   a ship is far too weak to settle it.</li>
 * </ul>
 * When the tension passes the breaking strength the rope stops pulling at once and the server
 * snaps it on its next tick ({@link Tethers}).
 *
 * <p>Threads: the server thread builds each tick's list of rope ends ({@link #stage}) and
 * publishes it as one immutable map ({@link #publish}); the physics thread only reads that map
 * and writes each rope's {@link Cell}.
 */
public final class TetherPhysics {
    public static final ResourceLocation HOOK = UnleashedLead.id("tethers");
    /** Fastest a single rope may make a ship oscillate, rad/s (a period of 0.6 s). */
    static final double OMEGA_MAX = 10.0;
    /** Largest damping per unit of mass, 1/s (stable at VS's 1/60 s step). */
    static final double DAMP_MAX = 40.0;
    private static final double TONNE = 1000.0;

    /** One rope as the physics thread sees it: shared state, written there, read by the server. */
    public static final class Cell {
        private final AtomicLong peakBits = new AtomicLong();
        /** Highest tension and knot distance ever seen (for the gametests and debugging). */
        private final AtomicLong maxTensionBits = new AtomicLong(), maxDistanceBits = new AtomicLong();
        private volatile boolean broken;
        private volatile double tension;
        private volatile double distance = Double.NaN;

        void report(double t, double d) {
            tension = t;
            distance = d;
            raise(peakBits, t);
            raise(maxTensionBits, t);
            if (d > 0.0) raise(maxDistanceBits, d);
        }

        /** Raises a non-negative double held as bits to {@code value}, if higher. */
        private static void raise(AtomicLong bits, double value) {
            long next = Double.doubleToRawLongBits(value);
            long prev;
            do {
                prev = bits.get();
                if (Double.longBitsToDouble(prev) >= value) return;
            } while (!bits.compareAndSet(prev, next));
        }

        /** The highest tension this rope has had, newtons. */
        public double maxTension() {
            return Double.longBitsToDouble(maxTensionBits.get());
        }

        /** The furthest its knots have been apart while a ship was pulled, blocks (0 before any). */
        public double maxDistance() {
            return Double.longBitsToDouble(maxDistanceBits.get());
        }

        /** The highest tension since the last call (newtons). */
        double takePeak() {
            return Double.longBitsToDouble(peakBits.getAndSet(0L));
        }

        void breakRope() {
            broken = true;
        }

        public boolean broken() {
            return broken;
        }

        /** The tension at the last physics step, newtons (0 when slack). */
        public double tension() {
            return tension;
        }

        /** The distance between the knots at the last physics step, blocks (NaN before any). */
        public double distance() {
            return distance;
        }

        /** Slack again (an end unloaded) or tied afresh: no pull, not broken. */
        void reset() {
            broken = false;
            tension = 0.0;
            distance = Double.NaN;
            peakBits.set(0L);
        }

        /** Tied afresh: its history starts over too. */
        void restart() {
            reset();
            maxTensionBits.set(0L);
            maxDistanceBits.set(0L);
        }
    }

    /**
     * One ship's end of a rope. {@code s*} is its knot in the ship's own (shipyard) space; the
     * other end is a world point ({@code otherShip == NO_ID}) or a knot in another ship's space.
     */
    record End(long ship, double sx, double sy, double sz, long otherShip, double ox, double oy, double oz,
               double length, double stiffness, double damping, double sway, double breaking, Cell cell) {}

    private static final End[] NONE = new End[0];
    /** Server thread: each level's ends from its last tick. */
    private static final Map<ResourceKey<Level>, List<End>> STAGED = new HashMap<>();
    /** Read by the physics thread. */
    private static volatile Map<Long, End[]> live = Map.of();

    private TetherPhysics() {}

    /** Registers the hook with Camafy (common setup). Does nothing without Valkyrien Skies. */
    public static void register() {
        if (!Ships.available()) return;
        ShipForces.registerHook(HOOK, TetherPhysics::onPhysicsTick);
        UnleashedLead.LOGGER.info("Unleashed Lead: fence-to-fence ropes pull on Valkyrien Skies ships");
    }

    /** Whether ropes can pull ships in this session. */
    public static boolean available() {
        return ShipForces.available();
    }

    static void stage(ResourceKey<Level> level, List<End> ends) {
        if (ends.isEmpty()) STAGED.remove(level);
        else STAGED.put(level, ends);
    }

    /** Server tick end: hands every level's ends to the physics thread as one map. */
    public static void publish() {
        if (STAGED.isEmpty()) {
            if (!live.isEmpty()) live = Map.of();
            return;
        }
        Map<Long, List<End>> byShip = new HashMap<>();
        for (List<End> ends : STAGED.values()) {
            for (End e : ends) byShip.computeIfAbsent(e.ship(), id -> new ArrayList<>()).add(e);
        }
        Map<Long, End[]> next = new HashMap<>(byShip.size() * 2);
        byShip.forEach((id, ends) -> next.put(id, ends.toArray(NONE)));
        live = next;
    }

    public static void clear() {
        STAGED.clear();
        live = Map.of();
    }

    // ------------------------------------------------------------------ physics thread

    private static void onPhysicsTick(PhysBody body, double dt) {
        Map<Long, End[]> map = live;
        if (map.isEmpty()) return;
        End[] ends = map.get(body.id());
        if (ends == null || body.isStatic()) return;
        for (End e : ends) step(body, e);
    }

    private static void step(PhysBody body, End e) {
        Cell cell = e.cell();
        if (cell.broken()) return;
        Vector3d pa = body.toWorld(new Vector3d(e.sx(), e.sy(), e.sz()));
        PhysBody other = null;
        Vector3d pb;
        if (e.otherShip() != ShipRef.NO_ID) {
            other = body.other(e.otherShip());
            if (other == null) { // the other ship isn't simulated right now: nothing to pull against
                cell.report(0.0, Double.NaN);
                return;
            }
            pb = other.toWorld(new Vector3d(e.ox(), e.oy(), e.oz()));
        } else {
            pb = new Vector3d(e.ox(), e.oy(), e.oz());
        }
        Vector3d n = new Vector3d(pb).sub(pa);
        double d = n.length();
        if (!(d > e.length()) || d < 1.0E-6) {
            cell.report(0.0, d);
            return;
        }
        n.div(d); // unit, from this ship's knot toward the other end

        boolean otherMoves = other != null && !other.isStatic();
        Vector3d va = pointVelocity(body, pa);
        Vector3d vb = otherMoves ? pointVelocity(other, pb) : new Vector3d();
        double stretchSpeed = vb.sub(va).dot(n); // > 0: the ends are moving apart

        double m = body.mass();
        if (!(m > 0.0)) return;
        double mass = m, pointMass = pointMass(body, pa, n, m);
        if (otherMoves) {
            double mo = other.mass();
            if (mo > 0.0) {
                mass = m * mo / (m + mo);
                double po = pointMass(other, pb, n, mo);
                pointMass = pointMass * po / (pointMass + po);
            }
        }
        double k = Math.min(e.stiffness() * Math.sqrt(mass / TONNE), pointMass * OMEGA_MAX * OMEGA_MAX);
        double c = Math.min(2.0 * e.damping() * Math.sqrt(k * pointMass), pointMass * DAMP_MAX);
        double tension = Math.max(0.0, k * (d - e.length()) + c * stretchSpeed);
        cell.report(tension, d);
        if (tension > e.breaking()) {
            cell.breakRope(); // no more pull from here on; the server snaps it next tick
            return;
        }
        Vector3d force = new Vector3d(n).mul(tension);
        if (e.sway() > 0.0) addSway(force, body, pa, va, other, otherMoves, pb, n, e.sway());
        if (force.lengthSquared() > 0.0) body.applyForceAtShipPos(force, new Vector3d(e.sx(), e.sy(), e.sz()));
    }

    /**
     * Adds to {@code force} the sway damping of a taut rope: the other knot's velocity relative to this one, the
     * part across the rope, times {@code rate} and the mass that sideways motion meets at the two knots (the pair's
     * reduced point mass, as for the pull). The other ship's hook adds the opposite with the same numbers, so the
     * pair's momentum is kept and only their swing against each other is taken out. Capped like the rope's own
     * damper, so it stays stable at VS's step however light the ship.
     */
    private static void addSway(Vector3d force, PhysBody body, Vector3d pa, Vector3d va,
                                PhysBody other, boolean otherMoves, Vector3d pb, Vector3d n, double rate) {
        Vector3d vb = otherMoves ? pointVelocity(other, pb) : new Vector3d();
        Vector3d across = vb.sub(va);
        across.sub(new Vector3d(n).mul(across.dot(n)));
        double speed = across.length();
        if (speed < 1.0E-6) return;
        Vector3d dir = new Vector3d(across).div(speed);
        double m = body.mass();
        double pointMass = pointMass(body, pa, dir, m);
        if (otherMoves) {
            double mo = other.mass();
            if (mo > 0.0) {
                double po = pointMass(other, pb, dir, mo);
                pointMass = pointMass * po / (pointMass + po);
            }
        }
        if (!(pointMass > 0.0)) return;
        double c = Math.min(rate, DAMP_MAX) * pointMass;
        force.add(across.mul(c));
    }

    /** Velocity of a point fixed to the ship: v + ω × (p − centre of mass). */
    private static Vector3d pointVelocity(PhysBody body, Vector3d point) {
        Vector3d r = new Vector3d(point).sub(body.position(new Vector3d()));
        Vector3d w = body.angularVelocity(new Vector3d());
        return body.velocity(new Vector3d()).add(w.cross(r));
    }

    /**
     * The mass a pull along {@code n} at {@code point} meets: less than the ship's when the point
     * is off the centre of mass, since part of the pull turns the ship instead of moving it.
     */
    private static double pointMass(PhysBody body, Vector3d point, Vector3d n, double mass) {
        Vector3d r = new Vector3d(point).sub(body.position(new Vector3d()));
        Vector3d axis = r.cross(n);
        double lever2 = axis.lengthSquared();
        if (lever2 < 1.0E-9) return mass;
        double inertia = body.inertiaAbout(axis.normalize());
        if (!(inertia > 0.0)) return mass;
        return 1.0 / (1.0 / mass + lever2 / inertia);
    }
}
