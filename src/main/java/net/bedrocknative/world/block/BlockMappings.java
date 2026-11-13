package net.bedrocknative.world.block;

import com.google.common.collect.UnmodifiableIterator;
import com.google.gson.JsonElement;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.protocol.BedrockProtocol;
import net.bedrocknative.protocol.BedrockProtocols;
import net.bedrocknative.world.block.custom.CarrierBlock;
import net.bedrocknative.world.block.custom.CustomBlockDefinition;
import net.bedrocknative.world.block.custom.CustomBlockSet;
import net.bedrocknative.world.block.custom.CustomBlockState;
import net.bedrocknative.world.block.custom.CustomBlocks;
import net.bedrocknative.world.chunk.BedrockChunkCodec;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.RedstoneWireBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.piston.MovingPistonBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.PistonType;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.StairsShape;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.protocol.bedrock.data.BlockPropertyData;
import org.jspecify.annotations.Nullable;

public final class BlockMappings {
   private static final String B2J = "/assets/bedrocknative/mappings/bedrock-1.26.30/blocksB2J.json.gz";
   private static final Map<String, BooleanProperty> CONNECTIONS = Map.of(
      "minecraft:connection_north",
      BlockStateProperties.NORTH,
      "minecraft:connection_east",
      BlockStateProperties.EAST,
      "minecraft:connection_south",
      BlockStateProperties.SOUTH,
      "minecraft:connection_west",
      BlockStateProperties.WEST,
      "minecraft:connection_up",
      BlockStateProperties.UP,
      "minecraft:connection_down",
      BlockStateProperties.DOWN
   );
   private static final Map<String, String> ALIASES = Map.ofEntries(
      Map.entry("minecraft:invisible_bedrock", "minecraft:barrier"),
      Map.entry("minecraft:underwater_torch", "minecraft:torch"),
      Map.entry("minecraft:colored_torch_blue", "minecraft:torch"),
      Map.entry("minecraft:colored_torch_green", "minecraft:torch"),
      Map.entry("minecraft:colored_torch_purple", "minecraft:torch"),
      Map.entry("minecraft:colored_torch_red", "minecraft:torch"),
      Map.entry("minecraft:underwater_tnt", "minecraft:tnt"),
      Map.entry("minecraft:deprecated_anvil", "minecraft:anvil"),
      Map.entry("minecraft:deprecated_purpur_block_1", "minecraft:purpur_block"),
      Map.entry("minecraft:deprecated_purpur_block_2", "minecraft:purpur_block"),
      Map.entry("minecraft:glowingobsidian", "minecraft:crying_obsidian"),
      Map.entry("minecraft:frame", "minecraft:air"),
      Map.entry("minecraft:glow_frame", "minecraft:air"),
      Map.entry("minecraft:client_request_placeholder_block", "minecraft:air"),
      Map.entry("minecraft:icicle", "minecraft:pointed_dripstone"),
      Map.entry("minecraft:ice_crystal", "minecraft:amethyst_cluster"),
      Map.entry("minecraft:disc_press", "minecraft:stonecutter")
   );
   private static final int UNMAPPED = Integer.MIN_VALUE;
   private static volatile BlockMappings instance;
   private volatile Map<String, NbtMap> customBlocks = Map.of();
   private final List<BedrockBlockState> palette;
   private final Int2ObjectOpenHashMap<BlockState> byHash;
   private final Map<String, BlockState> byName;
   private volatile Reference2IntOpenHashMap<BlockState> toBedrock;
   private volatile @Nullable Reference2IntOpenHashMap<BlockState> legacyToBedrock;
   private final List<String> unresolved;
   private final Int2ObjectOpenHashMap<String> unresolvedNames;
   private final BlockState fallback = Blocks.STONE.defaultBlockState();
   private final Map<Integer, Map<BlockState, Integer>> nearest = new ConcurrentHashMap<>();
   private final Set<Integer> unknownSeen = ConcurrentHashMap.newKeySet();
   private final Set<String> customSeen = ConcurrentHashMap.newKeySet();
   private final Set<String> bedrockOnlySeen = ConcurrentHashMap.newKeySet();
   private final Int2ObjectOpenHashMap<BlockState> byShapelessHash;
   private volatile boolean shapelessSeen;
   private volatile ServerBlockPalette server = ServerBlockPalette.VANILLA_HASHED;
   private volatile @Nullable Int2ObjectOpenHashMap<String> names;
   private volatile @Nullable Map<String, List<BedrockBlockState>> statesByName;
   private volatile @Nullable Int2ObjectOpenHashMap<BedrockBlockState> vanillaByHash;

