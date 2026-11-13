package net.bedrocknative.model.geometry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.model.ModelJson;
import org.jspecify.annotations.Nullable;

public final class GeometryLibrary {
   private static final String[] REST_FIELDS = new String[]{"rotation", "bind_pose_rotation"};
   private static final float PIVOT_EPSILON = 1.0E-4F;
   private final Map<String, GeometryParser.Raw> raws = new LinkedHashMap<>();
   private final Map<String, List<GeometryParser.Raw>> earlier = new HashMap<>();
   private final Set<String> baseWinners = new HashSet<>();
   private final Map<String, GeometryDef> resolved = new HashMap<>();
   private long version;
   @Nullable
   private final GeometryLibrary parent;

   public GeometryLibrary() {
      this(null);
   }

   public GeometryLibrary(@Nullable GeometryLibrary parent) {
      this.parent = parent;
   }

   public synchronized boolean owns(String identifier) {
      return this.raws.containsKey(identifier.toLowerCase(Locale.ROOT));
   }

   public synchronized void add(JsonObject file) {
      this.add(file, true);
   }

   public synchronized void add(JsonObject file, boolean base) {
      for (GeometryParser.Raw raw : GeometryParser.read(file)) {
         this.add(raw, base);
      }
   }

   public synchronized void add(GeometryParser.Raw raw) {
      this.add(raw, true);
   }

   public synchronized void add(GeometryParser.Raw raw, boolean base) {
      String key = raw.identifier().toLowerCase(Locale.ROOT);
      GeometryParser.Raw replaced = this.raws.put(key, raw);
      if (replaced != null && base && this.baseWinners.contains(key)) {
         this.earlier.computeIfAbsent(key, ignored -> new ArrayList<>()).add(replaced);
      }

      if (base) {
         this.baseWinners.add(key);
      } else {
         this.baseWinners.remove(key);
      }

      this.resolved.clear();
      this.version++;
   }

   public synchronized Set<String> identifiers() {
      Set<String> identifiers = new HashSet<>();
      this.raws.values().forEach(raw -> identifiers.add(raw.identifier()));
      return identifiers;
   }

   public synchronized int size() {
      return this.raws.size();
   }

   @Nullable
   public GeometryDef get(String identifier) {
      String key = identifier.toLowerCase(Locale.ROOT);
      GeometryParser.Raw raw;
      JsonObject json;
      long asked;
      synchronized (this) {
         GeometryDef geometry = this.resolved.get(key);
         if (geometry != null) {
            return geometry;
         }

         raw = this.raws.get(key);
         if (raw == null) {
            json = null;
            asked = 0L;
         } else {
            json = this.resolvedJson(key, new HashSet<>());
            json = this.keptRestPose(key, json != null ? json : raw.json());
            asked = this.version;
         }
      }

      if (raw == null) {
         return this.parent != null ? this.parent.get(identifier) : null;
      } else {
         GeometryDef geometryx = GeometryParser.parse(raw.identifier(), raw.formatVersion(), json);
         synchronized (this) {
            if (asked == this.version) {
               GeometryDef first = this.resolved.putIfAbsent(key, geometryx);
               if (first != null) {
                  return first;
               }
            }

            return geometryx;
         }
      }
   }

   private JsonObject keptRestPose(String key, JsonObject json) {
      List<GeometryParser.Raw> older = this.baseWinners.contains(key) ? this.earlier.get(key) : null;
      if (older != null && !older.isEmpty() && json.get("bones") instanceof JsonArray bones) {
         JsonObject var17 = null;
         ArrayList olderJson = null;

         for (int index = 0; index < bones.size(); index++) {
            if (bones.get(index) instanceof JsonObject bone) {
               for (String field : REST_FIELDS) {
                  if (!bone.has(field)) {
                     if (olderJson == null) {
                        olderJson = new ArrayList(older.size());

                        for (GeometryParser.Raw raw : older) {
                           JsonObject earlierJson = raw.json();
                           JsonObject parentJson = raw.parent() == null ? null : this.resolvedJson(raw.parent().toLowerCase(Locale.ROOT), new HashSet<>());
                           olderJson.add(parentJson != null ? GeometryParser.merge(parentJson, earlierJson) : earlierJson);
                        }
                     }

                     JsonElement value = earliest(olderJson, bone, field);
                     if (value != null) {
                        if (var17 == null) {
                           var17 = json.deepCopy();
                        }

                        ((JsonObject)var17.getAsJsonArray("bones").get(index)).add(field, value.deepCopy());
                     }
                  }
               }
            }
         }

         return var17 != null ? var17 : json;
      } else {
         return json;
      }
   }

   @Nullable
   private static JsonElement earliest(List<JsonObject> older, JsonObject bone, String field) {
      String name = ModelJson.string(bone, "name");
      if (name == null) {
         return null;
      } else {
         for (JsonObject json : older) {
            JsonElement var7 = json.get("bones");
            if (var7 instanceof JsonArray) {
               for (JsonElement element : (JsonArray)var7) {
                  if (element instanceof JsonObject candidate && name.equalsIgnoreCase(ModelJson.string(candidate, "name"))) {
                     if (sameJoint(bone, candidate) && candidate.has(field)) {
                        return candidate.get(field);
                     }
                     break;
                  }
               }
            }
         }

         return null;
      }
   }

   private static boolean sameJoint(JsonObject bone, JsonObject other) {
      String parent = ModelJson.string(bone, "parent");
      String otherParent = ModelJson.string(other, "parent");
      if (parent != null && !parent.isBlank() ? parent.equalsIgnoreCase(otherParent) : otherParent == null || otherParent.isBlank()) {
         float[] pivot = ModelJson.vec(bone, "pivot", new float[3]);
         float[] otherPivot = ModelJson.vec(other, "pivot", new float[3]);

         for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(pivot[axis] - otherPivot[axis]) > 1.0E-4F) {
               return false;
            }
         }

         return true;
      } else {
         return false;
      }
   }

   @Nullable
   private JsonObject resolvedJson(String key, Set<String> visiting) {
      GeometryParser.Raw raw = this.raws.get(key);
      if (raw == null) {
         return this.parent != null ? this.parent.inheritedJson(key, visiting) : null;
      } else if (raw.parent() == null) {
         return raw.json();
      } else {
         String parentKey = raw.parent().toLowerCase(Locale.ROOT);
         if (visiting.add(key) && !visiting.contains(parentKey)) {
            JsonObject parent = this.resolvedJson(parentKey, visiting);
            if (parent == null) {
               BedrockNative.LOGGER.debug("Geometry {} inherits unknown {}", raw.identifier(), raw.parent());
               return raw.json();
            } else {
               return GeometryParser.merge(parent, raw.json());
            }
         } else {
            BedrockNative.LOGGER.debug("Geometry inheritance loop at {}", raw.identifier());
            return raw.json();
         }
      }
   }

   @Nullable
   private synchronized JsonObject inheritedJson(String key, Set<String> visiting) {
      return this.resolvedJson(key, visiting);
   }
}
