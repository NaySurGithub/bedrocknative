package net.bedrocknative.client.net;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.content.BedrockTextures;
import net.bedrocknative.client.text.BedrockGlyphs;
import net.bedrocknative.client.text.BedrockNameTag;
import net.bedrocknative.client.ui.JsonUiHud;
import net.bedrocknative.text.GlyphPages;
import net.bedrocknative.ui.BitmapFontMetrics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.commands.arguments.EntityAnchorArgument.Anchor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.Scoreboard;
import org.jspecify.annotations.Nullable;

final class DevTextTest {
   private static final int[] GLYPHS = new int[]{58624, 58625, 58626, 58627, 58628};
   private static final float[] ADVANCES = new float[]{13.0F, 15.0F, 5.0F, 15.0F, 15.0F};
   private static final String FLOATING = "§l§g\ue504 BedrockNative §r\ue500\n§mRedstone §nCopper §hQuartz\n§q\ue501 §sDiamond \ue502 §tLapis \ue503\n§7last line";
   private static final String STAND = "§bStand top\n§uAmethyst §vResin\n§jNetherite §iIron §pGold";
   private final Minecraft minecraft;
   private int passed;
   private int failed;

   DevTextTest(Minecraft minecraft) {
      this.minecraft = minecraft;
   }

   void run() {
      this.commands("scriptevent bedrocknative:text clear");
      this.glyphs();
      this.commands("scriptevent bedrocknative:text spawn");
      sleep(2000L);
      this.tags();
      this.distances();
      this.commands("scriptevent bedrocknative:text chat");
      this.run(() -> this.minecraft.gui.setScreen(new ChatScreen("", false)));
      sleep(1000L);
      this.screenshot("text-chat");
      this.run(() -> this.minecraft.gui.setScreen(null));
      this.commands("scriptevent bedrocknative:text sidebar", "scriptevent bedrocknative:text title");
      this.sidebar();
      sleep(1000L);
      this.screenshot("text-hud");
      this.commands("scriptevent bedrocknative:text clear");
      log("done, {} passed, {} failed", this.passed, this.failed);
   }

   private void glyphs() {
      List<String> pages = this.onClient(() -> BedrockGlyphs.pages().stream().sorted().map(page -> String.format("%02X", page)).toList());
      log("glyph pages in Java's font: {}", pages);
      this.check(pages.contains("E5"), "the test pack's glyph_E5 page is in use");
      this.check(pages.contains("E0") || pages.contains("E1"), "the base game's own private-use pages are in use");
      BitmapFontMetrics jsonUi = new BitmapFontMetrics(index -> BedrockTextures.pixels(GlyphPages.path(index)).orElse(null));
      float missing = this.onClient(() -> this.minecraft.font.getSplitter().stringWidth(Character.toString(59808)));

      for (int i = 0; i < GLYPHS.length; i++) {
         int codePoint = GLYPHS[i];
         float expected = ADVANCES[i];
         float java = this.onClient(() -> this.minecraft.font.getSplitter().stringWidth(Character.toString(codePoint)));
         float provider = this.onClient(() -> BedrockGlyphs.advance(codePoint));
         float ui = this.onClient(() -> jsonUi.advance(codePoint, false));
         this.check(
            Math.abs(java - expected) < 0.01F && Math.abs(provider - expected) < 0.01F && Math.abs(ui - expected) < 0.01F,
            String.format(
               Locale.ROOT,
               "U+%04X width: Java font %.2f, glyph provider %.2f, JSON UI %.2f, Bedrock %.2f (missing glyph %.2f)",
               codePoint,
               java,
               provider,
               ui,
               expected,
               missing
            )
         );
      }

      float blank = this.onClient(() -> this.minecraft.font.getSplitter().stringWidth(Character.toString(58864)));
      this.check(Math.abs(blank - 4.0F) < 0.01F, "an empty cell of the page draws blank, width " + blank);
      float lineBreak = this.onClient(() -> this.minecraft.font.getSplitter().stringWidth("\n"));
      this.check(Math.abs(lineBreak - 4.0F) < 0.01F, "a line break draws blank, width " + lineBreak);
   }

