package net.bedrocknative.client.sound;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.concurrent.CompletableFuture;
import java.util.random.RandomGenerator;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.content.BaseDownloads;
import net.bedrocknative.content.BedrockInstall;
import net.bedrocknative.content.FolderPack;
import net.bedrocknative.content.LoadQueue;
import net.bedrocknative.content.PackSource;
import net.bedrocknative.content.PackStack;
import net.bedrocknative.sound.SoundCatalog;
import net.bedrocknative.sound.SoundPlanner;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;
import org.cloudburstmc.protocol.bedrock.data.sound.FadeSoundData;
import org.cloudburstmc.protocol.bedrock.data.sound.PauseSoundData;
import org.cloudburstmc.protocol.bedrock.data.sound.ResumeSoundData;
import org.cloudburstmc.protocol.bedrock.data.sound.SeekToSoundData;
import org.cloudburstmc.protocol.bedrock.data.sound.SetPitchSoundData;
import org.cloudburstmc.protocol.bedrock.data.sound.SetVolumeSoundData;
import org.cloudburstmc.protocol.bedrock.data.sound.SoundData;
import org.cloudburstmc.protocol.bedrock.data.sound.StopSoundData;
import org.cloudburstmc.protocol.bedrock.packet.ClientboundUpdateSoundDataPacket;
import org.jspecify.annotations.Nullable;

public final class BedrockSoundEngine {
   private static final boolean VANILLA = !Boolean.getBoolean("bedrocknative.javaSounds");
   private static final int PRUNE_AT = 64;
   private static final int WAITING_KEPT = 32;
   private static volatile @Nullable SoundCatalog catalog;
   private static final LoadQueue WAITING = new LoadQueue(32);
   private static final List<PackSource> EXTRA = BedrockInstall.musicPack().map(path -> List.<PackSource>of(new FolderPack(path))).orElse(List.of());
   private static final List<BedrockSoundEngine.Playing> PLAYING = new ArrayList<>();
   private static final Long2ObjectOpenHashMap<BedrockSoundEngine.Playing> HANDLES = new Long2ObjectOpenHashMap();
   private static final SplittableRandom RANDOM = new SplittableRandom();
   private static final RandomSource JAVA_RANDOM = RandomSource.create();
   private static int generation;
   private static boolean active;
   private static int played;
   private static int outOfRange;
   private static int notPlayable;
   private static final ArrayDeque<String> RECENT = new ArrayDeque<>();
   private static final int RECENT_KEPT = 64;

   private BedrockSoundEngine() {
   }

   public static boolean vanilla() {
      return VANILLA;
   }

   public static @Nullable SoundCatalog catalog() {
      return catalog;
   }

   public static RandomGenerator random() {
      return RANDOM;
   }

   public static void reload() {
      active = true;
      int current = ++generation;
      List<PackSource> packs = List.copyOf(PackStack.active().packs());
      int serverPacks = Math.min(PackStack.serverPackCount(), packs.size());
      long started = Util.getMillis();
      WAITING.begin();
      CompletableFuture.<SoundCatalog>supplyAsync(() -> SoundCatalog.load(packs, serverPacks), Util.backgroundExecutor())
         .whenCompleteAsync(
            (loaded, error) -> {
               if (current == generation) {
                  if (error != null) {
                     BedrockNative.LOGGER.warn("Couldn't load the Bedrock sounds of the pack stack", error);
                     WAITING.abandon();
                  } else {
                     catalog = loaded;
                     BedrockNative.LOGGER
                        .info(
                           "Bedrock sounds ready after {} ms: {} definitions, {} sounds.json entries, {} block sound types, {} music entries ({} packs, {} from the server{}; vanilla sounds {})",
                           new Object[]{
                              Util.getMillis() - started,
                              loaded.definitions().size(),
                              loaded.events().size(),
                              loaded.blockSoundCount(),
                              loaded.music().size(),
                              packs.size(),
                              serverPacks,
                              EXTRA.isEmpty() ? ", no music pack" : "",
                              VANILLA ? "Bedrock's" : "Java's"
                           }
                        );
                     List<String> silent = loaded.definitions().silentServerDefinitions();
                     BedrockNative.LOGGER
                        .info(
                           "Bedrock server sound definitions without a file: {}{}",
                           silent.size(),
                           silent.isEmpty() ? "" : " " + silent.subList(0, Math.min(20, silent.size()))
                        );
                     BaseDownloads.catalogLoaded(loaded, EXTRA);
                     WAITING.finish(e -> BedrockNative.LOGGER.warn("A server sound that waited for the catalog failed", e));
                  }
               }
            },
            Minecraft.getInstance()
         );
   }

