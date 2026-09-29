package net.camacraft.unleashedlead.gametest;

import net.camacraft.camafy.compat.gravity.Gravity;
import net.camacraft.camafy.compat.gravity.GravitySources;
import net.camacraft.unleashedlead.UnleashedLead;
import net.camacraft.unleashedlead.leash.LeashGeometry;
import net.camacraft.unleashedlead.leash.LeashPhysics;
import net.camacraft.unleashedlead.rope.RopeLength;
import net.camacraft.unleashedlead.rope.RopeSolver;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

import static net.camacraft.unleashedlead.gametest.TestUtil.PLATFORM;
import static net.camacraft.unleashedlead.gametest.TestUtil.fmt;

/**
 * Zero and low gravity. VS Genesis's space flags every entity no-gravity, Ad Astra's orbits and
 * Gravity Unbound's fields change the pull, and the Forge {@code entity_gravity} attribute is the
 * standard way for any mod to do it: these tests use the attribute at 0 and at half, and the
 * no-gravity flag, as the stand-ins (Ad Astra and Genesis aren't on the dev runtime).
 */
@GameTestHolder(UnleashedLead.MOD_ID)
@PrefixGameTestTemplate(false)
public class ZeroGravityTests {
    private static final UUID TEST_MODIFIER = UUID.fromString("5a7c0f7e-7d0b-4a8e-9f55-0b1a9e3c2d11");

    /** Camafy's reading of the real gravity: exactly vanilla by default, then the attribute and the flag. */
    @GameTest(template = PLATFORM, timeoutTicks = 20)
    public static void camafyReadsLocalGravity(GameTestHelper helper) {
        Pig pig = TestUtil.still(helper, EntityType.PIG, new Vec3(12.5, 1.0, 12.5));
        ItemEntity item = new ItemEntity(helper.getLevel(), pig.getX(), pig.getY() + 2.0, pig.getZ(), new ItemStack(Items.STONE));
        item.setNoGravity(false);
        helper.getLevel().addFreshEntity(item);
        StringBuilder problems = new StringBuilder();

        check(problems, Gravity.strength(pig) == 1.0, "vanilla strength " + Gravity.strength(pig));
        check(problems, Gravity.isNormal(pig), "vanilla pig not normal");
        check(problems, Gravity.acceleration(pig).equals(new Vec3(0.0, -0.08, 0.0)), "vanilla acceleration " + Gravity.acceleration(pig));
        check(problems, Gravity.accelerationMps2(pig).equals(new Vec3(0.0, -9.81, 0.0)), "vanilla m/s² " + Gravity.accelerationMps2(pig));
        check(problems, Gravity.strength(item) == 1.0, "item strength " + Gravity.strength(item));
        check(problems, Gravity.ambientStrength(helper.getLevel(), pig.position()) == 1.0,
                "ambient " + Gravity.ambientStrength(helper.getLevel(), pig.position()));

        AttributeInstance gravity = pig.getAttribute(ForgeMod.ENTITY_GRAVITY.get());
        gravity.addTransientModifier(new AttributeModifier(TEST_MODIFIER, "ul test half", -0.5, AttributeModifier.Operation.MULTIPLY_TOTAL));
        check(problems, Math.abs(Gravity.strength(pig) - 0.5) < 1.0E-9, "half-gravity attribute read " + Gravity.strength(pig));
        check(problems, !Gravity.isNormal(pig), "half gravity counted as normal");
        check(problems, Gravity.isDefault(pig, net.camacraft.camafy.compat.gravity.FrameKind.MOVEMENT), "half gravity turned the frame");
        check(problems, Math.abs(GravitySources.attributeRatio(pig, TEST_MODIFIER) - 1.0) < 1.0E-12,
                "excluding the modifier gave " + GravitySources.attributeRatio(pig, TEST_MODIFIER));
        gravity.removeModifier(TEST_MODIFIER);
        gravity.addTransientModifier(new AttributeModifier(TEST_MODIFIER, "ul test zero", -1.0, AttributeModifier.Operation.MULTIPLY_TOTAL));
        check(problems, Gravity.strength(pig) == 0.0, "zero-gravity attribute read " + Gravity.strength(pig));
        check(problems, Gravity.isWeightless(pig), "zero gravity not weightless");
        check(problems, Gravity.acceleration(pig).equals(Vec3.ZERO), "zero-g acceleration " + Gravity.acceleration(pig));
        gravity.removeModifier(TEST_MODIFIER);
        check(problems, Gravity.strength(pig) == 1.0, "strength after removing the modifier " + Gravity.strength(pig));
        // vanilla's slow falling brakes a descent (and is on while standing); it doesn't lighten anything
        gravity.addTransientModifier(new AttributeModifier(GravitySources.SLOW_FALLING_ID, "Slow falling acceleration reduction",
                -0.07, AttributeModifier.Operation.ADDITION));
        check(problems, Gravity.strength(pig) == 1.0, "slow falling read as low gravity " + Gravity.strength(pig));
        gravity.removeModifier(GravitySources.SLOW_FALLING_ID);

        pig.setNoGravity(true);
        check(problems, Gravity.strength(pig) == 0.0 && Gravity.isWeightless(pig), "no-gravity pig strength " + Gravity.strength(pig));
        item.setNoGravity(true);
        check(problems, Gravity.strength(item) == 0.0, "no-gravity item strength " + Gravity.strength(item));
        pig.setNoGravity(false);
        check(problems, Gravity.strength(pig) == 1.0, "strength after clearing the flag " + Gravity.strength(pig));
        item.discard();

        if (problems.length() > 0) helper.fail(problems.toString());
        else helper.succeed();
    }

