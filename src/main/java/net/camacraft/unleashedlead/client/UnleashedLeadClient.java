package net.camacraft.unleashedlead.client;

import net.camacraft.unleashedlead.client.rope.ClientRopes;
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
        if (event.phase == TickEvent.Phase.END) ClientRopes.tick();
    }

    private static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        ClientRopes.render(event.getPoseStack(), event.getCamera().getPosition(), event.getPartialTick(), event.getFrustum());
    }

    private static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            ClientRopes.clear();
            ClientTethers.clear();
        }
    }
}
