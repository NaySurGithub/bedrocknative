package net.bedrocknative.client.content;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import net.bedrocknative.net.client.PackProgress;
import net.bedrocknative.text.BedrockText;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class PackDownloadScreen extends Screen {
   private static final int BAR_WIDTH = 200;
   private static final int BAR_HEIGHT = 6;
   private final Supplier<PackProgress> progress;
   private final Runnable cancel;

   public PackDownloadScreen(Supplier<PackProgress> progress, Runnable cancel) {
      super(Component.literal(BedrockText.translate("progressScreen.title.applyingPacks", List.of())));
      this.progress = progress;
      this.cancel = cancel;
   }

   protected void init() {
      this.addRenderableWidget(
         Button.builder(Component.literal(BedrockText.translate("gui.cancel", List.of())), button -> this.cancel.run())
            .bounds(this.width / 2 - 100, this.height / 2 + 30, 200, 20)
            .build()
      );
   }

   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
      super.extractRenderState(graphics, mouseX, mouseY, a);
      PackProgress current = this.progress.get();
      int x = (this.width - 200) / 2;
      int y = this.height / 2 - 10;
      String title = current != null && !current.finished()
         ? BedrockText.translate("progressScreen.title.downloading", List.of(current.pack() + "/" + current.packs()))
         : this.getTitle().getString();
      graphics.centeredText(this.font, title, this.width / 2, y - 24, -1);
      graphics.fill(x - 1, y - 1, x + 200 + 1, y + 6 + 1, -16777216);
      graphics.fill(x, y, x + 200, y + 6, -10855846);
      if (current != null && current.totalBytes() > 0L) {
         int filled = (int)(200.0 * Math.min(1.0, (double)current.bytes() / current.totalBytes()));
         graphics.fill(x, y, x + filled, y + 6, -12810969);
         graphics.centeredText(this.font, megabytes(current.bytes()) + " / " + megabytes(current.totalBytes()), this.width / 2, y + 6 + 6, -3750202);
      }
   }

   private static String megabytes(long bytes) {
      return String.format(Locale.ROOT, "%.1f MB", bytes / 1048576.0);
   }

   public boolean shouldCloseOnEsc() {
      return false;
   }

   public boolean isPauseScreen() {
      return false;
   }
}
