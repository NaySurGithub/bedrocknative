package net.bedrocknative.client.content;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.content.ArgbImage;
import net.bedrocknative.content.BedrockImages;
import net.bedrocknative.content.BedrockTextureMap;
import net.bedrocknative.content.LegacyTextureNames;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.atlas.SpriteResourceLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackFormat;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jspecify.annotations.Nullable;

final class LegacyJavaPacks {
   private static final Pattern OLD_REFERENCE = Pattern.compile("(minecraft:)?(blocks|items)/[a-z0-9_./-]+");
   private static final int MAX_MODEL_REFERENCES = 4096;
   static final int MAX_MODEL_BYTES = 1048576;
   private final ResourceManager manager;
   private final List<PackResources> packs;
   private final Map<PackResources, Integer> order;
   private final LegacyJavaPacks.Strips strips = new LegacyJavaPacks.Strips();

   private LegacyJavaPacks(ResourceManager manager, List<PackResources> packs, Map<PackResources, Integer> order) {
      this.manager = manager;
      this.packs = packs;
      this.order = order;
   }

   LegacyJavaPacks.Strips strips() {
      return this.strips;
   }

   static LegacyJavaPacks of(ResourceManager manager) {
      List<PackResources> all = manager.listPacks().toList();
      Map<PackResources, Integer> order = new IdentityHashMap<>();
      List<PackResources> legacy = new ArrayList<>();

      for (int i = 0; i < all.size(); i++) {
         PackResources pack = all.get(i);
         order.put(pack, i);
         if (JavaResourcePacks.isPlayers(pack) && isLegacy(pack)) {
            legacy.addFirst(pack);
         }
      }

      return new LegacyJavaPacks(manager, legacy, order);
   }

   static boolean isLegacy(PackResources pack) {
      try {
         PackMetadataSection meta = (PackMetadataSection)pack.getMetadataSection(PackMetadataSection.CLIENT_TYPE);
         return meta != null && LegacyTextureNames.isLegacyFormat(((PackFormat)meta.supportedFormats().maxInclusive()).major());
      } catch (Exception var2) {
         return false;
      }
   }

   boolean isEmpty() {
      return this.packs.isEmpty();
   }

   List<String> ids() {
      return this.packs.stream().<String>map(PackResources::packId).toList();
   }

   @Nullable
   LegacyJavaPacks.Found find(List<LegacyTextureNames.Candidate> candidates, @Nullable Resource shown) {
      int shownPlace = shown != null && JavaResourcePacks.isPlayers(shown) ? this.order.getOrDefault(shown.source(), -1) : -1;

      for (PackResources pack : this.packs) {
         if (this.order.getOrDefault(pack, -1) < shownPlace) {
            return null;
         }

         for (LegacyTextureNames.Candidate candidate : candidates) {
            Identifier file = Identifier.tryBuild("minecraft", candidate.file());
            if (file != null && pack.getResource(PackType.CLIENT_RESOURCES, file) != null) {
               Resource resource = this.resourceOf(pack, file);
               if (resource != null) {
                  return new LegacyJavaPacks.Found(resource, candidate, pack.packId(), this.strips);
               }
            }
         }
      }

      return null;
   }

