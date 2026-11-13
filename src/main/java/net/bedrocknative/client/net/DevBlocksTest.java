package net.bedrocknative.client.net;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.content.BedrockBlockModels;
import net.bedrocknative.client.sound.BedrockSoundEngine;
import net.bedrocknative.sound.SoundCatalog;
import net.bedrocknative.world.block.BlockMappings;
import net.bedrocknative.world.block.custom.BlockProblems;
import net.bedrocknative.world.block.custom.CarrierBlock;
import net.bedrocknative.world.block.custom.CustomBlockSet;
import net.bedrocknative.world.block.custom.CustomBlockState;
import net.bedrocknative.world.item.CustomItem;
import net.bedrocknative.world.item.ItemMappings;
import net.bedrocknative.world.sound.JavaBlockSounds;
import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableMesh;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.commands.arguments.EntityAnchorArgument.Anchor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

final class DevBlocksTest {
   private static final String FULL = "bedrocknative:test_full";
   private static final String PLANT = "bedrocknative:test_plant";
   private static final String PEDESTAL = "bedrocknative:test_pedestal";
   private static final String LAMP = "bedrocknative:test_lamp";
   private static final String HALF = "bedrocknative:test_half";
   private static final String UNBREAKABLE = "bedrocknative:test_unbreakable";
   private static final String FLIPBOOK = "bedrocknative:test_flipbook";
   private static final String GLASS = "bedrocknative:test_glass";
   private static final String SOUND = "bedrocknative:test_sound";
   private static final String CHIME = "bedrocknative.test.chime";
   private final Minecraft minecraft;
   private final MovementStats movement;

   DevBlocksTest(Minecraft minecraft, MovementStats movement) {
      this.minecraft = minecraft;
      this.movement = movement;
   }

