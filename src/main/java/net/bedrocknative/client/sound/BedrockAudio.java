package net.bedrocknative.client.sound;

import com.mojang.blaze3d.audio.SoundBuffer;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import javax.sound.sampled.AudioFormat;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.content.PackSource;
import net.bedrocknative.sound.Fsb5;
import net.bedrocknative.sound.PcmAudio;
import net.bedrocknative.sound.PcmCollector;
import net.bedrocknative.sound.PcmStream;
import net.bedrocknative.sound.SoundCatalog;
import net.bedrocknative.sound.WavAudio;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.JOrbisAudioStream;
import net.minecraft.client.sounds.LoopingAudioStream;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;
import org.lwjgl.BufferUtils;

public final class BedrockAudio {
   static final String NAMESPACE = "bedrocknative";
   private static final String LOCATION_PREFIX = "bedrock/";
   private static final String FILE_PREFIX = "sounds/bedrock/";
   private static final AtomicInteger NEXT = new AtomicInteger();
   private static final Map<BedrockAudio.Key, Identifier> LOCATIONS = new ConcurrentHashMap<>();
   private static final Map<String, BedrockAudio.Source> SOURCES = new ConcurrentHashMap<>();
   private static final Map<BedrockAudio.FileKey, Optional<BedrockAudio.Located>> FILES = new ConcurrentHashMap<>();
   private static final Set<BedrockAudio.Located> UNPLAYABLE = ConcurrentHashMap.newKeySet();
   private static final Set<String> LOGGED = ConcurrentHashMap.newKeySet();
   private static final Set<BedrockAudio.Kind> FIRST_DECODES = ConcurrentHashMap.newKeySet();
   private static volatile BedrockAudio.@Nullable BufferCache cache;
   private static final List<BedrockAudio.Discard> DISCARDS = new ArrayList<>();
   private static final long DISCARD_DELAY_MS = 2000L;

   private BedrockAudio() {
   }

   static Optional<BedrockAudio.Located> locate(SoundCatalog catalog, String variantPath, List<? extends PackSource> extra) {
      Optional<BedrockAudio.Located> located = FILES.computeIfAbsent(new BedrockAudio.FileKey(catalog, variantPath), key -> probe(catalog, variantPath, extra));
      return located.isPresent() && UNPLAYABLE.contains(located.get()) ? Optional.empty() : located;
   }

   private static Optional<BedrockAudio.Located> probe(SoundCatalog catalog, String variantPath, List<? extends PackSource> extra) {
      SoundCatalog.SoundFile file = catalog.foundFile(variantPath, extra);
      if (file == null) {
         return Optional.empty();
      }

      BedrockAudio.Kind kind = kind(file.path(), file.data());
      if (kind == null) {
         if (LOGGED.add(file.path())) {
            BedrockNative.LOGGER.info("Bedrock sound {} in {} not playable (unknown audio format); the Java sound stands in", file.path(), file.pack());
         }

         return Optional.empty();
      } else {
         return Optional.of(new BedrockAudio.Located(file.pack(), file.path(), kind, file.serverPack()));
      }
   }

   static BedrockAudio.@Nullable Kind kind(String path, byte[] data) {
      if (Fsb5.isFsb5(data)) {
         return BedrockAudio.Kind.FSB;
      }

      if (WavAudio.isWav(data)) {
         return BedrockAudio.Kind.WAV;
      }

      if (data.length >= 4 && data[0] == 79 && data[1] == 103 && data[2] == 103 && data[3] == 83) {
         return BedrockAudio.Kind.OGG;
      }

      if (data.length > 0) {
         return null;
      }

      String lower = path.toLowerCase(Locale.ROOT);
      return lower.endsWith(".fsb")
         ? BedrockAudio.Kind.FSB
         : (lower.endsWith(".ogg") ? BedrockAudio.Kind.OGG : (lower.endsWith(".wav") ? BedrockAudio.Kind.WAV : null));
   }

