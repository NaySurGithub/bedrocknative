package net.bedrocknative.client.net;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import jdk.jfr.FlightRecorder;
import jdk.jfr.Recording;
import jdk.jfr.RecordingState;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.perf.FrameClock;
import net.bedrocknative.client.perf.FrameTimes;
import net.bedrocknative.client.perf.StallSampler;
import net.bedrocknative.client.ui.JsonUi;
import net.bedrocknative.client.ui.JsonUiHost;
import net.bedrocknative.client.ui.JsonUiHud;
import net.bedrocknative.perf.FrameStats;
import net.bedrocknative.perf.FrameWatch;
import net.bedrocknative.perf.RenderSwitches;
import net.bedrocknative.ui.UiScreenInstance;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.StartTick;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

final class DevPerfRenderTest {
   private static final int WINDOW_MS = Integer.getInteger("bedrocknative.perfRenderWindowMs", 3000);
   private static final int WINDOWS = Integer.getInteger("bedrocknative.perfRenderWindows", 8);
   private static final int CHAT_LINES = 18;
   private static final String[] FIGHTERS = new String[]{"zombie", "skeleton", "spider", "husk"};
   private static final String[] CROWD = new String[]{"cow", "pig", "sheep", "chicken", "villager", "iron_golem", "wolf", "cat", "horse", "armor_stand"};
   private final Minecraft minecraft;
   private final List<String> switches;
   private final List<String> summary = new ArrayList<>();
   private volatile Consumer<Minecraft> perTick;
   private int chatSerial;

   DevPerfRenderTest(Minecraft minecraft) {
      this.minecraft = minecraft;
      String listed = System.getProperty("bedrocknative.perfRenderAb", "");
      this.switches = listed.isBlank() ? List.copyOf(RenderSwitches.all().keySet()) : RenderSwitches.known(Arrays.asList(listed.split(",")));
   }

   void run() {
      String test = System.getProperty("bedrocknative.devTest", "perf-render");
      String only = test.startsWith("perf-render-") ? test.substring("perf-render-".length()) : "";
      Map<String, Boolean> initial = new LinkedHashMap<>();
      RenderSwitches.all().forEach((name, value) -> initial.put(name, value.get().get()));
      DevPerfRenderTest.Options saved = null;

      try {
         this.waitFor(
            () -> this.minecraft.player != null
               && this.minecraft.getConnection() != null
               && this.minecraft.getConnection().hasClientLoaded()
               && this.minecraft.gui.screen() == null,
            90000L
         );
         saved = this.onClient(
            () -> new DevPerfRenderTest.Options(
               (Integer)this.minecraft.options.renderDistance().get(),
               (CloudStatus)this.minecraft.options.cloudStatus().get(),
               (Boolean)this.minecraft.options.bobView().get()
            )
         );
         ClientTickEvents.START_CLIENT_TICK.register((StartTick)client -> {
            Consumer<Minecraft> step = this.perTick;
            if (step != null && client.player != null) {
               step.accept(client);
            }
         });
         log("switches A/B: {} (A all on, B these off); windows of {} ms, {} a scenario", this.switches, WINDOW_MS, WINDOWS);
         this.command("gamemode creative");
         this.command("time set noon");
         this.command("weather clear");
         this.command("gamerule dodaylightcycle false");
         Vec3 home = this.onClient(() -> this.minecraft.player.position());
         this.firstScreens();
         if (only.isEmpty() || only.equals("chat")) {
            this.chat(home);
         }

         if (only.isEmpty() || only.equals("fight")) {
            this.fight(home);
         }

         if (only.isEmpty() || only.equals("crowd")) {
            this.crowd(home);
         }

         this.command(String.format(Locale.ROOT, "tp @s %.2f %.2f %.2f", home.x, home.y, home.z));
      } catch (RuntimeException var10) {
         BedrockNative.LOGGER.error("Dev perf-render test failed", var10);
      } finally {
         this.perTick = null;
         initial.forEach((name, value) -> RenderSwitches.all().get(name).set().accept(value));
         this.command("kill @e[tag=bn_render]");
         this.command("gamerule dodaylightcycle true");
         DevPerfRenderTest.Options restore = saved;
         if (restore != null) {
            this.minecraft.execute(() -> {
               this.minecraft.options.renderDistance().set(restore.renderDistance());
               this.minecraft.options.cloudStatus().set(restore.clouds());
               this.minecraft.options.bobView().set(restore.bob());
               this.minecraft.options.keyLeft.setDown(false);
               this.minecraft.options.keyRight.setDown(false);
            });
         }
      }

      this.summary.forEach(line -> log("summary {}", line));
      dumpFlightRecording();
      log("done");
      BedrockNative.LOGGER.info("Dev perf-render test: done");
   }

