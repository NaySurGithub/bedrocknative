package net.bedrocknative.client.net;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.ui.JsonUi;
import net.bedrocknative.client.ui.JsonUiHud;
import net.bedrocknative.ui.UiControl;
import net.bedrocknative.ui.UiScreenInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

final class DevJsonUiHudTest {
   private final Minecraft minecraft;

   DevJsonUiHudTest(Minecraft minecraft) {
      this.minecraft = minecraft;
   }

   void run() {
      this.minecraft.execute(() -> JsonUi.setMode(JsonUi.Mode.ON));

      for (int i = 0; i < 40 && this.read(hud -> hud) == null; i++) {
         sleep(500L);
      }

      this.log(
         "ready",
         hud -> "controls="
            + hud.controlCount()
            + " owns="
            + JsonUiHud.owns()
            + " probe="
            + (hud.find("bn_jsonui_probe") != null)
            + " problems="
            + hud.problems()
      );
      this.commands("title @s clear", "title @s times 10 100 10", "title @s title BNUI:hello");
      sleep(1500L);
      this.log(
         "title",
         hud -> {
            UiControl probe = hud.find("bn_jsonui_probe");
            UiControl title = hud.find("hud_title_text");
            return "probe='"
               + (probe == null ? null : probe.text())
               + "' probeVisible="
               + (probe != null && probe.visible())
               + " title='"
               + (title != null && title.find("title") != null ? title.find("title").text() : null)
               + "'";
         }
      );
      this.screenshot("jsonui-hud-title");
      this.commands(
         "scoreboard objectives remove bn_ui",
         "scoreboard objectives add bn_ui dummy \"§6BN §fsidebar\"",
         "scoreboard objectives setdisplay sidebar bn_ui ascending",
         "scoreboard players set \"§aFirst\" bn_ui 1",
         "scoreboard players set \"§bSecond\" bn_ui 2",
         "scoreboard players set \"§cThird\" bn_ui 3"
      );
      this.log("sidebar ascending", DevJsonUiHudTest::sidebar);
      this.screenshot("jsonui-hud-sidebar");
      this.commands("scoreboard objectives setdisplay sidebar bn_ui descending");
      this.log("sidebar descending", DevJsonUiHudTest::sidebar);
      this.commands("title @s actionbar §bBedrockNative action bar");
      sleep(300L);
      this.log("actionbar", hud -> {
         UiControl message = hud.find("actionbar_message");
         return "text='" + (message == null ? null : message.text()) + "'";
      });
      this.screenshot("jsonui-hud-actionbar");
      this.commands("title @s clear", "scoreboard objectives remove bn_ui");
      this.log(
         "cleared",
         hud -> "title=" + (hud.find("hud_title_text") != null) + " sidebarVisible=" + (hud.find("sidebar") != null && hud.find("sidebar").visible())
      );
      this.log("test pack level probe", hud -> "text='" + (hud.find("bn_jsonui_level") == null ? null : hud.find("bn_jsonui_level").text()) + "'");
      new DevHudPlayerChecks(this.minecraft, "Dev jsonui-hud", "jsonui-hud").run();
      this.log("test pack level probe after xp", hud -> "text='" + (hud.find("bn_jsonui_level") == null ? null : hud.find("bn_jsonui_level").text()) + "'");
      BedrockNative.LOGGER.info("Dev jsonui-hud: done");
   }

   private static String sidebar(UiScreenInstance hud) {
      UiControl players = hud.find("players");
      UiControl scores = hud.find("scores");
      UiControl sidebar = hud.find("sidebar");
      List<String> lines = players == null ? List.of() : players.generated().stream().filter(Objects::nonNull).map(UiControl::text).toList();
      List<String> numbers = scores == null ? List.of() : scores.generated().stream().filter(Objects::nonNull).map(UiControl::text).toList();
      UiControl title = hud.find("displayed_objective");
      return "visible="
         + (sidebar != null && sidebar.visible())
         + " title='"
         + (title == null ? null : title.text())
         + "' lines="
         + lines
         + " scores="
         + numbers;
   }

   private <T> T read(Function<UiScreenInstance, T> reader) {
      return CompletableFuture.<T>supplyAsync(() -> {
         UiScreenInstance hud = JsonUiHud.instance();
         return hud == null ? null : reader.apply(hud);
      }, this.minecraft::execute).join();
   }

   private void log(String label, Function<UiScreenInstance, String> reader) {
      BedrockNative.LOGGER.info("Dev jsonui-hud {}: {}", label, this.read(reader));
   }

   private void commands(String... commands) {
      for (String command : commands) {
         this.minecraft.execute(() -> this.minecraft.getConnection().sendCommand(command));
         sleep(400L);
      }

      sleep(800L);
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