   private BlockMappings(
      List<BedrockBlockState> palette,
      Int2ObjectOpenHashMap<BlockState> byHash,
      Int2ObjectOpenHashMap<BlockState> byShapelessHash,
      Map<String, BlockState> byName,
      Reference2IntOpenHashMap<BlockState> toBedrock,
      List<String> unresolved,
      Int2ObjectOpenHashMap<String> unresolvedNames
   ) {
      this.palette = palette;
      this.byShapelessHash = byShapelessHash;
      this.byHash = byHash;
      this.byName = byName;
      this.toBedrock = toBedrock;
      this.unresolved = unresolved;
      this.unresolvedNames = unresolvedNames;
      toBedrock.defaultReturnValue(toBedrock.getInt(Blocks.AIR.defaultBlockState()));
   }

   public static BlockMappings get() {
      BlockMappings result = instance;
      if (result == null) {
         synchronized (BlockMappings.class) {
            result = instance;
            if (result == null) {
               BlockMappings modern = build(unionPalette(), true);
               BlockMappings legacy = build(BedrockBlockPalette.loadBundled(BedrockProtocols.V26_52.blockPalette()), false);
               modern.legacyToBedrock = legacy.toBedrock;
               result = modern;
               instance = modern;
            }
         }
      }

      return result;
   }

   private static BedrockBlockPalette unionPalette() {
      List<BedrockBlockState> states = new ArrayList<>();
      Set<Integer> seen = new HashSet<>();

      for (BedrockProtocol protocol : BedrockProtocols.all()) {
         for (BedrockBlockState state : BedrockBlockPalette.loadBundled(protocol.blockPalette()).states()) {
            if (seen.add(state.networkHash())) {
               states.add(state);
            }
         }
      }

      return new BedrockBlockPalette(states);
   }

   public ServerBlockPalette useServerBlocks(boolean hashed, Collection<BlockPropertyData> definitions) {
      return this.useServerBlocks(hashed, definitions, BedrockProtocols.latest());
   }

   public ServerBlockPalette useServerBlocks(boolean hashed, Collection<BlockPropertyData> definitions, BedrockProtocol protocol) {
      BedrockBlockPalette version = BedrockBlockPalette.loadBundled(protocol.blockPalette());
      ServerBlockPalette created = ServerBlockPalette.create(
         protocol.protocol(), hashed, version.states(), definitions, BedrockBlockPalette.dataDrivenBlocks(protocol.blockPalette())
      );
      Map<String, NbtMap> custom = new HashMap<>();
      Map<String, CustomBlockDefinition> parsed = new HashMap<>();
      definitions.forEach(definition -> {
         custom.put(definition.getName(), definition.getProperties());
         parsed.put(definition.getName(), CustomBlockDefinition.read(definition.getName(), definition.getProperties()));
      });
      CustomBlockSet.use(CustomBlockSet.assign(created.customOrder(), parsed, 65536));
      this.server = created;
      this.customBlocks = Map.copyOf(custom);
      this.unknownSeen.clear();
      this.customSeen.clear();
      this.bedrockOnlySeen.clear();
      this.shapelessSeen = false;
      BedrockChunkCodec.useBlockStates(this::persistentNetworkId);
      return created;
   }

   public int persistentNetworkId(NbtMap blockState) {
      return this.persistentNetworkId(this.server, blockState);
   }

   int persistentNetworkId(ServerBlockPalette palette, NbtMap blockState) {
      return palette.persistentId(blockState, state -> {
         try {
            return this.resolvePersistent(palette, state);
         } catch (RuntimeException e) {
            palette.resolved(ServerBlockPalette.Resolution.UNKNOWN, state + " (" + e + ")");
            return palette.idForHash(-2);
         }
      });
   }

   public String persistentSummary() {
      return this.server.persistentSummary();
   }

   public String numbering() {
      return this.server.numbering();
   }

   public BlockUpdateLog.Names updateNames() {
      return this.updateNames(this.server);
   }

