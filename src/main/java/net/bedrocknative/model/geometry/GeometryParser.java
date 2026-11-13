package net.bedrocknative.model.geometry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import net.bedrocknative.model.ModelJson;
import net.bedrocknative.molang.MolangVersion;
import org.jspecify.annotations.Nullable;

public final class GeometryParser {
   private static final int FORMAT_1_12 = MolangVersion.version(1, 12, 0);

   private GeometryParser() {
   }

   public static List<GeometryParser.Raw> read(JsonObject file) {
      List<GeometryParser.Raw> geometries = new ArrayList<>();
      int format = MolangVersion.parse(ModelJson.string(file, "format_version"));
      JsonElement modern = file.get("minecraft:geometry");
      if (modern != null) {
         for (JsonElement element : modern.isJsonArray() ? modern.getAsJsonArray() : wrap(modern)) {
            if (element instanceof JsonObject geometry) {
               JsonObject description = ModelJson.object(geometry, "description");
               String identifier = ModelJson.string(description, "identifier");
               if (identifier != null) {
                  JsonObject normalized = new JsonObject();
                  if (description != null) {
                     description.entrySet().forEach(entryx -> normalized.add((String)entryx.getKey(), (JsonElement)entryx.getValue()));
                     normalized.remove("identifier");
                  }

                  if (geometry.get("bones") instanceof JsonArray bones) {
                     normalized.add("bones", bones);
                  }

                  if (geometry.get("item_display_transforms") instanceof JsonObject transforms) {
                     normalized.add("item_display_transforms", transforms);
                  }

                  geometries.add(new GeometryParser.Raw(identifier, null, Math.max(format, FORMAT_1_12), normalized));
               }
            }
         }

         return geometries;
      } else {
         for (Entry<String, JsonElement> entry : file.entrySet()) {
            if (!entry.getKey().equals("format_version") && entry.getValue() instanceof JsonObject geometry) {
               String key = entry.getKey();
               int colon = key.indexOf(58);
               String identifier = (colon >= 0 ? key.substring(0, colon) : key).trim();
               String parent = colon >= 0 ? key.substring(colon + 1).trim() : null;
               JsonObject normalized = geometry.deepCopy();
               rename(normalized, "texturewidth", "texture_width");
               rename(normalized, "textureheight", "texture_height");
               geometries.add(new GeometryParser.Raw(identifier, parent != null && !parent.isEmpty() ? parent : null, format, normalized));
            }
         }

         return geometries;
      }
   }

   public static JsonObject merge(JsonObject parent, JsonObject child) {
      JsonObject merged = parent.deepCopy();

      for (Entry<String, JsonElement> entry : child.entrySet()) {
         if (!entry.getKey().equals("bones")) {
            merged.add(entry.getKey(), entry.getValue().deepCopy());
         }
      }

      JsonArray bones = merged.get("bones") instanceof JsonArray array ? array : new JsonArray();
      merged.add("bones", bones);
      if (child.get("bones") instanceof JsonArray childBones) {
         for (JsonElement element : childBones) {
            if (element instanceof JsonObject childBone) {
               String name = ModelJson.string(childBone, "name");
               JsonObject target = null;

               for (JsonElement existing : bones) {
                  if (existing instanceof JsonObject bone && name != null && name.equalsIgnoreCase(ModelJson.string(bone, "name"))) {
                     target = bone;
                     break;
                  }
               }

               if (target == null) {
                  bones.add(childBone.deepCopy());
               } else {
                  if (ModelJson.bool(childBone, "reset", false)) {
                     target.remove("cubes");
                     target.remove("poly_mesh");
                     target.remove("texture_meshes");
                  }

                  for (Entry<String, JsonElement> field : childBone.entrySet()) {
                     String key = field.getKey();
                     if (!key.equals("reset")) {
                        boolean added = key.equals("cubes") || key.equals("texture_meshes");
                        if (added && target.get(key) instanceof JsonArray inherited && field.getValue() instanceof JsonArray own) {
                           JsonArray both = inherited.deepCopy();
                           own.forEach(item -> {
                              if (!inherited.contains(item)) {
                                 both.add(item.deepCopy());
                              }
                           });
                           target.add(key, both);
                        } else if (key.equals("locators") && target.get(key) instanceof JsonObject inherited && field.getValue() instanceof JsonObject own) {
                           JsonObject both = inherited.deepCopy();
                           own.entrySet().forEach(locator -> both.add((String)locator.getKey(), ((JsonElement)locator.getValue()).deepCopy()));
                           target.add(key, both);
                        } else {
                           target.add(key, field.getValue().deepCopy());
                        }
                     }
                  }
               }
            }
         }
      }

      return merged;
   }

