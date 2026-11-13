package net.bedrocknative.client.inventory;

import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.lang.runtime.SwitchBootstraps;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.movement.BedrockEffects;
import net.bedrocknative.world.block.BeaconRules;
import net.bedrocknative.world.item.ItemMappings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundPlaceRecipePacket;
import net.minecraft.network.protocol.game.ServerboundRenameItemPacket;
import net.minecraft.network.protocol.game.ServerboundSelectTradePacket;
import net.minecraft.network.protocol.game.ServerboundSetBeaconPacket;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.BeaconMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtMapBuilder;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerSlotType;
import org.cloudburstmc.protocol.bedrock.data.inventory.CreativeItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.EnchantOptionData;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.ItemStackRequest;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.ItemStackRequestSlotData;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.TextProcessingEventOrigin;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.BeaconPaymentAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.ConsumeAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.CraftCreativeAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.CraftGrindstoneAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.CraftRecipeAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.CraftResultsDeprecatedAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.DestroyAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.DropAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.ItemStackRequestAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.PlaceAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.SwapAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.TakeAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.response.ItemStackResponse;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.response.ItemStackResponseContainer;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.response.ItemStackResponseSlot;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.response.ItemStackResponseStatus;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.ItemStackRequestPacket;
import org.cloudburstmc.protocol.bedrock.packet.ItemStackResponsePacket;

public final class StackRequests {
   private static final int MAX_PENDING = 64;
   private static final int RESULT_SLOT = 0;
   private final Minecraft minecraft;
   private final BedrockInventory inventory;
   private final CreativeItems creative;
   private final Crafting crafting;
   private final Workstations workstations;
   private final Consumer<BedrockPacket> send;
   private final Int2ObjectMap<Map<BedrockSlot, ItemData>> pending = new Int2ObjectLinkedOpenHashMap();
   private final Int2ObjectMap<String> pendingActions = new Int2ObjectLinkedOpenHashMap();
   private static int nextRequestId = -3;
   private static final AtomicInteger ACCEPTED_TOTAL = new AtomicInteger();
   private static final AtomicInteger REJECTED_TOTAL = new AtomicInteger();
   private int accepted;
   private int rejected;
   private volatile ItemStackResponseStatus lastStatus;
   private List<String> shownGrid = List.of();
   private ItemStack shownResult = ItemStack.EMPTY;
   private static final List<BedrockSlot> INVENTORY_GRID = buildGridSlots(2);
   private static final List<BedrockSlot> TABLE_GRID = buildGridSlots(3);

   public StackRequests(
      Minecraft minecraft, BedrockInventory inventory, CreativeItems creative, Crafting crafting, Workstations workstations, Consumer<BedrockPacket> send
   ) {
      this.minecraft = minecraft;
      this.inventory = inventory;
      this.creative = creative;
      this.crafting = crafting;
      this.workstations = workstations;
      this.send = send;
      inventory.onServerUpdate((slot, item) -> this.pending.values().forEach(before -> before.computeIfPresent(slot, (key, old) -> item)));
   }

   public BedrockInventory inventory() {
      return this.inventory;
   }

   public CreativeItems creative() {
      return this.creative;
   }

   public Crafting crafting() {
      return this.crafting;
   }

   public Workstations workstations() {
      return this.workstations;
   }

   public void reset() {
      this.pending.clear();
      this.pendingActions.clear();
      if (this.workstations != null) {
         this.workstations.reset();
      }
   }

   static void nextRequestId(int id) {
      nextRequestId = id;
   }

   public static int acceptedTotal() {
      return ACCEPTED_TOTAL.get();
   }

   public static int rejectedTotal() {
      return REJECTED_TOTAL.get();
   }

   public ItemStackResponseStatus lastStatus() {
      return this.lastStatus;
   }

   public int accepted() {
      return this.accepted;
   }

   public int rejected() {
      return this.rejected;
   }

   public int pendingCount() {
      return this.pending.size();
   }

   public void tick() {
      LocalPlayer player = this.minecraft.player;
      Screen screen = this.minecraft.gui.screen();
      if (screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen || JsonUiContainerScreen.showsInventory(screen)) {
         this.inventory.openInventory();
      } else if (this.inventory.isInventoryOpen() && player != null && player.containerMenu == player.inventoryMenu) {
         this.inventory.closeInventory();
      }

      if (player != null) {
         this.showCraftingResult(player.containerMenu);
      }

      this.workstations.tick(player == null ? null : player.containerMenu);
   }

