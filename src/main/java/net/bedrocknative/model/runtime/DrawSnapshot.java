package net.bedrocknative.model.runtime;

import java.util.List;
import net.bedrocknative.model.geometry.MeshedGeometry;
import net.bedrocknative.model.render.MaterialTable;
import net.bedrocknative.model.render.RenderKind;
import org.joml.Matrix3f;
import org.joml.Matrix3fc;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

public final class DrawSnapshot {
   public static final int MAX_LAYERS = 3;
   private DrawSnapshot.Call[] calls = new DrawSnapshot.Call[0];
   private int count;
   private int ownCalls;
   private boolean uvAnimation;
   private boolean polish;
   private final float[] scale = new float[]{1.0F, 1.0F, 1.0F};
   private final Matrix4f boneMatrix = new Matrix4f();
   private final Matrix4f vertexMatrix = new Matrix4f();
   private final Matrix3f boneNormal = new Matrix3f();
   private final Matrix3f normalMatrix = new Matrix3f();
   private final Vector3f position = new Vector3f();
   private final Vector3f normal = new Vector3f();

   static int multiplier(float[] rgba) {
      float a = Math.clamp(rgba[3], 0.0F, 1.0F);
      return channel(1.0F - a + Math.clamp(rgba[0], 0.0F, 1.0F) * a) << 16
         | channel(1.0F - a + Math.clamp(rgba[1], 0.0F, 1.0F) * a) << 8
         | channel(1.0F - a + Math.clamp(rgba[2], 0.0F, 1.0F) * a);
   }

   static int argb(float[] rgba) {
      return channel(rgba[3]) << 24 | channel(rgba[0]) << 16 | channel(rgba[1]) << 8 | channel(rgba[2]);
   }

   private static int channel(float value) {
      return Math.round(Math.clamp(value, 0.0F, 1.0F) * 255.0F);
   }

   public void copy(DrawList list) {
      List<DrawList.DrawCall> source = list.calls();
      if (this.calls.length < source.size()) {
         DrawSnapshot.Call[] grown = new DrawSnapshot.Call[source.size()];
         System.arraycopy(this.calls, 0, grown, 0, this.calls.length);

         for (int i = this.calls.length; i < grown.length; i++) {
            grown[i] = new DrawSnapshot.Call();
         }

         this.calls = grown;
      }

      this.count = source.size();
      this.ownCalls = list.ownCalls();

      for (int i = 0; i < this.count; i++) {
         this.calls[i].copy(source.get(i), this.polish);
      }

      float[] listScale = list.scale();
      this.scale[0] = listScale[0];
      this.scale[1] = listScale[1];
      this.scale[2] = listScale[2];
   }

   public void setPolish(boolean on) {
      this.polish = on;
   }

   public void clear() {
      this.count = 0;
      this.ownCalls = 0;
   }

   public void setUvAnimation(boolean on) {
      this.uvAnimation = on;
   }

   public int calls() {
      return this.count;
   }

   public int ownCalls() {
      return this.ownCalls;
   }

   public DrawSnapshot.Call call(int index) {
      return this.calls[index];
   }

   public float scale(int axis) {
      return this.scale[axis];
   }

   public void emit(int callIndex, int group, Matrix4fc pose, Matrix3fc poseNormal, DrawSnapshot.VertexSink sink) {
      this.emit(callIndex, group, -1, pose, poseNormal, sink);
   }

   public void emit(int callIndex, int group, int root, Matrix4fc pose, Matrix3fc poseNormal, DrawSnapshot.VertexSink sink) {
      this.emit(callIndex, group, root, -1, pose, poseNormal, sink);
   }

   public void emitSubtree(int callIndex, int group, int ancestor, Matrix4fc pose, Matrix3fc poseNormal, DrawSnapshot.VertexSink sink) {
      this.emit(callIndex, group, -1, ancestor, pose, poseNormal, sink);
   }

   private static boolean under(MeshedGeometry mesh, int bone, int ancestor) {
      int current = bone;

      while (current >= 0) {
         if (current == ancestor) {
            return true;
         }

         current = mesh.bone(current).parent();
      }

      return false;
   }

