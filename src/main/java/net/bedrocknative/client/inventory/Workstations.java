package net.bedrocknative.client.inventory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.LongFunction;
import java.util.function.LongToIntFunction;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.movement.BedrockEffects;
import net.bedrocknative.client.world.BedrockBeacons;
import net.bedrocknative.text.BedrockText;
import net.bedrocknative.ui.ContainerScreens;
import net.bedrocknative.world.item.BedrockItemTags;
import net.bedrocknative.world.item.ItemMappings;
import net.bedrocknative.world.recipe.BookRecipe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundMerchantOffersPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.BeaconMenu;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.LoomMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipePropertySet;
import net.minecraft.world.item.crafting.SelectableRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.item.crafting.SelectableRecipe.SingleInputEntry;
import net.minecraft.world.item.crafting.SelectableRecipe.SingleInputSet;
import net.minecraft.world.item.crafting.display.SlotDisplay.ItemStackSlotDisplay;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.block.entity.BannerPattern;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.protocol.bedrock.data.TrimMaterial;
import org.cloudburstmc.protocol.bedrock.data.TrimPattern;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.EnchantData;
import org.cloudburstmc.protocol.bedrock.data.inventory.EnchantOptionData;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData.Builder;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.ShapelessRecipeData;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.SmithingTransformRecipeData;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.SmithingTrimRecipeData;
import org.cloudburstmc.protocol.bedrock.data.inventory.descriptor.DefaultDescriptor;
import org.cloudburstmc.protocol.bedrock.data.inventory.descriptor.ItemDescriptor;
import org.cloudburstmc.protocol.bedrock.data.inventory.descriptor.ItemTagDescriptor;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.TextProcessingEventOrigin;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.CraftGrindstoneAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.CraftLoomAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.CraftRecipeAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.CraftRecipeOptionalAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.CraftResultsDeprecatedAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.ItemStackRequestAction;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerEnchantOptionsPacket;
import org.cloudburstmc.protocol.bedrock.packet.TrimDataPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateTradePacket;
import org.jspecify.annotations.Nullable;

public final class Workstations {
   static final UUID MAP_EXTENDING = UUID.fromString("8b36268c-1829-483c-a0f1-993b7156a8f2");
   static final UUID MAP_CLONING = UUID.fromString("442d85ed-8272-4543-a6f1-418f90ded05d");
   static final UUID MAP_LOCKING = UUID.fromString("602234e4-cac1-4353-8bb7-b1ebff70024b");
   static final UUID REPAIR_ITEM = UUID.fromString("00000000-0000-0000-0000-000000000001");
   private static final int LOCKED_MAP = 6;
   private static final int OPTIONS = 3;
   private static final int ENCHANT_SEED_DATA = 3;
   private final Minecraft minecraft;
   private final BedrockInventory inventory;
   private final Crafting crafting;
   private final Function<String, ItemDefinition> definitions;
   private final LongFunction<Entity> traders;
   private final LongToIntFunction tradeExperience;
   private final WorkstationRules.Facts facts;
   private final List<EnchantOptionData> enchantOptions = new ArrayList<>();
   private final Map<String, String> trimPatterns = new HashMap<>();
   private final Map<String, String> trimMaterials = new HashMap<>();
   private List<Workstations.StonecutterEntry> stonecutter = List.of();
   @Nullable
   private Workstations.Trade trade;
   @Nullable
   private Workstations.TradeView tradeView;
   @Nullable
   private String anvilName;
   private int activeOffer = -1;
   @Nullable
   private Workstations.StonecutterEntry shownStonecutter;
   @Nullable
   private String shownPattern;
   @Nullable
   private BeaconMenu shownBeacon;
   @Nullable
   private BedrockBeacons.Powers shownPowers;
   private int installedRecipes = -1;
   @Nullable
   private ClientPacketListener installedFor;

   public Workstations(
      Minecraft minecraft,
      BedrockInventory inventory,
      Crafting crafting,
      Function<String, ItemDefinition> definitions,
      LongFunction<Entity> traders,
      LongToIntFunction tradeExperience
   ) {
      this.minecraft = minecraft;
      this.inventory = inventory;
      this.crafting = crafting;
      this.definitions = definitions;
      this.traders = traders;
      this.tradeExperience = tradeExperience;
      this.facts = new Workstations.JavaFacts(definitions);
   }

   public static boolean handles(BedrockPacket packet) {
      return packet instanceof PlayerEnchantOptionsPacket || packet instanceof UpdateTradePacket || packet instanceof TrimDataPacket;
   }

   public void reset() {
      this.enchantOptions.clear();
      this.trade = null;
      this.tradeView = null;
      this.anvilName = null;
      this.activeOffer = -1;
      this.shownStonecutter = null;
      this.shownPattern = null;
      this.installedFor = null;
   }

   public WorkstationRules.Facts facts() {
      return this.facts;
   }

