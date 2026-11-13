package net.bedrocknative.world.item;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.LogLists;
import net.bedrocknative.content.PackItemFiles;
import net.bedrocknative.content.PackSource;
import net.bedrocknative.content.PackStack;
import net.bedrocknative.text.BedrockText;
import net.bedrocknative.world.block.BlockMappings;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.block.state.BlockState;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.jspecify.annotations.Nullable;

public final class ItemMappings {
   private static final Map<String, String> ALIASES = Map.ofEntries(
      Map.entry("minecraft:appleenchanted", "minecraft:enchanted_golden_apple"),
      Map.entry("minecraft:azalea_leaves_flowered", "minecraft:flowering_azalea_leaves"),
      Map.entry("minecraft:bamboo_sapling", "minecraft:bamboo"),
      Map.entry("minecraft:brick_block", "minecraft:bricks"),
      Map.entry("minecraft:carrotonastick", "minecraft:carrot_on_a_stick"),
      Map.entry("minecraft:cave_vines", "minecraft:glow_berries"),
      Map.entry("minecraft:cave_vines_body_with_berries", "minecraft:glow_berries"),
      Map.entry("minecraft:chain", "minecraft:iron_chain"),
      Map.entry("minecraft:clownfish", "minecraft:tropical_fish"),
      Map.entry("minecraft:cooked_fish", "minecraft:cooked_cod"),
      Map.entry("minecraft:deadbush", "minecraft:dead_bush"),
      Map.entry("minecraft:dirt_with_roots", "minecraft:rooted_dirt"),
      Map.entry("minecraft:empty_map", "minecraft:map"),
      Map.entry("minecraft:end_brick_stairs", "minecraft:end_stone_brick_stairs"),
      Map.entry("minecraft:end_bricks", "minecraft:end_stone_bricks"),
      Map.entry("minecraft:evocation_illager_spawn_egg", "minecraft:evoker_spawn_egg"),
      Map.entry("minecraft:fence_gate", "minecraft:oak_fence_gate"),
      Map.entry("minecraft:fireworks", "minecraft:firework_rocket"),
      Map.entry("minecraft:fireworkscharge", "minecraft:firework_star"),
      Map.entry("minecraft:fish", "minecraft:cod"),
      Map.entry("minecraft:frame", "minecraft:item_frame"),
      Map.entry("minecraft:frog_spawn", "minecraft:frogspawn"),
      Map.entry("minecraft:glow_frame", "minecraft:glow_item_frame"),
      Map.entry("minecraft:golden_rail", "minecraft:powered_rail"),
      Map.entry("minecraft:grass_path", "minecraft:dirt_path"),
      Map.entry("minecraft:hardened_clay", "minecraft:terracotta"),
      Map.entry("minecraft:light_block", "minecraft:light"),
      Map.entry("minecraft:lit_pumpkin", "minecraft:jack_o_lantern"),
      Map.entry("minecraft:lodestone_compass", "minecraft:compass"),
      Map.entry("minecraft:magma", "minecraft:magma_block"),
      Map.entry("minecraft:melon_block", "minecraft:melon"),
      Map.entry("minecraft:mob_spawner", "minecraft:spawner"),
      Map.entry("minecraft:muttoncooked", "minecraft:cooked_mutton"),
      Map.entry("minecraft:muttonraw", "minecraft:mutton"),
      Map.entry("minecraft:nether_brick", "minecraft:nether_bricks"),
      Map.entry("minecraft:netherbrick", "minecraft:nether_brick"),
      Map.entry("minecraft:netherstar", "minecraft:nether_star"),
      Map.entry("minecraft:normal_stone_slab", "minecraft:stone_slab"),
      Map.entry("minecraft:normal_stone_stairs", "minecraft:stone_stairs"),
      Map.entry("minecraft:noteblock", "minecraft:note_block"),
      Map.entry("minecraft:prismarine_bricks_stairs", "minecraft:prismarine_brick_stairs"),
      Map.entry("minecraft:quartz_ore", "minecraft:nether_quartz_ore"),
      Map.entry("minecraft:red_nether_brick", "minecraft:red_nether_bricks"),
      Map.entry("minecraft:scute", "minecraft:turtle_scute"),
      Map.entry("minecraft:sign", "minecraft:oak_sign"),
      Map.entry("minecraft:silver_glazed_terracotta", "minecraft:light_gray_glazed_terracotta"),
      Map.entry("minecraft:slime", "minecraft:slime_block"),
      Map.entry("minecraft:small_dripleaf_block", "minecraft:small_dripleaf"),
      Map.entry("minecraft:snow", "minecraft:snow_block"),
      Map.entry("minecraft:snow_layer", "minecraft:snow"),
      Map.entry("minecraft:speckled_melon", "minecraft:glistering_melon_slice"),
      Map.entry("minecraft:stone_stairs", "minecraft:cobblestone_stairs"),
      Map.entry("minecraft:stonecutter_block", "minecraft:stonecutter"),
      Map.entry("minecraft:totem", "minecraft:totem_of_undying"),
      Map.entry("minecraft:trapdoor", "minecraft:oak_trapdoor"),
      Map.entry("minecraft:undyed_shulker_box", "minecraft:shulker_box"),
      Map.entry("minecraft:waterlily", "minecraft:lily_pad"),
      Map.entry("minecraft:waxed_copper", "minecraft:waxed_copper_block"),
      Map.entry("minecraft:web", "minecraft:cobweb"),
      Map.entry("minecraft:wooden_button", "minecraft:oak_button"),
      Map.entry("minecraft:wooden_door", "minecraft:oak_door"),
      Map.entry("minecraft:wooden_pressure_plate", "minecraft:oak_pressure_plate"),
      Map.entry("minecraft:zombie_pigman_spawn_egg", "minecraft:zombified_piglin_spawn_egg")
   );
   private static final Map<String, String> STAND_INS = Map.ofEntries(
      Map.entry("minecraft:hard_glass", "minecraft:glass"),
      Map.entry("minecraft:hard_glass_pane", "minecraft:glass_pane"),
      Map.entry("minecraft:underwater_torch", "minecraft:torch"),
      Map.entry("minecraft:underwater_tnt", "minecraft:tnt"),
      Map.entry("minecraft:colored_torch_red", "minecraft:torch"),
      Map.entry("minecraft:colored_torch_green", "minecraft:torch"),
      Map.entry("minecraft:colored_torch_blue", "minecraft:torch"),
      Map.entry("minecraft:colored_torch_purple", "minecraft:torch"),
      Map.entry("minecraft:colored_torch_rg", "minecraft:torch"),
      Map.entry("minecraft:colored_torch_bp", "minecraft:torch"),
      Map.entry("minecraft:glowingobsidian", "minecraft:crying_obsidian"),
      Map.entry("minecraft:netherreactor", "minecraft:iron_block"),
      Map.entry("minecraft:camera", "minecraft:spyglass"),
      Map.entry("minecraft:item.camera", "minecraft:spyglass"),
      Map.entry("minecraft:chalkboard", "minecraft:dark_oak_sign"),
      Map.entry("minecraft:board", "minecraft:dark_oak_sign"),
      Map.entry("minecraft:portfolio", "minecraft:book"),
      Map.entry("minecraft:photo_item", "minecraft:paper"),
      Map.entry("minecraft:water", "minecraft:water_bucket"),
      Map.entry("minecraft:flowing_water", "minecraft:water_bucket"),
      Map.entry("minecraft:lava", "minecraft:lava_bucket"),
      Map.entry("minecraft:flowing_lava", "minecraft:lava_bucket"),
      Map.entry("minecraft:fire", "minecraft:fire_charge"),
      Map.entry("minecraft:soul_fire", "minecraft:fire_charge"),
      Map.entry("minecraft:bubble_column", "minecraft:water_bucket"),
      Map.entry("minecraft:powder_snow", "minecraft:powder_snow_bucket"),
      Map.entry("minecraft:frosted_ice", "minecraft:ice"),
      Map.entry("minecraft:invisible_bedrock", "minecraft:barrier"),
      Map.entry("minecraft:npc_spawn_egg", "minecraft:villager_spawn_egg")
   );
   private static final String[] WOODS = new String[]{"oak", "spruce", "birch", "jungle", "acacia", "dark_oak"};
   private static final String[] DYES = new String[]{
      "ink_sac",
      "red_dye",
      "green_dye",
      "cocoa_beans",
      "lapis_lazuli",
      "purple_dye",
      "cyan_dye",
      "light_gray_dye",
      "gray_dye",
      "pink_dye",
      "lime_dye",
      "yellow_dye",
      "light_blue_dye",
      "magenta_dye",
      "orange_dye",
      "bone_meal",
      "black_dye",
      "brown_dye",
      "blue_dye",
      "white_dye"
   };
   private static final String[] FLOWERS = new String[]{
      "poppy",
      "blue_orchid",
      "allium",
      "azure_bluet",
      "red_tulip",
      "orange_tulip",
      "white_tulip",
      "pink_tulip",
      "oxeye_daisy",
      "cornflower",
      "lily_of_the_valley"
   };
   private static final String[] CORALS = new String[]{"tube", "brain", "bubble", "fire", "horn"};
   private static final String[][] STONE_SLABS = new String[][]{
      {"smooth_stone", "sandstone", "oak", "cobblestone", "brick", "stone_brick", "quartz", "nether_brick"},
      {"red_sandstone", "purpur", "prismarine", "dark_prismarine", "prismarine_brick", "mossy_cobblestone", "smooth_sandstone", "red_nether_brick"},
      {"end_stone_brick", "smooth_red_sandstone", "polished_andesite", "andesite", "diorite", "polished_diorite", "granite", "polished_granite"},
      {"mossy_stone_brick", "smooth_quartz", "stone", "cut_sandstone", "cut_red_sandstone"}
   };
   private static final String[] SKULLS = new String[]{
      "skeleton_skull", "wither_skeleton_skull", "zombie_head", "player_head", "creeper_head", "dragon_head", "piglin_head"
   };
   private static final String[] BANNER_PATTERNS = new String[]{
      "creeper", "skull", "flower", "mojang", "field_masoned", "bordure_indented", "piglin", "globe", "flow", "guster"
   };
   private static final Map<Integer, String> LEGACY_MOBS = Map.ofEntries(
      Map.entry(10, "chicken"),
      Map.entry(11, "cow"),
      Map.entry(12, "pig"),
      Map.entry(13, "sheep"),
      Map.entry(14, "wolf"),
      Map.entry(15, "villager"),
      Map.entry(16, "mooshroom"),
      Map.entry(17, "squid"),
      Map.entry(18, "rabbit"),
      Map.entry(19, "bat"),
      Map.entry(20, "iron_golem"),
      Map.entry(21, "snow_golem"),
      Map.entry(22, "ocelot"),
      Map.entry(23, "horse"),
      Map.entry(24, "donkey"),
      Map.entry(25, "mule"),
      Map.entry(26, "skeleton_horse"),
      Map.entry(27, "zombie_horse"),
      Map.entry(28, "polar_bear"),
      Map.entry(29, "llama"),
      Map.entry(30, "parrot"),
      Map.entry(31, "dolphin"),
      Map.entry(32, "zombie"),
      Map.entry(33, "creeper"),
      Map.entry(34, "skeleton"),
      Map.entry(35, "spider"),
      Map.entry(36, "zombified_piglin"),
      Map.entry(37, "slime"),
      Map.entry(38, "enderman"),
      Map.entry(39, "silverfish"),
      Map.entry(40, "cave_spider"),
      Map.entry(41, "ghast"),
      Map.entry(42, "magma_cube"),
      Map.entry(43, "blaze"),
      Map.entry(44, "zombie_villager"),
      Map.entry(45, "witch"),
      Map.entry(46, "stray"),
      Map.entry(47, "husk"),
      Map.entry(48, "wither_skeleton"),
      Map.entry(49, "guardian"),
      Map.entry(50, "elder_guardian"),
      Map.entry(52, "wither"),
      Map.entry(53, "ender_dragon"),
      Map.entry(54, "shulker"),
      Map.entry(55, "endermite"),
      Map.entry(57, "vindicator"),
      Map.entry(58, "phantom"),
      Map.entry(59, "ravager"),
      Map.entry(74, "turtle"),
      Map.entry(75, "cat"),
      Map.entry(104, "evoker"),
      Map.entry(105, "vex"),
      Map.entry(108, "pufferfish"),
      Map.entry(109, "salmon"),
      Map.entry(110, "drowned"),
      Map.entry(111, "tropical_fish"),
      Map.entry(112, "cod"),
      Map.entry(113, "panda"),
      Map.entry(114, "pillager"),
      Map.entry(118, "wandering_trader"),
      Map.entry(121, "fox"),
      Map.entry(122, "bee"),
      Map.entry(123, "piglin"),
      Map.entry(124, "hoglin"),
      Map.entry(125, "strider"),
      Map.entry(126, "zoglin"),
      Map.entry(127, "piglin_brute")
   );
   private static final String[] INFESTED = new String[]{
      "stone", "cobblestone", "stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks", "chiseled_stone_bricks"
   };
   private static final Set<String> VANILLA_OFFHAND = Set.of(
      "minecraft:shield", "minecraft:totem_of_undying", "minecraft:arrow", "minecraft:firework_rocket", "minecraft:nautilus_shell", "minecraft:filled_map"
   );
   private static final Set<String> LOGGED = ConcurrentHashMap.newKeySet();
   private static final Map<String, ItemMappings.Resolution> BY_IDENTIFIER = new ConcurrentHashMap<>();
   private static volatile Set<String> offhandItems = Set.of();
   private static volatile Map<String, Float> movementModifiers = Map.of();
   private static volatile Map<String, CustomItem> customItems = Map.of();
   private static volatile Map<Integer, String> identifiers = Map.of();
   private static volatile List<? extends PackSource> serverPacks = List.of();

