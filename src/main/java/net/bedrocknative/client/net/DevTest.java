package net.bedrocknative.client.net;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.form.BedrockForms;
import net.bedrocknative.client.inventory.StackRequests;
import net.bedrocknative.client.world.BedrockParticles;
import net.bedrocknative.client.world.BedrockSounds;
import net.bedrocknative.net.client.WindowCloseGuard;
import net.bedrocknative.world.chunk.cache.CachedChunkTransformer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.arguments.EntityAnchorArgument.Anchor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;

final class DevTest {
   private static final List<String> COMMANDS = List.of(
      "list", "me checks emotes", "time set noon", "weather rain", "setblock ~ ~-1 ~ gold_block", "gamemode creative"
   );
   private static final List<String> INTERACTION_SETUP = List.of(
      "gamemode survival",
      "clear @s",
      "give @s diamond_pickaxe",
      "give @s dirt 16",
      "give @s diamond_sword",
      "fill ~1 ~ ~-1 ~3 ~2 ~2 air",
      "setblock ~2 ~-1 ~ stone",
      "setblock ~2 ~ ~ stone",
      "setblock ~2 ~-1 ~1 stone",
      "setblock ~2 ~ ~1 trapdoor",
      "kill @e[type=pig]"
   );
   private final Minecraft minecraft;
   private final MovementStats movement;
   private final IntSupplier corrections;
   private final IntSupplier blockUpdates;
   private final IntSupplier crackEvents;
   private final StackRequests requests;
   private final BedrockSounds sounds;
   private final BedrockParticles particles;
   private final BedrockForms forms;
   private final IntSupplier blockEvents;
   private final IntSupplier bossBars;
   private final Consumer<BedrockPacket> inject;
   private final Consumer<BedrockPacket> send;
   private final Supplier<CachedChunkTransformer.Summary> blobCache;
   private final Supplier<WindowCloseGuard> closeGuard;

   DevTest(
      Minecraft minecraft,
      MovementStats movement,
      IntSupplier blockUpdates,
      IntSupplier crackEvents,
      StackRequests requests,
      BedrockSounds sounds,
      BedrockParticles particles,
      BedrockForms forms,
      IntSupplier blockEvents,
      IntSupplier bossBars,
      Consumer<BedrockPacket> inject,
      Consumer<BedrockPacket> send,
      Supplier<CachedChunkTransformer.Summary> blobCache,
      Supplier<WindowCloseGuard> closeGuard
   ) {
      this.send = send;
      this.closeGuard = closeGuard;
      this.blobCache = blobCache;
      this.minecraft = minecraft;
      this.movement = movement;
      this.corrections = movement::count;
      this.blockUpdates = blockUpdates;
      this.crackEvents = crackEvents;
      this.requests = requests;
      this.sounds = sounds;
      this.particles = particles;
      this.forms = forms;
      this.blockEvents = blockEvents;
      this.bossBars = bossBars;
      this.inject = inject;
   }

