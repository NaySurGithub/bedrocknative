package net.bedrocknative.client.content;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Transparency;
import com.mojang.math.Quadrant;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.LogLists;
import net.bedrocknative.content.PackItemFiles;
import net.bedrocknative.content.PackSource;
import net.bedrocknative.content.PackStack;
import net.bedrocknative.content.TextureData;
import net.bedrocknative.world.block.BlockMappings;
import net.bedrocknative.world.item.CustomItem;
import net.bedrocknative.world.item.ItemMappings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.dispatch.BlockModelRotation;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.item.CuboidItemModelWrapper;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelBaker.Interner;
import net.minecraft.client.resources.model.cuboid.FaceBakery;
import net.minecraft.client.resources.model.cuboid.ItemModelGenerator;
import net.minecraft.client.resources.model.cuboid.CuboidFace.UVs;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.ItemQuads;
import net.minecraft.client.resources.model.geometry.BakedQuad.MaterialInfo;
import net.minecraft.client.resources.model.geometry.QuadCollection.Builder;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.cloudburstmc.nbt.NbtMap;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

public final class BedrockItemIcons {
   public static final Identifier ATLAS = Identifier.fromNamespaceAndPath("bedrocknative", "textures/atlas/bedrock_items.png");
   private static final Direction[] FACES = Direction.values();
   private static final Vector3fc CUBE_FROM = new Vector3f(0.0F, 0.0F, 0.0F);
   private static final Vector3fc CUBE_TO = new Vector3f(16.0F, 16.0F, 16.0F);
   private static final UVs FULL_FACE = new UVs(0.0F, 0.0F, 16.0F, 16.0F);
   private static final Interner AS_IS = new Interner() {
      public Vector3fc vector(Vector3fc vector) {
         return vector;
      }

      public MaterialInfo materialInfo(MaterialInfo material) {
         return material;
      }
   };
   private static final BedrockAtlas atlas = new BedrockAtlas(ATLAS);
   private static volatile Map<String, BedrockItemIcons.Icon> icons = Map.of();
   private static int generation;
   private static boolean active;

   private BedrockItemIcons() {
   }

   @Nullable
   public static BedrockItemIcons.Icon icon(String identifier) {
      return icons.get(identifier);
   }

   public static void reload() {
      active = true;
      int current = ++generation;
      List<CustomItem> items = List.copyOf(ItemMappings.customItems());
      PackStack stack = PackStack.active();
      Minecraft minecraft = Minecraft.getInstance();
      CompletableFuture.<BedrockItemIcons.Loaded>supplyAsync(() -> load(stack, items), Util.backgroundExecutor()).whenCompleteAsync((loaded, error) -> {
         if (error != null) {
            BedrockNative.LOGGER.warn("Couldn't load Bedrock item icons", error);
         } else if (current != generation) {
            loaded.close();
         } else {
            apply(loaded);
         }
      }, minecraft);
   }

   public static void packsChanged() {
      if (active) {
         reload();
      }
   }

   public static void release() {
      active = false;
      generation++;
      icons = Map.of();
      atlas.release();
   }

   private static BedrockItemIcons.Loaded load(PackStack stack, List<CustomItem> items) {
      Map<String, List<String>> itemTextures = TextureData.read(stack, "textures/item_texture.json");
      Map<String, List<String>> terrainTextures = null;
      Map<String, JsonElement> blocksJson = null;
      List<BedrockItemIcons.Request> requests = new ArrayList<>();
      Map<String, NativeImage> images = new HashMap<>();
      List<String> missing = new ArrayList<>();
      PackItemFiles itemFiles = null;

      for (CustomItem item : items) {
         BedrockItemIcons.Request request = null;
         String icon = item.icon();
         if (icon != null) {
            request = iconRequest(item.identifier(), icon, item.iconFrame(), itemTextures);
         } else if (item.block() != null) {
            if (terrainTextures == null) {
               terrainTextures = TextureData.read(stack, "textures/terrain_texture.json");
               blocksJson = blocksJson(stack);
            }

            request = blockRequest(item, terrainTextures, blocksJson);
         }

         if (request == null) {
            if (itemFiles == null) {
               itemFiles = PackItemFiles.read(stack);
            }

            PackItemFiles.Icon fromFile = itemFiles.icon(item.identifier());
            if (fromFile != null) {
               icon = fromFile.name();
               request = iconRequest(item.identifier(), icon, fromFile.frame(), itemTextures);
            }
         }

         if (request != null && request.paths().stream().allMatch(path -> images.containsKey(path) || read(stack, path, images))) {
            requests.add(request);
         } else {
            missing.add(item.identifier() + (icon != null ? " (" + icon + ")" : ""));
         }
      }

      if (itemFiles != null && itemFiles.unreadable() > 0) {
         BedrockNative.LOGGER.warn("Bedrock item files: {} of {} items/*.json files unreadable (skipped)", itemFiles.unreadable(), itemFiles.files());
      }

      BedrockNative.LOGGER
         .info(
            "Bedrock item icons: {} of {} custom items, {} images; none for {}",
            new Object[]{requests.size(), items.size(), images.size(), LogLists.summary(missing)}
         );
      return new BedrockItemIcons.Loaded(requests, images);
   }

   @Nullable
   private static BedrockItemIcons.Request iconRequest(String identifier, String icon, int frame, Map<String, List<String>> itemTextures) {
      List<String> paths = itemTextures.get(icon);
      return paths == null ? null : new BedrockItemIcons.Request(identifier, List.of(paths.get(Math.clamp((long)frame, 0, paths.size() - 1))), false);
   }