   Map<Identifier, LegacyJavaPacks.Found> modelSprites(BedrockTextureMap.Atlas atlas) {
      String prefix = atlas == BedrockTextureMap.Atlas.BLOCKS ? "blocks/" : "items/";
      Map<Identifier, LegacyJavaPacks.Found> sprites = new LinkedHashMap<>();

      for (PackResources pack : this.packs) {
         List<String> references = new ArrayList<>();
         pack.listResources(
            PackType.CLIENT_RESOURCES,
            "minecraft",
            "models",
            (id, supplier) -> {
               if (id.getPath().endsWith(".json") && references.size() < 4096) {
                  try {
                     try (InputStream input = (InputStream)supplier.get()) {
                        byte[] json = input.readNBytes(1048577);
                        if (json.length <= 1048576) {
                           if (JsonParser.parseString(new String(json, StandardCharsets.UTF_8)) instanceof JsonObject model
                              && model.get("textures") instanceof JsonObject textures) {
                              for (Entry<String, JsonElement> texture : textures.entrySet()) {
                                 if (texture.getValue().isJsonPrimitive() && OLD_REFERENCE.matcher(texture.getValue().getAsString()).matches()) {
                                    references.add(texture.getValue().getAsString());
                                 }
                              }
                           }

                           return;
                        }

                        BedrockNative.LOGGER.debug("Legacy model {} in {} is over {} bytes, not read", new Object[]{id, pack.packId(), 1048576});
                     }

                     return;
                  } catch (Exception var12x) {
                     BedrockNative.LOGGER.debug("Unreadable legacy model {} in {}", new Object[]{id, pack.packId(), var12x});
                  }
               }
            }
         );

         for (String reference : references) {
            Identifier sprite = Identifier.tryParse(reference);
            if (sprite != null && sprite.getPath().startsWith(prefix) && !sprites.containsKey(sprite)) {
               for (PackResources owner : this.packs) {
                  LegacyJavaPacks.Found found = this.findIn(owner, "textures/" + sprite.getPath() + ".png");
                  if (found != null) {
                     sprites.put(sprite, found);
                     break;
                  }
               }
            }
         }
      }

      return sprites;
   }

   @Nullable
   private LegacyJavaPacks.Found findIn(PackResources pack, String path) {
      Identifier file = Identifier.tryBuild("minecraft", path);
      if (file != null && pack.getResource(PackType.CLIENT_RESOURCES, file) != null) {
         Resource resource = this.resourceOf(pack, file);
         return resource == null
            ? null
            : new LegacyJavaPacks.Found(resource, new LegacyTextureNames.Candidate(path, LegacyTextureNames.Route.TABLE, 0, 0), pack.packId(), this.strips);
      } else {
         return null;
      }
   }

   @Nullable
   private Resource resourceOf(PackResources pack, Identifier file) {
      for (Resource resource : this.manager.getResourceStack(file)) {
         if (resource.source() == pack) {
            return resource;
         }
      }

      return null;
   }

   record Found(Resource resource, LegacyTextureNames.Candidate candidate, String packId, LegacyJavaPacks.Strips strips) {
      @Nullable
      SpriteContents load(SpriteResourceLoader loader, Identifier id) {
         if (!this.candidate.strip()) {
            return loader.loadSprite(id, this.resource);
         } else {
            try {
               ArgbImage strip = this.strips.decoded(this.packId + "/" + this.candidate.file(), this.resource);
               int frames = Math.max(1, strip.height() / strip.width());
               int frame = Math.min(frames - 1, this.candidate.frame() * frames / this.candidate.frames());
               return BedrockSpriteSource.still(id, strip.crop(0, frame * strip.width(), strip.width(), strip.width()));
            } catch (Exception var6) {
               BedrockNative.LOGGER
                  .warn(
                     "Couldn't read frame {} of the legacy pack strip {} for {}",
                     new Object[]{this.candidate.frame(), this.resource.sourcePackId() + "/" + this.candidate.file(), id, var6}
                  );
               return null;
            }
         }
      }
   }

   static final class Strips {
      private final Map<String, CompletableFuture<ArgbImage>> decoded = new ConcurrentHashMap<>();
      private final AtomicInteger decodes = new AtomicInteger();

      ArgbImage decoded(String key, Resource resource) throws IOException {
         CompletableFuture<ArgbImage> mine = new CompletableFuture<>();
         CompletableFuture<ArgbImage> first = this.decoded.putIfAbsent(key, mine);
         if (first != null) {
            try {
               return first.join();
            } catch (CompletionException var11) {
               throw var11.getCause() instanceof IOException io ? io : new IOException(var11.getCause());
            }
         } else {
            try {
               ArgbImage var7;
               try (InputStream input = resource.open()) {
                  this.decodes.incrementAndGet();
                  ArgbImage image = BedrockImages.decode(BedrockImages.readChecked(input, 67108864));
                  mine.complete(image);
                  var7 = image;
               }

               return var7;
            } catch (RuntimeException | IOException var10) {
               mine.completeExceptionally(var10);
               throw var10;
            }
         }
      }

      int decodes() {
         return this.decodes.get();
      }
   }
}
