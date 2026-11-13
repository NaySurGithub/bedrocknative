package net.bedrocknative.client.net;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import net.bedrocknative.BedrockNative;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.StartTick;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.Screenshot;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

final class DevMovementTest {
   private static final int BX = -40;
   private static final int BY = 100;
   private static final int BZ = 20;
   private static final int LAB_COLUMNS = 6;
   private static final int LAB_CELL = 10;
   private static final String HIT = "damage @s 1 entity_attack entity @e[type=armor_stand,c=1]";
   private static volatile DevMovementTest.Script running;
   private final Minecraft minecraft;
   private final MovementStats stats;
   private final List<String> results = new ArrayList<>();
   private final boolean boxes;
   private final boolean jumps;

   DevMovementTest(Minecraft minecraft, MovementStats stats) {
      this.minecraft = minecraft;
      this.stats = stats;
      this.boxes = System.getProperty("bedrocknative.devTest", "").startsWith("movement-boxes");
      this.jumps = System.getProperty("bedrocknative.devTest", "").startsWith("movement-jumps");
   }

   void run() {
      ClientTickEvents.START_CLIENT_TICK.register((StartTick)client -> {
         DevMovementTest.Script script = running;
         if (script != null) {
            script.tick(client);
         }
      });
      this.commands(
         List.of(
            "gamemode survival",
            "clear @s",
            "effect @s clear",
            "time set noon",
            "weather clear",
            this.boxes ? "tp @s -10 103 50" : "tp @s -12 103 41",
            "effect @s slow_falling 30 0 true"
         )
      );
      sleep(3000L);
      if (this.boxes) {
         this.commands(boxCourse());
         sleep(2500L);
         this.verifyLab();
      } else if (this.jumps) {
         this.commands(jumpCourse());
         sleep(2500L);
         this.commands(jumpCourse());
         sleep(2500L);
         this.verifyJumpCourse();
      } else {
         this.commands(course());
         sleep(2000L);
         this.minecraft
            .execute(
               () -> BedrockNative.LOGGER
                  .info(
                     "Dev movement course: ladder {}, chest {}, pane {}, trapdoor {}",
                     new Object[]{
                        this.minecraft.level.getBlockState(new BlockPos(cx(4), 100, 24)),
                        this.minecraft.level.getBlockState(new BlockPos(cx(13), 100, 25)),
                        this.minecraft.level.getBlockState(new BlockPos(cx(14), 100, 25)),
                        this.minecraft.level.getBlockState(new BlockPos(cx(15), 100, 25))
                     }
                  )
            );
      }

      this.commands(List.of("effect @s clear", "effect @s saturation 9999 255 true"));
      String test = System.getProperty("bedrocknative.devTest", "");
      List<String> only = test.contains(":") ? List.of(test.substring(test.indexOf(58) + 1).split(",")) : List.of();

      for (DevMovementTest.Scenario scenario : this.boxes ? boxScenarios() : (this.jumps ? jumpScenarios() : scenarios())) {
         if (only.isEmpty() ? !scenario.name().endsWith("-creep") : only.contains(scenario.name())) {
            this.runScenario(scenario);
         }
      }

      BedrockNative.LOGGER
         .info(
            "Dev movement results ({} scenarios, {} corrections in total):\n{}",
            new Object[]{this.results.size(), this.stats.count(), String.join("\n", this.results)}
         );
      this.command("replaceitem entity @s slot.armor.chest 0 air");
      this.minecraft.execute(this.minecraft::stop);
   }

   private void runScenario(DevMovementTest.Scenario scenario) {
      List<String> setup = new ArrayList<>(List.of("effect @s instant_health 1 4 true"));
      setup.addAll(scenario.setup());
      this.commands(setup);
      double x = cx(scenario.lane()) + 0.5 + scenario.dx();
      double y = 100.0 + scenario.dy();
      double z = 22.5 + scenario.dz();
      this.command(String.format(Locale.ROOT, "tp @s %.2f %.2f %.2f %.1f 0", x, y, z, scenario.yaw()));
      if (scenario.airborne()) {
         sleep(400L);
      } else {
         this.waitUntilSettled();
      }

      long startTick = this.stats.lastTick();
      Vec3[] start = new Vec3[1];
      this.minecraft.execute(() -> {
         start[0] = this.minecraft.player.position();
         this.minecraft.player.setYRot(scenario.yaw());
         this.minecraft.player.setXRot(0.0F);
      });
      DevMovementTest.Script script = new DevMovementTest.Script(scenario.steps(), this.stats, startTick);
      running = script;
      script.done.join();
      running = null;
      sleep(1000L);
      CompletableFuture<Vec3> end = new CompletableFuture<>();
      this.minecraft.execute(() -> end.complete(this.minecraft.player.position()));
      Vec3 finalPos = end.join();
      List<MovementStats.Correction> corrections = this.stats.since(startTick);
      String line;
      if (corrections.isEmpty()) {
         line = String.format(Locale.ROOT, "| %s | %d | 0 | - | - | - | %s |", scenario.name(), scenario.ticks(), format(finalPos.subtract(start[0])));
      } else {
         MovementStats.Correction first = corrections.getFirst();
         line = String.format(
            Locale.ROOT,
            "| %s | %d | %d | +%d (recv +%d) | server %s ours %s | %.4f (dv server %s ours %s) | %s |",
            scenario.name(),
            scenario.ticks(),
            corrections.size(),
            first.tick() - startTick,
            first.receivedAt() - startTick,
            format(first.server().subtract(start[0])),
            first.client() == null ? "?" : format(first.client().subtract(start[0])),
            first.distance(),
            format(first.serverDelta()),
            first.clientDelta() == null ? "?" : format(first.clientDelta()),
            format(finalPos.subtract(start[0]))
         );

         for (MovementStats.Correction correction : corrections.subList(0, Math.min(6, corrections.size()))) {
            BedrockNative.LOGGER
               .info(
                  "Dev movement {}: correction tick +{} server {} ours {} ({}) dv server {} ours {} ground {}",
                  new Object[]{
                     scenario.name(),
                     correction.tick() - startTick,
                     precise(correction.server()),
                     correction.client() == null ? "?" : precise(correction.client()),
                     String.format(Locale.ROOT, "%.6f", correction.distance()),
                     precise(correction.serverDelta()),
                     correction.clientDelta() == null ? "?" : precise(correction.clientDelta()),
                     correction.serverOnGround()
                  }
               );
         }
      }

      this.results.add(line);
      BedrockNative.LOGGER.info("Dev movement: {}", line);
      if (scenario.name().equals("sprint-hit")) {
         this.minecraft
            .execute(
               () -> BedrockNative.LOGGER
                  .info(
                     "Dev movement combat: sprinting {} attack strength {} block reach {} miss lockout {}",
                     new Object[]{
                        this.minecraft.player.isSprinting(),
                        this.minecraft.player.getAttackStrengthScale(0.0F),
                        this.minecraft.player.blockInteractionRange(),
                        this.minecraft.gameMode.hasMissTime()
                     }
                  )
            );
      }

      if (scenario.name().equals("ladder") || scenario.name().equals("water")) {
         this.minecraft.execute(() -> Screenshot.grab(this.minecraft, false));
      }
   }