   private static boolean read(PackStack stack, String path, Map<String, NativeImage> images) {
      Optional<PackStack.Resource> resource = stack.texture(path);
      if (resource.isEmpty()) {
         return false;
      } else {
         try {
            images.put(path, BedrockTextures.decode(resource.get().data()));
            return true;
         } catch (RuntimeException | IOException var5) {
            BedrockNative.LOGGER.warn("Couldn't decode Bedrock texture {}", resource.get().path(), var5);
            return false;
         }
      }
   }

   @Nullable
   private static BedrockItemIcons.Request blockRequest(CustomItem item, Map<String, List<String>> terrain, Map<String, JsonElement> blocksJson) {
      NbtMap definition = BlockMappings.get().customBlock(item.block());
      NbtMap materials = definition == null
         ? NbtMap.EMPTY
         : definition.getCompound("components").getCompound("minecraft:material_instances").getCompound("materials");
      JsonElement entry = blocksJson.get(item.block());
      JsonElement textures = entry != null && entry.isJsonObject() ? entry.getAsJsonObject().get("textures") : null;
      List<String> paths = new ArrayList<>();

      for (Direction face : FACES) {
         String name = materialTexture(materials, face);
         if (name == null) {
            name = blocksJsonTexture(textures, face);
         }

         List<String> found = name == null ? null : terrain.get(name);
         if (found == null) {
            return null;
         }

         paths.add(found.getFirst());
      }

      return new BedrockItemIcons.Request(item.identifier(), List.copyOf(paths), true);
   }

   @Nullable
   private static String materialTexture(NbtMap materials, Direction face) {
      for (String key : faceKeys(face)) {
         String texture = materials.getCompound(key).getString("texture", "");
         if (!texture.isEmpty()) {
            return texture;
         }
      }

      return null;
   }

   @Nullable
   private static String blocksJsonTexture(@Nullable JsonElement textures, Direction face) {
      if (textures == null) {
         return null;
      } else if (textures.isJsonPrimitive()) {
         return textures.getAsString();
      } else {
         if (textures.isJsonObject()) {
            JsonObject byFace = textures.getAsJsonObject();

            for (String key : faceKeys(face)) {
               JsonElement var6 = byFace.get(key);
               if (var6 instanceof JsonElement && var6.isJsonPrimitive()) {
                  return var6.getAsString();
               }
            }
         }

         return null;
      }
   }

   private static List<String> faceKeys(Direction face) {
      return switch (face) {
         case UP -> List.of("up", "*");
         case DOWN -> List.of("down", "*");
         default -> List.of(face.getSerializedName(), "side", "*");
      };
   }

   private static Map<String, JsonElement> blocksJson(PackStack stack) {
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

   private static void apply(BedrockItemIcons.Loaded loaded) {
      Map<String, TextureAtlasSprite> sprites = atlas.stitch(loaded.images());
      Map<String, BedrockItemIcons.Icon> built = new LinkedHashMap<>();

      for (BedrockItemIcons.Request request : loaded.requests()) {
         Builder quads = new Builder();
         boolean animated = false;
         if (request.cube()) {
            for (int face = 0; face < FACES.length; face++) {
               TextureAtlasSprite sprite = sprites.get(request.paths().get(face));
               animated |= sprite.contents().isAnimated();
               quads.addUnculledFace(
                  FaceBakery.bakeQuad(AS_IS, CUBE_FROM, CUBE_TO, FULL_FACE, Quadrant.R0, material(sprite), FACES[face], BlockModelRotation.IDENTITY, null)
               );
            }
         } else {
            TextureAtlasSprite sprite = sprites.get(request.paths().getFirst());
            animated = sprite.contents().isAnimated();
            ItemModelGenerator.bakeExtrudedSprite(quads, AS_IS, BlockModelRotation.IDENTITY, material(sprite));
         }

         List<BakedQuad> all = quads.build().getAll();
         Vector3fc[] extents = CuboidItemModelWrapper.computeExtents(all);
         built.put(request.identifier(), new BedrockItemIcons.Icon(ItemQuads.split(all), () -> extents, animated, request.cube()));
      }

      icons = Map.copyOf(built);
   }

   private static MaterialInfo material(TextureAtlasSprite sprite) {
      Transparency transparency = sprite.transparency();
      boolean translucent = transparency.hasTranslucent();
      return new MaterialInfo(
         sprite,
         ChunkSectionLayer.byTransparency(transparency),
         translucent ? RenderTypes.itemTranslucent(ATLAS) : RenderTypes.itemCutout(ATLAS),
         translucent ? RenderTypes.itemTranslucentGlint(ATLAS) : RenderTypes.itemCutoutGlint(ATLAS),
         translucent ? RenderTypes.itemTranslucentGlintSpecial(ATLAS) : RenderTypes.itemCutoutGlintSpecial(ATLAS),
         -1,
         null,
         0
      );
   }

   public record Icon(ItemQuads quads, Supplier<Vector3fc[]> extents, boolean animated, boolean cube) {
      @Override
      public int hashCode() {
         return System.identityHashCode(this);
      }

      @Override
      public boolean equals(Object other) {
         return this == other;
      }
   }

   private record Loaded(List<BedrockItemIcons.Request> requests, Map<String, NativeImage> images) {
      void close() {
         this.images.values().forEach(NativeImage::close);
      }
   }

   private record Request(String identifier, List<String> paths, boolean cube) {
   }
}
