package net.camacraft.unleashedlead.gametest;

import net.camacraft.unleashedlead.UnleashedLead;
import net.camacraft.unleashedlead.leash.LeadScale;
import net.camacraft.unleashedlead.rope.BlockCollider;
import net.camacraft.unleashedlead.rope.RopeLength;
import net.camacraft.unleashedlead.rope.RopeScale;
import net.camacraft.unleashedlead.rope.RopeSolver;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Turtle;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

import static net.camacraft.unleashedlead.gametest.TestUtil.PLATFORM;
import static net.camacraft.unleashedlead.gametest.TestUtil.fmt;

/**
 * A lead sized for the things it joins ({@link RopeScale}, {@link LeadScale}): the rule, that a
 * rope at 1/16 is the normal rope at 1/16, and that normal-sized ends (babies and knots included)
 * give exactly the normal lead. None of this needs a size mod; the resized entities themselves are
 * tested in {@code PehkuiLeashTests} (-Ppehkui).
 */
@GameTestHolder(UnleashedLead.MOD_ID)
@PrefixGameTestTemplate(false)
public class LeadScaleTests {
    private static final double TINY = 1.0 / 16.0;

    /** Thickness follows the smaller end; lengths the larger, never above 1. */
    @GameTest(template = PLATFORM, timeoutTicks = 20)
    public static void leadIsSizedForBothEnds(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        expect(problems, "normal and normal", RopeScale.between(1.0, 1.0), 1.0, 1.0);
        if (RopeScale.between(1.0, 1.0) != RopeScale.NORMAL) problems.add("two normal ends aren't RopeScale.NORMAL");
        expect(problems, "tiny and tiny", RopeScale.between(TINY, TINY), TINY, TINY);
        expect(problems, "tiny holder, normal pig", RopeScale.between(1.0, TINY), TINY, 1.0);
        expect(problems, "normal holder, tiny pig", RopeScale.between(TINY, 1.0), TINY, 1.0);
        expect(problems, "quarter and tiny", RopeScale.between(0.25, TINY), TINY, 0.25);
        expect(problems, "big and big", RopeScale.between(4.0, 4.0), 4.0, 1.0);
        expect(problems, "big and tiny", RopeScale.between(4.0, TINY), TINY, 1.0);
        expect(problems, "almost normal", RopeScale.between(1.0 + 1.0E-6, 1.0 - 1.0E-6), 1.0, 1.0);
        expect(problems, "unknown sizes", RopeScale.between(Double.NaN, 0.0), 1.0, 1.0);
        expect(problems, "absurdly small", RopeScale.between(1.0E-9, 1.0E-9), RopeScale.MIN, RopeScale.MIN);
        expect(problems, "absurdly big", RopeScale.between(1.0E9, 1.0E9), RopeScale.MAX, 1.0);
        RopeScale tiny = RopeScale.between(TINY, TINY);
        if (tiny.length(5.0) != 5.0 / 16.0 || tiny.length(12.0) != 0.75 || tiny.thickness(0.05) != 0.05 / 16.0) {
            problems.add("a lead at 1/16 isn't 5/16 free, 12/16 to snap and 0.05/16 thick");
        }
        if (RopeScale.NORMAL.length(5.0) != 5.0 || RopeScale.NORMAL.thickness(0.05) != 0.05) {
            problems.add("a normal lead's lengths changed");
        }
        if (problems.isEmpty()) helper.succeed();
        else helper.fail(String.join("; ", problems));
    }

    private static void expect(List<String> problems, String what, RopeScale scale, double size, double reach) {
        if (scale.size() != size || scale.reach() != reach) {
            problems.add(what + ": " + scale + ", expected size " + size + " and reach " + reach);
        }
    }

