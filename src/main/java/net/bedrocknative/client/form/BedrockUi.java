package net.bedrocknative.client.form;

import java.util.Optional;
import net.bedrocknative.client.content.BedrockTextures;
import net.bedrocknative.form.FormImage;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

final class BedrockUi {
   static final int DARK_TEXT = -11776948;
   static final int LIGHT_TEXT = -1;
   static final int IMAGE_SIZE = 32;

   private BedrockUi() {
   }

   static void nineSlice(GuiGraphicsExtractor graphics, String path, int x, int y, int width, int height, int color, int fallback) {
      Optional<BedrockTextures.NineSlice> found = BedrockTextures.nineSlice(path);
      if (found.isEmpty()) {
         graphics.fill(x, y, x + width, y + height, fallback);
      } else {
         BedrockTextures.NineSlice slice = found.get();
         BedrockTextures.Texture texture = slice.texture();
         int tw = texture.width();
         int th = texture.height();
         int left = Math.min(slice.left(), width / 2);
         int right = Math.min(slice.right(), width - left);
         int top = Math.min(slice.top(), height / 2);
         int bottom = Math.min(slice.bottom(), height - top);
         int centreWidth = width - left - right;
         int centreHeight = height - top - bottom;
         int sourceWidth = tw - slice.left() - slice.right();
         int sourceHeight = th - slice.top() - slice.bottom();
         part(graphics, texture, x, y, left, top, 0, 0, slice.left(), slice.top(), color);
         part(graphics, texture, x + left, y, centreWidth, top, slice.left(), 0, sourceWidth, slice.top(), color);
         part(graphics, texture, x + width - right, y, right, top, tw - slice.right(), 0, slice.right(), slice.top(), color);
         part(graphics, texture, x, y + top, left, centreHeight, 0, slice.top(), slice.left(), sourceHeight, color);
         part(graphics, texture, x + left, y + top, centreWidth, centreHeight, slice.left(), slice.top(), sourceWidth, sourceHeight, color);
         part(graphics, texture, x + width - right, y + top, right, centreHeight, tw - slice.right(), slice.top(), slice.right(), sourceHeight, color);
         part(graphics, texture, x, y + height - bottom, left, bottom, 0, th - slice.bottom(), slice.left(), slice.bottom(), color);
         part(graphics, texture, x + left, y + height - bottom, centreWidth, bottom, slice.left(), th - slice.bottom(), sourceWidth, slice.bottom(), color);
         part(
            graphics,
            texture,
            x + width - right,
            y + height - bottom,
            right,
            bottom,
            tw - slice.right(),
            th - slice.bottom(),
            slice.right(),
            slice.bottom(),
            color
         );
      }
   }

   private static void part(
      GuiGraphicsExtractor graphics,
      BedrockTextures.Texture texture,
      int x,
      int y,
      int width,
      int height,
      int u,
      int v,
      int sourceWidth,
      int sourceHeight,
      int color
   ) {
      if (width > 0 && height > 0 && sourceWidth > 0 && sourceHeight > 0) {
         graphics.blit(
            RenderPipelines.GUI_TEXTURED, texture.id(), x, y, u, v, width, height, sourceWidth, sourceHeight, texture.width(), texture.height(), color
         );
      }
   }

   static void image(GuiGraphicsExtractor graphics, BedrockTextures.Texture texture, int x, int y, int width, int height) {
      float scale = Math.min((float)width / texture.width(), (float)height / texture.height());
      int drawWidth = Math.max(1, Math.round(texture.width() * scale));
      int drawHeight = Math.max(1, Math.round(texture.height() * scale));
      graphics.blit(
         RenderPipelines.GUI_TEXTURED,
         texture.id(),
         x + (width - drawWidth) / 2,
         y + (height - drawHeight) / 2,
         0.0F,
         0.0F,
         drawWidth,
         drawHeight,
         texture.width(),
         texture.height(),
         texture.width(),
         texture.height()
      );
   }

   static void formImage(GuiGraphicsExtractor graphics, @Nullable FormImage image, int x, int y) {
      if (image != null) {
         if (image.type() == FormImage.Type.PATH) {
            BedrockTextures.pack(image.data()).ifPresent(texturex -> image(graphics, texturex, x, y, 32, 32));
         } else {
            BedrockTextures.UrlImage download = BedrockTextures.url(image.data());
            Optional<BedrockTextures.Texture> texture = download.texture();
            if (texture.isPresent()) {
               image(graphics, texture.get(), x, y, 32, 32);
            } else if (download.loading()) {
               int barX = x + 1;
               int barY = y + 14;
               graphics.fill(barX, barY, barX + 30, barY + 4, -12961222);
               int lit = (int)(Util.getMillis() / 150L % 3L);

               for (int i = 0; i < 3; i++) {
                  graphics.fill(barX + 1 + i * 10, barY + 1, barX + 9 + i * 10, barY + 3, i == lit ? -9649335 : -13673949);
               }
            }
         }
      }
   }
}
