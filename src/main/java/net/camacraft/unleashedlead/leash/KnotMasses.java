package net.camacraft.unleashedlead.leash;

import net.camacraft.unleashedlead.network.KnotMassMessage;
import net.camacraft.unleashedlead.network.UlNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;

/**
 * The mass of the Valkyrien Skies ship a knot is on, for a client that simulates something tied to
 * it: a car or a boat a player drives is moved on that player's client, which must share the lead's
 * pull with the ship by their masses ({@link LeashPhysics}), and Valkyrien Skies doesn't send ship
 * masses to clients. The server sends the mass of the ship a driven vehicle's lead is tied to now and
 * then ({@link #sendToDriver}); the client keeps it per knot for a few seconds.
 */
public final class KnotMasses {
    /** How often the server sends the mass to the driver (ticks). */
    private static final int SEND_EVERY = 10;
    /** How long the client trusts a mass it was sent (ticks of the client's own clock). */
    private static final int KEEP_TICKS = 60;

    private record Known(double massKg, long tick) {}

    /** Client: knot entity id -> its ship's mass. Client thread only. */
    private static final Map<Integer, Known> CLIENT = new HashMap<>();
    private static long clientTick;

    private KnotMasses() {}

    /**
     * Server: if {@code vehicle} (a car or a boat) is driven by a player and its lead is tied to a knot on a
     * ship, tells that player the ship's mass every {@link #SEND_EVERY} ticks.
     */
    public static void sendToDriver(Entity vehicle, Entity holder) {
        if (vehicle.level().isClientSide || vehicle.tickCount % SEND_EVERY != 0) return;
        LivingEntity driver = vehicle.getControllingPassenger();
        if (!(driver instanceof ServerPlayer player)) return;
        LeashPhysics.ShipKnot knot = LeashPhysics.shipKnot(holder);
        if (knot == null) return;
        UlNetwork.CHANNEL.toPlayer(player, new KnotMassMessage(holder.getId(), (float) knot.massKg()));
    }

    /** Client: a mass the server sent ({@link KnotMassMessage}). */
    public static void put(int knotId, float massKg) {
        CLIENT.put(knotId, new Known(massKg, clientTick));
    }

    /** Client: the mass of the ship the knot {@code knotId} is on, or NaN when the server hasn't said lately. */
    public static double get(int knotId) {
        Known known = CLIENT.get(knotId);
        return known == null || clientTick - known.tick > KEEP_TICKS ? Double.NaN : known.massKg;
    }

    /** Client, every tick: ages what it was sent. */
    public static void clientTick() {
        clientTick++;
        if (clientTick % 200 == 0) CLIENT.values().removeIf(k -> clientTick - k.tick > KEEP_TICKS);
    }

    /** Leaving a world. */
    public static void clear() {
        CLIENT.clear();
    }
}
