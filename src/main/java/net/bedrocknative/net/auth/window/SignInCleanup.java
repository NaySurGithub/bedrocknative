package net.bedrocknative.net.auth.window;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SignInCleanup {
   private static final Set<AutoCloseable> OPEN = ConcurrentHashMap.newKeySet();
   private static final AtomicBoolean HOOKED = new AtomicBoolean();

   private SignInCleanup() {
   }

   static void add(AutoCloseable open) {
      if (HOOKED.compareAndSet(false, true)) {
         Runtime.getRuntime().addShutdownHook(new Thread(SignInCleanup::closeAll, "Xbox sign-in cleanup"));
      }

      OPEN.add(open);
   }

   static void remove(AutoCloseable open) {
      OPEN.remove(open);
   }

   public static void closeAll() {
      for (AutoCloseable open : Set.copyOf(OPEN)) {
         try {
            open.close();
         } catch (Exception var3) {
         }

         OPEN.remove(open);
      }
   }

   static int openCount() {
      return OPEN.size();
   }
}
