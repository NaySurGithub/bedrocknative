package net.bedrocknative.content;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.bedrocknative.BedrockNative;
import org.jspecify.annotations.Nullable;

public record BedrockOptions(int guiScaleModifier, boolean accessibilityScaling, @Nullable Path source) {
   public static final BedrockOptions DEFAULT = new BedrockOptions(0, false, null);
   public static final String MODIFIER_PROPERTY = "bedrocknative.uiScaleModifier";

   public static BedrockOptions read() {
      String forced = System.getProperty("bedrocknative.uiScaleModifier");
      if (forced != null) {
         try {
            return new BedrockOptions(Integer.parseInt(forced.trim()), false, null);
         } catch (NumberFormatException e) {
            BedrockNative.LOGGER.warn("Ignoring -D{}={}: not a whole number", "bedrocknative.uiScaleModifier", forced);
         }
      }

      Path newest = null;

      for (Path file : candidates()) {
         try {
            if (Files.isRegularFile(file) && (newest == null || Files.getLastModifiedTime(file).compareTo(Files.getLastModifiedTime(newest)) > 0)) {
               newest = file;
            }
         } catch (IOException var6) {
         }
      }

      if (newest == null) {
         return DEFAULT;
      }

      try {
         return parse(Files.readAllLines(newest, StandardCharsets.UTF_8), newest);
      } catch (IOException | RuntimeException e) {
         return DEFAULT;
      }
   }

   public static BedrockOptions parse(List<String> lines, @Nullable Path source) {
      int modifier = 0;
      boolean accessibility = false;

      for (String line : lines) {
         int colon = line.indexOf(58);
         if (colon > 0) {
            String key = line.substring(0, colon).trim();
            String value = line.substring(colon + 1).trim();

            try {
               switch (key) {
                  case "gfx_guiscale_offset":
                     modifier = Math.max(-8, Math.min(8, (int)Math.round(Double.parseDouble(value))));
                     break;
                  case "gfx_gui_accessibility_scaling":
                     accessibility = Double.parseDouble(value) != 0.0;
               }
            } catch (NumberFormatException var11) {
            }
         }
      }

      return new BedrockOptions(modifier, accessibility, source);
   }

   private static List<Path> candidates() {
      List<Path> files = new ArrayList<>();
      String appData = System.getenv("APPDATA");
      if (appData != null) {
         Path users = Path.of(appData, "Minecraft Bedrock", "Users");
         if (Files.isDirectory(users)) {
            try (Stream<Path> folders = Files.list(users)) {
               folders.forEach(folder -> files.add(folder.resolve("games/com.mojang/minecraftpe/options.txt")));
            } catch (IOException var8) {
            }
         }
      }

      String localAppData = System.getenv("LOCALAPPDATA");
      if (localAppData != null) {
         files.add(Path.of(localAppData, "Packages", "Microsoft.MinecraftUWP_8wekyb3d8bbwe", "LocalState", "games", "com.mojang", "minecraftpe", "options.txt"));
      }

      return files;
   }
}
