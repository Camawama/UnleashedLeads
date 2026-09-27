package net.camacraft.unleashedlead;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

/**
 * The 1.21.6 lead sounds, in this mod's namespace. The audio is vanilla 1.20.1's own leash knot
 * sounds (see {@code assets/unleashedlead/sounds.json}); no sound files are shipped.
 */
public final class UlSounds {
    private UlSounds() {}

    public static final DeferredRegister<SoundEvent> REGISTER =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, UnleashedLead.MOD_ID);

    /** A lead is tied to a mob, a boat or a fence. */
    public static final RegistryObject<SoundEvent> LEAD_TIED = register("item.lead.tied");
    /** A lead is taken off by hand or cut. */
    public static final RegistryObject<SoundEvent> LEAD_UNTIED = register("item.lead.untied");
    /** A lead snaps because the ends got too far apart. */
    public static final RegistryObject<SoundEvent> LEAD_BREAK = register("item.lead.break");
    /** Shears cut a lead. */
    public static final RegistryObject<SoundEvent> SHEARS_SNIP = register("item.shears.snip");

    private static RegistryObject<SoundEvent> register(String path) {
        return REGISTER.register(path, () -> SoundEvent.createVariableRangeEvent(UnleashedLead.id(path)));
    }

    /** Plays {@code sound} at {@code at} for everyone nearby (server side; a no-op on the client). */
    public static void play(Level level, Vec3 at, RegistryObject<SoundEvent> sound, @Nullable Entity cause) {
        if (level.isClientSide || !UlConfig.on(UlConfig.LEAD_SOUNDS) || !sound.isPresent()) return;
        SoundSource source = cause == null ? SoundSource.NEUTRAL : cause.getSoundSource();
        level.playSound(null, at.x, at.y, at.z, sound.get(), source, 1.0F, 1.0F);
    }
}