   private static BedrockAudio.Kind checked(BedrockAudio.Located file, byte[] data) throws IOException {
      BedrockAudio.Kind kind = kind(file.path(), data);
      String problem = kind == null ? "unknown audio format" : (kind == BedrockAudio.Kind.FSB ? fsbProblem(data) : null);
      if (problem != null) {
         if (LOGGED.add(file.path())) {
            BedrockNative.LOGGER.info("Bedrock sound {} in {} not playable ({}); the Java sound stands in", new Object[]{file.path(), file.pack(), problem});
         }

         throw new IOException(problem);
      } else {
         return kind;
      }
   }

   private static @Nullable String fsbProblem(byte[] data) {
      Fsb5 bank = Fsb5.parse(data);
      if (bank == null) {
         return "broken FMOD bank";
      } else {
         return bank.decodable() ? null : "FMOD codec " + bank.codec();
      }
   }

   static Identifier location(BedrockAudio.Located file, boolean mono, float offset) {
      return LOCATIONS.computeIfAbsent(new BedrockAudio.Key(file, mono, offset), key -> {
         Identifier location = Identifier.fromNamespaceAndPath("bedrocknative", "bedrock/" + NEXT.incrementAndGet());
         SOURCES.put("sounds/bedrock/" + location.getPath().substring("bedrock/".length()) + ".ogg", new BedrockAudio.Source(file, mono, offset));
         return location;
      });
   }

   public static boolean owns(Identifier path) {
      return "bedrocknative".equals(path.getNamespace()) && path.getPath().startsWith("sounds/bedrock/");
   }

   public static void attach(BedrockAudio.BufferCache library) {
      cache = library;
   }

   public static CompletableFuture<SoundBuffer> buffer(Identifier path) {
      BedrockAudio.Source source = SOURCES.get(path.getPath());
      return CompletableFuture.supplyAsync(() -> {
         if (source == null) {
            throw new CompletionException(new IOException("No Bedrock sound for " + path));
         }

         try {
            return decode(source);
         } catch (IOException | RuntimeException e) {
            throw unplayable(source.file(), e);
         }
      }, Util.nonCriticalIoPool());
   }

   public static CompletableFuture<AudioStream> stream(Identifier path, boolean looping) {
      BedrockAudio.Source source = SOURCES.get(path.getPath());
      return CompletableFuture.supplyAsync(() -> {
         if (source == null) {
            throw new CompletionException(new IOException("No Bedrock sound for " + path));
         }

         try {
            return open(source, looping);
         } catch (IOException | RuntimeException e) {
            throw unplayable(source.file(), e);
         }
      }, Util.nonCriticalIoPool());
   }

   private static CompletionException unplayable(BedrockAudio.Located file, Exception e) {
      UNPLAYABLE.add(file);
      return new CompletionException(new IOException("Bedrock sound " + file.path() + ": " + e.getMessage(), e));
   }

   private static byte[] read(BedrockAudio.Located file) throws IOException {
      Optional<byte[]> data = file.pack().read(file.path());
      if (data.isEmpty()) {
         throw new IOException("gone from " + file.pack());
      } else {
         return data.get();
      }
   }

   private static SoundBuffer decode(BedrockAudio.Source source) throws IOException {
      long started = System.nanoTime();
      byte[] data = read(source.file());
      BedrockAudio.Kind kind = checked(source.file(), data);

      PcmAudio audio = switch (kind) {
         case FSB -> {
            Fsb5 bank = Fsb5.parse(data);
            PcmAudio decoded = bank != null ? bank.decode() : null;
            if (decoded == null) {
               throw new IOException("undecodable FMOD bank");
            }

            yield decoded;
         }
         case WAV -> {
            PcmAudio decoded = WavAudio.decode(data);
            if (decoded == null) {
               throw new IOException("unsupported WAV");
            }

            yield decoded;
         }
         case OGG -> ogg(data);
      };
      if (FIRST_DECODES.add(kind)) {
         BedrockNative.LOGGER
            .info(
               "Bedrock audio: first {} file decoded ({} from {}): {} Hz, {} channels, {} s, in {} ms",
               new Object[]{
                  kind,
                  source.file().path(),
                  source.file().pack(),
                  audio.sampleRate(),
                  audio.channels(),
                  Math.round(audio.seconds() * 100.0F) / 100.0F,
                  (System.nanoTime() - started) / 1000000L
               }
            );
      }

      if (audio.samples().length * 2L > 67108864L) {
         throw new IOException("audio longer than 64 MiB of PCM");
      }

      audio = source.mono() ? audio.mono() : audio.atMostStereo();
      if (source.offset() > 0.0F) {
         audio = audio.from(source.offset());
      }

      return new SoundBuffer(pcmBuffer(audio.samples(), audio.samples().length), format(audio.sampleRate(), audio.channels()));
   }

