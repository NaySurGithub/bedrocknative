package net.bedrocknative.world.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.LogLists;
import net.bedrocknative.content.PackItemFiles;
import net.bedrocknative.text.BedrockText;
import net.bedrocknative.world.block.BlockMappings;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.component.UseCooldown;
import net.minecraft.world.item.component.Tool.Rule;
import net.minecraft.world.item.enchantment.Enchantable;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtMapBuilder;
import org.cloudburstmc.nbt.NbtType;
import org.jspecify.annotations.Nullable;

public final class CustomItem {
   private static final Pattern QUOTED = Pattern.compile("'([^']*)'|\"([^\"]*)\"");
   private static final float BEDROCK_EAT_SECONDS = 1.6F;
   private final String identifier;
   @Nullable
   private final String displayName;
   @Nullable
   private final String icon;
   private final int iconFrame;
   private final int maxStackSize;
   private final int maxDurability;
   @Nullable
   private final EquipmentSlot wearSlot;
   private final int protection;
   private final boolean handEquipped;
   private final boolean allowOffhand;
   private final boolean glint;
   @Nullable
   private final CustomItem.Food food;
   private final float useSeconds;
   private final int useAnimation;
   private final int holdTicks;
   private final ItemUseAnimation holdAnimation;
   @Nullable
   private final String cooldownCategory;
   private final float cooldownSeconds;
   private final int enchantability;
   private final List<CustomItem.Digger> digger;
   private final boolean canDestroyInCreative;
   @Nullable
   private final String rarity;
   @Nullable
   private final String hoverColor;
   @Nullable
   private final String block;
   private final List<String> tags;
   @Nullable
   private volatile CustomItem.Built built;

