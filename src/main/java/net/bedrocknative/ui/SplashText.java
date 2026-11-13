package net.bedrocknative.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.ToIntFunction;
import net.bedrocknative.content.PackStack;

public final class SplashText {
   public static final int COLOUR = -256;
   public static final float ROTATION = (float)Math.toRadians(-20.0);
   public static final int WRAP_WIDTH = 84;
   public static final int LINE_HEIGHT = 10;
   public static final float DROP = 2.0F;
   private static final String FILE = "splashes.json";

   private SplashText() {
   }

   public static List<String> load(PackStack stack) {
      Optional<PackStack.Resource> file = stack.read("splashes.json");
      if (file.isEmpty()) {
         return List.of();
      }

      try {
         if (JsonParser.parseString(new String(file.get().data(), StandardCharsets.UTF_8).replace("\ufeff", "")) instanceof JsonObject object
            && object.get("splashes") instanceof JsonArray array) {
            List<String> splashes = new ArrayList<>(array.size());

            for (JsonElement element : array) {
               if (element.isJsonPrimitive() && !element.getAsString().isBlank()) {
                  splashes.add(element.getAsString());
               }
            }

            return List.copyOf(splashes);
         } else {
            return List.of();
         }
      } catch (RuntimeException e) {
         return List.of();
      }
   }

   public static String pick(List<String> splashes, Random random) {
      return splashes.isEmpty() ? "" : splashes.get(random.nextInt(splashes.size()));
   }

   public static List<String> wrap(String text, ToIntFunction<String> width) {
      List<String> lines = new ArrayList<>();
      StringBuilder line = new StringBuilder();

      for (String word : text.trim().split("\\s+")) {
         if (!word.isEmpty()) {
            String tried = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && width.applyAsInt(tried) > 84) {
               lines.add(line.toString());
               line.setLength(0);
               line.append(word);
            } else {
               line.setLength(0);
               line.append(tried);
            }
         }
      }

      if (!line.isEmpty()) {
         lines.add(line.toString());
      }

      return lines;
   }

   public static float scale(long millis) {
      float phase = (float)(millis % 1000L) / 1000.0F * (float) (Math.PI * 2);
      return (1.8F - Math.abs((float)Math.sin(phase)) * 0.1F) / 1.8F;
   }
}
