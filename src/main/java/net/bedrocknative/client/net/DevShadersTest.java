package net.bedrocknative.client.net;

import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.model.BedrockModels;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.commands.arguments.EntityAnchorArgument.Anchor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

final class DevShadersTest {
   private static final String[] BLOCKS = new String[]{
      "bedrocknative:test_full",
      "bedrocknative:test_pedestal",
      "bedrocknative:test_lamp",
      "bedrocknative:test_flipbook",
      "bedrocknative:test_glass",
      "chest",
      "enchanting_table",
      "standing_banner",
      "standing_sign",
      "campfire",
      "glass",
      "oak_leaves"
   };
   private static final String[] MOBS = new String[]{"armor_stand", "zombie", "cow", "villager", "sheep", "bedrocknative:test_golem", "pig"};
   private static final String PREFIX = "shaders-";
   private final Minecraft minecraft;

   DevShadersTest(Minecraft minecraft) {
      this.minecraft = minecraft;
   }

   void run() {
      log(
         "Iris loaded {}, Sodium loaded {}, shader pack in use {}",
         FabricLoader.getInstance().isModLoaded("iris"),
         FabricLoader.getInstance().isModLoaded("sodium"),
         packInUse()
      );
      if (!this.waitFor(30000L, () -> this.onClient(BedrockModels::registry) != null)) {
         log("the model registry never loaded (no Bedrock install?)");
      }

      BlockPos spawn = this.onClient(() -> this.minecraft.player.blockPosition());
      int ox = spawn.getX();
      int oz = spawn.getZ();
      int oy = Math.min(spawn.getY() + 30, 280);
      this.commands(
         "gamemode creative",
         "difficulty easy",
         "weather clear",
         "gamerule dodaylightcycle false",
         "time set 1000",
         "gamerule domobspawning false",
         "kill @e[type=!player,r=80]",
         tp(ox + 0.5, oy, oz + 0.5)
      );
      sleep(3000L);
      this.commands(
         String.format(Locale.ROOT, "fill %d %d %d %d %d %d stone", ox - 10, oy - 2, oz - 10, ox + 10, oy - 2, oz + 10),
         String.format(Locale.ROOT, "fill %d %d %d %d %d %d grass_block", ox - 10, oy - 1, oz - 10, ox + 10, oy - 1, oz + 10),
         String.format(Locale.ROOT, "fill %d %d %d %d %d %d air", ox - 10, oy, oz - 10, ox + 10, oy + 8, oz + 10),
         String.format(Locale.ROOT, "fill %d %d %d %d %d %d water", ox + 3, oy - 1, oz + 1, ox + 6, oy - 1, oz + 3)
      );

      for (int i = 0; i < BLOCKS.length; i++) {
         this.commands(String.format(Locale.ROOT, "setblock %d %d %d %s", ox - 9 + i, oy, oz + 5, BLOCKS[i]));
      }

      this.commands(String.format(Locale.ROOT, "fill %d %d %d %d %d %d stone", ox - 2, oy, oz - 2, ox - 2, oy + 1, oz - 2));

      for (int i = 0; i < MOBS.length; i++) {
         double x = ox - 6 + i * 2 + 0.5;
         this.commands(
            String.format(Locale.ROOT, "summon %s %.1f %d %.1f 0 0", MOBS[i], x, oy, oz - 4 + 0.5),
            String.format(Locale.ROOT, "effect @e[type=%s,x=%.1f,y=%d,z=%.1f,r=1.5] slowness 9999 255 true", MOBS[i], x, oy, oz - 3.5),
            String.format(Locale.ROOT, "effect @e[type=%s,x=%.1f,y=%d,z=%.1f,r=1.5] fire_resistance 9999 0 true", MOBS[i], x, oy, oz - 3.5)
         );
      }

      this.commands(
         "replaceitem entity @e[type=zombie] slot.armor.head 0 iron_helmet",
         "replaceitem entity @e[type=zombie] slot.weapon.mainhand 0 iron_sword",
         "replaceitem entity @e[type=armor_stand] slot.armor.head 0 bedrocknative:test_helmet",
         "replaceitem entity @e[type=armor_stand] slot.armor.chest 0 diamond_chestplate",
         "replaceitem entity @e[type=armor_stand] slot.weapon.mainhand 0 bedrocknative:test_wand",
         "replaceitem entity @s slot.armor.head 0 bedrocknative:test_helmet",
         "replaceitem entity @s slot.armor.chest 0 bedrocknative:test_chestplate",
         "replaceitem entity @s slot.hotbar 1 enchanted_golden_apple 3",
         "replaceitem entity @s slot.hotbar 2 bedrocknative:test_wand",
         "replaceitem entity @s slot.hotbar 0 diamond_sword",
         "enchant @s sharpness 1"
      );
      this.commands(tp(ox + 2.5, oy, oz + 0.5) + " facing " + (ox + 2.5) + " " + oy + " " + (oz + 3));
      this.run(() -> this.minecraft.player.getInventory().setSelectedSlot(1));
      sleep(800L);

      for (int i = 0; i < 3; i++) {
         this.run(() -> this.minecraft.gameMode.dropItem(this.minecraft.player, false));
         sleep(300L);
      }

      this.run(() -> this.minecraft.player.getInventory().setSelectedSlot(0));
      this.commands(tp(ox + 0.5, oy, oz + 9.5) + " facing " + ox + "0.5 " + (oy + 1) + " " + (oz - 4));
      this.commands("scriptevent bedrocknative:text spawn");
      sleep(3000L);
      this.reportEntities();
      this.views("", ox, oy, oz);
      Boolean before = packInUse();
      if (Boolean.TRUE.equals(before)) {
         this.setShaders(false);
         sleep(4000L);
         this.views("off-", ox, oy, oz);
         this.setShaders(true);
         sleep(10000L);
         this.views("re-", ox, oy, oz);
      }

      this.commands("gamemode survival");
      sleep(800L);
      this.run(() -> this.minecraft.gui.setScreen(new InventoryScreen(this.minecraft.player)));
      sleep(2000L);
      this.screenshot("inventory");
      this.run(() -> this.minecraft.gui.setScreen(null));
      sleep(500L);
      this.commands("gamemode creative", "time set 14000");
      this.view("night", new Vec3(ox + 0.5, oy + 1.62, oz + 9.5), new Vec3(ox + 0.5, oy, oz - 4));
      this.commands("time set 1000", "gamerule dodaylightcycle true", "kill @e[type=!player,r=80]");
      log("done");
   }

