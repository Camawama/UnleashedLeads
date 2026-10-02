package net.camacraft.unleashedlead.gametest;

import net.camacraft.camafy.physics.PhysicsAspect;
import net.camacraft.camafy.physics.PhysicsOwnership;
import net.camacraft.camafy.weight.Weights;
import net.camacraft.unleashedlead.UnleashedLead;
import net.camacraft.unleashedlead.leash.LeashChains;
import net.camacraft.unleashedlead.leash.LeashGeometry;
import net.camacraft.unleashedlead.leash.LeashPhysics;
import net.camacraft.unleashedlead.leash.Leashes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

import static net.camacraft.unleashedlead.gametest.TestUtil.PLATFORM;
import static net.camacraft.unleashedlead.gametest.TestUtil.fmt;

/**
 * Chains of mobs pull both ways (mobChains, the owner's report of 2026-10-01: two mobs tied
 * together could be dragged by one of them only; dragging the other snapped the lead). A mob is
 * "grabbed" here as Physgun Unbound grabs one: a MOTION claim by another mod, and its position set
 * every tick. Paces: walking 0.216 blocks a tick (4.3 m/s), sprinting 0.28 (5.6 m/s).
 */
@GameTestHolder(UnleashedLead.MOD_ID)
@PrefixGameTestTemplate(false)
public class ChainLeashTests {
    private static final double WALK = 0.216;
    private static final double SPRINT = 0.28;
    private static final double FLOOR = 1.0;
    /** How far a mob may trail the one it follows once the pull has settled (free length 5, plus the stretch a pace needs). */
    private static final double TRAIL = 8.0;
    private static final ResourceLocation GRAB = ResourceLocation.fromNamespaceAndPath("othermod", "grab");
    /** Their own batch, so the other suites' plots stay where they were (a plot moved onto a chunk another test releases stops ticking). */
    private static final String BATCH = "chains";

    /** The case that always worked: a player holds the top of a two-pig chain and walks; both follow, the bottom one fully. */
    @GameTest(template = PLATFORM, timeoutTicks = 200, batch = BATCH)
    public static void chainFollowsAPlayerHoldingItsTop(GameTestHelper helper) {
        Pig top = TestUtil.idle(helper, EntityType.PIG, new Vec3(5.5, FLOOR, 12.5));
        Pig bottom = TestUtil.idle(helper, EntityType.PIG, new Vec3(3.5, FLOOR, 12.5));
        FakePlayer player = TestUtil.player(helper, new Vec3(7.5, FLOOR, 12.5));
        bottom.setLeashedTo(top, true);
        top.setLeashedTo(player, true);
        drag(helper, player, new Vec3(7.5, FLOOR, 12.5), WALK, 14.0, List.of(top, bottom), List.of(player, top));
    }

    /** The owner's case: the mob tied to the other is the one dragged away; its holder follows instead of snapping the lead. */
    @GameTest(template = PLATFORM, timeoutTicks = 200, batch = BATCH)
    public static void chainFollowsItsGrabbedBottom(GameTestHelper helper) {
        Pig top = TestUtil.idle(helper, EntityType.PIG, new Vec3(5.5, FLOOR, 12.5));
        Pig bottom = TestUtil.idle(helper, EntityType.PIG, new Vec3(7.5, FLOOR, 12.5));
        bottom.setLeashedTo(top, true);
        grab(bottom);
        drag(helper, bottom, new Vec3(7.5, FLOOR, 12.5), WALK, 14.0, List.of(bottom), List.of(top));
    }

    /** As {@link #chainFollowsItsGrabbedBottom}, at a sprint. */
    @GameTest(template = PLATFORM, timeoutTicks = 200, batch = BATCH)
    public static void chainFollowsItsGrabbedBottomAtASprint(GameTestHelper helper) {
        Pig top = TestUtil.idle(helper, EntityType.PIG, new Vec3(5.5, FLOOR, 12.5));
        Pig bottom = TestUtil.idle(helper, EntityType.PIG, new Vec3(7.5, FLOOR, 12.5));
        bottom.setLeashedTo(top, true);
        grab(bottom);
        drag(helper, bottom, new Vec3(7.5, FLOOR, 12.5), SPRINT, 14.0, List.of(bottom), List.of(top));
    }

