package net.bedrocknative.client.text;

import com.mojang.blaze3d.font.GlyphBitmap;
import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.blaze3d.font.UnbakedGlyph;
import com.mojang.blaze3d.font.UnbakedGlyph.Stitcher;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.NativeImage.Format;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.GpuTexture;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.compat.LunarCompat;
import net.bedrocknative.client.content.BedrockTextures;
import net.bedrocknative.client.mixin.render.FontManagerAccessor;
import net.bedrocknative.client.mixin.render.FontSetInvoker;
import net.bedrocknative.client.mixin.render.MinecraftFontAccessor;
import net.bedrocknative.content.PackStack;
import net.bedrocknative.text.GlyphPages;
import net.bedrocknative.ui.BitmapFontMetrics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.font.FontManager;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.gui.font.glyphs.EmptyGlyph;
import org.jspecify.annotations.Nullable;

public final class BedrockGlyphs {
   public static final GlyphProvider PROVIDER = new BedrockGlyphs.Provider();
   public static final GlyphProvider LATIN_PROVIDER = new BedrockGlyphs.LatinProvider();
   private static final int MAX_CELL = 128;
   private static final boolean TRACE = Boolean.getBoolean("bedrocknative.glyphTrace");
   private static final Set<String> TRACED = ConcurrentHashMap.newKeySet();
   @Nullable
   private static volatile BedrockGlyphs.State state;
   private static volatile boolean session;
   private static volatile int generation;
   private static volatile boolean bakedPagesLeft;

   private BedrockGlyphs() {
   }

   public static boolean tracing() {
      return TRACE;
   }

   public static void trace(String source, int codePoint, BitmapFontMetrics.Page page, BitmapFontMetrics.Glyph glyph) {
      if (TRACE && GlyphPages.privateUse(codePoint >> 8) && TRACED.size() <= 400 && TRACED.add(source + codePoint)) {
         int[] rows = page.opaqueRows(codePoint & 0xFF);
         BedrockNative.LOGGER
            .info(
               "Bedrock glyph trace [{}] U+{}: page {}x{} (cell {}), opaque columns {} wide from column {}, opaque rows {}, advance {}, scale {}",
               new Object[]{
                  source,
                  String.format("%04X", codePoint),
                  page.width(),
                  page.height(),
                  page.cellHeight(),
                  glyph.width(),
                  glyph.u() - codePoint % 16 * page.cellWidth(),
                  rows == null ? "none" : rows[0] + ".." + rows[1],
                  glyph.advance(),
                  glyph.scale()
               }
            );
      }
   }

   public static void start() {
      session = true;
      rebuild();
   }

   public static void packsChanged() {
      if (session) {
         rebuild();
      }
   }

