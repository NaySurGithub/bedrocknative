package net.bedrocknative.client.content;

import net.bedrocknative.BedrockNative;
import net.bedrocknative.world.block.custom.CustomBlocks;

public final class MoreCullingCompat {
   private static boolean applied;

   private MoreCullingCompat() {
   }

   public static void disableCarrierCulling() {
      if (!applied) {
         applied = true;

         try {
            Class.forName("ca.fxco.moreculling.api.block.MoreBlockCulling", false, MoreCullingCompat.class.getClassLoader());
            CustomBlocks.CARRIER.getClass().getMethod("moreculling$setCanCull", boolean.class).invoke(CustomBlocks.CARRIER, false);
            BedrockNative.LOGGER.info("MoreCulling: custom block culling disabled for {}", CustomBlocks.KEY.identifier());
         } catch (ClassNotFoundException var1) {
         } catch (ReflectiveOperationException | RuntimeException e) {
            BedrockNative.LOGGER.warn("MoreCulling: could not disable custom block culling ({})", e.toString());
         }
      }
   }
}
