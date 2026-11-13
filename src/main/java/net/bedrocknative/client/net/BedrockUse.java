package net.bedrocknative.client.net;

import java.util.concurrent.atomic.AtomicBoolean;

public final class BedrockUse {
   private static volatile boolean known;
   private static final AtomicBoolean DETECTING = new AtomicBoolean();

   private BedrockUse() {
   }

   public static boolean known() {
      return known;
   }

   public static void markUsed() {
      known = true;
   }

   public static void detectInBackground() {
      if (!known && DETECTING.compareAndSet(false, true)) {
         Thread thread = new Thread(BedrockUse::detect, "Bedrock use detection");
         thread.setDaemon(true);
         thread.setPriority(1);
         thread.start();
      }
   }

   public static void detect() {
      try {
         if (BedrockConnector.memory().anyBedrock()) {
            known = true;
         }
      } catch (RuntimeException var1) {
      }
   }
}
