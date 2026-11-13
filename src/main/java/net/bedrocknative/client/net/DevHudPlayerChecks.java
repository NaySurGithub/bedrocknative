package net.bedrocknative.client.net;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.hud.BedrockHud;
import net.bedrocknative.client.ui.JsonUi;
import net.bedrocknative.client.ui.JsonUiHud;
import net.bedrocknative.client.world.BedrockLocalPlayer;
import net.bedrocknative.ui.HudRenderers;
import net.bedrocknative.ui.HudValues;
import net.bedrocknative.ui.UiControl;
import net.bedrocknative.ui.UiDrawCommand;
import net.bedrocknative.ui.UiScreenInstance;
import net.bedrocknative.ui.UiTextures;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.arguments.EntityAnchorArgument.Anchor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import org.cloudburstmc.protocol.bedrock.data.HudElement;

final class DevHudPlayerChecks {
   private final Minecraft minecraft;
   private final String prefix;
   private final String screenshots;
   private int passed;
   private int failed;

   DevHudPlayerChecks(Minecraft minecraft, String prefix, String screenshots) {
      this.minecraft = minecraft;
      this.prefix = prefix;
      this.screenshots = screenshots;
   }

   void run() {
      boolean jsonUi = this.read(JsonUiHud::owns);
      this.log("mode", (jsonUi ? "JSON UI owns the HUD" : "Java's HUD") + ", JSON UI mode " + JsonUi.mode());
      if (this.read(() -> this.minecraft.player.isDeadOrDying())) {
         this.minecraft.execute(() -> this.minecraft.player.respawn());
         sleep(4000L);
      }

      this.commands("gamemode creative", "tp @s -41.5 102 169.5", "kill @e[type=xp_orb]");
      sleep(3000L);
      this.dryMeadow();
      this.commands(
         "kill @e[type=xp_orb]",
         "gamemode survival",
         "effect @s clear",
         "effect @s instant_health 1 10 true",
         "effect @s saturation 1 10 true",
         "clear @s",
         "xp -10000L @s",
         "xp 7L @s",
         "replaceitem entity @s slot.hotbar 0 diamond_sword",
         "replaceitem entity @s slot.hotbar 2 apple 7",
         "replaceitem entity @s slot.hotbar 3 stone 64",
         "replaceitem entity @s slot.armor.chest 0 iron_chestplate",
         "effect @s speed 60 1",
         "effect @s weakness 60 0",
         "effect @s hunger 4 200",
         "damage @s 5"
      );
      sleep(4500L);
      this.minecraft.execute(() -> KeyMapping.click(InputConstants.getKey(this.minecraft.options.keyHotbarSlots[0].saveString())));
      sleep(600L);
      this.minecraft.execute(() -> KeyMapping.click(InputConstants.getKey(this.minecraft.options.keyHotbarSlots[2].saveString())));
      sleep(700L);
      this.check("held item name", this.read(() -> this.itemName(jsonUi)));
      sleep(300L);
      this.commands("testfor @s[hasitem={item=apple,location=slot.weapon.mainhand}]");
      String found = this.read(JsonUiHud::lastChat);
      this.check(
         "number key 3 selects slot 3 and the server holds the apple",
         this.read(() -> this.minecraft.player.getInventory().getSelectedSlot()) == 2 && !found.isEmpty() && !found.contains("No targets"),
         "selected " + this.read(() -> this.minecraft.player.getInventory().getSelectedSlot()) + ", server: '" + found + "'"
      );
      sleep(500L);
      this.check("hotbar", this.read(() -> this.hotbar(jsonUi)));
      this.check("health", this.read(() -> this.health(jsonUi)));
      this.check("food", this.read(() -> this.food(jsonUi)));
      this.check("armour", this.read(() -> this.armor(jsonUi)));
      this.check("experience", this.read(() -> this.experience(jsonUi)));
      this.check("effects", this.read(() -> this.effects(jsonUi)));
      this.commands("say hello hud");
      this.check("chat", this.read(() -> this.chat(jsonUi)));
      this.screenshot(this.screenshots + "-player");
      this.commands("effect @s clear", "effect @s instant_health 1 10 true", "effect @s saturation 1 10 true");
      int[] feet = this.read(() -> new int[]{this.minecraft.player.getBlockX(), this.minecraft.player.getBlockY(), this.minecraft.player.getBlockZ()});
      String pool = feet[0] - 3 + " " + feet[1] + " " + (feet[2] - 3) + " " + (feet[0] + 3) + " " + (feet[1] + 2) + " " + (feet[2] + 3);
      this.commands("fill " + pool + " water replace air");
      sleep(6000L);
      this.check("air", this.read(() -> this.air(jsonUi)));
      this.screenshot(this.screenshots + "-water");
      this.commands("fill " + pool + " air replace water");
      this.dryMeadow();
      this.commands("summon pig ~2 ~ ~ 0 0 minecraft:on_saddled");
      this.minecraft
         .execute(
            () -> this.minecraft
               .level
               .getEntitiesOfClass(Entity.class, this.minecraft.player.getBoundingBox().inflate(8.0), entity -> entity.getType() == EntityTypes.PIG)
               .stream()
               .findFirst()
               .ifPresent(
                  pig -> {
                     this.minecraft.player.lookAt(Anchor.EYES, pig.position());
                     this.minecraft
                        .gameMode
                        .interact(this.minecraft.player, pig, new EntityHitResult(pig, pig.position().add(0.0, 0.3, 0.0)), InteractionHand.MAIN_HAND);
                  }
               )
         );
      sleep(1500L);
      this.check("mount hearts", this.read(() -> this.mount(jsonUi)));
      this.screenshot(this.screenshots + "-mount");
      this.commands("ride @s stop_riding", "kill @e[type=pig]");
      this.commands("scriptevent bedrocknative:hud hide hotbar");
      sleep(500L);
      this.check("SetHud hides the hotbar", this.read(() -> this.hiddenHotbar(jsonUi)));
      this.commands("scriptevent bedrocknative:hud reset");
      this.commands("effect @s clear", "clear @s", "effect @s instant_health 1 10 true", "effect @s saturation 1 10 true");
      this.commands("kill @e[type=xp_orb]", "gamemode creative", "tp @s -41.5 102 169.5");
      sleep(3000L);
      this.dryMeadow();
      this.commands("gamemode survival");
      this.log("player checks done", this.passed + " passed, " + this.failed + " failed");
   }

