package net.camacraft.unleashedlead.gametest;

import net.camacraft.unleashedlead.UnleashedLead;
import net.camacraft.unleashedlead.leash.Leashes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Optional;

import static net.camacraft.unleashedlead.gametest.TestUtil.PLATFORM;

/**
 * The 1.21.6 / Bedrock lead rules: shears, mob-to-mob leads, taking a lead over, knots and fences,
 * dispensers, fireworks and the recipe. Players are Forge fake players driven through the same
 * {@code Player.interactOn} path a real click takes, so the Forge interaction event runs.
 */
@GameTestHolder(UnleashedLead.MOD_ID)
@PrefixGameTestTemplate(false)
public class LeashRuleTests {

    /**
     * Shears cut the target's own lead and every lead tied to it, drop the leads and wear the
     * shears; a lead another player holds is never cut by hand (Bedrock).
     */
    @GameTest(template = PLATFORM, timeoutTicks = 40)
    public static void shearsCutEveryLeadOnTheTarget(GameTestHelper helper) {
        FakePlayer player = TestUtil.player(helper, new Vec3(4.5, 1.0, 12.5));
        FakePlayer other = TestUtil.player(helper, new Vec3(4.5, 1.0, 16.5));
        Pig a = TestUtil.still(helper, EntityType.PIG, new Vec3(6.5, 1.0, 12.5));
        Pig b = TestUtil.still(helper, EntityType.PIG, new Vec3(8.5, 1.0, 12.5));
        Pig c = TestUtil.still(helper, EntityType.PIG, new Vec3(6.5, 1.0, 16.5));
        a.setLeashedTo(player, true);
        b.setLeashedTo(a, true);
        c.setLeashedTo(other, true);
        ItemStack shears = new ItemStack(Items.SHEARS);
        player.setItemInHand(InteractionHand.MAIN_HAND, shears);
        InteractionResult onA = player.interactOn(a, InteractionHand.MAIN_HAND);
        InteractionResult onC = player.interactOn(c, InteractionHand.MAIN_HAND);
        helper.runAfterDelay(2, () -> {
            if (!onA.consumesAction()) {
                helper.fail("shears on a leashed pig did nothing: " + onA);
            } else if (a.isLeashed() || b.isLeashed()) {
                helper.fail("shears left a lead: a=" + a.isLeashed() + " b=" + b.isLeashed());
            } else if (TestUtil.itemsNear(helper, a, Items.LEAD, 4.0) < 2) {
                helper.fail("expected 2 leads dropped, found " + TestUtil.itemsNear(helper, a, Items.LEAD, 4.0));
            } else if (shears.getDamageValue() != 1) {
                helper.fail("shears should lose 1 durability, lost " + shears.getDamageValue());
            } else if (!c.isLeashed() || c.getLeashHolder() != other) {
                helper.fail("shears cut a lead another player holds (" + onC + ")");
            } else {
                helper.succeed();
            }
        });
    }

