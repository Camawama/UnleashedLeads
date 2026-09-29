package net.camacraft.unleashedlead.gametest;

import net.camacraft.camafy.compat.scale.EntityScale;
import net.camacraft.camafy.weight.EntityMass;
import net.camacraft.camafy.weight.Weights;
import net.camacraft.unleashedlead.UlConfig;
import net.camacraft.unleashedlead.UnleashedLead;
import net.camacraft.unleashedlead.leash.LeadScale;
import net.camacraft.unleashedlead.leash.LeashGeometry;
import net.camacraft.unleashedlead.leash.LeashPhysics;
import net.camacraft.unleashedlead.rope.RopeScale;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import virtuoel.pehkui.api.ScaleData;
import virtuoel.pehkui.api.ScaleTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static net.camacraft.unleashedlead.gametest.TestUtil.PLATFORM;
import static net.camacraft.unleashedlead.gametest.TestUtil.fmt;

/**
 * Leads on mobs Pehkui has resized (Genesis bundles Pehkui 3.8.2). Only compiled and run with
 * {@code -Ppehkui}, which puts Pehkui on the dev runtime.
 *
 * <p>The bug: Pehkui multiplies every self-driven move by the mob's motion scale, so a pig shrunk
 * to 1/16 moved 1/16 of the velocity the lead gave it (at most 0.6 / 16 = 0.04 blocks a tick,
 * against a walking player's 0.2), fell behind, and its lead snapped at 12 blocks. A pig grown to
 * 4 was pulled four times too hard. The lead now works in real blocks per tick (Camafy's
 * {@code EntityScale.motion}), and measures itself from an enlarged body rather than its centre.
 *
 * <p>The second report: a player and a pig both shrunk to 1/16 had a normal-sized lead between
 * them, as thick as the pig and five blocks long. The lead is now sized for its ends
 * ({@code LeadScale}, {@code RopeScale}): 1/16 thick and 1/16 long between two ends at 1/16, 1/16
 * thick but normally long when one end (or a fence knot) is normal-sized.
 */
@GameTestHolder(UnleashedLead.MOD_ID)
@PrefixGameTestTemplate(false)
public class PehkuiLeashTests {
    private static final float TINY = 1.0F / 16.0F;
    /**
     * Where the platform's floor is: its stone is the block layer at y = 1, so its top is at 2 (the
     * other suites' mobs, stood at 1, drop through into the air gap under it; a pig grown to 4
     * doesn't fit there).
     */
    private static final double FLOOR = 2.0;
    private static final float BIG = 4.0F;

    /** Sets Pehkui's base scale at once (no easing), which resizes the box and the motion. */
    static void resize(Entity entity, float scale) {
        ScaleData base = ScaleTypes.BASE.getScaleData(entity);
        base.setScaleTickDelay(0);
        base.setScale(scale);
    }

    /** Pehkui really resized it: its box, and the motion scale Camafy reads. */
    private static String checkResized(Entity entity, float scale, double normalWidth) {
        double width = entity.getBbWidth();
        if (Math.abs(width - normalWidth * scale) > 1.0E-4 * Math.max(1.0, normalWidth * scale)) {
            return entity.getType().toShortString() + " box is " + fmt(width) + " wide, not " + fmt(normalWidth * scale);
        }
        double motion = EntityScale.motion(entity);
        if (Math.abs(motion - scale) > 1.0E-6 * scale) {
            return "Camafy reads a motion scale of " + motion + " for a pig at " + scale + " (Pehkui bridge or scaledMotion off?)";
        }
        return null;
    }

    // ------------------------------------------------------------------ the reported bug

    /**
     * The user's report: a pig 16 times smaller, led by a player walking away, lost its lead.
     * It now stays leashed, is dragged the whole way, and ends up near the player.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public static void tinyPigFollowsAWalkingPlayer(GameTestHelper helper) {
        followWalkingPlayer(helper, TINY, 1.0F);
    }

    /** A pig grown to 4 follows too, without being thrown at or past the player. */
    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public static void bigPigFollowsAWalkingPlayer(GameTestHelper helper) {
        followWalkingPlayer(helper, BIG, 1.0F);
    }