   private static List<DevMovementTest.Scenario> scenarios() {
      List<DevMovementTest.Scenario> list = new ArrayList<>();
      list.add(new DevMovementTest.Scenario("walk", 0, 0.0, 0.0F, List.of(), steps(40, "w"), false));
      list.add(new DevMovementTest.Scenario("sprint", 0, 0.0, 0.0F, List.of(), steps(40, "wr"), false));
      list.add(new DevMovementTest.Scenario("sneak", 0, 0.0, 0.0F, List.of(), steps(40, "wc"), false));
      list.add(new DevMovementTest.Scenario("sneak-release", 0, 0.0, 0.0F, List.of(), steps(10, "wc", 20, "w", 10, "wc", 10, "w"), false));
      list.add(new DevMovementTest.Scenario("diagonal", 0, 0.0, 45.0F, List.of(), steps(40, "wa"), false));
      list.add(new DevMovementTest.Scenario("diagonal-sneak", 0, 0.0, 45.0F, List.of(), steps(40, "wac"), false));
      list.add(new DevMovementTest.Scenario("jump", 0, 0.0, 0.0F, List.of(), steps(30, "j"), false));
      list.add(new DevMovementTest.Scenario("walk-jump", 0, 0.0, 0.0F, List.of(), steps(40, "wj"), false));
      list.add(new DevMovementTest.Scenario("sprint-jump", 0, 0.0, 0.0F, List.of(), steps(60, "wrj"), false));
      list.add(
         new DevMovementTest.Scenario(
            "bow-walk",
            0,
            0.0,
            0.0F,
            List.of("clear @s", "replaceitem entity @s slot.weapon.mainhand 0 bow", "give @s arrow 16"),
            steps(30, "wu", 10, "w"),
            false
         )
      );
      list.add(
         new DevMovementTest.Scenario(
            "knockback",
            0,
            0.0,
            0.0F,
            List.of("kill @e[type=armor_stand]", "summon armor_stand " + (cx(0) + 0.5) + " 100 20.5"),
            List.of(new DevMovementTest.Step(40, "", "damage @s 1 entity_attack entity @e[type=armor_stand,c=1]")),
            false
         )
      );
      list.add(
         new DevMovementTest.Scenario(
            "walk-knockback",
            0,
            0.0,
            0.0F,
            List.of(),
            List.of(new DevMovementTest.Step(10, "w", null), new DevMovementTest.Step(30, "w", "damage @s 1 entity_attack entity @e[type=armor_stand,c=1]")),
            false
         )
      );
      list.add(
         new DevMovementTest.Scenario(
            "sprint-jump-knockback",
            0,
            0.0,
            0.0F,
            List.of(),
            List.of(new DevMovementTest.Step(12, "wrj", null), new DevMovementTest.Step(40, "wrj", "damage @s 1 entity_attack entity @e[type=armor_stand,c=1]")),
            false
         )
      );
      list.add(
         new DevMovementTest.Scenario(
            "sprint-hit",
            0,
            0.0,
            0.0F,
            List.of("kill @e[type=armor_stand]", "summon armor_stand " + (cx(0) + 0.5) + " 100 26.5"),
            steps(6, "wr", 1, "wrk", 13, "wr"),
            false
         )
      );
      list.add(new DevMovementTest.Scenario("wall-then-jump-up", 1, 0.0, 0.0F, List.of("kill @e[type=armor_stand]"), steps(20, "w", 20, "wj"), false));
      list.add(new DevMovementTest.Scenario("slabs", 2, 0.0, 0.0F, List.of(), steps(50, "w"), false));
      list.add(new DevMovementTest.Scenario("stairs", 3, 0.0, 0.0F, List.of(), steps(50, "w"), false));
      list.add(new DevMovementTest.Scenario("ladder", 4, 0.0, 0.0F, List.of(), steps(70, "w"), false));
      list.add(new DevMovementTest.Scenario("ladder-sneak", 4, 0.0, 0.0F, List.of(), steps(25, "w", 20, "wc", 20, "c", 25, ""), false));
      list.add(new DevMovementTest.Scenario("ice", 5, 0.0, 0.0F, List.of(), steps(25, "wr", 25, ""), false));
      list.add(new DevMovementTest.Scenario("soul-sand", 6, 0.0, 0.0F, List.of(), steps(40, "w"), false));
      list.add(new DevMovementTest.Scenario("slime-walk", 7, 0.0, 0.0F, List.of(), steps(30, "w"), false));
      list.add(new DevMovementTest.Scenario("slime-bounce", 7, 5.0, 0.0F, List.of(), steps(60, ""), true));
      list.add(new DevMovementTest.Scenario("honey", 8, 0.0, 0.0F, List.of(), steps(30, "w", 20, "wj"), false));
      list.add(new DevMovementTest.Scenario("water", 9, 0.0, 0.0F, List.of(), steps(60, "w"), false));
      list.add(new DevMovementTest.Scenario("water-jump", 9, 0.0, 0.0F, List.of(), steps(20, "w", 40, "wj"), false));
      list.add(new DevMovementTest.Scenario("fall-10", 10, 10.0, 0.0F, List.of(), steps(50, ""), true));
      list.add(new DevMovementTest.Scenario("sneak-edge", 11, 0.0, 0.0, 2.0, 0.0F, List.of(), steps(40, "wc"), false));
      list.add(new DevMovementTest.Scenario("sneak-edge-diagonal", 11, 0.0, 0.0, 2.0, 20.0F, List.of(), steps(40, "wc"), false));
      list.add(new DevMovementTest.Scenario("cobweb", 12, 0.0, 0.0F, List.of(), steps(50, "w"), false));
      list.add(new DevMovementTest.Scenario("chest", 13, 0.0, 0.0F, List.of(), steps(20, "w", 20, "wj"), false));
      list.add(new DevMovementTest.Scenario("pane-arm", 14, 0.0, 0.0F, List.of(), steps(30, "w"), false));
      list.add(new DevMovementTest.Scenario("trapdoor-step", 15, 0.0, 0.0F, List.of(), steps(30, "w"), false));
      list.add(new DevMovementTest.Scenario("lantern-step", 16, 0.0, 0.0F, List.of(), steps(30, "w"), false));
      list.add(new DevMovementTest.Scenario("speed-walk", 0, 0.0, 0.0F, List.of("effect @s speed 30 1 true"), withClear(steps(40, "w")), false));
      list.add(new DevMovementTest.Scenario("slowness-walk", 0, 0.0, 0.0F, List.of("effect @s slowness 30 1 true"), withClear(steps(40, "w")), false));
      list.add(new DevMovementTest.Scenario("jump-boost", 0, 0.0, 0.0F, List.of("effect @s jump_boost 30 1 true"), withClear(steps(30, "j")), false));
      list.add(new DevMovementTest.Scenario("slow-falling", 0, 6.0, 0.0F, List.of("effect @s slow_falling 30 0 true"), withClear(steps(60, "")), true));
      list.add(new DevMovementTest.Scenario("levitation", 0, 0.0, 0.0F, List.of("effect @s levitation 2 0 true"), withClear(steps(60, "")), false));
      list.add(new DevMovementTest.Scenario("swim", 9, 0.0, -3.0, 4.0, 0.0F, List.of(), steps(40, "wr", 10, ""), false));
      list.add(new DevMovementTest.Scenario("bubble-column", 18, 0.0, -4.0, 3.0, 0.0F, List.of(), steps(40, ""), true));
      list.add(new DevMovementTest.Scenario("scaffolding", 19, 0.0, 0.0F, List.of(), steps(10, "w", 25, "wj", 25, "c"), false));
      list.addAll(flightScenarios());
      List<DevMovementTest.Step> glide = new ArrayList<>(steps(2, "", 1, "j", 30, "", 1, "u", 30, ""));
      glide.add(new DevMovementTest.Step(20, "", "effect @s slow_falling 30 0 true"));
      list.add(
         new DevMovementTest.Scenario(
            "elytra-firework",
            0,
            30.0,
            0.0F,
            List.of("gamemode survival", "replaceitem entity @s slot.armor.chest 0 elytra", "replaceitem entity @s slot.weapon.mainhand 0 firework_rocket 8"),
            glide,
            true
         )
      );
      return list;
   }

