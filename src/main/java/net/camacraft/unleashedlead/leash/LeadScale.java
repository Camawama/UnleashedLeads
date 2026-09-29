package net.camacraft.unleashedlead.leash;

import net.camacraft.camafy.compat.scale.EntityScale;
import net.camacraft.unleashedlead.rope.RopeScale;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.HangingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * How big a lead is, from the entities at its ends (the rule itself is {@link RopeScale}).
 *
 * <p>An end's size is how far a size mod (Pehkui, which Genesis bundles) has resized it: its
 * standing box next to its type's normal box (Camafy's {@link EntityScale}, which needs no Pehkui
 * API), the larger of width and height, divided by vanilla's own baby scale. So a calf or a baby
 * turtle is size 1 (vanilla draws their leads like any other), a pig shrunk to 1/16 is 1/16, a baby
 * pig shrunk to 1/16 is 1/16 too. A fence knot is always size 1: it is sized to the fence post it
 * wraps, and its leads leave it at the post.
 *
 * <p>Both sides compute it the same way (the size is synced with the entity), the server for the
 * physics and the client for the rope.
 */
public final class LeadScale {
    private LeadScale() {}

    /** An end's size: 1 at normal size (and for knots, babies and no entity), 1/16 when shrunk to 1/16. */
    public static double size(@Nullable Entity entity) {
        if (entity == null || entity instanceof HangingEntity) return 1.0;
        double resized = Math.max(EntityScale.widthFactor(entity), EntityScale.heightFactor(entity));
        if (entity instanceof LivingEntity living) {
            double natural = living.getScale(); // vanilla babies: 0.5 (a baby turtle 0.3)
            if (natural > 0.0 && Double.isFinite(natural)) resized /= natural;
        }
        return RopeScale.clean(resized);
    }

    /** The lead between {@code entity} and {@code holder} (a null holder is a fence being tied to: size 1). */
    public static RopeScale of(Entity entity, @Nullable Entity holder) {
        return RopeScale.between(size(entity), size(holder));
    }

    /** The lead's length scale: what its free length, snap distance, slack and segment spacing are multiplied by. */
    public static double reach(Entity entity, @Nullable Entity holder) {
        return of(entity, holder).reach();
    }
}
