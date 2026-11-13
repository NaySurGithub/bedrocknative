package net.bedrocknative.client.mixin.content;

import net.bedrocknative.client.content.ReloadTasks;
import net.minecraft.client.Minecraft;
import net.minecraft.util.thread.BlockableEventLoop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockableEventLoop.class)
public abstract class BlockableEventLoopMixin {
   @Inject(method = "dropAllTasks", at = @At("TAIL"))
   private void bedrocknative$keepReloadSteps(CallbackInfo ci) {
      if ((Object)this instanceof Minecraft minecraft) {
         ReloadTasks.tasksDropped(minecraft);
      }
   }
}
