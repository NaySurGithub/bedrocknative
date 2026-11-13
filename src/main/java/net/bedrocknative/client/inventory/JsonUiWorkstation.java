package net.bedrocknative.client.inventory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Map.Entry;
import net.bedrocknative.client.ui.LegacyText;
import net.bedrocknative.text.BedrockText;
import net.bedrocknative.ui.UiControl;
import net.bedrocknative.ui.UiDrawCommand;
import net.bedrocknative.ui.UiValue;
import net.bedrocknative.ui.WorkstationData;
import net.bedrocknative.world.item.ItemMappings;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.EnchantmentNames;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.banner.BannerFlagModel;
import net.minecraft.client.model.object.book.BookModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.state.ArmorStandRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundRenameItemPacket;
import net.minecraft.network.protocol.game.ServerboundSetBeaconPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.BeaconMenu;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.ContainerListener;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.LoomMenu;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SmithingTemplateItem;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.item.crafting.SelectableRecipe.SingleInputEntry;
import net.minecraft.world.item.crafting.SelectableRecipe.SingleInputSet;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

final class JsonUiWorkstation implements WorkstationData.Actions, ContainerListener {
   private static final Identifier BOOK_TEXTURE = Identifier.withDefaultNamespace("textures/entity/enchantment/enchanting_table_book.png");
   private static final float BOOK_SCALE = 40.0F;
   private static final int RENAME_SLOT = 0;
   private static final int TOO_EXPENSIVE = 40;
   private static final int MAX_PATTERNS = 6;
   private static final int OPTIONS = 3;
   private static final int ENCHANTING_INPUTS = 13;
   private static final int RUNES_WIDTH = 86;
   private static final String BANNER_PATTERN = "banner_pattern_renderer";
   private static final String BOOK = "enchanting_book_renderer";
   private static final String EQUIPMENT = "equipment_preview_renderer";
   private static final Vector3fc ARMOR_STAND_TRANSLATION = new Vector3f(0.0F, 1.0F, 0.0F);
   private static final Quaternionfc ARMOR_STAND_ANGLE = new Quaternionf().rotationXYZ(0.43633232F, 0.0F, (float) Math.PI);
   private static final float ARMOR_STAND_SCALE = 25.0F;
   private static final Map<String, Holder<MobEffect>> POWERS = Map.of(
      "speed",
      MobEffects.SPEED,
      "haste",
      MobEffects.HASTE,
      "resist",
      MobEffects.RESISTANCE,
      "jump",
      MobEffects.JUMP_BOOST,
      "strength",
      MobEffects.STRENGTH,
      "regen",
      MobEffects.REGENERATION
   );
   private final Minecraft minecraft;
   private final AbstractContainerMenu menu;
   private final WorkstationData data;
   private final List<ItemStack> options = new ArrayList<>();
   private String anvilName = "";
   private final Map<String, String> texts = new HashMap<>();
   private int anvilCost = -1;
   private String anvilCostLine = "";
   private int[] enchantingSeen = new int[13];
   private int[] enchantingNow = new int[13];
   private boolean enchantingRead;
   private List<WorkstationData.Option> enchantingOptions = List.of();
   private @Nullable Object cutterRecipes;
   private List<WorkstationData.Choice> cutterChoices = List.of();
   private @Nullable List<Holder<BannerPattern>> loomPatterns;
   private boolean loomFull;
   private @Nullable DyeColor loomDye;
   private List<WorkstationData.Choice> loomChoices = List.of();
   private boolean listening;
   private @Nullable BookModel book;
   private final RandomSource random = RandomSource.create();
   private ItemStack lastEnchanted = ItemStack.EMPTY;
   private float open;
   private float oOpen;
   private float flip;
   private float oFlip;
   private float flipTarget;
   private float flipSpeed;
   private @Nullable BannerFlagModel flag;
   private @Nullable ArmorStandRenderState armorStand;
   private @Nullable Holder<MobEffect> primary;
   private @Nullable Holder<MobEffect> secondary;

   JsonUiWorkstation(Minecraft minecraft, AbstractContainerMenu menu, WorkstationData data) {
      this.minecraft = minecraft;
      this.menu = menu;
      this.data = data;
      data.actions(this);
   }

