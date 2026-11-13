package net.bedrocknative.content.base;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.ToLongFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import net.bedrocknative.content.SafeHttp;
import org.jspecify.annotations.Nullable;

final class ZipRanges {
   static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(120L);
   static final int TAIL = 65536;
   static final long MAX_GAP = 262144L;
   static final long MAX_RANGE = 25165824L;
   static final long MAX_DIRECTORY = 33554432L;
   static final int MAX_ENTRIES = 200000;
   private static final int MAX_HOPS = 3;
   private static final int FAILED_RANGES_LIMIT = 3;
   private static final Pattern CONTENT_RANGE = Pattern.compile("bytes (\\d+)-(\\d+)/(\\d+)");
   private final HttpClient client;
   private final URI release;
   private final long expectedSize;
   private final String root;
   private final BaseHosts hosts;
   @Nullable
   private URI resolved;

   ZipRanges(HttpClient client, URI release, long expectedSize, String root, BaseHosts hosts) {
      this.client = client;
      this.release = release;
      this.expectedSize = expectedSize;
      this.root = root;
      this.hosts = hosts;
   }

   void fetch(List<BaseTable.Entry> entries, BaseStore store, FetchRun run) throws InterruptedException {
      ZipRanges.Directory found;
      try {
         found = this.directory(run);
      } catch (IOException var17) {
         run.note("release zip not used: " + var17.getMessage());
         return;
      }

      List<ZipRanges.Member> directory = found.members();
      Map<String, ZipRanges.Member> byName = new HashMap<>();
      Map<String, ZipRanges.Member> byLowerName = new HashMap<>();
      String prefix = null;

      for (ZipRanges.Member member : directory) {
         byName.put(member.name(), member);
         byLowerName.putIfAbsent(member.name().toLowerCase(Locale.ROOT), member);
         String manifest = this.root + "manifest.json";
         if (member.name().endsWith(manifest) && (prefix == null || member.name().length() - manifest.length() < prefix.length())) {
            prefix = member.name().substring(0, member.name().length() - manifest.length());
         }
      }

      if (prefix == null) {
         run.note("release zip not used: no " + this.root + "manifest.json in it");
      } else {
         List<ZipRanges.Member> sorted = new ArrayList<>(directory);
         sorted.sort(Comparator.comparingLong(ZipRanges.Member::offset));
         Map<ZipRanges.Member, Long> ends = new HashMap<>();

         for (int i = 0; i < sorted.size(); i++) {
            ends.put(sorted.get(i), i + 1 < sorted.size() ? sorted.get(i + 1).offset() : found.offset());
         }

         List<ZipRanges.Wanted> wanted = new ArrayList<>();

         for (BaseTable.Entry entry : entries) {
            String name = prefix + this.root + entry.path();
            ZipRanges.Member memberx = byName.get(name);
            if (memberx == null) {
               memberx = byLowerName.get(name.toLowerCase(Locale.ROOT));
            }

            if (memberx != null
               && !memberx.encrypted()
               && memberx.size() == entry.size()
               && (memberx.method() == 0 || memberx.method() == 8)
               && memberx.compressedSize() <= 134217728L) {
               wanted.add(new ZipRanges.Wanted(entry, memberx, ends.get(memberx)));
            }
         }

         wanted.sort(Comparator.comparingLong(item -> item.member().offset()));
         int failedInARow = 0;

         for (List<ZipRanges.Wanted> range : ranges(wanted)) {
            if (run.stopped() || failedInARow >= 3) {
               break;
            }

            try {
               this.readRange(range, store, run);
               failedInARow = 0;
            } catch (IOException var16) {
               failedInARow++;
               run.note("release zip range failed: " + var16.getMessage());
            }
         }
      }
   }

   static <T> List<List<T>> group(List<T> sorted, ToLongFunction<T> start, ToLongFunction<T> end) {
      List<List<T>> ranges = new ArrayList<>();
      List<T> current = new ArrayList<>();
      long rangeStart = 0L;
      long rangeEnd = 0L;

      for (T item : sorted) {
         long itemStart = start.applyAsLong(item);
         long itemEnd = end.applyAsLong(item);
         if (!current.isEmpty() && (itemStart - rangeEnd > 262144L || itemEnd - rangeStart > 25165824L)) {
            ranges.add(current);
            current = new ArrayList<>();
         }

         if (current.isEmpty()) {
            rangeStart = itemStart;
         }

         current.add(item);
         rangeEnd = Math.max(rangeEnd, itemEnd);
      }

      if (!current.isEmpty()) {
         ranges.add(current);
      }

      return ranges;
   }

   private static List<List<ZipRanges.Wanted>> ranges(List<ZipRanges.Wanted> wanted) {
      return group(wanted, found -> found.member().offset(), ZipRanges.Wanted::end);
   }

