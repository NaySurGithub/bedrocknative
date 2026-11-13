package net.bedrocknative.client.world;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.edit.BedrockSigns;
import net.bedrocknative.client.inventory.BedrockCrafters;
import net.bedrocknative.client.inventory.BedrockLecterns;
import net.bedrocknative.text.BedrockText;
import net.bedrocknative.world.block.BlockMappings;
import net.bedrocknative.world.block.BlockUpdateLog;
import net.bedrocknative.world.chunk.BlockIdCheck;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler.ServerVerifiedState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.AbstractBannerBlock;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.LevelChunk.EntityCreationType;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.protocol.bedrock.data.BlockChangeEntry;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.BlockEntityDataPacket;
import org.cloudburstmc.protocol.bedrock.packet.BlockEventPacket;
import org.cloudburstmc.protocol.bedrock.packet.OpenSignPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateBlockPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateBlockSyncedPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateSubChunkBlocksPacket;
import org.jspecify.annotations.Nullable;

public final class BedrockBlocks {
   private static final int UPDATE_FLAGS = 19;
   private static final Map<DyeColor, Block> BEDS = new EnumMap<>(DyeColor.class);
   private static final Map<DyeColor, Block> BANNERS = new EnumMap<>(DyeColor.class);
   private static final Map<DyeColor, Block> WALL_BANNERS = new EnumMap<>(DyeColor.class);
   private final Minecraft minecraft;
   private int updates;
   private static volatile int synced;
   private int blockEvents;
   private final BlockUpdateLog updateLog = new BlockUpdateLog();
   private BlockUpdateLog.@Nullable Names updateNames;
   private String numbering = "";
   private static final Map<BlockPos, Long> RECENT_BREAKS;
   private static volatile boolean serverAnswered;

   public BedrockBlocks(Minecraft minecraft) {
      this.minecraft = minecraft;
   }

   public int updateCount() {
      return this.updates;
   }

   public static int syncedCount() {
      return synced;
   }

   public static boolean handles(BedrockPacket packet) {
      return packet instanceof UpdateBlockPacket
         || packet instanceof UpdateSubChunkBlocksPacket
         || packet instanceof BlockEntityDataPacket
         || packet instanceof BlockEventPacket
         || packet instanceof OpenSignPacket;
   }

   public static int[] chunkOf(BedrockPacket packet) {
      Vector3i position = switch (packet) {
         case UpdateBlockPacket update -> update.getBlockPosition();
         case BlockEntityDataPacket data -> data.getBlockPosition();
         case BlockEventPacket event -> event.getBlockPosition();
         case UpdateSubChunkBlocksPacket update -> !update.getStandardBlocks().isEmpty()
            ? update.getStandardBlocks().getFirst().getPosition()
            : (!update.getExtraBlocks().isEmpty() ? update.getExtraBlocks().getFirst().getPosition() : null);
         default -> null;
      };
      return position == null ? null : new int[]{position.getX() >> 4, position.getZ() >> 4};
   }

   public void apply(BedrockPacket packet) {
      ClientLevel level = this.minecraft.level;
      if (level != null) {
         if (packet instanceof OpenSignPacket open) {
            BedrockSigns.open(this.minecraft, level, toPos(open.getPosition()), open.isFrontSide());
         } else {
            this.updates++;
            if (packet instanceof UpdateBlockSyncedPacket) {
               synced++;
            }

            switch (packet) {
               case UpdateBlockPacket update:
                  this.logUpdate(update.getDefinition().getRuntimeId(), update.getDataLayer());
                  setBlock(level, toPos(update.getBlockPosition()), update.getDataLayer(), update.getDefinition().getRuntimeId());
                  break;
               case UpdateSubChunkBlocksPacket update:
                  for (BlockChangeEntry entry : update.getStandardBlocks()) {
                     this.logUpdate(entry.getDefinition().getRuntimeId(), 0);
                     setBlock(level, toPos(entry.getPosition()), 0, entry.getDefinition().getRuntimeId());
                  }

                  for (BlockChangeEntry entry : update.getExtraBlocks()) {
                     this.logUpdate(entry.getDefinition().getRuntimeId(), 1);
                     setBlock(level, toPos(entry.getPosition()), 1, entry.getDefinition().getRuntimeId());
                  }
                  break;
               case BlockEntityDataPacket data:
                  loadBlockEntity(level, toPos(data.getBlockPosition()), data.getData());
                  break;
               case BlockEventPacket event:
                  this.blockEvent(level, toPos(event.getBlockPosition()), event.getEventType(), event.getEventData());
                  break;
               default:
            }
         }
      }
   }

