package net.bedrocknative.client.net;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.form.JsonUiFormScreen;
import net.bedrocknative.client.inventory.JsonUiContainerScreen;
import net.bedrocknative.client.ui.JsonUi;
import net.bedrocknative.ui.UiActorRenderers;
import net.bedrocknative.ui.UiControl;
import net.bedrocknative.ui.UiValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.commands.arguments.EntityAnchorArgument.Anchor;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.EntityHitResult;
import org.jspecify.annotations.Nullable;

final class DevServerUiTest {
   private static final String CHEST_UI_NAME = "§b§n§c§u§i§rBN Chest UI";
   private static final List<String> PICTURES = List.of(
      "textures/blocks/diamond_block",
      "textures/blocks/gold_block",
      "textures/blocks/emerald_block",
      "textures/items/apple",
      "textures/items/diamond_sword",
      "textures/ui/WarningGlyph"
   );
   private final Minecraft minecraft;

   DevServerUiTest(Minecraft minecraft) {
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

      this.commands("gamemode creative", "tp @s -41.5 102 169.5 0 0");
      sleep(3000L);
      this.commands("gamemode survival", "clear @s", "effect @s clear", "effect @s instant_health 1 10 true", "scriptevent bedrocknative:serverui clear");
      this.chestUi();
      this.actorCard();
      this.commands("scriptevent bedrocknative:serverui clear");
      BedrockNative.LOGGER.info("Dev serverui test: done");
   }

   private void chestUi() {
      this.command("scriptevent bedrocknative:serverui chestui");
      Entity minecart = null;

      for (int i = 0; i < 30 && minecart == null; i++) {
         sleep(100L);
         minecart = this.onClient(() -> this.nearest(EntityTypes.CHEST_MINECART));
      }

      if (check("a chest minecart came", minecart != null)) {
         Entity target = minecart;
         sleep(1000L);
         this.minecraft
            .execute(
               () -> {
                  this.minecraft.player.lookAt(Anchor.EYES, target.getBoundingBox().getCenter());
                  this.minecraft
                     .gameMode
                     .interact(this.minecraft.player, target, new EntityHitResult(target, target.getBoundingBox().getCenter()), InteractionHand.MAIN_HAND);
               }
            );
         JsonUiContainerScreen screen = null;

         for (int i = 0; i < 40 && screen == null; i++) {
            sleep(100L);
            screen = this.onClient(() -> this.minecraft.gui.screen() instanceof JsonUiContainerScreen json ? json : null);
         }

         if (!check(
            "the minecart's container opens as Bedrock's chest screen (" + this.screenName() + ")",
            screen != null && "chest.small_chest_screen".equals(screen.screen())
         )) {
            this.closeScreen();
         } else {
            JsonUiContainerScreen chest = screen;
            sleep(1000L);
            UiValue title = this.onClient(() -> chest.ui().instance().root().scope().get("$container_title"));
            check(
               "titled with the minecart's name (" + (title == null ? "unset" : title.stringOrNull()) + ")",
               title != null && "§b§n§c§u§i§rBN Chest UI".equals(title.stringOrNull())
            );
            check("the pack's card shows (routed by the actor's name)", this.onClient(() -> visible(chest.ui().instance().find("bn_chest_ui"))));
            List<String> shown = this.onClient(() -> {
               UiControl grid = chest.ui().instance().find("bn_picture_grid");
               List<String> textures = new ArrayList<>();

               for (int i = 0; grid != null && i < PICTURES.size(); i++) {
                  UiControl cell = grid.gridItem(i);
                  textures.add(cell == null ? "(no cell)" : cell.texture());
               }

               return textures;
            });
            check("the six pictures are the items' names " + shown, PICTURES.equals(shown));
            String firstName = this.onClient(() -> {
               UiControl label = chest.ui().instance().find("bn_first_name");
               return label == null ? "(no label)" : label.text();
            });
            check("the first item's name reads '" + firstName + "'", PICTURES.getFirst().equals(firstName));
            check(
               "the italic probe says no on this client",
               this.onClient(() -> visible(chest.ui().instance().find("bn_italic_no")) && !visible(chest.ui().instance().find("bn_italic_yes")))
            );
            Entity owner = this.onClient(() -> actorOf(chest.ui().instance().find("bn_owner")));
            check("#entity_id names the minecart (" + describe(owner) + ")", owner == target);
            Set<String> undrawn = this.onClient(() -> chest.ui().instance().gaps().undrawnRenderers());
            check("the gradient is drawn (renderers drawn as nothing: " + undrawn + ")", !undrawn.contains("gradient_renderer"));
            this.screenshot("serverui-chestui");
            this.closeScreen();
         }
      }
   }

