package net.bedrocknative.model.entity;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.content.ArgbImage;
import net.bedrocknative.content.BedrockImages;
import net.bedrocknative.content.PackPaths;
import net.bedrocknative.content.PackSource;
import net.bedrocknative.content.PackStack;
import net.bedrocknative.model.ModelJson;
import net.bedrocknative.model.animation.AnimationDef;
import net.bedrocknative.model.animation.AnimationParser;
import net.bedrocknative.model.animation.ControllerDef;
import net.bedrocknative.model.animation.ControllerParser;
import net.bedrocknative.model.geometry.GeometryDef;
import net.bedrocknative.model.geometry.GeometryLibrary;
import net.bedrocknative.model.geometry.GeometryMesher;
import net.bedrocknative.model.geometry.MeshedGeometry;
import net.bedrocknative.model.geometry.TextureMesher;
import net.bedrocknative.model.render.MaterialTable;
import net.bedrocknative.model.render.RenderControllerDef;
import net.bedrocknative.model.render.RenderControllerParser;
import net.bedrocknative.molang.CameraCalls;
import net.bedrocknative.molang.MolangCache;
import net.bedrocknative.molang.MolangEnv;
import net.bedrocknative.molang.MolangProgram;
import net.bedrocknative.molang.MolangVersion;
import org.jspecify.annotations.Nullable;

public final class ModelRegistry {
   public static final int ENGINE_VERSION = engineVersion();
   private final MolangCache molang = new MolangCache();
   private final GeometryLibrary geometries = new GeometryLibrary();
   private final Map<String, MeshedGeometry> meshes = new ConcurrentHashMap<>();
   private final Map<String, AnimationDef> animations = new HashMap<>();
   private final Map<String, ControllerDef> controllers = new HashMap<>();
   private final Map<String, RenderControllerDef> renderControllers = new HashMap<>();
   private final MaterialTable materials = new MaterialTable();
   private final Map<String, ClientEntityDef> entities = new HashMap<>();
   private final Set<String> baseEntities = new HashSet<>();
   private final Map<String, ClientEntityDef> baseDefinitions = new HashMap<>();
   private final Map<String, ClientEntityDef> attachables = new HashMap<>();
   private final List<ClientEntityDef> itemAttachables = new ArrayList<>();
   private final Map<ClientEntityDef, Map<String, MeshedGeometry>> textureMeshed = new ConcurrentHashMap<>();
   private final Map<String, Object> images = new ConcurrentHashMap<>();
   private static final Object NO_IMAGE = new Object();
   private List<? extends PackSource> packs = List.of();
   private int files;

   private ModelRegistry() {
   }

   public static ModelRegistry load(PackStack stack, int serverPacks) {
      return load(stack.packs(), serverPacks);
   }

