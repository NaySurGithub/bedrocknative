package net.bedrocknative.client.world;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.world.block.custom.CarrierBlock;
import net.bedrocknative.world.block.custom.CustomBlockState;
import net.bedrocknative.world.block.custom.CustomBlocks;
import net.bedrocknative.world.chunk.DimensionRange;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property.Value;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.cloudburstmc.math.vector.Vector2f;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.packet.StartGamePacket;
import org.jspecify.annotations.Nullable;

public final class SpawnCheck {
   private static final int MAX_LINES = 4;
   private static final int GIVE_UP_TICKS = 200;
   private static final int SETTLE_TICKS = 10;
   private static final int GROUND_SEARCH = 64;
   private static volatile SpawnCheck.@Nullable Look last;
   private @Nullable String pending;
   private int lines;
   private int waited;
   private int settled;
   private static final Set<Integer> TRACED_CUSTOM_SLOTS = ConcurrentHashMap.newKeySet();

   public static SpawnCheck.@Nullable Look last() {
      return last;
   }

   public void reset() {
      this.pending = null;
      this.lines = 0;
   }

   public void arm(String reason) {
      if (this.pending == null && this.lines < 4) {
         this.pending = reason;
         this.waited = 0;
         this.settled = 0;
      }
   }

   public void tick(@Nullable ClientLevel level, @Nullable LocalPlayer player) {
      String reason = this.pending;
      if (reason != null && level != null && player != null) {
         int chunkX = Mth.floor(player.getX()) >> 4;
         int chunkZ = Mth.floor(player.getZ()) >> 4;
         if (!level.hasChunk(chunkX, chunkZ)) {
            if (++this.waited >= 200) {
               this.pending = null;
               this.lines++;
               String text = String.format(
                  Locale.ROOT,
                  "Spawn check (%s): the player's column (%d, %d) did not arrive in %d s; the player is at %s",
                  reason,
                  chunkX,
                  chunkZ,
                  10,
                  position(player.getX(), player.getY(), player.getZ())
               );
               last = new SpawnCheck.Look(text, false);
               BedrockNative.LOGGER.warn(text);
            }
         } else if (++this.settled >= 10) {
            this.pending = null;
            this.lines++;
            LevelChunk chunk = level.getChunk(chunkX, chunkZ);
            SpawnCheck.Look look = look(player.getX(), player.getY(), player.getZ(), level.getMinY(), levelBlocks(level, player));
            String text = String.format(
               Locale.ROOT,
               "Spawn check (%s): %s; column (%d, %d): %s; %d block entities in the 3x3 columns around",
               reason,
               look.text(),
               chunkX,
               chunkZ,
               sections(level, chunk),
               blockEntitiesAround(level, chunkX, chunkZ)
            );
            last = new SpawnCheck.Look(text, look.inside());
            if (look.inside()) {
               inferCustomTop(level, player);
               BedrockNative.LOGGER.warn(text);
            } else {
               BedrockNative.LOGGER.info(text);
            }
         }
      }
   }

   static SpawnCheck.Look look(double x, double y, double z, int minY, SpawnCheck.Blocks blocks) {
      int blockX = Mth.floor(x);
      int feetY = Mth.floor(y);
      int blockZ = Mth.floor(z);
      boolean feetSolid = blocks.solid(blockX, feetY, blockZ);
      boolean inside = blocks.playerInside(blockX, feetY, blockZ);
      String feet = blocks.name(blockX, feetY, blockZ);
      String head = blocks.name(blockX, feetY + 1, blockZ);
      StringBuilder text = new StringBuilder();
      if (inside) {
         text.append("the player is INSIDE solid blocks: ");
      }

      text.append("feet at ").append(position(x, y, z)).append(" in ").append(feet).append(", head in ").append(head);
      if (!feetSolid || !inside) {
         int bottom = Math.max(minY, feetY - 64);
         int groundY = Integer.MIN_VALUE;

         for (int at = feetSolid ? feetY : feetY - 1; at >= bottom; at--) {
            if (blocks.solid(blockX, at, blockZ)) {
               groundY = at;
               break;
            }
         }

         if (groundY == Integer.MIN_VALUE) {
            text.append(", no ground in the ").append(feetY - bottom).append(" blocks below");
         } else {
            text.append(
               String.format(
                  Locale.ROOT,
                  ", ground %s at y %d (%.2f below the feet)",
                  blocks.name(blockX, groundY, blockZ),
                  groundY,
                  y - (groundY + blocks.top(blockX, groundY, blockZ))
               )
            );
         }
      }

      return new SpawnCheck.Look(text.toString(), inside);
   }

   private static void inferCustomTop(ClientLevel level, LocalPlayer player) {
      BlockPos pos = BlockPos.containing(player.getX(), player.getY(), player.getZ());
      BlockState state = level.getBlockState(pos);
      if (state.getBlock() instanceof CarrierBlock) {
         double top = player.getY() - pos.getY();
         if (top > 0.0 && top < 1.0) {
            CustomBlocks.limitCollisionTop(pos, (float)top);
         }
      }
   }

