package net.camacraft.unleashedlead.rope;

/**
 * How long a drawn lead is and how many points it gets. Plain Java, shared by the renderer and
 * the tests.
 *
 * <p>A real lead is as long as its free length, so when its ends are closer than that the spare
 * rope hangs as slack; past the free length it is taut. {@code slack} scales how much of the spare
 * length is shown (1 = all of it, a lead lying in loops on the ground; 0 = always straight).
 */
public final class RopeLength {
    /** A taut lead is drawn this much longer than the distance between its ends, so it never looks like a rod. */
    public static final double TAUT_SAG = 0.01;

    private RopeLength() {}

    /** The length the rope settles toward when its ends are {@code chord} apart. */
    public static double target(double chord, double freeLength, double slack) {
        double spare = Math.max(0.0, freeLength - chord);
        return chord * (1.0 + TAUT_SAG) + Math.max(0.0, slack) * spare;
    }

    /**
     * Moves the current length toward the target at most {@code rate} per tick (the rope pays out
     * and reels in rather than jumping), never shorter than the ends allow.
     */
    public static double approach(double current, double target, double chord, double rate) {
        double next = current + Math.max(-rate, Math.min(rate, target - current));
        return Math.max(next, chord);
    }

    /**
     * Points for a rope of {@code length} with segments about {@code segment} long. A lead at 1/16
     * has 1/16 the segment length (see {@link RopeScale}), so it gets the same number of points.
     */
    public static int points(double length, double segment, int max) {
        int n = (int) Math.ceil(length / Math.max(1.0E-4, segment)) + 1;
        return Math.max(RopeSolver.MIN_POINTS + 1, Math.min(Math.max(RopeSolver.MIN_POINTS + 1, max), n));
    }
}