   private static List<DevMovementTest.Scenario> flightScenarios() {
      List<String> creative = List.of("gamemode survival", "gamemode creative");
      List<DevMovementTest.Scenario> list = new ArrayList<>();
      list.add(new DevMovementTest.Scenario("fly-takeoff", 0, 0.0, 0.0F, creative, takeoff(20, ""), false));
      list.add(new DevMovementTest.Scenario("fly-forward", 0, 0.0, 0.0F, creative, takeoff(20, "w", 15, ""), false));
      list.add(new DevMovementTest.Scenario("fly-sprint", 0, 0.0, 0.0F, creative, takeoff(20, "wr", 15, ""), false));
      list.add(new DevMovementTest.Scenario("fly-diagonal", 0, 0.0, 45.0F, creative, takeoff(20, "wa", 10, ""), false));
      list.add(new DevMovementTest.Scenario("fly-rise", 0, 0.0, 0.0F, creative, takeoff(20, "j", 10, ""), false));
      list.add(new DevMovementTest.Scenario("fly-forward-rise", 0, 0.0, 0.0F, creative, takeoff(15, "w", 10, "j", 5, ""), false));
      list.add(new DevMovementTest.Scenario("fly-descend-land", 0, 0.0, 0.0F, creative, takeoff(10, "j", 5, "", 25, "c", 10, "w"), false));
      list.add(new DevMovementTest.Scenario("fly-forward-descend", 0, 0.0, 0.0F, creative, takeoff(10, "j", 20, "wc", 10, ""), false));
      list.add(new DevMovementTest.Scenario("fly-water", 9, 0.0, 0.0F, creative, takeoff(12, "w", 15, "c", 10, "w", 10, "j", 5, ""), false));
      list.add(new DevMovementTest.Scenario("fly-ground-edge", 11, 0.0, 0.0F, creative, takeoff(8, "c", 50, "w", 5, ""), false));
      list.add(new DevMovementTest.Scenario("fly-wall", 17, 0.0, 0.0F, creative, takeoff(5, "j", 25, "w", 5, ""), false));
      list.add(new DevMovementTest.Scenario("fly-ceiling", 20, 0.0, 0.0F, creative, takeoff(25, "wj", 5, ""), false));
      list.add(new DevMovementTest.Scenario("fly-toggle-off", 0, 0.0, 0.0F, creative, takeoff(10, "j", 2, "", 1, "j", 2, "", 1, "j", 30, ""), false));
      list.add(
         new DevMovementTest.Scenario(
            "fly-speed",
            0,
            0.0,
            0.0F,
            List.of("gamemode survival", "gamemode creative", "effect @s speed 30 1 true"),
            withClear(takeoff(20, "w", 10, "")),
            false
         )
      );
      list.add(new DevMovementTest.Scenario("fly-denied", 0, 0.0, 0.0F, List.of("gamemode survival"), steps(1, "f", 20, ""), false));
      List<DevMovementTest.Step> survival = new ArrayList<>(takeoff(6, "j", 5, ""));
      survival.add(new DevMovementTest.Step(30, "", "gamemode survival"));
      list.add(new DevMovementTest.Scenario("fly-to-survival", 0, 0.0, 0.0F, creative, survival, false));
      list.add(
         new DevMovementTest.Scenario(
            "spectator", 4, 0.5, 0.0F, List.of("gamemode spectator"), steps(30, "w", 10, "wj", 10, "wc", 10, "wr", 10, "", 10, "j", 10, ""), true
         )
      );
      List<DevMovementTest.Step> leave = new ArrayList<>(steps(10, "w"));
      leave.add(new DevMovementTest.Step(20, "", "gamemode creative"));
      leave.addAll(steps(10, "c"));
      list.add(new DevMovementTest.Scenario("spectator-to-creative", 0, 2.0, 0.0F, List.of("gamemode spectator"), leave, true));
      List<DevMovementTest.Step> roundTrip = new ArrayList<>(takeoff(5, ""));
      roundTrip.add(new DevMovementTest.Step(10, "w", "gamemode spectator"));
      roundTrip.add(new DevMovementTest.Step(15, "", "gamemode creative"));
      roundTrip.addAll(steps(10, "c"));
      list.add(new DevMovementTest.Scenario("fly-spectator-creative", 0, 0.0, 0.0F, creative, roundTrip, false));
      List<String> noElytra = List.of("replaceitem entity @s slot.armor.chest 0 air", "gamemode survival", "gamemode creative");
      list.add(new DevMovementTest.Scenario("fly-jump-sneak", 0, 0.0, 0.0F, noElytra, takeoff(10, "j", 10, "jc", 10, "jcw", 10, ""), false));
      list.add(new DevMovementTest.Scenario("fly-jump-sneak-forward", 0, 0.0, 0.0F, noElytra, takeoff(10, "j", 10, "jcw", 10, ""), false));
      list.add(new DevMovementTest.Scenario("fly-wall-top", 17, 0.0, 0.0F, noElytra, takeoff(3, "", 20, "w", 16, "wj", 10, "w", 10, ""), false));
      list.add(new DevMovementTest.Scenario("fly-ladder-top", 4, 0.0, 0.0F, noElytra, takeoff(3, "", 20, "w", 10, "wj", 10, "w", 10, ""), false));
      list.add(new DevMovementTest.Scenario("fly-scaffolding", 19, 0.0, 0.0, 3.0, 0.0F, noElytra, takeoff(10, "j", 10, "c", 10, ""), false));
      list.add(
         new DevMovementTest.Scenario(
            "glide-land",
            0,
            10.0,
            0.0F,
            List.of("gamemode survival", "replaceitem entity @s slot.armor.chest 0 elytra"),
            steps(2, "", 1, "j", 40, "", 20, "w"),
            true
         )
      );
      list.add(
         new DevMovementTest.Scenario(
            "glide-to-fly",
            0,
            15.0,
            0.0F,
            List.of("gamemode survival", "gamemode creative", "replaceitem entity @s slot.armor.chest 0 elytra"),
            steps(2, "", 1, "j", 2, "", 1, "j", 20, "w", 5, ""),
            true
         )
      );
      list.add(
         new DevMovementTest.Scenario(
            "fly-end-glide",
            0,
            0.0,
            0.0F,
            List.of("gamemode survival", "gamemode creative", "replaceitem entity @s slot.armor.chest 0 elytra"),
            takeoff(15, "j", 3, "", 1, "j", 2, "", 1, "j", 25, ""),
            false
         )
      );
      return list;
   }

