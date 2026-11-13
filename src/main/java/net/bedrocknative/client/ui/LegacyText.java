package net.bedrocknative.client.ui;

import java.util.Arrays;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import org.jspecify.annotations.Nullable;

public final class LegacyText {
   private static final ChatFormatting[] COLORS = Arrays.copyOf(ChatFormatting.values(), 16);

   private LegacyText() {
   }

   public static String of(Component component) {
      StringBuilder out = new StringBuilder();
      boolean[] styled = new boolean[]{false};
      component.visit((style, text) -> {
         if (text.isEmpty()) {
            return Optional.empty();
         } else {
            String codes = codes(style);
            if (!codes.isEmpty() || styled[0]) {
               out.append('§').append('r').append(codes);
               styled[0] = !codes.isEmpty();
            }

            out.append(text);
            return Optional.empty();
         }
      }, Style.EMPTY);
      return out.toString();
   }

   private static String codes(Style style) {
      StringBuilder codes = new StringBuilder();
      ChatFormatting color = color(style.getColor());
      if (color != null) {
         codes.append(color);
      }

      if (style.isBold()) {
         codes.append(ChatFormatting.BOLD);
      }

      if (style.isItalic()) {
         codes.append(ChatFormatting.ITALIC);
      }

      if (style.isObfuscated()) {
         codes.append(ChatFormatting.OBFUSCATED);
      }

      return codes.toString();
   }

   @Nullable
   static ChatFormatting color(@Nullable TextColor color) {
      if (color == null) {
         return null;
      } else {
         int rgb = color.getValue();
         ChatFormatting best = null;
         int bestDistance = Integer.MAX_VALUE;

         for (ChatFormatting candidate : COLORS) {
            int value = TextColor.fromLegacyFormat(candidate).getValue();
            int dr = (value >> 16 & 0xFF) - (rgb >> 16 & 0xFF);
            int dg = (value >> 8 & 0xFF) - (rgb >> 8 & 0xFF);
            int db = (value & 0xFF) - (rgb & 0xFF);
            int distance = dr * dr + dg * dg + db * db;
            if (distance < bestDistance) {
               bestDistance = distance;
               best = candidate;
            }
         }

         return best;
      }
   }
}
