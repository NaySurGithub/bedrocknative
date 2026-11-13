package net.bedrocknative.oreui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.content.ArgbImage;
import net.bedrocknative.content.BedrockImages;
import org.jspecify.annotations.Nullable;

public final class JavaFontPages implements OreFont.PixelFace {
   static final String DEFINITION = "assets/minecraft/font/include/default.json";
   private static final int MAX_BYTES = 4194304;
   static final int SPACE_ADVANCE = 3;
   private final Map<Integer, OreFont.PixelGlyph> glyphs;

   private JavaFontPages(Map<Integer, OreFont.PixelGlyph> glyphs) {
      this.glyphs = Map.copyOf(glyphs);
   }

   public static @Nullable JavaFontPages load() {
      return read(resource -> {
         try (InputStream in = JavaFontPages.class.getClassLoader().getResourceAsStream(resource)) {
            return in == null ? null : in.readNBytes(4194304);
         } catch (IOException e) {
            return null;
         }
      });
   }

   static @Nullable JavaFontPages read(Function<String, byte @Nullable []> resources) {
      byte[] definition = resources.apply("assets/minecraft/font/include/default.json");
      if (definition == null) {
         return null;
      }

      Map<Integer, OreFont.PixelGlyph> glyphs = new HashMap<>();

      try {
         if (!(
            JsonParser.parseString(new String(definition, StandardCharsets.UTF_8)) instanceof JsonObject object
               && object.get("providers") instanceof JsonArray providers
         )) {
            return null;
         }

         for (JsonElement element : providers) {
            if (element instanceof JsonObject provider && "bitmap".equals(string(provider, "type"))) {
               provider(provider, resources, glyphs);
            }
         }
      } catch (RuntimeException e) {
         BedrockNative.LOGGER.warn("Own menus: Java's font definition couldn't be read", e);
         return null;
      }

      if (!glyphs.containsKey(65)) {
         return null;
      }

      OreFont.PixelGlyph space = new OreFont.PixelGlyph(0, 0, 0, 3, new byte[0]);
      glyphs.put(32, space);
      glyphs.put(160, space);
      return new JavaFontPages(glyphs);
   }

   private static void provider(JsonObject provider, Function<String, byte @Nullable []> resources, Map<Integer, OreFont.PixelGlyph> glyphs) {
      String file = string(provider, "file");
      if (file != null && provider.get("chars") instanceof JsonArray rows && !rows.isEmpty()) {
         String path = file.contains(":") ? file.substring(file.indexOf(58) + 1) : file;
         if (!path.contains("..")) {
            byte[] bytes = resources.apply("assets/minecraft/textures/" + path);
            if (bytes != null) {
               ArgbImage page;
               try {
                  page = BedrockImages.decode(bytes);
               } catch (IOException | RuntimeException e) {
                  return;
               }

               int columns = rows.get(0).getAsString().codePointCount(0, rows.get(0).getAsString().length());
               if (columns > 0) {
                  int cellWidth = page.width() / columns;
                  int cellHeight = page.height() / rows.size();
                  int height = provider.has("height") ? provider.get("height").getAsInt() : 8;
                  int ascent = provider.get("ascent").getAsInt();
                  if (cellWidth > 0 && cellHeight > 0 && height == cellHeight) {
                     for (int row = 0; row < rows.size(); row++) {
                        int[] codePoints = rows.get(row).getAsString().codePoints().toArray();

                        for (int column = 0; column < codePoints.length && column < columns; column++) {
                           int codePoint = codePoints[column];
                           if (codePoint != 0 && codePoint != 32 && !glyphs.containsKey(codePoint)) {
                              OreFont.PixelGlyph glyph = glyph(page, column * cellWidth, row * cellHeight, cellWidth, cellHeight, ascent);
                              if (glyph != null) {
                                 glyphs.put(codePoint, glyph);
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static OreFont.@Nullable PixelGlyph glyph(ArgbImage page, int x0, int y0, int cellWidth, int cellHeight, int ascent) {
      int width = 0;

      for (int x = cellWidth - 1; x >= 0 && width == 0; x--) {
         for (int y = 0; y < cellHeight; y++) {
            if (page.pixel(x0 + x, y0 + y) >>> 24 != 0) {
               width = x + 1;
               break;
            }
         }
      }

      if (width == 0) {
         return null;
      }

      byte[] alpha = new byte[width * cellHeight];

      for (int y = 0; y < cellHeight; y++) {
         for (int x = 0; x < width; x++) {
            alpha[y * width + x] = (byte)(page.pixel(x0 + x, y0 + y) >>> 24);
         }
      }

      return new OreFont.PixelGlyph(width, cellHeight, ascent, width + 1, alpha);
   }

   private static @Nullable String string(JsonObject object, String key) {
      JsonElement value = object.get(key);
      return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
   }

   @Override
   public OreFont.@Nullable PixelGlyph glyph(int codePoint) {
      return this.glyphs.get(codePoint);
   }

   public int size() {
      return this.glyphs.size();
   }

   public OreFont.PixelFace titles() {
      Map<Integer, OreFont.PixelGlyph> bold = new ConcurrentHashMap<>();
      return codePoint -> {
         int shown = Character.toUpperCase(codePoint);
         OreFont.PixelGlyph cached = bold.get(shown);
         if (cached != null) {
            return cached;
         }

         OreFont.PixelGlyph plain = this.glyphs.get(shown);
         if (plain == null) {
            plain = this.glyphs.get(codePoint);
         }

         if (plain == null) {
            return null;
         }

         OreFont.PixelGlyph made = embolden(plain);
         if (bold.size() < 4096) {
            bold.put(shown, made);
         }

         return made;
      };
   }

   static OreFont.PixelGlyph embolden(OreFont.PixelGlyph plain) {
      if (plain.width() == 0) {
         return new OreFont.PixelGlyph(0, 0, 0, plain.advance() + 1, new byte[0]);
      }

      int width = plain.width() + 1;
      byte[] alpha = new byte[width * plain.height()];

      for (int y = 0; y < plain.height(); y++) {
         for (int x = 0; x < width; x++) {
            alpha[y * width + x] = (byte)Math.max(plain.coverage(x, y), plain.coverage(x - 1, y));
         }
      }

      return new OreFont.PixelGlyph(width, plain.height(), plain.top(), plain.advance() + 1, alpha);
   }

   @Override
   public String toString() {
      return "java-font-pages(" + this.glyphs.size() + " glyphs)".toLowerCase(Locale.ROOT);
   }
}