   BlockUpdateLog.Names updateNames(final ServerBlockPalette current) {
      return new BlockUpdateLog.Names() {
         @Override
         public String state(int networkId) {
            return current.numbers(networkId, false) ? BlockMappings.this.describe(current, current.hashOf(networkId), networkId) : "unknown id " + networkId;
         }

         @Override
         public @Nullable String otherState(int networkId) {
            if (!current.hasOtherNumbering()) {
               return null;
            } else {
               return current.numbers(networkId, true)
                  ? BlockMappings.this.describe(current, current.hashWithAllVanilla(networkId), networkId)
                  : "unknown id " + networkId;
            }
         }

         @Override
         public boolean airOrLiquid(int networkId) {
            return BlockMappings.airOrLiquidName(BlockMappings.this.stateOfHash(current, current.hashOf(networkId)));
         }

         @Override
         public boolean otherAirOrLiquid(int networkId) {
            return BlockMappings.airOrLiquidName(BlockMappings.this.stateOfHash(current, current.hashWithAllVanilla(networkId)));
         }
      };
   }

   private String describe(ServerBlockPalette current, int hash, int networkId) {
      BedrockBlockState state = this.stateOfHash(current, hash);
      if (state != null) {
         return state.toStateString();
      }

      String name = (String)this.names().get(hash);
      return name != null ? name + " (an older palette's state)" : "unknown id " + networkId;
   }

   private @Nullable BedrockBlockState stateOfHash(ServerBlockPalette current, int hash) {
      BedrockBlockState state = (BedrockBlockState)this.vanillaByHash().get(hash);
      return state != null ? state : current.custom(hash);
   }

   private static boolean airOrLiquidName(@Nullable BedrockBlockState state) {
      if (state != null) {
         switch (state.name()) {
            case "minecraft:air":
            case "minecraft:water":
            case "minecraft:flowing_water":
            case "minecraft:lava":
            case "minecraft:flowing_lava":
               return true;
         }
      }

      return false;
   }

   private Int2ObjectOpenHashMap<BedrockBlockState> vanillaByHash() {
      Int2ObjectOpenHashMap<BedrockBlockState> map = this.vanillaByHash;
      if (map == null) {
         map = new Int2ObjectOpenHashMap(this.palette.size());

         for (BedrockBlockState state : this.palette) {
            map.put(state.networkHash(), state);
         }

         this.vanillaByHash = map;
      }

      return map;
   }

   private int resolvePersistent(ServerBlockPalette current, NbtMap blockState) {
      String name = BedrockBlockPalette.namespaced(blockState.getString("name", ""));
      TreeMap<String, Object> states = new TreeMap<>();
      if (blockState.get("states") instanceof NbtMap tags) {
         tags.forEach((key, value) -> {
            if (value instanceof Byte || value instanceof Integer || value instanceof String) {
               states.put(key, value);
            }
         });
      }

      String sent = new BedrockBlockState(name, states, 0).toStateString();
      int hash = BedrockBlockHasher.hash(name, states);
      if (this.knownHash(current, hash)) {
         current.resolved(ServerBlockPalette.Resolution.AS_SENT, sent);
         return current.idForHash(hash);
      } else {
         BlockStateUpgrader.State upgraded = BlockStateUpgrader.bundled().upgrade(name, states, blockState.getInt("version", 0));
         int upgradedHash = BedrockBlockHasher.hash(upgraded.name(), upgraded.states());
         String upgradedString = new BedrockBlockState(upgraded.name(), upgraded.states(), 0).toStateString();
         if (upgradedHash != hash && this.knownHash(current, upgradedHash)) {
            current.resolved(ServerBlockPalette.Resolution.UPGRADED, sent + " -> " + upgradedString);
            return current.idForHash(upgradedHash);
         } else {
            BedrockBlockState closest = this.closestOfName(current, upgraded.name(), upgraded.states());
            if (closest != null) {
               current.resolved(ServerBlockPalette.Resolution.CLOSEST, sent + " -> " + closest.toStateString());
               return current.idForHash(closest.networkHash());
            } else {
               current.resolved(ServerBlockPalette.Resolution.UNKNOWN, upgradedString.equals(sent) ? sent : sent + " (upgraded " + upgradedString + ")");
               return current.idForHash(upgradedHash);
            }
         }
      }
   }

