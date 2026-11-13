package net.bedrocknative.partner;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.oreui.FeaturedServers;
import org.jspecify.annotations.Nullable;

public final class PartnerServers {
   static final Duration MIN_INTERVAL = Duration.ofMinutes(10L);
   static final Duration RETRY_INTERVAL = Duration.ofMinutes(1L);
   private final Path folder;
   private final PartnerGuard guard;
   private final PartnerServers.Fetch fetch;
   private final Supplier<Instant> clock;
   private final Object refreshLock = new Object();
   private volatile PartnerServers.@Nullable Shown shown;
   private volatile @Nullable Instant nextFetch;
   private volatile int generation;
   private volatile @Nullable String warnedUrl;

   public PartnerServers(Path folder, PartnerGuard guard, PartnerServers.Fetch fetch, Supplier<Instant> clock) {
      this.folder = folder;
      this.guard = guard;
      this.fetch = fetch;
      this.clock = clock;
   }

   public static PartnerServers create(Path folder) {
      PartnerGuard guard = PartnerGuard.forThisRun();
      return new PartnerServers(folder, guard, new PartnerFetcher(guard), Instant::now);
   }

   public Path folder() {
      return this.folder;
   }

   public PartnerServers.Snapshot state() {
      PartnerConfig config = PartnerConfig.load(this.folder);
      URI uri = config.uri(this.guard);
      if (uri == null) {
         this.warnBadUrl(config);
         return PartnerServers.Snapshot.of(PartnerServers.Source.OFF, null);
      }

      String host = config.host(this.guard);
      PartnerConsent.Answer answer = PartnerConsent.read(this.folder, host);
      if (answer != PartnerConsent.Answer.ACCEPTED) {
         return PartnerServers.Snapshot.of(answer == PartnerConsent.Answer.REFUSED ? PartnerServers.Source.REFUSED : PartnerServers.Source.ASK, host);
      }

      PartnerServers.Shown current = this.shown;
      if (current != null && current.matches(uri, host)) {
         return current.snapshot();
      }

      int before = this.generation;
      PartnerServers.Snapshot cached = this.fromCache(uri, host, PartnerServers.Source.CACHED);
      synchronized (this) {
         PartnerServers.Shown now = this.shown;
         if (this.generation == before && (now == null || !now.matches(uri, host))) {
            this.shown = new PartnerServers.Shown(uri.toString(), host, cached, false);
         }

         return cached;
      }
   }

   public synchronized void answer(boolean accept) throws IOException {
      String host = PartnerConfig.load(this.folder).host(this.guard);
      if (host != null) {
         this.generation++;
         PartnerConsent.write(this.folder, accept, host, this.clock.get());
         if (!accept) {
            PartnerCache.clear(this.folder);
         }

         this.shown = null;
         this.nextFetch = null;
         BedrockNative.LOGGER.info("Partner servers: the player {} the list from {}", accept ? "accepted" : "refused", host);
      }
   }

   public CompletableFuture<PartnerServers.Snapshot> refreshAsync() {
      CompletableFuture<PartnerServers.Snapshot> result = new CompletableFuture<>();
      Thread.ofVirtual().name("Partner servers fetch").start(() -> {
         try {
            result.complete(this.refresh());
         } catch (Throwable e) {
            result.completeExceptionally(e);
         }
      });
      return result;
   }

   public PartnerServers.Snapshot refresh() {
      synchronized (this.refreshLock) {
         return this.refreshLocked();
      }
   }

