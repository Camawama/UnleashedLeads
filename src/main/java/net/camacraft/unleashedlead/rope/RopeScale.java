package net.camacraft.unleashedlead.rope;

/**
 * How big one lead is, next to a normal lead, from the sizes of the two things it joins. Plain
 * Java, shared by the server's leash physics, the client's rope and the tests.
 *
 * <p>Each end has a size: 1 at normal size, 1/16 for a player or mob a size mod (Pehkui) has shrunk
 * to 1/16, 4 for one grown to 4 (see {@code leash.LeadScale}: vanilla babies and fence knots are
 * size 1). A lead has two scales:
 * <ul>
 *   <li>{@link #size}, the <b>thickness</b>: the smaller end's size. The rope has to fit the smaller
 *   of the two: a normal lead is as tall as a pig shrunk to 1/16, so a normal player leading one
 *   holds a thread, and so does a tiny player leading a normal pig. A thin cord on a big animal
 *   looks like a thin cord; a thick one on a tiny animal swamps it. The rope's width and its
 *   collision radius follow it.</li>
 *   <li>{@link #reach}, the <b>lengths</b>: the larger end's size, but never above 1. It is only
 *   below 1 when both ends are small, so a lead between a player and a pig both shrunk to 1/16 is
 *   a normal lead at 1/16: 5/16 of a block free, snapping past 12/16, with its slack, sag, segment
 *   spacing and swing all 1/16 as big. The lead has to be long enough for the bigger end (to reach
 *   around its body and keep up with it), so a tiny player leading a normal pig, a normal player
 *   leading a tiny one, and anything tied to a fence (a knot is size 1) keep the normal lengths.
 *   An enlarged end doesn't make the lead longer; it is measured from its body instead
 *   ({@code LeashGeometry.span}), as before.</li>
 * </ul>
 * Two normal ends give exactly {@link #NORMAL} (both 1.0), and every length times 1.0 is itself,
 * so normal leads are unchanged to the last bit.
 */
public record RopeScale(double size, double reach) {
    /** A lead between two normal-sized ends. */
    public static final RopeScale NORMAL = new RopeScale(1.0, 1.0);

    /** Sizes this close to 1 count as exactly 1 (rounding in a box that isn't resized at all). */
    public static final double SNAP = 1.0E-4;
    /** The smallest and largest size an end is taken to have (Pehkui allows far more). */
    public static final double MIN = 1.0 / 256.0, MAX = 256.0;

    /** The lead between ends of sizes {@code a} and {@code b} (each {@link #clean}ed first). */
    public static RopeScale between(double a, double b) {
        a = clean(a);
        b = clean(b);
        if (a == 1.0 && b == 1.0) return NORMAL;
        return new RopeScale(Math.min(a, b), Math.min(1.0, Math.max(a, b)));
    }

    /** A size factor made safe: 1 when unknown or within {@link #SNAP} of 1, else clamped to [{@link #MIN}, {@link #MAX}]. */
    public static double clean(double size) {
        if (!(size > 0.0) || !Double.isFinite(size) || Math.abs(size - 1.0) < SNAP) return 1.0;
        return Math.max(MIN, Math.min(MAX, size));
    }

    public boolean isNormal() {
        return size == 1.0 && reach == 1.0;
    }

    /** A length of a normal lead (free length, snap distance, slack, segment, sag speed) on this lead. */
    public double length(double normal) {
        return normal * reach;
    }

    /** A thickness of a normal lead (rope width, collision radius) on this lead. */
    public double thickness(double normal) {
        return normal * size;
    }
}
