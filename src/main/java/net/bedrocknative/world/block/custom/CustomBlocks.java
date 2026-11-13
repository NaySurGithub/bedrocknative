package net.bedrocknative.world.block.custom;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.perf.ParallelRange;
import net.bedrocknative.world.sound.JavaBlockSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;

public final class CustomBlocks {
   public static final ResourceKey<Block> KEY = ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("bedrocknative", "custom_block"));
   public static final Block CARRIER = (Block)Registry.register(BuiltInRegistries.BLOCK, KEY, new CarrierBlock(Properties.of().setId(KEY).strength(1.5F)));
   private static final int REFRESH_HELPERS = Math.clamp((long)(Runtime.getRuntime().availableProcessors() - 1), 0, 3);
   private static final Executor REFRESH_HELPERS_EXECUTOR = task -> Thread.ofVirtual().name("BedrockNative block states").start(task);
   private static int refreshed;
   private static volatile SoundType[] soundTypes = new SoundType[0];
   private static final Map<BlockPos, Float> COLLISION_TOPS = new ConcurrentHashMap<>();

   private CustomBlocks() {
   }

   public static void useSoundTypes(String[] bedrockTypes) {
      SoundType[] types = new SoundType[bedrockTypes.length];

      for (int slot = 0; slot < types.length; slot++) {
         types[slot] = JavaBlockSounds.javaType(bedrockTypes[slot]);
      }

      soundTypes = types;
   }

   public static SoundType soundType(int slot) {
      SoundType[] types = soundTypes;
      return slot >= 0 && slot < types.length ? types[slot] : SoundType.STONE;
   }

   public static void register() {
      BedrockNative.LOGGER.debug("Registered {} with {} states", KEY.identifier(), 65536);
   }

   public static BlockState state(int slot) {
      return (BlockState)CARRIER.defaultBlockState().setValue(CarrierBlock.SLOT, slot);
   }

   public static void resetCollisionTops() {
      COLLISION_TOPS.clear();
   }

   public static void limitCollisionTop(BlockPos pos, float top) {
      if (top > 0.0F && top < 1.0F) {
         COLLISION_TOPS.put(pos.immutable(), top);
      }
   }

   public static float collisionTop(BlockPos pos) {
      Float top = COLLISION_TOPS.get(pos);
      return top == null ? 0.0F : top;
   }

   public static void refreshStates() {
      CustomBlockSet set = CustomBlockSet.current();
      int count = Math.max(refreshed, set.size());
      ParallelRange.run(count, slot -> {
         BlockState state = state(slot);
         CustomBlockState data = set.data(slot);
         state.lightEmission = data == null ? 0 : data.lightEmission();
         state.initCache();
      }, REFRESH_HELPERS_EXECUTOR, REFRESH_HELPERS);
      refreshed = set.size();
   }

   static {
      CarrierBlock.registered();
   }
}
