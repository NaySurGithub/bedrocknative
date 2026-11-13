package net.bedrocknative.client.net;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.movement.BedrockVehicles;
import net.bedrocknative.client.world.ActorOffsets;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.StartTick;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.Screenshot;
import net.minecraft.commands.arguments.EntityAnchorArgument.Anchor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;

final class DevVehiclesTest {
   private static final int VX = -40;
   private static final int VY = 100;
   private static final int VZ = 80;
   private static final int LENGTH = 100;
   private static volatile DevVehiclesTest.Script running;
   private final Minecraft minecraft;
   private final MovementStats stats;
   private final List<String> results = new ArrayList<>();
   private int vehicleCorrections;
   private volatile long rowOrigin = Long.MIN_VALUE;
   private volatile String rowScenario = "";

   DevVehiclesTest(Minecraft minecraft, MovementStats stats) {
      this.minecraft = minecraft;
      this.stats = stats;
   }

   void run() {
      BedrockVehicles.rowTimeListener = (left, right) -> {
         if (this.rowOrigin != Long.MIN_VALUE) {
            BedrockNative.LOGGER
               .info("Dev vehicles {}: row +{} left {} right {}", new Object[]{this.rowScenario, this.stats.lastTick() - this.rowOrigin, left, right});
         }
      };
      ClientTickEvents.START_CLIENT_TICK.register((StartTick)client -> {
         DevVehiclesTest.Script script = running;
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
            "gamerule domobspawning false",
            "tp @s -36 102 124",
            "effect @s slow_falling 30 0 true"
         )
      );
      sleep(3000L);
      this.commands(List.of("tickingarea remove bn_vehicles", "tickingarea add -42 100 77 60 100 156 bn_vehicles true"));
      sleep(6000L);
      this.commands(course());
      sleep(2000L);
      this.commands(List.of("effect @s clear", "effect @s saturation 9999 255 true", "effect @s resistance 9999 255 true"));
      BedrockNative.LOGGER.info("Dev vehicles water at the course height: {}", this.supply(this::waterAtCourseHeight));
      String test = System.getProperty("bedrocknative.devTest", "");
      List<String> only = test.contains(":") ? List.of(test.substring(test.indexOf(58) + 1).split(",")) : List.of();
      this.commands(List.of("scriptevent bedrocknative:vtrace on"));
      if (only.contains("waveprobe")) {
         this.commands(List.of("scriptevent bedrocknative:waveprobe -26 " + pool()));
         sleep(90000L);
         this.commands(List.of("scriptevent bedrocknative:vtrace off", "kill @e[type=boat]", "tickingarea remove bn_vehicles"));
         BedrockNative.LOGGER.info("Dev vehicles test: done");
         this.minecraft.execute(this.minecraft::stop);
      } else {
         for (DevVehiclesTest.Scenario scenario : scenarios()) {
            if (only.isEmpty() || only.contains(scenario.name())) {
               this.runScenario(scenario);
            }
         }

         this.commands(List.of("scriptevent bedrocknative:vtrace off"));
         BedrockNative.LOGGER
            .info(
               "Dev vehicles results ({} scenarios, {} vehicle corrections, {} player corrections in total):\n{}",
               new Object[]{this.results.size(), this.vehicleCorrections, this.stats.count(), String.join("\n", this.results)}
            );
         this.commands(
            List.of(
               "kill @e[type=boat]", "kill @e[type=horse]", "kill @e[type=pig]", "kill @e[type=minecart]", "effect @s clear", "tickingarea remove bn_vehicles"
            )
         );
         BedrockNative.LOGGER.info("Dev vehicles test: done");
         this.minecraft.execute(this.minecraft::stop);
      }
   }

