package net.camacraft.unleashedlead.gametest;

import net.camacraft.camafy.physics.PhysicsAspect;
import net.camacraft.camafy.physics.PhysicsOwnership;
import net.camacraft.unleashedlead.UnleashedLead;
import net.camacraft.unleashedlead.leash.LeashGeometry;
import net.camacraft.unleashedlead.leash.LeashPhysics;
import net.camacraft.unleashedlead.leash.Leashes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import static net.camacraft.unleashedlead.gametest.TestUtil.PLATFORM;
import static net.camacraft.unleashedlead.gametest.TestUtil.fmt;

/**
 * The leash as a force: where it snaps, when it pulls, and that a taut lead only ever takes away
 * speed along the rope. Mobs have no AI here, so nothing but the lead moves them.
 *
 * <p>With Vanilla Backport installed its leash tick runs pigs, but the pull, the distances and
 * the snap are still this mod's (through its hooks), so every test here holds with it too.
 */
@GameTestHolder(UnleashedLead.MOD_ID)
@PrefixGameTestTemplate(false)
public class LeashPhysicsTests {

    /** 1.21.6 / Bedrock: the lead holds at 11 blocks and snaps past 12 (1.20.1 snapped at 10). */
    @GameTest(template = PLATFORM, timeoutTicks = 60)
    public static void leadSnapsPast12NotAt11(GameTestHelper helper) {
        LeashFenceKnotEntity nearKnot = TestUtil.knot(helper, new BlockPos(2, 1, 6));
        LeashFenceKnotEntity farKnot = TestUtil.knot(helper, new BlockPos(2, 1, 16));
        Pig near = TestUtil.idle(helper, EntityType.PIG, TestUtil.feetAtDistance(helper, nearKnot, 11.0, 1.0, 0.9, 1));
        Pig far = TestUtil.idle(helper, EntityType.PIG, TestUtil.feetAtDistance(helper, farKnot, 12.5, 1.0, 0.9, 1));
        near.setLeashedTo(nearKnot, true);
        far.setLeashedTo(farKnot, true);
        double dNear = TestUtil.distance(near, nearKnot);
        double dFar = TestUtil.distance(far, farKnot);
        helper.runAfterDelay(4, () -> {
            if (!near.isLeashed()) {
                helper.fail("the lead snapped at " + fmt(dNear) + " blocks");
            } else if (far.isLeashed()) {
                helper.fail("the lead held at " + fmt(dFar) + " blocks");
            } else if (TestUtil.itemsNear(helper, far, Items.LEAD, 3.0) < 1) {
                helper.fail("the snapped lead dropped no lead item");
            } else {
                helper.succeed();
            }
        });
    }

