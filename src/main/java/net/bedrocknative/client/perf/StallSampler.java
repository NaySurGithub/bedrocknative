package net.bedrocknative.client.perf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.locks.LockSupport;
import org.jspecify.annotations.Nullable;

public final class StallSampler {
   private static final long THRESHOLD_MILLIS = 12L;
   private static final long THRESHOLD_NANOS = 12000000L;
   private static final long INTERVAL_MICROS = 2000L;
   private static final int SHOWN = 6;
   private static volatile @Nullable StallSampler current;
   private static volatile long frameStarted;
   private final Thread client;
   private final Thread thread;
   private volatile boolean stopped;
   private final Map<String, Integer> phases = new HashMap<>();
   private final Map<String, Integer> tickParts = new HashMap<>();
   private final Map<String, Integer> ours = new HashMap<>();
   private final Map<String, Integer> tops = new HashMap<>();
   private int samples;
   private int slowFrames;
   private long lastSampledFrame;
   private int frameSamples;
   private int longestFrameSamples;
   private @Nullable String longestFrameStack;
   private static final int LONGEST_FRAME_SAMPLES = 15;
   private static final int STACK_FRAMES = 28;

   private StallSampler(Thread client) {
      this.client = client;
      this.thread = Thread.ofPlatform().daemon().name("Dev stall sampler").unstarted(this::loop);
   }

   public static void start() {
      stop();
      StallSampler sampler = new StallSampler(Thread.currentThread());
      current = sampler;
      sampler.thread.start();
   }

   public static String stop() {
      StallSampler sampler = current;
      current = null;
      if (sampler == null) {
         return "";
      }

      sampler.stopped = true;
      LockSupport.unpark(sampler.thread);

      try {
         sampler.thread.join(1000L);
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
      }

      return sampler.describe();
   }

   public static void frameStarted(long now) {
      frameStarted = now;
   }

   private void loop() {
      while (!this.stopped) {
         LockSupport.parkNanos(2000000L);
         long started = frameStarted;
         if (started != 0L && System.nanoTime() - started >= 12000000L) {
            StackTraceElement[] stack = this.client.getStackTrace();
            if (frameStarted == started) {
               if (started != this.lastSampledFrame) {
                  this.lastSampledFrame = started;
                  this.slowFrames++;
                  this.frameSamples = 0;
               }

               this.samples++;
               this.frameSamples++;
               this.add(stack);
               if (this.frameSamples > this.longestFrameSamples && this.frameSamples >= 15) {
                  this.longestFrameSamples = this.frameSamples;
                  this.longestFrameStack = stackText(stack);
               }
            }
         }
      }
   }

   private void add(StackTraceElement[] stack) {
      if (stack.length != 0) {
         count(this.tops, name(stack[0]));
         String phase = null;
         String tickPart = null;
         String own = null;

         for (int i = 0; i < stack.length; i++) {
            StackTraceElement element = stack[i];
            if (own == null && element.getClassName().startsWith("net.bedrocknative.")) {
               own = name(element);
            }

            if (i > 0 && element.getClassName().equals("net.minecraft.client.Minecraft")) {
               if (element.getMethodName().equals("runTick") && phase == null) {
                  phase = name(stack[i - 1]);
               } else if (element.getMethodName().equals("tick") && tickPart == null) {
                  tickPart = name(stack[i - 1]);
               }
            }
         }

         count(this.phases, phase != null ? phase : "outside runTick");
         if (tickPart != null) {
            count(this.tickParts, tickPart);
         }

         if (own != null) {
            count(this.ours, own);
         }
      }
   }

   private static String name(StackTraceElement element) {
      String className = element.getClassName();
      return className.substring(className.lastIndexOf(46) + 1) + "." + element.getMethodName();
   }

   private static void count(Map<String, Integer> counts, String key) {
      counts.merge(key, 1, Integer::sum);
   }

   private String describe() {
      if (this.samples == 0) {
         return "";
      }

      String longest = this.longestFrameStack == null
         ? ""
         : String.format(Locale.ROOT, "; the longest frame (%d samples) was in: %s", this.longestFrameSamples, this.longestFrameStack);
      return String.format(
         Locale.ROOT,
         "%d samples (%d us apart) in %d frames over %d ms; game loop: %s; in the client tick: %s; our code: %s; top frames: %s%s",
         this.samples,
         2000L,
         this.slowFrames,
         12L,
         largest(this.phases),
         largest(this.tickParts),
         largest(this.ours),
         largest(this.tops),
         longest
      );
   }

   private static String stackText(StackTraceElement[] stack) {
      StringBuilder text = new StringBuilder();
      String previous = null;
      int shown = 0;

      for (StackTraceElement element : stack) {
         String name = name(element);
         if (!name.equals(previous)) {
            previous = name;
            text.append(shown == 0 ? "" : " <- ").append(name);
            if (++shown == 28) {
               break;
            }
         }
      }

      return text.toString();
   }

   private static String largest(Map<String, Integer> counts) {
      if (counts.isEmpty()) {
         return "none";
      }

      List<Entry<String, Integer>> entries = new ArrayList<>(counts.entrySet());
      entries.sort(Entry.<String, Integer>comparingByValue().reversed());
      StringBuilder text = new StringBuilder();

      for (int i = 0; i < Math.min(6, entries.size()); i++) {
         text.append(i == 0 ? "" : ", ").append(entries.get(i).getKey()).append(' ').append(entries.get(i).getValue());
      }

      return text.toString();
   }
}
