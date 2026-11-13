package net.bedrocknative.client.sound;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.sound.SoundCatalog;
import net.bedrocknative.sound.SoundPlanner;
import net.minecraft.client.Minecraft;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.protocol.bedrock.data.LevelEvent;
import org.cloudburstmc.protocol.bedrock.packet.LevelEventGenericPacket;
import org.jspecify.annotations.Nullable;

public final class BedrockMusic {
   static final float MAX_FADE_SECONDS = 10.0F;
   private static final int STARTUP_TICKS = 40;
   private static final int MAX_QUEUED = 64;
   private final Minecraft minecraft;
   private @Nullable BedrockSoundInstance current;
   private BedrockMusic.@Nullable Track currentTrack;
   private int startedTicks;
   private final Deque<BedrockMusic.Track> queue = new ArrayDeque<>();
   private final List<BedrockMusic.Fading> fading = new ArrayList<>();
   private int played;

   public BedrockMusic(Minecraft minecraft) {
      this.minecraft = minecraft;
   }

   public static boolean handles(LevelEventGenericPacket packet) {
      return packet.getType() == LevelEvent.PLAY_CUSTOM_MUSIC
         || packet.getType() == LevelEvent.QUEUE_CUSTOM_MUSIC
         || packet.getType() == LevelEvent.STOP_CUSTOM_MUSIC
         || packet.getType() == LevelEvent.SET_MUSIC_VOLUME;
   }

   static BedrockMusic.@Nullable Track parse(NbtMap tag) {
      String name = tag.getString("trackName", "");
      return name.isEmpty()
         ? null
         : new BedrockMusic.Track(
            name, clamp(tag.getFloat("volume", 1.0F), 0.0F, 1.0F), clamp(tag.getFloat("fadeSeconds", 0.0F), 0.0F, 10.0F), tag.getBoolean("repeatMode", false)
         );
   }

   private static float clamp(float value, float min, float max) {
      return Float.isNaN(value) ? min : Math.max(min, Math.min(max, value));
   }

   public void onGeneric(LevelEventGenericPacket packet) {
      NbtMap tag = packet.getTag() instanceof NbtMap map ? map : NbtMap.EMPTY;
      if (packet.getType() == LevelEvent.PLAY_CUSTOM_MUSIC) {
         BedrockMusic.Track track = parse(tag);
         if (track != null) {
            this.queue.clear();
            this.play(track);
         }
      } else if (packet.getType() == LevelEvent.QUEUE_CUSTOM_MUSIC) {
         BedrockMusic.Track track = parse(tag);
         if (track != null && this.queue.size() < 64) {
            if (this.current == null) {
               this.play(track);
            } else {
               this.queue.add(track);
            }
         }
      } else if (packet.getType() == LevelEvent.STOP_CUSTOM_MUSIC) {
         this.queue.clear();
         this.endCurrent(clamp(tag.getFloat("fadeSeconds", 0.0F), 0.0F, 10.0F));
      } else if (packet.getType() == LevelEvent.SET_MUSIC_VOLUME && this.current != null) {
         this.current.setGain(clamp(tag.getFloat("volume", 1.0F), 0.0F, 1.0F));
      }
   }

   private void play(BedrockMusic.Track track) {
      SoundCatalog catalog = BedrockSoundEngine.catalog();
      if (catalog == null) {
         BedrockNative.LOGGER.info("Music {} not played: the sound catalog is not loaded", track.name());
      } else {
         SoundPlanner.Play play = SoundPlanner.definition(catalog.definitions(), track.name(), track.volume(), 1.0F, true, BedrockSoundEngine.random());
         if (play == null) {
            BedrockNative.LOGGER.info("Music {} not played: no sound definition of that name", track.name());
         } else {
            BedrockSoundEngine.Started started = BedrockSoundEngine.start(
               play, this.minecraft.gameRenderer.mainCamera().position(), new BedrockSoundEngine.Options(null, track.loop() ? -1 : 0, 0.0F, true, true)
            );
            if (started.instance() == null) {
               BedrockNative.LOGGER.info("Music {} not played: {}", track.name(), started.result());
            } else {
               this.endCurrent(track.fadeSeconds());
               this.current = started.instance();
               this.currentTrack = track;
               this.startedTicks = 0;
               this.played++;
               if (track.fadeSeconds() > 0.0F) {
                  float target = play.gain();
                  this.current.setGain(0.0F);
                  this.current.fade(target, track.fadeSeconds());
               }
            }
         }
      }
   }

   private void endCurrent(float fadeSeconds) {
      BedrockSoundInstance instance = this.current;
      this.current = null;
      this.currentTrack = null;
      if (instance != null) {
         if (fadeSeconds > 0.0F) {
            instance.fade(0.0F, fadeSeconds);
            this.fading.add(new BedrockMusic.Fading(instance, Math.max(1, Math.round(fadeSeconds * 20.0F)) + 2));
         } else {
            BedrockSoundEngine.stop(instance);
         }
      }
   }

   public void tick() {
      Iterator<BedrockMusic.Fading> iterator = this.fading.iterator();

      while (iterator.hasNext()) {
         BedrockMusic.Fading entry = iterator.next();
         if (--entry.ticks <= 0) {
            BedrockSoundEngine.stop(entry.instance);
            iterator.remove();
         }
      }

      if (this.current != null && ++this.startedTicks > 40 && (this.current.isStopped() || !this.minecraft.getSoundManager().isActive(this.current))) {
         this.current = null;
         this.currentTrack = null;
         BedrockMusic.Track next = this.queue.poll();
         if (next != null) {
            this.play(next);
         }
      }
   }

   public @Nullable String playing() {
      return this.currentTrack != null ? this.currentTrack.name() : null;
   }

   public int queued() {
      return this.queue.size();
   }

   public int playedCount() {
      return this.played;
   }

   public void clear() {
      this.queue.clear();
      this.endCurrent(0.0F);

      for (BedrockMusic.Fading entry : this.fading) {
         BedrockSoundEngine.stop(entry.instance);
      }

      this.fading.clear();
   }

   private static final class Fading {
      final BedrockSoundInstance instance;
      int ticks;

      Fading(BedrockSoundInstance instance, int ticks) {
         this.instance = instance;
         this.ticks = ticks;
      }
   }

   record Track(String name, float volume, float fadeSeconds, boolean loop) {
   }
}
