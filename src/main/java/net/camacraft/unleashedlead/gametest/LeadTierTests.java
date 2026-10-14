package net.camacraft.unleashedlead.gametest;

import net.camacraft.unleashedlead.UlConfig;
import net.camacraft.unleashedlead.UlItems;
import net.camacraft.unleashedlead.UnleashedLead;
import net.camacraft.unleashedlead.compat.VanillaBackport;
import net.camacraft.unleashedlead.leash.LeadTier;
import net.camacraft.unleashedlead.leash.LeadTiers;
import net.camacraft.unleashedlead.leash.LeashGeometry;
import net.camacraft.unleashedlead.leash.LeashPhysics;
import net.camacraft.unleashedlead.leash.Leashes;
import net.camacraft.unleashedlead.tether.Tether;
import net.camacraft.unleashedlead.tether.Tethers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static net.camacraft.unleashedlead.gametest.TestUtil.PLATFORM;
import static net.camacraft.unleashedlead.gametest.TestUtil.fmt;

/**
 * Upgraded leads ({@link LeadTier}): made at the smithing table with no template, tied, moved and untied like any
 * lead while keeping what they are made of, dropped as themselves, kept through a save, and harder to break: each
 * snaps only past its own break distance (iron 15.5, diamond 22.5, netherite 33 by default), a rope of one on a ship
 * holds a harder pull. Run with {@code -PvanillaBackport} too: there its leash tick snaps the leads it ticks.
 */
@GameTestHolder(UnleashedLead.MOD_ID)
@PrefixGameTestTemplate(false)
public class LeadTierTests {

