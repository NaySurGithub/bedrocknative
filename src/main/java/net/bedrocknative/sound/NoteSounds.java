package net.bedrocknative.sound;

import org.jspecify.annotations.Nullable;

public final class NoteSounds {
   private static final String[] INSTRUMENTS = new String[]{
      "note.harp",
      "note.bd",
      "note.snare",
      "note.hat",
      "note.bass",
      "note.bell",
      "note.flute",
      "note.chime",
      "note.guitar",
      "note.xylophone",
      "note.iron_xylophone",
      "note.cow_bell",
      "note.didgeridoo",
      "note.bit",
      "note.banjo",
      "note.pling",
      "note.skeleton",
      "note.witherskeleton",
      "note.zombie",
      "note.creeper",
      "note.enderdragon",
      "note.piglin",
      "note.trumpet",
      "note.trumpet_exposed",
      "note.trumpet_weathered",
      "note.trumpet_oxidized"
   };
   private static final int FIRST_HEAD = 16;
   private static final int FIRST_TRUMPET = 22;

   private NoteSounds() {
   }

   public static @Nullable String sound(int data) {
      int instrument = data >> 8 & 0xFF;
      return instrument < INSTRUMENTS.length ? INSTRUMENTS[instrument] : null;
   }

   public static float pitch(int data) {
      int instrument = data >> 8 & 0xFF;
      return instrument >= 16 && instrument < 22 ? 1.0F : (float)Math.pow(2.0, ((data & 0xFF) - 12) / 12.0);
   }
}
