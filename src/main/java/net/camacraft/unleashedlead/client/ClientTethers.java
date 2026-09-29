package net.camacraft.unleashedlead.client;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import java.util.Collection;

/** What the client knows of fence-to-fence ropes, from {@code TetherSyncMessage}s. Drawn by {@code ClientRopes}. */
public final class ClientTethers {
    /** One rope: entity ids of both ends and its length (NaN while a player carries it). */
    public record Entry(int id, int end, int other, float length) {}

    private static final Int2ObjectOpenHashMap<Entry> ROPES = new Int2ObjectOpenHashMap<>();

    private ClientTethers() {}

    /** Replaces every rope touching {@code knotId} with the ones given. */
    public static void apply(int knotId, int[] ids, int[] ends, int[] others, float[] lengths) {
        ROPES.values().removeIf(e -> e.end == knotId || e.other == knotId);
        for (int i = 0; i < ids.length; i++) {
            ROPES.put(ids[i], new Entry(ids[i], ends[i], others[i], lengths[i]));
        }
    }

    public static Collection<Entry> all() {
        return ROPES.values();
    }

    public static void clear() {
        ROPES.clear();
    }
}
