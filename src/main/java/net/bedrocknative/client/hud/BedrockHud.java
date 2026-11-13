package net.bedrocknative.client.hud;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicIntegerArray;
import net.bedrocknative.BedrockNative;
import org.cloudburstmc.protocol.bedrock.data.HudElement;
import org.cloudburstmc.protocol.bedrock.data.HudVisibility;
import org.cloudburstmc.protocol.bedrock.packet.SetHudPacket;

public final class BedrockHud {
   private static final HudElement[] ELEMENTS = HudElement.values();
   private static final int ALL = (1 << ELEMENTS.length) - 1;
   private static volatile int hidden;
   private static final AtomicIntegerArray ASKED = new AtomicIntegerArray(ELEMENTS.length);
   private static final AtomicIntegerArray SKIPPED = new AtomicIntegerArray(ELEMENTS.length);
   private static volatile String lastRequest = "";

   private BedrockHud() {
   }

   public static boolean hides(HudElement element) {
      ASKED.incrementAndGet(element.ordinal());
      if (isHidden(element)) {
         SKIPPED.incrementAndGet(element.ordinal());
         return true;
      } else {
         return false;
      }
   }

   private static boolean isHidden(HudElement element) {
      return (hidden & 1 << element.ordinal()) != 0;
   }

   public static void apply(SetHudPacket packet) {
      Set<HudElement> elements = packet.getElements();
      int mask = 0;

      for (HudElement element : elements) {
         mask |= 1 << element.ordinal();
      }

      if (mask == 0) {
         mask = ALL;
      }

      hidden = packet.getVisibility() == HudVisibility.HIDE ? hidden | mask : hidden & ~mask;
      lastRequest = packet.getVisibility() + " " + elements;
      BedrockNative.LOGGER.debug("SetHud {}: hidden now {}", lastRequest, hiddenElements());
   }

   public static Set<HudElement> hiddenElements() {
      Set<HudElement> result = EnumSet.noneOf(HudElement.class);

      for (HudElement element : ELEMENTS) {
         if (isHidden(element)) {
            result.add(element);
         }
      }

      return result;
   }

   public static int[] counts(HudElement element) {
      return new int[]{ASKED.get(element.ordinal()), SKIPPED.get(element.ordinal())};
   }

   public static String lastRequest() {
      return lastRequest;
   }

   public static void reset() {
      hidden = 0;
   }
}
