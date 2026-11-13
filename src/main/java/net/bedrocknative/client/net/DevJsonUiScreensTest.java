package net.bedrocknative.client.net;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.ui.JsonUi;
import net.bedrocknative.client.ui.JsonUiChatScreen;
import net.bedrocknative.client.ui.JsonUiHost;
import net.bedrocknative.client.ui.JsonUiPauseScreen;
import net.bedrocknative.client.ui.JsonUiScreen;
import net.bedrocknative.ui.ChatData;
import net.bedrocknative.ui.UiControl;
import net.bedrocknative.ui.UiType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.jspecify.annotations.Nullable;

final class DevJsonUiScreensTest {
   private static final String COMMAND = "/give @s dia";
   private static final String MESSAGE = "BN JSON UI chat";
   private final Minecraft minecraft;

   DevJsonUiScreensTest(Minecraft minecraft) {
      this.minecraft = minecraft;
   }

   void run() {
      this.minecraft.execute(() -> JsonUi.setMode(JsonUi.Mode.ON));

      for (int i = 0; i < 40 && !this.onClient(() -> JsonUi.definitions() != null); i++) {
         sleep(500L);
      }

      this.commands("time set noon", "weather clear");
      if (this.onClient(() -> this.minecraft.player.isDeadOrDying())) {
         this.minecraft.execute(() -> this.minecraft.player.respawn());
         sleep(4000L);
      }

      this.commands("gamemode creative", "tp @s -41.5 102 169.5");
      sleep(3000L);
      this.commands("gamemode survival", "effect @s clear", "effect @s instant_health 1 10 true", "effect @s saturation 1 10 true");
      this.chat();
      this.pause();
      new DevCreativeParityTest(this.minecraft, true).survival();
      BedrockNative.LOGGER.info("Dev jsonui-screens test: done");
   }

   private void chat() {
      String opened = this.onClient(() -> {
         this.minecraft.gui.setScreen(new ChatScreen("", false));
         return name(this.minecraft.gui.screen());
      });
      sleep(500L);
      if (!check("chat opens as Bedrock's (" + opened + ")", this.onClient(() -> this.minecraft.gui.screen() instanceof JsonUiChatScreen))) {
         this.closeScreen();
      } else {
         this.onClient(() -> {
            this.chatScreen().insertText("/give @s dia", true);
            return null;
         });
         sleep(800L);
         if (this.onClient(this::suggestions).isEmpty()) {
            this.onClient(() -> this.chatScreen().keyPressed(new KeyEvent(43, 9, 0)));
            sleep(800L);
         }

         List<String> shown = this.onClient(this::suggestions);
         String box = this.onClient(this::boxText);
         check(
            "chat box shows '" + box + "', autocomplete " + shown.size() + " lines " + shown.subList(0, Math.min(3, shown.size())),
            "/give @s dia".equals(box) && shown.stream().anyMatch(text -> text.startsWith("diamond"))
         );
         this.screenshot("jsonui-chat-autocomplete");
         if (shown.size() > 1) {
            String second = shown.get(1);
            boolean pressed = this.onClient(() -> {
               UiControl line = find(this.chatScreen(), control -> control.type() == UiType.LABEL && control.visible() && second.equals(control.text()));
               if (line == null) {
                  return false;
               } else {
                  press(this.chatScreen(), line.x() + 2.0F, line.y() + line.height() / 2.0F);
                  return true;
               }
            });
            sleep(300L);
            String typed = this.onClient(() -> this.chatScreen().typed());
            check("a clicked suggestion goes into the box ('" + second + "' -> '" + typed + "')", pressed && typed.equals("/give @s " + second));
         }

         this.onClient(() -> {
            this.chatScreen().insertText("BN JSON UI chat", true);
            return null;
         });
         sleep(300L);
         boolean sent = this.onClient(() -> {
            UiControl send = find(this.chatScreen(), control -> control.visible() && "send_button".equals(control.name()));
            UiControl button = send == null ? null : first(send, UiType.BUTTON);
            UiControl target = button != null ? button : send;
            if (target == null) {
               return false;
            } else {
               press(this.chatScreen(), target.x() + target.width() / 2.0F, target.y() + target.height() / 2.0F);
               return true;
            }
         });
         sleep(1500L);
         check(
            "the send button sends and closes the chat (now " + this.onClient(() -> name(this.minecraft.gui.screen())) + ")",
            sent && this.onClient(() -> this.minecraft.gui.screen() == null)
         );
         this.closeScreen();
         this.onClient(() -> {
            this.minecraft.gui.setScreen(new ChatScreen("", false));
            return null;
         });
         sleep(800L);
         boolean echoed = this.onClient(
            () -> this.minecraft.gui.screen() instanceof JsonUiChatScreen
               && find(this.chatScreen(), control -> control.type() == UiType.LABEL && control.visible() && control.text().contains("BN JSON UI chat")) != null
         );
         check("the sent message shows in the chat's list", echoed);
         this.screenshot("jsonui-chat");
         this.onClient(() -> {
            this.chatScreen().setTyped("/");
            return null;
         });
         sleep(1000L);
         List<String> commandList = this.onClient(this::suggestions);
         boolean described = commandList.stream().anyMatch(line -> line.contains(" – "));
         check("the command list shows descriptions (" + commandList.subList(0, Math.min(2, commandList.size())) + ")", described);
         this.screenshot("jsonui-chat-commands");
         this.command("damage @s 1");
         sleep(1500L);
         check(
            "hurt closes the chat (now " + this.onClient(() -> name(this.minecraft.gui.screen())) + ")",
            this.onClient(() -> this.minecraft.gui.screen() == null)
         );
         this.closeScreen();
         this.commands("effect @s instant_health 1 5");
      }
   }