    /**
     * The second report's setup: a player and a pig both at 1/16. Their lead is a lead at 1/16, and
     * the pig follows its (1/16-paced) player over 15/16 of a block exactly as a normal pig follows
     * a normal player over 15 blocks, staying within 7/16 of a block and never snapping.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public static void tinyPigFollowsATinyWalkingPlayer(GameTestHelper helper) {
        followWalkingPlayer(helper, TINY, TINY);
    }

    /**
     * A pig at {@code scale} on a lead held by a (fake) player at {@code playerScale} 2 blocks from
     * its body, who then walks 15 blocks along +X at 0.2 blocks a tick (walking pace) and stops.
     * Distances and the pace are times the lead's length scale ({@code LeadScale.reach}: 1 unless
     * both are small) and the player's size, so with a normal player it is the original test.
     */
    private static void followWalkingPlayer(GameTestHelper helper, float scale, float playerScale) {
        Pig pig = TestUtil.idle(helper, EntityType.PIG, new Vec3(3.5, FLOOR, 12.5));
        resize(pig, scale);
        String resized = checkResized(pig, scale, 0.9);
        if (resized != null) {
            helper.fail(resized);
            return;
        }
        double startX = LeashGeometry.centre(pig).x;
        double halfWidth = pig.getBbWidth() / 2.0;
        FakePlayer player = TestUtil.player(helper, new Vec3(3.5, FLOOR, 12.5));
        if (playerScale != 1.0F) resize(player, playerScale);
        double r = LeadScale.reach(pig, player);
        if (Math.abs(r - Math.max(Math.min(scale, 1.0), Math.min(playerScale, 1.0))) > 1.0E-5) {
            helper.fail("the lead's length scale is " + r + " for a pig at " + scale + " and a player at " + playerScale);
            return;
        }
        Vec3 from = new Vec3(3.5 + halfWidth + 2.0 * r, FLOOR, 12.5);
        Vec3 at0 = helper.absoluteVec(from);
        player.setPos(at0.x, at0.y, at0.z);
        pig.setLeashedTo(player, true);
        double walk = 15.0 * r, speed = 0.2 * playerScale;
        int walkTicks = (int) Math.round(walk / speed);

        double[] maxSpan = {0.0};
        double[] maxSpeed = {0.0}; // real, blocks per tick across the ground
        double[] maxY = {Double.NEGATIVE_INFINITY}; // highest feet while being dragged
        Vec3[] last = {null};
        int[] tick = {0};
        int[] lostAt = {-1};
        helper.onEachTick(() -> {
            int t = tick[0]++;
            if (t < walkTicks) {
                Vec3 at = helper.absoluteVec(from.add(speed * (t + 1), 0.0, 0.0));
                player.setPos(at.x, at.y, at.z);
            }
            Vec3 now = pig.position();
            if (last[0] != null && t > 20) maxSpeed[0] = Math.max(maxSpeed[0], now.subtract(last[0]).horizontalDistance());
            if (t > 20 && t < walkTicks) maxY[0] = Math.max(maxY[0], now.y);
            last[0] = now;
            if (pig.isLeashed()) {
                maxSpan[0] = Math.max(maxSpan[0], LeashGeometry.span(pig, player));
            } else if (lostAt[0] < 0) {
                lostAt[0] = t;
            }
        });
        helper.runAfterDelay(walkTicks + 60, () -> {
            double moved = LeashGeometry.centre(pig).x - startX;
            double span = LeashGeometry.span(pig, player);
            double lift = maxY[0] - pig.getY(); // it ends standing on the floor
            String report = "scale " + scale + " (player " + playerScale + ", lead x" + fmt(r) + "): moved " + fmt(moved) + ", widest " + fmt(maxSpan[0])
                    + ", end " + fmt(span) + ", top speed " + fmt(maxSpeed[0]) + " blocks/tick, lifted up to "
                    + fmt(lift) + " while dragged";
            UnleashedLead.LOGGER.info("[pehkui test] {}", report);
            if (lostAt[0] >= 0 || !pig.isLeashed()) {
                helper.fail("the lead broke at tick " + lostAt[0] + " (" + report + ")");
            } else if (moved < walk - 6.0 * r) {
                helper.fail("the pig was not dragged along: " + report);
            } else if (span > 7.0 * r) {
                helper.fail("the pig was left behind: " + report);
            } else if (maxSpeed[0] > 1.0 * r) {
                helper.fail("the lead threw the pig: " + report);
            } else if (lift > Math.max(0.1 * r, pig.getBbHeight())) {
                helper.fail("the pig was lifted off the ground like a kite: " + report);
            } else {
                helper.succeed();
            }
        });
    }

