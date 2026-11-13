package net.bedrocknative.client.net;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.ui.JsonUiHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.Scoreboard;

final class DevHudTest {
   private final Minecraft minecraft;

   DevHudTest(Minecraft minecraft) {
      this.minecraft = minecraft;
   }

   void run() {
      this.commands(
         "scoreboard objectives remove bn_hud",
         "scoreboard objectives remove bn_list",
         "scoreboard objectives add bn_hud dummy \"§6BN §fsidebar\"",
         "scoreboard objectives setdisplay sidebar bn_hud ascending",
         "scoreboard players set \"§aFirst\" bn_hud 1",
         "scoreboard players set \"§bSecond\" bn_hud 2",
         "scoreboard players set \"§cThird\" bn_hud 3",
         "scoreboard players set \"Gone\" bn_hud 4"
      );
      this.commands("scoreboard players reset \"Gone\" bn_hud");
      this.report("ascending");
      this.screenshot("hud-sidebar-ascending");
      this.commands("scoreboard objectives setdisplay sidebar bn_hud descending");
      this.report("descending");
      this.commands("scoreboard objectives add bn_list dummy List", "scoreboard objectives setdisplay list bn_list", "scoreboard players set @s bn_list 42");
      this.report("list");
      this.commands(
         "title @s times 5 100 5",
         "title @s subtitle §7BedrockNative subtitle",
         "title @s title §eBedrockNative title",
         "title @s actionbar §bBedrockNative action bar"
      );
      sleep(1000L);
      this.screenshot("hud-title");
      this.commands("title @s clear", "scoreboard objectives remove bn_hud", "scoreboard objectives remove bn_list");
      this.report("removed");
      new DevHudPlayerChecks(this.minecraft, "Dev hud test", JsonUiHud.owns() ? "hud-jsonui" : "hud").run();
      BedrockNative.LOGGER.info("Dev hud test: done");
   }

   private void report(String label) {
      String state = CompletableFuture.<String>supplyAsync(
            () -> {
               Scoreboard scoreboard = this.minecraft.level.getScoreboard();
               StringBuilder text = new StringBuilder();

               for (DisplaySlot slot : List.of(DisplaySlot.SIDEBAR, DisplaySlot.LIST)) {
                  Objective objective = scoreboard.getDisplayObjective(slot);
                  text.append(slot.getSerializedName()).append('=');
                  if (objective == null) {
                     text.append("none ");
                  } else {
                     text.append('\'')
                        .append(objective.getDisplayName().getString())
                        .append("' ")
                        .append(
                           scoreboard.listPlayerScores(objective)
                              .stream()
                              .sorted(
                                 Comparator.comparingInt(PlayerScoreEntry::value)
                                    .reversed()
                                    .thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER)
                              )
                              .map(entry -> entry.ownerName().getString() + ":" + entry.formatValue(StyledFormat.SIDEBAR_DEFAULT).getString())
                              .toList()
                        )
                        .append(' ');
                  }
               }

               return text.toString();
            },
            this.minecraft::execute
         )
         .join();
      BedrockNative.LOGGER.info("Dev hud test {}: {}", label, state);
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
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
      }
   }
}