    /**
     * Normal-sized ends are size 1 exactly, so their leads are exactly as before: an adult, a
     * vanilla baby (a calf is drawn half size, a baby turtle 0.3), a fence knot, a player.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 20)
    public static void normalEndsMakeANormalLead(GameTestHelper helper) {
        Pig pig = TestUtil.still(helper, EntityType.PIG, new Vec3(4.5, 2.0, 4.5));
        Cow calf = TestUtil.still(helper, EntityType.COW, new Vec3(8.5, 2.0, 4.5));
        calf.setBaby(true);
        Turtle hatchling = TestUtil.still(helper, EntityType.TURTLE, new Vec3(12.5, 2.0, 4.5));
        hatchling.setBaby(true);
        LeashFenceKnotEntity knot = TestUtil.knot(helper, new BlockPos(16, 2, 4));
        FakePlayer player = TestUtil.player(helper, new Vec3(4.5, 2.0, 10.5));
        List<String> problems = new ArrayList<>();
        size(problems, "pig", LeadScale.size(pig));
        size(problems, "calf (" + fmt(calf.getBbWidth()) + " wide)", LeadScale.size(calf));
        size(problems, "baby turtle (" + fmt(hatchling.getBbWidth()) + " wide)", LeadScale.size(hatchling));
        size(problems, "knot", LeadScale.size(knot));
        size(problems, "player", LeadScale.size(player));
        if (LeadScale.of(calf, player) != RopeScale.NORMAL || LeadScale.of(pig, knot) != RopeScale.NORMAL
                || LeadScale.of(hatchling, null) != RopeScale.NORMAL) {
            problems.add("normal ends don't give RopeScale.NORMAL");
        }
        if (problems.isEmpty()) helper.succeed();
        else helper.fail(String.join("; ", problems));
    }

    private static void size(List<String> problems, String what, double size) {
        if (size != 1.0) problems.add(what + " has size " + size + ", not 1");
    }

    /**
     * The rope a lead at 1/16 draws is the rope a normal lead draws, 1/16 as big: the same points,
     * at 1/16 of the distances, tick after tick, while one end walks off (the slack reeling in) and then
     * stops (the rope swings and settles). This runs the client's rope the way {@code ClientRopes}
     * does (free length, segment spacing and payout rate times {@link RopeScale#length}, gravity
     * times the reach), without blocks: those don't shrink.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 20)
    public static void tinyRopeIsTheNormalRopeScaledDown(GameTestHelper helper) {
        RopeScale tiny = RopeScale.between(TINY, TINY);
        Sim normal = new Sim(RopeScale.NORMAL);
        Sim small = new Sim(tiny);
        double worst = 0.0;
        for (int t = 0; t <= 120; t++) {
            // one end walks off along a slope for 40 ticks (staying inside the free length, so the
            // rope keeps its slack), then stops
            int walk = Math.min(t, 40);
            double bx = 1.0 + 0.05 * walk, by = 0.02 * walk, bz = 0.05 * walk;
            normal.tick(bx, by, bz);
            small.tick(bx, by, bz);
            if (normal.rope.count() != small.rope.count()) {
                helper.fail("tick " + t + ": " + small.rope.count() + " points at 1/16, " + normal.rope.count() + " normally");
                return;
            }
            for (int i = 0; i < normal.rope.count(); i++) {
                double ex = Math.abs(small.rope.x(i) / TINY - normal.rope.x(i));
                double ey = Math.abs(small.rope.y(i) / TINY - normal.rope.y(i));
                double ez = Math.abs(small.rope.z(i) / TINY - normal.rope.z(i));
                worst = Math.max(worst, Math.max(ex, Math.max(ey, ez)));
            }
        }
        UnleashedLead.LOGGER.info("[lead scale test] rope at 1/16 vs normal rope: {} points, worst difference {} blocks (at normal size), sag {} / {}",
                normal.rope.count(), worst, fmt(-lowest(normal.rope)), fmt(-lowest(small.rope)));
        if (worst > 1.0E-9) {
            helper.fail("the rope at 1/16 isn't the normal rope scaled down: off by " + worst + " blocks");
        } else if (-lowest(normal.rope) < 0.3) {
            helper.fail("the normal rope didn't sag (" + fmt(-lowest(normal.rope)) + "), so the comparison proves little");
        } else {
            helper.succeed();
        }
    }

    /** One rope stepped as {@code ClientRopes} steps it, with its ends at {@code scale.reach()} times the given positions. */
    private static final class Sim {
        final RopeScale scale;
        final RopeSolver.Settings settings = new RopeSolver.Settings();
        RopeSolver rope;
        double rest;

