package net.bedrocknative.client.mixin.render;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.blaze3d.font.GlyphProvider;
import java.util.ArrayList;
import java.util.List;
import net.bedrocknative.client.compat.LunarCompat;
import net.bedrocknative.client.text.BedrockGlyphs;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.font.GlyphStitcher;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FontSet.class)
public abstract class FontSetMixin {
   private static final Identifier BEDROCKNATIVE$DEFAULT_FONT = Identifier.withDefaultNamespace("default");
   @Shadow
   @Final
   private GlyphStitcher stitcher;

   @ModifyReturnValue(method = "selectProviders", at = @At("RETURN"))
   private List<GlyphProvider> bedrocknative$bedrockGlyphsFirst(List<GlyphProvider> providers) {
      return this.bedrocknative$withBedrock(providers);
   }

   @ModifyExpressionValue(
      method = "computeGlyphInfo",
      at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/font/FontSet;activeProviders:Ljava/util/List;", opcode = 180)
   )
   private List<GlyphProvider> bedrocknative$bedrockGlyphsInLookup(List<GlyphProvider> providers) {
      if (LunarCompat.active() && !providers.contains(BedrockGlyphs.PROVIDER)) {
         LunarCompat.diagnose(
            "glyphs",
            3,
            "font {} looks a glyph up without the Bedrock glyph provider ({} providers active): added in front",
            ((GlyphStitcherAccessor)this.stitcher).bedrocknative$texturePrefix(),
            providers.size()
         );
         return this.bedrocknative$withBedrock(providers);
      } else {
         return providers;
      }
   }

   private List<GlyphProvider> bedrocknative$withBedrock(List<GlyphProvider> providers) {
      List<GlyphProvider> withBedrock = new ArrayList<>(providers.size() + 2);
      withBedrock.add(BedrockGlyphs.PROVIDER);
      if (BEDROCKNATIVE$DEFAULT_FONT.equals(((GlyphStitcherAccessor)this.stitcher).bedrocknative$texturePrefix())) {
         withBedrock.add(BedrockGlyphs.LATIN_PROVIDER);
      }

      withBedrock.addAll(providers);
      return withBedrock;
   }
}
