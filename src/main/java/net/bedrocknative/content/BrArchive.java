package net.bedrocknative.content;

import java.io.IOException;
import java.lang.ref.SoftReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class BrArchive {
   private static final long MAGIC = 2769804620953102205L;
   private static final int HEADER = 16;
   private static final int ENTRY = 256;
   private static final long CACHED_SIZE = 33554432L;
   private final Path file;
   private final Map<String, BrArchive.Entry> entries;
   private final Map<String, BrArchive.Entry> lowerCase = new HashMap<>();
   private volatile SoftReference<byte[]> contents = new SoftReference<>(null);

   private BrArchive(Path file, Map<String, BrArchive.Entry> entries) {
      this.file = file;
      this.entries = entries;
      entries.forEach((name, entry) -> this.lowerCase.putIfAbsent(name.toLowerCase(Locale.ROOT), entry));
   }

   public static BrArchive open(Path file) throws IOException {
      BrArchive var17;
      try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
         ByteBuffer header = read(channel, 0L, 16);
         if (header.getLong() != 2769804620953102205L) {
            throw new IOException("Not a brarchive: " + file);
         }

         int count = header.getInt();
         ByteBuffer table = read(channel, 16L, count * 256);
         long dataStart = 16L + count * 256L;
         Map<String, BrArchive.Entry> entries = new HashMap<>(count * 2);

         for (int i = 0; i < count; i++) {
            int base = i * 256;
            int length = Byte.toUnsignedInt(table.get(base));
            String name = new String(table.array(), base + 1, Math.min(length, 247), StandardCharsets.UTF_8);
            long offset = Integer.toUnsignedLong(table.getInt(base + 248));
            int size = table.getInt(base + 252);
            entries.put(name, new BrArchive.Entry(name, dataStart + offset, size));
         }

         var17 = new BrArchive(file, entries);
      }

      return var17;
   }

   public Set<String> names() {
      return this.entries.keySet();
   }

   public boolean has(String name) {
      return this.entries.containsKey(name) || this.lowerCase.containsKey(name.toLowerCase(Locale.ROOT));
   }

   public Optional<byte[]> read(String name) throws IOException {
      BrArchive.Entry entry = this.entries.get(name);
      if (entry == null) {
         entry = this.lowerCase.get(name.toLowerCase(Locale.ROOT));
      }

      if (entry == null) {
         return Optional.empty();
      } else {
         byte[] all = this.contents();
         if (all != null && entry.size() >= 0 && entry.offset() + entry.size() <= all.length) {
            return Optional.of(Arrays.copyOfRange(all, (int)entry.offset(), (int)entry.offset() + entry.size()));
         } else {
            Optional var5;
            try (FileChannel channel = FileChannel.open(this.file, StandardOpenOption.READ)) {
               var5 = Optional.of(read(channel, entry.offset(), entry.size()).array());
            }

            return var5;
         }
      }
   }

   private byte[] contents() throws IOException {
      byte[] all = this.contents.get();
      if (all == null) {
         if (Files.size(this.file) > 33554432L) {
            return null;
         }

         all = Files.readAllBytes(this.file);
         this.contents = new SoftReference<>(all);
      }

      return all;
   }

   private static ByteBuffer read(FileChannel channel, long position, int size) throws IOException {
      ByteBuffer buffer = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);

      while (buffer.hasRemaining()) {
         if (channel.read(buffer, position + buffer.position()) < 0) {
            throw new IOException("Truncated brarchive");
         }
      }

      return buffer.flip();
   }

   private record Entry(String name, long offset, int size) {
   }
}
