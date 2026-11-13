package net.bedrocknative.perf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import org.jspecify.annotations.Nullable;

public final class FrameWatch {
   public static final long HITCH_NANOS = 40000000L;
   public static final long RECORD_NANOS = 10000000L;
   public static final long SLOW_NANOS = 100000000L;
   public static final int SLOW_LINES = 30;
   public static final long SLOW_WINDOW_NANOS = 300000000000L;
   private static final long SECOND = 1000000000L;
   private static final int WORST_KEPT = 6;
   private static final int SLOW_PARTS = 6;
   private static final FrameWatch.Bucket[] BUCKETS = FrameWatch.Bucket.values();
   private static final FrameWatch.ApplyPart[] APPLY_PARTS = FrameWatch.ApplyPart.values();
   private static final long APPLY_PARTS_NANOS = 2000000L;
   private final Consumer<String> hitchLog;
   private final Consumer<String> secondLog;
   @Nullable
   private final LongSupplier gcClock;
   private long frameStart;
   private long frameGcStart;
   private long frameGcNanos;
   private long lastFrameGcNanos;
   private long secondGcNanos;
   private boolean frameLoading;
   private final long[] buckets = new long[BUCKETS.length];
   private final long[] applyParts = new long[APPLY_PARTS.length];
   private final int[] counts = new int[BUCKETS.length];
   private final long[] lastBuckets = new long[BUCKETS.length];
   private final int[] lastCounts = new int[BUCKETS.length];
   private int frameColumns;
   private int frameDropped;
   private int frameWaiting;
   private long frameTickNanos;
   private int frameTickCount;
   private long longestTick;
   private long tickStart;
   private long lastTickStart;
   private long secondStart;
   private final FrameStats secondFrames = new FrameStats();
   private final FrameStats secondTickGaps = new FrameStats();
   private int secondColumns;
   private int secondDropped;
   private int secondHitches;
   private long secondApplyNanos;
   private long secondLightNanos;
   private int secondWaiting;
   private boolean secondActive;
   private final Consumer<String> slowLog;
   private int secondSlow;
   private int secondSlowLoading;
   private long secondFrameNanos;
   private long secondTickNanos;
   private final long[] secondBuckets = new long[BUCKETS.length];
   private final int[] secondCounts = new int[BUCKETS.length];
   private long slowWindowStart;
   private int slowLinesLeft;
   private int slowSecondsNotLogged;
   private final FrameStats burstFrames = new FrameStats();
   private final FrameStats burstTickGaps = new FrameStats();
   private long burstApplyNanos;
   private long burstLightNanos;
   private int burstHitches;
   private boolean inBurst;
   private boolean hitchLogged;
   private long lastHitchLogged;
   private int hitchesNotLogged;
   private FrameWatch.Recording recording;

   public FrameWatch(Consumer<String> hitchLog, Consumer<String> secondLog) {
      this(hitchLog, secondLog, line -> {});
   }

   public FrameWatch(Consumer<String> hitchLog, Consumer<String> secondLog, Consumer<String> slowLog) {
      this(hitchLog, secondLog, slowLog, null);
   }

   public FrameWatch(Consumer<String> hitchLog, Consumer<String> secondLog, Consumer<String> slowLog, @Nullable LongSupplier gcClock) {
      this.hitchLog = hitchLog;
      this.secondLog = secondLog;
      this.slowLog = slowLog;
      this.gcClock = gcClock;
   }

   public long frameStart(long now, boolean loading) {
      long gc = this.gcClock != null ? this.gcClock.getAsLong() : 0L;
      long ended = 0L;
      if (this.frameStart != 0L) {
         this.frameGcNanos = Math.max(0L, gc - this.frameGcStart);
         ended = now - this.frameStart;
         this.endFrame(ended, now);
      }

      this.frameGcStart = gc;
      this.frameStart = now;
      this.frameLoading = loading;
      Arrays.fill(this.buckets, 0L);
      Arrays.fill(this.applyParts, 0L);
      Arrays.fill(this.counts, 0);
      this.frameColumns = 0;
      this.frameDropped = 0;
      this.frameTickNanos = 0L;
      this.frameTickCount = 0;
      this.longestTick = 0L;
      return ended;
   }

   public void add(FrameWatch.Bucket bucket, long nanos) {
      this.buckets[bucket.ordinal()] += nanos;
   }

