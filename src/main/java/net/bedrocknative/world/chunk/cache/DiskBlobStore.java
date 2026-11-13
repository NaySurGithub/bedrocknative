package net.bedrocknative.world.chunk.cache;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;
import java.util.stream.Stream;
import net.bedrocknative.BedrockNative;

public final class DiskBlobStore implements BlobStore, Closeable {
   private static final int MAGIC = 1112425027;
   private static final int FORMAT = 1;
   private static final int HEADER_BYTES = 8;
   private static final int RECORD_HEADER_BYTES = 12;
   private static final int SCAN_WINDOW_BYTES = 1048576;
   static final int MAX_BLOB_BYTES = 16777216;
   private static final long MEMORY_BYTES = 33554432L;
   static final int OPEN_READERS = 8;
   private static final long MIN_ROOM_BYTES = 65536L;
   private static final long LOCK_POSITION = 1125899906842624L;
   private static final long STAMP_INTERVAL_MILLIS = 60000L;
   private static final Set<Path> WRITING = ConcurrentHashMap.newKeySet();
   private final Path folder;
   private final long capBytes;
   private final long segmentBytes;
   private final LongSupplier clockMillis;
   private final Map<Long, DiskBlobStore.Location> index = new HashMap<>();
   private final List<DiskBlobStore.Segment> segments = new ArrayList<>();
   private final MemoryBlobStore memory = new MemoryBlobStore(33554432L);
   private final Map<DiskBlobStore.Segment, FileChannel> readers = new LinkedHashMap<>(16, 0.75F, true);
   private DiskBlobStore.Segment active;
   private long totalBytes;
   private boolean closed;
   private boolean writeFailed;
   private int corrupt;
   private int evictedSegments;

   private DiskBlobStore(Path folder, long capBytes, LongSupplier clockMillis) {
      this.folder = folder;
      this.capBytes = capBytes;
      this.segmentBytes = Math.max(1048576L, Math.min(16777216L, capBytes / 4L));
      this.clockMillis = clockMillis;
   }

   public static DiskBlobStore open(Path folder, long capBytes) throws IOException {
      return open(folder, capBytes, System::currentTimeMillis);
   }

   public static DiskBlobStore open(Path folder, long capBytes, LongSupplier clockMillis) throws IOException {
      Files.createDirectories(folder);
      DiskBlobStore store = new DiskBlobStore(folder, capBytes, clockMillis);
      List<DiskBlobStore.Segment> found = new ArrayList<>();

      try (Stream<Path> files = Files.list(folder)) {
         for (Path file : (Iterable<Path>) files.filter(f -> f.getFileName().toString().endsWith(".seg"))::iterator) {
            try {
               found.add(new DiskBlobStore.Segment(file, Files.getLastModifiedTime(file).toMillis()));
            } catch (IOException e) {
               BedrockNative.LOGGER.warn("Blob cache: cannot read {}: {}", file.getFileName(), e.toString());
            }
         }
      }

      found.sort(Comparator.comparingLong(s -> s.lastUsed));

      for (DiskBlobStore.Segment segment : found) {
         store.scan(segment);
      }

      return store;
   }

   private void scan(DiskBlobStore.Segment segment) {
      try (FileChannel channel = FileChannel.open(segment.path, StandardOpenOption.READ)) {
         long size = channel.size();
         ByteBuffer head = ByteBuffer.allocate(8);
         if (size >= 8L && readFully(channel, head, 0L) && head.getInt(0) == 1112425027 && head.getInt(4) == 1) {
            ByteBuffer window = ByteBuffer.allocate((int)Math.min(1048576L, size));
            long windowStart = 0L;
            long windowEnd = 0L;
            long position = 8L;

            int records;
            for (records = 0; position + 12L <= size; records++) {
               if (position < windowStart || position + 12L > windowEnd) {
                  window.clear();
                  window.limit((int)Math.min(window.capacity(), size - position));
                  if (!readFully(channel, window, position)) {
                     break;
                  }

                  windowStart = position;
                  windowEnd = position + window.limit();
               }

               int at = (int)(position - windowStart);
               long hash = window.getLong(at);
               int length = window.getInt(at + 8);
               if (length < 0 || length > 16777216 || position + 12L + length > size) {
                  break;
               }

               this.index.put(hash, new DiskBlobStore.Location(segment, position + 12L, length));
               position += 12 + length;
            }

            if (position < size) {
               BedrockNative.LOGGER
                  .warn(
                     "Blob cache: {} ends with {} unreadable bytes after {} blobs; they are ignored",
                     new Object[]{segment.path.getFileName(), size - position, records}
                  );
            }

            segment.size = size;
            segment.validEnd = position;
            this.totalBytes += size;
            this.segments.add(segment);
         } else {
            BedrockNative.LOGGER.warn("Blob cache: {} is not a segment of this version; ignored", segment.path.getFileName());
         }
      } catch (IOException e) {
         BedrockNative.LOGGER.warn("Blob cache: cannot scan {}: {}", segment.path.getFileName(), e.toString());
      }
   }