   private static List<DevMovementTest.Step> takeoff(Object... ticksAndKeys) {
      List<DevMovementTest.Step> list = new ArrayList<>(steps(1, "j", 2, "", 1, "j", 3, ""));
      list.addAll(steps(ticksAndKeys));
      return list;
   }

   private static List<DevMovementTest.Step> withClear(List<DevMovementTest.Step> steps) {
      List<DevMovementTest.Step> list = new ArrayList<>(steps);
      list.add(new DevMovementTest.Step(1, "", "effect @s clear"));
      return list;
   }

   private static List<DevMovementTest.Step> steps(Object... ticksAndKeys) {
      List<DevMovementTest.Step> steps = new ArrayList<>();

      for (int i = 0; i < ticksAndKeys.length; i += 2) {
         steps.add(new DevMovementTest.Step((Integer)ticksAndKeys[i], (String)ticksAndKeys[i + 1], null));
      }

      return steps;
   }

   private static int cx(int lane) {
      return -38 + 4 * lane;
   }

   private static List<String> course() {
      int z = 20;
      List<String> c = new ArrayList<>();
      c.add(fill(-42, 99, 18, 42, 99, 64, "stone"));
      c.add(fill(-42, 100, 18, -13, 112, 64, "air"));
      c.add(fill(-12, 100, 18, 15, 112, 64, "air"));
      c.add(fill(16, 100, 18, 42, 112, 64, "air"));
      int x = cx(1);
      c.add(fill(x - 1, 100, z + 5, x + 1, 100, z + 12, "stone"));
      x = cx(2);
      c.add(fill(x - 1, 100, z + 4, x + 1, 100, z + 4, "oak_slab"));
      c.add(fill(x - 1, 100, z + 5, x + 1, 100, z + 12, "stone"));
      c.add(fill(x - 1, 101, z + 6, x + 1, 101, z + 6, "oak_slab"));
      c.add(fill(x - 1, 101, z + 7, x + 1, 101, z + 12, "stone"));
      c.add(fill(x - 1, 102, z + 8, x + 1, 102, z + 8, "oak_slab"));
      x = cx(3);
      c.add(fill(x - 1, 100, z + 4, x + 1, 100, z + 4, "oak_stairs [\"weirdo_direction\"=2]"));
      c.add(fill(x - 1, 100, z + 5, x + 1, 100, z + 12, "stone"));
      c.add(fill(x - 1, 101, z + 5, x + 1, 101, z + 5, "oak_stairs [\"weirdo_direction\"=2]"));
      c.add(fill(x - 1, 101, z + 6, x + 1, 101, z + 12, "stone"));
      x = cx(4);
      c.add(fill(x - 1, 100, z + 5, x + 1, 104, z + 10, "stone"));
      c.add(fill(x, 100, z + 4, x, 104, z + 4, "ladder [\"facing_direction\"=2]"));
      x = cx(5);
      c.add(fill(x - 1, 99, z, x + 1, 99, z + 40, "ice"));
      x = cx(6);
      c.add(fill(x - 1, 99, z + 3, x + 1, 99, z + 40, "soul_sand"));
      x = cx(7);
      c.add(fill(x - 1, 99, z, x + 1, 99, z + 40, "slime"));
      x = cx(8);
      c.add(fill(x - 1, 99, z, x + 1, 99, z + 40, "honey_block"));
      x = cx(9);
      c.add(fill(x - 2, 96, z + 4, x + 2, 99, z + 16, "stone"));
      c.add(fill(x - 1, 97, z + 5, x + 1, 99, z + 15, "water"));
      x = cx(11);
      c.add(fill(x - 1, 99, z + 6, x + 1, 99, z + 20, "air"));
      c.add(fill(x - 1, 96, z + 6, x + 1, 96, z + 20, "stone"));
      x = cx(12);
      c.add(fill(x - 1, 100, z + 5, x + 1, 101, z + 6, "web"));
      x = cx(13);
      c.add("setblock " + x + " 100 " + (z + 5) + " chest");
      x = cx(14);
      c.add("setblock " + x + " 100 " + (z + 6) + " stone");
      c.add("setblock " + x + " 100 " + (z + 5) + " glass_pane");
      x = cx(15);
      c.add("setblock " + x + " 100 " + (z + 5) + " trapdoor");
      x = cx(16);
      c.add("setblock " + x + " 100 " + (z + 5) + " lantern");
      x = cx(18);
      c.add(fill(x - 1, 93, z + 4, x + 1, 104, z + 6, "glass"));
      c.add(fill(x, 94, z + 5, x, 103, z + 5, "water"));
      c.add("setblock " + x + " 93 " + (z + 5) + " soul_sand");
      c.add(fill(x, 104, z + 5, x, 104, z + 5, "air"));
      x = cx(19);
      c.add(fill(x, 100, z + 5, x, 104, z + 5, "scaffolding"));
      x = cx(17);
      c.add(fill(x - 1, 100, z + 8, x + 1, 106, z + 8, "stone"));
      x = cx(20);
      c.add(fill(x - 1, 103, z, x + 1, 103, z + 30, "stone"));
      return c;
   }

   private static String facing(String direction) {
      return "[\"minecraft:cardinal_direction\"=\"" + direction + "\"]";
   }