   private ItemMappings() {
   }

   public static ItemStack fromIdAux(int idAux) {
      String identifier = LegacyItemIds.identifier(idAux);
      return identifier == null ? ItemStack.EMPTY : create(identifier, LegacyItemIds.data(idAux), 1, null, null);
   }

   public static ItemStack toJava(ItemData bedrock) {
      if (bedrock != null && !bedrock.isNull() && bedrock.getDefinition() != null) {
         String identifier = bedrock.getDefinition().getIdentifier();
         ItemStack stack = create(
            identifier, shownAux(identifier, bedrock.getDamage(), bedrock.getTag()), bedrock.getCount(), bedrock.getBlockDefinition(), null
         );
         BedrockItemTags.apply(stack, identifier, bedrock.getDamage(), bedrock.getTag(), bedrock.getCanPlace(), bedrock.getCanBreak());
         return stack;
      } else {
         return ItemStack.EMPTY;
      }
   }

   public static ItemStack fromNbt(NbtMap item) {
      String identifier = item.getString("Name", "");
      int count = item.getByte("Count", (byte)1);
      if (!identifier.isEmpty() && !identifier.equals("minecraft:air") && count > 0) {
         int aux = item.getShort("Damage", (short)0);
         String blockName = item.getCompound("Block").getString("name", null);
         NbtMap tag = item.getCompound("tag");
         ItemStack stack = create(identifier, shownAux(identifier, aux, tag), count, null, blockName);
         BedrockItemTags.apply(stack, identifier, aux, tag.isEmpty() ? null : tag, null, null);
         return stack;
      } else {
         return ItemStack.EMPTY;
      }
   }

