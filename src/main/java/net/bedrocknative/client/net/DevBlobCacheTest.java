package net.bedrocknative.client.net;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.world.chunk.cache.BlobTracker;
import net.bedrocknative.world.chunk.cache.CachedChunkTransformer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

final class DevBlobCacheTest {
   private static final int RADIUS = 6;
   private static final int RENDER_DISTANCE = renderDistanceToAsk();
   private static final String[] LABELS = new String[]{"cold", "warm", "off"};
   private static final int SETTLED_RADIUS = 2;
   private final Minecraft minecraft;
   private final Supplier<CachedChunkTransformer.Summary> blobCache;
   private final List<String> failures = new ArrayList<>();
   private int checks;
   private String currentLabel = "run";

   private static int renderDistanceToAsk() {
      try {
         return Integer.parseInt(System.getenv().getOrDefault("BN_BLOBCACHE_RD", "10"));
      } catch (NumberFormatException var1) {
         return 10;
      }
   }

   DevBlobCacheTest(Minecraft minecraft, Supplier<CachedChunkTransformer.Summary> blobCache) {
      this.minecraft = minecraft;
      this.blobCache = blobCache;
   }

   void run() {
      String name = System.getProperty("bedrocknative.devTest", "blobcache");
      String label = name.contains("-") ? name.substring(name.indexOf(45) + 1) : "run";
      int initialDistance = this.onClient(() -> (Integer)this.minecraft.options.renderDistance().get());

      try {
         if (RENDER_DISTANCE > 0) {
            this.minecraft.execute(() -> this.minecraft.options.renderDistance().set(RENDER_DISTANCE));
         }

         this.test(label);
      } catch (RuntimeException var8) {
         this.failures.add("exception: " + var8);
         BedrockNative.LOGGER.error("Dev blobcache test failed with an exception", var8);
      } finally {
         this.minecraft.execute(() -> this.minecraft.options.renderDistance().set(initialDistance));
      }

      this.minecraft.execute(() -> Screenshot.grab(this.minecraft, false));
      BedrockNative.LOGGER
         .info(
            "Dev blobcache test: {} of {} checks passed{}",
            new Object[]{this.checks - this.failures.size(), this.checks, this.failures.isEmpty() ? "" : "; failed: " + this.failures}
         );
      BedrockNative.LOGGER.info("Dev blobcache test: done");
   }