   private boolean knownHash(ServerBlockPalette current, int hash) {
      return this.byHash.containsKey(hash)
         || this.byShapelessHash.containsKey(hash)
         || this.unresolvedNames.containsKey(hash)
         || current.custom(hash) != null
         || CustomBlockSet.current().slot(hash) >= 0;
   }

   private @Nullable BedrockBlockState closestOfName(ServerBlockPalette current, String name, SortedMap<String, Object> states) {
      List<BedrockBlockState> candidates = this.statesByName().get(name);
      if (candidates == null) {
         candidates = current.customOrder().stream().filter(statex -> statex.name().equals(name)).toList();
      }

      BedrockBlockState best = null;
      int bestScore = -1;

      for (BedrockBlockState candidate : candidates) {
         int score = 0;

         for (Entry<String, Object> state : candidate.states().entrySet()) {
            if (sameValue(state.getValue(), states.get(state.getKey()))) {
               score++;
            }
         }

         if (score > bestScore) {
            best = candidate;
            bestScore = score;
         }
      }

      return best;
   }

   private static boolean sameValue(Object expected, @Nullable Object sent) {
      return expected.equals(sent) || expected instanceof Number a && sent instanceof Number b && a.intValue() == b.intValue();
   }

   private Map<String, List<BedrockBlockState>> statesByName() {
      Map<String, List<BedrockBlockState>> map = this.statesByName;
      if (map == null) {
         Map<String, List<BedrockBlockState>> built = new HashMap<>();

         for (BedrockBlockState state : this.palette) {
            built.computeIfAbsent(state.name(), key -> new ArrayList<>()).add(state);
         }

         this.statesByName = map = Map.copyOf(built);
      }

      return map;
   }

   public @Nullable NbtMap customBlock(String name) {
      return this.customBlocks.get(name);
   }

   public BlockState toJava(int networkId) {
      ServerBlockPalette current = this.server;
      int hash = current.hashOf(networkId);
      BlockState state = (BlockState)this.byHash.get(hash);
      if (state != null) {
         return state;
      }

      state = (BlockState)this.byShapelessHash.get(hash);
      if (state != null) {
         if (!this.shapelessSeen) {
            this.shapelessSeen = true;
         }

         return state;
      } else {
         int slot = CustomBlockSet.current().slot(hash);
         if (slot >= 0) {
            return CustomBlocks.state(slot);
         }

         BedrockBlockState custom = current.custom(hash);
         String bedrockOnly = (String)this.unresolvedNames.get(hash);
         if (custom != null) {
            this.customSeen.add(custom.toStateString());
         } else if (bedrockOnly != null) {
            this.bedrockOnlySeen.add(bedrockOnly);
         } else if (this.unknownSeen.add(networkId)) {
            BedrockNative.LOGGER.debug("Unknown block network id {} (hash {})", networkId, Integer.toUnsignedString(hash, 16));
         }

         return this.fallback;
      }
   }

   public BedrockBlockState customState(int networkId) {
      return this.server.custom(this.server.hashOf(networkId));
   }

   public boolean shapelessSeen() {
      return this.shapelessSeen;
   }

   public Set<String> customSeen() {
      return Set.copyOf(this.customSeen);
   }

   public Set<String> bedrockOnlySeen() {
      return Set.copyOf(this.bedrockOnlySeen);
   }

   public Set<Integer> unknownSeen() {
      return Set.copyOf(this.unknownSeen);
   }

   public int toBedrock(BlockState state) {
      Reference2IntOpenHashMap<BlockState> reverse = this.reverseMap();
      if (state.getBlock() instanceof CarrierBlock) {
         CustomBlockState custom = CarrierBlock.data(state);
         return this.server.networkIdOf(custom != null ? custom.state().networkHash() : reverse.defaultReturnValue());
      }

      int hash = reverse.getOrDefault(state, Integer.MIN_VALUE);
      if (hash == Integer.MIN_VALUE && reverse != this.toBedrock) {
         hash = this.toBedrock.getOrDefault(state, Integer.MIN_VALUE);
      }

      if (hash != Integer.MIN_VALUE) {
         return this.server.networkIdOf(hash);
      }

      int protocol = this.server.protocol();
      Map<BlockState, Integer> cache = this.nearest.computeIfAbsent(protocol, key -> new ConcurrentHashMap<>());
      return this.server.networkIdOf(cache.computeIfAbsent(state, key -> this.nearestMapped(key, reverse)));
   }