   private static ItemStack create(String identifier, int aux, int count, BlockDefinition block, String blockName) {
      CustomItem custom = customItems.get(identifier);
      if (custom != null) {
         ItemStack stack = new ItemStack(BedrockItems.CUSTOM, Math.max(1, count));
         custom.applyTo(stack);
         return stack;
      }

      identifier = legacyIdentifier(identifier, aux);
      ItemMappings.Resolution resolution = resolve(identifier, aux, block);
      if (resolution.kind() == ItemMappings.Kind.MISSING && blockName != null) {
         Item byName = itemOfBlock(BlockMappings.get().byName(blockName));
         if (byName != null) {
            resolution = new ItemMappings.Resolution(byName, ItemMappings.Kind.BLOCK);
         }
      }

      ItemStack stack = new ItemStack(resolution.item(), Math.max(1, count));
      if (resolution.kind() == ItemMappings.Kind.STAND_IN || resolution.kind() == ItemMappings.Kind.MISSING) {
         stack.set(DataComponents.ITEM_NAME, bedrockName(identifier));
         if (resolution.kind() == ItemMappings.Kind.MISSING && LOGGED.add(identifier)) {
            BedrockNative.LOGGER.debug("No Java item for Bedrock item {}", identifier);
         }
      }

      return stack;
   }

