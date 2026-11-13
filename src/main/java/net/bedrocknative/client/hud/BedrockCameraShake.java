package net.bedrocknative.client.hud;

import java.util.concurrent.ThreadLocalRandom;
import net.bedrocknative.BedrockNative;
import net.minecraft.util.Mth;
import org.cloudburstmc.protocol.bedrock.data.CameraShakeType;
import org.cloudburstmc.protocol.bedrock.packet.CameraShakePacket;
import org.jspecify.annotations.Nullable;

public final class BedrockCameraShake {
   private static final float MAX_INTENSITY = 4.0F;
   private static final float POSITION_SCALE = 0.04F;
   private static final float ROTATION_SCALE = 1.0F;
   private static final float FADE_OUT_SECONDS = 0.25F;
   private static final float[] FREQUENCIES = new float[]{71.0F, 53.0F, 89.0F};
   private static final float[] OVERTONES = new float[]{137.0F, 109.0F, 149.0F};
   private static BedrockCameraShake.@Nullable Shake current;
   private static float peakOffset;
   private static volatile String lastRequest = "";

   private BedrockCameraShake() {
   }

   public static void apply(CameraShakePacket packet) {
      lastRequest = packet.toString();
      switch (packet.getShakeAction()) {
         case ADD:
            float seconds = packet.getDuration();
            if (!(seconds > 0.0F) || !(packet.getIntensity() > 0.0F)) {
               current = null;
               return;
            }

            float[] phases = new float[FREQUENCIES.length * 2];

            for (int i = 0; i < phases.length; i++) {
               phases[i] = ThreadLocalRandom.current().nextFloat() * (float) (Math.PI * 2);
            }

            long now = System.nanoTime();
            current = new BedrockCameraShake.Shake(Math.min(packet.getIntensity(), 4.0F), packet.getShakeType(), now, now + (long)(seconds * 1.0E9F), phases);
            BedrockNative.LOGGER.debug("CameraShake {} intensity {} for {} s", new Object[]{packet.getShakeType(), packet.getIntensity(), seconds});
            break;
         case STOP:
            current = null;
      }
   }

   public static boolean active() {
      BedrockCameraShake.Shake shake = current;
      return shake != null && System.nanoTime() < shake.endNanos;
   }

   public static BedrockCameraShake.Offset offset() {
      BedrockCameraShake.Shake shake = current;
      if (shake == null) {
         return BedrockCameraShake.Offset.NONE;
      }

      long now = System.nanoTime();
      if (now >= shake.endNanos) {
         current = null;
         return BedrockCameraShake.Offset.NONE;
      }

      float elapsed = (float)(now - shake.startNanos) / 1.0E9F;
      float remaining = (float)(shake.endNanos - now) / 1.0E9F;
      float amplitude = shake.intensity * Math.min(1.0F, remaining / 0.25F);
      float[] axes = new float[3];

      for (int i = 0; i < axes.length; i++) {
         axes[i] = noise(shake, elapsed, i) * amplitude;
      }

      BedrockCameraShake.Offset offset = shake.type == CameraShakeType.POSITIONAL
         ? new BedrockCameraShake.Offset(axes[0] * 0.04F, axes[1] * 0.04F, axes[2] * 0.04F, 0.0F, 0.0F)
         : new BedrockCameraShake.Offset(0.0F, 0.0F, 0.0F, axes[0] * 1.0F, axes[1] * 1.0F);
      float largest = Math.max(
         Math.max(Math.abs(offset.x), Math.abs(offset.y)), Math.max(Math.abs(offset.z), Math.max(Math.abs(offset.yaw), Math.abs(offset.pitch)))
      );
      peakOffset = Math.max(peakOffset, largest);
      return offset;
   }

   public static float takePeakOffset() {
      float peak = peakOffset;
      peakOffset = 0.0F;
      return peak;
   }

   private static float noise(BedrockCameraShake.Shake shake, float seconds, int axis) {
      return Mth.sin(seconds * FREQUENCIES[axis] + shake.phases[axis]) * 0.65F
         + Mth.sin(seconds * OVERTONES[axis] + shake.phases[axis + FREQUENCIES.length]) * 0.35F;
   }

   public static String lastRequest() {
      return lastRequest;
   }

   public static void reset() {
      current = null;
      peakOffset = 0.0F;
   }

   public record Offset(float x, float y, float z, float yaw, float pitch) {
      static final BedrockCameraShake.Offset NONE = new BedrockCameraShake.Offset(0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
   }

   private record Shake(float intensity, CameraShakeType type, long startNanos, long endNanos, float[] phases) {
   }
}