   private void readRange(List<ZipRanges.Wanted> range, BaseStore store, FetchRun run) throws IOException, InterruptedException {
      long start = range.getFirst().member().offset();
      long end = range.getLast().end() - 1L;

      try (InputStream in = this.get(start, end, run)) {
         long position = start;

         for (ZipRanges.Wanted wanted : range) {
            ZipRanges.Member member = wanted.member();
            if (member.offset() < position) {
               throw new IOException("overlapping entries at " + member.name());
            }

            in.skipNBytes(member.offset() - position);
            byte[] header = in.readNBytes(30);
            ByteBuffer local = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
            if (header.length < 30 || local.getInt(0) != 67324752) {
               throw new IOException("no local header for " + member.name());
            }

            int skip = (local.getShort(26) & '\uffff') + (local.getShort(28) & '\uffff');
            in.skipNBytes(skip);
            byte[] stored = in.readNBytes((int)member.compressedSize());
            position = member.offset() + 30L + skip + stored.length;
            if (stored.length != member.compressedSize()) {
               throw new IOException("range ended inside " + member.name());
            }

            byte[] data;
            try {
               data = member.method() == 0 ? stored : inflate(stored, member.size());
            } catch (DataFormatException var23) {
               run.failed(wanted.entry().path(), "broken deflate data");
               continue;
            }

            CRC32 crc = new CRC32();
            crc.update(data);
            if (crc.getValue() != member.crc()) {
               run.failed(wanted.entry().path(), "CRC-32 differs");
            } else {
               try {
                  store.write(wanted.entry(), data, data.length);
                  run.written(wanted.entry());
                  run.bytes.addAndGet(data.length);
               } catch (BaseStore.Mismatch var22) {
                  run.failed(wanted.entry().path(), var22.getMessage());
               }
            }
         }
      }
   }

   static byte[] inflate(byte[] stored, long size) throws DataFormatException {
      if (size > 67108864L) {
         throw new DataFormatException("too large");
      } else {
         Inflater inflater = new Inflater(true);

         byte[] probe;
         try {
            inflater.setInput(stored);
            byte[] out = new byte[(int)size];
            int total = 0;

            while (total < out.length && !inflater.finished()) {
               int read = inflater.inflate(out, total, out.length - total);
               if (read == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                  break;
               }

               total += read;
            }

            if (total != out.length) {
               throw new DataFormatException("inflated " + total + " of " + size + " bytes");
            }

            if (!inflater.finished()) {
               probe = new byte[1];
               if (inflater.inflate(probe) > 0) {
                  throw new DataFormatException("longer than " + size + " bytes");
               }
            }

            probe = out;
         } finally {
            inflater.end();
         }

         return probe;
      }
   }

   ZipRanges.Directory directory(FetchRun run) throws IOException, InterruptedException {
      if (this.expectedSize <= 22L) {
         throw new IOException("no zip size in the table");
      } else {
         long tailStart = Math.max(0L, this.expectedSize - 65536L);

         byte[] tail;
         try (InputStream in = this.get(tailStart, this.expectedSize - 1L, run)) {
            tail = in.readNBytes((int)(this.expectedSize - tailStart));
         }

         ByteBuffer buffer = ByteBuffer.wrap(tail).order(ByteOrder.LITTLE_ENDIAN);
         int end = -1;

         for (int i = tail.length - 22; i >= 0 && i >= tail.length - 22 - 65535; i--) {
            if (buffer.getInt(i) == 101010256) {
               end = i;
               break;
            }
         }

         if (end < 0) {
            throw new IOException("no end of central directory");
         } else {
            long entries = buffer.getShort(end + 10) & '\uffff';
            long directorySize = buffer.getInt(end + 12) & 4294967295L;
            long directoryOffset = buffer.getInt(end + 16) & 4294967295L;
            if (entries == 65535L || directorySize == 4294967295L || directoryOffset == 4294967295L) {
               if (end < 20 || buffer.getInt(end - 20) != 117853008) {
                  throw new IOException("ZIP64 locator missing");
               }

               long record = buffer.getLong(end - 12);
               ByteBuffer zip64;
               if (record >= tailStart && record - tailStart + 56L <= tail.length) {
                  zip64 = ByteBuffer.wrap(tail, (int)(record - tailStart), 56).slice().order(ByteOrder.LITTLE_ENDIAN);
               } else {
                  try (InputStream in = this.get(record, record + 55L, run)) {
                     zip64 = ByteBuffer.wrap(in.readNBytes(56)).order(ByteOrder.LITTLE_ENDIAN);
                  }
               }

               if (zip64.limit() < 56 || zip64.getInt(0) != 101075792) {
                  throw new IOException("ZIP64 end record missing");
               }

               entries = zip64.getLong(32);
               directorySize = zip64.getLong(40);
               directoryOffset = zip64.getLong(48);
            }

            if (entries <= 200000L && directorySize <= 33554432L && directoryOffset + directorySize <= this.expectedSize) {
               byte[] directory;
               if (directoryOffset >= tailStart) {
                  directory = Arrays.copyOfRange(tail, (int)(directoryOffset - tailStart), (int)(directoryOffset - tailStart + directorySize));
               } else {
                  try (InputStream in = this.get(directoryOffset, directoryOffset + directorySize - 1L, run)) {
                     directory = in.readNBytes((int)directorySize);
                  }
               }

               if (directory.length != directorySize) {
                  throw new IOException("central directory cut short");
               } else {
                  return new ZipRanges.Directory(parseDirectory(directory, entries), directoryOffset);
               }
            } else {
               throw new IOException("central directory out of bounds");
            }
         }
      }
   }

