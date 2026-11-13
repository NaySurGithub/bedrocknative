package net.bedrocknative.net.transport;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.net.InetSocketAddress;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public record JavaStatus(
   boolean answered, @Nullable InetSocketAddress address, String motd, int online, int max, String version, int protocol, long latencyMillis, String problem
) {
   private static final Map<String, Character> COLOURS = Map.ofEntries(
      Map.entry("black", '0'),
      Map.entry("dark_blue", '1'),
      Map.entry("dark_green", '2'),
      Map.entry("dark_aqua", '3'),
      Map.entry("dark_red", '4'),
      Map.entry("dark_purple", '5'),
      Map.entry("gold", '6'),
      Map.entry("gray", '7'),
      Map.entry("dark_gray", '8'),
      Map.entry("blue", '9'),
      Map.entry("green", 'a'),
      Map.entry("aqua", 'b'),
      Map.entry("red", 'c'),
      Map.entry("light_purple", 'd'),
      Map.entry("yellow", 'e'),
      Map.entry("white", 'f')
   );
   private static final int MAX_DEPTH = 32;
   static final int MAX_JSON_NESTING = 64;

   public static JavaStatus none(@Nullable InetSocketAddress address, String problem) {
      return new JavaStatus(false, address, "", -1, -1, "", 0, 0L, problem);
   }

   public static JavaStatus parse(String json, InetSocketAddress address, long latencyMillis) {
      if (tooDeep(json)) {
         return none(address, "not a Java status (JSON nested too deep)");
      }

      JsonElement root;
      try {
         root = JsonParser.parseString(json);
      } catch (JsonParseException | StackOverflowError e) {
         return none(address, "not a Java status (unreadable JSON)");
      }

      if (root instanceof JsonObject object) {
         String motd = object.has("description") ? flatten(object.get("description"), 0) : "";
         int online = -1;
         int max = -1;
         if (object.get("players") instanceof JsonObject players) {
            online = number(players.get("online"), -1);
            max = number(players.get("max"), -1);
         }

         String version = "";
         int protocol = 0;
         if (object.get("version") instanceof JsonObject named) {
            version = named.get("name") instanceof JsonPrimitive name && name.isString() ? name.getAsString() : "";
            protocol = number(named.get("protocol"), 0);
         }

         return new JavaStatus(true, address, motd, online, max, version, protocol, latencyMillis, "");
      } else {
         return none(address, "not a Java status (no JSON object)");
      }
   }

   static boolean tooDeep(String json) {
      int depth = 0;
      boolean inString = false;

      for (int i = 0; i < json.length(); i++) {
         char c = json.charAt(i);
         if (inString) {
            if (c == '\\') {
               i++;
            } else if (c == '"') {
               inString = false;
            }
         } else if (c == '"') {
            inString = true;
         } else if (c == '{' || c == '[') {
            if (++depth > 64) {
               return true;
            }
         } else if (c == '}' || c == ']') {
            depth--;
         }
      }

      return false;
   }

   private static int number(@Nullable JsonElement element, int fallback) {
      if (element instanceof JsonPrimitive primitive && primitive.isNumber()) {
         try {
            return primitive.getAsInt();
         } catch (NumberFormatException e) {
            return fallback;
         }
      } else {
         return fallback;
      }
   }

   static String flatten(@Nullable JsonElement element, int depth) {
      if (element == null || depth > 32) {
         return "";
      } else if (element instanceof JsonPrimitive primitive) {
         return primitive.getAsString();
      } else {
         StringBuilder text = new StringBuilder();
         if (element instanceof JsonArray) {
            for (JsonElement part : (JsonArray)element) {
               text.append(flatten(part, depth + 1));
            }

            return text.toString();
         } else {
            if (element instanceof JsonObject object) {
               StringBuilder codes = new StringBuilder();
               if (object.get("color") instanceof JsonPrimitive colour && colour.isString()) {
                  Character code = COLOURS.get(colour.getAsString());
                  if (code != null) {
                     codes.append('§').append(code);
                  }
               }

               style(object, "obfuscated", 'k', codes);
               style(object, "bold", 'l', codes);
               style(object, "strikethrough", 'm', codes);
               style(object, "underlined", 'n', codes);
               style(object, "italic", 'o', codes);
               text.append(codes);
               if (object.get("text") instanceof JsonPrimitive own) {
                  text.append(own.getAsString());
               } else if (object.get("translate") instanceof JsonPrimitive key) {
                  text.append(key.getAsString());
               }

               JsonElement var16 = object.get("extra");
               if (var16 instanceof JsonArray) {
                  for (JsonElement part : (JsonArray)var16) {
                     text.append(codes).append(flatten(part, depth + 1));
                  }
               }
            }

            return text.toString();
         }
      }
   }

   private static void style(JsonObject object, String name, char code, StringBuilder codes) {
      if (object.get(name) instanceof JsonPrimitive flag && flag.isBoolean() && flag.getAsBoolean()) {
         codes.append('§').append(code);
      }
   }

   public String plainMotd() {
      String line = this.motd.lines().findFirst().orElse("");
      return line.replaceAll("§.", "").strip();
   }

   public boolean countsKnown() {
      return this.online >= 0 && this.max >= 0;
   }

   @Override
   public String toString() {
      String at = this.address == null
         ? "?"
         : (this.address.getAddress() == null ? this.address.getHostString() : this.address.getAddress().getHostAddress()) + ":" + this.address.getPort();
      return !this.answered
         ? "Java status of " + at + ": none (" + this.problem + ")"
         : "Java status of "
            + at
            + ": "
            + this.version
            + " (protocol "
            + this.protocol
            + ")"
            + (this.countsKnown() ? ", " + this.online + "/" + this.max + " players" : "")
            + ", "
            + this.latencyMillis
            + " ms";
   }
}
