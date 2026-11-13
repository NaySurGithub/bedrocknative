package net.bedrocknative.ui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.content.ArgbImage;
import net.bedrocknative.content.BedrockImages;
import net.bedrocknative.content.PackStack;
import org.jspecify.annotations.Nullable;

public final class MojanglesFont implements UiVectorFont {
   static final String JSON = "font/default8.json";
   private static final String[] PAGES = new String[]{"font/default8.png", "font/default8_1.png"};
   private static final int CELLS = 256;
   private static final int MAX_SIDE = 1024;
   private static final float UNIT = 8.0F;
   private static final float BASELINE = 1.0F;
   private final MojanglesFont.Cell[] cells = new MojanglesFont.Cell[256];
   private final int texels;
   private final float baselineRow;

   private MojanglesFont(int texels, float baselineRow) {
      this.texels = texels;
      this.baselineRow = baselineRow;
   }

   @Nullable
   static MojanglesFont load(PackStack stack) {
      List<PackStack.PackFile> json = stack.readAll("font/default8.json");
      if (json.isEmpty()) {
         return null;
      } else {
         PackStack.PackFile top = json.get(json.size() - 1);
         List<ArgbImage> pages = new ArrayList<>();

         for (String path : PAGES) {
            ArgbImage image = null;

            for (PackStack.PackFile copy : stack.readAll(path)) {
               if (copy.pack() == top.pack() && copy.data().length > 0) {
                  image = decode(copy.data());
               }
            }

            if (image == null) {
               break;
            }

            pages.add(image);
         }

         MojanglesFont font = of(top.data(), pages);
         if (font == null) {
            BedrockNative.LOGGER.info("JSON UI: {} found but its {} page(s) give no font; MinecraftTen stands in", "font/default8.json", pages.size());
         }

         return font;
      }
   }

   @Nullable
   static MojanglesFont of(byte[] json, List<ArgbImage> pages) {
      int cellHeight;
      int yOffset;
      try {
         JsonObject root = UiJson.parseObject(json);
         cellHeight = number(root.get("ascii_character_height"));
         yOffset = number(root.get("ascii_character_y_offset"));
      } catch (RuntimeException | UiJson.Error var12) {
         return null;
      }

      if (!pages.isEmpty() && cellHeight >= 8 && cellHeight <= 64 && yOffset >= -64 && yOffset <= 64) {
         int cellWidth = pages.get(0).width() / 16;
         if (cellWidth >= 8 && cellWidth % 8 == 0) {
            MojanglesFont font = new MojanglesFont(cellWidth / 8, 7.0F - yOffset);
            int rowHeight = cellHeight * font.texels;
            int index = 0;

            for (ArgbImage page : pages) {
               if (page.width() != pages.get(0).width()) {
                  break;
               }

               for (int row = 0; row < page.height() / rowHeight; row++) {
                  for (int column = 0; column < 16 && index < 256; index++) {
                     font.cells[index] = cell(page, column * cellWidth, row * rowHeight, cellWidth, rowHeight);
                     column++;
                  }
               }
            }

            return font.cell(65) == null ? null : font;
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   private static int number(@Nullable JsonElement value) {
      return value instanceof JsonPrimitive primitive && primitive.isNumber() ? primitive.getAsInt() : 0;
   }

   @Nullable
   private static ArgbImage decode(byte[] png) {
      try {
         ArgbImage image = BedrockImages.decode(png);
         return image.width() >= 128 && image.width() <= 2048 && image.height() <= 2048 ? image : null;
      } catch (RuntimeException | IOException var2) {
         return null;
      }
   }

   @Nullable
   private static MojanglesFont.Cell cell(ArgbImage page, int u, int v, int width, int height) {
      int left = width;
      int right = -1;
      int top = height;
      int bottom = -1;

      for (int y = 0; y < height; y++) {
         for (int x = 0; x < width; x++) {
            if (page.argb()[(v + y) * page.width() + u + x] >>> 24 != 0) {
               left = Math.min(left, x);
               right = Math.max(right, x);
               top = Math.min(top, y);
               bottom = y;
            }
         }
      }

      return right < 0 ? null : new MojanglesFont.Cell(page, u + left, v + top, right - left + 1, bottom - top + 1, top);
   }

   @Nullable
   private MojanglesFont.Cell cell(int codePoint) {
      int index = BitmapFontMetrics.default8Cell(codePoint);
      return index < 0 ? null : this.cells[index];
   }

   private static boolean blank(int codePoint) {
      return codePoint == 32 || Character.getType(codePoint) == 12;
   }

   @Override
   public String name() {
      return "Mojangles";
   }

   @Override
   public boolean has(int codePoint) {
      return blank(codePoint) || this.cell(codePoint) != null;
   }

   @Override
   public float advance(int codePoint) {
      if (blank(codePoint)) {
         return 0.5F;
      } else {
         MojanglesFont.Cell cell = this.cell(codePoint);
         return cell == null ? 0.0F : ((float)cell.width() / this.texels + 1.0F) / 8.0F;
      }
   }

   @Override
   public float lineHeight() {
      return 1.25F;
   }

   @Override
   public float baseline() {
      return 1.0F;
   }

   @Override
   public UiVectorFont.Glyph rasterize(int codePoint, float pixels) {
      MojanglesFont.Cell cell = blank(codePoint) ? null : this.cell(codePoint);
      if (cell != null && !(pixels <= 0.0F)) {
         float perFontPx = pixels / 8.0F;
         float perTexel = perFontPx / this.texels;
         int width = Math.max(1, Math.round(cell.width() * perTexel));
         int height = Math.max(1, Math.round(cell.height() * perTexel));
         if (width <= 1024 && height <= 1024) {
            int[] source = cell.page().argb();
            int stride = cell.page().width();
            int[] argb = new int[width * height];

            for (int y = 0; y < height; y++) {
               int row = (cell.v() + Math.min(cell.height() - 1, (int)((y + 0.5F) / perTexel))) * stride + cell.u();

               for (int x = 0; x < width; x++) {
                  argb[y * width + x] = source[row + Math.min(cell.width() - 1, (int)((x + 0.5F) / perTexel))] & 0xFF000000 | 16777215;
               }
            }

            return new UiVectorFont.Glyph(width, height, 0, Math.round(((float)cell.top() / this.texels - this.baselineRow) * perFontPx), argb);
         } else {
            return UiVectorFont.Glyph.EMPTY;
         }
      } else {
         return UiVectorFont.Glyph.EMPTY;
      }
   }

   private record Cell(ArgbImage page, int u, int v, int width, int height, int top) {
   }
}