    /**
     * Past the free length (5) the lead pulls toward the holder; within it, it adds nothing. The
     * force is read straight off one physics step (mobs with no AI, which vanilla never moves); a
     * second, free-moving pig 8.5 blocks out shows the pull bringing it in.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 80)
    public static void pullsOnlyPastFreeLength(GameTestHelper helper) {
        LeashFenceKnotEntity knotA = TestUtil.knot(helper, new BlockPos(2, 1, 4));
        LeashFenceKnotEntity knotB = TestUtil.knot(helper, new BlockPos(2, 1, 12));
        LeashFenceKnotEntity knotC = TestUtil.knot(helper, new BlockPos(2, 1, 20));
        Pig farProbe = TestUtil.still(helper, EntityType.PIG, TestUtil.feetAtDistance(helper, knotA, 8.5, 1.0, 0.9, 1));
        Pig nearProbe = TestUtil.still(helper, EntityType.PIG, TestUtil.feetAtDistance(helper, knotB, 3.5, 1.0, 0.9, 1));
        Pig walker = TestUtil.idle(helper, EntityType.PIG, TestUtil.feetAtDistance(helper, knotC, 8.5, 1.0, 0.9, 1));
        farProbe.setLeashedTo(knotA, true);
        nearProbe.setLeashedTo(knotB, true);
        walker.setLeashedTo(knotC, true);

        Vec3 drift = new Vec3(0.0, 0.0, 0.1); // sideways, so any pull shows as an X change
        farProbe.setDeltaMovement(drift);
        nearProbe.setDeltaMovement(drift);
        LeashPhysics.Tick far = LeashPhysics.tick(farProbe, knotA, true);
        LeashPhysics.Tick near = LeashPhysics.tick(nearProbe, knotB, true);
        Vec3 farV = farProbe.getDeltaMovement(), nearV = nearProbe.getDeltaMovement();
        if (!far.taut() || near.taut()) {
            helper.fail("taut at 8.5: " + far.taut() + ", taut at 3.5: " + near.taut());
            return;
        }
        if (farV.x >= -0.05) {
            helper.fail("8.5 blocks out, the lead added no pull toward the knot: " + fmt(farV));
            return;
        }
        if (!nearV.equals(drift)) {
            helper.fail("3.5 blocks out, the lead changed the velocity: " + fmt(drift) + " -> " + fmt(nearV));
            return;
        }
        double walkerStart = TestUtil.distance(walker, knotC);
        helper.runAfterDelay(40, () -> {
            double walkerEnd = TestUtil.distance(walker, knotC);
            if (!walker.isLeashed()) {
                helper.fail("the lead broke");
            } else if (walkerEnd > walkerStart - 1.5) {
                helper.fail("a pig 8.5 blocks out was not brought in: " + fmt(walkerStart) + " -> " + fmt(walkerEnd));
            } else if (walkerEnd < 1.0) {
                helper.fail("the pull threw the pig onto the knot: " + fmt(walkerEnd));
            } else {
                helper.succeed();
            }
        });
    }

    /**
     * A taut lead only takes speed away along the rope: an entity's speed across the rope is
     * untouched (what makes the pendulum), its speed away from the holder is cut.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 20)
    public static void tautLeadKeepsSpeedAcrossTheRope(GameTestHelper helper) {
        helper.setBlock(new BlockPos(12, 13, 12), net.minecraft.world.level.block.Blocks.OAK_FENCE);
        LeashFenceKnotEntity knot = LeashFenceKnotEntity.getOrCreateKnot(helper.getLevel(), helper.absolutePos(new BlockPos(12, 13, 12)));
        Vec3 k = helper.relativeVec(LeashGeometry.centre(knot));
        Pig pig = TestUtil.still(helper, EntityType.PIG, new Vec3(k.x + 4.0, k.y - 4.5 - 0.45, k.z));
        pig.setLeashedTo(knot, true);
        Vec3 n = LeashGeometry.centre(pig).subtract(LeashGeometry.centre(knot)).normalize();
        Vec3 across = new Vec3(0, 0, 1); // perpendicular to n, which lies in the XY plane
        Vec3 third = n.cross(across);
        Vec3 before = n.scale(0.3).add(across.scale(0.25));
        pig.setDeltaMovement(before);
        LeashPhysics.Tick tick = LeashPhysics.tick(pig, knot, true);
        Vec3 after = pig.getDeltaMovement();
        double acrossAfter = after.dot(across), thirdAfter = after.dot(third), outAfter = after.dot(n);
        if (!tick.taut()) {
            helper.fail("the lead should be taut at " + fmt(tick.distance()));
        } else if (Math.abs(acrossAfter - 0.25) > 1.0e-9 || Math.abs(thirdAfter) > 1.0e-9) {
            helper.fail("speed across the rope changed: " + fmt(before) + " -> " + fmt(after));
        } else if (outAfter >= 0.15) {
            helper.fail("speed away from the knot was not cut: " + fmt(before.dot(n)) + " -> " + fmt(outAfter));
        } else {
            helper.succeed();
        }
    }

    /**
     * A mob hanging from a knot over a drop swings through under it, stays on the lead, never
     * reaches the ground and takes no damage (Bedrock; Java has not built fall damage while
     * dangling since 1.19.3).
     */
    @GameTest(template = PLATFORM, timeoutTicks = 160)
    public static void hangingMobSwingsWithoutDamage(GameTestHelper helper) {
        BlockPos fence = new BlockPos(12, 12, 12); // floating fence: nothing under it to hit
        LeashFenceKnotEntity knot = TestUtil.knot(helper, fence);
        Vec3 k = helper.relativeVec(LeashGeometry.centre(knot));
        Pig pig = TestUtil.idle(helper, EntityType.PIG, new Vec3(k.x + 4.0, k.y - 4.0 - 0.45, k.z));
        pig.setLeashedTo(knot, true);
        float health = pig.getHealth();
        double[] minX = {Double.MAX_VALUE};
        double[] maxDist = {0.0};
        boolean[] grounded = {false};
        helper.onEachTick(() -> {
            Vec3 rel = LeashGeometry.centre(pig).subtract(LeashGeometry.centre(knot));
            minX[0] = Math.min(minX[0], rel.x);
            maxDist[0] = Math.max(maxDist[0], rel.length());
            grounded[0] |= pig.onGround();
        });
        helper.runAfterDelay(120, () -> {
            if (!pig.isLeashed()) {
                helper.fail("the pig fell off the lead (furthest " + fmt(maxDist[0]) + ")");
            } else if (grounded[0]) {
                helper.fail("the pig touched the ground");
            } else if (pig.getHealth() < health) {
                helper.fail("the pig took damage: " + pig.getHealth() + " < " + health);
            } else if (pig.fallDistance > 1.0F) {
                helper.fail("fall distance built up while hanging: " + pig.fallDistance);
            } else if (minX[0] > -0.5) {
                helper.fail("the pig did not swing through under the knot (min x " + fmt(minX[0]) + ")");
            } else {
                helper.succeed();
            }
        });
    }