   private Reference2IntOpenHashMap<BlockState> reverseMap() {
      Reference2IntOpenHashMap<BlockState> legacy = this.legacyToBedrock;
      return this.server.protocol() == BedrockProtocols.V26_52.protocol() && legacy != null ? legacy : this.toBedrock;
   }

   private int nearestMapped(BlockState state, Reference2IntOpenHashMap<BlockState> reverse) {
      BlockState best = this.closestMapped(state, state.getBlock(), reverse);
      if (best == null && colouredByBlockEntity(state.getBlock())) {
         int bestDifference = Integer.MAX_VALUE;

         for (Block sibling : BuiltInRegistries.BLOCK) {
            if (sibling != state.getBlock() && sibling.getClass() == state.getBlock().getClass()) {
               BlockState candidate = this.closestMapped(state, sibling, reverse);
               if (candidate != null) {
                  int difference = differences(state, candidate);
                  if (difference < bestDifference) {
                     best = candidate;
                     bestDifference = difference;
                  }
               }
            }
         }
      }

      return best == null ? reverse.defaultReturnValue() : reverse.getInt(best);
   }

   private static boolean colouredByBlockEntity(Block block) {
      return block instanceof BedBlock || block instanceof BannerBlock || block instanceof WallBannerBlock;
   }

   private @Nullable BlockState closestMapped(BlockState state, Block block, Reference2IntOpenHashMap<BlockState> reverse) {
      BlockState best = null;
      int bestDifference = Integer.MAX_VALUE;
      UnmodifiableIterator var6 = block.getStateDefinition().getPossibleStates().iterator();

      while (var6.hasNext()) {
         BlockState candidate = (BlockState)var6.next();
         if (reverse.containsKey(candidate)) {
            int difference = differences(state, candidate);
            if (difference < bestDifference) {
               best = candidate;
               bestDifference = difference;
            }
         }
      }

      return best;
   }

   private static int differences(BlockState state, BlockState candidate) {
      int difference = 0;

      for (Property<?> property : state.getProperties()) {
         if (!candidate.hasProperty(property) || !state.getValue(property).equals(candidate.getValue(property))) {
            difference++;
         }
      }

      return difference;
   }

   public BlockState byName(String bedrockName) {
      return this.byName.get(bedrockName);
   }

   public boolean isKnown(int networkId) {
      int hash = this.server.hashOf(networkId);
      return this.byHash.containsKey(hash) || this.byShapelessHash.containsKey(hash);
   }

   public boolean isKnownOrCustom(int networkId) {
      return this.isKnown(networkId) || CustomBlockSet.current().slot(this.server.hashOf(networkId)) >= 0;
   }

   public int airNetworkId() {
      return this.server.networkIdOf(BedrockBlockHasher.hash("minecraft:air", new TreeMap<>()));
   }

   public @Nullable String bedrockName(int networkId) {
      ServerBlockPalette current = this.server;
      int hash = current.hashOf(networkId);
      String name = (String)this.names().get(hash);
      if (name != null) {
         return name;
      }

      BedrockBlockState custom = current.custom(hash);
      return custom != null ? custom.name() : null;
   }

   private Int2ObjectOpenHashMap<String> names() {
      Int2ObjectOpenHashMap<String> map = this.names;
      if (map == null) {
         map = new Int2ObjectOpenHashMap(this.palette.size() * 2);

         for (BedrockBlockState state : this.palette) {
            map.put(state.networkHash(), state.name());
            int shapeless = shapelessHash(state);
            if (shapeless != state.networkHash()) {
               map.putIfAbsent(shapeless, state.name());
            }
         }

         this.names = map;
      }

      return map;
   }

   public int size() {
      return this.byHash.size();
   }

   public List<String> unresolved() {
      return this.unresolved;
   }

   static BlockMappings build(BedrockBlockPalette palette) {
      return build(palette, true);
   }