   public void apply(BedrockPacket packet) {
      switch (packet) {
         case PlayerEnchantOptionsPacket options:
            this.enchantOptions.clear();
            this.enchantOptions.addAll(options.getOptions());
            BedrockNative.LOGGER.debug("Enchanting options: {}", options.getOptions());
            break;
         case UpdateTradePacket update:
            this.onTrade(update);
            break;
         case TrimDataPacket trims:
            label29: {
               this.trimPatterns.clear();
               this.trimMaterials.clear();

               for (TrimPattern pattern : trims.getPatterns()) {
                  this.trimPatterns.put(pattern.getItemName(), pattern.getPatternId());
               }

               for (TrimMaterial material : trims.getMaterials()) {
                  this.trimMaterials.put(material.getItemName(), material.getMaterialId());
               }
               break label29;
            }
         default:
      }
   }

   public static int resultSlot(AbstractContainerMenu menu) {
      return switch (menu) {
         case AnvilMenu ignored -> 2;
         case GrindstoneMenu ignoredx -> 2;
         case StonecutterMenu ignoredxx -> 1;
         case CartographyTableMenu ignoredxxx -> 2;
         case LoomMenu ignoredxxxx -> 3;
         case SmithingMenu ignoredxxxxx -> 3;
         case MerchantMenu ignoredxxxxxx -> 2;
         default -> -1;
      };
   }

   public void tick(@Nullable AbstractContainerMenu menu) {
      this.installRecipes();
      if (menu != null && menu.containerId == this.inventory.openContainerId()) {
         switch (menu) {
            case EnchantmentMenu enchanting:
               this.showOptions(enchanting);
               break;
            case AnvilMenu anvil:
               WorkstationRules.Anvil result = this.anvil();
               show(menu, 2, result == null ? null : result.result());
               int cost = result == null ? 0 : result.cost();
               if (anvil.getCost() != cost) {
                  anvil.setData(0, cost);
               }
               break;
            case MerchantMenu merchant:
               this.observe(merchant);
               break;
            case BeaconMenu beacon:
               this.showBeacon(beacon);
               break;
            default:
               int slot = resultSlot(menu);
               if (slot >= 0) {
                  show(menu, slot, this.preview(menu));
               }
         }
      }
   }

   private void showBeacon(BeaconMenu menu) {
      BlockPos pos = this.inventory.openPosition();
      if (this.minecraft.level != null && pos != null) {
         int levels = BedrockBeacons.levels(this.minecraft.level, pos);
         if (menu.getLevels() != levels) {
            menu.setData(0, levels);
         }

         BedrockBeacons.Powers powers = BedrockBeacons.powersAt(this.minecraft.level, pos);
         if (menu != this.shownBeacon || !powers.equals(this.shownPowers)) {
            this.shownBeacon = menu;
            this.shownPowers = powers;
            menu.setData(1, BeaconMenu.encodeEffect(BedrockEffects.byId(powers.primary())));
            menu.setData(2, BeaconMenu.encodeEffect(BedrockEffects.byId(powers.secondary())));
         }
      }
   }

   void beaconChosen(BeaconMenu menu, int primary, int secondary) {
      menu.setData(1, BeaconMenu.encodeEffect(BedrockEffects.byId(primary)));
      menu.setData(2, BeaconMenu.encodeEffect(BedrockEffects.byId(secondary)));
   }

   public void observe(AbstractContainerMenu menu) {
      if (menu instanceof MerchantMenu merchant && merchant.getSlot(0).container instanceof MerchantContainer container) {
         MerchantOffer offer = container.getActiveOffer();
         this.activeOffer = -1;

         for (int i = 0; offer != null && i < merchant.getOffers().size(); i++) {
            if (merchant.getOffers().get(i) == offer) {
               this.activeOffer = i;
            }
         }
      }
   }

   public void onRename(String name) {
      this.anvilName = name;
   }

   @Nullable
   public EnchantOptionData enchantOption(int index) {
      return index >= 0 && index < this.enchantOptions.size() ? this.enchantOptions.get(index) : null;
   }

   @Nullable
   private ItemData preview(AbstractContainerMenu menu) {
      return switch (menu) {
         case GrindstoneMenu ignored -> WorkstationRules.grindstone(
            this.inventory.get(ContainerLayout.GRINDSTONE_INPUT), this.inventory.get(ContainerLayout.GRINDSTONE_ADDITIONAL), this.facts
         );
         case StonecutterMenu cutter -> {
            ShapelessRecipeData recipe = this.stonecutterRecipe(cutter, false);
            yield recipe == null ? null : recipe.getResults().getFirst();
         }
         case LoomMenu loom -> this.loomResult(this.selectedPattern(loom, false));
         case SmithingMenu ignoredx -> {
            Workstations.Smithing smithing = this.smithing();
            yield smithing == null ? null : smithing.result();
         }
         case CartographyTableMenu ignoredxx -> {
            Workstations.Cartography cartography = this.cartography();
            yield cartography == null ? null : cartography.result();
         }
         default -> null;
      };
   }

