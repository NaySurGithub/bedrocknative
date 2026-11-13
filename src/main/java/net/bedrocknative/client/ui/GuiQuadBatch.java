package net.bedrocknative.client.ui;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import net.bedrocknative.client.perf.FrameClock;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.util.Mth;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.joml.Vector2f;
import org.jspecify.annotations.Nullable;

final class GuiQuadBatch implements GuiElementRenderState {
   static final int FLOATS = 8;
   private final TextureSetup textureSetup;
   private final Matrix3x2fc pose;
   private final @Nullable ScreenRectangle scissor;
   private final float[] quads;
   private final int[] colors;
   private final int count;
   private final ScreenRectangle bounds;

   private GuiQuadBatch(
      TextureSetup textureSetup, Matrix3x2fc pose, @Nullable ScreenRectangle scissor, float[] quads, int[] colors, int count, ScreenRectangle bounds
   ) {
      this.textureSetup = textureSetup;
      this.pose = pose;
      this.scissor = scissor;
      this.quads = quads;
      this.colors = colors;
      this.count = count;
      this.bounds = bounds;
   }

   int count() {
      return this.count;
   }

   private boolean same(
      TextureSetup texture, @Nullable ScreenRectangle clip, Matrix3x2fc at, float[] builderQuads, int[] builderColors, int builderCount, ScreenRectangle union
   ) {
      return builderCount == this.count
         && (texture == this.textureSetup || texture.equals(this.textureSetup))
         && Objects.equals(clip, this.scissor)
         && at.m00() == this.pose.m00()
         && at.m01() == this.pose.m01()
         && at.m10() == this.pose.m10()
         && at.m11() == this.pose.m11()
         && at.m20() == this.pose.m20()
         && at.m21() == this.pose.m21()
         && union.equals(this.bounds)
         && Arrays.equals(builderColors, 0, builderCount, this.colors, 0, this.count)
         && Arrays.equals(builderQuads, 0, builderCount * 8, this.quads, 0, this.count * 8);
   }

   @Override
   public void buildVertices(VertexConsumer consumer) {
      for (int i = 0; i < this.count; i++) {
         int at = i * 8;
         float x0 = this.quads[at];
         float y0 = this.quads[at + 1];
         float x1 = this.quads[at + 2];
         float y1 = this.quads[at + 3];
         float u0 = this.quads[at + 4];
         float u1 = this.quads[at + 5];
         float v0 = this.quads[at + 6];
         float v1 = this.quads[at + 7];
         int color = this.colors[i];
         consumer.addVertexWith2DPose(this.pose, x0, y0).setUv(u0, v0).setColor(color);
         consumer.addVertexWith2DPose(this.pose, x0, y1).setUv(u0, v1).setColor(color);
         consumer.addVertexWith2DPose(this.pose, x1, y1).setUv(u1, v1).setColor(color);
         consumer.addVertexWith2DPose(this.pose, x1, y0).setUv(u1, v0).setColor(color);
      }
   }

   @Override
   public RenderPipeline pipeline() {
      return RenderPipelines.GUI_TEXTURED;
   }

   @Override
   public TextureSetup textureSetup() {
      return this.textureSetup;
   }

   @Override
   public @Nullable ScreenRectangle scissorArea() {
      return this.scissor;
   }

   @Override
   public ScreenRectangle bounds() {
      return this.bounds;
   }

   static final class Builder {
      private static final int KEPT_PER_FRAME = 4096;
      private final Vector2f corner = new Vector2f();
      private float[] quads = new float[512];
      private int[] colors = new int[64];
      private int count;
      private @Nullable TextureSetup texture;
      private @Nullable ScreenRectangle scissor;
      private final Matrix3x2f pose = new Matrix3x2f();
      private int left;
      private int top;
      private int right;
      private int bottom;
      private int quadLeft;
      private int quadTop;
      private int quadRight;
      private int quadBottom;
      private int coverRight;
      private int coverBottom;
      private int batches;
      private List<GuiQuadBatch> lastFrame = new ArrayList<>();
      private List<GuiQuadBatch> thisFrame = new ArrayList<>();
      private long frame = Long.MIN_VALUE;
      private int reused;

