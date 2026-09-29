package net.camacraft.unleashedlead.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.camacraft.unleashedlead.leash.LeashRules;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FenceBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Using a fence (server side) passes on which hand was used to {@code LeadItem.bindPlayerMobs}
 * (see {@code LeadItemMixin}): a lead in that hand starts a fence-to-fence rope, and the off-hand
 * use a client sends straight after an empty main hand is recognised as the same click.
 */
@Mixin(FenceBlock.class)
public abstract class FenceBlockMixin {
    @WrapOperation(method = "use",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/item/LeadItem;bindPlayerMobs(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/InteractionResult;"))
    private InteractionResult unleashedlead$bindWithHand(Player player, Level level, BlockPos pos,
                                                         Operation<InteractionResult> original,
                                                         @Local(argsOnly = true) InteractionHand hand) {
        return LeashRules.withHand(hand, () -> original.call(player, level, pos));
    }
}
