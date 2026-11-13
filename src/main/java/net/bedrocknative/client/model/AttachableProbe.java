package net.bedrocknative.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.bedrocknative.model.geometry.MeshedGeometry;
import net.bedrocknative.model.runtime.DrawSnapshot;
import net.bedrocknative.model.runtime.WearerBones;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

public final class AttachableProbe {
   private static final boolean ENABLED = System.getProperty("bedrocknative.devTest") != null;
   private static final Map<String, AttachableProbe.Sample> SAMPLES = new ConcurrentHashMap<>();
   private static final Map<String, Long> SUBMITS = new ConcurrentHashMap<>();
   private static final String[] WEARER_BONES = new String[]{"head", "body", "rightArm", "leftArm", "rightLeg", "leftLeg", "rightItem", "leftItem"};

   private AttachableProbe() {
   }

   static void armor(EntityRenderState state, Object slot, PoseStack poseStack, ActorSnapshot snapshot, JavaSkeletonAdapter adapter) {
      if (ENABLED && state instanceof AvatarRenderState avatar && Minecraft.getInstance().player != null && avatar.id == Minecraft.getInstance().player.getId()
         )
       {
         record(slot.toString().toLowerCase(Locale.ROOT), poseStack.last().pose(), snapshot, adapter.last(), new Vec3(state.x, state.y, state.z));
      }
   }

   static void held(String key, Matrix4f pose, ActorSnapshot snapshot, @Nullable WearerBones bones, Vec3 wearer) {
      if (ENABLED) {
         record(key, pose, snapshot, bones, wearer);
      }
   }

   static boolean enabled() {
      return ENABLED;
   }

   static void submitted(EntityRenderState state, String slot) {
      if (ENABLED && state.entityType != null) {
         SUBMITS.put(BuiltInRegistries.ENTITY_TYPE.getKey(state.entityType) + " " + slot, System.nanoTime());
      }
   }

   public static long lastSubmit(String entityType, String slot) {
      return SUBMITS.getOrDefault(entityType + " " + slot, 0L);
   }

   private static void record(String key, Matrix4f pose, ActorSnapshot snapshot, @Nullable WearerBones bones, Vec3 wearer) {
      Map<String, Matrix4f> matrices = new HashMap<>();
      DrawSnapshot draw = snapshot.draw();
      Matrix4f bone = new Matrix4f();

      for (int c = 0; c < draw.calls(); c++) {
         DrawSnapshot.Call call = draw.call(c);
         MeshedGeometry mesh = call.mesh();

         for (int g = 0; g < call.groups(); g++) {
            for (int i = call.groupStart(g); i < call.groupStart(g + 1); i++) {
               int index = call.bone(i);
               String binding = call.binding(call.rootOf(index));
               Matrix4f matrix = new Matrix4f(pose);
               Matrix4f wearerBone = binding != null && bones != null ? bones.bind(binding, mesh.bone(call.rootOf(index)), new Matrix4f()) : null;
               if (wearerBone != null) {
                  matrix.mul(wearerBone);
               }

               matrix.mul(call.boneMatrix(index, bone));
               matrices.putIfAbsent(mesh.bone(index).key(), matrix);
            }
         }
      }

      Map<String, Matrix4f> wearerBones = new HashMap<>();
      if (bones != null) {
         for (String name : WEARER_BONES) {
            Matrix4f matrix = bones.bone(name);
            if (matrix != null) {
               wearerBones.put(name.toLowerCase(Locale.ROOT), new Matrix4f(pose).mul(matrix));
            }
         }
      }

      SAMPLES.put(
         key, new AttachableProbe.Sample(matrices, wearerBones, Minecraft.getInstance().gameRenderer.mainCamera().position(), wearer, System.nanoTime())
      );
   }

   public static AttachableProbe.@Nullable Sample sample(String key) {
      return SAMPLES.get(key);
   }

   public static void reset() {
      SAMPLES.clear();
   }

   public static @Nullable Vector3f javaPoint(String key, String bone, float x, float y, float z) {
      AttachableProbe.Sample sample = SAMPLES.get(key);
      Matrix4f matrix = sample != null ? sample.wearerBones().get(bone.toLowerCase(Locale.ROOT)) : null;
      return matrix == null
         ? null
         : matrix.transformPosition(new Vector3f(-x, y, z))
            .add((float)(sample.camera().x - sample.wearer().x), (float)(sample.camera().y - sample.wearer().y), (float)(sample.camera().z - sample.wearer().z));
   }

   public static @Nullable Vector3f offset(String key, String bone, float x, float y, float z, boolean fromCamera) {
      AttachableProbe.Sample sample = SAMPLES.get(key);
      Matrix4f matrix = sample != null ? sample.bones().get(bone.toLowerCase(Locale.ROOT)) : null;
      if (matrix == null) {
         return null;
      }

      Vector3f point = matrix.transformPosition(new Vector3f(-x, y, z));
      if (!fromCamera) {
         point.add(
            (float)(sample.camera().x - sample.wearer().x), (float)(sample.camera().y - sample.wearer().y), (float)(sample.camera().z - sample.wearer().z)
         );
      }

      return point;
   }

   public record Sample(Map<String, Matrix4f> bones, Map<String, Matrix4f> wearerBones, Vec3 camera, Vec3 wearer, long nanos) {
   }
}
