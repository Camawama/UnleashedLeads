package net.camacraft.unleashedlead;

import com.mojang.logging.LogUtils;
import net.camacraft.unleashedlead.client.UnleashedLeadClient;
import net.camacraft.unleashedlead.compat.VanillaBackport;
import net.camacraft.unleashedlead.leash.LeashEvents;
import net.camacraft.unleashedlead.leash.VehicleLeashes;
import net.camacraft.unleashedlead.network.UlNetwork;
import net.camacraft.unleashedlead.recipe.ConfigCondition;
import net.camacraft.unleashedlead.tether.TetherPhysics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

/**
 * Unleashed Lead: Bedrock's lead rope and leash physics on Java, plus the 1.21 to 1.21.6 lead
 * changes backported to 1.20.1.
 *
 * <p>Where the pieces live:
 * <ul>
 *   <li>{@code rope}: the rope solver (plain Java, shared by the renderer and the tests).</li>
 *   <li>{@code leash}: the leash model ({@link net.camacraft.unleashedlead.leash.Leashes}),
 *   its physics, the interaction rules and the event wiring.</li>
 *   <li>{@code tether}: leads tied from fence to fence, and their pull on Valkyrien Skies ships.</li>
 *   <li>{@code compat}: Vanilla Backport, which owns the leash data model when installed.</li>
 *   <li>{@code client}: rope simulation and drawing.</li>
 *   <li>{@code mixin}: the vanilla hooks.</li>
 * </ul>
 */
@Mod(UnleashedLead.MOD_ID)
public final class UnleashedLead {
    public static final String MOD_ID = "unleashedlead";
    public static final Logger LOGGER = LogUtils.getLogger();

    public UnleashedLead(FMLJavaModLoadingContext context) {
        IEventBus modBus = context.getModEventBus();
        context.registerConfig(ModConfig.Type.COMMON, UlConfig.COMMON_SPEC);
        context.registerConfig(ModConfig.Type.SERVER, UlConfig.SERVER_SPEC);
        context.registerConfig(ModConfig.Type.CLIENT, UlConfig.CLIENT_SPEC);

        UlSounds.REGISTER.register(modBus);
        UlBlocks.register(modBus);
        CraftingHelper.register(ConfigCondition.Serializer.INSTANCE);
        UlNetwork.init();
        LeashEvents.register();
        VehicleLeashes.register();
        // fence-to-fence ropes pull ships through Camafy's one physics hook (a no-op without VS)
        modBus.addListener(UnleashedLead::onCommonSetup);

        if (VanillaBackport.present()) {
            LOGGER.info("Vanilla Backport found: it keeps the leash data model (boats, happy ghast) and its leash tick; "
                    + "Unleashed Lead supplies the pull and distances through its hooks, draws every lead and adds the rules it lacks.");
        }
        if (FMLEnvironment.dist.isClient()) UnleashedLeadClient.init(modBus);
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(TetherPhysics::register);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
