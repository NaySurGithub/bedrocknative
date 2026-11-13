package net.bedrocknative.client.oreui;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import net.bedrocknative.oreui.OreBox;
import net.bedrocknative.oreui.OreFont;
import net.bedrocknative.oreui.OreImagePainter;
import net.bedrocknative.oreui.OreInstall;
import net.bedrocknative.oreui.OrePainter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import org.jspecify.annotations.Nullable;

final class OreGuiPainter implements OrePainter, AutoCloseable {
   private static final int SUB = 16;
   private final GuiGraphicsExtractor graphics;
   private final OreInstall install;
   private final OreTextures textures;
   private final OreGlyphs glyphs;
   private final Deque<int[]> clips = new ArrayDeque<>();

   OreGuiPainter(GuiGraphicsExtractor graphics, OreInstall install, OreTextures textures, OreGlyphs glyphs) {
      this.graphics = graphics;
      this.install = install;
      this.textures = textures;
      this.glyphs = glyphs;
      float guiScale = Minecraft.getInstance().getWindow().getGuiScale();
      graphics.pose().pushMatrix();
      graphics.pose().scale(1.0F / guiScale, 1.0F / guiScale);
   }

   @Override
   public void close() {
      while (!this.clips.isEmpty()) {
         this.popClip();
      }

      this.glyphs.upload();
      this.graphics.pose().popMatrix();
   }

   @Override
   public void fill(OreBox box, int argb) {
      int x0 = box.px0();
      int y0 = box.py0();
      int x1 = box.px1();
      int y1 = box.py1();
      if (x1 > x0 && y1 > y0) {
         this.graphics.fill(x0, y0, x1, y1, argb);
      }
   }

   @Nullable
   @Override
   public int[] spriteSize(String sprite) {
      return this.install.sprite(sprite).map(found -> new int[]{found.width(), found.height()}).orElse(null);
   }

   @Override
   public void sprite(String sprite, float u, float v, float uw, float vh, OreBox dest, int argb) {
      Optional<OreInstall.Sprite> found = this.install.sprite(sprite);
      if (!found.isEmpty()) {
         OreInstall.Sprite s = found.get();
         this.textures.get(s.file()).ifPresent(texture -> this.blit(texture, s.x() + u, s.y() + v, uw, vh, dest, argb));
      }
   }

   @Override
   public void mask(String sprite, float u, float v, float uw, float vh, OreBox dest, int argb) {
      Optional<OreInstall.Sprite> found = this.install.sprite(sprite);
      if (!found.isEmpty()) {
         OreInstall.Sprite s = found.get();
         this.textures.mask(s.file()).ifPresent(texture -> this.blit(texture, s.x() + u, s.y() + v, uw, vh, dest, argb));
      }
   }

   @Override
   public void picture(Path file, OreBox dest, int argb) {
      if (!dest.empty()) {
         this.textures.get(file).ifPresent(texture -> {
            float[] crop = OreImagePainter.cover(texture.width(), texture.height(), dest.w() / dest.h());
            this.blit(texture, crop[0], crop[1], crop[2], crop[3], dest, argb);
         });
      }
   }

   private void blit(OreTextures.Texture texture, float u, float v, float uw, float vh, OreBox dest, int argb) {
      int x0 = dest.px0();
      int y0 = dest.py0();
      int x1 = dest.px1();
      int y1 = dest.py1();
      if (x1 > x0 && y1 > y0 && !(uw <= 0.0F) && !(vh <= 0.0F)) {
         this.graphics
            .blit(
               RenderPipelines.GUI_TEXTURED,
               texture.id(),
               x0,
               y0,
               u * 16.0F,
               v * 16.0F,
               x1 - x0,
               y1 - y0,
               Math.max(1, Math.round(uw * 16.0F)),
               Math.max(1, Math.round(vh * 16.0F)),
               texture.width() * 16,
               texture.height() * 16,
               argb
            );
      }
   }

   @Override
   public void glyph(OreFont font, float size, int codePoint, float x, float baseline, int argb) {
      OreGlyphs.Placed glyph = this.glyphs.glyph(font, size, codePoint);
      if (glyph.page() != null) {
         int x0 = Math.round(x) + glyph.left();
         int y0 = Math.round(baseline) + glyph.top();
         this.graphics
            .blit(
               RenderPipelines.GUI_TEXTURED,
               glyph.page(),
               x0,
               y0,
               glyph.u() * 16,
               glyph.v() * 16,
               glyph.width(),
               glyph.height(),
               glyph.width() * 16,
               glyph.height() * 16,
               8192,
               8192,
               argb
            );
      }
   }

   @Override
   public void pushClip(OreBox box) {
      int[] current = this.clips.peek();
      int x0 = box.px0();
      int y0 = box.py0();
      int x1 = box.px1();
      int y1 = box.py1();
      if (current != null) {
         x0 = Math.max(x0, current[0]);
         y0 = Math.max(y0, current[1]);
         x1 = Math.min(x1, current[2]);
         y1 = Math.min(y1, current[3]);
      }

      x1 = Math.max(x0, x1);
      y1 = Math.max(y0, y1);
      this.clips.push(new int[]{x0, y0, x1, y1});
      this.graphics.enableScissor(x0, y0, x1, y1);
   }

   @Override
   public void popClip() {
      if (this.clips.poll() != null) {
         this.graphics.disableScissor();
      }
   }
}