    /** Another mod owning an entity's motion (MKW karts, grapples) means no lead can pull it or be tied to it. */
    @GameTest(template = PLATFORM, timeoutTicks = 40)
    public static void claimedMotionIsNeverPulledOrLeashed(GameTestHelper helper) {
        ResourceLocation other = ResourceLocation.fromNamespaceAndPath("othermod", "test");
        LeashFenceKnotEntity knot = TestUtil.knot(helper, new BlockPos(2, 1, 6));
        Pig pig = TestUtil.idle(helper, EntityType.PIG, TestUtil.feetAtDistance(helper, knot, 9.0, 1.0, 0.9, 1));
        pig.setLeashedTo(knot, true);
        PhysicsOwnership.claim(pig, other, PhysicsAspect.MOTION.bit, 200);
        Vec3 start = pig.position();

        Pig free = TestUtil.still(helper, EntityType.PIG, new Vec3(10.5, 1.0, 16.5));
        PhysicsOwnership.claim(free, other, PhysicsAspect.MOTION.bit, 200);
        var player = TestUtil.player(helper, new Vec3(11.5, 1.0, 16.5));
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(Items.LEAD));
        var result = player.interactOn(free, net.minecraft.world.InteractionHand.MAIN_HAND);

        helper.runAfterDelay(20, () -> {
            double moved = pig.position().subtract(start).horizontalDistance();
            if (moved > 0.05) {
                helper.fail("a claimed pig was pulled " + fmt(moved) + " blocks");
            } else if (Leashes.isLeashed(free)) {
                helper.fail("a claimed pig was leashed (" + result + ")");
            } else if (!Leashes.refused(free)) {
                helper.fail("a claimed pig is not refused");
            } else {
                PhysicsOwnership.release(pig, other);
                PhysicsOwnership.release(free, other);
                helper.succeed();
            }
        });
    }
}
