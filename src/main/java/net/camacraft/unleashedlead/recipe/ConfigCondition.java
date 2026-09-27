package net.camacraft.unleashedlead.recipe;

import com.google.gson.JsonObject;
import net.camacraft.unleashedlead.UlConfig;
import net.camacraft.unleashedlead.UnleashedLead;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.common.crafting.conditions.IConditionSerializer;

/**
 * {@code {"type": "unleashedlead:config", "option": "new_lead_recipe"}}: true while that option
 * is on in {@code unleashedlead-common.toml}. The lead recipe override
 * ({@code data/minecraft/recipes/lead.json}) is a {@code forge:conditional} recipe that picks the
 * 1.21.6 string recipe when this holds and the old slimeball recipe otherwise, so turning the
 * option off never leaves the game without a lead recipe.
 */
public record ConfigCondition(String option) implements ICondition {
    public static final ResourceLocation ID = UnleashedLead.id("config");

    @Override
    public ResourceLocation getID() {
        return ID;
    }

    @Override
    public boolean test(IContext context) {
        return switch (option) {
            case "new_lead_recipe" -> UlConfig.on(UlConfig.NEW_LEAD_RECIPE);
            default -> {
                UnleashedLead.LOGGER.warn("Unknown unleashedlead:config recipe option '{}'", option);
                yield false;
            }
        };
    }

    public enum Serializer implements IConditionSerializer<ConfigCondition> {
        INSTANCE;

        @Override
        public void write(JsonObject json, ConfigCondition value) {
            json.addProperty("option", value.option());
        }

        @Override
        public ConfigCondition read(JsonObject json) {
            return new ConfigCondition(GsonHelper.getAsString(json, "option"));
        }

        @Override
        public ResourceLocation getID() {
            return ID;
        }
    }
}
