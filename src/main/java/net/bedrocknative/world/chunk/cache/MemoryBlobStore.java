package net.bedrocknative.world.chunk.cache;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map.Entry;

public final class MemoryBlobStore implements BlobStore {
   private final long capBytes;
   private final LinkedHashMap<Long, byte[]> blobs = new LinkedHashMap<>(256, 0.75F, true);
   private long bytes;

   public MemoryBlobStore(long capBytes) {
      this.capBytes = capBytes;
   }

   @Override
   public synchronized byte[] get(long hash) {
      return this.blobs.get(hash);
   }

   @Override
   public synchronized void put(long hash, byte[] data) {
      byte[] old = this.blobs.put(hash, data);
      if (old != null) {
         this.bytes -= old.length;
      }

      this.bytes += data.length;
      Iterator<Entry<Long, byte[]>> oldest = this.blobs.entrySet().iterator();

      while (this.bytes > this.capBytes && this.blobs.size() > 1 && oldest.hasNext()) {
         Entry<Long, byte[]> entry = oldest.next();
         if (entry.getKey() != hash) {
            this.bytes = this.bytes - entry.getValue().length;
            oldest.remove();
         }
      }
   }

   public synchronized int size() {
      return this.blobs.size();
   }

   public synchronized long bytes() {
      return this.bytes;
   }

   public synchronized void clear() {
      this.blobs.clear();
      this.bytes = 0L;
   }

   synchronized void remove(long hash) {
      byte[] old = this.blobs.remove(hash);
      if (old != null) {
         this.bytes -= old.length;
      }
   }
}
