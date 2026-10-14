package net.camacraft.unleashedlead.gametest;

import net.camacraft.unleashedlead.UnleashedLead;
import net.camacraft.unleashedlead.leash.LeadTier;
import net.camacraft.unleashedlead.leash.LeadTiers;
import net.camacraft.unleashedlead.leash.LeashGeometry;
import net.camacraft.unleashedlead.leash.LeashPhysics;
import net.camacraft.unleashedlead.leash.Leashes;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import static net.camacraft.unleashedlead.gametest.TestUtil.PLATFORM;
import static net.camacraft.unleashedlead.gametest.TestUtil.fmt;

/**
 * Led animals (the owner, 2026-10-12: "grabbing an animal with a lead and trying to pull is very difficult ... the higher
 * tier leads should make it easier to wrangle ... a tamed horse on a lead should automatically walk with the player";
 * 2026-10-13: "Only tamed animals on a lead should walk with the player. Untamed animals should still be difficult to
 * wrangle"): a tamed mob walking after its holder keeps its holder's pace (server config {@code physics.ledHurry}) and,
 * on a taut lead by mass, puts only {@code physics.ledWeight} of its weight against it, an untamed one
 * {@code untamedLedWeight} (all of it), both divided by the lead's strength squared; a creative holder takes none. And the
 * client's pulls act once per tick of what they move ({@code LeashPhysics.OncePerTick}: a paused game piled them up).
 * The holder is a fake player moved every tick: walking 0.216 blocks a tick (4.3 m/s), sprinting 0.28 (5.6 m/s).
 */
@GameTestHolder(UnleashedLead.MOD_ID)
@PrefixGameTestTemplate(false)
public class LedPaceTests {
    private static final double WALK = 0.216;
    private static final double SPRINT = 0.28;
    /**
     * On top of the platform's stone. Its stone ends up at relative y 1 here, over a one-block gap: a pig at y 1 (as the
     * other suites put their mobs) falls into the gap and still walks there, a cow or a horse is too tall and sticks.
     */
    private static final double FLOOR = 2.0;

    /**
     * Held by a player 9 blocks off: an untamed cow with AI on the ground counts its whole 650 kg on a plain lead and a
     * sixteenth on a netherite one; a tamed horse 0.3 of its 450 kg; a cow without AI is immovable; and a cow held by a
     * creative player takes the whole pull, the player none.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 40)
    public static void aLedAnimalPutsItsShareOfItsWeightOnTheLead(GameTestHelper helper) {
        Cow plain = TestUtil.idle(helper, EntityType.COW, new Vec3(3.5, FLOOR, 2.5));
        Cow netherite = TestUtil.idle(helper, EntityType.COW, new Vec3(3.5, FLOOR, 7.5));
        Horse tamed = TestUtil.idle(helper, EntityType.HORSE, new Vec3(3.5, FLOOR, 12.5));
        tamed.setTamed(true);
        Cow dead = TestUtil.idle(helper, EntityType.COW, new Vec3(3.5, FLOOR, 17.5));
        dead.setNoAi(true); // a mob without AI is moved by nothing: the lead can't move it (infinitely heavy)
        Cow creativeLed = TestUtil.idle(helper, EntityType.COW, new Vec3(3.5, FLOOR, 21.5));
        plain.setLeashedTo(TestUtil.player(helper, new Vec3(12.5, FLOOR, 2.5)), true);
        netherite.setLeashedTo(TestUtil.player(helper, new Vec3(12.5, FLOOR, 7.5)), true);
        LeadTiers.set(netherite, LeadTier.NETHERITE);
        tamed.setLeashedTo(TestUtil.player(helper, new Vec3(12.5, FLOOR, 12.5)), true);
        dead.setLeashedTo(TestUtil.player(helper, new Vec3(12.5, FLOOR, 17.5)), true);
        FakePlayer creative = TestUtil.player(helper, new Vec3(12.5, FLOOR, 21.5));
        creative.setGameMode(GameType.CREATIVE);
        creativeLed.setLeashedTo(creative, true);
        helper.runAfterDelay(6, () -> {
            double kgPlain = kg(plain), kgNether = kg(netherite), kgTamed = kg(tamed), kgDead = kg(dead);
            LeashPhysics.Shares c = LeashPhysics.lastShares(creativeLed);
            String got = "untamed cow " + fmt(kgPlain) + " kg, on netherite " + fmt(kgNether) + " kg, tamed horse " + fmt(kgTamed)
                    + " kg, no AI " + fmt(kgDead) + " kg, creative holder " + (c == null ? "none" : fmt(c.holderKg()) + " kg, cow's share " + fmt(c.entityShare()));
            if (Math.abs(kgPlain - 650.0) > 1.0) helper.fail("an untamed cow should count all 650 kg: " + got);
            if (Math.abs(kgNether - 650.0 / 16.0) > 0.5) helper.fail("an untamed cow on a netherite lead should count 40.6 kg: " + got);
            if (Math.abs(kgTamed - 450.0 * 0.3) > 1.0) helper.fail("a tamed horse should count 135 kg: " + got);
            if (!Double.isInfinite(kgDead)) helper.fail("a cow without AI should not be wrangled (immovable): " + got);
            if (c == null || !Double.isInfinite(c.holderKg()) || c.entityShare() != 1.0) helper.fail("a creative holder must take none of the pull: " + got);
            UnleashedLead.LOGGER.info("[led pace] shares: {}", got);
            helper.succeed();
        });
    }

    private static double kg(Entity e) {
        LeashPhysics.Shares s = LeashPhysics.lastShares(e);
        return s == null ? -1 : s.entityKg();
    }

    /** A player walks 15 blocks at 4.3 m/s with an untamed cow on a plain lead: it does not keep up (it is dragged, not led). */
    @GameTest(template = PLATFORM, timeoutTicks = 140)
    public static void anUntamedCowDoesNotKeepUpWithAWalkingPlayer(GameTestHelper helper) {
        Cow cow = TestUtil.idle(helper, EntityType.COW, new Vec3(3.5, FLOOR, 12.5));
        FakePlayer player = TestUtil.player(helper, new Vec3(6.5, FLOOR, 12.5));
        cow.setLeashedTo(player, true);
        int ticks = (int) Math.round(15.0 / WALK);
        int[] tick = {0};
        double[] halfway = {Double.NaN};
        helper.onEachTick(() -> {
            int t = tick[0]++;
            if (t < ticks) {
                Vec3 at = helper.absoluteVec(new Vec3(6.5 + WALK * (t + 1), FLOOR, 12.5));
                player.setPos(at.x, at.y, at.z);
            }
            if (t == ticks / 2) halfway[0] = cow.getX();
        });
        helper.runAfterDelay(ticks + 1, () -> {
            double mps = (cow.getX() - halfway[0]) * 20.0 / (ticks + 1 - ticks / 2);
            boolean snapped = Leashes.holder(cow) != player;
            String got = (snapped ? "snapped, " : "gap " + fmt(LeashGeometry.span(cow, player)) + ", ") + fmt(mps) + " m/s over the second half";
            if (!snapped && mps > 3.0) helper.fail("an untamed cow kept a walking player's pace: " + got);
            UnleashedLead.LOGGER.info("[led pace] untamed cow: {}", got);
            helper.succeed();
        });
    }

