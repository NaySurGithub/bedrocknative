package net.bedrocknative.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;

public final class UiLength {
   public static final UiLength DEFAULT = new UiLength(UiLength.Kind.DEFAULT, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
   public static final UiLength FILL = new UiLength(UiLength.Kind.FILL, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
   public static final UiLength ZERO = px(0.0F);
   public static final UiLength FULL = new UiLength(UiLength.Kind.TERMS, 0.0F, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
   public final UiLength.Kind kind;
   public final float px;
   public final float parent;
   public final float children;
   public final float largestChild;
   public final float largestSibling;
   public final float width;
   public final float height;
   private static final Map<String, UiLength> PARSED = new ConcurrentHashMap<>();
   private static final int MAX_PARSED = 8192;
   private static final UiLength UNPARSED = new UiLength(UiLength.Kind.DEFAULT, Float.NaN, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);

   private UiLength(UiLength.Kind kind, float px, float parent, float children, float largestChild, float largestSibling, float width, float height) {
      this.kind = kind;
      this.px = px;
      this.parent = parent;
      this.children = children;
      this.largestChild = largestChild;
      this.largestSibling = largestSibling;
      this.width = width;
      this.height = height;
   }

   public static UiLength px(float pixels) {
      return new UiLength(UiLength.Kind.TERMS, pixels, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
   }

   @Nullable
   public static UiLength parse(@Nullable JsonElement element) {
      if (element != null && !element.isJsonNull()) {
         if (element instanceof JsonPrimitive primitive) {
            if (primitive.isNumber()) {
               return px(primitive.getAsFloat());
            }

            if (primitive.isString()) {
               return parse(primitive.getAsString());
            }
         }

         return null;
      } else {
         return null;
      }
   }

   @Nullable
   public static UiLength parse(UiValue value) {
      return switch (value.kind()) {
         case NUMBER -> px((float)value.asNumber());
         case STRING -> parse(value.asString());
         default -> null;
      };
   }

   @Nullable
   public static UiLength[] pair(UiValue value) {
      JsonArray array = value.array();
      if (array != null && array.size() == 2) {
         UiLength x = parse(array.get(0));
         UiLength y = parse(array.get(1));
         return x != null && y != null ? new UiLength[]{x, y} : null;
      } else {
         return null;
      }
   }

   @Nullable
   public static UiLength parse(String text) {
      UiLength known = PARSED.get(text);
      if (known == null) {
         UiLength parsed = parseText(text);
         known = parsed == null ? UNPARSED : parsed;
         if (PARSED.size() < 8192) {
            PARSED.put(text, known);
         }
      }

      return known == UNPARSED ? null : known;
   }

   @Nullable
   private static UiLength parseText(String text) {
      String s = text.trim();
      if (s.equalsIgnoreCase("default")) {
         return DEFAULT;
      } else if (s.equalsIgnoreCase("fill")) {
         return FILL;
      } else {
         float[] terms = new float[7];
         int i = 0;
         int length = s.length();
         boolean first = true;

         while (true) {
            i = skipSpaces(s, i);
            if (i >= length) {
               if (first) {
                  return null;
               }

               return new UiLength(UiLength.Kind.TERMS, terms[0], terms[1], terms[2], terms[3], terms[4], terms[5], terms[6]);
            }

            float sign = 1.0F;
            char c = s.charAt(i);
            if (c != '+' && c != '-') {
               if (!first) {
                  return null;
               }
            } else {
               sign = c == '-' ? -1.0F : 1.0F;
               i = skipSpaces(s, i + 1);
            }

            int start = i;

            while (i < length && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.')) {
               i++;
            }

            if (start == i) {
               return null;
            }

            float number;
            try {
               number = Float.parseFloat(s.substring(start, i)) * sign;
            } catch (NumberFormatException var11) {
               return null;
            }

            i = skipSpaces(s, i);
            int unit;
            if (s.startsWith("%cm", i)) {
               unit = 3;
               i += 3;
            } else if (s.startsWith("%sm", i)) {
               unit = 4;
               i += 3;
            } else if (s.startsWith("%c", i)) {
               unit = 2;
               i += 2;
            } else if (s.startsWith("%x", i)) {
               unit = 5;
               i += 2;
            } else if (s.startsWith("%y", i)) {
               unit = 6;
               i += 2;
            } else if (s.startsWith("%", i)) {
               unit = 1;
               i++;
            } else if (s.startsWith("px", i)) {
               unit = 0;
               i += 2;
            } else {
               unit = 0;
            }

            terms[unit] += unit == 0 ? number : number / 100.0F;
            first = false;
         }
      }
   }

   private static int skipSpaces(String s, int i) {
      while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
         i++;
      }

      return i;
   }

   public boolean isDefault() {
      return this.kind == UiLength.Kind.DEFAULT;
   }

   public boolean isFill() {
      return this.kind == UiLength.Kind.FILL;
   }

   public boolean usesChildren() {
      return this.kind == UiLength.Kind.TERMS && (this.children != 0.0F || this.largestChild != 0.0F);
   }

   public boolean usesSiblings() {
      return this.kind == UiLength.Kind.TERMS && this.largestSibling != 0.0F;
   }

   public boolean usesParent() {
      return this.kind != UiLength.Kind.TERMS || this.parent != 0.0F;
   }

   public boolean usesOther(boolean horizontal) {
      return this.kind == UiLength.Kind.TERMS && (horizontal ? this.height != 0.0F : this.width != 0.0F);
   }

   public static UiLength lerp(UiLength from, UiLength to, float t) {
      if (from.kind == UiLength.Kind.TERMS && to.kind == UiLength.Kind.TERMS) {
         return new UiLength(
            UiLength.Kind.TERMS,
            mix(from.px, to.px, t),
            mix(from.parent, to.parent, t),
            mix(from.children, to.children, t),
            mix(from.largestChild, to.largestChild, t),
            mix(from.largestSibling, to.largestSibling, t),
            mix(from.width, to.width, t),
            mix(from.height, to.height, t)
         );
      } else {
         return t < 1.0F ? from : to;
      }
   }

   private static float mix(float a, float b, float t) {
      return a + (b - a) * t;
   }

   @Override
   public String toString() {
      if (this.kind != UiLength.Kind.TERMS) {
         return this.kind.name().toLowerCase();
      } else {
         StringBuilder text = new StringBuilder();
         append(text, this.px, "px");
         append(text, this.parent * 100.0F, "%");
         append(text, this.children * 100.0F, "%c");
         append(text, this.largestChild * 100.0F, "%cm");
         append(text, this.largestSibling * 100.0F, "%sm");
         append(text, this.width * 100.0F, "%x");
         append(text, this.height * 100.0F, "%y");
         return text.isEmpty() ? "0px" : text.toString();
      }
   }

   private static void append(StringBuilder text, float value, String unit) {
      if (value != 0.0F) {
         if (!text.isEmpty()) {
            text.append(value < 0.0F ? " - " : " + ");
         } else if (value < 0.0F) {
            text.append('-');
         }

         text.append(UiValue.format(Math.abs(value))).append(unit);
      }
   }

   public static enum Kind {
      DEFAULT,
      FILL,
      TERMS;
   }
}
