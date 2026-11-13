package net.bedrocknative.oreui;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.jspecify.annotations.Nullable;

public final class OreImagePainter implements OrePainter {
   private final BufferedImage image;
   private final int[] pixels;
   private final int width;
   private final int height;
   private final OreInstall install;
   private final Map<Path, Optional<BufferedImage>> files = new HashMap<>();
   private final Map<String, OreFont.Glyph> glyphs = new HashMap<>();
   private final Deque<int[]> clips = new ArrayDeque<>();

   public OreImagePainter(int width, int height, OreInstall install, int background) {
      this.width = width;
      this.height = height;
      this.install = install;
      this.image = new BufferedImage(width, height, 2);
      this.pixels = new int[width * height];
      Arrays.fill(this.pixels, background);
   }

   public BufferedImage image() {
      this.image.setRGB(0, 0, this.width, this.height, this.pixels, 0, this.width);
      return this.image;
   }

   public void background(BufferedImage picture) {
      for (int y = 0; y < Math.min(this.height, picture.getHeight()); y++) {
         for (int x = 0; x < Math.min(this.width, picture.getWidth()); x++) {
            this.pixels[y * this.width + x] = picture.getRGB(x, y) | 0xFF000000;
         }
      }
   }

   private int[] clip() {
      int[] clip = this.clips.peek();
      return clip != null ? clip : new int[]{0, 0, this.width, this.height};
   }

   @Override
   public void pushClip(OreBox box) {
      int[] current = this.clip();
      this.clips
         .push(new int[]{Math.max(current[0], box.px0()), Math.max(current[1], box.py0()), Math.min(current[2], box.px1()), Math.min(current[3], box.py1())});
   }

   @Override
   public void popClip() {
      this.clips.poll();
   }

   @Override
   public void fill(OreBox box, int argb) {
      int[] clip = this.clip();
      int x0 = Math.max(clip[0], box.px0());
      int y0 = Math.max(clip[1], box.py0());
      int x1 = Math.min(clip[2], box.px1());
      int y1 = Math.min(clip[3], box.py1());

      for (int y = y0; y < y1; y++) {
         for (int x = x0; x < x1; x++) {
            this.blend(x, y, argb);
         }
      }
   }

   @Override
   public int @Nullable [] spriteSize(String sprite) {
      return this.install.sprite(sprite).map(found -> new int[]{found.width(), found.height()}).orElse(null);
   }

   @Override
   public void sprite(String sprite, float u, float v, float uw, float vh, OreBox dest, int argb) {
      Optional<OreInstall.Sprite> found = this.install.sprite(sprite);
      if (!found.isEmpty()) {
         OreInstall.Sprite s = found.get();
         Optional<BufferedImage> sheet = this.file(s.file());
         if (!sheet.isEmpty()) {
            this.draw(sheet.get(), s.x() + u, s.y() + v, uw, vh, dest, argb, false);
         }
      }
   }

   @Override
   public void mask(String sprite, float u, float v, float uw, float vh, OreBox dest, int argb) {
      Optional<OreInstall.Sprite> found = this.install.sprite(sprite);
      if (!found.isEmpty()) {
         OreInstall.Sprite s = found.get();
         this.file(s.file()).ifPresent(sheet -> this.draw(sheet, s.x() + u, s.y() + v, uw, vh, dest, argb, true));
      }
   }

   @Override
   public void picture(Path file, OreBox dest, int argb) {
      Optional<BufferedImage> picture = this.file(file);
      if (!picture.isEmpty() && !dest.empty()) {
         BufferedImage source = picture.get();
         float[] crop = cover(source.getWidth(), source.getHeight(), dest.w() / dest.h());
         this.draw(source, crop[0], crop[1], crop[2], crop[3], dest, argb, false);
      }
   }

   public static float[] cover(int width, int height, float aspect) {
      float w = width;
      float h = height;
      if (w / h > aspect) {
         float keep = h * aspect;
         return new float[]{(w - keep) / 2.0F, 0.0F, keep, h};
      } else {
         float keep = w / aspect;
         return new float[]{0.0F, (h - keep) / 2.0F, w, keep};
      }
   }