    // ------------------------------------------------------------------ the pull, exactly

    /**
     * One pull step gives the same change whatever the size, {@code stiffness * (span - free)}
     * toward the holder, in lead units: across the ground in REAL blocks per tick (16 times the
     * own-unit change for the tiny pig, a quarter for the big one), up and down in the pig's own
     * units (so it is lifted against its own Pehkui-scaled weight like a normal pig). Before the
     * fix it was all in own units: 1/16 and 4 times the real pull across the ground.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 20)
    public static void pullIsTheSameInRealBlocksAtAnySize(GameTestHelper helper) {
        float[] scales = {1.0F, TINY, BIG};
        int[] rows = {3, 9, 17};
        List<String> problems = new ArrayList<>();
        for (int i = 0; i < scales.length; i++) {
            LeashFenceKnotEntity knot = TestUtil.knot(helper, new BlockPos(2, (int) FLOOR, rows[i]));
            double height = 0.9 * scales[i];
            Pig pig = TestUtil.still(helper, EntityType.PIG, TestUtil.feetAtDistance(helper, knot, 8.5, FLOOR, height, 1));
            resize(pig, scales[i]);
            String resized = checkResized(pig, scales[i], 0.9);
            if (resized != null) {
                problems.add(resized);
                continue;
            }
            pig.setLeashedTo(knot, true);
            pig.setDeltaMovement(Vec3.ZERO);
            LeashPhysics.Tick step = LeashPhysics.tick(pig, knot, true);
            Vec3 own = pig.getDeltaMovement();
            // Lead units: across the ground in real blocks per tick, up and down in the pig's own.
            double m = EntityScale.motion(pig);
            Vec3 lead = new Vec3(own.x * m, own.y, own.z * m);
            Vec3 n = LeashGeometry.centre(knot).subtract(LeashGeometry.centre(pig)).normalize(); // toward the knot
            double expected = Math.min(UlConfig.num(UlConfig.STIFFNESS) * (step.distance() - UlConfig.num(UlConfig.FREE_LENGTH)),
                    UlConfig.num(UlConfig.MAX_PULL));
            double along = lead.dot(n);
            double across = lead.subtract(n.scale(along)).length();
            UnleashedLead.LOGGER.info("[pehkui test] scale {}: span {}, own velocity {}, real {} (expected {} toward the knot)",
                    scales[i], fmt(step.distance()), TestUtil.fmt(own), TestUtil.fmt(EntityScale.realVelocity(pig, own)), fmt(expected));
            if (!step.taut()) {
                problems.add("scale " + scales[i] + ": not taut at span " + fmt(step.distance()));
            } else if (Math.abs(along - expected) > 1.0E-9 || across > 1.0E-9) {
                problems.add("scale " + scales[i] + ": real pull " + fmt(along) + " (+" + across + " across), expected " + fmt(expected));
            }
        }
        if (problems.isEmpty()) helper.succeed();
        else helper.fail(String.join("; ", problems));
    }

    // ------------------------------------------------------------------ snapping

    /**
     * Snapping is still at 12. A tiny pig is measured centre to centre as at normal size: it
     * holds at 11 and snaps at 12.5. A pig grown to 4 is measured from its body: 12.5 blocks from
     * its centre is only about 11 of lead (it holds), 14.5 is about 13 (it snaps).
     */
    @GameTest(template = PLATFORM, timeoutTicks = 60)
    public static void scaledPigsStillSnapPast12(GameTestHelper helper) {
        LeashFenceKnotEntity k1 = TestUtil.knot(helper, new BlockPos(2, (int) FLOOR, 2));
        LeashFenceKnotEntity k2 = TestUtil.knot(helper, new BlockPos(2, (int) FLOOR, 6));
        LeashFenceKnotEntity k3 = TestUtil.knot(helper, new BlockPos(2, (int) FLOOR, 12));
        LeashFenceKnotEntity k4 = TestUtil.knot(helper, new BlockPos(2, (int) FLOOR, 19));
        Pig tinyNear = scaledAt(helper, k1, 11.0, TINY);
        Pig tinyFar = scaledAt(helper, k2, 12.5, TINY);
        Pig bigNear = scaledAt(helper, k3, 12.5, BIG);
        Pig bigFar = scaledAt(helper, k4, 14.5, BIG);
        double spanTinyNear = LeashGeometry.span(tinyNear, k1), spanTinyFar = LeashGeometry.span(tinyFar, k2);
        double spanBigNear = LeashGeometry.span(bigNear, k3), spanBigFar = LeashGeometry.span(bigFar, k4);
        UnleashedLead.LOGGER.info("[pehkui test] spans: tiny {} / {}, big {} / {} (centres 11 / 12.5, 12.5 / 14.5)",
                fmt(spanTinyNear), fmt(spanTinyFar), fmt(spanBigNear), fmt(spanBigFar));
        if (Math.abs(spanTinyNear - 11.0) > 1.0E-6 || Math.abs(spanTinyFar - 12.5) > 1.0E-6) {
            helper.fail("a tiny pig's lead isn't measured centre to centre: " + fmt(spanTinyNear) + ", " + fmt(spanTinyFar));
            return;
        }
        if (spanBigNear < 10.5 || spanBigNear > 11.5 || spanBigFar < 12.5 || spanBigFar > 13.5) {
            helper.fail("a big pig's lead isn't measured from its body: " + fmt(spanBigNear) + ", " + fmt(spanBigFar));
            return;
        }
        helper.runAfterDelay(4, () -> {
            if (!tinyNear.isLeashed()) helper.fail("a tiny pig's lead snapped at 11 blocks");
            else if (tinyFar.isLeashed()) helper.fail("a tiny pig's lead held at 12.5 blocks");
            else if (TestUtil.itemsNear(helper, tinyFar, Items.LEAD, 3.0) < 1) helper.fail("the tiny pig's snapped lead dropped nothing");
            else if (!bigNear.isLeashed()) helper.fail("a big pig's lead snapped with " + fmt(spanBigNear) + " blocks of lead out");
            else if (bigFar.isLeashed()) helper.fail("a big pig's lead held with " + fmt(spanBigFar) + " blocks of lead out");
            else helper.succeed();
        });
    }

