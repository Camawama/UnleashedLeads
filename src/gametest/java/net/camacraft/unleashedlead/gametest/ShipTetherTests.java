package net.camacraft.unleashedlead.gametest;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import net.camacraft.camafy.compat.vs.ShipForces;
import net.camacraft.camafy.compat.vs.ShipRef;
import net.camacraft.camafy.compat.vs.Ships;
import net.camacraft.unleashedlead.UlConfig;
import net.camacraft.unleashedlead.UnleashedLead;
import net.camacraft.unleashedlead.tether.Knots;
import net.camacraft.unleashedlead.tether.Tether;
import net.camacraft.unleashedlead.tether.TetherData;
import net.camacraft.unleashedlead.tether.Tethers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
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
 * Fence-to-fence ropes on real Valkyrien Skies ships, with the whole pull running: Camafy's
 * physics hook on VS's physics thread, the server's upkeep, the snap.
 *
 * <p><b>Names VS types</b> ({@link ShipAssembler}, {@link ServerShip}) to build ships, so it is
 * compiled only with {@code -PshipTests}: {@code gradlew runGameTestServer -PshipTests
 * [-PvsBackend=classic|physx|jolt]} (PhysX by default; build.gradle seeds the test world's VS config).
 *
 * <p>How a scenario runs (Full Stop's rig): the terrain (stone pillars carrying the world fences)
 * and the raft's blocks are placed in chunks nobody has loaded, force-loaded in the same tick; the
 * raft is assembled into a ship, held static while the ropes are tied through the same block use a
 * click takes, then let go, held up by a continuous force equal to its weight (it floats), and
 * pushed with Camafy's impulse once VS simulates it. A fake player stands by each ship in the
 * server's player list, since VS only loads and simulates ships near a player. The tests wait on
 * what the ship does, never on a fixed number of ticks: the test server runs ahead of real time
 * while VS's physics thread keeps it. Every measurement is logged ({@code [UL ship test]}).
 */
@GameTestHolder(UnleashedLead.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShipTetherTests {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int TIMEOUT = 1600;
    private static final ResourceLocation FLOAT = UnleashedLead.id("gametest_float");
    /** A test's own engine: a steady push at the centre of mass. */
    private static final ResourceLocation THRUST = UnleashedLead.id("gametest_thrust");
    private static final String BACKEND = System.getProperty("unleashedlead.test.vsBackend", "physx");
    /** How far from its plot (along z) each test builds, in chunks nothing has loaded yet. */
    private static final int AWAY = 4096;

    private ShipTetherTests() {}

    // ------------------------------------------------------------------ scenarios

    /**
     * A 3x1x3 plank raft (about 5 t) tied by one rope to a fence on a stone pillar, 5 blocks
     * away, and pushed straight away from it at 3 m/s: the rope stops it near the rope's length
     * (well under 1.5 blocks of stretch) and pulls it back; nothing snaps.
     */
    @GameTest(template = TestUtil.PLATFORM, timeoutTicks = TIMEOUT)
    public static void raftPushedAwayStopsNearTheRopeLength(GameTestHelper helper) {
        BlockPos o = origin(helper, 0);
        Rig rig = raftWithRopes(helper, o, 1, Blocks.OAK_PLANKS, 3);
        if (rig == null) return;
        pushAndWatch(helper, rig, 3.0, () -> {
            double stretch = rig.maxStretch();
            report("one rope, plank raft, 3 m/s", rig.summary());
            rig.pin();
            helper.assertTrue(rig.ropesLeft() == 1, "the rope snapped under a 3 m/s push (" + rig.summary() + ")");
            helper.assertTrue(rig.maxDistance() > rig.length(0), "the rope never went taut (" + rig.summary() + ")");
            helper.assertTrue(stretch < 1.5, "the raft stretched the rope " + fmt(stretch) + " blocks past its length");
            helper.succeed();
        });
    }

    /**
     * A two-deck plank raft (about 9 t) with its fence between the decks, level with its centre of
     * mass, under a steady engine pull of 2 m/s² straight away from its fence: the rope stops it
     * and it settles, without shaking, where the rope's pull equals the engine's: at the rope's
     * length plus {@code force / k}, {@code k = stiffness · √(mass / 1 t)}.
     *
     * <p>The raft floats on "calm water": its vertical speed and its spin are damped, as a hull's
     * drag would. The rope only damps motion along itself, so in empty air a raft swings across
     * the rope (up and down, or turning about the knot) like a pendulum with nothing to stop it.
     */
    @GameTest(template = TestUtil.PLATFORM, timeoutTicks = TIMEOUT)
    public static void propelledRaftIsHeldAndSettles(GameTestHelper helper) {
        BlockPos o = origin(helper, 0);
        ServerLevel level = helper.getLevel();
        fill(level, o.offset(-5, 0, 1), o.offset(-5, 3, 1), Blocks.STONE.defaultBlockState());
        BlockPos worldFence = o.offset(-5, 4, 1);
        level.setBlock(worldFence, Blocks.OAK_FENCE.defaultBlockState(), Block.UPDATE_ALL);
        Set<BlockPos> blocks = new HashSet<>(fill(level, o.offset(0, 3, 0), o.offset(2, 3, 2), Blocks.OAK_PLANKS.defaultBlockState()));
        blocks.addAll(fill(level, o.offset(0, 5, 0), o.offset(2, 5, 2), Blocks.OAK_PLANKS.defaultBlockState()));
        level.setBlock(o.offset(1, 4, 1), Blocks.OAK_FENCE.defaultBlockState(), Block.UPDATE_ALL);
        blocks.add(o.offset(1, 4, 1));
        Rig rig = Rig.build(helper, blocks);
        if (rig == null) return;
        rig.worldFences.add(worldFence);
        rig.tieWhenReady = true;
        rig.tier = tier(helper, o.offset(-2, 5, 1));
        rig.trace = true;
        rig.calmWater = true;
        double accel = 2.0;
        helper.onEachTick(rig::record);
        helper.startSequence()
                .thenWaitUntil(rig::demandReady)
                .thenExecute(rig::release)
                .thenWaitUntil(rig::demandSimulated)
                .thenExecute(() -> {
                    rig.push(Vec3.ZERO);
                    rig.thrust(new Vec3(accel * rig.ref.mass(), 0.0, 0.0));
                })
                .thenWaitUntil(() -> rig.demandSettled(40))
                .thenExecute(() -> {
                    double mass = rig.ref.mass();
                    double force = accel * mass;
                    double k = UlConfig.num(UlConfig.TETHER_STIFFNESS) * Math.sqrt(mass / 1000.0);
                    double expected = force / k;
                    double stretch = rig.distance(0) - rig.length(0);
                    double tension = rig.ropes.get(0).cell().tension();
                    report("plank raft under a steady 2 m/s^2 engine", rig.summary() + ", engine " + fmt(force) + " N, k "
                            + fmt(k) + " N/m, settled stretch " + fmt(stretch) + " (expected " + fmt(expected) + "), tension "
                            + fmt(tension) + " N");
                    rig.pin();
                    helper.assertTrue(rig.ropesLeft() == 1, "a boat engine snapped the rope");
                    helper.assertTrue(Math.abs(stretch - expected) < 0.1, "settled " + fmt(stretch) + " past the rope's length, "
                            + fmt(expected) + " expected");
                    helper.assertTrue(Math.abs(tension - force) < 0.15 * force, "the rope pulls " + fmt(tension)
                            + " N against a " + fmt(force) + " N engine");
                    helper.succeed();
                });
    }

    /**
     * A 5x1x3 raft tied at its bow (a fence at one end) and pushed away across its length: the
     * rope pulls at the bow, off the centre of mass, so the raft swings around the knot.
     */
    @GameTest(template = TestUtil.PLATFORM, timeoutTicks = TIMEOUT)
    public static void ropeAtTheBowSwingsTheShip(GameTestHelper helper) {
        BlockPos o = origin(helper, 0);
        ServerLevel level = helper.getLevel();
        fill(level, o.offset(4, 0, -5), o.offset(4, 3, -5), Blocks.STONE.defaultBlockState());
        BlockPos worldFence = o.offset(4, 4, -5);
        level.setBlock(worldFence, Blocks.OAK_FENCE.defaultBlockState(), Block.UPDATE_ALL);
        Set<BlockPos> blocks = new HashSet<>(fill(level, o.offset(0, 3, 0), o.offset(4, 3, 2), Blocks.OAK_PLANKS.defaultBlockState()));
        level.setBlock(o.offset(4, 4, 1), Blocks.OAK_FENCE.defaultBlockState(), Block.UPDATE_ALL);
        blocks.add(o.offset(4, 4, 1));
        Rig rig = Rig.build(helper, blocks);
        if (rig == null) return;
        rig.worldFences.add(worldFence);
        rig.tieWhenReady = true;
        rig.tier = tier(helper, o.offset(3, 5, -2));
        helper.onEachTick(rig::record);
        helper.startSequence()
                .thenWaitUntil(rig::demandReady)
                .thenExecute(rig::release)
                .thenWaitUntil(rig::demandSimulated)
                .thenExecute(() -> rig.push(new Vec3(0.0, 0.0, 2.0)))
                .thenWaitUntil(rig::demandStoppedByRope)
                .thenIdle(10)
                .thenExecute(() -> {
                    report("5x1x3 raft tied at its bow, pushed at 2 m/s", rig.summary() + ", fastest yaw " + fmt(rig.maxYawRate)
                            + " rad/s");
                    rig.pin();
                    helper.assertTrue(rig.ropesLeft() == 1, "the rope snapped");
                    helper.assertTrue(rig.maxYawRate > 0.15, "the raft didn't swing around its bow knot: fastest yaw "
                            + fmt(rig.maxYawRate) + " rad/s");
                    helper.succeed();
                });
    }

    /**
     * Two identical rafts pushed away at 3 m/s side by side: one on one rope, the other on three
     * parallel ropes. The three ropes add up: that raft stretches them less (by about √3).
     */
    @GameTest(template = TestUtil.PLATFORM, timeoutTicks = TIMEOUT)
    public static void threeRopesStretchLessThanOne(GameTestHelper helper) {
        Rig one = raftWithRopes(helper, origin(helper, 0), 1, Blocks.OAK_PLANKS, 3);
        Rig three = one == null ? null : raftWithRopes(helper, origin(helper, 40), 3, Blocks.OAK_PLANKS, 3);
        if (one == null || three == null) return;
        helper.onEachTick(one::record);
        helper.onEachTick(three::record);
        helper.startSequence()
                .thenWaitUntil(one::demandReady)
                .thenWaitUntil(three::demandReady)
                .thenExecute(one::release)
                .thenExecute(three::release)
                .thenWaitUntil(one::demandSimulated)
                .thenWaitUntil(three::demandSimulated)
                .thenExecute(() -> {
                    one.push(new Vec3(3.0, 0.0, 0.0));
                    three.push(new Vec3(3.0, 0.0, 0.0));
                })
                .thenWaitUntil(one::demandStoppedByRope)
                .thenWaitUntil(three::demandStoppedByRope)
                .thenExecute(() -> {
                    double s1 = one.maxStretch(), s3 = three.maxStretch();
                    report("one rope vs three ropes, plank rafts, 3 m/s", "one: " + one.summary() + " | three: " + three.summary()
                            + " | stretch ratio " + fmt(s3 / s1));
                    one.pin();
                    three.pin();
                    helper.assertTrue(one.ropesLeft() == 1 && three.ropesLeft() == 3, "a rope snapped");
                    helper.assertTrue(s3 < 0.8 * s1, "three ropes stretched " + fmt(s3) + ", one rope " + fmt(s1)
                            + ": they don't add up");
                    helper.succeed();
                });
    }

    /**
     * A 5x1x5 stone raft (about 63 t) on one rope, rammed away at 15 m/s: the jerk passes the
     * default breaking strength, the rope snaps, its lead drops, and the raft sails on.
     */
    @GameTest(template = TestUtil.PLATFORM, timeoutTicks = TIMEOUT)
    public static void aHardPushSnapsTheRope(GameTestHelper helper) {
        BlockPos o = origin(helper, 0);
        Rig rig = raftWithRopes(helper, o, 1, Blocks.STONE, 5);
        if (rig == null) return;
        helper.onEachTick(rig::record);
        helper.startSequence()
                .thenWaitUntil(rig::demandReady)
                .thenExecute(rig::release)
                .thenWaitUntil(rig::demandSimulated)
                .thenExecute(() -> rig.push(new Vec3(15.0, 0.0, 0.0)))
                .thenWaitUntil(() -> helper.assertTrue(rig.ropesLeft() == 0, "the rope hasn't snapped yet (" + rig.summary() + ")"))
                .thenWaitUntil(() -> helper.assertTrue(rig.distance(0) > rig.length(0) + 3.0, "the raft hasn't sailed on yet"))
                .thenExecute(() -> {
                    int leads = leads(helper.getLevel(), new AABB(rig.worldFences.get(0)).inflate(32.0));
                    double breaking = UlConfig.num(UlConfig.TETHER_BREAKING_STRENGTH);
                    report("stone raft rammed away at 15 m/s", rig.summary() + ", breaking strength " + fmt(breaking)
                            + " N, leads dropped " + leads);
                    rig.pin();
                    helper.assertTrue(rig.peakTension >= breaking, "it snapped below the breaking strength: " + fmt(rig.peakTension));
                    helper.assertTrue(leads == 1, "the snapped rope dropped " + leads + " leads, not 1");
                    helper.succeed();
                });
    }

    /**
     * Two plank rafts, 5 blocks apart, tied fence to fence. The far one is pushed away at 3 m/s:
     * the rope pulls the near one after it (equal and opposite), and slows the pushed one.
     */
    @GameTest(template = TestUtil.PLATFORM, timeoutTicks = TIMEOUT)
    public static void shipToShipRopePullsBoth(GameTestHelper helper) {
        BlockPos o = origin(helper, 0);
        ServerLevel level = helper.getLevel();
        Set<BlockPos> nearBlocks = raftBlocks(level, o, Blocks.OAK_PLANKS, 3, 1);
        Set<BlockPos> farBlocks = raftBlocks(level, o.offset(8, 0, 0), Blocks.OAK_PLANKS, 3, 1);
        Rig near = Rig.build(helper, nearBlocks);
        Rig far = near == null ? null : Rig.build(helper, farBlocks);
        if (near == null || far == null) return;
        FakePlayer tier = tier(helper, o.offset(5, 6, 1));
        helper.onEachTick(near::record);
        helper.onEachTick(far::record);
        helper.startSequence()
                .thenWaitUntil(near::demandReady)
                .thenWaitUntil(far::demandReady)
                .thenExecute(() -> {
                    BlockPos a = near.shipFences().get(0), b = far.shipFences().get(0);
                    use(level, tier, a);
                    use(level, tier, b);
                    Tether rope = ropeAt(level, a);
                    helper.assertTrue(rope != null && b.equals(rope.b()), "the rope wasn't tied ship to ship: " + rope);
                    near.watch(rope, a, b);
                    far.watch(rope, a, b);
                })
                .thenExecute(near::release)
                .thenExecute(far::release)
                .thenWaitUntil(near::demandSimulated)
                .thenWaitUntil(far::demandSimulated)
                .thenExecute(() -> {
                    near.push(Vec3.ZERO);
                    far.push(new Vec3(3.0, 0.0, 0.0));
                })
                .thenWaitUntil(() -> helper.assertTrue(near.velocityAlong() > 0.8,
                        "the near raft hasn't been pulled along yet (" + near.summary() + " | " + far.summary() + ")"))
                .thenExecute(() -> {
                    double vNear = near.velocityAlong(), vFar = far.velocityAlong();
                    double momentum = near.ref.mass() * vNear + far.ref.mass() * vFar;
                    double pushed = far.ref.mass() * 3.0;
                    report("ship to ship, plank rafts, far one pushed at 3 m/s", "near v " + fmt(vNear) + ", far v " + fmt(vFar)
                            + ", momentum " + fmt(momentum) + " of " + fmt(pushed) + " | " + near.summary());
                    near.pin();
                    far.pin();
                    helper.assertTrue(near.ropesLeft() == 1, "the ship-to-ship rope snapped");
                    helper.assertTrue(vFar < 2.6, "the pushed raft wasn't held back: still " + fmt(vFar) + " m/s");
                    helper.assertTrue(momentum > 0.5 * pushed && momentum < 1.3 * pushed,
                            "the pulls aren't equal and opposite: momentum " + fmt(momentum) + " of " + fmt(pushed));
                    helper.succeed();
                });
    }

    /**
     * A ship-to-world rope survives its knots unloading and the level's rope data being saved and
     * loaded back (knots are never saved), finds its knots again (the ship's by its shipyard
     * position), and still holds the raft when it is pushed away.
     */
    @GameTest(template = TestUtil.PLATFORM, timeoutTicks = TIMEOUT)
    public static void shipRopeSurvivesSaveAndLoad(GameTestHelper helper) {
        BlockPos o = origin(helper, 0);
        Rig rig = raftWithRopes(helper, o, 1, Blocks.OAK_PLANKS, 3);
        if (rig == null) return;
        ServerLevel level = helper.getLevel();
        helper.onEachTick(rig::record);
        helper.startSequence()
                .thenWaitUntil(rig::demandReady)
                .thenExecute(() -> {
                    Tether before = ropeAt(level, rig.shipFences().get(0));
                    helper.assertTrue(before != null && before.knotA() != null && before.knotB() != null, "no rope to reload");
                    rig.lengthBefore = before.length();
                    before.knotA().remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
                    before.knotB().remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
                    TetherData.reload(level, t -> t == before);
                })
                .thenWaitUntil(() -> {
                    Tether back = ropeAt(level, rig.shipFences().get(0));
                    helper.assertTrue(back != null && back.knotA() != null && back.knotB() != null
                            && !back.knotA().isRemoved() && !back.knotB().isRemoved(), "the reloaded rope hasn't found its knots: " + back);
                    LeashFenceKnotEntity onShip = Knots.find(level, rig.shipFences().get(0));
                    helper.assertTrue(onShip != null && Ships.inShipyard(level, onShip.getX(), onShip.getY(), onShip.getZ()),
                            "the ship's knot wasn't made again on the ship");
                    helper.assertTrue(Math.abs(back.length() - rig.lengthBefore) < 1.0E-9, "the reloaded rope changed length");
                    rig.ropes.clear(); // watch the reloaded rope instead of the one it replaced
                    rig.ends.clear();
                    rig.watch(back, rig.shipFences().get(0), rig.worldFences.get(0));
                })
                .thenExecute(rig::release)
                .thenWaitUntil(rig::demandSimulated)
                .thenExecute(() -> rig.push(new Vec3(3.0, 0.0, 0.0)))
                .thenWaitUntil(rig::demandStoppedByRope)
                .thenExecute(() -> {
                    report("reloaded ship rope, plank raft, 3 m/s", rig.summary());
                    rig.pin();
                    helper.assertTrue(rig.ropesLeft() == 1, "the reloaded rope snapped");
                    helper.assertTrue(rig.maxStretch() < 1.5, "the reloaded rope didn't hold: stretch " + fmt(rig.maxStretch()));
                    helper.assertTrue(leads(level, new AABB(rig.worldFences.get(0)).inflate(16.0)) == 0, "reloading dropped a lead");
                    helper.succeed();
                });
    }

    // ------------------------------------------------------------------ building

    /**
     * A {@code size}x1x{@code size} raft of {@code material} three blocks up, with {@code ropes}
     * fences on its top along z at its middle x, each tied (through a player's clicks) to a fence
     * on a stone pillar 5 blocks further -x. Null (and the test failed) if VS couldn't build it.
     */
    @Nullable
    private static Rig raftWithRopes(GameTestHelper helper, BlockPos o, int ropes, Block material, int size) {
        ServerLevel level = helper.getLevel();
        int mid = size / 2;
        List<BlockPos> worldFences = new ArrayList<>();
        for (int i = 0; i < ropes; i++) {
            int z = mid - (ropes - 1) / 2 + i;
            fill(level, o.offset(-5, 0, z), o.offset(-5, 3, z), Blocks.STONE.defaultBlockState());
            BlockPos fence = o.offset(-5, 4, z);
            level.setBlock(fence, Blocks.OAK_FENCE.defaultBlockState(), Block.UPDATE_ALL);
            worldFences.add(fence);
        }
        Rig rig = Rig.build(helper, raftBlocks(level, o, material, size, ropes));
        if (rig == null) return null;
        rig.worldFences.addAll(worldFences);
        rig.tieWhenReady = true;
        rig.tier = tier(helper, o.offset(-2, 5, mid));
        return rig;
    }

    /** The raft's blocks: a deck three blocks up, and {@code fences} fences on it along z at the middle x. */
    private static Set<BlockPos> raftBlocks(ServerLevel level, BlockPos o, Block material, int size, int fences) {
        Set<BlockPos> blocks = new HashSet<>(fill(level, o.offset(0, 3, 0), o.offset(size - 1, 3, size - 1), material.defaultBlockState()));
        int mid = size / 2;
        for (int i = 0; i < fences; i++) {
            BlockPos fence = o.offset(mid, 4, mid - (fences - 1) / 2 + i);
            level.setBlock(fence, Blocks.OAK_FENCE.defaultBlockState(), Block.UPDATE_ALL);
            blocks.add(fence);
        }
        return blocks;
    }

    /** A player to tie ropes with, standing between the ship and the fences (ropes break past 12 blocks from them). */
    private static FakePlayer tier(GameTestHelper helper, BlockPos at) {
        ServerLevel level = helper.getLevel();
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "[UL tier]"));
        player.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.LEAD, 16));
        return player;
    }

    /** What a right click on the top of the fence at {@code pos} does (a ship's fence: its shipyard position). */
    private static InteractionResult use(ServerLevel level, FakePlayer player, BlockPos pos) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos).add(0.0, 0.5, 0.0), Direction.UP, pos, false);
        return player.gameMode.useItemOn(player, level, player.getItemInHand(InteractionHand.MAIN_HAND), InteractionHand.MAIN_HAND, hit);
    }

    @Nullable
    private static Tether ropeAt(ServerLevel level, BlockPos fence) {
        for (Tether t : Tethers.all(level)) {
            if (t.touches(fence)) return t;
        }
        return null;
    }

    /** The test is built {@link #AWAY} blocks from its plot (plus {@code lane} along x), in chunks loaded now. */
    private static BlockPos origin(GameTestHelper helper, int lane) {
        ServerLevel level = helper.getLevel();
        BlockPos plot = helper.absolutePos(BlockPos.ZERO);
        int groundTop = level.getMinBuildHeight() + 4; // the flat world: bedrock, dirt, dirt, grass
        BlockPos o = new BlockPos(plot.getX() + 8 + lane, groundTop, plot.getZ() + AWAY);
        for (int cx = (o.getX() - 24) >> 4; cx <= (o.getX() + 64) >> 4; cx++) {
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

    /** Push, then wait until the rope has stopped the raft (it no longer moves away), then {@code check}. */
    private static void pushAndWatch(GameTestHelper helper, Rig rig, double speed, Runnable check) {
        helper.onEachTick(rig::record);
        helper.startSequence()
                .thenWaitUntil(rig::demandReady)
                .thenExecute(rig::release)
                .thenWaitUntil(rig::demandSimulated)
                .thenExecute(() -> rig.push(new Vec3(speed, 0.0, 0.0)))
                .thenWaitUntil(rig::demandStoppedByRope)
                .thenExecute(check);
    }

    private static void report(String what, String detail) {
        LOGGER.info("[UL ship test] {} ({}): {}", what, BACKEND, detail);
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

    // ------------------------------------------------------------------ the rig

    /** One test ship, its ropes, and what they did. */
    private static final class Rig {
        final GameTestHelper helper;
        final ServerLevel level;
        final ServerShip ship;
        final ShipRef ref;
        final FakePlayer watcher;
        final int expected;
        final List<BlockPos> worldFences = new ArrayList<>();
        /** Tie each ship fence to its world fence once the ship is ready (then the list below is filled). */
        boolean tieWhenReady;
        @Nullable
        FakePlayer tier;
        final List<Tether> ropes = new ArrayList<>();
        final List<BlockPos[]> ends = new ArrayList<>();
        @Nullable
        List<BlockPos> fencesOnShip;
        double lengthBefore = Double.NaN;
        Vec3 start;
        Vec3 pushDir = new Vec3(1.0, 0.0, 0.0);
        boolean pushed;
        long pushedAt = -1;
        long tautAt = -1;
        double maxDist;
        double peakTension;
        double maxYawRate;
        /** Consecutive ticks the ship has been nearly still (under 0.03 m/s) with its rope taut. */
        int stillTaut;

        private Rig(GameTestHelper helper, ServerShip ship, int expected, FakePlayer watcher) {
            this.helper = helper;
            this.level = helper.getLevel();
            this.ship = ship;
            this.expected = expected;
            this.watcher = watcher;
            this.ref = ShipRef.of(level, ship.getId()); // resolved when used: VS loads a new ship a tick later
        }

        @Nullable
        static Rig build(GameTestHelper helper, Set<BlockPos> blocks) {
            if (!Ships.available()) {
                helper.fail("Valkyrien Skies isn't running");
                return null;
            }
            ServerLevel level = helper.getLevel();
            BlockPos at = blocks.iterator().next();
            FakePlayer watcher = FakePlayerFactory.get(level,
                    new GameProfile(UUID.nameUUIDFromBytes(("ul-ship-test-" + at).getBytes()), "[UL ship test]"));
            watcher.setPos(at.getX() + 0.5, at.getY() + 16.0, at.getZ() + 0.5);
            List<ServerPlayer> players = playerList(level);
            if (!players.contains(watcher)) players.add(watcher);
            ServerShip ship = ShipAssembler.assembleToShip(level, blocks, 1.0);
            if (ship == null) {
                players.remove(watcher);
                helper.fail("VS couldn't assemble a ship");
                return null;
            }
            ship.setStatic(true);
            ChunkClaim claim = ship.getChunkClaim();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    level.setChunkForced(claim.getXMiddle() + dx, claim.getZMiddle() + dz, true);
                }
            }
            return new Rig(helper, ship, blocks.size(), watcher);
        }

        /** The ship's fences, in its own (shipyard) space. */
        List<BlockPos> shipFences() {
            if (fencesOnShip == null || fencesOnShip.isEmpty()) {
                List<BlockPos> found = new ArrayList<>();
                ref.forEachBlock(state -> state.is(BlockTags.FENCES), (pos, state) -> found.add(pos.immutable()));
                found.sort((p, q) -> Integer.compare(p.getZ(), q.getZ()));
                fencesOnShip = found;
            }
            return fencesOnShip;
        }

        int blockCount() {
            int[] n = {0};
            ref.forEachBlock(state -> !state.isAir(), (pos, state) -> n[0]++);
            return n[0];
        }

        /** Game-test condition: VS has the ship with all its blocks; ties its ropes the first time. */
        void demandReady() {
            helper.assertTrue(ref.isLoaded() && Double.isFinite(ref.mass()), "VS hasn't loaded ship " + ship.getId() + " yet");
            int blocks = blockCount();
            helper.assertTrue(blocks == expected, "ship " + ship.getId() + " has " + blocks + " of its " + expected + " blocks");
            if (tieWhenReady && ropes.isEmpty()) tieRopes();
        }

        private void tieRopes() {
            List<BlockPos> onShip = shipFences();
            helper.assertTrue(onShip.size() == worldFences.size(), "the ship has " + onShip.size() + " fences, "
                    + worldFences.size() + " wanted");
            for (int i = 0; i < onShip.size(); i++) {
                BlockPos a = onShip.get(i), b = worldFences.get(i);
                InteractionResult started = use(level, tier, a);
                InteractionResult tied = use(level, tier, b);
                Tether rope = ropeAt(level, a);
                helper.assertTrue(rope != null && b.equals(rope.b()), "rope " + i + " wasn't tied (" + started + ", " + tied
                        + "): " + rope);
                watch(rope, a, b);
            }
        }

        void watch(Tether rope, BlockPos a, BlockPos b) {
            ropes.add(rope);
            ends.add(new BlockPos[]{a, b});
        }

        double length(int i) {
            return ropes.get(i).length();
        }

        double distance(int i) {
            BlockPos[] e = ends.get(i);
            return Tethers.worldAnchor(level, e[0]).distanceTo(Tethers.worldAnchor(level, e[1]));
        }

        /** How many of its ropes are still tied. */
        int ropesLeft() {
            int n = 0;
            List<Tether> all = Tethers.all(level);
            for (Tether t : ropes) {
                if (all.contains(t)) n++;
            }
            return n;
        }

        double maxDistance() {
            double d = maxDist;
            for (Tether t : ropes) d = Math.max(d, t.cell().maxDistance());
            return d;
        }

        double maxStretch() {
            double s = 0.0;
            for (int i = 0; i < ropes.size(); i++) {
                Tether t = ropes.get(i);
                s = Math.max(s, Math.max(t.cell().maxDistance(), maxDist) - t.length());
            }
            return s;
        }

        double velocityAlong() {
            Vec3 v = ref.velocity();
            return v == null ? Double.NaN : v.dot(pushDir);
        }

        /** Every tick: the furthest the knots got, when the rope went taut, and the highest tension. */
        void record() {
            if (ropes.isEmpty() || !ref.isLoaded()) return;
            boolean taut = false;
            for (int i = 0; i < ropes.size(); i++) {
                Tether t = ropes.get(i);
                double d = distance(i);
                if (pushed) maxDist = Math.max(maxDist, d);
                if (pushed && tautAt < 0 && d > t.length()) tautAt = level.getGameTime();
                if (d > t.length()) taut = true;
                peakTension = Math.max(peakTension, t.cell().maxTension());
            }
            Vec3 v = ref.velocity(), w = ref.angularVelocity();
            if (pushed && w != null) maxYawRate = Math.max(maxYawRate, Math.abs(w.y));
            if (calmWater && pushed && v != null) {
                // a hull in calm water: vertical speed and spin die away
                Vec3 gravity = Ships.dimensionGravity(level);
                double mass = ref.mass();
                if (gravity != null) {
                    ShipForces.setContinuous(level, ship.getId(), FLOAT,
                            gravity.scale(-mass).add(0.0, -2.0 * mass * v.y, 0.0), null, ShipForces.UNTIL_CLEARED);
                }
                ShipForces.dampSpin(level, ship.getId(), 0.1);
            }
            stillTaut = pushed && taut && v != null && v.length() < 0.03 ? stillTaut + 1 : 0;
            if (trace && pushed && (level.getGameTime() - pushedAt) % 5 == 0) {
                LOGGER.info("[UL ship test] ship {} t+{}: distance {} (length {}), v {} w {}, tension {} N", ship.getId(),
                        level.getGameTime() - pushedAt, fmt(distance(0)), fmt(length(0)), v, w, fmt(ropes.get(0).cell().tension()));
            }
        }

        /** Logs the ship's motion every 5 ticks after the push. */
        boolean trace;
        /** After the push, damp the ship's vertical speed and spin every tick (a hull in calm water). */
        boolean calmWater;

        /** A steady engine: a continuous force at the centre of mass (newtons). */
        void thrust(Vec3 force) {
            ShipForces.setContinuous(level, ship.getId(), THRUST, force, null, ShipForces.UNTIL_CLEARED);
        }

        /** Game-test condition: the rope is taut and the ship has been still for {@code ticks} ticks. */
        void demandSettled(int ticks) {
            helper.assertTrue(stillTaut >= ticks, "the ship hasn't settled on its rope yet (" + summary() + ")");
        }

        /** Lets go of the ship: VS simulates it again (it starts to fall). */
        void release() {
            start = ref.position();
            ship.setStatic(false);
            LOGGER.info("[UL ship test] ship {} ({} kg) released at {}", ship.getId(), Math.round(ref.mass()), ref.position());
        }

        /** Game-test condition: VS's physics has the ship (it has begun to fall). */
        void demandSimulated() {
            Vec3 v = ref.velocity();
            helper.assertTrue(v != null && v.y < -0.02, "VS isn't simulating ship " + ship.getId() + " yet (v " + v + ")");
        }

        /** Holds the ship up (a force equal to its weight) and sets its velocity to {@code velocity} (m/s). */
        void push(Vec3 velocity) {
            double mass = ref.mass();
            Vec3 gravity = Ships.dimensionGravity(level);
            if (gravity != null) {
                ShipForces.setContinuous(level, ship.getId(), FLOAT, gravity.scale(-mass), null, ShipForces.UNTIL_CLEARED);
            }
            Vec3 now = ref.velocity();
            ShipForces.applyImpulse(level, ship.getId(), velocity.subtract(now == null ? Vec3.ZERO : now).scale(mass), null);
            if (velocity.lengthSqr() > 0.0) pushDir = velocity.normalize();
            pushed = true;
            pushedAt = level.getGameTime();
            LOGGER.info("[UL ship test] ship {} ({} kg) pushed to {} m/s", ship.getId(), Math.round(mass), velocity);
        }

        /** Game-test condition: the rope went taut and the ship no longer moves away from the fence. */
        void demandStoppedByRope() {
            helper.assertTrue(tautAt >= 0, "the rope hasn't gone taut yet (" + summary() + ")");
            helper.assertTrue(velocityAlong() <= 0.0, "the ship is still moving away (" + summary() + ")");
            helper.assertTrue(level.getGameTime() - tautAt >= 10, "waiting after the rope went taut");
        }

        /** Pins the ship where it is, stops its float and sends the watcher away. */
        void pin() {
            ship.setStatic(true);
            ShipForces.clearContinuous(level, ship.getId(), FLOAT);
            ShipForces.clearContinuous(level, ship.getId(), THRUST);
            playerList(level).remove(watcher);
        }

        String summary() {
            StringBuilder s = new StringBuilder();
            s.append("ship ").append(ship.getId()).append(' ').append(Math.round(ref.mass())).append(" kg");
            for (int i = 0; i < ropes.size(); i++) {
                Tether t = ropes.get(i);
                s.append(", rope ").append(i).append(": length ").append(fmt(t.length())).append(" distance ").append(fmt(distance(i)))
                        .append(" max ").append(fmt(Math.max(maxDist, t.cell().maxDistance())))
                        .append(" peak ").append(fmt(t.cell().maxTension())).append(" N")
                        .append(Tethers.all(level).contains(t) ? "" : " SNAPPED");
            }
            s.append(", v ").append(fmt(velocityAlong())).append(" m/s");
            if (tautAt >= 0 && pushedAt >= 0) s.append(", taut after ").append(tautAt - pushedAt).append(" ticks");
            return s.toString();
        }
    }
}
