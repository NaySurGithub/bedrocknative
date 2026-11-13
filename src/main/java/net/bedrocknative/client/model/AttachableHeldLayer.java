package net.bedrocknative.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import net.bedrocknative.model.runtime.JavaSkeleton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class AttachableHeldLayer<S extends HumanoidRenderState, M extends HumanoidModel<S>> extends RenderLayer<S, M> {
   private final JavaSkeletonAdapter adapter = new JavaSkeletonAdapter();

   public AttachableHeldLayer(RenderLayerParent<S, M> parent) {
      super(parent);
   }

   public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, S state, float yRot, float xRot) {
      WornAttachables.Frame right = held(state.rightHandItemState);
      WornAttachables.Frame left = held(state.leftHandItemState);
      if (right != null || left != null) {
         JavaSkeleton skeleton = this.adapter.pose(this.getParentModel());
         int overlay = LivingEntityRenderer.getOverlayCoords(state, 0.0F);
         submit(right, poseStack, collector, light, overlay, state, skeleton);
         submit(left, poseStack, collector, light, overlay, state, skeleton);
      }
   }

   private static void submit(
      WornAttachables.@Nullable Frame frame,
      PoseStack poseStack,
      SubmitNodeCollector collector,
      int light,
      int overlay,
      HumanoidRenderState state,
      JavaSkeleton skeleton
   ) {
      ActorSnapshot snapshot = frame != null ? frame.snapshot() : null;
      if (snapshot != null && snapshot.draw().calls() != 0) {
         snapshot.submitBound(poseStack, collector, light, overlay, state.outlineColor, skeleton);
         AttachableProbe.submitted(state, frame.slot().bedrock());
         if (AttachableProbe.enabled()
            && state instanceof AvatarRenderState avatar
            && Minecraft.getInstance().player != null
            && avatar.id == Minecraft.getInstance().player.getId()) {
            AttachableProbe.held(frame.slot().bedrock(), poseStack.last().pose(), snapshot, skeleton, new Vec3(state.x, state.y, state.z));
         }
      }
   }

   private static WornAttachables.@Nullable Frame held(ItemStackRenderState item) {
      return (WornAttachables.Frame)item.getData(WornAttachables.HELD);
   }
}