   private CustomItem(String identifier, NbtMap components, boolean componentBased) {
      this.identifier = identifier;
      NbtMap properties = components.getCompound("item_properties");
      this.displayName = text(components.get("minecraft:display_name"));
      Object iconValue = properties.containsKey("minecraft:icon") ? properties.get("minecraft:icon") : components.get("minecraft:icon");
      NbtMap iconMap = iconValue instanceof NbtMap map ? map : NbtMap.EMPTY;
      Object textures = iconMap.get("textures");
      Object iconName = textures instanceof NbtMap byKind ? byKind.get("default") : (textures != null ? textures : iconMap.get("texture"));
      if (iconName == null) {
         iconName = iconValue;
      }

      this.icon = iconName instanceof String name && !name.isEmpty() ? name : null;
      this.iconFrame = number(iconMap.get("frame"), 0);
      Object stackComponent = value(components.get("minecraft:max_stack_size"));
      int stack = stackComponent != null
         ? number(stackComponent, 64)
         : (components.containsKey("minecraft:wearable") ? 1 : number(properties.get("max_stack_size"), 64));
      this.maxDurability = number(components.getCompound("minecraft:durability").get("max_durability"), 0);
      this.maxStackSize = this.maxDurability > 0 ? 1 : Math.clamp((long)stack, 1, 99);
      NbtMap wearable = components.getCompound("minecraft:wearable");
      this.wearSlot = slot(wearable.getString("slot", ""));
      this.protection = number(wearable.get("protection"), number(components.getCompound("minecraft:armor").get("protection"), 0));
      this.handEquipped = flag(value(components.get("minecraft:hand_equipped")), flag(properties.get("hand_equipped"), false));
      this.allowOffhand = offhand(components);
      Object glintValue = value(components.get("minecraft:glint"));
      this.glint = flag(glintValue != null ? glintValue : properties.get("foil"), false);
      NbtMap foodMap = components.getCompound("minecraft:food");
      this.food = components.containsKey("minecraft:food")
         ? new CustomItem.Food(
            number(foodMap.get("nutrition"), 0), decimal(foodMap.get("saturation_modifier"), 0.6F), flag(foodMap.get("can_always_eat"), false)
         )
         : null;
      float modifierSeconds = decimal(components.getCompound("minecraft:use_modifiers").get("use_duration"), 0.0F);
      int ticks = number(properties.get("use_duration"), 0);
      this.useSeconds = modifierSeconds > 0.0F ? modifierSeconds : (ticks > 0 ? ticks / 20.0F : 1.6F);
      this.useAnimation = animation(value(components.get("minecraft:use_animation")), number(properties.get("use_animation"), 0));
      NbtMap shooter = components.getCompound("minecraft:shooter");
      boolean shoots = components.containsKey("minecraft:shooter") || components.containsKey("minecraft:chargeable");
      boolean kinetic = components.containsKey("minecraft:kinetic_weapon");
      boolean held = this.food == null && (shoots || kinetic || modifierSeconds > 0.0F);
      this.holdTicks = !held ? 0 : (modifierSeconds > 0.0F ? Math.max(1, Math.round(modifierSeconds * 20.0F)) : (ticks > 0 ? ticks : 72000));
      ItemUseAnimation var32;
      if (!held) {
         var32 = ItemUseAnimation.NONE;
      } else {
         switch (this.useAnimation) {
            case 3:
               var32 = ItemUseAnimation.BLOCK;
               break;
            case 4:
               var32 = ItemUseAnimation.BOW;
               break;
            case 5:
            case 7:
            case 8:
            case 11:
            default:
               var32 = kinetic
                  ? ItemUseAnimation.SPEAR
                  : (shoots ? (shooter.getBoolean("charge_on_draw", false) ? ItemUseAnimation.CROSSBOW : ItemUseAnimation.BOW) : ItemUseAnimation.NONE);
               break;
            case 6:
               var32 = ItemUseAnimation.SPEAR;
               break;
            case 9:
               var32 = ItemUseAnimation.CROSSBOW;
               break;
            case 10:
               var32 = ItemUseAnimation.SPYGLASS;
               break;
            case 12:
               var32 = ItemUseAnimation.BRUSH;
         }
      }

      this.holdAnimation = var32;
      NbtMap cooldown = components.getCompound("minecraft:cooldown");
      this.cooldownSeconds = decimal(cooldown.get("duration"), 0.0F);
      this.cooldownCategory = this.cooldownSeconds > 0.0F ? cooldown.getString("category", "") : null;
      this.enchantability = number(value(components.get("minecraft:enchantable")), number(properties.get("enchantable_value"), 0));
      this.digger = diggers(components.getCompound("minecraft:digger"));
      this.canDestroyInCreative = flag(value(components.get("minecraft:can_destroy_in_creative")), flag(properties.get("can_destroy_in_creative"), true));
      this.rarity = value(components.get("minecraft:rarity")) instanceof String namex ? namex : null;
      this.hoverColor = value(components.get("minecraft:hover_text_color")) instanceof String namexx ? namexx : null;
      NbtMap placer = components.getCompound("minecraft:block_placer");
      String placed = placer.getString("block", "");
      this.block = !placed.isEmpty() ? placed : (componentBased ? null : identifier);
      List<String> itemTags = components.getList("item_tags", NbtType.STRING, List.of());
      this.tags = List.copyOf(!itemTags.isEmpty() ? itemTags : components.getCompound("minecraft:tags").getList("tags", NbtType.STRING, List.of()));
   }

   @Nullable
   public static CustomItem read(String identifier, @Nullable NbtMap componentData, boolean componentBased) {
      return read(identifier, componentData, componentBased, null);
   }

   @Nullable
   public static CustomItem read(String identifier, @Nullable NbtMap componentData, boolean componentBased, @Nullable PackItemFiles.ItemFile file) {
      if (!identifier.startsWith("minecraft:") && identifier.indexOf(58) >= 0) {
         NbtMap components = componentData == null ? NbtMap.EMPTY : componentData.getCompound("components");
         if (components.isEmpty() && file != null) {
            components = fromItemFile(file);
         }

         try {
            return new CustomItem(identifier, components, componentBased && !components.isEmpty());
         } catch (RuntimeException var6) {
            BedrockNative.LOGGER.warn("Unreadable components for custom item {}: {}", new Object[]{identifier, components, var6});
            return new CustomItem(identifier, NbtMap.EMPTY, false);
         }
      } else {
         return null;
      }
   }

   static NbtMap fromItemFile(PackItemFiles.ItemFile file) {
      NbtMapBuilder builder = NbtMap.builder();
      if (file.hoverTextColor() != null) {
         builder.putString("minecraft:hover_text_color", file.hoverTextColor());
      }

      if (file.handEquipped() != null) {
         builder.putBoolean("minecraft:hand_equipped", file.handEquipped());
      }

      if (file.maxStackSize() != null) {
         builder.putInt("minecraft:max_stack_size", file.maxStackSize());
      }

      if (file.glint() != null) {
         builder.putBoolean("minecraft:glint", file.glint());
      }

      return builder.build();
   }

