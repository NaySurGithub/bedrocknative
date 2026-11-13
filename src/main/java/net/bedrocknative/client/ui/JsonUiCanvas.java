package net.bedrocknative.client.ui;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.bedrocknative.client.content.BedrockTextures;
import net.bedrocknative.client.text.BedrockGlyphs;
import net.bedrocknative.perf.RenderSwitches;
import net.bedrocknative.ui.BitmapFontMetrics;
import net.bedrocknative.ui.HudRenderers;
import net.bedrocknative.ui.HudValues;
import net.bedrocknative.ui.UiCanvas;
import net.bedrocknative.ui.UiControl;
import net.bedrocknative.ui.UiDrawCommand;
import net.bedrocknative.ui.UiDrawList;
import net.bedrocknative.ui.UiFont;
import net.bedrocknative.ui.UiFormatting;
import net.bedrocknative.ui.UiGradient;
import net.bedrocknative.ui.UiProgressBar;
import net.bedrocknative.ui.UiTextMetrics;
import net.bedrocknative.ui.UiTextures;
import net.bedrocknative.ui.UiVectorFont;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.FontDescription.Resource;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

final class JsonUiCanvas implements UiCanvas {
   static final String ITEM_RENDERER = "inventory_item_renderer";
   private static final String RUNE = "rune";
   private static final Style RUNE_STYLE = Style.EMPTY.withFont(new Resource(Identifier.withDefaultNamespace("alt")));
   static final String PROGRESS_BAR = "progress_bar_renderer";
   private static final int SUB = 16;
   private static final JsonUiCanvas.RunCache RUNS = new JsonUiCanvas.RunCache();
   private static final GuiQuadBatch.Builder BATCH = new GuiQuadBatch.Builder();
   private static final Map<JsonUiCanvas.WidthKey, Float> WIDTHS = new ConcurrentHashMap<>(1024);
   private static final int WIDTHS_MAX = 8192;
   private final UiFormatting.State[] endState = new UiFormatting.State[1];
   private final GuiGraphicsExtractor graphics;
   private final Minecraft minecraft;
   private final float scale;
   private final BedrockFont font;
   @Nullable
   private final ItemSource items;
   @Nullable
   private final HudValues hud;
   private final UiTextures textures;
   private final Predicate<UiControl> skip;
   @Nullable
   private final JsonUiCanvas.Locator locator;
   private final Consumer<String> undrawn;
   private static final Set<String> NOTHING_ON_PURPOSE = Set.of("hover_text_renderer", "flying_item_renderer");
   @Nullable
   private final GuiRenderState state;
   @Nullable
   private ScreenRectangle scissor;
   @Nullable
   private final ScreenRectangle outerScissor;
   @Nullable
   private Identifier lastTexture;
   @Nullable
   private TextureSetup lastSetup;

   private static float width(UiTextMetrics metrics, UiFont font, String line) {
      if (WIDTHS.size() > 8192) {
         WIDTHS.clear();
      }

      return WIDTHS.computeIfAbsent(new JsonUiCanvas.WidthKey(metrics, font, line), key -> metrics.width(key.line(), key.font()));
   }

   private JsonUiCanvas(
      GuiGraphicsExtractor graphics,
      Minecraft minecraft,
      float scale,
      BedrockFont font,
      @Nullable ItemSource items,
      @Nullable HudValues hud,
      UiTextures textures,
      Predicate<UiControl> skip,
      @Nullable JsonUiCanvas.Locator locator,
      Consumer<String> undrawn
   ) {
      this.graphics = graphics;
      this.minecraft = minecraft;
      this.scale = scale;
      this.font = font;
      this.items = items;
      this.hud = hud;
      this.textures = textures;
      this.skip = skip;
      this.locator = locator;
      this.undrawn = undrawn;
      boolean batching = RenderSwitches.guiBatch;
      this.state = batching ? graphics.guiRenderState : null;
      this.outerScissor = batching ? graphics.scissorStack.peek() : null;
      this.scissor = this.outerScissor;
   }

   private void flush() {
      if (this.state != null) {
         BATCH.flush(this.state);
      }
   }

   private TextureSetup setup(Identifier id) {
      if (id != this.lastTexture || this.lastSetup == null) {
         AbstractTexture texture = this.minecraft.getTextureManager().getTexture(id);
         this.lastSetup = TextureSetup.singleTexture(texture.getTextureView(), texture.getSampler());
         this.lastTexture = id;
      }

      return this.lastSetup;
   }