   private void tags() {
      log("name tags in range: {}", this.onClient(() -> describeTags(this.minecraft, 16.0)));
      Entity floating = this.onClient(
         () -> this.tagged("§l§g\ue504 BedrockNative §r\ue500\n§mRedstone §nCopper §hQuartz\n§q\ue501 §sDiamond \ue502 §tLapis \ue503\n§7last line")
      );
      Entity stand = this.onClient(() -> this.tagged("§bStand top\n§uAmethyst §vResin\n§jNetherite §iIron §pGold"));
      this.check(floating != null, "the floating text arrived with its multi-line name");
      this.check(stand != null, "the armor stand arrived with its multi-line name");
      if (floating != null) {
         BedrockNameTag tag = BedrockNameTag.of(floating);
         List<String> lines = this.onClient(() -> tag.lines().stream().<String>map(Component::getString).toList());
         this.check(
            lines.equals(List.of("\ue504 BedrockNative \ue500", "Redstone Copper Quartz", "\ue501 Diamond \ue502 Lapis \ue503", "last line")),
            "floating text split into 4 lines: " + lines
         );
         this.check(this.onClient(() -> tag.alwaysShown(floating) && tag.shows(floating, false)), "floating text always shown");
         this.check(this.onClient(() -> tag.textOnly(floating)), "floating text drawn as text only (SCALE " + this.onClient(tag::scale) + ")");
         this.colour(tag, 0, "BedrockNative", 14538245, true);
         this.colour(tag, 1, "Redstone", 9901575, false);
         this.colour(tag, 1, "Copper", 11823181, false);
         this.colour(tag, 1, "Quartz", 14931153, false);
         this.colour(tag, 2, "Diamond", 2931368, false);
         this.colour(tag, 2, "Lapis", 2181499, false);
         this.colour(tag, 3, "last line", 13027014, false);
         this.look(floating, this.onClient(() -> floating.getY() + tag.boxHeight(floating) + 0.6));
         this.screenshot("text-floating");
      }

      if (stand != null) {
         BedrockNameTag tag = BedrockNameTag.of(stand);
         List<String> lines = this.onClient(() -> tag.lines().stream().<String>map(Component::getString).toList());
         this.check(lines.equals(List.of("Stand top", "Amethyst Resin", "Netherite Iron Gold")), "armor stand split into 3 lines: " + lines);
         this.check(!this.onClient(() -> tag.textOnly(stand)), "armor stand keeps its model");
         this.colour(tag, 1, "Amethyst", 10116294, false);
         this.colour(tag, 1, "Resin", 15429908, false);
         this.colour(tag, 2, "Netherite", 4471355, false);
         this.colour(tag, 2, "Iron", 13552330, false);
         this.colour(tag, 2, "Gold", 14594349, false);
         this.look(stand, stand.getY() + stand.getBbHeight() * 0.6);
         sleep(500L);
         this.check(this.onClient(() -> this.minecraft.crosshairPickEntity == stand), "looking at the armor stand (its tag shows on look)");
         this.screenshot("text-stand");
      }
   }

   private void distances() {
      Entity floating = this.onClient(
         () -> this.tagged("§l§g\ue504 BedrockNative §r\ue500\n§mRedstone §nCopper §hQuartz\n§q\ue501 §sDiamond \ue502 §tLapis \ue503\n§7last line")
      );
      if (floating != null) {
         BedrockNameTag tag = BedrockNameTag.of(floating);
         Vec3 text = this.onClient(floating::position);
         Vec3 player = this.onClient(() -> this.minecraft.player.position());
         String mode = this.onClient(() -> this.minecraft.gameMode.getPlayerMode().getName());
         double centreY = this.onClient(() -> floating.getY() + tag.boxHeight(floating) + 0.6);
         double baseAngle = Math.hypot(player.x - text.x, player.z - text.z) < 0.001 ? 0.0 : Math.atan2(player.z - text.z, player.x - text.x);
         int[] distances = new int[]{3, 10, 30};
         double angle = baseAngle;
         boolean clear = false;

         for (int step = 0; step < 24 && !clear; step++) {
            double candidate = baseAngle + step * Math.PI / 12.0;
            clear = this.onClient(() -> this.clearView(text, centreY, candidate, distances));
            angle = candidate;
         }

         if (!clear) {
            log("no side of the floating text has a clear view at 3, 10 and 30 blocks");
         }

         this.commands("gamemode spectator");

         for (int distance : distances) {
            String command = String.format(
               Locale.ROOT,
               "tp @s %.2f %.2f %.2f facing %.2f %.2f %.2f",
               text.x + Math.cos(angle) * distance,
               eyeY(centreY, distance) - 1.62,
               text.z + Math.sin(angle) * distance,
               text.x,
               centreY,
               text.z
            );
            this.commands(command);
            sleep(2500L);
            this.look(floating, centreY);
            double actual = this.onClient(() -> Math.hypot(this.minecraft.player.getX() - text.x, this.minecraft.player.getZ() - text.z));
            log(
               "tag seen from {} blocks (player {} blocks away horizontally, eye {} above the text centre), world line height {} blocks, view clear {}",
               distance,
               String.format(Locale.ROOT, "%.1f", actual),
               String.format(Locale.ROOT, "%.1f", eyeY(centreY, distance) - centreY),
               String.format(Locale.ROOT, "%.4f", 0.2828F),
               clear
            );
            this.screenshot("text-tag-" + distance);
         }

         this.commands("gamemode " + mode, String.format(Locale.ROOT, "tp @s %.2f %.2f %.2f", player.x, player.y, player.z));
         sleep(1500L);
      }
   }

