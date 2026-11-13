package net.bedrocknative.oreui;

public record OreTheme(
   OreTheme.Role neutral,
   OreTheme.Role neutral20,
   OreTheme.Role neutral60,
   OreTheme.Role neutral80,
   OreTheme.Role neutral100,
   OreTheme.Role neutralAlpha60,
   OreTheme.Role primary,
   OreTheme.Role secondary,
   OreTheme.Role destructive,
   int text,
   int dimmer,
   int dimmest,
   int overlay50,
   int textShadow
) {
   public static final OreTheme VANILLA = new OreTheme(
      new OreTheme.Role(
         -12039862,
         -10987430,
         -13553101,
         -13553101,
         -1,
         -14803425,
         -13553101,
         white(0.2F),
         white(0.1F),
         white(0.4F),
         white(0.3F),
         white(0.1F),
         black(0.3F),
         black(0.3F),
         white(0.1F),
         -1
      ),
      new OreTheme.Role(
         -1644309,
         -723207,
         -3092012,
         -3092012,
         -14803425,
         -14803425,
         -5131595,
         white(0.6F),
         white(0.4F),
         white(0.8F),
         white(0.6F),
         white(0.1F),
         black(0.3F),
         black(0.3F),
         white(0.1F),
         -16777216
      ),
      new OreTheme.Role(
         -10987430,
         -12039862,
         -13553101,
         -13553101,
         -1,
         -14803425,
         -13553101,
         white(0.1F),
         black(0.3F),
         white(0.1F),
         black(0.3F),
         white(0.1F),
         black(0.3F),
         black(0.3F),
         white(0.1F),
         -1
      ),
      new OreTheme.Role(
         -13553101,
         -12039862,
         -14408667,
         -12039862,
         -1,
         -14803425,
         -14408667,
         white(0.1F),
         black(0.4F),
         white(0.1F),
         black(0.4F),
         white(0.1F),
         black(0.4F),
         black(0.8F),
         white(0.1F),
         -1
      ),
      new OreTheme.Role(
         -14803425,
         -13553101,
         -14803425,
         -13553101,
         -1,
         -14803425,
         -14803425,
         white(0.1F),
         black(0.3F),
         white(0.1F),
         black(0.3F),
         white(0.1F),
         black(0.3F),
         black(0.3F),
         white(0.1F),
         -1
      ),
      new OreTheme.Role(
         black(0.6F),
         -12039862,
         -14408667,
         -12039862,
         -1,
         -14803425,
         -14408667,
         white(0.1F),
         black(0.3F),
         white(0.1F),
         black(0.3F),
         white(0.1F),
         black(0.3F),
         black(0.4F),
         white(0.1F),
         -1
      ),
      new OreTheme.Role(
         -12810969,
         -13999076,
         -14856941,
         -14856941,
         -1,
         -14803425,
         -14856941,
         white(0.2F),
         white(0.1F),
         white(0.4F),
         white(0.3F),
         white(0.1F),
         black(0.3F),
         black(0.3F),
         white(0.1F),
         -1
      ),
      new OreTheme.Role(
         -3092012,
         -5131595,
         -5131595,
         -5131595,
         -14803425,
         -14803425,
         -10987430,
         white(0.6F),
         white(0.4F),
         white(0.8F),
         white(0.6F),
         white(0.1F),
         black(0.3F),
         black(0.3F),
         white(0.1F),
         -1
      ),
      new OreTheme.Role(
         -3525066,
         -4182739,
         -5432035,
         -5432035,
         -1,
         -14803425,
         -5432035,
         white(0.2F),
         white(0.1F),
         white(0.4F),
         white(0.3F),
         white(0.1F),
         black(0.3F),
         black(0.3F),
         white(0.1F),
         -1
      ),
      -1,
      -3092012,
      -5131595,
      black(0.5F),
      black(0.3F)
   );

   private static int white(float alpha) {
      return Math.round(alpha * 255.0F) << 24 | 16777215;
   }

   private static int black(float alpha) {
      return Math.round(alpha * 255.0F) << 24;
   }

   public static OreTheme from(OreThemeVars vars) {
      OreTheme base = VANILLA;
      OreTheme.Role primary = base.primary.withBackground(vars.color("colorsPrimary", base.primary.background()));
      OreTheme.Role secondary = base.secondary.withBackground(vars.color("colorsSecondary", base.secondary.background()));
      OreTheme.Role destructive = base.destructive.withBackground(vars.color("colorsDestructive", base.destructive.background()));
      int text = vars.color("colorsText", base.text);
      return new OreTheme(
         base.neutral.withText(text),
         base.neutral20,
         base.neutral60.withText(text),
         base.neutral80.withText(text),
         base.neutral100.withText(text),
         base.neutralAlpha60.withText(text),
         primary.withText(text),
         secondary,
         destructive.withText(text),
         text,
         vars.color("colorsMuted0", base.dimmer),
         vars.color("colorsMuted1", base.dimmest),
         base.overlay50,
         base.textShadow
      );
   }

   public OreTheme.Role role(OreTheme.Variant variant) {
      return switch (variant) {
         case PRIMARY, HERO -> this.primary;
         case SECONDARY -> this.secondary;
         case DESTRUCTIVE -> this.destructive;
      };
   }

   public static int over(int top, int bottom) {
      float a = (top >>> 24) / 255.0F;
      int r = Math.round((top >> 16 & 0xFF) * a + (bottom >> 16 & 0xFF) * (1.0F - a));
      int g = Math.round((top >> 8 & 0xFF) * a + (bottom >> 8 & 0xFF) * (1.0F - a));
      int b = Math.round((top & 0xFF) * a + (bottom & 0xFF) * (1.0F - a));
      return 0xFF000000 | r << 16 | g << 8 | b;
   }

   public record Role(
      int background,
      int hovered,
      int pressed,
      int selected,
      int text,
      int border,
      int shadow,
      int specularTop,
      int specularBottom,
      int specularHoveredTop,
      int specularHoveredBottom,
      int bevelTop,
      int bevelBottom,
      int pressedBevelTop,
      int pressedBevelBottom,
      int outline
   ) {
      OreTheme.Role withBackground(int newBackground) {
         return new OreTheme.Role(
            newBackground,
            this.hovered,
            this.pressed,
            this.selected,
            this.text,
            this.border,
            this.shadow,
            this.specularTop,
            this.specularBottom,
            this.specularHoveredTop,
            this.specularHoveredBottom,
            this.bevelTop,
            this.bevelBottom,
            this.pressedBevelTop,
            this.pressedBevelBottom,
            this.outline
         );
      }

      OreTheme.Role withText(int newText) {
         return new OreTheme.Role(
            this.background,
            this.hovered,
            this.pressed,
            this.selected,
            newText,
            this.border,
            this.shadow,
            this.specularTop,
            this.specularBottom,
            this.specularHoveredTop,
            this.specularHoveredBottom,
            this.bevelTop,
            this.bevelBottom,
            this.pressedBevelTop,
            this.pressedBevelBottom,
            this.outline
         );
      }
   }

   public enum Variant {
      PRIMARY,
      SECONDARY,
      DESTRUCTIVE,
      HERO;
   }
}
