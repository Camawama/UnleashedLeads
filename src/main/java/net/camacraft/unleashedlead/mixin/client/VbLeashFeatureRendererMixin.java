package net.camacraft.unleashedlead.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.camacraft.unleashedlead.UlConfig;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla Backport draws its own leads (single and quad) from a {@code LeashFeatureRenderer} on
 * every entity renderer. While rope rendering is on, Unleashed Lead draws those same leads as
 * simulated ropes, so Vanilla Backport's are skipped here: one rope per lead, never two.
 *
 * <p>Only applied when Vanilla Backport is installed ({@code UlMixinPlugin}); both known package
 * names are targeted and neither is required, so a future move just leaves both ropes drawn.
 */
@Pseudo
@Mixin(targets = {
        "com.blackgear.vanillabackport.common.api.leash.LeashFeatureRenderer",
        "com.blackgear.vanillabackport.common.api.modules.leash_behavior.LeashFeatureRenderer"
}, remap = false)
public abstract class VbLeashFeatureRendererMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void unleashedlead$skipTheirRope(Entity entity, float partialTick, PoseStack poseStack, MultiBufferSource buffer,
                                             CallbackInfo ci) {
        if (UlConfig.on(UlConfig.ROPE_RENDERING)) ci.cancel();
    }
}