   public String identifier() {
      return this.identifier;
   }

   @Nullable
   public String icon() {
      return this.icon;
   }

   public int iconFrame() {
      return this.iconFrame;
   }

   public boolean handEquipped() {
      return this.handEquipped;
   }

   public List<String> tags() {
      return this.tags;
   }

   public boolean allowsOffhand() {
      return this.allowOffhand;
   }

   @Nullable
   public EquipmentSlot wearSlot() {
      return this.wearSlot;
   }

   @Nullable
   public String block() {
      return this.block;
   }

   public int maxStackSize() {
      return this.maxStackSize;
   }

   public int maxDurability() {
      return this.maxDurability;
   }

   public int holdTicks() {
      return this.holdTicks;
   }

   public ItemUseAnimation holdAnimation() {
      return this.holdAnimation;
   }

   public static boolean offhand(NbtMap components) {
      return flag(value(components.get("minecraft:allow_off_hand")), flag(components.getCompound("item_properties").get("allow_off_hand"), false));
   }

   public static float movementModifier(NbtMap components) {
      return decimal(components.getCompound("minecraft:use_modifiers").get("movement_modifier"), Float.NaN);
   }

   public void applyTo(ItemStack stack) {
      CustomItem.Built parts = this.built();
      stack.set(DataComponents.CUSTOM_MODEL_DATA, parts.modelData());
      stack.set(DataComponents.ITEM_NAME, this.name());
      stack.set(DataComponents.MAX_STACK_SIZE, this.maxStackSize);
      if (this.maxDurability > 0) {
         stack.set(DataComponents.MAX_DAMAGE, this.maxDurability);
         stack.set(DataComponents.DAMAGE, 0);
      }

      if (parts.equippable() != null) {
         stack.set(DataComponents.EQUIPPABLE, parts.equippable());
      }

      if (parts.armor() != null) {
         stack.set(DataComponents.ATTRIBUTE_MODIFIERS, parts.armor());
      }

      if (parts.food() != null) {
         stack.set(DataComponents.FOOD, parts.food());
         stack.set(DataComponents.CONSUMABLE, parts.consumable());
      }

      if (parts.cooldown() != null) {
         stack.set(DataComponents.USE_COOLDOWN, parts.cooldown());
      }

      if (parts.tool() != null) {
         stack.set(DataComponents.TOOL, parts.tool());
      }

      if (this.enchantability > 0) {
         stack.set(DataComponents.ENCHANTABLE, new Enchantable(this.enchantability));
      }

      if (this.glint) {
         stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
      }

      Rarity var10000;
      if (this.rarity == null) {
         var10000 = null;
      } else {
         String var4 = this.rarity.toLowerCase(Locale.ROOT);
         switch (var4) {
            case "uncommon":
               var10000 = Rarity.UNCOMMON;
               break;
            case "rare":
               var10000 = Rarity.RARE;
               break;
            case "epic":
               var10000 = Rarity.EPIC;
               break;
            default:
               var10000 = null;
         }
      }

      Rarity shown = var10000;
      if (shown != null) {
         stack.set(DataComponents.RARITY, shown);
      }
   }

   public Component name() {
      String key = this.displayName != null
         ? this.displayName
         : (this.block != null && this.block.equals(this.identifier) ? "tile." : "item.") + this.identifier + ".name";
      String text = BedrockText.lookupKey(key);
      MutableComponent name = text != null
         ? BedrockText.format(text)
         : (
            this.displayName != null
               ? BedrockText.format(this.displayName)
               : Component.literal(words(this.identifier.substring(this.identifier.indexOf(58) + 1)))
         );
      Integer color = HoverTextColor.rgb(this.hoverColor);
      return color != null ? name.withColor(color) : name;
   }

   private CustomItem.Built built() {
      CustomItem.Built parts = this.built;
      if (parts == null) {
         parts = this.build();
         this.built = parts;
      }

      return parts;
   }

