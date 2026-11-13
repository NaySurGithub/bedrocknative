package net.bedrocknative.world.block;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.ToIntFunction;
import net.bedrocknative.protocol.BedrockProtocols;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtType;
import org.cloudburstmc.protocol.bedrock.data.BlockPropertyData;
import org.jspecify.annotations.Nullable;

public final class ServerBlockPalette {
   static final ServerBlockPalette VANILLA_HASHED = new ServerBlockPalette(
      BedrockProtocols.latest().protocol(), true, null, null, new Int2ObjectOpenHashMap(), List.of(), 0, 0, null
   );
   static final int EXTRA_ID_BASE = 1073741824;
   private static final int EXAMPLES = 5;
   private final int protocol;
   private final boolean hashed;
   private final int[] hashByIndex;
   private final Int2IntOpenHashMap indexByHash;
   private final Int2ObjectOpenHashMap<BedrockBlockState> custom;
   private final List<BedrockBlockState> customOrder;
   private final int excludedBlocks;
   private final int excludedStates;
   private final int @Nullable [] withAllVanillaHashByIndex;
   private List<String> archetypes = List.of();
   private final ConcurrentHashMap<NbtMap, Integer> persistentIds = new ConcurrentHashMap<>();
   private final ConcurrentHashMap<Integer, Integer> extraIdByHash = new ConcurrentHashMap<>();
   private final ConcurrentHashMap<Integer, Integer> extraHashById = new ConcurrentHashMap<>();
   private final AtomicInteger nextExtraId = new AtomicInteger();
   private final EnumMap<ServerBlockPalette.Resolution, Integer> resolutions = new EnumMap<>(ServerBlockPalette.Resolution.class);
   private final EnumMap<ServerBlockPalette.Resolution, List<String>> examples = new EnumMap<>(ServerBlockPalette.Resolution.class);

   private ServerBlockPalette(
      int protocol,
      boolean hashed,
      int[] hashByIndex,
      Int2IntOpenHashMap indexByHash,
      Int2ObjectOpenHashMap<BedrockBlockState> custom,
      List<BedrockBlockState> customOrder,
      int excludedBlocks,
      int excludedStates,
      int @Nullable [] withAllVanillaHashByIndex
   ) {
      this.protocol = protocol;
      this.hashed = hashed;
      this.hashByIndex = hashByIndex;
      this.indexByHash = indexByHash;
      this.custom = custom;
      this.customOrder = customOrder;
      this.excludedBlocks = excludedBlocks;
      this.excludedStates = excludedStates;
      this.withAllVanillaHashByIndex = withAllVanillaHashByIndex;
   }

   static ServerBlockPalette create(boolean hashed, List<BedrockBlockState> vanilla, Collection<BlockPropertyData> definitions) {
      return create(BedrockProtocols.latest().protocol(), hashed, vanilla, definitions, BedrockBlockPalette.dataDrivenBlocks());
   }

   static ServerBlockPalette create(boolean hashed, List<BedrockBlockState> vanilla, Collection<BlockPropertyData> definitions, Set<String> dataDriven) {
      return create(BedrockProtocols.latest().protocol(), hashed, vanilla, definitions, dataDriven);
   }

   static ServerBlockPalette create(
      int protocol, boolean hashed, List<BedrockBlockState> vanilla, Collection<BlockPropertyData> definitions, Set<String> dataDriven
   ) {
      Set<String> vanillaNames = new HashSet<>();
      vanilla.forEach(state -> vanillaNames.add(state.name()));
      Set<String> defined = new HashSet<>();
      List<BedrockBlockState> customStates = new ArrayList<>();
      List<String> archetypes = new ArrayList<>();

      for (BlockPropertyData definition : definitions) {
         defined.add(definition.getName());
         if (!vanillaNames.contains(definition.getName())) {
            customStates.addAll(customStates(definition.getName(), definition.getProperties()));
            if (definition.getProperties().getCompound("vanilla_block_data", NbtMap.EMPTY).containsKey("block_archetype")) {
               archetypes.add(definition.getName());
            }
         }
      }

      Int2ObjectOpenHashMap<BedrockBlockState> custom = new Int2ObjectOpenHashMap(customStates.size());
      customStates.forEach(state -> custom.put(state.networkHash(), state));
      if (hashed) {
         ServerBlockPalette palette = new ServerBlockPalette(protocol, true, null, null, custom, List.copyOf(customStates), 0, 0, null);
         palette.archetypes = List.copyOf(archetypes);
         return palette;
      }

      List<BedrockBlockState> numbered = new ArrayList<>(vanilla.size());
      Set<String> excluded = new HashSet<>();

      for (BedrockBlockState state : vanilla) {
         if (dataDriven.contains(state.name()) && !defined.contains(state.name())) {
            excluded.add(state.name());
         } else {
            numbered.add(state);
         }
      }

      int[] hashByIndex = sortedHashes(numbered, customStates);
      Int2IntOpenHashMap indexByHash = new Int2IntOpenHashMap(hashByIndex.length);

      for (int i = 0; i < hashByIndex.length; i++) {
         indexByHash.put(hashByIndex[i], i);
      }

      indexByHash.defaultReturnValue(indexByHash.get(BedrockBlockHasher.hash("minecraft:air", new TreeMap<>())));
      int excludedStates = vanilla.size() - numbered.size();
      ServerBlockPalette palette = new ServerBlockPalette(
         protocol,
         false,
         hashByIndex,
         indexByHash,
         custom,
         List.copyOf(customStates),
         excluded.size(),
         excludedStates,
         excludedStates > 0 ? sortedHashes(vanilla, customStates) : null
      );
      palette.archetypes = List.copyOf(archetypes);
      return palette;
   }

