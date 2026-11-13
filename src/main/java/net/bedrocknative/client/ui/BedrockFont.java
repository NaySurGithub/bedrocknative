package net.bedrocknative.client.ui;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.bedrocknative.client.content.BedrockTextures;
import net.bedrocknative.content.JavaLatinFont;
import net.bedrocknative.content.PackStack;
import net.bedrocknative.ui.BitmapFontMetrics;
import net.bedrocknative.ui.UiFonts;
import net.bedrocknative.ui.UiTextMetrics;
import net.bedrocknative.ui.UiVectorFont;
import org.jspecify.annotations.Nullable;

final class BedrockFont {
   private static final int MAX_ATLASES = 12;
   private final BitmapFontMetrics bitmap;
   private final UiTextMetrics metrics;
   private final Map<Integer, Optional<BedrockTextures.Texture>> textures = new HashMap<>();
   private final Optional<BedrockTextures.Texture>[] pageTextures = new Optional[257];
   private final Map<BedrockFont.AtlasKey, GlyphAtlas> atlases = new LinkedHashMap<>(16, 0.75F, true);

   private BedrockFont(UiFonts fonts) {
      boolean standIn = PackStack.active().locateTexture("font/default8.png").map(located -> located.pack() == JavaLatinFont.INSTANCE).orElse(false);
      this.bitmap = new BitmapFontMetrics(index -> BedrockTextures.pixels(path(index)).orElse(null)).latinStandIn(standIn);
      this.metrics = new UiTextMetrics(this.bitmap, fonts);
   }

   static BedrockFont load(UiFonts fonts) {
      return new BedrockFont(fonts);
   }

   static String path(int page) {
      return page == 0 ? "font/default8.png" : String.format(Locale.ROOT, "font/glyph_%02X.png", page == 256 ? 0 : page);
   }

   BitmapFontMetrics bitmap() {
      return this.bitmap;
   }

   UiTextMetrics metrics() {
      return this.metrics;
   }

   @Nullable
   BedrockTextures.Texture texture(int page) {
      if (page >= 0 && page < this.pageTextures.length) {
         Optional<BedrockTextures.Texture> known = this.pageTextures[page];
         if (known == null) {
            known = BedrockTextures.pack(path(page));
            this.pageTextures[page] = known;
         }

         return known.orElse(null);
      } else {
         return this.textures.computeIfAbsent(page, index -> BedrockTextures.pack(path(index))).orElse(null);
      }
   }

   GlyphAtlas atlas(UiVectorFont font, int pixels) {
      BedrockFont.AtlasKey key = new BedrockFont.AtlasKey(font, pixels);
      GlyphAtlas atlas = this.atlases.get(key);
      if (atlas == null) {
         atlas = new GlyphAtlas(font, pixels);
         this.atlases.put(key, atlas);
         if (this.atlases.size() > 12) {
            Iterator<GlyphAtlas> oldest = this.atlases.values().iterator();
            oldest.next().close();
            oldest.remove();
         }
      }

      return atlas;
   }

   void upload() {
      this.atlases.values().forEach(GlyphAtlas::upload);
   }

   void close() {
      this.atlases.values().forEach(GlyphAtlas::close);
      this.atlases.clear();
   }

   private record AtlasKey(UiVectorFont font, int pixels) {
   }
}