   void start() {
      String devTest = System.getProperty("bedrocknative.devTest", "");
      if (devTest.startsWith("prisma")) {
         DevPrismaTest.attach(this.requests);
      }

      if (!devTest.startsWith("packs") && !devTest.startsWith("prisma")) {
         Thread.ofVirtual()
            .start(
               () -> {
                  sleep(6000L);
                  if ("creative".equals(System.getProperty("bedrocknative.devTest"))) {
                     new DevCreativeParityTest(this.minecraft, true).run();
                     new DevCreativeTest(this.minecraft, this.requests, true).run();
                  } else if ("creative-parity".equals(System.getProperty("bedrocknative.devTest"))) {
                     new DevCreativeParityTest(this.minecraft, true).run();
                     BedrockNative.LOGGER.info("Dev creative parity test: done");
                  } else if ("inventory".equals(System.getProperty("bedrocknative.devTest"))) {
                     new DevInventoryTest(this.minecraft, this.requests).run();
                  } else if ("inventory-armor".equals(System.getProperty("bedrocknative.devTest"))) {
                     new DevInventoryTest(this.minecraft, this.requests).runArmor();
                  } else if ("recipe-book".equals(System.getProperty("bedrocknative.devTest"))) {
                     new DevRecipeBookTest(this.minecraft, this.requests).run();
                  } else if (System.getProperty("bedrocknative.devTest", "").startsWith("workstations")) {
                     new DevWorkstationsTest(this.minecraft, this.requests).run(System.getProperty("bedrocknative.devTest"));
                  } else if (System.getProperty("bedrocknative.devTest", "").startsWith("movement")) {
                     new DevMovementTest(this.minecraft, this.movement).run();
                  } else if (System.getProperty("bedrocknative.devTest", "").startsWith("vehicles")) {
                     new DevVehiclesTest(this.minecraft, this.movement).run();
                  } else if (System.getProperty("bedrocknative.devTest", "").startsWith("pvp")) {
                     String names = System.getProperty("bedrocknative.devTest").contains(":")
                        ? System.getProperty("bedrocknative.devTest").substring(System.getProperty("bedrocknative.devTest").indexOf(58) + 1)
                        : "";
                     new DevPvpTest(this.minecraft, this.movement, names.isBlank() ? Set.of() : Set.of(names.split(","))).run();
                  } else if ("forms".equals(System.getProperty("bedrocknative.devTest"))) {
                     new DevFormsTest(this.minecraft, this.forms).run();
                  } else if (System.getProperty("bedrocknative.devTest", "").startsWith("items")) {
                     new DevItemsTest(this.minecraft, this.requests).run();
                  } else if (System.getProperty("bedrocknative.devTest", "").startsWith("commands")) {
                     new DevCommandsTest(this.minecraft).run();
                  } else if ("hud".equals(System.getProperty("bedrocknative.devTest"))) {
                     new DevHudTest(this.minecraft).run();
                  } else if ("jsonui-hud".equals(System.getProperty("bedrocknative.devTest"))) {
                     new DevJsonUiHudTest(this.minecraft).run();
                  } else if ("jsonui-screens".equals(System.getProperty("bedrocknative.devTest"))) {
                     new DevJsonUiScreensTest(this.minecraft).run();
                  } else if ("serverui".equals(System.getProperty("bedrocknative.devTest"))) {
                     new DevServerUiTest(this.minecraft).run();
                  } else if ("coverage".equals(System.getProperty("bedrocknative.devTest"))) {
                     new DevCoverageTest(this.minecraft, this.movement, this.blockEvents, this.bossBars, this.inject).run();
                  } else if (System.getProperty("bedrocknative.devTest", "").matches("camera(-.*)?")) {
                     new DevCameraTest(this.minecraft, this.inject, this.movement).run();
                  } else if (System.getProperty("bedrocknative.devTest", "").matches("quick(-.*)?")) {
                     new DevQuickTest(this.minecraft, this.inject, this.movement).run();
                  } else if (System.getProperty("bedrocknative.devTest", "").matches("completion(-.*)?")) {
                     new DevCompletionTest(this.minecraft, this.inject, this.sounds, this.particles).run();
                  } else if ("fakechests".equals(System.getProperty("bedrocknative.devTest"))) {
                     new DevFakeChestsTest(this.minecraft, this.inject, this.closeGuard.get()).run();
                  } else if ("gaps".equals(System.getProperty("bedrocknative.devTest"))) {
                     new DevGapsTest(this.minecraft, this.inject).run();
                  } else if ("capture".equals(System.getProperty("bedrocknative.devTest"))) {
                     new DevCaptureTest(this.minecraft).run();
                  } else if ("signs-books".equals(System.getProperty("bedrocknative.devTest"))) {
                     new DevSignsBooksTest(this.minecraft).run();
                  } else if (System.getProperty("bedrocknative.devTest", "").matches("containers2(-.*)?")) {
                     new DevContainers2Test(this.minecraft).run();
                  } else if ("blocks".equals(System.getProperty("bedrocknative.devTest"))) {
                     new DevBlocksTest(this.minecraft, this.movement).run();
                  } else if ("palette".equals(System.getProperty("bedrocknative.devTest"))) {
                     new DevPaletteTest(this.minecraft).run();
                  } else if ("textures".equals(System.getProperty("bedrocknative.devTest"))) {
                     new DevTexturesTest(this.minecraft).run();
                  } else if ("sounds-particles".equals(System.getProperty("bedrocknative.devTest"))) {
                     new DevSoundsParticlesTest(this.minecraft, this.sounds, this.particles).run();
                  } else if ("effects".equals(System.getProperty("bedrocknative.devTest"))) {
                     new DevEffectsTest(this.minecraft, this.sounds, this.particles).run();
                  } else if ("attachables".equals(System.getProperty("bedrocknative.devTest"))) {
                     new DevAttachablesTest(this.minecraft).run();
                  } else if ("models".equals(System.getProperty("bedrocknative.devTest"))) {
                     new DevModelsTest(this.minecraft).run();
                  } else if ("mobs".equals(System.getProperty("bedrocknative.devTest"))) {
                     new DevMobsTest(this.minecraft).run();
                  } else if ("text".equals(System.getProperty("bedrocknative.devTest"))) {
                     new DevTextTest(this.minecraft).run();
                  } else if ("shaders".equals(System.getProperty("bedrocknative.devTest"))) {
                     new DevShadersTest(this.minecraft).run();
                  } else if (System.getProperty("bedrocknative.devTest", "").matches("blobcache(-.*)?")) {
                     new DevBlobCacheTest(this.minecraft, this.blobCache).run();
                  } else if ("biomes".equals(System.getProperty("bedrocknative.devTest"))) {
                     new DevBiomesTest(this.minecraft).run();
                  } else if ("settings".equals(System.getProperty("bedrocknative.devTest"))) {
                     new DevSettingsTest(this.minecraft).run();
                  } else if ("players".equals(System.getProperty("bedrocknative.devTest"))) {
                     new DevPlayersTest(this.minecraft, this.send).run();
                  } else if ("npcs".equals(System.getProperty("bedrocknative.devTest"))) {
                     new DevNpcsTest(this.minecraft, this.inject).run();
                  } else if (System.getProperty("bedrocknative.devTest", "").matches("look(-.*)?")) {
                     new DevLookTest(this.minecraft, this.movement).run();
                  } else if (System.getProperty("bedrocknative.devTest", "").matches("chunk-burst(-.*)?")) {
                     new DevChunkBurstTest(this.minecraft, this.movement).run();
                  } else if (System.getProperty("bedrocknative.devTest", "").matches("chunk-steady(-.*)?")) {
                     new DevChunkSteadyTest(this.minecraft, this.movement).run();
                  } else if (System.getProperty("bedrocknative.devTest", "").matches("actor-burst(-.*)?")) {
                     new DevActorBurstTest(this.minecraft, this.inject).run();
                  } else if (System.getProperty("bedrocknative.devTest", "").matches("perf-render(-.*)?")) {
                     new DevPerfRenderTest(this.minecraft).run();
                  } else if ("perf".equals(System.getProperty("bedrocknative.devTest"))) {
                     new DevPerfTest(this.minecraft).run();
                  } else if ("players-first-person".equals(System.getProperty("bedrocknative.devTest"))) {
                     new DevPlayersTest(this.minecraft, this.send).run(true);
                  } else if ("players-anim".equals(System.getProperty("bedrocknative.devTest"))) {
                     new DevPlayersAnimTest(this.minecraft, this.inject).run();
                  } else {
                     this.minecraft.execute(() -> this.minecraft.options.keyUp.setDown(true));
                     sleep(3000L);
                     this.minecraft.execute(() -> this.minecraft.options.keyUp.setDown(false));
                     sleep(2000L);
                     this.minecraft
                        .execute(
                           () -> {
                              Map<String, Integer> seen = new TreeMap<>();
                              this.minecraft
                                 .level
                                 .entitiesForRendering()
                                 .forEach(entity -> seen.merge(entity.typeHolder().getRegisteredName(), 1, Integer::sum));
                              BedrockNative.LOGGER
                                 .info(
                                    "Dev test: position {}, {} server movement corrections, entities {}, tab list {} (self {})",
                                    new Object[]{
                                       this.minecraft.player.position(),
                                       this.corrections.getAsInt(),
                                       seen,
                                       this.minecraft.getConnection().getListedOnlinePlayers().size(),
                                       this.minecraft.getConnection().getPlayerInfo(this.minecraft.player.getUUID()) != null
                                    }
                                 );
                              this.reportState("after walking");
                              Screenshot.grab(this.minecraft, false);
                              this.lookAtNearestEntity();
                           }
                        );
                     sleep(1500L);
                     this.minecraft.execute(() -> Screenshot.grab(this.minecraft, false));
                     this.minecraft.execute(() -> this.minecraft.getConnection().sendChat("BedrockNative chat test §ain colour"));

                     for (String command : COMMANDS) {
                        sleep(700L);
                        this.minecraft.execute(() -> this.minecraft.getConnection().sendCommand(command));
                     }

                     sleep(6000L);
                     this.minecraft.execute(() -> {
                        this.reportState("after commands");
                        this.minecraft.player.setXRot(15.0F);
                     });
                     sleep(500L);
                     this.minecraft.execute(() -> Screenshot.grab(this.minecraft, false));
                     this.minecraft.execute(() -> this.minecraft.gui.setScreen(new ChatScreen("", false)));
                     sleep(500L);
                     this.minecraft.execute(() -> {
                        Screenshot.grab(this.minecraft, false);
                        this.minecraft.gui.setScreen(null);
                     });
                     this.interact();
                     new DevInventoryTest(this.minecraft, this.requests).run();
                     new DevEffectsTest(this.minecraft, this.sounds, this.particles).run();
                  }
               }
            );
      }
   }

