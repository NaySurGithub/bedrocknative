package net.bedrocknative.client.net;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.content.AtlasReload;
import net.bedrocknative.client.content.BedrockSpriteSource;
import net.bedrocknative.client.content.BedrockTextures;
import net.bedrocknative.client.content.BedrockVanillaTextures;
import net.bedrocknative.content.BedrockTextureMap;
import net.bedrocknative.content.BedrockTextureSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluids;

final class DevTexturesTest {
   private static final List<String> ROW = List.of(
      "stone",
      "dirt",
      "grass_block",
      "oak_planks",
      "oak_log",
      "oak_leaves",
      "glass",
      "white_stained_glass",
      "ice",
      "sea_lantern",
      "magma",
      "prismarine",
      "command_block",
      "furnace",
      "crafting_table",
      "bookshelf",
      "tnt",
      "sculk",
      "emerald_block",
      "gold_block",
      "lapis_block",
      "iron_block",
      "diamond_ore",
      "honey_block"
   );
   private static final List<String> ROW_FRONT = List.of(
      "iron_chain",
      "lantern",
      "redstone_wire",
      "torch",
      "short_grass",
      "poppy",
      "water",
      "lava",
      "kelp",
      "respawn_anchor [\"respawn_anchor_charge\"=4]",
      "campfire",
      "copper_chain"
   );
   private static final List<String> ITEMS = List.of(
      "apple",
      "diamond",
      "golden_apple",
      "clock",
      "compass",
      "recovery_compass",
      "bow",
      "potion",
      "firework_star",
      "leather_horse_armor",
      "written_book",
      "bucket",
      "water_bucket",
      "cod_bucket",
      "egg",
      "zombie_spawn_egg",
      "iron_sword",
      "diamond_pickaxe",
      "empty_map",
      "bread",
      "carrot",
      "spyglass",
      "trident",
      "crossbow",
      "emerald_block",
      "gold_block",
      "lapis_block",
      "grass_block",
      "oak_leaves",
      "iron_chain"
   );
   private static final List<String> LAYER_CHECKS = List.of(
      "stone", "grass_block", "oak_leaves", "glass", "white_stained_glass", "ice", "tripwire", "iron_chain", "honey_block", "slime_block", "emerald_block"
   );
   private final Minecraft minecraft;
   private final List<BlockPos> viewpoints = new ArrayList<>();

   DevTexturesTest(Minecraft minecraft) {
      this.minecraft = minecraft;
   }