    /**
     * With no gravity a slack rope floats: laid out with its slack bowed sideways, it keeps its
     * length and ends and never hangs below the line between its ends.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 20)
    public static void slackRopeFloatsWithoutGravity(GameTestHelper helper) {
        Vec3 a = helper.absoluteVec(new Vec3(4.0, 10.0, 4.0));
        Vec3 b = helper.absoluteVec(new Vec3(8.0, 10.0, 4.0));
        double rest = 5.0;
        int n = RopeLength.points(rest, 0.3, 40);
        RopeSolver rope = new RopeSolver(n);
        rope.reset(a.x, a.y, a.z, b.x, b.y, b.z, rest, n, 0, 0, 0);
        RopeSolver.Settings settings = new RopeSolver.Settings();
        double lowest = Double.MAX_VALUE, widest = 0.0;
        for (int t = 0; t < 200; t++) {
            rope.step(a.x, a.y, a.z, b.x, b.y, b.z, 0, 0, 0, settings, RopeSolver.NO_COLLISION);
            for (int i = 0; i < rope.count(); i++) {
                lowest = Math.min(lowest, rope.y(i));
                widest = Math.max(widest, Math.abs(rope.z(i) - a.z));
            }
        }
        double error = Math.abs(rope.length() - rest) / rest;
        int last = rope.count() - 1;
        if (error > 0.03) {
            helper.fail("length " + fmt(rope.length()) + " is off its rest length by " + fmt(error * 100) + "%");
        } else if (rope.x(0) != a.x || rope.y(0) != a.y || rope.x(last) != b.x || rope.y(last) != b.y) {
            helper.fail("an end moved off its pin");
        } else if (a.y - lowest > 0.05) {
            helper.fail("the rope hung down with no gravity: " + fmt(a.y - lowest) + " below its ends");
        } else if (widest < 0.5) {
            helper.fail("the slack did not bow out sideways (" + fmt(widest) + ")");
        } else {
            helper.succeed();
        }
    }

    /** Gravity's strength reaches the rope: from the same floating start, Moon gravity pulls it down slower than Earth's. */
    @GameTest(template = PLATFORM, timeoutTicks = 20)
    public static void moonGravitySagsARopeSlower(GameTestHelper helper) {
        Vec3 a = helper.absoluteVec(new Vec3(4.0, 10.0, 4.0));
        Vec3 b = helper.absoluteVec(new Vec3(8.0, 10.0, 4.0));
        double rest = 5.0;
        int n = RopeLength.points(rest, 0.3, 40);
        RopeSolver earth = new RopeSolver(n), moon = new RopeSolver(n);
        earth.reset(a.x, a.y, a.z, b.x, b.y, b.z, rest, n, 0, 0, 0);
        moon.reset(a.x, a.y, a.z, b.x, b.y, b.z, rest, n, 0, 0, 0);
        RopeSolver.Settings settings = new RopeSolver.Settings();
        for (int t = 0; t < 10; t++) {
            earth.step(a.x, a.y, a.z, b.x, b.y, b.z, 0, -1, 0, settings, RopeSolver.NO_COLLISION);
            moon.step(a.x, a.y, a.z, b.x, b.y, b.z, 0, -0.166, 0, settings, RopeSolver.NO_COLLISION);
        }
        double earthSag = a.y - lowest(earth), moonSag = a.y - lowest(moon);
        if (!(moonSag > 0.01)) {
            helper.fail("Moon gravity did not pull the rope at all (" + fmt(moonSag) + ")");
        } else if (!(earthSag > moonSag * 2.0)) {
            helper.fail("Earth sag " + fmt(earthSag) + " should be well past the Moon's " + fmt(moonSag));
        } else {
            helper.succeed();
        }
    }