   private void emit(int callIndex, int group, int root, int ancestor, Matrix4fc pose, Matrix3fc poseNormal, DrawSnapshot.VertexSink sink) {
      DrawSnapshot.Call call = this.calls[callIndex];
      MeshedGeometry mesh = call.mesh;
      boolean moves = this.uvAnimation && call.uvAnimated(group);
      float offsetU = moves ? call.uvAnimation[0] : 0.0F;
      float offsetV = moves ? call.uvAnimation[1] : 0.0F;
      float scaleU = moves ? call.uvAnimation[2] : 1.0F;
      float scaleV = moves ? call.uvAnimation[3] : 1.0F;

      for (int index = call.groupStart[group]; index < call.groupStart[group + 1]; index++) {
         int bone = call.bones[index];
         if ((root < 0 || call.rootOf[bone] == root) && (ancestor < 0 || under(mesh, bone, ancestor))) {
            this.boneMatrix.set(call.matrices, bone * 16);
            pose.mul(this.boneMatrix, this.vertexMatrix);
            this.boneNormal.set(call.normals, bone * 9);
            poseNormal.mul(this.boneNormal, this.normalMatrix);
            float[] quads = mesh.bone(bone).quads();
            boolean first = true;
            int lastX = 0;
            int lastY = 0;
            int lastZ = 0;

            for (int offset = 0; offset < quads.length; offset += 8) {
               this.vertexMatrix.transformPosition(quads[offset], quads[offset + 1], quads[offset + 2], this.position);
               float nx = quads[offset + 5];
               float ny = quads[offset + 6];
               float nz = quads[offset + 7];
               int bitsX = Float.floatToRawIntBits(nx);
               int bitsY = Float.floatToRawIntBits(ny);
               int bitsZ = Float.floatToRawIntBits(nz);
               if (first || bitsX != lastX || bitsY != lastY || bitsZ != lastZ) {
                  first = false;
                  lastX = bitsX;
                  lastY = bitsY;
                  lastZ = bitsZ;
                  this.normalMatrix.transform(nx, ny, nz, this.normal);
                  float length = this.normal.length();
                  if (length > 0.0F) {
                     this.normal.div(length);
                  }
               }

               sink.vertex(
                  this.position.x,
                  this.position.y,
                  this.position.z,
                  offsetU + quads[offset + 3] * scaleU,
                  offsetV + quads[offset + 4] * scaleV,
                  this.normal.x,
                  this.normal.y,
                  this.normal.z
               );
            }
         }
      }
   }

   public static final class Call {
      @Nullable
      private MeshedGeometry mesh;
      private String controller = "";
      @Nullable
      private String texture;
      private final String[] layers = new String[3];
      private int layerCount;
      private int tint = -1;
      private int tint2 = -1;
      private float colorAlpha = 1.0F;
      private float whiteOverlay;
      private int overlayTint = 16777215;
      private float[] matrices = new float[0];
      private float[] normals = new float[0];
      private int[] groupOf = new int[0];
      private int[] bones = new int[0];
      private int[] groupStart = new int[1];
      private int[] rootOf = new int[0];
      private String[] bindings = new String[0];
      private int[] groupRoots = new int[0];
      private int[] groupRootStart = new int[1];
      private MaterialTable.MaterialInfo[] groupMaterials = new MaterialTable.MaterialInfo[0];
      private RenderKind[] groupKinds = new RenderKind[0];
      private int groups;
      private boolean ignoreLighting;
      private boolean hurtFlash = true;
      private boolean hasUvAnimation;
      private final float[] uvAnimation = new float[4];
      private final Matrix3f normalScratch = new Matrix3f();

