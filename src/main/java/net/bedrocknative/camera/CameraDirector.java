package net.bedrocknative.camera;

import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.cloudburstmc.math.vector.Vector2f;
import org.cloudburstmc.math.vector.Vector3d;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.ControlScheme;
import org.cloudburstmc.protocol.bedrock.data.camera.CameraAudioListener;
import org.cloudburstmc.protocol.bedrock.data.camera.CameraEase;
import org.cloudburstmc.protocol.bedrock.data.camera.CameraFadeInstruction;
import org.cloudburstmc.protocol.bedrock.data.camera.CameraFovInstruction;
import org.cloudburstmc.protocol.bedrock.data.camera.CameraPreset;
import org.cloudburstmc.protocol.bedrock.data.camera.CameraSetInstruction;
import org.cloudburstmc.protocol.bedrock.data.camera.CameraSplineDefinition;
import org.cloudburstmc.protocol.bedrock.data.camera.CameraSplineInstruction;
import org.cloudburstmc.protocol.bedrock.data.camera.CameraTargetInstruction;
import org.cloudburstmc.protocol.bedrock.data.camera.CameraFadeInstruction.TimeData;
import org.cloudburstmc.protocol.bedrock.data.camera.CameraSetInstruction.EaseData;
import org.cloudburstmc.protocol.bedrock.packet.CameraInstructionPacket;
import org.cloudburstmc.protocol.common.util.OptionalBoolean;
import org.jspecify.annotations.Nullable;

public final class CameraDirector {
   public static final float DEFAULT_RADIUS = 10.0F;
   public static final float DEFAULT_FADE_SECONDS = 1.0F;
   private static final float THIRD_PERSON_DISTANCE = 4.0F;
   private final CameraPresetBook presets = new CameraPresetBook();
   private final Map<String, CameraSplineInstruction> splines = new HashMap<>();
   private CameraDirector.@Nullable Setting setting;
   private CameraDirector.@Nullable Ease ease;
   private CameraDirector.@Nullable SplineRun spline;
   private @Nullable Long target;
   private Vector3f targetOffset = Vector3f.ZERO;
   private @Nullable Long attached;
   private CameraDirector.@Nullable FadeRun fade;
   private CameraDirector.@Nullable FovRun fov;
   private CameraDirector.@Nullable Pose last;
   private ControlScheme serverScheme = ControlSchemes.DEFAULT;
   private boolean open = true;

   public CameraPresetBook presets() {
      return this.presets;
   }

   public void open() {
      this.reset();
      this.open = true;
   }

   public void close() {
      this.reset();
      this.open = false;
   }

   public boolean isOpen() {
      return this.open;
   }

   public void presets(List<CameraPreset> sent) {
      if (this.open) {
         this.presets.replace(sent);
      }
   }

   public void controlScheme(@Nullable ControlScheme scheme) {
      if (this.open && scheme != null) {
         this.serverScheme = scheme;
      }
   }

   public void splines(List<CameraSplineDefinition> definitions) {
      if (this.open) {
         this.splines.clear();
         if (definitions != null) {
            definitions.forEach(definition -> this.splines.put(definition.getName(), definition.getInstruction()));
         }
      }
   }

   public int splineCount() {
      return this.splines.size();
   }