   private void actorCard() {
      this.command("scriptevent bedrocknative:serverui actor");
      JsonUiFormScreen form = null;

      for (int i = 0; i < 60 && form == null; i++) {
         sleep(100L);
         form = this.onClient(() -> this.minecraft.gui.screen() instanceof JsonUiFormScreen json ? json : null);
      }

      if (!check("the actor form opens as a JSON UI form (" + this.screenName() + ")", form != null)) {
         this.closeScreen();
      } else {
         JsonUiFormScreen card = form;
         sleep(1500L);
         check("the pack's actor card shows (routed by its title)", this.onClient(() -> visible(card.ui().instance().find("bn_actor_layout"))));
         UiValue id = this.onClient(() -> {
            UiControl doll = card.ui().instance().find("bn_actor_doll");
            return doll == null ? null : doll.bag("#entity_id");
         });
         Entity actor = this.onClient(() -> actorOf(card.ui().instance().find("bn_actor_doll")));
         check("#entity_id " + (id == null ? "unset" : id.stringOrNull()) + " names the armor stand (" + describe(actor) + ")", actor instanceof ArmorStand);
         Set<String> undrawn = this.onClient(() -> card.ui().instance().gaps().undrawnRenderers());
         check(
            "the actor and the gradient are drawn (renderers drawn as nothing: " + undrawn + ")",
            !undrawn.contains("live_horse_renderer") && !undrawn.contains("gradient_renderer")
         );
         this.screenshot("serverui-actor");
         this.minecraft.execute(() -> {
            if (this.minecraft.gui.screen() != null) {
               this.minecraft.gui.screen().keyPressed(new KeyEvent(41, 0, 0));
            }
         });
         sleep(800L);
         check("Esc closes the actor card (now " + this.screenName() + ")", this.onClient(() -> this.minecraft.gui.screen() == null));
         this.closeScreen();
      }
   }

   @Nullable
   private Entity nearest(EntityType<?> type) {
      Entity found = null;
      double best = 36.0;

      for (Entity entity : this.minecraft.level.entitiesForRendering()) {
         double distance = entity.distanceToSqr(this.minecraft.player);
         if (entity.getType() == type && distance < best) {
            best = distance;
            found = entity;
         }
      }

      return found;
   }

   @Nullable
   private static Entity actorOf(@Nullable UiControl renderer) {
      Long id = renderer == null ? null : UiActorRenderers.entityId(renderer.bag("#entity_id"));
      return id == null ? null : BedrockExtras.actor(id);
   }

   private static boolean visible(@Nullable UiControl control) {
      return control != null && control.visible();
   }

   private static String describe(@Nullable Entity entity) {
      return entity == null ? "none" : EntityType.getKey(entity.getType()) + " " + entity.getId();
   }

   private static boolean check(String what, boolean pass) {
      BedrockNative.LOGGER.info("Dev serverui test: {}: {}", what, pass ? "PASS" : "FAIL");
      return pass;
   }

   private String screenName() {
      return this.onClient(() -> {
         Screen screen = this.minecraft.gui.screen();
         return screen == null ? "nothing" : screen.getClass().getSimpleName();
      });
   }

   private void closeScreen() {
      this.minecraft.execute(() -> {
         if (this.minecraft.gui.screen() != null) {
            this.minecraft.gui.setScreen(null);
         }
      });
      sleep(500L);
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