   private static void dumpFlightRecording() {
      try {
         for (Recording recording : FlightRecorder.getFlightRecorder().getRecordings()) {
            Path destination = recording.getDestination();
            if (destination != null && recording.getState() == RecordingState.RUNNING) {
               recording.dump(destination);
               log("flight recording written to {}", destination);
            }
         }
      } catch (RuntimeException | IOException var3) {
         log("the flight recording could not be written: {}", var3.toString());
      }
   }

   private void firstScreens() {
      for (String screen : new String[]{"pause", "inventory"}) {
         for (String time : new String[]{"first", "second"}) {
            this.screenOpen(screen, time);
         }
      }
   }

   private void screenOpen(String screen, String time) {
      this.onClient(() -> {
         FrameTimes.watch().startRecording(time + " " + screen);
         StallSampler.start();
         if (screen.equals("pause")) {
            this.minecraft.pauseGame(false);
         } else {
            this.minecraft.gui.setScreen(new InventoryScreen(this.minecraft.player));
         }

         return null;
      });
      sleep(1500L);
      String[] stalls = new String[1];
      FrameWatch.Recording opening = this.onClient(() -> {
         stalls[0] = StallSampler.stop();
         return FrameTimes.watch().stopRecording();
      });
      UiScreenInstance.UpdateCounts[] counts = new UiScreenInstance.UpdateCounts[2];
      this.onClient(() -> {
         counts[0] = this.screenCounts();
         FrameTimes.watch().startRecording(screen + " open");
         return null;
      });
      sleep(WINDOW_MS);
      FrameWatch.Recording open = this.onClient(() -> {
         counts[1] = this.screenCounts();
         return FrameTimes.watch().stopRecording();
      });
      String name = this.onClient(() -> this.minecraft.gui.screen() == null ? "none" : this.minecraft.gui.screen().getClass().getSimpleName());
      if (counts[0] != null && counts[1] != null) {
         log("{} {} open: {}", time, screen, counts[1].since(counts[0]));
      }

      if (time.endsWith("second")) {
         this.pixelCheck(screen + "-" + time.replace(' ', '-'));
         this.windows(screen + "-" + time.replace(' ', '-') + "-open", null, 0, 4, 2000);
      }

      this.onClient(() -> {
         if (screen.equals("inventory")) {
            this.minecraft.player.closeContainer();
         } else {
            this.minecraft.gui.setScreen(null);
         }

         return null;
      });
      FrameWatch.Hitch worst = opening.worst().isEmpty() ? null : opening.worst().getFirst();
      int frames = Math.max(1, open.inGame().count());
      String line = String.format(
         Locale.ROOT,
         "%s %s (%s): opening worst frame %.1f ms (%s); then open: %d frames, p50 %.2f, p99 %.2f ms, JSON UI %.3f ms and Java GUI draw %.3f ms a frame",
         time,
         screen,
         name,
         opening.inGame().maxMillis(),
         worst == null ? "no frame over 10 ms" : worst.causes(),
         open.inGame().count(),
         open.inGame().percentileMillis(50.0),
         open.inGame().percentileMillis(99.0),
         open.bucketMillis(FrameWatch.Bucket.UI) / frames,
         open.bucketMillis(FrameWatch.Bucket.GUI_DRAW) / frames
      );
      this.summary.add(line);
      log("{}", line);
      if (!stalls[0].isEmpty()) {
         log("{} {} opening stall samples: {}", time, screen, stalls[0].length() > 2500 ? stalls[0].substring(0, 2500) : stalls[0]);
      }

      sleep(500L);
   }

