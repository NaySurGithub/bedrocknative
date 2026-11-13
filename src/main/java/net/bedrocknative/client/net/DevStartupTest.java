package net.bedrocknative.client.net;

import com.sun.management.HotSpotDiagnosticMXBean;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javax.management.ObjectName;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.content.AtlasReload;
import net.bedrocknative.client.content.BedrockVanillaTextures;
import net.bedrocknative.client.perf.FrameClock;
import net.bedrocknative.perf.PhaseClock;
import net.bedrocknative.perf.StartupClock;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.StartTick;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerData.ServerPackStatus;
import net.minecraft.client.multiplayer.ServerData.Type;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.jspecify.annotations.Nullable;

public final class DevStartupTest {
   private static final long LOBBY_SHOWN_WITHIN_MS = 500L;
   private final Minecraft minecraft;
   private final String join = System.getProperty("bedrocknative.startupJoin", "127.0.0.1");
   private final boolean lowEnd = Boolean.getBoolean("bedrocknative.lowEnd");
   private final List<String> table = new ArrayList<>();
   private int passed;
   private int failed;
   @Nullable
   private Integer renderDistanceBefore;

   private DevStartupTest(Minecraft minecraft) {
      this.minecraft = minecraft;
   }

   public static void start() {
      Thread.ofVirtual().name("Dev startup test").start(() -> {
         DevStartupTest test = new DevStartupTest(awaitInstance());

         try {
            test.run();
         } catch (Throwable var5) {
            BedrockNative.LOGGER.error("Dev startup test: FAIL crashed", var5);
            test.failed++;
         } finally {
            test.finish();
         }
      });
   }

   private static Minecraft awaitInstance() {
      Minecraft instance;
      while ((instance = Minecraft.getInstance()) == null || instance.getWindow() == null) {
         sleep(200L);
      }

      return instance;
   }

   private void run() {
      this.waitFor(
         "the title screen",
         () -> this.onClient(() -> this.minecraft.isGameLoadFinished() && this.minecraft.gui.overlay() == null && this.minecraft.gui.screen() != null),
         180000L
      );
      ClientTickEvents.START_CLIENT_TICK.register((StartTick)client -> DevLoadWatch.tick(false));
      sleep(2000L);
      long titleMillis = StartupClock.titleMillis();
      this.check("the start-up line was logged at the first title screen", titleMillis >= 0L);
      Runtime runtime = Runtime.getRuntime();
      log(
         "profile: {} processors seen by the JVM, max heap {} MB, render distance {}{}; join phases {}",
         runtime.availableProcessors(),
         runtime.maxMemory() >> 20,
         this.onClient(() -> (Integer)this.minecraft.options.renderDistance().get()),
         this.lowEnd ? " (low-end profile: set to 8 for the join)" : "",
         ClientBedrockSession.JOIN_AHEAD ? "overlapped" : "one after the other (-PjoinAhead=off)"
      );
      if (this.lowEnd) {
         this.renderDistanceBefore = this.onClient(() -> {
            int before = (Integer)this.minecraft.options.renderDistance().get();
            this.minecraft.options.renderDistance().set(8);
            return before;
         });
      }

      this.stage(
         "start",
         String.format(
            Locale.ROOT,
            "title screen %d ms after the JVM started; mod init main %d ms, client %d ms; our init steps %d ms (%s); background %s; heap %d MB after GC",
            titleMillis,
            StartupClock.mainInitMillis(),
            StartupClock.clientInitMillis(),
            StartupClock.stepMillis(),
            StartupClock.steps(),
            StartupClock.backgroundLoads(),
            this.usedMbAfterGc()
         )
      );
      String[] atTitle = histogram("title", 10, 12);
      log("heap histogram at the title (live objects, top classes by bytes): {}", atTitle[0]);
      log("heap histogram at the title, the mod's own classes by bytes: {}", atTitle[1]);

      for (String turn : List.of("first", "warm")) {
         this.stage("menu Play " + turn, this.open("Play screen", () -> new JoinMultiplayerScreen(this.minecraft.gui.screen())));
         this.stage("menu Settings " + turn, this.open("Settings", () -> new OptionsScreen(this.minecraft.gui.screen(), this.minecraft.options)));
      }

      int reloads = AtlasReload.runs();
      PhaseClock first = this.connect("connect 1");
      if (first == null) {
         log("the join failed: the in-game stages, the leaves and the rejoin are not run");
      } else {
         this.check(
            "connect 1: the atlases came by the targeted reload (no full reload, no Mojang overlay)",
            first.has("atlases reloaded") && AtlasReload.lastTimings() != null && !AtlasReload.lastTimings().fallback() && AtlasReload.runs() >= reloads + 1
         );
         sleep(3000L);

         for (String turn : List.of("first", "warm")) {
            this.stage("game pause " + turn, this.open("Pause menu", () -> new PauseScreen(true)));
            this.stage("game inventory " + turn, this.open("Inventory", () -> new InventoryScreen(this.minecraft.player)));
            this.stage("game chat " + turn, this.open("Chat", () -> new ChatScreen("", false)));
         }

         reloads = AtlasReload.runs();
         PhaseClock leave = this.leave("leave 1");
         this.check(
            "leave 1: the screen after the session was drawn within 500 ms", leave != null && leave.millis("shown") >= 0L && leave.millis("shown") <= 500L
         );
         this.check(
            "leave 1: nothing was reloaded (the Bedrock textures wait in the atlases for a Java world)",
            BedrockVanillaTextures.restorePending() && AtlasReload.runs() == reloads && this.onClient(() -> this.minecraft.gui.overlay() == null)
         );
         histogram("leave1", 0, 0);
         PhaseClock second = this.connect("connect 2 (same packs)");
         this.check("connect 2: the same packs reloaded nothing", second != null && second.has("textures unchanged") && AtlasReload.runs() == reloads);
         sleep(2000L);
         PhaseClock leave2 = this.leave("leave 2");
         this.check(
            "leave 2: the screen after the session was drawn within 500 ms", leave2 != null && leave2.millis("shown") >= 0L && leave2.millis("shown") <= 500L
         );
         this.check("leave 2: nothing was reloaded", AtlasReload.runs() == reloads);
         String[] histogram = histogram("leave2", 14, 12);
         log("heap histogram after leave 2 (live objects, top classes by bytes): {}", histogram[0]);
         log("heap histogram after leave 2, the mod's own classes by bytes: {}", histogram[1]);
         String dump = System.getProperty("bedrocknative.startupHeapDump", "");
         if (!dump.isBlank()) {
            try {
               ManagementFactory.newPlatformMXBeanProxy(
                     ManagementFactory.getPlatformMBeanServer(), "com.sun.management:type=HotSpotDiagnostic", HotSpotDiagnosticMXBean.class
                  )
                  .dumpHeap(dump, true);
               log("heap dumped to {}", dump);
            } catch (RuntimeException | IOException var13) {
               log("couldn't dump the heap: {}", var13.toString());
            }
         }
      }
   }

