package net.bedrocknative.world.chunk.cache;

import java.io.IOException;
import java.nio.file.Path;
import net.bedrocknative.BedrockNative;
import net.fabricmc.loader.api.FabricLoader;

public final class BlobCaches {
   public static final String PROPERTY = "bedrocknative.blobCache";
   private static final long DEFAULT_CAP_MB = 256L;
   private static final long MEMORY_FALLBACK_BYTES = 134217728L;
   private static BlobStore shared;

   private BlobCaches() {
   }

   public static boolean enabled() {
      return !"off".equalsIgnoreCase(System.getProperty("bedrocknative.blobCache"));
   }

   public static Path folder() {
      return FabricLoader.getInstance().getConfigDir().resolve("bedrocknative").resolve("blobcache");
   }

   public static synchronized BlobStore shared() {
      if (shared == null) {
         long capMb = Long.getLong("bedrocknative.blobCacheMb", 256L);

         try {
            long started = System.nanoTime();
            DiskBlobStore disk = DiskBlobStore.open(folder(), capMb << 20);
            BedrockNative.LOGGER
               .info(
                  "Blob cache: {} blobs in {} segments ({} MB of {} MB) read from {} in {} ms",
                  new Object[]{disk.blobCount(), disk.segmentCount(), disk.totalBytes() >> 20, capMb, folder(), (System.nanoTime() - started) / 1000000L}
               );
            shared = disk;
         } catch (IOException | RuntimeException e) {
            BedrockNative.LOGGER.warn("Blob cache: cannot use {} ({}); keeping blobs in memory for this session", folder(), e.toString());
            shared = new MemoryBlobStore(134217728L);
         }
      }

      return shared;
   }

   public static synchronized void sessionEnded() {
      if (shared != null) {
         shared.releaseMemory();
      }
   }

   public static void warmUp() {
      Thread thread = new Thread(BlobCaches::shared, "BedrockNative blob cache");
      thread.setDaemon(true);
      thread.start();
   }
}
