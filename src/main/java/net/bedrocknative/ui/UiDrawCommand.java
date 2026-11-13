package net.bedrocknative.ui;

import java.util.List;
import org.jspecify.annotations.Nullable;

public sealed interface UiDrawCommand permits UiDrawCommand.Image, UiDrawCommand.Text, UiDrawCommand.Custom, UiDrawCommand.Fill {
   UiControl control();

   float z();

   float @Nullable [] clip();

   enum Alignment {
      LEFT,
      CENTER,
      RIGHT;
   }

   record Custom(UiControl control, String renderer, float x, float y, float w, float h, int argb, float @Nullable [] clip, float z) implements UiDrawCommand {
   }

   record Fill(UiControl control, float x, float y, float w, float h, int argb, float @Nullable [] clip, float z) implements UiDrawCommand {
   }

   record Image(
      UiControl control, UiTextureInfo texture, List<UiDrawCommand.Quad> quads, int argb, boolean bilinear, boolean grayscale, float @Nullable [] clip, float z
   ) implements UiDrawCommand {
   }

   record Quad(float x, float y, float w, float h, float u0, float v0, float u1, float v1) {
   }

   record Text(
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
      float @Nullable [] clip,
      float z
   ) implements UiDrawCommand {
   }
}