   static void draw(GuiGraphicsExtractor graphics, UiDrawList list, BedrockFont font, @Nullable ItemSource items) {
      draw(graphics, list, font, items, renderer -> {});
   }

   static void draw(GuiGraphicsExtractor graphics, UiDrawList list, BedrockFont font, @Nullable ItemSource items, Consumer<String> undrawn) {
      draw(graphics, list, font, items, null, UiTextures.NONE, control -> false, null, undrawn);
   }

   static void draw(
      GuiGraphicsExtractor graphics,
      UiDrawList list,
      BedrockFont font,
      @Nullable ItemSource items,
      @Nullable HudValues hud,
      UiTextures textures,
      Predicate<UiControl> skip,
      @Nullable JsonUiCanvas.Locator locator
   ) {
      draw(graphics, list, font, items, hud, textures, skip, locator, renderer -> {});
   }

   static void draw(
      GuiGraphicsExtractor graphics,
      UiDrawList list,
      BedrockFont font,
      @Nullable ItemSource items,
      @Nullable HudValues hud,
      UiTextures textures,
      Predicate<UiControl> skip,
      @Nullable JsonUiCanvas.Locator locator,
      Consumer<String> undrawn
   ) {
      Minecraft minecraft = Minecraft.getInstance();
      float guiScale = minecraft.getWindow().getGuiScale();
      graphics.pose().pushMatrix();

      try {
         graphics.pose().scale(1.0F / guiScale, 1.0F / guiScale);
         if (JsonUi.devBackdrop()) {
            graphics.fill(0, 0, minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight(), -16777216);
         }

         JsonUiCanvas canvas = new JsonUiCanvas(graphics, minecraft, JsonUi.uiScale(), font, items, hud, textures, skip, locator, undrawn);

         try {
            list.submit(canvas);
         } finally {
            canvas.flush();
         }

         font.upload();
      } finally {
         graphics.pose().popMatrix();
      }
   }

   static void fill(GuiGraphicsExtractor graphics, float x, float y, float w, float h, int argb, @Nullable float[] clip) {
      float guiScale = Minecraft.getInstance().getWindow().getGuiScale();
      float scale = JsonUi.uiScale();
      graphics.pose().pushMatrix();

      try {
         graphics.pose().scale(1.0F / guiScale, 1.0F / guiScale);
         if (clip != null) {
            graphics.enableScissor(
               Math.round(clip[0] * scale), Math.round(clip[1] * scale), Math.round((clip[0] + clip[2]) * scale), Math.round((clip[1] + clip[3]) * scale)
            );
         }

         int x0 = Math.round(x * scale);
         int y0 = Math.round(y * scale);
         graphics.fill(x0, y0, Math.max(x0 + 1, Math.round((x + w) * scale)), Math.max(y0 + 1, Math.round((y + h) * scale)), argb);
         if (clip != null) {
            graphics.disableScissor();
         }
      } finally {
         graphics.pose().popMatrix();
      }
   }

   private int px(float ui) {
      return Math.round(ui * this.scale);
   }

   @Override
   public void draw(UiDrawCommand command) {
      if (!this.skip.test(command.control())) {
         float[] clip = command.clip();
         if (clip != null) {
            if (clip[2] <= 0.0F || clip[3] <= 0.0F) {
               return;
            }

            this.graphics.enableScissor(this.px(clip[0]), this.px(clip[1]), this.px(clip[0] + clip[2]), this.px(clip[1] + clip[3]));
            if (this.state != null) {
               this.scissor = this.graphics.scissorStack.peek();
            }
         }

         try {
            switch (command) {
               case UiDrawCommand.Image image:
                  this.image(image);
                  break;
               case UiDrawCommand.Text text:
                  this.text(text);
                  break;
               case UiDrawCommand.Custom custom:
                  this.custom(custom);
                  break;
               case UiDrawCommand.Fill fill:
                  this.fill(fill);
                  break;
               default:
                  throw new MatchException(null, null);
            }
         } finally {
            if (clip != null) {
               this.graphics.disableScissor();
               this.scissor = this.outerScissor;
            }
         }
      }
   }

   private void fill(UiDrawCommand.Fill fill) {
      this.flush();
      int x0 = this.px(fill.x());
      int y0 = this.px(fill.y());
      this.graphics.fill(x0, y0, Math.max(x0 + 1, this.px(fill.x() + fill.w())), Math.max(y0 + 1, this.px(fill.y() + fill.h())), fill.argb());
   }

