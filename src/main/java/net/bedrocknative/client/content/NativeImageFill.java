package net.bedrocknative.client.content;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.NativeImage.Format;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import net.minecraft.util.ARGB;

public final class NativeImageFill {
   private NativeImageFill() {
   }

   public static void fillArgb(NativeImage image, int[] argb) {
      int width = image.getWidth();
      int height = image.getHeight();
      int count = width * height;
      if (image.format() == Format.RGBA && argb.length >= count && !image.isClosed()) {
         IntBuffer pixels = image.getPixelBytes().order(ByteOrder.nativeOrder()).asIntBuffer();

         for (int i = 0; i < count; i++) {
            pixels.put(i, ARGB.toABGR(argb[i]));
         }
      } else {
         for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
               image.setPixel(x, y, argb[y * width + x]);
            }
         }
      }
   }
}
