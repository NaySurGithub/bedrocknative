package net.bedrocknative.content.base;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpClient.Version;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import net.bedrocknative.BedrockNative;
import org.jspecify.annotations.Nullable;

public final class BaseDownload {
   static final int ZIP_FROM = 200;
   static final int PARALLEL = 6;
   private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15L);
   private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(task -> {
      Thread thread = new Thread(task, "BedrockNative base download");
      thread.setDaemon(true);
      return thread;
   });
   private static final Set<String> WANTED = ConcurrentHashMap.newKeySet();
   private static final List<Consumer<BaseDownload.Result>> LISTENERS = new CopyOnWriteArrayList<>();
   private static final AtomicBoolean QUEUED = new AtomicBoolean();
   private static volatile BooleanSupplier allowed = () -> false;
   private static volatile boolean running;
   private static volatile BaseDownload.@Nullable Result last;

   private BaseDownload() {
   }

   public static void allowWhen(BooleanSupplier check) {
      allowed = check;
   }

   public static void listen(Consumer<BaseDownload.Result> listener) {
      LISTENERS.add(listener);
   }

   public static boolean running() {
      return running || QUEUED.get();
   }

   public static BaseDownload.@Nullable Result last() {
      return last;
   }

   public static boolean want(Collection<String> paths) {
      BaseTable table = BaseTable.bundled();
      Path folder = DownloadedBase.folder();
      if (table != null && folder != null && allowed.getAsBoolean()) {
         BaseStore store = new BaseStore(folder);
         boolean queued = false;

         for (String path : paths) {
            BaseTable.Entry entry = find(table, path);
            if (entry != null && !store.present(entry) && WANTED.add(entry.path())) {
               queued = true;
            }
         }

         if (queued) {
            schedule();
         }

         return queued;
      } else {
         return false;
      }
   }

   public static List<BaseTable.Entry> fetchable(Collection<String> paths) {
      BaseTable table = BaseTable.bundled();
      Path folder = DownloadedBase.folder();
      if (table != null && folder != null) {
         BaseStore store = new BaseStore(folder);
         Set<BaseTable.Entry> found = new LinkedHashSet<>();

         for (String path : paths) {
            BaseTable.Entry entry = find(table, path);
            if (entry != null && !store.present(entry)) {
               found.add(entry);
            }
         }

         return List.copyOf(found);
      } else {
         return List.of();
      }
   }

   static BaseTable.@Nullable Entry find(BaseTable table, String path) {
      String clean = path.replace('\\', '/');
      BaseTable.Entry entry = table.entry(clean).orElse(null);
      if (entry != null) {
         return entry;
      }

      for (String extension : List.of(".ogg", ".fsb", ".wav", ".png", ".tga", ".json")) {
         entry = table.entry(clean + extension).orElse(null);
         if (entry != null) {
            return entry;
         }
      }

      return null;
   }

   private static void schedule() {
      if (QUEUED.compareAndSet(false, true)) {
         WORKER.execute(() -> {
            QUEUED.set(false);
            BaseTable table = BaseTable.bundled();
            Path folder = DownloadedBase.folder();
            if (table != null && folder != null) {
               List<String> wanted = new ArrayList<>(WANTED);
               WANTED.removeAll(wanted);
               running = true;
               HttpClient client = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).followRedirects(Redirect.NEVER).version(Version.HTTP_2).build();

               try {
                  BaseDownload.Result result = run(table, folder, false, wanted, BaseDownload.Network.github(table, client));
                  last = result;
                  if (result.written() > 0 || result.coreComplete()) {
                     LISTENERS.forEach(listener -> {
                        try {
                           listener.accept(result);
                        } catch (RuntimeException ex) {
                           BedrockNative.LOGGER.warn("Base download: a listener failed", ex);
                        }
                     });
                  }
               } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
               } catch (RuntimeException e) {
                  BedrockNative.LOGGER.warn("Base download failed", e);
               } finally {
                  running = false;
                  client.close();
               }
            }
         });
      }
   }

   static BaseDownload.Result run(BaseTable table, Path folder, boolean core, Collection<String> wanted, BaseDownload.Network network) throws InterruptedException {
      long started = System.nanoTime();
      FetchRun run = new FetchRun();
      BaseStore store = new BaseStore(folder);

      try {
         Files.createDirectories(folder);
      } catch (IOException e) {
         BedrockNative.LOGGER.warn("Base download: can't create {}: {}", folder, e.getMessage());
         return new BaseDownload.Result(0, 0, 0, 0L, 0, false, List.of(), "can't create the folder", null, 0L);
      }

      Path lockFile = folder.resolveSibling(folder.getFileName() + ".lock");

      try (
         FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
         FileLock lock = tryLock(channel);
      ) {
         if (lock != null) {
            Set<BaseTable.Entry> plan = new LinkedHashSet<>();
            boolean wasComplete = DownloadedBase.check(folder, table);
            if (core) {
               plan.addAll(store.missing(table.entries(BaseTable.Part.CORE)));
            }

            for (String path : wanted) {
               BaseTable.Entry entry = find(table, path);
               if (entry != null && !store.present(entry)) {
                  plan.add(entry);
               }
            }

            List<BaseTable.Entry> todo = new ArrayList<>(plan);
            long planned = todo.stream().mapToLong(BaseTable.Entry::size).sum();
            if (!todo.isEmpty()) {
               BedrockNative.LOGGER
                  .info("Base download: fetching {} files ({}) of {} into {}", new Object[]{todo.size(), megabytes(planned), table.pin(), folder});
               if (network.release() != null && todo.size() >= 200) {
                  new ZipRanges(network.client(), network.release(), network.releaseSize(), network.root(), network.hosts()).fetch(todo, store, run);
                  todo = store.missing(todo);
               }

               if (!todo.isEmpty() && !run.stopped()) {
                  new RawFiles(network.client(), network.raw(), network.hosts(), 6).fetch(todo, store, run);
               }
            }

            boolean complete = wasComplete;
            if (core && !wasComplete) {
               complete = completeCore(table, folder, store, run);
            }

            long millis = (System.nanoTime() - started) / 1000000L;
            BaseDownload.Result result = new BaseDownload.Result(
               plan.size(),
               run.written.get(),
               run.failed.get(),
               run.bytes.get(),
               run.requests.get(),
               complete && !wasComplete,
               List.copyOf(run.done),
               run.stopReason(),
               run.firstFailure(),
               millis
            );
            log(table, result, run, plan.size(), complete);
            return result;
         } else {
            BedrockNative.LOGGER.info("Base download: another game is downloading into {}; this one uses the base once it is complete", folder);
            return new BaseDownload.Result(0, 0, 0, 0L, 0, false, List.of(), "another game is downloading", null, 0L);
         }
      } catch (IOException e) {
         BedrockNative.LOGGER.warn("Base download: {}", e.getMessage());
         return new BaseDownload.Result(
            0,
            run.written.get(),
            run.failed.get(),
            run.bytes.get(),
            run.requests.get(),
            false,
            List.copyOf(run.done),
            e.getMessage(),
            null,
            (System.nanoTime() - started) / 1000000L
         );
      }
   }

   private static @Nullable FileLock tryLock(FileChannel channel) throws IOException {
      try {
         return channel.tryLock();
      } catch (OverlappingFileLockException e) {
         return null;
      }
   }

   private static boolean completeCore(BaseTable table, Path folder, BaseStore store, FetchRun run) throws IOException {
      List<BaseTable.Entry> entries = table.entries(BaseTable.Part.CORE);
      if (!store.missing(entries).isEmpty()) {
         return false;
      }

      List<BaseTable.Entry> changed = new ArrayList<>();

      for (BaseTable.Entry entry : entries) {
         if (!run.done.contains(entry.path()) && !store.verified(entry)) {
            changed.add(entry);
         }
      }

      if (changed.isEmpty()) {
         DownloadedBase.markComplete(folder, table, entries.size(), table.bytes(BaseTable.Part.CORE));
         return true;
      }

      for (BaseTable.Entry entry : changed) {
         Files.deleteIfExists(store.file(entry));
      }

      run.note(changed.size() + " stored files differed from the table and were removed (" + changed.getFirst().path() + "...)");
      return false;
   }

   private static void log(BaseTable table, BaseDownload.Result result, FetchRun run, int planned, boolean complete) {
      if (planned != 0 || result.coreComplete()) {
         String state = result.coreComplete() ? "the base is complete" : (complete ? "base complete" : "base not complete yet");
         String stop = result.stopReason() != null ? "; stopped: " + result.stopReason() + " (the next start goes on)" : "";
         String failure = result.firstFailure() != null ? "; " + result.failed() + " failed, first " + result.firstFailure() : "";
         String notes = run.notes().isEmpty() ? "" : "; " + String.join("; ", run.notes());
         BedrockNative.LOGGER
            .info(
               "Base download: {} of {} files ({}) in {} ms, {} requests, {}{}{}{}",
               new Object[]{result.written(), planned, megabytes(result.bytes()), result.millis(), result.requests(), state, stop, failure, notes}
            );
      }
   }

   static String megabytes(long bytes) {
      return String.format(Locale.ROOT, "%.1f MB", bytes / 1000000.0);
   }

   record Network(HttpClient client, Function<BaseTable.Entry, URI> raw, @Nullable URI release, long releaseSize, String root, BaseHosts hosts) {
      static BaseDownload.Network github(BaseTable table, HttpClient client) {
         return new BaseDownload.Network(client, table::rawUrl, table.releaseUrl().orElse(null), table.releaseSize(), table.root(), BaseHosts.GITHUB);
      }
   }

   public record Result(
      int planned,
      int written,
      int failed,
      long bytes,
      int requests,
      boolean coreComplete,
      List<String> arrived,
      @Nullable String stopReason,
      @Nullable String firstFailure,
      long millis
   ) {
   }
}