   private void showCraftingResult(AbstractContainerMenu menu) {
      int size = gridSize(menu);
      if (size != 0) {
         List<ItemData> grid = gridSlots(size).stream().map(this.inventory::get).toList();
         List<String> key = grid.stream().map(item -> item.isNull() ? "" : item.getDefinition().getRuntimeId() + ":" + item.getDamage()).toList();
         if (!key.equals(this.shownGrid)) {
            this.shownGrid = key;
            Crafting.Match match = this.crafting.match(grid, size);
            this.shownResult = match == null ? ItemStack.EMPTY : ItemMappings.toJava(match.result());
         }

         if (!ItemStack.matches(menu.getSlot(0).getItem(), this.shownResult)) {
            menu.setItem(0, menu.getStateId(), this.shownResult.copy());
         }
      }
   }

   static int gridSize(AbstractContainerMenu menu) {
      return menu instanceof InventoryMenu ? 2 : (menu instanceof CraftingMenu ? 3 : 0);
   }

   static List<BedrockSlot> gridSlots(int size) {
      return size == 2 ? INVENTORY_GRID : (size == 3 ? TABLE_GRID : buildGridSlots(size));
   }

   private static List<BedrockSlot> buildGridSlots(int size) {
      int start = size == 2 ? 28 : 32;
      List<BedrockSlot> slots = new ArrayList<>();

      for (int slot = 0; slot < size * size; slot++) {
         slots.add(BedrockSlot.ui(start + slot, ContainerSlotType.CRAFTING_INPUT));
      }

      return List.copyOf(slots);
   }

   public void onJavaClose(int containerId) {
      if (this.minecraft.gui.screen() instanceof CreativeModeInventoryScreen) {
         ItemData cursor = this.inventory.get(BedrockSlot.CURSOR);
         if (!cursor.isNull()) {
            StackRequests.Request request = new StackRequests.Request();
            request.destroy(cursor.getCount(), BedrockSlot.CURSOR);
            request.submit();
         }
      }

      ContainerLayout layout = this.inventory.openLayout();
      if (layout != null && containerId == this.inventory.openContainerId()) {
         this.returnInputs(layout.uiInputs(), item -> ItemMappings.toJava(item).getMaxStackSize());
         this.inventory.showPlayerSlots();
      }

      this.inventory.onJavaClose(containerId);
   }

   void returnInputs(List<BedrockSlot> inputs, ToIntFunction<ItemData> maxStackSize) {
      StackRequests.Request request = new StackRequests.Request();
      request.autoPlace = true;

      for (BedrockSlot input : inputs) {
         ItemData item = this.inventory.get(input);
         int left = item.isNull() ? 0 : item.getCount();
         int max = left == 0 ? 0 : Math.max(1, maxStackSize.applyAsInt(item));

         for (int pass = 0; pass < 2 && left > 0; pass++) {
            for (int slot = 0; slot < 36 && left > 0; slot++) {
               BedrockSlot target = BedrockSlot.player(slot);
               ItemData there = request.get(target);
               int room = pass == 0 ? (!there.isNull() && sameKind(there, item) ? max - there.getCount() : 0) : (there.isNull() ? max : 0);
               if (room > 0) {
                  int count = Math.min(room, left);
                  request.move(count, input, target);
                  left -= count;
               }
            }
         }
      }

      request.submit();
   }

   static boolean sameKind(ItemData a, ItemData b) {
      return a.getDefinition().getRuntimeId() == b.getDefinition().getRuntimeId() && a.getDamage() == b.getDamage() && Objects.equals(a.getTag(), b.getTag());
   }