    /**
     * Sneak-use a mob while holding leashed mobs: they are tied to it (1.21.6). Never to itself,
     * and not one that would snap straight away.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 40)
    public static void sneakUseTiesHeldMobsToAMob(GameTestHelper helper) {
        FakePlayer player = TestUtil.player(helper, new Vec3(10.5, 1.0, 4.5));
        Cow cow = TestUtil.still(helper, EntityType.COW, new Vec3(16.5, 1.0, 4.5));
        Pig p1 = TestUtil.still(helper, EntityType.PIG, new Vec3(10.5, 1.0, 7.5));
        Pig p2 = TestUtil.still(helper, EntityType.PIG, new Vec3(12.5, 1.0, 2.5));
        Pig far = TestUtil.still(helper, EntityType.PIG, new Vec3(1.5, 1.0, 4.5)); // 9 from the player, 15 from the cow
        for (Pig p : new Pig[]{p1, p2, far}) p.setLeashedTo(player, true);
        cow.setLeashedTo(player, true); // held too: it must not be tied to itself
        player.setShiftKeyDown(true);
        InteractionResult result = player.interactOn(cow, InteractionHand.MAIN_HAND);
        helper.runAfterDelay(2, () -> {
            if (!result.consumesAction()) {
                helper.fail("sneak-use did nothing: " + result);
            } else if (p1.getLeashHolder() != cow || p2.getLeashHolder() != cow) {
                helper.fail("held pigs were not tied to the cow: " + p1.getLeashHolder() + ", " + p2.getLeashHolder());
            } else if (cow.getLeashHolder() != player) {
                helper.fail("the cow should still be on the player, is on " + cow.getLeashHolder());
            } else if (far.getLeashHolder() != player) {
                helper.fail("a pig 15 blocks from the cow was re-tied (it would have snapped)");
            } else {
                helper.succeed();
            }
        });
    }

    /** A lead on a mob tied to a knot takes it over (old lead drops); one held by another player can't be taken. */
    @GameTest(template = PLATFORM, timeoutTicks = 40)
    public static void leadTakesOverKnotButNotAnotherPlayer(GameTestHelper helper) {
        LeashFenceKnotEntity knot = TestUtil.knot(helper, new BlockPos(2, 1, 6));
        FakePlayer player = TestUtil.player(helper, new Vec3(6.5, 1.0, 8.5));
        FakePlayer other = TestUtil.player(helper, new Vec3(6.5, 1.0, 14.5));
        Pig onKnot = TestUtil.still(helper, EntityType.PIG, new Vec3(5.5, 1.0, 6.5));
        Pig onOther = TestUtil.still(helper, EntityType.PIG, new Vec3(5.5, 1.0, 14.5));
        onKnot.setLeashedTo(knot, true);
        onOther.setLeashedTo(other, true);
        ItemStack leads = new ItemStack(Items.LEAD, 3);
        player.setItemInHand(InteractionHand.MAIN_HAND, leads);
        player.interactOn(onKnot, InteractionHand.MAIN_HAND);
        player.interactOn(onOther, InteractionHand.MAIN_HAND);
        helper.runAfterDelay(2, () -> {
            if (onKnot.getLeashHolder() != player) {
                helper.fail("a knot-tied pig was not taken over: holder " + onKnot.getLeashHolder());
            } else if (TestUtil.itemsNear(helper, onKnot, Items.LEAD, 3.0) < 1) {
                helper.fail("the knot's old lead did not drop");
            } else if (onOther.getLeashHolder() != other) {
                helper.fail("a pig was taken from another player");
            } else if (leads.getCount() != 2) {
                helper.fail("expected one lead used, " + leads.getCount() + " left of 3");
            } else {
                helper.succeed();
            }
        });
    }

    /** Using a knot with nothing to tie takes its leads into your hand; the empty knot goes (1.21.6). */
    @GameTest(template = PLATFORM, timeoutTicks = 40)
    public static void usingAKnotTakesItsLeads(GameTestHelper helper) {
        LeashFenceKnotEntity knot = TestUtil.knot(helper, new BlockPos(6, 1, 6));
        FakePlayer player = TestUtil.player(helper, new Vec3(8.5, 1.0, 6.5));
        Pig pig = TestUtil.still(helper, EntityType.PIG, new Vec3(6.5, 1.0, 9.5));
        pig.setLeashedTo(knot, true);
        player.interactOn(knot, InteractionHand.MAIN_HAND);
        helper.runAfterDelay(2, () -> {
            if (pig.getLeashHolder() != player) {
                helper.fail("the knot's pig is on " + pig.getLeashHolder() + ", not the player");
            } else if (!knot.isRemoved()) {
                helper.fail("the emptied knot is still there");
            } else {
                helper.succeed();
            }
        });
    }

    /** A knot breaks the tick its fence is removed (Bedrock 1.21.100), and its lead drops. */
    @GameTest(template = PLATFORM, timeoutTicks = 40)
    public static void knotBreaksWithItsFence(GameTestHelper helper) {
        BlockPos fence = new BlockPos(6, 1, 6);
        LeashFenceKnotEntity knot = TestUtil.knot(helper, fence);
        Pig pig = TestUtil.still(helper, EntityType.PIG, new Vec3(8.5, 1.0, 6.5));
        pig.setLeashedTo(knot, true);
        helper.runAfterDelay(5, () -> helper.setBlock(fence, Blocks.AIR));
        helper.runAfterDelay(6, () -> {
            if (!knot.isRemoved()) helper.fail("the knot outlived its fence by a tick");
        });
        helper.runAfterDelay(9, () -> {
            if (pig.isLeashed()) {
                helper.fail("the pig is still leashed to a broken knot");
            } else if (TestUtil.itemsNear(helper, pig, Items.LEAD, 3.0) < 1) {
                helper.fail("no lead dropped");
            } else {
                helper.succeed();
            }
        });
    }