   public void apply(CameraInstructionPacket packet, double now, CameraDirector.Pose player, float playerFov) {
      if (this.open) {
         CameraDirector.Pose current = this.last != null ? this.last : player;
         if (isTrue(packet.getClear())) {
            this.clearView();
         }

         if (packet.getSetInstruction() != null) {
            this.set(packet.getSetInstruction(), now, current);
         }

         if (packet.getFadeInstruction() != null) {
            this.fade(packet.getFadeInstruction(), now);
         }

         CameraTargetInstruction targetInstruction = packet.getTargetInstruction();
         if (targetInstruction != null) {
            this.target = targetInstruction.getUniqueEntityId();
            this.targetOffset = targetInstruction.getTargetCenterOffset() == null ? Vector3f.ZERO : targetInstruction.getTargetCenterOffset();
         }

         if (isTrue(packet.getRemoveTarget())) {
            this.target = null;
         }

         if (packet.getFovInstruction() != null) {
            CameraFovInstruction instruction = packet.getFovInstruction();
            this.fov = new CameraDirector.FovRun(
               now,
               Math.max(instruction.getEaseTime(), 0.0F),
               instruction.getEaseType() == null ? CameraEase.LINEAR : instruction.getEaseType(),
               this.fov(now, playerFov),
               instruction.isClear() ? null : instruction.getFov()
            );
         }

         if (packet.getSplineInstruction() != null) {
            this.startSpline(packet.getSplineInstruction(), now, current);
         }

         if (packet.getAttachInstruction() != null) {
            this.attached = packet.getAttachInstruction().getUniqueEntityId();
         }

         if (isTrue(packet.getDetachFromEntity())) {
            this.attached = null;
         }
      }
   }

   private void set(CameraSetInstruction set, double now, CameraDirector.Pose current) {
      CameraPresetBook.Resolved preset = set.getPreset() == null ? null : this.presets.resolve(set.getPreset().getIdentifier());
      if (preset == null && set.getPreset() != null) {
         preset = this.presets.resolve(set.getPreset().getRuntimeId());
      }

      if (preset != null) {
         boolean starting = !preset.vanilla() || set.isRemoveIgnoreStartingValues() || isTrue(set.getDefaultPreset());
         Vector3d position = current.position();
         float yaw = current.yaw();
         float pitch = current.pitch();
         if (preset.kind() == CameraPresetBook.Kind.FREE) {
            if (set.getPos() != null) {
               position = of(set.getPos());
            } else if (starting && preset.position() != null) {
               position = of(preset.position());
            }
         }

         if (preset.kind() == CameraPresetBook.Kind.FREE || preset.kind() == CameraPresetBook.Kind.FIXED_BOOM) {
            if (set.getRot() != null) {
               pitch = set.getRot().getX();
               yaw = set.getRot().getY();
            } else if (set.getFacing() != null) {
               float[] look = lookAt(position, of(set.getFacing()));
               yaw = look[0];
               pitch = look[1];
            } else if (starting && preset.startingRotation() != null) {
               pitch = preset.startingRotation().getX();
               yaw = preset.startingRotation().getY();
            } else if (starting || preset.kind() == CameraPresetBook.Kind.FIXED_BOOM) {
               yaw = preset.yaw() != null ? preset.yaw() : yaw;
               pitch = preset.pitch() != null ? preset.pitch() : pitch;
            }
         }

         Vector2f viewOffset = set.getViewOffset() != null ? set.getViewOffset() : (preset.viewOffset() != null ? preset.viewOffset() : Vector2f.ZERO);
         Vector3f entityOffset = set.getEntityOffset() != null
            ? set.getEntityOffset()
            : (preset.entityOffset() != null ? preset.entityOffset() : Vector3f.ZERO);
         float radius = preset.radius() != null ? preset.radius() : 10.0F;
         this.setting = new CameraDirector.Setting(preset, position, wrapDegrees(yaw), Math.clamp(pitch, -90.0F, 90.0F), viewOffset, entityOffset, radius);
         this.spline = null;
         EaseData easeData = set.getEase();
         this.ease = easeData != null && easeData.getTime() > 0.0F
            ? new CameraDirector.Ease(now, easeData.getTime(), easeData.getEaseType() == null ? CameraEase.LINEAR : easeData.getEaseType(), current)
            : null;
      }
   }

   private void fade(CameraFadeInstruction instruction, double now) {
      TimeData time = instruction.getTimeData();
      Color colour = instruction.getColor();
      this.fade = new CameraDirector.FadeRun(
         now,
         time == null ? 1.0 : Math.max(time.getFadeInTime(), 0.0F),
         time == null ? 1.0 : Math.max(time.getWaitTime(), 0.0F),
         time == null ? 1.0 : Math.max(time.getFadeOutTime(), 0.0F),
         colour == null ? 0.0F : colour.getRed() / 255.0F,
         colour == null ? 0.0F : colour.getGreen() / 255.0F,
         colour == null ? 0.0F : colour.getBlue() / 255.0F
      );
   }

