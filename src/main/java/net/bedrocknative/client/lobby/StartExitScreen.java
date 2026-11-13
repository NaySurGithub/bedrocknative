package net.bedrocknative.client.lobby;

import net.bedrocknative.BedrockNative;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

public final class StartExitScreen extends Screen {
   private static final String MOD_MENU = "modmenu";
   private static final String MOD_MENU_SCREEN = "com.terraformersmc.modmenu.gui.ModsScreen";
   private static final int WIDTH = 200;
   private static final int HEIGHT = 20;
   private static final int GAP = 4;
   private final Screen parent;
   private @Nullable Button mods;
   private @Nullable Button quit;
   private @Nullable Button back;

   public StartExitScreen(Screen parent) {
      super(Component.translatable("menu.quit"));
      this.parent = parent;
   }

   public static boolean modsAvailable() {
      return FabricLoader.getInstance().isModLoaded("modmenu");
   }

   @Override
   protected void init() {
      boolean withMods = modsAvailable();
      int rows = withMods ? 3 : 2;
      int x = (this.width - 200) / 2;
      int y = this.height / 2 - (rows * 20 + (rows - 1) * 4) / 2 + 10;
      this.mods = null;
      if (withMods) {
         this.mods = this.addRenderableWidget(
            Button.builder(Component.translatable("bedrocknative.lobby.mods"), button -> this.openMods()).bounds(x, y, 200, 20).build()
         );
         y += 24;
      }

      this.quit = this.addRenderableWidget(Button.builder(Component.translatable("menu.quit"), button -> this.minecraft.stop()).bounds(x, y, 200, 20).build());
      y += 24;
      this.back = this.addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> this.onClose()).bounds(x, y, 200, 20).build());
   }

   public @Nullable Button modsButton() {
      return this.mods;
   }

   public @Nullable Button quitButton() {
      return this.quit;
   }

   public @Nullable Button backButton() {
      return this.back;
   }

   private void openMods() {
      try {
         Class<?> type = Class.forName("com.terraformersmc.modmenu.gui.ModsScreen");
         Object screen = type.getConstructor(Screen.class).newInstance(this);
         this.minecraft.gui.setScreen((Screen)screen);
      } catch (ReflectiveOperationException | ClassCastException | LinkageError e) {
         BedrockNative.LOGGER.warn("Bedrock lobby: Mod Menu's list didn't open", e);
      }
   }

   @Override
   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
      super.extractRenderState(graphics, mouseX, mouseY, a);
      String text = I18n.get("menu.quit");
      graphics.text(this.font, text, (this.width - this.font.width(text)) / 2, this.height / 2 - 50, -1, true);
   }

   @Override
   public void onClose() {
      this.minecraft.gui.setScreen(this.parent);
   }
}
