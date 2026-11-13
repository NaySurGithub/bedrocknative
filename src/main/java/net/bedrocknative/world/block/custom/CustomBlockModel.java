package net.bedrocknative.world.block.custom;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.Direction;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

public final class CustomBlockModel {
   private static final float EPSILON = 1.0E-5F;
   private static final List<String> FACE_NAMES = List.of("down", "up", "north", "south", "west", "east");
   private static final int[][][] CORNERS = new int[][][]{
      {{0, 0, 1}, {0, 0, 0}, {1, 0, 0}, {1, 0, 1}},
      {{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}},
      {{1, 1, 0}, {1, 0, 0}, {0, 0, 0}, {0, 1, 0}},
      {{0, 1, 1}, {0, 0, 1}, {1, 0, 1}, {1, 1, 1}},
      {{0, 1, 0}, {0, 0, 0}, {0, 0, 1}, {0, 1, 1}},
      {{1, 1, 1}, {1, 0, 1}, {1, 0, 0}, {1, 1, 0}}
   };
   private static final float CROSS_MIN = 0.05F;
   private static final float CROSS_MAX = 0.95F;
   static final float BACK_INSET = 0.001953125F;

   private CustomBlockModel() {
   }

   public static List<CustomBlockModel.ModelQuad> build(CustomBlockState state, @Nullable BlockGeometry geometry) {
      CustomBlockState.Geometry component = state.geometry();
      List<CustomBlockModel.ModelQuad> quads = new ArrayList<>();
      if (component.cross()) {
         cross(state, quads);
      } else if (!component.fullBlock() && geometry != null) {
         custom(state, geometry, quads);
      } else {
         fullBlock(state, quads);
      }

      if (state.transform().isIdentity()) {
         return quads;
      }

      Matrix4f matrix = state.transform().matrix();
      List<CustomBlockModel.ModelQuad> transformed = new ArrayList<>(quads.size());

      for (CustomBlockModel.ModelQuad quad : quads) {
         Vector3f[] positions = new Vector3f[4];

         for (int i = 0; i < 4; i++) {
            positions[i] = matrix.transformPosition(new Vector3f(quad.positions()[i]));
         }

         Direction cull = quad.cullFace() != null && state.transform().keepsCell() ? rotate(matrix, quad.cullFace()) : null;
         transformed.add(finish(positions, (float[])quad.uvs().clone(), cull, quad.face(), quad.material(), quad.bone(), quad.cube(), quad.back()));
      }

      return transformed;
   }

   public static Direction worldDirection(CustomBlockState state, Direction direction) {
      return state.transform().isIdentity() ? direction : rotate(state.transform().matrix(), direction);
   }

   private static void fullBlock(CustomBlockState state, List<CustomBlockModel.ModelQuad> quads) {
      for (Direction direction : Direction.values()) {
         float[] rect = new float[]{0.0F, 0.0F, 1.0F, 1.0F};
         int turns = direction == Direction.DOWN && state.geometry().legacyTopRotation() ? 2 : 0;
         BlockMaterial material = state.materials().face(direction);
         CustomBlockModel.ModelQuad quad = face(
            direction, new float[]{0.0F, 0.0F, 0.0F}, new float[]{1.0F, 1.0F, 1.0F}, rect, turns, direction, direction.getSerializedName(), material
         );
         quads.add(quad);
         if (material.renderMethod().twoSided()) {
            quads.add(back(quad, direction));
         }
      }
   }

   private static void cross(CustomBlockState state, List<CustomBlockModel.ModelQuad> quads) {
      BlockMaterial material = state.materials().resolve("*");
      float[][] planes = new float[][]{{0.05F, 0.05F, 0.95F, 0.95F}, {0.05F, 0.95F, 0.95F, 0.05F}};

      for (float[] plane : planes) {
         Vector3f a = new Vector3f(plane[0], 0.0F, plane[1]);
         Vector3f b = new Vector3f(plane[2], 0.0F, plane[3]);
         quads.add(plane(b, a, material));
         quads.add(plane(a, b, material));
      }
   }

   private static CustomBlockModel.ModelQuad plane(Vector3f left, Vector3f right, BlockMaterial material) {
      Vector3f[] positions = new Vector3f[]{
         new Vector3f(left.x, 1.0F, left.z), new Vector3f(left.x, 0.0F, left.z), new Vector3f(right.x, 0.0F, right.z), new Vector3f(right.x, 1.0F, right.z)
      };
      float[] uvs = new float[]{0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F, 1.0F, 0.0F};
      return finish(positions, uvs, null, "*", material);
   }

