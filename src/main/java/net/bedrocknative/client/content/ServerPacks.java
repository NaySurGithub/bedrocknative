package net.bedrocknative.client.content;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.form.PackPromptScreen;
import net.bedrocknative.client.net.SessionTimeline;
import net.bedrocknative.content.PackSource;
import net.bedrocknative.content.PackStack;
import net.bedrocknative.net.client.PackChoice;
import net.bedrocknative.net.client.PackOffer;
import net.bedrocknative.net.client.PackProgress;
import net.bedrocknative.text.BedrockLanguage;
import net.bedrocknative.text.BedrockText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.multiplayer.ServerData.ServerPackStatus;
import org.jspecify.annotations.Nullable;

public final class ServerPacks {
   private final Minecraft minecraft;
   private final ServerData server;
   private final Screen connecting;
   private final Runnable leave;
   private final Runnable stackApplied;
   @Nullable
   private volatile PackProgress progress;
   private volatile boolean leaving;
   private volatile boolean closed;
   private boolean applied;

   public ServerPacks(Minecraft minecraft, ServerData server, Screen connecting, Runnable leave, Runnable stackApplied) {
      this.minecraft = minecraft;
      this.server = server;
      this.connecting = connecting;
      this.leave = leave;
      this.stackApplied = stackApplied;
   }

   public boolean leaving() {
      return this.leaving;
   }

   public boolean disabled() {
      return this.server.getResourcePackStatus() == ServerPackStatus.DISABLED;
   }

   public void offer(PackOffer offer, Consumer<PackChoice> answer) {
      ServerPackStatus status = this.server.getResourcePackStatus();
      if (status == ServerPackStatus.ENABLED || status == ServerPackStatus.DISABLED && !offer.anyRequired()) {
         PackChoice choice = status == ServerPackStatus.ENABLED ? PackChoice.EVERYTHING : PackChoice.NONE;
         BedrockNative.LOGGER.info("Server resource packs set to {} for this server: {}", status, choice);
         if (choice == PackChoice.EVERYTHING) {
            this.minecraft.execute(this::showDownloads);
         }

         answer.accept(choice);
      } else {
         this.minecraft.execute(() -> {
            if (this.closed) {
               answer.accept(PackChoice.LEAVE);
            } else {
               this.minecraft.gui.setScreen(new PackPromptScreen(offer, choicex -> {
                  this.remember(choicex);
                  if (choicex == PackChoice.LEAVE) {
                     this.leaving = true;
                  } else if (choicex == PackChoice.NONE) {
                     this.minecraft.gui.setScreen(this.connecting);
                  } else {
                     this.showDownloads();
                  }

                  answer.accept(choicex);
               }));
            }
         });
      }
   }

   public void progress(PackProgress progress) {
      this.progress = progress;
   }

   public void apply(List<? extends PackSource> packs, String baseGameVersion, List<String> experiments) {
      Map<String, String> language = packs.isEmpty()
         ? null
         : BedrockLanguage.load(PackStack.over(packs, PackStack.base(baseGameVersion, experiments)), this.minecraft.options.languageCode);
      this.minecraft.execute(() -> {
         if (this.closed) {
            packs.forEach(PackSource::close);
         } else {
            PackStack.selectBase(baseGameVersion, experiments);
            PackStack.useServerPacks(packs);
            this.applied = !packs.isEmpty();
            SessionTimeline.mark("packs");
            BedrockVanillaTextures.start();
            BedrockTextures.packsChanged();
            if (language != null) {
               BedrockText.useBedrockLanguage(language);
            }

            if (this.minecraft.gui.screen() instanceof PackDownloadScreen) {
               this.minecraft.gui.setScreen(this.connecting);
            }

            this.stackApplied.run();
         }
      });
   }

   public void close() {
      this.closed = true;
      if (this.minecraft.isSameThread()) {
         this.drop();
      } else {
         this.minecraft.execute(this::drop);
      }
   }

   private void drop() {
      PackStack.recordSkippedServerPacks(List.of());
      if (this.applied) {
         this.applied = false;
         PackStack.useServerPacks(List.of());
         BedrockTextures.packsChanged();
      }
   }

   private void showDownloads() {
      if (!this.closed) {
         this.minecraft.gui.setScreen(new PackDownloadScreen(() -> this.progress, () -> {
            this.leaving = true;
            this.leave.run();
         }));
      }
   }

   private void remember(PackChoice choice) {
      ServerPackStatus status = switch (choice) {
         case EVERYTHING -> ServerPackStatus.ENABLED;
         case NONE -> ServerPackStatus.DISABLED;
         default -> null;
      };
      if (status != null && this.server.getResourcePackStatus() != status) {
         this.server.setResourcePackStatus(status);
         if (this.inServerList(this.server)) {
            ServerList.saveSingleServer(this.server);
         }
      }
   }

   private boolean inServerList(ServerData server) {
      ServerList list = new ServerList(this.minecraft);
      list.load();

      for (int i = 0; i < list.size(); i++) {
         ServerData saved = list.get(i);
         if (saved.name.equals(server.name) && saved.ip.equals(server.ip)) {
            return true;
         }
      }

      return false;
   }
}
