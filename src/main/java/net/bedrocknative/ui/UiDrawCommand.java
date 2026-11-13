package net.bedrocknative.ui;

import java.util.List;
import org.jspecify.annotations.Nullable;

public sealed interface UiDrawCommand permits UiDrawCommand.Image, UiDrawCommand.Text, UiDrawCommand.Custom, UiDrawCommand.Fill {
   UiControl control();

   float z();

   @Nullable
   float[] clip();

   public static enum Alignment {
      LEFT,
      CENTER,
      RIGHT;
   }

   public record Custom(UiControl control, String renderer, float x, float y, float w, float h, int argb, @Nullable float[] clip, float z)
      implements UiDrawCommand {
   }

   public record Fill(UiControl control, float x, float y, float w, float h, int argb, @Nullable float[] clip, float z) implements UiDrawCommand {
   }

   public record Image(
      UiControl control, UiTextureInfo texture, List<UiDrawCommand.Quad> quads, int argb, boolean bilinear, boolean grayscale, @Nullable float[] clip, float z
   ) implements UiDrawCommand {
   }

   public record Quad(float x, float y, float w, float h, float u0, float v0, float u1, float v1) {
   }

   public record Text(
      UiControl control,
      List<String> lines,
      float x,
      float y,
      float width,
      float lineHeight,
      float linePadding,
      UiDrawCommand.Alignment alignment,
      UiFont font,
      int argb,
      boolean shadow,
      @Nullable float[] clip,
      float z
   ) implements UiDrawCommand {
   }
}