   private void custom(UiDrawCommand.Custom custom) {
      if (this.hud != null && HudRenderers.handles(custom.renderer())) {
         for (UiDrawCommand part : HudRenderers.expand(custom, this.hud, this.textures)) {
            switch (part) {
               case UiDrawCommand.Image image:
                  this.image(image);
                  break;
               case UiDrawCommand.Fill fill:
                  this.fill(fill);
                  break;
               default:
            }
         }
      } else {
         this.flush();
         if ("locator_bar".equals(custom.renderer()) && this.locator != null) {
            this.graphics.pose().pushMatrix();

            try {
               this.graphics.pose().scale(this.scale, this.scale);
               this.locator.draw(this.graphics, custom.x(), custom.y());
            } finally {
               this.graphics.pose().popMatrix();
            }
         } else {
            String var2 = custom.renderer();
            switch (var2) {
               case "progress_bar_renderer":
                  for (UiProgressBar.Fill fill : UiProgressBar.fills(custom)) {
                     int x0 = this.px(fill.x());
                     int y0 = this.px(fill.y());
                     this.graphics.fill(x0, y0, Math.max(x0 + 1, this.px(fill.x() + fill.w())), Math.max(y0 + 1, this.px(fill.y() + fill.h())), fill.argb());
                  }
                  break;
               case "inventory_item_renderer":
                  ItemStack stack = this.items == null ? ItemStack.EMPTY : this.items.stack(custom.control());
                  if (!stack.isEmpty() && custom.w() > 0.0F && custom.h() > 0.0F) {
                     this.item(stack, custom.x(), custom.y(), custom.w(), custom.h(), this.items.seed(custom.control()));
                  }
                  break;
               case "gradient_renderer":
                  this.gradient(custom);
                  break;
               default:
                  float guiScale = this.minecraft.getWindow().getGuiScale();
                  float toGui = this.scale / guiScale;
                  UiDrawCommand.Custom inGui = new UiDrawCommand.Custom(
                     custom.control(),
                     custom.renderer(),
                     custom.x() * toGui,
                     custom.y() * toGui,
                     custom.w() * toGui,
                     custom.h() * toGui,
                     custom.argb(),
                     custom.clip(),
                     custom.z()
                  );
                  boolean drawn = false;
                  this.graphics.pose().pushMatrix();

                  try {
                     this.graphics.pose().scale(guiScale, guiScale);
                     if (this.items != null) {
                        drawn = this.items.draw(this.graphics, inGui);
                     }

                     if (!drawn) {
                        drawn = JsonUiActors.draw(this.minecraft, this.graphics, inGui);
                     }
                  } finally {
                     this.graphics.pose().popMatrix();
                  }

                  if (!drawn && !NOTHING_ON_PURPOSE.contains(custom.renderer())) {
                     this.undrawn.accept(custom.renderer());
                  }
            }
         }
      }
   }

   private void gradient(UiDrawCommand.Custom custom) {
      UiGradient gradient = UiGradient.of(custom);
      int x0 = this.px(custom.x());
      int y0 = this.px(custom.y());
      int x1 = this.px(custom.x() + custom.w());
      int y1 = this.px(custom.y() + custom.h());
      if (gradient != null && x1 > x0 && y1 > y0) {
         if (!gradient.horizontal()) {
            this.graphics.fillGradient(x0, y0, x1, y1, gradient.from(), gradient.to());
         } else {
            int columns = Math.min(256, x1 - x0);

            for (int i = 0; i < columns; i++) {
               int left = x0 + (x1 - x0) * i / columns;
               int right = x0 + (x1 - x0) * (i + 1) / columns;
               if (right > left) {
                  this.graphics.fill(left, y0, right, y1, gradient.at(columns == 1 ? 0.0F : (float)i / (columns - 1)));
               }
            }
         }
      }
   }

   private void item(ItemStack stack, float x, float y, float w, float h, int seed) {
      this.graphics.pose().pushMatrix();

      try {
         this.graphics.pose().translate(this.px(x), this.px(y));
         this.graphics.pose().scale(this.scale * w / 16.0F, this.scale * h / 16.0F);
         this.graphics.item(stack, 0, 0, seed);
      } finally {
         this.graphics.pose().popMatrix();
      }
   }

   private void image(UiDrawCommand.Image image) {
      String path = image.texture().path();
      Optional<BedrockTextures.Texture> texture = path.startsWith("url:")
         ? BedrockTextures.url(path.substring("url:".length())).texture()
         : (path.startsWith("file:") ? BedrockTextures.file(path.substring("file:".length())) : BedrockTextures.pack(path));
      if (!texture.isEmpty()) {
         List<UiDrawCommand.Quad> quads = image.quads();

         for (int index = 0; index < quads.size(); index++) {
            UiDrawCommand.Quad quad = quads.get(index);
            this.quad(texture.get(), quad.x(), quad.y(), quad.w(), quad.h(), quad.u0(), quad.v0(), quad.u1(), quad.v1(), image.argb());
         }
      }
   }