   private void startSpline(CameraSplineInstruction instruction, double now, CameraDirector.Pose current) {
      CameraSplineInstruction path = instruction;
      if ((path.getCurve() == null || path.getCurve().isEmpty()) && path.getSplineIdentifier() != null) {
         CameraSplineInstruction named = this.splines.get(path.getSplineIdentifier());
         if (named != null) {
            path = named;
         }
      }

      if (path.getCurve() != null && !path.getCurve().isEmpty()) {
         this.spline = new CameraDirector.SplineRun(new CameraSplinePath(path), now, current);
         this.ease = null;
      }
   }

   public void clearView() {
      this.setting = null;
      this.ease = null;
      this.spline = null;
      this.target = null;
      this.attached = null;
   }

   public void resetView() {
      this.clearView();
      this.fade = null;
      this.fov = null;
      this.last = null;
   }

   public void reset() {
      this.resetView();
      this.presets.reset();
      this.splines.clear();
      this.serverScheme = ControlSchemes.DEFAULT;
   }

   public boolean active() {
      return this.setting != null || this.spline != null;
   }

   public CameraPresetBook.@Nullable Kind kind() {
      return this.spline != null ? CameraPresetBook.Kind.FREE : (this.setting == null ? null : this.setting.preset().kind());
   }

   public @Nullable String presetName() {
      return this.setting == null ? null : this.setting.preset().identifier();
   }

   public @Nullable CameraAudioListener listener() {
      return this.setting == null ? null : this.setting.preset().listener();
   }

   public ControlScheme controlScheme() {
      ControlScheme preset = this.setting == null ? null : this.setting.preset().controlScheme();
      return preset != null ? preset : this.serverScheme;
   }

   public boolean cameraRelative() {
      return ControlSchemes.cameraRelative(this.controlScheme());
   }

   public Vector2f controlMove(float left, float forward, float playerYaw) {
      CameraDirector.Pose camera = this.last;
      return camera != null && this.cameraRelative() ? ControlSchemes.relativeToPlayer(left, forward, camera.yaw(), playerYaw) : Vector2f.from(left, forward);
   }

   public boolean easing(double now) {
      return this.ease != null && now - this.ease.start() < this.ease.seconds();
   }

   public boolean splineRunning(double now) {
      return this.spline != null && !this.spline.path().finished(now - this.spline.start());
   }

   public @Nullable Long target() {
      return this.target;
   }

   public @Nullable Long attached() {
      return this.attached;
   }

   public CameraDirector.View view(double now) {
      if (this.spline != null) {
         return CameraDirector.View.DETACHED;
      }

      if (this.setting == null) {
         return CameraDirector.View.PLAYER;
      }

      if (!this.easing(now) && this.attached == null) {
         return switch (this.setting.preset().kind()) {
            case FIRST_PERSON -> CameraDirector.View.FIRST_PERSON;
            case THIRD_PERSON -> CameraDirector.View.THIRD_PERSON;
            case THIRD_PERSON_FRONT -> CameraDirector.View.THIRD_PERSON_FRONT;
            default -> CameraDirector.View.DETACHED;
         };
      } else {
         return CameraDirector.View.DETACHED;
      }
   }

   public CameraDirector.Pose frame(double now, CameraDirector.Pose player, CameraDirector.Pose java, CameraDirector.World world) {
      CameraDirector.Pose pose = java;
      if (this.spline != null) {
         pose = this.splinePose(this.spline, now);
      } else if (this.setting != null) {
         boolean easing = this.easing(now);
         CameraDirector.Pose goal = this.goal(this.setting, player, java, world, easing || this.attached != null);
         if (easing) {
            pose = blend(this.ease.from(), goal, CameraEasing.apply(this.ease.type(), (now - this.ease.start()) / this.ease.seconds()));
         } else {
            this.ease = null;
            pose = goal;
         }
      }

      this.last = pose;
      return pose;
   }