   public boolean onJavaPacket(Packet<?> packet) {
      LocalPlayer player = this.minecraft.player;
      AbstractContainerMenu menu = player == null ? null : player.containerMenu;
      Objects.requireNonNull(packet);
      Packet var4 = packet;
      byte var5 = 0;

      while (true) {
         switch (SwitchBootstraps.typeSwitch<"typeSwitch",ServerboundRenameItemPacket,ServerboundContainerButtonClickPacket,ServerboundSetBeaconPacket,ServerboundContainerButtonClickPacket,ServerboundSelectTradePacket,ServerboundPlaceRecipePacket>(
            var4, var5
         )) {
            case 0:
               ServerboundRenameItemPacket rename = (ServerboundRenameItemPacket)var4;
               this.workstations.onRename(rename.getName());
               break;
            case 1:
               ServerboundContainerButtonClickPacket button = (ServerboundContainerButtonClickPacket)var4;
               if (!(menu instanceof EnchantmentMenu) || menu.containerId != button.containerId()) {
                  var5 = 2;
                  continue;
               }

               this.enchant(button.buttonId());
               break;
            case 2:
               ServerboundSetBeaconPacket beacon = (ServerboundSetBeaconPacket)var4;
               if (!(menu instanceof BeaconMenu beaconMenu)) {
                  var5 = 3;
                  continue;
               }

               this.beaconPayment(beacon, beaconMenu);
               break;
            case 3: {
               ServerboundContainerButtonClickPacket ignored = (ServerboundContainerButtonClickPacket)var4;
               break;
            }
            case 4: {
               ServerboundSelectTradePacket ignored = (ServerboundSelectTradePacket)var4;
               if (!(menu instanceof MerchantMenu)) {
                  var5 = 5;
                  continue;
               }

               this.autoFill(menu);
               this.workstations.observe(menu);
               break;
            }
            case 5:
               ServerboundPlaceRecipePacket place = (ServerboundPlaceRecipePacket)var4;
               if (BedrockRecipeBook.active() != null) {
                  BedrockRecipeBook book = BedrockRecipeBook.active();
                  return book != null && book.placeFromJava(place);
               }

               var5 = 6;
               continue;
            default:
               return false;
         }

         return true;
      }
   }

   private void beaconPayment(ServerboundSetBeaconPacket packet, BeaconMenu menu) {
      int primary = BedrockEffects.idOf((Holder<MobEffect>)packet.primary().orElse(null));
      int secondary = BedrockEffects.idOf((Holder<MobEffect>)packet.secondary().orElse(null));
      ItemData payment = this.inventory.get(ContainerLayout.BEACON_PAYMENT);
      if (!payment.isNull() && BeaconRules.valid(primary, secondary, menu.getLevels())) {
         StackRequests.Request request = new StackRequests.Request();
         request.beaconPayment(primary, secondary, ContainerLayout.BEACON_PAYMENT);
         request.submit();
         this.workstations.beaconChosen(menu, primary, secondary);
         this.inventory.show(ContainerLayout.BEACON_PAYMENT);
         BedrockNative.LOGGER.info("Beacon powers sent: primary {}, secondary {}", primary, secondary);
      } else {
         BedrockNative.LOGGER
            .info(
               "Beacon powers {} and {} not sent: {} at level {}",
               new Object[]{primary, secondary, payment.isNull() ? "no payment" : "not allowed", menu.getLevels()}
            );
         this.inventory.showMenu(menu);
      }
   }

   private void enchant(int index) {
      EnchantOptionData option = this.workstations.enchantOption(index);
      ItemData input = this.inventory.get(ContainerLayout.ENCHANTING_INPUT);
      if (option != null && !input.isNull()) {
         ItemData result = WorkstationRules.enchant(input, option, this.workstations.facts());
         StackRequests.Request request = new StackRequests.Request();
         ItemData lapis = this.inventory.get(ContainerLayout.ENCHANTING_MATERIAL);
         request.craft(
            Workstations.enchant(option, index, result, !lapis.isNull() && lapis.getCount() > index),
            List.of(new Workstations.Use(ContainerLayout.ENCHANTING_INPUT, 1))
         );
         request.submit();
         this.inventory.show(ContainerLayout.ENCHANTING_INPUT);
         this.inventory.show(ContainerLayout.ENCHANTING_MATERIAL);
      } else {
         BedrockNative.LOGGER.info("Enchanting option {} not sent: no option from the server or nothing to enchant", index);
      }
   }

   private void autoFill(AbstractContainerMenu menu) {
      List<StackRequests.Change> changes = new ArrayList<>();
      List<StackRequests.Change> gains = new ArrayList<>();

      for (int javaSlot = 0; javaSlot < menu.slots.size(); javaSlot++) {
         BedrockSlot slot = this.inventory.slotOf(menu, javaSlot);
         if (slot != null && !slot.equals(BedrockSlot.CREATED_OUTPUT)) {
            StackRequests.Change change = this.change(slot, menu.getSlot(javaSlot).getItem());
            if (change.changed()) {
               if (change.replaced()) {
                  changes.add(new StackRequests.Change(slot, change.before(), change.beforeStack(), ItemStack.EMPTY));
                  gains.add(new StackRequests.Change(slot, ItemData.AIR, ItemStack.EMPTY, change.after()));
               } else {
                  changes.add(change);
               }
            }
         }
      }

      changes.addAll(gains);
      if (!changes.isEmpty()) {
         StackRequests.Request request = new StackRequests.Request();
         request.autoPlace = true;
         if (!transfers(request, changes, false)) {
            this.resync(menu, "choosing a trade moved " + changes);
         } else {
            request.submit();
         }
      }
   }

