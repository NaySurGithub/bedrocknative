package net.bedrocknative.camera;

import org.cloudburstmc.math.vector.Vector2f;
import org.cloudburstmc.protocol.bedrock.data.ControlScheme;

public final class ControlSchemes {
   public static final ControlScheme DEFAULT = ControlScheme.LOCKED_PLAYER_RELATIVE_STRAFE;

   private ControlSchemes() {
   }

   public static boolean cameraRelative(ControlScheme scheme) {
      return scheme == ControlScheme.CAMERA_RELATIVE || scheme == ControlScheme.CAMERA_RELATIVE_STRAFE;
   }

   public static Vector2f relativeToPlayer(float left, float forward, float cameraYaw, float playerYaw) {
      float delta = CameraDirector.wrapDegrees(cameraYaw - playerYaw);
      if (Math.abs(delta) < 1.0E-4F) {
         return Vector2f.from(left, forward);
      }

      double radians = Math.toRadians(delta);
      double sin = Math.sin(radians);
      double cos = Math.cos(radians);
      return Vector2f.from((float)(left * cos - forward * sin), (float)(forward * cos + left * sin));
   }
}
