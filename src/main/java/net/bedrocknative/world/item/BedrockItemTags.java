package net.bedrocknative.world.item;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntFunction;
import java.util.function.LongConsumer;
import net.bedrocknative.text.BedrockText;
import net.bedrocknative.world.block.BlockMappings;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.predicates.BlockPredicate.Builder;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.network.Filterable;
import net.minecraft.util.Unit;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.item.AdventureModePredicate;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.item.component.InstrumentComponent;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.item.component.OminousBottleAmplifier;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.item.component.FireworkExplosion.Shape;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments.Mutable;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import net.minecraft.world.item.equipment.trim.TrimPattern;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.SuspiciousEffectHolder;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.PotDecorations;
import net.minecraft.world.level.block.entity.BannerPatternLayers.Layer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.maps.MapId;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtType;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.jspecify.annotations.Nullable;

public final class BedrockItemTags {
   private static final String[] ENCHANTMENTS = new String[]{
      "protection",
      "fire_protection",
      "feather_falling",
      "blast_protection",
      "projectile_protection",
      "thorns",
      "respiration",
      "depth_strider",
      "aqua_affinity",
      "sharpness",
      "smite",
      "bane_of_arthropods",
      "knockback",
      "fire_aspect",
      "looting",
      "efficiency",
      "silk_touch",
      "unbreaking",
      "fortune",
      "power",
      "punch",
      "flame",
      "infinity",
      "luck_of_the_sea",
      "lure",
      "frost_walker",
      "mending",
      "binding_curse",
      "vanishing_curse",
      "impaling",
      "riptide",
      "loyalty",
      "channeling",
      "multishot",
      "piercing",
      "quick_charge",
      "soul_speed",
      "swift_sneak",
      "wind_burst",
      "density",
      "breach",
      "lunge"
   };
   private static final List<Holder<Potion>> POTIONS = Collections.unmodifiableList(
      Arrays.asList(
         Potions.WATER,
         Potions.MUNDANE,
         Potions.MUNDANE,
         Potions.THICK,
         Potions.AWKWARD,
         Potions.NIGHT_VISION,
         Potions.LONG_NIGHT_VISION,
         Potions.INVISIBILITY,
         Potions.LONG_INVISIBILITY,
         Potions.LEAPING,
         Potions.LONG_LEAPING,
         Potions.STRONG_LEAPING,
         Potions.FIRE_RESISTANCE,
         Potions.LONG_FIRE_RESISTANCE,
         Potions.SWIFTNESS,
         Potions.LONG_SWIFTNESS,
         Potions.STRONG_SWIFTNESS,
         Potions.SLOWNESS,
         Potions.LONG_SLOWNESS,
         Potions.WATER_BREATHING,
         Potions.LONG_WATER_BREATHING,
         Potions.HEALING,
         Potions.STRONG_HEALING,
         Potions.HARMING,
         Potions.STRONG_HARMING,
         Potions.POISON,
         Potions.LONG_POISON,
         Potions.STRONG_POISON,
         Potions.REGENERATION,
         Potions.LONG_REGENERATION,
         Potions.STRONG_REGENERATION,
         Potions.STRENGTH,
         Potions.LONG_STRENGTH,
         Potions.STRONG_STRENGTH,
         Potions.WEAKNESS,
         Potions.LONG_WEAKNESS,
         null,
         Potions.TURTLE_MASTER,
         Potions.LONG_TURTLE_MASTER,
         Potions.STRONG_TURTLE_MASTER,
         Potions.SLOW_FALLING,
         Potions.LONG_SLOW_FALLING,
         Potions.STRONG_SLOWNESS,
         Potions.WIND_CHARGED,
         Potions.WEAVING,
         Potions.OOZING,
         Potions.INFESTED
      )
   );
   private static final int DECAY = 36;
   private static final Item[] STEW_FLOWERS = new Item[]{
      Items.POPPY,
      Items.CORNFLOWER,
      Items.RED_TULIP,
      Items.AZURE_BLUET,
      Items.LILY_OF_THE_VALLEY,
      Items.DANDELION,
      Items.BLUE_ORCHID,
      Items.ALLIUM,
      Items.OXEYE_DAISY,
      Items.WITHER_ROSE,
      Items.TORCHFLOWER,
      Items.OPEN_EYEBLOSSOM,
      Items.CLOSED_EYEBLOSSOM
   };
   private static final Map<Integer, String[]> EXPLORER_MAPS = Map.ofEntries(
      Map.entry(3, new String[]{"monument", "ocean_monument_map"}),
      Map.entry(4, new String[]{"mansion", "woodland_mansion_map"}),
      Map.entry(5, new String[]{"buried_treasure", "buried_treasure_map"}),
      Map.entry(7, new String[]{"village_snowy", "snowy_village_map"}),
      Map.entry(8, new String[]{"village_taiga", "taiga_village_map"}),
      Map.entry(9, new String[]{"village_plains", "plains_village_map"}),
      Map.entry(10, new String[]{"village_savanna", "savanna_village_map"}),
      Map.entry(11, new String[]{"village_desert", "desert_village_map"}),
      Map.entry(12, new String[]{"jungle_temple", "jungle_pyramid_map"}),
      Map.entry(13, new String[]{"swamp_hut", "swamp_hut_map"}),
      Map.entry(14, new String[]{"trial_chambers", "buried_trial_chambers_map"}),
      Map.entry(15, new String[]{"abandoned_camp_bamboo_jungle", "abandoned_camp_map"}),
      Map.entry(16, new String[]{"abandoned_camp_birch_forest", "abandoned_camp_map"}),
      Map.entry(17, new String[]{"abandoned_camp_cherry_grove", "abandoned_camp_map"}),
      Map.entry(18, new String[]{"abandoned_camp_dappled_forest", "abandoned_camp_map"}),
      Map.entry(19, new String[]{"abandoned_camp_extreme_hills_plus_trees", "abandoned_camp_map"}),
      Map.entry(20, new String[]{"abandoned_camp_flower_forest", "abandoned_camp_map"}),
      Map.entry(21, new String[]{"abandoned_camp_pale_garden", "abandoned_camp_map"}),
      Map.entry(22, new String[]{"abandoned_camp_swampland", "abandoned_camp_map"}),
      Map.entry(23, new String[]{"buried_ancient_city", "buried_ancient_city_map"}),
      Map.entry(24, new String[]{"buried_mineshaft", "buried_mineshaft_map"}),
      Map.entry(25, new String[]{"desert_pyramid", "desert_pyramid_map"}),
      Map.entry(26, new String[]{"warm_ocean_ruins", "warm_ocean_ruins_map"})
   );
   private static final String[] GOAT_HORNS = new String[]{"ponder", "sing", "seek", "feel", "admire", "call", "yearn", "dream"};
   private static final Map<String, String> BANNER_PATTERNS = Map.ofEntries(
      Map.entry("b", "base"),
      Map.entry("bl", "square_bottom_left"),
      Map.entry("br", "square_bottom_right"),
      Map.entry("tl", "square_top_left"),
      Map.entry("tr", "square_top_right"),
      Map.entry("bs", "stripe_bottom"),
      Map.entry("ts", "stripe_top"),
      Map.entry("ls", "stripe_left"),
      Map.entry("rs", "stripe_right"),
      Map.entry("cs", "stripe_center"),
      Map.entry("ms", "stripe_middle"),
      Map.entry("drs", "stripe_downright"),
      Map.entry("dls", "stripe_downleft"),
      Map.entry("ss", "small_stripes"),
      Map.entry("cr", "cross"),
      Map.entry("sc", "straight_cross"),
      Map.entry("bt", "triangle_bottom"),
      Map.entry("tt", "triangle_top"),
      Map.entry("bts", "triangles_bottom"),
      Map.entry("tts", "triangles_top"),
      Map.entry("ld", "diagonal_left"),
      Map.entry("rd", "diagonal_up_right"),
      Map.entry("lud", "diagonal_up_left"),
      Map.entry("rud", "diagonal_right"),
      Map.entry("mc", "circle"),
      Map.entry("mr", "rhombus"),
      Map.entry("vh", "half_vertical"),
      Map.entry("hh", "half_horizontal"),
      Map.entry("vhr", "half_vertical_right"),
      Map.entry("hhb", "half_horizontal_bottom"),
      Map.entry("bo", "border"),
      Map.entry("cbo", "curly_border"),
      Map.entry("gra", "gradient"),
      Map.entry("gru", "gradient_up"),
      Map.entry("bri", "bricks"),
      Map.entry("glb", "globe"),
      Map.entry("cre", "creeper"),
      Map.entry("sku", "skull"),
      Map.entry("flo", "flower"),
      Map.entry("moj", "mojang"),
      Map.entry("pig", "piglin"),
      Map.entry("flw", "flow"),
      Map.entry("gus", "guster")
   );
   public static final String LOCK = "item_lock";
   public static final String KEEP_ON_DEATH = "keep_on_death";
   public static final String BEDROCK_DATA = "bedrocknative";
   private static final ThreadLocal<BedrockItemTags.DataCache> DATA_CACHE = ThreadLocal.withInitial(BedrockItemTags.DataCache::new);
   static final String RAW_NAME = "name";
   static final String RAW_LORE = "lore";
   static final int MAX_RAW_TEXT = 4096;
   static final int MAX_RAW_LORE_LINES = 64;
   private static final Map<String, Integer> VANILLA_DURABILITY = Map.of("minecraft:fishing_rod", 384);
   private static final Map<String, Integer> MAX_DURABILITY = new ConcurrentHashMap<>();
   private static volatile Provider registries;
   private static volatile IntFunction<List<ItemData>> dynamicContainers = id -> List.of();
   private static volatile Object containersOwner;
   private static volatile Object worldOwner;
   private static volatile LongConsumer mapSeen = id -> {};
   private static volatile IntFunction<Optional<GlobalPos>> lodestones = handle -> Optional.empty();