   private LocalPlayer player() {
      return this.minecraft.player;
   }

   private List<UiDrawCommand> drawn() {
      UiScreenInstance hud = JsonUiHud.instance();
      if (hud == null) {
         return List.of();
      }

      UiTextures textures = JsonUi.environment(hud.definitions()).textures();
      HudValues values = JsonUiHud.data().values();
      List<UiDrawCommand> all = new ArrayList<>();

      for (UiDrawCommand command : hud.drawList().commands()) {
         if (command instanceof UiDrawCommand.Custom custom && HudRenderers.handles(custom.renderer()) && command.control().visible()) {
            all.addAll(HudRenderers.expand(custom, values, textures));
         }
      }

      return all;
   }

   private static List<UiControl> visible(String name) {
      UiScreenInstance hud = JsonUiHud.instance();
      return hud == null ? List.of() : hud.findAll(name).stream().filter(UiControl::visible).toList();
   }

   private DevHudPlayerChecks.Result hotbar(boolean jsonUi) {
      LocalPlayer player = this.player();
      int selected = player.getInventory().getSelectedSlot();
      int count = player.getInventory().getItem(2).getCount();
      String java = "selected " + selected + ", slot 3 count " + count + " " + player.getInventory().getItem(2).getItem();
      if (!jsonUi) {
         return new DevHudPlayerChecks.Result(selected == 2 && count == 7, java);
      }

      List<UiControl> grids = visible("hotbar_grid");
      if (grids.size() != 1) {
         return new DevHudPlayerChecks.Result(false, java + "; visible hotbar grids " + grids.size());
      }

      UiControl grid = grids.getFirst();
      List<Integer> marked = new ArrayList<>();

      for (int i = 0; i < grid.generated().size(); i++) {
         UiControl image = grid.generated().get(i).find("hotbar_slot_selected_image");
         if (image != null && image.visible()) {
            marked.add(i);
         }
      }

      UiControl label = grid.generated().size() > 2 ? grid.generated().get(2).find("item_count") : null;
      String shown = label == null ? "none" : label.text();
      long slots = HudRenderers.textures(this.drawn()).stream().filter(path -> path.contains("/hotbar_")).count();
      return new DevHudPlayerChecks.Result(
         marked.equals(List.of(selected)) && Integer.toString(count).equals(shown) && slots == 9L,
         java + "; JSON UI selection " + marked + ", count '" + shown + "', slot images " + slots
      );
   }

