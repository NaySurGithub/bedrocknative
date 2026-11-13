package net.bedrocknative.model.render;

import net.bedrocknative.model.geometry.TextureMesher;
import org.jspecify.annotations.Nullable;

public final class TextureComposer {
   private TextureComposer() {
   }

   public static TextureComposer.@Nullable Composed compose(MaterialTable.MaterialInfo material, TextureMesher.@Nullable Pixels[] layers, int tint) {
      return compose(material, layers, tint, -1);
   }

   public static TextureComposer.@Nullable Composed compose(MaterialTable.MaterialInfo material, TextureMesher.@Nullable Pixels[] layers, int tint, int tint2) {
      TextureMesher.Pixels base = layers.length > 0 ? layers[0] : null;
      if (base == null) {
         return null;
      }

      TextureMesher.Pixels layer1 = layers.length > 1 ? layers[1] : null;
      TextureMesher.Pixels layer2 = layers.length > 2 ? layers[2] : null;
      boolean masked = material.defines().contains("MASKED_MULTITEXTURE");
      boolean multitexture = material.defines().contains("USE_MULTITEXTURE");
      boolean multiplicative = material.defines().contains("MULTIPLICATIVE_TINT");
      boolean multiplicativeColor = material.defines().contains("MULTIPLICATIVE_TINT_COLOR");
      boolean secondColor = material.defines().contains("COLOR_SECOND_TEXTURE");
      boolean alphaTest = material.alphaTest();
      boolean colorMask = material.colorMask();
      boolean emissive = material.emissive() || material.onlyEmissive();
      boolean onlyEmissive = material.onlyEmissive();
      boolean blending = material.blending();
      float tr = (tint >> 16 & 0xFF) / 255.0F;
      float tg = (tint >> 8 & 0xFF) / 255.0F;
      float tb = (tint & 0xFF) / 255.0F;
      float ta = (tint >>> 24) / 255.0F;
      int multiply = tint2 != -1 ? tint2 : tint;
      float mr = (multiply >> 16 & 0xFF) / 255.0F;
      float mg = (multiply >> 8 & 0xFF) / 255.0F;
      float mb = (multiply & 0xFF) / 255.0F;
      int width = base.width();
      int height = base.height();
      int[] out = new int[width * height];
      int[] glow = emissive ? new int[width * height] : null;
      boolean anyGlow = false;

      for (int y = 0; y < height; y++) {
         for (int x = 0; x < width; x++) {
            int texel = base.argb()[y * width + x];
            float a = (texel >>> 24) / 255.0F;
            float r = (texel >> 16 & 0xFF) / 255.0F;
            float g = (texel >> 8 & 0xFF) / 255.0F;
            float b = (texel & 0xFF) / 255.0F;
            if (masked && layer1 != null) {
               int over = sample(layer1, x, y, width, height);
               float oa = (over >>> 24) / 255.0F;
               int rgb = over & 16777215;
               boolean showBase = rgb != 0 && oa < 1.0F;
               if (!showBase) {
                  a = oa;
                  r = (over >> 16 & 0xFF) / 255.0F;
                  g = (over >> 8 & 0xFF) / 255.0F;
                  b = (over & 0xFF) / 255.0F;
               }
            }

            boolean discard = alphaTest && !multitexture && !multiplicative && needsDiscard(r, g, b, a, emissive, onlyEmissive, colorMask, blending);
            if (!discard && colorMask) {
               r = mix(r, r * tr, a);
               g = mix(g, g * tg, a);
               b = mix(b, b * tb, a);
               a *= ta;
            }

            if (!discard && multitexture) {
               int t1 = layer1 != null ? sample(layer1, x, y, width, height) : 0;
               int t2 = layer2 != null ? sample(layer2, x, y, width, height) : 0;
               float a1 = (t1 >>> 24) / 255.0F;
               r = mix(r, (t1 >> 16 & 0xFF) / 255.0F, a1);
               g = mix(g, (t1 >> 8 & 0xFF) / 255.0F, a1);
               b = mix(b, (t1 & 0xFF) / 255.0F, a1);
               if (alphaTest && a < 0.5F && a1 == 0.0F) {
                  discard = true;
               }

               float a2 = (t2 >>> 24) / 255.0F;
               float r2 = (t2 >> 16 & 0xFF) / 255.0F;
               float g2 = (t2 >> 8 & 0xFF) / 255.0F;
               float b2 = (t2 & 0xFF) / 255.0F;
               if (secondColor) {
                  if (a2 > 0.0F) {
                     r = r2 + (r2 * tr - r2) * a2;
                     g = g2 + (g2 * tg - g2) * a2;
                     b = b2 + (b2 * tb - b2) * a2;
                  }
               } else {
                  r = mix(r, r2, a2);
                  g = mix(g, g2, a2);
                  b = mix(b, b2, a2);
               }
            }

            if (!discard && multiplicative) {
               int t = layer1 != null ? sample(layer1, x, y, width, height) : 0;
               float at = (t >>> 24) / 255.0F;
               float rt = (t >> 16 & 0xFF) / 255.0F;
               float gt = (t >> 8 & 0xFF) / 255.0F;
               float bt = (t & 0xFF) / 255.0F;
               if (multiplicativeColor) {
                  rt *= mr;
                  gt *= mg;
                  bt *= mb;
               }

               if (alphaTest) {
                  r = mix(r, rt, at);
                  g = mix(g, gt, at);
                  b = mix(b, bt, at);
                  if (a + at <= 0.0F) {
                     discard = true;
                  }
               }

               r = mix(r, r * rt, at);
               g = mix(g, g * gt, at);
               b = mix(b, b * bt, at);
            }

            int index = y * width + x;
            if (discard) {
               out[index] = 0;
            } else {
               int rgb = channel(r) << 16 | channel(g) << 8 | channel(b);
               out[index] = onlyEmissive ? 0 : (blending ? channel(a) : 255) << 24 | rgb;
               if (glow != null) {
                  float strength = onlyEmissive ? 1.0F : 1.0F - a;
                  if (strength > 0.0F) {
                     glow[index] = channel(strength) << 24 | rgb;
                     anyGlow = true;
                  }
               }
            }
         }
      }

      return new TextureComposer.Composed(width, height, out, anyGlow ? glow : null);
   }

   static boolean needsDiscard(float r, float g, float b, float a, boolean emissive, boolean onlyEmissive, boolean colorMask, boolean blending) {
      if (emissive) {
         return onlyEmissive ? a == 0.0F || a == 1.0F : a + r + g + b == 0.0F;
      } else {
         return !colorMask && !blending ? a < 0.5F : a == 0.0F;
      }
   }

   private static int sample(TextureMesher.Pixels layer, int x, int y, int width, int height) {
      if (layer.width() == width && layer.height() == height) {
         return layer.argb()[y * width + x];
      }

      int lx = Math.min(layer.width() - 1, (int)((x + 0.5F) * layer.width() / width));
      int ly = Math.min(layer.height() - 1, (int)((y + 0.5F) * layer.height() / height));
      return layer.argb()[ly * layer.width() + lx];
   }

   private static float mix(float from, float to, float t) {
      return from + (to - from) * t;
   }

   private static int channel(float value) {
      return Math.round(Math.clamp(value, 0.0F, 1.0F) * 255.0F);
   }

   public record Composed(int width, int height, int[] base, int @Nullable [] emissive) {
   }
}
