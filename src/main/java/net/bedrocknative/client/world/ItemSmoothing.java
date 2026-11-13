package net.bedrocknative.client.world;

import java.util.function.BooleanSupplier;
import net.bedrocknative.client.movement.BedrockMovement;
import net.minecraft.core.PositionAndRotation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.PositionPath;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class ItemSmoothing {
   public static final int STEPS = 3;
   public static final double SNAP_DISTANCE_SQUARED = 64.0;
   private static final boolean JAVA_ITEMS = Boolean.getBoolean("bedrocknative.javaItems");

   private ItemSmoothing() {
   }

   public static boolean active() {
      return !JAVA_ITEMS && BedrockMovement.active();
   }

   public static boolean snaps(boolean teleport, double distanceSquared) {
      return teleport || distanceSquared > 64.0;
   }

   public static InterpolationHandler createHandler(Entity entity) {
      return (InterpolationHandler)(entity.level().isClientSide() ? new ItemSmoothing.Handler(entity, ItemSmoothing::active) : InterpolationHandler.NO_OP);
   }

   static final class Handler implements InterpolationHandler {
      private final Entity entity;
      private final BooleanSupplier enabled;
      private final PositionLerp lerp = new PositionLerp();

      Handler(Entity entity, BooleanSupplier enabled) {
         this.entity = entity;
         this.enabled = enabled;
      }

      @Nullable
      public PositionAndRotation target() {
         return this.lerp.active() ? PositionAndRotation.of(this.lerp.target(), this.entity.getYRot(), this.entity.getXRot()) : null;
      }

      public boolean interpolateTo(@Nullable PositionPath position, float yRot, float xRot, boolean hasRotation) {
         if (!this.enabled.getAsBoolean()) {
            return false;
         } else {
            if (position != null) {
               Vec3 end = position.endPosition();
               if (!this.lerp.heading(end)) {
                  this.lerp.begin(end, 3);
               }
            }

            return true;
         }
      }

      public void interpolate() {
         if (this.lerp.active()) {
            if (!this.enabled.getAsBoolean()) {
               this.lerp.cancel();
            } else {
               this.entity.setPos(this.lerp.next(this.entity.position()));
            }
         }
      }

      public void applyPredictedMovement(Vec3 delta) {
      }

      public boolean hasActiveInterpolation() {
         return this.lerp.active();
      }

      public void cancel() {
         this.lerp.cancel();
      }
   }
}
