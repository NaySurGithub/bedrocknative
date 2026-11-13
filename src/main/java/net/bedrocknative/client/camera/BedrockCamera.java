package net.bedrocknative.client.camera;

import com.mojang.blaze3d.audio.ListenerTransform;
import java.util.function.LongFunction;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.camera.CameraDirector;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import org.cloudburstmc.math.vector.Vector2f;
import org.cloudburstmc.math.vector.Vector3d;
import org.cloudburstmc.protocol.bedrock.data.ControlScheme;
import org.cloudburstmc.protocol.bedrock.data.camera.CameraAudioListener;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.CameraInstructionPacket;
import org.cloudburstmc.protocol.bedrock.packet.CameraPresetsPacket;
import org.cloudburstmc.protocol.bedrock.packet.CameraSplinePacket;
import org.jspecify.annotations.Nullable;

public final class BedrockCamera {
   private static final CameraDirector DIRECTOR = new CameraDirector();
   private static final double CLIP_MARGIN = 0.1;
   private static volatile @Nullable CameraType forced;
   private static LongFunction<@Nullable Entity> entities = id -> null;
   private static volatile String lastInstruction = "";
   private static volatile String lastAimAssist = "";
   private static volatile CameraDirector.@Nullable Pose lastPose;
   private static float partialTicks;

   private BedrockCamera() {
   }

   private static double now() {
      return System.nanoTime() / 1.0E9;
   }

   public static synchronized void start(LongFunction<@Nullable Entity> byUniqueId) {
      DIRECTOR.open();
      forced = null;
      lastPose = null;
      entities = byUniqueId;
   }

   public static synchronized void presets(CameraPresetsPacket packet) {
      if (DIRECTOR.isOpen()) {
         DIRECTOR.presets(packet.getPresets());
         BedrockNative.LOGGER.info("Camera presets: {}", DIRECTOR.presets().names());
      }
   }

   public static synchronized void splines(CameraSplinePacket packet) {
      if (DIRECTOR.isOpen()) {
         DIRECTOR.splines(packet.getSplines());
         BedrockNative.LOGGER.debug("Camera splines: {}", DIRECTOR.splineCount());
      }
   }

   public static synchronized void controlScheme(ControlScheme scheme) {
      DIRECTOR.controlScheme(scheme);
   }

