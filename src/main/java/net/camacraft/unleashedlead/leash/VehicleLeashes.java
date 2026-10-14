package net.camacraft.unleashedlead.leash;

import net.camacraft.unleashedlead.UlConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Leads on Automobility's vehicles ({@link VehicleLeash}): what a boat's own tick does for its lead
 * ({@code mixin.BoatMixin}), done from outside, since nothing is injected into the vehicle:
 * <ul>
 *   <li>the server, at the end of each level tick: the upkeep ({@link BoatLeashes#serverTick}:
 *   re-attach after loading, let go of a dead holder, wait for a ship's knot), the snap, and the pull
 *   of a vehicle nobody drives (the server simulates it);</li>
 *   <li>the driving player's client, at the end of its tick: the pull of the vehicle it drives, which
 *   is simulated there ({@code pullDrivenVehicles});</li>
 *   <li>the lead is kept in the vehicle's persistent data, which Forge saves with it, and read back
 *   when the vehicle joins a level.</li>
 * </ul>
 * The pull reaches the vehicle through Camafy's {@code Impulses} ({@link LeashPhysics}), which MKW
 * Mobility turns into the kart's own speed: a write to its {@code deltaMovement} would be gone a tick
 * later.
 */
public final class VehicleLeashes {
    /** The tied vehicles the server keeps up (server thread only). */
    private static final Set<Entity> SERVER = Collections.newSetFromMap(new WeakHashMap<>());

    private VehicleLeashes() {}

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(VehicleLeashes::onJoinLevel);
        MinecraftForge.EVENT_BUS.addListener(VehicleLeashes::onLevelTick);
    }

    /** Server: a vehicle was tied or untied ({@link Leashes#attach} / {@link Leashes#detach}). */
    public static void changed(Entity vehicle) {
        if (vehicle.level().isClientSide || !(vehicle instanceof VehicleLeash leash)) return;
        SERVER.add(vehicle);
        mirror(vehicle, leash);
    }

    private static void onJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof VehicleLeash leash)) return;
        CompoundTag data = event.getEntity().getPersistentData();
        if (leash.ul$holder() == null && data.contains(BoatLeashes.SAVE_KEY, 10)) {
            BoatLeashes.load(leash, data);
            SERVER.add(event.getEntity());
        }
    }

    private static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level) || SERVER.isEmpty()) return;
        for (Entity vehicle : new ArrayList<>(SERVER)) {
            if (vehicle == null || vehicle.level() != level) continue;
            if (vehicle.isRemoved()) {
                SERVER.remove(vehicle); // unloaded: saved with it; destroyed: LeashEvents dropped the lead
                continue;
            }
            VehicleLeash leash = (VehicleLeash) vehicle;
            BoatLeashes.serverTick(vehicle, leash);
            Entity holder = leash.ul$holder();
            if (holder != null) {
                // the server pulls a vehicle it simulates (nobody drives it: controlled by this instance); one a
                // player drives is pulled on that player's client, and its snap is still decided here
                LeashPhysics.tick(vehicle, holder, vehicle.isControlledByLocalInstance());
            }
            mirror(vehicle, leash);
            if (leash.ul$holder() == null && leash.ul$pendingTag() == null) SERVER.remove(vehicle);
        }
    }

    /** Client, end of its tick: the vehicle this client's player drives, pulled by its lead. */
    public static void clientTick(@Nullable Player player) {
        if (player == null || !UlConfig.on(UlConfig.PULL_DRIVEN_VEHICLES)) return;
        // the car itself, or the car a seat the player sits on rides (RIAutomobility's bigger vehicles)
        Entity vehicle = player.getVehicle();
        while (vehicle != null && !(vehicle instanceof VehicleLeash)) vehicle = vehicle.getVehicle();
        if (vehicle == null || !vehicle.isControlledByLocalInstance()) return;
        Entity holder = Leashes.holder(vehicle);
        // once per tick of the car itself: pulls given while it stood still (paused) piled up and flung it
        if (holder != null && CLIENT_TICKS.fresh(vehicle)) LeashPhysics.tick(vehicle, holder, true);
    }

    /** The driven vehicle {@link #clientTick} pulled, by the vehicle tick it was pulled on. */
    private static final LeashPhysics.OncePerTick CLIENT_TICKS = new LeashPhysics.OncePerTick();

    /** Keeps the lead in the vehicle's persistent data, which Forge saves with the entity. */
    private static void mirror(Entity vehicle, VehicleLeash leash) {
        CompoundTag data = vehicle.getPersistentData();
        data.remove(BoatLeashes.SAVE_KEY);
        BoatLeashes.save(leash, data);
    }

    /** Server: the tied vehicles kept up right now (for the tests). */
    public static int tracked() {
        return SERVER.size();
    }
}
