package net.bedrocknative.client.oreui;

import java.nio.file.Path;
import net.bedrocknative.oreui.OreInstall;
import net.bedrocknative.oreui.OreLang;
import net.bedrocknative.oreui.OrePainter;
import net.bedrocknative.oreui.OreText;
import net.bedrocknative.oreui.OreTheme;
import net.bedrocknative.oreui.OreThemeVars;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jspecify.annotations.Nullable;

public final class OreSession {
   private final OreInstall install;
   private final OreTextures textures;
   private final OreGlyphs glyphs = new OreGlyphs();

   OreSession(OreInstall install) {
      this.install = install;
      this.textures = new OreTextures(install);
   }

   public OreInstall install() {
      return this.install;
   }

   public OreText text() {
      return OreUi.text(this.install);
   }

   public OreLang lang() {
      return OreUi.lang();
   }

   public OreTheme theme() {
      return OreUi.theme(this.install);
   }

   public OreThemeVars vars() {
      return this.install.theme();
   }

   @Nullable
   public Path defaultWorldPicture() {
      return this.install.picture("world-preview-default").orElse(null);
   }

   public OreSession.Painter paint(GuiGraphicsExtractor graphics) {
      return new OreSession.Painter(new OreGuiPainter(graphics, this.install, this.textures, this.glyphs));
   }

   public static double guiScale() {
      return Minecraft.getInstance().getWindow().getGuiScale();
   }

   public static int windowWidth() {
      return Minecraft.getInstance().getWindow().getWidth();
   }

   public static int windowHeight() {
      return Minecraft.getInstance().getWindow().getHeight();
   }

   public void forget(@Nullable Path file) {
      this.textures.forget(file);
   }

   public void release() {
      this.textures.release();
      this.glyphs.release();
   }

   public static final class Painter implements AutoCloseable {
      private final OreGuiPainter painter;

      private Painter(OreGuiPainter painter) {
         this.painter = painter;
      }

      public OrePainter painter() {
         return this.painter;
      }

      @Override
      public void close() {
         this.painter.close();
      }
   }
}
