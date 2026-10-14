package net.camacraft.unleashedlead.leash;

import it.unimi.dsi.fastutil.ints.Int2ByteOpenHashMap;
import net.camacraft.unleashedlead.UlConfig;
import net.camacraft.unleashedlead.compat.VanillaBackport;
import net.camacraft.unleashedlead.mixin.EntityDataAccessor;
import net.camacraft.unleashedlead.network.LeadTierMessage;
import net.camacraft.unleashedlead.network.UlNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.function.DoubleSupplier;

/**
 * Which {@link LeadTier} the lead on a leashed entity is. One lead per leashed entity, so the tier is
 * kept on that entity: on the server in its persistent data ({@value #KEY}, saved with it by Forge, so it
 * survives saves, chunk reloads and a knot on a ship unloading), on clients from {@link LeadTierMessage}s.
 * <ul>
 *   <li>Written when a lead is tied from an item ({@code LeashRules}: the item's tier), and moved along a
 *   chain of mobs when a lead takes the chain ({@code LeashRules.takeChain}). A lead that only changes
 *   holder (tied to a fence, to another mob, taken back into a hand) keeps its tier.</li>
 *   <li>Read back as the lead drops: any {@code spawnAtLocation(Items.LEAD)} by an entity with a tiered lead
 *   drops that tier's item instead ({@code mixin.EntityLeadTierMixin}), which covers every way a lead drops
 *   (vanilla's, Vanilla Backport's boats, this mod's boats and cars, the snap), and the tier is then gone.</li>
 *   <li>Cleared when the lead is removed without an item (creative, a dimension change).</li>
 * </ul>
 * Fence-to-fence ropes keep their tier on the {@code tether.Tether} instead.
 */
public final class LeadTiers {
    /** Persistent-data key of a leashed entity's lead tier (absent: a plain lead). */
    public static final String KEY = "unleashedlead:LeadTier";

    /** Client: lead tiers by entity id (only tiered leads; anything missing is plain). Client thread only. */
    private static final Int2ByteOpenHashMap CLIENT = new Int2ByteOpenHashMap();

    private LeadTiers() {}

    /** The tier of the lead on {@code entity} (plain when it has none, or nothing is known). */
    public static LeadTier of(Entity entity) {
        if (entity.level().isClientSide) return LeadTier.byId(CLIENT.get(entity.getId()));
        CompoundTag data = ((EntityDataAccessor) entity).unleashedlead$persistentData(); // never makes the tag
        return data == null ? LeadTier.PLAIN : LeadTier.byId(data.getByte(KEY));
    }

    /** Server: the lead on {@code entity} is now of {@code tier}; its watchers are told. */
    public static void set(Entity entity, LeadTier tier) {
        if (entity.level().isClientSide || of(entity) == tier) return;
        if (tier == LeadTier.PLAIN) {
            CompoundTag data = ((EntityDataAccessor) entity).unleashedlead$persistentData();
            if (data != null) data.remove(KEY);
        } else {
            entity.getPersistentData().putByte(KEY, (byte) tier.id);
        }
        UlNetwork.CHANNEL.toTracking(entity, new LeadTierMessage(entity.getId(), (byte) tier.id));
    }

    /** Server: {@code entity}'s lead is gone (or plain). */
    public static void clear(Entity entity) {
        set(entity, LeadTier.PLAIN);
    }

    /** Server: a player started seeing {@code entity}: tell them its lead's tier, if it has a tiered one. */
    public static void sendTo(ServerPlayer player, Entity entity) {
        LeadTier tier = of(entity);
        if (tier != LeadTier.PLAIN) UlNetwork.CHANNEL.toPlayer(player, new LeadTierMessage(entity.getId(), (byte) tier.id));
    }

    /** How much stronger than a plain lead the lead on {@code entity} is (1 for a plain one). */
    public static double strength(Entity entity) {
        return of(entity).strength();
    }

    /**
     * The distance at which a lead snaps, for a lead whose plain one snaps at {@code snap} and is free up to
     * {@code free}: the stretch past the free length a plain lead takes ({@code snap - free}, 7 blocks by
     * default) times the lead's strength. A plain lead: {@code snap}.
     */
    public static double breakDistance(double free, double snap, double strength) {
        return free + Math.max(0.0, snap - free) * strength;
    }

    /**
     * The longest any lead can get before it snaps, normal-sized: the strongest tier's break distance, for a plain
     * lead's config lengths and for Vanilla Backport's happy ghast (free 9, snap 16) when that is installed.
     */
    public static double longestReach() {
        double strongest = 1.0;
        for (LeadTier tier : LeadTier.values()) strongest = Math.max(strongest, tier.strength());
        double reach = breakDistance(UlConfig.num(UlConfig.FREE_LENGTH), UlConfig.num(UlConfig.SNAP_DISTANCE), strongest);
        if (VanillaBackport.present()) reach = Math.max(reach, breakDistance(9.0, 16.0, strongest));
        return reach;
    }

    // ------------------------------------------------------------------ client

    /** Client: a {@link LeadTierMessage} arrived. */
    public static void applyClient(int entityId, int tier) {
        if (tier <= 0) CLIENT.remove(entityId);
        else CLIENT.put(entityId, (byte) tier);
    }

    /** Client: the level is gone. */
    public static void clearClient() {
        CLIENT.clear();
    }

    // ------------------------------------------------------------------ Vanilla Backport

    /**
     * Vanilla Backport's snap distance for {@code entity}, whose plain lead snaps at {@code base}: what its
     * own leash tick snaps the lead at ({@code vb$leashSnapDistance}, answered by {@code VbLeashableMixin} and
     * {@code VbHappyGhastLeadTierMixin}), so a tiered lead it ticks breaks where this mod's would. While this
     * mod reads the plain value ({@link #plain}) it gets {@code base} unchanged.
     */
    public static double vanillaBackportSnap(Entity entity, double base) {
        if (READING_PLAIN.get()[0] > 0) return base;
        LeadTier tier = of(entity);
        if (tier == LeadTier.PLAIN) return base;
        return breakDistance(LeashPhysics.freeLength(entity), base, tier.strength());
    }

    /** Depth of {@link #plain} calls on this thread. */
    private static final ThreadLocal<int[]> READING_PLAIN = ThreadLocal.withInitial(() -> new int[1]);

    /** Runs {@code read} so that Vanilla Backport's snap distance answers a plain lead's ({@link #vanillaBackportSnap}). */
    public static double plain(DoubleSupplier read) {
        int[] depth = READING_PLAIN.get();
        depth[0]++;
        try {
            return read.getAsDouble();
        } finally {
            depth[0]--;
        }
    }

    // ------------------------------------------------------------------ temporary detaches

    /** Depth of {@link #keep} calls on this thread. */
    private static final ThreadLocal<int[]> KEEPING = ThreadLocal.withInitial(() -> new int[1]);

    /** Runs {@code action}, in which a lead is taken off and put straight back: its tier stays. */
    public static void keep(Runnable action) {
        int[] depth = KEEPING.get();
        depth[0]++;
        try {
            action.run();
        } finally {
            depth[0]--;
        }
    }

    /** Whether a lead taken off now is only being put straight back ({@link #keep}). */
    public static boolean keeping() {
        return KEEPING.get()[0] > 0;
    }

    /** The tier of the lead on {@code entity}, or null when it has no lead (for the tests and the harness). */
    @Nullable
    public static LeadTier ofLeashed(Entity entity) {
        return Leashes.holder(entity) == null ? null : of(entity);
    }
}