   private void chat(Vec3 home) {
      this.fly(true);
      this.command(String.format(Locale.ROOT, "tp @s %.2f 250 %.2f", home.x, home.z));
      sleep(1500L);
      this.fly(true);
      this.onClient(() -> {
         this.minecraft.options.cloudStatus().set(CloudStatus.OFF);
         this.minecraft.options.bobView().set(false);
         LocalPlayer player = this.minecraft.player;
         player.setYRot(0.0F);
         player.setXRot(-90.0F);
         return null;
      });
      sleep(1500L);
      this.fillChat();
      sleep(1000L);
      this.pixelCheck("chat");
      this.windows("chat", this::fillChat, 400);
      this.clearChat();
      this.survivalHud(home);
      this.fly(false);
   }

   private void survivalHud(Vec3 home) {
      Vec3 here = this.onClient(() -> this.minecraft.player.position());
      String below = String.format(Locale.ROOT, "%d %d %d", (int)Math.floor(here.x), (int)Math.floor(here.y) - 1, (int)Math.floor(here.z));
      this.command("setblock " + below + " barrier");
      this.command("gamemode survival");
      this.command("effect @s instant_health 1 10 true");
      this.command("effect @s saturation 2 10 true");
      this.command("xp 30L @s");

      for (String[] armour : new String[][]{{"head", "iron_helmet"}, {"chest", "iron_chestplate"}, {"legs", "golden_leggings"}, {"feet", "diamond_boots"}}) {
         this.command("replaceitem entity @s slot.armor." + armour[0] + " 0 " + armour[1]);
      }

      sleep(2500L);
      this.pixelCheck("survival-hud");
      this.minecraft.execute(() -> this.minecraft.pauseGame(false));
      sleep(1500L);
      this.pixelCheck("pause");
      this.onClient(() -> {
         this.minecraft.gui.setScreen(null);
         return null;
      });
      this.screenOpen("inventory", "survival first");
      this.screenOpen("inventory", "survival second");

      for (String slot : new String[]{"head", "chest", "legs", "feet"}) {
         this.command("replaceitem entity @s slot.armor." + slot + " 0 air");
      }

      this.command("xp -30L @s");
      this.command("gamemode creative");
      this.command("setblock " + below + " air");
      sleep(500L);
   }

   private void fight(Vec3 home) {
      this.command(String.format(Locale.ROOT, "tp @s %.2f %.2f %.2f", home.x, home.y, home.z));
      this.fly(false);
      sleep(2000L);
      this.clearChat();

      for (int i = 0; i < 24; i++) {
         double angle = i * 2.399963;
         int radius = 3 + i % 7;
         this.command(
            String.format(
               Locale.ROOT,
               "summon %s ~%d ~ ~%d",
               FIGHTERS[i % FIGHTERS.length],
               (int)Math.round(Math.cos(angle) * radius),
               (int)Math.round(Math.sin(angle) * radius)
            )
         );
      }

      sleep(800L);
      this.command("tag @e[type=!player,r=14] add bn_render");
      sleep(1500L);
      int[] tick = new int[]{0};
      this.perTick = client -> {
         LocalPlayer player = client.player;
         int t = (int)(tick[0]++);
         boolean left = t / 20 % 2 == 0;
         client.options.keyLeft.setDown(left);
         client.options.keyRight.setDown(!left);
         player.setYRot(player.getYRot() + 7.0F);
         player.setXRot(5.0F);
         if (t % 8 == 0) {
            KeyMapping.click(InputConstants.getKey(client.options.keyAttack.saveString()));
         }

         if (t % 10 == 0) {
            this.chatLine(client);
         }
      };
      this.windows("fight", null, 0);
      this.perTick = null;
      this.onClient(() -> {
         this.minecraft.options.keyLeft.setDown(false);
         this.minecraft.options.keyRight.setDown(false);
         return null;
      });
      this.command("kill @e[tag=bn_render]");
      this.clearChat();
      sleep(1000L);
   }

