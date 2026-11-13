package net.bedrocknative.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.bedrocknative.BedrockNative;

public final class UiProblems {
   private static final int KEEP_PER_KIND = 50;
   private static final int WARN_MESSAGE = 300;
   private final Map<String, Integer> counts = new LinkedHashMap<>();
   private final Map<String, List<String>> messages = new LinkedHashMap<>();
   private final Map<String, List<String>> notes = new LinkedHashMap<>();
   private long changes;

   public synchronized void add(String kind, String message) {
      this.changes++;
      this.counts.merge(kind, 1, Integer::sum);
      List<String> kept = this.messages.computeIfAbsent(kind, key -> new ArrayList<>());
      if (kept.size() < 50) {
         kept.add(message);
      }
   }

   public synchronized void note(String kind, String message) {
      this.changes++;
      List<String> kept = this.notes.computeIfAbsent(kind, key -> new ArrayList<>());
      if (kept.size() < 50 && !kept.contains(message)) {
         kept.add(message);
      }
   }

   public synchronized List<String> notes(String kind) {
      return List.copyOf(this.notes.getOrDefault(kind, Collections.emptyList()));
   }

   public synchronized long changes() {
      return this.changes;
   }

   public synchronized int count(String kind) {
      return this.counts.getOrDefault(kind, 0);
   }

   public synchronized int total() {
      return this.counts.values().stream().mapToInt(Integer::intValue).sum();
   }

   public synchronized List<String> messages(String kind) {
      return List.copyOf(this.messages.getOrDefault(kind, Collections.emptyList()));
   }

   public synchronized Map<String, Integer> counts() {
      return Map.copyOf(this.counts);
   }

   public synchronized void log(String what) {
      this.notes.forEach((kind, list) -> list.forEach(message -> BedrockNative.LOGGER.debug("JSON UI {} note [{}] {}", new Object[]{what, kind, message})));
      if (!this.counts.isEmpty()) {
         BedrockNative.LOGGER.warn("JSON UI {}: {} problems {}", new Object[]{what, this.total(), this.counts});
         this.messages
            .forEach(
               (kind, list) -> {
                  if (!list.isEmpty()) {
                     String first = list.getFirst();
                     BedrockNative.LOGGER
                        .warn(
                           "JSON UI {} [{}] {}{}",
                           new Object[]{
                              what,
                              kind,
                              first.length() > 300 ? first.substring(0, 300) + "..." : first,
                              list.size() > 1 ? " (and " + (this.counts.getOrDefault(kind, list.size()) - 1) + " more)" : ""
                           }
                        );
                  }
               }
            );
         this.messages.forEach((kind, list) -> list.forEach(message -> BedrockNative.LOGGER.debug("JSON UI {} [{}] {}", new Object[]{what, kind, message})));
      }
   }

   @Override
   public synchronized String toString() {
      return this.counts.toString();
   }
}