   private static BlockMappings build(BedrockBlockPalette palette, boolean log) {
      Map<String, String> exact = new HashMap<>();
      Map<String, List<BlockMappings.B2JEntry>> byName = new HashMap<>();
      Map<String, Set<String>> knownKeys = new HashMap<>();

      for (Entry<String, JsonElement> entry : BedrockBlockPalette.readGzipJson("/assets/bedrocknative/mappings/bedrock-1.26.30/blocksB2J.json.gz")
         .getAsJsonObject()
         .entrySet()) {
         String key = entry.getKey();
         String java = entry.getValue().getAsString();
         exact.put(key, java);
         int bracket = key.indexOf(91);
         String name = key.substring(0, bracket);
         Map<String, String> states = parseStates(key.substring(bracket + 1, key.length() - 1));
         byName.computeIfAbsent(name, k -> new ArrayList<>()).add(new BlockMappings.B2JEntry(states, java));
         knownKeys.computeIfAbsent(name, k -> new HashSet<>()).addAll(states.keySet());
      }

      Map<Set<String>, List<String>> templatesByKeys = new HashMap<>();
      knownKeys.forEach((name, keysx) -> templatesByKeys.computeIfAbsent(keysx, k -> new ArrayList<>()).add(name));
      templatesByKeys.values().forEach(names -> names.sort(null));
      Int2ObjectOpenHashMap<BlockState> byHash = new Int2ObjectOpenHashMap(palette.states().size());
      Reference2IntOpenHashMap<BlockState> toBedrock = new Reference2IntOpenHashMap();
      Reference2IntOpenHashMap<BlockState> toBedrockRank = new Reference2IntOpenHashMap();
      Map<String, BlockState> parsed = new HashMap<>();
      Map<String, BlockState> firstByName = new HashMap<>();
      Int2ObjectOpenHashMap<BlockState> byShapelessHash = new Int2ObjectOpenHashMap();
      int direct = 0;
      int nearest = 0;
      int template = 0;
      int sameId = 0;
      List<String> unresolved = new ArrayList<>();
      Int2ObjectOpenHashMap<String> unresolvedNames = new Int2ObjectOpenHashMap();

      for (BedrockBlockState bedrock : palette.states()) {
         Set<String> keys = knownKeys.get(bedrock.name());
         BlockState java = frameAnchor(bedrock);
         if (java == null && keys != null) {
            SortedMap<String, Object> reduced = new TreeMap<>(bedrock.states());
            reduced.keySet().retainAll(keys);
            String javaString = exact.get(new BedrockBlockState(bedrock.name(), reduced, 0).toStateString());
            if (javaString != null) {
               direct++;
            } else {
               javaString = nearest(reduced, byName.get(bedrock.name()));
               nearest++;
            }

            java = parsed.computeIfAbsent(javaString, BlockMappings::parseJava);
         }

         if (java == null) {
            java = viaTemplate(bedrock, exact, byName, templatesByKeys, parsed);
            if (java != null) {
               template++;
            }
         }

         if (java == null) {
            java = parseJava(aliasOf(bedrock.name()));
            if (java != null) {
               sameId++;
            }
         }

         if (java == null) {
            unresolved.add(bedrock.toStateString());
            unresolvedNames.put(bedrock.networkHash(), bedrock.name());
         } else {
            BlockState mapped = applyPropertiesNewIn2650(java, bedrock.states());
            byHash.put(bedrock.networkHash(), mapped);
            int shapelessHash = shapelessHash(bedrock);
            if (shapelessHash != bedrock.networkHash()) {
               byShapelessHash.putIfAbsent(shapelessHash, mapped);
            }

            firstByName.putIfAbsent(bedrock.name(), mapped);
            boolean sameName = BuiltInRegistries.BLOCK.getKey(mapped.getBlock()).toString().equals(bedrock.name());
            int rank = (keys != null ? 0 : 2) + (sameName ? 0 : 1);
            if (!toBedrock.containsKey(mapped) || rank < toBedrockRank.getInt(mapped)) {
               toBedrock.put(mapped, bedrock.networkHash());
               toBedrockRank.put(mapped, rank);
            }
         }
      }

      if (log) {
         BedrockNative.LOGGER
            .info(
               "Bedrock blocks: {} direct, {} nearest variant, {} via template, {} same id, {} unresolved (of {})",
               new Object[]{direct, nearest, template, sameId, unresolved.size(), palette.states().size()}
            );
         if (!unresolved.isEmpty()) {
            BedrockNative.LOGGER
               .info(
                  "Bedrock blocks without a Java state (first {}): {}", Math.min(12, unresolved.size()), unresolved.subList(0, Math.min(12, unresolved.size()))
               );
         }
      }

      return new BlockMappings(palette.states(), byHash, byShapelessHash, Map.copyOf(firstByName), toBedrock, List.copyOf(unresolved), unresolvedNames);
   }

