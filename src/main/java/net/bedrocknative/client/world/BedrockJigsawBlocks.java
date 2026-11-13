package net.bedrocknative.client.world;

import java.util.HashMap;
import java.util.Map;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.world.block.JigsawEdits;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ServerboundSetJigsawBlockPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.JigsawBlockEntity;
import net.minecraft.world.level.block.entity.JigsawBlockEntity.JointType;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.protocol.bedrock.packet.BlockEntityDataPacket;

public final class BedrockJigsawBlocks {
   private static final Map<Long, JigsawEdits.Data> SERVER = new HashMap<>();
   private static volatile int sent;

   private BedrockJigsawBlocks() {
   }

   public static void clear() {
      SERVER.clear();
   }

   public static void load(ClientLevel level, BlockPos pos, NbtMap tag) {
      BedrockNative.LOGGER.debug("Jigsaw block entity data at {}: {}", pos, tag);
      JigsawEdits.Data data = JigsawEdits.read(tag);
      remember(pos, data);
      if (level.getBlockEntity(pos) instanceof JigsawBlockEntity jigsaw) {
         apply(jigsaw, data);
      }
   }

   private static void apply(JigsawBlockEntity jigsaw, JigsawEdits.Data data) {
      jigsaw.setName(identifier(data.name()));
      jigsaw.setTarget(identifier(data.target()));
      jigsaw.setPool(ResourceKey.create(Registries.TEMPLATE_POOL, identifier(data.pool())));
      jigsaw.setFinalState(data.finalState());
      jigsaw.setJoint("aligned".equals(data.joint()) ? JointType.ALIGNED : JointType.ROLLABLE);
      jigsaw.setSelectionPriority(data.selectionPriority());
      jigsaw.setPlacementPriority(data.placementPriority());
   }

   static void remember(BlockPos pos, JigsawEdits.Data data) {
      SERVER.put(pos.asLong(), data);
   }

   public static JigsawEdits.Data serverData(BlockPos pos) {
      return SERVER.get(pos.asLong());
   }

   public static int sentCount() {
      return sent;
   }

   public static BlockEntityDataPacket toBedrock(ServerboundSetJigsawBlockPacket done) {
      JigsawEdits.Data known = SERVER.getOrDefault(done.getPos().asLong(), JigsawEdits.Data.DEFAULT);
      JigsawEdits.Data data = new JigsawEdits.Data(
         BedrockStructureBlocks.bedrockName(done.getName().toString(), known.name()),
         BedrockStructureBlocks.bedrockName(done.getTarget().toString(), known.target()),
         BedrockStructureBlocks.bedrockName(done.getPool().toString(), known.pool()),
         done.getFinalState(),
         done.getJoint().getSerializedName(),
         done.getSelectionPriority(),
         done.getPlacementPriority()
      );
      BlockEntityDataPacket packet = new BlockEntityDataPacket();
      BlockPos pos = done.getPos();
      packet.setBlockPosition(Vector3i.from(pos.getX(), pos.getY(), pos.getZ()));
      packet.setData(JigsawEdits.write(pos.getX(), pos.getY(), pos.getZ(), data));
      remember(pos, data);
      Minecraft minecraft = Minecraft.getInstance();
      Level level = minecraft == null ? null : minecraft.level;
      if (level != null && level.getBlockEntity(pos) instanceof JigsawBlockEntity jigsaw) {
         apply(jigsaw, data);
      }

      sent++;
      return packet;
   }

   private static Identifier identifier(String name) {
      Identifier parsed = Identifier.tryParse(name);
      return parsed != null ? parsed : JigsawBlockEntity.EMPTY_ID;
   }
}
