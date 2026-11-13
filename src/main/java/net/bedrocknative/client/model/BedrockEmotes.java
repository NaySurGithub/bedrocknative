package net.bedrocknative.client.model;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.model.skin.EmoteLibrary;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping.Category;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.EmotePacket;
import org.jspecify.annotations.Nullable;

public final class BedrockEmotes {
   static final int PAGE_SIZE = 8;
   public static final KeyMapping KEY = new KeyMapping("key.bedrocknative.emotes", 5, Category.MISC);
   private static volatile BedrockEmotes.@Nullable Session session;

   private BedrockEmotes() {
   }

   public static void register() {
      KeyMappingHelper.registerKeyMapping(KEY);
      ClientTickEvents.END_CLIENT_TICK.register(BedrockEmotes::tick);
   }

   public static void start(BedrockEmotes.Session connected) {
      session = connected;
   }

   public static void stop() {
      session = null;
   }

   public static boolean active() {
      return session != null;
   }

   private static void tick(Minecraft minecraft) {
      boolean open = false;

      while (KEY.consumeClick()) {
         open = true;
      }

      if (open && session != null && minecraft.player != null && minecraft.gui.screen() == null) {
         minecraft.gui.setScreen(new BedrockEmotes.EmoteScreen());
      }
   }

   public static EmotePacket packet(long runtimeId, String pieceId, int durationTicks) {
      EmotePacket packet = new EmotePacket();
      packet.setRuntimeEntityId(runtimeId);
      packet.setEmoteId(pieceId);
      packet.setXuid("");
      packet.setPlatformId("");
      packet.setEmoteDuration(durationTicks);
      return packet;
   }

   static int ticksOf(EmoteLibrary.Emote emote) {
      return Math.max(1, Math.round(emote.animation().length() * 20.0F));
   }

   public static Optional<EmoteLibrary.Emote> first() {
      List<EmoteLibrary.Emote> all = PlayerSkins.emotes().all();
      return all.isEmpty() ? Optional.empty() : Optional.of(all.get(0));
   }

   public static boolean play(EmoteLibrary.Emote emote) {
      BedrockEmotes.Session current = session;
      if (current == null) {
         return false;
      }

      int ticks = ticksOf(emote);
      current.send().accept(packet(current.runtimeId(), emote.pieceId(), ticks));
      current.actors().emoteLocal(emote.pieceId(), ticks);
      BedrockNative.LOGGER.debug("Emote {} ({}) sent for {} ticks", new Object[]{emote.name(), emote.pieceId(), ticks});
      return true;
   }

   static final class EmoteScreen extends Screen {
      private int page;

      EmoteScreen() {
         super(Component.translatable("bedrocknative.emotes.title"));
      }

      @Override
      protected void init() {
         List<EmoteLibrary.Emote> all = PlayerSkins.emotes().all();
         int pages = Math.max(1, (all.size() + 8 - 1) / 8);
         this.page = Math.min(this.page, pages - 1);
         int left = this.width / 2 - 100;
         int top = this.height / 2 - 88 - 10;

         for (int i = 0; i < 8 && this.page * 8 + i < all.size(); i++) {
            EmoteLibrary.Emote emote = all.get(this.page * 8 + i);
            this.addRenderableWidget(Button.builder(Component.literal(emote.name()), button -> {
               BedrockEmotes.play(emote);
               this.onClose();
            }).bounds(left, top + i * 22, 200, 20).build());
         }

         int bottom = top + 176 + 4;
         if (pages > 1) {
            this.addRenderableWidget(Button.builder(Component.literal("<"), button -> {
               this.page = (this.page + pages - 1) % pages;
               this.rebuildWidgets();
            }).bounds(left, bottom, 30, 20).build());
            this.addRenderableWidget(Button.builder(Component.literal(">"), button -> {
               this.page = (this.page + 1) % pages;
               this.rebuildWidgets();
            }).bounds(left + 170, bottom, 30, 20).build());
         }

         this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose()).bounds(left + 40, bottom, 120, 20).build());
      }

      @Override
      public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
         super.extractRenderState(graphics, mouseX, mouseY, a);
         graphics.text(this.font, this.getTitle(), (this.width - this.font.width(this.getTitle())) / 2, this.height / 2 - 88 - 28, -1, true);
         if (PlayerSkins.emotes().size() == 0) {
            Component none = Component.translatable("bedrocknative.emotes.none");
            graphics.text(this.font, none, (this.width - this.font.width(none)) / 2, this.height / 2 - 10, -21846, true);
         }
      }

      @Override
      public boolean keyPressed(KeyEvent event) {
         if (BedrockEmotes.KEY.matches(event)) {
            this.onClose();
            return true;
         } else {
            return super.keyPressed(event);
         }
      }
   }

   public record Session(long runtimeId, Consumer<BedrockPacket> send, BedrockActors actors) {
   }
}