   public static boolean whenLoaded(String key, Runnable task) {
      return catalog == null && WAITING.offer(key.toLowerCase(Locale.ROOT), task);
   }

   public static boolean hasWaiting() {
      return catalog == null && !WAITING.isEmpty();
   }

   public static void unqueue(String name) {
      WAITING.remove(name.toLowerCase(Locale.ROOT));
   }

   public static void unqueueAll() {
      WAITING.clear();
   }

   public static void packsChanged() {
      if (active) {
         stopAll();
         BedrockAudio.release();
         reload();
      }
   }

   public static void release() {
      stopAll();
      WAITING.abandon();
      JavaSoundBridge.release();
      active = false;
      generation++;
      catalog = null;
      BedrockAudio.release();
   }

   public static BedrockSoundEngine.Result play(SoundPlanner.Play play, Vec3 position, BedrockSoundEngine.Options options) {
      return start(play, position, options).result();
   }

   public static BedrockSoundEngine.Started start(SoundPlanner.Play play, Vec3 position, BedrockSoundEngine.Options options) {
      SoundCatalog current = catalog;
      Minecraft minecraft = Minecraft.getInstance();
      if (current == null) {
         notPlayable++;
         return BedrockSoundEngine.Started.NOT_PLAYABLE;
      }

      boolean positional = play.positional() && !options.global();
      if (positional && !options.bypassRange() && play.range() > 0.0F) {
         Vec3 listener = minecraft.getSoundManager().getListenerTransform().position();
         if (listener.distanceToSqr(position) > (double)play.range() * play.range()) {
            outOfRange++;
            return BedrockSoundEngine.Started.OUT_OF_RANGE;
         }
      }

      Optional<BedrockAudio.Located> file = BedrockAudio.locate(current, play.variant().path(), EXTRA);
      if (file.isEmpty()) {
         notPlayable++;
         return BedrockSoundEngine.Started.NOT_PLAYABLE;
      }

      boolean stream = play.stream() || options.offset() > 0.0F;
      Identifier location = BedrockAudio.location(file.get(), positional, options.offset());
      BedrockSoundInstance instance = new BedrockSoundInstance(
         play.definition().name(),
         location,
         stream,
         source(play.category()),
         position,
         positional,
         play.gain(),
         play.pitch(),
         play.range(),
         play.start(),
         options.loopCount(),
         null,
         JAVA_RANDOM
      );
      minecraft.getSoundManager().play(instance);
      played++;
      if (RECENT.size() >= 64) {
         RECENT.pollFirst();
      }

      RECENT.addLast(play.definition().name());
      BedrockSoundEngine.Playing entry = new BedrockSoundEngine.Playing(instance, play, position, options);
      if (PLAYING.size() >= 64) {
         PLAYING.removeIf(playing -> playing.instance().isStopped() || !minecraft.getSoundManager().isActive(playing.instance()));
         HANDLES.values().removeIf(handled -> !PLAYING.contains(handled));
      }

      PLAYING.add(entry);
      if (options.handle() != null) {
         BedrockSoundEngine.Playing previous = (BedrockSoundEngine.Playing)HANDLES.put(options.handle(), entry);
         if (previous != null && previous != entry) {
            stop(previous);
         }
      }

      return new BedrockSoundEngine.Started(BedrockSoundEngine.Result.PLAYED, instance);
   }

   public static SoundSource source(String category) {
      for (SoundSource source : SoundSource.values()) {
         if (source.getName().equals(category)) {
            return source;
         }
      }

      return "bottle".equals(category) ? SoundSource.PLAYERS : SoundSource.NEUTRAL;
   }

   public static void stop(String name) {
      Iterator<BedrockSoundEngine.Playing> iterator = PLAYING.iterator();

      while (iterator.hasNext()) {
         BedrockSoundEngine.Playing playing = iterator.next();
         if (playing.instance().name().equalsIgnoreCase(name)) {
            stop(playing);
            iterator.remove();
         }
      }
   }

   public static void stopAll() {
      List<BedrockSoundEngine.Playing> all = List.copyOf(PLAYING);
      PLAYING.clear();
      HANDLES.clear();
      all.forEach(BedrockSoundEngine::stop);
   }

   public static void stopMusic() {
      Iterator<BedrockSoundEngine.Playing> iterator = PLAYING.iterator();

      while (iterator.hasNext()) {
         BedrockSoundEngine.Playing playing = iterator.next();
         if (playing.instance().getSource() == SoundSource.MUSIC) {
            stop(playing);
            iterator.remove();
         }
      }
   }