   private void test(String label) {
      this.currentLabel = label;
      int[] center = this.onClient(() -> new int[]{this.minecraft.player.getBlockX() >> 4, this.minecraft.player.getBlockZ() >> 4});
      int total = 169;
      Map<String, Long> digests = new LinkedHashMap<>();
      long started = System.currentTimeMillis();
      int stableSince = 0;
      int loaded = 0;

      while (System.currentTimeMillis() - started < 90000L) {
         Map<String, Long> now = this.onClient(() -> this.digests(center));
         CachedChunkTransformer.Summary summary = this.blobCache.get();
         boolean quiet = summary == null || summary.outstanding() == 0;
         if (now.size() == total && quiet && now.equals(digests)) {
            stableSince++;
         } else {
            stableSince = 0;
         }

         digests.clear();
         digests.putAll(now);
         loaded = now.size();
         if (stableSince >= 3) {
            break;
         }

         sleep(1000L);
      }

      long waited = System.currentTimeMillis() - started;
      long digest = 17L;

      for (Entry<String, Long> entry : digests.entrySet()) {
         digest = digest * 31L + entry.getKey().hashCode();
         digest = digest * 31L + entry.getValue();
      }

      this.check("no chunk missing around the spawn (" + loaded + " of " + total + " loaded after " + waited + " ms)", loaded == total);
      CachedChunkTransformer.Summary summaryx = this.blobCache.get();
      if (summaryx == null) {
         BedrockNative.LOGGER.info("Dev blobcache test: {}: the server named no blobs (full chunks, as with the cache off)", label);
         this.check("the cache-off run saw no blob traffic", !label.equals("cold") && !label.equals("warm"));
      } else {
         BlobTracker.Stats blobs = summaryx.blobs();
         BedrockNative.LOGGER
            .info(
               "Dev blobcache test: {}: {} blobs found in the cache, {} missing, {} received ({} KB), retries {}, given up {}, invalid {}, unexpected {}; {} LevelChunk and {} SubChunk packets, {} sub-chunks asked again, {} lost, {} columns lost",
               new Object[]{
                  label,
                  blobs.hits(),
                  blobs.misses(),
                  blobs.received(),
                  blobs.receivedBytes() >> 10,
                  blobs.retries(),
                  blobs.giveUps(),
                  blobs.invalid(),
                  blobs.unexpected(),
                  summaryx.levelChunks(),
                  summaryx.subChunkPackets(),
                  summaryx.subChunksResent(),
                  summaryx.subChunksLost(),
                  summaryx.columnsLost()
               }
            );
         this.check(
            "every blob arrived (none given up, none invalid, no sub-chunk or column lost)",
            blobs.giveUps() == 0L && blobs.invalid() == 0L && summaryx.subChunksLost() == 0 && summaryx.columnsLost() == 0
         );
         if (label.equals("warm")) {
            this.check("the warm run found blobs in the disk cache (" + blobs.hits() + " found, " + blobs.misses() + " missing)", blobs.hits() > blobs.misses());
         }

         if (label.equals("cold")) {
            this.check("the cold run asked the server for blobs (" + blobs.misses() + " missing)", blobs.misses() > 0L && blobs.received() > 0L);
         }
      }

      Path folder = this.minecraft.gameDirectory.toPath().resolve("blobcache");

      try {
         Files.createDirectories(folder);
         List<String> lines = new ArrayList<>();
         lines.add("center " + center[0] + " " + center[1]);
         lines.add("digest " + Long.toHexString(digest));
         digests.forEach((chunk, value) -> lines.add(chunk + " " + Long.toHexString(value)));
         Files.write(folder.resolve("digest-" + label + ".txt"), lines, StandardCharsets.UTF_8);
         List<String> partLines = new ArrayList<>();
         this.onClient(() -> this.parts(center))
            .forEach((chunk, value) -> partLines.add(chunk + " " + Long.toHexString(value[0]) + " " + Long.toHexString(value[1])));
         Files.write(folder.resolve("parts-" + label + ".txt"), partLines, StandardCharsets.UTF_8);
         this.dumpBlocks(folder, label, center);
         this.dumpExtraChunks(folder, label);
      } catch (IOException var19) {
         this.failures.add("cannot write the digests: " + var19);
      }

      BedrockNative.LOGGER.info("Dev blobcache test: {}: {} chunks, digest {}", new Object[]{label, digests.size(), Long.toHexString(digest)});

      for (String other : LABELS) {
         if (!other.equals(label)) {
            this.compare(folder.resolve("digest-" + other + ".txt"), other, center, digest, digests);
         }
      }
   }

