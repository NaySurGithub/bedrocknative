package net.bedrocknative.client.world;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.world.item.ItemMappings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.decoration.GlowItemFrame;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.MovingPistonBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.PistonType;
import org.cloudburstmc.nbt.NbtMap;

public final class BedrockItemFrames {
   private static final Map<BlockPos, ItemFrame> FRAMES = new HashMap<>();
   private static ClientLevel owner;
   private static int nextId = -1000000;
   private static int ticks;
   private static int logged;

   private BedrockItemFrames() {
   }

   public static int count() {
      return FRAMES.size();
   }

   public static void load(ClientLevel level, BlockPos pos, NbtMap tag) {
      BlockState state = level.getBlockState(pos);
      if (state.is(Blocks.MOVING_PISTON)) {
         if (owner != level) {
            FRAMES.clear();
            owner = level;
         }

         Direction facing = (Direction)state.getValue(MovingPistonBlock.FACING);
         ItemFrame frame = FRAMES.get(pos);
         if (frame == null || frame.isRemoved() || frame.getDirection() != facing) {
            if (frame != null) {
               frame.discard();
            }

            frame = (ItemFrame)(state.getValue(MovingPistonBlock.TYPE) == PistonType.STICKY
               ? new GlowItemFrame(level, pos, facing)
               : new ItemFrame(level, pos, facing));
            frame.setId(nextId--);
            level.addEntity(frame);
            FRAMES.put(pos.immutable(), frame);
         }

         NbtMap item = tag.getCompound("Item", NbtMap.EMPTY);
         ItemStack stack = item.isEmpty() ? ItemStack.EMPTY : ItemMappings.fromNbt(item);
         frame.setItem(stack, false);
         float degrees = tag.get("ItemRotation") instanceof Number rotation ? rotation.floatValue() : 0.0F;
         frame.setRotation(Math.round(degrees / (stack.is(Items.FILLED_MAP) ? 90.0F : 45.0F)));
      }
   }

   public static void tick() {
      if (!FRAMES.isEmpty() && ++ticks % 20 == 0) {
         if (FRAMES.size() > logged) {
            logged = FRAMES.size();
            long maps = FRAMES.values().stream().filter(frame -> frame.getItem().is(Items.FILLED_MAP)).count();
            BedrockNative.LOGGER.info("Bedrock item frames: {} shown ({} with a map)", logged, maps);
         }

         ClientLevel level = Minecraft.getInstance().level;
         if (level != owner) {
            FRAMES.clear();
            owner = level;
         } else {
            Iterator<Entry<BlockPos, ItemFrame>> it = FRAMES.entrySet().iterator();

            while (it.hasNext()) {
               Entry<BlockPos, ItemFrame> entry = it.next();
               BlockPos pos = entry.getKey();
               if (!level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4) || !level.getBlockState(pos).is(Blocks.MOVING_PISTON)) {
                  entry.getValue().discard();
                  it.remove();
               }
            }
         }
      }
   }
}