   private void views(String tag, int ox, int oy, int oz) {
      this.view(tag + "overview", new Vec3(ox + 0.5, oy + 1.62, oz + 9.5), new Vec3(ox + 0.5, oy, oz - 4));
      this.view(tag + "mobs", new Vec3(ox + 0.5, oy + 1.62, oz + 1.5), new Vec3(ox + 0.5, oy + 1, oz - 4));
      this.view(tag + "blocks", new Vec3(ox - 3.5, oy + 1.8, oz + 9.5), new Vec3(ox - 3.5, oy + 0.5, oz + 5));
      this.commands(
         String.format(Locale.ROOT, "particle minecraft:villager_happy %.1f %d %.1f", ox - 3.5, oy + 1, oz + 4.5),
         String.format(Locale.ROOT, "particle minecraft:basic_flame_particle %.1f %d %.1f", ox - 4.5, oy + 1, oz + 4.5),
         String.format(Locale.ROOT, "particle bedrocknative:test_sparkle %.1f %d %.1f", ox - 2.5, oy + 1, oz + 4.5),
         String.format(Locale.ROOT, "particle minecraft:critical_hit_emitter %.1f %d %.1f", ox - 5.5, oy + 1, oz + 4.5)
      );
      this.screenshot(tag + "particles");
      this.view(tag + "water-sun", new Vec3(ox + 1.5, oy + 1.62, oz + 2.5), new Vec3(ox + 40, oy + 10, oz + 2.5));
      this.view(tag + "items-water", new Vec3(ox + 4.5, oy + 1.62, oz + 6.5), new Vec3(ox + 4.5, oy - 1, oz + 2));
      this.commands(tp(ox + 0.5, oy, oz + 0.5) + " facing " + (ox + 0.5) + " " + (oy + 1) + " " + (oz + 9));
      this.run(() -> this.minecraft.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
      sleep(2000L);
      this.screenshot(tag + "third-front");
      this.run(() -> this.minecraft.options.setCameraType(CameraType.FIRST_PERSON));
      sleep(1500L);
      this.screenshot(tag + "first-sword");
      this.run(() -> this.minecraft.player.getInventory().setSelectedSlot(3));
      sleep(1500L);
      this.screenshot(tag + "first-empty");
      this.run(() -> this.minecraft.player.getInventory().setSelectedSlot(2));
      sleep(1500L);
      this.screenshot(tag + "first-wand");
      this.run(() -> this.minecraft.player.getInventory().setSelectedSlot(0));
   }

   private void setShaders(boolean on) {
      this.run(() -> {
         try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object instance = api.getMethod("getInstance").invoke(null);
            Object config = api.getMethod("getConfig").invoke(instance);
            Class.forName("net.irisshaders.iris.api.v0.IrisApiConfig").getMethod("setShadersEnabledAndApply", boolean.class).invoke(config, on);
         } catch (ReflectiveOperationException | LinkageError e) {
            log("switching shaders {} failed: {}", on ? "on" : "off", e.toString());
         }
      });
      log("shaders switched {} (in use now {})", on ? "on" : "off", packInUse());
   }