   public void applyPart(FrameWatch.ApplyPart part, long nanos) {
      this.applyParts[part.ordinal()] += nanos;
   }

   public long bucketNanos(FrameWatch.Bucket bucket) {
      return this.buckets[bucket.ordinal()];
   }

   public long namedNanos() {
      long sum = 0L;

      for (long nanos : this.buckets) {
         sum += nanos;
      }

      return sum;
   }

   public void count(FrameWatch.Bucket bucket, int count) {
      this.counts[bucket.ordinal()] = Math.max(this.counts[bucket.ordinal()], count);
   }

   public long lastFrameNanos(FrameWatch.Bucket bucket) {
      return this.lastBuckets[bucket.ordinal()];
   }

   public int lastFrameCount(FrameWatch.Bucket bucket) {
      return this.lastCounts[bucket.ordinal()];
   }

   public long lastFrameGcNanos() {
      return this.lastFrameGcNanos;
   }

   public int frameColumns() {
      return this.frameColumns;
   }

   public void columns(int applied, int dropped, int waiting) {
      this.frameColumns += applied;
      this.frameDropped += dropped;
      this.frameWaiting = waiting;
      this.secondWaiting = waiting;
      if (applied > 0 || dropped > 0 || waiting > 0) {
         this.secondActive = true;
      }
   }

   public void tickStart(long now) {
      if (this.lastTickStart != 0L) {
         long gap = now - this.lastTickStart;
         this.secondTickGaps.add(gap);
         if (this.inBurst) {
            this.burstTickGaps.add(gap);
         }

         if (this.recording != null) {
            this.recording.tickGaps.add(gap);
         }
      }

      this.lastTickStart = now;
      this.tickStart = now;
   }

   public void tickEnd(long now) {
      if (this.tickStart != 0L) {
         long nanos = now - this.tickStart;
         this.frameTickNanos += nanos;
         this.frameTickCount++;
         this.longestTick = Math.max(this.longestTick, nanos);
         if (this.recording != null) {
            this.recording.ticks.add(nanos);
         }

         this.tickStart = 0L;
      }
   }

   public void playerColumnWaited() {
      if (this.recording != null) {
         this.recording.playerColumnWaits++;
      }
   }

   public void beginBurst() {
      this.inBurst = true;
      this.burstFrames.clear();
      this.burstTickGaps.clear();
      this.burstApplyNanos = 0L;
      this.burstLightNanos = 0L;
      this.burstHitches = 0;
   }

   public boolean inBurst() {
      return this.inBurst;
   }

   public String endBurst() {
      this.inBurst = false;
      return String.format(
         Locale.ROOT,
         "frames %s; tick gaps %s; apply %.0f ms, light %.0f ms in all; %d frames over %d ms",
         this.burstFrames.summary(),
         this.burstTickGaps.summary(),
         this.burstApplyNanos / 1000000.0,
         this.burstLightNanos / 1000000.0,
         this.burstHitches,
         40L
      );
   }

   public void startRecording(String name) {
      this.recording = new FrameWatch.Recording(name);
   }

   public FrameWatch.Recording stopRecording() {
      FrameWatch.Recording done = this.recording;
      this.recording = null;
      return done;
   }

   public void waiting(int waiting) {
      if (this.recording != null) {
         this.recording.maxWaiting = Math.max(this.recording.maxWaiting, waiting);
      }
   }