   private static double eyeY(double centreY, int distance) {
      return centreY + distance * 0.5;
   }

   private boolean clearView(Vec3 text, double centreY, double angle, int[] distances) {
      for (int distance : distances) {
         Vec3 eye = new Vec3(text.x + Math.cos(angle) * distance, eyeY(centreY, distance), text.z + Math.sin(angle) * distance);
         HitResult hit = this.minecraft.level.clip(new ClipContext(eye, new Vec3(text.x, centreY, text.z), Block.VISUAL, Fluid.NONE, CollisionContext.empty()));
         if (hit.getType() != Type.MISS) {
            return false;
         }
      }

      return true;
   }

   private void sidebar() {
      String state = this.onClient(
         () -> {
            Scoreboard scoreboard = this.minecraft.level.getScoreboard();
            Objective objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
            return objective == null
               ? null
               : objective.getDisplayName().getString()
                  + " "
                  + scoreboard.listPlayerScores(objective).stream().map(PlayerScoreEntry::ownerName).map(Component::getString).toList();
         }
      );
      log("sidebar: {} (JSON UI HUD draws it: {})", state, this.onClient(JsonUiHud::owns));
      this.check(state != null && state.contains("\ue504") && state.contains("\ue500 Red square"), "sidebar keeps the glyphs: " + state);
   }

   static List<String> describeTags(Minecraft minecraft, double range) {
      List<String> tags = new ArrayList<>();

      for (Entity entity : minecraft.level.entitiesForRendering()) {
         BedrockNameTag tag = BedrockNameTag.of(entity);
         if (tag != null && tag.joined() != null && !(entity.distanceTo(minecraft.player) > range)) {
            tags.add(
               String.format(
                  Locale.ROOT,
                  "%s#%d at %.1f blocks: %s, always %s, text only %s, scale %.2f, tag centre %.2f above it",
                  entity.typeHolder().getRegisteredName(),
                  entity.getId(),
                  entity.distanceTo(minecraft.player),
                  tag.lines().stream().map(Component::getString).toList(),
                  tag.alwaysShown(entity),
                  tag.textOnly(entity),
                  tag.scale(),
                  tag.boxHeight(entity) + 0.6
               )
            );
         }
      }

      return tags;
   }

   private void colour(BedrockNameTag tag, int line, String text, int rgb, boolean bold) {
      Style style = this.onClient(() -> {
         Style[] found = new Style[]{null};
         tag.lines().get(line).visit((runStyle, part) -> {
            if (part.contains(text)) {
               found[0] = runStyle;
               return Optional.of(Boolean.TRUE);
            } else {
               return Optional.empty();
            }
         }, Style.EMPTY);
         return found[0];
      });
      int actual = style != null && style.getColor() != null ? style.getColor().getValue() : -1;
      boolean ok = style != null && actual == rgb && style.isBold() == bold && !style.isStrikethrough() && !style.isUnderlined();
      this.check(
         ok,
         String.format(
            Locale.ROOT,
            "line %d '%s' colour %06X (want %06X), bold %s, strike %s, underline %s",
            line + 1,
            text,
            actual,
            rgb,
            style != null && style.isBold(),
            style != null && style.isStrikethrough(),
            style != null && style.isUnderlined()
         )
      );
   }

   @Nullable
   private Entity tagged(String name) {
      for (Entity entity : this.minecraft.level.entitiesForRendering()) {
         BedrockNameTag tag = BedrockNameTag.of(entity);
         if (tag != null && tag.name().equals(name)) {
            return entity;
         }
      }

      return null;
   }

   private void look(Entity entity, double y) {
      this.run(() -> this.minecraft.player.lookAt(Anchor.EYES, new Vec3(entity.getX(), y, entity.getZ())));
      sleep(1000L);
   }

   private void check(boolean ok, String what) {
      if (ok) {
         this.passed++;
      } else {
         this.failed++;
      }

      log("{} {}", ok ? "PASS" : "FAIL", what);
   }

   private void commands(String... commands) {
      for (String command : commands) {
         this.run(() -> this.minecraft.getConnection().sendCommand(command));
         sleep(500L);
      }

      sleep(800L);
   }

   private void screenshot(String name) {
      this.run(() -> Screenshot.grab(this.minecraft.gameDirectory, name + ".png", this.minecraft.gameRenderer.mainRenderTarget(), 1, message -> {}));
      sleep(500L);
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
      BedrockNative.LOGGER.info("Dev text test: " + message, args);
   }

   private static void sleep(long millis) {
      try {
         Thread.sleep(millis);
      } catch (InterruptedException var3) {
         Thread.currentThread().interrupt();
      }
   }
}