   private static PcmAudio ogg(byte[] data) throws IOException {
      try (JOrbisAudioStream stream = new JOrbisAudioStream(new ByteArrayInputStream(data))) {
         AudioFormat format = stream.getFormat();
         short[] samples = PcmCollector.collect(stream::read);
         return new PcmAudio((int)format.getSampleRate(), format.getChannels(), samples);
      }
   }

   private static AudioStream open(BedrockAudio.Source source, boolean looping) throws IOException {
      byte[] data = read(source.file());

      return switch (checked(source.file(), data)) {
         case FSB -> {
            Fsb5 bank = Fsb5.parse(data);
            if (bank == null || bank.stream(0) == null) {
               throw new IOException("undecodable FMOD bank");
            }

            yield new BedrockAudio.PcmAudioStream(() -> bank.stream(0), source.mono(), looping, source.offset());
         }
         case WAV -> {
            PcmAudio audio = WavAudio.decode(data);
            if (audio == null) {
               throw new IOException("unsupported WAV");
            }

            yield new BedrockAudio.PcmAudioStream(() -> PcmStream.of(audio), source.mono(), looping, source.offset());
         }
         case OGG -> {
            AudioStream ogg = looping
               ? new LoopingAudioStream(JOrbisAudioStream::new, new ByteArrayInputStream(data))
               : new JOrbisAudioStream(new ByteArrayInputStream(data));
            AudioStream mixed = source.mono() && ogg.getFormat().getChannels() > 1 ? new BedrockAudio.MonoAudioStream(ogg) : ogg;
            skip(mixed, source.offset());
            yield mixed;
         }
      };
   }

   private static void skip(AudioStream stream, float seconds) throws IOException {
      AudioFormat format = stream.getFormat();
      long bytes = (long)(seconds * format.getSampleRate()) * format.getChannels() * 2L;

      while (bytes > 0L) {
         ByteBuffer skipped = stream.read((int)Math.min(bytes, 65536L));
         if (!skipped.hasRemaining()) {
            return;
         }

         bytes -= skipped.remaining();
      }
   }

   static AudioFormat format(int sampleRate, int channels) {
      return new AudioFormat(sampleRate, 16, channels, true, ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN);
   }

   static ByteBuffer pcmBuffer(short[] samples, int count) {
      ByteBuffer buffer = BufferUtils.createByteBuffer(count * 2);
      buffer.asShortBuffer().put(samples, 0, count);
      return buffer;
   }

   public static void forgetMissing() {
      FILES.values().removeIf(Optional::isEmpty);
   }

   public static void release() {
      FILES.clear();
      UNPLAYABLE.clear();
      BedrockAudio.BufferCache library = cache;
      if (library != null) {
         long after = Util.getMillis() + 2000L;
         library.bedrocknative$forget(BedrockAudio::owns)
            .forEach(buffer -> DISCARDS.add(new BedrockAudio.Discard((CompletableFuture<SoundBuffer>)buffer, after)));
      }

      SOURCES.clear();
      LOCATIONS.clear();
   }

   public static void discardPendingNow() {
      DISCARDS.forEach(discard -> discard.buffer().thenAccept(SoundBuffer::discardAlBuffer));
      DISCARDS.clear();
   }

   public static void tick() {
      if (!DISCARDS.isEmpty()) {
         long now = Util.getMillis();
         DISCARDS.removeIf(discard -> {
            if (discard.after() > now) {
               return false;
            }

            discard.buffer().thenAccept(SoundBuffer::discardAlBuffer);
            return true;
         });
      }
   }

