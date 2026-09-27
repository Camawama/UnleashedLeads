package net.camacraft.unleashedlead.rope;

/**
 * A rope as a chain of points, solved with position-based dynamics: Verlet integration, then
 * distance constraints between neighbours, long-range tethers to both ends, and collision with
 * boxes supplied by a {@link Collider}.
 *
 * <p>Plain Java on purpose: no Minecraft class is named here, so the same solver drives the
 * client renderer, runs inside a gametest against a real level, and can be exercised from a bare
 * JVM. Units are blocks and game ticks.
 *
 * <p>Both ends are pinned. Each {@link #step} moves the pins in a straight line from where they
 * were to where they are now across the substeps, so a fast-moving holder drags the rope rather
 * than teleporting its end.
 */
public final class RopeSolver {

    /** The solid boxes a rope point can't enter, cell by cell. */
    @FunctionalInterface
    public interface Collider {
        /** Reports every solid box of block cell (bx, by, bz), in world coordinates. */
        void boxes(int bx, int by, int bz, BoxSink sink);
    }

    /** Receives one box. */
    @FunctionalInterface
    public interface BoxSink {
        void box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ);
    }

    /** A world with nothing in it. */
    public static final Collider NO_COLLISION = (bx, by, bz, sink) -> { };

    /** Tuning. Mutable so a caller can keep one instance and refresh it from config. */
    public static final class Settings {
        /** Pull along the gravity direction, blocks per tick squared. */
        public double gravity = 0.05;
        /** Share of each point's velocity lost per tick (air drag). */
        public double damping = 0.05;
        /** Integration substeps per tick. */
        public int substeps = 3;
        /** Constraint passes per substep. */
        public int iterations = 8;
        /** Collision radius of a point: half the rope's thickness. */
        public double radius = 0.025;
        /** Share of sliding motion a contact removes per substep (0 = ice, 1 = glue). */
        public double friction = 0.5;
        /** Whether points collide with the collider's boxes at all. */
        public boolean collisions = true;
    }

    public static final int MIN_POINTS = 3;

    private int count;
    private double[] x, y, z;
    private double[] ox, oy, oz;
    private boolean[] touching;
    private double restLength;
    private double lastAx, lastAy, lastAz, lastBx, lastBy, lastBz;

    private final BoxGather gather = new BoxGather();

    public RopeSolver(int count) {
        allocate(Math.max(MIN_POINTS, count));
    }

    private void allocate(int n) {
        count = n;
        x = new double[n];
        y = new double[n];
        z = new double[n];
        ox = new double[n];
        oy = new double[n];
        oz = new double[n];
        touching = new boolean[n];
    }

    // ------------------------------------------------------------------ setup

    /**
     * Lays the rope out between the two ends as a hanging curve of {@code restLength}, at rest.
     * {@code (gx, gy, gz)} is the direction of gravity (need not be unit length).
     */
    public void reset(double ax, double ay, double az, double bx, double by, double bz,
                      double restLength, int points, double gx, double gy, double gz) {
        if (points != count) allocate(Math.max(MIN_POINTS, points));
        this.restLength = Math.max(1.0E-3, restLength);
        double chord = Math.sqrt(sq(bx - ax) + sq(by - ay) + sq(bz - az));
        // Depth of a shallow parabola whose arc is restLength: L ~ d + 8h^2 / (3d).
        double slack = Math.max(0.0, this.restLength - chord);
        double sag = chord < 1.0E-6 ? this.restLength * 0.5 : Math.sqrt(3.0 * chord * slack / 8.0);
        double gl = Math.sqrt(gx * gx + gy * gy + gz * gz);
        double dx = 0, dy = -1, dz = 0;
        if (gl > 1.0E-9) {
            dx = gx / gl;
            dy = gy / gl;
            dz = gz / gl;
        }
        for (int i = 0; i < count; i++) {
            double t = (double) i / (count - 1);
            double s = 4.0 * sag * t * (1.0 - t);
            x[i] = ax + (bx - ax) * t + dx * s;
            y[i] = ay + (by - ay) * t + dy * s;
            z[i] = az + (bz - az) * t + dz * s;
            ox[i] = x[i];
            oy[i] = y[i];
            oz[i] = z[i];
            touching[i] = false;
        }
        lastAx = ax;
        lastAy = ay;
        lastAz = az;
        lastBx = bx;
        lastBy = by;
        lastBz = bz;
    }

    /** Changes the rope's natural length (slack paid out or reeled in). */
    public void setRestLength(double restLength) {
        this.restLength = Math.max(1.0E-3, restLength);
    }

    public double restLength() {
        return restLength;
    }

    /**
     * Resamples the rope to {@code points} points evenly spaced along its current shape, keeping
     * each point's velocity. Used when the rope pays out or reels in far enough that a different
     * point count suits it.
     */
    public void resize(int points) {
        points = Math.max(MIN_POINTS, points);
        if (points == count) return;
        int n = count;
        double[] cum = new double[n];
        for (int i = 1; i < n; i++) {
            cum[i] = cum[i - 1] + Math.sqrt(sq(x[i] - x[i - 1]) + sq(y[i] - y[i - 1]) + sq(z[i] - z[i - 1]));
        }
        double total = cum[n - 1];
        double[] nx = new double[points], ny = new double[points], nz = new double[points];
        double[] vx = new double[points], vy = new double[points], vz = new double[points];
        int seg = 0;
        for (int j = 0; j < points; j++) {
            double target = total * j / (points - 1);
            while (seg < n - 2 && cum[seg + 1] < target) seg++;
            double len = cum[seg + 1] - cum[seg];
            double t = len < 1.0E-9 ? 0.0 : (target - cum[seg]) / len;
            t = Math.max(0.0, Math.min(1.0, t));
            int a = seg, b = seg + 1;
            nx[j] = x[a] + (x[b] - x[a]) * t;
            ny[j] = y[a] + (y[b] - y[a]) * t;
            nz[j] = z[a] + (z[b] - z[a]) * t;
            vx[j] = lerp(x[a] - ox[a], x[b] - ox[b], t);
            vy[j] = lerp(y[a] - oy[a], y[b] - oy[b], t);
            vz[j] = lerp(z[a] - oz[a], z[b] - oz[b], t);
        }
        nx[0] = x[0];
        ny[0] = y[0];
        nz[0] = z[0];
        nx[points - 1] = x[n - 1];
        ny[points - 1] = y[n - 1];
        nz[points - 1] = z[n - 1];
        allocate(points);
        for (int j = 0; j < points; j++) {
            x[j] = nx[j];
            y[j] = ny[j];
            z[j] = nz[j];
            ox[j] = nx[j] - vx[j];
            oy[j] = ny[j] - vy[j];
            oz[j] = nz[j] - vz[j];
        }
        ox[0] = x[0];
        oy[0] = y[0];
        oz[0] = z[0];
        ox[points - 1] = x[points - 1];
        oy[points - 1] = y[points - 1];
        oz[points - 1] = z[points - 1];
    }

    // ------------------------------------------------------------------ simulation

    /**
     * Advances the rope by one game tick with its ends pinned at A and B.
     * {@code (gx, gy, gz)} is the direction of gravity for this rope (need not be unit length).
     */
    public void step(double ax, double ay, double az, double bx, double by, double bz,
                     double gx, double gy, double gz, Settings settings, Collider collider) {
        int n = count;
        int substeps = Math.max(1, settings.substeps);
        int iterations = Math.max(1, settings.iterations);
        double h = 1.0 / substeps;
        double gl = Math.sqrt(gx * gx + gy * gy + gz * gz);
        double g = settings.gravity * h * h;
        double gdx = gl > 1.0E-9 ? gx / gl * g : 0.0;
        double gdy = gl > 1.0E-9 ? gy / gl * g : -g;
        double gdz = gl > 1.0E-9 ? gz / gl * g : 0.0;
        double keep = Math.pow(Math.max(0.0, 1.0 - settings.damping), h);
        double segment = restLength / (n - 1);
        boolean collide = settings.collisions && collider != null && collider != NO_COLLISION;

        for (int s = 1; s <= substeps; s++) {
            double t = (double) s / substeps;
            double pax = lastAx + (ax - lastAx) * t, pay = lastAy + (ay - lastAy) * t, paz = lastAz + (az - lastAz) * t;
            double pbx = lastBx + (bx - lastBx) * t, pby = lastBy + (by - lastBy) * t, pbz = lastBz + (bz - lastBz) * t;

            // integrate
            for (int i = 1; i < n - 1; i++) {
                double vx = (x[i] - ox[i]) * keep;
                double vy = (y[i] - oy[i]) * keep;
                double vz = (z[i] - oz[i]) * keep;
                ox[i] = x[i];
                oy[i] = y[i];
                oz[i] = z[i];
                x[i] += vx + gdx;
                y[i] += vy + gdy;
                z[i] += vz + gdz;
            }
            pin(0, pax, pay, paz);
            pin(n - 1, pbx, pby, pbz);

            // constrain
            for (int it = 0; it < iterations; it++) {
                if ((it & 1) == 0) {
                    for (int i = 0; i < n - 1; i++) link(i, i + 1, segment);
                } else {
                    for (int i = n - 2; i >= 0; i--) link(i, i + 1, segment);
                }
                tethers(segment);
            }

            // collide
            if (collide) {
                double r = settings.radius;
                double friction = Math.max(0.0, Math.min(1.0, settings.friction));
                for (int i = 1; i < n - 1; i++) {
                    touching[i] = false;
                    collidePoint(i, r, friction, collider);
                }
                // one more constraint pass so the pushed points drag their neighbours along
                for (int i = 0; i < n - 1; i++) link(i, i + 1, segment);
                for (int i = 1; i < n - 1; i++) collidePoint(i, r, friction, collider);
            }
        }
        lastAx = ax;
        lastAy = ay;
        lastAz = az;
        lastBx = bx;
        lastBy = by;
        lastBz = bz;
    }

    private void pin(int i, double px, double py, double pz) {
        x[i] = px;
        y[i] = py;
        z[i] = pz;
        ox[i] = px;
        oy[i] = py;
        oz[i] = pz;
    }

    private void link(int i, int j, double rest) {
        double dx = x[j] - x[i], dy = y[j] - y[i], dz = z[j] - z[i];
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d < 1.0E-9) return;
        double wi = (i == 0 || i == count - 1) ? 0.0 : 1.0;
        double wj = (j == 0 || j == count - 1) ? 0.0 : 1.0;
        double w = wi + wj;
        if (w == 0.0) return;
        double k = (d - rest) / (d * w);
        x[i] += dx * k * wi;
        y[i] += dy * k * wi;
        z[i] += dz * k * wi;
        x[j] -= dx * k * wj;
        y[j] -= dy * k * wj;
        z[j] -= dz * k * wj;
    }

    /** Long-range attachments: no point may be further from an end than the rope between them. */
    private void tethers(double segment) {
        int n = count;
        for (int i = 1; i < n - 1; i++) {
            tether(i, 0, i * segment);
            tether(i, n - 1, (n - 1 - i) * segment);
        }
    }

    private void tether(int i, int anchor, double max) {
        double dx = x[i] - x[anchor], dy = y[i] - y[anchor], dz = z[i] - z[anchor];
        double d2 = dx * dx + dy * dy + dz * dz;
        if (d2 <= max * max) return;
        double d = Math.sqrt(d2);
        double k = max / d;
        x[i] = x[anchor] + dx * k;
        y[i] = y[anchor] + dy * k;
        z[i] = z[anchor] + dz * k;
    }

    private void collidePoint(int i, double r, double friction, Collider collider) {
        int x0 = floor(x[i] - r), x1 = floor(x[i] + r);
        int y0 = floor(y[i] - r), y1 = floor(y[i] + r);
        int z0 = floor(z[i] - r), z1 = floor(z[i] + r);
        gather.size = 0;
        gather.radius = r;
        for (int cx = x0; cx <= x1; cx++) {
            for (int cy = y0; cy <= y1; cy++) {
                for (int cz = z0; cz <= z1; cz++) {
                    collider.boxes(cx, cy, cz, gather);
                }
            }
        }
        if (gather.size == 0) return;
        double[] b = gather.boxes;
        int boxes = gather.size;
        int pushedAxis = -1;
        // Push out of the UNION of the boxes: a push that only clears one box can land inside its
        // neighbour (two stacked blocks share a face), so each candidate face is checked against
        // every box. Up to three passes for points wedged into a corner.
        for (int pass = 0; pass < 3; pass++) {
            double px = x[i], py = y[i], pz = z[i];
            double bestDist = Double.MAX_VALUE, smallDist = Double.MAX_VALUE;
            int bestAxis = -1, smallAxis = -1;
            double bestTarget = 0.0, smallTarget = 0.0;
            boolean inside = false;
            for (int k = 0; k < boxes; k++) {
                int o = k * 6;
                if (!contains(b, o, px, py, pz)) continue;
                inside = true;
                for (int face = 0; face < 6; face++) {
                    int axis = face >> 1;
                    double target = b[o + ((face & 1) == 0 ? axis : axis + 3)];
                    double from = axis == 0 ? px : axis == 1 ? py : pz;
                    double dist = Math.abs(target - from);
                    if (dist < smallDist) {
                        smallDist = dist;
                        smallAxis = axis;
                        smallTarget = target;
                    }
                    if (dist >= bestDist) continue;
                    double qx = axis == 0 ? target : px, qy = axis == 1 ? target : py, qz = axis == 2 ? target : pz;
                    if (insideAny(b, boxes, qx, qy, qz)) continue;
                    bestDist = dist;
                    bestAxis = axis;
                    bestTarget = target;
                }
            }
            if (!inside) break;
            int axis = bestAxis >= 0 ? bestAxis : smallAxis;
            double target = bestAxis >= 0 ? bestTarget : smallTarget;
            if (axis == 0) x[i] = target;
            else if (axis == 1) y[i] = target;
            else z[i] = target;
            pushedAxis = axis;
            if (bestAxis >= 0) break;
        }
        if (pushedAxis < 0) return;
        touching[i] = true;
        // Normal motion stops (no bounce); sliding motion is reduced by friction.
        switch (pushedAxis) {
            case 0 -> {
                ox[i] = x[i];
                oy[i] += (y[i] - oy[i]) * friction;
                oz[i] += (z[i] - oz[i]) * friction;
            }
            case 1 -> {
                oy[i] = y[i];
                ox[i] += (x[i] - ox[i]) * friction;
                oz[i] += (z[i] - oz[i]) * friction;
            }
            default -> {
                oz[i] = z[i];
                ox[i] += (x[i] - ox[i]) * friction;
                oy[i] += (y[i] - oy[i]) * friction;
            }
        }
    }

    /** Strictly inside box {@code o} (a point exactly on a face is outside). */
    private static boolean contains(double[] b, int o, double px, double py, double pz) {
        return px > b[o] && px < b[o + 3] && py > b[o + 1] && py < b[o + 4] && pz > b[o + 2] && pz < b[o + 5];
    }

    private static boolean insideAny(double[] b, int boxes, double px, double py, double pz) {
        for (int k = 0; k < boxes; k++) {
            if (contains(b, k * 6, px, py, pz)) return true;
        }
        return false;
    }

    /** Collects the boxes around one point, grown by the rope's radius. Reused, allocation-free once warm. */
    private static final class BoxGather implements BoxSink {
        double[] boxes = new double[6 * 8];
        int size;
        double radius;

        @Override
        public void box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
            if ((size + 1) * 6 > boxes.length) {
                if (size >= 64) return; // a cell with dozens of boxes: enough is enough
                boxes = java.util.Arrays.copyOf(boxes, boxes.length * 2);
            }
            int o = size * 6;
            double r = radius;
            boxes[o] = minX - r;
            boxes[o + 1] = minY - r;
            boxes[o + 2] = minZ - r;
            boxes[o + 3] = maxX + r;
            boxes[o + 4] = maxY + r;
            boxes[o + 5] = maxZ + r;
            size++;
        }
    }

    // ------------------------------------------------------------------ reading

    public int count() {
        return count;
    }

    public double x(int i) {
        return x[i];
    }

    public double y(int i) {
        return y[i];
    }

    public double z(int i) {
        return z[i];
    }

    /** Whether point {@code i} touched a box during the last substep. */
    public boolean touching(int i) {
        return touching[i];
    }

    /** Copies the points into {@code out} as x, y, z triples; grows it when too small. */
    public double[] copyPoints(double[] out) {
        if (out == null || out.length < count * 3) out = new double[count * 3];
        for (int i = 0; i < count; i++) {
            out[i * 3] = x[i];
            out[i * 3 + 1] = y[i];
            out[i * 3 + 2] = z[i];
        }
        return out;
    }

    /** The rope's current length along its points. */
    public double length() {
        double total = 0.0;
        for (int i = 1; i < count; i++) {
            total += Math.sqrt(sq(x[i] - x[i - 1]) + sq(y[i] - y[i - 1]) + sq(z[i] - z[i - 1]));
        }
        return total;
    }

    /** The largest speed of any point in the last substep, blocks per substep. For settling checks. */
    public double maxSpeed() {
        double max = 0.0;
        for (int i = 0; i < count; i++) {
            max = Math.max(max, Math.sqrt(sq(x[i] - ox[i]) + sq(y[i] - oy[i]) + sq(z[i] - oz[i])));
        }
        return max;
    }

    private static int floor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }

    private static double sq(double v) {
        return v * v;
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