   static BlockState frameAnchor(BedrockBlockState bedrock) {
      boolean glow = bedrock.name().equals("minecraft:glow_frame");
      if (!glow && !bedrock.name().equals("minecraft:frame")) {
         return null;
      }

      int facing = bedrock.states().get("facing_direction") instanceof Number number ? number.intValue() : 2;
      return Blocks.MOVING_PISTON
         .defaultBlockState()
         .setValue(MovingPistonBlock.FACING, Direction.from3DDataValue(facing))
         .setValue(MovingPistonBlock.TYPE, glow ? PistonType.STICKY : PistonType.DEFAULT);
   }

   static int shapelessHash(BedrockBlockState bedrock) {
      SortedMap<String, Object> states = bedrock.states();
      boolean shaped = false;

      for (Entry<String, Object> state : states.entrySet()) {
         if (state.getKey().equals("minecraft:corner") || CONNECTIONS.containsKey(state.getKey())) {
            if (!state.getValue().equals("none") && !state.getValue().equals((byte)0)) {
               return bedrock.networkHash();
            }

            shaped = true;
         }
      }

      if (!shaped) {
         return bedrock.networkHash();
      }

      SortedMap<String, Object> reduced = new TreeMap<>(states);
      reduced.keySet().removeIf(key -> key.equals("minecraft:corner") || CONNECTIONS.containsKey(key));
      return BedrockBlockHasher.hash(bedrock.name(), reduced);
   }

   private static BlockState viaTemplate(
      BedrockBlockState bedrock,
      Map<String, String> exact,
      Map<String, List<BlockMappings.B2JEntry>> byName,
      Map<Set<String>, List<String>> templatesByKeys,
      Map<String, BlockState> parsed
   ) {
      BlockState target = parseJava(aliasOf(bedrock.name()));
      if (target == null) {
         return null;
      }

      SortedMap<String, Object> reduced = new TreeMap<>(bedrock.states());
      reduced.keySet().removeIf(key -> key.equals("minecraft:corner") || CONNECTIONS.containsKey(key));
      if (reduced.isEmpty() && target.getProperties().isEmpty()) {
         return target;
      }

      Set<String> targetProperties = propertyNames(target);
      List<String> templates = new ArrayList<>(templatesByKeys.getOrDefault(reduced.keySet(), List.of()));
      templates.sort(Comparator.<String>comparingInt(name -> -commonSuffix(name, bedrock.name())).thenComparing(name -> (String)name));

      for (String templateName : templates) {
         String javaString = exact.get(new BedrockBlockState(templateName, reduced, 0).toStateString());
         if (javaString == null) {
            javaString = nearest(reduced, byName.get(templateName));
         }

         BlockState templateJava = parsed.computeIfAbsent(javaString, BlockMappings::parseJava);
         if (templateJava != null && propertyNames(templateJava).equals(targetProperties)) {
            for (Property<?> property : templateJava.getProperties()) {
               target = copyValue(target, templateJava, property);
            }

            return target;
         }
      }

      return null;
   }

   private static int commonSuffix(String a, String b) {
      int n = 0;

      while (n < a.length() && n < b.length() && a.charAt(a.length() - 1 - n) == b.charAt(b.length() - 1 - n)) {
         n++;
      }

      return n;
   }

   private static Set<String> propertyNames(BlockState state) {
      Set<String> names = new HashSet<>();

      for (Property<?> property : state.getProperties()) {
         names.add(property.getName());
      }

      return names;
   }

   private static <T extends Comparable<T>> BlockState copyValue(BlockState target, BlockState source, Property<T> sourceProperty) {
      Property<?> targetProperty = target.getBlock().getStateDefinition().getProperty(sourceProperty.getName());
      return targetProperty == null ? target : setByName(target, targetProperty, sourceProperty.getName(source.getValue(sourceProperty)));
   }