   private void blockEvent(ClientLevel level, BlockPos pos, int type, int data) {
      if (level.getBlockEntity(pos) != null) {
         this.blockEvents++;
         level.blockEvent(pos, level.getBlockState(pos).getBlock(), type, data);
      }
   }

   public int blockEventCount() {
      return this.blockEvents;
   }

   private void logUpdate(int networkId, int layer) {
      BlockUpdateLog.Names names = this.updateNames;
      if (names == null) {
         BlockMappings mappings = BlockMappings.get();
         names = this.updateNames = mappings.updateNames();
         this.numbering = mappings.numbering();
      }

      BlockUpdateLog.Lines lines = this.updateLog.record(networkId, layer, this.numbering, names);
      if (lines.ids() != null) {
         BedrockNative.LOGGER.info(lines.ids());
      }

      if (lines.warning() != null) {
         BedrockNative.LOGGER.warn(lines.warning());
      }
   }

   public static void noteLocalBreak(BlockPos pos) {
      long now = System.currentTimeMillis();
      synchronized (RECENT_BREAKS) {
         RECENT_BREAKS.entrySet().removeIf(entry -> now - entry.getValue() >= 5000L);
         RECENT_BREAKS.put(pos.immutable(), now);
      }
   }

   public static boolean takeServerAnswer() {
      boolean answered = serverAnswered;
      serverAnswered = false;
      return answered;
   }

   private static long breakAgo(BlockPos pos) {
      Long at;
      synchronized (RECENT_BREAKS) {
         at = RECENT_BREAKS.get(pos);
      }

      if (at == null) {
         return -1L;
      }

      long ago = System.currentTimeMillis() - at;
      return ago >= 0L && ago < 5000L ? ago : -1L;
   }

   private static void setBlock(ClientLevel level, BlockPos pos, int layer, int bedrockHash) {
      ServerVerifiedState known = (ServerVerifiedState)level.getBlockStatePredictionHandler().serverVerifiedStates.get(pos.asLong());
      BlockState current = known != null ? known.blockState : level.getBlockState(pos);
      BlockState mapped = BlockMappings.get().toJava(bedrockHash);
      BlockState state;
      if (layer == 0) {
         state = mapped;
         if (isWaterlogged(current) && state.hasProperty(BlockStateProperties.WATERLOGGED)) {
            state = state.setValue(BlockStateProperties.WATERLOGGED, true);
         }

         if (state.getBlock() instanceof ChestBlock && current.getBlock() == state.getBlock()) {
            state = state.setValue(ChestBlock.TYPE, current.getValue(ChestBlock.TYPE));
         }

         state = BedrockJukeboxDiscs.adjust(pos, BedrockLecterns.adjust(pos, state));
      } else {
         if (!current.hasProperty(BlockStateProperties.WATERLOGGED) || !mapped.isAir() && !mapped.is(Blocks.WATER)) {
            return;
         }

         state = current.setValue(BlockStateProperties.WATERLOGGED, mapped.is(Blocks.WATER));
      }

      long ago = breakAgo(pos);
      if (ago >= 0L) {
         BedrockNative.LOGGER
            .debug(
               "Server block update at {} {} ms after the local break: {} -> {}", new Object[]{pos.toShortString(), ago, current.getBlock(), state.getBlock()}
            );
         serverAnswered = true;
      }

      level.setServerVerifiedBlockState(pos, state, 19);
      LegacyShapes.blockChanged(level, pos);
      if (current.getBlock() instanceof ChestBlock || state.getBlock() instanceof ChestBlock) {
         ChestPairs.blockChanged(new BedrockBlocks.LevelBlocks(level), pos);
      }
   }