   private DevHudPlayerChecks.Result health(boolean jsonUi) {
      float server = BedrockLocalPlayer.serverAttribute("minecraft:health");
      int health = (int)Math.ceil(this.player().getHealth());
      String java = "server " + server + ", Java " + this.player().getHealth();
      boolean same = Math.ceil(server) == health;
      if (!jsonUi) {
         return new DevHudPlayerChecks.Result(same && health < 20, java);
      }

      List<UiDrawCommand> drawn = this.drawn();
      long halves = HudRenderers.count(drawn, "heart") * 2L + HudRenderers.count(drawn, "heart_half");
      HudValues values = JsonUiHud.data().values();
      return new DevHudPlayerChecks.Result(
         same && values.health() == health && halves == health, java + "; JSON UI hearts " + halves + " halves, value " + values.health()
      );
   }

   private DevHudPlayerChecks.Result food(boolean jsonUi) {
      float server = BedrockLocalPlayer.serverAttribute("minecraft:player.hunger");
      int food = this.player().getFoodData().getFoodLevel();
      String java = "server " + server + ", Java " + food;
      boolean same = (int)server == food;
      if (!jsonUi) {
         return new DevHudPlayerChecks.Result(same, java);
      }

      List<UiDrawCommand> drawn = this.drawn();
      long halves = (HudRenderers.count(drawn, "hunger_full") + HudRenderers.count(drawn, "hunger_effect_full")) * 2L
         + HudRenderers.count(drawn, "hunger_half")
         + HudRenderers.count(drawn, "hunger_effect_half");
      return new DevHudPlayerChecks.Result(same && halves == food, java + "; JSON UI " + halves + " halves");
   }

   private DevHudPlayerChecks.Result armor(boolean jsonUi) {
      int armor = this.player().getArmorValue();
      String java = "Java (client-computed) " + armor;
      if (!jsonUi) {
         return new DevHudPlayerChecks.Result(armor == 6, java);
      }

      List<UiDrawCommand> drawn = this.drawn();
      long halves = HudRenderers.count(drawn, "armor_full") * 2L + HudRenderers.count(drawn, "armor_half");
      return new DevHudPlayerChecks.Result(armor == 6 && halves == armor, java + "; JSON UI " + halves + " halves");
   }

   private DevHudPlayerChecks.Result experience(boolean jsonUi) {
      float server = BedrockLocalPlayer.serverAttribute("minecraft:player.level");
      int level = this.player().experienceLevel;
      String java = "server level " + server + ", Java " + level + " progress " + this.player().experienceProgress;
      if (!jsonUi) {
         return new DevHudPlayerChecks.Result(level == 7 && (int)server == 7, java);
      }

      List<UiControl> labels = visible("progress_text_label");
      String shown = labels.size() == 1 ? labels.getFirst().text() : labels.toString();
      return new DevHudPlayerChecks.Result(level == 7 && (int)server == 7 && "7".equals(shown), java + "; JSON UI level '" + shown + "'");
   }

   private DevHudPlayerChecks.Result effects(boolean jsonUi) {
      List<String> active = new ArrayList<>();

      for (MobEffectInstance effect : this.player().getActiveEffects()) {
         active.add(BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value()).getPath());
      }

      boolean java = active.contains("speed") && active.contains("weakness");
      if (!jsonUi) {
         return new DevHudPlayerChecks.Result(java, "Java " + active);
      }