   public static ItemMappings.Resolution resolve(String identifier, int aux, BlockDefinition block) {
      if (customItems.containsKey(identifier)) {
         return new ItemMappings.Resolution(BedrockItems.CUSTOM, ItemMappings.Kind.CUSTOM);
      }

      identifier = legacyIdentifier(identifier, aux);
      Item variant = variant(identifier, aux);
      if (variant != null) {
         return new ItemMappings.Resolution(variant, ItemMappings.Kind.VARIANT);
      }

      ItemMappings.Resolution byIdentifier = BY_IDENTIFIER.computeIfAbsent(identifier, ItemMappings::resolveIdentifier);
      if (byIdentifier.kind() == ItemMappings.Kind.STAND_IN || byIdentifier.kind() == ItemMappings.Kind.MISSING) {
         Item placed = byBlock(block);
         if (placed != null) {
            return new ItemMappings.Resolution(placed, ItemMappings.Kind.BLOCK);
         }
      }

      return byIdentifier;
   }

   private static int shownAux(String identifier, int aux, NbtMap tag) {
      return identifier.equals("minecraft:banner") && tag != null && tag.getInt("Type", 0) == 1 ? 15 : aux;
   }

   private static String legacyIdentifier(String identifier, int aux) {
      return !identifier.equals("minecraft:hard_stained_glass") && !identifier.equals("minecraft:hard_stained_glass_pane")
         ? identifier
         : "minecraft:hard_" + color(aux) + identifier.substring("minecraft:hard".length());
   }

