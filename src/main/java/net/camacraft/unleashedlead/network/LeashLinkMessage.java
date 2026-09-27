package net.camacraft.unleashedlead.network;

import net.camacraft.camafy.network.Packets;
import net.camacraft.unleashedlead.client.ClientLeashSync;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server to client: {@code entityId}'s lead is now held by {@code holderId} (0 for nobody). */
public record LeashLinkMessage(int entityId, int holderId) {
    public static void encode(LeashLinkMessage msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeVarInt(msg.holderId);
    }

    public static LeashLinkMessage decode(FriendlyByteBuf buf) {
        return new LeashLinkMessage(buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(LeashLinkMessage msg, Supplier<NetworkEvent.Context> ctx) {
        Packets.handleClient(ctx, () -> () -> ClientLeashSync.apply(msg.entityId, msg.holderId));
    }
}
