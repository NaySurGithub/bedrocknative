package net.bedrocknative.sound;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

public final class Fsb5 {
   public static final int CODEC_PCM8 = 1;
   public static final int CODEC_PCM16 = 2;
   public static final int CODEC_PCM_FLOAT = 5;
   public static final int CODEC_VORBIS = 15;
   public static final int CODEC_FADPCM = 16;
   private static final int MAGIC = 893539142;
   private static final int[] FREQUENCIES = new int[]{4000, 8000, 11000, 11025, 16000, 22050, 24000, 32000, 44100, 48000, 96000};
   private static final int CHUNK_CHANNELS = 1;
   private static final int CHUNK_FREQUENCY = 2;
   private static final int CHUNK_LOOP = 3;
   private final byte[] data;
   private final int codec;
   private final boolean bigEndian;
   private final List<Fsb5.Sample> samples;

   private Fsb5(byte[] data, int codec, boolean bigEndian, List<Fsb5.Sample> samples) {
      this.data = data;
      this.codec = codec;
      this.bigEndian = bigEndian;
      this.samples = samples;
   }

   public static boolean isFsb5(byte[] data) {
      return data.length >= 4 && intLE(data, 0) == 893539142;
   }

   @Nullable
   public static Fsb5 parse(byte[] data) {
      if (data.length >= 60 && isFsb5(data)) {
         int version = intLE(data, 4);
         int count = intLE(data, 8);
         int sampleHeaders = intLE(data, 12);
         int nameTable = intLE(data, 16);
         int dataSize = intLE(data, 20);
         int codec = intLE(data, 24);
         if ((version == 0 || version == 1) && count > 0 && count <= 65536 && sampleHeaders >= 0 && nameTable >= 0 && dataSize >= 0) {
            int base = version == 1 ? 60 : 64;
            boolean bigEndian = version == 1 && (intLE(data, 32) & 1) != 0;
            long dataStart = (long)base + sampleHeaders + nameTable;
            if (dataStart > data.length) {
               return null;
            } else {
               List<Fsb5.Sample> samples = new ArrayList<>(count);
               int position = base;
               long[] modes = new long[count];
               int[][] overrides = new int[count][];

               for (int i = 0; i < count; i++) {
                  if (position + 8 > base + sampleHeaders) {
                     return null;
                  }

                  long mode = longLE(data, position);
                  position += 8;
                  modes[i] = mode;
                  int[] values = new int[]{-1, -1, -1, -1};
                  boolean more = (mode & 1L) != 0L;

                  while (more) {
                     if (position + 4 > base + sampleHeaders) {
                        return null;
                     }

                     int chunk = intLE(data, position);
                     more = (chunk & 1) != 0;
                     int size = chunk >>> 1 & 16777215;
                     int type = chunk >>> 25 & 127;
                     int body = position + 4;
                     if (body + size > base + sampleHeaders) {
                        return null;
                     }

                     if (type == 1 && size >= 1) {
                        values[0] = data[body] & 255;
                     } else if (type == 2 && size >= 4) {
                        values[1] = intLE(data, body);
                     } else if (type == 3 && size >= 4) {
                        values[2] = intLE(data, body);
                        values[3] = size >= 8 ? intLE(data, body + 4) + 1 : -1;
                     }

                     position = body + size;
                  }

                  overrides[i] = values;
               }

               for (int i = 0; i < count; i++) {
                  long mode = modes[i];
                  int frames = (int)(mode >>> 34 & 1073741823L);
                  long offset = (mode >>> 7 & 134217727L) << 5;
                  long next = i + 1 < count ? (modes[i + 1] >>> 7 & 134217727L) << 5 : dataSize;

                  int channels = switch ((int)(mode >>> 5 & 3L)) {
                     case 0 -> 1;
                     case 1 -> 2;
                     case 2 -> 6;
                     default -> 8;
                  };
                  int index = (int)(mode >>> 1 & 15L);
                  int frequency = index < FREQUENCIES.length ? FREQUENCIES[index] : '걄';
                  int[] values = overrides[i];
                  if (values[0] > 0) {
                     channels = values[0];
                  }

                  if (values[1] > 0) {
                     frequency = values[1];
                  }

                  long start = dataStart + offset;
                  long end = Math.min(dataStart + Math.max(offset, next), (long)data.length);
                  if (start > data.length) {
                     return null;
                  }

                  samples.add(
                     new Fsb5.Sample(
                        name(data, base + sampleHeaders, nameTable, i, count),
                        frequency,
                        channels,
                        frames,
                        (int)start,
                        (int)(end - start),
                        values[2],
                        values[3]
                     )
                  );
               }

               return new Fsb5(data, codec, bigEndian, List.copyOf(samples));
            }
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   public int codec() {
      return this.codec;
   }

   public List<Fsb5.Sample> samples() {
      return this.samples;
   }

   public boolean decodable() {
      return this.codec == 16 || this.codec == 2 || this.codec == 1 || this.codec == 5;
   }

   @Nullable
   public PcmAudio decode() {
      return this.decode(0);
   }

   @Nullable
   public PcmAudio decode(int index) {
      if (this.decodable() && index >= 0 && index < this.samples.size()) {
         Fsb5.Sample sample = this.samples.get(index);

         return switch (this.codec) {
            case 1 -> this.pcm8(sample);
            case 2 -> this.pcm16(sample);
            case 16 -> Fadpcm.decode(this.data, sample.offset(), sample.length(), sample.channels(), sample.frequency(), sample.frames());
            default -> this.pcmFloat(sample);
         };
      } else {
         return null;
      }
   }

   @Nullable
   public PcmStream stream(int index) {
      if (this.decodable() && index >= 0 && index < this.samples.size()) {
         Fsb5.Sample sample = this.samples.get(index);
         if (this.codec == 16) {
            return new Fadpcm.Stream(this.data, sample.offset(), sample.length(), sample.channels(), sample.frequency(), sample.frames());
         } else {
            PcmAudio whole = this.decode(index);
            return whole == null ? null : PcmStream.of(whole);
         }
      } else {
         return null;
      }
   }

   private PcmAudio pcm16(Fsb5.Sample sample) {
      int frames = Math.min(sample.frames() > 0 ? sample.frames() : Integer.MAX_VALUE, sample.length() / (2 * sample.channels()));
      short[] out = new short[frames * sample.channels()];

      for (int i = 0; i < out.length; i++) {
         int at = sample.offset() + i * 2;
         out[i] = this.bigEndian ? (short)(this.data[at] << 8 | this.data[at + 1] & 255) : (short)(this.data[at + 1] << 8 | this.data[at] & 255);
      }

      return new PcmAudio(sample.frequency(), sample.channels(), out);
   }

   private PcmAudio pcm8(Fsb5.Sample sample) {
      int frames = Math.min(sample.frames() > 0 ? sample.frames() : Integer.MAX_VALUE, sample.length() / sample.channels());
      short[] out = new short[frames * sample.channels()];

      for (int i = 0; i < out.length; i++) {
         out[i] = (short)((this.data[sample.offset() + i] & 255) - 128 << 8);
      }

      return new PcmAudio(sample.frequency(), sample.channels(), out);
   }

   private PcmAudio pcmFloat(Fsb5.Sample sample) {
      int frames = Math.min(sample.frames() > 0 ? sample.frames() : Integer.MAX_VALUE, sample.length() / (4 * sample.channels()));
      short[] out = new short[frames * sample.channels()];

      for (int i = 0; i < out.length; i++) {
         float value = Float.intBitsToFloat(this.bigEndian ? intBE(this.data, sample.offset() + i * 4) : intLE(this.data, sample.offset() + i * 4));
         out[i] = PcmAudio.clamp(Math.round(value * 32767.0F));
      }

      return new PcmAudio(sample.frequency(), sample.channels(), out);
   }

   private static String name(byte[] data, int table, int size, int index, int count) {
      if (size >= count * 4 && table + size <= data.length) {
         int start = table + intLE(data, table + index * 4);
         if (start >= table && start < table + size) {
            int end = start;

            while (end < table + size && data[end] != 0) {
               end++;
            }

            return new String(data, start, end - start, StandardCharsets.UTF_8);
         } else {
            return "";
         }
      } else {
         return "";
      }
   }

   static int intLE(byte[] data, int at) {
      return data[at] & 0xFF | (data[at + 1] & 0xFF) << 8 | (data[at + 2] & 0xFF) << 16 | (data[at + 3] & 0xFF) << 24;
   }

   private static int intBE(byte[] data, int at) {
      return (data[at] & 0xFF) << 24 | (data[at + 1] & 0xFF) << 16 | (data[at + 2] & 0xFF) << 8 | data[at + 3] & 0xFF;
   }

   private static long longLE(byte[] data, int at) {
      return intLE(data, at) & 4294967295L | (long)intLE(data, at + 4) << 32;
   }

   public record Sample(String name, int frequency, int channels, int frames, int offset, int length, int loopStart, int loopEnd) {
   }
}