   public static GeometryDef parse(String identifier, int formatVersion, JsonObject json) {
      float defaultTexture = 64.0F;
      List<BoneDef> bones = new ArrayList<>();
      JsonArray array = ModelJson.array(json, "bones");
      if (array != null) {
         int index = 0;

         for (JsonElement element : array) {
            if (element instanceof JsonObject bone) {
               bones.add(bone(bone, index));
            }

            index++;
         }
      }

      return new GeometryDef(
         identifier,
         formatVersion,
         positive(ModelJson.number(json, "texture_width", defaultTexture), defaultTexture),
         positive(ModelJson.number(json, "texture_height", defaultTexture), defaultTexture),
         ModelJson.number(json, "visible_bounds_width", Float.NaN),
         ModelJson.number(json, "visible_bounds_height", Float.NaN),
         ModelJson.vec(json, "visible_bounds_offset", new float[3]),
         List.copyOf(bones),
         ItemDisplayTransforms.of(ModelJson.object(json, "item_display_transforms"))
      );
   }

   private static BoneDef bone(JsonObject json, int index) {
      String name = ModelJson.string(json, "name");
      List<CubeDef> cubes = new ArrayList<>();
      JsonArray cubeArray = ModelJson.array(json, "cubes");
      if (cubeArray != null) {
         for (JsonElement element : cubeArray) {
            if (element instanceof JsonObject cube) {
               cubes.add(cube(cube));
            }
         }
      }

      List<TextureMeshDef> textureMeshes = new ArrayList<>();
      JsonArray meshArray = ModelJson.array(json, "texture_meshes");
      if (meshArray != null) {
         for (JsonElement element : meshArray) {
            if (element instanceof JsonObject mesh) {
               textureMeshes.add(
                  new TextureMeshDef(
                     String.valueOf(ModelJson.string(mesh, "texture")),
                     ModelJson.vec(mesh, "position", new float[3]),
                     ModelJson.vec(mesh, "local_pivot", new float[3]),
                     ModelJson.vec(mesh, "rotation", new float[3]),
                     ModelJson.vec(mesh, "scale", new float[]{1.0F, 1.0F, 1.0F}),
                     ModelJson.bool(mesh, "use_pixel_depth", true)
                  )
               );
            }
         }
      }

      boolean neverRender = ModelJson.bool(json, "neverRender", ModelJson.bool(json, "never_render", false));
      return new BoneDef(
         name != null && !name.isEmpty() ? name : "bone_" + index,
         emptyToNull(ModelJson.string(json, "parent")),
         ModelJson.vec(json, "pivot", new float[3]),
         ModelJson.vec(json, "rotation", new float[3]),
         ModelJson.vecOrNull(json, "bind_pose_rotation", 3),
         ModelJson.bool(json, "mirror", false),
         ModelJson.number(json, "inflate", 0.0F),
         neverRender,
         emptyToNull(ModelJson.string(json, "binding")),
         List.copyOf(cubes),
         locators(ModelJson.object(json, "locators")),
         polyMesh(ModelJson.object(json, "poly_mesh")),
         List.copyOf(textureMeshes),
         (int)ModelJson.number(json, "render_group_id", 0.0F)
      );
   }

   private static CubeDef cube(JsonObject json) {
      float[] boxUv = null;
      Map<Face, FaceUv> faces = null;
      JsonElement uv = json.get("uv");
      if (uv instanceof JsonArray) {
         boxUv = ModelJson.vec(uv, new float[2]);
      } else if (uv instanceof JsonObject perFace) {
         faces = new EnumMap<>(Face.class);

         for (Entry<String, JsonElement> entry : perFace.entrySet()) {
            Face face = Face.byKey(entry.getKey());
            if (face != null && entry.getValue() instanceof JsonObject faceJson) {
               float[] origin = ModelJson.vec(faceJson, "uv", new float[2]);
               float[] size = ModelJson.vec(faceJson, "uv_size", new float[]{Float.NaN, Float.NaN});
               faces.put(
                  face,
                  new FaceUv(
                     origin[0],
                     origin[1],
                     size[0],
                     size[1],
                     (int)ModelJson.number(faceJson, "uv_rotation", 0.0F),
                     ModelJson.string(faceJson, "material_instance")
                  )
               );
            }
         }
      }

      Boolean mirror = json.get("mirror") instanceof JsonPrimitive ? ModelJson.bool(json, "mirror", false) : null;
      return new CubeDef(
         ModelJson.vec(json, "origin", new float[3]),
         ModelJson.vec(json, "size", new float[3]),
         ModelJson.vec(json, "rotation", new float[3]),
         ModelJson.vecOrNull(json, "pivot", 3),
         ModelJson.number(json, "inflate", Float.NaN),
         mirror,
         boxUv,
         faces
      );
   }

