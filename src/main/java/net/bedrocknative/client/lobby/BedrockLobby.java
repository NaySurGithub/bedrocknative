package net.bedrocknative.client.lobby;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.compat.LunarCompat;
import net.bedrocknative.client.content.BedrockVanillaTextures;
import net.bedrocknative.client.net.SessionTimeline;
import net.bedrocknative.client.oreui.OreUi;
import net.bedrocknative.client.ui.JsonUi;
import net.bedrocknative.client.world.VanillaRegistries;
import net.bedrocknative.content.BaseContent;
import net.bedrocknative.content.BundledPack;
import net.bedrocknative.content.PackSource;
import net.bedrocknative.content.PackStack;
import net.bedrocknative.perf.StartupClock;
import net.bedrocknative.text.BedrockLanguage;
import net.bedrocknative.text.BedrockText;
import net.bedrocknative.ui.UiDefinitionSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import org.jspecify.annotations.Nullable;

public final class BedrockLobby {
   public static final String PROPERTY = "bedrocknative.bedrockLobby";
   static final boolean ON_BY_DEFAULT = true;
   static final String LAYER = "assets/bedrocknative/lobby_ui";
   private static volatile boolean failed;
   private static volatile @Nullable CompletableFuture<UiDefinitionSet> definitions;
   private static volatile @Nullable List<PackSource> loadedFor;
   private static @Nullable String language;
   private static boolean startScreenTimed;
   private static int lastTab = 2;
   private static volatile @Nullable UiDefinitionSet lastLoaded;
   private static volatile long sessionEndedAt;

   private BedrockLobby() {
   }

   public static boolean enabled() {
      return switched() && BaseContent.hasUi() && !failed && JsonUi.mode() != JsonUi.Mode.OFF;
   }

