package net.bedrocknative.perf;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.BooleanSupplier;

public final class ClassPreload {
   private ClassPreload() {
   }

   public static ClassPreload.Result preload(ClassLoader loader, BufferedReader list, BooleanSupplier stop) {
      long started = System.nanoTime();
      int loaded = 0;
      int missing = 0;
      int failed = 0;
      boolean stopped = false;

      String line;
      try {
         while ((line = list.readLine()) != null) {
            String name = line.strip();
            if (!name.isEmpty() && !name.startsWith("#")) {
               if (stop.getAsBoolean()) {
                  stopped = true;
                  break;
               }

               try {
                  Class.forName(name, false, loader);
                  loaded++;
               } catch (ClassNotFoundException var12) {
                  missing++;
               } catch (VirtualMachineError var13) {
                  throw var13;
               } catch (Throwable var14) {
                  failed++;
               }
            }
         }
      } catch (IOException var15) {
         throw new UncheckedIOException(var15);
      }

      return new ClassPreload.Result(loaded, missing, failed, System.nanoTime() - started, stopped);
   }

   public record Result(int loaded, int missing, int failed, long nanos, boolean stopped) {
   }
}