   private void endFrame(long nanos, long now) {
      String causes = null;
      long apply = this.buckets[FrameWatch.Bucket.CHUNK_APPLY.ordinal()];
      long light = this.buckets[FrameWatch.Bucket.LIGHT.ordinal()];
      this.secondFrames.add(nanos);
      this.secondColumns = this.secondColumns + this.frameColumns;
      this.secondDropped = this.secondDropped + this.frameDropped;
      this.secondApplyNanos += apply;
      this.secondLightNanos += light;
      if (this.inBurst) {
         this.burstFrames.add(nanos);
         this.burstApplyNanos += apply;
         this.burstLightNanos += light;
      }

      this.secondFrameNanos += nanos;
      this.secondTickNanos = this.secondTickNanos + this.frameTickNanos;
      this.secondGcNanos = this.secondGcNanos + this.frameGcNanos;
      this.lastFrameGcNanos = this.frameGcNanos;

      for (int i = 0; i < BUCKETS.length; i++) {
         this.secondBuckets[i] = this.secondBuckets[i] + this.buckets[i];
         this.secondCounts[i] = Math.max(this.secondCounts[i], this.counts[i]);
      }

      System.arraycopy(this.buckets, 0, this.lastBuckets, 0, BUCKETS.length);
      System.arraycopy(this.counts, 0, this.lastCounts, 0, BUCKETS.length);
      if (nanos > 100000000L) {
         this.secondSlow++;
         if (this.frameLoading) {
            this.secondSlowLoading++;
         }
      }

      if (nanos > 40000000L) {
         this.secondHitches++;
         if (this.inBurst) {
            this.burstHitches++;
         }

         causes = this.causes(nanos);
         if (this.hitchLogged && now - this.lastHitchLogged < 1000000000L) {
            this.hitchesNotLogged++;
         } else {
            this.hitchLogged = true;
            this.lastHitchLogged = now;
            String skipped = this.hitchesNotLogged > 0 ? " (" + this.hitchesNotLogged + " more since the last line)" : "";
            this.hitchesNotLogged = 0;
            this.hitchLog
               .accept(
                  String.format(
                     Locale.ROOT, "Hitch: frame %.0f ms%s (%s)%s", nanos / 1000000.0, this.frameLoading ? " under the loading screen" : "", causes, skipped
                  )
               );
         }
      }

      if (this.recording != null) {
         this.recording.columns = this.recording.columns + this.frameColumns;
         this.recording.dropped = this.recording.dropped + this.frameDropped;
         this.recording.applyNanos += apply;
         this.recording.lightNanos += light;
         this.recording.gcNanos = this.recording.gcNanos + this.frameGcNanos;
         this.recording.frameCount++;
         this.recording.frameNanos += nanos;

         for (int i = 0; i < BUCKETS.length; i++) {
            this.recording.bucketNanos[i] = this.recording.bucketNanos[i] + this.buckets[i];
         }

         for (int i = 0; i < APPLY_PARTS.length; i++) {
            this.recording.applyPartNanos[i] = this.recording.applyPartNanos[i] + this.applyParts[i];
         }

         if (causes == null && nanos > 10000000L) {
            causes = this.causes(nanos);
         }

         boolean viewRebuild = this.buckets[FrameWatch.Bucket.VIEW_REBUILD.ordinal()] > 0L;
         if (nanos > 10000000L && !this.frameLoading && !viewRebuild) {
            this.recording.slowFrame(nanos, this.buckets, this.frameTickNanos, this.frameGcNanos);
         }

         this.recording.frame(nanos, this.frameLoading, viewRebuild, causes != null ? causes : "");
      }

      if (this.secondStart == 0L) {
         this.secondStart = now;
      } else if (now - this.secondStart >= 1000000000L) {
         if (this.secondSlow > 0) {
            this.slowSecond(now);
         }

         if (this.secondActive) {
            this.secondLog
               .accept(
                  String.format(
                     Locale.ROOT,
                     "Chunk stream: %d columns applied, %d dropped, %d waiting; apply %.1f ms, light %.1f ms; frames %s, %d over %d ms; tick gaps p99 %.1f, max %.1f ms",
                     this.secondColumns,
                     this.secondDropped,
                     this.secondWaiting,
                     this.secondApplyNanos / 1000000.0,
                     this.secondLightNanos / 1000000.0,
                     this.secondFrames.summary(),
                     this.secondHitches,
                     40L,
                     this.secondTickGaps.percentileMillis(99.0),
                     this.secondTickGaps.maxMillis()
                  )
               );
         }

         this.secondStart = now;
         this.secondFrames.clear();
         this.secondTickGaps.clear();
         this.secondColumns = 0;
         this.secondDropped = 0;
         this.secondHitches = 0;
         this.secondApplyNanos = 0L;
         this.secondLightNanos = 0L;
         this.secondActive = this.secondWaiting > 0;
         this.secondSlow = 0;
         this.secondSlowLoading = 0;
         this.secondFrameNanos = 0L;
         this.secondTickNanos = 0L;
         this.secondGcNanos = 0L;
         Arrays.fill(this.secondBuckets, 0L);
         Arrays.fill(this.secondCounts, 0);
      }
   }

   private void slowSecond(long now) {
      if (this.slowWindowStart == 0L || now - this.slowWindowStart >= 300000000000L) {
         this.slowWindowStart = now;
         this.slowLinesLeft = 30;
      }

      if (this.slowLinesLeft <= 0) {
         this.slowSecondsNotLogged++;
      } else {
         this.slowLinesLeft--;
         this.slowLog.accept(this.slowLine());
         this.slowSecondsNotLogged = 0;
      }
   }

