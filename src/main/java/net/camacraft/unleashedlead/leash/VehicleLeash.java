package net.camacraft.unleashedlead.leash;

/**
 * A {@link BoatLeash} on a vehicle another mod drives: Automobility's automobiles (RIAutomobility's
 * vehicles included), mixed in by {@code mixin.compat.AutomobileLeashMixin}. The same leash state and
 * sync as a boat's; what differs is that MKW Mobility scripts their motion, so a lead pulls them
 * through Camafy's {@code Impulses} and {@link VehicleLeashes} ticks them, and that they save their
 * lead through Forge's persistent entity data.
 */
public interface VehicleLeash extends BoatLeash {
}
