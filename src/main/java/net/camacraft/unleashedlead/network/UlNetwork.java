package net.camacraft.unleashedlead.network;

import net.camacraft.camafy.network.CamafyChannel;
import net.camacraft.unleashedlead.UnleashedLead;

/**
 * The mod's one channel. Only needed for leashed boats under this mod's own boat model (mobs use
 * vanilla's leash sync, and with Vanilla Backport installed boats use its sync), but registered
 * unconditionally so client and server always agree on the protocol.
 */
public final class UlNetwork {
    public static final CamafyChannel CHANNEL = CamafyChannel.create(UnleashedLead.id("main"), "1");

    private UlNetwork() {}

    public static void init() {
        CHANNEL.clientbound(LeashLinkMessage.class, LeashLinkMessage::encode, LeashLinkMessage::decode,
                LeashLinkMessage::handle);
    }
}