   private void runScenario(DevVehiclesTest.Scenario scenario) {
      List<String> setup = new ArrayList<>(
         List.of(
            "kill @e[type=boat]",
            "kill @e[type=horse]",
            "kill @e[type=pig]",
            "kill @e[type=minecart]",
            String.format(Locale.ROOT, "tp @s %.2f %.2f %.2f", scenario.x(), scenario.y(), scenario.z())
         )
      );
      setup.addAll(scenario.setup());
      this.commands(setup);
      sleep(1500L);
      if (!this.board(scenario.type())) {
         this.results.add(String.format(Locale.ROOT, "| %s | %d | not boarded | - | - | - | - |", scenario.name(), scenario.ticks()));
         BedrockNative.LOGGER.info("Dev vehicles {}: FAILED to board a {}", scenario.name(), scenario.type());
      } else {
         this.commands(scenario.afterBoarding());
         BedrockNative.LOGGER.info("Dev vehicles {}: fluids around the start {}", scenario.name(), this.supply(this::fluidsAround));
         this.minecraft.execute(() -> {
            this.minecraft.player.setYRot(-90.0F);
            this.minecraft.player.setXRot(0.0F);
         });
         sleep(1500L);
         long startTick = this.stats.lastTick();
         this.rowScenario = scenario.name();
         this.rowOrigin = startTick;
         int playerBefore = this.stats.count();
         Vec3 start = this.supply(this::vehiclePosition);
         DevVehiclesTest.Script script = new DevVehiclesTest.Script(scenario.name(), scenario.steps(), this.minecraft);
         running = script;
         script.done.join();
         running = null;
         sleep(1500L);
         this.rowOrigin = Long.MIN_VALUE;
         Vec3 end = this.supply(this::vehiclePosition);
         List<MovementStats.VehicleCorrection> corrections = this.stats.vehicleCorrectionsSince(startTick);
         List<MovementStats.Correction> playerCorrections = this.stats.since(startTick);
         this.vehicleCorrections = this.vehicleCorrections + corrections.size();
         String line;
         if (corrections.isEmpty()) {
            line = String.format(
               Locale.ROOT, "| %s | %d | 0 | %d | - | - | %s |", scenario.name(), scenario.ticks(), playerCorrections.size(), format(end.subtract(start))
            );
         } else {
            BedrockVehicles.Correction first = corrections.getFirst().correction();
            line = String.format(
               Locale.ROOT,
               "| %s | %d | %d | %d | +%d (recv +%d) | server %s ours %s, %.4f (dv server %s ours %s) | %s |",
               scenario.name(),
               scenario.ticks(),
               corrections.size(),
               playerCorrections.size(),
               first.tick() - startTick,
               corrections.getFirst().receivedAt() - startTick,
               format(first.server().subtract(start)),
               first.client() == null ? "?" : format(first.client().subtract(start)),
               first.distance(),
               format(first.serverDelta()),
               first.clientDelta() == null ? "?" : format(first.clientDelta()),
               format(end.subtract(start))
            );
         }

         for (MovementStats.VehicleCorrection details : corrections.subList(0, Math.min(24, corrections.size()))) {
            BedrockVehicles.Correction correction = details.correction();
            BedrockNative.LOGGER
               .info(
                  "Dev vehicles {}: correction +{} (recv +{}) server {} dv {} yaw {} turn {} ground {} | ours {} dv {} yaw {} turn {} | {}",
                  new Object[]{
                     scenario.name(),
                     correction.tick() - startTick,
                     details.receivedAt() - startTick,
                     precise(correction.server()),
                     precise(correction.serverDelta()),
                     correction.serverYaw(),
                     correction.serverAngularVelocity(),
                     correction.serverOnGround(),
                     correction.client() == null ? "?" : precise(correction.client()),
                     correction.clientDelta() == null ? "?" : precise(correction.clientDelta()),
                     correction.clientYaw(),
                     correction.clientAngularVelocity(),
                     String.format(Locale.ROOT, "%.6f", correction.distance())
                  }
               );
         }

         if (this.stats.count() > playerBefore) {
            for (MovementStats.Correction correction : playerCorrections.subList(0, Math.min(6, playerCorrections.size()))) {
               BedrockNative.LOGGER
                  .info(
                     "Dev vehicles {}: player correction +{} server {} ours {} dv server {} ground {}",
                     new Object[]{
                        scenario.name(),
                        correction.tick() - startTick,
                        precise(correction.server()),
                        correction.client() == null ? "?" : precise(correction.client()),
                        precise(correction.serverDelta()),
                        correction.serverOnGround()
                     }
                  );
            }
         }

         this.results.add(line);
         BedrockNative.LOGGER.info("Dev vehicles: {}", line);
         this.leave();
      }
   }

   private static void state(Minecraft client, String name) {
      Entity vehicle = client.player.getVehicle();
      PlayerAuthInputPacket input = PlayerInputSender.lastBuilt;
      String reported = "?";
      if (input != null && vehicle != null) {
         Vector3f position = input.getPosition();
         double vehicleY = vehicle.getY() + ActorOffsets.move(vehicle);
         reported = String.format(
            Locale.ROOT,
            "input at (%.5f, %.5f, %.5f), %.5f above the vehicle's Bedrock position, flags %s",
            position.getX(),
            position.getY(),
            position.getZ(),
            position.getY() - vehicleY,
            input.getInputData()
         );
      }

      BedrockNative.LOGGER
         .info(
            "Dev vehicles {}: state {}, vehicle {} at {}, {}",
            new Object[]{
               name,
               BedrockVehicles.describe(),
               vehicle == null ? "none" : vehicle.typeHolder().getRegisteredName(),
               vehicle == null ? "-" : precise(vehicle.position()),
               reported
            }
         );
   }

