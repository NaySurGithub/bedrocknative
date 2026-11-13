package net.bedrocknative.client.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.content.BedrockTextures;
import net.bedrocknative.model.entity.ModelRegistry;
import net.bedrocknative.model.geometry.TextureMesher;
import net.bedrocknative.model.render.MaterialTable;
import net.bedrocknative.model.render.TextureComposer;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

public final class ComposedTextures {
   private static final Map<ComposedTextures.Key, Optional<ComposedTextures.Entry>> CACHE = new HashMap<>();
   private static final List<Identifier> MADE = new ArrayList<>();
   private static int counter;
   private static int generation;

   private ComposedTextures() {
   }

   static int generation() {
      return generation;
   }

   @Nullable
   static ComposedTextures.Entry get(
      MaterialTable.MaterialInfo material, @Nullable String base, @Nullable String layer1, @Nullable String layer2, int tint, int tint2
   ) {
      if (base == null) {
         return null;
      } else {
         boolean tinted = material.colorMask()
            || material.defines().contains("COLOR_SECOND_TEXTURE")
            || material.defines().contains("MULTIPLICATIVE_TINT_COLOR");
         ComposedTextures.Key key = new ComposedTextures.Key(
            material.name(), base, layer1, layer2, tinted ? tint : -1, material.defines().contains("MULTIPLICATIVE_TINT_COLOR") ? tint2 : -1
         );
         Optional<ComposedTextures.Entry> cached = CACHE.get(key);
         if (cached == null) {
            cached = Optional.ofNullable(make(material, key));
            CACHE.put(key, cached);
         }

         return cached.orElse(null);
      }
   }

   @Nullable
   private static ComposedTextures.Entry make(MaterialTable.MaterialInfo material, ComposedTextures.Key key) {
      ModelRegistry registry = BedrockModels.registry();
      if (registry == null) {
         return null;
      } else {
         try {
            TextureMesher.Pixels[] layers = new TextureMesher.Pixels[]{
               image(registry, key.base()),
               key.layer1() != null ? image(registry, key.layer1()) : null,
               key.layer2() != null ? image(registry, key.layer2()) : null
            };
            TextureComposer.Composed composed = TextureComposer.compose(material, layers, key.tint(), key.tint2());
            if (composed == null) {
               return null;
            } else {
               int id = counter++;
               String label = key.material() + " " + key.base();
               Identifier base = BedrockTextures.registerArgb("composed/" + id, composed.width(), composed.height(), composed.base(), label);
               MADE.add(base);
               Identifier emissive = null;
               if (composed.emissive() != null) {
                  emissive = BedrockTextures.registerArgb(
                     "composed/" + id + "_emissive", composed.width(), composed.height(), composed.emissive(), label + " (emissive)"
                  );
                  MADE.add(emissive);
               }

               return new ComposedTextures.Entry(base, emissive);
            }
         } catch (RuntimeException var9) {
            BedrockNative.LOGGER.warn("Couldn't compose the Bedrock texture {} for material {}", new Object[]{key.base(), key.material(), var9});
            return null;
         }
      }
   }

   @Nullable
   private static TextureMesher.Pixels image(ModelRegistry registry, String path) {
      return SkinTextures.handles(path) ? SkinTextures.pixels(path) : registry.image(path);
   }

   static void releaseBase(String prefix) {
      boolean any = false;
      Iterator<Map.Entry<ComposedTextures.Key, Optional<ComposedTextures.Entry>>> entries = CACHE.entrySet().iterator();

      while (entries.hasNext()) {
         Map.Entry<ComposedTextures.Key, Optional<ComposedTextures.Entry>> entry = entries.next();
         if (entry.getKey().base().startsWith(prefix)) {
            entries.remove();
            entry.getValue().ifPresent(made -> {
               BedrockTextures.releaseAfterTick(made.base());
               MADE.remove(made.base());
               if (made.emissive() != null) {
                  BedrockTextures.releaseAfterTick(made.emissive());
                  MADE.remove(made.emissive());
               }
            });
            any = true;
         }
      }

      if (any) {
         generation++;
      }
   }

   public static void clear() {
      MADE.forEach(BedrockTextures::releaseEntityTexture);
      MADE.clear();
      CACHE.clear();
      generation++;
   }

   public static int size() {
      return MADE.size();
   }

   public record Entry(Identifier base, @Nullable Identifier emissive) {
   }

   private record Key(String material, String base, @Nullable String layer1, @Nullable String layer2, int tint, int tint2) {
   }
}
