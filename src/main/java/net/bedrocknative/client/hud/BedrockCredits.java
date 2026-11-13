package net.bedrocknative.client.hud;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import net.bedrocknative.BedrockNative;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.WinScreen;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.ShowCreditsPacket;
import org.cloudburstmc.protocol.bedrock.packet.ShowCreditsPacket.Status;
import org.jspecify.annotations.Nullable;

public final class BedrockCredits {
   private static final AtomicInteger ANSWERED = new AtomicInteger();
   private final Minecraft minecraft;
   private final Consumer<BedrockPacket> send;
   private final LongSupplier runtimeEntityId;
   @Nullable
   private WinScreen screen;

   public BedrockCredits(Minecraft minecraft, Consumer<BedrockPacket> send, LongSupplier runtimeEntityId) {
      this.minecraft = minecraft;
      this.send = send;
      this.runtimeEntityId = runtimeEntityId;
   }

   public void apply(ShowCreditsPacket packet) {
      if (packet.getStatus() == Status.START_CREDITS && this.minecraft.player != null) {
         if (!this.showing()) {
            WinScreen credits = new WinScreen(true, this::finished);
            this.screen = credits;
            this.minecraft.gui.setScreen(credits);
         }
      }
   }

   private void finished() {
      this.screen = null;
      ShowCreditsPacket end = new ShowCreditsPacket();
      end.setRuntimeEntityId(this.runtimeEntityId.getAsLong());
      end.setStatus(Status.END_CREDITS);
      this.send.accept(end);
      ANSWERED.incrementAndGet();
      BedrockNative.LOGGER.info("Credits closed: sent END_CREDITS for runtime id {}", end.getRuntimeEntityId());
      this.minecraft.gui.setScreen(null);
   }

   public boolean showing() {
      return this.screen != null && this.minecraft.gui.screen() == this.screen;
   }

   public static int answered() {
      return ANSWERED.get();
   }
}