   public void onClick(ServerboundContainerClickPacket click) {
      LocalPlayer player = this.minecraft.player;
      AbstractContainerMenu menu = player == null ? null : player.containerMenu;
      if (menu != null && menu.containerId == click.containerId()) {
         List<StackRequests.Change> changes = new ArrayList<>();
         int workstationResult = Workstations.resultSlot(menu);
         IntIterator cursor = click.changedSlots().keySet().iterator();

         while (cursor.hasNext()) {
            int javaSlot = (Integer)cursor.next();
            if (javaSlot != workstationResult || click.slotNum() == workstationResult) {
               BedrockSlot slot = this.inventory.slotOf(menu, javaSlot);
               if (slot == null) {
                  this.resync(menu, "no Bedrock slot behind Java slot " + javaSlot);
                  return;
               }

               StackRequests.Change change = this.change(slot, menu.getSlot(javaSlot).getItem());
               if (change.changed()) {
                  changes.add(change);
               }
            }
         }

         StackRequests.Change cursorx = this.change(BedrockSlot.CURSOR, menu.getCarried());
         if (cursorx.changed()) {
            changes.add(cursorx);
         }

         if (!changes.isEmpty()) {
            String refused = refusedByOffhand(changes);
            if (refused != null) {
               this.resync(menu, "Bedrock's offhand does not take " + refused);
            } else {
               if (menu instanceof InventoryMenu) {
                  this.inventory.openInventory();
               }

               StackRequests.Request request = new StackRequests.Request();
               request.shiftPlaced = click.containerInput() == ContainerInput.QUICK_MOVE;
               if (workstationResult < 0 || click.slotNum() != workstationResult) {
                  boolean crafted = click.slotNum() == 0 && gridSize(menu) > 0;
                  if (crafted
                     ? this.craft(request, menu, changes)
                     : !changes.stream().anyMatch(change -> change.slot().equals(BedrockSlot.CREATED_OUTPUT))
                        && transfers(request, changes, click.containerInput() == ContainerInput.THROW || click.slotNum() == -999)) {
                     request.submit();
                     this.workstations.observe(menu);
                  } else {
                     this.resync(menu, click.containerInput() + " on slot " + click.slotNum() + " changed " + changes);
                  }
               } else if (!this.workstationCraft(request, menu, changes)) {
                  this.resync(menu, click.containerInput() + " on the result changed " + changes);
               } else {
                  request.submit();
                  this.inventory.showMenu(menu);
                  this.workstations.observe(menu);
               }
            }
         }
      }
   }

   private boolean workstationCraft(StackRequests.Request request, AbstractContainerMenu menu, List<StackRequests.Change> changes) {
      List<StackRequests.Change> outputs = changes.stream()
         .filter(change -> change.delta() > 0 && (change.slot().container() != 124 || change.slot().equals(BedrockSlot.CURSOR)))
         .toList();
      int made = outputs.stream().mapToInt(StackRequests.Change::delta).sum();
      Workstations.Craft craft = made <= 0 ? null : this.workstations.craftFor(menu, made);
      if (craft != null && !outputs.stream().anyMatch(output -> !output.after().is(ItemMappings.toJava(craft.result()).getItem()))) {
         request.craft(craft, outputs.stream().map(output -> new Workstations.Use(output.slot(), output.delta())).toList());
         return true;
      } else {
         return false;
      }
   }

   private boolean craft(StackRequests.Request request, AbstractContainerMenu menu, List<StackRequests.Change> changes) {
      int size = gridSize(menu);
      List<BedrockSlot> grid = gridSlots(size);
      Crafting.Match match = this.crafting.match(grid.stream().map(this.inventory::get).toList(), size);
      if (match == null) {
         return false;
      } else {
         ItemStack result = ItemMappings.toJava(match.result());
         List<StackRequests.Change> outputs = changes.stream()
            .filter(
               change -> change.slot().type() != ContainerSlotType.CRAFTING_INPUT
                  && !change.slot().equals(BedrockSlot.CREATED_OUTPUT)
                  && change.delta() > 0
                  && ItemStack.isSameItemSameComponents(change.after(), result)
                  && (change.before().isNull() || change.holds(result))
            )
            .toList();
         int made = outputs.stream().mapToInt(StackRequests.Change::delta).sum();
         int perCraft = match.result().getCount();
         if (made != 0 && made % perCraft == 0) {
            request.craft(match, made / perCraft, grid);

            for (StackRequests.Change output : outputs) {
               request.move(output.delta(), BedrockSlot.CREATED_OUTPUT, output.slot());
            }

            return true;
         } else {
            return false;
         }
      }
   }

