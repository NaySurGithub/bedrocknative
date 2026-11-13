package net.bedrocknative.client.rules;

import net.bedrocknative.client.movement.FreezeDiagnostics;
import net.minecraft.world.entity.player.Input;
import org.cloudburstmc.protocol.bedrock.packet.UpdateClientInputLocksPacket;

public final class BedrockInputLocks {
   public static final int CAMERA = 2;
   public static final int MOVEMENT = 4;
   public static final int LATERAL_MOVEMENT = 16;
   public static final int SNEAK = 32;
   public static final int JUMP = 64;
   public static final int MOUNT = 128;
   public static final int DISMOUNT = 256;
   public static final int MOVE_FORWARD = 512;
   public static final int MOVE_BACKWARD = 1024;
   public static final int MOVE_LEFT = 2048;
   public static final int MOVE_RIGHT = 4096;
   private static final int KNOWN = 8182;
   private static volatile int locks;
   private static volatile String lastRequest = "";

   private BedrockInputLocks() {
   }

   public static void apply(UpdateClientInputLocksPacket packet) {
      locks = packet.getLockComponentData() & 8182;
      lastRequest = packet.toString();
      FreezeDiagnostics.inputLocks(packet.getLockComponentData());
   }

   public static void reset() {
      locks = 0;
      lastRequest = "";
   }

   public static int locks() {
      return locks;
   }

   public static boolean any() {
      return locks != 0;
   }

   public static boolean cameraLocked() {
      return (locks & 2) != 0;
   }

   public static boolean jumpLocked() {
      return (locks & 68) != 0;
   }

   public static boolean mountLocked() {
      return (locks & 128) != 0;
   }

   public static boolean dismountLocked() {
      return (locks & 256) != 0;
   }

   public static Input filter(Input keys) {
      int current = locks;
      boolean moves = (current & 20) == 0;
      boolean forward = keys.forward() && moves && (current & 512) == 0;
      boolean backward = keys.backward() && moves && (current & 1024) == 0;
      boolean left = keys.left() && moves && (current & 2048) == 0;
      boolean right = keys.right() && moves && (current & 4096) == 0;
      boolean jump = keys.jump() && (current & 68) == 0;
      boolean shift = keys.shift() && (current & 36) == 0;
      return new Input(forward, backward, left, right, jump, shift, keys.sprint() && forward);
   }

   public static String lastRequest() {
      return lastRequest;
   }
}
