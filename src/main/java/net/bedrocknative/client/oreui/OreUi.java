package net.bedrocknative.client.oreui;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.ui.JsonUi;
import net.bedrocknative.content.own.OwnUi;
import net.bedrocknative.oreui.OreFonts;
import net.bedrocknative.oreui.OreInstall;
import net.bedrocknative.oreui.OreLang;
import net.bedrocknative.oreui.OreScale;
import net.bedrocknative.oreui.OreText;
import net.bedrocknative.oreui.OreTheme;
import net.bedrocknative.oreui.OwnOreKit;
import net.bedrocknative.perf.StartupClock;
import net.bedrocknative.text.BedrockLanguage;
import net.minecraft.client.Minecraft;
import net.minecraft.locale.Language;
import org.jspecify.annotations.Nullable;

public final class OreUi {
   static final List<String> REQUIRED_SPRITES = List.of(
      "tabBar_neutral_default", "tabBar_neutral_pressed", "arrowBack", "UI_Menu_WorldsTab", "UI_Menu_ServerTab", "UI_Menu_RealmsTab"
   );
   static final List<String> OWN_TEXTS = List.of(
      "bedrocknative.ore.realmsUnavailable",
      "bedrocknative.ore.notice.social",
      "bedrocknative.ore.notice.import",
      "bedrocknative.ore.notice.realms",
      "bedrocknative.ore.notice.featured",
      "bedrocknative.partner.sponsored",
      "bedrocknative.partner.consent.title",
      "bedrocknative.partner.consent.what",
      "bedrocknative.partner.consent.privacy",
      "bedrocknative.partner.consent.change",
      "bedrocknative.partner.consent.accept",
      "bedrocknative.partner.consent.refuse"
   );
   private static volatile @Nullable CompletableFuture<@Nullable OreInstall> install;
   private static @Nullable OreFonts fonts;
   private static @Nullable OreLang lang;
   private static @Nullable String langCode;
   private static @Nullable OreTheme theme;
   private static boolean logged;
   private static volatile boolean failed;
   private static final Map<Integer, OreText> TEXTS = new HashMap<>();

   private OreUi() {
   }

   public static void preload() {
      installFuture();
   }

   private static CompletableFuture<OreInstall> installFuture() {
      CompletableFuture<OreInstall> current = install;
      if (current == null) {
         current = CompletableFuture.supplyAsync(
            () -> {
               long start = System.nanoTime();
               OreInstall read = OreInstall.read(OreInstall.defaultFolder());
               if (read != null) {
                  long millis = (System.nanoTime() - start) / 1000000L;
                  StartupClock.background("Ore UI pictures", millis);
                  BedrockNative.LOGGER
                     .info(
                        "Ore UI: {} pictures and the theme ({} variables) read from {} in {} ms",
                        new Object[]{read.spriteCount(), read.theme().size(), read.folder(), millis}
                     );
                  prewarm(read);
               } else if (OwnUi.enabled()) {
                  read = OwnOreKit.build();
                  long millis = (System.nanoTime() - start) / 1000000L;
                  StartupClock.background("Ore UI pictures", millis);
                  BedrockNative.LOGGER
                     .info(
                        "Ore UI: no Ore UI in a Bedrock install ({}); the mod's own kit ({} pictures) in {} ms",
                        new Object[]{OreInstall.defaultFolder(), read == null ? 0 : read.spriteCount(), millis}
                     );
                  if (read != null) {
                     prewarm(read);
                  }
               } else {
                  BedrockNative.LOGGER.info("Ore UI: no Ore UI in the Bedrock install ({}); Java's screens stay", OreInstall.defaultFolder());
               }

               return read;
            }
         );
         install = current;
      }

      return current;
   }

   public static OreInstall install() {
      CompletableFuture<OreInstall> current = installFuture();
      return current.isDone() && !current.isCompletedExceptionally() ? current.join() : null;
   }

   public static @Nullable OreInstall awaitInstall() {
      try {
         return installFuture().join();
      } catch (RuntimeException e) {
         return null;
      }
   }

   public static void fail(String what, Throwable e) {
      if (!failed) {
         failed = true;
         BedrockNative.LOGGER.warn("Ore UI: the {} failed; Java's screens take over", what, e);
      }
   }

   public static boolean available() {
      if (failed) {
         return false;
      }

      OreInstall read = awaitInstall();
      if (read == null) {
         return false;
      }

      boolean ok = fonts(read).complete() && REQUIRED_SPRITES.stream().allMatch(read::has);
      if (!ok && !logged) {
         logged = true;
         BedrockNative.LOGGER.warn("Ore UI: the install lacks fonts or pictures the Play screen needs; Java's screens stay");
      }

      return ok;
   }

   private static void prewarm(OreInstall read) {
      long started = System.nanoTime();

      try {
         boolean complete = fonts(read).complete();
         theme(read);
         long millis = (System.nanoTime() - started) / 1000000L;
         StartupClock.background("Ore UI fonts", millis);
         BedrockNative.LOGGER.info("Ore UI: the Play screen's fonts{} and the theme ready in {} ms", complete ? "" : " (incomplete)", millis);
      } catch (RuntimeException | LinkageError e) {
         BedrockNative.LOGGER.info("Ore UI: fonts left to the Play screen ({})", e.toString());
      }
   }

   public static void prewarmTexts() {
      if (install() != null) {
         lang();
      }
   }

   static synchronized OreFonts fonts(OreInstall read) {
      OreFonts current = fonts;
      if (current == null) {
         current = new OreFonts(read);
         fonts = current;
      }

      return current;
   }

   static synchronized OreTheme theme(OreInstall read) {
      OreTheme current = theme;
      if (current == null) {
         current = OreTheme.from(read.theme());
         theme = current;
      }

      return current;
   }

   static synchronized OreLang lang() {
      String code = Minecraft.getInstance().options.languageCode;
      OreLang current = lang;
      if (current == null || !code.equals(langCode)) {
         Map<String, String> own = new HashMap<>();

         for (String key : OWN_TEXTS) {
            own.put(key, Language.getInstance().getOrDefault(key));
         }

         OreInstall read = install();
         current = read != null && read.own()
            ? OwnOreKit.lang(code, BedrockLanguage.baseTexts(code).orElse(Map.of())).with(own)
            : OreLang.load(OreInstall.textsFolder(), BedrockLanguage.textsFolder(), code.toLowerCase(Locale.ROOT)).with(own);
         lang = current;
         langCode = code;
      }

      return current;
   }

   static OreText text(OreInstall read) {
      int scale = JsonUi.uiScale();
      return TEXTS.computeIfAbsent(scale, key -> new OreText(fonts(read), new OreScale(key)));
   }

   public static @Nullable OreSession session() {
      if (!available()) {
         return null;
      }

      OreInstall read = install();
      return read == null ? null : new OreSession(read);
   }
}
