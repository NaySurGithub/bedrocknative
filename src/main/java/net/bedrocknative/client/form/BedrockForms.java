package net.bedrocknative.client.form;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.LogLists;
import net.bedrocknative.client.content.BedrockTextures;
import net.bedrocknative.client.ui.JsonUiHost;
import net.bedrocknative.form.BedrockForm;
import net.bedrocknative.form.FormParser;
import net.bedrocknative.text.BedrockText;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AfterInit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button.OnPress;
import net.minecraft.client.gui.components.Button.Plain;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;
import org.cloudburstmc.protocol.bedrock.data.ModalFormCancelReason;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.ClientboundCloseFormPacket;
import org.cloudburstmc.protocol.bedrock.packet.ModalFormRequestPacket;
import org.cloudburstmc.protocol.bedrock.packet.ModalFormResponsePacket;
import org.cloudburstmc.protocol.bedrock.packet.ServerSettingsRequestPacket;
import org.cloudburstmc.protocol.bedrock.packet.ServerSettingsResponsePacket;
import org.jspecify.annotations.Nullable;

public final class BedrockForms implements FormScreen.Host {
   private static volatile @Nullable BedrockForms active;
   private final Minecraft minecraft;
   private final Consumer<BedrockPacket> sender;
   private final Deque<ModalFormRequestPacket> waiting = new ArrayDeque<>();
   private @Nullable ServerSettingsResponsePacket settings;
   private @Nullable Screen settingsRequestedFor;
   private @Nullable FormScreen covered;
   private int answered;
   private boolean jsonUiFailed;

   public BedrockForms(Minecraft minecraft, Consumer<BedrockPacket> sender) {
      this.minecraft = minecraft;
      this.sender = sender;
   }

   public static boolean handles(BedrockPacket packet) {
      return packet instanceof ModalFormRequestPacket || packet instanceof ServerSettingsResponsePacket || packet instanceof ClientboundCloseFormPacket;
   }

   public static void registerScreenHooks() {
      ScreenEvents.AFTER_INIT.register((AfterInit)(minecraft, screen, width, height) -> {
         BedrockForms forms = active;
         if (forms != null && screen instanceof OptionsScreen) {
            forms.onOptionsScreen(screen);
         }
      });
   }

   public void start() {
      active = this;
   }

   public void close() {
      if (active == this) {
         active = null;
      }

      this.waiting.clear();
      BedrockTextures.releaseAll();
   }

   public int answeredCount() {
      return this.answered;
   }

   public void apply(BedrockPacket packet) {
      switch (packet) {
         case ModalFormRequestPacket request:
            this.onRequest(request);
            break;
         case ServerSettingsResponsePacket response:
            this.onServerSettings(response);
            break;
         case ClientboundCloseFormPacket ignored:
            this.closeAll();
            break;
         default:
      }
   }

   public void tick() {
      while (!this.waiting.isEmpty() && this.minecraft.player != null && this.minecraft.gui.screen() == null) {
         this.onRequest(this.waiting.poll());
      }
   }

   private void onRequest(ModalFormRequestPacket request) {
      BedrockNative.LOGGER.info("Server form {}: {}", request.getFormId(), LogLists.text(String.valueOf(request.getFormData())));
      Screen current = this.minecraft.gui.screen();
      if (this.minecraft.player != null && !(current instanceof LevelLoadingScreen) && !(current instanceof ProgressScreen)) {
         FormScreen below = current instanceof FormScreen form && !form.isServerSettings() ? form : null;
         if (current != null && below == null) {
            BedrockNative.LOGGER.info("Server form {} came while {} is open: answered USER_BUSY", request.getFormId(), describe(current));
            this.send(request.getFormId(), null, ModalFormCancelReason.USER_BUSY);
         } else {
            BedrockForm form;
            try {
               form = FormParser.parse(request.getFormData());
            } catch (FormParser.FormParseException e) {
               BedrockNative.LOGGER.warn("Invalid server form {}: {}", request.getFormId(), e.getMessage());
               form = new BedrockForm.Simple("serverUI.errorTitle", BedrockText.translate("serverUI.errorDescription", List.of(e.getMessage())), List.of());
            }

            this.covered = below;

            try {
               this.minecraft.gui.setScreen(this.screenFor(request.getFormId(), form, below, null));
            } finally {
               this.covered = null;
            }
         }
      } else {
         this.waiting.add(request);
      }
   }

   private FormScreen screenFor(int formId, BedrockForm form, @Nullable FormScreen below, @Nullable Screen settingsParent) {
      JsonUiFormScreen json = this.jsonUiFailed ? null : JsonUiFormScreen.create(this, formId, form, below, settingsParent);
      if (json != null) {
         json.fallback(() -> this.fallBack(json));
         return json;
      } else {
         return this.javaScreen(formId, form, below, settingsParent);
      }
   }

   private FormScreen javaScreen(int formId, BedrockForm form, @Nullable FormScreen below, @Nullable Screen settingsParent) {
      return switch (form) {
         case BedrockForm.Simple simple -> new SimpleFormScreen(this, formId, simple, below);
         case BedrockForm.Modal modal -> new ModalFormScreen(this, formId, modal, below);
         case BedrockForm.Custom custom -> new CustomFormScreen(this, formId, custom, below, settingsParent);
         default -> throw new MatchException(null, null);
      };
   }

