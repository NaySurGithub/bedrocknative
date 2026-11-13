package net.bedrocknative.model.geometry;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;

public final class MeshedGeometry {
   public static final int VERTEX_FLOATS = 8;
   public static final int QUAD_FLOATS = 32;
   private final String identifier;
   private final float textureWidth;
   private final float textureHeight;
   private final MeshedGeometry.Bone[] bones;
   private final Map<String, Integer> byName = new HashMap<>();
   private final Map<String, Integer> spellings = new ConcurrentHashMap<>();
   private final float[] visibleBounds;
   private final float[] cubeBounds;

   MeshedGeometry(String identifier, float textureWidth, float textureHeight, MeshedGeometry.Bone[] bones, float[] visibleBounds, float[] cubeBounds) {
      this.identifier = identifier;
      this.textureWidth = textureWidth;
      this.textureHeight = textureHeight;
      this.bones = bones;
      this.visibleBounds = visibleBounds;
      this.cubeBounds = cubeBounds;

      for (int i = 0; i < bones.length; i++) {
         this.byName.putIfAbsent(bones[i].key(), i);
      }

      for (int i = 0; i < bones.length; i++) {
         if (this.byName.get(bones[i].key()) == i) {
            this.byName.putIfAbsent(bones[i].name(), i);
         }
      }
   }

   public String identifier() {
      return this.identifier;
   }

   public float textureWidth() {
      return this.textureWidth;
   }

   public float textureHeight() {
      return this.textureHeight;
   }

   public int boneCount() {
      return this.bones.length;
   }

   public MeshedGeometry.Bone bone(int index) {
      return this.bones[index];
   }

   public int bone(String name) {
      Integer index = this.byName.get(name);
      if (index == null) {
         index = this.spellings.get(name);
         if (index == null) {
            Integer found = this.byName.get(name.toLowerCase(Locale.ROOT));
            index = found != null ? found : -1;
            this.spellings.put(name, index);
         }
      }

      return index;
   }

   public float @Nullable [] visibleBounds() {
      return Float.isNaN(this.visibleBounds[0]) ? null : (float[])this.visibleBounds.clone();
   }

   public float[] cubeBounds() {
      return (float[])this.cubeBounds.clone();
   }

   public int quadCount() {
      int quads = 0;

      for (MeshedGeometry.Bone bone : this.bones) {
         quads += bone.quadCount();
      }

      return quads;
   }

   public record Bone(
      String name,
      String key,
      int parent,
      float[] pivot,
      float[] restRotation,
      float @Nullable [] bindPoseRotation,
      boolean neverRender,
      float[] quads,
      int @Nullable [] quadMaterials,
      List<String> materialInstances,
      List<MeshedGeometry.Locator> locators,
      @Nullable String binding
   ) {
      public int quadCount() {
         return this.quads.length / 32;
      }
   }

   public record Locator(String name, float[] offset, float[] rotation, boolean ignoreInheritedScale) {
   }
}