      List<UiDrawCommand> drawn = this.drawn();
      boolean icons = HudRenderers.count(drawn, "speed_effect") == 1L && HudRenderers.count(drawn, "weakness_effect") == 1L;
      return new DevHudPlayerChecks.Result(
         java && icons,
         "Java "
            + active
            + "; JSON UI icons "
            + HudRenderers.textures(drawn).stream().filter(path -> path.endsWith("_effect.png") || path.endsWith("_effect")).toList()
      );
   }

   private DevHudPlayerChecks.Result itemName(boolean jsonUi) {
      String hand = this.player().getInventory().getSelectedItem().getHoverName().getString();
      if (!jsonUi) {
         return new DevHudPlayerChecks.Result(hand.contains("Apple"), "Java hand '" + hand + "'");
      }

      List<UiControl> names = visible("item_name_text");
      String shown = names.isEmpty() ? "none" : names.getLast().find("item_text_label").text();
      return new DevHudPlayerChecks.Result(shown.contains(hand), "Java hand '" + hand + "'; JSON UI '" + shown + "'");
   }

   private DevHudPlayerChecks.Result chat(boolean jsonUi) {
      String last = JsonUiHud.lastChat();
      if (!jsonUi) {
         return new DevHudPlayerChecks.Result(last.contains("hello hud"), "Java '" + last + "'");
      }

      UiScreenInstance hud = JsonUiHud.instance();
      UiControl panel = hud == null ? null : hud.find("chat_panel");
      List<String> lines = panel == null ? List.of() : panel.findAll("chat_text").stream().map(UiControl::text).toList();
      return new DevHudPlayerChecks.Result(!lines.isEmpty() && lines.getLast().contains("hello hud"), "Java '" + last + "'; JSON UI lines " + lines);
   }

   private DevHudPlayerChecks.Result air(boolean jsonUi) {
      int air = this.player().getAirSupply();
      boolean underwater = this.player().isEyeInFluid(FluidTags.WATER);
      String java = "Java air " + air + "/" + this.player().getMaxAirSupply() + ", eyes in water " + underwater;
      if (!jsonUi) {
         return new DevHudPlayerChecks.Result(underwater && air < this.player().getMaxAirSupply(), java);
      }

      List<UiDrawCommand> drawn = this.drawn();
      long full = HudRenderers.count(drawn, "bubble");
      long expected = (long)Math.ceil((float)((Math.max(0, air) - 2) * 10) / this.player().getMaxAirSupply());
      return new DevHudPlayerChecks.Result(underwater && full == expected, java + "; JSON UI " + full + " full bubbles, expected " + expected);
   }

   private DevHudPlayerChecks.Result mount(boolean jsonUi) {
      if (this.player().getVehicle() instanceof LivingEntity vehicle) {
         int var7 = Math.min(30, (int)(vehicle.getMaxHealth() + 0.5F) / 2);
         String java = "Java mount " + vehicle.getType() + " health " + vehicle.getHealth() + "/" + vehicle.getMaxHealth();
         if (!jsonUi) {
            return new DevHudPlayerChecks.Result(var7 == 5, java);
         }

         long drawn = HudRenderers.count(this.drawn(), "horse_heart") + HudRenderers.count(this.drawn(), "horse_heart_half");
         return new DevHudPlayerChecks.Result(var7 == 5 && drawn == Math.ceil(vehicle.getHealth() / 2.0F), java + "; JSON UI " + drawn + " mount hearts");
      } else {
         return new DevHudPlayerChecks.Result(false, "not riding");
      }
   }

   private DevHudPlayerChecks.Result hiddenHotbar(boolean jsonUi) {
      boolean hidden = BedrockHud.hiddenElements().contains(HudElement.HOTBAR);
      if (!jsonUi) {
         return new DevHudPlayerChecks.Result(hidden, "SetHud hotbar hidden " + hidden);
      }

      long slots = HudRenderers.textures(this.drawn()).stream().filter(path -> path.contains("/hotbar_")).count();
      return new DevHudPlayerChecks.Result(hidden && slots == 0L, "SetHud hotbar hidden " + hidden + "; JSON UI slot images " + slots);
   }

   private void check(String what, DevHudPlayerChecks.Result result) {
      this.check(what, result.ok(), result.detail());
   }

   private void check(String what, boolean ok, String detail) {
      if (ok) {
         this.passed++;
      } else {
         this.failed++;
      }

      BedrockNative.LOGGER.info("{} {}: {} ({})", new Object[]{this.prefix, what, ok ? "PASS" : "FAIL", detail});
   }

   private void log(String label, String text) {
      BedrockNative.LOGGER.info("{} {}: {}", new Object[]{this.prefix, label, text});
   }

   private <T> T read(Supplier<T> reader) {
      return CompletableFuture.supplyAsync(reader, this.minecraft::execute).join();
   }

   private void dryMeadow() {
      this.commands("fill -60 86 150 -24 96 190 air replace water", "fill -60 86 150 -24 96 190 air replace flowing_water");
      sleep(1500L);
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

   private record Result(boolean ok, String detail) {
   }
}