   void run() {
      this.waitSettled("join");
      this.report("joined");
      BedrockVanillaTextures.Timings joined = BedrockVanillaTextures.timings();
      log("join: prepared in {} ms, atlases rebuilt in {} ms", joined.prepareMillis(), joined.reloadMillis());
      this.checkTestPack();
      this.layers("Bedrock");
      BlockPos base = this.onClient(() -> this.minecraft.player.blockPosition());
      this.buildRow(base);
      this.view(base.offset(ROW.size() / 2, 3, -6), base.offset(ROW.size() / 2, 0, 3), "textures-row");
      this.view(base.offset(2, 2, -2), base.offset(5, 0, 4), "textures-row-near");
      this.viewAt(base.offset(ROW.indexOf("lapis_block"), 1, 0), base.offset(ROW.indexOf("lapis_block"), 0, 4), "textures-blocks-closeup");
      this.run(
         () -> {
            for (String name : List.of(
               "block/emerald_block", "block/gold_block", "block/lapis_block", "block/iron_block", "block/diamond_ore", "block/honey_block_side"
            )) {
               BedrockTextureSet set = BedrockVanillaTextures.armed(BedrockTextureMap.Atlas.BLOCKS);
               BedrockTextureSet.Sprite sprite = set == null ? null : set.sprites().get(name);
               if (sprite == null) {
                  log("close-up {}: no Bedrock sprite", name);
               } else {
                  long red = 0L;
                  long green = 0L;
                  long blue = 0L;
                  int count = sprite.image().width() * sprite.frameHeight();

                  for (int y = 0; y < sprite.frameHeight(); y++) {
                     for (int x = 0; x < sprite.image().width(); x++) {
                        int argb = sprite.image().pixel(x, y);
                        red += argb >> 16 & 0xFF;
                        green += argb >> 8 & 0xFF;
                        blue += argb & 0xFF;
                     }
                  }

                  log(
                     "close-up {}: {} ({}), {}x{}, mean colour ({}, {}, {}), corner {}, in the atlas from Bedrock {}",
                     name,
                     sprite.fromServerPack() ? "TEST PACK override" : "base game",
                     sprite.source(),
                     sprite.image().width(),
                     sprite.frameHeight(),
                     red / count,
                     green / count,
                     blue / count,
                     Integer.toHexString(sprite.image().pixel(0, 0)),
                     isBedrock(BedrockTextureMap.Atlas.BLOCKS, name)
                  );
               }
            }
         }
      );
      this.viewAt(base.offset(ROW.size() + 4, 30, 2), base.offset(ROW.size() / 2, 0, 3), "textures-row-far");
      this.inventory("textures-items");
      this.run(() -> BedrockVanillaTextures.setEnabled(false));
      this.waitSettled("Bedrock textures off");
      BedrockVanillaTextures.Timings off = BedrockVanillaTextures.timings();
      log(
         "switched off: Java textures back in {} ms, armed {} (expected false), block atlas stitch {}",
         off.reloadMillis(),
         BedrockVanillaTextures.isArmed(),
         BedrockSpriteSource.last(BedrockTextureMap.Atlas.BLOCKS)
      );
      this.layers("Java");
      this.view(base.offset(ROW.size() / 2, 3, -6), base.offset(ROW.size() / 2, 0, 3), "textures-row-java");
      this.inventory("textures-items-java");
      this.run(() -> BedrockVanillaTextures.setEnabled(true));
      this.waitSettled("Bedrock textures on again");
      BedrockVanillaTextures.Timings on = BedrockVanillaTextures.timings();
      log("switched on again: prepared in {} ms, atlases rebuilt in {} ms", on.prepareMillis(), on.reloadMillis());
      this.report("re-armed");
      this.run(BedrockTextures::packsChanged);
      this.waitSettled("unchanged stack");
      BedrockVanillaTextures.Timings unchanged = BedrockVanillaTextures.timings();
      log("same stack again: reloaded {} (expected false), checked in {} ms", unchanged.reloaded(), unchanged.prepareMillis());
      List<String> cleanup = new ArrayList<>(
         List.of(
            fill(base.offset(-2, 0, -8), base.offset(ROW.size() + 6, 4, 6), "air"),
            fill(base.offset(-2, -1, -8), base.offset(ROW.size() + 6, -1, 6), "stone"),
            "clear @s"
         )
      );
      this.viewpoints.forEach(pos -> cleanup.add(setblock(pos, "air")));
      this.commands(cleanup.toArray(String[]::new));
      int reloadsBefore = AtlasReload.runs();
      this.run(() -> this.minecraft.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE));
      sleep(1000L);
      this.waitFor("the screen after leaving", 30000L, () -> this.minecraft.level == null && this.minecraft.gui.screen() != null && !this.reloadOverlay());
      log(
         "after leaving: armed {} (expected true: kept until a Java world), restore pending {} (expected true), atlas reloads since {} (expected 0), overlay {}",
         BedrockVanillaTextures.isArmed(),
         BedrockVanillaTextures.restorePending(),
         AtlasReload.runs() - reloadsBefore,
         this.reloadOverlay()
      );
      this.run(() -> BedrockVanillaTextures.restoreForJavaWorld("the textures dev test"));
      this.waitFor(
         "Java textures after the restore",
         60000L,
         () -> !BedrockVanillaTextures.isArmed()
            && BedrockSpriteSource.last(BedrockTextureMap.Atlas.BLOCKS) == null
            && !this.reloadOverlay()
            && !BedrockVanillaTextures.busy()
      );
      this.run(
         () -> {
            TextureAtlasSprite emerald = this.minecraft
               .getAtlasManager()
               .getAtlasOrThrow(AtlasIds.BLOCKS)
               .getSprite(Identifier.withDefaultNamespace("block/emerald_block"));
            log(
               "after the restore: armed {}, block atlas stitched with Bedrock {}, item atlas {}; emerald block sprite {}x{} (expected 16x16, Java's); overlay {}",
               BedrockVanillaTextures.isArmed(),
               BedrockSpriteSource.last(BedrockTextureMap.Atlas.BLOCKS) != null,
               BedrockSpriteSource.last(BedrockTextureMap.Atlas.ITEMS) != null,
               emerald.contents().width(),
               emerald.contents().height(),
               this.reloadOverlay()
            );
         }
      );
      log("done");
   }

   void report(String when) {
      this.run(
         () -> {
            for (BedrockTextureMap.Atlas atlas : BedrockTextureMap.Atlas.values()) {
               BedrockTextureSet set = BedrockVanillaTextures.armed(atlas);
               BedrockSpriteSource.Stitch stitch = BedrockSpriteSource.last(atlas);
               TextureAtlas texture = this.minecraft
                  .getAtlasManager()
                  .getAtlasOrThrow(atlas == BedrockTextureMap.Atlas.BLOCKS ? AtlasIds.BLOCKS : AtlasIds.ITEMS);
               int javaSprites = set != null ? set.stats().javaSprites() : 0;
               int bedrock = stitch != null ? stitch.bedrock() : 0;
               log(
                  "{} {} atlas: {} of {} Java sprites from Bedrock, {} Java's; prepared: {}; stitch: {}; atlas {}x{} with {} mip levels",
                  when,
                  atlas.id(),
                  bedrock,
                  javaSprites,
                  javaSprites - bedrock,
                  set != null ? set.stats() : "NOT ARMED",
                  stitch,
                  texture.getTexture().getWidth(0),
                  texture.getTexture().getHeight(0),
                  texture.getTexture().getMipLevels()
               );
            }
         }
      );
   }

   void checkTestPack() {
      this.run(
         () -> {
            this.check(
               BedrockTextureMap.Atlas.BLOCKS,
               "block/emerald_block",
               "overridden by path at 32x: 32 wide, magenta corner",
               spritex -> spritex.fromServerPack() && spritex.image().width() == 32 && spritex.image().pixel(0, 0) == -65281
            );
            this.check(
               BedrockTextureMap.Atlas.BLOCKS,
               "block/gold_block",
               "re-pointed by short name: bedrocknative_gold_repointed",
               spritex -> spritex.fromServerPack() && spritex.source().contains("bedrocknative_gold_repointed")
            );
            this.check(
               BedrockTextureMap.Atlas.BLOCKS,
               "block/lapis_block",
               "animated by the pack's flipbook: 4 frames, 10 ticks, not blended",
               spritex -> spritex.animation() != null
                  && spritex.animation().frames().size() == 4
                  && spritex.animation().ticksPerFrame() == 10
                  && !spritex.animation().interpolate()
            );
            this.check(
               BedrockTextureMap.Atlas.BLOCKS,
               "block/water_still",
               "vanilla flipbook with Java's alpha",
               spritex -> spritex.animation() != null && spritex.keepJavaAlpha()
            );
            this.check(
               BedrockTextureMap.Atlas.ITEMS,
               "item/apple",
               "overridden by path: orange corner",
               spritex -> spritex.fromServerPack() && spritex.image().pixel(0, 0) == -32768
            );
            this.check(
               BedrockTextureMap.Atlas.ITEMS,
               "item/diamond",
               "re-pointed by short name",
               spritex -> spritex.fromServerPack() && spritex.source().contains("bedrocknative_diamond_repointed")
            );
            TextureAtlas blocks = this.minecraft.getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS);

            for (String name : List.of(
               "block/lapis_block",
               "block/water_still",
               "block/lava_still",
               "block/sea_lantern",
               "block/magma",
               "block/command_block_front",
               "block/fire_0",
               "block/stone"
            )) {
               TextureAtlasSprite sprite = blocks.getSprite(Identifier.withDefaultNamespace(name));
               log(
                  "atlas sprite {}: {}x{}, animated {}, from Bedrock {}",
                  name,
                  sprite.contents().width(),
                  sprite.contents().height(),
                  sprite.contents().isAnimated(),
                  isBedrock(BedrockTextureMap.Atlas.BLOCKS, name)
               );
            }

            TextureAtlas items = this.minecraft.getAtlasManager().getAtlasOrThrow(AtlasIds.ITEMS);

            for (String name : List.of(
               "item/apple",
               "item/diamond",
               "item/clock_00",
               "item/compass_16",
               "item/leather_helmet_overlay",
               "item/leather_chestplate_overlay",
               "item/knowledge_book"
            )) {
               TextureAtlasSprite sprite = items.getSprite(Identifier.withDefaultNamespace(name));
               log(
                  "atlas sprite {}: {}x{}, from Bedrock {}",
                  name,
                  sprite.contents().width(),
                  sprite.contents().height(),
                  isBedrock(BedrockTextureMap.Atlas.ITEMS, name)
               );
            }
         }
      );
   }

   private static boolean isBedrock(BedrockTextureMap.Atlas atlas, String name) {
      BedrockSpriteSource.Stitch stitch = BedrockSpriteSource.last(atlas);
      return stitch != null && stitch.isBedrock(name);
   }

   private void check(BedrockTextureMap.Atlas atlas, String name, String expected, Predicate<BedrockTextureSet.Sprite> test) {
      BedrockTextureSet set = BedrockVanillaTextures.armed(atlas);
      BedrockTextureSet.Sprite sprite = set == null ? null : set.sprites().get(name);
      boolean ok = sprite != null && test.test(sprite) && isBedrock(atlas, name);
      log(
         "test pack {}: {} ({}; from {}, server pack {}, {}x{}, animation {})",
         name,
         ok ? "OK" : "WRONG",
         expected,
         sprite == null ? "-" : sprite.source(),
         sprite != null && sprite.fromServerPack(),
         sprite == null ? 0 : sprite.image().width(),
         sprite == null ? 0 : sprite.frameHeight(),
         sprite == null ? null : sprite.animation()
      );
   }

   void layers(String textures) {
      this.run(() -> {
         Map<String, String> layers = new LinkedHashMap<>();
         LAYER_CHECKS.forEach(name -> {
            Block block = (Block)BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(name));
            List<BlockStateModelPart> parts = new ArrayList<>();
            this.minecraft.getModelManager().getBlockStateModelSet().get(block.defaultBlockState()).collectParts(RandomSource.create(0L), parts);
            TreeSet<String> found = new TreeSet<>();

            for (BlockStateModelPart part : parts) {
               part.getQuads(null).forEach(quad -> found.add(quad.materialInfo().layer().label()));

               for (Direction direction : Direction.values()) {
                  part.getQuads(direction).forEach(quad -> found.add(quad.materialInfo().layer().label()));
               }
            }

            layers.put(name, String.join("+", found));
         });
         layers.put("water", this.minecraft.getModelManager().getFluidStateModelSet().get(Fluids.WATER.defaultFluidState()).layer().label());
         layers.put("lava", this.minecraft.getModelManager().getFluidStateModelSet().get(Fluids.LAVA.defaultFluidState()).layer().label());
         log("chunk layers with {} textures: {}", textures, layers);
      });
   }

   private void buildRow(BlockPos base) {
      this.commands(
         "gamemode creative",
         "time set noon",
         "weather clear",
         "clear @s",
         fill(base.offset(-2, -1, -8), base.offset(ROW.size() + 6, -1, 6), "stone"),
         fill(base.offset(-2, 0, -8), base.offset(ROW.size() + 6, 4, 6), "air")
      );
      List<String> commands = new ArrayList<>();

      for (int i = 0; i < ROW.size(); i++) {
         commands.add(setblock(base.offset(i, 0, 4), ROW.get(i)));
      }

      BlockPos front = base.offset(0, 0, 2);

      for (int i = 0; i < ROW_FRONT.size(); i++) {
         String block = ROW_FRONT.get(i);
         BlockPos pos = front.offset(i * 2, 0, 0);
         if (block.equals("water") || block.equals("kelp")) {
            commands.add(fill(pos.offset(-1, -1, -1), pos.offset(1, 1, 1), "glass"));
            commands.add(setblock(pos.above(), "air"));
            commands.add(setblock(pos, "water"));
            if (block.equals("kelp")) {
               commands.add(setblock(pos.below(), "sand"));
               commands.add(setblock(pos, "kelp"));
            }
         } else if (block.equals("lava")) {
            commands.add(fill(pos.offset(-1, -1, -1), pos.offset(1, 1, 1), "stone"));
            commands.add(setblock(pos.above(), "air"));
            commands.add(setblock(pos, "lava"));
         } else if (!block.equals("short_grass") && !block.equals("poppy")) {
            commands.add(setblock(pos, block));
         } else {
            commands.add(setblock(pos.below(), "grass_block"));
            commands.add(setblock(pos, block));
         }
      }

      this.commands(commands.toArray(String[]::new));
      sleep(2000L);
   }

   private void inventory(String name) {
      List<String> gives = new ArrayList<>(List.of("clear @s"));
      ITEMS.forEach(item -> gives.add("give @s " + item));
      gives.add("loot give @s loot \"bedrocknative/dyed_armor\"");
      this.commands(gives.toArray(String[]::new));
      sleep(1000L);
      this.run(() -> this.minecraft.gui.setScreen(new InventoryScreen(this.minecraft.player)));
      sleep(1500L);
      this.screenshot(name);
      this.run(() -> this.minecraft.gui.setScreen(null));
      sleep(500L);
   }

   private void view(BlockPos eye, BlockPos target, String name) {
      this.viewAt(eye, target, name);
   }

   private void viewAt(BlockPos feet, BlockPos target, String name) {
      this.viewpoints.add(feet.below());
      this.commands(
         setblock(feet.below(), "barrier"),
         String.format(
            Locale.ROOT,
            "tp @s %.1f %d %.1f facing %.1f %.1f %.1f",
            feet.getX() + 0.5,
            feet.getY(),
            feet.getZ() + 0.5,
            target.getX() + 0.5,
            target.getY() + 0.5,
            target.getZ() + 0.5
         )
      );
      sleep(2500L);
      this.screenshot(name);
   }

   void waitSettled(String what) {
      sleep(500L);
      this.waitFor(what, 120000L, () -> !BedrockVanillaTextures.busy() && !this.reloadOverlay());
      sleep(1500L);
   }

   private boolean reloadOverlay() {
      return this.minecraft.gui.overlay() != null;
   }

   void waitFor(String what, long timeoutMillis, BooleanSupplier done) {
      long started = System.currentTimeMillis();

      while (!this.onClient(done::getAsBoolean)) {
         if (System.currentTimeMillis() - started > timeoutMillis) {
            log("WAITED {} s for {} without success", timeoutMillis / 1000L, what);
            return;
         }

         sleep(100L);
      }

      log("{}: settled after {} ms", what, System.currentTimeMillis() - started);
   }

   private static String setblock(BlockPos pos, String block) {
      return "setblock " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + block;
   }

   private static String fill(BlockPos from, BlockPos to, String block) {
      return "fill " + from.getX() + " " + from.getY() + " " + from.getZ() + " " + to.getX() + " " + to.getY() + " " + to.getZ() + " " + block;
   }

   private void commands(String... commands) {
      for (String command : commands) {
         this.run(() -> this.minecraft.getConnection().sendCommand(command));
         sleep(250L);
      }

      sleep(600L);
   }

   void screenshot(String name) {
      this.run(() -> Screenshot.grab(this.minecraft.gameDirectory, name + ".png", this.minecraft.gameRenderer.mainRenderTarget(), 1, message -> {}));
      sleep(500L);
   }

   <T> T onClient(Supplier<T> task) {
      return CompletableFuture.supplyAsync(task, this.minecraft::execute).join();
   }

   void run(Runnable task) {
      this.onClient(() -> {
         task.run();
         return null;
      });
   }

   static void log(String message, Object... args) {
      BedrockNative.LOGGER.info("Dev textures test: " + message, args);
   }

   static void sleep(long millis) {
      try {
         Thread.sleep(millis);
      } catch (InterruptedException var3) {
         Thread.currentThread().interrupt();
      }
   }
}