   private static <T extends Comparable<T>> BlockState setByName(BlockState state, Property<T> property, String value) {
      return property.getValue(value).map(v -> state.setValue(property, v)).orElse(state);
   }

   static BlockState applyPropertiesNewIn2650(BlockState java, SortedMap<String, Object> bedrock) {
      if (bedrock.get("minecraft:corner") instanceof String corner && java.hasProperty(BlockStateProperties.STAIRS_SHAPE)) {
         EnumProperty var10001 = BlockStateProperties.STAIRS_SHAPE;

         java = java.setValue(var10001, switch (corner) {
            case "inner_left" -> StairsShape.INNER_LEFT;
            case "inner_right" -> StairsShape.INNER_RIGHT;
            case "outer_left" -> StairsShape.OUTER_LEFT;
            case "outer_right" -> StairsShape.OUTER_RIGHT;
            default -> StairsShape.STRAIGHT;
         });
      }

      for (Entry<String, BooleanProperty> connection : CONNECTIONS.entrySet()) {
         if (bedrock.get(connection.getKey()) instanceof Byte value && java.hasProperty(connection.getValue())) {
            java = java.setValue(connection.getValue(), value != 0);
         }
      }

      if (java.getBlock() instanceof RedstoneWireBlock) {
         if (bedrock.get("redstone_signal") instanceof Number signal) {
            java = setProperty(java, "power", Integer.toString(signal.intValue()));
         }

         for (String side : List.of("north", "east", "south", "west")) {
            if (bedrock.get("redstone_" + side) instanceof String value) {
               java = setProperty(java, side, value);
            }
         }
      }

      if (java.getBlock() instanceof PointedDripstoneBlock) {
         if (bedrock.get("hanging") instanceof Byte hanging) {
            java = setProperty(java, "vertical_direction", hanging != 0 ? "down" : "up");
         }

         if (bedrock.get("dripstone_thickness") instanceof String thickness) {
            java = setProperty(java, "thickness", thickness.equals("merge") ? "tip_merge" : thickness);
         }
      }

      return java;
   }

   private static BlockState setProperty(BlockState state, String name, String value) {
      Property property = state.getBlock().getStateDefinition().getProperty(name);
      return property == null ? state : setByName(state, property, value);
   }

   private static String nearest(Map<String, Object> target, List<BlockMappings.B2JEntry> candidates) {
      String best = null;
      long bestScore = Long.MAX_VALUE;

      for (BlockMappings.B2JEntry candidate : candidates) {
         long score = 0L;

         for (Entry<String, Object> state : target.entrySet()) {
            String wanted = String.valueOf(state.getValue());
            String other = candidate.states().get(state.getKey());
            if (!wanted.equals(other)) {
               score += 1000000L;
               if (state.getValue() instanceof Number number && other != null) {
                  try {
                     score += Math.abs(number.longValue() - Long.parseLong(other));
                  } catch (NumberFormatException var15) {
                  }
               }
            }
         }

         if (score < bestScore) {
            bestScore = score;
            best = candidate.java();
         }
      }

      return best;
   }

   private static Map<String, String> parseStates(String body) {
      Map<String, String> states = new HashMap<>();
      if (!body.isEmpty()) {
         for (String pair : body.split(",")) {
            int eq = pair.indexOf(61);
            states.put(pair.substring(0, eq), pair.substring(eq + 1));
         }
      }

      return states;
   }

   private static String aliasOf(String bedrockName) {
      String alias = ALIASES.get(bedrockName);
      if (alias != null) {
         return alias;
      } else if (bedrockName.startsWith("minecraft:hard_")) {
         return "minecraft:" + bedrockName.substring("minecraft:hard_".length());
      } else if (bedrockName.endsWith("_standing_sign")) {
         return bedrockName.substring(0, bedrockName.length() - "_standing_sign".length()) + "_sign";
      } else {
         return bedrockName.endsWith("_double_slab")
            ? bedrockName.substring(0, bedrockName.length() - "_double_slab".length()) + "_slab[type=double]"
            : bedrockName;
      }
   }

   private static BlockState parseJava(String javaState) {
      try {
         return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, javaState, false).blockState();
      } catch (CommandSyntaxException e) {
         return null;
      }
   }

   private record B2JEntry(Map<String, String> states, String java) {
   }
}
