package net.camacraft.unleashedlead.tether;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One lead tied to a fence at {@link #a}, whose other end is either tied to the fence at
 * {@link #b} or carried by a player ({@link #carrier}) on the way to one.
 *
 * <p>Ends are fence positions, not knots: vanilla never saves knots (they are re-made by
 * whatever is tied to them), so the rope is saved with the level ({@link TetherData}) and its
 * knots are found or re-made while their fences are loaded. A position in a Valkyrien Skies
 * shipyard is a fence on that ship.
 */
public final class Tether {
    private static final AtomicInteger IDS = new AtomicInteger();

    /** Session id, for syncing to clients (not saved). */
    public final int id = IDS.incrementAndGet();
    /** The fence the rope is tied to. */
    BlockPos a;
    /** The fence the other end is tied to, or null while a player carries it. */
    @Nullable
    BlockPos b;
    /** Who carries the free end, or null once it is tied. */
    @Nullable
    UUID carrier;
    /** Rope length: the distance between the knots when tied, plus the slack. NaN while carried. */
    double length = Double.NaN;

    // ---- runtime, server thread

    @Nullable
    LeashFenceKnotEntity knotA;
    @Nullable
    LeashFenceKnotEntity knotB;
    @Nullable
    Player carrierEntity;
    /** Ticks a carried end has waited for its player to show up (after a load). */
    int waitTicks;
    /** Shared with the physics thread. */
    final TetherPhysics.Cell cell = new TetherPhysics.Cell();
    /** What clients were last told: entity ids of both ends and the length. */
    int sentA = -1, sentB = -1;
    float sentLength = Float.NaN;

    Tether(BlockPos a) {
        this.a = a.immutable();
    }

    public BlockPos a() {
        return a;
    }

    @Nullable
    public BlockPos b() {
        return b;
    }

    @Nullable
    public UUID carrier() {
        return carrier;
    }

    public boolean carried() {
        return carrier != null;
    }

    public double length() {
        return length;
    }

    public TetherPhysics.Cell cell() {
        return cell;
    }

    @Nullable
    public LeashFenceKnotEntity knotA() {
        return knotA;
    }

    @Nullable
    public LeashFenceKnotEntity knotB() {
        return knotB;
    }

    /** Whether one end is tied (or carried from) the fence at {@code pos}. */
    public boolean touches(BlockPos pos) {
        return a.equals(pos) || pos.equals(b);
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("A", NbtUtils.writeBlockPos(a));
        if (b != null) tag.put("B", NbtUtils.writeBlockPos(b));
        if (carrier != null) tag.putUUID("Carrier", carrier);
        if (!Double.isNaN(length)) tag.putDouble("Length", length);
        return tag;
    }

    @Nullable
    static Tether load(CompoundTag tag) {
        if (!tag.contains("A", 10)) return null;
        Tether t = new Tether(NbtUtils.readBlockPos(tag.getCompound("A")));
        if (tag.contains("B", 10)) t.b = NbtUtils.readBlockPos(tag.getCompound("B"));
        if (tag.hasUUID("Carrier")) t.carrier = tag.getUUID("Carrier");
        if (tag.contains("Length", 99)) t.length = tag.getDouble("Length");
        if (t.b == null && t.carrier == null) return null; // neither tied nor carried: nothing to restore
        if (t.b != null) t.carrier = null;
        if (t.b != null && !(t.length > 0.0)) t.length = 1.0;
        return t;
    }

    @Override
    public String toString() {
        return "Tether#" + id + "[" + a.toShortString() + " -> "
                + (b != null ? b.toShortString() : "carried by " + carrier) + ", length " + length + "]";
    }
}