    private static Pig scaledAt(GameTestHelper helper, LeashFenceKnotEntity knot, double centres, float scale) {
        Pig pig = TestUtil.idle(helper, EntityType.PIG, TestUtil.feetAtDistance(helper, knot, centres, FLOOR, 0.9 * scale, 1));
        resize(pig, scale);
        pig.setLeashedTo(knot, true);
        return pig;
    }

    // ------------------------------------------------------------------ the lead's own size

    /**
     * The sizes Pehkui gives (a vanilla baby shrunk to 1/16 is 1/16, a knot is always 1), and the
     * lead each pair gets: as thick as the smaller end, as long as the larger (never longer than
     * normal). Only two small ends shorten it.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 20)
    public static void leadIsSizedForItsEnds(GameTestHelper helper) {
        Pig pig = TestUtil.still(helper, EntityType.PIG, new Vec3(4.5, FLOOR, 4.5));
        Pig tinyPig = TestUtil.still(helper, EntityType.PIG, new Vec3(8.5, FLOOR, 4.5));
        Pig tinyPiglet = TestUtil.still(helper, EntityType.PIG, new Vec3(11.5, FLOOR, 4.5));
        Pig bigPig = TestUtil.still(helper, EntityType.PIG, new Vec3(17.5, FLOOR, 6.5));
        tinyPiglet.setBaby(true);
        resize(tinyPig, TINY);
        resize(tinyPiglet, TINY);
        resize(bigPig, BIG);
        FakePlayer player = TestUtil.player(helper, new Vec3(4.5, FLOOR, 16.5));
        FakePlayer tinyPlayer = TestUtil.player(helper, new Vec3(9.5, FLOOR, 16.5));
        FakePlayer bigPlayer = TestUtil.player(helper, new Vec3(16.5, FLOOR, 16.5));
        resize(tinyPlayer, TINY);
        resize(bigPlayer, BIG);
        LeashFenceKnotEntity knot = TestUtil.knot(helper, new BlockPos(2, (int) FLOOR, 21));

        List<String> problems = new ArrayList<>();
        size(problems, "pig", pig, 1.0);
        size(problems, "pig at 1/16", tinyPig, TINY);
        size(problems, "piglet at 1/16", tinyPiglet, TINY);
        size(problems, "pig at 4", bigPig, BIG);
        size(problems, "player", player, 1.0);
        size(problems, "player at 1/16", tinyPlayer, TINY);
        size(problems, "player at 4", bigPlayer, BIG);
        size(problems, "knot", knot, 1.0);
        lead(problems, "tiny pig, tiny player", tinyPig, tinyPlayer, TINY, TINY);
        lead(problems, "tiny piglet, tiny player", tinyPiglet, tinyPlayer, TINY, TINY);
        lead(problems, "tiny pig, player", tinyPig, player, TINY, 1.0);
        lead(problems, "pig, tiny player", pig, tinyPlayer, TINY, 1.0);
        lead(problems, "tiny pig, knot", tinyPig, knot, TINY, 1.0);
        lead(problems, "big pig, big player", bigPig, bigPlayer, BIG, 1.0);
        lead(problems, "big pig, player", bigPig, player, 1.0, 1.0);
        lead(problems, "big pig, tiny player", bigPig, tinyPlayer, TINY, 1.0);
        if (LeadScale.of(pig, player) != RopeScale.NORMAL) problems.add("a normal pig and player don't get RopeScale.NORMAL");
        double free = LeashPhysics.freeLength(tinyPig, tinyPlayer), snap = LeashPhysics.snapDistance(tinyPig, tinyPlayer);
        if (Math.abs(free - 5.0 / 16.0) > 1.0E-5 || Math.abs(snap - 12.0 / 16.0) > 1.0E-5) {
            problems.add("the tiny pair's lead is " + free + " free, snapping at " + snap + " (not 5/16 and 12/16)");
        }
        if (LeashPhysics.freeLength(tinyPig, player) != 5.0 || LeashPhysics.snapDistance(tinyPig, knot) != 12.0) {
            problems.add("a tiny pig's lead to a normal player or a knot isn't 5 free, 12 to snap");
        }
        if (problems.isEmpty()) helper.succeed();
        else helper.fail(String.join("; ", problems));
    }

    private static void size(List<String> problems, String what, Entity entity, double expected) {
        double size = LeadScale.size(entity);
        if (Math.abs(size - expected) > 1.0E-5 * expected) problems.add(what + " has size " + size + ", expected " + expected);
    }

    private static void lead(List<String> problems, String what, Entity entity, Entity holder, double size, double reach) {
        RopeScale scale = LeadScale.of(entity, holder);
        if (Math.abs(scale.size() - size) > 1.0E-5 * size || Math.abs(scale.reach() - reach) > 1.0E-5 * reach) {
            problems.add(what + ": " + scale + ", expected size " + size + " and reach " + reach);
        }
    }

    /**
     * Between a player and pigs all at 1/16, the lead snaps past 12/16 of a block (11/16 holds,
     * 13/16 snaps and drops its lead), and pulls past 5/16 exactly as a normal lead pulls past 5:
     * {@code stiffness * stretch} toward the player, in the same lead units as a normal lead
     * ({@code pullIsTheSameInRealBlocksAtAnySize}). A normal player's lead on a tiny pig keeps the
     * normal lengths: 13/16 of a block is nothing to it.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 60)
    public static void tinyPairLeadSnapsAndPullsAtItsScale(GameTestHelper helper) {
        FakePlayer holder = TestUtil.player(helper, new Vec3(12.5, FLOOR, 4.5));
        resize(holder, TINY);
        Pig near = tinyPigNear(helper, holder, 11.0 / 16.0, 1);
        Pig far = tinyPigNear(helper, holder, 13.0 / 16.0, -1);
        FakePlayer normalHolder = TestUtil.player(helper, new Vec3(12.5, FLOOR, 10.5));
        Pig farFromNormal = tinyPigNear(helper, normalHolder, 13.0 / 16.0, 1);

        // the pull, measured on a pig with no AI (nothing else moves it)
        FakePlayer puller = TestUtil.player(helper, new Vec3(12.5, FLOOR, 17.5));
        resize(puller, TINY);
        Pig pulled = TestUtil.still(helper, EntityType.PIG, TestUtil.feetAtDistance(helper, puller, 0.5, FLOOR, 0.9 * TINY, 1));
        resize(pulled, TINY);
        pulled.setLeashedTo(puller, true);
        pulled.setDeltaMovement(Vec3.ZERO);
        LeashPhysics.Tick step = LeashPhysics.tick(pulled, puller, true);
        Vec3 own = pulled.getDeltaMovement();
        double m = EntityScale.motion(pulled);
        Vec3 lead = new Vec3(own.x * m, own.y, own.z * m); // lead units, as in pullIsTheSameInRealBlocksAtAnySize
        Vec3 n = LeashGeometry.centre(puller).subtract(LeashGeometry.centre(pulled)).normalize();
        // free for 5/16 of a block (reach is 1/16 to within Pehkui's float rounding, checked below)
        double expected = UlConfig.num(UlConfig.STIFFNESS) * (step.distance() - UlConfig.num(UlConfig.FREE_LENGTH) * step.reach());
        double along = lead.dot(n), across = lead.subtract(n.scale(along)).length();
        UnleashedLead.LOGGER.info("[pehkui test] tiny pair: span {}, reach {}, pull {} (expected {}), spans {} / {} / {}",
                fmt(step.distance()), step.reach(), along, expected,
                fmt(LeashGeometry.span(near, holder)), fmt(LeashGeometry.span(far, holder)), fmt(LeashGeometry.span(farFromNormal, normalHolder)));
        if (!step.taut() || Math.abs(step.reach() - TINY) > 1.0E-5) {
            helper.fail("a tiny pair's lead isn't taut at span " + fmt(step.distance()) + " (reach " + step.reach() + ")");
            return;
        }
        if (Math.abs(along - expected) > 1.0E-9 || across > 1.0E-9) {
            helper.fail("a tiny pair's pull is " + along + " (+" + across + " across), expected " + expected);
            return;
        }
        helper.runAfterDelay(4, () -> {
            if (!near.isLeashed()) helper.fail("a tiny pair's lead snapped at 11/16 of a block");
            else if (far.isLeashed()) helper.fail("a tiny pair's lead held at 13/16 of a block");
            else if (TestUtil.itemsNear(helper, far, Items.LEAD, 3.0) < 1) helper.fail("the tiny pair's snapped lead dropped nothing");
            else if (!farFromNormal.isLeashed()) helper.fail("a normal player's lead on a tiny pig snapped at 13/16 of a block");
            else helper.succeed();
        });
    }

    /** A pig at 1/16, its centre {@code centres} from {@code holder}'s along X, leashed to it. */
    private static Pig tinyPigNear(GameTestHelper helper, Entity holder, double centres, int sign) {
        Pig pig = TestUtil.idle(helper, EntityType.PIG, TestUtil.feetAtDistance(helper, holder, centres, FLOOR, 0.9 * TINY, sign));
        resize(pig, TINY);
        pig.setLeashedTo(holder, true);
        return pig;
    }