    /**
     * The real smithing menu, no template: lead + iron ingot gives an iron lead, iron lead + diamond a diamond lead,
     * diamond lead + netherite ingot a netherite lead; taking the result uses up both inputs. With a template in its
     * slot the same inputs give nothing.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 20)
    public static void smithingTableMakesEveryTierWithNoTemplate(GameTestHelper helper) {
        BlockPos table = new BlockPos(4, 1, 4);
        helper.setBlock(table, Blocks.SMITHING_TABLE);
        FakePlayer player = TestUtil.player(helper, new Vec3(4.5, 2.0, 5.5));
        Item[][] steps = {
                {Items.LEAD, Items.IRON_INGOT, UlItems.IRON_LEAD.get()},
                {UlItems.IRON_LEAD.get(), Items.DIAMOND, UlItems.DIAMOND_LEAD.get()},
                {UlItems.DIAMOND_LEAD.get(), Items.NETHERITE_INGOT, UlItems.NETHERITE_LEAD.get()},
        };
        for (Item[] step : steps) {
            SmithingMenu menu = new SmithingMenu(1, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(table)));
            menu.getSlot(1).set(new ItemStack(step[0]));
            menu.getSlot(2).set(new ItemStack(step[1]));
            ItemStack result = menu.getSlot(3).getItem();
            if (!result.is(step[2])) {
                helper.fail(name(step[0]) + " + " + name(step[1]) + " with no template gave " + result + ", not " + name(step[2]));
                return;
            }
            menu.clicked(3, 0, ClickType.QUICK_MOVE, player);
            if (!menu.getSlot(1).getItem().isEmpty() || !menu.getSlot(2).getItem().isEmpty()) {
                helper.fail("taking the " + name(step[2]) + " left " + menu.getSlot(1).getItem() + " / " + menu.getSlot(2).getItem());
                return;
            }
            if (player.getInventory().countItem(step[2]) != 1) {
                helper.fail("the " + name(step[2]) + " never reached the inventory");
                return;
            }
            menu.removed(player);
        }
        SmithingMenu menu = new SmithingMenu(2, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(table)));
        menu.getSlot(0).set(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE));
        menu.getSlot(1).set(new ItemStack(Items.LEAD));
        menu.getSlot(2).set(new ItemStack(Items.IRON_INGOT));
        if (!menu.getSlot(3).getItem().isEmpty()) {
            helper.fail("with a template in its slot, lead + iron ingot still gave " + menu.getSlot(3).getItem());
            return;
        }
        if (!UlItems.NETHERITE_LEAD.get().isFireResistant()) {
            helper.fail("the netherite lead burns");
            return;
        }
        helper.succeed();
    }

    /**
     * Each tier holds where the one below it snaps, and snaps just past its own break distance. Pigs without AI hang
     * still in the air above their fences (the lead can't move them), so each lead is measured at exactly its distance.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 60)
    public static void eachTierSnapsOnlyPastItsBreakDistance(GameTestHelper helper) {
        double plain = UlConfig.num(UlConfig.SNAP_DISTANCE), free = UlConfig.num(UlConfig.FREE_LENGTH);
        double iron = LeadTiers.breakDistance(free, plain, LeadTier.IRON.strength());
        double diamond = LeadTiers.breakDistance(free, plain, LeadTier.DIAMOND.strength());
        double netherite = LeadTiers.breakDistance(free, plain, LeadTier.NETHERITE.strength());
        if (Math.abs(iron - 15.5) > 1.0E-9 || Math.abs(diamond - 22.5) > 1.0E-9 || Math.abs(netherite - 33.0) > 1.0E-9) {
            helper.fail("default break distances " + fmt(iron) + " / " + fmt(diamond) + " / " + fmt(netherite) + ", not 15.5 / 22.5 / 33");
            return;
        }
        List<Case> cases = new ArrayList<>();
        cases.add(hang(helper, new BlockPos(2, 1, 2), LeadTier.PLAIN, plain + 0.5, false));
        cases.add(hang(helper, new BlockPos(5, 1, 2), LeadTier.IRON, plain + 0.5, true));
        cases.add(hang(helper, new BlockPos(8, 1, 2), LeadTier.IRON, iron - 0.5, true));
        cases.add(hang(helper, new BlockPos(11, 1, 2), LeadTier.IRON, iron + 0.5, false));
        cases.add(hang(helper, new BlockPos(14, 1, 2), LeadTier.DIAMOND, iron + 0.5, true));
        cases.add(hang(helper, new BlockPos(17, 1, 2), LeadTier.DIAMOND, diamond - 0.5, true));
        cases.add(hang(helper, new BlockPos(20, 1, 2), LeadTier.DIAMOND, diamond + 0.5, false));
        cases.add(hang(helper, new BlockPos(2, 1, 8), LeadTier.NETHERITE, diamond + 0.5, true));
        cases.add(hang(helper, new BlockPos(5, 1, 8), LeadTier.NETHERITE, netherite - 0.5, true));
        cases.add(hang(helper, new BlockPos(8, 1, 8), LeadTier.NETHERITE, netherite + 0.5, false));
        helper.runAfterDelay(5, () -> {
            StringBuilder wrong = new StringBuilder();
            for (Case c : cases) {
                boolean held = c.pig.isLeashed();
                if (held != c.holds) {
                    wrong.append(String.format(Locale.ROOT, " %s at %.1f %s;", c.tier, c.distance, held ? "held" : "snapped"));
                } else if (!held) {
                    int own = itemsAbove(helper, c.fence, c.tier.item()), plainLeads = c.tier == LeadTier.PLAIN ? 0 : itemsAbove(helper, c.fence, Items.LEAD);
                    if (own != 1 || plainLeads != 0) {
                        wrong.append(String.format(Locale.ROOT, " %s snapped and dropped %d of itself, %d plain leads;", c.tier, own, plainLeads));
                    } else if (LeadTiers.of(c.pig) != LeadTier.PLAIN) {
                        wrong.append(" ").append(c.tier).append(" snapped but its tier stayed on the pig;");
                    }
                }
            }
            if (wrong.length() > 0) helper.fail("wrong:" + wrong);
            else helper.succeed();
        });
    }

    /**
     * A diamond lead tied by a real click keeps being a diamond lead: tied to a fence, taken back off the knot into
     * the hand, and untied (it drops a diamond lead, not a plain one), one lead used for all of it.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 60)
    public static void upgradedLeadKeepsItsTierThroughEveryHand(GameTestHelper helper) {
        BlockPos fence = new BlockPos(6, 1, 6);
        helper.setBlock(fence, Blocks.OAK_FENCE);
        FakePlayer player = TestUtil.player(helper, new Vec3(8.5, 1.0, 6.5));
        ItemStack leads = new ItemStack(UlItems.DIAMOND_LEAD.get(), 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, leads);
        Pig pig = TestUtil.idle(helper, EntityType.PIG, new Vec3(10.5, 1.0, 6.5));
        player.interactOn(pig, InteractionHand.MAIN_HAND);
        if (Leashes.holder(pig) != player || LeadTiers.of(pig) != LeadTier.DIAMOND || leads.getCount() != 1) {
            helper.fail("a diamond lead used on a pig: holder " + Leashes.holder(pig) + ", tier " + LeadTiers.of(pig) + ", " + leads.getCount() + " left");
            return;
        }
        TetherTests.use(helper, player, fence, InteractionHand.MAIN_HAND);
        if (!(Leashes.holder(pig) instanceof LeashFenceKnotEntity) || LeadTiers.of(pig) != LeadTier.DIAMOND || leads.getCount() != 1) {
            helper.fail("tied to the fence: holder " + Leashes.holder(pig) + ", tier " + LeadTiers.of(pig) + ", " + leads.getCount() + " left");
            return;
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        TetherTests.use(helper, player, fence, InteractionHand.MAIN_HAND); // nothing to tie: the knot's leads come into the hand
        if (Leashes.holder(pig) != player || LeadTiers.of(pig) != LeadTier.DIAMOND) {
            helper.fail("taken off the knot: holder " + Leashes.holder(pig) + ", tier " + LeadTiers.of(pig));
            return;
        }
        player.interactOn(pig, InteractionHand.MAIN_HAND); // your own leashed mob: the lead comes off
        if (Leashes.holder(pig) != null) {
            helper.fail("using the pig did not untie it");
            return;
        }
        int diamonds = TestUtil.itemsNear(helper, pig, UlItems.DIAMOND_LEAD.get(), 3.0), plain = TestUtil.itemsNear(helper, pig, Items.LEAD, 3.0);
        if (diamonds != 1 || plain != 0) {
            helper.fail("untied, it dropped " + diamonds + " diamond leads and " + plain + " plain ones");
        } else if (LeadTiers.of(pig) != LeadTier.PLAIN) {
            helper.fail("the untied pig still has a lead tier: " + LeadTiers.of(pig));
        } else {
            helper.succeed();
        }
    }

    /**
     * A netherite lead used on the bottom pig of a free chain (pig A held by B by an diamond lead, B held by C by an
     * iron one) takes the chain: A now on your netherite lead, B hanging from A by the diamond lead, C from B by the
     * iron one. Each lead keeps what it is made of, nothing dropped.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 20)
    public static void takingAChainKeepsEachLeadsTier(GameTestHelper helper) {
        Pig a = TestUtil.still(helper, EntityType.PIG, new Vec3(6.5, 1.0, 6.5));
        Pig b = TestUtil.still(helper, EntityType.PIG, new Vec3(9.5, 1.0, 6.5));
        Pig c = TestUtil.still(helper, EntityType.PIG, new Vec3(12.5, 1.0, 6.5));
        Leashes.attach(a, b);
        LeadTiers.set(a, LeadTier.DIAMOND);
        Leashes.attach(b, c);
        LeadTiers.set(b, LeadTier.IRON);
        FakePlayer player = TestUtil.player(helper, new Vec3(4.5, 1.0, 6.5));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(UlItems.NETHERITE_LEAD.get()));
        player.interactOn(a, InteractionHand.MAIN_HAND);
        String got = String.format(Locale.ROOT, "A held by %s (%s), B by %s (%s), C by %s (%s)",
                who(Leashes.holder(a), player, a, b, c), LeadTiers.of(a), who(Leashes.holder(b), player, a, b, c), LeadTiers.of(b),
                who(Leashes.holder(c), player, a, b, c), LeadTiers.of(c));
        if (Leashes.holder(a) != player || Leashes.holder(b) != a || Leashes.holder(c) != b
                || LeadTiers.of(a) != LeadTier.NETHERITE || LeadTiers.of(b) != LeadTier.DIAMOND || LeadTiers.of(c) != LeadTier.IRON) {
            helper.fail("the chain came out as: " + got);
        } else if (!player.getMainHandItem().isEmpty()) {
            helper.fail("the netherite lead was not used");
        } else {
            helper.succeed();
        }
    }

    /**
     * A fence-to-fence rope tied with an iron lead is an iron rope: saved as one, holding 1.5 times the pull on a
     * ship, and cut it drops an iron lead.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 20)
    public static void ropeKeepsItsLeadsTier(GameTestHelper helper) {
        BlockPos a = new BlockPos(4, 1, 8), b = new BlockPos(12, 1, 8);
        helper.setBlock(a, Blocks.OAK_FENCE);
        helper.setBlock(b, Blocks.OAK_FENCE);
        FakePlayer player = TestUtil.player(helper, new Vec3(8.5, 1.0, 9.5));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(UlItems.IRON_LEAD.get()));
        TetherTests.use(helper, player, a, InteractionHand.MAIN_HAND);
        TetherTests.use(helper, player, b, InteractionHand.MAIN_HAND);
        Tether rope = TetherTests.only(helper, a);
        if (rope == null || rope.carried() || rope.tier() != LeadTier.IRON) {
            helper.fail("an iron lead used on two fences gave " + rope);
            return;
        }
        double plain = Tethers.breakingStrength(LeadTier.PLAIN), iron = Tethers.breakingStrength(LeadTier.IRON);
        if (Math.abs(iron - plain * 1.5) > 1.0E-6) {
            helper.fail("an iron rope breaks at " + iron + " N, a plain one at " + plain);
            return;
        }
        Tethers.cut(helper.getLevel(), helper.absolutePos(a), null);
        int irons = 0, plains = 0;
        for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(a)).inflate(3.0))) {
            if (e.getItem().is(UlItems.IRON_LEAD.get())) irons += e.getItem().getCount();
            if (e.getItem().is(Items.LEAD)) plains += e.getItem().getCount();
        }
        if (irons != 1 || plains != 0) helper.fail("the cut iron rope dropped " + irons + " iron leads and " + plains + " plain ones");
        else helper.succeed();
    }

    /**
     * A knot whose only lead is a netherite one stretched 30 blocks is not empty: knots look for what they hold as far
     * as the longest lead reaches (they looked 20 blocks out, took themselves for empty after their 5 s of grace and
     * dropped the lead).
     */
    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public static void aKnotHoldingAStretchedLeadIsNotEmpty(GameTestHelper helper) {
        Case c = hang(helper, new BlockPos(6, 1, 6), LeadTier.NETHERITE, 30.0, true);
        LeashFenceKnotEntity knot = (LeashFenceKnotEntity) Leashes.holder(c.pig);
        helper.runAfterDelay(150, () -> {
            if (knot == null || knot.isRemoved()) helper.fail("the knot cleared itself away as empty");
            else if (!c.pig.isLeashed()) helper.fail("the netherite lead at 30 blocks dropped");
            else helper.succeed();
        });
    }

