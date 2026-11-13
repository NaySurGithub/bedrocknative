package net.bedrocknative.client.hud;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.bedrocknative.text.BedrockText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId;
import net.minecraft.network.chat.Component;
import org.cloudburstmc.protocol.bedrock.packet.ToastRequestPacket;

public final class BedrockToasts {
   private static final AtomicInteger SHOWN = new AtomicInteger();

   private BedrockToasts() {
   }

   public static void show(Minecraft minecraft, ToastRequestPacket packet) {
      String content = packet.getContent();
      SystemToast.add(minecraft.gui.toastManager(), new SystemToastId(), text(packet.getTitle()), content.isEmpty() ? null : text(content));
      SHOWN.incrementAndGet();
   }

   static Component text(String text) {
      return text.startsWith("{") ? BedrockText.ui(text) : BedrockText.message(text, List.of(), true);
   }

   public static int shown() {
      return SHOWN.get();
   }
}
