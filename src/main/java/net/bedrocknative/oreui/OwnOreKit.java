package net.bedrocknative.oreui;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.Supplier;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.content.ArgbImage;
import net.bedrocknative.content.SmoothFontStandIn;
import net.bedrocknative.content.own.PixelArt;
import net.bedrocknative.text.BedrockLanguage;
import org.jspecify.annotations.Nullable;

public final class OwnOreKit {
   static final Path FOLDER = Path.of(System.getProperty("java.io.tmpdir"), "bedrocknative-own-ore");
   static final String TEXTS = "assets/bedrocknative/own_ui/oreui/";
   static final Set<String> LOOSE = Set.of("world-preview-default");
   private static final String HASH = "0000000000000000";
   static final float SEVEN_ASCENT = 0.8F;
   static final float SEVEN_DESCENT = 0.1F;
   static final float TEN_ASCENT = 0.99F;
   static final float TEN_DESCENT = 0.25F;
   static final float TEN_TEXEL_X = 0.085F;

   private OwnOreKit() {
   }

   public static @Nullable OreInstall build() {
      JavaFontPages pages = JavaFontPages.load();
      if (pages == null) {
         BedrockNative.LOGGER.warn("Own menus: Java's font pages couldn't be read; Ore UI's screens stay Java's");
         return null;
      } else {
         return build(pages, SmoothFontStandIn.INSTANCE.fontBytes().orElse(null));
      }
   }

   static OreInstall build(JavaFontPages pages, byte @Nullable [] noto) {
      Map<String, byte[]> pngs = new HashMap<>();
      Map<String, int[]> sizes = new HashMap<>();

      for (Entry<String, Supplier<ArgbImage>> entry : OwnOreArt.all().entrySet()) {
         ArgbImage image = entry.getValue().get();
         pngs.put(entry.getKey(), PixelArt.png(image));
         sizes.put(entry.getKey(), new int[]{image.width(), image.height()});
      }

      Map<String, OreFont> fonts = new HashMap<>();
      OreFont seven = OreFont.pixel("Minecraft Seven v2", pages, 0.1F, 0.1F, 0.5F, 0.8F, 0.1F);
      OreFont ten = OreFont.pixel("Minecraft Ten v2", pages.titles(), 0.085F, 0.1F, 0.5F, 0.99F, 0.25F);
      fonts.put(OreInstall.faceKey("Minecraft Seven v2", 400, "normal"), seven);
      fonts.put(OreInstall.faceKey("Minecraft Ten v2", 400, "normal"), ten);
      fonts.put(OreInstall.faceKey("Minecraft Five v2", 400, "normal"), seven);
      OreFont notoSans = noto == null ? null : OreFont.load("Noto Sans", noto);
      fonts.put(OreInstall.faceKey("Noto Sans", 400, "normal"), notoSans != null ? notoSans : seven);
      return OreInstall.own(FOLDER, pngs, sizes, LOOSE, fonts, theme());
   }