   void opened() {
      switch (this.menu) {
         case EnchantmentMenu ignored:
            this.book = new BookModel(this.minecraft.getEntityModels().bakeLayer(ModelLayers.BOOK));
            break;
         case LoomMenu ignored:
            this.flag = new BannerFlagModel(this.minecraft.getEntityModels().bakeLayer(ModelLayers.STANDING_BANNER_FLAG));
            break;
         case SmithingMenu smithing:
            this.armorStand = new ArmorStandRenderState();
            this.armorStand.entityType = EntityTypes.ARMOR_STAND;
            this.armorStand.showBasePlate = false;
            this.armorStand.showArms = true;
            this.armorStand.xRot = 25.0F;
            this.armorStand.bodyRot = 210.0F;
            this.preview(smithing.getSlot(3).getItem());
            break;
         case BeaconMenu beacon:
            this.primary = beacon.getPrimaryEffect();
            this.secondary = beacon.getSecondaryEffect();
            break;
         default:
      }

      if (!this.listening && (this.menu instanceof AnvilMenu || this.menu instanceof SmithingMenu || this.menu instanceof BeaconMenu)) {
         this.listening = true;
         this.menu.addSlotListener(this);
      }
   }

   void removed() {
      if (this.listening) {
         this.listening = false;
         this.menu.removeSlotListener(this);
      }
   }

   void tick() {
      if (this.menu instanceof EnchantmentMenu enchanting) {
         ItemStack current = enchanting.getSlot(0).getItem();
         if (!ItemStack.matches(current, this.lastEnchanted)) {
            this.lastEnchanted = current.copy();

            do {
               this.flipTarget = this.flipTarget + (this.random.nextInt(4) - this.random.nextInt(4));
            } while (this.flip <= this.flipTarget + 1.0F && this.flip >= this.flipTarget - 1.0F);
         }

         this.oFlip = this.flip;
         this.oOpen = this.open;
         boolean offered = false;

         for (int i = 0; i < 3; i++) {
            offered |= enchanting.costs[i] != 0;
         }

         this.open = Mth.clamp(this.open + (offered ? 0.2F : -0.2F), 0.0F, 1.0F);
         float step = Mth.clamp((this.flipTarget - this.flip) * 0.4F, -0.2F, 0.2F);
         this.flipSpeed = this.flipSpeed + (step - this.flipSpeed) * 0.9F;
         this.flip = this.flip + this.flipSpeed;
      }
   }

   void read() {
      LocalPlayer player = this.minecraft.player;
      if (player != null) {
         switch (this.menu) {
            case AnvilMenu anvil:
               this.readAnvil(anvil, player);
               break;
            case GrindstoneMenu grindstone:
               this.data.grindstone((grindstone.getSlot(0).hasItem() || grindstone.getSlot(1).hasItem()) && !grindstone.getSlot(2).hasItem());
               break;
            case EnchantmentMenu enchanting:
               this.readEnchanting(enchanting, player);
               break;
            case StonecutterMenu cutter:
               this.readStonecutter(cutter);
               break;
            case LoomMenu loom:
               this.readLoom(loom);
               break;
            case SmithingMenu smithing:
               this.readSmithing(smithing);
               break;
            case CartographyTableMenu cartography:
               this.readCartography(cartography);
               break;
            case BeaconMenu beacon:
               this.data.beacon(beacon.getLevels(), name(this.primary), name(this.secondary), beacon.hasPayment());
               break;
            default:
         }
      }
   }

   private String text(String key) {
      return this.texts.computeIfAbsent(key, k -> BedrockText.translate(k, List.of()));
   }

   private void readAnvil(AnvilMenu anvil, LocalPlayer player) {
      int cost = anvil.getCost();
      String line = "";
      boolean canPay = true;
      if (cost > 0) {
         if (cost >= 40 && !player.hasInfiniteMaterials()) {
            line = this.text("container.repair.expensive");
            canPay = false;
         } else if (anvil.getSlot(2).hasItem()) {
            if (cost != this.anvilCost) {
               this.anvilCost = cost;
               this.anvilCostLine = BedrockText.translate("container.repair.cost", List.of(Integer.toString(cost)));
            }

            line = this.anvilCostLine;
            canPay = anvil.getSlot(2).mayPickup(player);
         }
      }

      boolean inputs = anvil.getSlot(0).hasItem() || anvil.getSlot(1).hasItem();
      this.data.anvil(this.anvilName, anvil.getSlot(0).hasItem(), line, canPay, inputs && !anvil.getSlot(2).hasItem());
   }