   private static void custom(CustomBlockState state, BlockGeometry geometry, List<CustomBlockModel.ModelQuad> quads) {
      Map<String, BlockGeometry.Bone> byName = new HashMap<>();
      geometry.bones().forEach(bonex -> byName.put(bonex.name().toLowerCase(Locale.ROOT), bonex));
      Map<String, Matrix4f> matrices = new HashMap<>();

      for (BlockGeometry.Bone bone : geometry.bones()) {
         if (visible(bone, byName, state.geometry())) {
            Matrix4f boneMatrix = boneMatrix(bone, byName, matrices, 0);
            String boneName = bone.name().toLowerCase(Locale.ROOT);

            for (int index = 0; index < bone.cubes().size(); index++) {
               cube(state, geometry, boneMatrix, bone.cubes().get(index), boneName, index, quads);
            }
         }
      }
   }

   private static boolean visible(BlockGeometry.Bone bone, Map<String, BlockGeometry.Bone> byName, CustomBlockState.Geometry component) {
      BlockGeometry.Bone current = bone;

      for (int depth = 0; current != null && depth < 64; depth++) {
         if (!component.visible(current.name())) {
            return false;
         }

         current = current.parent() == null ? null : byName.get(current.parent().toLowerCase(Locale.ROOT));
      }

      return true;
   }

   private static Matrix4f boneMatrix(BlockGeometry.Bone bone, Map<String, BlockGeometry.Bone> byName, Map<String, Matrix4f> cache, int depth) {
      String key = bone.name().toLowerCase(Locale.ROOT);
      Matrix4f cached = cache.get(key);
      if (cached != null) {
         return cached;
      }

      BlockGeometry.Bone parent = bone.parent() == null ? null : byName.get(bone.parent().toLowerCase(Locale.ROOT));
      Matrix4f matrix = parent != null && parent != bone && depth < 64 ? new Matrix4f(boneMatrix(parent, byName, cache, depth + 1)) : new Matrix4f();
      rotateAbout(matrix, javaPoint(bone.pivot()), bone.rotation());
      cache.put(key, matrix);
      return matrix;
   }

   private static void rotateAbout(Matrix4f matrix, Vector3f pivot, float[] rotation) {
      if (rotation[0] != 0.0F || rotation[1] != 0.0F || rotation[2] != 0.0F) {
         matrix.translate(pivot)
            .rotateZ((float)Math.toRadians(rotation[2]))
            .rotateY((float)Math.toRadians(-rotation[1]))
            .rotateX((float)Math.toRadians(-rotation[0]))
            .translate(-pivot.x, -pivot.y, -pivot.z);
      }
   }

   static Vector3f javaPoint(float[] point) {
      return new Vector3f(8.0F - point[0], point[1], point[2] + 8.0F);
   }

   private static void cube(
      CustomBlockState state,
      BlockGeometry geometry,
      Matrix4f boneMatrix,
      BlockGeometry.Cube cube,
      String bone,
      int cubeIndex,
      List<CustomBlockModel.ModelQuad> quads
   ) {
      float[] origin = cube.origin();
      float[] size = cube.size();
      float inflate = cube.inflate();
      float[] from = new float[]{8.0F - (origin[0] + size[0]) - inflate, origin[1] - inflate, origin[2] + 8.0F - inflate};
      float[] to = new float[]{8.0F - origin[0] + inflate, origin[1] + size[1] + inflate, origin[2] + size[2] + 8.0F + inflate};
      Matrix4f matrix = new Matrix4f(boneMatrix);
      float[] pivot = cube.pivot() != null ? cube.pivot() : new float[]{origin[0] + size[0] / 2.0F, origin[1] + size[1] / 2.0F, origin[2] + size[2] / 2.0F};
      rotateAbout(matrix, javaPoint(pivot), cube.rotation());
      Map<String, float[]> rects = cube.boxUv() != null ? boxRects(cube) : faceRects(cube);
      Set<String> defined = rects.keySet();

      for (int index = 0; index < FACE_NAMES.size(); index++) {
         String name = FACE_NAMES.get(index);
         float[] rect = rects.get(name);
         Direction direction = Direction.values()[index];
         if (rect != null && !flat(direction, size)) {
            BlockGeometry.Face face = cube.faces().get(name);
            String instance = face != null && face.materialInstance() != null ? face.materialInstance() : name;
            BlockMaterial material = state.materials().resolve(instance);
            float[] uv = new float[]{
               rect[0] / geometry.textureWidth(), rect[1] / geometry.textureHeight(), rect[2] / geometry.textureWidth(), rect[3] / geometry.textureHeight()
            };
            int turns = face == null ? 0 : Math.floorMod(face.rotation() / 90, 4);
            CustomBlockModel.ModelQuad quad = face(direction, from, to, uv, turns, null, name, material);
            quad = transform(quad, matrix).withPart(bone, cubeIndex);
            quads.add(quad);
            if (material.renderMethod().twoSided() && (!thin(direction, size) || !defined.contains(FACE_NAMES.get(index ^ 1)))) {
               quads.add(back(quad, null));
            }
         }
      }
   }