   private CameraDirector.Pose goal(
      CameraDirector.Setting setting, CameraDirector.Pose player, CameraDirector.Pose java, CameraDirector.World world, boolean own
   ) {
      Vector3d anchor = player.position();
      if (this.attached != null) {
         Vector3d eye = world.eyeOf(this.attached);
         if (eye != null) {
            anchor = eye;
         }
      }

      Vector3d pivot = anchor.add(of(setting.entityOffset()));

      return switch (setting.preset().kind()) {
         case FIRST_PERSON -> offset(own ? new CameraDirector.Pose(anchor, player.yaw(), player.pitch()) : java, setting);
         case THIRD_PERSON -> offset(own ? behind(world, anchor, player.yaw(), player.pitch(), 4.0F) : java, setting);
         case THIRD_PERSON_FRONT -> offset(own ? behind(world, anchor, player.yaw() + 180.0F, -player.pitch(), 4.0F) : java, setting);
         case FREE -> {
            Vector3d centre = this.target == null ? null : world.centreOf(this.target);
            if (centre != null) {
               float[] look = lookAt(setting.position(), centre.add(of(this.targetOffset)));
               yield new CameraDirector.Pose(setting.position(), look[0], look[1]);
            } else {
               yield new CameraDirector.Pose(setting.position(), setting.yaw(), setting.pitch());
            }
         }
         case FIXED_BOOM -> viewOffset(behind(world, pivot, setting.yaw(), setting.pitch(), setting.radius()), setting.viewOffset());
         case FOLLOW_ORBIT -> {
            float yaw = player.yaw();
            float pitch = player.pitch();
            Vector3d centre = this.target == null ? null : world.centreOf(this.target);
            if (centre != null) {
               float[] look = lookAt(pivot, centre.add(of(this.targetOffset)));
               yaw = look[0];
               pitch = look[1];
            }

            yield viewOffset(behind(world, pivot, yaw, pitch, setting.radius()), setting.viewOffset());
         }
      };
   }

   private static CameraDirector.Pose offset(CameraDirector.Pose pose, CameraDirector.Setting setting) {
      Vector3f entity = setting.entityOffset();
      CameraDirector.Pose moved = entity.equals(Vector3f.ZERO) ? pose : new CameraDirector.Pose(pose.position().add(of(entity)), pose.yaw(), pose.pitch());
      return viewOffset(moved, setting.viewOffset());
   }

   private static CameraDirector.Pose viewOffset(CameraDirector.Pose pose, Vector2f offset) {
      if (offset.equals(Vector2f.ZERO)) {
         return pose;
      }

      Vector3d right = right(pose.yaw());
      Vector3d up = right.cross(forward(pose.yaw(), pose.pitch()));
      return new CameraDirector.Pose(pose.position().add(right.mul(offset.getX())).add(up.mul(offset.getY())), pose.yaw(), pose.pitch());
   }

   private static CameraDirector.Pose behind(CameraDirector.World world, Vector3d pivot, float yaw, float pitch, float distance) {
      Vector3d wanted = pivot.sub(forward(yaw, pitch).mul(distance));
      return new CameraDirector.Pose(world.clip(pivot, wanted), wrapDegrees(yaw), Math.clamp(pitch, -90.0F, 90.0F));
   }

   private CameraDirector.Pose splinePose(CameraDirector.SplineRun run, double now) {
      double seconds = now - run.start();
      Vector3d position = run.path().position(seconds);
      float[] rotation = run.path().rotation(seconds, run.from().pitch(), run.from().yaw());
      return rotation == null
         ? new CameraDirector.Pose(position, run.from().yaw(), run.from().pitch())
         : new CameraDirector.Pose(position, wrapDegrees(rotation[1]), rotation[0]);
   }

   public CameraDirector.@Nullable Fade fade(double now) {
      CameraDirector.FadeRun run = this.fade;
      if (run == null) {
         return null;
      }

      double t = now - run.start();
      float alpha;
      if (t < run.in()) {
         alpha = (float)(t / run.in());
      } else if (t < run.in() + run.hold()) {
         alpha = 1.0F;
      } else {
         if (!(t < run.in() + run.hold() + run.out())) {
            this.fade = null;
            return null;
         }

         alpha = (float)(1.0 - (t - run.in() - run.hold()) / run.out());
      }

      return new CameraDirector.Fade(run.red(), run.green(), run.blue(), Math.clamp(alpha, 0.0F, 1.0F));
   }