    // ------------------------------------------------------------------ where the rope is drawn

    /**
     * The rope meets a resized pig at its resized collar (vanilla builds it from the eye height
     * and box width, which Pehkui scales), and leaves a resized player's hand at its resized
     * hand (vanilla's is a fixed block below the top of the box: under the feet of a tiny player).
     */
    @GameTest(template = PLATFORM, timeoutTicks = 20)
    public static void collarAndHandFollowSize(GameTestHelper helper) {
        Pig normal = TestUtil.still(helper, EntityType.PIG, new Vec3(4.5, FLOOR, 4.5));
        Pig tiny = TestUtil.still(helper, EntityType.PIG, new Vec3(9.5, FLOOR, 4.5));
        Pig big = TestUtil.still(helper, EntityType.PIG, new Vec3(16.5, FLOOR, 6.5));
        resize(tiny, TINY);
        resize(big, BIG);
        Vec3 collar = collar(normal), collarTiny = collar(tiny), collarBig = collar(big);

        FakePlayer p = TestUtil.player(helper, new Vec3(4.5, FLOOR, 14.5));
        FakePlayer pTiny = TestUtil.player(helper, new Vec3(9.5, FLOOR, 14.5));
        FakePlayer pTwo = TestUtil.player(helper, new Vec3(16.5, FLOOR, 14.5));
        resize(pTiny, TINY);
        resize(pTwo, 2.0F);
        Vec3 hand = hand(p), handTiny = hand(pTiny), handTwo = hand(pTwo);
        UnleashedLead.LOGGER.info("[pehkui test] collar offsets {} / {} / {}, hand offsets {} / {} / {}",
                TestUtil.fmt(collar), TestUtil.fmt(collarTiny), TestUtil.fmt(collarBig),
                TestUtil.fmt(hand), TestUtil.fmt(handTiny), TestUtil.fmt(handTwo));

        List<String> problems = new ArrayList<>();
        sameShape("tiny pig collar", collarTiny, collar, TINY, problems);
        sameShape("big pig collar", collarBig, collar, BIG, problems);
        sameShape("tiny player hand", handTiny, hand, TINY, problems);
        sameShape("2x player hand", handTwo, hand, 2.0F, problems);
        if (handTiny.y <= 0.0 || handTiny.y >= pTiny.getBbHeight()) {
            problems.add("tiny player's hand is outside its body height: " + fmt(handTiny.y) + " of " + fmt(pTiny.getBbHeight()));
        }
        if (problems.isEmpty()) helper.succeed();
        else helper.fail(String.join("; ", problems));
    }

