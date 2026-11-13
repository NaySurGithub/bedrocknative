package net.bedrocknative.client.oreui;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.bedrocknative.oreui.OreFont;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

final class OreGlyphs {
   static final int SIZE = 512;
   private static final int MAX_PAGES = 16;
   private static final AtomicInteger INSTANCES = new AtomicInteger();
   private final int instance = INSTANCES.incrementAndGet();
   private final List<OreGlyphs.Page> pages = new ArrayList<>();
   private final Map<OreGlyphs.Key, OreGlyphs.Placed> glyphs = new HashMap<>();

   OreGlyphs.Placed glyph(OreFont font, float size, int codePoint) {
      OreGlyphs.Key key = new OreGlyphs.Key(font.name(), size, codePoint);
      OreGlyphs.Placed placed = this.glyphs.get(key);
      if (placed == null) {
         placed = this.place(font.rasterize(codePoint, size));
         this.glyphs.put(key, placed);
      }

      return placed;
   }

   private OreGlyphs.Placed place(OreFont.Glyph glyph) {
      if (glyph.width() > 0 && glyph.height() > 0 && glyph.width() <= 512 && glyph.height() <= 512) {
         OreGlyphs.Page page = this.pages.isEmpty() ? null : this.pages.getLast();
         if (page != null && page.x + glyph.width() > 512) {
            page.x = 0;
            page.y = page.y + page.rowHeight + 1;
            page.rowHeight = 0;
         }

         if (page == null || page.y + glyph.height() > 512) {
            if (this.pages.size() >= 16) {
               return new OreGlyphs.Placed(null, 0, 0, 0, 0, 0, 0);
            }

            page = this.newPage();
         }

         NativeImage image = page.texture.getPixels();
         if (image == null) {
            return new OreGlyphs.Placed(null, 0, 0, 0, 0, 0, 0);
         }

         for (int y = 0; y < glyph.height(); y++) {
            for (int x = 0; x < glyph.width(); x++) {
               image.setPixel(page.x + x, page.y + y, glyph.argb()[y * glyph.width() + x]);
            }
         }

         OreGlyphs.Placed placed = new OreGlyphs.Placed(page.id, page.x, page.y, glyph.width(), glyph.height(), glyph.left(), glyph.top());
         page.x = page.x + glyph.width() + 1;
         page.rowHeight = Math.max(page.rowHeight, glyph.height());
         page.dirty = true;
         return placed;
      } else {
         return new OreGlyphs.Placed(null, 0, 0, 0, 0, 0, 0);
      }
   }

   private OreGlyphs.Page newPage() {
      Identifier id = Identifier.fromNamespaceAndPath("bedrocknative", "oreui/glyphs_" + this.instance + "_" + this.pages.size());
      DynamicTexture texture = new DynamicTexture(() -> "Ore UI glyphs " + this.pages.size(), 512, 512, true);
      Minecraft.getInstance().getTextureManager().register(id, texture);
      OreGlyphs.Page page = new OreGlyphs.Page(id, texture);
      this.pages.add(page);
      return page;
   }

   void upload() {
      for (OreGlyphs.Page page : this.pages) {
         if (page.dirty) {
            page.texture.upload();
            page.dirty = false;
         }
      }
   }

   void release() {
      for (OreGlyphs.Page page : this.pages) {
         Minecraft.getInstance().getTextureManager().release(page.id);
      }

      this.pages.clear();
      this.glyphs.clear();
   }

   private record Key(String font, float size, int codePoint) {
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

   record Placed(@Nullable Identifier page, int u, int v, int width, int height, int left, int top) {
   }
}
