package net.bedrocknative.particle;

import org.jspecify.annotations.Nullable;

public interface ParticleWorld {
   ParticleWorld NONE = new ParticleWorld() {};

   default int collide(double x, double y, double z, double radius, double[] motion) {
      return 0;
   }

   default String blockName(double x, double y, double z) {
      return "minecraft:air";
   }

   default void playSound(String event, double x, double y, double z, ParticleEffect source) {
   }

   default @Nullable ParticleEffect effect(String identifier) {
      return null;
   }

   default void log(ParticleEffect effect, String message, double x, double y, double z) {
   }
}
