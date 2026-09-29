package net.camacraft.unleashedlead.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.camacraft.unleashedlead.UlConfig;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Vanilla's leash line is not drawn while rope rendering is on: {@code client.rope.ClientRopes}
 * draws every lead instead. (With Vanilla Backport installed its own mixin already skips this
 * call.)
 *
 * <p>The call to {@code renderLeash} is skipped, not the method cancelled from its HEAD: other
 * mods inject into {@code renderLeash} in pairs. Pehkui pushes the pose stack twice at HEAD (to
 * scale the leash) and pops at RETURN, so a HEAD cancel returned past its pops and crashed the
 * game with "Pose stack not empty" the moment any leashed mob was drawn.
 */
@Mixin(MobRenderer.class)
public abstract class MobRendererMixin {
    @WrapOperation(method = "render(Lnet/minecraft/world/entity/Mob;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/MobRenderer;renderLeash(Lnet/minecraft/world/entity/Mob;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/entity/Entity;)V"))
    private void unleashedlead$skipVanillaLeash(MobRenderer<?, ?> renderer, Mob mob, float partialTick, PoseStack poseStack,
                                                MultiBufferSource buffers, Entity holder, Operation<Void> original) {
        if (UlConfig.on(UlConfig.ROPE_RENDERING)) return;
        original.call(renderer, mob, partialTick, poseStack, buffers, holder);
    }
}
