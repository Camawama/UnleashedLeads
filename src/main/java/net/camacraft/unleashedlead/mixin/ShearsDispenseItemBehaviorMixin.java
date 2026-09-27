package net.camacraft.unleashedlead.mixin;

import net.camacraft.unleashedlead.leash.LeashRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockSource;
import net.minecraft.core.dispenser.OptionalDispenseItemBehavior;
import net.minecraft.core.dispenser.ShearsDispenseItemBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DispenserBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A dispenser holding shears cuts the leads of an entity in front of it (1.21.6), when it found
 * nothing to shear the vanilla way (a beehive, a sheep). Costs durability like any shearing.
 */
@Mixin(ShearsDispenseItemBehavior.class)
public abstract class ShearsDispenseItemBehaviorMixin {
    @Inject(method = "execute", at = @At("RETURN"))
    private void unleashedlead$snip(BlockSource source, ItemStack stack, CallbackInfoReturnable<ItemStack> cir) {
        OptionalDispenseItemBehavior self = (OptionalDispenseItemBehavior) (Object) this;
        if (self.isSuccess()) return;
        ServerLevel level = source.getLevel();
        BlockPos front = source.getPos().relative(source.getBlockState().getValue(DispenserBlock.FACING));
        if (LeashRules.dispenserSnip(level, front)) {
            self.setSuccess(true);
            if (stack.hurt(1, level.getRandom(), null)) stack.setCount(0);
        }
    }
}
