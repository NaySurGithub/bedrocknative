package net.bedrocknative.client.account;

import net.bedrocknative.client.compat.LunarCompat;
import net.bedrocknative.client.lobby.LobbyScreen;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AfterInit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button.OnPress;
import net.minecraft.client.gui.components.Button.Plain;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;

public final class AccountScreenHooks {
   private AccountScreenHooks() {
   }

   public static void register() {
      ScreenEvents.AFTER_INIT.register((AfterInit)(minecraft, screen, width, height) -> {
         if (screen instanceof TitleScreen && !(screen instanceof LobbyScreen) || screen instanceof JoinMultiplayerScreen) {
            addButton(minecraft, screen, width);
         }

         if (screen instanceof TitleScreen && !(screen instanceof LobbyScreen) && LunarCompat.active()) {
            addProfileButton(minecraft, screen, height);
         }
      });
   }

   private static void addButton(Minecraft minecraft, Screen screen, int screenWidth) {
      Screens.getWidgets(screen).removeIf(AccountScreenHooks.AccountButton.class::isInstance);
      int width = screen instanceof JoinMultiplayerScreen ? (screenWidth - minecraft.font.width(screen.getTitle())) / 2 - 10 : 150;
      Screens.getWidgets(screen)
         .add(new AccountScreenHooks.AccountButton(Math.max(60, Math.min(150, width)), button -> minecraft.gui.setScreen(new AccountsScreen(screen))));
   }

   private static void addProfileButton(Minecraft minecraft, Screen screen, int screenHeight) {
      Screens.getWidgets(screen).removeIf(AccountScreenHooks.ProfileButton.class::isInstance);
      int y = Math.max(6, screenHeight - 90);
      Screens.getWidgets(screen).add(new AccountScreenHooks.ProfileButton(6, y, 90, 20, button -> minecraft.gui.setScreen(new AccountsScreen(screen))));
   }

   public static final class AccountButton extends Plain {
      AccountButton(int width, OnPress onPress) {
         super(4, 4, width, 20, Component.empty(), onPress, DEFAULT_NARRATION);
      }

      public Component getMessage() {
         return AccountManager.buttonLabel();
      }
   }

   public static final class ProfileButton extends Plain {
      ProfileButton(int x, int y, int width, int height, OnPress onPress) {
         super(x, y, width, height, Component.translatable("bedrocknative.accounts.profile"), onPress, DEFAULT_NARRATION);
      }
   }
}