    /** A dispenser with shears cuts the lead of the entity in front of it (1.21.6). */
    @GameTest(template = PLATFORM, timeoutTicks = 60)
    public static void dispenserShearsCutLeads(GameTestHelper helper) {
        BlockPos dispenser = new BlockPos(4, 1, 12);
        helper.setBlock(dispenser, Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.EAST));
        DispenserBlockEntity box = (DispenserBlockEntity) helper.getBlockEntity(dispenser);
        ItemStack shears = new ItemStack(Items.SHEARS);
        box.setItem(0, shears);
        LeashFenceKnotEntity knot = TestUtil.knot(helper, new BlockPos(8, 1, 12));
        Pig pig = TestUtil.still(helper, EntityType.PIG, new Vec3(5.5, 1.0, 12.5));
        pig.setLeashedTo(knot, true);
        helper.runAfterDelay(3, () -> helper.setBlock(dispenser.north(), Blocks.REDSTONE_BLOCK));
        helper.runAfterDelay(15, () -> {
            ItemStack left = box.getItem(0);
            if (pig.isLeashed()) {
                helper.fail("the dispenser did not cut the lead");
            } else if (!left.is(Items.SHEARS) || left.getDamageValue() != 1) {
                helper.fail("the dispenser's shears should have lost 1 durability: " + left + " damage " + left.getDamageValue());
            } else {
                helper.succeed();
            }
        });
    }

    /** Boosting with a firework while gliding breaks the leads you hold (1.21.6). */
    @GameTest(template = PLATFORM, timeoutTicks = 40)
    public static void fireworkBoostBreaksHeldLeads(GameTestHelper helper) {
        FakePlayer player = TestUtil.player(helper, new Vec3(8.5, 3.0, 8.5));
        Pig pig = TestUtil.still(helper, EntityType.PIG, new Vec3(10.5, 1.0, 8.5));
        pig.setLeashedTo(player, true);
        player.startFallFlying();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.FIREWORK_ROCKET, 4));
        // what a right click does: the Forge event, then the item's own use
        ForgeHooks.onItemRightClick(player, InteractionHand.MAIN_HAND);
        player.getMainHandItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.runAfterDelay(2, () -> {
            if (pig.isLeashed()) {
                helper.fail("the lead survived a firework boost");
            } else {
                helper.succeed();
            }
        });
    }

    /**
     * The lead recipe is 1.21.6's: string in A1, B1, A2, B2 and C3 makes two leads, no slimeball.
     * Checked by shape, not just by count: Vanilla Backport ships a different five-string shape
     * under the same id, and this mod's copy must be the one that loads.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 20)
    public static void leadRecipeIsFiveString(GameTestHelper helper) {
        Optional<? extends Recipe<?>> recipe = helper.getLevel().getRecipeManager()
                .byKey(ResourceLocation.fromNamespaceAndPath("minecraft", "lead"));
        if (recipe.isEmpty() || !(recipe.get() instanceof ShapedRecipe shaped)) {
            helper.fail("no shaped minecraft:lead recipe: " + recipe);
            return;
        }
        ItemStack result = shaped.getResultItem(helper.getLevel().registryAccess());
        if (!result.is(Items.LEAD) || result.getCount() != 2) {
            helper.fail("the recipe makes " + result);
            return;
        }
        if (shaped.getWidth() != 3 || shaped.getHeight() != 3) {
            helper.fail("the recipe is " + shaped.getWidth() + "x" + shaped.getHeight() + ", not 3x3");
            return;
        }
        boolean[] stringAt = {true, true, false, true, true, false, false, false, true};
        for (int i = 0; i < 9; i++) {
            Ingredient ingredient = shaped.getIngredients().get(i);
            boolean isString = !ingredient.isEmpty() && ingredient.test(new ItemStack(Items.STRING));
            if (isString != stringAt[i] || (!ingredient.isEmpty() && !isString)) {
                helper.fail("slot " + i + " of the lead recipe is wrong");
                return;
            }
        }
        helper.succeed();
    }
}