   private void crowd(Vec3 home) {
      this.command(String.format(Locale.ROOT, "tp @s %.2f %.2f %.2f", home.x, home.y, home.z));
      sleep(1500L);

      for (int i = 0; i < 40; i++) {
         int x = -6 + i % 13;
         int z = 4 + i / 13 * 2;
         String type = CROWD[i % CROWD.length];
         this.command(
            type.equals("armor_stand")
               ? String.format(Locale.ROOT, "summon armor_stand ~%d ~ ~%d 0 0 minecraft:entity_spawned \"§6Top %d §7- §a%d\"", x, z, i, 1000 - i)
               : String.format(Locale.ROOT, "summon %s ~%d ~ ~%d", type, x, z)
         );
      }

      sleep(800L);
      this.command("tag @e[type=!player,r=16] add bn_render");
      this.onClient(() -> {
         this.minecraft.player.setYRot(0.0F);
         this.minecraft.player.setXRot(15.0F);
         return null;
      });
      sleep(3000L);
      this.windows("crowd", null, 0);
      this.command("kill @e[tag=bn_render]");
      sleep(1000L);
   }

   private UiScreenInstance.UpdateCounts screenCounts() {
      return this.minecraft.gui.screen() instanceof JsonUiHost host ? host.ui().instance().updateCounts() : null;
   }

   private static UiScreenInstance.UpdateCounts hudCounts() {
      UiScreenInstance hud = JsonUiHud.instance();
      return hud == null ? null : hud.updateCounts();
   }

   private void windows(String scenario, Runnable before, int settleMs) {
      this.windows(scenario, before, settleMs, WINDOWS, WINDOW_MS);
   }

   private void windows(String scenario, Runnable before, int settleMs, int windows, int windowMs) {
      DevPerfRenderTest.Variant a = new DevPerfRenderTest.Variant("A");
      DevPerfRenderTest.Variant b = new DevPerfRenderTest.Variant("B");

      for (int window = 0; window < windows; window++) {
         boolean fixes = window % 2 == 0;
         this.set(fixes);
         if (before != null) {
            before.run();
         }

         sleep(Math.max(settleMs, 100));
         DevCpuSampler cpu = new DevCpuSampler().start();
         UiScreenInstance.UpdateCounts[] counts = new UiScreenInstance.UpdateCounts[2];
         String[] counted = new String[1];
         this.onClient(() -> {
            counts[0] = this.screenCounts();
            counted[0] = counts[0] != null ? "screen" : "HUD";
            if (counts[0] == null) {
               counts[0] = hudCounts();
            }

            FrameTimes.watch().startRecording(scenario);
            StallSampler.start();
            return null;
         });
         sleep(windowMs);
         String[] stalls = new String[1];
         FrameWatch.Recording recording = this.onClient(() -> {
            stalls[0] = StallSampler.stop();
            counts[1] = counted[0].equals("screen") ? this.screenCounts() : hudCounts();
            return FrameTimes.watch().stopRecording();
         });
         cpu.stop();
         DevPerfRenderTest.Variant variant = fixes ? a : b;
         variant.add(recording, cpu.meanOthers());
         log(
            "{} {} window {}: {}; CPU of other processes {}%; {} {}",
            scenario,
            variant.name,
            window + 1,
            describe(recording),
            Math.round(cpu.meanOthers() * 100.0),
            counted[0],
            counts[0] != null && counts[1] != null ? counts[1].since(counts[0]) : "none"
         );
         if (recording.slowFrameCount() > 0) {
            log("{} {} window {} slow frames: {}", scenario, variant.name, window + 1, recording.slowFrames(8));
         }

         if (!stalls[0].isEmpty()) {
            log("{} {} window {} stall samples: {}", scenario, variant.name, window + 1, stalls[0].length() > 1200 ? stalls[0].substring(0, 1200) : stalls[0]);
         }
      }

      this.set(true);
      String line = String.format(Locale.ROOT, "%s: A (fixes on) %s | B (%s off) %s", scenario, a.describe(), String.join(",", this.switches), b.describe());
      this.summary.add(line);
      log("summary {}", line);
   }