   private String slowLine() {
      long span = this.secondFrameNanos;
      int frames = Math.max(1, this.secondFrames.count());
      List<FrameWatch.Part> parts = new ArrayList<>();
      long named = 0L;

      for (FrameWatch.Bucket bucket : BUCKETS) {
         long nanos = this.secondBuckets[bucket.ordinal()];
         named += nanos;
         if (nanos > 0L || this.secondCounts[bucket.ordinal()] > 0) {
            parts.add(new FrameWatch.Part(bucket.label, nanos, this.secondCounts[bucket.ordinal()]));
         }
      }

      if (this.secondTickNanos > 0L) {
         parts.add(new FrameWatch.Part("client ticks", this.secondTickNanos, 0));
      }

      parts.add(new FrameWatch.Part("other", Math.max(0L, this.secondFrameNanos - named - this.secondTickNanos), 0));
      parts.sort(Comparator.comparingLong(FrameWatch.Part::nanos).reversed());
      StringBuilder text = new StringBuilder();
      text.append(
         String.format(
            Locale.ROOT,
            "Slow frames: %d of %d frames over %d ms in %.1f s (worst %.0f ms",
            this.secondSlow,
            this.secondFrames.count(),
            100L,
            span / 1.0E9,
            this.secondFrames.maxMillis()
         )
      );
      if (this.secondSlowLoading > 0) {
         text.append(", ").append(this.secondSlowLoading).append(" under the loading screen");
      }

      text.append("); a frame:");

      for (int i = 0; i < Math.min(6, parts.size()); i++) {
         FrameWatch.Part part = parts.get(i);
         text.append(i == 0 ? " " : ", ").append(part.label()).append(' ').append(Math.round(part.nanos() / 1000000.0 / frames)).append(" ms");
         if (part.count() > 0) {
            text.append(" (").append(part.count()).append(" drawn)");
         }
      }

      FrameWatch.Part top = parts.getFirst();
      text.append("; top cost ").append(top.label());
      if (top.label().equals("other")) {
         text.append(" (Java's own work no part names: terrain drawing, GC, the frame limiter)");
      }

      if (this.gcClock != null) {
         text.append("; GC pauses ").append(Math.round(this.secondGcNanos / 1000000.0)).append(" ms in all");
      }

      if (this.slowSecondsNotLogged > 0) {
         text.append("; ").append(this.slowSecondsNotLogged).append(" slow seconds not logged before this one");
      }

      return text.toString();
   }

   private String causes(long frameNanos) {
      StringBuilder text = new StringBuilder();
      long named = 0L;

      for (FrameWatch.Bucket bucket : BUCKETS) {
         long nanos = this.buckets[bucket.ordinal()];
         named += nanos;
         if (nanos >= 500000L || bucket == FrameWatch.Bucket.CHUNK_APPLY && this.frameColumns > 0) {
            if (!text.isEmpty()) {
               text.append(", ");
            }

            text.append(bucket.label).append(' ').append(Math.round(nanos / 1000000.0)).append(" ms");
            if (bucket == FrameWatch.Bucket.CHUNK_APPLY) {
               text.append(" for ").append(this.frameColumns).append(this.frameColumns == 1 ? " column" : " columns");
               String parts = nanos >= 2000000L ? partsText(this.applyParts, 500000L) : "";
               if (!parts.isEmpty()) {
                  text.append(" (").append(parts).append(')');
               }
            } else if (this.counts[bucket.ordinal()] > 0) {
               text.append(" (").append(this.counts[bucket.ordinal()]).append(" drawn)");
            }
         }
      }

      if (this.frameTickCount > 0) {
         if (!text.isEmpty()) {
            text.append(", ");
         }

         text.append(this.frameTickCount == 1 ? "client tick " : this.frameTickCount + " client ticks ")
            .append(Math.round(this.frameTickNanos / 1000000.0))
            .append(" ms");
         if (this.frameTickCount > 1) {
            text.append(" (longest ").append(Math.round(this.longestTick / 1000000.0)).append(" ms)");
         }
      }

      long known = named + this.frameTickNanos;
      if (!text.isEmpty()) {
         text.append(", ");
      }

      text.append("other ").append(Math.max(0L, Math.round((frameNanos - known) / 1000000.0))).append(" ms");
      if (this.frameWaiting > 0) {
         text.append("; ").append(this.frameWaiting).append(" columns waiting");
      }

      if (this.gcClock != null) {
         text.append("; GC pauses ").append(Math.round(this.frameGcNanos / 1000000.0)).append(" ms");
      }

      return text.toString();
   }

