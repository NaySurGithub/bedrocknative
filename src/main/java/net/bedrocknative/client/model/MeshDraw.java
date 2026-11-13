package net.bedrocknative.client.model;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.model.runtime.DrawSnapshot;
import net.minecraft.client.renderer.SubmitNodeCollector.CustomGeometryRenderer;
import org.jspecify.annotations.Nullable;

final class MeshDraw implements CustomGeometryRenderer, DrawSnapshot.VertexSink {
   private static volatile boolean failed;
   private @Nullable DrawSnapshot snapshot;
   private int call;
   private int group;
   private int root = -1;
   private int subtree = -1;
   private int color;
   private int overlay;
   private int light;
   private @Nullable VertexConsumer buffer;

   static boolean anyFailed() {
      return failed;
   }

   MeshDraw set(DrawSnapshot snapshot, int call, int group, int color, int overlay, int light) {
      return this.set(snapshot, call, group, -1, color, overlay, light);
   }

   MeshDraw set(DrawSnapshot snapshot, int call, int group, int root, int color, int overlay, int light) {
      this.snapshot = snapshot;
      this.call = call;
      this.group = group;
      this.root = root;
      this.subtree = -1;
      this.color = color;
      this.overlay = overlay;
      this.light = light;
      return this;
   }

   MeshDraw subtree(DrawSnapshot snapshot, int call, int group, int bone, int overlay, int light) {
      this.set(snapshot, call, group, -1, -1, overlay, light);
      this.subtree = bone;
      return this;
   }

   @Override
   public void render(Pose pose, VertexConsumer buffer) {
      DrawSnapshot draw = this.snapshot;
      if (draw != null) {
         this.buffer = buffer;

         try {
            if (this.subtree >= 0) {
               draw.emitSubtree(this.call, this.group, this.subtree, pose.pose(), pose.normal(), this);
            } else {
               draw.emit(this.call, this.group, this.root, pose.pose(), pose.normal(), this);
            }
         } catch (RuntimeException e) {
            if (!failed) {
               failed = true;
               BedrockNative.LOGGER.error("Couldn't draw a Bedrock model group", e);
            }
         } finally {
            this.buffer = null;
         }
      }
   }

   @Override
   public void vertex(float x, float y, float z, float u, float v, float nx, float ny, float nz) {
      this.buffer.addVertex(x, y, z, this.color, u, v, this.overlay, this.light, nx, ny, nz);
   }
}