   private static boolean transfers(StackRequests.Request request, List<StackRequests.Change> changes, boolean thrown) {
      List<StackRequests.Change> replaced = changes.stream().filter(StackRequests.Change::replaced).toList();
      if (!replaced.isEmpty()) {
         if (changes.size() == 2 && replaced.size() == 2 && replaced.get(0).swapsWith(replaced.get(1))) {
            request.swap(replaced.get(0).slot(), replaced.get(1).slot());
            return true;
         } else {
            return false;
         }
      } else {
         int[] left = new int[changes.size()];

         for (int i = 0; i < changes.size(); i++) {
            left[i] = Math.max(0, -changes.get(i).delta());
         }

         for (StackRequests.Change destination : changes) {
            int needed = destination.delta();

            for (int i = 0; i < changes.size() && needed > 0; i++) {
               StackRequests.Change source = changes.get(i);
               if (left[i] > 0 && source.holds(destination.after())) {
                  int count = Math.min(needed, left[i]);
                  request.move(count, source.slot(), destination.slot());
                  left[i] -= count;
                  needed -= count;
               }
            }

            if (needed > 0) {
               return false;
            }
         }

         for (int ix = 0; ix < changes.size(); ix++) {
            if (left[ix] > 0) {
               if (!thrown) {
                  return false;
               }

               request.drop(left[ix], changes.get(ix).slot());
            }
         }

         return true;
      }
   }

   public void onCreativeSlot(int javaSlot, ItemStack stack) {
      this.inventory.openInventory();
      StackRequests.Request request = new StackRequests.Request();
      if (javaSlot < 0) {
         if (!stack.isEmpty() && this.create(request, stack, stack.getCount())) {
            request.drop(stack.getCount(), BedrockSlot.CREATED_OUTPUT);
            request.submit();
         }
      } else {
         BedrockSlot slot = ContainerLayout.PLAYER.slot(javaSlot, 46);
         if (slot != null && slot.container() != 124) {
            StackRequests.Change change = this.change(slot, stack);
            if (change.changed()) {
               ItemData cursor = this.inventory.get(BedrockSlot.CURSOR);
               boolean fromCursor = !cursor.isNull() && ItemStack.isSameItemSameComponents(ItemMappings.toJava(cursor), stack);
               int before = change.beforeCount();
               boolean same = change.holds(stack);
               if (!same && !stack.isEmpty() && slot.type() == ContainerSlotType.OFFHAND && !this.offhandTakes(fromCursor ? cursor : null, stack)) {
                  this.refuseCreativeOffhand(slot, stack);
               } else {
                  if (stack.isEmpty()) {
                     if (cursor.isNull()) {
                        request.move(before, slot, BedrockSlot.CURSOR);
                     } else {
                        request.destroy(before, slot);
                     }
                  } else if (same && stack.getCount() < before) {
                     request.destroy(before - stack.getCount(), slot);
                  } else if (!same && !change.before().isNull() && fromCursor && cursor.getCount() == stack.getCount()) {
                     request.swap(slot, BedrockSlot.CURSOR);
                  } else {
                     if (!same && !change.before().isNull()) {
                        request.destroy(before, slot);
                        before = 0;
                     }

                     int added = stack.getCount() - (same ? before : 0);
                     if (fromCursor && cursor.getCount() >= added) {
                        request.move(added, BedrockSlot.CURSOR, slot);
                     } else {
                        if (!this.create(request, stack, added)) {
                           this.inventory.show(slot);
                           return;
                        }

                        request.move(added, BedrockSlot.CREATED_OUTPUT, slot);
                     }
                  }

                  request.submit();
                  if (!ItemStack.matches(ItemMappings.toJava(this.inventory.get(slot)), stack)) {
                     this.inventory.show(slot);
                  }
               }
            }
         }
      }
   }

   private boolean offhandTakes(ItemData fromCursor, ItemStack stack) {
      ItemData item = fromCursor;
      if (fromCursor == null) {
         CreativeItemData entry = this.creative.entryFor(stack);
         item = entry == null ? null : entry.getItem();
      }

      return item == null || ItemMappings.allowsOffhand(item.getDefinition().getIdentifier());
   }

