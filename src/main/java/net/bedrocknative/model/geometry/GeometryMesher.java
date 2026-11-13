package net.bedrocknative.model.geometry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import net.bedrocknative.BedrockNative;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

public final class GeometryMesher {
   private GeometryMesher() {
   }

   public static MeshedGeometry mesh(GeometryDef geometry) {
      return mesh(geometry, texture -> null);
   }

   public static boolean hasTextureMeshes(GeometryDef geometry) {
      for (BoneDef bone : geometry.bones()) {
         if (!bone.textureMeshes().isEmpty()) {
            return true;
         }
      }

      return false;
   }

   public static MeshedGeometry mesh(GeometryDef geometry, Function<String, TextureMesher.@Nullable Pixels> textures) {
      List<BoneDef> definitions = geometry.bones();
      int[] order = order(definitions);
      int[] newIndex = new int[definitions.size()];
      int i = 0;

      while (i < order.length) {
         newIndex[order[i]] = i++;
      }

      int[] parents = resolvedParents(definitions);
      float[] bounds = new float[]{Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
      MeshedGeometry.Bone[] bones = new MeshedGeometry.Bone[order.length];

      for (int ix = 0; ix < order.length; ix++) {
         BoneDef bone = definitions.get(order[ix]);
         int parent = parents[order[ix]];
         GeometryMesher.QuadBuilder quads = new GeometryMesher.QuadBuilder(geometry.textureWidth(), geometry.textureHeight());
         Matrix4f bindPose = bindPose(bone);
         float[] boneBounds = bindPose != null ? new float[6] : bounds;
         if (!bone.neverRender()) {
            for (CubeDef cube : bone.cubes()) {
               cube(quads, bone, cube, boneBounds);
            }

            if (bone.polyMesh() != null) {
               polyMesh(quads, bone.polyMesh(), geometry.textureWidth(), geometry.textureHeight(), boneBounds);
            }

            for (TextureMeshDef textureMesh : bone.textureMeshes()) {
               TextureMesher.Pixels pixels = textures.apply(textureMesh.texture());
               if (pixels != null) {
                  TextureMesher.mesh(quads, bone, textureMesh, pixels, geometry.textureWidth(), geometry.textureHeight(), boneBounds);
               }
            }
         }

         if (bindPose != null) {
            quads.transform(bindPose, bounds);
         }

         List<MeshedGeometry.Locator> locators = new ArrayList<>();

         for (LocatorDef locator : bone.locators()) {
            float[] offset = j(locator.offset());
            if (bindPose != null) {
               Vector3f turned = bindPose.transformPosition(new Vector3f(offset[0], offset[1], offset[2]));
               offset = new float[]{turned.x, turned.y, turned.z};
            }

            locators.add(new MeshedGeometry.Locator(locator.name(), offset, (float[])locator.rotation().clone(), locator.ignoreInheritedScale()));
         }

         bones[ix] = new MeshedGeometry.Bone(
            bone.name(),
            bone.name().toLowerCase(Locale.ROOT),
            parent >= 0 ? newIndex[parent] : -1,
            j(bone.pivot()),
            (float[])bone.rotation().clone(),
            bone.bindPoseRotation() != null ? (float[])bone.bindPoseRotation().clone() : null,
            bone.neverRender(),
            quads.quads(),
            quads.materials(),
            quads.materialNames(),
            List.copyOf(locators),
            bone.binding()
         );
      }

      if (bounds[0] > bounds[3]) {
         Arrays.fill(bounds, 0.0F);
      }

      float[] visible = new float[]{
         geometry.visibleBoundsWidth(),
         geometry.visibleBoundsHeight(),
         geometry.visibleBoundsOffset()[0],
         geometry.visibleBoundsOffset()[1],
         geometry.visibleBoundsOffset()[2]
      };
      return new MeshedGeometry(geometry.identifier(), geometry.textureWidth(), geometry.textureHeight(), bones, visible, bounds);
   }

   static @Nullable Matrix4f bindPose(BoneDef bone) {
      float[] rotation = bone.bindPoseRotation();
      if (rotation != null && (rotation[0] != 0.0F || rotation[1] != 0.0F || rotation[2] != 0.0F)) {
         float[] pivot = j(bone.pivot());
         Matrix4f matrix = new Matrix4f().translate(pivot[0], pivot[1], pivot[2]);
         rotateJ(matrix, rotation);
         return matrix.translate(-pivot[0], -pivot[1], -pivot[2]);
      } else {
         return null;
      }
   }

   private static int[] parents(List<BoneDef> bones) {
      Map<String, Integer> byName = new HashMap<>();

      for (int i = 0; i < bones.size(); i++) {
         byName.putIfAbsent(bones.get(i).name().toLowerCase(Locale.ROOT), i);
      }

      int[] parents = new int[bones.size()];

      for (int i = 0; i < bones.size(); i++) {
         String parent = bones.get(i).parent();
         Integer index = parent != null ? byName.get(parent.toLowerCase(Locale.ROOT)) : null;
         if (parent != null && index == null) {
            BedrockNative.LOGGER.debug("Bone {} has unknown parent {}", bones.get(i).name(), parent);
         }

         parents[i] = index != null && index != i ? index : -1;
      }

      return parents;
   }

   static int[] order(List<BoneDef> bones) {
      int[] parents = parents(bones);
      int count = bones.size();
      boolean[] placed = new boolean[count];
      int[] order = new int[count];
      int done = 0;

      while (done < count) {
         boolean progress = false;

         for (int i = 0; i < count; i++) {
            if (!placed[i] && (parents[i] < 0 || placed[parents[i]])) {
               placed[i] = true;
               order[done++] = i;
               progress = true;
            }
         }

         if (!progress) {
            for (int i = 0; i < count; i++) {
               if (!placed[i]) {
                  BedrockNative.LOGGER.debug("Bone parent cycle broken at {}", bones.get(i).name());
                  parents[i] = -1;
                  break;
               }
            }
         }
      }

      return order;
   }

   static int[] resolvedParents(List<BoneDef> bones) {
      int[] parents = parents(bones);
      int[] order = order(bones);
      boolean[] seen = new boolean[bones.size()];

      for (int index : order) {
         if (parents[index] >= 0 && !seen[parents[index]]) {
            parents[index] = -1;
         }

         seen[index] = true;
      }

      return parents;
   }

   private static void cube(GeometryMesher.QuadBuilder quads, BoneDef bone, CubeDef cube, float[] bounds) {
      float inflate = Float.isNaN(cube.inflate()) ? bone.inflate() : cube.inflate();
      boolean mirror = cube.mirror() != null ? cube.mirror() : bone.mirror();
      float[] origin = cube.origin();
      float[] size = cube.size();
      float[] min = new float[]{-(origin[0] + size[0]) - inflate, origin[1] - inflate, origin[2] - inflate};
      float[] max = new float[]{-origin[0] + inflate, origin[1] + size[1] + inflate, origin[2] + size[2] + inflate};
      Matrix4f rotation = null;
      float[] cubeRotation = cube.rotation();
      if (cubeRotation[0] != 0.0F || cubeRotation[1] != 0.0F || cubeRotation[2] != 0.0F) {
         float[] pivot = cube.pivot() != null
            ? j(cube.pivot())
            : new float[]{-(origin[0] + size[0] / 2.0F), origin[1] + size[1] / 2.0F, origin[2] + size[2] / 2.0F};
         rotation = new Matrix4f().translate(pivot[0], pivot[1], pivot[2]);
         rotateJ(rotation, cubeRotation);
         rotation.translate(-pivot[0], -pivot[1], -pivot[2]);
      }

      for (Face face : Face.values()) {
         float[] uvs = cube.faces() != null ? perFaceUv(face, cube.faces().get(face), size) : boxUv(face, cube.boxUv(), size, mirror);
         if (uvs != null && !zeroArea(face, min, max)) {
            FaceUv faceUv = cube.faces() != null ? cube.faces().get(face) : null;
            quads.face(face, min, max, uvs, rotation, faceUv != null ? faceUv.materialInstance() : null, bounds);
         }
      }
   }

   private static boolean zeroArea(Face face, float[] min, float[] max) {
      float x = max[0] - min[0];
      float y = max[1] - min[1];
      float z = max[2] - min[2];

      return switch (face) {
         case UP, DOWN -> x == 0.0F || z == 0.0F;
         case NORTH, SOUTH -> x == 0.0F || y == 0.0F;
         case EAST, WEST -> y == 0.0F || z == 0.0F;
      };
   }

   static float[] boxUv(Face face, float @Nullable [] uv, float[] size, boolean mirror) {
      float u = uv != null ? uv[0] : 0.0F;
      float v = uv != null ? uv[1] : 0.0F;
      float w = (float)Math.floor(size[0]);
      float h = (float)Math.floor(size[1]);
      float d = (float)Math.floor(size[2]);
      Face layout = mirror && face == Face.EAST ? Face.WEST : (mirror && face == Face.WEST ? Face.EAST : face);

      float[] rect = switch (layout) {
         case UP -> new float[]{u + d, v, u + d + w, v + d};
         case DOWN -> new float[]{u + d + w, v, u + d + w + w, v + d};
         case NORTH -> new float[]{u + d, v + d, u + d + w, v + d + h};
         case SOUTH -> new float[]{u + d + w + d, v + d, u + d + w + d + w, v + d + h};
         case EAST -> new float[]{u + d + w, v + d, u + d + w + d, v + d + h};
         case WEST -> new float[]{u, v + d, u + d, v + d + h};
      };
      float left = mirror ? rect[2] : rect[0];
      float right = mirror ? rect[0] : rect[2];
      return new float[]{left, rect[1], right, rect[1], right, rect[3], left, rect[3]};
   }

   static float @Nullable [] perFaceUv(Face face, @Nullable FaceUv faceUv, float[] size) {
      if (faceUv == null) {
         return null;
      }

      float defaultU = switch (face) {
         case EAST, WEST -> size[2];
         default -> size[0];
      };

      float defaultV = switch (face) {
         case UP, DOWN -> size[2];
         default -> size[1];
      };
      float u = faceUv.u();
      float v = faceUv.v();
      float su = Float.isNaN(faceUv.uSize()) ? defaultU : faceUv.uSize();
      float sv = Float.isNaN(faceUv.vSize()) ? defaultV : faceUv.vSize();
      float[] corners = face != Face.UP && face != Face.DOWN
         ? new float[]{u, v, u + su, v, u + su, v + sv, u, v + sv}
         : new float[]{u + su, v + sv, u, v + sv, u, v, u + su, v};
      int turns = Math.floorMod(Math.round(faceUv.rotation() / 90.0F), 4);
      if (turns == 0) {
         return corners;
      }

      float[] rotated = new float[8];

      for (int corner = 0; corner < 4; corner++) {
         int from = Math.floorMod(corner - turns, 4);
         rotated[corner * 2] = corners[from * 2];
         rotated[corner * 2 + 1] = corners[from * 2 + 1];
      }

      return rotated;
   }

   private static void polyMesh(GeometryMesher.QuadBuilder quads, PolyMeshDef mesh, float textureWidth, float textureHeight, float[] bounds) {
      float[] positions = mesh.positions();
      float[] normals = mesh.normals();
      float[] uvs = mesh.uvs();

      for (int[] polygon : mesh.polygons()) {
         int vertices = polygon.length / 3;
         float[] quad = new float[32];

         for (int corner = 0; corner < 4; corner++) {
            int vertex = vertices == 4 ? 3 - corner : (corner < 3 ? 2 - corner : 0);
            int position = polygon[vertex * 3];
            int normal = polygon[vertex * 3 + 1];
            int uv = polygon[vertex * 3 + 2];
            int o = corner * 8;
            quad[o] = -at(positions, position * 3);
            quad[o + 1] = at(positions, position * 3 + 1);
            quad[o + 2] = at(positions, position * 3 + 2);
            float u = at(uvs, uv * 2);
            float v = at(uvs, uv * 2 + 1);
            quad[o + 3] = mesh.normalizedUvs() ? u : u / textureWidth;
            quad[o + 4] = mesh.normalizedUvs() ? 1.0F - v : 1.0F - v / textureHeight;
            quad[o + 5] = -at(normals, normal * 3);
            quad[o + 6] = at(normals, normal * 3 + 1);
            quad[o + 7] = at(normals, normal * 3 + 2);
            include(bounds, quad[o], quad[o + 1], quad[o + 2]);
         }

         quads.raw(quad);
      }
   }

   private static float at(float[] values, int index) {
      return index >= 0 && index < values.length ? values[index] : 0.0F;
   }

   static float[] j(float[] raw) {
      return new float[]{-raw[0], raw[1], raw[2]};
   }

   public static Matrix4f rotateJ(Matrix4f matrix, float[] raw) {
      return rotateJ(matrix, raw[0], raw[1], raw[2]);
   }

   public static Matrix4f rotateJ(Matrix4f matrix, float x, float y, float z) {
      return matrix.rotateZ((float)Math.toRadians(z)).rotateY((float)Math.toRadians(-y)).rotateX((float)Math.toRadians(-x));
   }

   static void include(float[] bounds, float x, float y, float z) {
      bounds[0] = Math.min(bounds[0], x);
      bounds[1] = Math.min(bounds[1], y);
      bounds[2] = Math.min(bounds[2], z);
      bounds[3] = Math.max(bounds[3], x);
      bounds[4] = Math.max(bounds[4], y);
      bounds[5] = Math.max(bounds[5], z);
   }

   private static final class QuadBuilder implements GeometryMesher.QuadSink {
      private final float textureWidth;
      private final float textureHeight;
      private float[] quads = new float[192];
      private int size;
      private final List<Integer> materials = new ArrayList<>();
      private final List<String> materialNames = new ArrayList<>();
      private boolean anyMaterial;

      QuadBuilder(float textureWidth, float textureHeight) {
         this.textureWidth = textureWidth;
         this.textureHeight = textureHeight;
      }

      void face(Face face, float[] min, float[] max, float[] uvs, @Nullable Matrix4f rotation, @Nullable String material, float[] bounds) {
         float[] quad = new float[32];
         Vector3f scratch = new Vector3f();
         Vector3f normal = new Vector3f(face.normal(0), face.normal(1), face.normal(2));
         if (rotation != null) {
            rotation.transformDirection(normal).normalize();
         }

         int[] winding = face.winding();

         for (int i = 0; i < 4; i++) {
            int corner = winding[i];
            scratch.set(
               face.corner(corner, 0) == 1 ? max[0] : min[0], face.corner(corner, 1) == 1 ? max[1] : min[1], face.corner(corner, 2) == 1 ? max[2] : min[2]
            );
            if (rotation != null) {
               rotation.transformPosition(scratch);
            }

            int o = i * 8;
            quad[o] = scratch.x;
            quad[o + 1] = scratch.y;
            quad[o + 2] = scratch.z;
            quad[o + 3] = uvs[corner * 2] / this.textureWidth;
            quad[o + 4] = uvs[corner * 2 + 1] / this.textureHeight;
            quad[o + 5] = normal.x;
            quad[o + 6] = normal.y;
            quad[o + 7] = normal.z;
            GeometryMesher.include(bounds, scratch.x, scratch.y, scratch.z);
         }

         this.add(quad, material);
      }

      void raw(float[] quad) {
         this.add(quad, null);
      }

      void transform(Matrix4f matrix, float[] bounds) {
         Vector3f scratch = new Vector3f();

         for (int o = 0; o < this.size; o += 8) {
            matrix.transformPosition(scratch.set(this.quads[o], this.quads[o + 1], this.quads[o + 2]));
            this.quads[o] = scratch.x;
            this.quads[o + 1] = scratch.y;
            this.quads[o + 2] = scratch.z;
            GeometryMesher.include(bounds, scratch.x, scratch.y, scratch.z);
            matrix.transformDirection(scratch.set(this.quads[o + 5], this.quads[o + 6], this.quads[o + 7]));
            if (scratch.lengthSquared() > 0.0F) {
               scratch.normalize();
            }

            this.quads[o + 5] = scratch.x;
            this.quads[o + 6] = scratch.y;
            this.quads[o + 7] = scratch.z;
         }
      }

      @Override
      public void quad(float[] quad) {
         this.add(quad, null);
      }

      private void add(float[] quad, @Nullable String material) {
         if (this.size + quad.length > this.quads.length) {
            this.quads = Arrays.copyOf(this.quads, Math.max(this.quads.length * 2, this.size + quad.length));
         }

         System.arraycopy(quad, 0, this.quads, this.size, quad.length);
         this.size += quad.length;
         int index = -1;
         if (material != null) {
            this.anyMaterial = true;
            index = this.materialNames.indexOf(material);
            if (index < 0) {
               index = this.materialNames.size();
               this.materialNames.add(material);
            }
         }

         this.materials.add(index);
      }

      float[] quads() {
         return Arrays.copyOf(this.quads, this.size);
      }

      int @Nullable [] materials() {
         return this.anyMaterial ? this.materials.stream().mapToInt(Integer::intValue).toArray() : null;
      }

      List<String> materialNames() {
         return List.copyOf(this.materialNames);
      }
   }

   interface QuadSink {
      void quad(float[] var1);
   }
}