   private static Map<String, float[]> faceRects(BlockGeometry.Cube cube) {
      Map<String, float[]> rects = new HashMap<>();
      float[] size = cube.size();
      cube.faces()
         .forEach(
            (name, face) -> {
               float[] var10000;
               if (face.size() != null) {
                  var10000 = face.size();
               } else {
                  switch (name) {
                     case "north":
                     case "south":
                        var10000 = new float[]{size[0], size[1]};
                        break;
                     case "east":
                     case "west":
                        var10000 = new float[]{size[2], size[1]};
                        break;
                     default:
                        var10000 = new float[]{size[0], size[2]};
                  }
               }

               float[] uvSize = var10000;
               if (FACE_NAMES.contains(name)) {
                  rects.put(
                     name,
                     !name.equals("up") && !name.equals("down")
                        ? new float[]{face.u(), face.v(), face.u() + uvSize[0], face.v() + uvSize[1]}
                        : new float[]{face.u() + uvSize[0], face.v() + uvSize[1], face.u(), face.v()}
                  );
               }
            }
         );
      return rects;
   }

   private static Map<String, float[]> boxRects(BlockGeometry.Cube cube) {
      float u = cube.boxUv()[0];
      float v = cube.boxUv()[1];
      float w = (float)Math.floor(cube.size()[0]);
      float h = (float)Math.floor(cube.size()[1]);
      float d = (float)Math.floor(cube.size()[2]);
      Map<String, float[]> rects = new HashMap<>();
      rects.put("north", new float[]{u + d, v + d, u + d + w, v + d + h});
      rects.put("east", new float[]{u, v + d, u + d, v + d + h});
      rects.put("south", new float[]{u + 2.0F * d + w, v + d, u + 2.0F * d + 2.0F * w, v + d + h});
      rects.put("west", new float[]{u + d + w, v + d, u + 2.0F * d + w, v + d + h});
      rects.put("up", new float[]{u + d + w, v + d, u + d, v});
      rects.put("down", new float[]{u + d + 2.0F * w, v, u + d + w, v + d});
      if (cube.mirror()) {
         float[] east = rects.get("east");
         rects.put("east", rects.get("west"));
         rects.put("west", east);
         rects.replaceAll((name, rect) -> new float[]{rect[2], rect[1], rect[0], rect[3]});
      }

      return rects;
   }

   private static boolean flat(Direction direction, float[] size) {
      return switch (direction.getAxis()) {
         case X -> size[1] == 0.0F || size[2] == 0.0F;
         case Y -> size[0] == 0.0F || size[2] == 0.0F;
         case Z -> size[0] == 0.0F || size[1] == 0.0F;
      };
   }

   private static boolean thin(Direction direction, float[] size) {
      return switch (direction.getAxis()) {
         case X -> size[0] == 0.0F;
         case Y -> size[1] == 0.0F;
         case Z -> size[2] == 0.0F;
      };
   }

   private static CustomBlockModel.ModelQuad face(
      Direction direction, float[] from, float[] to, float[] rect, int turns, @Nullable Direction cull, String name, BlockMaterial material
   ) {
      Vector3f[] positions = new Vector3f[4];
      float[] uvs = new float[8];
      int[][] corners = CORNERS[direction.ordinal()];

      for (int i = 0; i < 4; i++) {
         int[] corner = corners[i];
         positions[i] = new Vector3f(corner[0] == 0 ? from[0] : to[0], corner[1] == 0 ? from[1] : to[1], corner[2] == 0 ? from[2] : to[2]);
         int index = (i + turns) % 4;
         uvs[i * 2] = index != 0 && index != 1 ? rect[2] : rect[0];
         uvs[i * 2 + 1] = index != 0 && index != 3 ? rect[3] : rect[1];
      }

      return new CustomBlockModel.ModelQuad(positions, uvs, direction, cull, name, material);
   }

   private static CustomBlockModel.ModelQuad transform(CustomBlockModel.ModelQuad quad, Matrix4f matrix) {
      Vector3f[] positions = new Vector3f[4];

      for (int i = 0; i < 4; i++) {
         positions[i] = matrix.transformPosition(new Vector3f(quad.positions()[i])).div(16.0F);
      }

      return finish(positions, quad.uvs(), null, quad.face(), quad.material());
   }

