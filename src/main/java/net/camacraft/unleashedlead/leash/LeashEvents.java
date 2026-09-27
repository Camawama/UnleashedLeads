package net.camacraft.unleashedlead.leash;

import net.camacraft.unleashedlead.UlConfig;
import net.camacraft.unleashedlead.compat.VanillaBackport;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.FireworkRocketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;

/** Forge event wiring for the leash rules (game bus). */
public final class LeashEvents {
    private LeashEvents() {}

    public static void register() {
        // HIGH: before the entity's own interaction and before Vanilla Backport's NORMAL handler.
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, LeashEvents::onEntityInteract);
        MinecraftForge.EVENT_BUS.addListener(LeashEvents::onRightClickItem);
        MinecraftForge.EVENT_BUS.addListener(LeashEvents::onStartTracking);
        MinecraftForge.EVENT_BUS.addListener(LeashEvents::onLeaveLevel);
    }

    private static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        InteractionResult result = LeashRules.interact(event.getEntity(), event.getTarget(), event.getHand());
        if (result != InteractionResult.PASS) {
            event.setCanceled(true);
            event.setCancellationResult(result);
        }
    }

    /** A firework boost while gliding breaks the leads you hold. Vanilla Backport does this itself when present. */
    private static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (VanillaBackport.present() || !UlConfig.on(UlConfig.FIREWORK_BREAKS_LEADS)) return;
        if (event.getItemStack().getItem() instanceof FireworkRocketItem && event.getEntity().isFallFlying()) {
            LeashRules.fireworkBoost(event.getEntity());
        }
    }

    /** Tell a player who starts seeing a leashed boat who holds it (our boat model only). */
    private static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof BoatLeash leash && event.getEntity() instanceof ServerPlayer player) {
            BoatLeashes.sendTo(player, event.getTarget(), leash);
        }
    }

    /**
     * A leashed boat that is destroyed drops its lead; one leaving the dimension just lets go.
     * The lead item is spawned on the next server tick rather than inside the removal callback.
     */
    private static void onLeaveLevel(EntityLeaveLevelEvent event) {
        Entity entity = event.getEntity();
        if (!(event.getLevel() instanceof ServerLevel level) || !(entity instanceof BoatLeash leash)) return;
        if (leash.ul$holder() == null) return;
        Entity.RemovalReason reason = entity.getRemovalReason();
        if (reason == null || !(reason == Entity.RemovalReason.KILLED || reason == Entity.RemovalReason.DISCARDED
                || reason == Entity.RemovalReason.CHANGED_DIMENSION)) {
            return; // unloaded: saved with the boat
        }
        leash.ul$setHolder(null);
        leash.ul$setPendingTag(null);
        if (reason == Entity.RemovalReason.CHANGED_DIMENSION) return;
        Vec3 at = entity.position();
        MinecraftServer server = level.getServer();
        server.tell(new TickTask(server.getTickCount(), () -> {
            ItemEntity lead = new ItemEntity(level, at.x, at.y + 0.2, at.z, new ItemStack(Items.LEAD));
            lead.setDefaultPickUpDelay();
            level.addFreshEntity(lead);
        }));
    }
}
