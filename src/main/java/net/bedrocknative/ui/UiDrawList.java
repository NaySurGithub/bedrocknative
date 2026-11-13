package net.bedrocknative.ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class UiDrawList {
   public static final UiDrawList EMPTY = new UiDrawList(List.of());
   private static final float INVISIBLE = 0.001953125F;
   private final List<UiDrawCommand> commands;

   private UiDrawList(List<UiDrawCommand> commands) {
      this.commands = commands;
   }

   public List<UiDrawCommand> commands() {
      return this.commands;
   }

   public void submit(UiCanvas canvas) {
      for (UiDrawCommand command : this.commands) {
         canvas.draw(command);
      }
   }

   static UiDrawList build(UiControl root, UiEnvironment environment) {
      List<UiDrawCommand> commands = new ArrayList<>();
      collect(root, 1.0F, environment, commands);
      commands.sort(Comparator.comparingDouble(UiDrawCommand::z));
      return new UiDrawList(Collections.unmodifiableList(commands));
   }

   private static void collect(UiControl c, float inherited, UiEnvironment environment, List<UiDrawCommand> commands) {
      if (c.ownVisible) {
         float alpha = c.alpha * inherited;
         c.drawAlpha = alpha;
         if (alpha > 0.001953125F && c.w > 0.0F && c.h > 0.0F) {
            switch (c.type) {
               case IMAGE:
                  image(c, alpha, environment, commands);
                  break;
               case LABEL:
                  text(c, alpha, environment, commands);
                  break;
               case CUSTOM:
                  if (c.renderer != null) {
                     commands.add(new UiDrawCommand.Custom(c, c.renderer, c.x, c.y, c.w, c.h, argb(c.color, alpha), c.clip, c.z));
                  }
            }
         }

         float childInherited = c.propagateAlpha ? alpha : inherited;

         for (int i = 0; i < c.children.size(); i++) {
            collect(c.children.get(i), childInherited, environment, commands);
         }
      }
   }

   private static void image(UiControl c, float alpha, UiEnvironment environment, List<UiDrawCommand> commands) {
      if (!c.texture.isEmpty()) {
         UiTextureInfo texture = environment.textures().info(c.texture, c.fileSystem);
         if (texture != null) {
            int argb = argb(c.color, alpha);
            UiDrawCommand.Image previous = c.drawnImage;
            UiDrawList.ImageInputs inputs = c.drawnInputs;
            if (previous != null
               && inputs != null
               && previous.argb() == argb
               && previous.bilinear() == c.bilinear
               && previous.grayscale() == c.grayscale
               && previous.z() == c.z
               && Arrays.equals(previous.clip(), c.clip)
               && inputs.same(c, texture)) {
               commands.add(previous);
            } else {
               List<UiDrawCommand.Quad> quads = UiImageGeometry.quads(
                  c.x, c.y, c.w, c.h, texture, c.uv, c.uvSize, c.nineslice, c.keepRatio, c.tiled, c.tiledScale, c.clipRatio, c.clipDirection
               );
               if (quads.isEmpty()) {
                  c.drawnImage = null;
                  c.drawnInputs = null;
               } else {
                  UiDrawCommand.Image image = new UiDrawCommand.Image(c, texture, quads, argb, c.bilinear, c.grayscale, c.clip, c.z);
                  commands.add(image);
                  c.drawnImage = image;
                  c.drawnInputs = UiDrawList.ImageInputs.of(c, texture);
               }
            }
         }
      }
   }

   private static void text(UiControl c, float alpha, UiEnvironment environment, List<UiDrawCommand> commands) {
      if (!c.text.isEmpty() && !c.lines.isEmpty()) {
         UiDrawCommand.Alignment alignment;
         if (c.alignment != null) {
            alignment = switch (c.alignment) {
               case "center" -> UiDrawCommand.Alignment.CENTER;
               case "right" -> UiDrawCommand.Alignment.RIGHT;
               default -> UiDrawCommand.Alignment.LEFT;
            };
         } else if (!c.props.containsKey("anchor_to") && !c.props.containsKey("anchor_from")) {
            alignment = UiDrawCommand.Alignment.LEFT;
         } else {
            alignment = c.anchorTo.x == 0.0F
               ? UiDrawCommand.Alignment.LEFT
               : (c.anchorTo.x == 1.0F ? UiDrawCommand.Alignment.RIGHT : UiDrawCommand.Alignment.CENTER);
         }

         commands.add(
            new UiDrawCommand.Text(
               c,
               c.lines,
               c.x,
               c.y,
               c.w,
               environment.measurer().lineHeight(c.font),
               c.linePadding,
               alignment,
               c.font,
               argb(c.color, alpha),
               c.shadow,
               c.clip,
               c.z
            )
         );
      }
   }

   static int argb(float[] color, float alpha) {
      int a = Math.round(UiProps.clamp(color[3] * alpha) * 255.0F);
      int r = Math.round(UiProps.clamp(color[0]) * 255.0F);
      int g = Math.round(UiProps.clamp(color[1]) * 255.0F);
      int b = Math.round(UiProps.clamp(color[2]) * 255.0F);
      return a << 24 | r << 16 | g << 8 | b;
   }

   record ImageInputs(
      UiTextureInfo texture,
      float x,
      float y,
      float w,
      float h,
      float @Nullable [] uv,
      float @Nullable [] uvSize,
      float @Nullable [] slices,
      boolean keepRatio,
      @Nullable String tiled,
      float[] tiledScale,
      float clipRatio,
      String clipDirection
   ) {
      static UiDrawList.ImageInputs of(UiControl c, UiTextureInfo texture) {
         return new UiDrawList.ImageInputs(
            texture,
            c.x,
            c.y,
            c.w,
            c.h,
            copy(c.uv),
            copy(c.uvSize),
            copy(c.nineslice),
            c.keepRatio,
            c.tiled,
            (float[])c.tiledScale.clone(),
            c.clipRatio,
            c.clipDirection
         );
      }

      boolean same(UiControl c, UiTextureInfo info) {
         return this.texture == info
            && this.x == c.x
            && this.y == c.y
            && this.w == c.w
            && this.h == c.h
            && this.keepRatio == c.keepRatio
            && this.clipRatio == c.clipRatio
            && Arrays.equals(this.uv, c.uv)
            && Arrays.equals(this.uvSize, c.uvSize)
            && Arrays.equals(this.slices, c.nineslice)
            && Arrays.equals(this.tiledScale, c.tiledScale)
            && Objects.equals(this.tiled, c.tiled)
            && this.clipDirection.equals(c.clipDirection);
      }

      private static float @Nullable [] copy(float @Nullable [] values) {
         return values == null ? null : (float[])values.clone();
      }
   }
}