   @Nullable
   public Workstations.Craft craftFor(AbstractContainerMenu menu, int made) {
      switch (menu) {
         case AnvilMenu var23:
            AnvilMenu ignoredxxxx = (AnvilMenu)menu;
            WorkstationRules.Anvil anvil = this.anvil();
            return anvil != null && anvil.result() != null && made == anvil.result().getCount()
               ? anvil(this.anvilFilter(this.inventory.get(ContainerLayout.ANVIL_INPUT)), anvil)
               : null;
         case GrindstoneMenu ignored: {
            ItemData input = this.inventory.get(ContainerLayout.GRINDSTONE_INPUT);
            ItemData additional = this.inventory.get(ContainerLayout.GRINDSTONE_ADDITIONAL);
            ItemData result = WorkstationRules.grindstone(input, additional, this.facts);
            return result != null && made == result.getCount()
               ? grindstone(result, input, additional, GrindstoneVariant.active(), this.crafting.multiRecipe(REPAIR_ITEM))
               : null;
         }
         case StonecutterMenu cutter:
            ShapelessRecipeData recipe = this.stonecutterRecipe(cutter, true);
            int per = recipe == null ? 0 : recipe.getResults().getFirst().getCount();
            int times = per != 0 && made % per == 0 ? made / per : 0;
            return times > 0 && this.inventory.get(ContainerLayout.STONECUTTER_INPUT).getCount() >= times ? stonecutter(recipe, times) : null;
         case LoomMenu loom: {
            String pattern = this.selectedPattern(loom, true);
            ItemData result = this.loomResult(pattern);
            return result != null && made == result.getCount() ? loom(pattern, result) : null;
         }
         case SmithingMenu ignoredx:
            Workstations.Smithing smithing = this.smithing();
            return smithing != null && made == smithing.result().getCount() ? smithing(smithing.netId(), smithing.result()) : null;
         case CartographyTableMenu ignoredxx:
            Workstations.Cartography cartography = this.cartography();
            return cartography != null && made == cartography.result().getCount() ? cartography(cartography.netId(), cartography.result()) : null;
         case MerchantMenu ignoredxxx:
            return this.tradeCraft(made);
         default:
            return null;
      }
   }

   public static Workstations.Craft enchant(EnchantOptionData option, int index, ItemData result, boolean lapis) {
      return new Workstations.Craft(
         List.of(new CraftRecipeAction(option.getEnchantNetId(), 1), new CraftResultsDeprecatedAction(new ItemData[]{result}, 1)),
         List.of(new Workstations.Use(ContainerLayout.ENCHANTING_INPUT, 1)),
         result,
         1,
         lapis ? List.of(new Workstations.Use(ContainerLayout.ENCHANTING_MATERIAL, index + 1)) : List.of(),
         List.of(),
         null
      );
   }

   public static Workstations.Craft trade(TradeOffer offer, ItemData sell, int times, BedrockSlot slotA, @Nullable BedrockSlot slotB) {
      List<Workstations.Use> uses = new ArrayList<>(List.of(new Workstations.Use(slotA, offer.priceA() * times)));
      if (slotB != null && offer.hasB()) {
         uses.add(new Workstations.Use(slotB, offer.priceB() * times));
      }

      return new Workstations.Craft(
         List.of(new CraftRecipeAction(offer.netId(), times), new CraftResultsDeprecatedAction(new ItemData[]{sell}, times)), uses, sell, times
      );
   }

   public static Workstations.Craft anvil(String name, WorkstationRules.Anvil anvil) {
      List<Workstations.Use> uses = new ArrayList<>(List.of(new Workstations.Use(ContainerLayout.ANVIL_INPUT, anvil.result().getCount())));
      if (anvil.materialUsed() > 0) {
         uses.add(new Workstations.Use(ContainerLayout.ANVIL_MATERIAL, anvil.materialUsed()));
      }

      return new Workstations.Craft(
         List.of(new CraftRecipeOptionalAction(0, 0)), uses, anvil.result(), 1, List.of(), List.of(name), TextProcessingEventOrigin.ANVIL_TEXT
      );
   }

   public static Workstations.Craft grindstone(ItemData result, ItemData input, ItemData additional, GrindstoneVariant variant, int repairItemRecipe) {
      List<Workstations.Use> uses = new ArrayList<>();
      if (!input.isNull()) {
         uses.add(new Workstations.Use(ContainerLayout.GRINDSTONE_INPUT, 1));
      }

      if (!additional.isNull()) {
         uses.add(new Workstations.Use(ContainerLayout.GRINDSTONE_ADDITIONAL, 1));
      }

      List<ItemStackRequestAction> header = new ArrayList<>();
      header.add(new CraftGrindstoneAction(variant.idFor(input, additional, repairItemRecipe), 1, variant.costFor(result, input, additional)));
      if (variant.deprecatedResults()) {
         header.add(new CraftResultsDeprecatedAction(new ItemData[]{result}, 1));
      }

      BedrockNative.LOGGER.info("Grindstone craft {}: {}", variant.name(), header);
      return new Workstations.Craft(List.copyOf(header), uses, result, 1, List.of(), List.of(), null, variant.consumeFirst());
   }

