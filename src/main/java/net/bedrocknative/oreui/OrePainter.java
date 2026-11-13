package net.bedrocknative.oreui;

import java.nio.file.Path;
import org.jspecify.annotations.Nullable;

public interface OrePainter {
   void fill(OreBox var1, int var2);

   void sprite(String var1, float var2, float var3, float var4, float var5, OreBox var6, int var7);

   void mask(String var1, float var2, float var3, float var4, float var5, OreBox var6, int var7);

   void picture(Path var1, OreBox var2, int var3);

   void glyph(OreFont var1, float var2, int var3, float var4, float var5, int var6);

   void pushClip(OreBox var1);

   void popClip();

   int @Nullable [] spriteSize(String var1);
}
