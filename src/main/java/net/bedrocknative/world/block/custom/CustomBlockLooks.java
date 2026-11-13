package net.bedrocknative.world.block.custom;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.content.FlipbookData;
import net.bedrocknative.content.PackSource;
import net.bedrocknative.content.PackStack;
import net.bedrocknative.content.TextureData;
import org.jspecify.annotations.Nullable;

public final class CustomBlockLooks {
   public static final String FLIPBOOKS = "textures/flipbook_textures.json";
   public static final String DEFAULT_SOUND = "normal";

   private CustomBlockLooks() {
   }

   public static CustomBlockLooks.Loaded load(PackStack stack, CustomBlockSet set) {
      BlockProblems problems = new BlockProblems();
      Map<String, List<String>> terrain = TextureData.read(stack, "textures/terrain_texture.json");
      FlipbookData flipbooks = FlipbookData.read(stack);
      Map<String, JsonElement> blocksJson = blocksJson(stack);
      Set<String> wanted = new HashSet<>();
      boolean culling = false;

      for (int slot = 0; slot < set.size(); slot++) {
         CustomBlockState state = set.data(slot);
         if (!state.geometry().builtIn()) {
            wanted.add(state.geometry().identifier());
         }

         culling |= !state.geometry().culling().isEmpty();
      }

      CustomBlockLooks.GeometryScan scan = geometries(stack, wanted);
      Map<String, List<BlockCullingRules.Rule>> rules = culling ? BlockCullingRules.read(stack) : Map.of();
      CustomBlockLooks.Hints hints = new CustomBlockLooks.Hints(stack, scan);
      CustomBlockLooks.Look[] looks = new CustomBlockLooks.Look[set.size()];
      String[] soundTypes = new String[set.size()];
      Map<String, CustomBlockLooks.Texture> textures = new LinkedHashMap<>();
      Map<String, Set<String>> users = new HashMap<>();
      Map<String, Boolean> opaque = new HashMap<>();
      Map<String, BlockItemDisplay> itemDisplays = new HashMap<>();
      Map<List<String>, List<String>> sharedKeys = new HashMap<>();
      Map<List<List<BlockCullingRules.Check>>, List<List<BlockCullingRules.Check>>> sharedCulls = new HashMap<>();

      for (int slot = 0; slot < set.size(); slot++) {
         CustomBlockState state = set.data(slot);
         String block = state.state().name();
         BlockGeometry geometry = scan.found().get(state.geometry().identifier());
         if (geometry != null && geometry.itemDisplayTransforms() != null && set.firstSlot(block) == slot) {
            itemDisplays.put(block, BlockItemDisplay.of(geometry.itemDisplayTransforms()));
         }

         if (!state.geometry().builtIn() && geometry == null) {
            String detail = "geometry " + state.geometry().identifier();
            String unreadable = hints.geometry();
            problems.add(
               block,
               unreadable != null ? BlockProblems.Reason.ENCRYPTED_PACK : BlockProblems.Reason.GEOMETRY_MISSING,
               unreadable != null ? detail + " in no pack read (" + unreadable + "); drawn as a full block" : detail + "; drawn as a full block"
            );
         }

         CustomBlockState built = state;
         BlockGeometry builtGeometry = geometry;

         List<CustomBlockModel.ModelQuad> quads;
         try {
            quads = CustomBlockModel.build(state, geometry);
         } catch (RuntimeException var34) {
            BedrockNative.LOGGER.warn("Couldn't build the model of {}", state, var34);
            built = CustomBlockState.read(state.state(), Map.of());
            builtGeometry = null;
            quads = CustomBlockModel.build(built, null);
         }

         List<BlockCullingRules.Rule> blockRules = List.of();
         if (!state.geometry().culling().isEmpty()) {
            blockRules = rules.get(state.geometry().culling());
            if (blockRules == null) {
               problems.add(block, BlockProblems.Reason.CULLING_MISSING, "culling " + state.geometry().culling() + " in no block_culling file; nothing culled");
               blockRules = List.of();
            }
         }

         JsonElement entry = blocksJson.get(block);
         List<String> keys = new ArrayList<>(quads.size());
         List<List<BlockCullingRules.Check>> culls = new ArrayList<>(quads.size());

         for (CustomBlockModel.ModelQuad quad : quads) {
            BlockMaterial material = quad.material();
            if (material.unknownRenderMethod() != null) {
               problems.add(block, BlockProblems.Reason.UNKNOWN_RENDER_METHOD, "render method '" + material.unknownRenderMethod() + "'; drawn as opaque");
            }

            String key = textureKey(block, quad, material, entry, terrain, flipbooks, textures, hints, problems);
            if (key != null) {
               users.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(block);
               opaque.merge(key, !material.renderMethod().cutout() && !material.renderMethod().translucent(), Boolean::logicalAnd);
            }

            keys.add(key);
            culls.add(checks(state, quad, blockRules));
         }

         looks[slot] = new CustomBlockLooks.Look(
            sharedKeys.computeIfAbsent(keys, Collections::unmodifiableList),
            sharedCulls.computeIfAbsent(culls, List::copyOf),
            built,
            builtGeometry != null ? builtGeometry.withoutItemDisplayTransforms() : null
         );
         soundTypes[slot] = soundType(state, entry);
      }

      for (String blockx : set.overflowBlocks()) {
         problems.add(blockx, BlockProblems.Reason.NO_CARRIER_SLOT, "past the 65536 carrier states; shows the stone placeholder");
      }

      Map<String, CustomBlockLooks.Texture> finished = new LinkedHashMap<>();
      textures.forEach(
         (keyx, texture) -> finished.put(
            keyx,
            new CustomBlockLooks.Texture(
               keyx,
               texture.path(),
               texture.flipbook(),
               texture.still(),
               opaque.getOrDefault(keyx, false),
               texture.flipbook() == null && flipbooks.paths().contains(texture.path())
            )
         )
      );
      Map<String, Set<String>> frozenUsers = new HashMap<>();
      users.forEach((keyx, blocks) -> frozenUsers.put(keyx, Collections.unmodifiableSet((Set<? extends String>)blocks)));
      return new CustomBlockLooks.Loaded(
         looks, Collections.unmodifiableMap(finished), Collections.unmodifiableMap(frozenUsers), soundTypes, problems, Map.copyOf(itemDisplays)
      );
   }

