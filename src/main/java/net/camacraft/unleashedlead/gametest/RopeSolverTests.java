package net.camacraft.unleashedlead.gametest;

import net.camacraft.unleashedlead.UnleashedLead;
import net.camacraft.unleashedlead.rope.BlockCollider;
import net.camacraft.unleashedlead.rope.RopeLength;
import net.camacraft.unleashedlead.rope.RopeSolver;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import static net.camacraft.unleashedlead.gametest.TestUtil.PLATFORM;
import static net.camacraft.unleashedlead.gametest.TestUtil.fmt;

/**
 * The rope the client draws, run headless: the same {@link RopeSolver} and {@link BlockCollider}
 * the renderer uses, against this test's real blocks. Nothing here needs a client.
 */
@GameTestHolder(UnleashedLead.MOD_ID)
@PrefixGameTestTemplate(false)
public class RopeSolverTests {
    private static final double TOLERANCE = 0.03;

    /** Hanging free, the rope settles at its length (within 3%) with both ends exactly where pinned. */
    @GameTest(template = PLATFORM, timeoutTicks = 20)
    public static void ropeKeepsItsLengthAndEnds(GameTestHelper helper) {
        Vec3 a = helper.absoluteVec(new Vec3(4.0, 10.0, 4.0));
        Vec3 b = helper.absoluteVec(new Vec3(8.0, 10.0, 4.0));
        double rest = 5.0;
        int n = RopeLength.points(rest, 0.3, 40);
        RopeSolver rope = new RopeSolver(n);
        rope.reset(a.x, a.y, a.z, b.x, b.y, b.z, rest, n, 0, -1, 0);
        BlockCollider collider = new BlockCollider();
        RopeSolver.Settings settings = new RopeSolver.Settings();
        for (int t = 0; t < 200; t++) {
            collider.begin(helper.getLevel());
            rope.step(a.x, a.y, a.z, b.x, b.y, b.z, 0, -1, 0, settings, collider);
        }
        double error = Math.abs(rope.length() - rest) / rest;
        double sag = a.y - lowest(rope);
        if (error > TOLERANCE) {
            helper.fail("length " + fmt(rope.length()) + " is off its rest length " + rest + " by " + fmt(error * 100) + "%");
        } else if (!pinned(rope, a, b)) {
            helper.fail("an end moved off its pin");
        } else if (sag < 1.0) {
            helper.fail("a slack rope should sag under gravity, sagged " + fmt(sag));
        } else if (rope.maxSpeed() > 1.0e-3) {
            helper.fail("the rope has not settled: " + rope.maxSpeed());
        } else {
            helper.succeed();
        }
    }

    /** Draped over a pillar, the rope rests on its top and no point ends up inside a block. */
    @GameTest(template = PLATFORM, timeoutTicks = 20)
    public static void ropeDrapesOverABlock(GameTestHelper helper) {
        for (int y = 1; y <= 4; y++) helper.setBlock(new BlockPos(10, y, 6), Blocks.STONE);
        Vec3 a = helper.absoluteVec(new Vec3(8.5, 7.0, 6.5));
        Vec3 b = helper.absoluteVec(new Vec3(12.5, 7.0, 6.5));
        double top = helper.absoluteVec(new Vec3(0, 5.0, 0)).y;
        double rest = 7.5;
        int n = RopeLength.points(rest, 0.3, 40);
        RopeSolver rope = new RopeSolver(n);
        // laid out taut above the pillar, then paid out so it falls onto it
        rope.reset(a.x, a.y, a.z, b.x, b.y, b.z, a.distanceTo(b), n, 0, -1, 0);
        rope.setRestLength(rest);
        BlockCollider collider = new BlockCollider();
        RopeSolver.Settings settings = new RopeSolver.Settings();
        for (int t = 0; t < 300; t++) {
            collider.begin(helper.getLevel());
            rope.step(a.x, a.y, a.z, b.x, b.y, b.z, 0, -1, 0, settings, collider);
        }
        double pillarMinX = helper.absoluteVec(new Vec3(10.0, 0, 0)).x, pillarMaxX = pillarMinX + 1.0;
        double lowestOver = Double.MAX_VALUE;
        int resting = 0;
        for (int i = 0; i < rope.count(); i++) {
            if (rope.x(i) > pillarMinX && rope.x(i) < pillarMaxX) {
                lowestOver = Math.min(lowestOver, rope.y(i));
                if (rope.touching(i)) resting++;
            }
            BlockPos cell = BlockPos.containing(rope.x(i), rope.y(i), rope.z(i));
            if (!helper.getLevel().getBlockState(cell).getCollisionShape(helper.getLevel(), cell).isEmpty()
                    && i > 0 && i < rope.count() - 1) {
                helper.fail("point " + i + " is inside a block at " + cell);
                return;
            }
        }
        if (lowestOver == Double.MAX_VALUE) {
            helper.fail("no point lies over the pillar");
        } else if (lowestOver < top - 0.01) {
            helper.fail("the rope sank into the pillar: " + fmt(lowestOver) + " < " + fmt(top));
        } else if (lowestOver > top + 0.1 || resting == 0) {
            helper.fail("the rope is not resting on the pillar (lowest " + fmt(lowestOver) + ", top " + fmt(top) + ")");
        } else if (!pinned(rope, a, b)) {
            helper.fail("an end moved off its pin");
        } else {
            helper.succeed();
        }
    }

    /** With one end running away and the rope paying out, the length tracks within tolerance and the ends stay pinned. */
    @GameTest(template = PLATFORM, timeoutTicks = 20)
    public static void ropeFollowsAMovingEnd(GameTestHelper helper) {
        Vec3 a = helper.absoluteVec(new Vec3(2.0, 12.0, 12.0));
        double rest = 3.0;
        int n = RopeLength.points(rest, 0.3, 40);
        RopeSolver rope = new RopeSolver(n);
        Vec3 b = a.add(2.5, 0, 0);
        rope.reset(a.x, a.y, a.z, b.x, b.y, b.z, rest, n, 0, -1, 0);
        RopeSolver.Settings settings = new RopeSolver.Settings();
        double worst = 0.0;
        for (int t = 1; t <= 60; t++) {
            b = a.add(2.5 + 0.25 * t, 0, 0);
            double chord = b.distanceTo(a);
            rest = RopeLength.approach(rest, RopeLength.target(chord, 5.0, 0.35), chord, 0.35);
            rope.setRestLength(rest);
            int want = RopeLength.points(rest, 0.3, 40);
            if (Math.abs(want - rope.count()) >= 2) rope.resize(want);
            rope.step(a.x, a.y, a.z, b.x, b.y, b.z, 0, -1, 0, settings, RopeSolver.NO_COLLISION);
            worst = Math.max(worst, Math.abs(rope.length() - rest) / rest);
            if (!pinned(rope, a, b)) {
                helper.fail("an end came off its pin at tick " + t);
                return;
            }
        }
        if (worst > 0.05) {
            helper.fail("length drifted " + fmt(worst * 100) + "% from the rest length while paying out");
        } else {
            helper.succeed();
        }
    }

    private static boolean pinned(RopeSolver rope, Vec3 a, Vec3 b) {
        int last = rope.count() - 1;
        return rope.x(0) == a.x && rope.y(0) == a.y && rope.z(0) == a.z
                && rope.x(last) == b.x && rope.y(last) == b.y && rope.z(last) == b.z;
    }

    private static double lowest(RopeSolver rope) {
        double min = Double.MAX_VALUE;
        for (int i = 0; i < rope.count(); i++) min = Math.min(min, rope.y(i));
        return min;
    }
}