   private void set(boolean fixes) {
      this.onClient(() -> {
         for (String name : this.switches) {
            RenderSwitches.all().get(name).set().accept(fixes);
         }

         return null;
      });
   }

   private static String describe(FrameWatch.Recording recording) {
      FrameStats frames = recording.inGame();
      int count = Math.max(1, frames.count());
      return String.format(
         Locale.ROOT,
         "%d frames, p50 %.2f, p99 %.2f, max %.1f ms, over 10 ms %d; per frame JSON UI %.3f, Java GUI draw %.3f, models %.3f ms; GC %.0f ms",
         frames.count(),
         frames.percentileMillis(50.0),
         frames.percentileMillis(99.0),
         frames.maxMillis(),
         frames.over(10000000L),
         recording.bucketMillis(FrameWatch.Bucket.UI) / count,
         recording.bucketMillis(FrameWatch.Bucket.GUI_DRAW) / count,
         recording.bucketMillis(FrameWatch.Bucket.MODELS) / count,
         recording.gcMillis()
      );
   }

   private void pixelCheck(String scenario) {
      JsonUi.devBackdrop(true);

      try {
         this.pixelRounds(scenario);
      } finally {
         JsonUi.devBackdrop(false);
      }
   }

   private void pixelRounds(String scenario) {
      for (int round = 1; round <= 3; round++) {
         this.set(true);
         NativeImage on = this.frame();
         this.set(false);
         NativeImage off = this.frame();
         this.set(true);
         NativeImage again = this.frame();
         if (on != null && off != null && again != null) {
            try {
               int[] diff = differ(on, off);
               int[] steady = differ(on, again);
               int[] still = differStill(on, again, off);
               log(
                  "{} pixels round {}: switches on against off: {} of {} pixels differ{}; on against on a few frames later: {} differ (the scene's own changes{}); pixels the scene kept still that the switches changed: {}{}",
                  scenario,
                  round,
                  diff[0],
                  on.getWidth() * on.getHeight(),
                  diff[0] == 0 ? "" : String.format(Locale.ROOT, " (box %d,%d to %d,%d)", diff[1], diff[2], diff[3], diff[4]),
                  steady[0],
                  steady[0] == 0 ? "" : String.format(Locale.ROOT, ": box %d,%d to %d,%d", steady[1], steady[2], steady[3], steady[4]),
                  still[0],
                  still[0] == 0 ? "" : String.format(Locale.ROOT, " (box %d,%d to %d,%d)", still[1], still[2], still[3], still[4])
               );
               if (still[0] > 0 || round == 1) {
                  this.save(on, "perf-render-" + scenario + "-on-" + round);
                  this.save(off, "perf-render-" + scenario + "-off-" + round);
               }

               this.summary.add(String.format(Locale.ROOT, "%s pixel check round %d: %d still pixels differ on/off", scenario, round, still[0]));
            } finally {
               on.close();
               off.close();
               again.close();
            }
         } else {
            log("{} pixels round {}: the frame could not be read back", scenario, round);
         }
      }
   }

