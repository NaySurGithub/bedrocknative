package net.bedrocknative.client.net;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.command.BedrockCommands;
import net.bedrocknative.client.command.SuggestionsView;
import net.bedrocknative.client.mixin.command.ChatScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import org.cloudburstmc.protocol.bedrock.packet.SetCommandsEnabledPacket;

final class DevCommandsTest {
   private static final KeyEvent TAB = new KeyEvent(43, 0, 0);
   private static final KeyEvent ENTER = new KeyEvent(40, 0, 0);
   private static final long STEP = 350L;
   private final Minecraft minecraft;

   DevCommandsTest(Minecraft minecraft) {
      this.minecraft = minecraft;
   }

   void run() {
      if ("commands-scroll".equals(System.getProperty("bedrocknative.devTest"))) {
         new DevChatScrollTest(this.minecraft).run();
      } else {
         this.command("gamemode creative");

         for (String help : List.of(
            "help time", "help give", "help xp", "help execute", "help tp", "help gamemode", "help clear", "help scoreboard", "help paint"
         )) {
            this.command(help);
         }

         sleep(1000L);
         this.run(
            () -> {
               try {
                  log(
                     "root usage: {} entries",
                     this.minecraft
                        .getConnection()
                        .getCommands()
                        .getSmartUsage(this.minecraft.getConnection().getCommands().getRoot(), this.minecraft.getConnection().getSuggestionsProvider())
                        .size()
                  );
               } catch (Throwable e) {
                  BedrockNative.LOGGER.warn("Dev commands test: root usage failed", e);
               }
            }
         );
         this.step("command prefix", "/gi", true);
         this.step("give target", "/give ", false);
         this.step("give item", "/give @s dia", false);
         this.screenshot("give-item");
         this.tab();
         this.step("gamemode", "/gamemode ", false);
         this.step("gamemode enum", "/gamemode cr", true);
         this.step("tp", "/tp ", false);
         this.screenshot("tp");
         this.step("tp partial", "/tp ~ ", true);
         this.step("tp usage", "/tp 1 2 3 ", false);
         this.step("effect", "/effect @s ", false);
         this.step("effect name", "/effect @s spe", true);
         this.step("time set", "/time set ", false);
         this.step("time set enum", "/time set no", true);
         this.step("scoreboard", "/scoreboard ", false);
         this.step("scoreboard players", "/scoreboard players ", false);
         this.step("execute run", "/execute as @s at @s run gi", true);
         this.step("execute chain", "/execute as @s ", false);
         this.step("selector argument", "/testfor @e[ty", true);
         this.step("selector type", "/testfor @e[type=pi", false);
         this.screenshot("selector");
         this.tab();
         this.step("selector name", "/testfor @a[name=Lx", true);
         this.step("xp", "/xp ", false);
         this.step("ride modes", "/ride @s ", false);
         this.step("ride evict", "/ride @s ev", true);
         this.step("xp postfix", "/xp 10L ", false);
         this.step("tell digit name", "/tell 2Fast ", false);
         this.parse("ride summon", "ride @s su");
         this.parse("xp postfix", "xp 10L @s");
         this.parse("tell digit name", "tell 2Fast hi");
         this.parse("tell dot name", "tell .Steve hi");
         this.parse("give digit name", "give 2Fast dia");
         this.parse("tp coordinates", "tp 1 2 3");
         this.parse("tp relative", "tp ~ ~1 ~");
         this.parse("tp victim", "tp @s ");
         this.step("alias", "/tele", true);
         this.step("custom", "/paint ", false);
         this.screenshot("custom");
         this.step("custom enum", "/bn:paint li", true);
         this.step("custom target", "/bn:paint red ", false);
         this.step("custom usage", "/bn:paint red @s ", false);
         this.screenshot("usage");
         this.step("unknown command", "/notacommand 1 2", false);
         this.step("bad subcommand", "/execute nope @s run say x", false);
         this.step("bad chained", "/execute as @s nope", false);
         this.step("mention", "@", false);
         this.screenshot("mention");
         this.step("mention name", "hello @Lx", true);
         this.step("mention all", "hi @", true);
         this.step("say mention", "/say hi @Lx", true);
         this.closeChat();
         this.command("scoreboard objectives add bnobj dummy");
         this.command("tag @s add bntag");
         sleep(1500L);
         this.step("soft enum objective", "/scoreboard objectives remove ", false);
         this.step("soft enum tag", "/tag @s remove ", false);
         this.screenshot("soft-enum");
         this.step("send custom", "/bn:paint li", true);
         this.run(() -> this.screen().insertText(" @s 3 ~ ~1 ~", false));
         this.enter("send custom");
         this.step("send selector", "/testfor @a[name=Lx", true);
         this.run(() -> this.screen().insertText("]", false));
         this.enter("send selector");
         this.step("send mention", "BedrockNative mention test @Lx", true);
         this.enter("send mention");
         sleep(1000L);
         this.command("scoreboard objectives remove bnobj");
         this.command("tag @s remove bntag");
         sleep(1500L);
         this.step("soft enum removed", "/tag @s remove ", false);
         this.closeChat();
         this.commandsEnabled(false);
         this.parse("gamerule cheats off", "gamerule ");
         this.parse("cheat rule cheats off", "gamerule keepinventory true");
         this.parse("plain rule cheats off", "gamerule pvp true");
         this.commandsEnabled(true);
         this.parse("gamerule cheats on", "gamerule k");
         this.parse("cheat rule cheats on", "gamerule keepinventory true");
         log("done");
      }
   }