    /**
     * A weightless mob held past the lead's free length is pulled straight along the rope (up
     * toward a knot above it here) and never falls: one with its gravity attribute at zero (how a
     * gravity mod does it) and one flagged no-gravity (how VS Genesis's space does it). Neither
     * loses the lead, touches the ground or takes damage.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 120)
    public static void weightlessMobsArePulledAlongTheRope(GameTestHelper helper) {
        Pig byAttribute = floatingOnALead(helper, 6);
        byAttribute.getAttribute(ForgeMod.ENTITY_GRAVITY.get()).addTransientModifier(
                new AttributeModifier(TEST_MODIFIER, "ul test zero", -1.0, AttributeModifier.Operation.MULTIPLY_TOTAL));
        Pig byFlag = floatingOnALead(helper, 17);
        byFlag.setNoGravity(true);
        Pig[] pigs = {byAttribute, byFlag};
        String[] names = {"zero-attribute pig", "no-gravity pig"};
        double[] startY = {byAttribute.getY(), byFlag.getY()};
        double[] lowest = {startY[0], startY[1]};
        double[] risen = new double[2];
        double[] closest = {Double.MAX_VALUE, Double.MAX_VALUE};
        boolean[] grounded = new boolean[2];
        float[] health = {byAttribute.getHealth(), byFlag.getHealth()};
        long start = helper.getTick();
        helper.onEachTick(() -> {
            boolean early = helper.getTick() - start <= 30; // the pull, before any overshoot past the knot
            for (int i = 0; i < 2; i++) {
                if (early) {
                    lowest[i] = Math.min(lowest[i], pigs[i].getY());
                    risen[i] = Math.max(risen[i], pigs[i].getY() - startY[i]);
                }
                grounded[i] |= pigs[i].onGround();
                if (pigs[i].getLeashHolder() != null) {
                    closest[i] = Math.min(closest[i], TestUtil.distance(pigs[i], pigs[i].getLeashHolder()));
                }
            }
        });
        helper.runAfterDelay(100, () -> {
            StringBuilder problems = new StringBuilder();
            for (int i = 0; i < 2; i++) {
                Pig pig = pigs[i];
                double free = LeashPhysics.freeLength(pig);
                check(problems, Gravity.isWeightless(pig), names[i] + " is not weightless to Camafy");
                check(problems, pig.isLeashed(), names[i] + " lost the lead");
                check(problems, !grounded[i], names[i] + " touched the ground");
                check(problems, pig.getHealth() >= health[i], names[i] + " took damage");
                check(problems, startY[i] - lowest[i] < 0.1, names[i] + " sank " + fmt(startY[i] - lowest[i]) + " before the lead caught it, with no gravity");
                check(problems, risen[i] > 0.3, names[i] + " was not pulled up toward the knot (rose " + fmt(risen[i]) + ")");
                check(problems, closest[i] < free + 0.6, names[i] + " was never brought inside the lead's reach (closest " + fmt(closest[i]) + ")");
            }
            if (problems.length() > 0) helper.fail(problems.toString());
            else helper.succeed();
        });
    }

    /** A pig on a lead to a floating knot at relative z {@code z}, 3.5 blocks below and 5.5 across: past the free length. */
    private static Pig floatingOnALead(GameTestHelper helper, int z) {
        LeashFenceKnotEntity knot = TestUtil.knot(helper, new BlockPos(6, 12, z));
        Vec3 k = helper.relativeVec(LeashGeometry.centre(knot));
        Pig pig = TestUtil.idle(helper, EntityType.PIG, new Vec3(k.x + 5.5, k.y - 3.5 - 0.45, k.z));
        pig.setDeltaMovement(Vec3.ZERO);
        pig.setLeashedTo(knot, true);
        return pig;
    }

    private static double lowest(RopeSolver rope) {
        double min = Double.MAX_VALUE;
        for (int i = 0; i < rope.count(); i++) min = Math.min(min, rope.y(i));
        return min;
    }

    private static void check(StringBuilder problems, boolean ok, String what) {
        if (!ok) problems.append(problems.length() > 0 ? "; " : "").append(what);
    }
}