   private void dumpBlocks(Path folder, String label, int[] center) throws IOException {
      List<String> lines = this.onClient(() -> {
         List<String> result = new ArrayList<>();

         for (int cx = center[0] - 1; cx <= center[0] + 1; cx++) {
            for (int cz = center[1] - 1; cz <= center[1] + 1; cz++) {
               LevelChunk chunk = this.minecraft.level.getChunkSource().getChunk(cx, cz, false);
               if (chunk != null) {
                  int minY = chunk.getMinY();

                  for (int i = 0; i < chunk.getSections().length; i++) {
                     LevelChunkSection section = chunk.getSections()[i];
                     if (!section.hasOnlyAir()) {
                        for (int y = 0; y < 16; y++) {
                           for (int z = 0; z < 16; z++) {
                              for (int x = 0; x < 16; x++) {
                                 BlockState state = section.getBlockState(x, y, z);
                                 if (!state.isAir()) {
                                    result.add((cx << 4) + x + " " + (minY + i * 16 + y) + " " + ((cz << 4) + z) + " " + state);
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }
         }

         return result;
      });
      Files.write(folder.resolve("blocks-" + label + ".txt"), lines, StandardCharsets.UTF_8);
   }

   private void dumpExtraChunks(Path folder, String label) throws IOException {
      String wanted = System.getenv("BN_BLOBCACHE_DUMP");
      if (wanted != null && !wanted.isBlank()) {
         List<String> lines = this.onClient(() -> {
            List<String> result = new ArrayList<>();

            for (String pair : wanted.split(";")) {
               String[] xz = pair.trim().split(",");
               int cx = Integer.parseInt(xz[0].trim());
               int cz = Integer.parseInt(xz[1].trim());
               LevelChunk chunk = this.minecraft.level.getChunkSource().getChunk(cx, cz, false);
               if (chunk == null) {
                  result.add("chunk " + cx + "," + cz + " not loaded");
               } else {
                  int minY = chunk.getMinY();

                  for (int i = 0; i < chunk.getSections().length; i++) {
                     LevelChunkSection section = chunk.getSections()[i];
                     if (!section.hasOnlyAir()) {
                        for (int y = 0; y < 16; y++) {
                           for (int z = 0; z < 16; z++) {
                              for (int x = 0; x < 16; x++) {
                                 BlockState state = section.getBlockState(x, y, z);
                                 if (!state.isAir()) {
                                    result.add((cx << 4) + x + " " + (minY + i * 16 + y) + " " + ((cz << 4) + z) + " " + state);
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }

            return result;
         });
         Files.write(folder.resolve("blocks-extra-" + label + ".txt"), lines, StandardCharsets.UTF_8);
      }
   }

   private void compare(Path file, String other, int[] center, long digest, Map<String, Long> digests) {
      if (Files.isRegularFile(file)) {
         try {
            try (Stream<String> lines = Files.lines(file, StandardCharsets.UTF_8)) {
               List<String> all = lines.toList();
               if (all.getFirst().equals("center " + center[0] + " " + center[1])) {
                  int differing = 0;
                  int differingNear = 0;
                  int near = 0;

                  for (String line : all.subList(2, all.size())) {
                     int space = line.lastIndexOf(32);
                     String key = line.substring(0, space);
                     int comma = key.indexOf(44);
                     int distance = Math.max(
                        Math.abs(Integer.parseInt(key.substring(0, comma)) - center[0]), Math.abs(Integer.parseInt(key.substring(comma + 1)) - center[1])
                     );
                     near += distance <= 2 ? 1 : 0;
                     Long mine = digests.get(key);
                     if (mine == null || !Long.toHexString(mine).equals(line.substring(space + 1))) {
                        differing++;
                        differingNear += distance <= 2 ? 1 : 0;
                     }
                  }

                  this.check(
                     "the chunks within 2 of the spawn equal the "
                        + other
                        + " run's ("
                        + differingNear
                        + " of "
                        + near
                        + " differ; "
                        + differing
                        + " of "
                        + digests.size()
                        + " differ over the whole square)",
                     differingNear == 0
                  );
                  if (differing > 0) {
                     this.explainDifference(file.resolveSibling("parts-" + other + ".txt"), file.resolveSibling("parts-" + this.currentLabel + ".txt"), other);
                  }

                  return;
               }

               BedrockNative.LOGGER.info("Dev blobcache test: the {} run started in another chunk ({}); not compared", other, all.getFirst());
            }
         } catch (RuntimeException | IOException var21) {
            this.failures.add("cannot compare with " + other + ": " + var21);
         }
      }
   }

   private void explainDifference(Path otherFile, Path myFile, String other) {
      try {
         Map<String, String> theirs = new LinkedHashMap<>();

         for (String line : Files.readAllLines(otherFile, StandardCharsets.UTF_8)) {
            int space = line.indexOf(32);
            theirs.put(line.substring(0, space), line.substring(space + 1));
         }

         int blocks = 0;
         int biomes = 0;
         List<String> sample = new ArrayList<>();

         for (String line : Files.readAllLines(myFile, StandardCharsets.UTF_8)) {
            int space = line.indexOf(32);
            String key = line.substring(0, space);
            String[] mine = line.substring(space + 1).split(" ");
            String[] their = theirs.getOrDefault(key, "- -").split(" ");
            boolean blocksDiffer = !mine[0].equals(their[0]);
            boolean biomesDiffer = !mine[1].equals(their[1]);
            blocks += blocksDiffer ? 1 : 0;
            biomes += biomesDiffer ? 1 : 0;
            if ((blocksDiffer || biomesDiffer) && sample.size() < 12) {
               sample.add(key + (blocksDiffer ? " blocks" : "") + (biomesDiffer ? " biomes" : ""));
            }
         }

         BedrockNative.LOGGER
            .info(
               "Dev blobcache test: against the {} run {} chunks differ in their blocks and {} in their biomes; first: {}",
               new Object[]{other, blocks, biomes, sample}
            );
      } catch (RuntimeException | IOException var16) {
         BedrockNative.LOGGER.info("Dev blobcache test: cannot tell which part differs from the {} run: {}", other, var16.toString());
      }
   }

   private Map<String, long[]> parts(int[] center) {
      Map<String, long[]> result = new LinkedHashMap<>();

      for (int x = center[0] - 6; x <= center[0] + 6; x++) {
         for (int z = center[1] - 6; z <= center[1] + 6; z++) {
            LevelChunk chunk = this.minecraft.level.getChunkSource().getChunk(x, z, false);
            if (chunk != null) {
               long blocks = 1125899906842597L;
               long biomes = 1125899906842597L;

               for (LevelChunkSection section : chunk.getSections()) {
                  for (int y = 0; y < 16; y++) {
                     for (int zz = 0; zz < 16; zz++) {
                        for (int xx = 0; xx < 16; xx++) {
                           blocks = 31L * blocks + Block.getId(section.getBlockState(xx, y, zz));
                        }
                     }
                  }

                  for (int y = 0; y < 4; y++) {
                     for (int zz = 0; zz < 4; zz++) {
                        for (int xx = 0; xx < 4; xx++) {
                           biomes = 31L * biomes + section.getNoiseBiome(xx, y, zz).getRegisteredName().hashCode();
                        }
                     }
                  }
               }

               result.put(x + "," + z, new long[]{blocks, biomes});
            }
         }
      }

      return result;
   }

   private Map<String, Long> digests(int[] center) {
      Map<String, Long> result = new LinkedHashMap<>();

      for (int x = center[0] - 6; x <= center[0] + 6; x++) {
         for (int z = center[1] - 6; z <= center[1] + 6; z++) {
            LevelChunk chunk = this.minecraft.level.getChunkSource().getChunk(x, z, false);
            if (chunk != null) {
               result.put(x + "," + z, digest(chunk));
            }
         }
      }

      return result;
   }

   private static long digest(LevelChunk chunk) {
      long hash = 1125899906842597L;

      for (LevelChunkSection section : chunk.getSections()) {
         for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
               for (int x = 0; x < 16; x++) {
                  hash = 31L * hash + Block.getId(section.getBlockState(x, y, z));
               }
            }
         }

         for (int y = 0; y < 4; y++) {
            for (int z = 0; z < 4; z++) {
               for (int x = 0; x < 4; x++) {
                  hash = 31L * hash + section.getNoiseBiome(x, y, z).getRegisteredName().hashCode();
               }
            }
         }
      }

      return hash;
   }

   private void check(String what, boolean ok) {
      this.checks++;
      if (ok) {
         BedrockNative.LOGGER.info("Dev blobcache test PASS: {}", what);
      } else {
         this.failures.add(what);
         BedrockNative.LOGGER.warn("Dev blobcache test FAIL: {}", what);
      }
   }

   private static void sleep(long millis) {
      try {
         Thread.sleep(millis);
      } catch (InterruptedException var3) {
         Thread.currentThread().interrupt();
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
