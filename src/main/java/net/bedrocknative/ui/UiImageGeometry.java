package net.bedrocknative.ui;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

public final class UiImageGeometry {
   public static final int MAX_TILES = 4096;

   private UiImageGeometry() {
   }

   public static List<UiDrawCommand.Quad> quads(
      float x,
      float y,
      float w,
      float h,
      UiTextureInfo texture,
      float @Nullable [] uv,
      float @Nullable [] uvSize,
      float @Nullable [] slices,
      boolean keepRatio,
      @Nullable String tiled,
      float[] tiledScale,
      float clipRatio,
      String clipDirection
   ) {
      float u0 = uv != null && uv.length >= 2 ? uv[0] : 0.0F;
      float v0 = uv != null && uv.length >= 2 ? uv[1] : 0.0F;
      float uw = uvSize != null && uvSize.length >= 2 ? uvSize[0] : texture.width();
      float vh = uvSize != null && uvSize.length >= 2 ? uvSize[1] : texture.height();
      float[] slice = slices != null ? slices : texture.nineslice();
      List<UiDrawCommand.Quad> quads;
      if (slice == null || !(slice[0] > 0.0F) && !(slice[1] > 0.0F) && !(slice[2] > 0.0F) && !(slice[3] > 0.0F)) {
         if (tiled != null) {
            quads = tiled(x, y, w, h, u0, v0, uw, vh, texture, tiled, tiledScale);
         } else if (keepRatio && slice == null && uw > 0.0F && vh > 0.0F) {
            float scale = Math.min(w / uw, h / vh);
            float fw = uw * scale;
            float fh = vh * scale;
            quads = List.of(new UiDrawCommand.Quad(x + (w - fw) / 2.0F, y + (h - fh) / 2.0F, fw, fh, u0, v0, u0 + uw, v0 + vh));
         } else {
            quads = List.of(new UiDrawCommand.Quad(x, y, w, h, u0, v0, u0 + uw, v0 + vh));
         }
      } else {
         quads = nineSlice(x, y, w, h, u0, v0, uw, vh, slice, texture.texelsPerUnitX(), texture.texelsPerUnitY());
      }

      if (clipRatio > 0.0F) {
         quads = clip(quads, visible(x, y, w, h, clipRatio, clipDirection));
      }

      return quads;
   }

   public static List<UiDrawCommand.Quad> nineSlice(
      float x, float y, float w, float h, float u0, float v0, float uw, float vh, float[] slice, float texelsX, float texelsY
   ) {
      float left = slice[0];
      float top = slice[1];
      float right = slice[2];
      float bottom = slice[3];
      if (left + right > w && left + right > 0.0F) {
         float factor = w / (left + right);
         left *= factor;
         right *= factor;
      }

      if (top + bottom > h && top + bottom > 0.0F) {
         float factor = h / (top + bottom);
         top *= factor;
         bottom *= factor;
      }

      float[] xs = new float[]{x, x + left, x + w - right, x + w};
      float[] ys = new float[]{y, y + top, y + h - bottom, y + h};
      float[] us = new float[]{u0, u0 + slice[0] * texelsX, u0 + uw - slice[2] * texelsX, u0 + uw};
      float[] vs = new float[]{v0, v0 + slice[1] * texelsY, v0 + vh - slice[3] * texelsY, v0 + vh};
      List<UiDrawCommand.Quad> quads = new ArrayList<>(9);

      for (int row = 0; row < 3; row++) {
         for (int column = 0; column < 3; column++) {
            float qw = xs[column + 1] - xs[column];
            float qh = ys[row + 1] - ys[row];
            if (qw > 0.0F && qh > 0.0F) {
               float[] u = texels(us[column], us[column + 1]);
               float[] v = texels(vs[row], vs[row + 1]);
               quads.add(new UiDrawCommand.Quad(xs[column], ys[row], qw, qh, u[0], v[0], u[1], v[1]));
            }
         }
      }

      return quads;
   }

   private static float[] texels(float from, float to) {
      if (to >= from) {
         return new float[]{from, to};
      }

      float middle = (from + to) / 2.0F;
      return new float[]{middle, middle};
   }

   private static List<UiDrawCommand.Quad> tiled(
      float x, float y, float w, float h, float u0, float v0, float uw, float vh, UiTextureInfo texture, String mode, float[] scale
   ) {
      boolean tileX = !mode.equals("y");
      boolean tileY = !mode.equals("x");
      float tileW = tileX ? uw / texture.texelsPerUnitX() * scale[0] : w;
      float tileH = tileY ? vh / texture.texelsPerUnitY() * scale[1] : h;
      if (!(tileW <= 0.0F) && !(tileH <= 0.0F) && !(w / tileW * (h / tileH) > 4096.0F)) {
         List<UiDrawCommand.Quad> quads = new ArrayList<>();

         for (float ty = 0.0F; ty < h; ty += tileH) {
            float qh = Math.min(tileH, h - ty);

            for (float tx = 0.0F; tx < w; tx += tileW) {
               float qw = Math.min(tileW, w - tx);
               quads.add(new UiDrawCommand.Quad(x + tx, y + ty, qw, qh, u0, v0, u0 + uw * qw / tileW, v0 + vh * qh / tileH));
            }
         }

         return quads;
      } else {
         return List.of(new UiDrawCommand.Quad(x, y, w, h, u0, v0, u0 + uw, v0 + vh));
      }
   }

   static float[] visible(float x, float y, float w, float h, float ratio, String direction) {
      return switch (direction) {
         case "right" -> new float[]{x + w * ratio, y, w * (1.0F - ratio), h};
         case "up" -> new float[]{x, y, w, h * (1.0F - ratio)};
         case "down" -> new float[]{x, y + h * ratio, w, h * (1.0F - ratio)};
         case "center" -> new float[]{x + w * ratio / 2.0F, y, w * (1.0F - ratio), h};
         default -> new float[]{x, y, w * (1.0F - ratio), h};
      };
   }

   static List<UiDrawCommand.Quad> clip(List<UiDrawCommand.Quad> quads, float[] area) {
      List<UiDrawCommand.Quad> clipped = new ArrayList<>(quads.size());

      for (UiDrawCommand.Quad quad : quads) {
         float left = Math.max(quad.x(), area[0]);
         float top = Math.max(quad.y(), area[1]);
         float right = Math.min(quad.x() + quad.w(), area[0] + area[2]);
         float bottom = Math.min(quad.y() + quad.h(), area[1] + area[3]);
         if (!(right <= left) && !(bottom <= top)) {
            float du = (quad.u1() - quad.u0()) / quad.w();
            float dv = (quad.v1() - quad.v0()) / quad.h();
            clipped.add(
               new UiDrawCommand.Quad(
                  left,
                  top,
                  right - left,
                  bottom - top,
                  quad.u0() + (left - quad.x()) * du,
                  quad.v0() + (top - quad.y()) * dv,
                  quad.u0() + (right - quad.x()) * du,
                  quad.v0() + (bottom - quad.y()) * dv
               )
            );
         }
      }

      return clipped;
   }
}