   private BedrockItemTags() {
   }

   public static void useRegistries(Provider provider) {
      registries = provider;
   }

   public static void useDynamicContainers(IntFunction<List<ItemData>> containers) {
      useDynamicContainers(containers, containers);
   }

   public static void useDynamicContainers(Object owner, IntFunction<List<ItemData>> containers) {
      dynamicContainers = containers;
      containersOwner = owner;
   }

   public static void useWorldData(LongConsumer onMapSeen, IntFunction<Optional<GlobalPos>> lodestonePositions) {
      useWorldData(onMapSeen, onMapSeen, lodestonePositions);
   }

   public static void useWorldData(Object owner, LongConsumer onMapSeen, IntFunction<Optional<GlobalPos>> lodestonePositions) {
      mapSeen = onMapSeen;
      lodestones = lodestonePositions;
      worldOwner = owner;
   }

   public static void sessionEnded() {
      dynamicContainers = id -> List.of();
      containersOwner = null;
      mapSeen = id -> {};
      lodestones = handle -> Optional.empty();
      worldOwner = null;
   }

   static Optional<GlobalPos> lodestoneFor(int handle) {
      return lodestones.apply(handle);
   }

   public static void sessionEnded(Object owner) {
      if (containersOwner == owner) {
         dynamicContainers = id -> List.of();
         containersOwner = null;
      }

      if (worldOwner == owner) {
         mapSeen = id -> {};
         lodestones = handle -> Optional.empty();
         worldOwner = null;
      }
   }

