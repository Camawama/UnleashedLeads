package net.camacraft.unleashedlead.mixin;

import net.camacraft.unleashedlead.leash.LeashRules;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Using a knot: the 1.21.6 rules in {@link LeashRules#useKnot} (server side; the client keeps vanilla's SUCCESS). */
@Mixin(LeashFenceKnotEntity.class)
public abstract class LeashFenceKnotEntityMixin {
    @Inject(method = "interact", at = @At("HEAD"), cancellable = true)
    private void unleashedlead$interact(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        LeashFenceKnotEntity self = (LeashFenceKnotEntity) (Object) this;
        if (!self.level().isClientSide) cir.setReturnValue(LeashRules.useKnot(self, player));
    }
}
