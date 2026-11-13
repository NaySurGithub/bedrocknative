package net.bedrocknative.sound;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public final class PcmCollector {
   public static final long MAX_BYTES = 67108864L;
   private static final int CHUNK = 65536;

   private PcmCollector() {
   }

   public static short[] collect(PcmCollector.Source source) throws IOException {
      return collect(source, 67108864L);
   }

   static short[] collect(PcmCollector.Source source, long maxBytes) throws IOException {
      short[] samples = new short[16384];
      int count = 0;

      while (true) {
         ByteBuffer chunk = source.read(65536).order(ByteOrder.nativeOrder());
         int shorts = chunk.remaining() / 2;
         if (shorts == 0) {
            return count == samples.length ? samples : Arrays.copyOf(samples, count);
         }

         if (((long)count + shorts) * 2L > maxBytes) {
            throw new IOException("decoded audio longer than " + maxBytes / 1048576L + " MiB");
         }

         if (count + shorts > samples.length) {
            samples = Arrays.copyOf(samples, Math.max(count + shorts, (int)Math.min(2147483639L, samples.length * 2L)));
         }

         chunk.asShortBuffer().get(samples, count, shorts);
         count += shorts;
      }
   }

   @FunctionalInterface
   public interface Source {
      ByteBuffer read(int var1) throws IOException;
   }
}
