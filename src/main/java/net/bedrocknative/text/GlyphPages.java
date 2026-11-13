package net.bedrocknative.text;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.content.PackSource;
import net.bedrocknative.content.PackStack;

public final class GlyphPages {
   public static final int FIRST_PRIVATE = 224;
   public static final int LAST_PRIVATE = 248;
   public static final String LATIN_FONT = "font/default8.png";

   private GlyphPages() {
   }

   public static String path(int page) {
      return String.format(Locale.ROOT, "font/glyph_%02X.png", page);
   }

   public static boolean privateUse(int page) {
      return page >= 224 && page <= 248;
   }

   public static boolean blankWithoutPage(int codePoint) {
      return privateUse(codePoint >> 8);
   }

   public static TreeSet<Integer> collect(PackStack stack, int serverPacks) {
      TreeSet<Integer> pages = new TreeSet<>();
      List<PackSource> packs = stack.packs();

      for (int page = 1; page <= 255; page++) {
         for (PackSource pack : privateUse(page) ? packs : packs.subList(0, Math.min(serverPacks, packs.size()))) {
            if (has(pack, path(page))) {
               pages.add(page);
               break;
            }
         }
      }

      return pages;
   }

   public static boolean serverLatinFont(PackStack stack, int serverPacks) {
      List<PackSource> packs = stack.packs();

      for (PackSource pack : packs.subList(0, Math.min(Math.max(serverPacks, 0), packs.size()))) {
         if (has(pack, "font/default8.png")) {
            return true;
         }
      }

      return false;
   }

   private static boolean has(PackSource pack, String path) {
      try {
         return pack.read(path).isPresent();
      } catch (IOException var3) {
         BedrockNative.LOGGER.debug("Couldn't read {} from {}", new Object[]{path, pack, var3});
         return false;
      }
   }
}
