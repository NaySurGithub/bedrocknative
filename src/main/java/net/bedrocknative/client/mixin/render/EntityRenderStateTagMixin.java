package net.bedrocknative.client.mixin.render;

import net.bedrocknative.client.text.BedrockNameTag;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin({EntityRenderState.class})
public abstract class EntityRenderStateTagMixin implements BedrockNameTag.StateHolder {
   @Unique
   @Nullable
   private BedrockNameTag.Drawn bedrocknative$drawn;

   @Nullable
   @Override
   public BedrockNameTag.Drawn bedrocknative$drawn() {
      return this.bedrocknative$drawn;
   }

   @Override
   public void bedrocknative$setDrawn(@Nullable BedrockNameTag.Drawn drawn) {
      this.bedrocknative$drawn = drawn;
   }
}