   private void interact() {
      Vec3[] before = new Vec3[1];
      this.minecraft.execute(() -> {
         before[0] = this.minecraft.player.position();
         this.minecraft.getConnection().sendCommand("tp @s ~ ~ ~");
      });
      sleep(1500L);
      this.minecraft
         .execute(
            () -> BedrockNative.LOGGER
               .info(
                  "Dev test: server position (tp ~ ~ ~) is {} blocks from the client's",
                  Math.round(this.minecraft.player.position().distanceTo(before[0]) * 100.0) / 100.0
               )
         );

      for (String command : INTERACTION_SETUP) {
         sleep(600L);
         this.minecraft.execute(() -> this.minecraft.getConnection().sendCommand(command));
      }

      sleep(2000L);
      BlockPos[] target = new BlockPos[1];
      this.minecraft.execute(() -> {
         this.reportInventory("after give");
         target[0] = this.minecraft.player.blockPosition().offset(2, 0, 0);
         this.select(Items.DIAMOND_PICKAXE);
         this.minecraft.player.lookAt(Anchor.EYES, Vec3.atCenterOf(target[0]));
         BedrockNative.LOGGER.info("Dev test: breaking {} at {} (survival, pickaxe)", this.minecraft.level.getBlockState(target[0]), target[0]);
      });
      sleep(500L);
      int updatesBefore = this.blockUpdates.getAsInt();
      this.minecraft.execute(() -> this.minecraft.options.keyAttack.setDown(true));
      long started = System.currentTimeMillis();

      while (System.currentTimeMillis() - started < 4000L && !this.isAir(target[0])) {
         sleep(50L);
      }

      long brokeAfter = System.currentTimeMillis() - started;
      this.minecraft.execute(() -> this.minecraft.options.keyAttack.setDown(false));
      sleep(1500L);
      this.minecraft
         .execute(
            () -> {
               BedrockNative.LOGGER
                  .info(
                     "Dev test: broke in {} ms; now {} after the prediction ack, {} block updates since, {} crack events",
                     new Object[]{
                        brokeAfter, this.minecraft.level.getBlockState(target[0]), this.blockUpdates.getAsInt() - updatesBefore, this.crackEvents.getAsInt()
                     }
                  );
               Screenshot.grab(this.minecraft, false);
               this.minecraft.getConnection().sendCommand(testFor(target[0], "air"));
            }
         );
      sleep(1000L);
      this.minecraft.execute(() -> {
         this.select(Items.DIRT);
         this.minecraft.player.lookAt(Anchor.EYES, Vec3.atCenterOf(target[0].below()).add(0.0, 0.5, 0.0));
      });
      sleep(300L);
      this.minecraft
         .execute(
            () -> {
               BlockPos support = target[0].below();
               InteractionResult result = this.minecraft
                  .gameMode
                  .useItemOn(
                     this.minecraft.player,
                     InteractionHand.MAIN_HAND,
                     new BlockHitResult(Vec3.atCenterOf(support).add(0.0, 0.5, 0.0), Direction.UP, support, false)
                  );
               this.minecraft.player.swing(InteractionHand.MAIN_HAND, this.minecraft.player.getMainHandItem().getInteractAnimation(), false);
               BedrockNative.LOGGER
                  .info(
                     "Dev test: placing dirt on {}: {}, predicted {}",
                     new Object[]{this.minecraft.level.getBlockState(support), result, this.minecraft.level.getBlockState(target[0])}
                  );
            }
         );
      sleep(1500L);
      this.minecraft.execute(() -> {
         BedrockNative.LOGGER.info("Dev test: placed block now {} after the prediction ack", this.minecraft.level.getBlockState(target[0]));
         this.reportInventory("after placing");
         this.minecraft.getConnection().sendCommand(testFor(target[0], "dirt"));
      });
      sleep(800L);
      BlockPos trapdoor = target[0].offset(0, 0, 1);
      this.minecraft.execute(() -> this.minecraft.player.lookAt(Anchor.EYES, Vec3.atCenterOf(trapdoor)));
      sleep(300L);
      this.minecraft
         .execute(
            () -> {
               BedrockNative.LOGGER.info("Dev test: using {}", this.minecraft.level.getBlockState(trapdoor));
               this.minecraft
                  .gameMode
                  .useItemOn(this.minecraft.player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(trapdoor), Direction.WEST, trapdoor, false));
            }
         );
      sleep(1500L);
      this.minecraft.execute(() -> {
         BedrockNative.LOGGER.info("Dev test: trapdoor now {}", this.minecraft.level.getBlockState(trapdoor));
         Screenshot.grab(this.minecraft, false);
      });
      Entity[] pig = new Entity[1];
      this.minecraft.execute(() -> this.minecraft.getConnection().sendCommand("summon pig ~-2 ~ ~"));
      sleep(1000L);
      this.minecraft
         .execute(
            () -> {
               this.minecraft.player.getInventory().setSelectedSlot(8);

               for (Entity entity : this.minecraft.level.entitiesForRendering()) {
                  if (entity.getType() == EntityTypes.PIG
                     && (pig[0] == null || entity.distanceToSqr(this.minecraft.player) < pig[0].distanceToSqr(this.minecraft.player))) {
                     pig[0] = entity;
                  }
               }
            }
         );

