package net.bedrocknative.client.mixin.model;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EnderDragonRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(EnderDragonRenderer.class)
public interface EnderDragonRendererInvoker {
   @Invoker("submitRays")
   static void bedrocknative$submitRays(PoseStack poseStack, float deathTime, SubmitNodeCollector collector, RenderType renderType) {
      throw new AssertionError();
   }
}
