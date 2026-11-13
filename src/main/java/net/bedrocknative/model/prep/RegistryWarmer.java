package net.bedrocknative.model.prep;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import net.bedrocknative.model.entity.ClientEntityDef;
import net.bedrocknative.model.entity.ModelRegistry;

public final class RegistryWarmer {
   private final ModelRegistry registry;
   private final PrepPool pool;
   private final List<ClientEntityDef> order;
   private final long maxPixels;
   private final Consumer<RegistryWarmer.Summary> onDone;
   private final LongSupplier clock;
   private final long started;
   private final Set<String> claimed = ConcurrentHashMap.newKeySet();
   private final AtomicInteger next = new AtomicInteger();
   private final AtomicInteger definitions = new AtomicInteger();
   private final AtomicInteger images = new AtomicInteger();
   private final AtomicLong pixels = new AtomicLong();
   private final AtomicLong chainPixels = new AtomicLong();
   private final AtomicLong spawnPixels = new AtomicLong();
   private final AtomicInteger meshes = new AtomicInteger();
   private volatile boolean cancelled;
   private volatile long doneNanos = -1L;

   public RegistryWarmer(ModelRegistry registry, PrepPool pool, long maxPixels, Consumer<RegistryWarmer.Summary> onDone, LongSupplier clock) {
      this.registry = registry;
      this.pool = pool;
      this.maxPixels = maxPixels;
      this.onDone = onDone;
      this.clock = clock;
      this.order = order(registry);
      this.started = clock.getAsLong();
   }

   public static List<ClientEntityDef> order(ModelRegistry registry) {
      List<ClientEntityDef> order = new ArrayList<>();
      ClientEntityDef player = registry.entity("minecraft:player");
      if (player != null) {
         order.add(player);
      }

      for (ClientEntityDef definition : sorted(registry.entities())) {
         if (definition != player && registry.baseGameDefines(definition.identifier())) {
            order.add(definition);
         }
      }

      order.addAll(sorted(registry.attachables()));
      return order;
   }

   private static List<ClientEntityDef> sorted(Collection<ClientEntityDef> definitions) {
      List<ClientEntityDef> list = new ArrayList<>(definitions);
      list.sort(Comparator.comparing(ClientEntityDef::identifier));
      return list;
   }

   public ModelRegistry registry() {
      return this.registry;
   }

   public void start() {
      this.schedule();
   }

   public void spawn(ClientEntityDef definition, long distanceSq) {
      if (!this.cancelled && this.claimed.add(key(definition))) {
         this.pool.submit(PrepPool.Tier.NEAR, distanceSq, false, ticket -> {
            if (!this.cancelled) {
               long left = this.maxPixels - this.spawnPixels.get();
               this.spawnPixels.addAndGet(this.warm(definition, Math.max(0L, left)).pixels());
            }

            return null;
         }, null, result -> 0L);
      }
   }

   public void cancel() {
      this.cancelled = true;
   }

   public boolean cancelled() {
      return this.cancelled;
   }

   private void schedule() {
      if (!this.cancelled) {
         this.pool.submit(PrepPool.Tier.WARM, 0L, false, ticket -> {
            this.step();
            return null;
         }, null, result -> 0L);
      }
   }

   private void step() {
      if (!this.cancelled) {
         int index;
         while ((index = this.next.getAndIncrement()) < this.order.size()) {
            ClientEntityDef definition = this.order.get(index);
            if (this.claimed.add(key(definition))) {
               ModelRegistry.Warmed warmed = this.warm(definition, this.chainPixels.get() < this.maxPixels);
               this.chainPixels.addAndGet(warmed.pixels());
               this.schedule();
               return;
            }
         }

         if (this.doneNanos < 0L) {
            this.doneNanos = Math.max(0L, this.clock.getAsLong() - this.started);
            this.onDone.accept(this.summary());
         }
      }
   }

   private ModelRegistry.Warmed warm(ClientEntityDef definition, boolean textures) {
      return this.warm(definition, textures ? Long.MAX_VALUE : 0L);
   }

   private ModelRegistry.Warmed warm(ClientEntityDef definition, long texturePixels) {
      ModelRegistry.Warmed warmed = this.registry.warm(definition, texturePixels);
      this.definitions.incrementAndGet();
      this.images.addAndGet(warmed.images());
      this.pixels.addAndGet(warmed.pixels());
      this.meshes.addAndGet(warmed.meshes());
      return warmed;
   }

   private static String key(ClientEntityDef definition) {
      return (definition.attachable() ? "a:" : "e:") + definition.identifier();
   }

   public RegistryWarmer.Summary summary() {
      long done = this.doneNanos;
      return new RegistryWarmer.Summary(
         this.definitions.get(),
         this.images.get(),
         this.pixels.get(),
         this.meshes.get(),
         this.order.size(),
         done >= 0L,
         done >= 0L ? done : Math.max(0L, this.clock.getAsLong() - this.started)
      );
   }

   public record Summary(int definitions, int images, long pixels, int meshes, int planned, boolean done, long nanos) {
   }
}