   static void stop(BedrockSoundInstance instance) {
      PLAYING.removeIf(playing -> {
         if (playing.instance() != instance) {
            return false;
         }

         forgetHandle(playing);
         return true;
      });
      instance.stopNow();
      Minecraft.getInstance().getSoundManager().stop(instance);
   }

   private static void stop(BedrockSoundEngine.Playing playing) {
      forgetHandle(playing);
      playing.instance().stopNow();
      Minecraft.getInstance().getSoundManager().stop(playing.instance());
   }

   private static void forgetHandle(BedrockSoundEngine.Playing playing) {
      Long handle = playing.options().handle();
      if (handle != null && HANDLES.get(handle) == playing) {
         HANDLES.remove(handle);
      }
   }

   public static void stopHandle(long handle) {
      BedrockSoundEngine.Playing playing = (BedrockSoundEngine.Playing)HANDLES.remove(handle);
      if (playing != null) {
         PLAYING.remove(playing);
         stop(playing);
      }
   }

   public static void update(ClientboundUpdateSoundDataPacket packet) {
      BedrockSoundEngine.Playing playing = (BedrockSoundEngine.Playing)HANDLES.get(packet.getServerSoundHandle());
      if (playing != null) {
         BedrockSoundInstance instance = playing.instance();
         SoundData event = packet.getEvent();
         if (!(event instanceof StopSoundData) && packet.getStop() == null) {
            if (!(event instanceof SeekToSoundData seek) && packet.getSeekTo() == null) {
               if (event instanceof SetVolumeSoundData volume) {
                  instance.setGain(volume.getVolume());
               } else if (packet.getVolume() != null) {
                  instance.setGain(packet.getVolume().getVolume());
               }

               if (event instanceof SetPitchSoundData pitch) {
                  instance.setPitch(pitch.getPitch());
               } else if (packet.getPitch() != null) {
                  instance.setPitch(packet.getPitch().getPitch());
               }

               if (event instanceof FadeSoundData fade) {
                  instance.fade(fade.getTargetVolume(), fade.getDuration());
               } else if (packet.getFade() != null) {
                  instance.fade(packet.getFade().getTargetVolume(), packet.getFade().getDuration());
               }

               if (event instanceof PauseSoundData || packet.getPause() != null) {
                  instance.setPaused(true);
               }

               if (event instanceof ResumeSoundData || packet.getResume() != null) {
                  instance.setPaused(false);
               }
            } else {
               float seconds = event instanceof SeekToSoundData seekTo ? seekTo.getSeconds() : packet.getSeekTo().getSeconds();
               stop(playing);
               PLAYING.remove(playing);
               HANDLES.remove(packet.getServerSoundHandle());
               BedrockSoundEngine.Options options = playing.options();
               play(
                  playing.play(),
                  playing.position(),
                  new BedrockSoundEngine.Options(options.handle(), options.loopCount(), Math.max(0.0F, seconds), options.bypassRange(), options.global())
               );
            }
         } else {
            HANDLES.remove(packet.getServerSoundHandle());
            PLAYING.remove(playing);
            stop(playing);
         }
      }
   }

   public static int playedCount() {
      return played;
   }

   public static List<String> recent() {
      return List.copyOf(RECENT);
   }

   public static int playing() {
      Minecraft minecraft = Minecraft.getInstance();
      PLAYING.removeIf(entry -> entry.instance().isStopped() || !minecraft.getSoundManager().isActive(entry.instance()));
      return PLAYING.size();
   }

   public static List<String> playingNames() {
      playing();
      return PLAYING.stream().map(entry -> entry.play().definition().name()).toList();
   }

   public static int outOfRangeCount() {
      return outOfRange;
   }

   public static int notPlayableCount() {
      return notPlayable;
   }

   public record Options(@Nullable Long handle, int loopCount, float offset, boolean bypassRange, boolean global) {
      public static final BedrockSoundEngine.Options DEFAULT = new BedrockSoundEngine.Options(null, 0, 0.0F, false, false);
   }

   private record Playing(BedrockSoundInstance instance, SoundPlanner.Play play, Vec3 position, BedrockSoundEngine.Options options) {
   }

   public enum Result {
      PLAYED,
      OUT_OF_RANGE,
      NOT_PLAYABLE;
   }

   public record Started(BedrockSoundEngine.Result result, @Nullable BedrockSoundInstance instance) {
      static final BedrockSoundEngine.Started OUT_OF_RANGE = new BedrockSoundEngine.Started(BedrockSoundEngine.Result.OUT_OF_RANGE, null);
      static final BedrockSoundEngine.Started NOT_PLAYABLE = new BedrockSoundEngine.Started(BedrockSoundEngine.Result.NOT_PLAYABLE, null);
   }
}
