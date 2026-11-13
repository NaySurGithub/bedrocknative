package net.bedrocknative.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record PackManifest(UUID uuid, String version, List<PackManifest.Subpack> subpacks) {
   public static PackManifest parse(byte[] json) throws IOException {
      try {
         String text = new String(json, StandardCharsets.UTF_8);
         if (text.startsWith("\ufeff")) {
            text = text.substring(1);
         }

         JsonObject root = JsonParser.parseString(text).getAsJsonObject();
         JsonObject header = root.getAsJsonObject("header");
         List<PackManifest.Subpack> subpacks = new ArrayList<>();
         JsonElement var6 = root.get("subpacks");
         if (var6 instanceof JsonArray) {
            for (JsonElement element : (JsonArray)var6) {
               if (element instanceof JsonObject subpack) {
                  JsonElement name = subpack.get("folder_name");
                  if (name instanceof JsonElement && name.isJsonPrimitive()) {
                     JsonElement namex = subpack.get("name");
                     subpacks.add(
                        new PackManifest.Subpack(
                           name.getAsString(),
                           namex != null && namex.isJsonPrimitive() ? namex.getAsString() : "",
                           tier(subpack.get("memory_performance_tier"))
                        )
                     );
                  }
               }
            }
         }

         return new PackManifest(UUID.fromString(header.get("uuid").getAsString().trim()), version(header.get("version")), List.copyOf(subpacks));
      } catch (RuntimeException var11) {
         throw new IOException("Invalid manifest.json: " + var11.getMessage(), var11);
      }
   }

   @Nullable
   public String subpackFolder(String selected) {
      String wanted = selected.trim().toLowerCase(Locale.ROOT);

      for (PackManifest.Subpack subpack : this.subpacks) {
         if (subpack.folder().toLowerCase(Locale.ROOT).equals(wanted)) {
            return subpack.folder();
         }
      }

      for (PackManifest.Subpack subpackx : this.subpacks) {
         if (subpackx.name().trim().toLowerCase(Locale.ROOT).equals(wanted)) {
            return subpackx.folder();
         }
      }

      return null;
   }

   @Nullable
   public String defaultSubpackFolder(int deviceTier) {
      if (this.subpacks.isEmpty()) {
         return null;
      } else {
         for (int i = this.subpacks.size() - 1; i >= 0; i--) {
            if (this.subpacks.get(i).tier() <= deviceTier) {
               return this.subpacks.get(i).folder();
            }
         }

         return this.subpacks.getFirst().folder();
      }
   }

   private static int tier(@Nullable JsonElement tier) {
      try {
         return tier != null && tier.isJsonPrimitive() ? Math.max(0, tier.getAsInt()) : 0;
      } catch (RuntimeException var2) {
         return 0;
      }
   }

   static String version(JsonElement version) {
      if (version.isJsonArray()) {
         List<String> parts = new ArrayList<>();
         version.getAsJsonArray().forEach(part -> parts.add(Integer.toString(part.getAsInt())));
         return String.join(".", parts);
      } else {
         return version.getAsString().trim();
      }
   }

   public static boolean sameVersion(String a, String b) {
      int[] left = BedrockInstall.version(a);
      int[] right = BedrockInstall.version(b);
      return left != null && right != null ? BedrockInstall.compare(left, right) == 0 : a.equals(b);
   }

   public record Subpack(String folder, String name, int tier) {
      public Subpack(String folder, String name) {
         this(folder, name, 0);
      }
   }
}
