package net.bedrocknative.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.function.Consumer;
import net.bedrocknative.model.runtime.FirstPersonRig;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

public final class FirstPersonAttachableRenderer implements SpecialModelRenderer<WornAttachables.Frame> {
   public static final FirstPersonAttachableRenderer INSTANCE = new FirstPersonAttachableRenderer();
   private static final FirstPersonRig RIG = new FirstPersonRig();

   private FirstPersonAttachableRenderer() {
   }

   public void submit(
      WornAttachables.@Nullable Frame frame, PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay, boolean hasFoil, int outlineColor
   ) {
      ActorSnapshot snapshot = frame != null ? frame.snapshot() : null;
      if (snapshot != null && snapshot.draw().calls() != 0) {
         poseStack.pushPose();
         poseStack.translate(0.5F, 0.5F, 0.5F);
         poseStack.translate((frame.rightArm() ? -1.0F : 1.0F) * 0.56F, 0.52F, 0.72F);
         snapshot.submitBound(poseStack, collector, light, OverlayTexture.NO_OVERLAY, 0, RIG);
         if (AttachableProbe.enabled()) {
            AttachableProbe.held("first_person_" + frame.slot().bedrock(), poseStack.last().pose(), snapshot, RIG, Vec3.ZERO);
         }

         poseStack.popPose();
      }
   }

   @Override
   public void getExtents(Consumer<Vector3fc> output) {
   }

   public WornAttachables.@Nullable Frame extractArgument(ItemStack stack) {
      return null;
   }
}