   public static Workstations.Craft stonecutter(ShapelessRecipeData recipe, int times) {
      ItemData result = recipe.getResults().getFirst();
      return new Workstations.Craft(
         List.of(new CraftRecipeAction(recipe.getNetId(), times)), List.of(new Workstations.Use(ContainerLayout.STONECUTTER_INPUT, times)), result, times
      );
   }

   public static Workstations.Craft loom(String pattern, ItemData result) {
      return new Workstations.Craft(
         List.of(new CraftLoomAction(pattern, 1), new CraftResultsDeprecatedAction(new ItemData[]{result}, 1)),
         List.of(new Workstations.Use(ContainerLayout.LOOM_INPUT, 1), new Workstations.Use(ContainerLayout.LOOM_DYE, 1)),
         result,
         1
      );
   }

   public static Workstations.Craft smithing(int netId, ItemData result) {
      return new Workstations.Craft(
         List.of(new CraftRecipeAction(netId, 1), new CraftResultsDeprecatedAction(new ItemData[]{result}, 1)),
         List.of(
            new Workstations.Use(ContainerLayout.SMITHING_INPUT, 1),
            new Workstations.Use(ContainerLayout.SMITHING_MATERIAL, 1),
            new Workstations.Use(ContainerLayout.SMITHING_TEMPLATE, 1)
         ),
         result,
         1
      );
   }

   public static Workstations.Craft cartography(int netId, ItemData result) {
      return new Workstations.Craft(
         List.of(new CraftRecipeOptionalAction(netId, 0)),
         List.of(new Workstations.Use(ContainerLayout.CARTOGRAPHY_INPUT, 1), new Workstations.Use(ContainerLayout.CARTOGRAPHY_ADDITIONAL, 1)),
         result,
         1,
         List.of(),
         List.of(""),
         TextProcessingEventOrigin.CARTOGRAPHY_TEXT
      );
   }

   private void showOptions(EnchantmentMenu menu) {
      Registry<Enchantment> registry = this.minecraft.level == null ? null : this.minecraft.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
      int seed = this.enchantOptions.stream().mapToInt(optionx -> optionx.getEnchantName().hashCode()).sum();

      for (int i = 0; i < 3; i++) {
         EnchantOptionData option = i < this.enchantOptions.size() ? this.enchantOptions.get(i) : null;
         EnchantData clue = option == null ? null : firstEnchant(option);
         int clueId = clue != null && registry != null
            ? BedrockItemTags.enchantment(clue.getType()).flatMap(Reference::unwrapKey).map(registry::getValue).<Integer>map(registry::getId).orElse(-1)
            : -1;
         setData(menu, i, option == null ? 0 : option.getCost(), menu.costs[i]);
         setData(menu, 4 + i, clueId, menu.enchantClue[i]);
         setData(menu, 7 + i, clueId < 0 ? -1 : clue.getLevel(), menu.levelClue[i]);
      }

      setData(menu, 3, seed, menu.getEnchantmentSeed());
   }

   @Nullable
   private static EnchantData firstEnchant(EnchantOptionData option) {
      for (List<EnchantData> list : List.of(option.getEnchants0(), option.getEnchants1(), option.getEnchants2())) {
         if (!list.isEmpty()) {
            return list.getFirst();
         }
      }

      return null;
   }

   private static void setData(AbstractContainerMenu menu, int id, int value, int current) {
      if (value != current) {
         menu.setData(id, value);
      }
   }

   @Nullable
   private WorkstationRules.Anvil anvil() {
      ItemData left = this.inventory.get(ContainerLayout.ANVIL_INPUT);
      if (left.isNull()) {
         return null;
      } else {
         LocalPlayer player = this.minecraft.player;
         return WorkstationRules.anvil(
            left, this.inventory.get(ContainerLayout.ANVIL_MATERIAL), this.rename(left), player != null && player.hasInfiniteMaterials(), this.facts
         );
      }
   }

   @Nullable
   private String rename(ItemData left) {
      String name = this.anvilName;
      return name != null && !name.equals(ItemMappings.toJava(left).getHoverName().getString()) ? name : null;
   }

   private String anvilFilter(ItemData left) {
      String rename = this.rename(left);
      if (rename != null) {
         return rename;
      } else {
         String custom = WorkstationRules.customName(left);
         return custom == null ? "" : custom;
      }
   }

   @Nullable
   private ShapelessRecipeData stonecutterRecipe(StonecutterMenu menu, boolean taking) {
      ItemData input = this.inventory.get(ContainerLayout.STONECUTTER_INPUT);
      if (input.isNull()) {
         return null;
      } else {
         ItemStack java = ItemMappings.toJava(input);
         List<Workstations.StonecutterEntry> matching = this.stonecutter.stream().filter(entry -> entry.input().test(java)).toList();
         int index = menu.getSelectedRecipeIndex();
         if (index >= 0 && index < matching.size()) {
            this.shownStonecutter = matching.get(index);
            return this.shownStonecutter.recipe();
         } else {
            return taking && this.shownStonecutter != null && matching.contains(this.shownStonecutter) ? this.shownStonecutter.recipe() : null;
         }
      }
   }

