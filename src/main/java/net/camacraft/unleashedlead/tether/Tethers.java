package net.camacraft.unleashedlead.tether;

import net.camacraft.camafy.compat.vs.ShipRef;
import net.camacraft.camafy.compat.vs.Ships;
import net.camacraft.camafy.minigame.MatchPolicies;
import net.camacraft.unleashedlead.UlConfig;
import net.camacraft.unleashedlead.UlSounds;
import net.camacraft.unleashedlead.leash.LeashGeometry;
import net.camacraft.unleashedlead.network.TetherSyncMessage;
import net.camacraft.unleashedlead.network.UlNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.camacraft.unleashedlead.UlBlocks;
import net.camacraft.unleashedlead.block.LeadCleatBlock;
import net.minecraft.world.level.Level;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Leads between two fences (Unleashed Lead's own addition; vanilla only ties mobs to fences).
 *
 * <p><b>Tying one.</b> Use a lead on a fence: a knot appears and you carry the rope's free end
 * ({@link #start}). Use another fence, or its knot, and the rope is tied there ({@link #tieCarried}),
 * as long as the rope as the length it had when it was tied, plus {@code tethers.slack}. Using
 * the first fence again takes the lead back off. Carried ends snap like any lead past the snap
 * distance, and break with a firework boost.
 *
 * <p><b>What they do.</b> Between two fences in the world a rope just hangs. When either end is
 * on a Valkyrien Skies ship it holds the ship back ({@link TetherPhysics}) and snaps, dropping
 * the lead where it broke, when its tension passes {@code tethers.breakingStrength}.
 *
 * <p><b>The knot rules still apply.</b> Using a knot with nothing to tie takes its ropes into your
 * hand (the other ends stay tied), shears cut them, a knot that is broken or loses its fence drops
 * them, and a knot with a rope on it is never cleared away as empty. Players need the minigame
 * host's leave to change a block ({@code MatchPolicies.mayAlter}) to tie, take or cut ropes there.
 *
 * <p><b>Loading.</b> Ropes are saved with the level ({@link TetherData}). An end whose fence isn't
 * loaded just waits (the rope goes slack) and nothing is ever loaded to find it; once the fence
 * is back its knot is found, or made again as a mob tied there would.
 */
public final class Tethers {
    /** Ticks a carried end waits for its player after a load before the lead drops. */
    private static final int CARRIER_PATIENCE = 100;
    /** Most ropes on one fence. */
    public static final int MAX_PER_FENCE = 32;

    private Tethers() {}

    // ------------------------------------------------------------------ config

    public static boolean enabled() {
        return UlConfig.on(UlConfig.TETHERS);
    }

    static double slack() {
        return UlConfig.num(UlConfig.TETHER_SLACK);
    }

    /** How far a carried end reaches, and the longest rope that can be tied (the lead snap distance). */
    public static double reach() {
        return UlConfig.num(UlConfig.SNAP_DISTANCE);
    }

    /** How far past its length a rope may be stretched while nothing pulls on it. */
    static double looseStretch() {
        return Math.max(1.0, UlConfig.num(UlConfig.SNAP_DISTANCE) - UlConfig.num(UlConfig.FREE_LENGTH));
    }

    // ------------------------------------------------------------------ geometry

    /** Where a rope pulls on a knot: the knot entity's own position, in the fence's space (on a lead cleat, its horn). */
    public static Vec3 anchor(Level level, BlockPos fence) {
        Vec3 cleat = level.isLoaded(fence) ? LeadCleatBlock.knotPosition(fence, level.getBlockState(fence)) : null;
        if (cleat != null) return cleat;
        return new Vec3(fence.getX() + 0.5, fence.getY() + LeashFenceKnotEntity.OFFSET_Y, fence.getZ() + 0.5);
    }

    /** {@link #anchor} in world space (through the fence's ship, if it is on one). */
    public static Vec3 worldAnchor(ServerLevel level, BlockPos fence) {
        return Ships.toWorld(level, anchor(level, fence));
    }

    // ------------------------------------------------------------------ queries

    public static List<Tether> all(ServerLevel level) {
        return TetherData.get(level).tethers;
    }

    /** Whether any rope is tied to, or carried from, the fence at {@code pos}. */
    public static boolean touches(ServerLevel level, BlockPos pos) {
        for (Tether t : all(level)) {
            if (t.touches(pos)) return true;
        }
        return false;
    }

    /** The ropes {@code player} carries. */
    public static List<Tether> carriedBy(ServerLevel level, Player player) {
        List<Tether> out = new ArrayList<>();
        for (Tether t : all(level)) {
            if (player.getUUID().equals(t.carrier)) out.add(t);
        }
        return out;
    }

    // ------------------------------------------------------------------ player actions

    /**
     * Ties a new rope to the fence at {@code pos} with the lead in {@code hand}: the player now
     * carries its free end. False (and nothing used) when it can't be done.
     */
    public static boolean start(ServerLevel level, BlockPos pos, Player player, InteractionHand hand) {
        if (!enabled() || player.isSpectator()) return false;
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.is(Items.LEAD) || !UlBlocks.isAnchor(level.getBlockState(pos))) return false;
        if (!MatchPolicies.mayAlter(level, pos, player)) return false;
        TetherData data = TetherData.get(level);
        int onFence = 0;
        for (Tether t : data.tethers) {
            if (t.touches(pos)) onFence++;
        }
        if (onFence >= MAX_PER_FENCE) return false;

        LeashFenceKnotEntity knot = Knots.getOrCreate(level, pos);
        knot.playPlacementSound();
        Tether t = new Tether(pos);
        t.carrier = player.getUUID();
        t.carrierEntity = player;
        t.knotA = knot;
        data.tethers.add(t);
        data.setDirty();
        if (!player.getAbilities().instabuild) stack.shrink(1);
        UlSounds.play(level, LeashGeometry.centre(knot), UlSounds.LEAD_TIED, player);
        level.gameEvent(GameEvent.BLOCK_ATTACH, pos, GameEvent.Context.of(player));
        sync(level, t);
        return true;
    }

    /**
     * Ties every rope {@code player} carries to {@code knot}, those that reach. A rope brought
     * back to its own fence is taken off instead (the lead drops, unless in creative; the untie
     * sound plays here). Returns {tied, taken off}.
     */
    public static int[] tieCarried(ServerLevel level, Player player, LeashFenceKnotEntity knot) {
        TetherData data = TetherData.get(level);
        BlockPos pos = knot.getPos();
        Vec3 here = worldAnchor(level, pos);
        int tied = 0, untied = 0;
        Iterator<Tether> it = data.tethers.iterator();
        while (it.hasNext()) {
            Tether t = it.next();
            if (!player.getUUID().equals(t.carrier)) continue;
            if (t.a.equals(pos)) {
                it.remove();
                if (!player.getAbilities().instabuild) dropLead(level, here);
                untied++;
                resync(level, t);
                continue;
            }
            double d = worldAnchor(level, t.a).distanceTo(here);
            if (d > reach() || !MatchPolicies.mayAlter(level, pos, player)) continue;
            t.carrier = null;
            t.carrierEntity = null;
            t.b = pos.immutable();
            t.knotB = knot;
            t.length = d + slack();
            t.cell.restart();
            tied++;
            sync(level, t);
        }
        if (tied + untied > 0) data.setDirty();
        if (untied > 0) UlSounds.play(level, here, UlSounds.LEAD_UNTIED, player);
        return new int[]{tied, untied};
    }

    /**
     * Takes the ropes tied to {@code knot} into {@code player}'s hand: the end at this knot comes
     * off, the other end stays tied. Returns {taken, left}: ropes that couldn't be taken (out of
     * reach, not allowed, carried by someone) stay.
     */
    public static int[] collect(ServerLevel level, Player player, LeashFenceKnotEntity knot) {
        TetherData data = TetherData.get(level);
        BlockPos pos = knot.getPos();
        Vec3 at = LeashGeometry.centre(player);
        boolean allowed = MatchPolicies.mayAlter(level, pos, player);
        int taken = 0, left = 0;
        for (Tether t : data.tethers) {
            if (!t.touches(pos)) continue;
            if (t.carried() || !allowed) {
                left++;
                continue;
            }
            BlockPos stays = t.a.equals(pos) ? t.b : t.a;
            LeashFenceKnotEntity staysKnot = t.a.equals(pos) ? t.knotB : t.knotA;
            if (stays == null || worldAnchor(level, stays).distanceTo(at) > reach()) {
                left++;
                continue;
            }
            resync(level, t);
            t.a = stays;
            t.knotA = staysKnot;
            t.b = null;
            t.knotB = null;
            t.carrier = player.getUUID();
            t.carrierEntity = player;
            t.length = Double.NaN;
            t.cell.restart();
            taken++;
            sync(level, t);
        }
        if (taken > 0) data.setDirty();
        return new int[]{taken, left};
    }

    /**
     * Cuts every rope tied to (or carried from) the fence at {@code pos}, dropping the leads at
     * the knot. {@code cause} is the player cutting (null for a dispenser). Returns how many.
     */
    public static int cut(ServerLevel level, BlockPos pos, @Nullable Player cause) {
        if (cause != null && !MatchPolicies.mayAlter(level, pos, cause)) return 0;
        TetherData data = TetherData.get(level);
        Vec3 at = worldAnchor(level, pos);
        int cut = 0;
        Iterator<Tether> it = data.tethers.iterator();
        while (it.hasNext()) {
            Tether t = it.next();
            if (!t.touches(pos)) continue;
            it.remove();
            dropLead(level, at);
            resync(level, t);
            cut++;
        }
        if (cut > 0) data.setDirty();
        return cut;
    }

    /** A firework boost breaks the ropes {@code player} carries (the leads drop at their knots). */
    public static int breakCarried(ServerLevel level, Player player) {
        TetherData data = TetherData.get(level);
        int broken = 0;
        Iterator<Tether> it = data.tethers.iterator();
        while (it.hasNext()) {
            Tether t = it.next();
            if (!player.getUUID().equals(t.carrier)) continue;
            it.remove();
            dropLead(level, t.knotA != null ? worldAnchor(level, t.a) : player.position());
            resync(level, t);
            broken++;
        }
        if (broken > 0) data.setDirty();
        return broken;
    }

    // ------------------------------------------------------------------ every tick

    private enum Fate { KEEP, GONE }

    /** Server, end of each level tick: upkeep of every rope, and this tick's pulls for the physics thread. */
    public static void tick(ServerLevel level) {
        TetherData data = TetherData.get(level);
        List<TetherPhysics.End> ends = new ArrayList<>();
        if (!data.tethers.isEmpty()) {
            boolean physics = enabled() && TetherPhysics.available();
            Iterator<Tether> it = data.tethers.iterator();
            while (it.hasNext()) {
                Tether t = it.next();
                if (upkeep(level, t, physics, ends) == Fate.GONE) {
                    it.remove();
                    data.setDirty();
                    resync(level, t);
                } else {
                    syncIfChanged(level, t);
                }
            }
        }
        TetherPhysics.stage(level.dimension(), ends);
        flush(level);
    }

    private static Fate upkeep(ServerLevel level, Tether t, boolean physics, List<TetherPhysics.End> ends) {
        // end A: always a fence
        if (t.knotA != null && t.knotA.isRemoved()) {
            if (destroyed(t.knotA)) return breakAt(level, worldAnchor(level, t.a), false);
            t.knotA = null;
        }
        if (t.knotA == null) {
            Presence p = present(level, t.a);
            if (p == Presence.GONE) return breakAt(level, worldAnchor(level, t.a), false);
            if (p == Presence.HERE) t.knotA = Knots.getOrCreate(level, t.a);
        }

        // a carried end
        if (t.carrier != null) return upkeepCarried(level, t);

        // end B: a fence too
        if (t.b == null) return Fate.GONE;
        if (t.knotB != null && t.knotB.isRemoved()) {
            if (destroyed(t.knotB)) return breakAt(level, worldAnchor(level, t.b), false);
            t.knotB = null;
        }
        if (t.knotB == null) {
            Presence p = present(level, t.b);
            if (p == Presence.GONE) return breakAt(level, worldAnchor(level, t.b), false);
            if (p == Presence.HERE) t.knotB = Knots.getOrCreate(level, t.b);
        }
        if (t.knotA == null || t.knotB == null) {
            t.cell.reset(); // an end isn't loaded: slack, and nothing pulls
            return Fate.KEEP;
        }

        ShipRef shipA = shipOf(level, t.a), shipB = shipOf(level, t.b);
        boolean onShipyardA = Ships.inShipyard(level, t.a), onShipyardB = Ships.inShipyard(level, t.b);
        if ((onShipyardA && shipA == null) || (onShipyardB && shipB == null)) {
            t.cell.reset(); // its ship isn't loaded
            return Fate.KEEP;
        }
        if (shipA == null && shipB == null) return Fate.KEEP; // two world fences: it just hangs
        if (shipA != null && shipB != null && shipA.id() == shipB.id()) return Fate.KEEP; // both on one ship

        Vec3 wa = worldAnchor(level, t.a), wb = worldAnchor(level, t.b);
        double breaking = UlConfig.num(UlConfig.TETHER_BREAKING_STRENGTH);
        double peak = t.cell.takePeak();
        if (t.cell.broken() || peak > breaking) return breakAt(level, wa.add(wb).scale(0.5), true);
        if (!physics) {
            if (wa.distanceTo(wb) > t.length + looseStretch()) return breakAt(level, wa.add(wb).scale(0.5), true);
            return Fate.KEEP;
        }
        double stiffness = UlConfig.num(UlConfig.TETHER_STIFFNESS);
        double damping = UlConfig.num(UlConfig.TETHER_DAMPING);
        Vec3 sa = anchor(level, t.a), sb = anchor(level, t.b);
        if (shipA != null) {
            Vec3 other = shipB != null ? sb : wb;
            ends.add(new TetherPhysics.End(shipA.id(), sa.x, sa.y, sa.z, shipB != null ? shipB.id() : ShipRef.NO_ID,
                    other.x, other.y, other.z, t.length, stiffness, damping, breaking, t.cell));
        }
        if (shipB != null) {
            Vec3 other = shipA != null ? sa : wa;
            ends.add(new TetherPhysics.End(shipB.id(), sb.x, sb.y, sb.z, shipA != null ? shipA.id() : ShipRef.NO_ID,
                    other.x, other.y, other.z, t.length, stiffness, damping, breaking, t.cell));
        }
        return Fate.KEEP;
    }

    private static Fate upkeepCarried(ServerLevel level, Tether t) {
        Player p = t.carrierEntity;
        if (p == null || p.isRemoved()) p = level.getPlayerByUUID(t.carrier);
        t.carrierEntity = p;
        if (t.knotA == null) return Fate.KEEP; // its fence isn't loaded: wait
        Vec3 at = worldAnchor(level, t.a);
        if (p == null) {
            if (++t.waitTicks <= CARRIER_PATIENCE) return Fate.KEEP;
            return breakAt(level, at, false);
        }
        t.waitTicks = 0;
        if (!p.isAlive() || p.isSpectator() || p.level() != level) return breakAt(level, at, false);
        if (LeashGeometry.centre(p).distanceTo(at) > reach()) {
            UlSounds.play(level, p.position(), UlSounds.LEAD_BREAK, p); // snapped: heard at the holder, like a lead
            return breakAt(level, at, false);
        }
        return Fate.KEEP;
    }

    /** The rope is gone: its lead drops at {@code where}; {@code snapped} also plays the snap there. */
    private static Fate breakAt(ServerLevel level, Vec3 where, boolean snapped) {
        dropLead(level, where);
        if (snapped) UlSounds.play(level, where, UlSounds.LEAD_BREAK, null);
        return Fate.GONE;
    }

    private enum Presence { HERE, AWAY, GONE }

    /**
     * Whether a rope end's fence is here (loaded, with its entities: a knot can be found or made
     * without making a second one), away (not loaded: wait), or gone (loaded, and no fence).
     */
    private static Presence present(ServerLevel level, BlockPos fence) {
        if (!level.isLoaded(fence) || !level.areEntitiesLoaded(ChunkPos.asLong(fence))) return Presence.AWAY;
        return UlBlocks.isAnchor(level.getBlockState(fence)) ? Presence.HERE : Presence.GONE;
    }

    /** A knot that was broken, taken down or cut (not one that was only unloaded). */
    private static boolean destroyed(Entity knot) {
        Entity.RemovalReason reason = knot.getRemovalReason();
        return reason != null && reason.shouldDestroy();
    }

    @Nullable
    private static ShipRef shipOf(ServerLevel level, BlockPos pos) {
        return Ships.inShipyard(level, pos) ? Ships.managing(level, pos) : null;
    }

    static void dropLead(ServerLevel level, Vec3 at) {
        ItemEntity lead = new ItemEntity(level, at.x, at.y, at.z, new ItemStack(Items.LEAD));
        lead.setDefaultPickUpDelay();
        level.addFreshEntity(lead);
    }

    // ------------------------------------------------------------------ client sync

    /** Knots whose clients need their rope list again this tick. */
    private static final Set<LeashFenceKnotEntity> DIRTY = new LinkedHashSet<>();

    /** A rope changed: both its knots (as they are now) need re-sending. */
    private static void sync(ServerLevel level, Tether t) {
        if (t.knotA != null) DIRTY.add(t.knotA);
        if (t.knotB != null) DIRTY.add(t.knotB);
    }

    /** A rope is going away or moving its ends: the knots it had need re-sending too. */
    private static void resync(ServerLevel level, Tether t) {
        sync(level, t);
        t.sentA = t.sentB = -1;
    }

    private static void syncIfChanged(ServerLevel level, Tether t) {
        int a = t.knotA == null ? 0 : t.knotA.getId();
        Entity other = t.carrier != null ? t.carrierEntity : t.knotB;
        int b = other == null ? 0 : other.getId();
        float length = (float) t.length;
        if (a != t.sentA || b != t.sentB || Float.compare(length, t.sentLength) != 0) {
            sync(level, t);
            t.sentA = a;
            t.sentB = b;
            t.sentLength = length;
        }
    }

    private static void flush(ServerLevel level) {
        if (DIRTY.isEmpty()) return;
        Iterator<LeashFenceKnotEntity> it = DIRTY.iterator();
        while (it.hasNext()) {
            LeashFenceKnotEntity knot = it.next();
            if (knot.isRemoved()) {
                it.remove(); // its clients drop it, and its ropes with it
            } else if (knot.level() == level) {
                it.remove();
                UlNetwork.CHANNEL.toTracking(knot, message(level, knot));
            }
        }
    }

    /** A player started seeing a knot: tell them its ropes. */
    public static void sendTo(ServerPlayer player, LeashFenceKnotEntity knot) {
        if (!(knot.level() instanceof ServerLevel level)) return;
        TetherSyncMessage msg = message(level, knot);
        if (msg.ids().length > 0) UlNetwork.CHANNEL.toPlayer(player, msg);
    }

    private static TetherSyncMessage message(ServerLevel level, LeashFenceKnotEntity knot) {
        List<Tether> touching = new ArrayList<>();
        for (Tether t : all(level)) {
            if (t.knotA == knot || t.knotB == knot) touching.add(t);
        }
        int n = touching.size();
        int[] ids = new int[n], ends = new int[n], others = new int[n];
        float[] lengths = new float[n];
        for (int i = 0; i < n; i++) {
            Tether t = touching.get(i);
            ids[i] = t.id;
            ends[i] = t.knotA == null ? 0 : t.knotA.getId();
            Entity other = t.carrier != null ? t.carrierEntity : t.knotB;
            others[i] = other == null ? 0 : other.getId();
            lengths[i] = (float) t.length;
        }
        return new TetherSyncMessage(knot.getId(), ids, ends, others, lengths);
    }

    public static void clear() {
        DIRTY.clear();
    }
}
