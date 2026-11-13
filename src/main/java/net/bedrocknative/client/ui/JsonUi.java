package net.bedrocknative.client.ui;

import com.mojang.blaze3d.platform.Window;
import java.util.BitSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.content.BaseContent;
import net.bedrocknative.content.BedrockOptions;
import net.bedrocknative.content.PackSource;
import net.bedrocknative.content.PackStack;
import net.bedrocknative.perf.RenderSwitches;
import net.bedrocknative.text.BedrockText;
import net.bedrocknative.ui.UiDefinitionSet;
import net.bedrocknative.ui.UiEnvironment;
import net.bedrocknative.ui.UiFonts;
import net.bedrocknative.ui.UiPackTextures;
import net.bedrocknative.ui.UiScale;
import net.bedrocknative.ui.UiScreenInstance;
import net.bedrocknative.ui.UiTextMeasurer;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

public final class JsonUi {
   public static final Set<String> PARITY_VERIFIED = Set.of(
      "hud",
      "server_form",
      "chest",
      "inventory",
      "furnace",
      "brewing_stand",
      "creative",
      "anvil",
      "enchanting",
      "grindstone",
      "stonecutter",
      "loom",
      "smithing_table",
      "cartography",
      "beacon",
      "chat",
      "pause",
      "trading",
      "modal_form"
   );
   public static final Set<String> IN_GAME_CHECK_PENDING = Set.of("xbox_signin_qr", "xbox_signin_welcome", "xbox_signin_problem", "xbox_signin_waiting");
   private static volatile JsonUi.Mode mode = JsonUi.Mode.of(System.getProperty("bedrocknative.jsonui", "default"));
   @Nullable
   private static volatile List<PackSource> loadedFor;
   @Nullable
   private static volatile CompletableFuture<UiDefinitionSet> definitions;
   @Nullable
   private static volatile CompletableFuture<UiFonts> fonts;
   private static volatile int serverPacks;
   @Nullable
   private static volatile UiDefinitionSet environmentFor;
   @Nullable
   private static volatile UiEnvironment environment;
   @Nullable
   private static volatile BedrockFont font;
   @Nullable
   private static volatile BedrockOptions options;
   private static int loggedScale;
   private static int loggedJavaOption = -1;
   private static volatile boolean devBackdrop;

   private JsonUi() {
   }

   public static JsonUi.Mode mode() {
      return mode;
   }

   public static void setMode(JsonUi.Mode newMode) {
      mode = newMode;
   }

   @Nullable
   public static UiDefinitionSet definitions() {
      if (mode != JsonUi.Mode.OFF && BaseContent.hasUi()) {
         PackStack stack = PackStack.active();
         List<PackSource> packs = stack.packs();
         if (definitions == null || !packs.equals(loadedFor)) {
            loadedFor = List.copyOf(packs);
            serverPacks = PackStack.serverPackCount();
            definitions = CompletableFuture.supplyAsync(
               () -> {
                  long start = System.nanoTime();
                  UiDefinitionSet set = UiDefinitionSet.load(stack);
                  BedrockNative.LOGGER
                     .info(
                        "JSON UI: {} elements from {} files in {} ms",
                        new Object[]{set.elementCount(), set.files().size(), (System.nanoTime() - start) / 1000000L}
                     );
                  set.problems().log("definitions");
                  return set;
               }
            );
            if (RenderSwitches.fontPreload) {
               CompletableFuture<UiFonts> declared = CompletableFuture.supplyAsync(() -> UiFonts.load(stack));
               declared.thenAcceptAsync(
                  loaded -> {
                     long start = System.nanoTime();
                     int ready = loaded.preload();
                     BedrockNative.LOGGER
                        .info(
                           "JSON UI fonts: {} of {} ready in {} ms (off the client thread)",
                           new Object[]{ready, loaded.declared(), (System.nanoTime() - start) / 1000000L}
                        );
                  }
               );
               fonts = declared;
            } else {
               fonts = null;
            }
         }

         CompletableFuture<UiDefinitionSet> current = definitions;
         return current.isDone() && !current.isCompletedExceptionally() ? current.join() : null;
      } else {
         return null;
      }
   }

   public static void packsChanged() {
      loadedFor = null;
      definitions = null;
      fonts = null;
      environmentFor = null;
      environment = null;
      BedrockFont previous = font;
      font = null;
      if (previous != null) {
         previous.close();
      }

      options = null;
   }

