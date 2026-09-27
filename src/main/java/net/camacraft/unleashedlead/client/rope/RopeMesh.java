package net.camacraft.unleashedlead.client.rope;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;

/**
 * Turns a rope's points into geometry: a smooth (Catmull-Rom) curve through them, swept with a
 * square cross-section. Colouring follows vanilla's leash: a brown with every other stripe darker,
 * plus a fixed shade per face (top brightest, bottom darkest) since the leash shader has no
 * normals to light it with. Light is sampled per rope point and blended along the rope.
 */
final class RopeMesh {
    /** Curve points per rope segment. */
    private static final int SUBDIVISIONS = 2;

    private double[] curve = new double[0];
    private int[] curveLight = new int[0];

    /**
     * Emits the rope. {@code points} holds {@code count} x, y, z triples in world space,
     * {@code light} one packed lightmap value per point; {@code (upX, upY, upZ)} is "up" for this
     * rope (against its gravity).
     */
    void draw(VertexConsumer out, Matrix4f pose, double[] points, int count, int[] light,
              double camX, double camY, double camZ, double halfWidth,
              float red, float green, float blue, float stripeShade,
              double upX, double upY, double upZ) {
        if (count < 2) return;
        int m = (count - 1) * SUBDIVISIONS + 1;
        if (curve.length < m * 3) {
            curve = new double[m * 3];
            curveLight = new int[m];
        }
        buildCurve(points, count, light, m);

        double prevSx = 0, prevSy = 0, prevSz = 0;
        boolean havePrev = false;
        float[] ring = new float[12];
        float[] prevRing = new float[12];
        for (int j = 0; j < m; j++) {
            int a = Math.max(0, j - 1), b = Math.min(m - 1, j + 1);
            double tx = curve[b * 3] - curve[a * 3];
            double ty = curve[b * 3 + 1] - curve[a * 3 + 1];
            double tz = curve[b * 3 + 2] - curve[a * 3 + 2];
            double tl = Math.sqrt(tx * tx + ty * ty + tz * tz);
            if (tl < 1.0E-9) {
                tx = 0; ty = 1; tz = 0;
            } else {
                tx /= tl; ty /= tl; tz /= tl;
            }
            // side = tangent x up; near vertical, carry the previous side over instead
            double sx = ty * upZ - tz * upY, sy = tz * upX - tx * upZ, sz = tx * upY - ty * upX;
            double sl = Math.sqrt(sx * sx + sy * sy + sz * sz);
            if (sl < 0.15 && havePrev) {
                double d = prevSx * tx + prevSy * ty + prevSz * tz;
                sx = prevSx - tx * d;
                sy = prevSy - ty * d;
                sz = prevSz - tz * d;
                sl = Math.sqrt(sx * sx + sy * sy + sz * sz);
            }
            if (sl < 1.0E-9) {
                sx = 1; sy = 0; sz = 0; sl = 1;
            }
            sx /= sl; sy /= sl; sz /= sl;
            // up' = side x tangent
            double ux = sy * tz - sz * ty, uy = sz * tx - sx * tz, uz = sx * ty - sy * tx;
            prevSx = sx; prevSy = sy; prevSz = sz;
            havePrev = true;

            double px = curve[j * 3] - camX, py = curve[j * 3 + 1] - camY, pz = curve[j * 3 + 2] - camZ;
            double w = halfWidth;
            // corners: +s+u, -s+u, -s-u, +s-u
            ring[0] = (float) (px + (sx + ux) * w);  ring[1] = (float) (py + (sy + uy) * w);  ring[2] = (float) (pz + (sz + uz) * w);
            ring[3] = (float) (px + (-sx + ux) * w); ring[4] = (float) (py + (-sy + uy) * w); ring[5] = (float) (pz + (-sz + uz) * w);
            ring[6] = (float) (px + (-sx - ux) * w); ring[7] = (float) (py + (-sy - uy) * w); ring[8] = (float) (pz + (-sz - uz) * w);
            ring[9] = (float) (px + (sx - ux) * w);  ring[10] = (float) (py + (sy - uy) * w); ring[11] = (float) (pz + (sz - uz) * w);

            if (j > 0) {
                float stripe = ((j - 1) & 1) == 0 ? 1.0F : stripeShade;
                int l0 = curveLight[j - 1], l1 = curveLight[j];
                for (int k = 0; k < 4; k++) {
                    // face normal: +u, -s, -u, +s
                    double ny = switch (k) {
                        case 0 -> ux * upX + uy * upY + uz * upZ;
                        case 1 -> -(sx * upX + sy * upY + sz * upZ);
                        case 2 -> -(ux * upX + uy * upY + uz * upZ);
                        default -> sx * upX + sy * upY + sz * upZ;
                    };
                    float shade = (float) (0.75 + 0.25 * ny) * stripe;
                    float r = red * shade, g = green * shade, bl = blue * shade;
                    int c0 = k * 3, c1 = ((k + 1) & 3) * 3;
                    out.vertex(pose, prevRing[c0], prevRing[c0 + 1], prevRing[c0 + 2]).color(r, g, bl, 1.0F).uv2(l0).endVertex();
                    out.vertex(pose, prevRing[c1], prevRing[c1 + 1], prevRing[c1 + 2]).color(r, g, bl, 1.0F).uv2(l0).endVertex();
                    out.vertex(pose, ring[c1], ring[c1 + 1], ring[c1 + 2]).color(r, g, bl, 1.0F).uv2(l1).endVertex();
                    out.vertex(pose, ring[c0], ring[c0 + 1], ring[c0 + 2]).color(r, g, bl, 1.0F).uv2(l1).endVertex();
                }
            }
            float[] swap = prevRing;
            prevRing = ring;
            ring = swap;
        }
    }

    /** Catmull-Rom through the points, SUBDIVISIONS samples per segment, light blended linearly. */
    private void buildCurve(double[] p, int n, int[] light, int m) {
        int o = 0;
        for (int i = 0; i < n - 1; i++) {
            int i0 = Math.max(0, i - 1), i2 = i + 1, i3 = Math.min(n - 1, i + 2);
            for (int s = 0; s < SUBDIVISIONS; s++) {
                double t = (double) s / SUBDIVISIONS;
                for (int c = 0; c < 3; c++) {
                    curve[o * 3 + c] = catmullRom(p[i0 * 3 + c], p[i * 3 + c], p[i2 * 3 + c], p[i3 * 3 + c], t);
                }
                curveLight[o] = blendLight(light[i], light[i2], t);
                o++;
            }
        }
        curve[o * 3] = p[(n - 1) * 3];
        curve[o * 3 + 1] = p[(n - 1) * 3 + 1];
        curve[o * 3 + 2] = p[(n - 1) * 3 + 2];
        curveLight[o] = light[n - 1];
    }

    private static double catmullRom(double p0, double p1, double p2, double p3, double t) {
        double t2 = t * t, t3 = t2 * t;
        return 0.5 * ((2.0 * p1) + (-p0 + p2) * t + (2.0 * p0 - 5.0 * p1 + 4.0 * p2 - p3) * t2
                + (-p0 + 3.0 * p1 - 3.0 * p2 + p3) * t3);
    }

    private static int blendLight(int a, int b, double t) {
        int block = (int) Math.round(LightTexture.block(a) + (LightTexture.block(b) - LightTexture.block(a)) * t);
        int sky = (int) Math.round(LightTexture.sky(a) + (LightTexture.sky(b) - LightTexture.sky(a)) * t);
        return LightTexture.pack(block, sky);
    }
}
