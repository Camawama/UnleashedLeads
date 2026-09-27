package net.camacraft.unleashedlead.gametest;

import net.camacraft.unleashedlead.UnleashedLead;
import net.camacraft.unleashedlead.compat.VanillaBackport;
import net.camacraft.unleashedlead.leash.BoatLeash;
import net.camacraft.unleashedlead.leash.Leashes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.camacraft.unleashedlead.gametest.TestUtil.PLATFORM;

/**
 * Living alongside Vanilla Backport. Run the suite twice: plain, and with
 * {@code gradlew runGameTestServer -PvanillaBackport}. Without it these check that our own model
 * is the one in use; with it, that its model is used (not ours), that its happy ghast carries a
 * boat on four ropes through our bridge, and that one mob's lead is simulated by one mod only.
 */
@GameTestHolder(UnleashedLead.MOD_ID)
@PrefixGameTestTemplate(false)
public class CompatTests {
    private static final Logger LOGGER = LoggerFactory.getLogger("UnleashedLead/test");

    /** Exactly one leash model per boat: ours without Vanilla Backport, its with it. */
    @GameTest(template = PLATFORM, timeoutTicks = 20)
    public static void oneLeashModelPerBoat(GameTestHelper helper) {
        LeashFenceKnotEntity knot = TestUtil.knot(helper, new BlockPos(6, 1, 6));
        Boat boat = helper.spawn(EntityType.BOAT, new Vec3(8.5, 1.0, 6.5));
        Leashes.attach(boat, knot);
        boolean vb = VanillaBackport.present();
        LOGGER.info("[UnleashedLead/test] Vanilla Backport present: {}, bridge active: {}", vb, VanillaBackport.active());
        if (vb) {
            if (!VanillaBackport.active()) {
                helper.fail("Vanilla Backport is installed but the bridge to its leash model did not link");
            } else if (boat instanceof BoatLeash) {
                helper.fail("our boat mixin applied alongside Vanilla Backport's");
            } else if (VanillaBackport.holder(boat) != knot) {
                helper.fail("the boat's lead is not in Vanilla Backport's model");
            } else {
                helper.succeed();
            }
        } else if (!(boat instanceof BoatLeash leash) || leash.ul$holder() != knot) {
            helper.fail("without Vanilla Backport the boat should use our model");
        } else {
            helper.succeed();
        }
    }

    /** One mob, one simulation: ours without Vanilla Backport, its with it, and the lead holds either way. */
    @GameTest(template = PLATFORM, timeoutTicks = 60)
    public static void oneLeashSimulationPerMob(GameTestHelper helper) {
        LeashFenceKnotEntity knot = TestUtil.knot(helper, new BlockPos(2, 1, 6));
        Pig pig = TestUtil.idle(helper, EntityType.PIG, TestUtil.feetAtDistance(helper, knot, 9.0, 1.0, 0.9, 1));
        pig.setLeashedTo(knot, true);
        double start = TestUtil.distance(pig, knot);
        boolean ours = Leashes.ownsPhysics(pig);
        if (ours == VanillaBackport.present()) {
            helper.fail("physics owner is wrong: ours=" + ours + " with Vanilla Backport " + VanillaBackport.present());
            return;
        }
        helper.runAfterDelay(40, () -> {
            double end = TestUtil.distance(pig, knot);
            if (!pig.isLeashed()) {
                helper.fail("the lead broke at " + TestUtil.fmt(start));
            } else if (end > start - 1.0) {
                helper.fail("nobody pulled the pig: " + TestUtil.fmt(start) + " -> " + TestUtil.fmt(end));
            } else {
                helper.succeed();
            }
        });
    }

    /**
     * With Vanilla Backport: its happy ghast holds a boat on four ropes, read through our bridge,
     * and the boat's quad-lead physics is handed back to Vanilla Backport's own hook (the one
     * piece of its leash physics Unleashed Lead doesn't replace) without error.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 80)
    public static void happyGhastCarriesBoatOnFourRopes(GameTestHelper helper) {
        if (!VanillaBackport.present()) {
            LOGGER.info("[UnleashedLead/test] happyGhastCarriesBoatOnFourRopes: Vanilla Backport absent, nothing to check");
            helper.succeed();
            return;
        }
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.fromNamespaceAndPath("minecraft", "happy_ghast"));
        if (type == null || type == EntityType.PIG) {
            helper.fail("Vanilla Backport is installed but minecraft:happy_ghast is not registered");
            return;
        }
        Entity ghast = helper.spawn(type, new Vec3(12.0, 8.0, 12.0));
        if (ghast instanceof net.minecraft.world.entity.Mob mob) mob.setNoAi(true); // hover in place
        Boat boat = helper.spawn(EntityType.BOAT, new Vec3(12.5, 1.0, 12.5));
        Leashes.attach(boat, ghast);
        Vec3[] boatOffsets = VanillaBackport.quadOffsets(boat);
        Vec3[] ghastOffsets = VanillaBackport.quadHolderOffsets(ghast);
        if (Leashes.holder(boat) != ghast) {
            helper.fail("the boat is not tied to the happy ghast");
            return;
        } else if (!VanillaBackport.usesQuadLeash(boat, ghast)) {
            helper.fail("the boat does not hang from the ghast on a quad leash");
            return;
        } else if (boatOffsets == null || ghastOffsets == null) {
            helper.fail("quad leash offsets unreadable");
            return;
        }
        int before = VanillaBackport.delegatedCalls();
        helper.runAfterDelay(40, () -> {
            if (VanillaBackport.defaultsFailed()) {
                helper.fail("could not call Vanilla Backport's own quad-lead hook");
            } else if (VanillaBackport.delegatedCalls() <= before) {
                helper.fail("the quad lead's physics was never handed to Vanilla Backport");
            } else if (Leashes.holder(boat) != ghast) {
                helper.fail("the boat fell off the ghast");
            } else {
                LOGGER.info("[UnleashedLead/test] quad leash: boat snaps at {}, free {}; ghast snaps at {}, free {}; {} hand-backs",
                        VanillaBackport.snapDistance(boat), VanillaBackport.elasticDistance(boat),
                        VanillaBackport.snapDistance(ghast), VanillaBackport.elasticDistance(ghast),
                        VanillaBackport.delegatedCalls() - before);
                helper.succeed();
            }
        });
    }
}