   private static List<DevVehiclesTest.Scenario> scenarios() {
      List<DevVehiclesTest.Scenario> list = new ArrayList<>();
      double boatX = -34.5;
      list.add(
         new DevVehiclesTest.Scenario(
            "boat-land",
            EntityTypes.OAK_BOAT,
            boatX - 2.0,
            100.0,
            lane(0) + 0.5,
            List.of(boat(boatX, lane(0) + 0.5)),
            List.of(),
            steps(10, "", 30, "w", 1, "wp", 15, "", 20, "a", 20, "d", 15, "s", 10, "")
         )
      );
      list.add(
         new DevVehiclesTest.Scenario(
            "boat-water",
            EntityTypes.OAK_BOAT,
            boatX,
            100.0,
            lane(1) - 2.5,
            List.of(boat(boatX, lane(1) + 0.5)),
            List.of(),
            steps(20, "", 60, "w", 1, "wp", 25, "")
         )
      );
      list.add(
         new DevVehiclesTest.Scenario(
            "boat-water-turn",
            EntityTypes.OAK_BOAT,
            -27.5,
            100.0,
            pool() - 8.5,
            List.of(boat(-27.5, pool() - 6.5)),
            List.of(),
            steps(20, "", 10, "w", 30, "wa", 1, "wap", 20, "d", 20, "", 20, "s", 10, "")
         )
      );
      list.add(
         new DevVehiclesTest.Scenario(
            "boat-water-east2",
            EntityTypes.OAK_BOAT,
            5.5,
            100.0,
            lane(1) - 2.5,
            List.of(boat(5.5, lane(1) + 0.5)),
            List.of(),
            steps(20, "", 60, "w", 1, "wp", 25, "")
         )
      );
      list.add(
         new DevVehiclesTest.Scenario(
            "boat-water-south",
            EntityTypes.OAK_BOAT,
            -19.5,
            100.0,
            pool() - 8.5,
            List.of(boatFacing(-19.5, pool() - 6.5, 90)),
            List.of(),
            steps(20, "", 28, "w", 1, "wp", 25, "")
         )
      );
      list.add(
         new DevVehiclesTest.Scenario(
            "boat-ice",
            EntityTypes.OAK_BOAT,
            boatX - 2.0,
            100.0,
            lane(2) + 0.5,
            List.of(boat(boatX, lane(2) + 0.5)),
            List.of(),
            steps(10, "", 30, "w", 1, "wp", 20, "")
         )
      );
      list.add(
         new DevVehiclesTest.Scenario(
            "boat-blue-ice",
            EntityTypes.OAK_BOAT,
            boatX - 2.0,
            100.0,
            lane(3) + 0.5,
            List.of(boat(boatX, lane(3) + 0.5)),
            List.of(),
            steps(10, "", 25, "w", 1, "wp", 20, "")
         )
      );
      list.add(
         new DevVehiclesTest.Scenario(
            "boat-onto-land",
            EntityTypes.OAK_BOAT,
            -31.5,
            100.0,
            lane(4) - 2.5,
            List.of(boat(-31.5, lane(4) + 0.5)),
            List.of(),
            steps(20, "", 45, "w", 1, "wp", 25, "")
         )
      );
      list.add(
         new DevVehiclesTest.Scenario(
            "boat-land-wa",
            EntityTypes.OAK_BOAT,
            -5.5,
            100.0,
            field() + 0.5,
            List.of(boat(-3.5, field() + 0.5)),
            List.of(),
            steps(10, "", 40, "wa", 1, "wap", 20, "")
         )
      );
      list.add(
         new DevVehiclesTest.Scenario(
            "boat-land-wd",
            EntityTypes.OAK_BOAT,
            -33.5,
            100.0,
            field() + 12.5,
            List.of(boat(-31.5, field() + 12.5)),
            List.of(),
            steps(10, "", 40, "wd", 1, "wdp", 20, "")
         )
      );
      list.add(
         new DevVehiclesTest.Scenario(
            "boat-land-reverse",
            EntityTypes.OAK_BOAT,
            -19.5,
            100.0,
            field() + 0.5,
            List.of(boat(-17.5, field() + 0.5)),
            List.of(),
            steps(10, "", 20, "s", 20, "sa", 20, "sd", 1, "sp", 15, "")
         )
      );
      list.add(
         new DevVehiclesTest.Scenario(
            "boat-water-spin",
            EntityTypes.OAK_BOAT,
            -27.5,
            100.0,
            pool() - 1.5,
            List.of(boat(-27.5, pool() + 0.5)),
            List.of(),
            steps(20, "", 60, "a", 1, "ap", 40, "d", 20, "")
         )
      );
      list.add(
         new DevVehiclesTest.Scenario(
            "boat-water-wa",
            EntityTypes.OAK_BOAT,
            -27.5,
            100.0,
            pool() - 1.5,
            List.of(boat(-27.5, pool() + 0.5)),
            List.of(),
            steps(20, "", 80, "wa", 1, "wap", 20, "")
         )
      );
      list.add(
         new DevVehiclesTest.Scenario(
            "boat-ice-turn",
            EntityTypes.OAK_BOAT,
            boatX - 2.0,
            100.0,
            lane(2) + 0.5,
            List.of(boat(boatX, lane(2) + 0.5)),
            List.of(),
            steps(10, "", 12, "a", 1, "ap", 30, "", 12, "w", 6, "wd", 1, "wdp", 20, "")
         )
      );
      list.add(
         new DevVehiclesTest.Scenario(
            "boat-water-reverse",
            EntityTypes.OAK_BOAT,
            -27.5,
            100.0,
            pool() - 1.5,
            List.of(boat(-27.5, pool() + 0.5)),
            List.of(),
            steps(20, "", 25, "s", 25, "sa", 25, "sd", 1, "sp", 20, "")
         )
      );
      list.add(
         new DevVehiclesTest.Scenario(
            "minecart-loop",
            EntityTypes.MINECART,
            22.5,
            100.0,
            lane(5) - 3.5,
            List.of("summon minecart 22.5 100 " + (lane(5) - 1.5)),
            List.of(),
            steps(20, "w", 60, "", 1, "p", 60, "")
         )
      );
      String horse = "summon horse -33.5 100 " + (field() + 0.5) + " 0 0 minecraft:spawn_tame_adult";
      List<String> horseSetup = List.of(
         horse, "replaceitem entity @e[type=horse,c=1] slot.saddle 0 saddle", "event entity @e[type=horse] minecraft:horse_saddled"
      );
      list.add(
         new DevVehiclesTest.Scenario(
            "horse-walk", EntityTypes.HORSE, -35.5, 100.0, field() + 0.5, horseSetup, List.of(), steps(10, "", 40, "w", 1, "wp", 15, "", 20, "wa", 10, "")
         )
      );
      list.add(
         new DevVehiclesTest.Scenario(
            "horse-jump", EntityTypes.HORSE, -35.5, 100.0, field() + 0.5, horseSetup, List.of(), steps(10, "", 10, "w", 12, "wj", 1, "wp", 30, "w", 15, "")
         )
      );
      list.add(
         new DevVehiclesTest.Scenario(
            "pig-ride",
            EntityTypes.PIG,
            -35.5,
            100.0,
            field() + 6.5,
            List.of("summon pig -33.5 100 " + (field() + 6.5) + " 0 0 minecraft:on_saddled"),
            List.of("replaceitem entity @s slot.weapon.mainhand 0 carrot_on_a_stick"),
            steps(20, "", 30, "w", 1, "p", 30, "")
         )
      );
      return list;
   }

