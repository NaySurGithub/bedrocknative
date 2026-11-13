package net.bedrocknative.client.world;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.protocol.game.ServerboundSetStructureBlockPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.level.block.entity.StructureBlockEntity.UpdateType;
import net.minecraft.world.level.block.state.properties.StructureMode;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.protocol.bedrock.data.structure.StructureAnimationMode;
import org.cloudburstmc.protocol.bedrock.data.structure.StructureBlockType;
import org.cloudburstmc.protocol.bedrock.data.structure.StructureEditorData;
import org.cloudburstmc.protocol.bedrock.data.structure.StructureMirror;
import org.cloudburstmc.protocol.bedrock.data.structure.StructureRedstoneSaveMode;
import org.cloudburstmc.protocol.bedrock.data.structure.StructureRotation;
import org.cloudburstmc.protocol.bedrock.data.structure.StructureSettings;
import org.cloudburstmc.protocol.bedrock.packet.StructureBlockUpdatePacket;

public final class BedrockStructureBlocks {
   private static final String DEFAULT_PALETTE = "default";
   private static final Map<Long, BedrockStructureBlocks.Extras> EXTRAS = new HashMap<>();
   private static volatile long localUniqueId;

   private BedrockStructureBlocks() {
   }

   public static void start(long localPlayerUniqueId) {
      localUniqueId = localPlayerUniqueId;
      EXTRAS.clear();
   }

   public static void load(ClientLevel level, BlockPos pos, NbtMap tag) {
      EXTRAS.put(
         pos.asLong(),
         new BedrockStructureBlocks.Extras(
            tag.getString("structureName", ""),
            tag.getBoolean("includePlayers", false),
            tag.getBoolean("removeBlocks", false),
            safe(StructureAnimationMode.values(), tag.getByte("animationMode", (byte)0), StructureAnimationMode.NONE),
            tag.getFloat("animationSeconds", 0.0F),
            Vector3f.from(tag.getFloat("xStructurePivot", 0.0F), tag.getFloat("yStructurePivot", 0.0F), tag.getFloat("zStructurePivot", 0.0F)),
            safe(StructureRedstoneSaveMode.values(), tag.getInt("redstoneSaveMode", 0), StructureRedstoneSaveMode.SAVES_TO_MEMORY)
         )
      );
      if (level.getBlockEntity(pos) instanceof StructureBlockEntity structure) {
         structure.setStructureName(tag.getString("structureName", ""));
         structure.setMetaData(tag.getString("dataField", ""));
         structure.setMode(javaMode(safe(StructureBlockType.values(), tag.getInt("data", 0), StructureBlockType.DATA)));
         structure.setStructurePos(new BlockPos(tag.getInt("xStructureOffset", 0), tag.getInt("yStructureOffset", 0), tag.getInt("zStructureOffset", 0)));
         structure.setStructureSize(new Vec3i(tag.getInt("xStructureSize", 0), tag.getInt("yStructureSize", 0), tag.getInt("zStructureSize", 0)));

         structure.setMirror(switch ((StructureMirror)safe(StructureMirror.values(), tag.getByte("mirror", (byte)0), StructureMirror.NONE)) {
            case X -> Mirror.FRONT_BACK;
            case Z -> Mirror.LEFT_RIGHT;
            default -> Mirror.NONE;
         });
         structure.setRotation(Rotation.BY_ID.apply(tag.getByte("rotation", (byte)0)));
         structure.setIgnoreEntities(tag.getBoolean("ignoreEntities", true));
         structure.setShowBoundingBox(tag.getBoolean("showBoundingBox", true));
         structure.setIntegrity(Math.clamp(tag.getFloat("integrity", 100.0F) / 100.0F, 0.0F, 1.0F));
         structure.setSeed(tag.getLong("seed", 0L));
         structure.setPowered(tag.getBoolean("isPowered", false));
      }
   }

