package net.bedrocknative.content;

import java.awt.image.BufferedImage;
import java.awt.image.ComponentColorModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.jspecify.annotations.Nullable;
import org.w3c.dom.Node;

public final class BedrockImages {
   private static final String PNG_METADATA = "javax_imageio_png_1.0";
   public static final int MAX_FILE_BYTES = 67108864;

   private BedrockImages() {
   }

   public static byte[] readChecked(InputStream input, int maxBytes) throws IOException {
      byte[] head = input.readNBytes(24);
      if (head.length == 24 && isPng(head) && head[12] == 73 && head[13] == 72 && head[14] == 68 && head[15] == 82) {
         ImageLimits.requireSource(ByteBuffer.wrap(head, 16, 4).getInt(), ByteBuffer.wrap(head, 20, 4).getInt(), 67108864L);
      }

      byte[] rest = input.readNBytes(Math.max(0, maxBytes - head.length) + 1);
      if (head.length + rest.length > maxBytes) {
         throw new IOException("image file larger than " + maxBytes + " bytes");
      } else {
         byte[] all = Arrays.copyOf(head, head.length + rest.length);
         System.arraycopy(rest, 0, all, head.length, rest.length);
         return all;
      }
   }

   public static ArgbImage decode(byte[] data) throws IOException {
      if (!isPng(data) && !isJpeg(data)) {
         TgaImage tga = TgaImage.decode(data);
         ArgbImage image = new ArgbImage(tga.width(), tga.height(), tga.argb());
         return TextureOps.downscale(image, ImageLimits.step(tga.width(), tga.height()));
      } else {
         return decodeImageIo(data);
      }
   }