   private static String boat(double x, double z) {
      return String.format(Locale.ROOT, "summon boat %.2f %d %.2f 0 0", x, 100, z);
   }

   private static String boatFacing(double x, double z, int yaw) {
      return String.format(Locale.ROOT, "summon boat %.2f %d %.2f %d 0", x, 100, z, yaw);
   }

   private static List<DevVehiclesTest.Step> steps(Object... ticksAndKeys) {
      List<DevVehiclesTest.Step> steps = new ArrayList<>();

      for (int i = 0; i < ticksAndKeys.length; i += 2) {
         steps.add(new DevVehiclesTest.Step((Integer)ticksAndKeys[i], (String)ticksAndKeys[i + 1]));
      }

      return steps;
   }

   private static int lane(int index) {
      return 80 + 6 * index;
   }

   private static int field() {
      return 120;
   }

   private static int pool() {
      return 144;
   }

   private static List<String> course() {
      List<String> c = new ArrayList<>();
      int x0 = -42;
      int x1 = 60;

      for (int z = 77; z <= 156; z += 8) {
         c.add(fill(x0, 100, z, x1, 101, Math.min(z + 7, 156), "stone"));
      }

      for (int z = 77; z <= 156; z += 8) {
         c.add(fill(x0, 100, z, x1, 108, Math.min(z + 7, 156), "air"));
      }

      c.add(fill(x0, 99, 77, x1, 99, 132, "stone"));
      basin(c, lane(1), x0, x1);
      c.add(fill(x0, 99, lane(2) - 2, x1, 99, lane(2) + 2, "packed_ice"));
      c.add(fill(x0, 99, lane(3) - 2, x1, 99, lane(3) + 2, "blue_ice"));
      basin(c, lane(4), x0, -20);
      c.add(fill(-19, 98, lane(4) - 2, x1, 98, lane(4) + 2, "stone"));
      c.add(fill(-19, 99, lane(4) - 2, x1, 99, lane(4) + 2, "oak_slab"));
      rails(c);
      c.add(fill(-42, 96, pool() - 9, -10, 99, pool() + 9, "stone"));
      c.add(fill(-41, 97, pool() - 8, -11, 99, pool() + 8, "water"));
      return c;
   }

