package net.bedrocknative.content;

import java.util.List;
import org.jspecify.annotations.Nullable;

public record FlipbookAnimation(int[] order, int ticksPerFrame, boolean blend) {
   public FlipbookAnimation {
      if (order.length == 0) {
         throw new IllegalArgumentException("A flipbook needs a frame");
      }

      order = (int[])order.clone();
      ticksPerFrame = Math.max(1, ticksPerFrame);
   }

   public static @Nullable FlipbookAnimation of(FlipbookData.Flipbook flipbook, int frameCount) {
      List<Integer> order = flipbook.order(frameCount);
      return order.size() < 2
         ? null
         : new FlipbookAnimation(order.stream().mapToInt(Integer::intValue).toArray(), flipbook.ticksPerFrame(), flipbook.blendFrames());
   }

   public static int stillFrame(FlipbookData.Flipbook flipbook, int frameCount) {
      List<Integer> order = flipbook.order(frameCount);
      return order.isEmpty() ? 0 : order.getFirst();
   }

   public long length() {
      return (long)this.order.length * this.ticksPerFrame;
   }

   public int frame(long tick) {
      return this.order[(int)(Math.floorMod(tick, this.length()) / this.ticksPerFrame)];
   }

   public int nextFrame(long tick) {
      return this.order[(int)((Math.floorMod(tick, this.length()) / this.ticksPerFrame + 1L) % this.order.length)];
   }

   public float blendFactor(long tick) {
      return this.blend ? (float)(Math.floorMod(tick, this.length()) % this.ticksPerFrame) / this.ticksPerFrame : 0.0F;
   }

   public long picture(long tick) {
      long time = Math.floorMod(tick, this.length());
      return this.blend ? time : time / this.ticksPerFrame;
   }

   public static int lerp(int from, int to, float factor) {
      if (!(factor <= 0.0F) && from != to) {
         int result = 0;

         for (int shift = 0; shift < 32; shift += 8) {
            int a = from >>> shift & 0xFF;
            int b = to >>> shift & 0xFF;
            result |= Math.round(a + (b - a) * factor) << shift;
         }

         return result;
      } else {
         return from;
      }
   }

   public static void lerp(int[] from, int[] to, float factor, int[] out) {
      for (int i = 0; i < out.length; i++) {
         out[i] = lerp(from[i], to[i], factor);
      }
   }
}