   public static ModelRegistry load(List<? extends PackSource> topFirst, int serverPacks) {
      ModelRegistry registry = new ModelRegistry();
      registry.packs = List.copyOf(topFirst);
      Map<String, List<ClientEntityDef>> entityCandidates = new HashMap<>();
      Map<String, List<ClientEntityDef>> attachableCandidates = new HashMap<>();
      int count = topFirst.size();

      for (int pack = 0; pack < count; pack++) {
         PackSource source = topFirst.get(count - 1 - pack);
         boolean server = count - 1 - pack < serverPacks;
         MolangVersion version = packVersion(source);
         registry.loadPack(source, pack, server, version, entityCandidates, attachableCandidates);
      }

      entityCandidates.forEach((identifier, candidates) -> {
         ClientEntityDef selected = select((List<ClientEntityDef>)candidates);
         if (selected != null) {
            registry.entities.put(identifier, selected);
         }

         if (candidates.stream().anyMatch(candidate -> !candidate.serverPack())) {
            registry.baseEntities.add(identifier);
            ClientEntityDef base = select(candidates.stream().filter(candidate -> !candidate.serverPack()).toList());
            if (base != null) {
               registry.baseDefinitions.put(identifier, base);
            }
         }
      });
      List<String> shared = new ArrayList<>();
      entityCandidates.forEach(
         (identifier, candidates) -> {
            if (candidates.size() > 1 && candidates.stream().anyMatch(ClientEntityDef::serverPack)) {
               ClientEntityDef chosen = registry.entities.get(identifier);
               shared.add(
                  identifier
                     + candidates.stream()
                        .map(
                           candidate -> " [pack "
                              + candidate.pack()
                              + ", min_engine "
                              + candidate.minEngineVersion()
                              + ", format "
                              + candidate.formatVersion()
                              + (candidate == chosen ? ", used" : "")
                              + "]"
                        )
                        .toList()
               );
            }
         }
      );
      if (!shared.isEmpty()) {
         BedrockNative.LOGGER.info("Client entities with several definitions in the stack (server packs involved): {}", shared);
      }

      attachableCandidates.forEach((identifier, candidates) -> {
         ClientEntityDef selected = select((List<ClientEntityDef>)candidates);
         if (selected != null) {
            registry.attachables.put(identifier, selected);
         }
      });
      registry.attachables.values().stream().filter(definition -> definition.items() != null).forEach(registry.itemAttachables::add);
      BedrockNative.LOGGER
         .info(
            "Bedrock models: {} files, {} entities, {} attachables, {} geometries, {} animations, {} controllers, {} render controllers; Molang: {} programs, {} errors",
            new Object[]{
               registry.files,
               registry.entities.size(),
               registry.attachables.size(),
               registry.geometries.size(),
               registry.animations.size(),
               registry.controllers.size(),
               registry.renderControllers.size(),
               registry.molang.size(),
               registry.molang.errors()
            }
         );
      CameraCalls.Survey camera = CameraCalls.survey(registry.molang.programs());
      if (camera.programs() > 0) {
         BedrockNative.LOGGER
            .info(
               "Molang camera queries with an axis other than 0 and 1, answered with the yaw as Blockbench does: {} programs, calls {}, e.g. {}",
               new Object[]{camera.programs(), camera.calls(), camera.examples()}
            );
      }

      return registry;
   }

   private void loadPack(
      PackSource source,
      int pack,
      boolean server,
      MolangVersion version,
      Map<String, List<ClientEntityDef>> entityCandidates,
      Map<String, List<ClientEntityDef>> attachableCandidates
   ) {
      this.each(source, "materials", ".material", this.materials::add);
      this.each(source, "models", ".json", json -> this.geometries.add(json, !server));
      AnimationParser animationParser = new AnimationParser(this.molang, version);
      this.each(source, "animations", ".json", json -> this.animations.putAll(animationParser.parse(json)));
      ControllerParser controllerParser = new ControllerParser(this.molang, version);
      this.each(source, "animation_controllers", ".json", json -> this.controllers.putAll(controllerParser.parse(json)));
      RenderControllerParser renderParser = new RenderControllerParser(this.molang, version);
      this.each(source, "render_controllers", ".json", json -> this.renderControllers.putAll(renderParser.parse(json)));
      DefinitionParser definitions = new DefinitionParser(this.molang, version);

      for (String folder : new String[]{"entity", "attachables"}) {
         this.each(
            source,
            folder,
            ".json",
            json -> {
               ClientEntityDef definition = definitions.parse(json, pack, server);
               if (definition != null) {
                  (definition.attachable() ? attachableCandidates : entityCandidates)
                     .computeIfAbsent(definition.identifier(), key -> new ArrayList<>())
                     .add(definition);
               }
            }
         );
      }
   }

   private void each(PackSource source, String folder, String extension, Consumer<JsonObject> action) {
      for (String path : source.list(folder)) {
         if (path.toLowerCase(Locale.ROOT).endsWith(extension)) {
            try {
               Optional<byte[]> data = source.read(path);
               JsonObject json = data.isPresent() ? ModelJson.parse(data.get(), path) : null;
               if (json != null) {
                  this.files++;
                  action.accept(json);
               }
            } catch (IOException | RuntimeException e) {
               BedrockNative.LOGGER.warn("Couldn't load Bedrock {} from {}: {}", new Object[]{path, source, e.toString()});
            }
         }
      }
   }

   private static @Nullable ClientEntityDef select(List<ClientEntityDef> candidates) {
      ClientEntityDef best = null;

      for (ClientEntityDef candidate : candidates) {
         if (candidate.minEngineVersion() <= ENGINE_VERSION
            && (
               best == null
                  || candidate.minEngineVersion() > best.minEngineVersion()
                  || candidate.minEngineVersion() == best.minEngineVersion() && candidate.pack() >= best.pack()
            )) {
            best = candidate;
         }
      }

      return best;
   }