   private CustomItem.Built build() {
      CustomModelData modelData = new CustomModelData(List.of(), List.of(), List.of(this.identifier), List.of());
      Equippable equippable = null;
      ItemAttributeModifiers armor = null;
      if (this.wearSlot != null) {
         equippable = Equippable.builder(this.wearSlot).setEquipSound(SoundEvents.ARMOR_EQUIP_GENERIC).build();
         if (this.protection > 0) {
            AttributeModifier modifier = new AttributeModifier(
               Identifier.withDefaultNamespace("armor." + armorName(this.wearSlot)), this.protection, Operation.ADD_VALUE
            );
            armor = ItemAttributeModifiers.builder().add(Attributes.ARMOR, modifier, EquipmentSlotGroup.bySlot(this.wearSlot)).build();
         }
      }

      FoodProperties foodProperties = null;
      Consumable consumable = null;
      if (this.food != null) {
         foodProperties = new FoodProperties(this.food.nutrition(), this.food.nutrition() * this.food.saturationModifier() * 2.0F, this.food.canAlwaysEat());
         boolean drink = this.useAnimation == 2;
         consumable = Consumable.builder()
            .consumeSeconds(this.useSeconds)
            .animation(drink ? ItemUseAnimation.DRINK : ItemUseAnimation.EAT)
            .sound(drink ? SoundEvents.GENERIC_DRINK : SoundEvents.GENERIC_EAT)
            .hasConsumeParticles(!drink)
            .build();
      }

      UseCooldown cooldown = null;
      if (this.cooldownCategory != null) {
         String group = this.cooldownCategory.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_./-]", "_");
         cooldown = new UseCooldown(
            this.cooldownSeconds, group.isEmpty() ? Optional.empty() : Optional.of(Identifier.fromNamespaceAndPath("bedrocknative", "cooldown/" + group))
         );
      }

      Tool tool = null;
      if (!this.digger.isEmpty() || !this.canDestroyInCreative) {
         List<Rule> rules = new ArrayList<>();

         for (CustomItem.Digger entry : this.digger) {
            List<Holder<Block>> blocks = new ArrayList<>();

            for (String name : entry.blocks()) {
               BlockState state = BlockMappings.get().byName(name.contains(":") ? name : "minecraft:" + name);
               if (state != null && !blocks.contains(state.getBlock().builtInRegistryHolder())) {
                  blocks.add(state.getBlock().builtInRegistryHolder());
               }
            }

            if (!blocks.isEmpty()) {
               rules.add(Rule.overrideSpeed(HolderSet.direct(blocks), entry.speed()));
            }

            entry.tags()
               .stream()
               .map(CustomItem::javaTag)
               .filter(tag -> tag != null)
               .distinct()
               .forEach(tag -> rules.add(Rule.overrideSpeed(BuiltInRegistries.BLOCK.getOrThrow(tag), entry.speed())));
         }

         tool = new Tool(rules, 1.0F, 0, this.canDestroyInCreative);
      }

