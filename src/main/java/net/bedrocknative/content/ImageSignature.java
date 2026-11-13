package net.bedrocknative.content;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

public final class ImageSignature {
   private static final int MAX_TYPE = 64;
   private static final int HEX_BYTES = 8;

   private ImageSignature() {
   }

   public static String describe(@Nullable String contentType, byte[] data) {
      StringBuilder out = new StringBuilder().append(data.length).append(" bytes");
      String type = contentType == null ? "" : printable(contentType.strip(), 64);
      if (!type.isEmpty()) {
         out.append(", Content-Type ").append(type);
      }

      return out.append(", ").append(kind(data)).toString();
   }

   static String kind(byte[] data) {
      if (data.length == 0) {
         return "no body";
      } else if (starts(data, 0, "GIF87a") || starts(data, 0, "GIF89a")) {
         return "a GIF image";
      } else if (starts(data, 0, "RIFF") && starts(data, 8, "WEBP")) {
         return "a WebP image";
      } else if (data.length >= 12 && starts(data, 4, "ftyp")) {
         return "an ISO media file (" + printable(new String(data, 8, 4, StandardCharsets.ISO_8859_1), 4) + ")";
      } else if (data.length >= 26 && starts(data, 0, "BM")) {
         return "a BMP image";
      } else if (data.length >= 6 && data[0] == 0 && data[1] == 0 && data[2] == 1 && data[3] == 0) {
         return "an ICO image";
      } else {
         String text = leadingText(data);
         if (text.startsWith("<svg") || text.startsWith("<?xml")) {
            return "SVG or XML text";
         } else if (text.startsWith("<!doctype html") || text.startsWith("<html") || text.startsWith("<head") || text.startsWith("<body")) {
            return "an HTML page";
         } else if (!text.startsWith("{") && !text.startsWith("[")) {
            StringBuilder hex = new StringBuilder("bytes");

            for (int i = 0; i < Math.min(8, data.length); i++) {
               hex.append(' ').append(String.format(Locale.ROOT, "%02x", data[i] & 255));
            }

            return hex.toString();
         } else {
            return "JSON text";
         }
      }
   }

   private static boolean starts(byte[] data, int at, String ascii) {
      if (data.length < at + ascii.length()) {
         return false;
      } else {
         for (int i = 0; i < ascii.length(); i++) {
            if (data[at + i] != (byte)ascii.charAt(i)) {
               return false;
            }
         }

         return true;
      }
   }

   private static String leadingText(byte[] data) {
      int start = 0;
      if (data.length >= 3 && (data[0] & 255) == 239 && (data[1] & 255) == 187 && (data[2] & 255) == 191) {
         start = 3;
      }

      while (start < data.length && (data[start] == 32 || data[start] == 9 || data[start] == 13 || data[start] == 10)) {
         start++;
      }

      return new String(data, start, Math.min(16, data.length - start), StandardCharsets.ISO_8859_1).toLowerCase(Locale.ROOT);
   }

   private static String printable(String text, int max) {
      StringBuilder out = new StringBuilder();

      for (int i = 0; i < text.length() && out.length() < max; i++) {
         char c = text.charAt(i);
         out.append(c >= ' ' && c < 127 ? c : '?');
      }

      return out.toString();
   }
}