   private static int[] differStill(NativeImage on, NativeImage again, NativeImage off) {
      if (on.getWidth() == off.getWidth() && on.getHeight() == off.getHeight() && on.getWidth() == again.getWidth() && on.getHeight() == again.getHeight()) {
         int count = 0;
         int minX = Integer.MAX_VALUE;
         int minY = Integer.MAX_VALUE;
         int maxX = -1;
         int maxY = -1;

         for (int y = 0; y < on.getHeight(); y++) {
            for (int x = 0; x < on.getWidth(); x++) {
               int pixel = on.getPixel(x, y);
               if (pixel == again.getPixel(x, y) && pixel != off.getPixel(x, y)) {
                  count++;
                  minX = Math.min(minX, x);
                  minY = Math.min(minY, y);
                  maxX = Math.max(maxX, x);
                  maxY = Math.max(maxY, y);
               }
            }
         }

         return new int[]{count, minX, minY, maxX, maxY};
      } else {
         return new int[]{Integer.MAX_VALUE, 0, 0, 0, 0};
      }
   }

   private static int[] differ(NativeImage a, NativeImage b) {
      if (a.getWidth() == b.getWidth() && a.getHeight() == b.getHeight()) {
         int count = 0;
         int minX = Integer.MAX_VALUE;
         int minY = Integer.MAX_VALUE;
         int maxX = -1;
         int maxY = -1;

         for (int y = 0; y < a.getHeight(); y++) {
            for (int x = 0; x < a.getWidth(); x++) {
               if (a.getPixel(x, y) != b.getPixel(x, y)) {
                  count++;
                  minX = Math.min(minX, x);
                  minY = Math.min(minY, y);
                  maxX = Math.max(maxX, x);
                  maxY = Math.max(maxY, y);
               }
            }
         }

         return new int[]{count, minX, minY, maxX, maxY};
      } else {
         return new int[]{Integer.MAX_VALUE, 0, 0, 0, 0};
      }
   }

   private NativeImage frame() {
      long start = FrameClock.frames();
      this.waitFor(() -> FrameClock.frames() >= start + 3L, 5000L);
      CompletableFuture<NativeImage> image = new CompletableFuture<>();
      this.minecraft.execute(() -> Screenshot.takeScreenshot(this.minecraft.gameRenderer.mainRenderTarget(), image::complete));

      try {
         return image.get(10L, TimeUnit.SECONDS);
      } catch (Exception var5) {
         return null;
      }
   }

   private void save(NativeImage image, String name) {
      try {
         File folder = new File(this.minecraft.gameDirectory, "screenshots");
         folder.mkdirs();
         image.writeToFile(new File(folder, name + ".png"));
      } catch (IOException var4) {
         log("could not save {}: {}", name, var4.toString());
      }
   }

   private void fillChat() {
      this.onClient(() -> {
         this.minecraft.gui.hud.getChat().clearMessages(false);

         for (int i = 0; i < 18; i++) {
            this.chatLine(this.minecraft);
         }

         return null;
      });
   }

   private void clearChat() {
      this.onClient(() -> {
         this.minecraft.gui.hud.getChat().clearMessages(false);
         return null;
      });
   }

   private void chatLine(Minecraft client) {
      int n = this.chatSerial++;

      String line = switch (n % 3) {
         case 0 -> "§6[Admin] §fPlayer" + n % 7 + "§7: welcome to the server, the event starts in " + n % 60 + " seconds";
         case 1 -> "§cZombie §7was slain by §aDevUser §7using §b[Diamond Sword] §8(" + n + ")";
         default -> "§eYou took §c" + (n % 9 + 1) + " §edamage from §fSkeleton§e, health left §a" + (20 - n % 9) + "§e/§a20";
      };
      client.gui.chatListener().handleSystemMessage(Component.literal(line), false);
   }

