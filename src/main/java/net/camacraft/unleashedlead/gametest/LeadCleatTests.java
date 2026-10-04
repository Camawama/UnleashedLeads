package net.camacraft.unleashedlead.gametest;

import net.camacraft.unleashedlead.UlBlocks;
import net.camacraft.unleashedlead.UlConfig;
import net.camacraft.unleashedlead.UnleashedLead;
import net.camacraft.unleashedlead.block.LeadCleatBlock;
import net.camacraft.unleashedlead.leash.Leashes;
import net.camacraft.unleashedlead.tether.Knots;
import net.camacraft.unleashedlead.tether.Tether;
import net.camacraft.unleashedlead.tether.Tethers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import static net.camacraft.unleashedlead.gametest.TestUtil.PLATFORM;

/** The lead cleat: leads tie to it as to a fence, its knot sits on its horn and lasts, and it snags nothing. */
@GameTestHolder(UnleashedLead.MOD_ID)
@PrefixGameTestTemplate(false)
public class LeadCleatTests {

    /**
     * A cleat on the floor, used with a pig in hand, ties the pig to a knot on the cleat (as a fence
     * would), the knot sits on the horn, and it is still there with the pig on it after two of
     * vanilla's 100-tick survival checks (which take a knot off anything that isn't a fence).
     */
    @GameTest(template = PLATFORM, timeoutTicks = 280)
    public static void aLeadTiesToACleatAndItsKnotStays(GameTestHelper helper) {
        int y = above(helper, 6, 6);
        BlockPos rel = new BlockPos(6, y, 6);
        helper.setBlock(rel, UlBlocks.LEAD_CLEAT.get().defaultBlockState());
        BlockPos abs = helper.absolutePos(rel);
        FakePlayer player = TestUtil.player(helper, new Vec3(8.5, y, 6.5));
        Pig pig = TestUtil.still(helper, EntityType.PIG, new Vec3(9.5, y, 6.5));
        pig.setLeashedTo(player, true);
        BlockState state = helper.getLevel().getBlockState(abs);
        InteractionResult used = state.use(helper.getLevel(), player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(used.consumesAction(), "using the cleat did nothing (" + used + ")");
            Entity holder = Leashes.holder(pig);
            helper.assertTrue(holder instanceof LeashFenceKnotEntity knot && knot.getPos().equals(abs),
                    "the pig isn't tied to a knot on the cleat (holder " + holder + ")");
            Vec3 expected = LeadCleatBlock.knotPosition(abs, state);
            helper.assertTrue(expected != null && holder.position().distanceTo(expected) < 1.0E-6,
                    "the knot isn't on the horn: " + TestUtil.fmt(holder.position()) + " vs " + (expected == null ? "none" : TestUtil.fmt(expected)));
        });
        helper.runAfterDelay(260, () -> {
            LeashFenceKnotEntity knot = Knots.find(helper.getLevel(), abs);
            helper.assertTrue(knot != null && knot.isAlive(), "the knot on the cleat went");
            helper.assertTrue(Leashes.holder(pig) == knot, "the pig came off the cleat");
            helper.succeed();
        });
    }

    /** Taking the cleat away takes its knot with it, and the lead drops, as with a fence. */
    @GameTest(template = PLATFORM, timeoutTicks = 60)
    public static void aKnotGoesWithItsCleat(GameTestHelper helper) {
        int y = above(helper, 6, 12);
        BlockPos rel = new BlockPos(6, y, 12);
        helper.setBlock(rel, UlBlocks.LEAD_CLEAT.get().defaultBlockState());
        BlockPos abs = helper.absolutePos(rel);
        Pig pig = TestUtil.still(helper, EntityType.PIG, new Vec3(9.5, y, 12.5));
        LeashFenceKnotEntity knot = Knots.getOrCreate(helper.getLevel(), abs);
        Leashes.attach(pig, knot);
        helper.runAfterDelay(5, () -> helper.setBlock(rel, Blocks.AIR));
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(knot.isRemoved(), "the knot stayed without its cleat");
            helper.assertTrue(Leashes.holder(pig) == null, "the pig is still tied");
            helper.succeed();
        });
    }

    /**
     * Using a lead item on a cleat while sneaking (which skips the block's own use) ties the held pig
     * too, as a lead used on a fence does.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 40)
    public static void aLeadUsedOnACleatWhileSneakingTies(GameTestHelper helper) {
        int y = above(helper, 14, 6);
        BlockPos rel = new BlockPos(14, y, 6);
        helper.setBlock(rel, UlBlocks.LEAD_CLEAT.get().defaultBlockState());
        BlockPos abs = helper.absolutePos(rel);
        FakePlayer player = TestUtil.player(helper, new Vec3(16.5, y, 6.5));
        player.setShiftKeyDown(true);
        Pig pig = TestUtil.still(helper, EntityType.PIG, new Vec3(17.5, y, 6.5));
        pig.setLeashedTo(player, true);
        ItemStack lead = new ItemStack(Items.LEAD);
        player.setItemInHand(InteractionHand.MAIN_HAND, lead);
        InteractionResult used = lead.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false)));
        helper.runAfterDelay(2, () -> {
            Entity holder = Leashes.holder(pig);
            helper.assertTrue(holder instanceof LeashFenceKnotEntity knot && knot.getPos().equals(abs),
                    "a lead used on the cleat (" + used + ") didn't tie the pig to it (holder " + holder + ")");
            helper.succeed();
        });
    }

    /**
     * The cleat has no collision on any face (it snags nothing, on a ship's hull too), counts as an
     * anchor, and its knot sits on the side it is fixed to, inside its own cell.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 20)
    public static void aCleatSnagsNothingAndItsKnotSitsOnItsFace(GameTestHelper helper) {
        BlockPos rel = new BlockPos(18, 1, 18);
        BlockPos abs = helper.absolutePos(rel);
        for (AttachFace face : AttachFace.values()) {
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                BlockState state = UlBlocks.LEAD_CLEAT.get().defaultBlockState()
                        .setValue(FaceAttachedHorizontalDirectionalBlock.FACE, face)
                        .setValue(FaceAttachedHorizontalDirectionalBlock.FACING, facing);
                helper.assertTrue(state.getCollisionShape(helper.getLevel(), abs, CollisionContext.empty()).isEmpty(),
                        "the cleat (" + face + ", " + facing + ") has a collision shape");
                helper.assertTrue(UlBlocks.isAnchor(state), "the cleat isn't an anchor");
                Vec3 horn = LeadCleatBlock.hornCentre(state);
                helper.assertTrue(horn != null && horn.x > 0 && horn.x < 1 && horn.y > 0 && horn.y < 1 && horn.z > 0 && horn.z < 1,
                        "the horn of (" + face + ", " + facing + ") is outside its cell: " + horn);
                double toward = switch (face) {
                    case FLOOR -> -horn.y + 0.5;
                    case CEILING -> horn.y - 0.5;
                    // fixed on the side it faces away from
                    case WALL -> -Vec3.atLowerCornerOf(facing.getNormal()).dot(horn.subtract(0.5, 0.5, 0.5));
                };
                helper.assertTrue(toward > 0.2, "the horn of (" + face + ", " + facing + ") isn't on its face: " + horn);
            }
        }
        helper.succeed();
    }

    /** A rope from cleat to cleat (the 1.21.6 fence-to-fence lead) ties and pulls at the cleats' horns. */
    @GameTest(template = PLATFORM, timeoutTicks = 40)
    public static void aRopeTiesCleatToCleat(GameTestHelper helper) {
        if (!UlConfig.on(UlConfig.TETHERS)) {
            helper.succeed();
            return;
        }
        int y = above(helper, 4, 18);
        BlockPos relA = new BlockPos(4, y, 18), relB = new BlockPos(10, y, 18);
        helper.setBlock(relA, UlBlocks.LEAD_CLEAT.get().defaultBlockState());
        helper.setBlock(relB, UlBlocks.LEAD_CLEAT.get().defaultBlockState());
        BlockPos a = helper.absolutePos(relA), b = helper.absolutePos(relB);
        FakePlayer player = TestUtil.player(helper, new Vec3(7.5, y, 18.5));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.LEAD, 4));
        boolean started = Tethers.start(helper.getLevel(), a, player, InteractionHand.MAIN_HAND);
        int[] tied = Tethers.tieCarried(helper.getLevel(), player, Knots.getOrCreate(helper.getLevel(), b));
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(started && tied[0] == 1, "no rope from cleat to cleat (started " + started + ", tied " + tied[0] + ")");
            Tether rope = null;
            for (Tether t : Tethers.all(helper.getLevel())) {
                if (t.touches(a) && t.touches(b)) rope = t;
            }
            helper.assertTrue(rope != null, "the rope isn't between the two cleats");
            Vec3 expected = LeadCleatBlock.knotPosition(a, helper.getLevel().getBlockState(a));
            helper.assertTrue(Tethers.worldAnchor(helper.getLevel(), a).distanceTo(expected) < 1.0E-6,
                    "the rope doesn't pull at the cleat's horn");
            helper.succeed();
        });
    }

    /** The first air cell above the floor at relative (x, z): where the test's blocks and mobs go (the floor's height isn't assumed). */
    private static int above(GameTestHelper helper, int x, int z) {
        for (int y = 6; y >= 0; y--) {
            if (!helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(x, y, z))).isAir()) return y + 1;
        }
        return 1;
    }
}
