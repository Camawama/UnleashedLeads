package net.camacraft.unleashedlead.network;

import net.camacraft.camafy.network.CamafyChannel;
import net.camacraft.unleashedlead.UnleashedLead;

/**
 * The mod's one channel: leashed boats under this mod's own boat model (mobs use vanilla's leash
 * sync, and with Vanilla Backport installed boats use its sync), fence-to-fence ropes, the mass of a ship
 * a driven vehicle's lead is tied to (protocol 3), and the tier of upgraded leads and ropes (protocol 4).
 * All messages are registered unconditionally so client and server always agree on the protocol.
 */
public final class UlNetwork {
    public static final CamafyChannel CHANNEL = CamafyChannel.create(UnleashedLead.id("main"), "4");

    private UlNetwork() {}

    public static void init() {
        CHANNEL.clientbound(LeashLinkMessage.class, LeashLinkMessage::encode, LeashLinkMessage::decode,
                LeashLinkMessage::handle);
        CHANNEL.clientbound(TetherSyncMessage.class, TetherSyncMessage::encode, TetherSyncMessage::decode,
                TetherSyncMessage::handle);
        CHANNEL.clientbound(KnotMassMessage.class, KnotMassMessage::encode, KnotMassMessage::decode,
                KnotMassMessage::handle);
        CHANNEL.clientbound(LeadTierMessage.class, LeadTierMessage::encode, LeadTierMessage::decode,
                LeadTierMessage::handle);
    }
}
