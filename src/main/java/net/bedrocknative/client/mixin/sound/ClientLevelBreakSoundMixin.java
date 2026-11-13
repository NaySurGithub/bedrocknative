package net.bedrocknative.client.mixin.sound;

import net.bedrocknative.client.sound.JavaSoundBridge;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public abstract class ClientLevelBreakSoundMixin {
   @Inject(method = "levelEvent(Lnet/minecraft/world/entity/Entity;ILnet/minecraft/core/BlockPos;I)V", at = @At("HEAD"))
   private void bedrocknative$breakStarts(@Nullable Entity source, int type, BlockPos pos, int data, CallbackInfo ci) {
      if (type == 2001) {
         JavaSoundBridge.breaking(pos, Block.stateById(data));
      }
   }

   @Inject(method = "levelEvent(Lnet/minecraft/world/entity/Entity;ILnet/minecraft/core/BlockPos;I)V", at = @At("RETURN"))
   private void bedrocknative$breakEnds(@Nullable Entity source, int type, BlockPos pos, int data, CallbackInfo ci) {
      if (type == 2001) {
         JavaSoundBridge.breaking(null, null);
      }
   }
}