   private void refuseCreativeOffhand(BedrockSlot slot, ItemStack stack) {
      BedrockNative.LOGGER.info("Creative offhand: Bedrock's offhand does not take {}; showing the server's slot again", stack);
      this.inventory.show(slot);
      LocalPlayer player = this.minecraft.player;
      if (player != null && this.minecraft.gui.screen() instanceof CreativeModeInventoryScreen) {
         ItemStack carried = player.inventoryMenu.getCarried();
         if (ItemStack.isSameItemSameComponents(carried, stack)) {
            carried.grow(stack.getCount());
         } else {
            player.inventoryMenu.setCarried(stack.copy());
         }
      }
   }

   private boolean create(StackRequests.Request request, ItemStack stack, int count) {
      CreativeItemData entry = this.creative.entryFor(stack);
      if (entry == null) {
         BedrockNative.LOGGER.warn("No Bedrock creative item for {}", stack);
         return false;
      } else {
         this.creative.created(entry);
         request.create(entry, count);
         return true;
      }
   }

   public void onResponse(ItemStackResponsePacket packet) {
      for (ItemStackResponse response : packet.getEntries()) {
         Map<BedrockSlot, ItemData> before = (Map<BedrockSlot, ItemData>)this.pending.remove(response.getRequestId());
         String sent = (String)this.pendingActions.remove(response.getRequestId());
         this.lastStatus = response.getResult();
         if (response.getResult() != ItemStackResponseStatus.OK) {
            this.rejected++;
            REJECTED_TOTAL.incrementAndGet();
            BedrockNative.LOGGER
               .warn("Bedrock rejected stack request {}: {}; sent {}", new Object[]{response.getRequestId(), response.getResult(), sent == null ? "?" : sent});
            if (before != null) {
               before.forEach(this.inventory::setAndShow);
            }
         } else {
            this.accepted++;
            ACCEPTED_TOTAL.incrementAndGet();

            for (ItemStackResponseContainer container : response.getContainers()) {
               ContainerSlotType type = container.getContainerName().getContainer();

               for (ItemStackResponseSlot entry : container.getItems()) {
                  this.confirm(BedrockSlot.fromResponse(type, entry.getSlot(), this.inventory.openContainerId()), entry);
               }
            }
         }
      }
   }

   private void confirm(BedrockSlot slot, ItemStackResponseSlot entry) {
      ObjectIterator known = this.pending.values().iterator();

      while (known.hasNext()) {
         Map<BedrockSlot, ItemData> later = (Map<BedrockSlot, ItemData>)known.next();
         if (later.containsKey(slot)) {
            return;
         }
      }

      ItemData knownx = this.inventory.get(slot);
      if (entry.getCount() == 0) {
         if (!knownx.isNull()) {
            this.inventory.setAndShow(slot, ItemData.AIR);
         }
      } else if (knownx.isNull()) {
         BedrockNative.LOGGER
            .warn(
               "Stack response names {} x{} in {} slot {}, which we think is empty",
               new Object[]{entry.getStackNetworkId(), entry.getCount(), slot.type(), slot.slot()}
            );
      } else {
         ItemData confirmed = this.corrected(knownx, entry);
         this.inventory.set(slot, confirmed.toBuilder().count(entry.getCount()).usingNetId(true).netId(entry.getStackNetworkId()).build());
         if (knownx.getCount() != entry.getCount() || confirmed != knownx) {
            this.inventory.show(slot);
         }
      }
   }

   private ItemData corrected(ItemData known, ItemStackResponseSlot entry) {
      NbtMapBuilder tag = null;
      String name = entry.getCustomName();
      if (name != null && !name.isEmpty() && !name.equals(WorkstationRules.customName(known))) {
         tag = known.getTag() == null ? NbtMap.builder() : known.getTag().toBuilder();
         NbtMap display = known.getTag() == null ? NbtMap.EMPTY : known.getTag().getCompound("display", NbtMap.EMPTY);
         tag.putCompound("display", display.toBuilder().putString("Name", name).build());
      }

      if (this.workstations != null && entry.getDurabilityCorrection() != WorkstationRules.damage(known) && this.workstations.facts().maxDamage(known) > 0) {
         tag = tag != null ? tag : (known.getTag() == null ? NbtMap.builder() : known.getTag().toBuilder());
         tag.putInt("Damage", entry.getDurabilityCorrection());
      }

      return tag == null ? known : known.toBuilder().tag(tag.build()).build();
   }

