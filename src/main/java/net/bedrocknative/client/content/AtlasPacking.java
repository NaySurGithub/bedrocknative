package net.bedrocknative.client.content;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.TextureFilteringMethod;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

record AtlasPacking(int padding, int granularity, int maxSide) {
   static AtlasPacking of(Minecraft minecraft, Identifier atlas, boolean mipmapped) {
      Options options = minecraft.options;
      int mipLevels = mipmapped ? options.mipmapLevels().get() : 0;
      int anisotropy = options.textureFiltering().get() != TextureFilteringMethod.ANISOTROPIC ? 0 : options.maxAnisotropyBit().get();
      int padding = 1 << mipLevels << Mth.clamp(anisotropy - 1, 0, 4);
      return new AtlasPacking(padding, 1 << mipLevels, minecraft.getAtlasManager().getAtlasOrThrow(atlas).maxSupportedTextureSize());
   }
}