   private static ItemMappings.Resolution resolveIdentifier(String identifier) {
      if (identifier.equals("minecraft:unknown")) {
         return new ItemMappings.Resolution(Items.BARRIER, ItemMappings.Kind.MISSING);
      }

      String alias = ALIASES.get(identifier);
      if (alias != null) {
         return new ItemMappings.Resolution(item(alias), ItemMappings.Kind.ALIAS);
      }

      Item named = item(identifier);
      if (named != null) {
         return new ItemMappings.Resolution(named, ItemMappings.Kind.NAME);
      }

      if (identifier.startsWith("minecraft:record_")) {
         Item disc = item("minecraft:music_disc_" + identifier.substring("minecraft:record_".length()));
         if (disc != null) {
            return new ItemMappings.Resolution(disc, ItemMappings.Kind.ALIAS);
         }
      }

      String standIn = STAND_INS.get(identifier);
      if (standIn == null && identifier.startsWith("minecraft:hard_") && identifier.contains("glass")) {
         standIn = "minecraft:" + identifier.substring("minecraft:hard_".length());
      }

      if (standIn == null && identifier.startsWith("minecraft:colored_torch")) {
         standIn = "minecraft:torch";
      }

      if (standIn == null || item(standIn) == null) {
         String blockName = identifier.startsWith("minecraft:item.") ? "minecraft:" + identifier.substring("minecraft:item.".length()) : identifier;
         Item placed = itemOfBlock(BlockMappings.get().byName(blockName));
         if (placed != null) {
            return new ItemMappings.Resolution(placed, ItemMappings.Kind.BLOCK);
         }
      }

      if (standIn != null && item(standIn) != null) {
         return new ItemMappings.Resolution(item(standIn), ItemMappings.Kind.STAND_IN);
      }

      if (identifier.startsWith("minecraft:item.")) {
         ItemMappings.Resolution plain = resolveIdentifier("minecraft:" + identifier.substring("minecraft:item.".length()));
         if (plain.kind() != ItemMappings.Kind.MISSING) {
            return plain;
         }
      }

      return identifier.endsWith("candle_cake")
         ? new ItemMappings.Resolution(Items.CAKE, ItemMappings.Kind.ALIAS)
         : new ItemMappings.Resolution(Items.BARRIER, ItemMappings.Kind.MISSING);
   }

