package net.bedrocknative.world.chunk;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import java.util.Map;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

public final class ChunkDiagnostics {
   public static final int ALL_FAILED_AFTER = 20;
   static final int HEX_BYTES = 160;
   private static final int MAX_KIND_LENGTH = 140;
   private String server = "?";
   private int read;
   private int failed;
   private int nonEmptySeen;
   private int nonEmptyFailed;
   private boolean described;
   private boolean allFailedTold;
   @Nullable
   private String firstKind;
   private final Map<String, Integer> kinds = new TreeMap<>();

   public synchronized void reset(String server) {
      this.server = server;
      this.read = 0;
      this.failed = 0;
      this.nonEmptySeen = 0;
      this.nonEmptyFailed = 0;
      this.described = false;
      this.allFailedTold = false;
      this.firstKind = null;
      this.kinds.clear();
   }

   public synchronized void columnRead(ChunkDiagnostics.Column column) {
      this.read++;
      if (column.subChunkCount() > 0) {
         this.nonEmptySeen++;
      }
   }

   public synchronized ChunkDiagnostics.Failure columnFailed(
      ChunkDiagnostics.Column column, ByteBuf payload, int payloadStart, int payloadLength, RuntimeException failure
   ) {
      this.failed++;
      String kind = kind(failure);
      this.kinds.merge(kind, 1, Integer::sum);
      if (this.firstKind == null) {
         this.firstKind = kind;
      }

      String description = null;
      if (!this.described) {
         this.described = true;
         description = this.describe(column, payload, payloadStart, payloadLength, failure, kind);
      }

      String verdict = null;
      if (column.subChunkCount() > 0) {
         this.nonEmptySeen++;
         this.nonEmptyFailed++;
         if (!this.allFailedTold && this.nonEmptySeen == 20 && this.nonEmptyFailed == 20) {
            this.allFailedTold = true;
            verdict = String.format(
               "Chunks from %s cannot be read: the first %d columns with blocks all failed (%s), so the world stays empty. This server sends chunks in a format BedrockNative cannot read yet; failures by kind %s",
               this.server,
               20,
               this.firstKind,
               this.kinds
            );
         }
      }

      return new ChunkDiagnostics.Failure(description, verdict);
   }

   public synchronized String summary() {
      return String.format("full columns read %d, unreadable %d%s", this.read, this.failed, this.failed > 0 ? " (by kind " + this.kinds + ")" : "");
   }

   public synchronized int failed() {
      return this.failed;
   }

   public synchronized int read() {
      return this.read;
   }

   private String describe(ChunkDiagnostics.Column column, ByteBuf payload, int payloadStart, int payloadLength, RuntimeException failure, String kind) {
      StringBuilder where = new StringBuilder();
      if (failure instanceof BedrockChunkCodec.FormatException located) {
         if (located.subChunk() >= 0) {
            where.append("sub-chunk ").append(located.subChunk());
            if (located.version() >= 0) {
               where.append(" (version byte ").append(located.version()).append(')');
            }

            if (located.storage() >= 0) {
               where.append(" storage ").append(located.storage());
            }
         } else if (located.biome() >= 0) {
            where.append("biome storage ").append(located.biome());
         }

         if (located.elementStart() >= 0) {
            where.append(", element at offset ").append(located.elementStart() - payloadStart);
         }

         if (located.failedAt() >= 0) {
            where.append(", reader at offset ").append(located.failedAt() - payloadStart);
         }
      } else {
         where.append("reader at offset ").append(payload.readerIndex() - payloadStart);
      }

      int shown = Math.max(0, Math.min(160, Math.min(payloadLength, payload.writerIndex() - payloadStart)));
      return String.format(
         "First unreadable chunk from %s: column (%d, %d), subChunkCount %d, cachingEnabled %s, blob ids %d, payload %d bytes; failed at %s: %s; payload starts (%d bytes): %s",
         this.server,
         column.chunkX(),
         column.chunkZ(),
         column.subChunkCount(),
         column.cachingEnabled(),
         column.blobIds(),
         payloadLength,
         where,
         kind,
         shown,
         ByteBufUtil.hexDump(payload, payloadStart, shown)
      );
   }

   static String kind(RuntimeException failure) {
      Throwable shown = (Throwable)(failure instanceof BedrockChunkCodec.FormatException && failure.getCause() != null ? failure.getCause() : failure);
      String message = String.valueOf(shown.getMessage()).replaceAll("-?\\d+", "#");
      String kind = shown.getClass().getSimpleName() + ": " + message;
      return kind.length() > 140 ? kind.substring(0, 140) + "..." : kind;
   }

   public record Column(int chunkX, int chunkZ, int subChunkCount, boolean cachingEnabled, int blobIds) {
   }

   public record Failure(@Nullable String firstDescription, @Nullable String allFailed) {
   }
}