   private void fallBack(JsonUiFormScreen failed) {
      this.jsonUiFailed = true;
      if (active == this && this.minecraft.gui.screen() == failed && !failed.isAnswered()) {
         FormScreen java = this.javaScreen(failed.formId(), failed.form(), failed.below(), failed.settingsParent());
         this.covered = failed;

         try {
            this.minecraft.gui.setScreen(java);
         } finally {
            this.covered = null;
         }
      }
   }

   private void closeAll() {
      while (!this.waiting.isEmpty()) {
         this.send(this.waiting.poll().getFormId(), null, ModalFormCancelReason.USER_CLOSED);
      }

      if (this.minecraft.gui.screen() instanceof FormScreen top && !top.isServerSettings()) {
         for (FormScreen form = top; form != null; form = form.below()) {
            this.answer(form, null, ModalFormCancelReason.USER_CLOSED);
         }

         this.minecraft.gui.setScreen(null);
      }
   }

   @Override
   public void submit(FormScreen screen, String response) {
      this.answer(screen, response, null);
      this.leave(screen);
   }

   @Override
   public void cancel(FormScreen screen) {
      this.answer(screen, null, ModalFormCancelReason.USER_CLOSED);
      this.leave(screen);
   }

   @Override
   public void removed(FormScreen screen) {
      if (active == this && screen != this.covered && this.answer(screen, null, ModalFormCancelReason.USER_CLOSED)) {
         for (FormScreen form = screen.below(); form != null; form = form.below()) {
            this.answer(form, null, ModalFormCancelReason.USER_CLOSED);
         }
      }
   }

   private boolean answer(FormScreen screen, @Nullable String data, @Nullable ModalFormCancelReason reason) {
      if (!screen.markAnswered()) {
         return false;
      }

      this.send(screen.formId(), data, reason);
      if (screen.isServerSettings()) {
         this.settings = null;
         this.settingsRequestedFor = null;
      }

      return true;
   }

   private void leave(FormScreen screen) {
      if (this.minecraft.gui.screen() == screen) {
         if (screen.settingsParent() != null) {
            this.minecraft.gui.setScreen(screen.settingsParent());
         } else {
            this.minecraft.gui.setScreen(screen.below());
         }
      }
   }

   private void send(int formId, @Nullable String data, @Nullable ModalFormCancelReason reason) {
      ModalFormResponsePacket packet = new ModalFormResponsePacket();
      packet.setFormId(formId);
      packet.setFormData(data);
      packet.setCancelReason(Optional.ofNullable(reason));
      this.sender.accept(packet);
      this.answered++;
      BedrockNative.LOGGER.info("Answered server form {}: {}", formId, reason != null ? reason : data.strip());
   }

   private static String describe(Screen screen) {
      String kind = screen.getClass().getSimpleName();
      return screen instanceof JsonUiHost host ? kind + " (" + host.ui().instance().root() + ")" : kind;
   }

   private void onOptionsScreen(Screen screen) {
      if (this.settingsRequestedFor != screen) {
         this.settingsRequestedFor = screen;
         this.settings = null;
         this.sender.accept(new ServerSettingsRequestPacket());
      }

      this.addSettingsButton(screen);
   }

   private void onServerSettings(ServerSettingsResponsePacket response) {
      BedrockNative.LOGGER.info("Server settings form {}: {}", response.getFormId(), response.getFormData());
      this.settings = response;
      if (this.minecraft.gui.screen() instanceof OptionsScreen options) {
         this.addSettingsButton(options);
      }
   }

   private void addSettingsButton(Screen screen) {
      Screens.getWidgets(screen).removeIf(BedrockForms.SettingsButton.class::isInstance);
      ServerSettingsResponsePacket response = this.settings;
      if (response != null) {
         BedrockForm.Custom form;
         try {
            if (!(FormParser.parse(response.getFormData()) instanceof BedrockForm.Custom custom)) {
               BedrockNative.LOGGER.warn("Server settings form {} is not a custom form", response.getFormId());
               return;
            }

            form = custom;
         } catch (FormParser.FormParseException e) {
            BedrockNative.LOGGER.warn("Invalid server settings form {}: {}", response.getFormId(), e.getMessage());
            return;
         }

         Component label = form.title().isEmpty() ? BedrockText.ui("options.category.server") : BedrockText.ui(form.title());
         int width = Math.min(150, (screen.width - this.minecraft.font.width(screen.getTitle())) / 2 - 10);
         Screens.getWidgets(screen)
            .add(
               new BedrockForms.SettingsButton(width, label, button -> this.minecraft.gui.setScreen(this.screenFor(response.getFormId(), form, null, screen)))
            );
      }
   }

   public static final class SettingsButton extends Plain {
      SettingsButton(int width, Component label, OnPress onPress) {
         super(4, 4, Math.max(40, width), 20, label, onPress, DEFAULT_NARRATION);
      }
   }
}