   private static Item item(String javaName) {
      Identifier id = Identifier.tryParse(javaName);
      Optional<Item> item = id == null ? Optional.empty() : BuiltInRegistries.ITEM.getOptional(id);
      return item.filter(found -> found != Items.AIR || javaName.equals("minecraft:air")).orElse(null);
   }

   private static Item byBlock(BlockDefinition block) {
      return block != null && BlockMappings.get().isKnown(block.getRuntimeId()) ? itemOfBlock(BlockMappings.get().toJava(block.getRuntimeId())) : null;
   }

   private static Item itemOfBlock(BlockState state) {
      if (state == null) {
         return null;
      }

      Item item = state.getBlock().asItem();
      return item == Items.AIR ? null : item;
   }

   private static Item variant(String identifier, int aux) {
      String name = switch (identifier) {
         case "minecraft:bed" -> color(aux) + "_bed";
         case "minecraft:banner" -> DyeColor.byId(15 - (aux & 15)).getName() + "_banner";
         case "minecraft:arrow" -> aux > 0 ? "tipped_arrow" : null;
         case "minecraft:filled_map" -> BedrockItemTags.explorerMap(aux);
         case "minecraft:wool" -> color(aux) + "_wool";
         case "minecraft:carpet" -> color(aux) + "_carpet";
         case "minecraft:concrete" -> color(aux) + "_concrete";
         case "minecraft:concrete_powder" -> color(aux) + "_concrete_powder";
         case "minecraft:stained_glass" -> color(aux) + "_stained_glass";
         case "minecraft:stained_glass_pane" -> color(aux) + "_stained_glass_pane";
         case "minecraft:stained_hardened_clay" -> color(aux) + "_terracotta";
         case "minecraft:shulker_box" -> color(aux) + "_shulker_box";
         case "minecraft:dye" -> pick(DYES, aux);
         case "minecraft:log" -> pick(WOODS, aux & 3) + "_log";
         case "minecraft:log2" -> pick(WOODS, 4 + (aux & 1)) + "_log";
         case "minecraft:wood" -> ((aux & 8) != 0 ? "stripped_" : "") + pick(WOODS, aux & 7) + "_wood";
         case "minecraft:planks" -> pick(WOODS, aux) + "_planks";
         case "minecraft:sapling" -> pick(WOODS, aux & 7) + "_sapling";
         case "minecraft:leaves" -> pick(WOODS, aux & 3) + "_leaves";
         case "minecraft:leaves2" -> pick(WOODS, 4 + (aux & 1)) + "_leaves";
         case "minecraft:wooden_slab" -> pick(WOODS, aux & 7) + "_slab";
         case "minecraft:fence" -> pick(WOODS, aux) + "_fence";
         case "minecraft:boat" -> pick(WOODS, aux) + "_boat";
         case "minecraft:chest_boat" -> pick(WOODS, aux) + "_chest_boat";
         case "minecraft:stonebrick" -> {
            switch (aux) {
               case 1:
                  yield "mossy_stone_bricks";
               case 2:
                  yield "cracked_stone_bricks";
               case 3:
                  yield "chiseled_stone_bricks";
               default:
                  yield "stone_bricks";
            }
         }
         case "minecraft:red_flower" -> pick(FLOWERS, aux);
         case "minecraft:double_plant" -> pick(new String[]{"sunflower", "lilac", "tall_grass", "large_fern", "rose_bush", "peony"}, aux & 7);
         case "minecraft:tallgrass" -> aux != 2 && aux != 3 ? "short_grass" : "fern";
         case "minecraft:coral" -> ((aux & 8) != 0 ? "dead_" : "") + pick(CORALS, aux & 7) + "_coral";
         case "minecraft:coral_block" -> ((aux & 8) != 0 ? "dead_" : "") + pick(CORALS, aux & 7) + "_coral_block";
         case "minecraft:coral_fan" -> pick(CORALS, aux & 7) + "_coral_fan";
         case "minecraft:coral_fan_dead" -> "dead_" + pick(CORALS, aux & 7) + "_coral_fan";
         case "minecraft:monster_egg" -> "infested_" + pick(INFESTED, aux);
         case "minecraft:stone_block_slab", "minecraft:double_stone_block_slab" -> pick(STONE_SLABS[0], aux & 7) + "_slab";
         case "minecraft:stone_block_slab2", "minecraft:double_stone_block_slab2" -> pick(STONE_SLABS[1], aux & 7) + "_slab";
         case "minecraft:stone_block_slab3", "minecraft:double_stone_block_slab3" -> pick(STONE_SLABS[2], aux & 7) + "_slab";
         case "minecraft:stone_block_slab4", "minecraft:double_stone_block_slab4" -> pick(STONE_SLABS[3], aux & 7) + "_slab";
         case "minecraft:skull" -> pick(SKULLS, aux);
         case "minecraft:banner_pattern" -> pick(BANNER_PATTERNS, aux) + "_banner_pattern";
         case "minecraft:spawn_egg" -> LEGACY_MOBS.containsKey(aux) ? (String)LEGACY_MOBS.get(aux) + "_spawn_egg" : null;
         default -> null;
      };
      return name == null ? null : item("minecraft:" + name);
   }

