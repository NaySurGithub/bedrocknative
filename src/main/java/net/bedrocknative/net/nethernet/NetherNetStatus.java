package net.bedrocknative.net.nethernet;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.net.transport.ProbeResult;

public final class NetherNetStatus {
   private NetherNetStatus() {
   }

   public static ProbeResult.Status parse(String body) {
      if (body != null && !body.isBlank()) {
         try {
            JsonElement parsed = JsonParser.parseString(body);
            if (!parsed.isJsonObject()) {
               BedrockNative.LOGGER.warn("NetherNet status: the answer is not a JSON object ({} bytes), shown as empty", body.length());
               return ProbeResult.Status.EMPTY;
            } else {
               JsonObject json = parsed.getAsJsonObject();
               return new ProbeResult.Status(
                  text(json, "name"),
                  number(json, "protocol", 0),
                  text(json, "version"),
                  text(json, "level"),
                  number(json, "players", -1),
                  number(json, "maxPlayers", -1),
                  gameType(json)
               );
            }
         } catch (RuntimeException var3) {
            BedrockNative.LOGGER.warn("NetherNet status: {} ({} bytes), shown as empty", var3.getMessage(), body.length());
            return ProbeResult.Status.EMPTY;
         }
      } else {
         return ProbeResult.Status.EMPTY;
      }
   }

   private static String text(JsonObject json, String key) {
      JsonElement value = json.get(key);
      if (value != null && !value.isJsonNull()) {
         if (value instanceof JsonPrimitive primitive && primitive.isString()) {
            return primitive.getAsString();
         } else {
            throw new IllegalArgumentException("\"" + key + "\" is not a string");
         }
      } else {
         return "";
      }
   }

   private static int number(JsonObject json, String key, int absent) {
      JsonElement value = json.get(key);
      if (value != null && !value.isJsonNull()) {
         if (value instanceof JsonPrimitive primitive && primitive.isNumber()) {
            double number = primitive.getAsDouble();
            if (number == Math.rint(number) && number >= -2.1474836E9F && number <= 2.147483647E9) {
               return (int)number;
            }
         }

         throw new IllegalArgumentException("\"" + key + "\" is not a whole number");
      } else {
         return absent;
      }
   }

   private static String gameType(JsonObject json) {
      JsonElement value = json.get("gameType");
      if (value != null && !value.isJsonNull()) {
         if (value instanceof JsonPrimitive primitive && (primitive.isString() || primitive.isNumber())) {
            return primitive.isNumber() ? Integer.toString(number(json, "gameType", 0)) : primitive.getAsString();
         } else {
            throw new IllegalArgumentException("\"gameType\" is neither a number nor a string");
         }
      } else {
         return "";
      }
   }
}
