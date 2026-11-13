package net.bedrocknative;

import java.util.Collection;
import java.util.Locale;

public final class LogLists {
   public static final int ENTRIES = 40;
   public static final int ENTRY_CHARS = 200;
   public static final int TEXT_CHARS = 4000;
   private static final long FNV_OFFSET = -3750763034362895579L;
   private static final long FNV_PRIME = 1099511628211L;

   private LogLists() {
   }

   public static String summary(Collection<?> items) {
      return summary(items, 40, 200);
   }

   public static String summary(Collection<?> items, int entries, int entryChars) {
      StringBuilder text = new StringBuilder("[");
      long hash = -3750763034362895579L;
      boolean cut = false;
      int index = 0;

      for (Object item : items) {
         String entry = String.valueOf(item);
         hash = fnv(hash, entry);
         if (index < entries) {
            if (index > 0) {
               text.append(", ");
            }

            if (entry.length() > entryChars) {
               text.append(entry, 0, entryChars).append("...");
               cut = true;
            } else {
               text.append(entry);
            }
         }

         index++;
      }

      if (index > entries) {
         text.append(", ...");
      }

      text.append(']');
      if (index > entries || cut) {
         text.append(String.format(Locale.ROOT, " (%d entries, hash %016x)", index, hash));
      }

      return text.toString();
   }

   public static String text(String text, int max) {
      return text.length() <= max
         ? text
         : text.substring(0, max) + String.format(Locale.ROOT, "... (%d chars, hash %016x)", text.length(), fnv(-3750763034362895579L, text));
   }

   public static String text(String text) {
      return text(text, 4000);
   }

   private static long fnv(long hash, String text) {
      for (int i = 0; i < text.length(); i++) {
         hash ^= text.charAt(i);
         hash *= 1099511628211L;
      }

      hash ^= 65535L;
      return hash * 1099511628211L;
   }
}