   public static long pixelCount(byte[] data) throws IOException {
      if (!isPng(data) && !isJpeg(data)) {
         throw new IOException("not a PNG or JPEG image");
      } else {
         try {
            long var4;
            try (ImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(data))) {
               Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
               if (!readers.hasNext()) {
                  throw new IOException("unreadable image");
               }

               ImageReader reader = readers.next();

               try {
                  reader.setInput(input, true, true);
                  var4 = (long)reader.getWidth(0) * reader.getHeight(0);
               } finally {
                  reader.dispose();
               }
            }

            return var4;
         } catch (RuntimeException var14) {
            throw new IOException("unreadable image: " + var14, var14);
         }
      }
   }

   private static ArgbImage decodeImageIo(byte[] data) throws IOException {
      try {
         ArgbImage var11;
         try (ImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(data))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
               throw new IOException("unreadable image");
            }

            ImageReader reader = readers.next();

            try {
               reader.setInput(input, true, false);
               int width = reader.getWidth(0);
               int height = reader.getHeight(0);
               ImageLimits.requireSource(width, height, isJpeg(data) ? 33554432L : 67108864L);
               int step = ImageLimits.step(width, height);
               ImageReadParam param = reader.getDefaultReadParam();
               if (step > 1) {
                  param.setSourceSubsampling(step, step, 0, 0);
               }

               BufferedImage image = reader.read(0, param);
               ArgbImage pixels = pixels(image);
               int[] key = isPng(data) && mayHaveGreyKey(data) ? greyTransparencyKey(reader.getImageMetadata(0)) : null;
               var11 = key != null ? clearKey(image, pixels, key[0], key[1]) : pixels;
            } finally {
               reader.dispose();
            }
         }

         return var11;
      } catch (RuntimeException var20) {
         throw new IOException("undecodable image: " + var20, var20);
      }
   }

   private static ArgbImage pixels(BufferedImage image) {
      int width = image.getWidth();
      int height = image.getHeight();
      if (!(image.getColorModel() instanceof ComponentColorModel model)) {
         return new ArgbImage(width, height, image.getRGB(0, 0, width, height, null, 0, width));
      } else {
         WritableRaster var18 = image.getRaster();
         int colours = model.getNumColorComponents();
         boolean alpha = model.hasAlpha();
         int bands = var18.getNumBands();
         int[] argb = new int[width * height];
         if (bands < (alpha ? colours + 1 : colours)) {
            for (int y = 0; y < height; y++) {
               for (int x = 0; x < width; x++) {
                  int r = sample(var18, model, x, y, 0);
                  int g = colours >= 3 ? sample(var18, model, x, y, 1) : r;
                  int b = colours >= 3 ? sample(var18, model, x, y, 2) : r;
                  int a = alpha ? sample(var18, model, x, y, colours) : 255;
                  argb[y * width + x] = a << 24 | r << 16 | g << 8 | b;
               }
            }

            return new ArgbImage(width, height, argb);
         } else {
            int[] shifts = new int[bands];

            for (int band = 0; band < bands; band++) {
               shifts[band] = model.getComponentSize(band);
            }

            int[] row = new int[width * bands];

            for (int y = 0; y < height; y++) {
               var18.getPixels(0, y, width, 1, row);

               for (int x = 0; x < width; x++) {
                  int at = x * bands;
                  int r = scale(row[at], shifts[0]);
                  int g = colours >= 3 ? scale(row[at + 1], shifts[1]) : r;
                  int b = colours >= 3 ? scale(row[at + 2], shifts[2]) : r;
                  int a = alpha ? scale(row[at + colours], shifts[colours]) : 255;
                  argb[y * width + x] = a << 24 | r << 16 | g << 8 | b;
               }
            }

            return new ArgbImage(width, height, argb);
         }
      }
   }

   private static boolean mayHaveGreyKey(byte[] data) {
      if (data.length >= 26 && data[12] == 73 && data[13] == 72 && data[14] == 68 && data[15] == 82) {
         int bitDepth = data[24] & 255;
         int colourType = data[25] & 255;
         return colourType == 0 && bitDepth > 0 && bitDepth < 8;
      } else {
         return true;
      }
   }

   @Nullable
   private static int[] greyTransparencyKey(@Nullable IIOMetadata metadata) {
      if (metadata != null && Arrays.asList(metadata.getMetadataFormatNames()).contains("javax_imageio_png_1.0")) {
         Node root = metadata.getAsTree("javax_imageio_png_1.0");
         int bitDepth = -1;

         for (Node node = root.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node.getNodeName().equals("IHDR") && "Grayscale".equals(attribute(node, "colorType"))) {
               bitDepth = Integer.parseInt(attribute(node, "bitDepth"));
            }
         }

         if (bitDepth > 0 && bitDepth < 8) {
            for (Node nodex = root.getFirstChild(); nodex != null; nodex = nodex.getNextSibling()) {
               if (nodex.getNodeName().equals("tRNS")) {
                  for (Node child = nodex.getFirstChild(); child != null; child = child.getNextSibling()) {
                     if (child.getNodeName().equals("tRNS_Grayscale")) {
                        return new int[]{Integer.parseInt(attribute(child, "gray")), bitDepth};
                     }
                  }
               }
            }

            return null;
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   private static String attribute(Node node, String name) {
      Node attribute = node.getAttributes().getNamedItem(name);
      return attribute == null ? "" : attribute.getNodeValue();
   }

   private static ArgbImage clearKey(BufferedImage image, ArgbImage pixels, int key, int bitDepth) {
      Raster raster = image.getRaster();
      int sampleBits = image.getColorModel().getComponentSize(0);
      int expected = sampleBits == bitDepth ? key : key * ((1 << sampleBits) - 1) / ((1 << bitDepth) - 1);
      int[] argb = (int[])pixels.argb().clone();

      for (int y = 0; y < pixels.height(); y++) {
         for (int x = 0; x < pixels.width(); x++) {
            if (raster.getSample(x, y, 0) == expected) {
               argb[y * pixels.width() + x] &= 16777215;
            }
         }
      }

      return new ArgbImage(pixels.width(), pixels.height(), argb);
   }

   private static int sample(Raster raster, ComponentColorModel model, int x, int y, int band) {
      return scale(raster.getSample(x, y, band), model.getComponentSize(band));
   }

   private static int scale(int value, int bits) {
      return bits == 8 ? value & 0xFF : (bits > 8 ? value >>> bits - 8 & 0xFF : value * 255 / ((1 << bits) - 1));
   }

   private static boolean isPng(byte[] data) {
      return data.length > 8 && (data[0] & 255) == 137 && data[1] == 80 && data[2] == 78 && data[3] == 71;
   }

   private static boolean isJpeg(byte[] data) {
      return data.length > 3 && (data[0] & 255) == 255 && (data[1] & 255) == 216;
   }
}