   private void quad(BedrockTextures.Texture texture, float x, float y, float w, float h, float u0, float v0, float u1, float v1, int argb) {
      int x0 = this.px(x);
      int y0 = this.px(y);
      int x1 = this.px(x + w);
      int y1 = this.px(y + h);
      if (x1 > x0 && y1 > y0) {
         float u = u0;
         float v = v0;
         float du = u1 - u0;
         float dv = v1 - v0;
         if (Math.abs(du) * 16.0F < 1.0F) {
            u = Math.max(0.0F, u0 - 0.25F);
            du = 0.5F;
         }

         if (Math.abs(dv) * 16.0F < 1.0F) {
            v = Math.max(0.0F, v0 - 0.25F);
            dv = 0.5F;
         }

         if (this.state == null) {
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
                  Math.round(du * 16.0F),
                  Math.round(dv * 16.0F),
                  texture.width() * 16,
                  texture.height() * 16,
                  argb
               );
         } else {
            this.blitBatched(
               texture.id(),
               x0,
               y0,
               u * 16.0F,
               v * 16.0F,
               x1 - x0,
               y1 - y0,
               Math.round(du * 16.0F),
               Math.round(dv * 16.0F),
               texture.width() * 16,
               texture.height() * 16,
               argb
            );
         }
      }
   }

   private void blitBatched(
      Identifier id, int x, int y, float u, float v, int width, int height, int srcWidth, int srcHeight, int textureWidth, int textureHeight, int color
   ) {
      BATCH.add(
         this.state,
         this.setup(id),
         this.graphics.pose(),
         this.scissor,
         x,
         y,
         x + width,
         y + height,
         (u + 0.0F) / textureWidth,
         (u + srcWidth) / textureWidth,
         (v + 0.0F) / textureHeight,
         (v + srcHeight) / textureHeight,
         color
      );
   }

   private void text(UiDrawCommand.Text text) {
      if ("rune".equals(text.font().type())) {
         this.runes(text);
      } else {
         UiTextMetrics metrics = this.font.metrics();
         UiVectorFont vector = metrics.vector(text.font());
         float baselineOffset = metrics.baseline(text.font(), vector);
         GlyphAtlas atlas = vector == null ? null : this.font.atlas(vector, Math.max(1, Math.round(UiTextMetrics.size(text.font()) * this.scale)));
         UiFormatting.State state = UiFormatting.State.INITIAL;
         float y = text.y();
         UiFormatting.State[] end = this.endState;
         List<String> lines = text.lines();

         for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            float width = width(metrics, text.font(), line);

            float x = switch (text.alignment()) {
               case LEFT -> text.x();
               case CENTER -> text.x() + (text.width() - width) / 2.0F;
               case RIGHT -> text.x() + text.width() - width;
            };
            List<UiFormatting.Run> runs = RUNS.runs(line, state, end);
            if (text.shadow()) {
               this.runs(runs, x + text.font().scale(), y + text.font().scale(), text, true, vector, baselineOffset, atlas);
            }

            this.runs(runs, x, y, text, false, vector, baselineOffset, atlas);
            state = end[0];
            y += text.lineHeight() + text.linePadding();
         }
      }
   }

   private void runs(
      List<UiFormatting.Run> runs,
      float x,
      float y,
      UiDrawCommand.Text text,
      boolean shadow,
      @Nullable UiVectorFont vector,
      float baselineOffset,
      @Nullable GlyphAtlas atlas
   ) {
      UiTextMetrics metrics = this.font.metrics();
      BitmapFontMetrics bitmap = this.font.bitmap();
      float fontScale = text.font().scale();
      int alpha = text.argb() >>> 24;
      float baseline = y + baselineOffset;
      float bitmapTop = baseline - 7.0F * fontScale;

      for (int index = 0; index < runs.size(); index++) {
         UiFormatting.Run run = runs.get(index);
         int rgb = run.rgb() >= 0 ? run.rgb() : text.argb() & 16777215;
         int argb = alpha << 24 | rgb;
         if (shadow) {
            argb = ARGB.scaleRGB(argb, 0.25F);
         }

         int i = 0;

         while (i < run.text().length()) {
            int codePoint = run.text().codePointAt(i);
            i += Character.charCount(codePoint);
            if (atlas != null && vector.has(codePoint)) {
               GlyphAtlas.Placed glyph = atlas.glyph(codePoint);
               if (glyph.texture() != null) {
                  this.ttfGlyph(glyph, x, baseline, argb);
                  if (run.bold()) {
                     this.ttfGlyph(glyph, x + fontScale, baseline, argb);
                  }
               }

               x += metrics.advance(codePoint, run.bold(), text.font(), vector);
            } else {
               BitmapFontMetrics.Glyph glyph = bitmap.glyph(codePoint);
               if (glyph == null) {
                  this.javaGlyph(codePoint, x, bitmapTop, fontScale, argb);
               } else if (glyph.width() > 0) {
                  BedrockTextures.Texture page = this.font.texture(glyph.page());
                  if (page != null) {
                     if (BedrockGlyphs.tracing()) {
                        BitmapFontMetrics.Page glyphPage = bitmap.page(glyph.page());
                        if (glyphPage != null) {
                           BedrockGlyphs.trace("ui scale " + fontScale, codePoint, glyphPage, glyph);
                        }
                     }

                     float texel = glyph.scale() * fontScale;
                     float w = glyph.width() * texel;
                     float h = glyph.height() * texel;
                     float top = bitmapTop + glyph.top() * fontScale;
                     this.quad(page, x, top, w, h, glyph.u(), glyph.v(), glyph.u() + glyph.width(), glyph.v() + glyph.height(), argb);
                     if (run.bold()) {
                        this.quad(page, x + fontScale, top, w, h, glyph.u(), glyph.v(), glyph.u() + glyph.width(), glyph.v() + glyph.height(), argb);
                     }
                  }
               }

               x += bitmap.advance(codePoint, glyph, run.bold()) * fontScale;
            }
         }
      }
   }

   private void ttfGlyph(GlyphAtlas.Placed glyph, float x, float baseline, int argb) {
      int x0 = this.px(x) + glyph.left();
      int y0 = this.px(baseline) + glyph.top();
      if (this.state == null) {
         this.graphics
            .blit(
               RenderPipelines.GUI_TEXTURED,
               glyph.texture(),
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
      } else {
         this.blitBatched(
            glyph.texture(), x0, y0, glyph.u() * 16, glyph.v() * 16, glyph.width(), glyph.height(), glyph.width() * 16, glyph.height() * 16, 8192, 8192, argb
         );
      }
   }

   private void runes(UiDrawCommand.Text text) {
      this.flush();
      float fontScale = text.font().scale();
      float y = text.y();

      for (String line : text.lines()) {
         this.graphics.pose().pushMatrix();

         try {
            this.graphics.pose().translate(this.px(text.x()), this.px(y));
            this.graphics.pose().scale(this.scale * fontScale, this.scale * fontScale);
            this.graphics.text(this.minecraft.font, Component.literal(line).withStyle(RUNE_STYLE), 0, 0, text.argb(), text.shadow());
         } finally {
            this.graphics.pose().popMatrix();
         }

         y += text.lineHeight() + text.linePadding();
      }
   }

   private void javaGlyph(int codePoint, float x, float y, float fontScale, int argb) {
      this.flush();
      this.graphics.pose().pushMatrix();

      try {
         this.graphics.pose().translate(this.px(x), this.px(y));
         this.graphics.pose().scale(this.scale * fontScale, this.scale * fontScale);
         this.graphics.text(this.minecraft.font, Character.toString(codePoint), 0, 0, argb, false);
      } finally {
         this.graphics.pose().popMatrix();
      }
   }

   interface Locator {
      void draw(GuiGraphicsExtractor var1, float var2, float var3);
   }

   private static final class RunCache {
      private static final int MAX = 4096;
      private final HashMap<String, JsonUiCanvas.RunCache.Entry> entries = new HashMap<>();

      List<UiFormatting.Run> runs(String line, UiFormatting.State start, UiFormatting.State[] end) {
         JsonUiCanvas.RunCache.Entry entry = this.entries.get(line);
         if (entry == null || !entry.start().equals(start)) {
            List<UiFormatting.Run> runs = List.copyOf(UiFormatting.runs(line, start, end));
            entry = new JsonUiCanvas.RunCache.Entry(start, runs, end[0]);
            if (this.entries.size() >= 4096) {
               this.entries.clear();
            }

            this.entries.put(line, entry);
         }

         end[0] = entry.end();
         return entry.runs();
      }

      private record Entry(UiFormatting.State start, List<UiFormatting.Run> runs, UiFormatting.State end) {
      }
   }

   private record WidthKey(UiTextMetrics metrics, UiFont font, String line) {
   }
}
