package net.bedrocknative.client.ui;

import net.bedrocknative.client.inventory.JsonUiContainers;
import net.bedrocknative.client.lobby.BedrockDisconnects;
import net.bedrocknative.client.lobby.BedrockLobby;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;

public final class JsonUiScreens {
   private JsonUiScreens() {
   }

   public static Screen swap(Screen screen) {
      if (screen.getClass() == DisconnectedScreen.class) {
         return BedrockDisconnects.swap((DisconnectedScreen)screen);
      } else {
         Screen lobby = BedrockLobby.swap(screen);
         if (lobby != screen) {
            return lobby;
         } else if (screen.getClass() == ChatScreen.class) {
            return JsonUiChatScreen.swap((ChatScreen)screen);
         } else {
            return screen.getClass() == PauseScreen.class ? JsonUiPauseScreen.swap((PauseScreen)screen) : JsonUiContainers.inventory(screen);
         }
      }
   }

   public static void reset() {
      JsonUiContainers.reset();
      JsonUiChatScreen.reset();
      JsonUiPauseScreen.reset();
      JsonUiDiagnostics.reset();
      JsonUiActors.reset();
   }
}
