package net.bedrocknative.client.world;

import java.util.function.Consumer;
import net.minecraft.client.player.LocalPlayer;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.PlayerActionType;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerActionPacket;

public final class BedrockSpinAttack {
   private boolean spinning;

   public void tick(LocalPlayer player, long runtimeId, Consumer<BedrockPacket> send) {
      boolean now = player.isAutoSpinAttack();
      if (now != this.spinning) {
         this.spinning = now;
         send.accept(packet(now, runtimeId, player.getBlockX(), player.getBlockY(), player.getBlockZ()));
      }
   }

   public void reset() {
      this.spinning = false;
   }

   public static PlayerActionPacket packet(boolean start, long runtimeId, int x, int y, int z) {
      PlayerActionPacket packet = new PlayerActionPacket();
      packet.setRuntimeEntityId(runtimeId);
      packet.setAction(start ? PlayerActionType.START_SPIN_ATTACK : PlayerActionType.STOP_SPIN_ATTACK);
      packet.setBlockPosition(Vector3i.from(x, y, z));
      packet.setResultPosition(Vector3i.ZERO);
      packet.setFace(0);
      return packet;
   }
}