   private static String partsText(long[] partNanos, long atLeastNanos) {
      StringBuilder text = new StringBuilder();

      for (FrameWatch.ApplyPart part : APPLY_PARTS) {
         long nanos = partNanos[part.ordinal()];
         if (nanos >= atLeastNanos) {
            text.append(text.isEmpty() ? "" : ", ").append(part.label).append(' ').append(Math.round(nanos / 1000000.0));
         }
      }

      return text.isEmpty() ? "" : text.append(" ms").toString();
   }

   public static enum ApplyPart {
      INSERT("insert"),
      SKY_SOURCES("sky sources"),
      EVICT("evict"),
      LIGHT_ENQUEUE("light enqueue"),
      BLOCK_ENTITIES("block entities"),
      PARKED("parked packets");

      private final String label;

      private ApplyPart(String label) {
         this.label = label;
      }

      public String label() {
         return this.label;
      }
   }

   public static enum Bucket {
      CHUNK_APPLY("chunk apply"),
      LIGHT("light"),
      LIGHT_UPDATES("light updates"),
      CHUNK_UNLOAD("chunk unload"),
      CUSTOM_BLOCKS("custom block models"),
      RELOAD("resource reload"),
      ENTITIES("entity setup"),
      SKINS("skins"),
      JAVA_ADD("Java add-entity"),
      ACTOR_SPAWN("actor spawn"),
      ACTOR_PREP("actor prep"),
      MODELS("models"),
      UI("JSON UI"),
      VIEW_REBUILD("view rebuild"),
      VIEW_MOVE("view move"),
      SECTION_COPIES("section copies"),
      SECTION_COMPILE("section compile"),
      TERRAIN_UPLOADS("terrain uploads"),
      ENTITY_RENDER("Java entities"),
      BLOCK_ENTITY_RENDER("Java block entities"),
      FEATURE_PREP("Java feature prep"),
      GUI_DRAW("Java GUI draw"),
      PRESENT("present"),
      WINDOW_EVENTS("window events");

      private final String label;

      private Bucket(String label) {
         this.label = label;
      }

      public String label() {
         return this.label;
      }
   }

   public record Hitch(double frameMillis, boolean loading, boolean viewRebuild, String causes) {
   }

   private record Part(String label, long nanos, int count) {
   }

   public static final class Recording {
      private final String name;
      private final FrameStats inGame = new FrameStats();
      private final FrameStats loading = new FrameStats();
      private final FrameStats viewRebuilds = new FrameStats();
      private final FrameStats tickGaps = new FrameStats();
      private final FrameStats ticks = new FrameStats();
      private final List<FrameWatch.Hitch> worst = new ArrayList<>();
      private int columns;
      private int dropped;
      private int maxWaiting;
      private int playerColumnWaits;
      private long applyNanos;
      private long lightNanos;
      private long gcNanos;
      private final long[] bucketNanos = new long[FrameWatch.BUCKETS.length];
      private final long[] applyPartNanos = new long[FrameWatch.APPLY_PARTS.length];
      private int frameCount;
      private long frameNanos;
      private int slowFrames;
      private long slowFrameNanos;
      private final long[] slowBucketNanos = new long[FrameWatch.BUCKETS.length];
      private long slowTickNanos;
      private long slowGcNanos;

      Recording(String name) {
         this.name = name;
      }

      public int slowFrameCount() {
         return this.slowFrames;
      }

