package net.bedrocknative.partner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import net.bedrocknative.content.ArgbImage;
import net.bedrocknative.content.BedrockImages;

public final class PartnerImage {
   static final String DATA_PREFIX = "data:image/png;base64,";
   private static final byte[] SIGNATURE = new byte[]{-119, 80, 78, 71, 13, 10, 26, 10};

   private PartnerImage() {
   }

   public static boolean isDataUri(String value) {
      return value.regionMatches(true, 0, "data:image/png;base64,", 0, "data:image/png;base64,".length());
   }

   public static byte[] decodeDataUri(String value, PartnerImage.Kind kind) throws IOException {
      if (!isDataUri(value)) {
         throw new IOException("not a PNG data URI");
      } else {
         String payload = value.substring("data:image/png;base64,".length()).strip();
         if (payload.length() > (kind.maxInlineBytes + 2) / 3 * 4 + 4) {
            throw new IOException("inline picture larger than " + kind.maxInlineBytes + " bytes");
         } else {
            byte[] bytes;
            try {
               bytes = Base64.getDecoder().decode(payload.getBytes(StandardCharsets.US_ASCII));
            } catch (IllegalArgumentException var5) {
               throw new IOException("bad base64 in the data URI");
            }

            check(bytes, kind, kind.maxInlineBytes);
            return bytes;
         }
      }
   }

   public static void check(byte[] bytes, PartnerImage.Kind kind, int maxBytes) throws IOException {
      if (bytes.length > maxBytes) {
         throw new IOException("picture larger than " + maxBytes + " bytes");
      } else {
         int[] size = pngSize(bytes);
         if (size[0] >= 1 && size[1] >= 1 && size[0] <= kind.maxWidth && size[1] <= kind.maxHeight) {
            ArgbImage image;
            try {
               image = BedrockImages.decode(bytes);
            } catch (RuntimeException | IOException var6) {
               throw new IOException("unreadable PNG");
            }

            if (image.width() != size[0] || image.height() != size[1]) {
               throw new IOException("PNG decodes to another size");
            }
         } else {
            throw new IOException("picture " + size[0] + "x" + size[1] + " outside 1x1.." + kind.maxWidth + "x" + kind.maxHeight);
         }
      }
   }

   static int[] pngSize(byte[] bytes) throws IOException {
      if (bytes.length < 33) {
         throw new IOException("not a PNG");
      } else {
         for (int i = 0; i < SIGNATURE.length; i++) {
            if (bytes[i] != SIGNATURE[i]) {
               throw new IOException("not a PNG");
            }
         }

         String type = new String(bytes, 12, 4, StandardCharsets.US_ASCII);
         if (type.equals("IHDR") && int32(bytes, 8) == 13) {
            long width = int32(bytes, 16) & 4294967295L;
            long height = int32(bytes, 20) & 4294967295L;
            return new int[]{(int)Math.min(width, 2147483647L), (int)Math.min(height, 2147483647L)};
         } else {
            throw new IOException("PNG without IHDR first");
         }
      }
   }

   private static int int32(byte[] b, int at) {
      return (b[at] & 0xFF) << 24 | (b[at + 1] & 0xFF) << 16 | (b[at + 2] & 0xFF) << 8 | b[at + 3] & 0xFF;
   }

   static String describe(String value) {
      if (isDataUri(value)) {
         return "data URI (" + value.length() + " chars)";
      } else {
         String shown = value.length() > 80 ? value.substring(0, 80) + "..." : value;
         return shown.toLowerCase(Locale.ROOT).startsWith("https://") ? shown : "\"" + shown + "\"";
      }
   }

   public static enum Kind {
      ICON(16384, 65536, 256, 256),
      BANNER(49152, 524288, 1600, 600);

      public final int maxInlineBytes;
      public final int maxUrlBytes;
      public final int maxWidth;
      public final int maxHeight;

      private Kind(int maxInlineBytes, int maxUrlBytes, int maxWidth, int maxHeight) {
         this.maxInlineBytes = maxInlineBytes;
         this.maxUrlBytes = maxUrlBytes;
         this.maxWidth = maxWidth;
         this.maxHeight = maxHeight;
      }
   }
}
