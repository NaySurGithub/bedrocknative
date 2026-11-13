package net.bedrocknative.client.net;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.bedrocknative.client.content.BedrockTextures;
import net.bedrocknative.client.content.BedrockVanillaTextures;
import net.bedrocknative.content.FolderPack;
import net.bedrocknative.content.PackSource;
import net.bedrocknative.content.PackStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.thread.BlockableEventLoop;

public final class DevTexturesOffline {
   private static final List<String> SPRITES = List.of(
      "block/stone",
      "block/dirt",
      "block/grass_block_top",
      "block/grass_block_side",
      "block/grass_block_side_overlay",
      "block/oak_planks",
      "block/oak_log",
      "block/oak_leaves",
      "block/glass",
      "block/white_stained_glass",
      "block/ice",
      "block/water_still",
      "block/water_flow",
      "block/lava_still",
      "block/sea_lantern",
      "block/magma",
      "block/prismarine",
      "block/command_block_front",
      "block/fire_0",
      "block/furnace_front",
      "block/crafting_table_front",
      "block/bookshelf",
      "block/tnt_side",
      "block/sculk",
      "block/iron_chain",
      "block/copper_chain",
      "block/redstone_dust_line0",
      "block/tripwire",
      "block/loom_top",
      "block/kelp",
      "block/respawn_anchor_top",
      "block/water_overlay",
      "block/emerald_block",
      "block/gold_block",
      "block/lapis_block",
      "item/apple",
      "item/diamond",
      "item/golden_apple",
      "item/clock_00",
      "item/clock_32",
      "item/compass_00",
      "item/bow",
      "item/potion",
      "item/firework_star",
      "item/firework_star_overlay",
      "item/leather_helmet",
      "item/leather_helmet_overlay",
      "item/leather_chestplate_overlay",
      "item/written_book",
      "item/water_bucket",
      "item/zombie_spawn_egg",
      "item/iron_sword",
      "item/trident",
      "item/knowledge_book"
   );
   private final Minecraft minecraft;
   private final List<Path> folders;
   private final DevTexturesTest test;

   private DevTexturesOffline(Minecraft minecraft, List<Path> folders) {
      this.minecraft = minecraft;
      this.folders = folders;
      this.test = new DevTexturesTest(minecraft);
   }

   public static void start() {
      String packs = System.getProperty("bedrocknative.devTexturesPacks", "");
      List<Path> folders = new ArrayList<>();

      for (String folder : packs.split(File.pathSeparator)) {
         if (!folder.isBlank()) {
            folders.add(Path.of(folder.trim()));
         }
      }

      Thread.ofVirtual().start(() -> {
         try {
            new DevTexturesOffline(Minecraft.getInstance(), folders).run();
         } catch (RuntimeException var2) {
            DevTexturesTest.log("FAILED: {}", var2.toString());
            throw var2;
         }
      });
   }

   private void run() {
      this.test.waitFor("the game to load", 1200000L, () -> this.minecraft.isGameLoadFinished() && this.minecraft.gui.overlay() == null);
      DevTexturesTest.sleep(2000L);
      DevTexturesTest.log("packs (top first): {}", this.folders);
      this.test.run(() -> {
         List<PackSource> packs = new ArrayList<>();
         this.folders.forEach(folder -> packs.add(new FolderPack(folder)));
         PackStack.useServerPacks(packs);
         BedrockVanillaTextures.start();
         BedrockTextures.packsChanged();
      });
      this.test.waitSettled("armed");
      BedrockVanillaTextures.Timings timings = BedrockVanillaTextures.timings();
      DevTexturesTest.log("join: prepared in {} ms, atlases rebuilt in {} ms", timings.prepareMillis(), timings.reloadMillis());
      this.test.report("joined");
      this.test.checkTestPack();
      this.test.layers("Bedrock");
      this.dumpAtlases("bedrock");
      this.test.run(() -> this.minecraft.gui.setScreen(new DevTexturesOffline.IconScreen()));
      DevTexturesTest.sleep(3000L);
      this.test.screenshot("textures-offline-icons");
      DevTexturesTest.sleep(1300L);
      this.test.screenshot("textures-offline-icons-later");
      this.test.run(BedrockTextures::packsChanged);
      this.test.waitSettled("unchanged stack");
      DevTexturesTest.log("same stack again: reloaded {} (expected false)", BedrockVanillaTextures.timings().reloaded());
      this.test.run(() -> {
         this.minecraft.reloadResourcePacks();
         this.dropAllTasks();
      });
      this.test.waitFor("a reload whose queued steps were dropped", 120000L, () -> this.minecraft.gui.overlay() == null);
      DevTexturesTest.log("reload after dropped tasks: overlay gone {} (expected true)", this.test.onClient(() -> this.minecraft.gui.overlay() == null));
      this.test.run(() -> {
         BedrockVanillaTextures.release();
         PackStack.useServerPacks(List.of());
      });
      this.test.waitSettled("released");
      DevTexturesTest.log(
         "released: armed {} (expected false), Java textures back in {} ms", BedrockVanillaTextures.isArmed(), BedrockVanillaTextures.timings().reloadMillis()
      );
      this.test.report("released");
      this.test.layers("Java");
      this.dumpAtlases("java");
      this.test.run(() -> this.minecraft.gui.setScreen(new DevTexturesOffline.IconScreen()));
      DevTexturesTest.sleep(3000L);
      this.test.screenshot("textures-offline-icons-java");
      DevTexturesTest.log("done");
      DevTexturesTest.sleep(1000L);
      this.test.run(this.minecraft::stop);
   }

   private void dropAllTasks() {
      try {
         Method drop = BlockableEventLoop.class.getDeclaredMethod("dropAllTasks");
         drop.setAccessible(true);
         drop.invoke(this.minecraft);
      } catch (ReflectiveOperationException var2) {
         DevTexturesTest.log("couldn't drop the client's tasks: {}", var2.toString());
      }
   }

   private void dumpAtlases(String label) {
      Path dir = this.minecraft.gameDirectory.toPath().resolve("screenshots").resolve("textures-atlas-" + label);
      this.test.run(() -> {
         try {
            Files.createDirectories(dir);

            for (Identifier atlas : List.of(AtlasIds.BLOCKS, AtlasIds.ITEMS)) {
               TextureAtlas texture = this.minecraft.getAtlasManager().getAtlasOrThrow(atlas);
               texture.dumpContents(texture.location(), dir);
            }
         } catch (Exception var5) {
            DevTexturesTest.log("atlas dump failed: {}", var5.toString());
         }
      });
      DevTexturesTest.sleep(1000L);
      DevTexturesTest.log("atlases dumped to {}", dir);
   }

   private static final class IconScreen extends Screen {
      IconScreen() {
         super(Component.literal("Bedrock textures"));
      }

      public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
         graphics.fill(0, 0, this.width, this.height, -8355712);
         Minecraft minecraft = Minecraft.getInstance();
         int columns = Math.max(1, (this.width - 8) / 36);

         for (int i = 0; i < DevTexturesOffline.SPRITES.size(); i++) {
            String name = DevTexturesOffline.SPRITES.get(i);
            TextureAtlas atlas = minecraft.getAtlasManager().getAtlasOrThrow(name.startsWith("item/") ? AtlasIds.ITEMS : AtlasIds.BLOCKS);
            TextureAtlasSprite sprite = atlas.getSprite(Identifier.withDefaultNamespace(name));
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, 8 + i % columns * 36, 8 + i / columns * 36, 32, 32);
         }
      }

      public boolean isPauseScreen() {
         return false;
      }
   }
}