   private void readEnchanting(EnchantmentMenu enchanting, LocalPlayer player) {
      boolean infinite = player.hasInfiniteMaterials();
      int lapis = enchanting.getGoldCount();
      int[] now = this.enchantingNow;
      now[0] = infinite ? 1 : 0;
      now[1] = lapis;
      now[2] = player.experienceLevel;
      now[3] = enchanting.getEnchantmentSeed();

      for (int i = 0; i < 3; i++) {
         now[4 + i] = enchanting.costs[i];
         now[7 + i] = enchanting.enchantClue[i];
         now[10 + i] = enchanting.levelClue[i];
      }

      if (!this.enchantingRead || !Arrays.equals(now, this.enchantingSeen)) {
         this.enchantingRead = true;
         this.enchantingNow = this.enchantingSeen;
         this.enchantingSeen = now;
         EnchantmentNames.getInstance().initSeed(enchanting.getEnchantmentSeed());
         List<WorkstationData.Option> shown = new ArrayList<>(3);

         for (int i = 0; i < 3; i++) {
            int cost = enchanting.costs[i];
            if (cost == 0) {
               shown.add(WorkstationData.Option.NONE);
            } else {
               boolean affordable = infinite || lapis >= i + 1 && player.experienceLevel >= cost;
               String runes = EnchantmentNames.getInstance()
                  .getRandomName(this.minecraft.font, 86 - this.minecraft.font.width(Integer.toString(cost)))
                  .getString();
               shown.add(new WorkstationData.Option(cost, affordable, this.clue(enchanting, i, player, infinite, lapis), runes));
            }
         }

         this.enchantingOptions = List.copyOf(shown);
      }

      this.data.enchanting(this.enchantingOptions, enchanting.getSlot(0).hasItem());
   }

   private String clue(EnchantmentMenu enchanting, int i, LocalPlayer player, boolean infinite, int lapis) {
      if (this.minecraft.level == null) {
         return "";
      }

      Optional<Reference<Enchantment>> enchantment = this.minecraft.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(enchanting.enchantClue[i]);
      int level = enchanting.levelClue[i];
      if (!enchantment.isEmpty() && level >= 0) {
         List<Component> lines = new ArrayList<>();
         lines.add(Component.translatable("container.enchant.clue", Enchantment.getFullname(enchantment.get(), level)).withStyle(ChatFormatting.WHITE));
         if (!infinite) {
            lines.add(Component.empty());
            int cost = enchanting.costs[i];
            int uses = i + 1;
            if (player.experienceLevel < cost) {
               lines.add(Component.translatable("container.enchant.level.requirement", cost).withStyle(ChatFormatting.RED));
            } else {
               lines.add(
                  (uses == 1 ? Component.translatable("container.enchant.lapis.one") : Component.translatable("container.enchant.lapis.many", uses))
                     .withStyle(lapis >= uses ? ChatFormatting.GRAY : ChatFormatting.RED)
               );
               lines.add(
                  (uses == 1 ? Component.translatable("container.enchant.level.one") : Component.translatable("container.enchant.level.many", uses))
                     .withStyle(ChatFormatting.GRAY)
               );
            }
         }

         return String.join("\n", lines.stream().map(LegacyText::of).toList());
      } else {
         return "";
      }
   }

   private void readStonecutter(StonecutterMenu cutter) {
      SingleInputSet<StonecutterRecipe> recipes = cutter.getVisibleRecipes();
      if (this.minecraft.level != null && recipes != this.cutterRecipes) {
         this.cutterRecipes = recipes;
         this.options.clear();
         List<WorkstationData.Choice> choices = new ArrayList<>();
         ContextMap context = SlotDisplayContext.fromLevel(this.minecraft.level);

         for (SingleInputEntry<StonecutterRecipe> entry : recipes.entries()) {
            ItemStack icon = entry.recipe().optionDisplay().resolveForFirstStack(context);
            this.options.add(icon);
            choices.add(new WorkstationData.Choice(icon.isEmpty() ? "" : icon.getHoverName().getString(), icon.getCount()));
         }

         this.cutterChoices = List.copyOf(choices);
      }

      this.data.choices(this.cutterChoices, cutter.getSelectedRecipeIndex());
   }

   private void readLoom(LoomMenu loom) {
      ItemStack banner = loom.getBannerSlot().getItem();
      boolean full = banner.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY).layers().size() >= 6;
      DyeColor dye = loom.getDyeSlot().getItem().getOrDefault(DataComponents.DYE, DyeColor.WHITE);
      List<Holder<BannerPattern>> patterns = loom.getSelectablePatterns();
      if (patterns != this.loomPatterns || full != this.loomFull || dye != this.loomDye) {
         this.loomPatterns = patterns;
         this.loomFull = full;
         this.loomDye = dye;
         List<WorkstationData.Choice> choices = new ArrayList<>();
         if (!full) {
            for (Holder<BannerPattern> pattern : patterns) {
               choices.add(new WorkstationData.Choice(Component.translatable(pattern.value().translationKey() + "." + dye.getName()).getString(), 1));
            }
         }

         this.loomChoices = List.copyOf(choices);
      }