   static boolean switched() {
      String value = System.getProperty("bedrocknative.bedrockLobby");
      if (value != null && !value.isBlank()) {
         return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "on", "true", "yes", "1" -> true;
            case "off", "false", "no", "0" -> false;
            default -> true;
         };
      } else {
         return true;
      }
   }

   public static void preload() {
      if (switched() && BaseContent.hasUi() && JsonUi.mode() != JsonUi.Mode.OFF) {
         definitions();
      }
   }

   public static void tick(Minecraft minecraft) {
      if (minecraft.level == null && minecraft.gui.overlay() == null && minecraft.gui.screen() != null && StartupClock.titleShown()) {
         BedrockNative.LOGGER.info(StartupClock.line());
      }

      if (minecraft.level == null
         && minecraft.gui.overlay() == null
         && minecraft.gui.screen() != null
         && minecraft.gui.screen().getClass() == TitleScreen.class
         && enabled()
         && definitions() != null) {
         minecraft.gui.setScreen(new TitleScreen());
      }

      if (LunarCompat.active()) {
         LunarLobby.tick(minecraft);
      }
   }

   public static @Nullable UiDefinitionSet definitions() {
      List<PackSource> base = new ArrayList<>();

      for (PackSource pack : PackStack.active().packs()) {
         if (!PackStack.isServerPack(pack)) {
            base.add(pack);
         }
      }

      CompletableFuture<UiDefinitionSet> current = definitions;
      if (current == null || !base.equals(loadedFor)) {
         UiDefinitionSet previous = current != null && current.isDone() && !current.isCompletedExceptionally() ? current.join() : null;
         loadedFor = List.copyOf(base);
         List<PackSource> bottomFirst = new ArrayList<>(base.reversed());
         if (BaseContent.uiSource() != BaseContent.UiSource.OWN) {
            bottomFirst.add(new BundledPack("assets/bedrocknative/lobby_ui"));
         }

         current = CompletableFuture.supplyAsync(() -> {
            long start = System.nanoTime();
            UiDefinitionSet set = UiDefinitionSet.load(bottomFirst);
            long millis = (System.nanoTime() - start) / 1000000L;
            StartupClock.background("lobby definitions", millis);
            BedrockNative.LOGGER.info("Bedrock lobby: {} JSON UI elements in {} ms", set.elementCount(), millis);
            set.problems().log("lobby definitions");
            return set;
         });
         definitions = current;
         lastLoaded = previous;
      }

      if (current.isDone() && !current.isCompletedExceptionally()) {
         lastLoaded = null;
         return current.join();
      } else {
         return lastLoaded;
      }
   }

   public static void sessionEnded() {
      sessionEndedAt = System.currentTimeMillis();
   }

   private static boolean sessionJustEnded() {
      return System.currentTimeMillis() - sessionEndedAt < 10000L;
   }

   static long sinceSessionEnd() {
      long ended = sessionEndedAt;
      return ended == 0L ? -1L : System.currentTimeMillis() - ended;
   }

   public static void connecting() {
      sessionEndedAt = 0L;
   }

   public static Screen swap(Screen screen) {
      Screen result = swapScreen(screen);
      if (LunarCompat.active()) {
         LunarLobby.note(screen, result, Minecraft.getInstance().level != null, sessionJustEnded());
      }

      return result;
   }

   private static Screen swapScreen(Screen screen) {
      Minecraft minecraft = Minecraft.getInstance();
      Class<?> type = screen.getClass();
      boolean lobbyKind = type == TitleScreen.class || type == JoinMultiplayerScreen.class || type == SelectWorldScreen.class;
      Screen lobby = screen;
      if (lobbyKind && (minecraft.level == null || sessionJustEnded()) && enabled()) {
         if (type == TitleScreen.class) {
            lobby = start(screen);
         } else if (OreUi.available()) {
            lobby = play(type == JoinMultiplayerScreen.class ? 2 : 0, screen);
         }
      }

      if (lobbyKind) {
         SessionTimeline.leaveScreen(lobby.getClass().getSimpleName(), texturesAfterLeave());
      }

      return lobby;
   }

   static String texturesAfterLeave() {
      return BedrockVanillaTextures.restorePending() ? "kept in the atlases, no reload" : (BedrockVanillaTextures.isArmed() ? "armed" : "Java's");
   }

   static Screen playScreen(Screen start, int tab) {
      if (tab != 0) {
         VanillaRegistries.get(Minecraft.getInstance());
      }

      try {
         Screen ore = OrePlayScreen.create(start, tab);
         if (ore != null) {
            return ore;
         }
      } catch (RuntimeException e) {
         OreUi.fail("play screen", e);
      }

      return tab == 0 ? new SelectWorldScreen(start) : new JoinMultiplayerScreen(start);
   }

   public static TitleScreen titleScreen(TitleScreen java) {
      return swap(java) instanceof TitleScreen screen ? screen : java;
   }

   static Screen start(Screen java) {
      UiDefinitionSet set = definitions();
      if (set == null) {
         return java;
      }

      try {
         long started = System.nanoTime();
         JsonUiStartScreen screen = JsonUiStartScreen.create(set);
         if (screen == null) {
            fail("start screen", new IllegalStateException("start_screen didn't resolve"));
            return java;
         }

         if (!startScreenTimed) {
            startScreenTimed = true;
            BedrockNative.LOGGER.info("Bedrock lobby: the first start screen built in {} ms", (System.nanoTime() - started) / 1000000L);
         }

         return screen;
      } catch (RuntimeException e) {
         fail("start screen", e);
         return java;
      }
   }

   static Screen play(int tab, Screen java) {
      if (start(java) instanceof JsonUiStartScreen parent) {
         SessionTimeline.leaveMark("start screen");
         Screen play = parent.playScreen(tab);
         SessionTimeline.leaveMark("play screen");
         return play == null ? java : play;
      } else {
         return java;
      }
   }

   static int lastTab() {
      return lastTab;
   }

   static void lastTab(int tab) {
      lastTab = tab;
   }

   static boolean language(Minecraft minecraft, boolean force) {
      String code = minecraft.options.languageCode;
      if (!force && code.equals(language)) {
         return false;
      }

      language = code;
      BedrockLanguage.baseTexts(code).ifPresent(BedrockText::useBedrockLanguage);
      return true;
   }

   static void fail(String what, Throwable e) {
      if (!failed) {
         failed = true;
         BedrockNative.LOGGER.warn("Bedrock lobby: the {} failed; Java's screens take over", what, e);
      }
   }

   public static boolean failed() {
      return failed;
   }

   static void showJavaTitle(Minecraft minecraft) {
      minecraft.gui.setScreen(new TitleScreen());
   }
}
