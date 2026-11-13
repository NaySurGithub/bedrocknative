package net.bedrocknative.client.world;

import java.util.EnumSet;
import java.util.Set;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.text.BedrockText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import org.cloudburstmc.protocol.bedrock.data.LevelEvent;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.LevelEventPacket;

public final class BedrockWorldEvents {
   private static final Set<LevelEvent> CONSUMED = EnumSet.of(
      LevelEvent.ALL_PLAYERS_SLEEPING,
      LevelEvent.JUMP_PREVENTED,
      LevelEvent.ACTIVATE_BLOCK,
      LevelEvent.SET_DATA,
      LevelEvent.AGENT_SPAWN_EFFECT,
      LevelEvent.SIM_TIME_STEP,
      LevelEvent.SIM_TIME_SCALE,
      LevelEvent.CAULDRON_FLUSH
   );
   private static final Set<LevelEvent> LOGGED = EnumSet.noneOf(LevelEvent.class);
   private final Minecraft minecraft;
   private boolean paused;

   public BedrockWorldEvents(Minecraft minecraft) {
      this.minecraft = minecraft;
   }

   public static boolean handles(BedrockPacket packet) {
      return packet instanceof LevelEventPacket event
         && event.getType() instanceof LevelEvent type
         && (type == LevelEvent.GLOBAL_PAUSE || CONSUMED.contains(type));
   }

   public void apply(LevelEventPacket packet) {
      if (packet.getType() instanceof LevelEvent type) {
         if (type == LevelEvent.GLOBAL_PAUSE) {
            this.setPaused(packet.getData() != 0);
         } else if (LOGGED.add(type)) {
            BedrockNative.LOGGER.debug("Level event {} (data {}): nothing for this client to show", type, packet.getData());
         }
      }
   }

   public void setPaused(boolean pause) {
      this.paused = pause;
      ClientLevel level = this.minecraft.level;
      if (level != null) {
         level.tickRateManager().setFrozen(pause);
      }

      if (pause && !(this.minecraft.gui.screen() instanceof BedrockWorldEvents.GlobalPauseScreen)) {
         this.minecraft.gui.setScreen(new BedrockWorldEvents.GlobalPauseScreen());
      } else if (!pause && this.minecraft.gui.screen() instanceof BedrockWorldEvents.GlobalPauseScreen) {
         this.minecraft.gui.setScreen(null);
      }
   }

   public boolean paused() {
      return this.paused;
   }

   public void reset() {
      this.paused = false;
      if (this.minecraft.gui.screen() instanceof BedrockWorldEvents.GlobalPauseScreen) {
         this.minecraft.gui.setScreen(null);
      }
   }

   public static final class GlobalPauseScreen extends Screen {
      GlobalPauseScreen() {
         super(BedrockText.ui("globalPauseScreen.message"));
      }

      @Override
      protected void init() {
         this.addRenderableWidget(
            Button.builder(BedrockText.ui("globalPauseScreen.quit"), button -> this.minecraft.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE))
               .bounds(this.width / 2 - 100, this.height / 2 + 10, 200, 20)
               .build()
         );
      }

      @Override
      public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
         super.extractRenderState(graphics, mouseX, mouseY, a);
         graphics.text(this.minecraft.font, this.getTitle(), (this.width - this.minecraft.font.width(this.getTitle())) / 2, this.height / 2 - 20, -1, true);
      }

      @Override
      public boolean shouldCloseOnEsc() {
         return false;
      }
   }
}
