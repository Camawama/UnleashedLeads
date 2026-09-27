package net.camacraft.unleashedlead.mixin.client;

import net.camacraft.unleashedlead.UlConfig;
import net.minecraft.client.renderer.entity.MobRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla's leash line is not drawn while rope rendering is on: {@code client.rope.ClientRopes}
 * draws every lead instead. (With Vanilla Backport installed its own mixin already skips this
 * call; cancelling here as well is harmless.)
 */
@Mixin(MobRenderer.class)
public abstract class MobRendererMixin {
    @Inject(method = "renderLeash", at = @At("HEAD"), cancellable = true)
    private void unleashedlead$skipVanillaLeash(CallbackInfo ci) {
        if (UlConfig.on(UlConfig.ROPE_RENDERING)) ci.cancel();
    }
}
