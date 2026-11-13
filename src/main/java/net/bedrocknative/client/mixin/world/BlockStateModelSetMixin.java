package net.bedrocknative.client.mixin.world;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.bedrocknative.client.content.BedrockBlockModels;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({BlockStateModelSet.class})
public abstract class BlockStateModelSetMixin {
   @ModifyReturnValue(
      method = {"get"},
      at = {@At("RETURN")}
   )
   private BlockStateModel bedrocknative$customBlock(BlockStateModel original, @Local(argsOnly = true) BlockState state) {
      BlockStateModel custom = BedrockBlockModels.model(state);
      return custom != null ? custom : original;
   }
}
