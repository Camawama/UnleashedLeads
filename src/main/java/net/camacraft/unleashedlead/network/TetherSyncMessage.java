package net.camacraft.unleashedlead.network;

import net.camacraft.camafy.network.Packets;
import net.camacraft.unleashedlead.client.ClientTethers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server to client: every fence-to-fence rope that touches the knot {@code knotId}, replacing
 * what the client knew about that knot. Each rope is its id, the entity ids of its two ends (a
 * knot, and a knot or the player carrying it; 0 while that end isn't loaded), its length (NaN
 * while carried) and the tier of the lead it is ({@code leash.LeadTier}, protocol 4).
 */
public record TetherSyncMessage(int knotId, int[] ids, int[] ends, int[] others, float[] lengths, byte[] tiers) {
    /** Most ropes per message; a knot never has this many. */
    private static final int MAX = 256;

    public static void encode(TetherSyncMessage msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.knotId);
        int n = Math.min(MAX, msg.ids.length);
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            buf.writeVarInt(msg.ids[i]);
            buf.writeVarInt(msg.ends[i]);
            buf.writeVarInt(msg.others[i]);
            buf.writeFloat(msg.lengths[i]);
            buf.writeByte(msg.tiers[i]);
        }
    }

    public static TetherSyncMessage decode(FriendlyByteBuf buf) {
        int knot = buf.readVarInt();
        int n = Math.min(MAX, Math.max(0, buf.readVarInt()));
        int[] ids = new int[n], ends = new int[n], others = new int[n];
        float[] lengths = new float[n];
        byte[] tiers = new byte[n];
        for (int i = 0; i < n; i++) {
            ids[i] = buf.readVarInt();
            ends[i] = buf.readVarInt();
            others[i] = buf.readVarInt();
            lengths[i] = buf.readFloat();
            tiers[i] = buf.readByte();
        }
        return new TetherSyncMessage(knot, ids, ends, others, lengths, tiers);
    }

    public static void handle(TetherSyncMessage msg, Supplier<NetworkEvent.Context> ctx) {
        Packets.handleClient(ctx, () -> () -> ClientTethers.apply(msg.knotId, msg.ids, msg.ends, msg.others, msg.lengths, msg.tiers));
    }
}
