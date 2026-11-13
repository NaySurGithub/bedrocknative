package net.bedrocknative.oreui;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public final class OreFonts implements OreText.Fonts {
   private final OreInstall install;
   private final Map<String, Optional<OreFont>> loaded = new HashMap<>();

   public OreFonts(OreInstall install) {
      this.install = install;
   }

   @Nullable
   @Override
   public synchronized OreFont font(String family, int weight) {
      OreFont own = this.install.ownFont(family, weight);
      if (own != null) {
         return own;
      } else {
         String key = OreInstall.faceKey(family, weight, "normal");
         return this.loaded.computeIfAbsent(key, k -> {
            Optional<Path> file = this.install.font(family, weight, "normal");
            return file.map(path -> OreFont.load(family + (weight >= 600 ? " bold" : ""), path));
         }).orElse(null);
      }
   }

   @Nullable
   @Override
   public OreFont fallback() {
      return this.font("Noto Sans", 400);
   }

   public boolean complete() {
      return this.font("Minecraft Seven v2", 400) != null && this.font("Minecraft Ten v2", 400) != null;
   }
}
