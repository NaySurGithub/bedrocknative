package net.bedrocknative.model.skin;

import net.bedrocknative.model.entity.ModelRegistry;
import net.bedrocknative.model.geometry.TextureMesher;
import org.jspecify.annotations.Nullable;

public record PreparedSkin(PlayerSkin skin, @Nullable ModelRegistry registry, @Nullable SkinModel model) {
   public long bytes() {
      long pixels = pixels(this.skin.image()) + pixels(this.skin.cape());

      for (PlayerSkin.Animation animation : this.skin.animations().values()) {
         pixels += pixels(animation.image());
      }

      return pixels * 4L;
   }

   private static long pixels(TextureMesher.@Nullable Pixels image) {
      return image != null ? (long)image.width() * image.height() : 0L;
   }
}
