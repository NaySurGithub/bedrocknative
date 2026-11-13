package net.bedrocknative.sound;

public record PcmAudio(int sampleRate, int channels, short[] samples) {
   public int frames() {
      return this.samples.length / this.channels;
   }

   public float seconds() {
      return this.sampleRate <= 0 ? 0.0F : (float)this.frames() / this.sampleRate;
   }

   public PcmAudio mono() {
      if (this.channels == 1) {
         return this;
      } else {
         int frames = this.frames();
         short[] mixed = new short[frames];

         for (int frame = 0; frame < frames; frame++) {
            int sum = 0;

            for (int channel = 0; channel < this.channels; channel++) {
               sum += this.samples[frame * this.channels + channel];
            }

            mixed[frame] = (short)(sum / this.channels);
         }

         return new PcmAudio(this.sampleRate, 1, mixed);
      }
   }

   public PcmAudio atMostStereo() {
      if (this.channels <= 2) {
         return this;
      } else {
         int frames = this.frames();
         short[] pair = new short[frames * 2];

         for (int frame = 0; frame < frames; frame++) {
            pair[frame * 2] = this.samples[frame * this.channels];
            pair[frame * 2 + 1] = this.samples[frame * this.channels + 1];
         }

         return new PcmAudio(this.sampleRate, 2, pair);
      }
   }

   public PcmAudio from(float seconds) {
      int skip = Math.min(this.frames(), Math.max(0, Math.round(seconds * this.sampleRate)));
      if (skip == 0) {
         return this;
      } else {
         short[] rest = new short[(this.frames() - skip) * this.channels];
         System.arraycopy(this.samples, skip * this.channels, rest, 0, rest.length);
         return new PcmAudio(this.sampleRate, this.channels, rest);
      }
   }

   static short clamp(int value) {
      return (short)Math.max(-32768, Math.min(32767, value));
   }
}
