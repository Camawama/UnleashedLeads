package net.camacraft.unleashedlead.gametest;

import net.camacraft.unleashedlead.UnleashedLead;
import net.camacraft.unleashedlead.compat.VanillaBackport;
import net.camacraft.unleashedlead.leash.LeashGeometry;
import net.camacraft.unleashedlead.leash.Leashes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.vehicle.ChestBoat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.LeadItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

import static net.camacraft.unleashedlead.gametest.TestUtil.PLATFORM;
import static net.camacraft.unleashedlead.gametest.TestUtil.fmt;

/**
 * Leads on boats, chest boats and bamboo rafts (Java 1.21, Bedrock). Everything goes through
 * {@link Leashes}, so the same tests cover this mod's boat model and, with Vanilla Backport
 * installed, its model.
 */
@GameTestHolder(UnleashedLead.MOD_ID)
@PrefixGameTestTemplate(false)
public class BoatLeashTests {

    /** A lead used on a boat ties it; using the boat again takes the lead back. Rafts and chest boats too. */
    @GameTest(template = PLATFORM, timeoutTicks = 40)
    public static void leadTiesBoatsRaftsAndChestBoats(GameTestHelper helper) {
        FakePlayer player = TestUtil.player(helper, new Vec3(12.5, 1.0, 8.5));
        Boat boat = helper.spawn(EntityType.BOAT, new Vec3(10.5, 1.0, 10.5));
        ChestBoat chest = helper.spawn(EntityType.CHEST_BOAT, new Vec3(14.5, 1.0, 10.5));
        Boat raft = helper.spawn(EntityType.BOAT, new Vec3(12.5, 1.0, 13.5));
        raft.setVariant(Boat.Type.BAMBOO);
        ItemStack leads = new ItemStack(Items.LEAD, 5);
        player.setItemInHand(InteractionHand.MAIN_HAND, leads);
        InteractionResult tied = player.interactOn(boat, InteractionHand.MAIN_HAND);
        player.interactOn(chest, InteractionHand.MAIN_HAND);
        player.interactOn(raft, InteractionHand.MAIN_HAND);
        helper.runAfterDelay(2, () -> {
            if (!tied.consumesAction() || Leashes.holder(boat) != player) {
                helper.fail("a lead did not tie the boat: " + tied);
                return;
            }
            if (Leashes.holder(chest) != player || Leashes.holder(raft) != player) {
                helper.fail("chest boat or raft not tied: " + Leashes.holder(chest) + ", " + Leashes.holder(raft));
                return;
            }
            if (leads.getCount() != 2) {
                helper.fail("expected 3 leads used, " + leads.getCount() + " of 5 left");
                return;
            }
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            InteractionResult untied = player.interactOn(boat, InteractionHand.MAIN_HAND);
            if (!untied.consumesAction() || Leashes.isLeashed(boat)) {
                helper.fail("using a boat you lead did not untie it: " + untied);
                return;
            }
            if (player.getVehicle() != null) {
                helper.fail("untying the boat also boarded it");
                return;
            }
            helper.runAfterDelay(2, () -> {
                if (TestUtil.itemsNear(helper, boat, Items.LEAD, 3.0) < 1) helper.fail("untying dropped no lead");
                else helper.succeed();
            });
        });
    }

    /** A boat tied to a knot keeps its lead through a save and load. */
    @GameTest(template = PLATFORM, timeoutTicks = 60)
    public static void boatLeadSurvivesSaveAndLoad(GameTestHelper helper) {
        LeashFenceKnotEntity knot = TestUtil.knot(helper, new BlockPos(6, 1, 6));
        Boat boat = helper.spawn(EntityType.BOAT, new Vec3(9.5, 1.0, 6.5));
        Leashes.attach(boat, knot);
        helper.runAfterDelay(2, () -> {
            if (Leashes.holder(boat) != knot) {
                helper.fail("the boat is not tied to the knot");
                return;
            }
            CompoundTag saved = boat.saveWithoutId(new CompoundTag());
            Boat loaded = EntityType.BOAT.create(helper.getLevel());
            loaded.load(saved);
            loaded.setUUID(UUID.randomUUID());
            boat.discard();
            helper.getLevel().addFreshEntity(loaded);
            helper.runAfterDelay(5, () -> {
                Entity holder = Leashes.holder(loaded);
                if (!(holder instanceof LeashFenceKnotEntity restored) || !restored.getPos().equals(knot.getPos())) {
                    helper.fail("the loaded boat's lead was not restored to the knot: " + holder);
                } else {
                    helper.succeed();
                }
            });
        });
    }