   public static int uiScale() {
      Minecraft minecraft = Minecraft.getInstance();
      Window window = minecraft.getWindow();
      BedrockOptions settings = options;
      if (settings == null) {
         settings = BedrockOptions.read();
         options = settings;
      }

      int javaOption = (Integer)minecraft.options.guiScale().get();
      int bedrock = UiScale.scale(window.getWidth(), window.getHeight(), settings.guiScaleModifier(), settings.accessibilityScaling());
      int scale = UiScale.followingJava(javaOption, window.getWidth(), window.getHeight(), bedrock);
      if (scale != loggedScale || javaOption != loggedJavaOption) {
         loggedScale = scale;
         loggedJavaOption = javaOption;
         BedrockNative.LOGGER
            .info(
               "JSON UI scale {} for a {}x{} window ({}; Bedrock's base {}, modifier {}{}{}; Java's GUI scale {})",
               new Object[]{
                  scale,
                  window.getWidth(),
                  window.getHeight(),
                  javaOption > 0 ? "Java's GUI Scale option " + javaOption : "Java's GUI Scale is Auto: Bedrock's rule",
                  UiScale.base(window.getWidth(), window.getHeight()),
                  settings.guiScaleModifier(),
                  settings.accessibilityScaling() ? ", accessibility scaling" : "",
                  settings.source() != null ? " from " + settings.source() : "",
                  window.getGuiScale()
               }
            );
      }

      return scale;
   }

   public static float uiWidth() {
      return (float)Minecraft.getInstance().getWindow().getWidth() / uiScale();
   }

   public static float uiHeight() {
      return (float)Minecraft.getInstance().getWindow().getHeight() / uiScale();
   }

   public static float toUi(double gui) {
      return (float)(gui * Minecraft.getInstance().getWindow().getGuiScale() / uiScale());
   }

   public static float toGui(float ui) {
      return ui * uiScale() / Minecraft.getInstance().getWindow().getGuiScale();
   }

   public static boolean drawn(String screen) {
      return switch (mode) {
         case OFF -> false;
         case AUTO -> PARITY_VERIFIED.contains(screen);
         case DEFAULT -> !IN_GAME_CHECK_PENDING.contains(screen);
         case ON -> true;
      };
   }

   public static boolean offered(String screen) {
      return mode == JsonUi.Mode.AUTO || drawn(screen);
   }

   public static boolean use(String screen, UiScreenInstance instance) {
      return drawn(screen) || mode == JsonUi.Mode.AUTO && serverContributed(instance);
   }

   private static boolean serverContributed(UiScreenInstance instance) {
      int base = instance.definitions().packs().size() - serverPacks;
      BitSet packs = instance.contributingPacks();
      return base >= 0 && packs.nextSetBit(base) >= 0;
   }

   public static UiEnvironment environment(UiDefinitionSet set) {
      UiEnvironment current = environment;
      if (current == null || environmentFor != set) {
         BedrockFont loaded = BedrockFont.load(readyFonts());
         UiTextMeasurer measurer = (UiTextMeasurer)(loaded.bitmap().hasPages() ? loaded.metrics() : UiTextMeasurer.FIXED);
         current = new UiEnvironment(measurer, new JsonUiTextures(new UiPackTextures(PackStack.active())), BedrockText::lookupKey);
         BedrockFont previous = font;
         font = loaded;
         if (previous != null && previous != loaded) {
            previous.close();
         }

         environment = current;
         environmentFor = set;
      }

      return current;
   }

   @Nullable
   static BedrockFont font() {
      return font;
   }

   private static UiFonts readyFonts() {
      CompletableFuture<UiFonts> preloaded = fonts;
      return preloaded != null && preloaded.isDone() && !preloaded.isCompletedExceptionally() && PackStack.active().packs().equals(loadedFor)
         ? preloaded.join()
         : UiFonts.load(PackStack.active());
   }

   public static void devBackdrop(boolean on) {
      devBackdrop = on;
   }

   static boolean devBackdrop() {
      return devBackdrop;
   }

   public static enum Mode {
      OFF,
      AUTO,
      DEFAULT,
      ON;

      static JsonUi.Mode of(String name) {
         try {
            return valueOf(name.trim().toUpperCase(Locale.ROOT));
         } catch (IllegalArgumentException var2) {
            return DEFAULT;
         }
      }
   }
}