   public static synchronized Vec2 controlMove(Vec2 move) {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null && DIRECTOR.cameraRelative()) {
         Vector2f turned = DIRECTOR.controlMove(move.x, move.y, player.getYRot());
         return new Vec2(turned.getX(), turned.getY());
      } else {
         return move;
      }
   }

   public static synchronized boolean cameraRelativeControls() {
      return DIRECTOR.cameraRelative();
   }

   public static synchronized void apply(Minecraft minecraft, CameraInstructionPacket packet) {
      if (DIRECTOR.isOpen()) {
         lastInstruction = packet.toString();
         LocalPlayer player = minecraft.player;
         CameraDirector.Pose start = player == null
            ? new CameraDirector.Pose(Vector3d.ZERO, 0.0F, 0.0F)
            : new CameraDirector.Pose(of(player.getEyePosition()), player.getYRot(), player.getXRot());
         DIRECTOR.apply(packet, now(), start, minecraft.options.fov().get().intValue());
         refresh();
         BedrockNative.LOGGER.debug("Camera instruction {} -> {} {}", new Object[]{packet, DIRECTOR.presetName(), forced});
      }
   }

   public static void aimAssist(BedrockPacket packet) {
      lastAimAssist = packet.getClass().getSimpleName();
      BedrockNative.LOGGER.debug("Aim assist (not used with mouse input): {}", packet.getPacketType());
   }

   public static synchronized void reset() {
      DIRECTOR.close();
      forced = null;
      lastPose = null;
      entities = id -> null;
   }

   public static synchronized void changeDimension() {
      DIRECTOR.resetView();
      forced = null;
   }

   public static @Nullable CameraType forcedCameraType() {
      return forced;
   }

   private static void refresh() {
      forced = switch (DIRECTOR.view(now())) {
         case PLAYER -> null;
         case FIRST_PERSON -> CameraType.FIRST_PERSON;
         case THIRD_PERSON_FRONT -> CameraType.THIRD_PERSON_FRONT;
         case THIRD_PERSON, DETACHED -> CameraType.THIRD_PERSON_BACK;
      };
   }

   public static synchronized void beforeAlign() {
      refresh();
   }

   public static synchronized CameraDirector.@Nullable Pose align(
      Entity entity, Level level, float partialTick, Vec3 eye, float yaw, float pitch, Vec3 javaPosition, float javaYaw, float javaPitch
   ) {
      partialTicks = partialTick;
      CameraDirector.Pose java = new CameraDirector.Pose(of(javaPosition), javaYaw, javaPitch);
      CameraDirector.Pose pose = DIRECTOR.frame(now(), new CameraDirector.Pose(of(eye), yaw, pitch), java, new BedrockCamera.World(entity, level));
      lastPose = pose;
      return pose == java ? null : pose;
   }

   public static synchronized float fovFactor(float playerFov) {
      return playerFov <= 0.0F ? 1.0F : DIRECTOR.fov(now(), playerFov) / playerFov;
   }

   public static synchronized int fadeColour() {
      CameraDirector.Fade fade = DIRECTOR.fade(now());
      return fade != null && !(fade.alpha() <= 0.0F)
         ? Math.round(fade.alpha() * 255.0F) << 24
            | Math.round(fade.red() * 255.0F) << 16
            | Math.round(fade.green() * 255.0F) << 8
            | Math.round(fade.blue() * 255.0F)
         : 0;
   }

   public static synchronized ListenerTransform listener(ListenerTransform camera) {
      LocalPlayer player = Minecraft.getInstance().player;
      if (forced != null && player != null && DIRECTOR.listener() == CameraAudioListener.PLAYER) {
         float yaw = player.getViewYRot(partialTicks);
         float pitch = player.getViewXRot(partialTicks);
         Vector3d forward = CameraDirector.forward(yaw, pitch);
         Vector3d up = CameraDirector.right(yaw).cross(forward);
         return new ListenerTransform(
            player.getEyePosition(partialTicks), new Vec3(forward.getX(), forward.getY(), forward.getZ()), new Vec3(up.getX(), up.getY(), up.getZ())
         );
      } else {
         return camera;
      }
   }

   public static synchronized CameraDirector director() {
      return DIRECTOR;
   }

   public static CameraDirector.@Nullable Pose lastPose() {
      return lastPose;
   }

   public static String lastInstruction() {
      return lastInstruction;
   }

   public static String lastAimAssist() {
      return lastAimAssist;
   }

   private static Vector3d of(Vec3 vec) {
      return Vector3d.from(vec.x, vec.y, vec.z);
   }

   private static Vec3 of(Vector3d vec) {
      return new Vec3(vec.getX(), vec.getY(), vec.getZ());
   }

   public static Vec3 toVec3(Vector3d vec) {
      return of(vec);
   }

   private record World(Entity camera, Level level) implements CameraDirector.World {
      @Override
      public @Nullable Vector3d eyeOf(long uniqueEntityId) {
         Entity entity = BedrockCamera.entities.apply(uniqueEntityId);
         return entity == null ? null : BedrockCamera.of(entity.getEyePosition(BedrockCamera.partialTicks));
      }

      @Override
      public @Nullable Vector3d centreOf(long uniqueEntityId) {
         Entity entity = BedrockCamera.entities.apply(uniqueEntityId);
         return entity == null
            ? null
            : Vector3d.from(
               Mth.lerp(BedrockCamera.partialTicks, entity.xo, entity.getX()),
               Mth.lerp(BedrockCamera.partialTicks, entity.yo, entity.getY()) + entity.getBbHeight() / 2.0F,
               Mth.lerp(BedrockCamera.partialTicks, entity.zo, entity.getZ())
            );
      }

      @Override
      public Vector3d clip(Vector3d from, Vector3d to) {
         Vec3 start = BedrockCamera.of(from);
         Vec3 end = BedrockCamera.of(to);
         HitResult hit = this.level.clip(new ClipContext(start, end, Block.VISUAL, Fluid.NONE, this.camera));
         if (hit.getType() == Type.MISS) {
            return to;
         }

         Vec3 along = end.subtract(start);
         double reach = Math.max(hit.getLocation().distanceTo(start) - 0.1, 0.0);
         return BedrockCamera.of(start.add(along.normalize().scale(reach)));
      }
   }
}
