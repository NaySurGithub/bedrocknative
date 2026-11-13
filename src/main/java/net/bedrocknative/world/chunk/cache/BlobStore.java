package net.bedrocknative.world.chunk.cache;

import org.jspecify.annotations.Nullable;

public interface BlobStore {
   byte @Nullable [] get(long var1);

   void put(long var1, byte[] var3);

   default void releaseMemory() {
   }
}
