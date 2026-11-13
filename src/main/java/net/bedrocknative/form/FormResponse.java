package net.bedrocknative.form;

import java.math.BigDecimal;
import java.util.List;

public final class FormResponse {
   private FormResponse() {
   }

   public static String button(int index) {
      return index + "\n";
   }

   public static String modal(boolean firstButton) {
      return firstButton + "\n";
   }

   public static String custom(List<?> values) {
      StringBuilder out = new StringBuilder("[");

      for (int i = 0; i < values.size(); i++) {
         if (i > 0) {
            out.append(',');
         }

         Object value = values.get(i);
         switch (value) {
            case null:
               out.append("null");
               break;
            case Boolean bool:
               out.append(bool);
               break;
            case Integer integer:
               out.append(integer);
               break;
            case Float real:
               out.append(real(real));
               break;
            case String string:
               quote(out, string);
               break;
            default:
               throw new IllegalArgumentException("Unsupported form value " + value);
         }
      }

      return out.append("]\n").toString();
   }

   static String real(float value) {
      if (!Float.isFinite(value)) {
         return "null";
      } else {
         String plain = new BigDecimal(Float.toString(value)).stripTrailingZeros().toPlainString();
         return plain.contains(".") ? plain : plain + ".0";
      }
   }

   private static void quote(StringBuilder out, String value) {
      out.append('"');

      for (int i = 0; i < value.length(); i++) {
         char c = value.charAt(i);
         switch (c) {
            case '\b':
               out.append("\\b");
               break;
            case '\t':
               out.append("\\t");
               break;
            case '\n':
               out.append("\\n");
               break;
            case '\f':
               out.append("\\f");
               break;
            case '\r':
               out.append("\\r");
               break;
            case '"':
               out.append("\\\"");
               break;
            case '\\':
               out.append("\\\\");
               break;
            default:
               if (c >= ' ' && c < 128) {
                  out.append(c);
               } else {
                  out.append(String.format("\\u%04x", Integer.valueOf(c)));
               }
         }
      }

      out.append('"');
   }
}