   static void readDefinition(String identifier, NbtMap componentData) {
      if (componentData != null) {
         int max = componentData.getCompound("components").getCompound("minecraft:durability").getInt("max_durability", 0);
         if (max > 0) {
            MAX_DURABILITY.put(identifier, max);
         }
      }
   }

   static void apply(ItemStack stack, String identifier, int aux, NbtMap tag, String[] canPlace, String[] canBreak) {
      applyDataValue(stack, identifier, aux);
      Integer max = MAX_DURABILITY.getOrDefault(identifier, VANILLA_DURABILITY.get(identifier));
      if (max != null && stack.has(DataComponents.MAX_DAMAGE) && stack.getMaxDamage() != max) {
         stack.set(DataComponents.MAX_DAMAGE, max);
      }

      if (canPlace != null && canPlace.length > 0) {
         adventurePredicate(canPlace).ifPresent(predicate -> stack.set(DataComponents.CAN_PLACE_ON, predicate));
      }

      if (canBreak != null && canBreak.length > 0) {
         adventurePredicate(canBreak).ifPresent(predicate -> stack.set(DataComponents.CAN_BREAK, predicate));
      }

      if (tag != null && !tag.isEmpty()) {
         NbtMap display = tag.getCompound("display");
         List<Component> loreLines = new ArrayList<>();
         if (display.get("Name") instanceof String name && !name.isEmpty()) {
            List<MutableComponent> nameLines = BedrockText.lines(name);
            stack.set(DataComponents.CUSTOM_NAME, nameLines.getFirst());
            nameLines.subList(1, nameLines.size()).forEach(line -> loreLines.add(Component.empty().withStyle(ChatFormatting.WHITE).append(line)));
         }

         for (String line : display.getList("Lore", NbtType.STRING)) {
            loreLines.addAll(BedrockText.lines(line));
         }

         if (!loreLines.isEmpty()) {
            stack.set(DataComponents.LORE, new ItemLore(loreLines));
         }

         if (tag.get("Damage") instanceof Number damage && stack.isDamageableItem()) {
            stack.set(DataComponents.DAMAGE, Math.max(0, damage.intValue()));
         }

         if (tag.getByte("Unbreakable", (byte)0) != 0) {
            stack.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
         }

         if (tag.get("RepairCost") instanceof Number cost && cost.intValue() > 0) {
            stack.set(DataComponents.REPAIR_COST, cost.intValue());
         }

         enchantments(stack, tag.getList("ench", NbtType.COMPOUND), tag.containsKey("ench"));
         if (tag.get("customColor") instanceof Number color && !stack.is(Items.FIREWORK_STAR)) {
            stack.set(DataComponents.DYED_COLOR, new DyedItemColor(color.intValue() & 16777215));
         }

         trim(stack, tag.getCompound("Trim"));
         fireworks(stack, tag);
         books(stack, tag);
         patterns(stack, tag);
         if (stack.is(Items.DECORATED_POT)) {
            sherds(stack, tag.getList("sherds", NbtType.STRING));
         }

         if (tag.get("chargedItem") instanceof NbtMap charged) {
            ItemStack projectile = ItemMappings.fromNbt(charged);
            if (!projectile.isEmpty()) {
               stack.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.ofNonEmpty(List.of(projectile)));
            }
         }

         if (tag.containsKey("Items") && stack.getItem().components().has(DataComponents.CONTAINER)) {
            container(stack, tag.getList("Items", NbtType.COMPOUND));
         }

         if (tag.get("map_uuid") instanceof Long map) {
            stack.set(DataComponents.MAP_ID, new MapId(mapId(map)));
            mapSeen.accept(map);
         }

         if (identifier.equals("minecraft:lodestone_compass") && tag.get("trackingHandle") instanceof Integer handle) {
            stack.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(lodestones.apply(handle), true));
         }

