package net.bedrocknative.client.net;

import net.bedrocknative.client.content.BedrockVanillaTextures;

public final class LoadingScreenHold {
   private static volatile boolean held;
   private static volatile boolean javaReady;

   private LoadingScreenHold() {
   }

   public static boolean filter(boolean ready) {
      javaReady = ready && !BedrockVanillaTextures.busy();
      return javaReady && !held;
   }

   static void hold() {
      javaReady = false;
      held = true;
   }

   static void release() {
      held = false;
   }

   static boolean javaReady() {
      return javaReady;
   }
}
