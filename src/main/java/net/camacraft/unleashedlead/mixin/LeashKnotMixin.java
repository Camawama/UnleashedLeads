package net.camacraft.unleashedlead.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.camacraft.unleashedlead.UlBlocks;
import net.camacraft.unleashedlead.block.LeadCleatBlock;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A lead's knot on a lead cleat: it holds there as on a fence ({@code survives}), and it sits on the
 * cleat's horn rather than halfway up a fence post that isn't there.
 */
@Mixin(LeashFenceKnotEntity.class)
public abstract class LeashKnotMixin extends HangingEntity {
    protected LeashKnotMixin(EntityType<? extends HangingEntity> type, Level level) {
        super(type, level);
    }

    @WrapOperation(method = "survives",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/tags/TagKey;)Z"))
    private boolean unleashedlead$holdsOnCleats(BlockState state, TagKey<Block> tag, Operation<Boolean> original) {
        return original.call(state, tag) || UlBlocks.isAnchor(state);
    }

    @Inject(method = "recalculateBoundingBox", at = @At("TAIL"))
    private void unleashedlead$sitOnTheHorn(CallbackInfo ci) {
        Level level = level();
        if (level == null || pos == null || !level.isLoaded(pos)) return;
        Vec3 at = LeadCleatBlock.knotPosition(pos, level.getBlockState(pos));
        if (at == null) return;
        // the knot's box is centred on the horn
        double halfWidth = getType().getWidth() / 2.0, height = getType().getHeight();
        double x = at.x, y = at.y, z = at.z;
        setPosRaw(x, y, z);
        setBoundingBox(new AABB(x - halfWidth, y, z - halfWidth, x + halfWidth, y + height, z + halfWidth));
    }
}
