package net.bedrocknative.sound;

final class Fadpcm {
   static final int FRAME_BYTES = 140;
   static final int FRAME_SAMPLES = 256;
   private static final int[] COEFFICIENT_1 = new int[]{0, 60, 122, 115, 98, 0, 0, 0};
   private static final int[] COEFFICIENT_2 = new int[]{0, 0, 60, 52, 55, 0, 0, 0};

   private Fadpcm() {
   }

   static PcmAudio decode(byte[] data, int offset, int length, int channels, int sampleRate, int frames) {
      int blocks = length / (140 * channels);
      int available = blocks * 256;
      int total = frames > 0 ? Math.min(frames, available) : available;
      short[] out = new short[total * channels];
      short[] frame = new short[256];

      for (int block = 0; block < blocks && block * 256 < total; block++) {
         int count = Math.min(256, total - block * 256);

         for (int channel = 0; channel < channels; channel++) {
            decodeFrame(data, offset + (block * channels + channel) * 140, frame);
            int base = block * 256 * channels + channel;

            for (int i = 0; i < count; i++) {
               out[base + i * channels] = frame[i];
            }
         }
      }

      return new PcmAudio(sampleRate, channels, out);
   }

   static void decodeFrame(byte[] data, int offset, short[] out) {
      int coefficients = Fsb5.intLE(data, offset);
      int shifts = Fsb5.intLE(data, offset + 4);
      int history1 = (short)(data[offset + 9] << 8 | data[offset + 8] & 0xFF);
      int history2 = (short)(data[offset + 11] << 8 | data[offset + 10] & 0xFF);
      int written = 0;

      for (int group = 0; group < 8; group++) {
         int index = (coefficients >>> group * 4 & 15) % 7;
         int shift = 22 - (shifts >>> group * 4 & 15);
         int coefficient1 = COEFFICIENT_1[index];
         int coefficient2 = COEFFICIENT_2[index];

         for (int word = 0; word < 4; word++) {
            int nibbles = Fsb5.intLE(data, offset + 12 + 16 * group + 4 * word);

            for (int nibble = 0; nibble < 8; nibble++) {
               int sample = (nibbles >>> nibble * 4 & 15) << 28 >> shift;
               int var17 = PcmAudio.clamp(sample - history2 * coefficient2 + history1 * coefficient1 >> 6);
               out[written++] = (short)var17;
               history2 = history1;
               history1 = var17;
            }
         }
      }
   }

   static final class Stream implements PcmStream {
      private final byte[] data;
      private final int offset;
      private final int channels;
      private final int sampleRate;
      private final int blocks;
      private final int total;
      private final short[] frame = new short[256];
      private final short[] pending;
      private int pendingStart;
      private int pendingEnd;
      private int block;
      private int position;

      Stream(byte[] data, int offset, int length, int channels, int sampleRate, int frames) {
         this.data = data;
         this.offset = offset;
         this.channels = channels;
         this.sampleRate = sampleRate;
         this.blocks = length / (140 * channels);
         int available = this.blocks * 256;
         this.total = frames > 0 ? Math.min(frames, available) : available;
         this.pending = new short[256 * channels];
      }

      @Override
      public int sampleRate() {
         return this.sampleRate;
      }

      @Override
      public int channels() {
         return this.channels;
      }

      @Override
      public int read(short[] into) {
         int capacity = into.length - into.length % this.channels;
         int written = 0;

         while (written < capacity && (this.pendingStart != this.pendingEnd || this.nextBlock())) {
            int count = Math.min(capacity - written, this.pendingEnd - this.pendingStart);
            System.arraycopy(this.pending, this.pendingStart, into, written, count);
            this.pendingStart += count;
            written += count;
         }

         return written;
      }

      @Override
      public void skip(float seconds) {
         int current = this.position - (this.pendingEnd - this.pendingStart) / this.channels;
         int target = Math.min(this.total, current + Math.max(0, Math.round(seconds * this.sampleRate)));
         if (target <= this.position) {
            this.pendingStart = this.pendingStart + (target - current) * this.channels;
         } else {
            this.block = target / 256;
            this.position = this.block * 256;
            this.pendingStart = this.pendingEnd = 0;
            int start = this.position;
            if (this.nextBlock()) {
               this.pendingStart = Math.min(this.pendingEnd, (target - start) * this.channels);
            }
         }
      }

      private boolean nextBlock() {
         if (this.block < this.blocks && this.position < this.total) {
            int count = Math.min(256, this.total - this.position);

            for (int channel = 0; channel < this.channels; channel++) {
               Fadpcm.decodeFrame(this.data, this.offset + (this.block * this.channels + channel) * 140, this.frame);

               for (int i = 0; i < count; i++) {
                  this.pending[i * this.channels + channel] = this.frame[i];
               }
            }

            this.block++;
            this.position += count;
            this.pendingStart = 0;
            this.pendingEnd = count * this.channels;
            return true;
         } else {
            return false;
         }
      }
   }
}
