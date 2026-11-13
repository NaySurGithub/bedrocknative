package net.bedrocknative.client.lobby;

import com.mojang.blaze3d.Blaze3D;
import java.net.URI;
import net.bedrocknative.BedrockNative;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

final class DiscordButton {
   private static final URI INVITE = URI.create("https://discord.gg/nU66jacxhP");
   private static final Identifier LOGO = Identifier.fromNamespaceAndPath("bedrocknative", "textures/gui/discord.png");
   private static final int SIZE = 22;
   private static final int MARGIN = 3;
   private final boolean right;
   private final boolean bottom;
   private int x;
   private int y;

   DiscordButton(boolean right, boolean bottom) {
      this.right = right;
      this.bottom = bottom;
   }

   void render(GuiGraphicsExtractor graphics, int width, int height, int mouseX, int mouseY) {
      this.x = this.right ? width - 22 - 3 : 3;
      this.y = this.bottom ? height - 22 - 3 : 3;
      graphics.fill(this.x - 1, this.y - 1, this.x + 22 + 1, this.y + 22 + 1, this.contains(mouseX, mouseY) ? -1 : -14803425);
      graphics.blit(RenderPipelines.GUI_TEXTURED, LOGO, this.x, this.y, 0.0F, 0.0F, 22, 22, 256, 256, 256, 256, -1);
   }

   private boolean contains(double mouseX, double mouseY) {
      return mouseX >= this.x && mouseX < this.x + 22 && mouseY >= this.y && mouseY < this.y + 22;
   }

   boolean clicked(double mouseX, double mouseY) {
      if (!this.contains(mouseX, mouseY)) {
         return false;
      } else {
         try {
            Blaze3D.openUri(INVITE);
         } catch (RuntimeException var6) {
            BedrockNative.LOGGER.warn("Couldn't open the Discord invite: {}", var6.getClass().getSimpleName());
         }

         return true;
      }
   }
}
