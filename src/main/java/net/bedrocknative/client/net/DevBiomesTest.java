package net.bedrocknative.client.net;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.world.BedrockBiomes;
import net.bedrocknative.world.biome.BiomeMappings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerFactory;

final class DevBiomesTest {
   private final Minecraft minecraft;
   private final List<String> failures = new ArrayList<>();
   private int checks;

   DevBiomesTest(Minecraft minecraft) {
      this.minecraft = minecraft;
   }

   void run() {
      try {
         this.test();
      } catch (RuntimeException var2) {
         this.failures.add("exception: " + var2);
         BedrockNative.LOGGER.error("Dev biomes test failed with an exception", var2);
      }

      this.minecraft.execute(() -> Screenshot.grab(this.minecraft, false));
      BedrockNative.LOGGER
         .info(
            "Dev biomes test: {} of {} checks passed{}",
            new Object[]{this.checks - this.failures.size(), this.checks, this.failures.isEmpty() ? "" : "; failed: " + this.failures}
         );
      BedrockNative.LOGGER.info("Dev biomes test: done");
   }

   private void test() {
      int[] ids = BiomeMappings.bedrockIds();
      this.check("the table knows 89 vanilla Bedrock biome ids (" + ids.length + ")", ids.length == 89);
      List<String> lines = this.onClient(
         () -> {
            PalettedContainerFactory factory = PalettedContainerFactory.create(this.minecraft.level.registryAccess());
            BedrockBiomes biomes = new BedrockBiomes(this.minecraft.level.registryAccess(), new Int2ObjectOpenHashMap(), new Int2ObjectOpenHashMap());
            Set<Integer> plainsGrass = new HashSet<>();
            List<String> out = new ArrayList<>();
            int approximatex = 0;
            int approximateNotPlainsTint = 0;
            int wrongx = 0;
            int[] section = new int[4096];

            for (int id : ids) {
               Arrays.fill(section, id);
               PalettedContainer<Holder<Biome>> container = biomes.container(factory, section);
               Holder<Biome> holder = (Holder<Biome>)container.get(0, 0, 0);
               Biome biome = (Biome)holder.value();
               String name = BiomeMappings.bedrockName(id);
               boolean approx = BiomeMappings.isApproximate(id);
               boolean expected = BiomeMappings.toJava(id) != null && holder.is(BiomeMappings.toJava(id));
               if (name.equals("plains")) {
                  plainsGrass.add(biome.getGrassColor(0.0, 0.0));
               }

               if (!expected) {
                  wrongx++;
               }

               out.add(
                  String.format(
                     "%3d %-32s -> %-34s %s grass %06X foliage %06X water %06X",
                     id,
                     name,
                     holder.getRegisteredName(),
                     approx ? "approx" : "exact ",
                     biome.getGrassColor(0.0, 0.0) & 16777215,
                     biome.getFoliageColor() & 16777215,
                     biome.getWaterColor() & 16777215
                  )
               );
               if (approx) {
                  approximatex++;
                  if (!holder.getRegisteredName().equals("minecraft:plains") && !plainsGrass.contains(biome.getGrassColor(0.0, 0.0))) {
                     approximateNotPlainsTint++;
                  }
               }
            }

            out.add("SUMMARY approximate " + approximatex + " not plains tint " + approximateNotPlainsTint + " not the table's biome " + wrongx);
            return out;
         }
      );

      for (String line : lines) {
         BedrockNative.LOGGER.info("Dev biomes test: {}", line);
      }

      String summary = lines.get(lines.size() - 1);
      String[] parts = summary.split(" ");
      int approximate = Integer.parseInt(parts[2]);
      int tinted = Integer.parseInt(parts[6]);
      int wrong = Integer.parseInt(parts[parts.length - 1]);
      this.check("27 Bedrock-only ids use the closest-climate table (" + approximate + ")", approximate == 27);
      this.check("every id became the Java biome its table names (" + wrong + " did not)", wrong == 0);
      this.check("most Bedrock-only ids keep a tint other than plains' (" + tinted + " of " + approximate + ")", tinted >= approximate / 2);
   }

   private void check(String what, boolean ok) {
      this.checks++;
      if (ok) {
         BedrockNative.LOGGER.info("Dev biomes test: ok: {}", what);
      } else {
         this.failures.add(what);
         BedrockNative.LOGGER.warn("Dev biomes test FAILED: {}", what);
      }
   }

   private <T> T onClient(Supplier<T> supplier) {
      CompletableFuture<T> future = new CompletableFuture<>();
      this.minecraft.execute(() -> {
         try {
            future.complete(supplier.get());
         } catch (RuntimeException var3) {
            future.completeExceptionally(var3);
         }
      });

      try {
         return future.get(30L, TimeUnit.SECONDS);
      } catch (Exception var4) {
         throw new IllegalStateException(var4);
      }
   }
}
