package net.bedrocknative.client.net;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.Map.Entry;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.content.BaseContent;
import net.bedrocknative.content.BedrockInstall;
import net.fabricmc.loader.api.FabricLoader;

final class DevSkinPack {
   private static final String[] SKINS = new String[]{"steve.png", "alex.png"};

   private DevSkinPack() {
   }

   static Path folder() {
      Path install = BedrockInstall.dataFolder().resolve("skin_packs").resolve("vanilla");
      if (Files.isDirectory(install)) {
         return install;
      }

      Optional<Path> base = BaseContent.vanillaPack();
      if (base.isEmpty()) {
         return install;
      }

      Path made = FabricLoader.getInstance().getGameDir().resolve("dev-skin-pack");

      try {
         Files.createDirectories(made);

         for (String skin : SKINS) {
            Path image = base.get().resolve("textures").resolve("entity").resolve(skin);
            if (Files.isRegularFile(image)) {
               Files.copy(image, made.resolve(skin), StandardCopyOption.REPLACE_EXISTING);
            }
         }

         JsonObject mobs = JsonParser.parseString(Files.readString(base.get().resolve("models").resolve("mobs.json"), StandardCharsets.UTF_8))
            .getAsJsonObject();
         JsonObject geometry = new JsonObject();

         for (Entry<String, JsonElement> entry : mobs.entrySet()) {
            if (entry.getKey().equals("format_version") || entry.getKey().equals("geometry.humanoid") || entry.getKey().startsWith("geometry.humanoid.custom")) {
               geometry.add(entry.getKey(), entry.getValue());
            }
         }

         Files.writeString(made.resolve("geometry.json"), geometry.toString(), StandardCharsets.UTF_8);
         BedrockNative.LOGGER.info("Dev skin pack: no install, test skins from the base ({}): {}", BaseContent.describe(), made);
      } catch (IOException | RuntimeException e) {
         BedrockNative.LOGGER.warn("Dev skin pack: couldn't put one together from the base: {}", e.toString());
      }

      return made;
   }
}