   private void step(String label, String text, boolean tab) {
      if (!this.onClient(() -> this.minecraft.gui.screen() instanceof ChatScreen)) {
         this.run(() -> this.minecraft.gui.setScreen(new ChatScreen("", false)));
         sleep(350L);
      }

      this.run(() -> this.screen().insertText(text, true));
      sleep(350L);
      this.report(label + " '" + text + "'");
      if (tab) {
         this.tab();
      }
   }

   private void parse(String label, String text) {
      this.run(
         () -> {
            CommandDispatcher<ClientSuggestionProvider> dispatcher = this.minecraft.getConnection().getCommands();
            ParseResults<ClientSuggestionProvider> parse = dispatcher.parse(text, this.minecraft.getConnection().getSuggestionsProvider());
            String error = parse.getExceptions()
               .values()
               .stream()
               .map(Throwable::getMessage)
               .findFirst()
               .orElse(parse.getReader().canRead() ? "unread '" + parse.getReader().getRemaining() + "'" : "none");
            List<String> completions = ((Suggestions)dispatcher.getCompletionSuggestions(parse).join())
               .getList()
               .stream()
               .<String>map(Suggestion::getText)
               .toList();
            log("parse {} '{}': error {}, {} completions {}", label, text, error, completions.size(), completions.subList(0, Math.min(14, completions.size())));
         }
      );
   }

   private void commandsEnabled(boolean enabled) {
      this.run(() -> {
         SetCommandsEnabledPacket packet = new SetCommandsEnabledPacket();
         packet.setCommandsEnabled(enabled);
         BedrockCommands.active().apply(packet);
      });
   }

   private void tab() {
      this.run(() -> this.screen().keyPressed(TAB));
      sleep(350L);
      this.run(() -> log("  Tab -> '{}'", this.input().getValue()));
   }

   private void enter(String label) {
      this.run(() -> {
         log("{}: Enter on '{}'", label, this.input().getValue());
         this.screen().keyPressed(ENTER);
      });
      sleep(800L);
   }

   private void report(String label) {
      this.run(
         () -> {
            SuggestionsView view = (SuggestionsView)((ChatScreenAccessor)this.screen()).bedrocknative$commandSuggestions();
            List<String> suggestions = view.bedrocknative$suggestions();
            log(
               "{}: list shown {}, {} suggestions {}, usage {}",
               label,
               view.bedrocknative$listShown(),
               suggestions.size(),
               suggestions.subList(0, Math.min(14, suggestions.size())),
               view.bedrocknative$usage()
            );
         }
      );
   }

   private ChatScreen screen() {
      return (ChatScreen)this.minecraft.gui.screen();
   }

   private EditBox input() {
      return (EditBox)this.screen().getFocused();
   }

   private void closeChat() {
      this.run(() -> this.minecraft.gui.setScreen(null));
      sleep(350L);
   }

   private void command(String command) {
      this.run(() -> this.minecraft.getConnection().sendCommand(command));
      sleep(400L);
   }

   private void screenshot(String name) {
      sleep(300L);
      this.run(
         () -> Screenshot.grab(this.minecraft.gameDirectory, "commands-" + name + ".png", this.minecraft.gameRenderer.mainRenderTarget(), 1, message -> {})
      );
      sleep(300L);
   }

   private <T> T onClient(Supplier<T> task) {
      return CompletableFuture.supplyAsync(task, this.minecraft::execute).join();
   }

   private void run(Runnable task) {
      this.onClient(() -> {
         task.run();
         return null;
      });
   }

   private static void log(String message, Object... args) {
      BedrockNative.LOGGER.info("Dev commands test: " + message, args);
   }

   private static void sleep(long millis) {
      try {
         Thread.sleep(millis);
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
      }
   }
}
