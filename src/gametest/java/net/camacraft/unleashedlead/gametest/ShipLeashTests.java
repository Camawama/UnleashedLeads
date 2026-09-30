package net.camacraft.unleashedlead.gametest;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import net.camacraft.camafy.compat.vs.ShipRef;
import net.camacraft.camafy.compat.vs.Ships;
import net.camacraft.unleashedlead.UnleashedLead;
import net.camacraft.unleashedlead.leash.LeashGeometry;
import net.camacraft.unleashedlead.leash.Leashes;
import net.camacraft.unleashedlead.tether.Knots;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.valkyrienskies.core.api.ships.ServerShip;
import org.valkyrienskies.core.api.ships.properties.ChunkClaim;
import org.valkyrienskies.mod.common.assembly.ShipAssembler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Leads from mobs to a knot on a real Valkyrien Skies ship's fence, while the ship goes out of
 * range and comes back. The mob is an ordinary entity in the world and keeps ticking all along;
 * VS loads the ship, and force-loads its chunks, only while a player is near it.
 *
 * <p><b>Names VS types</b> ({@link ShipAssembler}, {@link ServerShip}) to build the ship, so it is
 * compiled only with {@code -PshipTests}, like {@code ShipTetherTests}, whose rig this follows: a
 * fake player in the server's player list is what VS loads ships for. The player leaves the way a
 * real one does, by going far away (VS hears of a player leaving the server only from its
 * connection, which a fake player hasn't got), and the ship's chunks are then left to VS, which
 * unloads them and the knot with them (knots are never saved). It runs in a batch of its own, far
 * from the rope tests' ships. Every step is logged ({@code [UL ship lead test]}).
 */
@GameTestHolder(UnleashedLead.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShipLeashTests {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int TIMEOUT = 1600;
    /** Its own batch: no other test's watcher may keep the ship loaded. */
    private static final String BATCH = "shipLeads";
    /** How far from its plot (along z) the test builds: well clear of the rope tests' ships (4096). */
    private static final int AWAY = 8192;

    private ShipLeashTests() {}

    /**
     * A pig in the world tied to the fence on a static raft, 4 blocks away. The only player goes
     * far away: VS lets go of the raft's chunks, and the knot unloads with them while the pig, in
     * the world, goes on ticking (vanilla dropped the lead there). The pig keeps its lead as a
     * saved one, and a second pig loaded with that lead meanwhile (a mob loading before its ship's
     * chunks) waits too, past vanilla's 100 ticks, with no knot made on the fence. The player comes
     * back: both are tied to one knot on the raft's fence, 4 blocks away, and no lead ever dropped.
     * (VS keeps the raft itself loaded here, only its chunks go: whether it is logged.)
     */
    @GameTest(template = TestUtil.PLATFORM, timeoutTicks = TIMEOUT, batch = BATCH)
    public static void leadToAShipWaitsWhileTheShipIsAway(GameTestHelper helper) {
        if (!Ships.available()) {
            helper.fail("Valkyrien Skies isn't running");
            return;
        }
        ServerLevel level = helper.getLevel();
        BlockPos o = origin(helper);
        Vec3 near = new Vec3(o.getX() + 1.5, o.getY() + 16.0, o.getZ() + 1.5);
        Vec3 far = near.add(0.0, 0.0, 20000.0);
        // a 3x1x3 plank raft three blocks up with a fence in the middle of its deck, and beside it a
        // stone pillar level with the deck for the pig
        Set<BlockPos> blocks = new HashSet<>(fill(level, o.offset(0, 3, 0), o.offset(2, 3, 2), Blocks.OAK_PLANKS.defaultBlockState()));
        level.setBlock(o.offset(1, 4, 1), Blocks.OAK_FENCE.defaultBlockState(), Block.UPDATE_ALL);
        blocks.add(o.offset(1, 4, 1));
        fill(level, o.offset(5, 0, 1), o.offset(5, 3, 1), Blocks.STONE.defaultBlockState());
        List<ServerPlayer> players = playerList(level);
        FakePlayer watcher = FakePlayerFactory.get(level,
                new GameProfile(UUID.nameUUIDFromBytes(("ul-ship-lead-test-" + o).getBytes()), "[UL ship lead test]"));
        watcher.setPos(near.x, near.y, near.z);
        if (!players.contains(watcher)) players.add(watcher);
        ServerShip ship = ShipAssembler.assembleToShip(level, blocks, 1.0);
        if (ship == null) {
            players.remove(watcher);
            helper.fail("VS couldn't assemble a ship");
            return;
        }
        ship.setStatic(true);
        ChunkClaim claim = ship.getChunkClaim();
        forceClaim(level, claim, true); // until VS has the raft (then its chunks are VS's to load)
        ShipRef ref = ShipRef.of(level, ship.getId()); // resolved when used: VS loads a new ship a tick later
        Pig pig = stillPig(level, o.getX() + 5.5, o.getY() + 4.0, o.getZ() + 1.5);
        AABB around = new AABB(o).inflate(16.0);
        BlockPos[] fence = new BlockPos[1];
        LeashFenceKnotEntity[] knot = new LeashFenceKnotEntity[1];
        Pig[] loaded = new Pig[1];
        helper.onEachTick(() -> {
            if (level.getGameTime() % 100 == 0 && fence[0] != null) {
                LOGGER.info("[UL ship lead test] t{}: raft loaded {}, fence chunk loaded {}, knot {}, pig held by {}, saved to {}",
                        level.getGameTime(), ref.isLoaded(), level.isLoaded(fence[0]), knot[0], pig.getLeashHolder(), savedFence(pig));
            }
        });
        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertTrue(ref.isLoaded(), "VS hasn't loaded the raft yet");
                    fence[0] = shipFence(ref);
                    helper.assertTrue(fence[0] != null, "the raft has no fence yet");
                })
                .thenExecute(() -> {
                    knot[0] = Knots.getOrCreate(level, fence[0]);
                    Leashes.attach(pig, knot[0]);
                })
                .thenIdle(5)
                .thenExecute(() -> {
                    report("tied, raft loaded", pig, knot[0]);
                    helper.assertTrue(pig.getLeashHolder() == knot[0], "the lead to the raft's fence broke with the raft loaded");
                    // the only player goes far away: VS lets go of the raft's chunks, and the knot unloads with them
                    watcher.setPos(far.x, far.y, far.z);
                    forceClaim(level, claim, false);
                })
                .thenWaitUntil(() -> helper.assertTrue(knot[0].isRemoved(), "the knot's chunk hasn't unloaded yet"))
                .thenIdle(2)
                .thenExecute(() -> {
                    report("knot " + knot[0].getRemovalReason() + ", raft loaded " + ref.isLoaded(), pig, null);
                    helper.assertTrue(leads(level, around) == 0, "a lead dropped when the knot unloaded");
                    helper.assertTrue(!pig.isLeashed(), "the pig is tied to " + pig.getLeashHolder() + " with the raft's chunk unloaded");
                    CompoundTag saved = pig.saveWithoutId(new CompoundTag());
                    helper.assertTrue(fence[0].equals(savedFence(saved)), "the pig didn't keep its lead to the raft's fence: " + saved.get("Leash"));
                    // a pig loading with that lead while the raft is away
                    loaded[0] = EntityType.PIG.create(level);
                    loaded[0].load(saved);
                    loaded[0].setUUID(UUID.randomUUID());
                    level.addFreshEntity(loaded[0]);
                })
                .thenIdle(120) // past vanilla's 100 ticks of patience
                .thenExecute(() -> {
                    report("raft away 120 ticks", pig, null);
                    helper.assertTrue(!pig.isLeashed() && !loaded[0].isLeashed(), "a lead was tied back while the raft was away");
                    helper.assertTrue(Knots.find(level, fence[0]) == null, "a knot was made on the fence of a raft that isn't loaded");
                    helper.assertTrue(fence[0].equals(savedFence(pig)) && fence[0].equals(savedFence(loaded[0])),
                            "a pig forgot its lead while the raft was away: " + savedFence(pig) + ", " + savedFence(loaded[0]));
                    helper.assertTrue(leads(level, around) == 0, "a lead dropped while the raft was away");
                    watcher.setPos(near.x, near.y, near.z); // the player comes back: VS loads the raft again
                })
                .thenWaitUntil(() -> {
                    LeashFenceKnotEntity back = Knots.find(level, fence[0]);
                    helper.assertTrue(back != null && pig.getLeashHolder() == back && loaded[0].getLeashHolder() == back,
                            "the leads aren't tied back to the raft's fence yet: " + pig.getLeashHolder() + ", " + loaded[0].getLeashHolder());
                })
                .thenIdle(10)
                .thenExecute(() -> {
                    players.remove(watcher);
                    LeashFenceKnotEntity back = Knots.find(level, fence[0]);
                    report("raft back", pig, back);
                    helper.assertTrue(back != null && pig.getLeashHolder() == back && loaded[0].getLeashHolder() == back,
                            "a lead broke after it was tied back: " + pig.getLeashHolder() + ", " + loaded[0].getLeashHolder());
                    helper.assertTrue(Ships.inShipyard(level, back.getX(), back.getY(), back.getZ()), "the knot wasn't made on the raft");
                    double span = LeashGeometry.span(pig, back);
                    helper.assertTrue(span < 6.0, "the lead measures " + fmt(span) + " to the knot on the raft");
                    helper.assertTrue(leads(level, around) == 0, "a lead dropped");
                    helper.succeed();
                });
    }

    // ------------------------------------------------------------------ helpers

    /** Force-loads (or stops force-loading) the chunks around the middle of a ship's claim, as the rope tests' rig does. */
    private static void forceClaim(ServerLevel level, ChunkClaim claim, boolean force) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.setChunkForced(claim.getXMiddle() + dx, claim.getZMiddle() + dz, force);
            }
        }
    }

    /** A pig with no AI (it stays where it is put, however the lead pulls) at a world position. */
    private static Pig stillPig(ServerLevel level, double x, double y, double z) {
        Pig pig = EntityType.PIG.create(level);
        pig.moveTo(x, y, z, 0.0F, 0.0F);
        pig.setNoAi(true);
        level.addFreshEntity(pig);
        return pig;
    }

    /** The ship's fence, in its own (shipyard) space, or null before VS has its blocks. */
    @Nullable
    private static BlockPos shipFence(ShipRef ref) {
        List<BlockPos> found = new ArrayList<>();
        ref.forEachBlock(state -> state.is(BlockTags.FENCES), (pos, state) -> found.add(pos.immutable()));
        return found.isEmpty() ? null : found.get(0);
    }

    /** The fence a mob's saved lead is tied to ({@code Leash: X, Y, Z}), or null. */
    @Nullable
    private static BlockPos savedFence(Mob mob) {
        return savedFence(mob.saveWithoutId(new CompoundTag()));
    }

    @Nullable
    private static BlockPos savedFence(CompoundTag saved) {
        if (!saved.contains("Leash", 10)) return null;
        CompoundTag leash = saved.getCompound("Leash");
        return leash.contains("X", 99) ? NbtUtils.readBlockPos(leash) : null;
    }

    /** The test is built {@link #AWAY} blocks from its plot, in chunks loaded now. */
    private static BlockPos origin(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos plot = helper.absolutePos(BlockPos.ZERO);
        int groundTop = level.getMinBuildHeight() + 4; // the flat world: bedrock, dirt, dirt, grass
        BlockPos o = new BlockPos(plot.getX() + 8, groundTop, plot.getZ() + AWAY);
        for (int cx = (o.getX() - 16) >> 4; cx <= (o.getX() + 24) >> 4; cx++) {
            for (int cz = (o.getZ() - 16) >> 4; cz <= (o.getZ() + 24) >> 4; cz++) {
                level.setChunkForced(cx, cz, true);
                level.getChunk(cx, cz);
            }
        }
        return o;
    }

    private static List<BlockPos> fill(ServerLevel level, BlockPos from, BlockPos to, BlockState state) {
        List<BlockPos> placed = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            level.setBlock(pos, state, Block.UPDATE_ALL);
            placed.add(pos.immutable());
        }
        return placed;
    }

    private static int leads(ServerLevel level, AABB box) {
        int n = 0;
        for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, box)) {
            if (e.getItem().is(Items.LEAD)) n += e.getItem().getCount();
        }
        return n;
    }

    private static void report(String what, Pig pig, @Nullable LeashFenceKnotEntity knot) {
        String span = knot == null || knot.isRemoved() || !LeashGeometry.placed(knot) ? "-" : fmt(LeashGeometry.span(pig, knot));
        LOGGER.info("[UL ship lead test] {}: pig held by {}, saved to {}, lead {}", what, pig.getLeashHolder(), savedFence(pig), span);
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    /** The server's own player list (what VS reads), not the read-only view {@code getPlayers()} hands out. */
    @SuppressWarnings("unchecked")
    private static List<ServerPlayer> playerList(ServerLevel level) {
        try {
            return (List<ServerPlayer>) ObfuscationReflectionHelper.findField(PlayerList.class, "f_11196_")
                    .get(level.getServer().getPlayerList());
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("can't reach the server's player list", e);
        }
    }
}