   private static List<LocatorDef> locators(@Nullable JsonObject json) {
      if (json == null) {
         return List.of();
      }

      List<LocatorDef> locators = new ArrayList<>();

      for (Entry<String, JsonElement> entry : json.entrySet()) {
         if (entry.getValue() instanceof JsonObject locator) {
            locators.add(
               new LocatorDef(
                  entry.getKey(),
                  ModelJson.vec(locator, "offset", new float[3]),
                  ModelJson.vec(locator, "rotation", new float[3]),
                  ModelJson.bool(locator, "ignore_inherited_scale", false)
               )
            );
         } else {
            locators.add(new LocatorDef(entry.getKey(), ModelJson.vec(entry.getValue(), new float[3]), new float[3], false));
         }
      }

      return List.copyOf(locators);
   }

   private static @Nullable PolyMeshDef polyMesh(@Nullable JsonObject json) {
      if (json == null) {
         return null;
      }

      float[] positions = flatten(ModelJson.array(json, "positions"), 3);
      float[] normals = flatten(ModelJson.array(json, "normals"), 3);
      float[] uvs = flatten(ModelJson.array(json, "uvs"), 2);
      List<int[]> polygons = new ArrayList<>();
      JsonElement polys = json.get("polys");
      if (polys instanceof JsonPrimitive mode) {
         int perPolygon = mode.getAsString().toLowerCase(Locale.ROOT).equals("tri_list") ? 3 : 4;
         int vertices = positions.length / 3;

         for (int start = 0; start + perPolygon <= vertices; start += perPolygon) {
            int[] polygon = new int[perPolygon * 3];

            for (int i = 0; i < perPolygon; i++) {
               polygon[i * 3] = polygon[i * 3 + 1] = polygon[i * 3 + 2] = start + i;
            }

            polygons.add(polygon);
         }
      } else if (polys instanceof JsonArray array) {
         for (JsonElement element : array) {
            if (element instanceof JsonArray polygonJson && polygonJson.size() >= 3) {
               int[] polygon = new int[Math.min(4, polygonJson.size()) * 3];

               for (int i = 0; i < polygon.length / 3; i++) {
                  float[] indices = ModelJson.vec(polygonJson.get(i), new float[3]);

                  for (int j = 0; j < 3; j++) {
                     polygon[i * 3 + j] = (int)indices[j];
                  }
               }

               polygons.add(polygon);
            }
         }
      }

      return new PolyMeshDef(ModelJson.bool(json, "normalized_uvs", false), positions, normals, uvs, polygons.toArray(new int[0][]));
   }

   private static float[] flatten(@Nullable JsonArray array, int width) {
      if (array == null) {
         return new float[0];
      }

      float[] values = new float[array.size() * width];

      for (int i = 0; i < array.size(); i++) {
         float[] vector = ModelJson.vec(array.get(i), new float[width]);
         System.arraycopy(vector, 0, values, i * width, width);
      }

      return values;
   }

   private static JsonArray wrap(JsonElement element) {
      JsonArray array = new JsonArray();
      array.add(element);
      return array;
   }

   private static void rename(JsonObject json, String from, String to) {
      if (json.has(from) && !json.has(to)) {
         json.add(to, json.remove(from));
      }
   }

   private static float positive(float value, float fallback) {
      return value > 0.0F ? value : fallback;
   }

   private static @Nullable String emptyToNull(@Nullable String value) {
      return value != null && !value.isBlank() ? value : null;
   }

   public record Raw(String identifier, @Nullable String parent, int formatVersion, byte[] packed) {
      public Raw(String identifier, @Nullable String parent, int formatVersion, JsonObject json) {
         this(identifier, parent, formatVersion, ModelJson.pack(json));
      }

      public JsonObject json() {
         return ModelJson.unpack(this.packed);
      }
   }
}
