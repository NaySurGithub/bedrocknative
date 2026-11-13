package net.bedrocknative.world.chunk.cache;

public final class XxHash64 {
   private static final long P1 = -7046029288634856825L;
   private static final long P2 = -4417276706812531889L;
   private static final long P3 = 1609587929392839161L;
   private static final long P4 = -8796714831421723037L;
   private static final long P5 = 2870177450012600261L;

   private XxHash64() {
   }

   public static long hash(byte[] data) {
      return hash(data, 0, data.length);
   }

   public static long hash(byte[] data, int offset, int length) {
      int p = offset;
      int end = offset + length;
      long h;
      if (length >= 32) {
         long v1 = 6983438078262162902L;
         long v2 = -4417276706812531889L;
         long v3 = 0L;
         long v4 = 7046029288634856825L;
         int limit = end - 32;

         do {
            v1 = round(v1, readLong(data, p));
            v2 = round(v2, readLong(data, p + 8));
            v3 = round(v3, readLong(data, p + 16));
            v4 = round(v4, readLong(data, p + 24));
            p += 32;
         } while (p <= limit);

         h = Long.rotateLeft(v1, 1) + Long.rotateLeft(v2, 7) + Long.rotateLeft(v3, 12) + Long.rotateLeft(v4, 18);
         h = mergeRound(h, v1);
         h = mergeRound(h, v2);
         h = mergeRound(h, v3);
         h = mergeRound(h, v4);
      } else {
         h = 2870177450012600261L;
      }

      for (h += length; p + 8 <= end; p += 8) {
         h ^= round(0L, readLong(data, p));
         h = Long.rotateLeft(h, 27) * -7046029288634856825L + -8796714831421723037L;
      }

      if (p + 4 <= end) {
         h ^= (readInt(data, p) & 4294967295L) * -7046029288634856825L;
         h = Long.rotateLeft(h, 23) * -4417276706812531889L + 1609587929392839161L;
         p += 4;
      }

      while (p < end) {
         h ^= (data[p] & 255L) * 2870177450012600261L;
         h = Long.rotateLeft(h, 11) * -7046029288634856825L;
         p++;
      }

      h ^= h >>> 33;
      h *= -4417276706812531889L;
      h ^= h >>> 29;
      h *= 1609587929392839161L;
      return h ^ h >>> 32;
   }

   private static long round(long acc, long input) {
      acc += input * -4417276706812531889L;
      acc = Long.rotateLeft(acc, 31);
      return acc * -7046029288634856825L;
   }

   private static long mergeRound(long acc, long value) {
      acc ^= round(0L, value);
      return acc * -7046029288634856825L + -8796714831421723037L;
   }

   private static long readLong(byte[] b, int i) {
      return b[i] & 255L
         | (b[i + 1] & 255L) << 8
         | (b[i + 2] & 255L) << 16
         | (b[i + 3] & 255L) << 24
         | (b[i + 4] & 255L) << 32
         | (b[i + 5] & 255L) << 40
         | (b[i + 6] & 255L) << 48
         | (b[i + 7] & 255L) << 56;
   }

   private static int readInt(byte[] b, int i) {
      return b[i] & 0xFF | (b[i + 1] & 0xFF) << 8 | (b[i + 2] & 0xFF) << 16 | (b[i + 3] & 0xFF) << 24;
   }
}