    /** A tamed horse at the slowest horse speed keeps up with a player walking 15 blocks (4.3 m/s). */
    @GameTest(template = PLATFORM, timeoutTicks = 140)
    public static void aLedTamedHorseKeepsUpWithAWalkingPlayer(GameTestHelper helper) {
        Horse horse = TestUtil.idle(helper, EntityType.HORSE, new Vec3(3.5, FLOOR, 12.5));
        horse.setTamed(true);
        horse.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.1125);
        keepsUp(helper, horse, new Vec3(6.5, FLOOR, 12.5), WALK, 3.4);
    }

    /** A tamed horse at the slowest horse speed keeps up with a player sprinting 15 blocks (5.6 m/s). */
    @GameTest(template = PLATFORM, timeoutTicks = 140)
    public static void aLedTamedHorseKeepsUpWithASprintingPlayer(GameTestHelper helper) {
        Horse horse = TestUtil.idle(helper, EntityType.HORSE, new Vec3(3.5, FLOOR, 12.5));
        horse.setTamed(true);
        horse.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.1125); // the slowest a horse is born with
        keepsUp(helper, horse, new Vec3(6.5, FLOOR, 12.5), SPRINT, 4.4);
    }

    /**
     * The holder walks east from {@code start} 15 blocks at {@code pace}; the led mob must still be tied at the end, no
     * more than 8 blocks behind, and over the second half have moved at least {@code minMps} m/s.
     */
    private static void keepsUp(GameTestHelper helper, Entity led, Vec3 start, double pace, double minMps) {
        FakePlayer player = TestUtil.player(helper, start);
        ((net.minecraft.world.entity.Mob) led).setLeashedTo(player, true);
        int ticks = (int) Math.round(15.0 / pace);
        int[] tick = {0};
        double[] halfway = {Double.NaN};
        helper.onEachTick(() -> {
            int t = tick[0]++;
            if (t < ticks) {
                Vec3 at = helper.absoluteVec(start.add(pace * (t + 1), 0.0, 0.0));
                player.setPos(at.x, at.y, at.z);
            }
            if (t == ticks / 2) halfway[0] = led.getX();
        });
        helper.runAfterDelay(ticks + 1, () -> {
            double gap = LeashGeometry.span(led, player);
            double mps = (led.getX() - halfway[0]) * 20.0 / (ticks + 1 - ticks / 2);
            String got = "gap " + fmt(gap) + ", " + fmt(mps) + " m/s over the second half (holder " + fmt(pace * 20.0) + ")";
            if (Leashes.holder(led) != player) helper.fail("the lead snapped: " + got);
            if (gap > 8.0) helper.fail("fell behind: " + got);
            if (mps < minMps) helper.fail("too slow: " + got);
            UnleashedLead.LOGGER.info("[led pace] {}: {}", led.getType().getDescriptionId(), got);
            helper.succeed();
        });
    }

    /** {@code OncePerTick}: a pull keyed to an entity acts once per tick of that entity, never twice without one. */
    @GameTest(template = PLATFORM, timeoutTicks = 20)
    public static void aClientPullActsOncePerEntityTick(GameTestHelper helper) {
        Pig pig = TestUtil.idle(helper, EntityType.PIG, new Vec3(5.5, FLOOR, 5.5));
        LeashPhysics.OncePerTick gate = new LeashPhysics.OncePerTick();
        if (!gate.fresh(pig)) helper.fail("the first call must act");
        if (gate.fresh(pig) || gate.fresh(pig)) helper.fail("a second call in the same entity tick must not act (a paused game)");
        helper.runAfterDelay(2, () -> {
            if (!gate.fresh(pig)) helper.fail("after the pig ticked the pull must act again");
            helper.succeed();
        });
    }
}