   static OreThemeVars theme() {
      StringBuilder css = new StringBuilder(".menus{");
      border(
         css,
         "tabBarNeutralDefault",
         "tabBar_neutral_default",
         "2 2 4 2 fill",
         "0.4rem 0.4rem 0.8rem 0.4rem",
         "0 0 0 0",
         "0.4rem",
         "0.4rem",
         "0.8rem",
         "0.4rem"
      );
      border(
         css,
         "tabBarNeutralHovered",
         "tabBar_neutral_hovered",
         "2 2 4 2 fill",
         "0.4rem 0.4rem 0.8rem 0.4rem",
         "0 0 0 0",
         "0.4rem",
         "0.4rem",
         "0.8rem",
         "0.4rem"
      );
      border(
         css,
         "tabBarNeutralFocused",
         "tabBar_neutral_default_focused",
         "3 3 5 3 fill",
         "0.6rem 0.6rem 1rem 0.6rem",
         "0.2rem 0.2rem 0.2rem 0.2rem",
         "0.6rem",
         "0.4rem",
         "1rem",
         "0.4rem"
      );
      border(
         css,
         "tabBarNeutralPressed",
         "tabBar_neutral_pressed",
         "2 2 2 2 fill",
         "0.4rem 0.4rem 0.4rem 0.4rem",
         "0 0 0 0",
         "0.4rem",
         "0.4rem",
         "0.4rem",
         "0.4rem"
      );
      border(
         css,
         "tabBarNeutralPressedFocused",
         "tabBar_neutral_pressed_focused",
         "3 3 3 3 fill",
         "0.6rem 0.6rem 0.6rem 0.6rem",
         "0.2rem 0.2rem 0.2rem 0.2rem",
         "0.6rem",
         "0.4rem",
         "0.6rem",
         "0.4rem"
      );

      for (String field : new String[]{"Default", "Hovered", "Focused"}) {
         border(
            css,
            "baseTextField" + field,
            "baseTextField_neutral80",
            "3 1 1 1 fill",
            "0.6rem 0.2rem 0.2rem 0.2rem",
            "0 0 0 0",
            "0.6rem",
            "0.2rem",
            "0.2rem",
            "0.2rem"
         );
      }

      border(
         css,
         "baseTextFieldDisabled",
         "baseTextField_disabled",
         "3 1 1 1 fill",
         "0.6rem 0.2rem 0.2rem 0.2rem",
         "0 0 0 0",
         "0.6rem",
         "0.2rem",
         "0.2rem",
         "0.2rem"
      );
      border(
         css,
         "listItemBaseDefault",
         "listItem_disabled_default",
         "1 1 1 1 fill",
         "0.2rem 0.2rem 0.2rem 0.2rem",
         "0 0 0 0",
         "0.2rem",
         "0.2rem",
         "0.2rem",
         "0.2rem"
      );
      border(
         css,
         "listItemBaseFocused",
         "listItem_disabled_focused",
         "2 2 2 2 fill",
         "0.4rem 0.4rem 0.4rem 0.4rem",
         "0.2rem 0.2rem 0.2rem 0.2rem",
         "0.2rem",
         "0.2rem",
         "0.2rem",
         "0.2rem"
      );

      for (String action : new String[]{"listItemAction", "listItemAdditionalAction"}) {
         border(
            css,
            action + "Default",
            "listItem_action_default",
            "1 1 1 1 fill",
            "0.2rem 0.2rem 0.2rem 0.2rem",
            "0 0 0 0",
            "0.2rem",
            "0.2rem",
            "0.2rem",
            "0.2rem"
         );
         border(
            css,
            action + "Hovered",
            "listItem_action_hovered",
            "1 1 1 1 fill",
            "0.2rem 0.2rem 0.2rem 0.2rem",
            "0 0 0 0",
            "0.2rem",
            "0.2rem",
            "0.2rem",
            "0.2rem"
         );
         border(
            css,
            action + "Pressed",
            "listItem_action_pressed",
            "1 1 1 1 fill",
            "0.2rem 0.2rem 0.2rem 0.2rem",
            "0 0 0 0",
            "0.2rem",
            "0.2rem",
            "0.2rem",
            "0.2rem"
         );
         border(
            css,
            action + "Focused",
            "listItem_action_focused",
            "3 3 3 3 fill",
            "0.6rem 0.6rem 0.6rem 0.6rem",
            "0.4rem 0.4rem 0.4rem 0.4rem",
            "0.2rem",
            "0.2rem",
            "0.2rem",
            "0.2rem"
         );
         border(
            css,
            action + "PressedFocused",
            "listItem_action_pressed_focused",
            "3 3 3 3 fill",
            "0.6rem 0.6rem 0.6rem 0.6rem",
            "0.4rem 0.4rem 0.4rem 0.4rem",
            "0.2rem",
            "0.2rem",
            "0.2rem",
            "0.2rem"
         );
      }

      css.append("--baseTextFieldColor:#ffffff;--baseTextFieldPlaceholderColor:#b1b2b5;--baseTextFieldCaretColor:#6cc349;");
      return OreThemeVars.parse(css.append('}').toString());
   }

   private static void border(
      StringBuilder css, String prefix, String sprite, String slice, String widths, String outset, String top, String right, String bottom, String left
   ) {
      css.append("--").append(prefix).append("BorderImageSource:url(/hbui/assets/").append(sprite).append('-').append("0000000000000000").append(".png);");
      css.append("--").append(prefix).append("BorderImageSlice:").append(slice).append(';');
      css.append("--").append(prefix).append("BorderImageWidth:").append(widths).append(';');
      css.append("--").append(prefix).append("BorderImageOutset:").append(outset).append(';');
      css.append("--").append(prefix).append("BorderTopWidth:").append(top).append(';');
      css.append("--").append(prefix).append("BorderRightWidth:").append(right).append(';');
      css.append("--").append(prefix).append("BorderBottomWidth:").append(bottom).append(';');
      css.append("--").append(prefix).append("BorderLeftWidth:").append(left).append(';');
   }

   public static OreLang lang(String javaCode, Map<String, String> vanilla) {
      Map<String, String> merged = new HashMap<>(texts("en_us"));
      String code = javaCode.toLowerCase(Locale.ROOT);
      if (!code.equals("en_us")) {
         merged.putAll(texts(code.startsWith("fr") ? "fr_fr" : code));
      }

      merged.putAll(vanilla);
      return OreLang.of(merged);
   }

   static Map<String, String> texts(String code) {
      try (InputStream in = OwnOreKit.class.getClassLoader().getResourceAsStream("assets/bedrocknative/own_ui/oreui/" + code + ".lang")) {
         if (in == null) {
            return Map.of();
         }

         Map<String, String> entries = new HashMap<>();
         new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().forEach(line -> BedrockLanguage.parseLine(line, entries));
         return entries;
      } catch (IOException e) {
         return Map.of();
      }
   }
}