   @Nullable
   private static String textureKey(
      String block,
      CustomBlockModel.ModelQuad quad,
      BlockMaterial material,
      @Nullable JsonElement entry,
      Map<String, List<String>> terrain,
      FlipbookData flipbooks,
      Map<String, CustomBlockLooks.Texture> textures,
      CustomBlockLooks.Hints hints,
      BlockProblems problems
   ) {
      String name = material.texture();
      if (name.isEmpty()) {
         name = blocksJsonTexture(entry, quad.face());
      }

      if (name == null) {
         problems.add(block, BlockProblems.Reason.NO_TEXTURE_NAMED, "face " + quad.face() + ": the material names no texture and blocks.json has none");
         return null;
      } else {
         List<String> paths = terrain.get(name);
         String still = paths != null && !paths.isEmpty() ? paths.getFirst() : null;
         FlipbookData.Flipbook flipbook = flipbooks.get(name, 0);
         if (flipbook != null) {
            String key = "flipbook " + name;
            textures.putIfAbsent(key, new CustomBlockLooks.Texture(key, flipbook.texture(), flipbook, still, false, false));
            return key;
         } else if (still != null) {
            textures.putIfAbsent(still, new CustomBlockLooks.Texture(still, still, null, null, false, false));
            return still;
         } else {
            String unreadable = hints.terrain();
            if (unreadable != null) {
               problems.add(
                  block,
                  BlockProblems.Reason.ENCRYPTED_PACK,
                  "short name '" + name + "' (face " + quad.face() + ") in no terrain_texture.json read (" + unreadable + ")"
               );
            } else {
               problems.add(block, BlockProblems.Reason.TEXTURE_KEY_MISSING, "short name '" + name + "' (face " + quad.face() + ") in no terrain_texture.json");
            }

            return null;
         }
      }
   }