   private static boolean readFully(FileChannel channel, ByteBuffer buffer, long position) throws IOException {
      while (buffer.hasRemaining()) {
         int read = channel.read(buffer, position + buffer.position());
         if (read < 0) {
            return false;
         }
      }

      return true;
   }

   @Override
   public synchronized byte[] get(long hash) {
      DiskBlobStore.Location location = this.index.get(hash);
      byte[] cached = this.memory.get(hash);
      if (cached != null) {
         if (location != null) {
            this.touch(location.segment);
         }

         return cached;
      } else {
         if (location == null) {
            return null;
         }

         byte[] data;
         try {
            data = this.read(location);
         } catch (IOException e) {
            BedrockNative.LOGGER.warn("Blob cache: cannot read {}: {}; its blobs are dropped", location.segment.path.getFileName(), e.toString());
            this.dropEntries(location.segment);
            return null;
         }

         if (XxHash64.hash(data) != hash) {
            if (this.corrupt++ == 0) {
               BedrockNative.LOGGER.warn("Blob cache: a stored blob in {} no longer matches its id; dropped", location.segment.path.getFileName());
            }

            this.index.remove(hash);
            return null;
         } else {
            this.touch(location.segment);
            this.memory.put(hash, data);
            return data;
         }
      }
   }

   private byte[] read(DiskBlobStore.Location location) throws IOException {
      DiskBlobStore.Segment segment = location.segment;
      FileChannel channel = segment.channel;
      if (channel == null) {
         channel = this.readers.get(segment);
         if (channel == null) {
            channel = FileChannel.open(segment.path, StandardOpenOption.READ);
            this.readers.put(segment, channel);
            this.trimReaders();
         }
      }

      ByteBuffer buffer = ByteBuffer.allocate(location.length);
      if (!readFully(channel, buffer, location.offset)) {
         throw new IOException("file ends inside a blob");
      } else {
         return buffer.array();
      }
   }

   private void trimReaders() {
      Iterator<Entry<DiskBlobStore.Segment, FileChannel>> it = this.readers.entrySet().iterator();

      while (this.readers.size() > 8 && it.hasNext()) {
         closeQuietly(it.next().getValue());
         it.remove();
      }
   }

   @Override
   public synchronized void releaseMemory() {
      if (!this.writeFailed) {
         this.memory.clear();
      }
   }

   @Override
   public synchronized void put(long hash, byte[] data) {
      if (!this.closed && data.length <= 16777216) {
         this.memory.put(hash, data);
         DiskBlobStore.Location existing = this.index.get(hash);
         if (existing != null) {
            this.touch(existing.segment);
         } else if (!this.writeFailed) {
            try {
               if (this.active == null || this.active.size + 12L + data.length > this.segmentBytes && this.active.size > 8L) {
                  this.startSegment(12 + data.length);
               }

               ByteBuffer record = ByteBuffer.allocate(12 + data.length);
               record.putLong(hash).putInt(data.length).put(data).flip();
               writeFully(this.active.channel, record, this.active.size);
               this.index.put(hash, new DiskBlobStore.Location(this.active, this.active.size + 12L, data.length));
               this.active.size = this.active.size + record.capacity();
               this.active.validEnd = this.active.size;
               this.active.lastUsed = this.clockMillis.getAsLong();
               this.totalBytes = this.totalBytes + record.capacity();
            } catch (IOException e) {
               this.writeFailed = true;
               BedrockNative.LOGGER.warn("Blob cache: cannot write to {}: {}; blobs are kept in memory only from now on", this.folder, e.toString());
               return;
            }

            this.enforceCap();
         }
      }
   }

   private void startSegment(int needed) throws IOException {
      DiskBlobStore.Segment previous = this.active;
      if (previous != null) {
         stopWriting(previous);
         this.active = null;
      }

      DiskBlobStore.Segment adopt = null;

      for (DiskBlobStore.Segment candidate : this.segments) {
         if (candidate != previous
            && candidate.validEnd + Math.max(65536L, needed) <= this.segmentBytes
            && (adopt == null || candidate.lastUsed > adopt.lastUsed)) {
            adopt = candidate;
         }
      }

      if (adopt != null && this.adopt(adopt)) {
         this.active = adopt;
      } else {
         Path path = this.folder
            .resolve(
               "blobs-"
                  + Long.toString(System.currentTimeMillis(), 36)
                  + "-"
                  + Long.toString(ThreadLocalRandom.current().nextLong() & 4294967295L, 36)
                  + ".seg"
            );
         FileChannel channel = FileChannel.open(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.READ, StandardOpenOption.WRITE);
         DiskBlobStore.Segment segment = new DiskBlobStore.Segment(path, this.clockMillis.getAsLong());
         segment.channel = channel;
         WRITING.add(path.toAbsolutePath().normalize());
         lockOrNull(channel);
         ByteBuffer head = ByteBuffer.allocate(8);
         head.putInt(1112425027).putInt(1).flip();
         writeFully(channel, head, 0L);
         segment.size = 8L;
         segment.validEnd = 8L;
         this.totalBytes += 8L;
         this.segments.add(segment);
         this.active = segment;
      }
   }