    private static Vec3 collar(Pig pig) {
        return LeashGeometry.leashedPoint(pig, 1.0F).subtract(pig.position());
    }

    private static Vec3 hand(FakePlayer player) {
        return LeashGeometry.holderPoint(player, 1.0F).subtract(player.position());
    }

    /** {@code scaled} is {@code normal} times {@code scale}: same height, same reach (yaw may differ). */
    private static void sameShape(String what, Vec3 scaled, Vec3 normal, float scale, List<String> problems) {
        double tolerance = 0.02 * Math.max(normal.length() * scale, 1.0E-3);
        double reach = Math.sqrt(scaled.x * scaled.x + scaled.z * scaled.z);
        double normalReach = Math.sqrt(normal.x * normal.x + normal.z * normal.z) * scale;
        if (Math.abs(scaled.y - normal.y * scale) > tolerance || Math.abs(reach - normalReach) > tolerance) {
            problems.add(what + " " + TestUtil.fmt(scaled) + " is not " + scale + " x " + TestUtil.fmt(normal));
        }
    }

    // ------------------------------------------------------------------ Camafy's weights

    /**
     * Camafy's weight model (which Full Stop uses) with Pehkui: fixed weights scale with volume,
     * babies are lighter, slimes are volume x density, and a resize counts at once, not after the
     * cache's second. Checked here because this is the dev runtime with Pehkui on it; the table is
     * logged for the report.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 20)
    public static void camafyWeightsFollowPehkuiSize(GameTestHelper helper) {
        List<String> rows = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        float[] playerScales = {1.0F, TINY, 2.0F, 16.0F};
        for (int i = 0; i < playerScales.length; i++) {
            FakePlayer player = TestUtil.player(helper, new Vec3(3.5 + 4 * i, FLOOR, 3.5));
            resize(player, playerScales[i]);
            double s = playerScales[i];
            expect(rows, problems, "player x" + label(s), player, Math.min(75.0 * s * s * s, 100000.0));
        }
        Pig pig = TestUtil.still(helper, EntityType.PIG, new Vec3(3.5, FLOOR, 9.5));
        Pig tinyPig = TestUtil.still(helper, EntityType.PIG, new Vec3(7.5, FLOOR, 9.5));
        resize(tinyPig, TINY);
        expect(rows, problems, "pig x1", pig, 150.0);
        expect(rows, problems, "pig x1/16", tinyPig, 150.0 / 4096.0);
        Pig bigPig = TestUtil.still(helper, EntityType.PIG, new Vec3(12.5, FLOOR, 9.5));
        resize(bigPig, BIG);
        expect(rows, problems, "pig x4", bigPig, 150.0 * 64.0);

        Cow cow = TestUtil.still(helper, EntityType.COW, new Vec3(3.5, FLOOR, 14.5));
        Cow calf = TestUtil.still(helper, EntityType.COW, new Vec3(7.5, FLOOR, 14.5));
        calf.setBaby(true);
        expect(rows, problems, "cow", cow, 650.0);
        expect(rows, problems, "calf (vanilla baby)", calf, 650.0 * 0.125);

        int[] sizes = {1, 2, 4};
        for (int i = 0; i < sizes.length; i++) {
            Slime slime = TestUtil.still(helper, EntityType.SLIME, new Vec3(4.5 + 6 * i, FLOOR, 20.5));
            slime.setSize(sizes[i], true);
            double w = slime.getBbWidth(), h = slime.getBbHeight();
            expect(rows, problems, "slime size " + sizes[i], slime, w * w * h * 140.0);
            if (Weights.measureNow(slime).basis() != EntityMass.Basis.VOLUME) problems.add("slime size " + sizes[i] + " isn't weighed by volume");
        }
        Slime tinySlime = TestUtil.still(helper, EntityType.SLIME, new Vec3(20.5, FLOOR, 14.5));
        tinySlime.setSize(4, true);
        resize(tinySlime, TINY);
        double tw = tinySlime.getBbWidth(), th = tinySlime.getBbHeight();
        expect(rows, problems, "slime size 4 x1/16", tinySlime, tw * tw * th * 140.0);

        // The cache: weighed at 150, shrunk to half, weighed again straight away.
        Pig resized = TestUtil.still(helper, EntityType.PIG, new Vec3(18.5, FLOOR, 9.5));
        double before = Weights.bodyMassKg(resized);
        resize(resized, 0.5F);
        double after = Weights.bodyMassKg(resized);
        rows.add(String.format(Locale.ROOT, "%-22s cached %.4f kg, then halved in size: %.4f kg on the same tick", "pig (cache)", before, after));
        if (Math.abs(before - 150.0) > 1.0E-6 || Math.abs(after - 150.0 / 8.0) > 1.0E-6) {
            problems.add("the cached weight didn't follow a resize: " + before + " -> " + after);
        }

        UnleashedLead.LOGGER.info("[pehkui test] Camafy weights with Pehkui:\n{}", String.join("\n", rows));
        if (problems.isEmpty()) helper.succeed();
        else helper.fail(String.join("; ", problems));
    }

    private static void expect(List<String> rows, List<String> problems, String name, Entity entity, double expectedKg) {
        EntityMass mass = Weights.measureNow(entity);
        double kg = Weights.bodyMassKg(entity);
        rows.add(String.format(Locale.ROOT, "%-22s box %.4f x %.4f  body %.6g kg  (%s, expected %.6g)",
                name, entity.getBbWidth(), entity.getBbHeight(), kg, mass.basis(), expectedKg));
        if (Math.abs(kg - expectedKg) > 1.0E-4 * Math.max(expectedKg, 1.0E-3) || Math.abs(mass.bodyKg() - kg) > 1.0E-9 * Math.max(kg, 1.0)) {
            problems.add(name + " weighs " + kg + " kg, expected " + expectedKg);
        }
    }

    private static String label(double s) {
        return s < 1.0 ? "1/" + Math.round(1.0 / s) : String.valueOf(Math.round(s));
    }
}
