package net.bedrocknative.client.model;

import net.bedrocknative.model.runtime.DrawSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

public final class FirstPersonProbe {
   private static final boolean ENABLED = System.getProperty("bedrocknative.devTest") != null;
   private static final float NEAR = 0.05F;
   private static FirstPersonProbe.@Nullable Box arm;
   private static FirstPersonProbe.@Nullable Box item;

   private FirstPersonProbe() {
   }

   static boolean enabled() {
      return ENABLED;
   }

   public static FirstPersonProbe.@Nullable Box arm() {
      return arm;
   }

   public static FirstPersonProbe.@Nullable Box item() {
      return item;
   }

   static void arm(Matrix4fc pose, ActorSnapshot snapshot) {
      if (ENABLED) {
         FirstPersonProbe.Bounds bounds = new FirstPersonProbe.Bounds(pose);
         if (bounds.camera != null) {
            DrawSnapshot draw = snapshot.draw();
            Matrix3f normal = new Matrix3f();

            for (int c = 0; c < Math.min(draw.ownCalls(), draw.calls()); c++) {
               for (int g = 0; g < draw.call(c).groups(); g++) {
                  draw.emit(c, g, bounds.camera, normal, bounds);
               }
            }

            arm = bounds.box();
         }
      }
   }

   public static void item(Matrix4fc pose, ItemStackRenderState state) {
      if (ENABLED) {
         FirstPersonProbe.Bounds bounds = new FirstPersonProbe.Bounds(pose);
         if (bounds.camera != null) {
            Vector3f point = new Vector3f();
            state.visitExtents(extent -> {
               bounds.camera.transformPosition(extent, point);
               bounds.vertex(point.x, point.y, point.z, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
            });
            item = bounds.box();
         }
      }
   }

   private static final class Bounds implements DrawSnapshot.VertexSink {
      final @Nullable Matrix4f camera;
      private final float tanHalf;
      private final float aspect;
      private final int width;
      private final int height;
      private float minX = Float.MAX_VALUE;
      private float minY = Float.MAX_VALUE;
      private float maxX = -Float.MAX_VALUE;
      private float maxY = -Float.MAX_VALUE;

      Bounds(Matrix4fc pose) {
         Minecraft minecraft = Minecraft.getInstance();
         CameraRenderState state = minecraft.gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
         this.width = minecraft.getWindow().getWidth();
         this.height = minecraft.getWindow().getHeight();
         this.tanHalf = (float)Math.tan(Math.toRadians(state.hudFov) / 2.0);
         this.aspect = (float)this.width / Math.max(1, this.height);
         this.camera = this.width > 0 && this.height > 0 ? new Matrix4f(state.viewRotationMatrix).mul(pose) : null;
      }

      @Override
      public void vertex(float x, float y, float z, float u, float v, float nx, float ny, float nz) {
         if (!(z > -0.05F)) {
            float px = (x / (-z * this.tanHalf * this.aspect) + 1.0F) * 0.5F * this.width;
            float py = (1.0F - y / (-z * this.tanHalf)) * 0.5F * this.height;
            this.minX = Math.min(this.minX, px);
            this.minY = Math.min(this.minY, py);
            this.maxX = Math.max(this.maxX, px);
            this.maxY = Math.max(this.maxY, py);
         }
      }

      FirstPersonProbe.@Nullable Box box() {
         return !(this.minX > this.maxX) && !(this.maxX < 0.0F) && !(this.maxY < 0.0F) && !(this.minX > this.width - 1) && !(this.minY > this.height - 1)
            ? new FirstPersonProbe.Box(
               clamp(this.minX, this.width), clamp(this.minY, this.height), clamp(this.maxX, this.width), clamp(this.maxY, this.height), System.nanoTime()
            )
            : null;
      }

      private static int clamp(float value, int size) {
         return Math.clamp(Math.round(value), 0, size - 1);
      }
   }

   public record Box(int minX, int minY, int maxX, int maxY, long nanos) {
      public int distance(FirstPersonProbe.Box other) {
         return Math.max(
            Math.max(Math.abs(this.minX - other.minX), Math.abs(this.minY - other.minY)),
            Math.max(Math.abs(this.maxX - other.maxX), Math.abs(this.maxY - other.maxY))
         );
      }

      @Override
      public String toString() {
         return "[" + this.minX + ", " + this.minY + " .. " + this.maxX + ", " + this.maxY + "]";
      }
   }
}
