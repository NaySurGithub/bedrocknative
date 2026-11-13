package net.bedrocknative.perf;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.LongSupplier;
import org.jspecify.annotations.Nullable;

public final class PhaseClock {
   private final String what;
   private final LongSupplier nanos;
   private final long startNanos;
   private final List<PhaseClock.Mark> marks = new ArrayList<>();

   private PhaseClock(String what, LongSupplier nanos) {
      this.what = what;
      this.nanos = nanos;
      this.startNanos = nanos.getAsLong();
   }

   public static PhaseClock start(String what) {
      return new PhaseClock(what, System::nanoTime);
   }

   public static PhaseClock start(String what, LongSupplier nanos) {
      return new PhaseClock(what, nanos);
   }

   public synchronized void mark(String name) {
      if (!this.has(name)) {
         this.marks.add(new PhaseClock.Mark(name, (this.nanos.getAsLong() - this.startNanos) / 1000000L));
      }
   }

   public synchronized boolean has(String name) {
      for (PhaseClock.Mark mark : this.marks) {
         if (mark.name().equals(name)) {
            return true;
         }
      }

      return false;
   }

   public synchronized long millis(String name) {
      for (PhaseClock.Mark mark : this.marks) {
         if (mark.name().equals(name)) {
            return mark.atMillis();
         }
      }

      return -1L;
   }

   public long between(String from, String to) {
      long start = this.millis(from);
      long end = this.millis(to);
      return start >= 0L && end >= 0L ? end - start : -1L;
   }

   public synchronized List<PhaseClock.Mark> marks() {
      return List.copyOf(this.marks);
   }

   public long elapsedMillis() {
      return (this.nanos.getAsLong() - this.startNanos) / 1000000L;
   }

   public String what() {
      return this.what;
   }

   public synchronized String line() {
      StringBuilder text = new StringBuilder(this.what).append(": ");
      long previous = 0L;

      for (int i = 0; i < this.marks.size(); i++) {
         PhaseClock.Mark mark = this.marks.get(i);
         if (i > 0) {
            text.append(" | ");
         }

         text.append(mark.name()).append(i == 0 ? " " : " +").append(mark.atMillis() - (i == 0 ? 0L : previous));
         previous = mark.atMillis();
      }

      return text.append(String.format(Locale.ROOT, " = %d ms", previous)).toString();
   }

   @Override
   public String toString() {
      return this.line();
   }

   public synchronized PhaseClock.@Nullable Mark last() {
      return this.marks.isEmpty() ? null : this.marks.getLast();
   }

   public record Mark(String name, long atMillis) {
   }
}
