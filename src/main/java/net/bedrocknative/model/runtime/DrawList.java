package net.bedrocknative.model.runtime;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.bedrocknative.model.geometry.MeshedGeometry;
import net.bedrocknative.model.geometry.Skeleton;
import net.bedrocknative.model.render.MaterialTable;
import org.jspecify.annotations.Nullable;

public final class DrawList {
   private final float[] scale = new float[]{1.0F, 1.0F, 1.0F};
   private final List<DrawList.DrawCall> calls = new ArrayList<>();
   private int ownCalls;

   public float[] scale() {
      return this.scale;
   }

   public List<DrawList.DrawCall> calls() {
      return this.calls;
   }

   void begin(float x, float y, float z) {
      this.scale[0] = x;
      this.scale[1] = y;
      this.scale[2] = z;
      this.calls.clear();
      this.ownCalls = 0;
   }

   void endOwn() {
      this.ownCalls = this.calls.size();
   }

   public int ownCalls() {
      return this.ownCalls;
   }

   void add(DrawList.DrawCall call) {
      this.calls.add(call);
   }

   public static final class DrawCall {
      private String controller = "";
      private String geometry = "";
      @Nullable
      private MeshedGeometry mesh;
      @Nullable
      private Skeleton skeleton;
      @Nullable
      private String[] bindings;
      private final List<String> textures = new ArrayList<>(2);
      private MaterialTable.MaterialInfo[] boneMaterials = new MaterialTable.MaterialInfo[0];
      private boolean[] boneVisible = new boolean[0];
      private final float[] color = new float[4];
      private final float[] overlayColor = new float[4];
      private final float[] hurtColor = new float[4];
      private final float[] fireColor = new float[4];
      private final float[] uvAnimation = new float[4];
      private boolean hasColor;
      private boolean hasOverlayColor;
      private boolean hasHurtColor;
      private boolean hasFireColor;
      private boolean hasUvAnimation;
      private boolean ignoreLighting;
      private float lightMultiplier = 1.0F;
      private int changeColor = -1;
      private int secondColor = -1;

      void reset(String controller, String geometry, MeshedGeometry mesh, Skeleton skeleton, @Nullable String[] bindings) {
         this.controller = controller;
         this.geometry = geometry;
         this.mesh = mesh;
         this.skeleton = skeleton;
         this.bindings = bindings;
         this.textures.clear();
         int bones = mesh.boneCount();
         if (this.boneMaterials.length != bones) {
            this.boneMaterials = new MaterialTable.MaterialInfo[bones];
            this.boneVisible = new boolean[bones];
         }

         Arrays.fill(this.boneMaterials, null);
         Arrays.fill(this.boneVisible, true);
         this.hasColor = this.hasOverlayColor = this.hasHurtColor = this.hasFireColor = this.hasUvAnimation = false;
         this.ignoreLighting = false;
         this.lightMultiplier = 1.0F;
      }

      List<String> mutableTextures() {
         return this.textures;
      }

      float[] colorArray() {
         this.hasColor = true;
         return this.color;
      }

      float[] overlayColorArray() {
         this.hasOverlayColor = true;
         return this.overlayColor;
      }

      float[] hurtColorArray() {
         this.hasHurtColor = true;
         return this.hurtColor;
      }

      float[] fireColorArray() {
         this.hasFireColor = true;
         return this.fireColor;
      }

      float[] uvAnimationArray() {
         this.hasUvAnimation = true;
         return this.uvAnimation;
      }

      void lighting(boolean ignore, float multiplier) {
         this.ignoreLighting = ignore;
         this.lightMultiplier = multiplier;
      }

      void changeColor(int argb) {
         this.changeColor = argb;
      }

      public int changeColor() {
         return this.changeColor;
      }

      void secondColor(int argb) {
         this.secondColor = argb;
      }

      public int secondColor() {
         return this.secondColor;
      }

      public String controller() {
         return this.controller;
      }

      public String geometry() {
         return this.geometry;
      }

      public MeshedGeometry mesh() {
         return this.mesh;
      }

      public Skeleton skeleton() {
         return this.skeleton;
      }

      @Nullable
      public String binding(int bone) {
         return this.bindings != null ? this.bindings[bone] : null;
      }

      public List<String> textures() {
         return this.textures;
      }

      public MaterialTable.MaterialInfo[] boneMaterials() {
         return this.boneMaterials;
      }

      public boolean[] boneVisible() {
         return this.boneVisible;
      }

      @Nullable
      public float[] color() {
         return this.hasColor ? this.color : null;
      }

      @Nullable
      public float[] overlayColor() {
         return this.hasOverlayColor ? this.overlayColor : null;
      }

      @Nullable
      public float[] hurtColor() {
         return this.hasHurtColor ? this.hurtColor : null;
      }

      @Nullable
      public float[] fireColor() {
         return this.hasFireColor ? this.fireColor : null;
      }

      public boolean ignoreLighting() {
         return this.ignoreLighting;
      }

      public float lightMultiplier() {
         return this.lightMultiplier;
      }

      @Nullable
      public float[] uvAnimation() {
         return this.hasUvAnimation ? this.uvAnimation : null;
      }

      public boolean drawn(int bone) {
         return this.boneMaterials[bone] != null && this.boneVisible[bone] && this.mesh.bone(bone).quadCount() > 0;
      }

      public List<DrawList.DrawGroup> groups() {
         Map<MaterialTable.MaterialInfo, List<Integer>> groups = new LinkedHashMap<>();

         for (int bone = 0; bone < this.boneMaterials.length; bone++) {
            if (this.drawn(bone)) {
               groups.computeIfAbsent(this.boneMaterials[bone], key -> new ArrayList<>()).add(bone);
            }
         }

         List<DrawList.DrawGroup> result = new ArrayList<>();
         groups.forEach((material, bones) -> result.add(new DrawList.DrawGroup(material, bones.stream().mapToInt(Integer::intValue).toArray())));
         return result;
      }
   }

   public record DrawGroup(MaterialTable.MaterialInfo material, int[] bones) {
   }
}