   private static List<BlockCullingRules.Check> checks(CustomBlockState state, CustomBlockModel.ModelQuad quad, List<BlockCullingRules.Rule> rules) {
      if (!rules.isEmpty() && !quad.bone().isEmpty()) {
         List<BlockCullingRules.Check> checks = new ArrayList<>();

         for (BlockCullingRules.Rule rule : rules) {
            if (rule.matches(quad)) {
               checks.add(new BlockCullingRules.Check(CustomBlockModel.worldDirection(state, rule.direction()), rule.condition(), rule.againstFullAndOpaque()));
            }
         }

         return List.copyOf(checks);
      } else {
         return List.of();
      }
   }

   static String soundType(CustomBlockState state, @Nullable JsonElement entry) {
      if (state.soundType() != null) {
         return state.soundType();
      } else {
         if (entry instanceof JsonObject object) {
            JsonElement var4 = object.get("sound");
            if (var4 instanceof JsonElement && var4.isJsonPrimitive() && !var4.getAsString().isBlank()) {
               return var4.getAsString().trim().toLowerCase(Locale.ROOT);
            }
         }

         return "normal";
      }
   }

   // $VF: One or more variable merging failures!
   // $VF: Could not properly define all variable types!
   // Please report this to the Vineflower issue tracker, at https://github.com/Vineflower/vineflower/issues with a copy of the class file (if you have the rights to distribute it!)
   @Nullable
   static String blocksJsonTexture(@Nullable JsonElement entry, String face) {
      JsonElement textures = entry instanceof JsonObject object ? object.get("textures") : null;
      if (textures == null) {
         return null;
      } else if (textures.isJsonPrimitive()) {
         return textures.getAsString();
      } else if (textures instanceof JsonObject byFace) {
         <unknown> var11;
         for (String key : var11_1) {
            JsonElement var8 = byFace.get(key);
            if (var8 instanceof JsonElement && var8.isJsonPrimitive()) {
               return var8.getAsString();
            }
         }

         return null;
      } else {
         return null;
      }
   }

   static Map<String, JsonElement> blocksJson(PackStack stack) {
      Map<String, JsonElement> blocks = new HashMap<>();

      for (PackSource pack : stack.packs()) {
         try {
            Optional<byte[]> data = pack.read("blocks.json");
            JsonObject root = data.isEmpty() ? null : TextureData.json(data.get(), "blocks.json in " + pack);
            if (root != null) {
               root.entrySet()
                  .forEach(
                     entry -> blocks.putIfAbsent(
                        ((String)entry.getKey()).contains(":") ? (String)entry.getKey() : "minecraft:" + (String)entry.getKey(), (JsonElement)entry.getValue()
                     )
                  );
            }
         } catch (IOException var6) {
            BedrockNative.LOGGER.warn("Couldn't read blocks.json from {}", pack, var6);
         }
      }

      return blocks;
   }

   static CustomBlockLooks.GeometryScan geometries(PackStack stack, Set<String> wanted) {
      Map<String, BlockGeometry> found = new HashMap<>();
      List<String> unreadable = new ArrayList<>();
      if (wanted.isEmpty()) {
         return new CustomBlockLooks.GeometryScan(found, unreadable);
      } else {
         for (PackSource pack : stack.packs()) {
            for (String file : pack.list("models")) {
               if (found.keySet().containsAll(wanted)) {
                  return new CustomBlockLooks.GeometryScan(found, unreadable);
               }

               if (file.toLowerCase(Locale.ROOT).endsWith(".json")) {
                  try {
                     Optional<byte[]> data = pack.read(file);
                     if (!data.isEmpty()) {
                        if (!looksLikeJson(data.get())) {
                           unreadable.add(file + " in " + pack);
                        } else {
                           String text = new String(data.get(), StandardCharsets.UTF_8);
                           if (!wanted.stream().noneMatch(text::contains)) {
                              JsonObject root = TextureData.json(data.get(), file + " in " + pack);
                              if (root != null) {
                                 BlockGeometry.parseFile(root).forEach((id, geometry) -> {
                                    if (wanted.contains(id)) {
                                       found.putIfAbsent(id, geometry);
                                    }
                                 });
                              }
                           }
                        }
                     }
                  } catch (RuntimeException | IOException var11) {
                     unreadable.add(file + " in " + pack);
                     BedrockNative.LOGGER.warn("Couldn't read geometry {} from {}", new Object[]{file, pack, var11});
                  }
               }
            }
         }

         return new CustomBlockLooks.GeometryScan(found, unreadable);
      }
   }

