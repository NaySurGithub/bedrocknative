package net.bedrocknative.client.edit;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.world.BedrockBlocks;
import net.bedrocknative.world.block.SignEdits;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HangingSignBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.entity.SignText.Mutable;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.protocol.bedrock.packet.BlockEntityDataPacket;
import org.jspecify.annotations.Nullable;

public final class BedrockSigns {
   private static final Map<BlockEntity, NbtMap> LAST = Collections.synchronizedMap(new WeakHashMap<>());
   private static volatile int opened;
   private static volatile int sent;
   private static volatile boolean lastFront;

   private BedrockSigns() {
   }

   public static void record(SignBlockEntity sign, NbtMap tag) {
      LAST.put(sign, tag);
   }

   public static int openedCount() {
      return opened;
   }

   public static boolean lastOpenedFront() {
      return lastFront;
   }

   public static int sentCount() {
      return sent;
   }

   @Nullable
   public static NbtMap last(ClientLevel level, BlockPos pos) {
      return level.getBlockEntity(pos) instanceof SignBlockEntity sign ? LAST.get(sign) : null;
   }

   public static void open(Minecraft minecraft, ClientLevel level, BlockPos pos, boolean front) {
      if (minecraft.player != null && level.getBlockEntity(pos) instanceof SignBlockEntity sign) {
         NbtMap last = LAST.get(sign);
         if (last != null && SignEdits.waxed(last)) {
            BedrockNative.LOGGER.debug("OpenSign for the waxed sign at {} ignored", pos);
         } else {
            SignTextSlot slot = front ? SignTextSlot.FRONT : SignTextSlot.BACK;
            if (last != null) {
               Mutable raw = sign.getText(slot).asMutable();
               List<String> lines = SignEdits.lines(SignEdits.sideText(last, front));

               for (int i = 0; i < 4; i++) {
                  raw.setLine(i, Component.literal(lines.get(i)));
               }

               sign.setText(raw.asImmutable(), slot);
            }

            opened++;
            lastFront = front;
            minecraft.player.openTextEdit(sign, slot);
         }
      } else {
         BedrockNative.LOGGER.warn("OpenSign for {} but no sign block entity is there", pos);
      }
   }

   public static BlockEntityDataPacket toBedrock(ClientLevel level, ServerboundSignUpdatePacket update) {
      BlockPos pos = update.pos();
      BlockEntity entity = level.getBlockEntity(pos);
      NbtMap last = entity instanceof SignBlockEntity sign ? LAST.get(sign) : null;
      String id = entity instanceof HangingSignBlockEntity ? "HangingSign" : "Sign";
      boolean front = update.slot() == SignTextSlot.FRONT;
      BlockEntityDataPacket packet = new BlockEntityDataPacket();
      packet.setBlockPosition(Vector3i.from(pos.getX(), pos.getY(), pos.getZ()));
      NbtMap data = SignEdits.edited(last, id, pos.getX(), pos.getY(), pos.getZ(), front, update.lines());
      packet.setData(data);
      sent++;
      BedrockBlocks.loadBlockEntity(level, pos, data);
      return packet;
   }
}
