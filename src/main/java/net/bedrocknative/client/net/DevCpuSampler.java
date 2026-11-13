package net.bedrocknative.client.net;

import com.sun.management.OperatingSystemMXBean;
import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import org.jspecify.annotations.Nullable;

final class DevCpuSampler {
   private final OperatingSystemMXBean os = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
   private final int cores = Runtime.getRuntime().availableProcessors();
   private final long self = ProcessHandle.current().pid();
   @Nullable
   private final ProcessHandle server = ProcessHandle.allProcesses()
      .filter(process -> process.info().command().map(command -> command.toLowerCase(Locale.ROOT).endsWith("bedrock_server.exe")).orElse(false))
      .findFirst()
      .orElse(null);
   private final long started = System.nanoTime();
   private final Map<Long, Long> othersAtStart = this.otherCpu();
   private final long serverCpuAtStart = this.serverCpuNanos();
   private long serverCpuAtStop = -1L;
   private long othersCpu = -1L;
   private long stopped;
   private double systemSum;
   private double systemMax;
   private double oursSum;
   private int samples;
   @Nullable
   private volatile Thread thread;

   DevCpuSampler() {
      this.os.getCpuLoad();
      this.os.getProcessCpuLoad();
   }

   DevCpuSampler start() {
      Thread sampler = Thread.ofPlatform().daemon().name("Dev CPU sampler").unstarted(() -> {
         while (this.thread == Thread.currentThread()) {
            try {
               Thread.sleep(500L);
            } catch (InterruptedException var2) {
               return;
            }

            this.sample();
         }
      });
      this.thread = sampler;
      sampler.start();
      return this;
   }

   synchronized void stop() {
      Thread sampler = this.thread;
      this.thread = null;
      if (sampler != null) {
         sampler.interrupt();
      }

      if (this.serverCpuAtStop < 0L) {
         this.serverCpuAtStop = this.serverCpuNanos();
         this.stopped = System.nanoTime();
         long sum = 0L;

         for (Entry<Long, Long> entry : this.otherCpu().entrySet()) {
            sum += Math.max(0L, entry.getValue() - this.othersAtStart.getOrDefault(entry.getKey(), 0L));
         }

         this.othersCpu = sum;
      }
   }

   synchronized void sample() {
      double all = this.os.getCpuLoad();
      double ours = this.os.getProcessCpuLoad();
      if (!(all < 0.0) && !(ours < 0.0)) {
         this.systemSum = this.systemSum + Math.max(0.0, all - ours);
         this.systemMax = Math.max(this.systemMax, all - ours);
         this.oursSum += ours;
         this.samples++;
      }
   }

   synchronized double meanOurs() {
      return this.samples == 0 ? 0.0 : this.oursSum / this.samples;
   }

   synchronized double meanServer() {
      long end = this.serverCpuAtStop >= 0L ? this.serverCpuAtStop : this.serverCpuNanos();
      return this.server == null ? 0.0 : this.share(Math.max(0L, end - this.serverCpuAtStart));
   }

   synchronized double meanOthers() {
      return this.othersCpu < 0L ? 0.0 : this.share(this.othersCpu);
   }

   synchronized double meanSystem() {
      return this.samples == 0 ? 0.0 : this.systemSum / this.samples;
   }

   synchronized double maxOthers() {
      return this.systemMax;
   }

   private double share(long cpuNanos) {
      long wall = (this.stopped > 0L ? this.stopped : System.nanoTime()) - this.started;
      return wall <= 0L ? 0.0 : (double)cpuNanos / wall / this.cores;
   }

   private long serverCpuNanos() {
      return this.server == null ? 0L : this.server.info().totalCpuDuration().map(Duration::toNanos).orElse(0L);
   }

   private Map<Long, Long> otherCpu() {
      Map<Long, Long> cpu = new HashMap<>();
      long serverPid = this.server == null ? -1L : this.server.pid();
      ProcessHandle.allProcesses().forEach(process -> {
         long pid = process.pid();
         if (pid != this.self && pid != serverPid) {
            process.info().totalCpuDuration().ifPresent(duration -> cpu.put(pid, duration.toNanos()));
         }
      });
      return cpu;
   }
}
