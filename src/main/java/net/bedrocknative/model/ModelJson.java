package net.bedrocknative.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import net.bedrocknative.BedrockNative;
import org.jspecify.annotations.Nullable;

public final class ModelJson {
   private static final Gson PACKER = new GsonBuilder().serializeNulls().disableHtmlEscaping().setStrictness(Strictness.LENIENT).create();

   private ModelJson() {
   }

   public static @Nullable JsonObject parse(byte[] data, String what) {
      String text = new String(data, StandardCharsets.UTF_8);
      if (text.startsWith("\ufeff")) {
         text = text.substring(1);
      }

      try {
         JsonReader reader = new JsonReader(new StringReader(text));

         JsonObject var5;
         try {
            reader.setStrictness(Strictness.LENIENT);
            JsonElement element = JsonParser.parseReader(reader);
            var5 = element.isJsonObject() ? element.getAsJsonObject() : null;
         } catch (Throwable var7) {
            try {
               reader.close();
            } catch (Throwable var6) {
               var7.addSuppressed(var6);
            }

            throw var7;
         }

         reader.close();
         return var5;
      } catch (Exception e) {
         BedrockNative.LOGGER.warn("Unreadable Bedrock JSON {}: {}", what, e.getMessage());
         return null;
      }
   }

   public static byte[] pack(JsonObject json) {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream(1024);
      Deflater deflater = new Deflater(1);

      try (Writer writer = new OutputStreamWriter(new DeflaterOutputStream(bytes, deflater), StandardCharsets.UTF_8)) {
         PACKER.toJson(json, writer);
      } catch (IOException e) {
         throw new UncheckedIOException(e);
      } finally {
         deflater.end();
      }

      return bytes.toByteArray();
   }

   public static JsonObject unpack(byte[] packed) {
      Inflater inflater = new Inflater();

      JsonObject var3;
      try {
         JsonReader reader = new JsonReader(new InputStreamReader(new InflaterInputStream(new ByteArrayInputStream(packed), inflater), StandardCharsets.UTF_8));

         try {
            reader.setStrictness(Strictness.LENIENT);
            var3 = JsonParser.parseReader(reader).getAsJsonObject();
         } catch (Throwable var11) {
            try {
               reader.close();
            } catch (Throwable var10) {
               var11.addSuppressed(var10);
            }

            throw var11;
         }

         reader.close();
      } catch (IOException e) {
         throw new UncheckedIOException(e);
      } finally {
         inflater.end();
      }

      return var3;
   }

   public static @Nullable JsonObject object(@Nullable JsonObject parent, String key) {
      return parent != null && parent.get(key) instanceof JsonObject object ? object : null;
   }

   public static @Nullable JsonArray array(@Nullable JsonObject parent, String key) {
      return parent != null && parent.get(key) instanceof JsonArray array ? array : null;
   }

   public static @Nullable String string(@Nullable JsonObject parent, String key) {
      return parent != null && parent.get(key) instanceof JsonPrimitive primitive ? primitive.getAsString() : null;
   }

   public static boolean bool(@Nullable JsonObject parent, String key, boolean fallback) {
      if (!(parent != null && parent.get(key) instanceof JsonPrimitive primitive)) {
         return fallback;
      } else if (primitive.isBoolean()) {
         return primitive.getAsBoolean();
      } else {
         return primitive.isNumber() ? primitive.getAsDouble() != 0.0 : primitive.getAsString().equalsIgnoreCase("true");
      }
   }

   public static float number(@Nullable JsonElement element, float fallback) {
      if (element instanceof JsonPrimitive primitive) {
         if (primitive.isNumber()) {
            return primitive.getAsFloat();
         }

         if (primitive.isString()) {
            try {
               return Float.parseFloat(primitive.getAsString().trim());
            } catch (NumberFormatException ignored) {
               return fallback;
            }
         }

         if (primitive.isBoolean()) {
            return primitive.getAsBoolean() ? 1.0F : 0.0F;
         }
      }

      return element instanceof JsonArray array && array.size() == 1 ? number(array.get(0), fallback) : fallback;
   }

   public static float number(@Nullable JsonObject parent, String key, float fallback) {
      return parent != null ? number(parent.get(key), fallback) : fallback;
   }

   public static float[] vec(@Nullable JsonElement element, float[] fallback) {
      float[] result = (float[])fallback.clone();
      if (element instanceof JsonArray array) {
         for (int i = 0; i < Math.min(result.length, array.size()); i++) {
            result[i] = number(array.get(i), result[i]);
         }

         if (array.size() == 1) {
            for (int i = 1; i < result.length; i++) {
               result[i] = result[0];
            }
         }
      } else if (element instanceof JsonPrimitive) {
         float value = number(element, Float.NaN);
         if (!Float.isNaN(value)) {
            Arrays.fill(result, value);
         }
      }

      return result;
   }

   public static float[] vec(@Nullable JsonObject parent, String key, float[] fallback) {
      return parent != null ? vec(parent.get(key), fallback) : (float[])fallback.clone();
   }

   public static float @Nullable [] vecOrNull(@Nullable JsonObject parent, String key, int size) {
      return parent != null && parent.has(key) && !parent.get(key).isJsonNull() ? vec(parent.get(key), new float[size]) : null;
   }
}
