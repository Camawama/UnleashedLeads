package net.camacraft.unleashedlead.network;

import net.camacraft.camafy.network.CamafyChannel;
import net.camacraft.unleashedlead.UnleashedLead;

/**
 * The mod's one channel: leashed boats under this mod's own boat model (mobs use vanilla's leash
 * sync, and with Vanilla Backport installed boats use its sync), and fence-to-fence ropes. Both
 * messages are registered unconditionally so client and server always agree on the protocol.
 */
public final class UlNetwork {
    public static final CamafyChannel CHANNEL = CamafyChannel.create(UnleashedLead.id("main"), "2");

    private UlNetwork() {}

    public static void init() {
        CHANNEL.clientbound(LeashLinkMessage.class, LeashLinkMessage::encode, LeashLinkMessage::decode,
                LeashLinkMessage::handle);
        CHANNEL.clientbound(TetherSyncMessage.class, TetherSyncMessage::encode, TetherSyncMessage::decode,
                TetherSyncMessage::handle);
    }
}
