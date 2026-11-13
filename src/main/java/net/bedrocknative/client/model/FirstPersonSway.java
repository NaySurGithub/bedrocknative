package net.bedrocknative.client.model;

import net.bedrocknative.client.movement.BedrockMovement;
import net.minecraft.client.Minecraft;

public final class FirstPersonSway {
   private FirstPersonSway() {
   }

   public static float angle(float javaAngle, boolean bobbing, boolean bedrockSession) {
      return bedrockSession && !bobbing ? 0.0F : javaAngle;
   }

   public static float current(float javaAngle) {
      return angle(javaAngle, (Boolean)Minecraft.getInstance().options.bobView().get(), BedrockMovement.active());
   }
}
