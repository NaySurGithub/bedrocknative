package net.bedrocknative.client.content;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import org.jspecify.annotations.Nullable;

public final class ModelFileCache {
   static final boolean ENABLED = !"off".equalsIgnoreCase(System.getProperty("bedrocknative.modelFileCache", "on").trim());
   private static final ModelFileCache INSTANCE = new ModelFileCache(() -> AtlasReload.running(), name -> AtlasReload.phase(name));
   private final BooleanSupplier targeted;
   private final Consumer<String> phases;
   private final Object[] values = new Object[ModelFileCache.Kind.values().length];
   private final AtomicInteger generation = new AtomicInteger();

   ModelFileCache(BooleanSupplier targeted, Consumer<String> phases) {
      this.targeted = targeted;
      this.phases = phases;
   }

   public static void javaReloadStarts() {
      INSTANCE.forget();
   }

   public static <T> CompletableFuture<T> load(ModelFileCache.Kind kind, Supplier<CompletableFuture<T>> read, UnaryOperator<T> copy, Executor executor) {
      return ENABLED ? INSTANCE.get(kind, read, copy, executor) : read.get();
   }

   public static boolean complete() {
      return INSTANCE.allKept();
   }

   void forget() {
      this.generation.incrementAndGet();
      synchronized (this.values) {
         Arrays.fill(this.values, null);
      }
   }

   <T> CompletableFuture<T> get(ModelFileCache.Kind kind, Supplier<CompletableFuture<T>> read, UnaryOperator<T> copy, Executor executor) {
      if (this.targeted.getAsBoolean()) {
         Object kept;
         synchronized (this.values) {
            kept = this.values[kind.ordinal()];
         }

         if (kept != null) {
            T value = (T)kept;
            return CompletableFuture.supplyAsync(() -> {
               T own = copy.apply(value);
               this.phases.accept(kind.shown + " reused");
               return own;
            }, executor);
         }
      }

      int readFor = this.generation.get();
      return read.get().thenApply(value -> {
         this.keep(kind, value, readFor);
         return value == null ? null : copy.apply((T)value);
      });
   }

   private void keep(ModelFileCache.Kind kind, @Nullable Object value, int readFor) {
      synchronized (this.values) {
         if (value != null && readFor == this.generation.get()) {
            this.values[kind.ordinal()] = value;
         }
      }
   }

   boolean allKept() {
      synchronized (this.values) {
         for (Object value : this.values) {
            if (value == null) {
               return false;
            }
         }

         return true;
      }
   }

   public enum Kind {
      BLOCK_MODELS("block models"),
      BLOCK_STATES("block states"),
      ITEMS("item definitions");

      private final String shown;

      Kind(String shown) {
         this.shown = shown;
      }
   }
}