   static boolean looksLikeJson(byte[] data) {
      int start = data.length >= 3 && (data[0] & 255) == 239 && (data[1] & 255) == 187 && (data[2] & 255) == 191 ? 3 : 0;

      for (int i = start; i < data.length; i++) {
         int c = data[i] & 255;
         if (c != 32 && c != 9 && c != 13 && c != 10) {
            return c == 123 || c == 91 || c == 47;
         }
      }

      return false;
   }

   record GeometryScan(Map<String, BlockGeometry> found, List<String> unreadable) {
   }

   private static final class Hints {
      private final PackStack stack;
      private final CustomBlockLooks.GeometryScan scan;
      @Nullable
      private String terrain;
      @Nullable
      private String geometry;
      private boolean terrainChecked;
      private boolean geometryChecked;

      Hints(PackStack stack, CustomBlockLooks.GeometryScan scan) {
         this.stack = stack;
         this.scan = scan;
      }

      @Nullable
      String terrain() {
         if (!this.terrainChecked) {
            this.terrainChecked = true;
            List<String> unreadable = new ArrayList<>(PackStack.skippedServerPacks());
            unreadable.addAll(TextureData.unreadable(this.stack, "textures/terrain_texture.json"));
            this.terrain = describe(unreadable);
         }

         return this.terrain;
      }

      @Nullable
      String geometry() {
         if (!this.geometryChecked) {
            this.geometryChecked = true;
            List<String> unreadable = new ArrayList<>(PackStack.skippedServerPacks());
            if (!this.scan.unreadable().isEmpty()) {
               unreadable.add(this.scan.unreadable().size() + " model files not readable, e.g. " + this.scan.unreadable().getFirst());
            }

            this.geometry = describe(unreadable);
         }

         return this.geometry;
      }

      @Nullable
      private static String describe(List<String> unreadable) {
         if (unreadable.isEmpty()) {
            return null;
         } else {
            return unreadable.size() <= 2
               ? String.join("; ", unreadable)
               : unreadable.getFirst() + "; " + unreadable.get(1) + "; +" + (unreadable.size() - 2) + " more";
         }
      }
   }

   public record Loaded(
      CustomBlockLooks.Look[] bySlot,
      Map<String, CustomBlockLooks.Texture> textures,
      Map<String, Set<String>> users,
      String[] soundTypes,
      BlockProblems problems,
      Map<String, BlockItemDisplay> itemDisplays
   ) {
      public void releaseQuads() {
         for (CustomBlockLooks.Look look : this.bySlot) {
            look.releaseQuads();
         }
      }
   }

   public static final class Look {
      private final List<String> textures;
      private final List<List<BlockCullingRules.Check>> culls;
      private final CustomBlockState state;
      @Nullable
      private final BlockGeometry geometry;
      private List<CustomBlockModel.ModelQuad> quads = List.of();
      private boolean released = true;

      public Look(List<String> textures, List<List<BlockCullingRules.Check>> culls, CustomBlockState state, @Nullable BlockGeometry geometry) {
         this.textures = textures;
         this.culls = culls;
         this.state = state;
         this.geometry = geometry;
      }

      public synchronized List<CustomBlockModel.ModelQuad> quads() {
         if (this.released) {
            this.released = false;
            this.quads = List.copyOf(CustomBlockModel.build(this.state, this.geometry));
         }

         return this.quads;
      }

      public List<String> textures() {
         return this.textures;
      }

      public List<List<BlockCullingRules.Check>> culls() {
         return this.culls;
      }

      public synchronized void releaseQuads() {
         this.released = true;
         this.quads = List.of();
      }
   }

   public record Texture(String key, String path, @Nullable FlipbookData.Flipbook flipbook, @Nullable String still, boolean opaque, boolean firstFrame) {
   }
}