   private static String bed(int direction, boolean head) {
      return "bed [\"direction\"=" + direction + ",\"head_piece_bit\"=" + head + "]";
   }

   private static List<DevMovementTest.Box> labBoxes() {
      List<DevMovementTest.Box> list = new ArrayList<>();
      list.add(DevMovementTest.Box.single("chest", "chest"));
      list.add(new DevMovementTest.Box("chest-double", "chest", "chest", "chest " + facing("east"), "chest " + facing("east")));
      list.add(DevMovementTest.Box.single("trapped-chest", "trapped_chest"));
      list.add(DevMovementTest.Box.single("ender-chest", "ender_chest"));
      list.add(DevMovementTest.Box.single("barrel", "barrel"));
      list.add(DevMovementTest.Box.single("hopper", "hopper"));
      list.add(DevMovementTest.Box.single("shulker-box", "undyed_shulker_box"));
      list.add(new DevMovementTest.Box("bed", bed(3, false), bed(3, true), bed(0, false), bed(0, true)));
      list.add(DevMovementTest.Box.single("enchanting-table", "enchanting_table"));
      list.add(DevMovementTest.Box.single("lectern", "lectern"));
      list.add(DevMovementTest.Box.single("cauldron", "cauldron"));
      list.add(DevMovementTest.Box.single("composter", "composter"));
      list.add(DevMovementTest.Box.single("brewing-stand", "brewing_stand"));
      list.add(DevMovementTest.Box.single("anvil-north", "anvil " + facing("north")));
      list.add(DevMovementTest.Box.single("anvil-east", "anvil " + facing("east")));
      list.add(DevMovementTest.Box.single("grindstone", "grindstone"));
      list.add(DevMovementTest.Box.single("grindstone-wall", "grindstone [\"attachment\"=\"side\",\"direction\"=0]"));
      list.add(DevMovementTest.Box.single("stonecutter", "stonecutter_block"));
      list.add(DevMovementTest.Box.single("bell", "bell"));
      list.add(DevMovementTest.Box.single("end-portal-frame", "end_portal_frame"));
      list.add(DevMovementTest.Box.single("daylight-detector", "daylight_detector"));
      list.add(DevMovementTest.Box.single("lantern", "lantern"));
      list.add(DevMovementTest.Box.single("chain", "chain"));
      list.add(DevMovementTest.Box.single("candle", "candle"));
      list.add(DevMovementTest.Box.single("campfire", "campfire"));
      list.add(DevMovementTest.Box.single("flower-pot", "flower_pot"));
      list.add(DevMovementTest.Box.single("skull", "skull [\"facing_direction\"=1]"));
      list.add(DevMovementTest.Box.single("conduit", "conduit"));
      list.add(DevMovementTest.Box.single("turtle-egg", "turtle_egg"));
      list.add(DevMovementTest.Box.single("cake", "cake"));
      list.add(DevMovementTest.Box.single("dragon-egg", "dragon_egg"));
      list.add(DevMovementTest.Box.single("honey-block", "honey_block"));
      return list;
   }

   private static int labX(int index) {
      return -35 + 10 * (index % 6);
   }

   private static int labZ(int index) {
      return 25 + 10 * (index / 6);
   }

   private static int[][] ringOffsets() {
      return new int[][]{{0, -3}, {0, 3}, {3, 2}, {-3, 2}, {3, -2}};
   }

   private static List<String> boxCourse() {
      List<DevMovementTest.Box> list = labBoxes();
      int rows = (list.size() + 6 - 1) / 6;
      int x0 = -48;
      int x1 = 28;
      int z0 = 12;
      int z1 = 20 + rows * 10 + 8;
      List<String> c = new ArrayList<>();
      c.add(fill(x0, 99, z0, x1, 99, z1, "stone"));
      c.add(fill(x0, 100, z0, x0 + 28, 109, z1, "air"));
      c.add(fill(x0 + 29, 100, z0, x0 + 57, 109, z1, "air"));
      c.add(fill(x0 + 58, 100, z0, x1, 109, z1, "air"));

      for (int i = 0; i < list.size(); i++) {
         DevMovementTest.Box box = list.get(i);
         int[][] ring = ringOffsets();

         for (int k = 0; k < ring.length; k++) {
            boolean ns = k < 2;
            String block = ns ? box.ns() : box.ew();
            String pair = ns ? box.nsPair() : box.ewPair();
            int x = labX(i) + ring[k][0];
            int z = labZ(i) + ring[k][1];
            c.add("setblock " + x + " 100 " + z + " " + block);
            if (pair != null) {
               c.add("setblock " + (ns ? x + 1 : x) + " 100 " + (ns ? z : z + 1) + " " + pair);
            }
         }
      }

      return c;
   }

   private void verifyLab() {
      List<DevMovementTest.Box> list = labBoxes();
      this.minecraft.execute(() -> {
         int air = 0;

         for (int i = 0; i < list.size(); i++) {
            int[][] ring = ringOffsets();
            StringBuilder states = new StringBuilder();

            for (int k : new int[]{0, 2}) {
               BlockPos pos = new BlockPos(labX(i) + ring[k][0], 100, labZ(i) + ring[k][1]);
               BlockState state = this.minecraft.level.getBlockState(pos);
               if (state.isAir()) {
                  air++;
               }

               states.append(k == 0 ? "N " : "; E ").append(state);
            }

            BedrockNative.LOGGER.info("Dev boxes lab: {}: {}", list.get(i).name(), states);
         }

         BedrockNative.LOGGER.info("Dev boxes lab: {} kinds, {} of their north and east blocks are air", list.size(), air);
      });
      sleep(500L);
   }

   private static List<DevMovementTest.Scenario> boxScenarios() {
      List<DevMovementTest.Scenario> list = new ArrayList<>();
      List<DevMovementTest.Box> boxes = labBoxes();

      for (int i = 0; i < boxes.size(); i++) {
         String name = boxes.get(i).name();
         double dx = labX(i) - -40 - 2;
         double dz = labZ(i) - 20 - 2;
         list.add(
            new DevMovementTest.Scenario(
               name + "-sides", 0, dx, 0.0, dz, 180.0F, List.of(), steps(12, "wr", 24, "wr@0", 4, "s", 14, "wr@270", 24, "wr@90", 26, "wr@225", 6, ""), false
            )
         );
         list.add(new DevMovementTest.Scenario(name + "-top", 0, dx, 0.0, dz, 180.0F, List.of(), steps(7, "w", 1, "wj", 16, "w", 10, ""), false));
         list.add(
            new DevMovementTest.Scenario(
               name + "-creep", 0, dx, 0.0, dz, 180.0F, List.of(), steps(40, "wc", 96, "wc@0", 44, "wc@270", 96, "wc@90", 10, ""), false
            )
         );
      }

      return list;
   }

