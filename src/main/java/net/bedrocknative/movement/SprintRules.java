package net.bedrocknative.movement;

public final class SprintRules {
   private SprintRules() {
   }

   public static boolean sneakEndsSprint(boolean sneakKeyDown, boolean flying, boolean inFluid) {
      return sneakKeyDown && !flying && !inFluid;
   }

   public static boolean sneakBlocksSprintStart(boolean sneakKeyWasDown, boolean flying, boolean inFluid) {
      return sneakKeyWasDown && !flying && !inFluid;
   }
}