        Sim(RopeScale scale) {
            this.scale = scale;
            settings.gravity = 0.05; // the client config's defaults
            settings.damping = 0.06;
            settings.collisions = false;
        }

        void tick(double bx, double by, double bz) {
            double r = scale.reach();
            bx *= r;
            by *= r;
            bz *= r;
            double chord = Math.sqrt(bx * bx + by * by + bz * bz);
            double target = RopeLength.target(chord, scale.length(5.0), 0.35);
            double segment = scale.length(0.3);
            if (rope == null) {
                rest = target;
                int n = RopeLength.points(rest, segment, 40);
                rope = new RopeSolver(n);
                rope.reset(0, 0, 0, bx, by, bz, rest, n, 0, -1, 0);
                return;
            }
            rest = RopeLength.approach(rest, target, chord, scale.length(0.35));
            rope.setRestLength(rest);
            int want = RopeLength.points(rest, segment, 40);
            if (Math.abs(want - rope.count()) >= 2) rope.resize(want);
            settings.radius = scale.thickness(0.025);
            rope.step(0, 0, 0, bx, by, bz, 0, -r, 0, settings, RopeSolver.NO_COLLISION);
        }
    }

    /**
     * A rope at 1/16 lying on the ground rests on it at its own thickness (0.05 / 16 thick), not a
     * normal rope's: a normal rope's radius alone is almost half as tall as a pig at 1/16.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 20)
    public static void tinyRopeLiesOnTheGround(GameTestHelper helper) {
        for (int x = 3; x <= 6; x++) helper.setBlock(new BlockPos(x, 3, 4), Blocks.STONE);
        double top = helper.absoluteVec(new Vec3(0, 4.0, 0)).y;
        RopeScale tiny = RopeScale.between(TINY, TINY);
        // a tiny player's hand and a tiny pig's collar, 0.2 apart, just above the stone
        Vec3 a = helper.absoluteVec(new Vec3(4.5, 4.0 + 0.06, 4.5));
        Vec3 b = helper.absoluteVec(new Vec3(4.7, 4.0 + 0.03, 4.5));
        double chord = a.distanceTo(b);
        double rest = RopeLength.target(chord, tiny.length(5.0), 1.0); // all the slack, so it has to lie down
        int n = RopeLength.points(rest, tiny.length(0.3), 40);
        RopeSolver rope = new RopeSolver(n);
        rope.reset(a.x, a.y, a.z, b.x, b.y, b.z, rest, n, 0, -1, 0);
        RopeSolver.Settings settings = new RopeSolver.Settings();
        settings.radius = tiny.thickness(0.025);
        BlockCollider collider = new BlockCollider();
        for (int t = 0; t < 200; t++) {
            collider.begin(helper.getLevel());
            rope.step(a.x, a.y, a.z, b.x, b.y, b.z, 0, -tiny.reach(), 0, settings, collider);
        }
        double low = Double.MAX_VALUE;
        int resting = 0;
        for (int i = 1; i < rope.count() - 1; i++) {
            low = Math.min(low, rope.y(i));
            if (rope.touching(i)) resting++;
        }
        UnleashedLead.LOGGER.info("[lead scale test] tiny rope: {} points, rest length {}, lowest {} above the stone (radius {}), {} resting",
                rope.count(), fmt(rest), low - top, settings.radius, resting);
        if (resting == 0) {
            helper.fail("the tiny rope isn't lying on the stone (lowest " + (low - top) + " above it)");
        } else if (low < top + settings.radius - 1.0E-6) {
            helper.fail("the tiny rope sank into the stone: " + (low - top));
        } else if (low > top + 2.0 * settings.radius) {
            helper.fail("the tiny rope rests " + (low - top) + " above the stone, not at its own radius " + settings.radius);
        } else {
            helper.succeed();
        }
    }

    private static double lowest(RopeSolver rope) {
        double min = Double.MAX_VALUE;
        for (int i = 0; i < rope.count(); i++) min = Math.min(min, rope.y(i));
        return min;
    }
}
