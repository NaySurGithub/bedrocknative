package net.bedrocknative.particle;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.content.PackSource;
import net.bedrocknative.model.ModelJson;
import net.bedrocknative.model.entity.ModelRegistry;
import net.bedrocknative.molang.MolangCache;
import org.jspecify.annotations.Nullable;

public final class ParticleEffects {
   private final Map<String, ParticleEffect> byIdentifier;
   private final int files;
   private final int molangErrors;
   private final int unreadable;

   private ParticleEffects(Map<String, ParticleEffect> byIdentifier, int files, int molangErrors, int unreadable) {
      this.byIdentifier = byIdentifier;
      this.files = files;
      this.molangErrors = molangErrors;
      this.unreadable = unreadable;
   }

   public static ParticleEffects empty() {
      return new ParticleEffects(Map.of(), 0, 0, 0);
   }

   public static ParticleEffects load(List<? extends PackSource> topFirst, int serverPacks) {
      Map<String, ParticleEffect> effects = new HashMap<>();
      MolangCache molang = new MolangCache();
      int files = 0;
      int unreadable = 0;
      int count = topFirst.size();

      for (int index = count - 1; index >= 0; index--) {
         PackSource pack = topFirst.get(index);
         boolean server = index < serverPacks;
         ParticleEffectParser parser = new ParticleEffectParser(molang, ModelRegistry.packVersion(pack));

         for (String path : pack.list("particles")) {
            if (path.toLowerCase(Locale.ROOT).endsWith(".json")) {
               try {
                  Optional<byte[]> data = pack.read(path);
                  JsonObject json = null;
                  if (data.isPresent() && McbParticles.isMcb(data.get())) {
                     json = McbParticles.toJson(data.get());
                     if (json == null) {
                        unreadable++;
                     }
                  } else if (data.isPresent()) {
                     json = ModelJson.parse(data.get(), path + " in " + pack);
                  }

                  ParticleEffect effect = json != null ? parser.parse(json, count - 1 - index, server) : null;
                  if (effect != null) {
                     files++;
                     effects.put(effect.identifier(), effect);
                  }
               } catch (RuntimeException | IOException var16) {
                  BedrockNative.LOGGER.warn("Couldn't load Bedrock particle {} from {}: {}", new Object[]{path, pack, var16.toString()});
               }
            }
         }
      }

      return new ParticleEffects(Map.copyOf(effects), files, molang.errors(), unreadable);
   }

   @Nullable
   public ParticleEffect get(String identifier) {
      ParticleEffect effect = this.byIdentifier.get(identifier);
      return effect != null ? effect : this.byIdentifier.get(identifier.toLowerCase(Locale.ROOT));
   }

   public int size() {
      return this.byIdentifier.size();
   }

   public int files() {
      return this.files;
   }

   public int molangErrors() {
      return this.molangErrors;
   }

   public int unreadable() {
      return this.unreadable;
   }

   public Map<String, ParticleEffect> all() {
      return this.byIdentifier;
   }

   public boolean baseGame() {
      return this.byIdentifier.values().stream().anyMatch(effect -> !effect.serverPack());
   }
}