   private static String[] histogram(String label, int count, int ours) {
      try {
         ObjectName bean = new ObjectName("com.sun.management:type=DiagnosticCommand");
         String output = (String)ManagementFactory.getPlatformMBeanServer()
            .invoke(bean, "gcClassHistogram", new Object[]{new String[0]}, new String[]{String[].class.getName()});

         try {
            Files.writeString(Minecraft.getInstance().gameDirectory.toPath().resolve("startup-histogram-" + label + ".txt"), output);
         } catch (IOException var14) {
            log("couldn't keep the histogram {}: {}", label, var14.toString());
         }

         List<String> lines = List.of(output.split("\\R"));
         StringBuilder all = new StringBuilder();
         StringBuilder mod = new StringBuilder();
         int taken = 0;
         int takenOurs = 0;

         for (String line : lines) {
            String[] columns = line.trim().split("\\s+");
            if (columns.length >= 4 && columns[0].endsWith(":")) {
               String entry = (Long.parseLong(columns[2]) >> 10) + " KB " + columns[3] + " x" + columns[1];
               if (taken < count) {
                  all.append(taken > 0 ? ", " : "").append(entry);
                  taken++;
               }

               if (takenOurs < ours && columns[3].startsWith("net.bedrocknative.")) {
                  mod.append(takenOurs > 0 ? ", " : "").append(entry);
                  takenOurs++;
               }

               if (taken >= count && takenOurs >= ours) {
                  break;
               }
            }
         }

         String unavailable = "unavailable (" + String.join(" / ", lines.subList(0, Math.min(2, lines.size()))) + ")";
         return new String[]{all.isEmpty() ? unavailable : all.toString(), mod.isEmpty() ? unavailable : mod.toString()};
      } catch (Exception var15) {
         return new String[]{"unavailable (" + var15 + ")", "unavailable"};
      }
   }

   @Nullable
   private PhaseClock connect(String name) {
      int joins = SessionTimeline.joins();
      DevLoadWatch.reset();
      this.run(() -> {
         ServerData server = new ServerData("BN startup test", this.join, Type.OTHER);
         server.setResourcePackStatus(ServerPackStatus.ENABLED);
         ConnectScreen.startConnecting(this.minecraft.gui.screen(), this.minecraft, ServerAddress.parseString(this.join), server, false, null);
      });
      this.waitFor(name + ": the join's loading screen to close", () -> SessionTimeline.joins() > joins, 120000L);
      PhaseClock clock = SessionTimeline.lastJoin();
      if (SessionTimeline.joins() != joins && clock != null) {
         sleep(1000L);
         AtlasReload.Timings reload = AtlasReload.lastTimings();
         BedrockVanillaTextures.Timings textures = BedrockVanillaTextures.timings();
         this.stage(
            name,
            clock.line()
               + String.format(
                  Locale.ROOT,
                  "; textures prepared %d ms, atlases %s; heap %d MB after GC",
                  textures.prepareMillis(),
                  textures.reloaded() && reload != null
                     ? reload.totalMillis() + " ms by the targeted reload (" + reload.clientMillis() + " ms on the client thread)"
                     : "unchanged, no reload",
                  this.usedMbAfterGc()
               )
         );
         return clock;
      } else {
         this.check(name + ": joined " + this.join, false);
         return null;
      }
   }