   private static boolean isWaterlogged(BlockState state) {
      return state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED);
   }

   public static void createBlockEntities(ClientLevel level, LevelChunk chunk, Collection<Integer> sectionIndices, List<NbtMap> tags) {
      createBlockEntities(level, chunk, sectionIndices, tags, null);
   }

   public static void createBlockEntities(ClientLevel level, LevelChunk chunk, Collection<Integer> sectionIndices, List<NbtMap> tags, int @Nullable [] blocks) {
      LevelChunkSection[] sections = chunk.getSections();
      int minX = chunk.getPos().getMinBlockX();
      int minZ = chunk.getPos().getMinBlockZ();
      if (BlockIdCheck.misread()) {
         for (NbtMap tag : tags) {
            level.getBlockEntity(new BlockPos(tag.getInt("x", 0), tag.getInt("y", 0), tag.getInt("z", 0)));
         }

         sectionIndices = List.of();
      } else if (blocks != null) {
         for (int packed : blocks) {
            int index = packed >>> 12;
            if (index < sections.length) {
               int minY = SectionPos.sectionToBlockCoord(level.getSectionYFromSectionIndex(index));
               chunk.getBlockEntity(new BlockPos(minX + (packed & 15), minY + (packed >> 8 & 15), minZ + (packed >> 4 & 15)), EntityCreationType.IMMEDIATE);
            }
         }

         sectionIndices = List.of();
      }

      for (int index : sectionIndices) {
         if (index >= 0 && index < sections.length) {
            LevelChunkSection section = sections[index];
            if (!section.hasOnlyAir() && section.maybeHas(BlockStateBase::hasBlockEntity)) {
               int minY = SectionPos.sectionToBlockCoord(level.getSectionYFromSectionIndex(index));

               for (int y = 0; y < 16; y++) {
                  for (int z = 0; z < 16; z++) {
                     for (int x = 0; x < 16; x++) {
                        if (section.getBlockState(x, y, z).hasBlockEntity()) {
                           chunk.getBlockEntity(new BlockPos(minX + x, minY + y, minZ + z), EntityCreationType.IMMEDIATE);
                        }
                     }
                  }
               }
            }
         }
      }

      for (NbtMap tag : tags) {
         loadBlockEntity(level, new BlockPos(tag.getInt("x", 0), tag.getInt("y", 0), tag.getInt("z", 0)), tag);
      }
   }

   public static void removeBlockEntities(ClientLevel level, LevelChunk chunk, Collection<Integer> sectionIndices) {
      List<BlockPos> stale = new ArrayList<>();

      for (BlockPos pos : chunk.getBlockEntities().keySet()) {
         if (sectionIndices.contains(level.getSectionIndex(pos.getY()))) {
            stale.add(pos);
         }
      }

      stale.forEach(chunk::removeBlockEntity);
   }

   public static void loadBlockEntity(ClientLevel level, BlockPos pos, NbtMap tag) {
      BlockState state = level.getBlockState(pos);
      switch (tag.getString("id", "")) {
         case "Sign":
         case "HangingSign":
            if (level.getBlockEntity(pos) instanceof SignBlockEntity sign) {
               sign.setText(signText(tag.getCompound("FrontText", NbtMap.EMPTY)), SignTextSlot.FRONT);
               sign.setText(signText(tag.getCompound("BackText", NbtMap.EMPTY)), SignTextSlot.BACK);
               sign.setWaxed(tag.getBoolean("IsWaxed", false));
               BedrockSigns.record(sign, tag);
            }
            break;
         case "Beacon":
            BedrockBeacons.record(level, pos, tag);
            break;
         case "Crafter":
            BedrockCrafters.record(pos, tag);
            break;
         case "CommandBlock":
            BedrockCommandBlocks.load(level, pos, tag);
            break;
         case "StructureBlock":
            BedrockStructureBlocks.load(level, pos, tag);
            break;
         case "JigsawBlock":
            BedrockJigsawBlocks.load(level, pos, tag);
            break;
         case "Lectern":
            BedrockLecterns.load(level, pos, tag);
            break;
         case "DecoratedPot":
            BedrockDecoratedPots.load(level, pos, tag);
            break;
         case "Jukebox":
            BedrockJukeboxDiscs.load(level, pos, tag);
            break;
         case "ItemFrame":
         case "GlowItemFrame":
            BedrockItemFrames.load(level, pos, tag);
            break;
         case "Bed":
            recolour(level, pos, state, BEDS.get(DyeColor.byId(tag.getByte("color", (byte)DyeColor.RED.getId()))));
            break;
         case "Banner":
            DyeColor base = DyeColor.byId(15 - tag.getInt("Base", 0));
            recolour(level, pos, state, (state.getBlock() instanceof BannerBlock ? BANNERS : WALL_BANNERS).get(base));
            break;
         case "Chest":
            ChestPairs.claim(new BedrockBlocks.LevelBlocks(level), pos, ChestPairs.partnerOf(pos, tag));
            break;
         case "Skull":
            if (state.hasProperty(BlockStateProperties.ROTATION_16)) {
               int rotation = Math.floorMod(Math.round(tag.getFloat("Rotation", 0.0F) / 22.5F), 16);
               setIfChanged(level, pos, state, state.setValue(BlockStateProperties.ROTATION_16, rotation));
            }
      }

      BedrockBlockNames.record(level, pos, tag);
   }

   private static SignText signText(NbtMap text) {
      String[] lines = text.getString("Text", "").split("\n", -1);
      Component[] messages = new Component[4];

      for (int i = 0; i < messages.length; i++) {
         messages[i] = i < lines.length ? BedrockText.format(lines[i]) : Component.empty();
      }

      List<Component> list = List.of(messages);
      return new SignText(list, list, nearestDye(text.getInt("SignTextColor", -16777216)), text.getBoolean("IgnoreLighting", false));
   }

   private static DyeColor nearestDye(int argb) {
      DyeColor best = DyeColor.BLACK;
      int bestDistance = Integer.MAX_VALUE;

      for (DyeColor dye : DyeColor.values()) {
         int rgb = dye.getTextureDiffuseColor();
         int dr = (rgb >> 16 & 0xFF) - (argb >> 16 & 0xFF);
         int dg = (rgb >> 8 & 0xFF) - (argb >> 8 & 0xFF);
         int db = (rgb & 0xFF) - (argb & 0xFF);
         int distance = dr * dr + dg * dg + db * db;
         if (distance < bestDistance) {
            bestDistance = distance;
            best = dye;
         }
      }

      return best;
   }

   private static void recolour(ClientLevel level, BlockPos pos, BlockState state, Block coloured) {
      if (coloured != null && (state.getBlock() instanceof BedBlock || state.getBlock() instanceof AbstractBannerBlock)) {
         setIfChanged(level, pos, state, coloured.withPropertiesOf(state));
      }
   }

   private static void setIfChanged(ClientLevel level, BlockPos pos, BlockState current, BlockState state) {
      if (state != current) {
         level.setServerVerifiedBlockState(pos, state, 19);
      }
   }

   private static BlockPos toPos(Vector3i v) {
      return new BlockPos(v.getX(), v.getY(), v.getZ());
   }

   static {
      for (Block block : BuiltInRegistries.BLOCK) {
         if (block instanceof BedBlock bed) {
            BEDS.put(bed.getColor(), block);
         } else if (block instanceof AbstractBannerBlock banner) {
            (block instanceof BannerBlock ? BANNERS : WALL_BANNERS).put(banner.getColor(), block);
         }
      }

      RECENT_BREAKS = Collections.synchronizedMap(new LinkedHashMap<>());
   }

   private record LevelBlocks(ClientLevel level) implements ChestPairs.Blocks {
      @Override
      public BlockState state(BlockPos pos) {
         return this.level.getBlockState(pos);
      }

      @Override
      public @Nullable Object owner(BlockPos pos) {
         return this.level.getBlockEntity(pos);
      }

      @Override
      public void set(BlockPos pos, BlockState state) {
         this.level.setServerVerifiedBlockState(pos, state, 19);
      }
   }
}