   private static String refusedByOffhand(List<StackRequests.Change> changes) {
      for (StackRequests.Change change : changes) {
         if (change.slot().type() == ContainerSlotType.OFFHAND && !change.after().isEmpty() && !change.holds(change.after())) {
            for (StackRequests.Change source : changes) {
               if (source != change && source.holds(change.after())) {
                  String identifier = source.before().getDefinition().getIdentifier();
                  return ItemMappings.allowsOffhand(identifier) ? null : identifier;
               }
            }
         }
      }

      return null;
   }

   private void resync(AbstractContainerMenu menu, String why) {
      BedrockNative.LOGGER.info("Inventory click not sent to Bedrock ({}); showing the server's slots again", why);
      this.inventory.showMenu(menu);
   }

   private StackRequests.Change change(BedrockSlot slot, ItemStack after) {
      ItemData before = this.inventory.get(slot);
      return new StackRequests.Change(slot, before, ItemMappings.toJava(before), after.copy());
   }

   private static ItemData withCount(ItemData item, int count, int netId) {
      return count <= 0 ? ItemData.AIR : item.toBuilder().count(count).usingNetId(true).netId(netId).build();
   }

   static boolean placesItems(BedrockSlot slot) {
      return slot.container() == 124 && !slot.equals(BedrockSlot.CURSOR) || slot.container() == 120;
   }

   private record Change(BedrockSlot slot, ItemData before, ItemStack beforeStack, ItemStack after) {
      int beforeCount() {
         return this.before.isNull() ? 0 : this.before.getCount();
      }

      boolean holds(ItemStack stack) {
         return !this.before.isNull() && ItemStack.isSameItemSameComponents(this.beforeStack, stack);
      }

      boolean changed() {
         return this.after.isEmpty() ? !this.before.isNull() : !this.holds(this.after) || this.before.getCount() != this.after.getCount();
      }

      boolean replaced() {
         return !this.before.isNull() && !this.after.isEmpty() && !this.holds(this.after);
      }

      int delta() {
         return this.after.getCount() - this.beforeCount();
      }

      boolean swapsWith(StackRequests.Change other) {
         return this.holds(other.after)
            && other.holds(this.after)
            && this.beforeCount() == other.after.getCount()
            && other.beforeCount() == this.after.getCount();
      }
   }

   final class Request {
      final int id;
      final List<ItemStackRequestAction> actions;
      final Map<BedrockSlot, ItemData> before;
      final Map<BedrockSlot, ItemData> after;
      final Set<BedrockSlot> consumed;
      final List<String> filters;
      TextProcessingEventOrigin origin;
      boolean autoPlace;
      boolean shiftPlaced;

      Request() {
         Objects.requireNonNull(StackRequests.this);
         super();
         this.id = StackRequests.nextRequestId;
         this.actions = new ArrayList<>();
         this.before = new LinkedHashMap<>();
         this.after = new LinkedHashMap<>();
         this.consumed = new HashSet<>();
         this.filters = new ArrayList<>();
      }

      ItemData get(BedrockSlot slot) {
         ItemData item = this.after.get(slot);
         return item != null ? item : StackRequests.this.inventory.get(slot);
      }

      void put(BedrockSlot slot, ItemData item) {
         this.before.putIfAbsent(slot, StackRequests.this.inventory.get(slot));
         this.after.put(slot, item);
      }

      ItemStackRequestSlotData ref(BedrockSlot slot) {
         if (this.get(slot).isNull()) {
            return slot.request(this.consumed.contains(slot) ? this.id : 0);
         } else {
            ItemData start = this.before.containsKey(slot) ? this.before.get(slot) : StackRequests.this.inventory.get(slot);
            return slot.request(!start.isNull() ? start.getNetId() : this.id);
         }
      }

      private BedrockSlot named(BedrockSlot slot, BedrockSlot other, boolean destination) {
         if (!slot.isPlayer()) {
            return slot;
         } else {
            boolean placedByBedrock = this.shiftPlaced && destination && StackRequests.placesItems(other);
            return !this.autoPlace && !placedByBedrock ? slot : slot.named(ContainerSlotType.HOTBAR_AND_INVENTORY);
         }
      }

      void move(int count, BedrockSlot from, BedrockSlot to) {
         ItemData source = this.get(from);
         ItemData target = this.get(to);
         ItemStackRequestSlotData fromRef = this.named(from, to, false).request(this.ref(from).getStackNetworkId());
         ItemStackRequestSlotData toRef = this.named(to, from, true).request(this.ref(to).getStackNetworkId());
         this.actions
            .add((ItemStackRequestAction)(to.equals(BedrockSlot.CURSOR) ? new TakeAction(count, fromRef, toRef) : new PlaceAction(count, fromRef, toRef)));
         this.put(to, StackRequests.withCount(target.isNull() ? source : target, target.getCount() + count, this.id));
         this.put(from, StackRequests.withCount(source, source.getCount() - count, this.id));
      }

