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
               } catch (ClassNotFoundException e) {
                  missing++;
               } catch (VirtualMachineError e) {
                  throw e;
               } catch (Throwable e) {
                  failed++;
               }
            }
         }
      } catch (IOException e) {
         throw new UncheckedIOException(e);
      }

      return new ClassPreload.Result(loaded, missing, failed, System.nanoTime() - started, stopped);
   }

   public record Result(int loaded, int missing, int failed, long nanos, boolean stopped) {
   }
}
