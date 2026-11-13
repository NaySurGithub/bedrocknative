package net.bedrocknative.particle;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.bedrocknative.molang.MolangProgram;
import org.jspecify.annotations.Nullable;

public record ParticleEffect(
   String identifier,
   String texture,
   ParticleEffect.Material material,
   int pack,
   boolean serverPack,
   List<ParticleCurve> curves,
   Map<String, ParticleEvent> events,
   ParticleEffect.Emitter emitter,
   ParticleEffect.Particle particle,
   ParticleEffect.Builtins builtins
) {
   public record Billboard(
      MolangProgram[] size,
      ParticleEffect.Facing facing,
      boolean customDirection,
      float minSpeed,
      MolangProgram[] direction,
      float textureWidth,
      float textureHeight,
      MolangProgram[] uv,
      MolangProgram[] uvSize,
      @Nullable ParticleEffect.Flipbook flipbook
   ) {
   }

   public record Builtins(
      boolean particleAge,
      boolean particleLifetime,
      boolean particleRandom,
      boolean emitterAge,
      boolean emitterLifetime,
      boolean emitterRandom,
      boolean entityScale,
      boolean particleScope
   ) {
   }

   public record Collision(
      MolangProgram enabled, float drag, float restitution, float radius, boolean expireOnContact, List<ParticleEffect.CollisionEvent> events
   ) {
   }

   public record CollisionEvent(String event, float minSpeed) {
   }

   public static enum Direction {
      OUTWARDS,
      INWARDS,
      CUSTOM;
   }

   public record DistanceEvent(float distance, String[] names, boolean looping) {
   }

   public record Emitter(
      @Nullable MolangProgram creation,
      @Nullable MolangProgram perUpdate,
      boolean localPosition,
      boolean localRotation,
      boolean localVelocity,
      ParticleEffect.Rate rate,
      MolangProgram rateAmount,
      MolangProgram maxParticles,
      ParticleEffect.Lifetime lifetime,
      MolangProgram activeTime,
      MolangProgram sleepTime,
      MolangProgram activation,
      MolangProgram expiration,
      String[] creationEvents,
      String[] expirationEvents,
      ParticleEffect.Timeline timeline,
      List<ParticleEffect.DistanceEvent> distanceEvents,
      ParticleEffect.Shape shape,
      MolangProgram[] offset,
      MolangProgram radius,
      MolangProgram[] halfDimensions,
      boolean surfaceOnly,
      MolangProgram[] planeNormal,
      ParticleEffect.Direction direction,
      MolangProgram[] customDirection
   ) {
   }

   public static enum Facing {
      ROTATE_XYZ,
      ROTATE_Y,
      LOOKAT_XYZ,
      LOOKAT_Y,
      LOOKAT_DIRECTION,
      DIRECTION_X,
      DIRECTION_Y,
      DIRECTION_Z,
      EMITTER_TRANSFORM_XY,
      EMITTER_TRANSFORM_XZ,
      EMITTER_TRANSFORM_YZ;
   }

   public record Flipbook(
      MolangProgram[] baseUv, float[] sizeUv, float[] stepUv, MolangProgram framesPerSecond, MolangProgram maxFrame, boolean stretchToLifetime, boolean loop
   ) {
   }

   public static enum Lifetime {
      EXPRESSION,
      ONCE,
      LOOPING;
   }

   public static enum Material {
      ALPHA,
      BLEND,
      ADD,
      OPAQUE;

      public static ParticleEffect.Material of(@Nullable String name) {
         if (name == null) {
            return ALPHA;
         } else {
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.contains("add")) {
               return ADD;
            } else if (lower.contains("blend")) {
               return BLEND;
            } else {
               return lower.contains("opaque") ? OPAQUE : ALPHA;
            }
         }
      }
   }

   public record Particle(
      @Nullable MolangProgram speed,
      @Nullable MolangProgram[] speedVector,
      MolangProgram spinRotation,
      MolangProgram spinRate,
      @Nullable MolangProgram creation,
      @Nullable MolangProgram perUpdate,
      @Nullable MolangProgram perRender,
      @Nullable MolangProgram expiration,
      @Nullable MolangProgram maxLifetime,
      String[] creationEvents,
      String[] expirationEvents,
      ParticleEffect.Timeline timeline,
      Set<String> expireInBlocks,
      Set<String> expireNotInBlocks,
      @Nullable float[] killPlane,
      MolangProgram[] acceleration,
      MolangProgram drag,
      MolangProgram rotationAcceleration,
      MolangProgram rotationDrag,
      @Nullable MolangProgram[] parametricPosition,
      @Nullable MolangProgram[] parametricDirection,
      @Nullable MolangProgram parametricRotation,
      @Nullable ParticleEffect.Collision collision,
      @Nullable ParticleEffect.Billboard billboard,
      @Nullable ParticleEffect.Tint tint,
      boolean lighting
   ) {
   }

   public static enum Rate {
      NONE,
      INSTANT,
      STEADY,
      MANUAL;
   }

   public static enum Shape {
      POINT,
      SPHERE,
      BOX,
      DISC,
      CUSTOM,
      ENTITY_AABB;
   }

   public record Timeline(float[] times, String[][] names) {
      public static final ParticleEffect.Timeline EMPTY = new ParticleEffect.Timeline(new float[0], new String[0][]);
   }

   public record Tint(@Nullable MolangProgram[] color, float[] keys, MolangProgram[][] gradient, @Nullable MolangProgram interpolant) {
   }
}