         if (tag.get("bundle_id") instanceof Integer bundle) {
            bundle(stack, bundle);
         }

         bedrockOnly(stack, tag);
      }
   }

   static String explorerMap(int aux) {
      String[] map = EXPLORER_MAPS.get(aux);
      return map == null ? null : map[1];
   }

   public static int mapId(long bedrockId) {
      return Long.hashCode(bedrockId);
   }

   private static void applyDataValue(ItemStack stack, String identifier, int aux) {
      switch (identifier) {
         case "minecraft:potion":
         case "minecraft:splash_potion":
         case "minecraft:lingering_potion":
            potion(stack, aux, identifier);
            break;
         case "minecraft:arrow":
            if (aux > 0) {
               potion(stack, aux - 1, identifier);
            }
            break;
         case "minecraft:suspicious_stew":
            SuspiciousEffectHolder flower = aux >= 0 && aux < STEW_FLOWERS.length ? SuspiciousEffectHolder.tryGet(STEW_FLOWERS[aux]) : null;
            if (flower != null) {
               stack.set(DataComponents.SUSPICIOUS_STEW_EFFECTS, flower.getSuspiciousEffects());
            }
            break;
         case "minecraft:goat_horn":
            holder(Registries.INSTRUMENT, GOAT_HORNS[aux >= 0 && aux < GOAT_HORNS.length ? aux : 0] + "_goat_horn")
               .ifPresent(instrument -> stack.set(DataComponents.INSTRUMENT, new InstrumentComponent(instrument)));
            break;
         case "minecraft:ominous_bottle":
            if (aux > 0) {
               stack.set(DataComponents.OMINOUS_BOTTLE_AMPLIFIER, new OminousBottleAmplifier(aux));
            }
            break;
         case "minecraft:lodestone_compass":
            stack.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(Optional.empty(), true));
            break;
         case "minecraft:filled_map":
            String[] map = EXPLORER_MAPS.get(aux);
            String name = map == null ? null : BedrockText.lookupKey("item.map.exploration." + map[0] + ".name");
            if (name != null) {
               stack.set(DataComponents.ITEM_NAME, BedrockText.format(name));
            }
            break;
         default:
            if (identifier.startsWith("minecraft:light_block_") && stack.is(Items.LIGHT)) {
               try {
                  int level = Integer.parseInt(identifier.substring("minecraft:light_block_".length()));
                  stack.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(LightBlock.LEVEL, Math.clamp(level, 0, 15)));
               } catch (NumberFormatException var7) {
               }
            }
      }
   }

   private static void potion(ItemStack stack, int aux, String identifier) {
      if (aux == 36) {
         stack.set(
            DataComponents.POTION_CONTENTS,
            new PotionContents(
               Optional.empty(), Optional.of(MobEffects.WITHER.value().getColor()), List.of(new MobEffectInstance(MobEffects.WITHER, 800, 1)), Optional.empty()
            )
         );

         String[] name = switch (identifier) {
            case "minecraft:splash_potion" -> new String[]{"potion.wither.splash.name", "Splash Potion of Decay"};
            case "minecraft:lingering_potion" -> new String[]{"potion.wither.linger.name", "Lingering Potion of Decay"};
            case "minecraft:arrow" -> new String[]{"tipped_arrow.effect.wither", "Arrow of Decay"};
            default -> new String[]{"potion.wither.name", "Potion of Decay"};
         };
         String text = BedrockText.lookupKey(name[0]);
         stack.set(DataComponents.ITEM_NAME, BedrockText.format(text != null ? text : name[1]));
      } else {
         Holder<Potion> potion = aux >= 0 && aux < POTIONS.size() ? POTIONS.get(aux) : null;
         if (potion != null) {
            stack.set(DataComponents.POTION_CONTENTS, new PotionContents(potion));
         }
      }
   }

   private static void enchantments(ItemStack stack, List<NbtMap> list, boolean present) {
      if (present) {
         Mutable enchantments = new Mutable(ItemEnchantments.EMPTY);

         for (NbtMap entry : list) {
            int id = entry.getShort("id", (short)-1);
            if (id >= 0 && id < ENCHANTMENTS.length) {
               holder(Registries.ENCHANTMENT, ENCHANTMENTS[id])
                  .ifPresent(enchantment -> enchantments.set(enchantment, Math.max(1, entry.getShort("lvl", (short)1))));
            }
         }

         ItemEnchantments result = enchantments.toImmutable();
         if (result.isEmpty()) {
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
         } else {
            stack.set(stack.is(Items.ENCHANTED_BOOK) ? DataComponents.STORED_ENCHANTMENTS : DataComponents.ENCHANTMENTS, result);
         }
      }
   }

   private static void trim(ItemStack stack, NbtMap trim) {
      if (!trim.isEmpty()) {
         Optional<Reference<TrimMaterial>> material = holder(Registries.TRIM_MATERIAL, trim.getString("Material", ""));
         Optional<Reference<TrimPattern>> pattern = holder(Registries.TRIM_PATTERN, trim.getString("Pattern", ""));
         if (material.isPresent() && pattern.isPresent()) {
            stack.set(DataComponents.TRIM, new ArmorTrim(material.get(), pattern.get()));
         }
      }
   }

   private static void fireworks(ItemStack stack, NbtMap tag) {
      if (tag.get("Fireworks") instanceof NbtMap fireworks) {
         List<FireworkExplosion> explosions = new ArrayList<>();

         for (NbtMap explosion : fireworks.getList("Explosions", NbtType.COMPOUND)) {
            explosions.add(explosion(explosion));
         }

         stack.set(DataComponents.FIREWORKS, new Fireworks(fireworks.getByte("Flight", (byte)1), explosions));
      }

      if (tag.get("FireworksItem") instanceof NbtMap explosion && !explosion.isEmpty()) {
         stack.set(DataComponents.FIREWORK_EXPLOSION, explosion(explosion));
      }
   }

   private static FireworkExplosion explosion(NbtMap explosion) {
      return new FireworkExplosion(
         Shape.byId(explosion.getByte("FireworkType", (byte)0)),
         fireworkColors(explosion.getByteArray("FireworkColor")),
         fireworkColors(explosion.getByteArray("FireworkFade")),
         explosion.getBoolean("FireworkTrail"),
         explosion.getBoolean("FireworkFlicker")
      );
   }

   private static IntList fireworkColors(byte[] colors) {
      IntList result = new IntArrayList();
      if (colors != null) {
         for (byte color : colors) {
            result.add(bedrockDye(color).getFireworkColor());
         }
      }

      return result;
   }

   private static DyeColor bedrockDye(int bedrockColor) {
      return DyeColor.byId(15 - (bedrockColor & 15));
   }

   private static void books(ItemStack stack, NbtMap tag) {
      List<NbtMap> pages = tag.getList("pages", NbtType.COMPOUND);
      if (!stack.is(Items.WRITTEN_BOOK) || !tag.containsKey("title") && pages.isEmpty()) {
         if (stack.is(Items.WRITABLE_BOOK) && !pages.isEmpty()) {
            stack.set(
               DataComponents.WRITABLE_BOOK_CONTENT,
               new WritableBookContent(pages.stream().map(page -> Filterable.passThrough(page.getString("text", ""))).toList())
            );
         }
      } else {
         List<Filterable<Component>> content = pages.stream().map(page -> Filterable.<Component>passThrough(BedrockText.format(page.getString("text", "")))).toList();
         stack.set(
            DataComponents.WRITTEN_BOOK_CONTENT,
            new WrittenBookContent(Filterable.passThrough(tag.getString("title", "")), tag.getString("author", ""), tag.getInt("generation", 0), content, true)
         );
      }
   }

   private static void patterns(ItemStack stack, NbtMap tag) {
      Provider provider = registries;
      if (provider != null) {
         if (tag.getInt("Type", 0) == 1 && stack.is(Items.BANNER.white())) {
            stack.applyComponents(Raid.getOminousBannerInstance(provider.lookupOrThrow(Registries.BANNER_PATTERN)).getComponentsPatch());
         } else {
            if (stack.is(Items.SHIELD) && tag.get("Base") instanceof Integer base) {
               stack.set(DataComponents.BASE_COLOR, bedrockDye(base));
            }

            List<NbtMap> patterns = tag.getList("Patterns", NbtType.COMPOUND);
            if (!patterns.isEmpty()) {
               List<Layer> layers = new ArrayList<>();

               for (NbtMap pattern : patterns) {
                  String java = BANNER_PATTERNS.get(pattern.getString("Pattern", ""));
                  if (java != null) {
                     holder(Registries.BANNER_PATTERN, java).ifPresent(holder -> layers.add(new Layer(holder, bedrockDye(pattern.getInt("Color", 0)))));
                  }
               }

               if (!layers.isEmpty()) {
                  stack.set(DataComponents.BANNER_PATTERNS, new BannerPatternLayers(layers));
               }
            }
         }
      }
   }

   public static void sherds(ItemStack stack, List<String> sherds) {
      if (sherds.size() == 4) {
         List<Optional<ItemStackTemplate>> sides = new ArrayList<>();

         for (String sherd : sherds) {
            Item item = ItemMappings.resolve(sherd, 0, null).item();
            sides.add(item != Items.AIR && item != Items.BARRIER ? Optional.of(new ItemStackTemplate(item)) : Optional.empty());
         }

         stack.set(DataComponents.POT_DECORATIONS, new PotDecorations(sides.get(0), sides.get(1), sides.get(2), sides.get(3)));
      }
   }

   private static void container(ItemStack stack, List<NbtMap> items) {
      List<ItemStack> slots = new ArrayList<>(Collections.nCopies(27, ItemStack.EMPTY));

      for (NbtMap item : items) {
         int slot = item.getByte("Slot", (byte)-1);
         if (slot >= 0 && slot < slots.size()) {
            slots.set(slot, ItemMappings.fromNbt(item));
         }
      }

      stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(slots));
   }

   private static void bundle(ItemStack stack, int bundleId) {
      List<ItemStackTemplate> contents = new ArrayList<>();

      for (ItemData item : dynamicContainers.apply(bundleId)) {
         ItemStack java = ItemMappings.toJava(item);
         if (!java.isEmpty()) {
            contents.add(ItemStackTemplate.fromNonEmptyStack(java));
         }
      }

      Collections.reverse(contents);
      stack.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(contents));
   }

   private static void bedrockOnly(ItemStack stack, NbtMap tag) {
      int lock = tag.getByte("minecraft:item_lock", (byte)0);
      boolean keep = tag.getByte("minecraft:keep_on_death", (byte)0) != 0;
      NbtMap display = tag.getCompound("display");
      String name = display.get("Name") instanceof String text && !text.isEmpty() ? text : null;
      List<String> lore = display.getList("Lore", NbtType.STRING);
      if (lock != 0 || keep || name != null || !lore.isEmpty()) {
         CompoundTag data = new CompoundTag();
         if (lock != 0) {
            data.putByte("item_lock", (byte)lock);
         }

         if (keep) {
            data.putBoolean("keep_on_death", true);
         }

         if (name != null) {
            data.putString("name", cap(name));
         }

         if (!lore.isEmpty()) {
            ListTag lines = new ListTag();

            for (int i = 0; i < Math.min(lore.size(), 64); i++) {
               lines.add(StringTag.valueOf(cap(lore.get(i))));
            }

            data.put("lore", lines);
         }

         CompoundTag root = new CompoundTag();
         root.put("bedrocknative", data);
         stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
      }
   }

   private static String cap(String text) {
      return text.length() > 4096 ? text.substring(0, 4096) : text;
   }

   public static @Nullable String rawHoverText(ItemStack stack) {
      CompoundTag data = bedrockData(stack);
      String name = data.getStringOr("name", "");
      ListTag lore = data.getListOrEmpty("lore");
      if (name.isEmpty() && lore.isEmpty()) {
         return null;
      }

      StringBuilder text = new StringBuilder(name.isEmpty() ? stack.getItemName().getString() : name);

      for (int i = 0; i < lore.size(); i++) {
         text.append('\n').append(lore.getStringOr(i, ""));
      }

      return text.toString();
   }

   private static Optional<AdventureModePredicate> adventurePredicate(String[] bedrockBlocks) {
      List<Block> blocks = new ArrayList<>();

      for (String name : bedrockBlocks) {
         String full = name.contains(":") ? name : "minecraft:" + name;
         BlockState state = BlockMappings.get().byName(full);
         Block block = state != null ? state.getBlock() : BuiltInRegistries.BLOCK.getOptional(Identifier.tryParse(full)).orElse(null);
         if (block != null && !blocks.contains(block)) {
            blocks.add(block);
         }
      }

      return blocks.isEmpty()
         ? Optional.empty()
         : Optional.of(new AdventureModePredicate(List.of(Builder.block().of(BuiltInRegistries.BLOCK, blocks).build())));
   }

   public static Optional<Reference<Enchantment>> enchantment(int bedrockId) {
      return bedrockId >= 0 && bedrockId < ENCHANTMENTS.length ? holder(Registries.ENCHANTMENT, ENCHANTMENTS[bedrockId]) : Optional.empty();
   }

   public static int bedrockEnchantment(Holder<Enchantment> enchantment) {
      String path = enchantment.unwrapKey().map(key -> key.identifier().getPath()).orElse("");
      return Arrays.asList(ENCHANTMENTS).indexOf(path);
   }

   public static String bannerPatternCode(String javaName) {
      return BANNER_PATTERNS.entrySet().stream().filter(entry -> entry.getValue().equals(javaName)).map(Entry::getKey).findFirst().orElse(null);
   }

   private static <T> Optional<Reference<T>> holder(ResourceKey<? extends Registry<T>> registry, String name) {
      Provider provider = registries;
      Identifier id = Identifier.tryParse(name.contains(":") ? name : "minecraft:" + name);
      return provider != null && id != null ? provider.lookup(registry).flatMap(lookup -> lookup.get(ResourceKey.create(registry, id))) : Optional.empty();
   }

   public static CompoundTag bedrockData(ItemStack stack) {
      CustomData data = stack.get(DataComponents.CUSTOM_DATA);
      if (data == null) {
         return new CompoundTag();
      }

      BedrockItemTags.DataCache cache = DATA_CACHE.get();
      if (cache.stack == stack && cache.data == data) {
         return cache.tag;
      }

      CompoundTag tag = data.copyTag().getCompoundOrEmpty("bedrocknative");
      cache.stack = stack;
      cache.data = data;
      cache.tag = tag;
      return tag;
   }

   private static final class DataCache {
      private @Nullable ItemStack stack;
      private @Nullable CustomData data;
      private CompoundTag tag = new CompoundTag();
   }
}
