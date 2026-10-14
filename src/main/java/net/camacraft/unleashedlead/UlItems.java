package net.camacraft.unleashedlead;

import net.camacraft.unleashedlead.item.TieredLeadItem;
import net.camacraft.unleashedlead.leash.LeadTier;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * The upgraded leads, made at the smithing table with no template: lead + iron ingot, then + diamond,
 * then + netherite ingot ({@code data/unleashedlead/recipes/*_lead_smithing.json}). Stronger, otherwise
 * plain leads ({@link LeadTier}). The netherite one survives fire and lava like netherite gear.
 */
public final class UlItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, UnleashedLead.MOD_ID);

    public static final RegistryObject<Item> IRON_LEAD = ITEMS.register("iron_lead",
            () -> new TieredLeadItem(LeadTier.IRON, new Item.Properties()));
    public static final RegistryObject<Item> DIAMOND_LEAD = ITEMS.register("diamond_lead",
            () -> new TieredLeadItem(LeadTier.DIAMOND, new Item.Properties()));
    public static final RegistryObject<Item> NETHERITE_LEAD = ITEMS.register("netherite_lead",
            () -> new TieredLeadItem(LeadTier.NETHERITE, new Item.Properties().fireResistant()));

    private UlItems() {}

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