   public float fov(double now, float playerFov) {
      CameraDirector.FovRun run = this.fov;
      if (run == null) {
         return playerFov;
      } else {
         float to = run.to() == null ? playerFov : run.to();
         double t = run.seconds() <= 0.0 ? 1.0 : (now - run.start()) / run.seconds();
         if (t >= 1.0 && run.to() == null) {
            this.fov = null;
            return playerFov;
         } else {
            return (float)(run.from() + (to - run.from()) * CameraEasing.apply(run.type(), t));
         }
      }
   }

   public static CameraDirector.Pose blend(CameraDirector.Pose from, CameraDirector.Pose to, double t) {
      Vector3d position = from.position().add(to.position().sub(from.position()).mul(t));
      float yaw = (float)(from.yaw() + wrapDegrees(to.yaw() - from.yaw()) * t);
      float pitch = (float)(from.pitch() + (to.pitch() - from.pitch()) * t);
      return new CameraDirector.Pose(position, yaw, pitch);
   }

   public static float[] lookAt(Vector3d from, Vector3d at) {
      Vector3d d = at.sub(from);
      double horizontal = Math.sqrt(d.getX() * d.getX() + d.getZ() * d.getZ());
      if (horizontal < 1.0E-7 && Math.abs(d.getY()) < 1.0E-7) {
         return new float[]{0.0F, 0.0F};
      }

      float yaw = (float)Math.toDegrees(Math.atan2(-d.getX(), d.getZ()));
      float pitch = (float)(-Math.toDegrees(Math.atan2(d.getY(), horizontal)));
      return new float[]{yaw, pitch};
   }

   public static Vector3d forward(float yaw, float pitch) {
      double yawRad = Math.toRadians(yaw);
      double pitchRad = Math.toRadians(pitch);
      return Vector3d.from(-Math.sin(yawRad) * Math.cos(pitchRad), -Math.sin(pitchRad), Math.cos(yawRad) * Math.cos(pitchRad));
   }

   public static Vector3d right(float yaw) {
      double yawRad = Math.toRadians(yaw);
      return Vector3d.from(-Math.cos(yawRad), 0.0, -Math.sin(yawRad));
   }

   public static float wrapDegrees(float degrees) {
      float wrapped = degrees % 360.0F;
      if (wrapped >= 180.0F) {
         wrapped -= 360.0F;
      }

      if (wrapped < -180.0F) {
         wrapped += 360.0F;
      }

      return wrapped;
   }

   private static Vector3d of(Vector3f vector) {
      return Vector3d.from(vector.getX(), vector.getY(), vector.getZ());
   }

   private static boolean isTrue(@Nullable OptionalBoolean value) {
      return value != null && value.isPresent() && value.getAsBoolean();
   }

   private record Ease(double start, double seconds, CameraEase type, CameraDirector.Pose from) {
   }

   public record Fade(float red, float green, float blue, float alpha) {
   }

   private record FadeRun(double start, double in, double hold, double out, float red, float green, float blue) {
   }

   private record FovRun(double start, double seconds, CameraEase type, float from, @Nullable Float to) {
   }

   public record Pose(Vector3d position, float yaw, float pitch) {
   }

   private record Setting(CameraPresetBook.Resolved preset, Vector3d position, float yaw, float pitch, Vector2f viewOffset, Vector3f entityOffset, float radius) {
   }

   private record SplineRun(CameraSplinePath path, double start, CameraDirector.Pose from) {
   }

   public enum View {
      PLAYER,
      FIRST_PERSON,
      THIRD_PERSON,
      THIRD_PERSON_FRONT,
      DETACHED;
   }

   public interface World {
      @Nullable Vector3d eyeOf(long var1);

      @Nullable Vector3d centreOf(long var1);

      Vector3d clip(Vector3d var1, Vector3d var2);
   }
}
