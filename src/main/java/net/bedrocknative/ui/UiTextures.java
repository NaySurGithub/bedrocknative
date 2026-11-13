package net.bedrocknative.ui;

import org.jspecify.annotations.Nullable;

public interface UiTextures {
   UiTextures NONE = (texture, fileSystem) -> null;

   @Nullable UiTextureInfo info(String var1, String var2);
}
