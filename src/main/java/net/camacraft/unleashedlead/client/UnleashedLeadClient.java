package net.camacraft.unleashedlead.client;

import net.camacraft.unleashedlead.client.rope.ClientRopes;
import net.camacraft.unleashedlead.leash.KnotMasses;
import net.camacraft.unleashedlead.leash.LeadTiers;
import net.camacraft.unleashedlead.leash.LeashPhysics;
import net.camacraft.unleashedlead.leash.VehicleLeashes;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.IEventBus;

/** Client wiring: the rope simulation ticks with the client and draws after entities. */
public final class UnleashedLeadClient {
    private UnleashedLeadClient() {}

    public static void init(IEventBus modBus) {
        MinecraftForge.EVENT_BUS.addListener(UnleashedLeadClient::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(UnleashedLeadClient::onRenderStage);
        MinecraftForge.EVENT_BUS.addListener(UnleashedLeadClient::onLevelUnload);
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ClientRopes.tick();
        KnotMasses.clientTick();
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        // Paused, nothing moves while this event still fires: pulls given then piled up and flung the car (2026-10-12).
        // Both also act at most once per tick of what they move (LeashPhysics.OncePerTick), whatever stops the level.
        if (mc.isPaused()) return;
        VehicleLeashes.clientTick(mc.player);
        // the local player's own ends of leads by mass: held by a car it leads, or its car feeling what it tows
        LeashPhysics.clientHolderTick(mc.player);
    }

    private static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        ClientRopes.render(event.getPoseStack(), event.getCamera().getPosition(), event.getPartialTick(), event.getFrustum());
    }

    private static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            ClientRopes.clear();
            ClientTethers.clear();
            KnotMasses.clear();
            LeadTiers.clearClient();
        }
    }
}
