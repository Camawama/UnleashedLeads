package net.camacraft.unleashedlead.leash;

import net.camacraft.unleashedlead.UlConfig;
import net.camacraft.unleashedlead.UlSounds;
import net.camacraft.unleashedlead.compat.VanillaBackport;
import net.camacraft.unleashedlead.tether.Knots;
import net.camacraft.unleashedlead.tether.TetherPhysics;
import net.camacraft.unleashedlead.tether.Tethers;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.FireworkRocketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
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
        // fence-to-fence ropes
        MinecraftForge.EVENT_BUS.addListener(LeashEvents::onJoinLevel);
        MinecraftForge.EVENT_BUS.addListener(LeashEvents::onLevelTick);
        MinecraftForge.EVENT_BUS.addListener(LeashEvents::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(LeashEvents::onServerStopped);
    }

    private static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        InteractionResult result = LeashRules.interact(event.getEntity(), event.getTarget(), event.getHand());
        if (result != InteractionResult.PASS) {
            event.setCanceled(true);
            event.setCancellationResult(result);
        }
    }

    /**
     * A firework boost while gliding breaks the leads you hold. Vanilla Backport does this itself
     * for leads when present; the fence-to-fence ropes you carry are this mod's either way.
     */
    private static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!UlConfig.on(UlConfig.FIREWORK_BREAKS_LEADS)) return;
        if (event.getItemStack().getItem() instanceof FireworkRocketItem && event.getEntity().isFallFlying()) {
            if (!VanillaBackport.present()) {
                LeashRules.fireworkBoost(event.getEntity());
            } else if (event.getLevel() instanceof ServerLevel level && Tethers.breakCarried(level, event.getEntity()) > 0) {
                UlSounds.play(level, event.getEntity().position(), UlSounds.LEAD_BREAK, event.getEntity());
            }
        }
    }

    /**
     * Tell a player who starts seeing a leashed boat who holds it (our boat model only), or a knot its ropes; and
     * anything with an upgraded lead what its lead is made of.
     */
    private static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        LeadTiers.sendTo(player, event.getTarget());
        if (event.getTarget() instanceof BoatLeash leash) {
            BoatLeashes.sendTo(player, event.getTarget(), leash);
        } else if (event.getTarget() instanceof LeashFenceKnotEntity knot) {
            Tethers.sendTo(player, knot);
        }
    }

    private static void onJoinLevel(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide()) Knots.onJoin(event.getEntity());
    }

    /** Server, end of each level's tick: the fence-to-fence ropes' upkeep and pulls. */
    private static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel level) Tethers.tick(level);
    }

    /** End of the server tick: this tick's rope pulls go to the physics thread in one piece. */
    private static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) TetherPhysics.publish();
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        TetherPhysics.clear();
        Tethers.clear();
        Knots.clear();
    }

    /**
     * A leashed boat that is destroyed drops its lead; one leaving the dimension just lets go.
     * The lead item is spawned on the next server tick rather than inside the removal callback.
     */
    private static void onLeaveLevel(EntityLeaveLevelEvent event) {
        Entity entity = event.getEntity();
        if (!event.getLevel().isClientSide()) Knots.onLeave(entity);
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
        LeadTier tier = LeadTiers.of(entity); // an upgraded lead drops as itself
        MinecraftServer server = level.getServer();
        server.tell(new TickTask(server.getTickCount(), () -> {
            ItemEntity lead = new ItemEntity(level, at.x, at.y + 0.2, at.z, new ItemStack(tier.item()));
            lead.setDefaultPickUpDelay();
            level.addFreshEntity(lead);
        }));
    }
}
