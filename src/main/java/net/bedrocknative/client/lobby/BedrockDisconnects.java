package net.bedrocknative.client.lobby;

import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.compat.LunarCompat;
import net.bedrocknative.client.net.SessionTimeline;
import net.bedrocknative.client.oreui.OreUi;
import net.bedrocknative.net.client.SessionEnd;
import net.bedrocknative.oreui.OreDisconnect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import org.jspecify.annotations.Nullable;

public final class BedrockDisconnects {
   static final long VALID_MS = 30000L;
   @Nullable
   private static volatile BedrockDisconnects.Ended pending;
   @Nullable
   private static volatile BedrockDisconnects.Ended last;

   private BedrockDisconnects() {
   }

   public static void ended(SessionEnd end, ServerData server, String host, int port, Screen parent) {
      BedrockDisconnects.Ended record = new BedrockDisconnects.Ended(end, server, host, port, parent, System.currentTimeMillis());
      pending = record;
      last = record;
      BedrockNative.LOGGER
         .info(
            "Bedrock session ended: {} (server {} at {}:{}, screen {})",
            new Object[]{end.logText(), server.name, host, port, end.showsScreen() ? "Bedrock's disconnect screen" : "none, straight back"}
         );
   }

   public static void connecting() {
      pending = null;
   }

   @Nullable
   public static BedrockDisconnects.Ended last() {
      return last;
   }

   public static boolean isDisconnectScreen(@Nullable Screen screen) {
      return screen instanceof DisconnectedScreen || screen instanceof OreDisconnectScreen;
   }

   public static String describe(@Nullable Screen screen) {
      if (screen instanceof OreDisconnectScreen ore) {
         OreDisconnect data = ore.ui().data();
         return data.title() + " / " + (data.serverMessage().isEmpty() ? data.message() : data.serverMessage()) + " (" + data.reasonName() + ")";
      } else {
         return screen == null ? "none" : screen.getTitle().getString();
      }
   }

   public static Screen swap(DisconnectedScreen java) {
      Screen result = swapScreen(java);
      if (LunarCompat.active()) {
         LunarLobby.note(java, result, Minecraft.getInstance().level != null, true);
      }

      return result;
   }

   private static Screen swapScreen(DisconnectedScreen java) {
      BedrockDisconnects.Ended end = pending;
      if (end != null && System.currentTimeMillis() - end.at() <= 30000L && Minecraft.getInstance().level == null) {
         pending = null;
         Screen shown = java;
         if (!end.end().showsScreen()) {
            shown = end.parent();
         } else if (BedrockLobby.enabled()) {
            try {
               Screen ore = OreDisconnectScreen.create(end);
               if (ore != null) {
                  shown = ore;
               }
            } catch (RuntimeException var4) {
               OreUi.fail("disconnect screen", var4);
            }
         }

         SessionTimeline.leaveScreen(shown.getClass().getSimpleName(), BedrockLobby.texturesAfterLeave());
         return shown;
      } else {
         return java;
      }
   }

   public record Ended(SessionEnd end, ServerData server, String host, int port, Screen parent, long at) {
   }
}
