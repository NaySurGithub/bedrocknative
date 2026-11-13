package net.bedrocknative.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.math.BigDecimal;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class UiValue {
   public static final UiValue NULL = new UiValue(UiValue.Kind.NULL, 0.0, null, null);
   public static final UiValue TRUE = new UiValue(UiValue.Kind.BOOL, 1.0, null, null);
   public static final UiValue FALSE = new UiValue(UiValue.Kind.BOOL, 0.0, null, null);
   public static final UiValue ZERO = new UiValue(UiValue.Kind.NUMBER, 0.0, null, null);
   public static final UiValue ONE = new UiValue(UiValue.Kind.NUMBER, 1.0, null, null);
   public static final UiValue EMPTY = new UiValue(UiValue.Kind.STRING, 0.0, "", null);
   private final UiValue.Kind kind;
   private final double number;
   @Nullable
   private final String string;
   @Nullable
   private final JsonElement json;
   @Nullable
   private float[] floats;

   private UiValue(UiValue.Kind kind, double number, @Nullable String string, @Nullable JsonElement json) {
      this.kind = kind;
      this.number = number;
      this.string = string;
      this.json = json;
   }

   public static UiValue of(boolean value) {
      return value ? TRUE : FALSE;
   }

   public static UiValue of(double value) {
      if (value == 0.0 && Double.doubleToRawLongBits(value) == 0L) {
         return ZERO;
      } else {
         return value == 1.0 ? ONE : new UiValue(UiValue.Kind.NUMBER, value, null, null);
      }
   }

   public static UiValue of(@Nullable String value) {
      if (value == null) {
         return NULL;
      } else {
         return value.isEmpty() ? EMPTY : new UiValue(UiValue.Kind.STRING, 0.0, value, null);
      }
   }

   public static UiValue of(@Nullable JsonElement json) {
      if (json == null || json.isJsonNull()) {
         return NULL;
      } else if (json instanceof JsonPrimitive primitive) {
         if (primitive.isBoolean()) {
            return of(primitive.getAsBoolean());
         } else {
            return primitive.isNumber() ? of(primitive.getAsDouble()) : of(primitive.getAsString());
         }
      } else {
         return new UiValue(json.isJsonArray() ? UiValue.Kind.ARRAY : UiValue.Kind.OBJECT, 0.0, null, json);
      }
   }

   public static UiValue array(double... values) {
      JsonArray array = new JsonArray();

      for (double value : values) {
         array.add(value);
      }

      return of(array);
   }

   public UiValue.Kind kind() {
      return this.kind;
   }

   public boolean isNull() {
      return this.kind == UiValue.Kind.NULL;
   }

   public boolean isString() {
      return this.kind == UiValue.Kind.STRING;
   }

   public boolean isNumber() {
      return this.kind == UiValue.Kind.NUMBER;
   }

   public boolean isBool() {
      return this.kind == UiValue.Kind.BOOL;
   }

   public boolean isArray() {
      return this.kind == UiValue.Kind.ARRAY;
   }

   public boolean isObject() {
      return this.kind == UiValue.Kind.OBJECT;
   }

   public double asNumber() {
      return switch (this.kind) {
         case BOOL, NUMBER -> this.number;
         case STRING -> parse(this.string);
         default -> 0.0;
      };
   }

   public boolean truthy() {
      return switch (this.kind) {
         case NULL -> false;
         case BOOL, NUMBER -> this.number != 0.0;
         case STRING -> !this.string.isEmpty();
         default -> true;
      };
   }

   public String asString() {
      return switch (this.kind) {
         case NULL -> "";
         case BOOL -> this.number != 0.0 ? "true" : "false";
         case NUMBER -> format(this.number);
         case STRING -> this.string;
         default -> this.json.toString();
      };
   }

   @Nullable
   public String stringOrNull() {
      return this.kind == UiValue.Kind.STRING ? this.string : null;
   }

   @Nullable
   public JsonArray array() {
      return this.kind == UiValue.Kind.ARRAY ? this.json.getAsJsonArray() : null;
   }

   @Nullable
   public JsonObject object() {
      return this.kind == UiValue.Kind.OBJECT ? this.json.getAsJsonObject() : null;
   }

   @Nullable
   public float[] floats() {
      if (this.kind != UiValue.Kind.ARRAY) {
         return null;
      } else {
         float[] cached = this.floats;
         if (cached == null) {
            JsonArray array = this.json.getAsJsonArray();
            cached = new float[array.size()];

            for (int i = 0; i < cached.length; i++) {
               cached[i] = (float)of(array.get(i)).asNumber();
            }

            this.floats = cached;
         }

         return cached;
      }
   }

   public JsonElement toJson() {
      return (JsonElement)(switch (this.kind) {
         case NULL -> JsonNull.INSTANCE;
         case BOOL -> new JsonPrimitive(this.number != 0.0);
         case NUMBER -> new JsonPrimitive(this.number);
         case STRING -> new JsonPrimitive(this.string);
         default -> this.json;
      });
   }

   public static String format(double value) {
      if (value == Math.rint(value) && Math.abs(value) < 1.0E15) {
         return Long.toString((long)value);
      } else {
         return !Double.isNaN(value) && !Double.isInfinite(value) ? BigDecimal.valueOf(value).stripTrailingZeros().toPlainString() : Double.toString(value);
      }
   }

   static double parse(String text) {
      String trimmed = text.trim();
      if (trimmed.isEmpty()) {
         return 0.0;
      } else {
         try {
            return Double.parseDouble(trimmed);
         } catch (NumberFormatException var3) {
            return 0.0;
         }
      }
   }

   @Override
   public boolean equals(Object other) {
      if (this == other) {
         return true;
      } else if (other instanceof UiValue value && value.kind == this.kind) {
         return switch (this.kind) {
            case NULL -> true;
            case BOOL, NUMBER -> Double.compare(this.number, value.number) == 0;
            case STRING -> this.string.equals(value.string);
            default -> this.json.equals(value.json);
         };
      } else {
         return false;
      }
   }

   @Override
   public int hashCode() {
      return switch (this.kind) {
         case NULL -> 0;
         case BOOL, NUMBER -> Double.hashCode(this.number) * 31 + this.kind.ordinal();
         case STRING -> this.string.hashCode();
         default -> Objects.hashCode(this.json);
      };
   }

   @Override
   public String toString() {
      return this.kind == UiValue.Kind.STRING ? "'" + this.string + "'" : this.asString();
   }

   public static enum Kind {
      NULL,
      BOOL,
      NUMBER,
      STRING,
      ARRAY,
      OBJECT;
   }
}
