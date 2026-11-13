package net.bedrocknative.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import java.util.Map;
import net.bedrocknative.PerfProbe;
import net.bedrocknative.model.runtime.ActorBounds;
import net.bedrocknative.model.runtime.DrawSnapshot;
import net.bedrocknative.world.entity.BedrockActorEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

public final class BedrockActorRenderer extends LivingEntityRenderer<BedrockActorEntity, BedrockActorRenderState, EntityModel<BedrockActorRenderState>> {
   public BedrockActorRenderer(Context context) {
      super(context, new BedrockActorRenderer.EmptyModel(), 0.0F);
   }

   public BedrockActorRenderState createRenderState() {
      return new BedrockActorRenderState();
   }

   public Identifier getTextureLocation(BedrockActorRenderState state) {
      return MissingTextureAtlasSprite.getLocation();
   }

   protected @Nullable RenderType getRenderType(BedrockActorRenderState state, boolean isBodyVisible, boolean forceTransparent, boolean appearGlowing) {
      return null;
   }

   public void extractRenderState(BedrockActorEntity entity, BedrockActorRenderState state, float partialTicks) {
      super.extractRenderState(entity, state, partialTicks);
      state.snapshot = null;
      state.shadow = 0.0F;
      ActorState actor = BedrockActors.state(entity);
      if (actor != null && !state.isInvisible) {
         long perf = PerfProbe.start();
         state.snapshot = actor.evaluate(entity, state, partialTicks);
         PerfProbe.end(PerfProbe.Part.MODELS, perf);
         if (state.snapshot != null) {
            state.shadow = Math.min(entity.getBbWidth(), 8.0F) * 0.5F;
         }
      }
   }

   protected float getShadowRadius(BedrockActorRenderState state) {
      return state.shadow;
   }

   protected boolean shouldShowName(BedrockActorEntity entity, double distanceToCameraSq) {
      return super.shouldShowName(entity, distanceToCameraSq)
         && (entity.shouldShowName() || entity.hasCustomName() && entity == this.entityRenderDispatcher.crosshairPickEntity);
   }

   public boolean shouldRender(BedrockActorEntity entity, Frustum culler, double camX, double camY, double camZ, float partialTicks) {
      ActorState actor = BedrockActors.state(entity);
      float[] local = actor != null && actor.instance() != null ? actor.bounds() : null;
      if (local == null) {
         return super.shouldRender(entity, culler, camX, camY, camZ, partialTicks);
      }

      double x = Mth.lerp(partialTicks, entity.xOld, entity.getX());
      double y = Mth.lerp(partialTicks, entity.yOld, entity.getY());
      double z = Mth.lerp(partialTicks, entity.zOld, entity.getZ());
      float yaw = Mth.rotLerp(partialTicks, entity.yBodyRotO, entity.yBodyRot);
      ActorSnapshot last = actor.lastSnapshot();
      float scale = actor.data().scale();
      double[] box = actor.worldBounds();
      ActorBounds.world(
         local,
         x,
         y,
         z,
         yaw,
         scale * (last != null ? last.draw().scale(0) : 1.0F),
         scale * (last != null ? last.draw().scale(1) : 1.0F),
         scale * (last != null ? last.draw().scale(2) : 1.0F),
         box
      );
      double size = Math.max((box[3] - box[0] + box[4] - box[1] + box[5] - box[2]) / 3.0, entity.getBoundingBox().getSize());
      if (Double.isNaN(size) || size <= 0.0) {
         size = 1.0;
      }

      size *= 64.0 * Entity.getViewScale();
      double dx = x - camX;
      double dy = y - camY;
      double dz = z - camZ;
      if (dx * dx + dy * dy + dz * dz >= size * size) {
         return false;
      }

      int visible = culler.cubeInFrustum(box[0] - 0.5, box[1] - 0.5, box[2] - 0.5, box[3] + 0.5, box[4] + 0.5, box[5] + 0.5);
      return visible == -2 || visible == -1;
   }

   public void submit(BedrockActorRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
      ActorSnapshot snapshot = state.snapshot;
      if (snapshot != null && snapshot.draw().calls() > 0) {
         poseStack.pushPose();
         float scale = state.scale;
         poseStack.scale(scale, scale, scale);
         this.setupRotations(state, poseStack, state.bodyRot, scale);
         DrawSnapshot draw = snapshot.draw();
         float model = snapshot.actorScale() / 16.0F;
         poseStack.scale(draw.scale(0) * model, draw.scale(1) * model, draw.scale(2) * model);
         snapshot.submit(poseStack, collector, state.lightCoords, getOverlayCoords(state, 0.0F), state.outlineColor);
         poseStack.popPose();
      }

      super.submit(state, poseStack, collector, camera);
   }

   private static final class EmptyModel extends EntityModel<BedrockActorRenderState> {
      EmptyModel() {
         super(new ModelPart(List.of(), Map.of()));
      }
   }
}
