package net.bedrocknative.client.mixin.model;

import net.bedrocknative.client.model.BedrockMobs;
import net.bedrocknative.client.model.BedrockPlayers;
import net.bedrocknative.client.model.OverlayActors;
import net.bedrocknative.client.model.WornAttachables;
import net.bedrocknative.world.entity.BedrockActorEntity;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
   @Inject(
      method = "createRenderState(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;",
      at = @At("RETURN")
   )
   private void bedrocknative$modelRuntime(Entity entity, float partialTicks, CallbackInfoReturnable<EntityRenderState> info) {
      if (!(entity instanceof BedrockActorEntity)) {
         EntityRenderState state = (EntityRenderState)info.getReturnValue();
         if (BedrockPlayers.enabled() && entity instanceof Player player) {
            BedrockPlayers.extract(player, state, partialTicks);
            if (state.getData(BedrockPlayers.FRAME) != null) {
               return;
            }
         }

         if (BedrockMobs.enabled() && !(entity instanceof Player)) {
            BedrockMobs.extract(entity, state, partialTicks);
            if (state.getData(BedrockMobs.FRAME) != null) {
               return;
            }
         }

         OverlayActors.extract(entity, state, partialTicks);
         if (entity instanceof LivingEntity living) {
            WornAttachables.Worn worn = WornAttachables.extractArmor(living, partialTicks);
            if (worn != null) {
               state.setData(WornAttachables.WORN, worn);
            }
         }
      }
   }
}