   private void fly(boolean on) {
      for (int attempt = 0; attempt < 2 && this.onClient(() -> this.minecraft.player.getAbilities().flying) != on; attempt++) {
         for (int press = 0; press < 2; press++) {
            this.minecraft.execute(() -> this.minecraft.options.keyJump.setDown(true));
            sleep(120L);
            this.minecraft.execute(() -> this.minecraft.options.keyJump.setDown(false));
            sleep(120L);
         }

         sleep(800L);
      }
   }

   private void command(String command) {
      this.minecraft.execute(() -> {
         if (this.minecraft.getConnection() != null) {
            this.minecraft.getConnection().sendCommand(command);
         }
      });
      sleep(150L);
   }

   private void waitFor(BooleanSupplier condition, long timeout) {
      long end = System.currentTimeMillis() + timeout;

      while (System.currentTimeMillis() < end && !this.onClient(condition::getAsBoolean)) {
         sleep(100L);
      }
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

   private static void log(String format, Object... args) {
      BedrockNative.LOGGER.info("Dev perf-render test: " + format, args);
   }

   private static void sleep(long millis) {
      try {
         Thread.sleep(millis);
      } catch (InterruptedException var3) {
         Thread.currentThread().interrupt();
      }
   }

   private record Options(int renderDistance, CloudStatus clouds, boolean bob) {
   }

   private static final class Variant {
      final String name;
      final FrameStats frames = new FrameStats();
      final double[] buckets = new double[FrameWatch.Bucket.values().length];
      double ticks;
      double gc;
      double frameMillis;
      double cpu;
      int windows;
      int slow;

      Variant(String name) {
         this.name = name;
      }

      void add(FrameWatch.Recording recording, double others) {
         this.frames.addAll(recording.inGame());

         for (FrameWatch.Bucket bucket : FrameWatch.Bucket.values()) {
            this.buckets[bucket.ordinal()] += recording.bucketMillis(bucket);
         }

         this.ticks = this.ticks + recording.ticks().meanMillis() * recording.ticks().count();
         this.gc = this.gc + recording.gcMillis();
         this.frameMillis = this.frameMillis + recording.frameMillis();
         this.cpu += others;
         this.slow = this.slow + recording.slowFrameCount();
         this.windows++;
      }

      String describe() {
         int count = Math.max(1, this.frames.count());
         return String.format(
            Locale.ROOT,
            "%d frames (%.0f fps), p50 %.2f, p95 %.2f, p99 %.2f, max %.1f ms, over 10/16 ms %d/%d; per frame: JSON UI %.3f, Java GUI draw %.3f, models %.3f, Java entities %.3f, feature prep %.3f, client ticks %.3f, window events %.3f ms; GC %.0f ms; CPU of other processes %.0f%%",
            this.frames.count(),
            this.frameMillis > 0.0 ? this.frames.count() / (this.frameMillis / 1000.0) : 0.0,
            this.frames.percentileMillis(50.0),
            this.frames.percentileMillis(95.0),
            this.frames.percentileMillis(99.0),
            this.frames.maxMillis(),
            this.frames.over(10000000L),
            this.frames.over(16700000L),
            this.per(FrameWatch.Bucket.UI, count),
            this.per(FrameWatch.Bucket.GUI_DRAW, count),
            this.per(FrameWatch.Bucket.MODELS, count),
            this.per(FrameWatch.Bucket.ENTITY_RENDER, count),
            this.per(FrameWatch.Bucket.FEATURE_PREP, count),
            this.ticks / count,
            this.per(FrameWatch.Bucket.WINDOW_EVENTS, count),
            this.gc,
            this.windows == 0 ? 0.0 : this.cpu / this.windows * 100.0
         );
      }

      private double per(FrameWatch.Bucket bucket, int count) {
         return this.buckets[bucket.ordinal()] / count;
      }
   }
}
