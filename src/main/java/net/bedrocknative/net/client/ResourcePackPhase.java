package net.bedrocknative.net.client;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.content.BaseContent;
import net.bedrocknative.content.BedrockInstall;
import net.bedrocknative.content.CdnDownload;
import net.bedrocknative.content.FolderPack;
import net.bedrocknative.content.PackCache;
import net.bedrocknative.content.PackFeatures;
import net.bedrocknative.content.PackManifest;
import net.bedrocknative.content.PackSource;
import net.bedrocknative.content.PackStack;
import net.bedrocknative.content.UrlGuard;
import net.bedrocknative.content.ZipPack;
import net.bedrocknative.net.auth.ClientIdentity;
import net.bedrocknative.text.BedrockText;
import org.cloudburstmc.protocol.bedrock.data.ExperimentData;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePackChunkRequestPacket;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePackClientResponsePacket;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePackDataInfoPacket;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePackStackPacket;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePacksInfoPacket;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePackClientResponsePacket.Status;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePacksInfoPacket.Entry;
import org.jspecify.annotations.Nullable;

final class ResourcePackPhase {
   static final long MAX_PACK_BYTES = 536870912L;
   static final int MAX_OFFERED_PACKS = 128;
   static final long MAX_SESSION_BYTES = 1073741824L;
   static final long MAX_CACHE_BYTES = 1073741824L;
   static final int MIN_CHUNK_SIZE = 1024;
   static final int MAX_CHUNK_SIZE = 16777216;
   static final int WINDOW = 4;
   static final Duration STALL = Duration.ofSeconds(30L);
   static final Duration STACK_WAIT = Duration.ofSeconds(60L);
   private static final Duration CDN_DEADLINE = Duration.ofMinutes(10L);
   private static final long CDN_PROGRESS_NANOS = TimeUnit.MILLISECONDS.toNanos(250L);
   private static final int CDN_PROGRESS_STEPS = 50;
   private final ResourcePackPhase.Server server;
   private final BedrockGameListener listener;
   private final UrlGuard urlGuard;
   private final ScheduledExecutorService thread = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("Bedrock packs").factory());
   private final Map<UUID, ResourcePackPhase.Offered> offered = new LinkedHashMap<>();
   private final Map<UUID, Path> available = new HashMap<>();
   private final Map<UUID, Path> cachedOptional = new HashMap<>();
   private final Map<UUID, ResourcePackPhase.Download> downloads = new LinkedHashMap<>();
   private @Nullable PackCache cache;
   private boolean serverRequired;
   private ResourcePackPhase.@Nullable Download active;
   private ResourcePackPhase.@Nullable Download cdnActive;
   private @Nullable ScheduledFuture<?> watchdog;
   private @Nullable ScheduledFuture<?> stackWait;
   private boolean answered;
   private boolean stackReceived;
   private volatile boolean closed;
   private final long stallNanos;
   private final long stackWaitNanos;
   private long lastProgress;

   ResourcePackPhase(ResourcePackPhase.Server server, BedrockGameListener listener, UrlGuard urlGuard) {
      this(server, listener, STALL, STACK_WAIT, urlGuard);
   }

   ResourcePackPhase(ResourcePackPhase.Server server, BedrockGameListener listener, Duration stall, UrlGuard urlGuard) {
      this(server, listener, stall, STACK_WAIT, urlGuard);
   }

   ResourcePackPhase(ResourcePackPhase.Server server, BedrockGameListener listener, Duration stall, Duration stackWait, UrlGuard urlGuard) {
      this.server = server;
      this.listener = listener;
      this.urlGuard = urlGuard;
      this.stallNanos = stall.toNanos();
      this.stackWaitNanos = stackWait.toNanos();
   }

   void execute(Runnable task) {
      try {
         this.thread.execute(() -> {
            if (!this.closed) {
               try {
                  task.run();
               } catch (RuntimeException e) {
                  BedrockNative.LOGGER.error("Resource pack phase failed", e);
                  this.server.fail(failure());
               }
            }
         });
      } catch (RejectedExecutionException var3) {
      }
   }

   void onInfo(ResourcePacksInfoPacket info) {
      this.serverRequired = info.isForcedToAccept();

      for (Entry entry : info.getResourcePackInfos()) {
         if (entry.getPackId() != null && entry.getPackVersion() != null && this.offered.size() < 128) {
            this.offered
               .putIfAbsent(
                  entry.getPackId(),
                  new ResourcePackPhase.Offered(
                     entry.getPackId(),
                     entry.getPackVersion(),
                     entry.getPackSize(),
                     text(entry.getContentKey()),
                     text(entry.getSubPackName()),
                     text(entry.getCdnUrl()),
                     entry.isAddonPack()
                  )
               );
         }
      }

      if (this.offered.isEmpty()) {
         this.haveAllPacks();
      } else {
         PackCache packCache = this.listener.packCache();
         this.cache = packCache;
         packCache.removeStaleDownloads();
         List<ResourcePackPhase.Offered> missing = new ArrayList<>();

         for (ResourcePackPhase.Offered pack : this.offered.values()) {
            BedrockNative.LOGGER
               .info(
                  "Server pack {}: {} bytes{}{}{}",
                  new Object[]{
                     pack.id(),
                     pack.size,
                     pack.contentKey.isEmpty() ? "" : ", encrypted",
                     pack.subpack.isEmpty() ? "" : ", subpack " + pack.subpack,
                     pack.cdnUrl.isEmpty() ? "" : ", CDN " + pack.cdnUrl
                  }
               );
            Path cached = packCache.find(pack.uuid, pack.version, pack.size).orElse(null);
            if (cached != null && !pack.contentKey.isEmpty() && !opensWithKey(cached, pack)) {
               packCache.delete(pack.uuid, pack.version);
               cached = null;
            }

            if (cached == null) {
               missing.add(pack);
            } else if (this.required(pack)) {
               this.available.put(pack.uuid, cached);
            } else {
               this.cachedOptional.put(pack.uuid, cached);
            }
         }

         this.trimCache();
         int cached = this.available.size() + this.cachedOptional.size();
         boolean disabled = this.listener.serverPacksDisabled() && !this.cachedOptional.isEmpty();
         if (!disabled) {
            this.available.putAll(this.cachedOptional);
         }

         BedrockNative.LOGGER
            .info(
               "Server offers {} resource packs{}, {} cached{}",
               new Object[]{
                  this.offered.size(),
                  this.serverRequired ? " (required)" : "",
                  cached,
                  disabled ? ", " + this.cachedOptional.size() + " left out (server packs disabled)" : ""
               }
            );
         if (missing.isEmpty()) {
            this.haveAllPacks();
         } else {
            PackOffer offer = new PackOffer(
               this.serverRequired, missing.stream().map(pack -> new PackOffer.Pack(pack.uuid, pack.version, pack.size, this.required(pack))).toList()
            );
            this.listener.onPackOffer(offer, choice -> this.execute(() -> this.onChoice(choice, missing)));
         }
      }
   }

   private void onChoice(PackChoice choice, List<ResourcePackPhase.Offered> missing) {
      BedrockNative.LOGGER.info("Resource pack prompt answered {}", choice);
      if (choice == PackChoice.LEAVE) {
         this.server.send(response(Status.REFUSED));
         this.server.fail(null);
      } else {
         if (choice == PackChoice.EVERYTHING) {
            this.available.putAll(this.cachedOptional);
         } else if (choice == PackChoice.NONE) {
            this.available.keySet().removeAll(this.cachedOptional.keySet());
         }

         List<ResourcePackPhase.Offered> fetch = new ArrayList<>();
         long sessionBytes = 0L;

         for (ResourcePackPhase.Offered pack : missing) {
            boolean wanted = choice == PackChoice.EVERYTHING || choice == PackChoice.REQUIRED_ONLY && this.required(pack);
            boolean sized = pack.size > 0L && pack.size <= 536870912L && sessionBytes + pack.size <= 1073741824L;
            if (wanted && sized) {
               sessionBytes += pack.size;
               fetch.add(pack);
            } else if (!this.skip(pack, wanted ? "advertised size " + pack.size + " (" + sessionBytes + " already taken)" : "declined")) {
               return;
            }
         }

         if (fetch.isEmpty()) {
            this.haveAllPacks();
         } else {
            ResourcePackPhase.Mode mode = fetch.stream().allMatch(pack -> !pack.cdnUrl.isEmpty()) ? ResourcePackPhase.Mode.CDN : ResourcePackPhase.Mode.CHUNKS;
            fetch.forEach(pack -> this.downloads.put(pack.uuid, new ResourcePackPhase.Download(pack, mode)));
            ResourcePackClientResponsePacket request = response(Status.SEND_PACKS);
            if (mode == ResourcePackPhase.Mode.CHUNKS) {
               this.downloads.values().forEach(download -> {
                  download.requested = true;
                  request.getPackIds().add(download.pack.id());
               });
            }

            this.server.send(request);
            BedrockNative.LOGGER
               .info(
                  "Resource packs to download: {} ({} bytes advertised), {}",
                  new Object[]{
                     fetch.size(),
                     sessionBytes,
                     mode == ResourcePackPhase.Mode.CDN ? "from the CDN (the server is asked for none)" : "in chunks from the server"
                  }
               );
            this.lastProgress = System.nanoTime();
            long check = this.stallNanos / 6L;
            this.watchdog = this.thread.scheduleWithFixedDelay(() -> this.execute(this::checkStall), check, check, TimeUnit.NANOSECONDS);
            this.progress();
            this.nextCdn();
         }
      }
   }

   void onDataInfo(ResourcePackDataInfoPacket info) {
      ResourcePackPhase.Download download = this.downloads.get(info.getPackId());
      if (download != null && !download.done && download.chunkSize <= 0) {
         long size = info.getCompressedPackSize();
         long chunkSize = info.getMaxChunkSize();
         byte[] hash = info.getHash();
         if (chunkSize >= 1024L && chunkSize <= 16777216L && size > 0L && size <= allowedBytes(download.pack.size) && hash != null && hash.length == 32) {
            download.size = size;
            download.chunkSize = (int)chunkSize;
            download.chunkCount = (int)((size + chunkSize - 1L) / chunkSize);
            download.hash = hash;
            download.dataInfoVersion = info.getPackVersion();
            BedrockNative.LOGGER
               .info(
                  "Resource pack {}: {} bytes in {} chunks of {} (server count {}){}{}",
                  new Object[]{
                     download.pack.id(),
                     size,
                     download.chunkCount,
                     chunkSize,
                     info.getChunkCount(),
                     size != download.pack.size ? ", advertised " + download.pack.size : "",
                     download.mode == ResourcePackPhase.Mode.CDN
                        ? "; it comes from the CDN, not asked of the server: its hash checks the CDN's zip, its chunks are the fallback"
                        : ""
                  }
               );
            this.startNext();
         } else {
            this.finish(download, "bad ResourcePackDataInfo (" + size + " bytes, chunks of " + chunkSize + ")");
         }
      } else {
         BedrockNative.LOGGER.debug("Ignoring ResourcePackDataInfo for {}", info.getPackId());
      }
   }

   void onChunkData(UUID id, int index, long offset, byte[] data) {
      ResourcePackPhase.Download download = this.active;
      if (download != null && download.pack.uuid.equals(id) && index >= 0 && index < download.next && !download.received.get(index)) {
         long start = (long)index * download.chunkSize;
         if (data.length == Math.min(download.chunkSize, download.size - start) && offset == start) {
            try {
               ByteBuffer buffer = ByteBuffer.wrap(data);

               while (buffer.hasRemaining()) {
                  download.channel.write(buffer, start + buffer.position());
               }
            } catch (IOException e) {
               this.finish(download, "couldn't write the download: " + e.getMessage());
               return;
            }

            download.received.set(index);
            download.inFlight--;
            download.bytes += data.length;
            this.lastProgress = System.nanoTime();
            this.progress();
            if (download.received.cardinality() == download.chunkCount) {
               closeChannel(download);

               try {
                  byte[] actual = PackCache.sha256(download.temp);
                  if (!MessageDigest.isEqual(actual, download.hash)) {
                     this.finish(download, "SHA-256 does not match ResourcePackDataInfo");
                     return;
                  }

                  this.store(download, actual);
               } catch (IOException e) {
                  this.finish(download, e.getMessage());
               }
            } else {
               this.requestChunks(download);
            }
         } else {
            this.finish(download, "chunk " + index + " has " + data.length + " bytes at " + offset);
         }
      } else {
         BedrockNative.LOGGER.debug("Ignoring resource pack chunk {} of {}", index, id);
      }
   }

   void onStack(ResourcePackStackPacket stack) {
      this.stopWatchdog();
      this.stackReceived = true;
      cancel(this.stackWait);
      List<String> experiments = stack.getExperiments().stream().filter(ExperimentData::isEnabled).map(ExperimentData::getName).toList();
      List<Path> base = BaseContent.basePacks(stack.getGameVersion(), experiments);
      List<PackSource> packs = new ArrayList<>();
      List<String> skipped = new ArrayList<>();

      for (org.cloudburstmc.protocol.bedrock.packet.ResourcePackStackPacket.Entry entry : stack.getResourcePacks()) {
         UUID uuid = uuid(entry.getPackId());
         ResourcePackPhase.Offered pack = uuid == null ? null : this.offered.get(uuid);
         Path file = uuid == null ? null : this.available.get(uuid);
         Path builtIn = pack == null && uuid != null
            ? BedrockInstall.resourcePack(uuid, text(entry.getPackVersion())).filter(folder -> !base.contains(folder)).orElse(null)
            : null;
         if (builtIn != null) {
            BedrockNative.LOGGER.info("Applying built-in resource pack {}_{} from {}", new Object[]{uuid, entry.getPackVersion(), builtIn.getFileName()});
            packs.add(new FolderPack(builtIn));
         } else if (pack != null && file != null) {
            String subpack = text(entry.getSubPackName()).isEmpty() ? pack.subpack : entry.getSubPackName();

            try {
               String defaulted = null;
               if (subpack.isBlank()) {
                  defaulted = ZipPack.readManifest(file).defaultSubpackFolder(ClientIdentity.memoryTier());
                  subpack = defaulted == null ? "" : defaulted;
               }

               ZipPack opened = ZipPack.open(file, pack.contentKey, subpack);
               BedrockNative.LOGGER
                  .info(
                     "Applying resource pack {}{}{}",
                     new Object[]{
                        pack.id(),
                        opened.encrypted() ? " (decrypted)" : "",
                        opened.subpack() == null ? "" : " with " + opened.subpack() + (defaulted != null ? " (default subpack, the server named none)" : "")
                     }
                  );
               List<String> notRead = PackFeatures.notRead(opened);
               if (!notRead.isEmpty()) {
                  BedrockNative.LOGGER.info("Resource pack {} features not read: {}", pack.id(), String.join("; ", notRead));
               }

               packs.add(opened);
            } catch (IOException e) {
               BedrockNative.LOGGER.warn("Resource pack {} not applied: {}", pack.id(), e.getMessage());
               skipped.add(pack.id() + (pack.contentKey.isEmpty() ? "" : " (encrypted)") + ": " + e.getMessage());
            }
         } else {
            BedrockNative.LOGGER.debug("Stack pack {}_{} not applied: not downloaded", entry.getPackId(), entry.getPackVersion());
         }
      }

      PackStack.recordSkippedServerPacks(skipped);
      BedrockNative.LOGGER
         .info(
            "Resource pack stack: {} of {} packs applied, base game {}, experiments {}",
            new Object[]{packs.size(), stack.getResourcePacks().size(), stack.getGameVersion(), experiments}
         );
      this.listener.onPacksApplied(List.copyOf(packs), stack.getGameVersion(), experiments);
      this.server.send(response(Status.COMPLETED));
   }

   static long allowedBytes(long advertised) {
      return Math.min(536870912L, advertised + Math.max(advertised / 4L, 4194304L));
   }

   private void trimCache() {
      if (this.cache != null) {
         this.cache.trim(1073741824L, Stream.concat(this.available.values().stream(), this.cachedOptional.values().stream()).toList());
      }
   }

   void close() {
      try {
         this.thread.execute(() -> {
            this.closed = true;
            this.stopWatchdog();
            cancel(this.stackWait);
            this.downloads.values().forEach(download -> {
               if (download.cdn != null) {
                  download.cdn.cancel();
               }

               closeChannel(download);
               delete(download.temp);
            });
         });
      } catch (RejectedExecutionException var2) {
      }

      this.thread.shutdown();
   }

   private void startNext() {
      if (this.active == null) {
         for (ResourcePackPhase.Download download : this.downloads.values()) {
            if (!download.done && download.mode == ResourcePackPhase.Mode.CHUNKS && download.chunkSize > 0) {
               try {
                  download.temp = this.cache.temporary(download.pack.uuid);
                  download.channel = FileChannel.open(download.temp, StandardOpenOption.WRITE);
               } catch (IOException e) {
                  this.finish(download, "couldn't write the download: " + e.getMessage());
                  return;
               }

               download.received = new BitSet(download.chunkCount);
               download.bytes = 0L;
               this.active = download;
               this.requestChunks(download);
               return;
            }
         }
      }
   }

   private void requestChunks(ResourcePackPhase.Download download) {
      while (download.inFlight < 4 && download.next < download.chunkCount) {
         ResourcePackChunkRequestPacket request = new ResourcePackChunkRequestPacket();
         request.setPackId(download.pack.uuid);
         request.setPackVersion(download.dataInfoVersion);
         request.setChunkIndex(download.next++);
         this.server.send(request);
         download.inFlight++;
      }
   }

   private void nextCdn() {
      if (this.cdnActive == null) {
         for (ResourcePackPhase.Download download : this.downloads.values()) {
            if (!download.done && download.mode == ResourcePackPhase.Mode.CDN) {
               this.startCdn(download);
               return;
            }
         }
      }
   }

   private void startCdn(ResourcePackPhase.Download download) {
      Path temp;
      try {
         temp = this.cache.temporary(download.pack.uuid);
      } catch (IOException e) {
         this.finish(download, "couldn't write the download: " + e.getMessage());
         return;
      }

      this.cdnActive = download;
      download.temp = temp;
      this.lastProgress = System.nanoTime();
      CdnDownload cdn = new CdnDownload(this.urlGuard);
      download.cdn = cdn;
      AtomicLong posted = new AtomicLong(System.nanoTime());
      AtomicLong postedBytes = new AtomicLong();
      long step = Math.max(1L, download.pack.size / 50L);
      long started = System.nanoTime();
      Thread.ofVirtual().name("Bedrock pack CDN").start(() -> {
         try {
            byte[] hash = cdn.fetch(download.pack.cdnUrl, temp, allowedBytes(download.pack.size), CDN_DEADLINE, bytes -> {
               long now = System.nanoTime();
               if (bytes == 0L || now - posted.get() >= CDN_PROGRESS_NANOS || bytes - postedBytes.get() >= step) {
                  posted.set(now);
                  postedBytes.set(bytes);
                  this.execute(() -> this.cdnProgress(download, bytes));
               }
            });
            long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            this.execute(() -> this.cdnDone(download, hash, millis));
         } catch (IOException | RuntimeException e) {
            if (this.closed) {
               delete(temp);
            }

            this.execute(() -> this.cdnFailed(download, e.getMessage()));
         }
      });
   }

   private void cdnProgress(ResourcePackPhase.Download download, long bytes) {
      if (!download.done && download.mode == ResourcePackPhase.Mode.CDN) {
         download.bytes = bytes;
         this.lastProgress = System.nanoTime();
         this.progress();
      }
   }

   private void cdnDone(ResourcePackPhase.Download download, byte[] hash, long millis) {
      if (!download.done && download.mode == ResourcePackPhase.Mode.CDN) {
         this.cdnActive = null;
         download.cdn = null;

         try {
            download.bytes = Files.size(download.temp);
            this.progress();
            if (download.hash != null && !MessageDigest.isEqual(hash, download.hash)) {
               throw new IOException("SHA-256 does not match the server's ResourcePackDataInfo");
            }

            BedrockNative.LOGGER.info("CDN download of resource pack {}: {} bytes in {} ms", new Object[]{download.pack.id(), download.bytes, millis});
            this.store(download, hash);
         } catch (IOException e) {
            this.cdnFailed(download, e.getMessage());
         }
      }
   }

   private void cdnFailed(ResourcePackPhase.Download download, String reason) {
      if (!download.done && download.mode == ResourcePackPhase.Mode.CDN) {
         BedrockNative.LOGGER.info("CDN download of resource pack {} failed ({}); requesting it from the server", download.pack.id(), reason);
         this.cdnActive = null;
         download.cdn = null;
         delete(download.temp);
         download.temp = null;
         download.bytes = 0L;
         download.mode = ResourcePackPhase.Mode.CHUNKS;
         this.lastProgress = System.nanoTime();
         this.startNext();
         this.nextCdn();
         this.requestFallbacks();
      }
   }

   private void requestFallbacks() {
      if (this.cdnActive == null && !this.closed) {
         List<ResourcePackPhase.Download> missing = this.downloads
            .values()
            .stream()
            .filter(download -> !download.done && download.mode == ResourcePackPhase.Mode.CHUNKS && !download.requested && download.chunkSize == 0)
            .toList();
         if (!missing.isEmpty()) {
            ResourcePackClientResponsePacket request = response(Status.SEND_PACKS);
            missing.forEach(download -> {
               download.requested = true;
               request.getPackIds().add(download.pack.id());
            });
            this.lastProgress = System.nanoTime();
            this.server.send(request);
         }
      }
   }

   private void store(ResourcePackPhase.Download download, byte[] hash) throws IOException {
      PackManifest manifest = ZipPack.readManifest(download.temp);
      if (!manifest.uuid().equals(download.pack.uuid)) {
         throw new IOException("manifest.json is pack " + manifest.uuid());
      }

      if (!PackManifest.sameVersion(manifest.version(), download.pack.version)) {
         BedrockNative.LOGGER.info("Resource pack {} has manifest version {}", download.pack.id(), manifest.version());
      }

      this.available.put(download.pack.uuid, this.cache.store(download.pack.uuid, download.pack.version, download.pack.size, download.temp, hash));
      this.trimCache();
      download.temp = null;
      BedrockNative.LOGGER
         .info(
            "Downloaded resource pack {} ({} bytes{})",
            new Object[]{download.pack.id(), download.bytes, download.mode == ResourcePackPhase.Mode.CDN ? " from the CDN" : ""}
         );
      this.finish(download, null);
   }

   private void finish(ResourcePackPhase.Download download, @Nullable String failure) {
      this.lastProgress = System.nanoTime();
      closeChannel(download);
      delete(download.temp);
      download.temp = null;
      download.done = true;
      if (this.active == download) {
         this.active = null;
      }

      if (this.cdnActive == download) {
         this.cdnActive = null;
      }

      if (failure == null || this.skip(download.pack, failure)) {
         if (this.downloads.values().stream().allMatch(remaining -> remaining.done)) {
            this.haveAllPacks();
         } else {
            this.startNext();
            this.nextCdn();
            this.requestFallbacks();
         }
      }
   }

   private boolean skip(ResourcePackPhase.Offered pack, String reason) {
      BedrockNative.LOGGER.warn("Resource pack {} not downloaded: {}", pack.id(), reason);
      if (this.required(pack)) {
         this.server.fail(failure());
         return false;
      } else {
         return true;
      }
   }

   private void checkStall() {
      if (!this.answered && System.nanoTime() - this.lastProgress >= this.stallNanos) {
         String reason = "no data for " + TimeUnit.NANOSECONDS.toSeconds(this.stallNanos) + " s";
         ResourcePackPhase.Download stalledCdn = this.cdnActive;
         if (stalledCdn != null) {
            if (stalledCdn.cdn != null) {
               stalledCdn.cdn.cancel();
            }

            this.cdnFailed(stalledCdn, reason);
         } else {
            for (ResourcePackPhase.Download download : List.copyOf(this.downloads.values())) {
               if (!download.done) {
                  if (download.cdn != null) {
                     download.cdn.cancel();
                  }

                  this.finish(download, reason);
                  if (this.closed || this.answered) {
                     return;
                  }
               }
            }
         }
      }
   }

   private void progress() {
      long done = this.downloads.values().stream().filter(download -> download.done).count();
      long bytes = this.downloads.values().stream().mapToLong(download -> download.done ? Math.max(download.size, download.pack.size) : download.bytes).sum();
      long total = this.downloads.values().stream().mapToLong(download -> download.size > 0L ? download.size : download.pack.size).sum();
      this.listener
         .onPackProgress(new PackProgress((int)Math.min(done + 1L, this.downloads.size()), this.downloads.size(), Math.min(bytes, total), total, this.answered));
   }

   private void haveAllPacks() {
      if (!this.answered) {
         this.answered = true;
         this.stopWatchdog();
         if (!this.downloads.isEmpty()) {
            this.progress();
         }

         this.server.send(response(Status.HAVE_ALL_PACKS));

         try {
            this.stackWait = this.thread.schedule(() -> this.execute(this::stackMissing), this.stackWaitNanos, TimeUnit.NANOSECONDS);
         } catch (RejectedExecutionException var2) {
         }
      }
   }

   private void stackMissing() {
      if (!this.stackReceived) {
         List<String> announced = this.downloads
            .values()
            .stream()
            .filter(download -> download.chunkSize > 0 && !download.requested)
            .map(download -> download.pack.id())
            .toList();
         String message = "The server sent no resource pack stack " + TimeUnit.NANOSECONDS.toSeconds(this.stackWaitNanos) + " s after the client had all packs";
         BedrockNative.LOGGER
            .warn(
               "{} ({} downloads{})",
               new Object[]{
                  message, this.downloads.size(), announced.isEmpty() ? "" : "; the server sent a DataInfo it may still want chunk requests for: " + announced
               }
            );
         this.server.fail(message);
      }
   }

   private static void cancel(@Nullable ScheduledFuture<?> future) {
      if (future != null) {
         future.cancel(false);
      }
   }

   private static boolean opensWithKey(Path cached, ResourcePackPhase.Offered pack) {
      try (ZipPack ignored = ZipPack.open(cached, pack.contentKey, "")) {
         return true;
      } catch (IOException e) {
         BedrockNative.LOGGER.info("Cached resource pack {} doesn't open with the server's content key ({}); downloading it again", pack.id(), e.getMessage());
         return false;
      }
   }

   private boolean required(ResourcePackPhase.Offered pack) {
      return this.serverRequired || pack.addon;
   }

   private void stopWatchdog() {
      if (this.watchdog != null) {
         this.watchdog.cancel(false);
         this.watchdog = null;
      }
   }

   private static void closeChannel(ResourcePackPhase.Download download) {
      if (download.channel != null) {
         try {
            download.channel.close();
         } catch (IOException var2) {
         }

         download.channel = null;
      }
   }

   private static void delete(@Nullable Path file) {
      if (file != null) {
         try {
            Files.deleteIfExists(file);
         } catch (IOException e) {
            BedrockNative.LOGGER.debug("Couldn't delete {}", file, e);
         }
      }
   }

   private static ResourcePackClientResponsePacket response(Status status) {
      ResourcePackClientResponsePacket response = new ResourcePackClientResponsePacket();
      response.setStatus(status);
      return response;
   }

   private static @Nullable UUID uuid(@Nullable String text) {
      try {
         return text == null ? null : UUID.fromString(text.trim());
      } catch (IllegalArgumentException e) {
         return null;
      }
   }

   private static String text(@Nullable String text) {
      return text == null ? "" : text;
   }

   static String failure() {
      return BedrockText.translate("disconnectionScreen.resourcePack", List.of());
   }

   private static final class Download {
      final ResourcePackPhase.Offered pack;
      ResourcePackPhase.Mode mode;
      boolean done;
      long bytes;
      long size;
      int chunkSize;
      int chunkCount;
      byte[] hash;
      @Nullable String dataInfoVersion;
      boolean requested;
      @Nullable Path temp;
      @Nullable FileChannel channel;
      BitSet received = new BitSet();
      int next;
      int inFlight;
      @Nullable CdnDownload cdn;

      Download(ResourcePackPhase.Offered pack, ResourcePackPhase.Mode mode) {
         this.pack = pack;
         this.mode = mode;
      }
   }

   private enum Mode {
      CHUNKS,
      CDN;
   }

   private record Offered(UUID uuid, String version, long size, String contentKey, String subpack, String cdnUrl, boolean addon) {
      String id() {
         return this.uuid + "_" + this.version;
      }
   }

   interface Server {
      void send(BedrockPacket var1);

      void fail(@Nullable String var1);
   }
}