   private static int[] sortedHashes(List<BedrockBlockState> vanilla, List<BedrockBlockState> customs) {
      List<BedrockBlockState> all = new ArrayList<>(vanilla.size() + customs.size());
      all.addAll(vanilla);
      all.addAll(customs);
      Map<String, Long> nameHashes = new HashMap<>();
      all.forEach(state -> nameHashes.computeIfAbsent(state.name(), ServerBlockPalette::fnv1_64));
      all.sort(Comparator.comparing(state -> nameHashes.get(state.name()), Long::compareUnsigned));
      int[] hashes = new int[all.size()];

      for (int i = 0; i < hashes.length; i++) {
         hashes[i] = all.get(i).networkHash();
      }

      return hashes;
   }

   public String numbering() {
      String archetypeNote = this.archetypes.isEmpty()
         ? ""
         : "; "
            + this.archetypes.size()
            + " server blocks name a block archetype, whose states are not generated ("
            + this.archetypes.subList(0, Math.min(5, this.archetypes.size()))
            + ")";
      return this.hashed
         ? "hashed" + archetypeNote
         : "by palette index ("
            + this.hashByIndex.length
            + " states"
            + (
               this.excludedBlocks > 0
                  ? ", without the " + this.excludedBlocks + " data-driven vanilla blocks the server does not define: " + this.excludedStates + " states"
                  : ""
            )
            + ")"
            + archetypeNote;
   }

   int numberedStates() {
      return this.hashed ? 0 : this.hashByIndex.length;
   }

   boolean numbers(int networkId, boolean withAllVanilla) {
      if (this.hashed) {
         return true;
      }

      int[] numbering = withAllVanilla && this.withAllVanillaHashByIndex != null ? this.withAllVanillaHashByIndex : this.hashByIndex;
      return networkId >= 0 && networkId < numbering.length || !withAllVanilla && this.extraHashById.containsKey(networkId);
   }

   public boolean hasOtherNumbering() {
      return this.withAllVanillaHashByIndex != null;
   }

   int hashWithAllVanilla(int networkId) {
      int[] other = this.withAllVanillaHashByIndex;
      if (other == null) {
         return this.hashOf(networkId);
      } else {
         return networkId >= 0 && networkId < other.length ? other[networkId] : -2;
      }
   }

   static List<BedrockBlockState> customStates(String name, NbtMap definition) {
      List<String> keys = new ArrayList<>();
      List<List<?>> values = new ArrayList<>();

      for (NbtMap property : definition.getList("properties", NbtType.COMPOUND, List.of())) {
         List<?> options = enumValues(property);
         if (!options.isEmpty()) {
            keys.add(property.getString("name"));
            values.add(options);
         }
      }

      for (NbtMap trait : definition.getList("traits", NbtType.COMPOUND, List.of())) {
         traitStates(trait).forEach((key, optionsx) -> {
            keys.add(key);
            values.add(optionsx);
         });
      }

      int total = 1;

      for (List<?> options : values) {
         total = Math.multiplyExact(total, options.size());
      }

      List<BedrockBlockState> states = new ArrayList<>(total);

      for (int i = 0; i < total; i++) {
         TreeMap<String, Object> state = new TreeMap<>();
         int rest = i;

         for (int p = 0; p < keys.size(); p++) {
            List<?> options = values.get(p);
            state.put(keys.get(p), options.get(rest % options.size()));
            rest /= options.size();
         }

         states.add(new BedrockBlockState(name, state, BedrockBlockHasher.hash(name, state)));
      }

      return states;
   }