   public interface BufferCache {
      List<CompletableFuture<SoundBuffer>> bedrocknative$forget(Predicate<Identifier> var1);
   }

   private record Discard(CompletableFuture<SoundBuffer> buffer, long after) {
   }

   private record FileKey(SoundCatalog catalog, String path) {
   }

   private record Key(BedrockAudio.Located file, boolean mono, float offset) {
   }

   enum Kind {
      FSB,
      WAV,
      OGG;
   }

   record Located(PackSource pack, String path, BedrockAudio.Kind kind, boolean serverPack) {
   }

   private static final class MonoAudioStream implements AudioStream {
      private final AudioStream stereo;

      MonoAudioStream(AudioStream stereo) {
         this.stereo = stereo;
      }

      @Override
      public AudioFormat getFormat() {
         return BedrockAudio.format((int)this.stereo.getFormat().getSampleRate(), 1);
      }

      @Override
      public ByteBuffer read(int expectedSize) throws IOException {
         int channels = this.stereo.getFormat().getChannels();
         ShortBuffer in = this.stereo.read(expectedSize * channels).order(ByteOrder.nativeOrder()).asShortBuffer();
         int frames = in.remaining() / channels;
         short[] out = new short[frames];

         for (int frame = 0; frame < frames; frame++) {
            int sum = 0;

            for (int channel = 0; channel < channels; channel++) {
               sum += in.get(frame * channels + channel);
            }

            out[frame] = (short)(sum / channels);
         }

         return BedrockAudio.pcmBuffer(out, out.length);
      }

      @Override
      public void close() throws IOException {
         this.stereo.close();
      }
   }

   private static final class PcmAudioStream implements AudioStream {
      private final BedrockAudio.PcmAudioStream.StreamFactory factory;
      private final boolean mono;
      private final boolean looping;
      private PcmStream stream;
      private short[] scratch = new short[0];

      PcmAudioStream(BedrockAudio.PcmAudioStream.StreamFactory factory, boolean mono, boolean looping, float offset) throws IOException {
         this.factory = factory;
         this.looping = looping;
         PcmStream opened = factory.open();
         if (opened == null) {
            throw new IOException("no audio stream");
         }

         this.stream = opened;
         this.mono = mono && opened.channels() > 1;
         if (offset > 0.0F) {
            opened.skip(offset);
         }
      }

      @Override
      public AudioFormat getFormat() {
         return BedrockAudio.format(this.stream.sampleRate(), this.mono ? 1 : Math.min(2, this.stream.channels()));
      }

      @Override
      public ByteBuffer read(int expectedSize) {
         int channels = this.stream.channels();
         int outChannels = this.mono ? 1 : Math.min(2, channels);
         int frames = Math.max(1, expectedSize / 2 / outChannels);
         if (this.scratch.length != frames * channels) {
            this.scratch = new short[frames * channels];
         }

         int read = this.stream.read(this.scratch);
         if (read == 0 && this.looping) {
            PcmStream again = this.factory.open();
            if (again != null && again.channels() == channels) {
               this.stream = again;
               read = this.stream.read(this.scratch);
            }
         }

         int got = read / channels;
         short[] out = new short[got * outChannels];

         for (int frame = 0; frame < got; frame++) {
            if (!this.mono) {
               for (int channel = 0; channel < outChannels; channel++) {
                  out[frame * outChannels + channel] = this.scratch[frame * channels + channel];
               }
            } else {
               int sum = 0;

               for (int channel = 0; channel < channels; channel++) {
                  sum += this.scratch[frame * channels + channel];
               }

               out[frame] = (short)(sum / channels);
            }
         }

         return BedrockAudio.pcmBuffer(out, out.length);
      }

      @Override
      public void close() {
      }

      @FunctionalInterface
      interface StreamFactory {
         @Nullable PcmStream open();
      }
   }

   private record Source(BedrockAudio.Located file, boolean mono, float offset) {
   }
}