   private static String color(int aux) {
      return DyeColor.byId(aux & 15).getName();
   }

   private static String pick(String[] names, int index) {
      return names[index >= 0 && index < names.length ? index : 0];
   }

   private static Component bedrockName(String identifier) {
      String path = identifier.substring(identifier.indexOf(58) + 1);
      String block = path.startsWith("item.") ? path.substring("item.".length()) : path;
      List<String> keys = new ArrayList<>(List.of("item." + path + ".name", "tile." + block + ".name", "item." + block + ".name"));
      if (path.endsWith("_spawn_egg")) {
         keys.add("item.spawn_egg.entity." + path.substring(0, path.length() - "_spawn_egg".length()) + ".name");
      }

      for (String key : keys) {
         String text = BedrockText.lookupKey(key);
         if (text != null) {
            return BedrockText.format(text);
         }
      }

      StringBuilder words = new StringBuilder();

      for (String word : block.split("_")) {
         if (!word.isEmpty()) {
            words.append(words.isEmpty() ? "" : " ").append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
         }
      }

      return Component.literal(words.toString());
   }

   public static boolean allowsOffhand(String identifier) {
      return VANILLA_OFFHAND.contains(identifier) || offhandItems.contains(identifier);
   }

   public static @Nullable CustomItem custom(String identifier) {
      return customItems.get(identifier);
   }

   public static @Nullable CustomItem custom(ItemStack stack) {
      if (!stack.is(BedrockItems.CUSTOM)) {
         return null;
      }

      CustomModelData data = stack.get(DataComponents.CUSTOM_MODEL_DATA);
      String identifier = data == null ? null : data.getString(0);
      return identifier == null ? null : customItems.get(identifier);
   }