   private static int[][] jumpProbes() {
      return new int[][]{
         {3, 4, 2},
         {4, 4, 3},
         {5, 4, 2},
         {6, 4, 2},
         {7, 10, 0},
         {1, 12, -1},
         {1, 12, -4},
         {1, 11, -1},
         {2, 15, -1},
         {2, 15, -4},
         {10, 6, -1},
         {10, 6, -2},
         {11, 8, 1},
         {12, 6, -1},
         {13, 6, -1},
         {14, 6, -1},
         {15, 6, 0},
         {16, 6, 0},
         {17, 4, 0},
         {17, 5, 0},
         {17, 9, 0},
         {17, 11, 0},
         {18, 6, -1},
         {19, 6, -1},
         {19, 6, 0}
      };
   }

   private static List<String> jumpCourse() {
      int z = 20;
      List<String> c = new ArrayList<>();
      c.add(fill(cx(18) - 1, 93, z + 4, cx(18) + 1, 104, z + 6, "air"));
      c.add(fill(-42, 99, 18, 42, 99, 64, "stone"));
      c.add(fill(-42, 100, 18, -13, 112, 64, "air"));
      c.add(fill(-12, 100, 18, 15, 112, 64, "air"));
      c.add(fill(16, 100, 18, 42, 112, 64, "air"));
      int x = cx(1);
      c.add(fill(x - 1, 99, z + 12, x + 1, 99, z + 13, "air"));
      c.add(fill(x - 1, 96, z + 12, x + 1, 96, z + 13, "stone"));
      x = cx(2);
      c.add(fill(x - 1, 99, z + 12, x + 1, 99, z + 15, "air"));
      c.add(fill(x - 1, 96, z + 12, x + 1, 96, z + 15, "stone"));
      x = cx(3);
      c.add(fill(x - 1, 102, z + 4, x + 1, 102, z + 18, "stone"));
      x = cx(4);
      c.add(fill(x - 1, 103, z + 4, x + 1, 103, z + 18, "stone"));
      x = cx(5);
      c.add(fill(x - 1, 102, z + 4, x + 1, 102, z + 18, "oak_slab [\"minecraft:vertical_half\"=\"top\"]"));
      x = cx(6);
      c.add(fill(x - 1, 102, z + 4, x + 1, 102, z + 18, "trapdoor [\"direction\"=0,\"open_bit\"=false,\"upside_down_bit\"=true]"));
      x = cx(7);
      c.add(fill(x - 1, 100, z + 10, x + 1, 104, z + 10, "stone"));
      x = cx(8) + 2;
      c.add(fill(x, 100, z + 4, x, 104, z + 40, "stone"));
      x = cx(9);
      c.add(fill(x - 1, 100, z + 4, x - 1, 101, z + 18, "stone"));
      c.add(fill(x + 1, 100, z + 4, x + 1, 101, z + 18, "stone"));
      c.add(fill(x - 1, 102, z + 4, x + 1, 102, z + 18, "stone"));
      x = cx(10);
      c.add(fill(x, 99, z + 6, x, 99, z + 6, "air"));
      c.add(fill(x, 98, z + 6, x, 98, z + 6, "stone"));
      c.add(fill(x - 2, 96, z + 13, x + 2, 99, z + 25, "stone"));
      c.add(fill(x - 1, 97, z + 14, x + 1, 99, z + 24, "water"));
      x = cx(11);

      for (int step = 0; step < 6; step++) {
         c.add(fill(x - 1, 100, z + 4 + 2 * step, x + 1, 100 + step, z + 5 + 2 * step, "stone"));
      }

      x = cx(12);
      c.add(fill(x - 1, 99, z, x + 1, 99, z + 40, "ice"));
      x = cx(13);
      c.add(fill(x - 1, 99, z, x + 1, 99, z + 40, "slime"));
      x = cx(14);
      c.add(fill(x - 1, 99, z + 3, x + 1, 99, z + 40, "soul_sand"));
      x = cx(15);
      c.add(fill(x - 1, 100, z + 5, x + 1, 101, z + 7, "web"));
      x = cx(16);
      c.add(fill(x - 1, 100, z + 4, x + 1, 100, z + 40, "carpet"));
      x = cx(17);

      for (int layer = 0; layer < 8; layer++) {
         c.add(fill(x - 1, 100, z + 4 + layer, x + 1, 100, z + 4 + layer, "snow_layer [\"height\"=" + layer + "]"));
      }

      x = cx(18);
      c.add(fill(x - 1, 99, z + 4, x + 1, 99, z + 40, "grass_path"));
      x = cx(19);
      c.add(fill(x - 1, 99, z, x + 1, 99, z + 40, "beacon"));
      c.add(fill(x - 1, 100, z, x + 1, 101, z + 40, "light_block [\"block_light_level\"=15]"));
      return c;
   }

   private void verifyJumpCourse() {
      this.minecraft.execute(() -> {
         for (int[] probe : jumpProbes()) {
            BlockPos pos = new BlockPos(cx(probe[0]), 100 + probe[2], 20 + probe[1]);
            BedrockNative.LOGGER.info("Dev jumps lab: lane {} at {}: {}", new Object[]{probe[0], pos, this.minecraft.level.getBlockState(pos)});
         }
      });
      sleep(500L);
   }

   private static DevMovementTest.Scenario jump(String name, int lane, float yaw, List<String> setup, List<DevMovementTest.Step> steps) {
      return new DevMovementTest.Scenario(name, lane, 0.0, 0.0, 0.0, yaw, setup, steps, false);
   }

