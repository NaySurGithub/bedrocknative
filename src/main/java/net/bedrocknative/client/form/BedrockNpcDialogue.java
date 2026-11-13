package net.bedrocknative.client.form;

import java.util.function.Consumer;
import java.util.function.LongUnaryOperator;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.npc.NpcDialogue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.Screen;
import org.cloudburstmc.protocol.bedrock.data.NpcRequestType;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.NpcDialoguePacket;
import org.jspecify.annotations.Nullable;

public final class BedrockNpcDialogue implements FormScreen.Host {
   private final Minecraft minecraft;
   private final Consumer<BedrockPacket> sender;
   private final LongUnaryOperator runtimeIdOf;
   @Nullable
   private NpcDialogueScreen screen;
   private boolean closingReported;
   private int requests;

   public BedrockNpcDialogue(Minecraft minecraft, Consumer<BedrockPacket> sender, LongUnaryOperator runtimeIdOf) {
      this.minecraft = minecraft;
      this.sender = sender;
      this.runtimeIdOf = runtimeIdOf;
   }

   public static boolean handles(BedrockPacket packet) {
      return packet instanceof NpcDialoguePacket;
   }

   public int requestCount() {
      return this.requests;
   }

   @Nullable
   public NpcDialogueScreen screen() {
      return this.screen;
   }

   public void apply(BedrockPacket packet) {
      if (packet instanceof NpcDialoguePacket dialogue && dialogue.getAction() != null) {
         switch (dialogue.getAction()) {
            case OPEN:
               this.open(NpcDialogue.of(dialogue));
               break;
            case CLOSE:
               if (this.screen != null && this.minecraft.gui.screen() == this.screen) {
                  this.minecraft.gui.setScreen(null);
               }
         }
      }
   }

   private void open(NpcDialogue dialogue) {
      Screen current = this.minecraft.gui.screen();
      if (this.minecraft.player != null && !(current instanceof LevelLoadingScreen) && !(current instanceof ProgressScreen)) {
         if (current != null && current != this.screen) {
            BedrockNative.LOGGER.debug("NPC dialogue '{}' ignored: a screen is open", dialogue.sceneName());
         } else {
            NpcDialogueScreen opened = new NpcDialogueScreen(this, dialogue);
            if (this.screen != null) {
               this.reportClosing(this.screen);
            }

            this.screen = opened;
            this.closingReported = false;
            this.minecraft.gui.setScreen(opened);
            this.send(dialogue, NpcRequestType.EXECUTE_OPENING_COMMANDS, 0);
         }
      }
   }

   @Override
   public void submit(FormScreen form, String response) {
      if (form == this.screen) {
         try {
            this.send(this.screen.dialogue(), NpcRequestType.EXECUTE_COMMAND_ACTION, Integer.parseInt(response));
         } catch (NumberFormatException var4) {
         }
      }
   }

   @Override
   public void cancel(FormScreen form) {
      if (form == this.screen && this.minecraft.gui.screen() == form) {
         this.minecraft.gui.setScreen(null);
      }
   }

   @Override
   public void removed(FormScreen form) {
      if (form == this.screen) {
         this.reportClosing(this.screen);
      }
   }

   private void reportClosing(NpcDialogueScreen closing) {
      if (closing == this.screen && !this.closingReported) {
         this.closingReported = true;
         this.send(closing.dialogue(), NpcRequestType.EXECUTE_CLOSING_COMMANDS, 0);
      }
   }

   public void close() {
      NpcDialogueScreen open = this.screen;
      this.screen = null;
      this.closingReported = true;
      if (open != null && this.minecraft.gui.screen() == open) {
         this.minecraft.gui.setScreen(null);
      }
   }

   private void send(NpcDialogue dialogue, NpcRequestType type, int actionIndex) {
      this.sender.accept(dialogue.request(type, this.runtimeIdOf.applyAsLong(dialogue.entityUniqueId()), actionIndex));
      this.requests++;
      BedrockNative.LOGGER.info("NPC dialogue '{}': {} {}", new Object[]{dialogue.sceneName(), type, actionIndex});
   }
}