   private PartnerServers.Snapshot refreshLocked() {
      PartnerConfig config = PartnerConfig.load(this.folder);
      URI uri = config.uri(this.guard);
      if (uri == null) {
         this.warnBadUrl(config);
         return PartnerServers.Snapshot.of(PartnerServers.Source.OFF, null);
      }

      String host = config.host(this.guard);
      PartnerConsent.Answer answer = PartnerConsent.read(this.folder, host);
      if (answer != PartnerConsent.Answer.ACCEPTED) {
         return PartnerServers.Snapshot.of(answer == PartnerConsent.Answer.REFUSED ? PartnerServers.Source.REFUSED : PartnerServers.Source.ASK, host);
      }

      Instant now = this.clock.get();
      PartnerServers.Shown current = this.shown;
      Instant due = this.nextFetch;
      if (current != null && current.fetched() && current.matches(uri, host) && due != null && now.isBefore(due)) {
         return current.snapshot();
      }

      int started = this.generation;

      try {
         byte[] body = this.fetch.get(uri, 262144, "application/json");
         String text = utf8(body);
         PartnerList.Result result = PartnerList.parse(text, now, uri, this.guard);
         PartnerCache.Cached previous = PartnerCache.load(this.folder, uri.toString(), now);
         Map<String, String> known = previous == null ? Map.of() : previous.pictures();
         Map<String, String> byUrl = new HashMap<>();
         Set<String> keep = new HashSet<>();
         List<String> problems = new ArrayList<>(result.problems());
         List<FeaturedServers.Server> servers = this.servers(result.entries(), known, byUrl, keep, true, problems);
         if (Thread.currentThread().isInterrupted()) {
            return this.fromCache(uri, host, PartnerServers.Source.CACHED);
         }

         this.carryOver(result.entries(), known, byUrl, keep);
         PartnerServers.Snapshot snapshot = new PartnerServers.Snapshot(PartnerServers.Source.FETCHED, host, servers, now, problems);
         synchronized (this) {
            if (this.generation != started) {
               return this.superseded(host);
            }

            PartnerCache.save(this.folder, uri.toString(), now, text, byUrl);
            PartnerCache.prunePictures(this.folder, keep);
            this.store(uri, host, snapshot, now.plus(MIN_INTERVAL));
         }

         this.log(snapshot);
         return snapshot;
      } catch (IOException e) {
         BedrockNative.LOGGER.info("Partner servers: the list from {} did not load ({}); using the last good copy if any", host, e.getMessage());
         Duration wait = RETRY_INTERVAL;
         PartnerServers.Snapshot snapshot = this.fromCache(uri, host, PartnerServers.Source.CACHED);
         synchronized (this) {
            if (this.generation != started) {
               return this.superseded(host);
            }

            this.store(uri, host, snapshot, now.plus(wait));
         }

         this.log(snapshot);
         return snapshot;
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
         return this.fromCache(uri, host, PartnerServers.Source.CACHED);
      }
   }

   private void store(URI uri, @Nullable String host, PartnerServers.Snapshot snapshot, Instant next) {
      this.shown = new PartnerServers.Shown(uri.toString(), host, snapshot, true);
      this.nextFetch = next;
   }

   private PartnerServers.Snapshot superseded(@Nullable String host) {
      PartnerConsent.Answer answer = PartnerConsent.read(this.folder, host);
      if (answer != PartnerConsent.Answer.ACCEPTED) {
         PartnerCache.clear(this.folder);
         return PartnerServers.Snapshot.of(answer == PartnerConsent.Answer.REFUSED ? PartnerServers.Source.REFUSED : PartnerServers.Source.ASK, host);
      } else {
         return PartnerServers.Snapshot.of(PartnerServers.Source.UNAVAILABLE, host);
      }
   }

   private void carryOver(List<PartnerList.Entry> entries, Map<String, String> known, Map<String, String> byUrl, Set<String> keep) {
      for (PartnerList.Entry entry : entries) {
         for (PartnerList.ImageRef ref : new PartnerList.ImageRef[]{entry.icon(), entry.banner()}) {
            if (ref != null && !ref.inline() && !byUrl.containsKey(ref.value())) {
               String file = known.get(ref.value());
               if (file != null
                  && (
                     PartnerCache.picture(this.folder, file, PartnerImage.Kind.ICON) != null
                        || PartnerCache.picture(this.folder, file, PartnerImage.Kind.BANNER) != null
                  )) {
                  byUrl.put(ref.value(), file);
                  keep.add(file);
               }
            }
         }
      }
   }

   private PartnerServers.Snapshot fromCache(URI uri, String host, PartnerServers.Source source) {
      Instant now = this.clock.get();
      PartnerCache.Cached cached = PartnerCache.load(this.folder, uri.toString(), now);
      if (cached == null) {
         return PartnerServers.Snapshot.of(PartnerServers.Source.UNAVAILABLE, host);
      }

      try {
         PartnerList.Result result = PartnerList.parse(cached.body(), now, uri, this.guard);
         List<String> problems = new ArrayList<>(result.problems());
         List<FeaturedServers.Server> servers = this.servers(result.entries(), cached.pictures(), new HashMap<>(), new HashSet<>(), false, problems);
         return new PartnerServers.Snapshot(source, host, servers, cached.fetched(), problems);
      } catch (IOException e) {
         return PartnerServers.Snapshot.of(PartnerServers.Source.UNAVAILABLE, host);
      }
   }