   public int stonecutterServerIndex(StonecutterMenu menu) {
      ShapelessRecipeData recipe = this.stonecutterRecipe(menu, false);
      if (recipe == null) {
         return -1;
      } else {
         List<ShapelessRecipeData> server = this.crafting.stonecutter();

         for (int i = 0; i < server.size(); i++) {
            if (server.get(i) == recipe) {
               return i;
            }
         }

         return -1;
      }
   }

   @Nullable
   private String selectedPattern(LoomMenu menu, boolean taking) {
      List<Holder<BannerPattern>> patterns = menu.getSelectablePatterns();
      int index = menu.getSelectedBannerPatternIndex();
      if (index >= 0 && index < patterns.size()) {
         this.shownPattern = patterns.get(index).unwrapKey().map(key -> BedrockItemTags.bannerPatternCode(key.identifier().getPath())).orElse(null);
         return this.shownPattern;
      } else {
         return taking ? this.shownPattern : null;
      }
   }

   @Nullable
   private ItemData loomResult(@Nullable String pattern) {
      ItemData banner = this.inventory.get(ContainerLayout.LOOM_INPUT);
      ItemData dye = this.inventory.get(ContainerLayout.LOOM_DYE);
      if (!banner.isNull() && !dye.isNull() && pattern != null) {
         DyeColor color = (DyeColor)ItemMappings.toJava(dye).get(DataComponents.DYE);
         return color == null ? null : WorkstationRules.loom(banner, pattern, 15 - color.getId());
      } else {
         return null;
      }
   }

   @Nullable
   private Workstations.Smithing smithing() {
      ItemData template = this.inventory.get(ContainerLayout.SMITHING_TEMPLATE);
      ItemData base = this.inventory.get(ContainerLayout.SMITHING_INPUT);
      ItemData addition = this.inventory.get(ContainerLayout.SMITHING_MATERIAL);
      if (!template.isNull() && !base.isNull() && !addition.isNull()) {
         for (SmithingTransformRecipeData recipe : this.crafting.smithingTransforms()) {
            if (this.fits(recipe.getTemplate().getDescriptor(), template)
               && this.fits(recipe.getBase().getDescriptor(), base)
               && this.fits(recipe.getAddition().getDescriptor(), addition)) {
               return new Workstations.Smithing(recipe.getNetId(), WorkstationRules.smithingTransform(base, recipe.getResult()));
            }
         }

         for (SmithingTrimRecipeData recipex : this.crafting.smithingTrims()) {
            String pattern = this.trimPatterns.get(template.getDefinition().getIdentifier());
            String material = this.trimMaterials.get(addition.getDefinition().getIdentifier());
            if (pattern != null
               && material != null
               && this.fits(recipex.getTemplate().getDescriptor(), template)
               && this.fits(recipex.getBase().getDescriptor(), base)
               && this.fits(recipex.getAddition().getDescriptor(), addition)) {
               return new Workstations.Smithing(recipex.getNetId(), WorkstationRules.smithingTrim(base, pattern, material));
            }
         }

         return null;
      } else {
         return null;
      }
   }

   private boolean fits(ItemDescriptor descriptor, ItemData item) {
      return this.crafting.matches(descriptor, item);
   }

   @Nullable
   private Workstations.Cartography cartography() {
      ItemData map = this.inventory.get(ContainerLayout.CARTOGRAPHY_INPUT);
      ItemData additional = this.inventory.get(ContainerLayout.CARTOGRAPHY_ADDITIONAL);
      if (!map.isNull() && !additional.isNull() && map.getDefinition().getIdentifier().equals("minecraft:filled_map")) {
         Builder result = map.toBuilder().count(1).usingNetId(false).netId(0);
         String netId = additional.getDefinition().getIdentifier();
         UUID recipe;
         switch (netId) {
            case "minecraft:paper":
               recipe = MAP_EXTENDING;
               break;
            case "minecraft:empty_map":
               recipe = MAP_CLONING;
               result.count(2);
               break;
            case "minecraft:glass_pane":
               recipe = MAP_LOCKING;
               result.damage(6);
               break;
            default:
               return null;
         }

         int netId = this.crafting.multiRecipe(recipe);
         return netId == 0 ? null : new Workstations.Cartography(netId, result.build());
      } else {
         return null;
      }
   }

