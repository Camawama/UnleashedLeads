package net.camacraft.unleashedlead.item;

import net.camacraft.unleashedlead.leash.LeadTier;
import net.minecraft.world.item.LeadItem;

/**
 * An upgraded lead (iron, diamond, netherite: made at the smithing table). It is a lead in every way: it
 * ties to fences ({@code LeadItem.useOn}), ties mobs, boats and cars ({@code leash.LeashRules}), starts
 * fence-to-fence ropes; everything that asks for "a lead" asks {@link LeadTier#isLead}. Only harder to
 * break ({@link LeadTier#strength}).
 */
public class TieredLeadItem extends LeadItem {
    private final LeadTier tier;

    public TieredLeadItem(LeadTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    public LeadTier tier() {
        return tier;
    }
}
