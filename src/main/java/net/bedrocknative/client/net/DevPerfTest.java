package net.bedrocknative.client.net;

import com.sun.management.GarbageCollectionNotificationInfo;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javax.management.ListenerNotFoundException;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import javax.management.openmbean.CompositeData;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.PerfProbe;
import net.bedrocknative.client.content.BedrockVanillaTextures;
import net.bedrocknative.client.perf.FrameTimes;
import net.bedrocknative.perf.FrameWatch;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.StartTick;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public final class DevPerfTest {
   private static final int MAX_SAMPLES = 65536;
   private static final String[] CROWD = new String[]{"cow", "pig", "sheep", "chicken", "villager", "iron_golem", "wolf", "cat", "horse", "armor_stand"};
   private static final long[] FRAME_NANOS = new long[65536];
   private static final long[] TICK_NANOS = new long[65536];
   private static volatile boolean recording;
   private static int frames;
   private static long lastFrame;
   private static int ticks;
   private static long tickStarted;
   private static boolean ticksHooked;
   private static volatile long joinMillis = -1L;
   private final Minecraft minecraft;
   private final List<Long> gcPauses = new ArrayList<>();
   private final Map<String, Integer> gcByName = new TreeMap<>();

   DevPerfTest(Minecraft minecraft) {
      this.minecraft = minecraft;
   }

   public static void frame() {
      DevLoadWatch.frame();
      long now = System.nanoTime();
      if (recording && lastFrame != 0L && frames < 65536) {
         FRAME_NANOS[frames++] = now - lastFrame;
      }

      lastFrame = now;
   }

   static void joined(long millis) {
      joinMillis = millis;
   }

   void run() {
      try {
         this.test();
      } catch (RuntimeException var2) {
         BedrockNative.LOGGER.error("Dev perf test failed", var2);
      }

      BedrockNative.LOGGER.info("Dev perf test: done");
   }

   private void test() {
      int seconds = Integer.getInteger("bedrocknative.perfSeconds", 60);
      int crowd = Integer.getInteger("bedrocknative.perfCrowd", 0);
      long waited = System.currentTimeMillis();

      while (joinMillis < 0L && System.currentTimeMillis() - waited < 120000L) {
         sleep(500L);
      }

      BedrockVanillaTextures.Timings textures = BedrockVanillaTextures.timings();
      if (crowd > 0) {
         this.summon(crowd);
      }

      this.joinCommand();
      sleep(10000L);
      if (!ticksHooked) {
         ticksHooked = true;
         ClientTickEvents.START_CLIENT_TICK.register((StartTick)client -> tickStarted = System.nanoTime());
         ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> {
            if (recording && tickStarted != 0L && ticks < 65536) {
               TICK_NANOS[ticks++] = System.nanoTime() - tickStarted;
            }
         });
      }

      List<Runnable> unhook = this.watchGc();
      this.onClient(() -> {
         frames = 0;
         ticks = 0;
         lastFrame = 0L;
         PerfProbe.reset();
         recording = true;
         return null;
      });
      long started = System.nanoTime();
      sleep(seconds * 1000L);
      long elapsed = this.onClient(() -> {
         recording = false;
         return System.nanoTime() - started;
      });
      unhook.forEach(Runnable::run);
      this.report(elapsed, textures, crowd);
      this.minecraft.execute(() -> Screenshot.grab(this.minecraft, false));
      if (crowd > 0) {
         sleep(1000L);
         this.command("kill @e[tag=bn_perf]");
      }
   }

   private void report(long elapsed, BedrockVanillaTextures.Timings textures, int crowd) {
      long[] frameTimes = Arrays.copyOf(FRAME_NANOS, frames);
      long[] tickTimes = Arrays.copyOf(TICK_NANOS, ticks);
      double seconds = elapsed / 1.0E9;
      BedrockNative.LOGGER
         .info(
            "Dev perf test: measured {} s{}; join: loading screen closed after {} ms, textures prepared in {} ms, atlases rebuilt in {} ms",
            new Object[]{Math.round(seconds), crowd > 0 ? " with a crowd of " + crowd : "", joinMillis, textures.prepareMillis(), textures.reloadMillis()}
         );
      BedrockNative.LOGGER
         .info(
            String.format(
               Locale.ROOT,
               "Dev perf test: frames %d (%.0f fps), frame ms p50 %.1f, p95 %.1f, p99 %.1f, max %.1f; frames over 50 ms: %d",
               frameTimes.length,
               frameTimes.length / seconds,
               percentile(frameTimes, 50),
               percentile(frameTimes, 95),
               percentile(frameTimes, 99),
               percentile(frameTimes, 100),
               Arrays.stream(frameTimes).filter(nanos -> nanos > 50000000L).count()
            )
         );
      BedrockNative.LOGGER
         .info(
            String.format(
               Locale.ROOT,
               "Dev perf test: client ticks %d, tick ms p50 %.2f, p95 %.2f, p99 %.2f, max %.2f",
               tickTimes.length,
               percentile(tickTimes, 50),
               percentile(tickTimes, 95),
               percentile(tickTimes, 99),
               percentile(tickTimes, 100)
            )
         );
      long[] pauses;
      synchronized (this.gcPauses) {
         pauses = this.gcPauses.stream().mapToLong(Long::longValue).toArray();
      }

      BedrockNative.LOGGER
         .info(
            "Dev perf test: GC pauses {}, total {} ms, max {} ms ({})",
            new Object[]{pauses.length, Arrays.stream(pauses).sum(), Arrays.stream(pauses).max().orElse(0L), this.gcByName}
         );
      int frameCount = Math.max(1, frameTimes.length);
      BedrockNative.LOGGER
         .info(
            String.format(
               Locale.ROOT,
               "Dev perf test: ours per frame (render thread): models %.3f ms (%d evaluations), HUD %.3f ms, screens %.3f ms, chunk apply %.3f ms (%d)",
               ms(PerfProbe.Part.MODELS) / frameCount,
               PerfProbe.calls(PerfProbe.Part.MODELS),
               ms(PerfProbe.Part.HUD) / frameCount,
               ms(PerfProbe.Part.SCREENS) / frameCount,
               ms(PerfProbe.Part.CHUNKS_APPLY) / frameCount,
               PerfProbe.calls(PerfProbe.Part.CHUNKS_APPLY)
            )
         );
      BedrockNative.LOGGER
         .info(
            String.format(
               Locale.ROOT,
               "Dev perf test: network thread: packets %.0f ms in all (%d, %.1f%% of the time), chunks read %.0f ms (%d packets); decode workers %.0f ms (%d jobs)",
               ms(PerfProbe.Part.PACKETS),
               PerfProbe.calls(PerfProbe.Part.PACKETS),
               ms(PerfProbe.Part.PACKETS) / (elapsed / 1000000.0) * 100.0,
               ms(PerfProbe.Part.CHUNKS_NETWORK),
               PerfProbe.calls(PerfProbe.Part.CHUNKS_NETWORK),
               ms(PerfProbe.Part.CHUNKS_DECODE),
               PerfProbe.calls(PerfProbe.Part.CHUNKS_DECODE)
            )
         );
      String javaParts = this.onClient(
         () -> {
            FrameWatch watch = FrameTimes.watch();
            return String.format(
               Locale.ROOT,
               "entities %d drawn in %.2f ms, block entities %d drawn in %.2f ms, feature prep %.2f ms",
               watch.lastFrameCount(FrameWatch.Bucket.ENTITY_RENDER),
               watch.lastFrameNanos(FrameWatch.Bucket.ENTITY_RENDER) / 1000000.0,
               watch.lastFrameCount(FrameWatch.Bucket.BLOCK_ENTITY_RENDER),
               watch.lastFrameNanos(FrameWatch.Bucket.BLOCK_ENTITY_RENDER) / 1000000.0,
               watch.lastFrameNanos(FrameWatch.Bucket.FEATURE_PREP) / 1000000.0
            );
         }
      );
      BedrockNative.LOGGER.info("Dev perf test: Java render parts, last frame: {}", javaParts);
      String scene = this.onClient(
         () -> {
            int entities = 0;
            int players = 0;

            for (Entity entity : this.minecraft.level.entitiesForRendering()) {
               entities++;
               if (entity instanceof Player) {
                  players++;
               }
            }

            return String.format(
               Locale.ROOT,
               "%d entities (%d players), %d chunks, render distance %d, fps limit %d, vsync %s, %s",
               entities,
               players,
               this.minecraft.level.getChunkSource().getLoadedChunksCount(),
               this.minecraft.options.getEffectiveRenderDistance(),
               this.minecraft.options.framerateLimit().get(),
               this.minecraft.options.enableVsync().get(),
               this.minecraft.getWindow().getWidth() + "x" + this.minecraft.getWindow().getHeight()
            );
         }
      );
      BedrockNative.LOGGER.info("Dev perf test: scene: {}", scene);
   }

   private static double ms(PerfProbe.Part part) {
      return PerfProbe.nanos(part) / 1000000.0;
   }

   private static double percentile(long[] nanos, int percent) {
      if (nanos.length == 0) {
         return 0.0;
      } else {
         long[] sorted = (long[])nanos.clone();
         Arrays.sort(sorted);
         int rank = (int)Math.ceil(percent / 100.0 * sorted.length);
         return sorted[Math.clamp((long)(rank - 1), 0, sorted.length - 1)] / 1000000.0;
      }
   }

   private List<Runnable> watchGc() {
      List<Runnable> unhook = new ArrayList<>();

      for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
         if (bean instanceof NotificationEmitter emitter && !bean.getName().contains("Cycles")) {
            NotificationListener listener = (notification, handback) -> {
               if (recording && "com.sun.management.gc.notification".equals(notification.getType())) {
                  GarbageCollectionNotificationInfo info = GarbageCollectionNotificationInfo.from((CompositeData)notification.getUserData());
                  synchronized (this.gcPauses) {
                     this.gcPauses.add(info.getGcInfo().getDuration());
                     this.gcByName.merge(info.getGcName(), 1, Integer::sum);
                  }
               }
            };
            emitter.addNotificationListener(listener, null, null);
            unhook.add(() -> {
               try {
                  emitter.removeNotificationListener(listener);
               } catch (ListenerNotFoundException var3x) {
               }
            });
         }
      }

      return unhook;
   }

   private void summon(int count) {
      this.command("time set noon");
      this.command("weather clear");

      for (int i = 0; i < count; i++) {
         double angle = i * 2.399963;
         int radius = 3 + i % 7;
         int x = (int)Math.round(Math.cos(angle) * radius);
         int z = (int)Math.round(Math.sin(angle) * radius);
         String type = CROWD[i % CROWD.length];
         this.command(
            type.equals("armor_stand")
               ? String.format(Locale.ROOT, "summon armor_stand ~%d ~ ~%d 0 0 minecraft:entity_spawned \"§6Top %d §7- §a%d\"", x, z, i, 1000 - i)
               : String.format(Locale.ROOT, "summon %s ~%d ~ ~%d", type, x, z)
         );
      }

      sleep(1000L);
      this.command("tag @e[type=!player,r=12] add bn_perf");
   }

   private void joinCommand() {
      String configured = System.getProperty("bedrocknative.devJoinCommand", "");
      if (!configured.isEmpty()) {
         String sent = configured.startsWith("/") ? configured.substring(1) : configured;
         if (!sent.matches("[a-z]+")) {
            BedrockNative.LOGGER.info("Dev perf test: not sending '{}': only a single plain word is allowed", configured);
         } else {
            for (int i = 0; i < 60 && this.onClient(() -> this.minecraft.gui.screen() != null || this.minecraft.player == null); i++) {
               sleep(1000L);
            }

            BedrockNative.LOGGER.info("Dev perf test: sending the allowed command /{} once", sent);
            this.command(sent);
            sleep(Long.getLong("bedrocknative.lookCommandWait", 8000L));
         }
      }
   }

   private void command(String command) {
      this.minecraft.execute(() -> {
         if (this.minecraft.getConnection() != null) {
            this.minecraft.getConnection().sendCommand(command);
         }
      });
      sleep(120L);
   }

   private <T> T onClient(Supplier<T> supplier) {
      CompletableFuture<T> future = new CompletableFuture<>();
      this.minecraft.execute(() -> {
         try {
            future.complete(supplier.get());
         } catch (RuntimeException var3) {
            future.completeExceptionally(var3);
         }
      });

      try {
         return future.get(30L, TimeUnit.SECONDS);
      } catch (Exception var4) {
         throw new IllegalStateException(var4);
      }
   }

   private static void sleep(long millis) {
      try {
         Thread.sleep(millis);
      } catch (InterruptedException var3) {
         Thread.currentThread().interrupt();
      }
   }
}
