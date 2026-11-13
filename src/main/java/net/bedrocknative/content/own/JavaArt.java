package net.bedrocknative.content.own;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.content.ArgbImage;
import net.bedrocknative.content.BedrockImages;
import org.jspecify.annotations.Nullable;

final class JavaArt {
   static final String LOGO = "assets/minecraft/textures/gui/title/minecraft.png";
   static final List<String> EFFECTS = List.of(
      "absorption",
      "bad_omen",
      "blindness",
      "breath_of_the_nautilus",
      "conduit_power",
      "darkness",
      "dolphins_grace",
      "fire_resistance",
      "glowing",
      "haste",
      "health_boost",
      "hero_of_the_village",
      "hunger",
      "infested",
      "instant_damage",
      "instant_health",
      "invisibility",
      "jump_boost",
      "levitation",
      "luck",
      "mining_fatigue",
      "nausea",
      "night_vision",
      "oozing",
      "poison",
      "raid_omen",
      "regeneration",
      "resistance",
      "saturation",
      "slow_falling",
      "slowness",
      "speed",
      "strength",
      "trial_omen",
      "unluck",
      "water_breathing",
      "weakness",
      "weaving",
      "wind_charged",
      "wither"
   );
   static final Map<String, String> EFFECT_ALIASES = Map.of("village_hero", "hero_of_the_village", "fatal_poison", "poison");

   private JavaArt() {
   }

   static void register(Map<String, Supplier<OwnArt.Texture>> into) {
      into.put("textures/ui/title", () -> {
         ArgbImage logo = read("assets/minecraft/textures/gui/title/minecraft.png");
         return logo == null ? null : OwnArt.Texture.plain(cropToContent(logo));
      });

      for (String effect : EFFECTS) {
         into.put("textures/ui/" + effect + "_effect", () -> effectIcon(effect));
      }

      EFFECT_ALIASES.forEach((bedrock, java) -> into.put("textures/ui/" + bedrock + "_effect", () -> effectIcon(java)));
   }

   @Nullable
   private static OwnArt.Texture effectIcon(String javaName) {
      ArgbImage icon = read("assets/minecraft/textures/mob_effect/" + javaName + ".png");
      return icon == null ? null : OwnArt.Texture.plain(icon);
   }

   @Nullable
   static ArgbImage read(String resource) {
      try {
         ArgbImage var2;
         try (InputStream in = JavaArt.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
               return null;
            }

            var2 = BedrockImages.decode(BedrockImages.readChecked(in, 67108864));
         }

         return var2;
      } catch (RuntimeException | IOException var6) {
         BedrockNative.LOGGER.debug("Own menus: Java's {} couldn't be read", resource, var6);
         return null;
      }
   }

   static ArgbImage cropToContent(ArgbImage image) {
      int x0 = image.width();
      int y0 = image.height();
      int x1 = -1;
      int y1 = -1;

      for (int y = 0; y < image.height(); y++) {
         for (int x = 0; x < image.width(); x++) {
            if (image.pixel(x, y) >>> 24 != 0) {
               x0 = Math.min(x0, x);
               y0 = Math.min(y0, y);
               x1 = Math.max(x1, x);
               y1 = Math.max(y1, y);
            }
         }
      }

      return x1 < 0 ? image : image.crop(x0, y0, x1 - x0 + 1, y1 - y0 + 1);
   }
}