   private boolean adopt(DiskBlobStore.Segment segment) {
      Path claim = segment.path.toAbsolutePath().normalize();
      if (!WRITING.add(claim)) {
         return false;
      }

      boolean adopted = false;
      FileChannel channel = null;

      try {
         closeQuietly(this.readers.remove(segment));
         channel = FileChannel.open(segment.path, StandardOpenOption.READ, StandardOpenOption.WRITE);
         if (lockOrNull(channel) != null && channel.size() == segment.size) {
            if (segment.validEnd < segment.size) {
               channel.truncate(segment.validEnd);
               this.totalBytes = this.totalBytes - (segment.size - segment.validEnd);
               segment.size = segment.validEnd;
            }

            segment.channel = channel;
            segment.lastUsed = this.clockMillis.getAsLong();
            adopted = true;
            return true;
         } else {
            return false;
         }
      } catch (IOException | RuntimeException e) {
         return false;
      } finally {
         if (!adopted) {
            closeQuietly(channel);
            WRITING.remove(claim);
         }
      }
   }

   private static FileLock lockOrNull(FileChannel channel) {
      try {
         return channel.tryLock(1125899906842624L, 1L, false);
      } catch (IOException | RuntimeException e) {
         return null;
      }
   }

   private static void writeFully(FileChannel channel, ByteBuffer buffer, long position) throws IOException {
      while (buffer.hasRemaining()) {
         position += channel.write(buffer, position);
      }
   }

   private void touch(DiskBlobStore.Segment segment) {
      long now = this.clockMillis.getAsLong();
      segment.lastUsed = now;
      if (now - segment.stampedAt >= 60000L) {
         this.stamp(segment);
      }
   }

   private void stamp(DiskBlobStore.Segment segment) {
      segment.stampedAt = segment.lastUsed;

      try {
         Files.setLastModifiedTime(segment.path, FileTime.fromMillis(segment.lastUsed));
      } catch (IOException var3) {
      }
   }

   private void enforceCap() {
      while (this.totalBytes > this.capBytes) {
         DiskBlobStore.Segment victim = null;

         for (DiskBlobStore.Segment segment : this.segments) {
            if (segment != this.active && (victim == null || segment.lastUsed < victim.lastUsed)) {
               victim = segment;
            }
         }

         if (victim == null || !this.evict(victim)) {
            return;
         }
      }
   }

   private boolean evict(DiskBlobStore.Segment victim) {
      this.closeSegment(victim);

      try {
         Files.deleteIfExists(victim.path);
      } catch (IOException e) {
         victim.lastUsed = this.clockMillis.getAsLong();
         return false;
      }

      this.dropEntries(victim);
      this.segments.remove(victim);
      this.totalBytes = this.totalBytes - victim.size;
      this.evictedSegments++;
      return true;
   }

   private void dropEntries(DiskBlobStore.Segment segment) {
      this.index.values().removeIf(location -> location.segment == segment);
   }

   private void closeSegment(DiskBlobStore.Segment segment) {
      closeQuietly(this.readers.remove(segment));
      stopWriting(segment);
   }

   private static void stopWriting(DiskBlobStore.Segment segment) {
      if (segment.channel != null) {
         closeQuietly(segment.channel);
         segment.channel = null;
         WRITING.remove(segment.path.toAbsolutePath().normalize());
      }
   }

   private static void closeQuietly(FileChannel channel) {
      if (channel != null) {
         try {
            channel.close();
         } catch (IOException var2) {
         }
      }
   }

   public synchronized int blobCount() {
      return this.index.size();
   }

   public synchronized long totalBytes() {
      return this.totalBytes;
   }

   public synchronized int segmentCount() {
      return this.segments.size();
   }

   synchronized long memoryBytes() {
      return this.memory.bytes();
   }

   synchronized int openReaderCount() {
      return this.readers.size();
   }

   public synchronized int corruptCount() {
      return this.corrupt;
   }

   public synchronized int evictedSegmentCount() {
      return this.evictedSegments;
   }

   @Override
   public synchronized void close() {
      this.closed = true;

      for (DiskBlobStore.Segment segment : this.segments) {
         if (segment.lastUsed > segment.stampedAt) {
            this.stamp(segment);
         }

         this.closeSegment(segment);
      }

      this.active = null;
   }

   private record Location(DiskBlobStore.Segment segment, long offset, int length) {
   }

   private static final class Segment {
      final Path path;
      long size;
      long validEnd;
      long lastUsed;
      long stampedAt;
      FileChannel channel;

      Segment(Path path, long lastUsed) {
         this.path = path;
         this.lastUsed = lastUsed;
         this.stampedAt = lastUsed;
      }
   }
}