      for (int hit = 1; hit <= 2; hit++) {
         sleep(600L);
         if (pig[0] == null) {
            BedrockNative.LOGGER.warn("Dev test: no pig to attack");
            break;
         }

         int attempt = hit;
         this.minecraft.execute(() -> {
            if (attempt == 2) {
               this.select(Items.DIAMOND_SWORD);
            }

            this.minecraft.player.lookAt(Anchor.EYES, pig[0].getBoundingBox().getCenter());
         });
         sleep(150L);
         this.minecraft
            .execute(
               () -> {
                  BedrockNative.LOGGER
                     .info(
                        "Dev test: attack {} on pig at {} ({} blocks), health {}",
                        new Object[]{
                           attempt, pig[0].position(), Math.round(pig[0].distanceTo(this.minecraft.player) * 10.0F) / 10.0F, ((LivingEntity)pig[0]).getHealth()
                        }
                     );
                  this.minecraft.gameMode.attack(this.minecraft.player, pig[0]);
                  this.minecraft.player.swing(InteractionHand.MAIN_HAND, this.minecraft.player.getMainHandItem().getAttackAnimation(), false);
               }
            );
         sleep(150L);
         this.minecraft
            .execute(
               () -> BedrockNative.LOGGER
                  .info(
                     "Dev test: pig hurt time {}, at {}, motion {}, health {}, alive {}, removed {}",
                     new Object[]{
                        ((LivingEntity)pig[0]).hurtTime,
                        pig[0].position(),
                        pig[0].getDeltaMovement(),
                        ((LivingEntity)pig[0]).getHealth(),
                        pig[0].isAlive(),
                        pig[0].isRemoved()
                     }
                  )
            );
      }