   private JsonUiChatScreen chatScreen() {
      return (JsonUiChatScreen)this.minecraft.gui.screen();
   }

   private List<String> suggestions() {
      List<String> lines = new ArrayList<>();
      if (this.minecraft.gui.screen() instanceof JsonUiChatScreen screen) {
         for (ChatData.Line line : screen.data().lines()) {
            if (line.suggestion()) {
               lines.add(line.text());
            }
         }
      }

      return lines;
   }

   private String boxText() {
      JsonUiChatScreen screen = this.chatScreen();
      UiControl box = find(screen, control -> control.type() == UiType.EDIT_BOX && control.visible());
      return box == null ? "(no box)" : screen.ui().input().text(box);
   }

   private void pause() {
      String opened = this.onClient(() -> {
         this.minecraft.pauseGame(false);
         return name(this.minecraft.gui.screen());
      });
      sleep(800L);
      if (!check("pause menu opens as Bedrock's (" + opened + ")", this.onClient(() -> this.minecraft.gui.screen() instanceof JsonUiPauseScreen))) {
         this.closeScreen();
      } else {
         this.screenshot("jsonui-pause");
         this.pressFiring("button.menu_settings");
         sleep(500L);
         check(
            "Settings opens Java's options (" + this.onClient(() -> name(this.minecraft.gui.screen())) + ")",
            this.onClient(() -> this.minecraft.gui.screen() instanceof OptionsScreen)
         );
         this.onClient(() -> {
            if (this.minecraft.gui.screen() instanceof OptionsScreen options) {
               options.onClose();
            }

            return null;
         });
         sleep(500L);
         check(
            "Esc in the options comes back to Bedrock's pause menu (" + this.onClient(() -> name(this.minecraft.gui.screen())) + ")",
            this.onClient(() -> this.minecraft.gui.screen() instanceof JsonUiPauseScreen)
         );
         this.pressFiring("button.menu_continue");
         sleep(500L);
         check(
            "Resume Game closes the pause menu (now " + this.onClient(() -> name(this.minecraft.gui.screen())) + ")",
            this.onClient(() -> this.minecraft.gui.screen() == null)
         );
         this.closeScreen();
      }
   }

   private void pressFiring(String id) {
      this.onClient(() -> {
         if (this.minecraft.gui.screen() instanceof JsonUiHost host) {
            UiControl button = find(this.minecraft.gui.screen(), control -> {
               if (control.type() == UiType.BUTTON && control.visible()) {
                  for (JsonObject mapping : control.buttonMappings()) {
                     if (mapping.has("to_button_id") && id.equals(mapping.get("to_button_id").getAsString())) {
                        return true;
                     }
                  }

                  return false;
               } else {
                  return false;
               }
            });
            if (button == null) {
               BedrockNative.LOGGER.warn("Dev jsonui-screens test JSON UI: no button fires {} on {}", id, host.getClass().getSimpleName());
               return null;
            } else {
               press(this.minecraft.gui.screen(), button.x() + button.width() / 2.0F, button.y() + button.height() / 2.0F);
               return null;
            }
         } else {
            return null;
         }
      });
   }

   @Nullable
   private static UiControl find(Screen screen, Predicate<UiControl> wanted) {
      if (screen instanceof JsonUiHost host && host.ui().instance().root() != null) {
         UiControl[] found = new UiControl[]{null};
         host.ui().instance().root().walk(control -> {
            if (found[0] == null && wanted.test(control)) {
               found[0] = control;
            }
         });
         return found[0];
      } else {
         return null;
      }
   }

   @Nullable
   private static UiControl first(UiControl from, UiType type) {
      UiControl[] found = new UiControl[]{null};
      from.walk(control -> {
         if (found[0] == null && control.type() == type && control.visible()) {
            found[0] = control;
         }
      });
      return found[0];
   }

   private static void press(Screen screen, float uiX, float uiY) {
      float[] at = JsonUiScreen.toGui(uiX, uiY);
      float x = at[0];
      float y = at[1];
      MouseButtonEvent event = new MouseButtonEvent(x, y, new MouseButtonInfo(1, 0));
      screen.mouseMoved(x, y);
      screen.mouseClicked(event, false);
      screen.mouseReleased(event);
   }

   private void closeScreen() {
      this.minecraft.execute(() -> {
         if (this.minecraft.gui.screen() != null) {
            this.minecraft.gui.setScreen(null);
         }
      });
      sleep(300L);
   }

   private static String name(@Nullable Screen screen) {
      return screen == null ? "nothing" : screen.getClass().getSimpleName();
   }

   private static boolean check(String what, boolean pass) {
      BedrockNative.LOGGER.info("Dev jsonui-screens test JSON UI: {}: {}", what, pass ? "PASS" : "FAIL");
      return pass;
   }

   private <T> T onClient(Supplier<T> task) {
      return CompletableFuture.supplyAsync(task, this.minecraft::execute).join();
   }

   private void commands(String... commands) {
      for (String command : commands) {
         this.command(command);
      }

      sleep(800L);
   }

   private void command(String command) {
      this.minecraft.execute(() -> this.minecraft.getConnection().sendCommand(command));
      sleep(400L);
   }

   private void screenshot(String name) {
      this.minecraft
         .execute(() -> Screenshot.grab(this.minecraft.gameDirectory, name + ".png", this.minecraft.gameRenderer.mainRenderTarget(), 1, message -> {}));
      sleep(500L);
   }

   private static void sleep(long millis) {
      try {
         Thread.sleep(millis);
      } catch (InterruptedException var3) {
         Thread.currentThread().interrupt();
      }
   }
}
