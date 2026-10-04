package net.camacraft.unleashedlead.network;

import net.camacraft.camafy.network.Packets;
import net.camacraft.unleashedlead.leash.KnotMasses;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server to client: the knot {@code knotId} is on a Valkyrien Skies ship of {@code massKg}, sent to the
 * player driving a vehicle tied to it ({@code leash.KnotMasses}).
 */
public record KnotMassMessage(int knotId, float massKg) {
    public static void encode(KnotMassMessage msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.knotId);
        buf.writeFloat(msg.massKg);
    }

    public static KnotMassMessage decode(FriendlyByteBuf buf) {
        return new KnotMassMessage(buf.readVarInt(), buf.readFloat());
    }

    public static void handle(KnotMassMessage msg, Supplier<NetworkEvent.Context> ctx) {
        Packets.handleClient(ctx, () -> () -> KnotMasses.put(msg.knotId, msg.massKg));
    }
}
