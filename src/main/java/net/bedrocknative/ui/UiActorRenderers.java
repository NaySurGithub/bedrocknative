package net.bedrocknative.ui;

import org.jspecify.annotations.Nullable;

public final class UiActorRenderers {
   public static final String LIVE_HORSE = "live_horse_renderer";
   public static final String LIVE_PLAYER = "live_player_renderer";
   public static final String PAPER_DOLL = "paper_doll_renderer";
   public static final String ENTITY_ID = "#entity_id";
   static final int MAX_ID_LENGTH = 20;

   private UiActorRenderers() {
   }

   public static boolean drawsActor(@Nullable String renderer) {
      return "live_horse_renderer".equals(renderer) || "live_player_renderer".equals(renderer) || "paper_doll_renderer".equals(renderer);
   }

   @Nullable
   public static Long entityId(@Nullable UiValue value) {
      if (value == null || value.isNull()) {
         return null;
      } else if (value.isNumber()) {
         double number = value.asNumber();
         return Double.isFinite(number) && number == Math.rint(number) && !(Math.abs(number) >= 9.223372E18F) ? (long)number : null;
      } else {
         String text = value.stringOrNull();
         return text == null ? null : entityId(text);
      }
   }

   @Nullable
   public static Long entityId(String text) {
      String digits = UiFormatting.strip(text).strip();
      if (!digits.isEmpty() && digits.length() <= 20) {
         int start = digits.charAt(0) != '-' && digits.charAt(0) != '+' ? 0 : 1;
         if (start == digits.length()) {
            return null;
         } else {
            for (int i = start; i < digits.length(); i++) {
               char c = digits.charAt(i);
               if (c < '0' || c > '9') {
                  return null;
               }
            }

            try {
               return Long.parseLong(digits);
            } catch (NumberFormatException var5) {
               return null;
            }
         }
      } else {
         return null;
      }
   }

   public static UiValue value(long uniqueId) {
      return UiValue.of(Long.toString(uniqueId));
   }

   public static UiActorRenderers.Stand stand(float x, float y, float w, float h) {
      return new UiActorRenderers.Stand(x + w / 2.0F, y + h, Math.max(0.0F, Math.min(w, h)));
   }

   public record Stand(float centreX, float feetY, float scale) {
   }
}
