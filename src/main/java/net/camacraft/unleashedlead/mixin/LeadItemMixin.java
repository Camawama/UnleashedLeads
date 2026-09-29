package net.camacraft.unleashedlead.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.camacraft.unleashedlead.leash.LeashRules;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.LeadItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Tying held leads to a fence ({@code FenceBlock.use} and a lead used on a fence both end up in
 * {@code bindPlayerMobs}): {@link LeashRules#useFence} ties boats and fence-to-fence ropes too,
 * measures in world space (fences on Valkyrien Skies ships), skips leads that would snap at
 * once, starts a rope when a lead is used on a fence with nothing to tie, and with nothing to tie
 * takes the knot's leads (1.21.6).
 *
 * <p>The whole method is wrapped, not cancelled at its head: an injection pair another mod puts
 * inside it (a push at HEAD, a pop at RETURN) is skipped as a whole instead of being cut in half.
 * The lead's own use passes on which hand holds it ({@code FenceBlockMixin} does the same for the
 * fence), which the rope needs.
 */
@Mixin(LeadItem.class)
public abstract class LeadItemMixin {
    @WrapMethod(method = "bindPlayerMobs")
    private static InteractionResult unleashedlead$bind(Player player, Level level, BlockPos pos,
                                                        Operation<InteractionResult> original) {
        return LeashRules.bindPlayerMobs(player, level, pos);
    }

    @WrapOperation(method = "useOn",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/item/LeadItem;bindPlayerMobs(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/InteractionResult;"))
    private InteractionResult unleashedlead$bindWithHand(Player player, Level level, BlockPos pos,
                                                         Operation<InteractionResult> original,
                                                         @Local(argsOnly = true) UseOnContext context) {
        return LeashRules.withHand(context.getHand(), () -> original.call(player, level, pos));
    }
}