   public static MolangVersion packVersion(PackSource source) {
      try {
         Optional<byte[]> manifest = source.read("manifest.json");
         JsonObject json = manifest.isPresent() ? ModelJson.parse(manifest.get(), "manifest.json") : null;
         JsonElement version = json != null && ModelJson.object(json, "header") != null ? ModelJson.object(json, "header").get("min_engine_version") : null;
         if (version instanceof JsonArray array && array.size() >= 2) {
            return MolangVersion.ofEngineVersion(
               MolangVersion.version(array.get(0).getAsInt(), array.get(1).getAsInt(), array.size() > 2 ? array.get(2).getAsInt() : 0)
            );
         }

         if (version != null && version.isJsonPrimitive()) {
            return MolangVersion.ofEngineVersion(version.getAsString());
         }
      } catch (IOException | RuntimeException e) {
         BedrockNative.LOGGER.debug("No readable manifest in {}: {}", source, e.toString());
      }

      return MolangVersion.BEFORE_VERSIONING;
   }

   private static int engineVersion() {
      return MolangVersion.parse("1.26.63");
   }

   public @Nullable ClientEntityDef entity(String identifier) {
      return this.entities.get(identifier);
   }

   public boolean baseGameDefines(String identifier) {
      return this.baseEntities.contains(identifier);
   }

   public @Nullable ClientEntityDef baseEntity(String identifier) {
      return this.baseDefinitions.get(identifier);
   }

   public boolean hasBaseGame() {
      return !this.baseEntities.isEmpty();
   }

   public boolean serverDefines(String identifier) {
      ClientEntityDef definition = this.entities.get(identifier);
      return definition != null && definition.serverPack() && !this.baseEntities.contains(identifier);
   }

   public Collection<ClientEntityDef> entities() {
      return Collections.unmodifiableCollection(this.entities.values());
   }

   public @Nullable ClientEntityDef attachableByIdentifier(String identifier) {
      return this.attachables.get(identifier);
   }

   public Collection<ClientEntityDef> attachables() {
      return Collections.unmodifiableCollection(this.attachables.values());
   }

   public @Nullable ClientEntityDef attachable(String itemId, MolangEnv env) {
      ClientEntityDef match = null;

      for (ClientEntityDef candidate : this.itemAttachables) {
         MolangProgram condition = candidate.items().get(itemId);
         if (condition != null && condition.test(env) && (match == null || candidate.pack() >= match.pack())) {
            match = candidate;
         }
      }

      if (match != null) {
         return match;
      }

      ClientEntityDef byIdentifier = this.attachables.get(itemId);
      return byIdentifier != null && byIdentifier.items() == null ? byIdentifier : null;
   }

   public @Nullable AnimationDef animation(String name) {
      return this.animations.get(name);
   }

   public @Nullable ControllerDef controller(String name) {
      return this.controllers.get(name);
   }

   public @Nullable RenderControllerDef renderController(String name) {
      return this.renderControllers.get(name);
   }

   public MaterialTable materials() {
      return this.materials;
   }

   public @Nullable GeometryDef geometry(String identifier) {
      return this.geometries.get(identifier);
   }

   public GeometryLibrary geometryLibrary() {
      return this.geometries;
   }

   public @Nullable MeshedGeometry mesh(String identifier) {
      MeshedGeometry cached = this.meshes.get(identifier);
      if (cached != null) {
         return cached;
      }

      GeometryDef geometry = this.geometries.get(identifier);
      return geometry == null ? null : this.meshes.computeIfAbsent(identifier, key -> GeometryMesher.mesh(geometry));
   }

   public @Nullable MeshedGeometry mesh(String identifier, ClientEntityDef definition) {
      GeometryDef geometry = this.geometries.get(identifier);
      return geometry != null && GeometryMesher.hasTextureMeshes(geometry)
         ? this.textureMeshed
            .computeIfAbsent(definition, key -> new ConcurrentHashMap<>())
            .computeIfAbsent(identifier, key -> GeometryMesher.mesh(geometry, name -> {
               String path = definition.texture(name);
               return path != null ? this.image(path) : null;
            }))
         : this.mesh(identifier);
   }