      void add(
         GuiRenderState state,
         TextureSetup textureSetup,
         Matrix3x2fc currentPose,
         @Nullable ScreenRectangle currentScissor,
         int x0,
         int y0,
         int x1,
         int y1,
         float u0,
         float u1,
         float v0,
         float v1,
         int color
      ) {
         this.bounds(currentPose, x0, y0, x1, y1);
         if (currentScissor == null
            || Math.max(currentScissor.left(), this.quadLeft) < Math.min(currentScissor.right(), this.quadRight)
               && Math.max(currentScissor.top(), this.quadTop) < Math.min(currentScissor.bottom(), this.quadBottom)) {
            if (this.count > 0
               && (
                  textureSetup != this.texture && !textureSetup.equals(this.texture)
                     || currentScissor != this.scissor && !Objects.equals(currentScissor, this.scissor)
                     || !this.samePose(currentPose)
               )) {
               this.flush(state);
            }

            if (this.count == 0) {
               this.texture = textureSetup;
               this.scissor = currentScissor;
               this.pose.set(currentPose);
               this.left = Integer.MAX_VALUE;
               this.top = Integer.MAX_VALUE;
               this.right = Integer.MIN_VALUE;
               this.bottom = Integer.MIN_VALUE;
            }

            if (this.count == this.colors.length) {
               this.quads = Arrays.copyOf(this.quads, this.quads.length * 2);
               this.colors = Arrays.copyOf(this.colors, this.colors.length * 2);
            }

            int at = this.count * 8;
            this.quads[at] = x0;
            this.quads[at + 1] = y0;
            this.quads[at + 2] = x1;
            this.quads[at + 3] = y1;
            this.quads[at + 4] = u0;
            this.quads[at + 5] = u1;
            this.quads[at + 6] = v0;
            this.quads[at + 7] = v1;
            this.colors[this.count] = color;
            this.count++;
            this.left = Math.min(this.left, this.quadLeft);
            this.top = Math.min(this.top, this.quadTop);
            this.right = Math.max(this.right, this.coverRight);
            this.bottom = Math.max(this.bottom, this.coverBottom);
         }
      }

      private boolean samePose(Matrix3x2fc other) {
         return this.pose.m00() == other.m00()
            && this.pose.m01() == other.m01()
            && this.pose.m10() == other.m10()
            && this.pose.m11() == other.m11()
            && this.pose.m20() == other.m20()
            && this.pose.m21() == other.m21();
      }

      private void bounds(Matrix3x2fc at, int x0, int y0, int x1, int y1) {
         at.transformPosition(x0, y0, this.corner);
         float topLeftX = this.corner.x();
         float topLeftY = this.corner.y();
         at.transformPosition(x1, y0, this.corner);
         float topRightX = this.corner.x();
         float topRightY = this.corner.y();
         at.transformPosition(x0, y1, this.corner);
         float bottomLeftX = this.corner.x();
         float bottomLeftY = this.corner.y();
         at.transformPosition(x1, y1, this.corner);
         float bottomRightX = this.corner.x();
         float bottomRightY = this.corner.y();
         float minX = Math.min(Math.min(topLeftX, bottomLeftX), Math.min(topRightX, bottomRightX));
         float maxX = Math.max(Math.max(topLeftX, bottomLeftX), Math.max(topRightX, bottomRightX));
         float minY = Math.min(Math.min(topLeftY, bottomLeftY), Math.min(topRightY, bottomRightY));
         float maxY = Math.max(Math.max(topLeftY, bottomLeftY), Math.max(topRightY, bottomRightY));
         this.quadLeft = Mth.floor(minX);
         this.quadTop = Mth.floor(minY);
         this.quadRight = this.quadLeft + Mth.ceil(maxX - minX);
         this.quadBottom = this.quadTop + Mth.ceil(maxY - minY);
         this.coverRight = Math.max(this.quadRight, Mth.ceil(maxX));
         this.coverBottom = Math.max(this.quadBottom, Mth.ceil(maxY));
      }

      void flush(GuiRenderState state) {
         if (this.count != 0) {
            ScreenRectangle union = new ScreenRectangle(this.left, this.top, this.right - this.left, this.bottom - this.top);
            ScreenRectangle clipped = this.scissor != null ? this.scissor.intersection(union) : union;
            if (clipped != null) {
               long now = FrameClock.frames();
               if (now != this.frame) {
                  this.frame = now;
                  List<GuiQuadBatch> old = this.lastFrame;
                  this.lastFrame = this.thisFrame;
                  this.thisFrame = old;
                  this.thisFrame.clear();
               }

               int place = this.thisFrame.size();
               GuiQuadBatch previous = place < this.lastFrame.size() ? this.lastFrame.get(place) : null;
               GuiQuadBatch batch;
               if (previous != null && previous.same(this.texture, this.scissor, this.pose, this.quads, this.colors, this.count, clipped)) {
                  batch = previous;
                  this.reused++;
               } else {
                  batch = new GuiQuadBatch(
                     this.texture,
                     new Matrix3x2f(this.pose),
                     this.scissor,
                     Arrays.copyOf(this.quads, this.count * 8),
                     Arrays.copyOf(this.colors, this.count),
                     this.count,
                     clipped
                  );
               }

               state.addGuiElement(batch);
               this.batches++;
               if (this.thisFrame.size() < 4096) {
                  this.thisFrame.add(batch);
               }
            }

            this.count = 0;
            this.texture = null;
            this.scissor = null;
         }
      }

      int batches() {
         return this.batches;
      }

      int reused() {
         return this.reused;
      }
   }
}
