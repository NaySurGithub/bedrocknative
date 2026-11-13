package net.bedrocknative.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public final class AttachableArmorRenderer implements ArmorRenderer {
   private final JavaSkeletonAdapter adapter = new JavaSkeletonAdapter();

   public void render(
      PoseStack poseStack,
      SubmitNodeCollector collector,
      ItemStack stack,
      HumanoidRenderState state,
      EquipmentSlot slot,
      int light,
      HumanoidModel<HumanoidRenderState> model
   ) {
      WornAttachables.Worn worn = (WornAttachables.Worn)state.getData(WornAttachables.WORN);
      WornAttachables.Slot wornSlot = WornAttachables.Slot.of(slot);
      WornAttachables.Frame frame = worn != null && wornSlot != null ? worn.slot(wornSlot) : null;
      ActorSnapshot snapshot = frame != null ? frame.snapshot() : null;
      if (snapshot != null && snapshot.draw().calls() != 0) {
         snapshot.submitBound(poseStack, collector, light, LivingEntityRenderer.getOverlayCoords(state, 0.0F), state.outlineColor, this.adapter.pose(model));
         AttachableProbe.armor(state, slot, poseStack, snapshot, this.adapter);
         AttachableProbe.submitted(state, wornSlot.bedrock());
      }
   }

   public boolean shouldRenderDefaultHeadItem(LivingEntity entity, ItemStack stack) {
      return !WornAttachables.drawsArmor(entity, stack);
   }
}
