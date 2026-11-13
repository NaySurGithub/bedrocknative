package net.bedrocknative.client.mixin.movement;

import net.bedrocknative.client.movement.BedrockMovement;
import net.bedrocknative.client.world.BedrockLocalPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import org.cloudburstmc.math.vector.Vector2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({LivingEntity.class})
public abstract class LivingEntityDimensionsMixin {
   @Inject(
      method = {"getDimensions"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void bedrocknative$localDimensions(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
      LivingEntity self = (LivingEntity)this;
      if (self == Minecraft.getInstance().player && BedrockMovement.active() && pose == Pose.STANDING) {
         Vector2f size = BedrockLocalPlayer.localSize();
         if (size != null && (!(Math.abs(size.getX() - 0.6F) < 1.0E-4F) || !(Math.abs(size.getY() - 1.8F) < 1.0E-4F))) {
            float scale = self.getScale();
            float width = size.getX() * scale;
            float height = size.getY() * scale;
            cir.setReturnValue(EntityDimensions.scalable(width, height).withEyeHeight(Math.min(1.62F, height * 0.9F)));
         }
      }
   }
}
