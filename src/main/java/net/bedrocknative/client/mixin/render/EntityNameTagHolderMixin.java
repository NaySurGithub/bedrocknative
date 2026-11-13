package net.bedrocknative.client.mixin.render;

import net.bedrocknative.client.text.BedrockNameTag;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin({Entity.class})
public abstract class EntityNameTagHolderMixin implements BedrockNameTag.Holder {
   @Unique
   @Nullable
   private BedrockNameTag bedrocknative$nameTag;

   @Nullable
   @Override
   public BedrockNameTag bedrocknative$nameTag() {
      return this.bedrocknative$nameTag;
   }

   @Override
   public void bedrocknative$setNameTag(@Nullable BedrockNameTag tag) {
      this.bedrocknative$nameTag = tag;
   }
}