      return new CustomItem.Built(modelData, equippable, armor, foodProperties, consumable, cooldown, tool);
   }

   @Nullable
   private static TagKey<Block> javaTag(String bedrockTag) {
      String tag = bedrockTag.startsWith("minecraft:") ? bedrockTag.substring("minecraft:".length()) : bedrockTag;

      return switch (tag) {
         case "stone", "metal", "rock", "is_pickaxe_item_destructible", "diamond_pick_diggable", "iron_pick_diggable", "stone_pick_diggable", "gold_pick_diggable", "wood_pick_diggable", "netherite_pick_diggable" -> BlockTags.MINEABLE_WITH_PICKAXE;
         case "wood", "log", "is_axe_item_destructible" -> BlockTags.MINEABLE_WITH_AXE;
         case "dirt", "sand", "gravel", "grass", "snow", "is_shovel_item_destructible" -> BlockTags.MINEABLE_WITH_SHOVEL;
         case "plant", "crop", "is_hoe_item_destructible" -> BlockTags.MINEABLE_WITH_HOE;
         case "is_sword_item_destructible" -> BlockTags.SWORD_EFFICIENT;
         default -> null;
      };
   }

   private static String armorName(EquipmentSlot slot) {
      return switch (slot) {
         case HEAD -> "helmet";
         case CHEST -> "chestplate";
         case LEGS -> "leggings";
         case FEET -> "boots";
         default -> "body";
      };
   }

   @Nullable
   private static EquipmentSlot slot(String bedrockSlot) {
      return switch (bedrockSlot) {
         case "slot.armor.head" -> EquipmentSlot.HEAD;
         case "slot.armor.chest" -> EquipmentSlot.CHEST;
         case "slot.armor.legs" -> EquipmentSlot.LEGS;
         case "slot.armor.feet" -> EquipmentSlot.FEET;
         default -> null;
      };
   }

   private static int animation(@Nullable Object named, int number) {
      if (named instanceof String name) {
         return switch (name) {
            case "eat" -> 1;
            case "drink" -> 2;
            case "block" -> 3;
            case "bow" -> 4;
            case "spear" -> 6;
            case "crossbow" -> 9;
            case "spyglass" -> 10;
            case "brush" -> 12;
            default -> 0;
         };
      } else {
         return named instanceof Number value ? value.intValue() : number;
      }
   }

   private static List<CustomItem.Digger> diggers(NbtMap digger) {
      List<CustomItem.Digger> result = new ArrayList<>();

      for (NbtMap entry : digger.getList("destroy_speeds", NbtType.COMPOUND)) {
         int speed = number(entry.get("speed"), 1);
         List<String> blocks = new ArrayList<>();
         List<String> tags = new ArrayList<>();
         Object block = entry.get("block");
         if (block instanceof String name) {
            blocks.add(name);
         } else if (block instanceof NbtMap descriptor) {
            String name = descriptor.getString("name", "");
            if (!name.isEmpty()) {
               blocks.add(name);
            }

            Matcher quoted = QUOTED.matcher(descriptor.getString("tags", ""));

            while (quoted.find()) {
               tags.add(quoted.group(1) != null ? quoted.group(1) : quoted.group(2));
            }
         }

         result.add(new CustomItem.Digger(List.copyOf(blocks), List.copyOf(tags), speed));
      }

      return List.copyOf(result);
   }

   @Nullable
   private static Object value(@Nullable Object component) {
      return component instanceof NbtMap map ? map.get("value") : component;
   }

   @Nullable
   private static String text(@Nullable Object component) {
      return value(component) instanceof String text && !text.isEmpty() ? text : null;
   }

   private static int number(@Nullable Object value, int fallback) {
      return value instanceof Number number ? number.intValue() : fallback;
   }

   private static float decimal(@Nullable Object value, float fallback) {
      return value instanceof Number number ? number.floatValue() : fallback;
   }

   private static boolean flag(@Nullable Object value, boolean fallback) {
      return value instanceof Number number ? number.intValue() != 0 : (value instanceof Boolean bool ? bool : fallback);
   }

   private static String words(String path) {
      StringBuilder words = new StringBuilder();

      for (String word : path.split("_")) {
         if (!word.isEmpty()) {
            words.append(words.isEmpty() ? "" : " ").append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
         }
      }

      return words.toString();
   }

   @Override
   public String toString() {
      return this.identifier
         + "{icon "
         + this.icon
         + ", stack "
         + this.maxStackSize
         + (this.maxDurability > 0 ? ", durability " + this.maxDurability : "")
         + (this.wearSlot != null ? ", worn " + this.wearSlot.getName() + " " + this.protection : "")
         + (this.food != null ? ", food " + this.food : "")
         + (this.holdTicks > 0 ? ", held " + this.holdAnimation.getSerializedName() + " " + this.holdTicks : "")
         + (this.handEquipped ? ", hand equipped" : "")
         + (this.allowOffhand ? ", offhand" : "")
         + (this.block != null ? ", block " + this.block : "")
         + (this.digger.isEmpty() ? "" : ", digger " + LogLists.summary(this.digger, 3, 120))
         + "}";
   }

   private record Built(
      CustomModelData modelData,
      @Nullable Equippable equippable,
      @Nullable ItemAttributeModifiers armor,
      @Nullable FoodProperties food,
      @Nullable Consumable consumable,
      @Nullable UseCooldown cooldown,
      @Nullable Tool tool
   ) {
   }

   public record Digger(List<String> blocks, List<String> tags, int speed) {
   }

   public record Food(int nutrition, float saturationModifier, boolean canAlwaysEat) {
   }
}