   private static List<DevMovementTest.Scenario> jumpScenarios() {
      List<DevMovementTest.Scenario> list = new ArrayList<>();
      list.add(
         jump(
            "j-sprint-side",
            0,
            0.0F,
            List.of(),
            steps(10, "wr", 6, "wrja", 6, "wrjd", 6, "wrja", 6, "wrjd", 6, "wrja", 6, "wrjd", 6, "wrja", 6, "wrjd", 10, "wr", 20, "")
         )
      );
      list.add(
         jump("j-sprint-side-tap", 0, 0.0F, List.of(), steps(10, "wr", 1, "wrja", 11, "wra", 1, "wrjd", 11, "wrd", 1, "wrja", 11, "wra", 15, "wr", 20, ""))
      );
      list.add(jump("j-wtap", 0, 0.0F, List.of(), steps(12, "wr", 1, "wrj", 4, "wr", 3, "", 5, "wr", 8, "wr", 1, "wrj", 3, "wr", 3, "", 12, "wr", 10, "")));
      list.add(
         jump(
            "j-wtap-ground",
            0,
            0.0F,
            List.of(),
            steps(12, "wr", 1, "wrj", 14, "wr", 3, "", 2, "wr", 1, "wrj", 14, "wr", 3, "", 2, "wr", 1, "wrj", 14, "wr", 10, "")
         )
      );
      list.add(jump("j-sprint-stop-air", 0, 0.0F, List.of(), steps(12, "wr", 1, "wrj", 14, "", 30, "")));
      list.add(jump("j-sneak-air", 0, 0.0F, List.of(), steps(12, "wr", 1, "wrj", 3, "wr", 4, "wc", 6, "wr", 20, "wr", 10, "")));
      list.add(jump("j-sneak-jump", 0, 0.0F, List.of(), steps(10, "wc", 1, "wcj", 15, "wc", 1, "wcj", 15, "wc", 10, "")));
      list.add(jump("j-sneak-sprint", 0, 0.0F, List.of(), steps(12, "wr", 1, "wrj", 14, "wr", 5, "wc", 1, "wcj", 14, "wc", 5, "wr", 10, "")));
      list.add(jump("j-sprint-sneak-keys", 0, 0.0F, List.of(), steps(12, "wr", 20, "wrc", 12, "wr", 1, "wrj", 14, "wrc", 10, "wr", 10, "")));
      list.add(
         jump(
            "j-sneak-taps",
            0,
            0.0F,
            List.of(),
            steps(12, "wr", 1, "wrj", 4, "wr", 1, "wrc", 2, "wr", 1, "wrc", 6, "wr", 1, "wrj", 14, "wr", 3, "wc", 1, "wcj", 14, "wr", 10, "")
         )
      );
      list.add(jump("j-diagonal", 0, 45.0F, List.of(), steps(10, "war", 50, "warj", 10, "")));
      list.add(jump("j-back", 0, 0.0F, List.of(), steps(10, "s", 50, "sj", 10, "")));
      list.add(jump("j-strafe", 0, 0.0F, List.of(), steps(10, "a", 50, "aj", 10, "d", 50, "dj", 10, "")));
      list.add(jump("j-speed", 0, 0.0F, List.of("effect @s speed 30 1 true"), withClear(steps(10, "wr", 50, "wrj", 10, ""))));
      list.add(
         jump("j-speed-boost", 0, 0.0F, List.of("effect @s speed 30 1 true", "effect @s jump_boost 30 2 true"), withClear(steps(10, "wr", 50, "wrj", 10, "")))
      );
      list.add(jump("j-slowness", 0, 0.0F, List.of("effect @s slowness 30 1 true"), withClear(steps(10, "wr", 50, "wrj", 10, ""))));
      list.add(
         jump(
            "j-bow",
            0,
            0.0F,
            List.of("clear @s", "replaceitem entity @s slot.weapon.mainhand 0 bow", "give @s arrow 16"),
            steps(10, "wu", 25, "wju", 10, "w", 10, "")
         )
      );
      list.add(jump("j-gap2", 1, 0.0F, List.of(), steps(45, "wrj", 20, "")));
      list.add(jump("j-gap4", 2, 0.0F, List.of(), steps(45, "wrj", 20, "")));

      for (int late : new int[]{40, 43, 45, 46, 47, 48, 49, 51}) {
         list.add(jump("j-edge-walk-" + late, 1, 0.0F, List.of(), steps(late, "w", 1, "wj", 14, "", 8, "")));
      }

      for (int late : new int[]{34, 36, 37, 38, 39, 40, 41, 43}) {
         list.add(jump("j-edge-sprint-" + late, 1, 0.0F, List.of(), steps(late, "wr", 1, "wrj", 14, "", 8, "")));
      }

      for (int[] roof : new int[][]{{3, 20}, {4, 30}, {5, 25}, {6, 28}}) {
         String name = roof[0] == 3 ? "ceil2" : (roof[0] == 4 ? "ceil3" : (roof[0] == 5 ? "ceil-slab" : "ceil-trapdoor"));
         list.add(jump("j-" + name + "-stand", roof[0], 0.0F, List.of(), steps(16, "w", 8, "", 1, "j", 14, "", 1, "j", 14, "", 10, "w", 10, "")));
         list.add(jump("j-" + name + "-sprint", roof[0], 0.0F, List.of(), steps(12, "wr", 1, "wrj", 14, "wr", 1, "wrj", 14, "wr", 10, "")));
         list.add(jump("j-" + name + "-held", roof[0], 0.0F, List.of(), steps(6, "w", 50, "wj", 10, "")));
      }

      list.add(jump("j-tunnel-sprint", 9, 0.0F, List.of(), steps(8, "wr", 40, "wrj", 10, "")));
      list.add(jump("j-tunnel-stand", 9, 0.0F, List.of(), steps(16, "w", 1, "j", 12, "", 1, "j", 12, "", 10, "")));
      list.add(jump("j-wall-sprint", 7, 0.0F, List.of(), steps(30, "wr", 1, "wrj", 14, "wr", 1, "wrj", 14, "wr", 10, "")));
      list.add(jump("j-wall-held", 7, 0.0F, List.of(), steps(22, "wr", 50, "wrj", 10, "")));
      list.add(jump("j-wall-slide", 8, -20.0F, List.of(), steps(70, "wrj", 10, "")));
      list.add(jump("j-wall-slide-tap", 8, -20.0F, List.of(), steps(10, "wr", 1, "wrj", 14, "wr", 1, "wrj", 14, "wr", 1, "wrj", 14, "wr", 10, "")));
      list.add(jump("j-blocks-up", 11, 0.0F, List.of(), steps(6, "wr", 90, "wrj", 10, "")));
      list.add(new DevMovementTest.Scenario("j-hole-exit", 10, 0.0, -1.0, 4.0, 0.0F, List.of(), steps(10, "", 30, "wj", 10, ""), false));
      list.add(
         new DevMovementTest.Scenario(
            "j-swim-dive", 10, 0.0, -2.0, 14.0, 0.0F, List.of(), steps(10, "wr", 4, "wrc", 10, "wr", 3, "wrc", 2, "wr", 10, "wr", 6, ""), false
         )
      );
      list.add(
         new DevMovementTest.Scenario("j-swim-bottom", 10, 0.0, -3.0, 14.0, 0.0F, List.of(), steps(8, "wr", 8, "wc", 10, "wr", 6, "wrc", 8, "wr", 6, ""), false)
      );
      list.add(
         new DevMovementTest.Scenario(
            "j-swim-bottom-restart",
            10,
            0.0,
            -3.0,
            14.0,
            0.0F,
            List.of(),
            steps(6, "c", 8, "wc", 8, "wcr", 6, "c", 8, "wr", 4, "", 6, "wr", 3, "c", 3, "wr", 6, ""),
            false
         )
      );
      list.add(jump("j-ice", 12, 0.0F, List.of(), steps(10, "wr", 60, "wrj", 20, "")));
      list.add(jump("j-slime", 13, 0.0F, List.of(), steps(10, "wr", 60, "wrj", 20, "")));
      list.add(jump("j-soul-sand", 14, 0.0F, List.of(), steps(10, "w", 60, "wj", 10, "")));
      list.add(jump("j-web", 15, 0.0F, List.of(), steps(15, "w", 30, "wj", 20, "")));
      list.add(jump("j-carpet", 16, 0.0F, List.of(), steps(10, "wr", 50, "wrj", 10, "")));
      list.add(jump("j-snow", 17, 0.0F, List.of(), steps(10, "w", 50, "wj", 10, "")));
      list.add(jump("j-snow-sprint", 17, 0.0F, List.of(), steps(6, "wr", 40, "wrj", 10, "")));
      list.add(jump("j-path", 18, 0.0F, List.of(), steps(10, "wr", 50, "wrj", 10, "")));
      list.add(jump("j-beacon", 19, 0.0F, List.of(), steps(10, "wr", 60, "wrj", 10, "")));
      list.add(jump("j-beacon-stand", 19, 0.0F, List.of(), steps(10, "", 1, "j", 14, "", 1, "j", 14, "", 1, "j", 14, "")));
      return list;
   }

