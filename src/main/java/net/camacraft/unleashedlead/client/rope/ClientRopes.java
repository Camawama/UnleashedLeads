package net.camacraft.unleashedlead.client.rope;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.camacraft.camafy.util.color.Argb;
import net.camacraft.unleashedlead.UlConfig;
import net.camacraft.unleashedlead.client.ClientTethers;
import net.camacraft.unleashedlead.compat.VanillaBackport;
import net.camacraft.unleashedlead.leash.LeadScale;
import net.camacraft.unleashedlead.leash.LeadTier;
import net.camacraft.unleashedlead.leash.LeadTiers;
import net.camacraft.unleashedlead.leash.LeashGeometry;
import net.camacraft.unleashedlead.leash.LeashPhysics;
import net.camacraft.unleashedlead.leash.Leashes;
import net.camacraft.unleashedlead.rope.BlockCollider;
import net.camacraft.unleashedlead.rope.RopeLength;
import net.camacraft.unleashedlead.rope.RopeScale;
import net.camacraft.unleashedlead.rope.RopeSolver;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Every lead the client can see, simulated and drawn.
 *
 * <p>Once per client tick every leashed entity in the level is gathered (mobs, boats, Vanilla
 * Backport's quad-leashed boats and horses, which get four ropes), and every fence-to-fence rope
 * ({@link ClientTethers}: knot to knot, or knot to the player carrying it). The nearest ones, up to
 * {@code maxSimulated} and within {@code simulationDistance}, are stepped with {@link RopeSolver};
 * the rest are drawn as a fixed hanging curve. Each frame the simulated ropes are interpolated
 * between their last two tick states and bent so their ends sit exactly on the holder's hand and
 * the entity's collar at that partial tick, so the rope stays glued on at any frame rate.
 *
 * <p>Ropes are drawn after entities, all in one batch, culled by the rope's own bounds rather
 * than either entity's: a rope stays visible while it crosses the screen with both ends off it.
 *
 * <p>Each rope is sized for the two things it joins ({@link LeadScale}, {@link RopeScale}): its
 * width and collision radius follow the smaller end, and its lengths (free length and slack,
 * segment spacing, how fast slack pays out, and its gravity, so it sags and swings like a normal
 * rope seen smaller) follow the lead's length scale, which is below 1 only when both ends are small.
 * A lead between a player and a pig both shrunk to 1/16 is the normal rope at 1/16; between two
 * normal-sized ends every number is exactly what it was.
 */
public final class ClientRopes {
    private static final Long2ObjectOpenHashMap<Rope> ROPES = new Long2ObjectOpenHashMap<>();
    private static final BlockCollider COLLIDER = new BlockCollider();
    private static final RopeSolver.Settings SETTINGS = new RopeSolver.Settings();
    private static final RopeMesh MESH = new RopeMesh();
    /** Red, green, blue per {@link LeadTier}, refreshed each frame from the client config. */
    private static final float[] COLORS = new float[LeadTier.values().length * 3];
    /** Points in a rope drawn as a fixed curve. */
    private static final int STATIC_POINTS = 10;
    /** An end moving further than this in one tick is a teleport: the rope is laid out afresh. */
    private static final double TELEPORT = 8.0;
    /** How fast slack pays out or reels in, blocks per tick. */
    private static final double PAYOUT_RATE = 0.35;

    /** Half the configured rope width: the collision radius of a normal-sized rope. */
    private static double baseRadius = 0.025;
    private static long tickStamp;
    private static double[] scratch = new double[STATIC_POINTS * 3];
    private static int[] scratchLight = new int[STATIC_POINTS];

    private ClientRopes() {}

    // ------------------------------------------------------------------ one rope

    /** One drawn rope: a lead, or one of the four ropes of a quad lead. */
    private static final class Rope {
        Entity entity;
        Entity holder;
        int index;
        boolean quad;
        /** A fence-to-fence rope: both ends are knots (or a knot and the player carrying it). */
        boolean tether;
        /** A tied fence-to-fence rope's length (NaN while carried: it hangs like a lead). */
        double tetherLength = Double.NaN;
        /** What the lead is made of: its colour ({@link LeadTier#ropeColor}). */
        LeadTier tier = LeadTier.PLAIN;
        long seen;
        boolean simulated;
        RopeSolver solver;
        double[] prev = new double[0];
        double[] cur = new double[0];
        int[] light = new int[0];
        Vec3 a0, a1, b0, b1;
        double restLength;
        /** Extra length the rope has needed to go around blocks lately (see {@link #tick}). */
        double wrapExtra;
        Vec3 up = new Vec3(0, 1, 0);
        /** Gravity on the rope, as a multiple of normal (zero in space): see LeashGeometry.ropeGravity. */
        Vec3 gravity = new Vec3(0, -1, 0);
        /** How big this rope is next to a normal lead (width, and lengths): see {@link RopeScale}. */
        RopeScale scale = RopeScale.NORMAL;

        Vec3 holderEnd(float partialTick) {
            if (quad) {
                Vec3[] offsets = VanillaBackport.quadHolderOffsets(holder);
                if (offsets != null) return LeashGeometry.attachPoint(holder, offsets[index], partialTick);
            }
            return LeashGeometry.holderPoint(holder, partialTick);
        }

        Vec3 entityEnd(float partialTick) {
            if (tether) return LeashGeometry.holderPoint(entity, partialTick); // a knot: where ropes leave it
            if (quad) {
                Vec3[] offsets = VanillaBackport.quadOffsets(entity);
                if (offsets != null) return LeashGeometry.attachPoint(entity, offsets[index], partialTick);
            }
            return LeashGeometry.leashedPoint(entity, partialTick);
        }

        double targetLength(double chord) {
            if (quad) return chord * (1.0 + RopeLength.TAUT_SAG); // Java draws quad leads without slack
            // a tied fence-to-fence rope is exactly as long as it was tied: all of its slack hangs
            if (tether && tetherLength > 0.0) return RopeLength.target(chord, tetherLength, 1.0);
            if (tether) return RopeLength.target(chord, scale.length(UlConfig.num(UlConfig.FREE_LENGTH)), UlConfig.num(UlConfig.ROPE_SLACK));
            // as long as the lead's free length, sized for its ends (5/16 of a block at 1/16)
            return RopeLength.target(chord, scale.length(LeashPhysics.freeLength(entity)), UlConfig.num(UlConfig.ROPE_SLACK));
        }

        void tick(ClientLevel level, boolean simulate) {
            scale = LeadScale.of(entity, holder);
            Vec3 a = holderEnd(1.0F), b = entityEnd(1.0F);
            Vec3 down = LeashGeometry.ropeGravity(entity, holder);
            gravity = down;
            // the mesh needs a unit up for its sides and shading even where nothing pulls the rope
            up = LeashGeometry.ropeDown(entity, holder).scale(-1.0);
            double chord = a.distanceTo(b);
            double target = targetLength(chord) + wrapExtra;
            if (!simulate) {
                simulated = false;
                solver = null;
                restLength = target;
                return;
            }
            int max = UlConfig.num(UlConfig.ROPE_MAX_POINTS);
            double segment = scale.length(UlConfig.num(UlConfig.ROPE_SEGMENT_LENGTH));
            boolean fresh = !simulated || solver == null
                    || a.distanceToSqr(a1) > TELEPORT * TELEPORT || b.distanceToSqr(b1) > TELEPORT * TELEPORT;
            if (fresh) {
                restLength = target;
                int n = RopeLength.points(restLength, segment, max);
                solver = new RopeSolver(n);
                solver.reset(a.x, a.y, a.z, b.x, b.y, b.z, restLength, n, down.x, down.y, down.z);
                cur = solver.copyPoints(cur);
                prev = solver.copyPoints(prev);
                a0 = a1 = a;
                b0 = b1 = b;
                simulated = true;
            } else {
                restLength = RopeLength.approach(restLength, target, chord, scale.length(PAYOUT_RATE));
                solver.setRestLength(restLength);
                int want = RopeLength.points(restLength, segment, max);
                if (Math.abs(want - solver.count()) >= 2) {
                    solver.resize(want);
                    prev = solver.copyPoints(prev);
                } else {
                    double[] swap = prev;
                    prev = cur;
                    cur = swap;
                }
                a0 = a1;
                b0 = b1;
                // A rope at 1/16 falls at 1/16 of the pace (as Pehkui has entities at 1/16 fall), so
                // it sags and swings like a normal rope seen smaller; it is 1/16 as thick, too.
                double r = scale.reach();
                SETTINGS.radius = scale.thickness(baseRadius);
                solver.step(a.x, a.y, a.z, b.x, b.y, b.z, down.x * r, down.y * r, down.z * r, SETTINGS, COLLIDER);
                cur = solver.copyPoints(cur);
                a1 = a;
                b1 = b;
                // A rope bent around a post or a corner is longer than the straight line between
                // its ends: let it have that length instead of fighting the blocks. (Grapple Unbound's former
                // lead overhaul, by the same author, wrapped leads around corners with explicit
                // bend points; here the rope's own collisions do it and this keeps it relaxed.)
                double stretch = solver.length() - restLength;
                if (stretch > restLength * 0.02 && touchingAny(solver)) {
                    wrapExtra = Math.min(wrapExtra + stretch * 0.5, chord);
                } else {
                    wrapExtra *= 0.9;
                }
            }
            int n = solver.count();
            if (light.length < n) light = new int[n];
            sampleLight(level, cur, n, light);
        }

        void render(ClientLevel level, VertexConsumer out, Matrix4f pose, Vec3 cam, float partialTick, Frustum frustum,
                    double halfWidth, float[] colors, float stripe) {
            int at = tier.ordinal() * 3;
            float r = colors[at], g = colors[at + 1], bl = colors[at + 2];
            Vec3 a = holderEnd(partialTick), b = entityEnd(partialTick);
            double[] points;
            int n;
            int[] pointLight;
            if (simulated && solver != null && prev.length >= solver.count() * 3) {
                n = solver.count();
                points = scratch(n);
                // interpolate the last two tick states, then bend the ends onto this frame's ends
                double ax = a.x - lerp(a0.x, a1.x, partialTick), ay = a.y - lerp(a0.y, a1.y, partialTick), az = a.z - lerp(a0.z, a1.z, partialTick);
                double bx = b.x - lerp(b0.x, b1.x, partialTick), by = b.y - lerp(b0.y, b1.y, partialTick), bz = b.z - lerp(b0.z, b1.z, partialTick);
                for (int i = 0; i < n; i++) {
                    double t = (double) i / (n - 1);
                    for (int c = 0; c < 3; c++) {
                        double base = lerp(prev[i * 3 + c], cur[i * 3 + c], partialTick);
                        double fix = c == 0 ? ax * (1 - t) + bx * t : c == 1 ? ay * (1 - t) + by * t : az * (1 - t) + bz * t;
                        points[i * 3 + c] = base + fix;
                    }
                }
                pointLight = light;
            } else {
                n = STATIC_POINTS;
                points = scratch(n);
                hangingCurve(a, b, restLength, gravity, n, points);
                pointLight = scratchLight;
                sampleLight(level, points, n, pointLight);
            }
            double w = scale.thickness(halfWidth);
            if (!visible(points, n, w, frustum)) return;
            MESH.draw(out, pose, points, n, pointLight, cam.x, cam.y, cam.z, w, r, g, bl, stripe, up.x, up.y, up.z);
        }
    }

    // ------------------------------------------------------------------ per tick

    /** Client tick end: gather the leads, step the near ones. */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            ROPES.clear();
            return;
        }
        if (mc.isPaused()) return;
        tickStamp++;
        refreshSettings();
        COLLIDER.begin(level);

        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        boolean ropes = UlConfig.on(UlConfig.ROPE_RENDERING);
        double renderDistance = UlConfig.num(UlConfig.ROPE_RENDER_DISTANCE);
        double simDistance = UlConfig.num(UlConfig.ROPE_SIMULATION_DISTANCE);
        int maxSimulated = UlConfig.num(UlConfig.ROPE_MAX_SIMULATED);

        List<Rope> live = new ArrayList<>();
        for (Entity entity : level.entitiesForRendering()) {
            Entity holder = Leashes.holder(entity);
            if (holder == null || holder.level() != level || !entity.isAlive()) continue;
            // Without rope rendering, mobs keep vanilla's leash line (or Vanilla Backport's) and only
            // leads vanilla has no renderer for are drawn here.
            if (!ropes && (entity instanceof Mob || VanillaBackport.present())) continue;
            if (LeashGeometry.centre(entity).distanceToSqr(cam) > renderDistance * renderDistance) continue;
            boolean quad = VanillaBackport.usesQuadLeash(entity, holder);
            int count = quad ? 4 : 1;
            for (int i = 0; i < count; i++) {
                long key = ((long) entity.getId() << 2) | i;
                Rope rope = ROPES.get(key);
                if (rope == null || rope.holder != holder || rope.quad != quad) {
                    rope = new Rope();
                    ROPES.put(key, rope);
                }
                rope.entity = entity;
                rope.holder = holder;
                rope.index = i;
                rope.quad = quad;
                rope.tier = LeadTiers.of(entity);
                rope.seen = tickStamp;
                live.add(rope);
            }
        }
        // Fence-to-fence ropes: drawn with or without rope rendering (vanilla has no line for them).
        // Keyed below zero, clear of the leads' keys.
        for (ClientTethers.Entry entry : ClientTethers.all()) {
            Entity knot = level.getEntity(entry.end());
            Entity other = level.getEntity(entry.other());
            if (knot == null || other == null || !knot.isAlive() || !other.isAlive()) continue;
            if (LeashGeometry.centre(knot).distanceToSqr(cam) > renderDistance * renderDistance) continue;
            long key = -1L - entry.id();
            Rope rope = ROPES.get(key);
            if (rope == null || rope.entity != knot || rope.holder != other) {
                rope = new Rope();
                ROPES.put(key, rope);
            }
            rope.entity = knot;
            rope.holder = other;
            rope.tether = true;
            rope.tetherLength = entry.length();
            rope.tier = LeadTier.byId(entry.tier());
            rope.seen = tickStamp;
            live.add(rope);
        }
        live.sort(Comparator.comparingDouble(r -> LeashGeometry.centre(r.entity).distanceToSqr(cam)));
        int simulated = 0;
        for (Rope rope : live) {
            boolean near = LeashGeometry.centre(rope.entity).distanceToSqr(cam) <= simDistance * simDistance;
            boolean simulate = ropes && near && simulated < maxSimulated;
            if (simulate) simulated++;
            rope.tick(level, simulate);
        }
        ROPES.values().removeIf(r -> r.seen != tickStamp);
    }

    private static void refreshSettings() {
        SETTINGS.gravity = UlConfig.num(UlConfig.ROPE_GRAVITY);
        SETTINGS.damping = UlConfig.num(UlConfig.ROPE_DAMPING);
        SETTINGS.collisions = UlConfig.on(UlConfig.ROPE_COLLISIONS);
        baseRadius = UlConfig.num(UlConfig.ROPE_THICKNESS) * 0.5;
        SETTINGS.radius = baseRadius;
    }

    // ------------------------------------------------------------------ per frame

    /** After entities are drawn: every rope, one batch. */
    public static void render(PoseStack poseStack, Vec3 cam, float partialTick, Frustum frustum) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || ROPES.isEmpty()) return;
        boolean ropes = UlConfig.on(UlConfig.ROPE_RENDERING);
        double halfWidth = (ropes ? UlConfig.num(UlConfig.ROPE_THICKNESS) : 0.025) * 0.5;
        // each lead's colour by what it is made of (plain, iron, diamond, netherite)
        for (LeadTier tier : LeadTier.values()) {
            int color = tier.ropeColor(), c = tier.ordinal() * 3;
            COLORS[c] = Argb.red(color) / 255.0F;
            COLORS[c + 1] = Argb.green(color) / 255.0F;
            COLORS[c + 2] = Argb.blue(color) / 255.0F;
        }
        float stripe = (float) UlConfig.num(UlConfig.ROPE_STRIPE_SHADE);

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer out = buffers.getBuffer(RopeRenderType.ROPE);
        Matrix4f pose = poseStack.last().pose();
        for (Rope rope : ROPES.values()) {
            if (!rope.entity.isAlive() || !rope.holder.isAlive()) continue;
            rope.render(level, out, pose, cam, partialTick, frustum, halfWidth, COLORS, stripe);
        }
        buffers.endBatch(RopeRenderType.ROPE);
    }

    public static void clear() {
        ROPES.clear();
    }

    /** Number of ropes currently tracked (for the debug overlay and tests). */
    public static int size() {
        return ROPES.size();
    }

    // ------------------------------------------------------------------ helpers

    private static double[] scratch(int n) {
        if (scratch.length < n * 3) scratch = new double[n * 3];
        if (scratchLight.length < n) scratchLight = new int[n];
        return scratch;
    }

    /**
     * A parabola of arc length about {@code length} between a and b, its slack bowed the way the
     * solver lays a fresh rope out: hanging along {@code gravity}, or sideways where there is none
     * (a rope at rest has the same shape in any gravity, so low gravity keeps the full sag).
     */
    private static void hangingCurve(Vec3 a, Vec3 b, double length, Vec3 gravity, int n, double[] out) {
        double chord = a.distanceTo(b);
        double sag = chord < 1.0E-6 ? length * 0.5 : Math.sqrt(3.0 * chord * Math.max(0.0, length - chord) / 8.0);
        double[] bow = RopeSolver.slackDirection(b.x - a.x, b.y - a.y, b.z - a.z, gravity.x, gravity.y, gravity.z);
        for (int i = 0; i < n; i++) {
            double t = (double) i / (n - 1);
            double s = 4.0 * sag * t * (1.0 - t);
            out[i * 3] = a.x + (b.x - a.x) * t + bow[0] * s;
            out[i * 3 + 1] = a.y + (b.y - a.y) * t + bow[1] * s;
            out[i * 3 + 2] = a.z + (b.z - a.z) * t + bow[2] * s;
        }
    }

    private static void sampleLight(ClientLevel level, double[] points, int n, int[] out) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < n; i++) {
            pos.set(points[i * 3], points[i * 3 + 1], points[i * 3 + 2]);
            out[i] = LightTexture.pack(level.getBrightness(LightLayer.BLOCK, pos), level.getBrightness(LightLayer.SKY, pos));
        }
    }

    private static boolean visible(double[] points, int n, double pad, Frustum frustum) {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            minX = Math.min(minX, points[i * 3]);
            minY = Math.min(minY, points[i * 3 + 1]);
            minZ = Math.min(minZ, points[i * 3 + 2]);
            maxX = Math.max(maxX, points[i * 3]);
            maxY = Math.max(maxY, points[i * 3 + 1]);
            maxZ = Math.max(maxZ, points[i * 3 + 2]);
        }
        return frustum.isVisible(new AABB(minX, minY, minZ, maxX, maxY, maxZ).inflate(pad + 0.1));
    }

    private static boolean touchingAny(RopeSolver solver) {
        for (int i = 1; i < solver.count() - 1; i++) {
            if (solver.touching(i)) return true;
        }
        return false;
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