   private void onTrade(UpdateTradePacket packet) {
      List<TradeOffer> offers = TradeOffer.parse(packet.getOffers());
      List<TradeOffer> shown = offers.stream().filter(offer -> offer.tier() <= packet.getTradeTier()).toList();
      this.trade = new Workstations.Trade(packet.getContainerId(), shown, packet.getTradeTier(), packet.getTraderUniqueEntityId());
      BedrockNative.LOGGER
         .debug("Trade {}: {} of {} offers unlocked at tier {}", new Object[]{packet.getContainerId(), shown.size(), offers.size(), packet.getTradeTier()});
      boolean opened = this.inventory.openTrade(packet.getContainerId(), packet.isNewTradingUi());
      ClientPacketListener connection = this.minecraft.getConnection();
      LocalPlayer player = this.minecraft.player;
      if (connection != null && player != null) {
         Entity trader = this.traders.apply(packet.getTraderUniqueEntityId());
         String key = packet.getDisplayName();
         String text = key == null ? null : BedrockText.lookupKey(key);
         String name = text != null
            ? text
            : (key != null && !key.isBlank() ? key : Objects.requireNonNullElse(BedrockText.lookupKey("entity.villager.name"), "Villager"));
         List<Integer> tierExp = TradeOffer.tierExp(packet.getOffers());
         boolean levels = trader != null ? trader instanceof Villager : tierExp.size() > 1;
         this.tradeView = new Workstations.TradeView(
            packet.getContainerId(), offers, packet.getTradeTier(), tierExp, packet.getTraderUniqueEntityId(), name, levels
         );
         if (opened || player.containerMenu.containerId != packet.getContainerId()) {
            Component title = text != null ? BedrockText.format(text) : Component.translatable("entity.minecraft.villager");
            JsonUiContainers.opening(
               packet.getContainerId(),
               ContainerScreens.trade(packet.isNewTradingUi()),
               null,
               this,
               () -> connection.handleOpenScreen(new ClientboundOpenScreenPacket(packet.getContainerId(), MenuType.MERCHANT, title))
            );
         }

         connection.handleMerchantOffers(
            new ClientboundMerchantOffersPacket(
               packet.getContainerId(),
               javaOffers(shown),
               packet.getTradeTier() + 1,
               this.tradeExperience.applyAsInt(packet.getTraderUniqueEntityId()),
               trader instanceof Villager,
               false
            )
         );
         ContainerLayout layout = this.inventory.openLayout();
         if (layout != null) {
            layout.slots().forEach(this.inventory::show);
         }
      }
   }

   @Nullable
   public Workstations.TradeView tradeView() {
      return this.tradeView;
   }

   public int traderExperience(long trader) {
      return this.tradeExperience.applyAsInt(trader);
   }

   static ItemStack javaItem(NbtMap item) {
      return TradeOffer.isItem(item) ? ItemMappings.fromNbt(anyData(item)) : ItemStack.EMPTY;
   }

   private static MerchantOffers javaOffers(List<TradeOffer> shown) {
      MerchantOffers offers = new MerchantOffers();

      for (TradeOffer offer : shown) {
         Item costA = ItemMappings.fromNbt(anyData(offer.buyA())).getItem();
         int base = offer.buyCountA() > 0 ? offer.buyCountA() : offer.priceA();
         Optional<ItemCost> costB = offer.hasB()
            ? Optional.of(new ItemCost(ItemMappings.fromNbt(anyData(offer.buyB())).getItem(), offer.priceB()))
            : Optional.empty();
         MerchantOffer java = new MerchantOffer(
            new ItemCost(costA, base), costB, ItemMappings.fromNbt(offer.sell()), offer.uses(), offer.maxUses(), offer.traderExp(), offer.priceMultiplierA(), 0
         );
         java.setSpecialPriceDiff(offer.priceA() - base);
         offers.add(java);
      }

      return offers;
   }

   private static NbtMap anyData(NbtMap item) {
      return item.getShort("Damage", (short)0) == 32767 ? item.toBuilder().putShort("Damage", (short)0).build() : item;
   }