   public static void clear() {
      BedrockGlyphs.State previous = state;
      boolean was = session || previous != null;
      session = false;
      if (was) {
         if (previous != null && !previous.latin) {
            state = null;
            generation++;
            bakedPagesLeft = true;
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.isSameThread()) {
               previous.close();
            } else {
               minecraft.execute(previous::close);
            }
         } else {
            replace(null);
         }
      }
   }

   public static void restoreForJavaWorld() {
      if (bakedPagesLeft && !session && state == null) {
         replace(null);
      }
   }

   public static int generation() {
      return generation;
   }

   public static boolean active() {
      return state != null;
   }

   public static boolean bakedPagesLeft() {
      return bakedPagesLeft;
   }

   public static Set<Integer> pages() {
      BedrockGlyphs.State current = state;
      return current == null ? Set.of() : current.pages;
   }

   public static float advance(int codePoint) {
      BedrockGlyphs.State current = state;
      BitmapFontMetrics.Glyph glyph = current != null && current.pages.contains(codePoint >> 8) ? current.metrics.pageGlyph(codePoint) : null;
      return glyph == null ? Float.NaN : glyph.advance();
   }

   private static void rebuild() {
      try {
         PackStack stack = PackStack.active();
         replace(
            new BedrockGlyphs.State(GlyphPages.collect(stack, PackStack.serverPackCount()), GlyphPages.serverLatinFont(stack, PackStack.serverPackCount()))
         );
      } catch (RuntimeException var1) {
         BedrockNative.LOGGER.warn("Couldn't read Bedrock's glyph pages", var1);
         replace(null);
      }
   }

   private static void replace(@Nullable BedrockGlyphs.State next) {
      BedrockGlyphs.State previous = state;
      state = next;
      generation++;
      bakedPagesLeft = false;
      if (next != null) {
         BedrockNative.LOGGER
            .info(
               "Bedrock glyph pages in Java's font: {}; Latin font: {}",
               next.pages.stream().map(page -> String.format("%02X", page)).toList(),
               next.latinSummary()
            );
      }

      Minecraft minecraft = Minecraft.getInstance();
      Runnable reload = () -> {
         FontManager fonts = ((MinecraftFontAccessor)minecraft).bedrocknative$fontManager();
         fonts.updateOptions(minecraft.options);
         if (LunarCompat.active()) {
            ((FontManagerAccessor)fonts).bedrocknative$fontSets().values().forEach(set -> ((FontSetInvoker)set).bedrocknative$resetTextures());
         }

         if (previous != null) {
            previous.close();
         }
      };
      if (minecraft.isSameThread()) {
         reload.run();
      } else {
         minecraft.execute(reload);
      }
   }

   private static final class LatinProvider implements GlyphProvider {
      @Nullable
      public UnbakedGlyph getGlyph(int codePoint) {
         BedrockGlyphs.State current = BedrockGlyphs.state;
         return current == null ? null : current.latinGlyph(codePoint);
      }

      public IntSet getSupportedGlyphs() {
         BedrockGlyphs.State current = BedrockGlyphs.state;
         IntSet supported = new IntOpenHashSet();
         if (current != null && current.latin) {
            synchronized (current.glyphs) {
               for (int codePoint : BitmapFontMetrics.default8CodePoints()) {
                  if (current.metrics.latinGlyph(codePoint) != null) {
                     supported.add(codePoint);
                  }
               }
            }
         }

         return supported;
      }
   }

   private record PageGlyph(BedrockGlyphs.State owner, BitmapFontMetrics.Page page, BitmapFontMetrics.Glyph glyph) implements UnbakedGlyph {
      public GlyphInfo info() {
         return GlyphInfo.simple(this.glyph.advance());
      }

      public BakedGlyph bake(Stitcher stitcher) {
         int cell = this.glyph.v() / this.page.cellHeight() * 16 + this.glyph.u() / this.page.cellWidth();
         int[] range = this.page.opaqueRows(cell);
         final int firstRow = range == null ? 0 : range[0];
         int rows = range == null ? this.glyph.height() : range[1] - range[0] + 1;
         final int step = Math.max(1, (Math.max(rows, this.glyph.width()) + 128 - 1) / 128);
         final int width = Math.max(1, (this.glyph.width() + step - 1) / step);
         final int height = Math.max(1, (rows + step - 1) / step);
         final float oversample = height / (rows * this.glyph.scale());
         final float bearingTop = 7.0F - (this.glyph.top() + firstRow * this.glyph.scale());
         return stitcher.stitch(this.info(), new GlyphBitmap() {
            {
               Objects.requireNonNull(PageGlyph.this);
            }

            public int getPixelWidth() {
               return width;
            }

            public int getPixelHeight() {
               return height;
            }

            public float getOversample() {
               return oversample;
            }

            public float getBearingTop() {
               return bearingTop;
            }

            public boolean isColored() {
               return true;
            }

            public void upload(int x, int y, GpuTexture texture) {
               NativeImage image = PageGlyph.this.owner.image(width, height);

               for (int row = 0; row < height; row++) {
                  for (int column = 0; column < width; column++) {
                     int u = Math.min(PageGlyph.this.glyph.u() + column * step, PageGlyph.this.page.width() - 1);
                     int v = Math.min(PageGlyph.this.glyph.v() + firstRow + row * step, PageGlyph.this.page.height() - 1);
                     image.setPixel(column, row, PageGlyph.this.page.argb()[v * PageGlyph.this.page.width() + u]);
                  }
               }

               RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture, image, 0, 0, x, y);
            }
         });
      }
   }

   private static final class Provider implements GlyphProvider {
      @Nullable
      public UnbakedGlyph getGlyph(int codePoint) {
         BedrockGlyphs.State current = BedrockGlyphs.state;
         return current == null ? null : current.glyph(codePoint);
      }

      public IntSet getSupportedGlyphs() {
         BedrockGlyphs.State current = BedrockGlyphs.state;
         IntSet supported = new IntOpenHashSet();
         if (current != null) {
            supported.add(10);

            for (int page : current.pages) {
               for (int cell = 0; cell < 256; cell++) {
                  supported.add(page << 8 | cell);
               }
            }
         }

         return supported;
      }
   }

   private static final class State implements AutoCloseable {
      private final Set<Integer> pages;
      private BitmapFontMetrics metrics;
      private final Int2ObjectMap<UnbakedGlyph> glyphs = new Int2ObjectOpenHashMap();
      private final List<NativeImage> uploads = new ArrayList<>();
      private final IntSet reported = new IntOpenHashSet();
      private boolean latin;
      private final Int2ObjectMap<UnbakedGlyph> latinGlyphs = new Int2ObjectOpenHashMap();

      private boolean markReported(int key) {
         synchronized (this.reported) {
            return this.reported.size() < 4000 && this.reported.add(key);
         }
      }

      private State(Set<Integer> pages, boolean latin) {
         this.pages = Set.copyOf(pages);
         this.latin = latin;
         this.metrics = new BitmapFontMetrics(
            index -> index == 0
               ? (latin ? BedrockTextures.pixels("font/default8.png").orElse(null) : null)
               : (this.pages.contains(index) ? BedrockTextures.pixels(GlyphPages.path(index)).orElse(null) : null)
         );
         if (latin && !hasLatinGlyphs(this.metrics)) {
            this.latin = false;
            this.metrics = new BitmapFontMetrics(
               index -> index == 0 ? null : (this.pages.contains(index) ? BedrockTextures.pixels(GlyphPages.path(index)).orElse(null) : null)
            );
         }
      }

      private static boolean hasLatinGlyphs(BitmapFontMetrics metrics) {
         for (int codePoint : new int[]{65, 97, 48, 101, 63}) {
            if (metrics.latinGlyph(codePoint) != null) {
               return true;
            }
         }

         return false;
      }

      private String latinSummary() {
         if (!this.latin) {
            return "Java's";
         } else {
            synchronized (this.glyphs) {
               BitmapFontMetrics.Page page = this.metrics.page(0);
               if (page == null) {
                  return "Java's (the server's font/default8.png could not be read)";
               } else {
                  long drawn = BitmapFontMetrics.default8CodePoints().stream().filter(codePoint -> this.metrics.latinGlyph(codePoint) != null).count();
                  return "the server's font/default8.png (" + page.width() + "x" + page.height() + ", " + drawn + " characters)";
               }
            }
         }
      }

      @Nullable
      private UnbakedGlyph latinGlyph(int codePoint) {
         if (!this.latin) {
            return null;
         } else {
            synchronized (this.glyphs) {
               if (this.latinGlyphs.containsKey(codePoint)) {
                  return (UnbakedGlyph)this.latinGlyphs.get(codePoint);
               } else {
                  BitmapFontMetrics.Glyph glyph = this.metrics.latinGlyph(codePoint);
                  BitmapFontMetrics.Page page = glyph == null ? null : this.metrics.page(0);
                  UnbakedGlyph made = page == null ? null : new BedrockGlyphs.PageGlyph(this, page, glyph);
                  this.latinGlyphs.put(codePoint, made);
                  return made;
               }
            }
         }
      }

      @Nullable
      private UnbakedGlyph glyph(int codePoint) {
         if (codePoint != 10 && codePoint != 13) {
            int page = codePoint >> 8;
            if (page != 0 && !this.pages.contains(page) && GlyphPages.blankWithoutPage(codePoint)) {
               if (this.markReported(-page)) {
                  BedrockNative.LOGGER
                     .warn("Bedrock glyph page {} has no page in the pack stack: its private-use characters draw as blanks", String.format("%02X", page));
               }

               return new EmptyGlyph(4.0F);
            } else if (page != 0 && this.pages.contains(page)) {
               synchronized (this.glyphs) {
                  if (this.glyphs.containsKey(codePoint)) {
                     return (UnbakedGlyph)this.glyphs.get(codePoint);
                  } else {
                     UnbakedGlyph glyph = this.make(codePoint, page);
                     this.glyphs.put(codePoint, glyph);
                     return glyph;
                  }
               }
            } else {
               return null;
            }
         } else {
            return new EmptyGlyph(codePoint == 10 ? 4.0F : 0.0F);
         }
      }

      @Nullable
      private UnbakedGlyph make(int codePoint, int pageIndex) {
         BitmapFontMetrics.Glyph glyph = this.metrics.pageGlyph(codePoint);
         BitmapFontMetrics.Page page = glyph == null ? null : this.metrics.page(glyph.page());
         if (glyph != null && page != null) {
            this.report(codePoint, pageIndex, glyph, page);
            if (glyph.width() == 0) {
               return GlyphPages.privateUse(pageIndex) ? new EmptyGlyph(glyph.advance()) : null;
            } else {
               return new BedrockGlyphs.PageGlyph(this, page, glyph);
            }
         } else {
            return null;
         }
      }

      private void report(int codePoint, int pageIndex, BitmapFontMetrics.Glyph glyph, BitmapFontMetrics.Page page) {
         if (GlyphPages.privateUse(pageIndex)) {
            if (this.markReported(-(pageIndex + 256))) {
               String source = PackStack.active()
                  .readAll(GlyphPages.path(pageIndex))
                  .stream()
                  .reduce((first, second) -> second)
                  .map(file -> String.valueOf(file.pack()))
                  .orElse("?");
               BedrockNative.LOGGER
                  .info(
                     "Bedrock glyph page {}: {}x{} texels, cells {}x{}, drawn {} font px tall, from {}",
                     new Object[]{
                        String.format("%02X", pageIndex),
                        page.width(),
                        page.height(),
                        page.cellWidth(),
                        page.cellHeight(),
                        page.cellHeight() * glyph.scale(),
                        source
                     }
                  );
            }

            if (this.markReported(codePoint)) {
               BedrockNative.LOGGER
                  .debug(
                     "Bedrock glyph U+{}: {}x{} texels of its cell, advance {}, drawn {}x{} font pixels",
                     new Object[]{
                        String.format("%04X", codePoint),
                        glyph.width(),
                        glyph.height(),
                        glyph.advance(),
                        glyph.width() * glyph.scale(),
                        glyph.height() * glyph.scale()
                     }
                  );
               BedrockGlyphs.trace("font", codePoint, page, glyph);
            }
         }
      }

      private synchronized NativeImage image(int width, int height) {
         NativeImage image = new NativeImage(Format.RGBA, width, height, false);
         this.uploads.add(image);
         return image;
      }

      @Override
      public synchronized void close() {
         this.uploads.forEach(NativeImage::close);
         this.uploads.clear();
      }
   }
}
