package net.camacraft.unleashedlead.gametest;

import net.camacraft.unleashedlead.UnleashedLead;
import net.camacraft.unleashedlead.tether.Knots;
import net.camacraft.unleashedlead.tether.Tether;
import net.camacraft.unleashedlead.tether.TetherData;
import net.camacraft.unleashedlead.tether.Tethers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static net.camacraft.unleashedlead.gametest.TestUtil.PLATFORM;
import static net.camacraft.unleashedlead.gametest.TestUtil.fmt;

/**
 * Leads tied from fence to fence, in the world (no Valkyrien Skies needed): tying one through
 * the real block-use path, the off-hand second use a client sends, taking and untying, the snap
 * of a carried end, shears and broken fences, and a rope finding its knots again after the knots
 * unload and the level's rope data is saved and loaded. The ship tests (src/gametest, with
 * {@code -PshipTests}) cover the pull on ships.
 */
@GameTestHolder(UnleashedLead.MOD_ID)
@PrefixGameTestTemplate(false)
public class TetherTests {

    /**
     * A lead used on one fence and then on another ties a rope between them, through the same
     * {@code ServerPlayerGameMode.useItemOn} a click takes. It is the distance plus the slack
     * long, uses one lead, and in the world just hangs: both knots stay well past the empty-knot
     * grace, nothing pulls and nothing snaps.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public static void leadTiesFenceToFenceAndJustHangs(GameTestHelper helper) {
        BlockPos a = new BlockPos(4, 1, 8), b = new BlockPos(12, 1, 8);
        helper.setBlock(a, Blocks.OAK_FENCE);
        helper.setBlock(b, Blocks.OAK_FENCE);
        FakePlayer player = TestUtil.player(helper, new Vec3(8.5, 1.0, 9.5));
        ItemStack leads = new ItemStack(Items.LEAD, 3);
        player.setItemInHand(InteractionHand.MAIN_HAND, leads);

        InteractionResult first = use(helper, player, a, InteractionHand.MAIN_HAND);
        Tether carried = only(helper, a);
        if (!first.consumesAction() || carried == null || !player.getUUID().equals(carried.carrier())) {
            helper.fail("a lead used on a fence did not start a rope: " + first + ", " + carried);
            return;
        }
        if (leads.getCount() != 2) {
            helper.fail("starting a rope should use one lead, " + leads.getCount() + " of 3 left");
            return;
        }
        InteractionResult second = use(helper, player, b, InteractionHand.MAIN_HAND);
        Tether rope = only(helper, a);
        BlockPos absB = helper.absolutePos(b);
        if (!second.consumesAction() || rope == null || rope.carried() || !absB.equals(rope.b())) {
            helper.fail("using the second fence did not tie the rope to it: " + second + ", " + rope);
            return;
        }
        double want = 8.0 + 0.5; // the knots are 8 apart, plus the default slack
        if (Math.abs(rope.length() - want) > 1.0E-6) {
            helper.fail("the rope is " + fmt(rope.length()) + " long, not " + fmt(want));
            return;
        }
        if (leads.getCount() != 2) {
            helper.fail("tying the rope's end used another lead: " + leads.getCount() + " left");
            return;
        }
        helper.runAfterDelay(150, () -> {
            ServerLevel level = helper.getLevel();
            Tether still = only(helper, a);
            LeashFenceKnotEntity ka = Knots.find(level, helper.absolutePos(a)), kb = Knots.find(level, absB);
            if (still == null || still != rope) {
                helper.fail("the rope between two world fences went away: " + still);
            } else if (ka == null || kb == null) {
                helper.fail("a knot of the rope was cleared away as empty: " + ka + ", " + kb);
            } else if (still.knotA() != ka || still.knotB() != kb) {
                helper.fail("the rope isn't on the fences' knots");
            } else if (still.cell().tension() != 0.0) {
                helper.fail("something pulled on a rope between two world fences: " + still.cell().tension());
            } else {
                helper.succeed();
            }
        });
    }

    /**
     * With an empty main hand the client also sends an off-hand use of the fence in the same
     * tick: it must not take back the rope just tied. A real second use with an empty hand does
     * (the end at that fence comes off, the other stays tied), and bringing the end back to the
     * rope's own fence takes the rope off (the lead drops there).
     */
    @GameTest(template = PLATFORM, timeoutTicks = 60)
    public static void offHandUseDoesNotUndoATie(GameTestHelper helper) {
        BlockPos a = new BlockPos(4, 1, 14), b = new BlockPos(10, 1, 14);
        helper.setBlock(a, Blocks.OAK_FENCE);
        helper.setBlock(b, Blocks.OAK_FENCE);
        FakePlayer player = TestUtil.player(helper, new Vec3(7.5, 1.0, 15.5));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.LEAD, 1));
        use(helper, player, a, InteractionHand.MAIN_HAND);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        use(helper, player, b, InteractionHand.MAIN_HAND);
        use(helper, player, b, InteractionHand.OFF_HAND); // the client's follow-up, same tick
        Tether rope = only(helper, a);
        if (rope == null || rope.carried() || !helper.absolutePos(b).equals(rope.b())) {
            helper.fail("the off-hand follow-up undid the tie: " + rope);
            return;
        }
        helper.runAfterDelay(5, () -> {
            use(helper, player, b, InteractionHand.MAIN_HAND); // a real second click: take it
            Tether taken = only(helper, a);
            if (taken == null || !player.getUUID().equals(taken.carrier()) || !taken.a().equals(helper.absolutePos(a))) {
                helper.fail("using the knot with an empty hand did not take the rope's end: " + taken);
                return;
            }
            helper.runAfterDelay(5, () -> {
                use(helper, player, a, InteractionHand.MAIN_HAND); // back to its own fence: untied
                if (only(helper, a) != null) {
                    helper.fail("bringing the end back to its own fence did not take the rope off");
                    return;
                }
                helper.runAfterDelay(2, () -> {
                    int dropped = leadsNear(helper, a, 2.0);
                    if (dropped != 1) helper.fail("expected the lead dropped at the fence, found " + dropped);
                    else helper.succeed();
                });
            });
        });
    }

    /** A carried end snaps past the snap distance like any lead: the lead drops at its fence. */
    @GameTest(template = PLATFORM, timeoutTicks = 40)
    public static void carriedRopeSnapsPastReach(GameTestHelper helper) {
        BlockPos a = new BlockPos(3, 1, 3);
        helper.setBlock(a, Blocks.OAK_FENCE);
        FakePlayer player = TestUtil.player(helper, new Vec3(4.5, 1.0, 4.5));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.LEAD, 1));
        use(helper, player, a, InteractionHand.MAIN_HAND);
        if (only(helper, a) == null) {
            helper.fail("no rope started");
            return;
        }
        Vec3 far = helper.absoluteVec(new Vec3(19.5, 1.0, 4.5)); // 16 from the fence
        player.setPos(far.x, far.y, far.z);
        helper.runAfterDelay(3, () -> {
            if (only(helper, a) != null) helper.fail("a rope carried 16 blocks away didn't snap");
            else if (leadsNear(helper, a, 2.0) != 1) helper.fail("the snapped rope's lead didn't drop at its fence");
            else helper.succeed();
        });
    }

    /** Shears on a knot cut its ropes; a fence that is broken takes its ropes down. Each drops its lead. */
    @GameTest(template = PLATFORM, timeoutTicks = 60)
    public static void shearsAndBrokenFencesDropRopes(GameTestHelper helper) {
        BlockPos a = new BlockPos(3, 1, 20), b = new BlockPos(9, 1, 20);
        BlockPos c = new BlockPos(14, 1, 20), d = new BlockPos(20, 1, 20);
        for (BlockPos pos : new BlockPos[]{a, b, c, d}) helper.setBlock(pos, Blocks.OAK_FENCE);
        FakePlayer player = TestUtil.player(helper, new Vec3(11.5, 1.0, 21.5));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.LEAD, 2));
        use(helper, player, a, InteractionHand.MAIN_HAND);
        use(helper, player, b, InteractionHand.MAIN_HAND);
        use(helper, player, c, InteractionHand.MAIN_HAND);
        use(helper, player, d, InteractionHand.MAIN_HAND);
        if (only(helper, a) == null || only(helper, c) == null) {
            helper.fail("the two ropes weren't tied");
            return;
        }
        helper.runAfterDelay(2, () -> {
            ItemStack shears = new ItemStack(Items.SHEARS);
            player.setItemInHand(InteractionHand.MAIN_HAND, shears);
            LeashFenceKnotEntity knotB = Knots.find(helper.getLevel(), helper.absolutePos(b));
            InteractionResult cut = knotB == null ? InteractionResult.FAIL : player.interactOn(knotB, InteractionHand.MAIN_HAND);
            if (!cut.consumesAction() || only(helper, a) != null) {
                helper.fail("shears on the knot did not cut its rope: " + cut);
                return;
            }
            if (shears.getDamageValue() != 1) {
                helper.fail("cutting a rope should cost the shears 1 durability, cost " + shears.getDamageValue());
                return;
            }
            helper.setBlock(d, Blocks.AIR);
            helper.runAfterDelay(3, () -> {
                if (only(helper, c) != null) {
                    helper.fail("the rope outlived the fence it was tied to");
                } else if (leadsNear(helper, b, 2.0) != 1) {
                    helper.fail("the cut rope's lead didn't drop at the knot: " + leadsNear(helper, b, 2.0));
                } else if (leadsNear(helper, d, 2.0) != 1) {
                    helper.fail("the broken fence's rope didn't drop its lead: " + leadsNear(helper, d, 2.0));
                } else {
                    helper.succeed();
                }
            });
        });
    }

    /**
     * A rope survives its knots unloading and the level's rope data being saved and loaded back
     * (knots are never saved: whatever is tied to a fence re-makes its knot). Afterwards it is on
     * the fences' knots again, with the same ends and length.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 60)
    public static void ropeSurvivesUnloadAndReload(GameTestHelper helper) {
        BlockPos a = new BlockPos(16, 1, 4), b = new BlockPos(21, 1, 8);
        helper.setBlock(a, Blocks.OAK_FENCE);
        helper.setBlock(b, Blocks.OAK_FENCE);
        FakePlayer player = TestUtil.player(helper, new Vec3(18.5, 1.0, 6.5));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.LEAD, 1));
        use(helper, player, a, InteractionHand.MAIN_HAND);
        use(helper, player, b, InteractionHand.MAIN_HAND);
        Tether rope = only(helper, a);
        if (rope == null || rope.carried()) {
            helper.fail("no rope tied");
            return;
        }
        double length = rope.length();
        LeashFenceKnotEntity oldA = rope.knotA(), oldB = rope.knotB();
        helper.runAfterDelay(2, () -> {
            // a chunk unload takes the knots away (without saving them), then a restart reloads the data
            if (oldA != null) oldA.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            if (oldB != null) oldB.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            TetherData.reload(helper.getLevel(), t -> t == rope);
            helper.runAfterDelay(3, () -> {
                Tether back = only(helper, a);
                ServerLevel level = helper.getLevel();
                LeashFenceKnotEntity ka = Knots.find(level, helper.absolutePos(a)), kb = Knots.find(level, helper.absolutePos(b));
                if (back == null || back == rope) {
                    helper.fail("the rope wasn't reloaded: " + back);
                } else if (back.carried() || !helper.absolutePos(b).equals(back.b()) || Math.abs(back.length() - length) > 1.0E-9) {
                    helper.fail("the reloaded rope changed: " + back + " (was " + fmt(length) + " long)");
                } else if (ka == null || kb == null || ka == oldA || kb == oldB) {
                    helper.fail("the knots weren't made again: " + ka + ", " + kb);
                } else if (back.knotA() != ka || back.knotB() != kb) {
                    helper.fail("the reloaded rope didn't find its knots");
                } else if (leadsNear(helper, a, 3.0) + leadsNear(helper, b, 3.0) != 0) {
                    helper.fail("unloading the knots dropped the lead");
                } else {
                    helper.succeed();
                }
            });
        });
    }

    // ------------------------------------------------------------------ helpers

    /** What a right click on the fence's top does, through the whole block-and-item use path. */
    static InteractionResult use(GameTestHelper helper, FakePlayer player, BlockPos rel, InteractionHand hand) {
        BlockPos pos = helper.absolutePos(rel);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos).add(0.0, 0.5, 0.0), Direction.UP, pos, false);
        return player.gameMode.useItemOn(player, helper.getLevel(), player.getItemInHand(hand), hand, hit);
    }

    /** The one rope tied to (or carried from) the fence at {@code rel}, or null. */
    @Nullable
    static Tether only(GameTestHelper helper, BlockPos rel) {
        BlockPos pos = helper.absolutePos(rel);
        List<Tether> found = new ArrayList<>();
        for (Tether t : Tethers.all(helper.getLevel())) {
            if (t.touches(pos)) found.add(t);
        }
        return found.size() == 1 ? found.get(0) : null;
    }

    static int leadsNear(GameTestHelper helper, BlockPos rel, double radius) {
        int n = 0;
        for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(rel)).inflate(radius))) {
            if (e.getItem().is(Items.LEAD)) n += e.getItem().getCount();
        }
        return n;
    }
}