   private static void basin(List<String> c, int z, int x0, int x1) {
      c.add(fill(x0, 96, z - 3, x1, 99, z + 3, "stone"));
      c.add(fill(x0 + 1, 97, z - 2, x1 - 1, 99, z + 2, "water"));
   }

   private static void rails(List<String> c) {
      int x0 = 12;
      int x1 = 50;
      int z0 = lane(5) - 2;
      int z1 = lane(5) + 2;
      c.add(fill(x0 + 1, 100, z0, x1 - 1, 100, z0, "rail [\"rail_direction\"=1]"));
      c.add(fill(x0 + 1, 100, z1, x1 - 1, 100, z1, "rail [\"rail_direction\"=1]"));
      c.add(fill(x0, 100, z0 + 1, x0, 100, z1 - 1, "rail [\"rail_direction\"=0]"));
      c.add(fill(x1, 100, z0 + 1, x1, 100, z1 - 1, "rail [\"rail_direction\"=0]"));
      c.add("setblock " + x0 + " 100 " + z0 + " rail [\"rail_direction\"=6]");
      c.add("setblock " + x1 + " 100 " + z0 + " rail [\"rail_direction\"=7]");
      c.add("setblock " + x1 + " 100 " + z1 + " rail [\"rail_direction\"=8]");
      c.add("setblock " + x0 + " 100 " + z1 + " rail [\"rail_direction\"=9]");

      for (int x = x0 + 6; x < x1 - 2; x += 10) {
         for (int z : new int[]{z0, z1}) {
            c.add("setblock " + x + " 99 " + z + " redstone_block");
            c.add("setblock " + x + " 100 " + z + " golden_rail [\"rail_direction\"=1,\"rail_data_bit\"=true]");
         }
      }
   }

   private static String fill(int x1, int y1, int z1, int x2, int y2, int z2, String block) {
      return "fill " + x1 + " " + y1 + " " + z1 + " " + x2 + " " + y2 + " " + z2 + " " + block;
   }

   private String waterAtCourseHeight() {
      StringBuilder out = new StringBuilder();
      int count = 0;

      for (int y = 100; y <= 103; y++) {
         for (int x = -42; x <= 60; x++) {
            for (int z = 77; z <= 156; z++) {
               BlockPos pos = new BlockPos(x, y, z);
               if (!this.minecraft.level.getFluidState(pos).isEmpty() && count++ < 12) {
                  out.append(pos.toShortString()).append("=").append(this.minecraft.level.getFluidState(pos).getAmount()).append(" ");
               }
            }
         }
      }

      return count + " cells " + out;
   }

   private String fluidsAround() {
      Entity vehicle = this.minecraft.player.getVehicle();
      if (vehicle == null) {
         return "no vehicle";
      }

      BlockPos base = vehicle.blockPosition();
      StringBuilder out = new StringBuilder();

      for (int dy = -1; dy <= 0; dy++) {
         for (int dx = -3; dx <= 3; dx += 3) {
            for (int dz = -16; dz <= 3; dz++) {
               BlockPos pos = base.offset(dx, dy, dz);
               FluidState fluid = this.minecraft.level.getFluidState(pos);
               if (!fluid.isEmpty()) {
                  out.append(pos.toShortString()).append("=").append(fluid.getAmount()).append(fluid.isSource() ? "s " : " ");
               }
            }
         }
      }

      return out.length() == 0 ? "none" : out.toString();
   }

