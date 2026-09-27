package net.camacraft.unleashedlead.client.rope;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;

/**
 * The rope's render type: vanilla's leash shader (position, colour, lightmap; no texture, no
 * culling) but drawn as separate quads rather than one triangle strip, so every rope in the frame
 * goes into a single batch instead of one draw call each.
 *
 * <p>Extends {@link RenderType} only to reach its protected render-state shards; never instantiated.
 */
public final class RopeRenderType extends RenderType {
    public static final RenderType ROPE = RenderType.create("unleashedlead_rope",
            DefaultVertexFormat.POSITION_COLOR_LIGHTMAP, VertexFormat.Mode.QUADS, 1 << 16, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RENDERTYPE_LEASH_SHADER)
                    .setTextureState(NO_TEXTURE)
                    .setCullState(NO_CULL)
                    .setLightmapState(LIGHTMAP)
                    .createCompositeState(false));

    private RopeRenderType(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                           boolean affectsCrumbling, boolean sortOnUpload, Runnable setup, Runnable clear) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setup, clear);
    }
}
