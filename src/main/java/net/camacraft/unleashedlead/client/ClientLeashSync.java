package net.camacraft.unleashedlead.client;

import net.camacraft.unleashedlead.leash.BoatLeash;
import net.camacraft.unleashedlead.leash.BoatLeashes;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/** Client side of {@code LeashLinkMessage}. */
public final class ClientLeashSync {
    private ClientLeashSync() {}

    public static void apply(int entityId, int holderId) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        Entity entity = level.getEntity(entityId);
        if (entity instanceof BoatLeash leash) BoatLeashes.applySync(entity, leash, holderId);
    }
}