   private void view(String name, Vec3 eye, Vec3 target) {
      this.commands(tp(eye.x, eye.y - 1.62, eye.z));
      this.run(() -> this.minecraft.player.lookAt(Anchor.EYES, target));
      sleep(2500L);
      this.run(() -> this.minecraft.player.lookAt(Anchor.EYES, target));
      sleep(500L);
      this.screenshot(name);
   }

   private static String tp(double x, double y, double z) {
      return String.format(Locale.ROOT, "tp @s %.2f %.2f %.2f", x, y, z);
   }

   private void reportEntities() {
      Map<String, Integer> seen = this.onClient(() -> {
         Map<String, Integer> types = new TreeMap<>();
         this.minecraft.level.entitiesForRendering().forEach(entity -> types.merge(entity.typeHolder().getRegisteredName(), 1, Integer::sum));
         return types;
      });
      log("entities in the level: {}", seen);
   }

   private static Boolean packInUse() {
      try {
         Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
         Object instance = api.getMethod("getInstance").invoke(null);
         return (Boolean)api.getMethod("isShaderPackInUse").invoke(instance);
      } catch (ReflectiveOperationException | LinkageError absent) {
         return null;
      }
   }

   private void commands(String... commands) {
      for (String command : commands) {
         this.run(() -> this.minecraft.getConnection().sendCommand(command));
         sleep(400L);
      }

      sleep(600L);
   }

   private void screenshot(String name) {
      this.run(
         () -> Screenshot.grab(this.minecraft.gameDirectory, "shaders-" + name + ".png", this.minecraft.gameRenderer.mainRenderTarget(), 1, message -> {})
      );
      sleep(700L);
      log("screenshot {}{} (shader pack in use {})", "shaders-", name, packInUse());
   }

   private boolean waitFor(long millis, BooleanSupplier condition) {
      long started = System.currentTimeMillis();

      while (System.currentTimeMillis() - started < millis) {
         if (condition.getAsBoolean()) {
            return true;
         }

         sleep(200L);
      }

      return condition.getAsBoolean();
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
      BedrockNative.LOGGER.info("Dev shaders test: " + message, args);
   }

   private static void sleep(long millis) {
      try {
         Thread.sleep(millis);
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
      }
   }
}
