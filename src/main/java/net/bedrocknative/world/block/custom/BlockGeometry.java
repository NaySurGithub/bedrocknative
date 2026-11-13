package net.bedrocknative.world.block.custom;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import org.jspecify.annotations.Nullable;

public record BlockGeometry(
   String identifier, float textureWidth, float textureHeight, List<BlockGeometry.Bone> bones, @Nullable JsonObject itemDisplayTransforms
) {
   public BlockGeometry(String identifier, float textureWidth, float textureHeight, List<BlockGeometry.Bone> bones) {
      this(identifier, textureWidth, textureHeight, bones, null);
   }

   public BlockGeometry withoutItemDisplayTransforms() {
      return this.itemDisplayTransforms == null ? this : new BlockGeometry(this.identifier, this.textureWidth, this.textureHeight, this.bones, null);
   }

   public static Map<String, BlockGeometry> parseFile(JsonObject root) {
      Map<String, BlockGeometry> found = new LinkedHashMap<>();
      JsonElement var3 = root.get("minecraft:geometry");
      if (var3 instanceof JsonArray) {
         for (JsonElement element : (JsonArray)var3) {
            if (element instanceof JsonObject geometry && geometry.get("description") instanceof JsonObject description) {
               JsonElement var17 = description.get("identifier");
               if (var17 instanceof JsonElement && var17.isJsonPrimitive()) {
                  found.put(
                     var17.getAsString(),
                     new BlockGeometry(
                        var17.getAsString(),
                        number(description, "texture_width", 16.0F),
                        number(description, "texture_height", 16.0F),
                        bones(geometry.get("bones")),
                        geometry.get("item_display_transforms") instanceof JsonObject display ? display : null
                     )
                  );
               }
            }
         }
      }

      Map<String, String> parents = new HashMap<>();

      for (Entry<String, JsonElement> entry : root.entrySet()) {
         if (entry.getKey().startsWith("geometry.") && entry.getValue() instanceof JsonObject legacy) {
            String[] names = entry.getKey().split(":", 2);
            found.put(
               names[0], new BlockGeometry(names[0], number(legacy, "texturewidth", 64.0F), number(legacy, "textureheight", 64.0F), bones(legacy.get("bones")))
            );
            if (names.length == 2) {
               parents.put(names[0], names[1]);
            }
         }
      }

      parents.forEach((child, parent) -> {
         BlockGeometry base = found.get(parent);
         BlockGeometry own = found.get(child);
         if (base != null && own != null) {
            found.put(child, own.inherit(base));
         }
      });
      return found;
   }

   private BlockGeometry inherit(BlockGeometry parent) {
      Map<String, BlockGeometry.Bone> merged = new LinkedHashMap<>();
      parent.bones.forEach(bonex -> merged.put(bonex.name().toLowerCase(Locale.ROOT), bonex));

      for (BlockGeometry.Bone bone : this.bones) {
         String key = bone.name().toLowerCase(Locale.ROOT);
         BlockGeometry.Bone base = merged.get(key);
         merged.put(
            key,
            base != null && bone.cubes().isEmpty()
               ? new BlockGeometry.Bone(bone.name(), bone.parent(), bone.pivot(), bone.rotation(), bone.mirror(), bone.inflate(), base.cubes())
               : bone
         );
      }

      return new BlockGeometry(
         this.identifier,
         this.textureWidth,
         this.textureHeight,
         List.copyOf(merged.values()),
         this.itemDisplayTransforms != null ? this.itemDisplayTransforms : parent.itemDisplayTransforms
      );
   }

   private static List<BlockGeometry.Bone> bones(@Nullable JsonElement element) {
      List<BlockGeometry.Bone> bones = new ArrayList<>();
      if (!(element instanceof JsonArray array)) {
         return bones;
      } else {
         for (JsonElement entry : array) {
            if (entry instanceof JsonObject bone) {
               boolean mirror = bool(bone, "mirror");
               float inflate = number(bone, "inflate", 0.0F);
               List<BlockGeometry.Cube> cubes = new ArrayList<>();
               if (bone.get("cubes") instanceof JsonArray cubeArray && !bool(bone, "neverRender")) {
                  for (JsonElement cubeElement : cubeArray) {
                     if (cubeElement instanceof JsonObject cube) {
                        cubes.add(cube(cube, mirror, inflate));
                     }
                  }
               }

               JsonElement var15 = bone.get("parent");
               String parent = var15 instanceof JsonElement && var15.isJsonPrimitive() ? var15.getAsString() : null;
               bones.add(
                  new BlockGeometry.Bone(
                     string(bone, "name", ""), parent, vector(bone, "pivot", 0.0F), vector(bone, "rotation", 0.0F), mirror, inflate, List.copyOf(cubes)
                  )
               );
            }
         }

         return bones;
      }
   }

   private static BlockGeometry.Cube cube(JsonObject cube, boolean boneMirror, float boneInflate) {
      float[] boxUv = null;
      Map<String, BlockGeometry.Face> faces = new HashMap<>();
      JsonElement uv = cube.get("uv");
      if (uv instanceof JsonArray array && array.size() >= 2) {
         boxUv = new float[]{array.get(0).getAsFloat(), array.get(1).getAsFloat()};
      } else if (uv instanceof JsonObject perFace) {
         for (Entry<String, JsonElement> entry : perFace.entrySet()) {
            if (entry.getValue() instanceof JsonObject face) {
               float[] corner = vector2(face.get("uv"));
               float[] size = face.has("uv_size") ? vector2(face.get("uv_size")) : null;
               JsonElement var15 = face.get("material_instance");
               String instance = var15 instanceof JsonElement && var15.isJsonPrimitive() ? var15.getAsString() : null;
               faces.put(
                  entry.getKey().toLowerCase(Locale.ROOT),
                  new BlockGeometry.Face(corner[0], corner[1], size, Math.round(number(face, "uv_rotation", 0.0F)), instance)
               );
            }
         }
      } else {
         boxUv = new float[]{0.0F, 0.0F};
      }

      return new BlockGeometry.Cube(
         vector(cube, "origin", 0.0F),
         vector(cube, "size", 0.0F),
         cube.has("pivot") ? vector(cube, "pivot", 0.0F) : null,
         vector(cube, "rotation", 0.0F),
         number(cube, "inflate", boneInflate),
         cube.has("mirror") ? bool(cube, "mirror") : boneMirror,
         boxUv,
         Map.copyOf(faces)
      );
   }

   private static float[] vector(JsonObject object, String key, float fallback) {
      return object.get(key) instanceof JsonArray array && array.size() >= 3
         ? new float[]{array.get(0).getAsFloat(), array.get(1).getAsFloat(), array.get(2).getAsFloat()}
         : new float[]{fallback, fallback, fallback};
   }

   private static float[] vector2(@Nullable JsonElement element) {
      return element instanceof JsonArray array && array.size() >= 2
         ? new float[]{array.get(0).getAsFloat(), array.get(1).getAsFloat()}
         : new float[]{0.0F, 0.0F};
   }

   private static float number(JsonObject object, String key, float fallback) {
      JsonElement value = object.get(key);

      try {
         return value != null && value.isJsonPrimitive() ? value.getAsFloat() : fallback;
      } catch (NumberFormatException var5) {
         return fallback;
      }
   }

   private static boolean bool(JsonObject object, String key) {
      JsonElement value = object.get(key);
      return value != null && value.isJsonPrimitive() && value.getAsBoolean();
   }

   private static String string(JsonObject object, String key, String fallback) {
      JsonElement value = object.get(key);
      return value != null && value.isJsonPrimitive() ? value.getAsString() : fallback;
   }

   public record Bone(String name, @Nullable String parent, float[] pivot, float[] rotation, boolean mirror, float inflate, List<BlockGeometry.Cube> cubes) {
   }

   public record Cube(
      float[] origin,
      float[] size,
      @Nullable float[] pivot,
      float[] rotation,
      float inflate,
      boolean mirror,
      @Nullable float[] boxUv,
      Map<String, BlockGeometry.Face> faces
   ) {
   }

   public record Face(float u, float v, @Nullable float[] size, int rotation, @Nullable String materialInstance) {
   }
}
