package net.bedrocknative.sound;

public final class SoundRange {
   public static final float DEFAULT_RANGE = 16.0F;

   private SoundRange() {
   }

   public static float range(SoundDefinitions.Definition definition, float volume) {
      float byVolume = Math.max(16.0F, 16.0F * volume);
      float max = definition.maxDistance();
      if (!Float.isNaN(max) && !(max <= 0.0F)) {
         return definition.legacyMaxDistance() ? Math.min(max, byVolume) : max;
      } else {
         return byVolume;
      }
   }

   public static float start(SoundDefinitions.Definition definition, float range) {
      float min = definition.minDistance();
      return !Float.isNaN(min) && !(min <= 0.0F) ? Math.min(min, range) : 0.0F;
   }

   public static float gain(float requested, SoundDefinitions.Definition definition, SoundDefinitions.Variant variant) {
      return Math.max(0.0F, Math.min(requested, 1.0F) * definition.volume() * variant.volume());
   }

   public static boolean positional(SoundDefinitions.Definition definition, SoundDefinitions.Variant variant) {
      return variant.is3d() && !"music".equals(definition.category()) && !"ui".equals(definition.category());
   }

   public static boolean ranged(SoundDefinitions.Definition definition) {
      return !"music".equals(definition.category());
   }
}