   public static StructureBlockUpdatePacket toBedrock(ServerboundSetStructureBlockPacket done) {
      BedrockStructureBlocks.Extras extras = EXTRAS.getOrDefault(done.getPos().asLong(), BedrockStructureBlocks.Extras.NEW);

      StructureBlockType type = switch (done.getMode()) {
         case SAVE -> StructureBlockType.SAVE;
         case LOAD -> StructureBlockType.LOAD;
         case CORNER -> StructureBlockType.CORNER;
         case DATA -> StructureBlockType.DATA;
      };

      StructureSettings settings = new StructureSettings(
         "default",
         done.isIgnoreEntities(),
         extras.removeBlocks(),
         false,
         Vector3i.from(done.getSize().getX(), done.getSize().getY(), done.getSize().getZ()),
         Vector3i.from(done.getOffset().getX(), done.getOffset().getY(), done.getOffset().getZ()),
         localUniqueId,
         rotation(done.getRotation()),
         switch (done.getMirror()) {
            case FRONT_BACK -> StructureMirror.X;
            case LEFT_RIGHT -> StructureMirror.Z;
            default -> StructureMirror.NONE;
         },
         extras.animationMode(),
         extras.animationSeconds(),
         Math.clamp(done.getIntegrity() * 100.0F, 0.0F, 100.0F),
         (int)done.getSeed(),
         extras.pivot()
      );
      StructureBlockUpdatePacket packet = new StructureBlockUpdatePacket();
      packet.setBlockPosition(Vector3i.from(done.getPos().getX(), done.getPos().getY(), done.getPos().getZ()));
      packet.setEditorData(
         new StructureEditorData(
            bedrockName(done.getName(), extras.name()),
            "",
            done.getData(),
            extras.includePlayers(),
            done.isShowBoundingBox(),
            type,
            settings,
            extras.redstoneSaveMode()
         )
      );
      packet.setPowered(done.getUpdateType() != UpdateType.UPDATE_DATA);
      packet.setWaterlogged(false);
      return packet;
   }

   static String bedrockName(String java, String bedrock) {
      if (!bedrock.isEmpty() && !java.equals(bedrock)) {
         Identifier shown = Identifier.tryParse(bedrock);
         return java.equals(shown == null ? "" : shown.toString()) ? bedrock : java;
      } else {
         return java;
      }
   }

   public static String serverName(BlockPos pos) {
      return EXTRAS.getOrDefault(pos.asLong(), BedrockStructureBlocks.Extras.NEW).name();
   }

   private static StructureMode javaMode(StructureBlockType type) {
      return switch (type) {
         case SAVE, EXPORT -> StructureMode.SAVE;
         case LOAD -> StructureMode.LOAD;
         case CORNER -> StructureMode.CORNER;
         case DATA, INVALID -> StructureMode.DATA;
      };
   }

   private static StructureRotation rotation(Rotation rotation) {
      return switch (rotation) {
         case NONE -> StructureRotation.NONE;
         case CLOCKWISE_90 -> StructureRotation.ROTATE_90;
         case CLOCKWISE_180 -> StructureRotation.ROTATE_180;
         case COUNTERCLOCKWISE_90 -> StructureRotation.ROTATE_270;
      };
   }

   private static <T> T safe(T[] values, int ordinal, T fallback) {
      return ordinal >= 0 && ordinal < values.length ? values[ordinal] : fallback;
   }

   private record Extras(
      String name,
      boolean includePlayers,
      boolean removeBlocks,
      StructureAnimationMode animationMode,
      float animationSeconds,
      Vector3f pivot,
      StructureRedstoneSaveMode redstoneSaveMode
   ) {
      private static final BedrockStructureBlocks.Extras NEW = new BedrockStructureBlocks.Extras(
         "", false, false, StructureAnimationMode.NONE, 0.0F, Vector3f.ZERO, StructureRedstoneSaveMode.SAVES_TO_MEMORY
      );
   }
}
