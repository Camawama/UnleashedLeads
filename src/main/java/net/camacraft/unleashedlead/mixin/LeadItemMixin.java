package net.camacraft.unleashedlead.mixin;

import net.camacraft.unleashedlead.leash.LeashRules;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.LeadItem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Tying held leads to a fence ({@code FenceBlock.use} and a lead used on a fence both end up
 * here): {@link LeashRules#useFence} ties boats too, measures in world space (fences on Valkyrien
 * Skies ships), skips leads that would snap at once, and with nothing to tie takes the knot's
 * leads (1.21.6).
 */
@Mixin(LeadItem.class)
public abstract class LeadItemMixin {
    @Inject(method = "bindPlayerMobs", at = @At("HEAD"), cancellable = true)
    private static void unleashedlead$bind(Player player, Level level, BlockPos pos,
                                           CallbackInfoReturnable<InteractionResult> cir) {
        cir.setReturnValue(LeashRules.useFence(player, level, pos));
    }
}