    /** A boat on a lead is dragged toward its holder, and (with this mod's physics) turns its bow toward it. */
    @GameTest(template = PLATFORM, timeoutTicks = 120)
    public static void leashedBoatIsDraggedAndTurns(GameTestHelper helper) {
        // a walled pool of still water, x 3..21, z 10..14
        for (int x = 2; x <= 22; x++) {
            for (int z = 9; z <= 15; z++) {
                boolean wall = x == 2 || x == 22 || z == 9 || z == 15;
                helper.setBlock(new BlockPos(x, 1, z), wall ? Blocks.STONE.defaultBlockState() : Blocks.WATER.defaultBlockState());
            }
        }
        LeashFenceKnotEntity knot = TestUtil.knot(helper, new BlockPos(2, 2, 12));
        Boat boat = helper.spawn(EntityType.BOAT, new Vec3(12.5, 1.6, 12.5)); // 10 blocks out
        boat.setYRot(0.0F); // bow facing +Z, across the rope
        Leashes.attach(boat, knot);
        double start = TestUtil.distance(boat, knot);
        helper.runAfterDelay(80, () -> {
            double end = TestUtil.distance(boat, knot);
            Vec3 toKnot = LeashGeometry.centre(knot).subtract(LeashGeometry.centre(boat));
            float want = (float) (Mth.atan2(-toKnot.x, toKnot.z) * Mth.RAD_TO_DEG);
            float off = Math.abs(Mth.wrapDegrees(want - boat.getYRot()));
            if (!Leashes.isLeashed(boat)) {
                helper.fail("the boat's lead broke (" + fmt(end) + ")");
            } else if (end > start - 2.0) {
                helper.fail("the boat was not dragged: " + fmt(start) + " -> " + fmt(end));
            } else if (!VanillaBackport.present() && off > 30.0F) {
                helper.fail("the boat did not turn toward the knot: yaw " + fmt(boat.getYRot()) + ", want " + fmt(want));
            } else {
                helper.succeed();
            }
        });
    }

    /** Using a fence ties the boats you hold to it, like mobs (vanilla only ever tied mobs). */
    @GameTest(template = PLATFORM, timeoutTicks = 40)
    public static void fenceTiesHeldBoats(GameTestHelper helper) {
        BlockPos fence = new BlockPos(8, 1, 8);
        helper.setBlock(fence, Blocks.OAK_FENCE);
        FakePlayer player = TestUtil.player(helper, new Vec3(9.5, 1.0, 9.5));
        Boat boat = helper.spawn(EntityType.BOAT, new Vec3(12.5, 1.0, 12.5));
        Leashes.attach(boat, player);
        helper.runAfterDelay(1, () -> {
            InteractionResult result = LeadItem.bindPlayerMobs(player, helper.getLevel(), helper.absolutePos(fence));
            helper.runAfterDelay(2, () -> {
                Entity holder = Leashes.holder(boat);
                if (!result.consumesAction()) {
                    helper.fail("using the fence did nothing: " + result);
                } else if (!(holder instanceof LeashFenceKnotEntity knot) || !knot.getPos().equals(helper.absolutePos(fence))) {
                    helper.fail("the boat is held by " + holder + ", not a knot on the fence");
                } else {
                    helper.succeed();
                }
            });
        });
    }
}