    /**
     * Three pigs, bottom tied to middle tied to top, three times over: the bottom, the middle or
     * the top is grabbed and dragged at a sprint, and the other two come along.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 200, batch = BATCH)
    public static void threePigChainFollowsWhicheverPigIsGrabbed(GameTestHelper helper) {
        List<Entity> grabbed = new ArrayList<>(), followers = new ArrayList<>(), leads = new ArrayList<>(), holders = new ArrayList<>();
        List<Vec3> starts = new ArrayList<>();
        for (int which = 0; which < 3; which++) {
            double z = 4.5 + 8.0 * which;
            Pig[] pigs = new Pig[3]; // bottom, middle, top
            for (int i = 0; i < 3; i++) {
                // the grabbed pig in front (+X), the others behind it in chain order
                int rank = Math.abs(i - which);
                double side = i == which ? 0.0 : (i < which ? -1.0 : 1.0) * (which == 1 ? 1.0 : 0.0);
                pigs[i] = TestUtil.idle(helper, EntityType.PIG, new Vec3(8.5 - 2.0 * rank, FLOOR, z + side));
            }
            pigs[0].setLeashedTo(pigs[1], true);
            pigs[1].setLeashedTo(pigs[2], true);
            grab(pigs[which]);
            grabbed.add(pigs[which]);
            starts.add(new Vec3(8.5, FLOOR, z));
            for (int i = 0; i < 3; i++) if (i != which) followers.add(pigs[i]);
            leads.add(pigs[0]);
            holders.add(pigs[1]);
            leads.add(pigs[1]);
            holders.add(pigs[2]);
        }
        int ticks = (int) Math.round(13.0 / SPRINT);
        int[] tick = {0};
        double[] widest = {0.0};
        String[] lost = {null};
        Vec3[] startCentres = followers.stream().map(LeashGeometry::centre).toArray(Vec3[]::new);
        helper.onEachTick(() -> {
            int t = tick[0]++;
            if (t < ticks) {
                for (int i = 0; i < grabbed.size(); i++) place(helper, grabbed.get(i), starts.get(i).add(SPRINT * (t + 1), 0.0, 0.0));
            }
            for (int i = 0; i < leads.size(); i++) {
                if (Leashes.holder(leads.get(i)) != holders.get(i)) {
                    if (lost[0] == null) lost[0] = "lead " + i + " lost at tick " + t + " (" + fmt(TestUtil.distance(leads.get(i), holders.get(i))) + " apart)";
                } else {
                    widest[0] = Math.max(widest[0], LeashGeometry.span(leads.get(i), holders.get(i)));
                }
            }
        });
        helper.runAfterDelay(ticks + 60, () -> {
            release(grabbed);
            StringBuilder moved = new StringBuilder(), ends = new StringBuilder();
            String fail = lost[0];
            for (int i = 0; i < followers.size(); i++) {
                double m = LeashGeometry.centre(followers.get(i)).x - startCentres[i].x;
                moved.append(fmt(m)).append(' ');
                if (fail == null && m < 5.0) fail = "follower " + i + " was not dragged along";
            }
            for (int i = 0; i < leads.size(); i++) {
                double span = TestUtil.distance(leads.get(i), holders.get(i));
                ends.append(fmt(span)).append(' ');
                if (fail == null && span > TRAIL) fail = "lead " + i + " left its mob behind";
            }
            String report = "followers moved " + moved + "end spans " + ends + "widest lead " + fmt(widest[0]);
            UnleashedLead.LOGGER.info("[chain test] three-pig chains: {}", report);
            if (fail != null) helper.fail(fail + ": " + report);
            else if (widest[0] > 11.0) helper.fail("a lead stretched almost to snapping: " + report);
            else helper.succeed();
        });
    }

    /**
     * A lead used on the bottom pig of a free chain takes the chain: the bottom pig is on your
     * lead, the top one now tied to it, nothing dropped. Done on a chain you already hold by its
     * top, your lead moves to the bottom pig (no new lead used). Done on a chain tied to a fence,
     * the pig is taken over alone as before (its old lead drops).
     */
    @GameTest(template = PLATFORM, timeoutTicks = 40, batch = BATCH)
    public static void leadOnAChainMobTakesTheChain(GameTestHelper helper) {
        // a free chain
        Pig top = TestUtil.idle(helper, EntityType.PIG, new Vec3(4.5, FLOOR, 4.5));
        Pig bottom = TestUtil.idle(helper, EntityType.PIG, new Vec3(6.5, FLOOR, 4.5));
        bottom.setLeashedTo(top, true);
        FakePlayer player = TestUtil.player(helper, new Vec3(8.5, FLOOR, 4.5));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.LEAD, 2));
        player.interactOn(bottom, InteractionHand.MAIN_HAND);
        int leftFree = player.getMainHandItem().getCount();

        // a chain the player already holds by its top
        Pig top2 = TestUtil.idle(helper, EntityType.PIG, new Vec3(4.5, FLOOR, 12.5));
        Pig bottom2 = TestUtil.idle(helper, EntityType.PIG, new Vec3(6.5, FLOOR, 12.5));
        FakePlayer player2 = TestUtil.player(helper, new Vec3(8.5, FLOOR, 12.5));
        bottom2.setLeashedTo(top2, true);
        top2.setLeashedTo(player2, true);
        player2.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.LEAD, 2));
        player2.interactOn(bottom2, InteractionHand.MAIN_HAND);
        int leftHeld = player2.getMainHandItem().getCount();

        // a chain tied to a fence
        LeashFenceKnotEntity knot = TestUtil.knot(helper, new BlockPos(2, 1, 20));
        Pig top3 = TestUtil.idle(helper, EntityType.PIG, new Vec3(4.5, FLOOR, 20.5));
        Pig bottom3 = TestUtil.idle(helper, EntityType.PIG, new Vec3(6.5, FLOOR, 20.5));
        top3.setLeashedTo(knot, true);
        bottom3.setLeashedTo(top3, true);
        FakePlayer player3 = TestUtil.player(helper, new Vec3(8.5, FLOOR, 20.5));
        player3.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.LEAD, 2));
        player3.interactOn(bottom3, InteractionHand.MAIN_HAND);

        helper.runAfterDelay(5, () -> {
            if (Leashes.holder(bottom) != player || Leashes.holder(top) != bottom) {
                helper.fail("free chain: bottom held by " + Leashes.holder(bottom) + ", top by " + Leashes.holder(top));
            } else if (leftFree != 1) {
                helper.fail("free chain: taking it used " + (2 - leftFree) + " leads");
            } else if (TestUtil.itemsNear(helper, bottom, Items.LEAD, 6.0) > 0) {
                helper.fail("free chain: a lead was dropped");
            } else if (Leashes.holder(bottom2) != player2 || Leashes.holder(top2) != bottom2) {
                helper.fail("held chain: bottom held by " + Leashes.holder(bottom2) + ", top by " + Leashes.holder(top2));
            } else if (leftHeld != 2) {
                helper.fail("held chain: moving your own lead used " + (2 - leftHeld) + " leads");
            } else if (TestUtil.itemsNear(helper, bottom2, Items.LEAD, 6.0) > 0) {
                helper.fail("held chain: a lead was dropped");
            } else if (Leashes.holder(bottom3) != player3 || Leashes.holder(top3) != knot) {
                helper.fail("chain on a fence: bottom held by " + Leashes.holder(bottom3) + ", top by " + Leashes.holder(top3));
            } else if (TestUtil.itemsNear(helper, bottom3, Items.LEAD, 6.0) < 1) {
                helper.fail("chain on a fence: the taken-over lead didn't drop");
            } else {
                helper.succeed();
            }
        });
    }

    /** After a lead takes a chain by its bottom pig, walking off drags the whole chain, the old top pig last. */
    @GameTest(template = PLATFORM, timeoutTicks = 200, batch = BATCH)
    public static void chainTakenByItsBottomFollowsThePlayer(GameTestHelper helper) {
        Pig top = TestUtil.idle(helper, EntityType.PIG, new Vec3(3.5, FLOOR, 12.5));
        Pig bottom = TestUtil.idle(helper, EntityType.PIG, new Vec3(5.5, FLOOR, 12.5));
        bottom.setLeashedTo(top, true);
        FakePlayer player = TestUtil.player(helper, new Vec3(7.5, FLOOR, 12.5));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.LEAD));
        player.interactOn(bottom, InteractionHand.MAIN_HAND);
        if (Leashes.holder(bottom) != player || Leashes.holder(top) != bottom) {
            helper.fail("the lead didn't take the chain: bottom held by " + Leashes.holder(bottom) + ", top by " + Leashes.holder(top));
            return;
        }
        drag(helper, player, new Vec3(7.5, FLOOR, 12.5), WALK, 14.0, List.of(bottom, top), List.of(player, bottom));
    }

    /**
     * Two free mobs on a stretched lead pull each other, each by the other's share of their
     * mass: one tick of the lead between a chicken and the pig holding it gives both the same
     * momentum toward each other, so the chicken moves several times as much.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 20, batch = BATCH)
    public static void freeMobsPullEachOtherByMass(GameTestHelper helper) {
        Pig pig = TestUtil.idle(helper, EntityType.PIG, new Vec3(4.5, FLOOR, 12.5));
        Chicken chicken = TestUtil.idle(helper, EntityType.CHICKEN, TestUtil.feetAtDistance(helper, pig, 8.0, FLOOR, 0.7, 1));
        chicken.setLeashedTo(pig, true);
        pig.setDeltaMovement(Vec3.ZERO);
        chicken.setDeltaMovement(Vec3.ZERO);
        LeashChains.Role role = LeashChains.role(chicken, pig);
        LeashPhysics.Tick tick = LeashPhysics.tick(chicken, pig, true);
        Vec3 n = LeashGeometry.centre(chicken).subtract(LeashGeometry.centre(pig)).normalize();
        double chickenIn = -chicken.getDeltaMovement().dot(n), pigOut = pig.getDeltaMovement().dot(n);
        double mc = Weights.massWithPassengersKg(chicken), mp = Weights.massWithPassengersKg(pig);
        String report = "chicken " + fmt(chickenIn) + " (" + fmt(mc) + " kg), pig " + fmt(pigOut) + " (" + fmt(mp) + " kg), role " + role;
        if (role != LeashChains.Role.BOTH || !tick.taut()) {
            helper.fail("two free mobs: " + report + ", taut " + tick.taut());
        } else if (chickenIn <= 0.0 || pigOut <= 0.0) {
            helper.fail("both ends should move toward each other: " + report);
        } else if (Math.abs(chickenIn * mc - pigOut * mp) > 0.01 * chickenIn * mc) {
            helper.fail("the pull isn't shared by mass: " + report);
        } else if (chickenIn < 2.0 * pigOut) {
            helper.fail("the lighter chicken should move much more: " + report);
        } else {
            helper.succeed();
        }
    }

    /**
     * One tick of the lead between a grabbed pig and the pig holding it, read straight off the
     * velocities: the holder gets the whole pull toward the grabbed pig, which the lead leaves
     * alone (another mod moves it), and the speed the grabbed pig has away from it is damped as a
     * holder's is for a leashed mob.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 20, batch = BATCH)
    public static void grabbedMobDragsItsHolder(GameTestHelper helper) {
        Pig top = TestUtil.idle(helper, EntityType.PIG, new Vec3(4.5, FLOOR, 12.5));
        Pig bottom = TestUtil.idle(helper, EntityType.PIG, TestUtil.feetAtDistance(helper, top, 8.0, FLOOR, 0.9, 1));
        bottom.setLeashedTo(top, true);
        grab(bottom);
        Vec3 drift = new Vec3(0.0, 0.0, 0.1);
        top.setDeltaMovement(drift);
        bottom.setDeltaMovement(drift);
        LeashChains.Role role = LeashChains.role(bottom, top);
        LeashPhysics.Tick tick = LeashPhysics.tick(bottom, top, true);
        Vec3 topV = top.getDeltaMovement(), bottomV = bottom.getDeltaMovement();
        PhysicsOwnership.release(bottom, GRAB);
        if (role != LeashChains.Role.HOLDER || !tick.taut()) {
            helper.fail("a grabbed bottom pig: role " + role + ", taut " + tick.taut());
        } else if (!bottomV.equals(drift)) {
            helper.fail("the lead moved the grabbed pig: " + fmt(bottomV));
        } else if (topV.x <= 0.1 || Math.abs(topV.z - 0.1) > 1.0E-9) {
            helper.fail("the holder wasn't pulled along the lead toward the grabbed pig: " + fmt(topV));
        } else {
            helper.succeed();
        }
    }

    /** A pig on a player's lead never pulls the player, and one tied to a fence never moves the knot: only mobs are chains. */
    @GameTest(template = PLATFORM, timeoutTicks = 20, batch = BATCH)
    public static void playersAndKnotsAreNeverPulled(GameTestHelper helper) {
        FakePlayer player = TestUtil.player(helper, new Vec3(4.5, FLOOR, 6.5));
        Pig onPlayer = TestUtil.idle(helper, EntityType.PIG, TestUtil.feetAtDistance(helper, player, 9.0, FLOOR, 0.9, 1));
        onPlayer.setLeashedTo(player, true);
        LeashFenceKnotEntity knot = TestUtil.knot(helper, new BlockPos(4, 1, 16));
        Pig onKnot = TestUtil.idle(helper, EntityType.PIG, TestUtil.feetAtDistance(helper, knot, 9.0, FLOOR, 0.9, 1));
        onKnot.setLeashedTo(knot, true);
        player.setDeltaMovement(Vec3.ZERO);
        LeashPhysics.tick(onPlayer, player, true);
        LeashPhysics.tick(onKnot, knot, true);
        if (LeashChains.role(onPlayer, player) != LeashChains.Role.ENTITY || LeashChains.role(onKnot, knot) != LeashChains.Role.ENTITY) {
            helper.fail("a player's or a knot's lead isn't one-way");
        } else if (player.getDeltaMovement().lengthSqr() > 0.0) {
            helper.fail("the player was pulled: " + fmt(player.getDeltaMovement()));
        } else if (onPlayer.getDeltaMovement().x >= -0.05 || onKnot.getDeltaMovement().x >= -0.05) {
            helper.fail("the pigs weren't pulled: " + fmt(onPlayer.getDeltaMovement()) + ", " + fmt(onKnot.getDeltaMovement()));
        } else {
            helper.succeed();
        }
    }

    // ------------------------------------------------------------------ helpers

    private static void grab(Entity entity) {
        PhysicsOwnership.claim(entity, GRAB, PhysicsAspect.MOTION.bit, 400);
    }

    private static void release(List<Entity> entities) {
        for (Entity e : entities) PhysicsOwnership.release(e, GRAB);
    }

    /** Moves {@code mover} (a fake player, or a grabbed mob held still in between) to {@code rel}. */
    private static void place(GameTestHelper helper, Entity mover, Vec3 rel) {
        Vec3 at = helper.absoluteVec(rel);
        mover.setPos(at.x, at.y, at.z);
        mover.setDeltaMovement(Vec3.ZERO);
    }

    /**
     * Moves {@code mover} from {@code from} along +X by {@code distance} at {@code pace} blocks a
     * tick, then lets everything settle for three seconds. {@code leads[i]} must stay tied to
     * {@code holders[i]} throughout, end within {@link #TRAIL} of it, and the last lead's mob must
     * have come along.
     */
    private static void drag(GameTestHelper helper, Entity mover, Vec3 from, double pace, double distance,
                             List<? extends Entity> leads, List<? extends Entity> holders) {
        int ticks = (int) Math.round(distance / pace);
        int[] tick = {0};
        double[] widest = {0.0};
        String[] lost = {null};
        Entity last = leads.get(leads.size() - 1) == mover ? holders.get(holders.size() - 1) : leads.get(leads.size() - 1);
        double lastStart = LeashGeometry.centre(last).x;
        helper.onEachTick(() -> {
            int t = tick[0]++;
            if (t < ticks) place(helper, mover, from.add(pace * (t + 1), 0.0, 0.0));
            for (int i = 0; i < leads.size(); i++) {
                if (Leashes.holder(leads.get(i)) != holders.get(i)) {
                    if (lost[0] == null) lost[0] = "lead " + i + " lost at tick " + t + " (" + fmt(TestUtil.distance(leads.get(i), holders.get(i))) + " apart)";
                } else {
                    widest[0] = Math.max(widest[0], LeashGeometry.span(leads.get(i), holders.get(i)));
                }
            }
        });
        helper.runAfterDelay(ticks + 60, () -> {
            if (mover != null) PhysicsOwnership.release(mover, GRAB);
            double moved = LeashGeometry.centre(last).x - lastStart;
            StringBuilder ends = new StringBuilder();
            for (int i = 0; i < leads.size(); i++) ends.append(fmt(TestUtil.distance(leads.get(i), holders.get(i)))).append(' ');
            String report = "pace " + pace + ": last mob moved " + fmt(moved) + ", widest " + fmt(widest[0]) + ", end spans " + ends;
            UnleashedLead.LOGGER.info("[chain test] {}", report);
            if (lost[0] != null) {
                helper.fail(lost[0] + ": " + report);
            } else if (moved < distance - 2.0 * TRAIL + 2.0) {
                helper.fail("the chain was not dragged along: " + report);
            } else if (widest[0] > 11.0) {
                helper.fail("a lead stretched almost to snapping: " + report);
            } else {
                for (int i = 0; i < leads.size(); i++) {
                    if (TestUtil.distance(leads.get(i), holders.get(i)) > TRAIL) {
                        helper.fail("lead " + i + " left its mob behind: " + report);
                        return;
                    }
                }
                helper.succeed();
            }
        });
    }
}
