package net.bedrocknative.particle;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.util.Locale;
import net.bedrocknative.molang.MolangNames;
import net.bedrocknative.molang.MolangStruct;
import net.bedrocknative.molang.VariableStore;
import org.jspecify.annotations.Nullable;

public final class MolangVariablesJson {
   private MolangVariablesJson() {
   }

   public static int apply(@Nullable String json, VariableStore store) {
      if (json != null && !json.isBlank()) {
         JsonElement root;
         try {
            root = JsonParser.parseString(json);
         } catch (RuntimeException var19) {
            return 0;
         }

         JsonArray list = root instanceof JsonArray array
            ? array
            : (root instanceof JsonObject object && object.get("variables") instanceof JsonArray inner ? inner : null);
         if (list == null) {
            return 0;
         } else {
            int set = 0;

            for (JsonElement element : list) {
               if (element instanceof JsonObject entry && entry.get("name") instanceof JsonPrimitive name) {
                  String variable = strip(name.getAsString());
                  if (!variable.isEmpty()) {
                     JsonElement value = entry.get("value");
                     if (value instanceof JsonObject typed && "member_array".equalsIgnoreCase(text(typed.get("type")))) {
                        MolangStruct struct = new MolangStruct();
                        JsonElement var14 = typed.get("value");
                        if (var14 instanceof JsonArray) {
                           for (JsonElement member : (JsonArray)var14) {
                              if (member instanceof JsonObject field && field.get("name") instanceof JsonPrimitive memberName) {
                                 String key = memberName.getAsString();
                                 key = key.startsWith(".") ? key.substring(1) : key;
                                 struct.members().set(MolangNames.id(key), number(field.get("value")), null);
                              }
                           }
                        }

                        store.set(MolangNames.id(variable), 0.0F, struct);
                        set++;
                     } else if (value != null) {
                        store.set(MolangNames.id(variable), number(value), null);
                        set++;
                     }
                  }
               }
            }

            return set;
         }
      } else {
         return 0;
      }
   }

   private static String strip(String name) {
      String lower = name.toLowerCase(Locale.ROOT);
      if (lower.startsWith("variable.")) {
         return lower.substring(9);
      } else {
         return lower.startsWith("v.") ? lower.substring(2) : lower;
      }
   }

   @Nullable
   private static String text(@Nullable JsonElement element) {
      return element instanceof JsonPrimitive primitive ? primitive.getAsString() : null;
   }

   private static float number(@Nullable JsonElement element) {
      if (element instanceof JsonObject typed) {
         return number(typed.get("value"));
      } else if (element instanceof JsonPrimitive primitive) {
         if (primitive.isNumber()) {
            return primitive.getAsFloat();
         } else if (primitive.isBoolean()) {
            return primitive.getAsBoolean() ? 1.0F : 0.0F;
         } else {
            try {
               return Float.parseFloat(primitive.getAsString());
            } catch (NumberFormatException var3) {
               return 0.0F;
            }
         }
      } else {
         return 0.0F;
      }
   }
}
