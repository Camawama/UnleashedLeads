package net.camacraft.unleashedlead.leash;

import net.camacraft.camafy.config.ConfigUtil;
import net.camacraft.camafy.util.color.Argb;
import net.camacraft.unleashedlead.UlConfig;
import net.camacraft.unleashedlead.UlItems;
import net.camacraft.unleashedlead.item.TieredLeadItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.ForgeConfigSpec;
import org.jetbrains.annotations.Nullable;

/**
 * What a lead is made of: a plain lead, or one upgraded at the smithing table (lead + iron ingot,
 * then + diamond, then + netherite ingot). An upgraded lead has a plain one's free length, pull and tow
 * length; it is harder to break: it stretches further past its free length before it snaps
 * ({@link #strength}), and a rope of it tied to a ship holds a harder pull. It also wrangles easier: a
 * led animal puts its strength squared times less of its weight against it ({@code LeashPhysics.wrangle}). Which tier a lead is travels with the lead ({@link LeadTiers}): tied to a mob, a boat,
 * a car, a knot or a ship, saved with it, drawn in its colour, and dropped as its own item.
 */
public enum LeadTier {
    PLAIN(0),
    IRON(1),
    DIAMOND(2),
    NETHERITE(3);

    private static final LeadTier[] BY_ID = values();

    /** Saved and sent as this number (0 = a plain lead, and what anything without the data reads as). */
    public final int id;

    LeadTier(int id) {
        this.id = id;
    }

    public static LeadTier byId(int id) {
        return id > 0 && id < BY_ID.length ? BY_ID[id] : PLAIN;
    }

    /** The item a lead of this tier is (and drops as). */
    public Item item() {
        return switch (this) {
            case PLAIN -> Items.LEAD;
            case IRON -> UlItems.IRON_LEAD.get();
            case DIAMOND -> UlItems.DIAMOND_LEAD.get();
            case NETHERITE -> UlItems.NETHERITE_LEAD.get();
        };
    }

    /**
     * How much stronger than a plain lead (server config; 1 for a plain lead): how far past its free length
     * it stretches before it snaps, and how hard a rope of it on a ship may pull before it breaks.
     */
    public double strength() {
        return switch (this) {
            case PLAIN -> 1.0;
            case IRON -> UlConfig.num(UlConfig.IRON_LEAD_STRENGTH);
            case DIAMOND -> UlConfig.num(UlConfig.DIAMOND_LEAD_STRENGTH);
            case NETHERITE -> UlConfig.num(UlConfig.NETHERITE_LEAD_STRENGTH);
        };
    }

    /** The rope's colour as 0xAARRGGBB (client config). */
    public int ropeColor() {
        return switch (this) {
            case PLAIN -> color(UlConfig.ROPE_COLOR, 0xFF7F664C);
            case IRON -> color(UlConfig.IRON_ROPE_COLOR, 0xFFA9ADB1);
            case DIAMOND -> color(UlConfig.DIAMOND_ROPE_COLOR, 0xFF3FCFC0);
            case NETHERITE -> color(UlConfig.NETHERITE_ROPE_COLOR, 0xFF4A4043);
        };
    }

    private static int color(ForgeConfigSpec.ConfigValue<String> value, int fallback) {
        return Argb.parse(ConfigUtil.getOr(value), fallback);
    }

    /** The tier of a lead item, or null when {@code stack} isn't a lead at all. */
    @Nullable
    public static LeadTier of(ItemStack stack) {
        if (stack.is(Items.LEAD)) return PLAIN;
        return stack.getItem() instanceof TieredLeadItem lead ? lead.tier() : null;
    }

    /** Whether {@code stack} is a lead of any tier: everything a plain lead does, it does. */
    public static boolean isLead(ItemStack stack) {
        return of(stack) != null;
    }
}
