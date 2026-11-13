package net.bedrocknative.client.sound;

import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.resources.sounds.Sound.Type;
import net.minecraft.client.resources.sounds.SoundInstance.Attenuation;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.ConstantFloat;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class BedrockSoundInstance extends AbstractSoundInstance implements TickableSoundInstance {
   private final String name;
   private final BedrockSoundInstance.RangedSound bedrockSound;
   private final WeighedSoundEvents events;
   private final float range;
   private final float start;
   private float gain;
   private float fadeFrom;
   private float fadeTo;
   private int fadeTicks;
   private int fadeTotal;
   private boolean paused;
   private boolean stopped;
   private int plays;
   private final int totalPlays;

   BedrockSoundInstance(
      String name,
      Identifier location,
      boolean stream,
      SoundSource source,
      Vec3 position,
      boolean positional,
      float gain,
      float pitch,
      float range,
      float start,
      int loopCount,
      @Nullable String subtitle,
      RandomSource random
   ) {
      super(Identifier.fromNamespaceAndPath("bedrocknative", "sound/" + sanitize(name)), source, random);
      this.name = name;
      this.range = range;
      this.start = start;
      this.bedrockSound = new BedrockSoundInstance.RangedSound(location, stream, range);
      this.events = new WeighedSoundEvents(this.identifier, subtitle);
      this.gain = gain;
      this.volume = gain;
      this.pitch = pitch;
      if (positional) {
         this.x = position.x;
         this.y = position.y;
         this.z = position.z;
         this.attenuation = Attenuation.LINEAR;
         this.relative = false;
      } else {
         this.attenuation = Attenuation.NONE;
         this.relative = true;
      }

      this.totalPlays = loopCount < 0 ? -1 : loopCount + 1;
      this.looping = loopCount != 0;
      this.delay = loopCount > 0 ? 1 : 0;
   }

   static String sanitize(String name) {
      StringBuilder clean = new StringBuilder(name.length());

      for (int i = 0; i < name.length(); i++) {
         char c = Character.toLowerCase(name.charAt(i));
         clean.append((c < 'a' || c > 'z') && (c < '0' || c > '9') && c != '.' && c != '_' && c != '-' && c != '/' ? '_' : c);
      }

      return clean.isEmpty() ? "unnamed" : clean.toString();
   }

   public WeighedSoundEvents getOrResolve(SoundManager soundManager) {
      this.sound = this.bedrockSound;
      this.soundEvent = this.events;
      this.plays++;
      if (this.totalPlays > 0 && this.plays >= this.totalPlays) {
         this.looping = false;
      }

      return this.events;
   }

   public String name() {
      return this.name;
   }

   public float range() {
      return this.range;
   }

   public float start() {
      return this.start;
   }

   public boolean canStartSilent() {
      return true;
   }

   public boolean isStopped() {
      return this.stopped;
   }

   public void stopNow() {
      this.stopped = true;
      this.looping = false;
   }

   public void setGain(float gain) {
      this.gain = Math.max(0.0F, gain);
      this.fadeTicks = 0;
      this.refresh();
   }

   public void setPitch(float pitch) {
      this.pitch = pitch;
   }

   public void fade(float target, float seconds) {
      this.fadeFrom = this.gain;
      this.fadeTo = Math.max(0.0F, target);
      this.fadeTotal = Math.max(1, Math.round(seconds * 20.0F));
      this.fadeTicks = this.fadeTotal;
   }

   public void setPaused(boolean paused) {
      this.paused = paused;
      this.refresh();
   }

   public void tick() {
      if (this.fadeTicks > 0) {
         this.fadeTicks--;
         float progress = 1.0F - (float)this.fadeTicks / this.fadeTotal;
         this.gain = this.fadeFrom + (this.fadeTo - this.fadeFrom) * progress;
         this.refresh();
      }
   }

   private void refresh() {
      this.volume = this.paused ? 0.0F : this.gain;
   }

   static final class RangedSound extends Sound {
      private final float range;

      RangedSound(Identifier location, boolean stream, float range) {
         super(location, ConstantFloat.of(1.0F), ConstantFloat.of(1.0F), 1, Type.FILE, stream, false, Math.max(1, Math.round(range)));
         this.range = range;
      }

      public float getAttenuationDistance(float volume) {
         return this.range;
      }
   }
}