   @Nullable
   private Workstations.Craft tradeCraft(int made) {
      Workstations.Trade open = this.trade;
      if (open != null && this.activeOffer >= 0 && this.activeOffer < open.shown().size()) {
         TradeOffer offer = open.shown().get(this.activeOffer);
         ItemData sell = TradeOffer.item(offer.sell(), this.definitions);
         if (sell != null && !offer.outOfStock() && made % sell.getCount() == 0) {
            int times = made / sell.getCount();
            ContainerLayout layout = this.inventory.openLayout();
            if (layout != null && times > 0) {
               BedrockSlot first = layout.slots().get(0);
               BedrockSlot second = layout.slots().get(1);
               BedrockSlot slotA = this.holds(first, offer.buyA(), offer.priceA() * times)
                  ? first
                  : (this.holds(second, offer.buyA(), offer.priceA() * times) ? second : null);
               BedrockSlot slotB = !offer.hasB() ? null : (slotA == first ? second : first);
               return slotA != null && (slotB == null || this.holds(slotB, offer.buyB(), offer.priceB() * times))
                  ? trade(offer, WorkstationRules.withDamage(sell, this.facts), times, slotA, slotB)
                  : null;
            } else {
               return null;
            }
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   private boolean holds(BedrockSlot slot, NbtMap item, int count) {
      ItemData held = this.inventory.get(slot);
      return !held.isNull() && held.getDefinition().getIdentifier().equals(item.getString("Name", "")) && held.getCount() >= count;
   }

   private void installRecipes() {
      ClientPacketListener connection = this.minecraft.getConnection();
      if (connection != null && (connection != this.installedFor || this.installedRecipes != this.crafting.version())) {
         this.installedFor = connection;
         this.installedRecipes = this.crafting.version();
         List<Workstations.StonecutterEntry> entries = new ArrayList<>();
         List<SingleInputEntry<StonecutterRecipe>> java = new ArrayList<>();

         for (ShapelessRecipeData recipe : this.crafting.stonecutter()) {
            Item input = javaItem(recipe.getIngredients().getFirst().getDescriptor());
            ItemStack result = ItemMappings.toJava(recipe.getResults().getFirst());
            if (input != null && !result.isEmpty()) {
               Ingredient ingredient = Ingredient.of(input);
               entries.add(new Workstations.StonecutterEntry(ingredient, recipe));
               java.add(
                  new SingleInputEntry(
                     ingredient, new SelectableRecipe(new ItemStackSlotDisplay(ItemStackTemplate.fromNonEmptyStack(result)), Optional.empty())
                  )
               );
            }
         }

         this.stonecutter = List.copyOf(entries);
         Map<ResourceKey<RecipePropertySet>, RecipePropertySet> sets = new HashMap<>();
         sets.put(RecipePropertySet.SMITHING_TEMPLATE, this.smithingSet(Workstations.Slot.TEMPLATE));
         sets.put(RecipePropertySet.SMITHING_BASE, this.smithingSet(Workstations.Slot.BASE));
         sets.put(RecipePropertySet.SMITHING_ADDITION, this.smithingSet(Workstations.Slot.ADDITION));
         sets.put(RecipePropertySet.FURNACE_INPUT, this.stationSet("furnace"));
         sets.put(RecipePropertySet.BLAST_FURNACE_INPUT, this.stationSet("blast_furnace"));
         sets.put(RecipePropertySet.SMOKER_INPUT, this.stationSet("smoker"));
         sets.put(RecipePropertySet.CAMPFIRE_INPUT, this.stationSet("campfire"));
         sets.put(RecipePropertySet.BREWING_INPUTS, brewingSet(this.crafting.brewingInputs()));
         sets.put(RecipePropertySet.BREWING_REAGENTS, brewingSet(this.crafting.brewingReagents()));
         connection.handleUpdateRecipes(new ClientboundUpdateRecipesPacket(sets, new SingleInputSet(java)));
         BedrockNative.LOGGER
            .info(
               "Workstation recipes: {} stonecutter, {} smithing upgrades, {} smithing trims",
               new Object[]{java.size(), this.crafting.smithingTransforms().size(), this.crafting.smithingTrims().size()}
            );
      }
   }

   private RecipePropertySet smithingSet(Workstations.Slot slot) {
      Set<Item> items = new LinkedHashSet<>();

      for (SmithingTransformRecipeData recipe : this.crafting.smithingTransforms()) {
         addItems(items, switch (slot) {
            case TEMPLATE -> recipe.getTemplate().getDescriptor();
            case BASE -> recipe.getBase().getDescriptor();
            case ADDITION -> recipe.getAddition().getDescriptor();
         });
      }

      for (SmithingTrimRecipeData recipe : this.crafting.smithingTrims()) {
         switch (slot) {
            case TEMPLATE:
               BuiltInRegistries.ITEM
                  .stream()
                  .filter(item -> BuiltInRegistries.ITEM.getKey(item).getPath().endsWith("_armor_trim_smithing_template"))
                  .forEach(items::add);
               break;
            case BASE:
               BuiltInRegistries.ITEM.stream().filter(item -> new ItemStack(item).is(ItemTags.TRIMMABLE_ARMOR)).forEach(items::add);
               break;
            case ADDITION:
               BuiltInRegistries.ITEM.stream().filter(item -> new ItemStack(item).is(ItemTags.TRIM_MATERIALS)).forEach(items::add);
         }
      }

      return RecipePropertySet.create(items.stream().map(Ingredient::of).toList());
   }

   private RecipePropertySet stationSet(String tag) {
      Set<Item> items = new LinkedHashSet<>();

      for (BookRecipe recipe : this.crafting.index().withTag(tag)) {
         for (BookRecipe.Unit unit : recipe.units()) {
            ItemDescriptor descriptor = unit.ingredient().getDescriptor();
            if (descriptor instanceof ItemTagDescriptor named) {
               String javaTag = Crafting.javaTagName(named.getItemTag());
               Identifier id = javaTag == null ? null : Identifier.tryParse(javaTag);
               if (id != null) {
                  TagKey<Item> key = TagKey.create(Registries.ITEM, id);
                  BuiltInRegistries.ITEM.stream().filter(item -> new ItemStack(item).is(key)).forEach(items::add);
               }
            } else {
               addItems(items, descriptor);
            }
         }
      }

      return RecipePropertySet.create(items.stream().map(Ingredient::of).toList());
   }

   private static RecipePropertySet brewingSet(Set<Integer> runtimeIds) {
      Set<Item> items = new LinkedHashSet<>();

      for (int runtimeId : runtimeIds) {
         String identifier = ItemMappings.identifier(runtimeId);
         if (identifier != null) {
            ItemMappings.Resolution resolution = ItemMappings.resolve(identifier, 0, null);
            if (resolution.kind() != ItemMappings.Kind.MISSING && resolution.item() != Items.AIR) {
               items.add(resolution.item());
            }
         }
      }

      return RecipePropertySet.create(items.stream().map(Ingredient::of).toList());
   }

   private static void addItems(Set<Item> items, ItemDescriptor descriptor) {
      Item item = javaItem(descriptor);
      if (item != null) {
         items.add(item);
      }
   }

   @Nullable
   private static Item javaItem(ItemDescriptor descriptor) {
      if (descriptor instanceof DefaultDescriptor exact && exact.getItemId() != null) {
         ItemMappings.Resolution resolution = ItemMappings.resolve(
            exact.getItemId().getIdentifier(), Math.max(0, exact.getAuxValue() == 32767 ? 0 : exact.getAuxValue()), null
         );
         return resolution.kind() != ItemMappings.Kind.MISSING && resolution.item() != Items.AIR ? resolution.item() : null;
      } else {
         return null;
      }
   }

   private static void show(AbstractContainerMenu menu, int slot, @Nullable ItemData result) {
      ItemStack stack = result != null && !result.isNull() ? ItemMappings.toJava(result) : ItemStack.EMPTY;
      if (!ItemStack.matches(menu.getSlot(slot).getItem(), stack)) {
         menu.setItem(slot, menu.getStateId(), stack);
      }
   }

   private record Cartography(int netId, ItemData result) {
   }

   public record Craft(
      List<ItemStackRequestAction> header,
      List<Workstations.Use> uses,
      ItemData result,
      int times,
      List<Workstations.Use> after,
      List<String> filters,
      @Nullable TextProcessingEventOrigin origin,
      boolean consumeFirst
   ) {
      Craft(List<ItemStackRequestAction> header, List<Workstations.Use> uses, ItemData result, int times) {
         this(header, uses, result, times, List.of(), List.of(), null, false);
      }

      Craft(
         List<ItemStackRequestAction> header,
         List<Workstations.Use> uses,
         ItemData result,
         int times,
         List<Workstations.Use> after,
         List<String> filters,
         @Nullable TextProcessingEventOrigin origin
      ) {
         this(header, uses, result, times, after, filters, origin, false);
      }
   }

   private record JavaFacts(Function<String, ItemDefinition> definitions) implements WorkstationRules.Facts {
      @Override
      public int maxDamage(ItemData item) {
         ItemStack stack = ItemMappings.toJava(item);
         return stack.isDamageableItem() ? stack.getMaxDamage() : 0;
      }

      @Override
      public boolean repairs(ItemData item, ItemData material) {
         return ItemMappings.toJava(item).isValidRepairItem(ItemMappings.toJava(material));
      }

      @Override
      public int maxLevel(int enchantment) {
         return BedrockItemTags.enchantment(enchantment).map(holder -> ((Enchantment)holder.value()).getMaxLevel()).orElse(1);
      }

      @Override
      public int anvilCost(int enchantment) {
         return BedrockItemTags.enchantment(enchantment).map(holder -> ((Enchantment)holder.value()).getAnvilCost()).orElse(1);
      }

      @Override
      public boolean canEnchant(int enchantment, ItemData item) {
         return BedrockItemTags.enchantment(enchantment).map(holder -> ((Enchantment)holder.value()).canEnchant(ItemMappings.toJava(item))).orElse(false);
      }

      @Override
      public boolean compatible(int first, int second) {
         Optional<Reference<Enchantment>> a = BedrockItemTags.enchantment(first);
         Optional<Reference<Enchantment>> b = BedrockItemTags.enchantment(second);
         return a.isEmpty() || b.isEmpty() || Enchantment.areCompatible((Holder)a.get(), (Holder)b.get());
      }

      @Override
      public boolean isCurse(int enchantment) {
         return BedrockItemTags.enchantment(enchantment).map(holder -> holder.is(EnchantmentTags.CURSE)).orElse(false);
      }

      @Nullable
      @Override
      public ItemDefinition definition(String identifier) {
         return this.definitions.apply(identifier);
      }
   }

   private static enum Slot {
      TEMPLATE,
      BASE,
      ADDITION;
   }

   private record Smithing(int netId, ItemData result) {
   }

   private record StonecutterEntry(Ingredient input, ShapelessRecipeData recipe) {
   }

   private record Trade(int containerId, List<TradeOffer> shown, int tier, long trader) {
   }

   public record TradeView(int containerId, List<TradeOffer> offers, int tier, List<Integer> tierExp, long trader, String name, boolean levels) {
   }

   public record Use(BedrockSlot slot, int count) {
   }
}
