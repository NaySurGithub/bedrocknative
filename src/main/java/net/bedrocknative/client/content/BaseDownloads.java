package net.bedrocknative.client.content;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.sound.BedrockAudio;
import net.bedrocknative.content.BaseContent;
import net.bedrocknative.content.FolderPack;
import net.bedrocknative.content.PackSource;
import net.bedrocknative.content.PackStack;
import net.bedrocknative.content.base.BaseConsent;
import net.bedrocknative.content.base.BaseDownload;
import net.bedrocknative.content.base.BaseTable;
import net.bedrocknative.content.base.DownloadedBase;
import net.bedrocknative.content.base.EmbeddedBase;
import net.bedrocknative.sound.SoundCatalog;
import net.bedrocknative.sound.SoundDefinitions;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;

public final class BaseDownloads {
   private static final Set<String> PENDING = new LinkedHashSet<>();
   private static boolean noticeChecked;
   private static boolean asked;
   private static volatile boolean baseLate;

   private BaseDownloads() {
   }

   static Path configFolder() {
      return FabricLoader.getInstance().getConfigDir().resolve("bedrocknative");
   }

   public static void register() {
      BaseDownload.allowWhen(() -> BaseConsent.allowed(configFolder()));
      BaseDownload.listen(result -> Minecraft.getInstance().execute(() -> arrived(result)));
      EmbeddedBase.whenLate(() -> baseLate = true);
      ClientTickEvents.END_CLIENT_TICK.register(BaseDownloads::tick);
   }

   static boolean needed() {
      return BaseContent.kind() == BaseContent.Kind.EMBEDDED && BaseContent.embeddedPack().isPresent() && BaseTable.bundled() != null;
   }

   private static void tick(Minecraft minecraft) {
      useLateBase(minecraft);
      if (needed() && minecraft.level == null && minecraft.gui.overlay() == null && minecraft.gui.screen() instanceof TitleScreen title) {
         if (!noticeChecked) {
            noticeChecked = true;
            if (BaseNotice.shouldShow(configFolder())) {
               minecraft.gui.setScreen(new BaseNoticeScreen(title, () -> {
                  try {
                     BaseNotice.markSeen(configFolder());
                  } catch (IOException var1x) {
                     BedrockNative.LOGGER.warn("Couldn't keep the base notice as seen: {}", var1x.getMessage());
                  }
               }));
               return;
            }
         }

         if (!asked && !PENDING.isEmpty() && BaseConsent.shouldAsk(configFolder())) {
            asked = true;
            ask(minecraft, title);
         }
      }
   }

   private static void ask(Minecraft minecraft, Screen screen) {
      List<BaseTable.Entry> files = BaseDownload.fetchable(PENDING);
      if (files.isEmpty()) {
         PENDING.clear();
      } else {
         long bytes = files.stream().mapToLong(BaseTable.Entry::size).sum();
         String size = String.format(Locale.ROOT, "%.1f MB", bytes / 1000000.0);
         minecraft.gui.setScreen(new BaseDownloadScreen(screen, size, answer -> {
            try {
               BaseConsent.store(configFolder(), answer);
            } catch (IOException var3x) {
               BedrockNative.LOGGER.warn("Couldn't keep the base download answer: {}", var3x.getMessage());
            }

            if (answer == BaseConsent.Answer.YES && BaseDownload.want(PENDING)) {
               BedrockNative.LOGGER.info("Base download: {} sound files asked for after the player's yes", files.size());
            }

            if (answer != BaseConsent.Answer.LATER) {
               PENDING.clear();
            }
         }));
      }
   }

   static void queueForQuestion(Collection<String> paths) {
      PENDING.addAll(paths);
      asked = false;
   }

   static void arrived(BaseDownload.Result result) {
      Path folder = DownloadedBase.folder();

      for (PackSource pack : PackStack.active().packs()) {
         if (pack instanceof FolderPack folderPack && folderPack.root().equals(folder)) {
            folderPack.forgetFolders();
         }
      }

      BedrockAudio.forgetMissing();
   }

   private static void useLateBase(Minecraft minecraft) {
      if (baseLate && minecraft.level == null) {
         baseLate = false;
         BaseContent.refresh();
         if (PackStack.selectBase(null, List.of())) {
            BedrockTextures.packsChanged();
         }
      }
   }

   public static void catalogLoaded(SoundCatalog catalog, List<? extends PackSource> extra) {
      if (needed() && catalog.serverPacks() != 0) {
         List<String> missing = new ArrayList<>();

         for (SoundDefinitions.Definition definition : catalog.definitions().all().values()) {
            if (definition.serverPack()) {
               for (SoundDefinitions.Variant variant : definition.variants()) {
                  if (catalog.foundFile(variant.path(), extra) == null) {
                     missing.add(variant.path());
                  }
               }
            }
         }

         if (!missing.isEmpty()) {
            if (BaseConsent.allowed(configFolder())) {
               if (BaseDownload.want(missing)) {
                  BedrockNative.LOGGER.info("Base download: {} sound files the server's definitions name and no pack has were asked for", missing.size());
               }
            } else if (BaseConsent.shouldAsk(configFolder())) {
               List<BaseTable.Entry> files = BaseDownload.fetchable(missing);
               files.forEach(entry -> PENDING.add(entry.path()));
               if (!files.isEmpty()) {
                  BedrockNative.LOGGER
                     .info("Base download: {} sound files the server's definitions name wait for the player's answer on the title screen", files.size());
               }
            }
         }
      }
   }
}