      void swap(BedrockSlot a, BedrockSlot b) {
         this.actions.add(new SwapAction(this.ref(a), this.ref(b)));
         ItemData first = this.get(a);
         this.put(a, StackRequests.withCount(this.get(b), this.get(b).getCount(), this.id));
         this.put(b, StackRequests.withCount(first, first.getCount(), this.id));
      }

      void drop(int count, BedrockSlot from) {
         this.actions.add(new DropAction(count, this.ref(from), false));
         this.put(from, StackRequests.withCount(this.get(from), this.get(from).getCount() - count, this.id));
      }

      void destroy(int count, BedrockSlot from) {
         this.actions.add(new DestroyAction(count, this.ref(from)));
         this.put(from, StackRequests.withCount(this.get(from), this.get(from).getCount() - count, this.id));
      }

      void beaconPayment(int primary, int secondary, BedrockSlot payment) {
         this.actions.add(new BeaconPaymentAction(primary, secondary));
         this.destroy(1, payment);
      }

      void craft(Crafting.Match match, int times, List<BedrockSlot> grid) {
         this.actions.add(new CraftRecipeAction(match.netId(), times));
         this.actions.add(new CraftResultsDeprecatedAction(match.results().toArray(new ItemData[0]), times));

         for (BedrockSlot slot : grid) {
            ItemData item = this.get(slot);
            if (!item.isNull()) {
               this.actions.add(new ConsumeAction(times, this.ref(slot)));
               this.put(slot, StackRequests.withCount(item, item.getCount() - times, this.id));
            }
         }

         this.put(BedrockSlot.CREATED_OUTPUT, StackRequests.withCount(match.result(), match.result().getCount() * times, this.id));
      }

      void create(CreativeItemData entry, int count) {
         this.actions.add(new CraftCreativeAction(entry.getNetId(), count));
         this.put(BedrockSlot.CREATED_OUTPUT, StackRequests.withCount(entry.getItem(), count, this.id));
      }

      void consume(int count, BedrockSlot from) {
         this.actions.add(new ConsumeAction(count, this.ref(from)));
         this.put(from, StackRequests.withCount(this.get(from), this.get(from).getCount() - count, this.id));
         this.consumed.add(from);
      }

      void craft(Workstations.Craft craft, List<Workstations.Use> outputs) {
         if (craft.consumeFirst()) {
            craft.uses().forEach(use -> this.consume(use.count(), use.slot()));
         }

         for (ItemStackRequestAction action : craft.header()) {
            this.actions
               .add(
                  (ItemStackRequestAction)(action instanceof CraftGrindstoneAction grindstone && grindstone.getRecipeNetworkId() == Integer.MIN_VALUE
                     ? new CraftGrindstoneAction(this.id, grindstone.getNumberOfRequestedCrafts(), grindstone.getRepairCost())
                     : action)
               );
         }

         if (!craft.consumeFirst()) {
            craft.uses().forEach(use -> this.consume(use.count(), use.slot()));
         }

         this.put(BedrockSlot.CREATED_OUTPUT, StackRequests.withCount(craft.result(), craft.result().getCount() * craft.times(), this.id));
         outputs.forEach(output -> this.move(output.count(), BedrockSlot.CREATED_OUTPUT, output.slot()));
         craft.after().forEach(use -> this.consume(use.count(), use.slot()));
         this.filters.addAll(craft.filters());
         this.origin = craft.origin();
      }

      void submit() {
         if (!this.actions.isEmpty()) {
            StackRequests.nextRequestId -= 2;
            ItemStackRequestPacket packet = new ItemStackRequestPacket();
            packet.getRequests()
               .add(new ItemStackRequest(this.id, this.actions.toArray(new ItemStackRequestAction[0]), this.filters.toArray(new String[0]), this.origin));
            StackRequests.this.send.accept(packet);
            this.after.forEach(StackRequests.this.inventory::set);
            if (StackRequests.this.pending.size() >= 64) {
               StackRequests.this.pending.clear();
               StackRequests.this.pendingActions.clear();
            }

            StackRequests.this.pending.put(this.id, this.before);
            StackRequests.this.pendingActions.put(this.id, this.actions.toString());
            BedrockNative.LOGGER.debug("Stack request {}: {}", this.id, this.actions);
         }
      }
   }
}