   public static float movementModifier(ItemStack stack) {
      CustomItem custom = custom(stack);
      Float modifier = movementModifiers.get(custom != null ? custom.identifier() : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
      return modifier != null ? modifier : Float.NaN;
   }

   public static @Nullable String identifier(int runtimeId) {
      return identifiers.get(runtimeId);
   }

   public static Collection<CustomItem> customItems() {
      return customItems.values();
   }

   public static void useServerPacks(List<? extends PackSource> topFirst) {
      serverPacks = List.copyOf(topFirst);
   }

   static PackItemFiles itemFilesForRegistration() {
      try {
         List<? extends PackSource> announced = serverPacks;
         return announced.isEmpty() ? PackItemFiles.read(PackStack.active()) : PackItemFiles.read(announced);
      } catch (RuntimeException e) {
         BedrockNative.LOGGER.warn("Couldn't read the resource packs' item files", e);
         return PackItemFiles.read(List.of());
      }
   }

   public static void registerDefinitions(Collection<ItemDefinition> definitions) {
      Set<String> offhand = ConcurrentHashMap.newKeySet();
      Map<String, Float> modifiers = new HashMap<>();
      Map<String, CustomItem> custom = new LinkedHashMap<>();
      Map<ItemMappings.Kind, List<String>> byKind = new EnumMap<>(ItemMappings.Kind.class);
      StringBuilder dump = new StringBuilder();
      PackItemFiles itemFiles = null;
      Map<String, Integer> fromFiles = new TreeMap<>();
      List<String> fileItems = new ArrayList<>();

      for (ItemDefinition definition : definitions) {
         String identifier = definition.getIdentifier();
         NbtMap components = definition.getComponentData();
         PackItemFiles.ItemFile file = null;
         if (!identifier.startsWith("minecraft:") && (components == null || components.getCompound("components").isEmpty())) {
            if (itemFiles == null) {
               itemFiles = itemFilesForRegistration();
            }

            file = itemFiles.item(identifier);
            if (file != null && !file.components().isEmpty()) {
               file.components().forEach(name -> fromFiles.merge(name, 1, Integer::sum));
               fileItems.add(identifier);
            }
         }

         CustomItem item = CustomItem.read(identifier, components, definition.isComponentBased(), file);
         if (item != null) {
            custom.put(identifier, item);
         }

         NbtMap data = components == null ? NbtMap.EMPTY : components.getCompound("components");
         if (CustomItem.offhand(data)) {
            offhand.add(identifier);
         }

         float modifier = CustomItem.movementModifier(data);
         if (!Float.isNaN(modifier)) {
            modifiers.put(identifier, modifier);
         }

         BedrockItemTags.readDefinition(identifier, components);
         ItemMappings.Resolution resolution = item != null
            ? new ItemMappings.Resolution(BedrockItems.CUSTOM, ItemMappings.Kind.CUSTOM)
            : resolve(identifier, 0, null);
         byKind.computeIfAbsent(resolution.kind(), kind -> new ArrayList<>())
            .add(identifier + (resolution.kind() == ItemMappings.Kind.MISSING ? "" : " -> " + BuiltInRegistries.ITEM.getKey(resolution.item()).getPath()));
         dump.append(identifier)
            .append(' ')
            .append(definition.getRuntimeId())
            .append(' ')
            .append(definition.isComponentBased())
            .append(' ')
            .append(components)
            .append('\n');
      }

      Map<Integer, String> byRuntimeId = new HashMap<>();

      for (ItemDefinition definition : definitions) {
         byRuntimeId.put(definition.getRuntimeId(), definition.getIdentifier());
      }

      identifiers = Map.copyOf(byRuntimeId);
      offhandItems = Set.copyOf(offhand);
      movementModifiers = Map.copyOf(modifiers);
      customItems = Map.copyOf(custom);
      Map<ItemMappings.Kind, Integer> counts = new EnumMap<>(ItemMappings.Kind.class);
      byKind.forEach((kind, list) -> counts.put(kind, list.size()));
      BedrockNative.LOGGER.info("Bedrock items: {} definitions, {}; offhand items {}", new Object[]{definitions.size(), counts, offhandItems.size()});
      BedrockNative.LOGGER.info("Bedrock items shown as a stand-in: {}", LogLists.summary(byKind.getOrDefault(ItemMappings.Kind.STAND_IN, List.of())));
      BedrockNative.LOGGER.info("Bedrock items without a Java item: {}", LogLists.summary(byKind.getOrDefault(ItemMappings.Kind.MISSING, List.of())));
      if (!custom.isEmpty()) {
         BedrockNative.LOGGER.info("Custom items: {}", LogLists.summary(custom.values()));
      }

      if (!fileItems.isEmpty()) {
         BedrockNative.LOGGER
            .info(
               "Bedrock item files: {} items without network components take components from items/*.json {} {}",
               new Object[]{fileItems.size(), fromFiles, fileItems.size() > 12 ? fileItems.subList(0, 12) + " ..." : fileItems}
            );
      }

      if (System.getProperty("bedrocknative.devTest") != null) {
         try {
            Files.writeString(Path.of("bedrocknative-items.txt"), dump + "\n" + byKind);
         } catch (IOException e) {
            BedrockNative.LOGGER.warn("Could not write the item dump", e);
         }
      }
   }

   public enum Kind {
      NAME,
      ALIAS,
      VARIANT,
      BLOCK,
      STAND_IN,
      MISSING,
      CUSTOM;
   }

   public record Resolution(Item item, ItemMappings.Kind kind) {
   }
}
