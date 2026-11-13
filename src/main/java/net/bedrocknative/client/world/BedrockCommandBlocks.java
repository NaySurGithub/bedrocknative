package net.bedrocknative.client.world;

import java.util.HashMap;
import java.util.Map;
import net.bedrocknative.text.BedrockText;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundSetCommandBlockPacket;
import net.minecraft.world.level.BaseCommandBlock;
import net.minecraft.world.level.block.entity.CommandBlockEntity;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.protocol.bedrock.data.CommandBlockMode;
import org.cloudburstmc.protocol.bedrock.packet.CommandBlockUpdatePacket;

public final class BedrockCommandBlocks {
   private static final Map<Long, BedrockCommandBlocks.Extras> EXTRAS = new HashMap<>();

   private BedrockCommandBlocks() {
   }

   public static void clear() {
      EXTRAS.clear();
   }

   public static void load(ClientLevel level, BlockPos pos, NbtMap tag) {
      EXTRAS.put(
         pos.asLong(),
         new BedrockCommandBlocks.Extras(Math.max(0, tag.getInt("TickDelay", 0)), tag.getBoolean("ExecuteOnFirstTick", true), tag.getString("CustomName", ""))
      );
      if (level.getBlockEntity(pos) instanceof CommandBlockEntity commandBlock) {
         BaseCommandBlock command = commandBlock.getCommandBlock();
         command.setCommand(tag.getString("Command", ""));
         command.setTrackOutput(tag.getBoolean("TrackOutput", true));
         String name = tag.getString("CustomName", "");
         command.setCustomName(name.isEmpty() ? null : BedrockText.format(name));
         String output = tag.getString("LastOutput", "");
         command.setLastOutput(output.isEmpty() ? null : BedrockText.format(output));
         commandBlock.setAutomatic(tag.getBoolean("auto", false));
         commandBlock.setPowered(tag.getBoolean("powered", false));
      }
   }

   public static CommandBlockUpdatePacket toBedrock(ServerboundSetCommandBlockPacket done) {
      BedrockCommandBlocks.Extras extras = EXTRAS.getOrDefault(done.getPos().asLong(), BedrockCommandBlocks.Extras.NEW);
      CommandBlockUpdatePacket packet = new CommandBlockUpdatePacket();
      packet.setBlock(true);
      packet.setBlockPosition(Vector3i.from(done.getPos().getX(), done.getPos().getY(), done.getPos().getZ()));

      packet.setMode(switch (done.getMode()) {
         case SEQUENCE -> CommandBlockMode.CHAIN;
         case AUTO -> CommandBlockMode.REPEATING;
         case REDSTONE -> CommandBlockMode.NORMAL;
      });
      packet.setRedstoneMode(!done.isAutomatic());
      packet.setConditional(done.isConditional());
      packet.setCommand(done.getCommand());
      packet.setLastOutput("");
      packet.setName(extras.name());
      packet.setFilteredName(extras.name());
      packet.setOutputTracked(done.isTrackOutput());
      packet.setTickDelay(extras.tickDelay());
      packet.setExecutingOnFirstTick(extras.executeOnFirstTick());
      return packet;
   }

   private record Extras(long tickDelay, boolean executeOnFirstTick, String name) {
      private static final BedrockCommandBlocks.Extras NEW = new BedrockCommandBlocks.Extras(0L, true, "");
   }
}
