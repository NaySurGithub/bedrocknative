package net.bedrocknative.model.render;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.bedrocknative.molang.MolangProgram;
import net.bedrocknative.molang.MolangResources;
import org.jspecify.annotations.Nullable;

public record RenderControllerDef(
   String name,
   Map<String, Object[]> arrays,
   MolangProgram geometry,
   List<RenderControllerDef.PatternValue> materials,
   List<MolangProgram> textures,
   List<RenderControllerDef.PatternValue> partVisibility,
   @Nullable RenderControllerDef.Color color,
   @Nullable RenderControllerDef.Color overlayColor,
   @Nullable RenderControllerDef.Color isHurtColor,
   @Nullable RenderControllerDef.Color onFireColor,
   @Nullable RenderControllerDef.UvAnimation uvAnimation,
   @Nullable MolangProgram ignoreLighting,
   @Nullable MolangProgram lightColorMultiplier,
   boolean filterLighting,
   boolean rebuildAnimationMatrices
) implements MolangResources {
   @Nullable
   @Override
   public Object[] array(String name) {
      return this.arrays.get(name);
   }

   public record Color(MolangProgram r, MolangProgram g, MolangProgram b, MolangProgram a) {
   }

   public record PatternValue(String pattern, MolangProgram value) {
      static final int MAX_GLOB_LENGTH = 512;

      public boolean matches(String bone) {
         return glob(this.pattern, bone);
      }

      static boolean glob(String pattern, String text) {
         String p = pattern.toLowerCase(Locale.ROOT);
         String t = text.toLowerCase(Locale.ROOT);
         if (p.length() <= 512 && t.length() <= 512) {
            int pi = 0;
            int ti = 0;
            int star = -1;
            int mark = 0;

            while (ti < t.length()) {
               if (pi < p.length() && p.charAt(pi) == '*') {
                  star = pi++;
                  mark = ti;
               } else if (pi < p.length() && p.charAt(pi) == t.charAt(ti)) {
                  pi++;
                  ti++;
               } else {
                  if (star < 0) {
                     return false;
                  }

                  pi = star + 1;
                  ti = ++mark;
               }
            }

            while (pi < p.length() && p.charAt(pi) == '*') {
               pi++;
            }

            return pi == p.length();
         } else {
            return p.equals(t);
         }
      }
   }

   public record UvAnimation(MolangProgram offsetU, MolangProgram offsetV, MolangProgram scaleU, MolangProgram scaleV) {
   }
}