      this.data.choices(this.loomChoices, full ? -1 : loom.getSelectedBannerPatternIndex());
   }

   private void readSmithing(SmithingMenu smithing) {
      ItemStack template = smithing.getSlot(0).getItem();
      WorkstationData.Template kind = !template.isEmpty() && template.getItem() instanceof SmithingTemplateItem
         ? (template.is(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE) ? WorkstationData.Template.UPGRADE : WorkstationData.Template.TRIM)
         : WorkstationData.Template.NONE;
      boolean error = smithing.hasRecipeError();
      this.data
         .smithing(
            kind,
            template.isEmpty(),
            smithing.getSlot(1).getItem().isEmpty(),
            smithing.getSlot(2).getItem().isEmpty(),
            error,
            error ? this.text("container.smithing_table.arrow_tooltip_cannot_craft") : ""
         );
   }

   private void readCartography(CartographyTableMenu cartography) {
      ItemStack map = cartography.getSlot(0).getItem();
      ItemStack additional = cartography.getSlot(1).getItem();
      String mode = map.isEmpty()
         ? "none"
         : (additional.is(Items.MAP) ? "clone" : (additional.is(Items.PAPER) ? "extend" : (additional.is(Items.GLASS_PANE) ? "locked" : "basic_map")));

      String key = switch (mode) {
         case "clone" -> "map.clone";
         case "extend" -> "map.extendAndClear";
         case "locked" -> "map.lock";
         default -> null;
      };
      boolean result = cartography.getSlot(2).hasItem();
      this.data.cartography(mode, key != null && result ? this.text(key) : "");
   }

   private static @Nullable String name(@Nullable Holder<MobEffect> effect) {
      if (effect == null) {
         return null;
      }

      for (Entry<String, Holder<MobEffect>> power : POWERS.entrySet()) {
         if (power.getValue().equals(effect)) {
            return power.getKey();
         }
      }

      return null;
   }

   @Override
   public void rename(String name) {
      this.anvilName = name;
      if (this.menu instanceof AnvilMenu anvil && this.minecraft.player != null) {
         ItemStack item = anvil.getSlot(0).getItem();
         if (!item.isEmpty()) {
            String newName = !item.has(DataComponents.CUSTOM_NAME) && name.equals(item.getHoverName().getString()) ? "" : name;
            if (anvil.setItemName(newName)) {
               this.minecraft.player.connection.send(new ServerboundRenameItemPacket(newName));
            }
         }
      }
   }

   @Override
   public void enchant(int option) {
      this.click(option);
   }

   @Override
   public void select(int option) {
      this.click(option);
   }

   private void click(int button) {
      LocalPlayer player = this.minecraft.player;
      if (player != null && this.minecraft.gameMode != null && this.menu.clickMenuButton(player, button)) {
         this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, button);
      }
   }

   @Override
   public void beacon(String button) {
      LocalPlayer player = this.minecraft.player;
      if (player != null) {
         switch (button) {
            case "confirm":
               player.connection.send(new ServerboundSetBeaconPacket(Optional.ofNullable(this.primary), Optional.ofNullable(this.secondary)));
               player.closeContainer();
               break;
            case "cancel":
               player.closeContainer();
               break;
            case "extra":
               this.secondary = this.primary;
               break;
            case "regen":
               this.secondary = MobEffects.REGENERATION;
               break;
            default:
               Holder<MobEffect> effect = POWERS.get(button);
               if (effect != null) {
                  this.primary = effect;
                  if (!Objects.equals(this.secondary, effect)) {
                     this.secondary = null;
                  }
               }
         }
      }
   }

   @Override
   public void slotChanged(AbstractContainerMenu container, int slot, ItemStack stack) {
      if (this.menu instanceof AnvilMenu && slot == 0) {
         this.rename(stack.isEmpty() ? "" : stack.getHoverName().getString());
      } else if (this.menu instanceof SmithingMenu && slot == 3) {
         this.preview(stack);
      }
   }

   @Override
   public void dataChanged(AbstractContainerMenu container, int id, int value) {
      if (this.menu instanceof BeaconMenu beacon) {
         this.primary = beacon.getPrimaryEffect();
         this.secondary = beacon.getSecondaryEffect();
      }
   }

   private void preview(ItemStack stack) {
      ArmorStandRenderState state = this.armorStand;
      if (state != null) {
         state.leftHandItemStack = ItemStack.EMPTY;
         state.leftHandItemState.clear();
         state.headEquipment = ItemStack.EMPTY;
         state.chestEquipment = ItemStack.EMPTY;
         state.legsEquipment = ItemStack.EMPTY;
         state.feetEquipment = ItemStack.EMPTY;
         if (!stack.isEmpty()) {
            Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
            EquipmentSlot slot = equippable == null ? null : equippable.slot();
            if (slot == EquipmentSlot.HEAD) {
               state.headEquipment = stack.copy();
            } else if (slot == EquipmentSlot.CHEST) {
               state.chestEquipment = stack.copy();
            } else if (slot == EquipmentSlot.LEGS) {
               state.legsEquipment = stack.copy();
            } else if (slot == EquipmentSlot.FEET) {
               state.feetEquipment = stack.copy();
            } else {
               state.leftHandItemStack = stack.copy();
               this.minecraft
                  .getItemModelResolver()
                  .updateForTopItem(state.leftHandItemState, stack, ItemDisplayContext.THIRD_PERSON_LEFT_HAND, this.minecraft.level, null, 0);
            }
         }
      }
   }

   ItemStack stack(UiControl renderer) {
      if ("stones".equals(renderer.itemCollection())) {
         int index = renderer.itemIndex();
         return index >= 0 && index < this.options.size() ? this.options.get(index) : ItemStack.EMPTY;
      } else {
         UiValue idAux = renderer.bag("#item_id_aux");
         return idAux != null && idAux.isNumber() ? ItemMappings.fromIdAux((int)idAux.asNumber()) : ItemStack.EMPTY;
      }
   }

   boolean draw(GuiGraphicsExtractor graphics, UiDrawCommand.Custom renderer) {
      int x0 = Math.round(renderer.x());
      int y0 = Math.round(renderer.y());
      int x1 = Math.round(renderer.x() + renderer.w());
      int y1 = Math.round(renderer.y() + renderer.h());
      switch (renderer.renderer()) {
         case "enchanting_book_renderer":
            if (this.book == null) {
               return false;
            }

            float partial = this.minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
            graphics.book(this.book, BOOK_TEXTURE, 40.0F, Mth.lerp(partial, this.oOpen, this.open), Mth.lerp(partial, this.oFlip, this.flip), x0, y0, x1, y1);
            return true;
         case "equipment_preview_renderer":
            if (this.armorStand == null) {
               return false;
            }

            graphics.entity(this.armorStand, 25.0F, ARMOR_STAND_TRANSLATION, ARMOR_STAND_ANGLE, null, x0, y0, x1, y1);
            return true;
         case "banner_pattern_renderer":
            return this.menu instanceof LoomMenu loom && this.banner(graphics, loom, renderer.control(), renderer);
         default:
            return false;
      }
   }

   private boolean banner(GuiGraphicsExtractor graphics, LoomMenu loom, UiControl control, UiDrawCommand.Custom renderer) {
      if ("patterns".equals(control.itemCollection())) {
         List<Holder<BannerPattern>> patterns = loom.getSelectablePatterns();
         int index = control.itemIndex();
         if (index >= 0 && index < patterns.size()) {
            TextureAtlasSprite sprite = graphics.getSprite(Sheets.getBannerSprite(patterns.get(index)));
            float u0 = sprite.getU0();
            float u1 = u0 + (sprite.getU1() - sprite.getU0()) * 21.0F / 64.0F;
            float vSpan = sprite.getV1() - sprite.getV0();
            float v0 = sprite.getV0() + vSpan / 64.0F;
            float v1 = v0 + vSpan * 40.0F / 64.0F;
            int x0 = Math.round(renderer.x());
            int y0 = Math.round(renderer.y());
            int x1 = Math.round(renderer.x() + renderer.w());
            int y1 = Math.round(renderer.y() + renderer.h());
            graphics.fill(x0, y0, x1, y1, DyeColor.GRAY.getTextureDiffuseColor());
            graphics.blit(sprite.atlasLocation(), x0, y0, x1, y1, u0, u1, v0, v1);
            return true;
         } else {
            return false;
         }
      } else {
         ItemStack result = loom.getResultSlot().getItem();
         if (this.flag != null && !result.isEmpty() && result.getItem() instanceof BannerItem banner) {
            graphics.bannerPattern(
               this.flag,
               banner.getColor(),
               result.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY),
               Math.round(renderer.x()),
               Math.round(renderer.y()),
               Math.round(renderer.x() + renderer.w()),
               Math.round(renderer.y() + renderer.h())
            );
            return true;
         } else {
            return true;
         }
      }
   }
}