      void copy(DrawList.DrawCall call, boolean polish) {
         MeshedGeometry geometry = call.mesh();
         this.mesh = geometry;
         this.controller = call.controller();
         List<String> textures = call.textures();
         this.texture = textures.isEmpty() ? null : textures.getFirst();
         this.layerCount = Math.min(textures.size(), 3);

         for (int i = 0; i < 3; i++) {
            this.layers[i] = i < this.layerCount ? textures.get(i) : null;
         }

         float[] color = call.color();
         this.tint = color != null ? DrawSnapshot.argb(color) : call.changeColor();
         this.colorAlpha = color != null ? Math.clamp(color[3], 0.0F, 1.0F) : 1.0F;
         if (polish) {
            this.tint |= -16777216;
         }

         this.tint2 = call.secondColor();
         float[] overlay = call.overlayColor();
         this.whiteOverlay = overlay != null && overlay[0] >= 0.9F && overlay[1] >= 0.9F && overlay[2] >= 0.9F ? Math.clamp(overlay[3], 0.0F, 1.0F) : 0.0F;
         this.overlayTint = polish && overlay != null && this.whiteOverlay == 0.0F ? DrawSnapshot.multiplier(overlay) : 16777215;
         this.ignoreLighting = call.ignoreLighting();
         float[] hurt = call.hurtColor();
         this.hurtFlash = hurt == null || hurt[3] > 0.0F;
         float[] uv = call.uvAnimation();
         this.hasUvAnimation = uv != null;
         if (uv != null) {
            System.arraycopy(uv, 0, this.uvAnimation, 0, 4);
         }

         int count = geometry.boneCount();
         if (this.groupOf.length < count) {
            this.groupOf = new int[count];
            this.bones = new int[count];
            this.groupStart = new int[count + 1];
            this.groupMaterials = new MaterialTable.MaterialInfo[count];
            this.groupKinds = new RenderKind[count];
            this.matrices = new float[count * 16];
            this.normals = new float[count * 9];
            this.rootOf = new int[count];
            this.bindings = new String[count];
            this.groupRoots = new int[count];
            this.groupRootStart = new int[count + 1];
         }

         for (int bone = 0; bone < count; bone++) {
            int parent = geometry.bone(bone).parent();
            String binding = call.binding(bone);
            this.rootOf[bone] = parent >= 0 && binding == null ? this.rootOf[parent] : bone;
            this.bindings[bone] = binding;
         }

         MaterialTable.MaterialInfo[] materials = call.boneMaterials();
         this.groups = 0;

         for (int bone = 0; bone < count; bone++) {
            if (!call.drawn(bone)) {
               this.groupOf[bone] = -1;
            } else {
               MaterialTable.MaterialInfo material = materials[bone];
               int group = 0;

               while (group < this.groups && this.groupMaterials[group] != material) {
                  group++;
               }

               if (group == this.groups) {
                  this.groupMaterials[group] = material;
                  this.groupKinds[group] = RenderKind.of(material, polish);
                  this.groups++;
               }

               this.groupOf[bone] = group;
               Matrix4f world = call.skeleton().world(bone);
               world.get(this.matrices, bone * 16);
               world.normal(this.normalScratch).get(this.normals, bone * 9);
            }
         }

         for (int group = 0; group <= this.groups; group++) {
            this.groupStart[group] = 0;
         }

         for (int bonex = 0; bonex < count; bonex++) {
            if (this.groupOf[bonex] >= 0) {
               this.groupStart[this.groupOf[bonex] + 1]++;
            }
         }

         for (int group = 0; group < this.groups; group++) {
            this.groupStart[group + 1] = this.groupStart[group + 1] + this.groupStart[group];
         }

         for (int bonexx = 0; bonexx < count; bonexx++) {
            int group = this.groupOf[bonexx];
            if (group >= 0) {
               this.bones[this.groupStart[group]++] = bonexx;
            }
         }

         for (int group = this.groups; group > 0; group--) {
            this.groupStart[group] = this.groupStart[group - 1];
         }

         this.groupStart[0] = 0;
         int roots = 0;

         for (int group = 0; group < this.groups; group++) {
            this.groupRootStart[group] = roots;

            for (int i = this.groupStart[group]; i < this.groupStart[group + 1]; i++) {
               int root = this.rootOf[this.bones[i]];
               boolean seen = false;

               for (int r = this.groupRootStart[group]; r < roots; r++) {
                  if (this.groupRoots[r] == root) {
                     seen = true;
                     break;
                  }
               }

               if (!seen) {
                  this.groupRoots[roots++] = root;
               }
            }
         }

         this.groupRootStart[this.groups] = roots;
      }

      public MeshedGeometry mesh() {
         return this.mesh;
      }

      public String controller() {
         return this.controller;
      }

      @Nullable
      public String texture() {
         return this.texture;
      }

      public int layers() {
         return this.layerCount;
      }

      @Nullable
      public String layer(int index) {
         return this.layers[index];
      }

      public int tint() {
         return this.tint;
      }

      public float whiteOverlay() {
         return this.whiteOverlay;
      }

      public int overlayTint() {
         return this.overlayTint;
      }

      public float colorAlpha() {
         return this.colorAlpha;
      }

      public int tint2() {
         return this.tint2;
      }

      public int groups() {
         return this.groups;
      }

      public MaterialTable.MaterialInfo material(int group) {
         return this.groupMaterials[group];
      }

      public RenderKind kind(int group) {
         return this.groupKinds[group];
      }

      public int groupStart(int group) {
         return this.groupStart[group];
      }

      public int bone(int index) {
         return this.bones[index];
      }

      public boolean ignoreLighting() {
         return this.ignoreLighting;
      }

      public int rootStart(int group) {
         return this.groupRootStart[group];
      }

      public int root(int index) {
         return this.groupRoots[index];
      }

      public Matrix4f boneMatrix(int bone, Matrix4f out) {
         return out.set(this.matrices, bone * 16);
      }

      public int rootOf(int bone) {
         return this.rootOf[bone];
      }

      @Nullable
      public String binding(int root) {
         return this.bindings[root];
      }

      public boolean hurtFlash() {
         return this.hurtFlash;
      }

      @Nullable
      public float[] uvAnimation() {
         return this.hasUvAnimation ? this.uvAnimation : null;
      }

      public boolean uvAnimated(int group) {
         return this.hasUvAnimation && this.groupMaterials[group].uvAnimation();
      }

      public int quads(int group) {
         int quads = 0;

         for (int i = this.groupStart[group]; i < this.groupStart[group + 1]; i++) {
            quads += this.mesh.bone(this.bones[i]).quadCount();
         }

         return quads;
      }
   }

   public interface VertexSink {
      void vertex(float var1, float var2, float var3, float var4, float var5, float var6, float var7, float var8);
   }
}