   private static String fill(int x1, int y1, int z1, int x2, int y2, int z2, String block) {
      return "fill " + x1 + " " + y1 + " " + z1 + " " + x2 + " " + y2 + " " + z2 + " " + block;
   }

   private void waitUntilSettled() {
      int still = 0;
      long started = System.currentTimeMillis();

      while (still < 10 && System.currentTimeMillis() - started < 5000L) {
         sleep(50L);
         CompletableFuture<Boolean> settled = new CompletableFuture<>();
         this.minecraft.execute(() -> {
            LocalPlayer player = this.minecraft.player;
            settled.complete(player.onGround() && player.getDeltaMovement().horizontalDistance() < 1.0E-4);
         });
         still = settled.join() ? still + 1 : 0;
      }
   }

   private void commands(List<String> commands) {
      for (String command : commands) {
         this.command(command);
      }
   }

   private void command(String command) {
      this.minecraft.execute(() -> this.minecraft.getConnection().sendCommand(command));
      sleep(200L);
   }

   private static String format(Vec3 vec) {
      return String.format(Locale.ROOT, "(%.4f, %.4f, %.4f)", vec.x, vec.y, vec.z);
   }

   private static String precise(Vec3 vec) {
      return String.format(Locale.ROOT, "(%.6f, %.6f, %.6f)", vec.x, vec.y, vec.z);
   }

   private static void sleep(long millis) {
      try {
         Thread.sleep(millis);
      } catch (InterruptedException var3) {
      }
   }

   private record Box(String name, String ns, String nsPair, String ew, String ewPair) {
      static DevMovementTest.Box single(String name, String block) {
         return new DevMovementTest.Box(name, block, null, block, null);
      }
   }

   private record Scenario(
      String name, int lane, double dx, double dy, double dz, float yaw, List<String> setup, List<DevMovementTest.Step> steps, boolean airborne
   ) {
      Scenario(String name, int lane, double dy, float yaw, List<String> setup, List<DevMovementTest.Step> steps, boolean airborne) {
         this(name, lane, 0.0, dy, 0.0, yaw, setup, steps, airborne);
      }

      int ticks() {
         return this.steps.stream().mapToInt(DevMovementTest.Step::ticks).sum();
      }
   }

   private static final class Script {
      final List<DevMovementTest.Step> steps;
      final MovementStats stats;
      final long startTick;
      final CompletableFuture<Void> done = new CompletableFuture<>();
      int step;
      int tickInStep;

      Script(List<DevMovementTest.Step> steps, MovementStats stats, long startTick) {
         this.steps = steps;
         this.stats = stats;
         this.startTick = startTick;
      }

      void tick(Minecraft minecraft) {
         if (minecraft.player != null && this.step < this.steps.size()) {
            DevMovementTest.Step current = this.steps.get(this.step);
            if (Boolean.getBoolean("bedrocknative.devTrace")) {
               LocalPlayer p = minecraft.player;
               Vec3 sent = this.stats.sentPosition(this.stats.lastTick());
               BedrockNative.LOGGER
                  .info(
                     "TRACE after +{} sent {} pos {} v {} ground {} hc {} climb {}",
                     new Object[]{
                        this.stats.lastTick() - this.startTick,
                        sent == null ? "?" : DevMovementTest.precise(sent),
                        DevMovementTest.precise(p.position()),
                        DevMovementTest.precise(p.getDeltaMovement())
                           + " crouch "
                           + p.isCrouching()
                           + " sprint "
                           + p.isSprinting()
                           + " swim "
                           + p.isSwimming()
                           + " pose "
                           + p.getPose()
                           + " fly "
                           + p.getAbilities().flying
                           + " mayfly "
                           + p.getAbilities().mayfly
                           + " noPhys "
                           + p.noPhysics,
                        p.onGround(),
                        p.horizontalCollision,
                        p.onClimbable()
                     }
                  );
            }

            if (this.tickInStep == 0 && current.command() != null) {
               minecraft.getConnection().sendCommand(current.command());
            }

            Options options = minecraft.options;
            String keys = current.keys();
            int turn = keys.indexOf(64);
            if (turn >= 0) {
               if (this.tickInStep == 0) {
                  minecraft.player.setYRot(Float.parseFloat(keys.substring(turn + 1)));
               }

               keys = keys.substring(0, turn);
            }

            options.keyUp.setDown(keys.contains("w"));
            options.keyLeft.setDown(keys.contains("a"));
            options.keyDown.setDown(keys.contains("s"));
            options.keyRight.setDown(keys.contains("d"));
            options.keyJump.setDown(keys.contains("j"));
            options.keyShift.setDown(keys.contains("c"));
            options.keySprint.setDown(keys.contains("r"));
            options.keyUse.setDown(keys.contains("u"));
            if (keys.contains("f")) {
               minecraft.player.getAbilities().flying = true;
               minecraft.player.onUpdateAbilities();
            }

            if (keys.contains("k") && minecraft.hitResult instanceof EntityHitResult hit) {
               BedrockNative.LOGGER.info("Dev movement: attacking {}", hit.getEntity());
               minecraft.gameMode.attack(minecraft.player, hit.getEntity());
            }

            if (++this.tickInStep >= current.ticks()) {
               this.step++;
               this.tickInStep = 0;
            }
         } else {
            release(minecraft.options);
            this.done.complete(null);
         }
      }

      private static void release(Options options) {
         options.keyUp.setDown(false);
         options.keyLeft.setDown(false);
         options.keyDown.setDown(false);
         options.keyRight.setDown(false);
         options.keyJump.setDown(false);
         options.keyShift.setDown(false);
         options.keySprint.setDown(false);
         options.keyUse.setDown(false);
      }
   }

   private record Step(int ticks, String keys, String command) {
   }
}
