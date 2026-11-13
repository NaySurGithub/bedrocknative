package net.bedrocknative.content.base;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;

final class FetchRun {
   final AtomicInteger written = new AtomicInteger();
   final AtomicInteger failed = new AtomicInteger();
   final AtomicLong bytes = new AtomicLong();
   final AtomicInteger requests = new AtomicInteger();
   final Set<String> done = ConcurrentHashMap.newKeySet();
   private final AtomicReference<String> stopReason = new AtomicReference<>();
   private final AtomicReference<String> firstFailure = new AtomicReference<>();
   private final List<String> notes = new CopyOnWriteArrayList<>();
   static final int NO_ANSWER_LIMIT = 8;
   private final AtomicInteger noAnswer = new AtomicInteger();

   boolean stopped() {
      return this.stopReason.get() != null;
   }

   void stop(String reason) {
      this.stopReason.compareAndSet(null, reason);
   }

   @Nullable
   String stopReason() {
      return this.stopReason.get();
   }

   void failed(String path, String reason) {
      this.failed.incrementAndGet();
      this.firstFailure.compareAndSet(null, path + ": " + reason);
   }

   @Nullable
   String firstFailure() {
      return this.firstFailure.get();
   }

   void note(String note) {
      if (this.notes.size() < 8) {
         this.notes.add(note);
      }
   }

   List<String> notes() {
      return List.copyOf(this.notes);
   }

   void written(BaseTable.Entry entry) {
      this.written.incrementAndGet();
      this.done.add(entry.path());
   }

   void answered() {
      this.noAnswer.set(0);
   }

   void noAnswer(String reason) {
      if (this.noAnswer.incrementAndGet() >= 8) {
         this.stop("no answer from the host (" + reason + ")");
      }
   }
}
