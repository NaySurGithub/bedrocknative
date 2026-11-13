package net.bedrocknative.client.mixin.content;

import java.util.List;
import net.bedrocknative.client.content.BedrockSpriteSource;
import net.bedrocknative.client.content.SpriteProvenance;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.renderer.texture.atlas.SpriteSourceList;
import net.minecraft.client.renderer.texture.atlas.SpriteSource.Output;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin({SpriteSourceList.class})
public abstract class SpriteSourceListMixin {
   @Shadow
   @Final
   private List<SpriteSource> sources;

   @ModifyVariable(
      method = {"list"},
      at = {@At("STORE")},
      ordinal = 0,
      require = 0
   )
   private Output bedrocknative$bedrockTexturesLast(Output output) {
      try {
         return (Output)(BedrockSpriteSource.runLast(this.sources) ? new SpriteProvenance(output) : output);
      } catch (UnsupportedOperationException var3) {
         return new SpriteProvenance(output);
      }
   }
}
