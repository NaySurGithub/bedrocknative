package net.bedrocknative.client.world;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.world.block.BeaconRules;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import org.cloudburstmc.nbt.NbtMap;

public final class BedrockBeacons {
   private static final Map<ClientLevel, Map<Long, BedrockBeacons.Powers>> POWERS = Collections.synchronizedMap(new WeakHashMap<>());

   private BedrockBeacons() {
   }

   static void record(ClientLevel level, BlockPos pos, NbtMap tag) {
      BedrockBeacons.Powers powers = new BedrockBeacons.Powers(number(tag, "primary"), number(tag, "secondary"));
      BedrockNative.LOGGER.debug("Beacon block entity data at {}: {}", pos, powers);
      of(level).put(pos.asLong(), powers);
   }

   public static BedrockBeacons.Powers powersAt(ClientLevel level, BlockPos pos) {
      Map<Long, BedrockBeacons.Powers> powers = of(level);
      if (!level.getBlockState(pos).is(Blocks.BEACON)) {
         powers.remove(pos.asLong());
         return BedrockBeacons.Powers.NONE;
      } else {
         return powers.getOrDefault(pos.asLong(), BedrockBeacons.Powers.NONE);
      }
   }

   private static Map<Long, BedrockBeacons.Powers> of(ClientLevel level) {
      synchronized (POWERS) {
         return POWERS.computeIfAbsent(level, ignored -> Collections.synchronizedMap(new HashMap<>()));
      }
   }

   public static int levels(ClientLevel level, BlockPos pos) {
      MutableBlockPos at = new MutableBlockPos();
      return BeaconRules.pyramidLevels(
         pos.getX(), pos.getY(), pos.getZ(), level.getMinY(), (x, y, z) -> level.getBlockState(at.set(x, y, z)).is(BlockTags.BEACON_BASE_BLOCKS)
      );
   }

   private static int number(NbtMap tag, String key) {
      return tag.get(key) instanceof Number number ? number.intValue() : 0;
   }

   public record Powers(int primary, int secondary) {
      public static final BedrockBeacons.Powers NONE = new BedrockBeacons.Powers(0, 0);
   }
}
