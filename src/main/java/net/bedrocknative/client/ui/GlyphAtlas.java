package net.bedrocknative.client.ui;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.bedrocknative.ui.UiVectorFont;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

final class GlyphAtlas {
   static final int SIZE = 512;
   private static final int MAX_PAGES = 8;
   private final UiVectorFont font;
   private final int pixels;
   private final List<GlyphAtlas.Page> pages = new ArrayList<>();
   private final Map<Integer, GlyphAtlas.Placed> glyphs = new HashMap<>();

   GlyphAtlas(UiVectorFont font, int pixels) {
      this.font = font;
      this.pixels = pixels;
   }

   GlyphAtlas.Placed glyph(int codePoint) {
      GlyphAtlas.Placed placed = this.glyphs.get(codePoint);
      if (placed == null) {
         placed = this.place(this.font.rasterize(codePoint, this.pixels));
         this.glyphs.put(codePoint, placed);
      }

      return placed;
   }

   private GlyphAtlas.Placed place(UiVectorFont.Glyph glyph) {
      if (glyph.width() > 0 && glyph.height() > 0 && glyph.width() <= 512 && glyph.height() <= 512) {
         GlyphAtlas.Page page = this.pages.isEmpty() ? null : this.pages.getLast();
         if (page != null && page.x + glyph.width() > 512) {
            page.x = 0;
            page.y = page.y + page.rowHeight + 1;
            page.rowHeight = 0;
         }

         if (page == null || page.y + glyph.height() > 512) {
            if (this.pages.size() >= 8) {
               return new GlyphAtlas.Placed(null, 0, 0, 0, 0, 0, 0);
            }

            page = this.newPage();
         }

         NativeImage image = page.texture.getPixels();

         for (int y = 0; y < glyph.height(); y++) {
            for (int x = 0; x < glyph.width(); x++) {
               image.setPixel(page.x + x, page.y + y, glyph.argb()[y * glyph.width() + x]);
            }
         }

         GlyphAtlas.Placed placed = new GlyphAtlas.Placed(page.id, page.x, page.y, glyph.width(), glyph.height(), glyph.left(), glyph.top());
         page.x = page.x + glyph.width() + 1;
         page.rowHeight = Math.max(page.rowHeight, glyph.height());
         page.dirty = true;
         return placed;
      } else {
         return new GlyphAtlas.Placed(null, 0, 0, 0, 0, 0, 0);
      }
   }

   private GlyphAtlas.Page newPage() {
      String name = this.font.name().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
      Identifier id = Identifier.fromNamespaceAndPath("bedrocknative", "bedrock/font/" + name + "_" + this.pixels + "_" + this.pages.size());
      DynamicTexture texture = new DynamicTexture(() -> "Bedrock font " + this.font.name() + " " + this.pixels + "px", 512, 512, true);
      Minecraft.getInstance().getTextureManager().register(id, texture);
      GlyphAtlas.Page page = new GlyphAtlas.Page(id, texture);
      this.pages.add(page);
      return page;
   }

   void upload() {
      for (GlyphAtlas.Page page : this.pages) {
         if (page.dirty) {
            page.texture.upload();
            page.dirty = false;
         }
      }
   }

   void close() {
      for (GlyphAtlas.Page page : this.pages) {
         Minecraft.getInstance().getTextureManager().release(page.id);
      }

      this.pages.clear();
      this.glyphs.clear();
   }

   private static final class Page {
      final Identifier id;
      final DynamicTexture texture;
      int x;
      int y;
      int rowHeight;
      boolean dirty;

      Page(Identifier id, DynamicTexture texture) {
         this.id = id;
         this.texture = texture;
      }
   }

   record Placed(@Nullable Identifier texture, int u, int v, int width, int height, int left, int top) {
   }
}