   @Nullable
   private PhaseClock leave(String name) {
      int leaves = SessionTimeline.leaves();
      DevLoadWatch.reset();
      this.run(() -> this.minecraft.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE));
      this.waitFor(name + ": the screen after the session to be drawn", () -> SessionTimeline.leaves() > leaves, 60000L);
      PhaseClock clock = SessionTimeline.lastLeave();
      if (SessionTimeline.leaves() != leaves && clock != null) {
         sleep(1500L);
         this.stage(
            name,
            clock.line()
               + String.format(
                  Locale.ROOT,
                  "; screen %s; textures %s; heap %d MB after GC",
                  this.onClient(() -> name(this.minecraft.gui.screen())),
                  BedrockVanillaTextures.restorePending() ? "kept in the atlases (no reload)" : "reloaded",
                  this.usedMbAfterGc()
               )
         );
         return clock;
      } else {
         this.check(name + ": left the server", false);
         return null;
      }
   }

   private String open(String what, Supplier<Screen> screen) {
      DevLoadWatch.reset();
      CompletableFuture<Long> shownAt = new CompletableFuture<>();
      long[] started = new long[2];
      this.run(() -> {
         started[0] = System.nanoTime();
         started[1] = FrameClock.lastStartNanos();
         this.minecraft.gui.setScreen(screen.get());
         FrameClock.afterNextFrame(() -> shownAt.complete(System.nanoTime()));
      });
      this.waitFor(what + " to be drawn", shownAt::isDone, 20000L);
      long shown = shownAt.getNow(System.nanoTime());
      long shownMillis = (shown - started[0]) / 1000000L;
      double frameMillis = (shown - started[1]) / 1000000.0;
      String drawn = this.onClient(() -> name(this.minecraft.gui.screen()));
      sleep(600L);
      this.run(() -> {
         Screen open = this.minecraft.gui.screen();
         if (open != null) {
            open.onClose();
         }
      });
      sleep(600L);
      return String.format(Locale.ROOT, "%s drawn as %s %d ms after it was set (its frame %.0f ms)", what, drawn, shownMillis, frameMillis);
   }

   private void stage(String name, String what) {
      String line = String.format(
         Locale.ROOT,
         "%s: %s; longest tick gap %.0f ms%s",
         name,
         what,
         DevLoadWatch.maxTickMs(),
         DevLoadWatch.overloaded() ? " (LOADED PC: the times above say little)" : ""
      );
      this.table.add(line);
      log("TABLE {}", line);
   }

   private long usedMbAfterGc() {
      System.gc();
      sleep(400L);
      System.gc();
      sleep(200L);
      Runtime runtime = Runtime.getRuntime();
      return runtime.totalMemory() - runtime.freeMemory() >> 20;
   }

   private void finish() {
      if (this.renderDistanceBefore != null) {
         int before = this.renderDistanceBefore;
         this.run(() -> this.minecraft.options.renderDistance().set(before));
      }

      log("SUMMARY {} passed, {} failed ({} stages)", this.passed, this.failed, this.table.size());
      log("done");
      sleep(500L);
      this.run(this.minecraft::stop);
   }

   private static String name(@Nullable Screen screen) {
      return screen == null ? "nothing" : screen.getClass().getSimpleName();
   }

   private void check(String what, boolean ok) {
      if (ok) {
         this.passed++;
      } else {
         this.failed++;
      }

      log("{} {}", ok ? "PASS" : "FAIL", what);
   }

   private void waitFor(String what, BooleanSupplier done, long millis) {
      long started = System.currentTimeMillis();

      while (!done.getAsBoolean()) {
         if (System.currentTimeMillis() - started > millis) {
            log("FAIL waited {} s for {} ({})", millis / 1000L, what, this.onClient(() -> name(this.minecraft.gui.screen())));
            this.failed++;
            return;
         }

         sleep(50L);
      }
   }

   private <T> T onClient(Supplier<T> task) {
      return this.minecraft.isSameThread() ? task.get() : CompletableFuture.supplyAsync(task, this.minecraft::execute).join();
   }

   private void run(Runnable task) {
      this.onClient(() -> {
         task.run();
         return null;
      });
   }

   private static void sleep(long millis) {
      try {
         Thread.sleep(millis);
      } catch (InterruptedException var3) {
         Thread.currentThread().interrupt();
      }
   }

   private static void log(String message, Object... args) {
      BedrockNative.LOGGER.info("Dev startup test: " + message, args);
   }
}
