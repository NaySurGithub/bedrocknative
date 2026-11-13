package net.bedrocknative.client.world;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import net.bedrocknative.BedrockNative;
import net.minecraft.client.multiplayer.ClientLevel;

public final class SodiumChunks {
   static final String HOLDER = "net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTrackerHolder";
   static final String STATUS = "net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkStatus";
   private static final int FLAG_ALL = 3;
   private static final int FLAG_BLOCKS = 1;
   private static final int FLAG_LIGHT = 2;
   private static final SodiumChunks INSTANCE = new SodiumChunks(
      "net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTrackerHolder", "net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkStatus"
   );
   private final String holderName;
   private final String statusName;
   private boolean resolved;
   private String unavailable;
   private MethodHandle tracker;
   private MethodHandle added;
   private MethodHandle removed;
   private int flags = 3;
   private int blockFlag = 1;
   private int lightFlag = 2;
   private WeakReference<Object> session = new WeakReference<>(null);
   private boolean failureLogged;
   private boolean unavailableLogged;

   SodiumChunks(String holderName, String statusName) {
      this.holderName = holderName;
      this.statusName = statusName;
   }

   public static void loaded(ClientLevel level, int x, int z) {
      INSTANCE.added(level, x, z);
   }

   public static void loaded(ClientLevel level, int x, int z, boolean withLight) {
      INSTANCE.reportLoaded(level, x, z, withLight);
   }

   public static void lit(ClientLevel level, int x, int z) {
      INSTANCE.reportLit(level, x, z);
   }

   public static void unloaded(ClientLevel level, int x, int z) {
      INSTANCE.removed(level, x, z);
   }

   boolean added(Object level, int x, int z) {
      return this.report(level, x, z, true);
   }

   boolean removed(Object level, int x, int z) {
      return this.report(level, x, z, false);
   }

   boolean reportLoaded(Object level, int x, int z, boolean withLight) {
      return this.report(level, x, z, true, -1, withLight);
   }

   boolean reportLit(Object level, int x, int z) {
      return this.report(level, x, z, true, -2, false);
   }

   private boolean report(Object level, int x, int z, boolean add) {
      return this.report(level, x, z, add, 0, true);
   }

   private boolean report(Object level, int x, int z, boolean add, int which, boolean withLight) {
      if (this.session.get() != level) {
         this.session = new WeakReference<>(level);
         this.failureLogged = false;
         this.unavailableLogged = false;
      }

      if (!this.resolved) {
         this.resolve(level.getClass());
      }

      MethodHandle call = add ? this.added : this.removed;
      if (call == null) {
         if (add && !this.unavailableLogged) {
            this.unavailableLogged = true;
            BedrockNative.LOGGER.debug("Sodium's chunk tracker was not found, Bedrock columns are not reported to it ({})", this.unavailable);
         }

         return false;
      } else {
         try {
            Object chunkTracker = (Object)this.tracker.invoke((Object)level);
            int reported = which == -2 ? this.lightFlag : (which == -1 && !withLight ? this.blockFlag : this.flags);
            call.invoke((Object)chunkTracker, (int)x, (int)z, (int)reported);
            return true;
         } catch (Throwable failed) {
            if (!this.failureLogged) {
               this.failureLogged = true;
               BedrockNative.LOGGER
                  .warn(
                     "Sodium: its chunk tracker refused column {} {} ({} {}: {}); Sodium may draw columns wrongly on Bedrock servers (reported once per session)",
                     new Object[]{x, z, add ? "onChunkStatusAdded" : "onChunkStatusRemoved", failed.getClass().getName(), failed.getMessage()}
                  );
            }

            return false;
         }
      }
   }

   private void resolve(Class<?> levelClass) {
      this.resolved = true;

      Class<?> holder;
      try {
         holder = Class.forName(this.holderName, true, SodiumChunks.class.getClassLoader());
      } catch (ClassNotFoundException | LinkageError absent) {
         this.unavailable = "class " + this.holderName + " is not visible to " + SodiumChunks.class.getClassLoader() + " (the only name tried)";
         return;
      }

      try {
         Lookup lookup = MethodHandles.publicLookup();
         Method get = null;

         for (Method method : holder.getMethods()) {
            if (method.getName().equals("get")
               && Modifier.isStatic(method.getModifiers())
               && method.getParameterCount() == 1
               && method.getParameterTypes()[0].isAssignableFrom(levelClass)) {
               get = method;
            }
         }

         if (get == null) {
            throw new NoSuchMethodException(this.holderName + ".get(" + levelClass.getName() + ")");
         }

         Class<?> trackerClass = get.getReturnType();
         this.tracker = lookup.unreflect(get).asType(MethodType.methodType(Object.class, Object.class));
         MethodType call = MethodType.methodType(void.class, int.class, int.class, int.class);
         MethodType generic = MethodType.methodType(void.class, Object.class, int.class, int.class, int.class);
         this.added = lookup.findVirtual(trackerClass, "onChunkStatusAdded", call).asType(generic);

         try {
            this.flags = Class.forName(this.statusName, true, holder.getClassLoader()).getField("FLAG_ALL").getInt(null);
         } catch (ReflectiveOperationException | LinkageError keep) {
            this.flags = 3;
         }

         try {
            Class<?> status = Class.forName(this.statusName, true, holder.getClassLoader());
            this.blockFlag = status.getField("FLAG_HAS_BLOCK_DATA").getInt(null);
            this.lightFlag = status.getField("FLAG_HAS_LIGHT_DATA").getInt(null);
         } catch (ReflectiveOperationException | LinkageError keep) {
            this.blockFlag = 1;
            this.lightFlag = 2;
         }

         try {
            this.removed = lookup.findVirtual(trackerClass, "onChunkStatusRemoved", call).asType(generic);
         } catch (ReflectiveOperationException | RuntimeException | LinkageError none) {
            this.removed = null;
            BedrockNative.LOGGER
               .warn("Sodium: its chunk tracker has no onChunkStatusRemoved ({}); columns the client drops stay known to Sodium", none.toString());
         }

         BedrockNative.LOGGER.info("Sodium: Bedrock columns are reported to its chunk tracker (flags {})", this.flags);
      } catch (ReflectiveOperationException | RuntimeException | LinkageError failed) {
         this.tracker = null;
         this.added = null;
         this.removed = null;
         this.unavailable = failed.toString();
         BedrockNative.LOGGER.warn("Sodium is loaded but its chunk tracker was not found ({}); it may draw no terrain on Bedrock servers", failed.toString());
      }
   }
}
