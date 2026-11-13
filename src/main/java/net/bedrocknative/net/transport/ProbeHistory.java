package net.bedrocknative.net.transport;

import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;

public final class ProbeHistory {
   public static final int MISSES_BEFORE_REPLACING = 2;
   static final long FIRST_BACKOFF_MS = 60000L;
   static final long MAX_BACKOFF_MS = 600000L;
   private static final int MAX_ENTRIES = 512;
   private final LongSupplier clockMillis;
   private final Map<String, ProbeHistory.State> states = new HashMap<>();

   public ProbeHistory() {
      this(System::currentTimeMillis);
   }

   ProbeHistory(LongSupplier clockMillis) {
      this.clockMillis = clockMillis;
   }

   public synchronized void answered(String key) {
      this.states.remove(key);
   }

   public synchronized int bedrockMissed(String key) {
      ProbeHistory.State before = this.states.getOrDefault(key, new ProbeHistory.State(0, 0, 0L));
      ProbeHistory.State after = new ProbeHistory.State(before.misses() + 1, before.tcpFailures(), before.tcpAllowedAt());
      this.put(key, after);
      return after.misses();
   }

   public synchronized int misses(String key) {
      return this.states.getOrDefault(key, new ProbeHistory.State(0, 0, 0L)).misses();
   }

   public synchronized boolean tcpAllowed(String key) {
      return this.clockMillis.getAsLong() >= this.states.getOrDefault(key, new ProbeHistory.State(0, 0, 0L)).tcpAllowedAt();
   }

   public synchronized void tcpFailed(String key) {
      ProbeHistory.State before = this.states.getOrDefault(key, new ProbeHistory.State(0, 0, 0L));
      int failures = before.tcpFailures() + 1;
      long wait = Math.min(600000L, 60000L << Math.min(failures - 1, 10));
      this.put(key, new ProbeHistory.State(before.misses(), failures, this.clockMillis.getAsLong() + wait));
   }

   private void put(String key, ProbeHistory.State state) {
      if (this.states.size() >= 512 && !this.states.containsKey(key)) {
         this.states.clear();
      }

      this.states.put(key, state);
   }

   private record State(int misses, int tcpFailures, long tcpAllowedAt) {
   }
}