   private Vec3 vehiclePosition() {
      Entity vehicle = this.minecraft.player.getVehicle();
      return vehicle == null ? this.minecraft.player.position() : vehicle.position().add(0.0, ActorOffsets.move(vehicle), 0.0);
   }

   private boolean board(EntityType<?> type) {
      for (int attempt = 0; attempt < 3; attempt++) {
         this.minecraft
            .execute(
               () -> this.minecraft
                  .level
                  .getEntitiesOfClass(Entity.class, this.minecraft.player.getBoundingBox().inflate(6.0), entity -> entity.getType() == type)
                  .stream()
                  .findFirst()
                  .ifPresent(
                     vehicle -> {
                        this.minecraft.player.lookAt(Anchor.EYES, vehicle.getBoundingBox().getCenter());
                        this.minecraft
                           .gameMode
                           .interact(
                              this.minecraft.player, vehicle, new EntityHitResult(vehicle, vehicle.getBoundingBox().getCenter()), InteractionHand.MAIN_HAND
                           );
                     }
                  )
            );
         this.waitFor(() -> this.minecraft.player.isPassenger(), 3000L);
         if (this.supply(() -> this.minecraft.player.isPassenger())) {
            return true;
         }
      }

      return false;
   }

   private void leave() {
      this.minecraft.execute(() -> this.minecraft.options.keyShift.setDown(true));
      sleep(150L);
      this.minecraft.execute(() -> this.minecraft.options.keyShift.setDown(false));
      sleep(1000L);
      this.minecraft.execute(() -> {
         if (this.minecraft.player.getInventory().getSelectedItem().is(Items.CARROT_ON_A_STICK)) {
            this.minecraft.getConnection().sendCommand("clear @s carrot_on_a_stick");
         }
      });
      sleep(500L);
   }

   private void waitFor(BooleanSupplier condition, long timeout) {
      long end = System.currentTimeMillis() + timeout;

      while (System.currentTimeMillis() < end && !this.supply(condition::getAsBoolean)) {
         sleep(100L);
      }
   }

   private <T> T supply(Supplier<T> value) {
      return CompletableFuture.supplyAsync(value, this.minecraft::execute).join();
   }

   private void commands(List<String> commands) {
      for (String command : commands) {
         this.minecraft.execute(() -> this.minecraft.getConnection().sendCommand(command));
         sleep(200L);
      }
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

   private record Scenario(
      String name, EntityType<?> type, double x, double y, double z, List<String> setup, List<String> afterBoarding, List<DevVehiclesTest.Step> steps
   ) {
      int ticks() {
         return this.steps.stream().mapToInt(DevVehiclesTest.Step::ticks).sum();
      }
   }

   private static final class Script {
      final String name;
      final List<DevVehiclesTest.Step> steps;
      final Minecraft minecraft;
      final CompletableFuture<Void> done = new CompletableFuture<>();
      int step;
      int tickInStep;

      Script(String name, List<DevVehiclesTest.Step> steps, Minecraft minecraft) {
         this.name = name;
         this.steps = steps;
         this.minecraft = minecraft;
      }

      void tick(Minecraft client) {
         Options options = client.options;
         if (client.player != null && this.step < this.steps.size()) {
            String keys = this.steps.get(this.step).keys();
            options.keyUp.setDown(keys.contains("w"));
            options.keyLeft.setDown(keys.contains("a"));
            options.keyDown.setDown(keys.contains("s"));
            options.keyRight.setDown(keys.contains("d"));
            options.keyJump.setDown(keys.contains("j"));
            if (keys.contains("p") && this.tickInStep == 0) {
               Screenshot.grab(client.gameDirectory, "vehicles-" + this.name + ".png", client.gameRenderer.mainRenderTarget(), 1, message -> {});
               DevVehiclesTest.state(client, this.name);
            }

            if (++this.tickInStep >= this.steps.get(this.step).ticks()) {
               this.step++;
               this.tickInStep = 0;
            }
         } else {
            release(options);
            this.done.complete(null);
         }
      }

      private static void release(Options options) {
         options.keyUp.setDown(false);
         options.keyLeft.setDown(false);
         options.keyDown.setDown(false);
         options.keyRight.setDown(false);
         options.keyJump.setDown(false);
      }
   }

   private record Step(int ticks, String keys) {
   }
}