   private void draw(BufferedImage source, float u, float v, float uw, float vh, OreBox dest, int argb, boolean mask) {
      int[] clip = this.clip();
      int dx0 = dest.px0();
      int dy0 = dest.py0();
      int dx1 = dest.px1();
      int dy1 = dest.py1();
      if (dx1 > dx0 && dy1 > dy0 && !(uw <= 0.0F) && !(vh <= 0.0F)) {
         int tintA = argb >>> 24;
         int tintR = argb >> 16 & 0xFF;
         int tintG = argb >> 8 & 0xFF;
         int tintB = argb & 0xFF;

         for (int y = Math.max(dy0, clip[1]); y < Math.min(dy1, clip[3]); y++) {
            int sy = (int)Math.floor(v + (y - dy0 + 0.5F) * vh / (dy1 - dy0));
            sy = Math.clamp(sy, 0, source.getHeight() - 1);

            for (int x = Math.max(dx0, clip[0]); x < Math.min(dx1, clip[2]); x++) {
               int sx = (int)Math.floor(u + (x - dx0 + 0.5F) * uw / (dx1 - dx0));
               sx = Math.clamp(sx, 0, source.getWidth() - 1);
               int texel = source.getRGB(sx, sy);
               if (mask) {
                  texel = texel & 0xFF000000 | 16777215;
               }

               int a = (texel >>> 24) * tintA / 255;
               int r = (texel >> 16 & 0xFF) * tintR / 255;
               int g = (texel >> 8 & 0xFF) * tintG / 255;
               int b = (texel & 0xFF) * tintB / 255;
               this.blend(x, y, a << 24 | r << 16 | g << 8 | b);
            }
         }
      }
   }

   @Override
   public void glyph(OreFont font, float size, int codePoint, float x, float baseline, int argb) {
      String key = font.name() + "|" + size + "|" + codePoint;
      OreFont.Glyph glyph = this.glyphs.computeIfAbsent(key, k -> font.rasterize(codePoint, size));
      if (glyph.width() > 0) {
         int[] clip = this.clip();
         int x0 = Math.round(x) + glyph.left();
         int y0 = Math.round(baseline) + glyph.top();
         int alpha = argb >>> 24;

         for (int gy = 0; gy < glyph.height(); gy++) {
            int y = y0 + gy;
            if (y >= clip[1] && y < clip[3]) {
               for (int gx = 0; gx < glyph.width(); gx++) {
                  int px = x0 + gx;
                  if (px >= clip[0] && px < clip[2]) {
                     int coverage = glyph.argb()[gy * glyph.width() + gx] >>> 24;
                     if (coverage > 0) {
                        this.blend(px, y, coverage * alpha / 255 << 24 | argb & 16777215);
                     }
                  }
               }
            }
         }
      }
   }

   private void blend(int x, int y, int argb) {
      int a = argb >>> 24;
      if (a != 0 && x >= 0 && y >= 0 && x < this.width && y < this.height) {
         int index = y * this.width + x;
         if (a == 255) {
            this.pixels[index] = argb;
         } else {
            int below = this.pixels[index];
            int r = ((argb >> 16 & 0xFF) * a + (below >> 16 & 0xFF) * (255 - a)) / 255;
            int g = ((argb >> 8 & 0xFF) * a + (below >> 8 & 0xFF) * (255 - a)) / 255;
            int b = ((argb & 0xFF) * a + (below & 0xFF) * (255 - a)) / 255;
            int outA = Math.min(255, a + (below >>> 24) * (255 - a) / 255);
            this.pixels[index] = outA << 24 | r << 16 | g << 8 | b;
         }
      }
   }

   private Optional<BufferedImage> file(Path file) {
      return this.files
         .computeIfAbsent(
            file,
            key -> {
               try {
                  Optional<byte[]> painted = this.install.generated(key);
                  return painted.isPresent()
                     ? Optional.ofNullable(ImageIO.read(new ByteArrayInputStream(painted.get())))
                     : Optional.ofNullable(ImageIO.read(key.toFile()));
               } catch (IOException e) {
                  return Optional.empty();
               }
            }
         );
   }
}
