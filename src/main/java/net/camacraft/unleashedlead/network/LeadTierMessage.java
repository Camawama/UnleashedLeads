package net.camacraft.unleashedlead.network;

import net.camacraft.camafy.network.Packets;
import net.camacraft.unleashedlead.leash.LeadTiers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server to client: the lead on {@code entityId} is of tier {@code tier} (0: a plain lead, or none). */
public record LeadTierMessage(int entityId, byte tier) {
    public static void encode(LeadTierMessage msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeByte(msg.tier);
    }

    public static LeadTierMessage decode(FriendlyByteBuf buf) {
        return new LeadTierMessage(buf.readVarInt(), buf.readByte());
    }

    public static void handle(LeadTierMessage msg, Supplier<NetworkEvent.Context> ctx) {
        Packets.handleClient(ctx, () -> () -> LeadTiers.applyClient(msg.entityId, msg.tier));
    }
}