   public TextureMesher.@Nullable Pixels image(String path) {
      Object held = this.images.get(path);
      if (held == NO_IMAGE) {
         return null;
      }

      TextureMesher.Pixels pixels = held instanceof SoftReference<?> reference ? (TextureMesher.Pixels)reference.get() : null;
      if (pixels == null) {
         pixels = this.readImage(path);
         this.images.put(path, pixels == null ? NO_IMAGE : new SoftReference<>(pixels));
      }

      return pixels;
   }

   public ModelRegistry.Warmed warm(ClientEntityDef definition) {
      return this.warm(definition, true);
   }

   public ModelRegistry.Warmed warm(ClientEntityDef definition, boolean textures) {
      return this.warm(definition, textures ? Long.MAX_VALUE : 0L);
   }

   public ModelRegistry.Warmed warm(ClientEntityDef definition, long maxPixels) {
      int decoded = 0;
      long pixels = 0L;
      if (maxPixels > 0L) {
         for (String path : definition.textures().values()) {
            if (pixels >= maxPixels) {
               break;
            }

            TextureMesher.Pixels image = this.warmImage(path);
            if (image != null) {
               decoded++;
               pixels += (long)image.width() * image.height();
            }
         }
      }

      int meshed = 0;

      for (String identifier : definition.geometries().values()) {
         if (this.warmMesh(identifier, definition)) {
            meshed++;
         }
      }

      return new ModelRegistry.Warmed(decoded, pixels, meshed);
   }

   private TextureMesher.@Nullable Pixels warmImage(String path) {
      if (this.images.containsKey(path)) {
         return null;
      }

      TextureMesher.Pixels decoded;
      try {
         decoded = this.readImage(path);
      } catch (RuntimeException e) {
         return null;
      }

      return this.images.putIfAbsent(path, decoded == null ? NO_IMAGE : new SoftReference<>(decoded)) == null ? decoded : null;
   }

   private boolean warmMesh(String identifier, ClientEntityDef definition) {
      try {
         GeometryDef geometry = this.geometries.get(identifier);
         if (geometry == null) {
            return false;
         }

         if (!GeometryMesher.hasTextureMeshes(geometry)) {
            return this.meshes.containsKey(identifier) ? false : this.meshes.putIfAbsent(identifier, GeometryMesher.mesh(geometry)) == null;
         }

         Map<String, MeshedGeometry> own = this.textureMeshed.computeIfAbsent(definition, key -> new ConcurrentHashMap<>());
         if (own.containsKey(identifier)) {
            return false;
         }

         MeshedGeometry mesh = GeometryMesher.mesh(geometry, name -> {
            String path = definition.texture(name);
            return path != null ? this.image(path) : null;
         });
         return own.putIfAbsent(identifier, mesh) == null;
      } catch (RuntimeException e) {
         return false;
      }
   }

   public boolean imageCached(String path) {
      return this.images.containsKey(path);
   }

   public boolean meshCached(String identifier, ClientEntityDef definition) {
      Map<String, MeshedGeometry> own = this.textureMeshed.get(definition);
      return this.meshes.containsKey(identifier) || own != null && own.containsKey(identifier);
   }

   private TextureMesher.@Nullable Pixels readImage(String path) {
      if (PackPaths.escapes(path)) {
         return null;
      }

      for (PackSource pack : this.packs) {
         for (String extension : new String[]{".png", ".tga", ".jpg"}) {
            try {
               Optional<byte[]> data = pack.read(path + extension);
               if (!data.isEmpty()) {
                  ArgbImage image = BedrockImages.decode(data.get());
                  return new TextureMesher.Pixels(image.width(), image.height(), image.argb());
               }
            } catch (IOException | RuntimeException e) {
               BedrockNative.LOGGER.warn("Couldn't read the texture {}{} of {}: {}", new Object[]{path, extension, pack, e.toString()});
            }
         }
      }

      return null;
   }

   public MolangCache molang() {
      return this.molang;
   }

   public int files() {
      return this.files;
   }

   public record Warmed(int images, long pixels, int meshes) {
   }
}