   static List<ZipRanges.Member> parseDirectory(byte[] directory, long count) throws IOException {
      ByteBuffer buffer = ByteBuffer.wrap(directory).order(ByteOrder.LITTLE_ENDIAN);
      List<ZipRanges.Member> members = new ArrayList<>();
      int position = 0;

      for (long i = 0L; i < count; i++) {
         if (position + 46 > directory.length || buffer.getInt(position) != 33639248) {
            throw new IOException("bad central directory entry " + i);
         }

         int flags = buffer.getShort(position + 8) & '\uffff';
         int method = buffer.getShort(position + 10) & '\uffff';
         long crc = buffer.getInt(position + 16) & 4294967295L;
         long compressed = buffer.getInt(position + 20) & 4294967295L;
         long size = buffer.getInt(position + 24) & 4294967295L;
         int nameLength = buffer.getShort(position + 28) & '\uffff';
         int extraLength = buffer.getShort(position + 30) & '\uffff';
         int commentLength = buffer.getShort(position + 32) & '\uffff';
         long offset = buffer.getInt(position + 42) & 4294967295L;
         int next = position + 46 + nameLength + extraLength + commentLength;
         if (next > directory.length) {
            throw new IOException("central directory entry " + i + " cut short");
         }

         String name = new String(directory, position + 46, nameLength, StandardCharsets.UTF_8);
         int extra = position + 46 + nameLength;
         int at = extra;

         while (at + 4 <= extra + extraLength) {
            int id = buffer.getShort(at) & '\uffff';
            int length = buffer.getShort(at + 2) & '\uffff';
            if (id == 1) {
               int field = at + 4;
               if (size == 4294967295L && field + 8 <= at + 4 + length) {
                  size = buffer.getLong(field);
                  field += 8;
               }

               if (compressed == 4294967295L && field + 8 <= at + 4 + length) {
                  compressed = buffer.getLong(field);
                  field += 8;
               }

               if (offset == 4294967295L && field + 8 <= at + 4 + length) {
                  offset = buffer.getLong(field);
               }
            }

            at += 4 + length;
         }

         if (!name.endsWith("/")) {
            members.add(new ZipRanges.Member(name, method, compressed, size, crc, offset, (flags & 1) != 0));
         }

         position = next;
      }

      return members;
   }

   private InputStream get(long first, long last, FetchRun run) throws IOException, InterruptedException {
      IOException failure = null;

      for (int attempt = 0; attempt < 2; attempt++) {
         URI uri = this.resolved != null ? this.resolved : this.release;

         for (int hop = 0; hop <= 3; hop++) {
            if (run.stopped()) {
               throw new IOException("run stopped");
            }

            this.hosts.check(uri);
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(REQUEST_TIMEOUT).header("Range", "bytes=" + first + "-" + last).GET().build();
            run.requests.incrementAndGet();
            HttpResponse<InputStream> response = this.client.send(request, BodyHandlers.ofInputStream());
            int status = response.statusCode();
            URI next = SafeHttp.redirect(uri, status, response.headers().firstValue("location").orElse(null));
            if (next == null) {
               if (status == 206) {
                  Matcher range = CONTENT_RANGE.matcher(response.headers().firstValue("content-range").orElse(""));
                  if (range.matches()
                     && Long.parseLong(range.group(1)) == first
                     && Long.parseLong(range.group(2)) == last
                     && Long.parseLong(range.group(3)) == this.expectedSize) {
                     this.resolved = uri;
                     return response.body();
                  }

                  response.body().close();
                  throw new IOException("the release zip is not the pinned one (" + response.headers().firstValue("content-range").orElse("no range") + ")");
               }

               response.body().close();
               if (status == 429) {
                  run.stop("the host's rate limit (HTTP 429)");
                  throw new IOException("rate limit");
               }

               if (status == 200) {
                  throw new IOException("the host ignores ranges");
               }

               failure = new IOException("HTTP " + status);
               break;
            }

            response.body().close();
            uri = next;
         }

         if (this.resolved == null) {
            break;
         }

         this.resolved = null;
      }

      throw failure != null ? failure : new IOException("too many redirects");
   }

   record Directory(List<ZipRanges.Member> members, long offset) {
   }

   record Member(String name, int method, long compressedSize, long size, long crc, long offset, boolean encrypted) {
   }

   private record Wanted(BaseTable.Entry entry, ZipRanges.Member member, long end) {
   }
}
