package net.bedrocknative.client.mixin.render;

import net.bedrocknative.client.text.BedrockNameTag;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EntityRenderState.class)
public abstract class EntityRenderStateTagMixin implements BedrockNameTag.StateHolder {
   @Unique
   private BedrockNameTag.@Nullable Drawn bedrocknative$drawn;

   @Override
   public BedrockNameTag.@Nullable Drawn bedrocknative$drawn() {
      return this.bedrocknative$drawn;
   }

   @Override
   public void bedrocknative$setDrawn(BedrockNameTag.@Nullable Drawn drawn) {
      this.bedrocknative$drawn = drawn;
   }
}
