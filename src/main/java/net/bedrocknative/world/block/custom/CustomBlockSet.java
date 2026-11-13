package net.bedrocknative.world.block.custom;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.world.block.BedrockBlockState;
import org.jspecify.annotations.Nullable;

public final class CustomBlockSet {
   public static final CustomBlockSet EMPTY = new CustomBlockSet(new Int2IntOpenHashMap(), new CustomBlockState[0], Map.of(), 0, List.of());
   private static volatile CustomBlockSet current = EMPTY;
   private final Int2IntOpenHashMap slotByHash;
   private final CustomBlockState[] bySlot;
   private final Map<String, Integer> firstSlotByBlock;
   private final int overflow;
   private final List<String> overflowBlocks;

   private CustomBlockSet(
      Int2IntOpenHashMap slotByHash, CustomBlockState[] bySlot, Map<String, Integer> firstSlotByBlock, int overflow, List<String> overflowBlocks
   ) {
      this.slotByHash = slotByHash;
      this.bySlot = bySlot;
      this.firstSlotByBlock = firstSlotByBlock;
      this.overflow = overflow;
      this.overflowBlocks = overflowBlocks;
      slotByHash.defaultReturnValue(-1);
   }

   public static CustomBlockSet current() {
      return current;
   }

   public static void use(CustomBlockSet set) {
      current = set;
      CustomBlocks.resetCollisionTops();
   }

   public static CustomBlockSet assign(List<BedrockBlockState> states, Map<String, CustomBlockDefinition> definitions, int capacity) {
      Int2IntOpenHashMap slotByHash = new Int2IntOpenHashMap(Math.min(states.size(), capacity));
      List<CustomBlockState> assigned = new ArrayList<>(Math.min(states.size(), capacity));
      Map<String, Integer> firstSlot = new HashMap<>();
      Set<String> overflowBlocks = new LinkedHashSet<>();

      for (BedrockBlockState state : states) {
         if (!slotByHash.containsKey(state.networkHash())) {
            if (assigned.size() >= capacity) {
               overflowBlocks.add(state.name());
            } else {
               CustomBlockDefinition definition = definitions.get(state.name());

               CustomBlockState resolved;
               try {
                  resolved = definition != null ? definition.resolve(state) : CustomBlockState.read(state, Map.of());
               } catch (RuntimeException var12) {
                  BedrockNative.LOGGER.warn("Couldn't read custom block state {}; it keeps Bedrock's defaults", state.toStateString(), var12);
                  resolved = CustomBlockState.read(state, Map.of());
               }

               slotByHash.put(state.networkHash(), assigned.size());
               firstSlot.putIfAbsent(state.name(), assigned.size());
               assigned.add(resolved);
            }
         }
      }

      int overflow = states.size() - assigned.size();
      if (overflow > 0) {
         BedrockNative.LOGGER
            .warn("{} custom block states but {} carrier slots: {} states stay the placeholder", new Object[]{states.size(), capacity, overflow});
      }

      return new CustomBlockSet(slotByHash, assigned.toArray(CustomBlockState[]::new), Map.copyOf(firstSlot), overflow, List.copyOf(overflowBlocks));
   }

   public List<String> overflowBlocks() {
      return this.overflowBlocks;
   }

   public int slot(int hash) {
      return this.slotByHash.get(hash);
   }

   @Nullable
   public CustomBlockState data(int slot) {
      return slot >= 0 && slot < this.bySlot.length ? this.bySlot[slot] : null;
   }

   public int firstSlot(String block) {
      return this.firstSlotByBlock.getOrDefault(block, -1);
   }

   public Collection<String> blocks() {
      return this.firstSlotByBlock.keySet();
   }

   public int size() {
      return this.bySlot.length;
   }

   public int overflow() {
      return this.overflow;
   }
}
