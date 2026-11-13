package net.bedrocknative.client.net;

import java.util.List;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.net.nethernet.TrustPrompt;
import net.bedrocknative.net.nethernet.TrustPromptWords;
import net.bedrocknative.net.nethernet.UntrustedServerException;
import net.bedrocknative.text.BedrockText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

public final class ServerTrustScreen extends Screen {
   static final String AUTO_PROPERTY = "bedrocknative.nethernetTrust";
   private static final int BUTTON_WIDTH = 150;
   private static final int WIDE_BUTTON_WIDTH = 280;
   private static final int GAP = 8;
   private static final int WHITE = -1;
   private static final int WARNING = -43691;
   private static final int DIM = -6250336;
   private final ConnectScreen connecting;
   private final UntrustedServerException untrusted;
   private final TrustPrompt prompt;
   private final Component message;
   private final Component keyLine;
   @Nullable
   private Button trust;
   @Nullable
   private Button doNotTrust;

   private ServerTrustScreen(ConnectScreen connecting, UntrustedServerException untrusted, TrustPrompt prompt) {
      super(Component.literal(untrusted.keyChanged() ? "The server's key changed" : text("permissions.servertrust.title", "Trust this server?", List.of())));
      this.connecting = connecting;
      this.untrusted = untrusted;
      this.prompt = prompt;
      this.message = Component.literal(
         untrusted.keyChanged()
            ? TrustPromptWords.changedMessage(untrusted)
            : text(
               "permissions.servertrust.message",
               "You are connecting to %1$s for the first time. Only trust servers you recognize.",
               List.of(untrusted.displayTarget())
            )
      );
      this.keyLine = Component.literal(TrustPromptWords.keyLine(untrusted));
   }

   public static TrustPrompt ask(Minecraft minecraft, ConnectScreen connecting, UntrustedServerException untrusted) {
      TrustPrompt prompt = new TrustPrompt();
      if (!untrusted.keyChanged() && "accept".equalsIgnoreCase(System.getProperty("bedrocknative.nethernetTrust", "").trim())) {
         untrusted.trust();
         BedrockNative.LOGGER
            .info("NetherNet: server key {} trusted without asking (dev switch {}=accept)", untrusted.shortDigest(), "bedrocknative.nethernetTrust");
         prompt.decide(true);
         return prompt;
      } else {
         BedrockNative.LOGGER
            .info(
               "NetherNet: server key {} of {} {}, asking the player",
               new Object[]{
                  untrusted.shortDigest(),
                  untrusted.displayTarget(),
                  untrusted.keyChanged() ? "CHANGED (was " + untrusted.shortPreviousDigest() + ")" : "is new"
               }
            );
         minecraft.execute(() -> {
            if (!connecting.aborted && !prompt.isDone()) {
               ServerTrustScreen screen = new ServerTrustScreen(connecting, untrusted, prompt);
               prompt.whenCancelled(() -> minecraft.execute(() -> {
                  if (minecraft.gui.screen() == screen) {
                     minecraft.gui.setScreen(connecting.parent);
                  }
               }));
               minecraft.gui.setScreen(screen);
            } else {
               prompt.cancel();
            }
         });
         return prompt;
      }
   }

   private static String text(String key, String english, List<String> parameters) {
      return BedrockText.lookupKey(key) != null
         ? BedrockText.translate(key, parameters)
         : english.replace("%1$s", (CharSequence)(parameters.isEmpty() ? "" : parameters.getFirst()));
   }

   protected void init() {
      if (this.untrusted.keyChanged()) {
         int left = this.width / 2 - 140;
         int y = this.height / 2 + 12;
         this.doNotTrust = (Button)this.addRenderableWidget(
            Button.builder(Component.literal("Cancel"), button -> this.refuse()).bounds(left, y, 280, 20).build()
         );
         this.trust = (Button)this.addRenderableWidget(
            Button.builder(Component.literal("Trust the new key and replace the old one"), button -> this.trust()).bounds(left, y + 24, 280, 20).build()
         );
      } else {
         int y = this.height / 2 + 20;
         int left = this.width / 2 - 150 - 4;
         this.trust = (Button)this.addRenderableWidget(
            Button.builder(Component.literal(text("permissions.servertrust.button.trust", "Trust and Join", List.of())), button -> this.trust())
               .bounds(left, y, 150, 20)
               .build()
         );
         this.doNotTrust = (Button)this.addRenderableWidget(
            Button.builder(Component.literal(text("permissions.servertrust.button.doNotTrust", "Don't Trust", List.of())), button -> this.refuse())
               .bounds(this.width / 2 + 4, y, 150, 20)
               .build()
         );
      }
   }

   protected void setInitialFocus() {
      if (this.doNotTrust != null) {
         this.setInitialFocus(this.doNotTrust);
      } else {
         super.setInitialFocus();
      }
   }

   @Nullable
   public Button trustButton() {
      return this.trust;
   }

   @Nullable
   public Button doNotTrustButton() {
      return this.doNotTrust;
   }

   private void trust() {
      if (!this.prompt.isDone()) {
         this.untrusted.trust();
         BedrockNative.LOGGER
            .info("NetherNet: server key {} {} by the player", this.untrusted.shortDigest(), this.untrusted.keyChanged() ? "trusted as the new key" : "trusted");
         this.prompt.decide(true);
         this.minecraft.gui.setScreen(this.connecting);
      }
   }

   private void refuse() {
      if (!this.prompt.isDone()) {
         this.end();
         this.minecraft.gui.setScreen(this.connecting.parent);
      }
   }

   private void end() {
      this.connecting.aborted = true;
      BedrockNative.LOGGER.info("NetherNet: server key {} not trusted, join ended", this.untrusted.shortDigest());
      this.prompt.decide(false);
   }

   public void onClose() {
      this.refuse();
   }

   public void removed() {
      super.removed();
      if (!this.prompt.isDone()) {
         this.end();
      }
   }

   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
      super.extractRenderState(graphics, mouseX, mouseY, a);
      int y = this.height / 2 - 50;
      graphics.centeredText(this.font, this.title, this.width / 2, y, this.untrusted.keyChanged() ? -43691 : -1);
      y += 20;

      for (FormattedCharSequence line : this.font.split(this.message, Math.max(1, this.width - 60)).stream().limit(4L).toList()) {
         graphics.centeredText(this.font, line, this.width / 2, y, -1);
         y += 11;
      }

      graphics.centeredText(this.font, this.keyLine, this.width / 2, this.height / 2 + (this.untrusted.keyChanged() ? 74 : 50), -6250336);
   }
}