   private static SpawnCheck.Blocks levelBlocks(final ClientLevel level, final LocalPlayer player) {
      return new SpawnCheck.Blocks() {
         @Override
         public String name(int x, int y, int z) {
            BlockState state = level.getBlockState(new BlockPos(x, y, z));
            String name = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
            String values = state.getValues().map(Value::toString).collect(Collectors.joining(","));
            if (state.getBlock() instanceof CarrierBlock) {
               int slot = state.getValue(CarrierBlock.SLOT);
               if (SpawnCheck.TRACED_CUSTOM_SLOTS.add(slot)) {
                  CustomBlockState data = CarrierBlock.data(state);
                  BedrockNative.LOGGER
                     .info(
                        "Custom block slot {} at ({}, {}, {}) {}: collision {}, selection {}",
                        new Object[]{
                           slot,
                           x,
                           y,
                           z,
                           data == null ? "placeholder" : data.state().name(),
                           data == null ? "placeholder (full)" : data.collision(),
                           data == null ? "placeholder" : data.selection()
                        }
                     );
               }
            }

            return values.isEmpty() ? name : name + "[" + values + "]";
         }

         @Override
         public boolean solid(int x, int y, int z) {
            BlockPos pos = new BlockPos(x, y, z);
            return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
         }

         @Override
         public double top(int x, int y, int z) {
            BlockPos pos = new BlockPos(x, y, z);
            VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
            return shape.isEmpty() ? 0.0 : shape.max(Axis.Y);
         }

         @Override
         public boolean playerInside(int x, int feetY, int z) {
            return !level.noBlockCollision(player, player.getBoundingBox().deflate(0.001));
         }
      };
   }

   private static int blockEntitiesAround(ClientLevel level, int chunkX, int chunkZ) {
      int count = 0;

      for (int x = chunkX - 1; x <= chunkX + 1; x++) {
         for (int z = chunkZ - 1; z <= chunkZ + 1; z++) {
            if (level.hasChunk(x, z)) {
               count += level.getChunk(x, z).getBlockEntities().size();
            }
         }
      }

      return count;
   }

   private static String sections(ClientLevel level, LevelChunk chunk) {
      LevelChunkSection[] sections = chunk.getSections();
      int count = 0;
      int lowest = Integer.MAX_VALUE;
      int highest = Integer.MIN_VALUE;

      for (int i = 0; i < sections.length; i++) {
         if (!sections[i].hasOnlyAir()) {
            count++;
            int minY = level.getSectionYFromSectionIndex(i) << 4;
            lowest = Math.min(lowest, minY);
            highest = Math.max(highest, minY + 15);
         }
      }

      return count == 0 ? "no blocks" : count + " sections with blocks, y " + lowest + " to " + highest;
   }

   private static String position(double x, double y, double z) {
      return String.format(Locale.ROOT, "(%.2f, %.2f, %.2f)", x, y, z);
   }

   public static String startGameLine(StartGamePacket start, DimensionType type, DimensionRange range) {
      return startGameLine(start, type.minY(), type.height(), range);
   }

   static String startGameLine(StartGamePacket start, int minY, int height, DimensionRange range) {
      Vector3f eyes = start.getPlayerPosition();
      Vector2f rotation = start.getRotation();
      Vector3i spawn = start.getDefaultSpawn();
      StringBuilder text = new StringBuilder();
      text.append("dimension ").append(start.getDimensionId()).append(", generator ").append(start.getGeneratorId());
      if (eyes != null) {
         text.append(", player at ")
            .append(position(eyes.getX(), eyes.getY(), eyes.getZ()))
            .append(" (eyes; feet y ")
            .append(String.format(Locale.ROOT, "%.2f", eyes.getY() - 1.62001F))
            .append(')');
      }

      if (rotation != null) {
         text.append(String.format(Locale.ROOT, ", pitch %.1f yaw %.1f", rotation.getX(), rotation.getY()));
      }

      if (spawn != null) {
         text.append(", world spawn (").append(spawn.getX()).append(", ").append(spawn.getY()).append(", ").append(spawn.getZ()).append(')');
      }

      text.append(", heights ")
         .append(minY)
         .append(" to ")
         .append(minY + height)
         .append(" (sub-chunks ")
         .append(range.firstSection())
         .append(" to ")
         .append(range.firstSection() + range.sectionCount() - 1)
         .append(" asked)");
      long checksum = start.getBlockRegistryChecksum();
      text.append(", block registry checksum ").append(checksum == 0L ? "none" : "0x" + Long.toHexString(checksum));
      return text.toString();
   }

   interface Blocks {
      String name(int var1, int var2, int var3);

      boolean solid(int var1, int var2, int var3);

      default double top(int x, int y, int z) {
         return this.solid(x, y, z) ? 1.0 : 0.0;
      }

      default boolean playerInside(int x, int feetY, int z) {
         return this.solid(x, feetY, z) || this.solid(x, feetY + 1, z);
      }
   }

   public record Look(String text, boolean inside) {
   }
}