      this.minecraft.execute(() -> Screenshot.grab(this.minecraft, false));
      this.minecraft.execute(() -> this.minecraft.getConnection().sendCommand("gamemode creative"));
      sleep(1000L);
      this.minecraft.execute(() -> {
         this.select(Items.DIAMOND_PICKAXE);
         this.minecraft.player.lookAt(Anchor.EYES, Vec3.atCenterOf(target[0]));
      });
      sleep(300L);
      this.minecraft
         .execute(
            () -> {
               BedrockNative.LOGGER
                  .info(
                     "Dev test: creative breaking {}, looking at {}",
                     this.minecraft.level.getBlockState(target[0]),
                     this.minecraft.hitResult instanceof BlockHitResult hitx ? hitx.getBlockPos() : this.minecraft.hitResult
                  );
               this.minecraft.options.keyAttack.setDown(true);
            }
         );
      sleep(400L);
      this.minecraft.execute(() -> this.minecraft.options.keyAttack.setDown(false));
      sleep(1500L);
      this.minecraft.execute(() -> {
         BedrockNative.LOGGER.info("Dev test: after creative break {}", this.minecraft.level.getBlockState(target[0]));
         this.minecraft.getConnection().sendCommand(testFor(target[0], "air"));
         this.minecraft.getConnection().sendCommand("gamemode survival");
         this.select(Items.DIRT);
      });
      sleep(1000L);
      this.minecraft.execute(() -> {
         this.reportInventory("before dropping one dirt");
         this.minecraft.gameMode.dropItem(this.minecraft.player, false);
      });
      sleep(1500L);
      this.minecraft
         .execute(
            () -> {
               long dirt = 0L;

               for (Entity entity : this.minecraft.level.entitiesForRendering()) {
                  if (entity instanceof ItemEntity item && item.getItem().is(Items.DIRT) && entity.distanceTo(this.minecraft.player) < 5.0F) {
                     dirt++;
                  }
               }

               BedrockNative.LOGGER
                  .info("Dev test: {} dirt item entities nearby after dropping, {} movement corrections in total", dirt, this.corrections.getAsInt());
               this.reportInventory("end");
               this.minecraft.getConnection().sendCommand("clear @s dirt 0 0");
            }
         );
   }

   private static String testFor(BlockPos pos, String block) {
      return "testforblock " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + block;
   }

   private boolean isAir(BlockPos pos) {
      CompletableFuture<Boolean> air = new CompletableFuture<>();
      this.minecraft.execute(() -> air.complete(this.minecraft.level.getBlockState(pos).isAir()));
      return air.join();
   }

   private void select(Item item) {
      Inventory inventory = this.minecraft.player.getInventory();

      for (int slot = 0; slot < 9; slot++) {
         if (inventory.getItem(slot).is(item)) {
            inventory.setSelectedSlot(slot);
            return;
         }
      }

      BedrockNative.LOGGER.warn("Dev test: no {} in the hotbar", item);
   }

   private void reportInventory(String when) {
      Inventory inventory = this.minecraft.player.getInventory();
      List<String> items = new ArrayList<>();

      for (int slot = 0; slot < 36; slot++) {
         if (!inventory.getItem(slot).isEmpty()) {
            items.add(slot + "=" + inventory.getItem(slot).getCount() + "x" + inventory.getItem(slot).getItem());
         }
      }

      BedrockNative.LOGGER.info("Dev test inventory ({}): selected {}, {}", new Object[]{when, inventory.getSelectedSlot(), items});
   }

   private void reportState(String when) {
      LocalPlayer player = this.minecraft.player;
      ClientLevel level = this.minecraft.level;
      int blockEntities = 0;
      Map<String, Integer> biomes = new TreeMap<>();
      int radius = this.minecraft.options.getEffectiveRenderDistance();

      for (int x = -radius; x <= radius; x++) {
         for (int z = -radius; z <= radius; z++) {
            LevelChunk chunk = level.getChunkSource().getChunk((player.getBlockX() >> 4) + x, (player.getBlockZ() >> 4) + z, false);
            if (chunk != null) {
               blockEntities += chunk.getBlockEntities().size();
               biomes.merge(level.getBiome(chunk.getPos().getMiddleBlockPosition(player.getBlockY())).getRegisteredName(), 1, Integer::sum);
            }
         }
      }

      BedrockNative.LOGGER
         .info(
            "Dev test ({}): mode {}, health {}/{}, food {}, xp level {} ({}), speed {}, day time {}, rain {}, thunder {}, biome {}, below {}, {} block updates, {} block entities nearby, chunk biomes {}",
            new Object[]{
               when,
               this.minecraft.gameMode.getPlayerMode(),
               player.getHealth(),
               player.getMaxHealth(),
               player.getFoodData().getFoodLevel(),
               player.experienceLevel,
               player.experienceProgress,
               player.getSpeed(),
               level.getDefaultClockTime(),
               level.getRainLevel(1.0F),
               level.getThunderLevel(1.0F),
               level.getBiome(player.blockPosition()).getRegisteredName(),
               level.getBlockState(player.blockPosition().below()),
               this.blockUpdates.getAsInt(),
               blockEntities,
               biomes
            }
         );
   }

   private void lookAtNearestEntity() {
      LocalPlayer player = this.minecraft.player;
      Entity nearest = null;

      for (Entity entity : this.minecraft.level.entitiesForRendering()) {
         if (entity != player && (nearest == null || entity.distanceToSqr(player) < nearest.distanceToSqr(player))) {
            nearest = entity;
         }
      }

      if (nearest != null) {
         player.lookAt(Anchor.EYES, nearest.getEyePosition());
         BedrockNative.LOGGER
            .info(
               "Dev test: nearest {} at {} ({} blocks), fire {}, baby {}, name {}, hand {}, head {}",
               new Object[]{
                  nearest.typeHolder().getRegisteredName(),
                  nearest.position(),
                  Math.round(nearest.distanceTo(player)),
                  nearest.isOnFire(),
                  nearest instanceof LivingEntity living && living.isBaby(),
                  nearest.getCustomName(),
                  nearest instanceof LivingEntity living ? living.getMainHandItem() : null,
                  nearest instanceof LivingEntity living ? living.getItemBySlot(EquipmentSlot.HEAD) : null
               }
            );
      }
   }

   private static void sleep(long millis) {
      try {
         Thread.sleep(millis);
      } catch (InterruptedException var3) {
      }
   }
}
