package net.bedrocknative.sound;

public interface PcmStream {
   int sampleRate();

   int channels();

   int read(short[] var1);

   void skip(float var1);

   static PcmStream of(final PcmAudio audio) {
      return new PcmStream() {
         private int position;

         @Override
         public int sampleRate() {
            return audio.sampleRate();
         }

         @Override
         public int channels() {
            return audio.channels();
         }

         @Override
         public int read(short[] into) {
            int count = Math.min(into.length - into.length % audio.channels(), audio.samples().length - this.position);
            System.arraycopy(audio.samples(), this.position, into, 0, count);
            this.position += count;
            return count;
         }

         @Override
         public void skip(float seconds) {
            this.position = Math.min(audio.samples().length, this.position + Math.max(0, Math.round(seconds * audio.sampleRate())) * audio.channels());
         }
      };
   }
}