   private static CustomBlockModel.ModelQuad back(CustomBlockModel.ModelQuad quad, @Nullable Direction cull) {
      Vector3f[] front = quad.positions();
      Vector3f normal = new Vector3f(front[1]).sub(front[0]).cross(new Vector3f(front[2]).sub(front[0]));
      Vector3f inset = normal.lengthSquared() > 1.0E-12F ? normal.normalize().mul(-0.001953125F) : new Vector3f();
      Vector3f[] positions = new Vector3f[]{
         new Vector3f(front[0]).add(inset), new Vector3f(front[3]).add(inset), new Vector3f(front[2]).add(inset), new Vector3f(front[1]).add(inset)
      };
      float[] uvs = quad.uvs();
      float[] reversed = new float[]{uvs[0], uvs[1], uvs[6], uvs[7], uvs[4], uvs[5], uvs[2], uvs[3]};
      return finish(positions, reversed, cull, quad.face(), quad.material(), quad.bone(), quad.cube(), true);
   }

   static CustomBlockModel.ModelQuad finish(Vector3f[] positions, float[] uvs, @Nullable Direction cull, String face, BlockMaterial material) {
      return finish(positions, uvs, cull, face, material, "", -1, false);
   }

   private static CustomBlockModel.ModelQuad finish(
      Vector3f[] positions, float[] uvs, @Nullable Direction cull, String face, BlockMaterial material, String bone, int cube, boolean back
   ) {
      Vector3f normal = new Vector3f(positions[1]).sub(positions[0]).cross(new Vector3f(positions[2]).sub(positions[0]));
      Direction direction = closest(normal);
      if (direction == null) {
         return new CustomBlockModel.ModelQuad(positions, uvs, Direction.UP, cull, face, material, bone, cube, back);
      }

      if (axisAligned(positions, direction)) {
         reorder(positions, uvs, direction);
      }

      return new CustomBlockModel.ModelQuad(positions, uvs, direction, cull, face, material, bone, cube, back);
   }

   private static @Nullable Direction closest(Vector3f normal) {
      if (!(normal.lengthSquared() < 1.0E-12F) && normal.isFinite()) {
         normal.normalize();
         Direction best = null;
         float bestDot = 0.0F;

         for (Direction candidate : Direction.values()) {
            float dot = normal.dot(candidate.getUnitVec3f());
            if (dot >= 0.0F && dot > bestDot) {
               bestDot = dot;
               best = candidate;
            }
         }

         return best;
      } else {
         return null;
      }
   }

   private static boolean axisAligned(Vector3f[] positions, Direction direction) {
      int axis = direction.getAxis().ordinal();
      float first = positions[0].get(axis);

      for (Vector3f position : positions) {
         if (Math.abs(position.get(axis) - first) > 1.0E-5F) {
            return false;
         }
      }

      return true;
   }

   private static void reorder(Vector3f[] positions, float[] uvs, Direction direction) {
      Vector3f min = new Vector3f(Float.MAX_VALUE);
      Vector3f max = new Vector3f(-Float.MAX_VALUE);

      for (Vector3f position : positions) {
         min.min(position);
         max.max(position);
      }

      int[][] corners = CORNERS[direction.ordinal()];

      for (int target = 0; target < 4; target++) {
         int[] corner = corners[target];
         float x = corner[0] == 0 ? min.x : max.x;
         float y = corner[1] == 0 ? min.y : max.y;
         float z = corner[2] == 0 ? min.z : max.z;

         for (int source = target; source < 4; source++) {
            Vector3f candidate = positions[source];
            if (Math.abs(candidate.x - x) < 1.0E-5F && Math.abs(candidate.y - y) < 1.0E-5F && Math.abs(candidate.z - z) < 1.0E-5F) {
               if (source != target) {
                  Vector3f position = positions[source];
                  positions[source] = positions[target];
                  positions[target] = position;
                  float u = uvs[source * 2];
                  float v = uvs[source * 2 + 1];
                  uvs[source * 2] = uvs[target * 2];
                  uvs[source * 2 + 1] = uvs[target * 2 + 1];
                  uvs[target * 2] = u;
                  uvs[target * 2 + 1] = v;
               }
               break;
            }
         }
      }
   }

   private static Direction rotate(Matrix4f matrix, Direction direction) {
      Vector3f normal = matrix.transformDirection(new Vector3f(direction.getUnitVec3f()));
      Direction rotated = closest(normal);
      return rotated == null ? direction : rotated;
   }

   public record ModelQuad(
      Vector3f[] positions,
      float[] uvs,
      Direction direction,
      @Nullable Direction cullFace,
      String face,
      BlockMaterial material,
      String bone,
      int cube,
      boolean back
   ) {
      public ModelQuad(Vector3f[] positions, float[] uvs, Direction direction, @Nullable Direction cullFace, String face, BlockMaterial material) {
         this(positions, uvs, direction, cullFace, face, material, "", -1, false);
      }

      CustomBlockModel.ModelQuad withPart(String bone, int cube) {
         return new CustomBlockModel.ModelQuad(this.positions, this.uvs, this.direction, this.cullFace, this.face, this.material, bone, cube, this.back);
      }
   }
}