   private List<FeaturedServers.Server> servers(
      List<PartnerList.Entry> entries, Map<String, String> known, Map<String, String> byUrl, Set<String> keep, boolean online, List<String> problems
   ) {
      List<FeaturedServers.Server> servers = new ArrayList<>();

      for (PartnerList.Entry entry : entries) {
         Path icon = this.picture(entry.icon(), PartnerImage.Kind.ICON, known, byUrl, keep, online, problems, entry.name());
         Path banner = this.picture(entry.banner(), PartnerImage.Kind.BANNER, known, byUrl, keep, online, problems, entry.name());
         servers.add(new FeaturedServers.Server(entry.name(), entry.address(), entry.port(), entry.message(), entry.description(), icon, banner, true));
      }

      return servers;
   }

   private @Nullable Path picture(
      PartnerList.@Nullable ImageRef ref,
      PartnerImage.Kind kind,
      Map<String, String> known,
      Map<String, String> byUrl,
      Set<String> keep,
      boolean online,
      List<String> problems,
      String name
   ) {
      if (ref == null) {
         return null;
      }

      try {
         String file;
         if (ref.inline()) {
            file = PartnerCache.storePicture(this.folder, PartnerImage.decodeDataUri(ref.value(), kind));
         } else {
            file = known.get(ref.value());
            if (file == null || PartnerCache.picture(this.folder, file, kind) == null) {
               if (!online) {
                  return null;
               }

               byte[] bytes = this.fetch.get(URI.create(ref.value()), kind.maxUrlBytes, "image/png");
               PartnerImage.check(bytes, kind, kind.maxUrlBytes);
               file = PartnerCache.storePicture(this.folder, bytes);
            }

            byUrl.put(ref.value(), file);
         }

         keep.add(file);
         return PartnerCache.picture(this.folder, file, kind);
      } catch (IOException | IllegalArgumentException e) {
         problems.add(name + " " + kind.name().toLowerCase(Locale.ROOT) + " not shown: " + e.getMessage());
         return null;
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
         return null;
      }
   }

   private static String utf8(byte[] body) throws IOException {
      try {
         return StandardCharsets.UTF_8
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(body))
            .toString();
      } catch (CharacterCodingException e) {
         throw new IOException("the list is not UTF-8");
      }
   }

   private void log(PartnerServers.Snapshot snapshot) {
      BedrockNative.LOGGER
         .info(
            "Partner servers: {} sponsored servers from {} ({}{}), {} problems",
            new Object[]{
               snapshot.servers().size(),
               snapshot.host(),
               snapshot.source().name().toLowerCase(Locale.ROOT),
               snapshot.fetched() == null ? "" : ", list of " + snapshot.fetched(),
               snapshot.problems().size()
            }
         );

      for (int i = 0; i < snapshot.problems().size() && i < 10; i++) {
         BedrockNative.LOGGER.info("Partner servers: {}", snapshot.problems().get(i));
      }
   }

   private void warnBadUrl(PartnerConfig config) {
      if (!config.url().isEmpty() && !config.url().equals(this.warnedUrl)) {
         this.warnedUrl = config.url();
         BedrockNative.LOGGER.warn("Partner servers: the list URL in {} is not a plain https URL; partner servers off", "partner-servers.json");
      }
   }

   public interface Fetch {
      byte[] get(URI var1, int var2, String var3) throws IOException, InterruptedException;
   }

   private record Shown(String url, @Nullable String host, PartnerServers.Snapshot snapshot, boolean fetched) {
      boolean matches(URI uri, @Nullable String other) {
         return this.url.equals(uri.toString()) && this.host != null && this.host.equals(other);
      }
   }

   public record Snapshot(
      PartnerServers.Source source, @Nullable String host, List<FeaturedServers.Server> servers, @Nullable Instant fetched, List<String> problems
   ) {
      public Snapshot {
         servers = List.copyOf(servers);
         problems = List.copyOf(problems);
      }

      static PartnerServers.Snapshot of(PartnerServers.Source source, @Nullable String host) {
         return new PartnerServers.Snapshot(source, host, List.of(), null, List.of());
      }
   }

   public enum Source {
      OFF,
      ASK,
      REFUSED,
      FETCHED,
      CACHED,
      UNAVAILABLE;
   }
}
