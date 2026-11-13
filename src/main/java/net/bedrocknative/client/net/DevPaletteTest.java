package net.bedrocknative.client.net;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.world.SpawnCheck;
import net.bedrocknative.world.block.BlockMappings;
import net.bedrocknative.world.block.custom.CarrierBlock;
import net.bedrocknative.world.block.custom.CustomBlockState;
import net.bedrocknative.world.chunk.BlockIdCheck;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;

final class DevPaletteTest {
   private static final List<String> BLOCKS = List.of(
      "minecraft:stone",
      "minecraft:grass_block",
      "minecraft:dirt",
      "minecraft:oak_planks",
      "minecraft:glass",
      "minecraft:sea_lantern",
      "minecraft:white_wool_stairs",
      "minecraft:red_concrete_slab",
      "minecraft:black_wool_stairs",
      "minecraft:white_concrete_stairs",
      "bedrocknative:ruby_block",
      "bedrocknative:test_full"
   );
   private final Minecraft minecraft;
   private final List<String> failures = new ArrayList<>();
   private int checks;

   DevPaletteTest(Minecraft minecraft) {
      this.minecraft = minecraft;
   }

   void run() {
      try {
         this.test();
      } catch (RuntimeException e) {
         this.failures.add("exception: " + e);
         BedrockNative.LOGGER.error("Dev palette test failed with an exception", e);
      }

      BedrockNative.LOGGER
         .info(
            "Dev palette test: {} of {} checks passed{}",
            new Object[]{this.checks - this.failures.size(), this.checks, this.failures.isEmpty() ? "" : "; failed: " + this.failures}
         );
      BedrockNative.LOGGER.info("Dev palette test: done");
   }

   private void test() {
      String numbering = BlockMappings.get().numbering();
      BedrockNative.LOGGER.info("Dev palette test: numbering {}", numbering);
      this.check("the numbering keeps every vanilla block BDS defines (" + numbering + ")", !numbering.contains("does not define"));
      waitFor(() -> BlockIdCheck.last() != null && SpawnCheck.last() != null, 30000L);
      BlockIdCheck.Report report = BlockIdCheck.last();
      this.check("block ids check made" + (report == null ? "" : ": " + report.text()), report != null);
      if (report != null) {
         this.check("block ids read as a world", !report.suspicious());
      }

      SpawnCheck.Look look = SpawnCheck.last();
      this.check("spawn check made" + (look == null ? "" : ": " + look.text()), look != null && !look.text().contains("did not arrive"));
      if (look != null) {
         this.check("the player is not inside blocks", !look.inside());
      }

      BlockPos base = this.onClient(() -> this.minecraft.player.blockPosition().offset(3, 2, -BLOCKS.size() / 2));

      for (int i = 0; i < BLOCKS.size(); i++) {
         BlockPos pos = base.offset(0, 0, i);
         this.command("setblock " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + BLOCKS.get(i));
      }

      sleep(2500L);

      for (int i = 0; i < BLOCKS.size(); i++) {
         String name = BLOCKS.get(i);
         BlockPos pos = base.offset(0, 0, i);
         BlockState state = this.onClient(() -> this.minecraft.level.getBlockState(pos));
         this.check(name + " reads back as " + describe(state), readsAs(state, name));
      }

      BlockPos last = base.offset(0, 0, BLOCKS.size() - 1);
      this.command("fill " + base.getX() + " " + base.getY() + " " + base.getZ() + " " + last.getX() + " " + last.getY() + " " + last.getZ() + " air");
      sleep(1000L);
   }

   private static boolean readsAs(BlockState state, String bedrockName) {
      if (!bedrockName.startsWith("minecraft:")) {
         CustomBlockState custom = CarrierBlock.data(state);
         return custom != null && custom.state().name().equals(bedrockName);
      } else {
         BlockState expected = BlockMappings.get().byName(bedrockName);
         return expected != null && expected.getBlock() == state.getBlock();
      }
   }

   private static String describe(BlockState state) {
      CustomBlockState custom = CarrierBlock.data(state);
      return custom != null ? custom.state().toStateString() : BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
   }

   private void command(String command) {
      this.minecraft.execute(() -> this.minecraft.getConnection().sendCommand(command));
      sleep(300L);
   }

   private void check(String what, boolean ok) {
      this.checks++;
      if (ok) {
         BedrockNative.LOGGER.info("Dev palette test: ok: {}", what);
      } else {
         this.failures.add(what);
         BedrockNative.LOGGER.warn("Dev palette test FAILED: {}", what);
      }
   }

   private static void waitFor(BooleanSupplier condition, long millis) {
      long end = System.currentTimeMillis() + millis;

      while (!condition.getAsBoolean() && System.currentTimeMillis() < end) {
         sleep(250L);
      }
   }

   private <T> T onClient(Supplier<T> task) {
      return CompletableFuture.supplyAsync(task, this.minecraft::execute).join();
   }

   private static void sleep(long millis) {
      try {
         Thread.sleep(millis);
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
      }
   }
}