      public String slowFrames(int atMost) {
         if (this.slowFrames == 0) {
            return "no frame over 10 ms";
         } else {
            List<FrameWatch.Part> parts = new ArrayList<>();
            long named = this.slowTickNanos;

            for (FrameWatch.Bucket bucket : FrameWatch.BUCKETS) {
               named += this.slowBucketNanos[bucket.ordinal()];
               if (this.slowBucketNanos[bucket.ordinal()] >= 1000000L) {
                  parts.add(new FrameWatch.Part(bucket.label, this.slowBucketNanos[bucket.ordinal()], 0));
               }
            }

            if (this.slowTickNanos >= 1000000L) {
               parts.add(new FrameWatch.Part("client ticks", this.slowTickNanos, 0));
            }

            parts.add(new FrameWatch.Part("other", Math.max(0L, this.slowFrameNanos - named), 0));
            parts.sort(Comparator.comparingLong(FrameWatch.Part::nanos).reversed());
            StringBuilder text = new StringBuilder(
               String.format(Locale.ROOT, "%d frames over %d ms (%.0f ms): ", this.slowFrames, 10L, this.slowFrameNanos / 1000000.0)
            );

            for (int i = 0; i < Math.min(atMost, parts.size()); i++) {
               text.append(i == 0 ? "" : ", ").append(parts.get(i).label()).append(' ').append(Math.round(parts.get(i).nanos() / 1000000.0));
            }

            return text.append(String.format(Locale.ROOT, " ms; GC %.0f ms", this.slowGcNanos / 1000000.0)).toString();
         }
      }

      void slowFrame(long nanos, long[] frameBuckets, long tickNanos, long gcNanos) {
         this.slowFrames++;
         this.slowFrameNanos += nanos;

         for (int i = 0; i < frameBuckets.length; i++) {
            this.slowBucketNanos[i] = this.slowBucketNanos[i] + frameBuckets[i];
         }

         this.slowTickNanos += tickNanos;
         this.slowGcNanos += gcNanos;
      }

      public double gcMillis() {
         return this.gcNanos / 1000000.0;
      }

      public double bucketMillis(FrameWatch.Bucket bucket) {
         return this.bucketNanos[bucket.ordinal()] / 1000000.0;
      }

      public double applyPartMillis(FrameWatch.ApplyPart part) {
         return this.applyPartNanos[part.ordinal()] / 1000000.0;
      }

      public int frameCount() {
         return this.frameCount;
      }

      public double frameMillis() {
         return this.frameNanos / 1000000.0;
      }

      public String applyParts() {
         return FrameWatch.partsText(this.applyPartNanos, 1000000L);
      }

      public String bucketsLargestFirst(int atMost) {
         List<FrameWatch.Part> parts = new ArrayList<>();

         for (FrameWatch.Bucket bucket : FrameWatch.BUCKETS) {
            if (this.bucketNanos[bucket.ordinal()] >= 1000000L) {
               parts.add(new FrameWatch.Part(bucket.label, this.bucketNanos[bucket.ordinal()], 0));
            }
         }

         parts.sort(Comparator.comparingLong(FrameWatch.Part::nanos).reversed());
         StringBuilder text = new StringBuilder();

         for (int i = 0; i < Math.min(atMost, parts.size()); i++) {
            text.append(i == 0 ? "" : ", ").append(parts.get(i).label()).append(' ').append(Math.round(parts.get(i).nanos() / 1000000.0));
         }

         return text.isEmpty() ? "none" : text.append(" ms").toString();
      }

      public String name() {
         return this.name;
      }

      public FrameStats inGame() {
         return this.inGame;
      }

      public FrameStats loading() {
         return this.loading;
      }

      public FrameStats viewRebuilds() {
         return this.viewRebuilds;
      }

      public FrameStats tickGaps() {
         return this.tickGaps;
      }

      public FrameStats ticks() {
         return this.ticks;
      }

      public List<FrameWatch.Hitch> worst() {
         return this.worst;
      }

      public int columns() {
         return this.columns;
      }

      public int dropped() {
         return this.dropped;
      }

      public int maxWaiting() {
         return this.maxWaiting;
      }

      public int playerColumnWaits() {
         return this.playerColumnWaits;
      }

      public double applyMillis() {
         return this.applyNanos / 1000000.0;
      }

      public double lightMillis() {
         return this.lightNanos / 1000000.0;
      }

      void frame(long nanos, boolean loadingScreen, boolean viewRebuild, String causes) {
         (loadingScreen ? this.loading : (viewRebuild ? this.viewRebuilds : this.inGame)).add(nanos);
         if (nanos > 10000000L) {
            this.worst.add(new FrameWatch.Hitch(nanos / 1000000.0, loadingScreen, viewRebuild, causes));
            this.worst.sort(Comparator.comparingDouble(FrameWatch.Hitch::frameMillis).reversed());
            if (this.worst.size() > 6) {
               this.worst.removeLast();
            }
         }
      }
   }
}