   void run() {
      BlockPos base = this.onClient(() -> this.minecraft.player.blockPosition());
      BlockPos full = base.offset(0, 0, 4);
      BlockPos plant = base.offset(2, 0, 4);
      BlockPos pedestal = base.offset(4, 0, 4);
      BlockPos lamp = base.offset(6, 0, 4);
      BlockPos half = base.offset(8, 0, 4);
      BlockPos unbreakable = base.offset(-2, 0, 4);
      BlockPos flipbook = base.offset(0, 0, 7);
      BlockPos glassStone = base.offset(2, 0, 7);
      BlockPos glassWest = base.offset(3, 0, 7);
      BlockPos glassEast = base.offset(4, 0, 7);
      BlockPos sound = base.offset(7, 0, 7);
      log(
         "server: {} custom states, {} past the pool; blocks {}",
         CustomBlockSet.current().size(),
         CustomBlockSet.current().overflow(),
         CustomBlockSet.current().blocks()
      );
      this.commands(
         "gamemode creative",
         "time set noon",
         "weather clear",
         "clear @s",
         fill(base.offset(-3, -1, -2), base.offset(12, -1, 8), "stone"),
         fill(base.offset(-3, 0, -2), base.offset(12, 4, 8), "air")
      );
      this.commands(
         setblock(full, "bedrocknative:test_full"),
         setblock(plant, "bedrocknative:test_plant"),
         setblock(pedestal, "bedrocknative:test_pedestal"),
         setblock(lamp, "bedrocknative:test_lamp [\"minecraft:cardinal_direction\"=\"west\"]"),
         setblock(half, "bedrocknative:test_half"),
         setblock(unbreakable, "bedrocknative:test_unbreakable"),
         fill(pedestal.south(), pedestal.south().above(2), "stone")
      );
      this.commands(
         setblock(flipbook, "bedrocknative:test_flipbook"),
         setblock(glassStone, "stone"),
         setblock(glassWest, "bedrocknative:test_glass"),
         setblock(glassEast, "bedrocknative:test_glass"),
         setblock(sound, "bedrocknative:test_sound")
      );
      sleep(2000L);

      for (BlockPos pos : List.of(full, plant, pedestal, lamp, half, unbreakable, flipbook, glassWest, glassEast, sound)) {
         this.report(pos);
      }

      this.problems();
      this.run(
         () -> log(
            "lamp light: block light {} above the lamp, {} two above (expected 14 and 13)",
            this.minecraft.level.getBrightness(LightLayer.BLOCK, lamp.above()),
            this.minecraft.level.getBrightness(LightLayer.BLOCK, lamp.above(2))
         )
      );
      this.view(base.offset(4, 1, -3), pedestal, "blocks-row");
      this.view(lamp.offset(-3, 1, 0), lamp, "blocks-lamp-west");
      this.view(half.offset(3, 1, 0), half, "blocks-half-east");
      this.view(pedestal.offset(0, 2, -2), pedestal, "blocks-pedestal-north");
      this.commands("time set midnight");
      sleep(1500L);
      this.view(lamp.offset(-3, 1, 0), lamp, "blocks-lamp-night");
      this.commands("time set noon");
      this.commands(
         "give @s bedrocknative:test_full 4",
         "give @s bedrocknative:test_plant",
         "give @s bedrocknative:test_pedestal",
         "give @s bedrocknative:test_lamp",
         "give @s bedrocknative:test_half"
      );
      sleep(1500L);
      this.run(
         () -> {
            Inventory inventory = this.minecraft.player.getInventory();

            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
               CustomItem custom = ItemMappings.custom(inventory.getItem(slot));
               if (custom != null) {
                  log(
                     "item slot {}: {} x{} '{}', block {}, block model {}",
                     slot,
                     custom.identifier(),
                     inventory.getItem(slot).getCount(),
                     inventory.getItem(slot).getHoverName().getString(),
                     custom.block(),
                     custom.block() != null && BedrockBlockModels.item(custom.block()) != null ? "yes" : "NO"
                  );
               }
            }

            this.minecraft.gui.setScreen(new InventoryScreen(this.minecraft.player));
         }
      );
      sleep(1500L);
      this.screenshot("blocks-inventory");
      this.run(() -> this.minecraft.gui.setScreen(null));
      sleep(500L);
      this.droppedItems(base);
      this.run(
         () -> log(
            "after the drops: pedestal {}, lamp {}, half {}",
            this.minecraft.level.getBlockState(pedestal),
            this.minecraft.level.getBlockState(lamp),
            this.minecraft.level.getBlockState(half)
         )
      );
      this.commands("gamemode survival");
      this.walk(
         "onto the pedestal",
         Vec3.atBottomCenterOf(pedestal).add(0.0, 0.0, -1.5),
         0.0F,
         () -> String.format(Locale.ROOT, "feet y %.4f (expected %d.5000 on the 8 px collision box)", this.minecraft.player.getY(), pedestal.getY())
      );
      this.walk(
         "into the half wall from the east",
         Vec3.atBottomCenterOf(half).add(2.0, 0.0, 0.0),
         90.0F,
         () -> String.format(
            Locale.ROOT,
            "x %.4f (expected %.4f: stopped by the west half; %.4f would mean the east half), feet y %.4f (expected %d.0000)",
            this.minecraft.player.getX(),
            half.getX() + 0.8,
            half.getX() + 1.3,
            this.minecraft.player.getY(),
            half.getY()
         )
      );
      this.breakAndPlace(full);
      this.breakUnbreakable(unbreakable);
      this.commands("gamemode creative");
      this.flipbook(flipbook);
      this.culling(glassWest, glassEast);
      this.sounds(sound);
      this.pickBlock(full);
      this.commands(fill(base.offset(-3, 0, -2), base.offset(12, 4, 8), "air"), "clear @s", "gamemode creative");
      log("done");
   }

   private void droppedItems(BlockPos base) {
      this.commands("kill @e[type=item]");
      double[] spot = new double[]{base.getX() + 2.5, base.getX() + 6.5};
      List<String> ids = List.of("bedrocknative:test_full", "bedrocknative:test_pedestal");

      for (int n = 0; n < ids.size(); n++) {
         String id = ids.get(n);
         this.commands(String.format(Locale.ROOT, "tp @s %.1f %d %.1f 0 0", spot[n], base.getY(), base.getZ() - 3.5));
         int slot = this.onClient(() -> {
            Inventory inventory = this.minecraft.player.getInventory();

            for (int i = 0; i < 9; i++) {
               CustomItem custom = ItemMappings.custom(inventory.getItem(i));
               if (custom != null && custom.identifier().equals(id)) {
                  return i;
               }
            }

            return -1;
         });
         log("drop {}: hotbar slot {}", id, slot);
         if (slot >= 0) {
            this.run(() -> this.minecraft.player.getInventory().setSelectedSlot(slot));
            sleep(700L);
            this.screenshot("blocks-hand-" + id.substring(id.indexOf(58) + 1));
            this.run(() -> KeyMapping.click(InputConstants.getKey(this.minecraft.options.keyDrop.saveString())));
            sleep(900L);
         }
      }

      sleep(2500L);
      List<Vec3> found = this.onClient(
         () -> {
            List<Vec3> positions = new ArrayList<>();

            for (Entity entity : this.minecraft.level.entitiesForRendering()) {
               if (entity instanceof ItemEntity item && item.distanceToSqr(this.minecraft.player) < 400.0) {
                  log(
                     "dropped item entity at {} {} {}: {}",
                     String.format(Locale.ROOT, "%.2f", item.getX()),
                     String.format(Locale.ROOT, "%.2f", item.getY()),
                     String.format(Locale.ROOT, "%.2f", item.getZ()),
                     item.getItem().getHoverName().getString()
                  );
                  positions.add(item.position());
               }
            }

            return positions;
         }
      );
      if (found.isEmpty()) {
         log("no dropped item entity near the player");
      } else {
         Vec3 middle = Vec3.ZERO;

         for (Vec3 position : found) {
            middle = middle.add(position);
         }

         Vec3 target = middle.scale(1.0 / found.size());
         this.commands(String.format(Locale.ROOT, "tp @s %.2f %.2f %.2f 0 0", target.x, target.y, target.z - 3.2));
         this.run(() -> this.minecraft.gui.hud.getChat().clearMessages(false));
         this.run(() -> this.minecraft.player.lookAt(Anchor.EYES, target.add(0.0, 0.3, 0.0)));
         sleep(500L);
         this.screenshot("blocks-dropped");
         this.run(() -> this.minecraft.player.lookAt(Anchor.EYES, target.add(0.0, 0.3, 0.0)));
         this.commands("kill @e[type=item]");
      }
   }

   private void problems() {
      this.run(
         () -> {
            List<BlockProblems.Problem> problems = BedrockBlockModels.problems();
            log(
               "block problems: {} (expected 0 for the test pack){}",
               problems.size(),
               problems.isEmpty() ? "" : " " + problems.stream().map(BlockProblems.Problem::line).limit(10L).toList()
            );
         }
      );
   }

   private void flipbook(BlockPos target) {
      this.view(target.offset(0, 1, -2), target, "blocks-flipbook-1");
      this.run(() -> log("flipbook slots: {}", BedrockBlockModels.animations()));
      boolean hudShown = this.onClient(() -> !this.minecraft.gui.hud.isHidden());
      if (hudShown) {
         this.run(() -> this.minecraft.gui.hud.toggle());
         sleep(300L);
      }

      Set<String> seen = new LinkedHashSet<>();
      List<String> samples = new ArrayList<>();

      for (int i = 0; i < 4; i++) {
         int argb = this.centerPixel();
         String colour = colour(argb);
         samples.add(colour + String.format(Locale.ROOT, " #%06X", argb & 16777215));
         if (!colour.equals("other")) {
            seen.add(colour);
         }

         sleep(550L);
      }

      if (hudShown) {
         this.run(() -> this.minecraft.gui.hud.toggle());
         sleep(300L);
      }

      this.screenshot("blocks-flipbook-2");
      this.run(() -> log("flipbook slots now: {}", BedrockBlockModels.animations()));
      log("flipbook samples: centre of the face over 2 s {} -> {} frame colours", samples, seen.size());
      log("flipbook {}: the face cycles its frames (2 or more of red, green, blue, yellow seen)", seen.size() >= 2 ? "PASS" : "FAIL");
   }

   private void culling(BlockPos west, BlockPos east) {
      this.run(
         () -> {
            int westQuads = this.emitted(west);
            int eastQuads = this.emitted(east);
            int all = BedrockBlockModels.quadCount(this.minecraft.level.getBlockState(west));
            log(
               "culling {}: glass west of glass with stone beside it emits {} of {} quads (expected 6 of 12), the east one {} (expected 8 of 12); AO off on every quad: {}",
               westQuads == 6 && eastQuads == 8 && all == 12 ? "PASS" : "FAIL",
               westQuads,
               all,
               eastQuads,
               this.aoOff(west) && this.aoOff(east)
            );
         }
      );
      this.view(east.offset(2, 1, -2), west, "blocks-glass");
   }

   private int emitted(BlockPos pos) {
      return this.mesh(pos).size();
   }

   private boolean aoOff(BlockPos pos) {
      MutableMesh mesh = this.mesh(pos);
      boolean[] off = new boolean[]{true};
      mesh.forEach(quad -> off[0] &= quad.ambientOcclusion() == TriState.FALSE);
      return off[0];
   }

   private MutableMesh mesh(BlockPos pos) {
      BlockState state = this.minecraft.level.getBlockState(pos);
      MutableMesh mesh = Renderer.get().mutableMesh();
      BlockStateModel model = BedrockBlockModels.model(state);
      if (model != null) {
         model.emitQuads(
            mesh.emitter(),
            this.minecraft.level,
            pos,
            state,
            RandomSource.create(42L),
            direction -> !Block.shouldRenderFace(state, this.minecraft.level.getBlockState(pos.relative(direction)), direction)
         );
      }

      return mesh;
   }

   private void sounds(BlockPos target) {
      this.run(
         () -> {
            CustomBlockState custom = CarrierBlock.data(this.minecraft.level.getBlockState(target));
            SoundCatalog catalog = BedrockSoundEngine.catalog();
            log(
               "sound type of {}: Bedrock {}, Java stand-in {}",
               custom,
               catalog != null && custom != null ? JavaBlockSounds.customType(catalog, custom) : "no catalog",
               this.minecraft.level.getBlockState(target).getSoundType().getBreakSound().location()
            );
         }
      );
      this.commands("gamemode survival", "give @s bedrocknative:test_sound");
      this.commands(
         String.format(
            Locale.ROOT,
            "tp @s %.1f %d %.1f facing %.1f %.1f %.1f",
            target.getX() + 0.5,
            target.getY(),
            target.getZ() - 2.5,
            target.getX() + 0.5,
            target.getY() + 0.5,
            target.getZ() + 0.5
         )
      );
      sleep(1000L);
      int before = this.onClient(BedrockSoundEngine::playedCount);
      this.run(() -> {
         this.minecraft.player.getInventory().setSelectedSlot(8);
         this.minecraft.player.lookAt(Anchor.EYES, Vec3.atCenterOf(target));
         this.minecraft.options.keyAttack.setDown(true);
      });
      long started = System.currentTimeMillis();

      while (System.currentTimeMillis() - started < 4000L && !this.onClient(() -> this.minecraft.level.getBlockState(target).isAir())) {
         sleep(50L);
      }

      this.run(() -> this.minecraft.options.keyAttack.setDown(false));
      sleep(800L);
      int broke = this.chimes(before);
      boolean broken = this.onClient(() -> this.minecraft.level.getBlockState(target).isAir());
      int afterBreak = this.onClient(BedrockSoundEngine::playedCount);
      this.run(
         () -> {
            Inventory inventory = this.minecraft.player.getInventory();

            for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
               CustomItem custom = ItemMappings.custom(inventory.getItem(slot));
               if (custom != null && custom.identifier().equals("bedrocknative:test_sound")) {
                  inventory.setSelectedSlot(slot);
               }
            }

            BlockPos support = target.below();
            this.minecraft.player.lookAt(Anchor.EYES, Vec3.atCenterOf(support).add(0.0, 0.5, 0.0));
            this.minecraft
               .gameMode
               .useItemOn(
                  this.minecraft.player,
                  InteractionHand.MAIN_HAND,
                  new BlockHitResult(Vec3.atCenterOf(support).add(0.0, 0.5, 0.0), Direction.UP, support, false)
               );
            this.minecraft.player.swing(InteractionHand.MAIN_HAND, this.minecraft.player.getMainHandItem().getInteractAnimation(), false);
         }
      );
      sleep(1500L);
      int placed = this.chimes(afterBreak);
      boolean back = this.onClient(() -> CarrierBlock.data(this.minecraft.level.getBlockState(target)) != null);
      log(
         "sound plays: hitting and breaking the chime block played the chime {} times (broken {}), placing it back {} times (placed {})",
         broke,
         broken,
         placed,
         back
      );
      log("sound {}: the chime block's own sound group plays when it is hit, broken and placed", broke >= 1 && placed >= 1 && broken && back ? "PASS" : "FAIL");
      this.commands("gamemode creative");
   }

   private int chimes(int before) {
      return this.onClient(() -> {
         List<String> recent = BedrockSoundEngine.recent();
         int added = Math.min(recent.size(), BedrockSoundEngine.playedCount() - before);
         return (int)recent.subList(recent.size() - added, recent.size()).stream().filter("bedrocknative.test.chime"::equals).count();
      });
   }

   private void pickBlock(BlockPos target) {
      this.commands("gamemode creative", "clear @s");
      this.commands(
         String.format(
            Locale.ROOT,
            "tp @s %.1f %d %.1f facing %.1f %.1f %.1f",
            target.getX() + 0.5,
            target.getY(),
            target.getZ() - 2.5,
            target.getX() + 0.5,
            target.getY() + 0.5,
            target.getZ() + 0.5
         )
      );
      sleep(1000L);
      this.run(() -> {
         this.minecraft.player.getInventory().setSelectedSlot(0);
         this.minecraft.player.lookAt(Anchor.EYES, Vec3.atCenterOf(target));
         this.minecraft.gameMode.handlePickItemFromBlock(target, false);
      });
      sleep(1500L);
      this.run(
         () -> {
            Inventory inventory = this.minecraft.player.getInventory();
            CustomItem held = ItemMappings.custom(this.minecraft.player.getMainHandItem());
            boolean anywhere = false;

            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
               CustomItem custom = ItemMappings.custom(inventory.getItem(slot));
               anywhere |= custom != null && "bedrocknative:test_full".equals(custom.identifier());
            }

            log("pick-block request {}", BedrockInteractions.lastPick);
            log(
               "pick-block {}: main hand {}, the block's item in the inventory {}, block model {}",
               held != null && "bedrocknative:test_full".equals(held.identifier()) ? "PASS" : (anywhere ? "PASS (not selected)" : "FAIL"),
               held != null ? held.identifier() : this.minecraft.player.getMainHandItem().getItem(),
               anywhere,
               held != null && held.block() != null && BedrockBlockModels.item(held.block()) != null ? "yes" : "NO"
            );
         }
      );
      this.screenshot("blocks-pick");
   }

   private int centerPixel() {
      CompletableFuture<Integer> pixel = new CompletableFuture<>();
      this.minecraft.execute(() -> Screenshot.takeScreenshot(this.minecraft.gameRenderer.mainRenderTarget(), image -> {
         try {
            NativeImage twrVar0$ = image;

            try {
               pixel.complete(image.getPixel(image.getWidth() / 2, image.getHeight() / 2));
            } catch (Throwable var6) {
               if (image != null) {
                  try {
                     twrVar0$.close();
                  } catch (Throwable var5) {
                     var6.addSuppressed(var5);
                  }
               }

               throw var6;
            }

            if (image != null) {
               image.close();
            }
         } catch (RuntimeException var7) {
            pixel.completeExceptionally(var7);
         }
      }));

      try {
         return pixel.get(10L, TimeUnit.SECONDS);
      } catch (InterruptedException var3) {
         Thread.currentThread().interrupt();
         return 0;
      } catch (TimeoutException | ExecutionException var4) {
         log("the frame could not be read back: {}", var4.toString());
         return 0;
      }
   }

   private static String colour(int argb) {
      int r = argb >> 16 & 0xFF;
      int g = argb >> 8 & 0xFF;
      int b = argb & 0xFF;
      if (r > 2 * g && r > 2 * b) {
         return "red";
      } else if (g > 2 * r && g > 2 * b) {
         return "green";
      } else if (b > 2 * r && b > 2 * g) {
         return "blue";
      } else {
         return r > 2 * b && g > 2 * b && Math.abs(r - g) < Math.max(r, g) / 4 ? "yellow" : "other";
      }
   }

   private void report(BlockPos pos) {
      this.run(
         () -> {
            BlockState state = this.minecraft.level.getBlockState(pos);
            CustomBlockState custom = CarrierBlock.data(state);
            if (custom == null) {
               log(
                  "{}: {} is not a custom block (unknown ids {}, custom states without a slot {})",
                  pos,
                  state,
                  BlockMappings.get().unknownSeen().size(),
                  BlockMappings.get().customSeen()
               );
            } else {
               VoxelShape collision = state.getCollisionShape(this.minecraft.level, pos, CollisionContext.empty());
               VoxelShape selection = state.getShape(this.minecraft.level, pos);
               BlockStateModel model = BedrockBlockModels.model(state);
               int quads = 0;
               List<String> layers = new ArrayList<>();
               if (model != null) {
                  List<BlockStateModelPart> parts = new ArrayList<>();
                  model.collectParts(RandomSource.create(0L), parts);

                  for (BlockStateModelPart part : parts) {
                     for (Direction direction : Direction.values()) {
                        quads += part.getQuads(direction).size();
                     }

                     quads += part.getQuads(null).size();
                     part.getQuads(null).forEach(quad -> layers.add(quad.materialInfo().layer().label()));

                     for (Direction direction : Direction.values()) {
                        part.getQuads(direction).forEach(quad -> layers.add(quad.materialInfo().layer().label()));
                     }
                  }
               }

               log(
                  "{}: {} slot {} geometry {} | collision {} | selection {} | light {} dampening {} | occludes {} | model {} quads, layers {} | network id round trip {}",
                  pos,
                  custom,
                  state.getValue(CarrierBlock.SLOT),
                  custom.geometry().identifier(),
                  bounds(collision),
                  bounds(selection),
                  state.getLightEmission(),
                  state.getLightDampening(),
                  state.canOcclude() && state.isSolidRender(),
                  model == null ? "NONE" : quads,
                  layers.stream().distinct().toList(),
                  BlockMappings.get().toJava(BlockMappings.get().toBedrock(state)) == state
               );
            }
         }
      );
   }

   private static String bounds(VoxelShape shape) {
      if (shape.isEmpty()) {
         return "none";
      } else {
         AABB box = shape.bounds();
         return String.format(Locale.ROOT, "%.3f,%.3f,%.3f..%.3f,%.3f,%.3f", box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
      }
   }

   private void view(BlockPos eye, BlockPos target, String name) {
      this.commands(
         String.format(
            Locale.ROOT,
            "tp @s %.1f %d %.1f facing %.1f %.1f %.1f",
            eye.getX() + 0.5,
            eye.getY() - 1,
            eye.getZ() + 0.5,
            target.getX() + 0.5,
            target.getY() + 0.5,
            target.getZ() + 0.5
         )
      );
      sleep(1500L);
      this.run(() -> this.minecraft.gui.hud.getChat().clearMessages(false));
      this.run(() -> this.minecraft.player.lookAt(Anchor.EYES, Vec3.atCenterOf(target)));
      sleep(200L);
      this.screenshot(name);
   }

   private void walk(String what, Vec3 from, float yaw, Supplier<String> result) {
      this.commands(String.format(Locale.ROOT, "tp @s %.2f %.2f %.2f %.1f 0", from.x, from.y, from.z, yaw));
      sleep(1500L);
      int before = this.movement.count();
      boolean autoJump = this.onClient(() -> (Boolean)this.minecraft.options.autoJump().get());
      this.run(() -> {
         this.minecraft.options.autoJump().set(false);
         this.minecraft.options.keyUp.setDown(true);
      });
      sleep(1500L);
      this.run(() -> this.minecraft.options.keyUp.setDown(false));
      sleep(1000L);
      log("walk {}: {}, {} server corrections (expected 0)", what, this.onClient(result), this.movement.count() - before);
      this.run(() -> this.minecraft.options.autoJump().set(autoJump));
   }

   private void breakAndPlace(BlockPos target) {
      this.commands(
         String.format(
            Locale.ROOT,
            "tp @s %.1f %d %.1f facing %.1f %.1f %.1f",
            target.getX() + 0.5,
            target.getY(),
            target.getZ() - 2.5,
            target.getX() + 0.5,
            target.getY() + 0.5,
            target.getZ() + 0.5
         )
      );
      sleep(1000L);
      this.run(
         () -> {
            this.minecraft.player.getInventory().setSelectedSlot(8);
            this.minecraft.player.lookAt(Anchor.EYES, Vec3.atCenterOf(target));
            log(
               "breaking {} by hand, destroy progress per tick {}",
               CarrierBlock.data(this.minecraft.level.getBlockState(target)),
               this.minecraft.level.getBlockState(target).getDestroyProgress(this.minecraft.player, this.minecraft.level, target)
            );
            this.minecraft.options.keyAttack.setDown(true);
         }
      );
      long started = System.currentTimeMillis();
      boolean diagnosed = false;

      while (System.currentTimeMillis() - started < 5000L && !this.onClient(() -> this.minecraft.level.getBlockState(target).isAir())) {
         sleep(50L);
         if (!diagnosed && System.currentTimeMillis() - started > 300L) {
            diagnosed = true;
            this.run(
               () -> log(
                  "break diagnostics: screen {}, mouse grabbed {}, window active {}, hit {}, mode {}",
                  this.minecraft.gui.screen(),
                  this.minecraft.mouseHandler.isMouseGrabbed(),
                  this.minecraft.isWindowActive(),
                  this.minecraft.hitResult,
                  this.minecraft.gameMode.getPlayerMode()
               )
            );
         }
      }

      long broke = System.currentTimeMillis() - started;
      this.run(() -> this.minecraft.options.keyAttack.setDown(false));
      sleep(1500L);
      log("broke after {} ms (expected about 1500); now {}", broke, this.onClient(() -> this.minecraft.level.getBlockState(target)));
      this.commands(testFor(target, "air"));
      this.run(
         () -> {
            Inventory inventory = this.minecraft.player.getInventory();

            for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
               CustomItem custom = ItemMappings.custom(inventory.getItem(slot));
               if (custom != null && custom.identifier().equals("bedrocknative:test_full")) {
                  inventory.setSelectedSlot(slot);
               }
            }

            BlockPos support = target.below();
            this.minecraft.player.lookAt(Anchor.EYES, Vec3.atCenterOf(support).add(0.0, 0.5, 0.0));
            InteractionResult result = this.minecraft
               .gameMode
               .useItemOn(
                  this.minecraft.player,
                  InteractionHand.MAIN_HAND,
                  new BlockHitResult(Vec3.atCenterOf(support).add(0.0, 0.5, 0.0), Direction.UP, support, false)
               );
            this.minecraft.player.swing(InteractionHand.MAIN_HAND, this.minecraft.player.getMainHandItem().getInteractAnimation(), false);
            log("placing {} on {}: {}", ItemMappings.custom(this.minecraft.player.getMainHandItem()), support, result);
         }
      );
      sleep(2000L);
      this.run(() -> log("placed: {} (expected {})", CarrierBlock.data(this.minecraft.level.getBlockState(target)), "bedrocknative:test_full"));
      this.commands(testFor(target, "bedrocknative:test_full"));
      this.screenshot("blocks-placed");
   }

   private void breakUnbreakable(BlockPos target) {
      this.commands(
         String.format(
            Locale.ROOT,
            "tp @s %.1f %d %.1f facing %.1f %.1f %.1f",
            target.getX() + 0.5,
            target.getY(),
            target.getZ() - 2.5,
            target.getX() + 0.5,
            target.getY() + 0.5,
            target.getZ() + 0.5
         )
      );
      sleep(1000L);
      this.run(
         () -> {
            this.minecraft.player.getInventory().setSelectedSlot(8);
            this.minecraft.player.lookAt(Anchor.EYES, Vec3.atCenterOf(target));
            log(
               "trying to break {} by hand, destroy progress per tick {} (expected 0: unbreakable)",
               CarrierBlock.data(this.minecraft.level.getBlockState(target)),
               this.minecraft.level.getBlockState(target).getDestroyProgress(this.minecraft.player, this.minecraft.level, target)
            );
            this.minecraft.options.keyAttack.setDown(true);
         }
      );
      sleep(4000L);
      this.run(() -> this.minecraft.options.keyAttack.setDown(false));
      sleep(1500L);
      log("unbreakable after 4 s of attack: {} (expected still the custom block, not air)", this.onClient(() -> this.minecraft.level.getBlockState(target)));
      this.commands(testFor(target, "bedrocknative:test_unbreakable"));
   }

   private static String setblock(BlockPos pos, String block) {
      return "setblock " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + block;
   }

   private static String fill(BlockPos from, BlockPos to, String block) {
      return "fill " + from.getX() + " " + from.getY() + " " + from.getZ() + " " + to.getX() + " " + to.getY() + " " + to.getZ() + " " + block;
   }

   private static String testFor(BlockPos pos, String block) {
      return "testforblock " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + block;
   }

   private void commands(String... commands) {
      for (String command : commands) {
         this.run(() -> this.minecraft.getConnection().sendCommand(command));
         sleep(400L);
      }

      sleep(600L);
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
      BedrockNative.LOGGER.info("Dev blocks test: " + message, args);
   }

   private static void sleep(long millis) {
      try {
         Thread.sleep(millis);
      } catch (InterruptedException var3) {
         Thread.currentThread().interrupt();
      }
   }
}
