package net.bedrocknative.ui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

public final class UiJson {
   private UiJson() {
   }

   public static JsonObject parseObject(byte[] data) throws UiJson.Error {
      JsonElement element = parse(data);
      if (!element.isJsonObject()) {
         throw new UiJson.Error("not a JSON object");
      } else {
         return element.getAsJsonObject();
      }
   }

   public static JsonElement parse(byte[] data) throws UiJson.Error {
      String text = new String(data, StandardCharsets.UTF_8);
      if (text.startsWith("\ufeff")) {
         text = text.substring(1);
      }

      try {
         JsonReader reader = new JsonReader(new StringReader(withoutTrailingCommas(text)));

         JsonElement var4;
         try {
            reader.setStrictness(Strictness.LENIENT);
            JsonElement element = JsonParser.parseReader(reader);
            if (reader.peek() != JsonToken.END_DOCUMENT) {
               throw new UiJson.Error("trailing content at " + reader);
            }

            var4 = element;
         } catch (Throwable var6) {
            try {
               reader.close();
            } catch (Throwable var5) {
               var6.addSuppressed(var5);
            }

            throw var6;
         }

         reader.close();
         return var4;
      } catch (IOException | IllegalStateException | JsonParseException var7) {
         throw new UiJson.Error(String.valueOf(var7.getMessage()));
      }
   }

   static String withoutTrailingCommas(String text) {
      char[] out = text.toCharArray();
      int length = out.length;
      int i = 0;

      while (i < length) {
         char c = out[i];
         if (c == '"' || c == '\'') {
            i = skipString(out, i);
         } else if (c == '/' && i + 1 < length && (out[i + 1] == '/' || out[i + 1] == '*') || c == '#') {
            i = skipComment(out, i);
         } else if (c != ',') {
            i++;
         } else {
            int next = i + 1;

            while (next < length) {
               char n = out[next];
               if (Character.isWhitespace(n)) {
                  next++;
               } else {
                  if ((n != '/' || next + 1 >= length || out[next + 1] != '/' && out[next + 1] != '*') && n != '#') {
                     break;
                  }

                  next = skipComment(out, next);
               }
            }

            if (next < length && (out[next] == '}' || out[next] == ']')) {
               out[i] = ' ';
            }

            i++;
         }
      }

      return new String(out);
   }

   private static int skipString(char[] text, int start) {
      char quote = text[start];
      int i = start + 1;

      while (i < text.length) {
         char c = text[i];
         if (c == '\\') {
            i += 2;
         } else {
            if (c == quote) {
               return i + 1;
            }

            i++;
         }
      }

      return text.length;
   }

   private static int skipComment(char[] text, int start) {
      if (text[start] != '#' && text[start + 1] != '/') {
         int i = start + 2;

         while (i + 1 < text.length && (text[i] != '*' || text[i + 1] != '/')) {
            i++;
         }

         return Math.min(text.length, i + 2);
      } else {
         int i = start;

         while (i < text.length && text[i] != '\n') {
            i++;
         }

         return i;
      }
   }

   public static final class Error extends Exception {
      public Error(String message) {
         super(message);
      }
   }
}