    /** What a lead is made of is saved with the entity it is on. */
    @GameTest(template = PLATFORM, timeoutTicks = 20)
    public static void leadTierIsSavedWithTheEntity(GameTestHelper helper) {
        LeashFenceKnotEntity knot = TestUtil.knot(helper, new BlockPos(6, 1, 6));
        Pig pig = TestUtil.still(helper, EntityType.PIG, new Vec3(9.5, 1.0, 6.5));
        Leashes.attach(pig, knot);
        LeadTiers.set(pig, LeadTier.NETHERITE);
        CompoundTag saved = pig.saveWithoutId(new CompoundTag());
        Pig loaded = EntityType.PIG.create(helper.getLevel());
        if (loaded == null) {
            helper.fail("no pig");
            return;
        }
        loaded.load(saved);
        if (LeadTiers.of(loaded) != LeadTier.NETHERITE) helper.fail("loaded back, the lead is " + LeadTiers.of(loaded));
        else helper.succeed();
    }

    /**
     * With Vanilla Backport its own leash tick snaps the leads of pathfinding mobs and boats, and its happy ghast
     * has its own distances: each answers an upgraded lead's break distance there, while this mod still reads the
     * plain snap distance for its own rules (the tow length). Without Vanilla Backport there is nothing to check.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 20)
    public static void vanillaBackportSnapsUpgradedLeadsLater(GameTestHelper helper) {
        if (!VanillaBackport.present()) {
            helper.succeed();
            return;
        }
        LeashFenceKnotEntity knot = TestUtil.knot(helper, new BlockPos(6, 1, 6));
        Boat boat = helper.spawn(EntityType.BOAT, new Vec3(9.5, 1.0, 6.5));
        Leashes.attach(boat, knot);
        LeadTiers.set(boat, LeadTier.DIAMOND);
        double theirs = VanillaBackport.snapDistance(boat), ours = LeashPhysics.snapDistance(boat), breaks = LeashPhysics.breakDistance(boat);
        if (Math.abs(theirs - 22.5) > 1.0E-6 || Math.abs(ours - 12.0) > 1.0E-6 || Math.abs(breaks - 22.5) > 1.0E-6) {
            helper.fail("a boat on a diamond lead: Vanilla Backport snaps at " + fmt(theirs) + ", the plain distance reads "
                    + fmt(ours) + ", it breaks at " + fmt(breaks) + " (want 22.5 / 12 / 22.5)");
            return;
        }
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.fromNamespaceAndPath("minecraft", "happy_ghast"));
        Entity ghast = type == null ? null : helper.spawn(type, new Vec3(12.0, 8.0, 12.0));
        if (ghast == null) {
            helper.fail("no happy ghast");
            return;
        }
        if (ghast instanceof net.minecraft.world.entity.Mob mob) mob.setNoAi(true);
        double ghastPlain = VanillaBackport.snapDistance(ghast);
        Leashes.attach(ghast, knot);
        LeadTiers.set(ghast, LeadTier.IRON);
        double ghastIron = VanillaBackport.snapDistance(ghast);
        double want = LeadTiers.breakDistance(LeashPhysics.freeLength(ghast), ghastPlain, LeadTier.IRON.strength());
        if (Math.abs(ghastIron - want) > 1.0E-6 || Math.abs(LeashPhysics.snapDistance(ghast) - ghastPlain) > 1.0E-6) {
            helper.fail("a happy ghast on an iron lead: Vanilla Backport snaps at " + fmt(ghastIron) + " (want " + fmt(want)
                    + "), plain " + fmt(ghastPlain) + ", the plain distance reads " + fmt(LeashPhysics.snapDistance(ghast)));
        } else {
            helper.succeed();
        }
    }

    // ------------------------------------------------------------------ helpers

    private record Case(Pig pig, BlockPos fence, LeadTier tier, double distance, boolean holds) {}

    /** A pig without AI hung {@code distance} straight above a knot on the fence at {@code rel}, on a lead of {@code tier}. */
    private static Case hang(GameTestHelper helper, BlockPos rel, LeadTier tier, double distance, boolean holds) {
        LeashFenceKnotEntity knot = TestUtil.knot(helper, rel);
        Vec3 knotCentre = helper.relativeVec(LeashGeometry.centre(knot));
        double pigHalf = EntityType.PIG.getHeight() / 2.0;
        Pig pig = TestUtil.still(helper, EntityType.PIG, new Vec3(knotCentre.x, knotCentre.y + distance - pigHalf, knotCentre.z));
        pig.setLeashedTo(knot, true);
        LeadTiers.set(pig, tier);
        double measured = TestUtil.distance(pig, knot);
        return new Case(pig, rel, tier, measured, holds);
    }

    /** Items of {@code item} in the column above the fence at {@code rel} (where a hung pig's lead drops). */
    private static int itemsAbove(GameTestHelper helper, BlockPos rel, Item item) {
        BlockPos abs = helper.absolutePos(rel);
        ServerLevel level = helper.getLevel();
        int n = 0;
        for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new AABB(abs).inflate(1.2, 0.0, 1.2).expandTowards(0.0, 40.0, 0.0))) {
            if (e.getItem().is(item)) n += e.getItem().getCount();
        }
        return n;
    }

    private static String who(Entity holder, Entity player, Entity a, Entity b, Entity c) {
        if (holder == null) return "nothing";
        if (holder == player) return "you";
        return holder == a ? "A" : holder == b ? "B" : holder == c ? "C" : holder.toString();
    }

    private static String name(Item item) {
        return String.valueOf(ForgeRegistries.ITEMS.getKey(item));
    }
}
