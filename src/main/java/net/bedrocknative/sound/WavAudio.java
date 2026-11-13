package net.bedrocknative.sound;

import org.jspecify.annotations.Nullable;

public final class WavAudio {
   private static final int FORMAT_PCM = 1;
   private static final int FORMAT_FLOAT = 3;
   private static final int FORMAT_EXTENSIBLE = 65534;

   private WavAudio() {
   }

   public static boolean isWav(byte[] data) {
      return data.length >= 12
         && data[0] == 82
         && data[1] == 73
         && data[2] == 70
         && data[3] == 70
         && data[8] == 87
         && data[9] == 65
         && data[10] == 86
         && data[11] == 69;
   }

   public static @Nullable PcmAudio decode(byte[] data) {
      if (!isWav(data)) {
         return null;
      }

      int format = -1;
      int channels = 0;
      int rate = 0;
      int bits = 0;
      int position = 12;

      while (position + 8 <= data.length) {
         int size = Fsb5.intLE(data, position + 4);
         int body = position + 8;
         if (size < 0 || (long)body + size > data.length) {
            size = data.length - body;
         }

         if (data[position] == 102 && data[position + 1] == 109 && data[position + 2] == 116 && data[position + 3] == 32 && size >= 16) {
            format = data[body] & 255 | (data[body + 1] & 255) << 8;
            channels = data[body + 2] & 255 | (data[body + 3] & 255) << 8;
            rate = Fsb5.intLE(data, body + 4);
            bits = data[body + 14] & 255 | (data[body + 15] & 255) << 8;
            if (format == 65534 && size >= 26) {
               format = data[body + 24] & 255 | (data[body + 25] & 255) << 8;
            }
         } else if (data[position] == 100 && data[position + 1] == 97 && data[position + 2] == 116 && data[position + 3] == 97) {
            if (channels > 0 && rate > 0) {
               return samples(data, body, size, format, channels, rate, bits);
            }

            return null;
         }

         position = body + size + (size & 1);
      }

      return null;
   }

   private static @Nullable PcmAudio samples(byte[] data, int offset, int length, int format, int channels, int rate, int bits) {
      int bytes = bits / 8;
      if (bytes > 0 && (format == 1 || format == 3 && bits == 32)) {
         int count = length / bytes / channels * channels;
         short[] out = new short[count];

         for (int i = 0; i < count; i++) {
            int at = offset + i * bytes;

            out[i] = switch (bits) {
               case 8 -> (short)((data[at] & 255) - 128 << 8);
               case 16 -> (short)(data[at + 1] << 8 | data[at] & 0xFF);
               case 24 -> (short)(data[at + 2] << 8 | data[at + 1] & 0xFF);
               case 32 -> format == 3 ? PcmAudio.clamp(Math.round(Float.intBitsToFloat(Fsb5.intLE(data, at)) * 32767.0F)) : (short)(Fsb5.intLE(data, at) >> 16);
               default -> 0;
            };
         }

         return bits != 8 && bits != 16 && bits != 24 && bits != 32 ? null : new PcmAudio(rate, channels, out);
      } else {
         return null;
      }
   }
}