   static Map<String, List<?>> traitStates(NbtMap trait) {
      NbtMap enabled = trait.getCompound("enabled_states", NbtMap.EMPTY);
      Map<String, List<?>> states = new LinkedHashMap<>();
      switch (trait.getString("name")) {
         case "minecraft:placement_direction":
            if (enabled.getBoolean("cardinal_direction", false)) {
               states.put("minecraft:cardinal_direction", List.of("south", "west", "north", "east"));
            }

            if (enabled.getBoolean("facing_direction", false)) {
               states.put("minecraft:facing_direction", List.of("down", "up", "north", "south", "west", "east"));
            }

            if (enabled.getBoolean("corner_and_cardinal_direction", false)) {
               states.put("minecraft:cardinal_direction", List.of("south", "west", "north", "east"));
               states.put("minecraft:corner", List.of("none", "inner_left", "inner_right", "outer_left", "outer_right"));
            }
            break;
         case "minecraft:placement_position":
            if (enabled.getBoolean("block_face", false)) {
               states.put("minecraft:block_face", List.of("down", "up", "north", "south", "west", "east"));
            }

            if (enabled.getBoolean("vertical_half", false)) {
               states.put("minecraft:vertical_half", List.of("bottom", "top"));
            }
            break;
         case "minecraft:connection":
            if (enabled.getBoolean("cardinal_connections", false)) {
               for (String side : List.of("north", "east", "south", "west")) {
                  states.put("minecraft:connection_" + side, List.of((byte)0, (byte)1));
               }
            }
            break;
         case "minecraft:multi_block":
            int parts = trait.getInt("parts", 0);
            if (parts > 0) {
               List<Integer> indices = new ArrayList<>(parts);

               for (int i = 0; i < parts; i++) {
                  indices.add(i);
               }

               states.put("minecraft:multi_block_part", indices);
            }
      }

      return states;
   }

   private static List<?> enumValues(NbtMap property) {
      if (property.get("enum") instanceof List<?> options && !options.isEmpty()) {
         List<Object> values = new ArrayList<>(options.size());

         for (Object option : options) {
            values.add(option instanceof Boolean bool ? (byte)(bool ? 1 : 0) : option);
         }

         return values;
      } else {
         return List.of();
      }
   }

   static long fnv1_64(String name) {
      long hash = -3750763034362895579L;

      for (byte b : name.getBytes(StandardCharsets.UTF_8)) {
         hash *= 1099511628211L;
         hash ^= b & 0xFF;
      }

      return hash;
   }

   public boolean hashed() {
      return this.hashed;
   }

   public int protocol() {
      return this.protocol;
   }

   public int customStateCount() {
      return this.custom.size();
   }

   public List<BedrockBlockState> customOrder() {
      return this.customOrder;
   }

   int hashOf(int networkId) {
      if (this.hashed) {
         return networkId;
      }

      if (networkId >= 0 && networkId < this.hashByIndex.length) {
         return this.hashByIndex[networkId];
      }

      Integer extra = networkId >= 1073741824 ? this.extraHashById.get(networkId) : null;
      return extra != null ? extra : -2;
   }

   int networkIdOf(int hash) {
      return this.hashed ? hash : this.indexByHash.get(hash);
   }

   int idForHash(int hash) {
      if (this.hashed) {
         return hash;
      } else {
         return this.indexByHash.containsKey(hash) ? this.indexByHash.get(hash) : this.extraIdByHash.computeIfAbsent(hash, key -> {
            int id = 1073741824 + this.nextExtraId.getAndIncrement();
            this.extraHashById.put(id, key);
            return id;
         });
      }
   }

   int persistentId(NbtMap state, ToIntFunction<NbtMap> resolve) {
      Integer known = this.persistentIds.get(state);
      return known != null ? known : (Integer)this.persistentIds.computeIfAbsent(state, resolve::applyAsInt);
   }

   synchronized void resolved(ServerBlockPalette.Resolution resolution, String example) {
      this.resolutions.merge(resolution, 1, Integer::sum);
      List<String> kept = this.examples.computeIfAbsent(resolution, key -> new ArrayList<>());
      if (kept.size() < 5) {
         kept.add(example);
      }
   }

   public synchronized int resolvedCount(ServerBlockPalette.Resolution resolution) {
      return this.resolutions.getOrDefault(resolution, 0);
   }

   public synchronized String persistentSummary() {
      int total = this.resolutions.values().stream().mapToInt(Integer::intValue).sum();
      if (total == 0) {
         return "none";
      }

      StringBuilder summary = new StringBuilder()
         .append(total)
         .append(" distinct: ")
         .append(this.resolvedCount(ServerBlockPalette.Resolution.AS_SENT))
         .append(" as sent");
      this.appendKind(summary, ServerBlockPalette.Resolution.UPGRADED, "upgraded");
      this.appendKind(summary, ServerBlockPalette.Resolution.CLOSEST, "closest state of their block");
      this.appendKind(summary, ServerBlockPalette.Resolution.UNKNOWN, "unknown");
      return summary.toString();
   }

   private void appendKind(StringBuilder summary, ServerBlockPalette.Resolution resolution, String label) {
      int count = this.resolvedCount(resolution);
      summary.append(", ").append(count).append(' ').append(label);
      if (count > 0) {
         summary.append(" (e.g. ").append(this.examples.get(resolution)).append(')');
      }
   }

   BedrockBlockState custom(int hash) {
      return (BedrockBlockState)this.custom.get(hash);
   }

   public enum Resolution {
      AS_SENT,
      UPGRADED,
      CLOSEST,
      UNKNOWN;
   }
}
