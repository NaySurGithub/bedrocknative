package net.bedrocknative.client.inventory;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.lang.runtime.SwitchBootstraps;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.LongFunction;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.text.BedrockNameTag;
import net.bedrocknative.client.ui.JsonUiScreens;
import net.bedrocknative.client.world.BedrockBlockNames;
import net.bedrocknative.client.world.BedrockJukeboxDiscs;
import net.bedrocknative.net.client.WindowCloseGuard;
import net.bedrocknative.ui.ContainerScreens;
import net.bedrocknative.ui.UiTreeDump;
import net.bedrocknative.world.block.ContainerNames;
import net.bedrocknative.world.entity.BedrockActorEntity;
import net.bedrocknative.world.item.BedrockItemTags;
import net.bedrocknative.world.item.ItemMappings;
import net.bedrocknative.world.state.MountPlan;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundMountScreenOpenPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundSetCursorItemPacket;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlot.Type;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtMapBuilder;
import org.cloudburstmc.protocol.bedrock.data.PlayerArmorDamageFlag;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerSlotType;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerType;
import org.cloudburstmc.protocol.bedrock.data.inventory.FullContainerName;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryActionData;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryTransactionType;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.ContainerClosePacket;
import org.cloudburstmc.protocol.bedrock.packet.ContainerOpenPacket;
import org.cloudburstmc.protocol.bedrock.packet.ContainerSetDataPacket;
import org.cloudburstmc.protocol.bedrock.packet.InteractPacket;
import org.cloudburstmc.protocol.bedrock.packet.InventoryContentPacket;
import org.cloudburstmc.protocol.bedrock.packet.InventorySlotPacket;
import org.cloudburstmc.protocol.bedrock.packet.InventoryTransactionPacket;
import org.cloudburstmc.protocol.bedrock.packet.MobEquipmentPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerArmorDamagePacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerHotbarPacket;
import org.cloudburstmc.protocol.bedrock.packet.ToggleCrafterSlotRequestPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateEquipPacket;
import org.cloudburstmc.protocol.bedrock.packet.InteractPacket.Action;
import org.jspecify.annotations.Nullable;

public final class BedrockInventory {
   private static final int FURNACE_COOK_TIME = 200;
   private static final int BREW_TIME_DATA = 0;
   private static final int BREW_FUEL_DATA = 1;
   private static final int BREW_TOTAL_TIME_DATA = 2;
   private static final int BREW_TOTAL_FUEL_DATA = 3;
   private static final int BREW_TICKS = 400;
   private static final int BLAZE_FUEL = 20;
   private final Minecraft minecraft;
   private final Consumer<BedrockPacket> send;
   private final BedrockLecterns lecterns;
   private final BedrockBlockEditors editors;
   private final WindowCloseGuard closeGuard = new WindowCloseGuard();
   private final Int2ObjectMap<ItemData[]> containers = new Int2ObjectOpenHashMap();
   private final Int2ObjectMap<ItemData[]> dynamic = new Int2ObjectOpenHashMap();
   private final Map<EquipmentSlot, ItemStack> worn = new EnumMap<>(EquipmentSlot.class);
   private long runtimeId;
   private BedrockInventory.OpenContainer open;
   private boolean inventoryOpen;
   private int inventoryId = 0;
   private int reportedSlot = -1;
   private ItemData reportedItem = ItemData.AIR;
   private ItemData reportedOffhand = ItemData.AIR;
   private BiConsumer<BedrockSlot, ItemData> serverUpdates = (slot, item) -> {};
   private LongFunction<Entity> entities = id -> null;
   private int equipWindow = -1;
   private long equipEntity;

   public BedrockInventory(Minecraft minecraft, Consumer<BedrockPacket> send) {
      this.minecraft = minecraft;
      this.send = send;
      this.lecterns = new BedrockLecterns(minecraft, send);
      this.editors = new BedrockBlockEditors(minecraft, send);
      this.reset(0L);
      BedrockItemTags.useDynamicContainers(this, id -> {
         ItemData[] items = (ItemData[])this.dynamic.get(id);
         return items == null ? List.of() : Arrays.asList(items);
      });
   }

   public static boolean handles(BedrockPacket packet) {
      return packet instanceof InventoryContentPacket
         || packet instanceof InventorySlotPacket
         || packet instanceof PlayerHotbarPacket
         || packet instanceof ContainerOpenPacket
         || packet instanceof ContainerClosePacket
         || packet instanceof ContainerSetDataPacket
         || packet instanceof PlayerArmorDamagePacket
         || packet instanceof InventoryTransactionPacket;
   }

   public void reset(long runtimeId) {
      this.runtimeId = runtimeId;
      this.containers.clear();
      this.dynamic.clear();
      this.worn.clear();
      this.open = null;
      this.closeGuard.reset();
      this.equipWindow = -1;
      BedrockLecterns.clear();
      BedrockJukeboxDiscs.clear();
      this.editors.reset();
      this.inventoryOpen = false;
      this.reportedSlot = -1;
      this.reportedItem = ItemData.AIR;
      this.reportedOffhand = ItemData.AIR;
      JsonUiScreens.reset();
   }

   @Nullable
   public BedrockInventory.HeldReport takeHeldReport() {
      int slot = this.selectedSlot();
      ItemData item = this.held();
      boolean sameSlot = slot == this.reportedSlot;
      if (sameSlot && sameStack(item, this.reportedItem)) {
         return null;
      } else {
         boolean otherItem = item.isNull() != this.reportedItem.isNull() || !item.isNull() && item.getDefinition() != this.reportedItem.getDefinition();
         this.reportedSlot = slot;
         this.reportedItem = item;
         return new BedrockInventory.HeldReport(this.equipment(this.runtimeId, slot), otherItem);
      }
   }

   @Nullable
   public MobEquipmentPacket takeOffhandReport() {
      ItemData item = this.get(BedrockSlot.offhand());
      if (sameStack(item, this.reportedOffhand)) {
         return null;
      } else {
         this.reportedOffhand = item;
         MobEquipmentPacket packet = new MobEquipmentPacket();
         packet.setRuntimeEntityId(this.runtimeId);
         packet.setItem(withoutStackId(item));
         packet.setInventorySlot(1);
         packet.setHotbarSlot(0);
         packet.setContainerId(119);
         return packet;
      }
   }

   private static boolean sameStack(ItemData a, ItemData b) {
      return a.isNull() ? b.isNull() : !b.isNull() && a.equals(b);
   }

   public static ItemData withoutStackId(ItemData item) {
      return !item.isNull() && item.isUsingNetId() ? item.toBuilder().usingNetId(false).netId(0).build() : item;
   }

   public void apply(BedrockPacket packet) {
      Objects.requireNonNull(packet);
      BedrockPacket var2 = packet;
      byte var3 = 0;

      while (true) {
         switch (SwitchBootstraps.typeSwitch<"typeSwitch",InventoryContentPacket,InventorySlotPacket,InventoryContentPacket,InventorySlotPacket,PlayerHotbarPacket,ContainerOpenPacket,ContainerClosePacket,ContainerSetDataPacket,PlayerArmorDamagePacket,InventoryTransactionPacket>(
            var2, var3
         )) {
            case 0: {
               InventoryContentPacket content = (InventoryContentPacket)var2;
               if (dynamicId(content.getContainerNameData()) == null) {
                  var3 = 1;
                  break;
               }

               this.onDynamic(dynamicId(content.getContainerNameData()), content.getContents().toArray(ItemData[]::new));
               return;
            }
            case 1: {
               InventorySlotPacket slot = (InventorySlotPacket)var2;
               if (dynamicId(slot.getContainerNameData()) == null) {
                  var3 = 2;
                  break;
               }

               this.onDynamicSlot(dynamicId(slot.getContainerNameData()), slot);
               return;
            }
            case 2: {
               InventoryContentPacket content = (InventoryContentPacket)var2;
               this.onContent(content.getContainerId(), content.getContents());
               return;
            }
            case 3: {
               InventorySlotPacket slot = (InventorySlotPacket)var2;
               this.fromServer(new BedrockSlot(slot.getContainerId(), slot.getSlot(), null), slot.getItem(), true);
               return;
            }
            case 4:
               PlayerHotbarPacket hotbar = (PlayerHotbarPacket)var2;
               if (hotbar.isSelectHotbarSlot() && hotbar.getContainerId() == 0) {
                  this.select(hotbar.getSelectedHotbarSlot());
                  return;
               }

               var3 = 5;
               break;
            case 5:
               ContainerOpenPacket opened = (ContainerOpenPacket)var2;
               this.onOpen(opened);
               return;
            case 6:
               ContainerClosePacket closed = (ContainerClosePacket)var2;
               this.onClose(closed);
               return;
            case 7:
               ContainerSetDataPacket data = (ContainerSetDataPacket)var2;
               this.onData(data);
               return;
            case 8:
               PlayerArmorDamagePacket damage = (PlayerArmorDamagePacket)var2;
               this.onArmorDamage(damage);
               return;
            case 9:
               InventoryTransactionPacket transaction = (InventoryTransactionPacket)var2;
               if (transaction.getTransactionType() != InventoryTransactionType.NORMAL) {
                  var3 = 10;
                  break;
               } else {
                  this.onTransaction(transaction);
                  return;
               }
            default:
               return;
         }
      }
   }

   public void tickArmor(LocalPlayer player) {
      for (EquipmentSlot slot : EquipmentSlot.VALUES) {
         if (slot.getType() == Type.HUMANOID_ARMOR) {
            ItemStack now = player.getItemBySlot(slot);
            ItemStack before = this.worn.getOrDefault(slot, ItemStack.EMPTY);
            if (!ItemStack.matches(before, now)) {
               before.forEachModifier(slot, (attribute, modifier) -> {
                  AttributeInstance instance = player.getAttribute(attribute);
                  if (instance != null) {
                     instance.removeModifier(modifier.id());
                  }
               });
               if (!now.isBroken()) {
                  now.forEachModifier(slot, (attribute, modifier) -> {
                     AttributeInstance instance = player.getAttribute(attribute);
                     if (instance != null && (attribute.is(Attributes.ARMOR) || attribute.is(Attributes.ARMOR_TOUGHNESS))) {
                        instance.removeModifier(modifier.id());
                        instance.addTransientModifier(modifier);
                     }
                  });
               }

               this.worn.put(slot, now.copy());
            }
         }
      }
   }

   public void onLocalEquipment(MobEquipmentPacket equipment) {
      if (equipment.getContainerId() == 0) {
         this.fromServer(BedrockSlot.player(equipment.getInventorySlot()), equipment.getItem(), true);
         this.select(equipment.getHotbarSlot());
      }
   }

   public ItemData get(BedrockSlot slot) {
      ItemData[] items = (ItemData[])this.containers.get(slot.container());
      ItemData item = items != null && slot.slot() >= 0 && slot.slot() < items.length ? items[slot.slot()] : null;
      return item == null ? ItemData.AIR : item;
   }

   public void set(BedrockSlot slot, ItemData item) {
      if (slot.slot() >= 0) {
         ItemData[] items = (ItemData[])this.containers.get(slot.container());
         if (items == null || slot.slot() >= items.length) {
            ItemData[] grown = new ItemData[Math.max(slot.slot() + 1, defaultSize(slot.container()))];
            Arrays.fill(grown, ItemData.AIR);
            if (items != null) {
               System.arraycopy(items, 0, grown, 0, items.length);
            }

            this.containers.put(slot.container(), grown);
            items = grown;
         }

         items[slot.slot()] = item == null ? ItemData.AIR : item;
      }
   }

   public void setAndShow(BedrockSlot slot, ItemData item) {
      this.set(slot, item);
      this.show(slot);
   }

   public void onServerUpdate(BiConsumer<BedrockSlot, ItemData> listener) {
      this.serverUpdates = listener;
   }

   private void fromServer(BedrockSlot slot, ItemData item, boolean show) {
      ItemData data = item == null ? ItemData.AIR : item;
      this.set(slot, data);
      this.serverUpdates.accept(slot, data);
      if (show) {
         this.show(slot);
      }
   }

   public void show(BedrockSlot slot) {
      ClientPacketListener connection = this.minecraft.getConnection();
      LocalPlayer player = this.minecraft.player;
      if (connection != null && player != null) {
         ItemStack stack = ItemMappings.toJava(this.get(slot));
         if (slot.equals(BedrockSlot.CURSOR)) {
            connection.handleSetCursorItem(new ClientboundSetCursorItemPacket(stack));
         } else {
            int containerSlot = this.open != null && this.open.layout != null ? this.open.layout.javaSlot(slot) : -1;
            if (containerSlot >= 0) {
               if (player.containerMenu.containerId == this.open.id) {
                  connection.handleContainerSetSlot(
                     new ClientboundContainerSetSlotPacket(this.open.id, player.containerMenu.getStateId(), containerSlot, stack)
                  );
               }
            } else {
               int inventorySlot = ContainerLayout.PLAYER.javaSlot(slot);
               if (inventorySlot >= 0) {
                  connection.handleContainerSetSlot(new ClientboundContainerSetSlotPacket(0, player.inventoryMenu.getStateId(), inventorySlot, stack));
               }
            }
         }
      }
   }

   public BedrockSlot slotOf(AbstractContainerMenu menu, int javaSlot) {
      if (menu instanceof InventoryMenu) {
         return ContainerLayout.PLAYER.slot(javaSlot, menu.slots.size());
      } else {
         return this.open != null && this.open.layout != null && menu.containerId == this.open.id ? this.open.layout.slot(javaSlot, menu.slots.size()) : null;
      }
   }

   public void showMenu(AbstractContainerMenu menu) {
      for (int javaSlot = 0; javaSlot < menu.slots.size(); javaSlot++) {
         BedrockSlot slot = this.slotOf(menu, javaSlot);
         if (slot != null) {
            this.show(slot);
         }
      }

      this.show(BedrockSlot.CURSOR);
   }

   public BedrockLecterns lecterns() {
      return this.lecterns;
   }

   public BedrockBlockEditors editors() {
      return this.editors;
   }

   public void tickScreens() {
      this.lecterns.tick();
      this.editors.tick();
      WindowCloseGuard.Settled settled = this.closeGuard.endOfTick();

      for (int answer = 0; answer < settled.answers(); answer++) {
         this.send.accept(WindowCloseGuard.answer());
      }

      if (settled.closeWindow() != null && this.open != null && this.open.id == settled.closeWindow()) {
         this.closeOpenWindow();
      }
   }

   public WindowCloseGuard closeGuard() {
      return this.closeGuard;
   }

   private void closeOpenWindow() {
      LocalPlayer player = this.minecraft.player;
      boolean showing = this.open != null && player != null && player.containerMenu.containerId == this.open.id;
      ContainerTrace.client(
         this.open == null ? "close: nothing open" : "window " + this.open.id + " closed" + (showing ? " (its screen goes)" : " (no screen)")
      );
      this.open = null;
      if (showing) {
         player.clientSideCloseContainer();
      }
   }

   public int openContainerId() {
      return this.open == null ? -1 : this.open.id;
   }

   public ContainerType openContainerType() {
      return this.open == null ? null : this.open.type;
   }

   @Nullable
   public ContainerLayout openLayout() {
      return this.open == null ? null : this.open.layout;
   }

   @Nullable
   public BlockPos openPosition() {
      return this.open == null ? null : this.open.pos;
   }

   public boolean openTrade(int id, boolean newUi) {
      if (this.open != null && this.open.id == id && this.open.type == ContainerType.TRADE) {
         return false;
      } else {
         this.open = new BedrockInventory.OpenContainer(id, ContainerType.TRADE, BlockPos.ZERO, 0L);
         this.open.layout = ContainerLayout.trade(newUi);
         return true;
      }
   }

   public ItemData item(int slot) {
      return this.get(BedrockSlot.player(slot));
   }

   public int selectedSlot() {
      LocalPlayer player = this.minecraft.player;
      return player == null ? 0 : player.getInventory().getSelectedSlot();
   }

   public ItemData held() {
      return this.item(this.selectedSlot());
   }

   public ItemData predictedHeld() {
      ItemData server = this.held();
      ItemStack predicted = this.minecraft.player == null ? ItemStack.EMPTY : this.minecraft.player.getMainHandItem();
      if (!server.isNull() && !predicted.isEmpty()) {
         return predicted.getCount() == server.getCount() ? server : server.toBuilder().count(predicted.getCount()).build();
      } else {
         return predicted.isEmpty() ? ItemData.AIR : server;
      }
   }

   public void predict(int slot, ItemData item) {
      this.set(BedrockSlot.player(slot), item);
   }

   public MobEquipmentPacket equipment(long runtimeId, int hotbarSlot) {
      MobEquipmentPacket packet = new MobEquipmentPacket();
      packet.setRuntimeEntityId(runtimeId);
      packet.setItem(withoutStackId(this.item(hotbarSlot)));
      packet.setInventorySlot(hotbarSlot);
      packet.setHotbarSlot(hotbarSlot);
      packet.setContainerId(0);
      return packet;
   }

   public void openInventory() {
      if (!this.inventoryOpen) {
         InteractPacket interact = new InteractPacket();
         interact.setAction(Action.OPEN_INVENTORY);
         interact.setRuntimeEntityId(this.runtimeId);
         this.send.accept(interact);
         this.inventoryOpen = true;
      }
   }

   public boolean isInventoryOpen() {
      return this.inventoryOpen;
   }

   public void closeInventory() {
      if (this.inventoryOpen) {
         this.sendClose(this.inventoryId);
         this.inventoryOpen = false;
      }
   }

   public void onJavaClose(int containerId) {
      if (this.open != null && containerId == this.open.id) {
         this.sendClose(this.open.type == ContainerType.TRADE ? -1 : this.open.id);
         this.open = null;
      } else if (containerId == 0) {
         this.closeInventory();
      }
   }

   private void sendClose(int id) {
      ContainerClosePacket close = new ContainerClosePacket();
      close.setId((byte)id);
      close.setType(ContainerType.NONE);
      close.setServerInitiated(false);
      this.send.accept(close);
   }

   private void onContent(int containerId, List<ItemData> items) {
      if (this.open != null && containerId == this.open.id) {
         StringBuilder ids = new StringBuilder();
         int shown = 0;

         for (int slot = 0; slot < items.size() && shown < 16; slot++) {
            ItemData item = items.get(slot);
            if (item != null && !item.isNull()) {
               ids.append(slot).append(':').append(item.getNetId()).append(' ');
               shown++;
            }
         }

         BedrockNative.LOGGER.info("Container {} content: {} items, ids {}", new Object[]{containerId, items.size(), ids.toString().trim()});
      }

      if (this.open != null && containerId == this.open.id && this.open.layout == null) {
         if (this.open.type == ContainerType.HORSE) {
            this.openMount(items.size());
         } else {
            this.openScreen(ContainerLayout.of(this.open.type, this.open.id, items.size(), this.block(this.open.pos)), items.size());
         }
      }

      for (int slotx = 0; slotx < items.size(); slotx++) {
         this.fromServer(new BedrockSlot(containerId, slotx, null), items.get(slotx), false);
      }

      for (int slotx = 0; slotx < items.size(); slotx++) {
         this.show(new BedrockSlot(containerId, slotx, null));
      }
   }

   private static Integer dynamicId(FullContainerName name) {
      return name != null && name.getContainer() == ContainerSlotType.DYNAMIC_CONTAINER ? name.getDynamicId() : null;
   }

   private void onDynamic(int id, ItemData[] items) {
      this.dynamic.put(id, items);
      this.showPlayerSlots();
   }

   private void onDynamicSlot(int id, InventorySlotPacket packet) {
      ItemData[] items = (ItemData[])this.dynamic.get(id);
      if (items == null || packet.getSlot() >= items.length) {
         ItemData[] grown = new ItemData[Math.max(packet.getSlot() + 1, items == null ? 0 : items.length)];
         Arrays.fill(grown, ItemData.AIR);
         if (items != null) {
            System.arraycopy(items, 0, grown, 0, items.length);
         }

         items = grown;
      }

      if (packet.getSlot() >= 0) {
         items[packet.getSlot()] = packet.getItem() == null ? ItemData.AIR : packet.getItem();
      }

      this.onDynamic(id, items);
   }

   private void onTransaction(InventoryTransactionPacket transaction) {
      for (InventoryActionData action : transaction.getActions()) {
         if (action.getSource().getType() == org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventorySource.Type.CONTAINER) {
            this.fromServer(new BedrockSlot(action.getSource().getContainerId(), action.getSlot(), null), action.getToItem(), true);
         }
      }
   }

   private void onArmorDamage(PlayerArmorDamagePacket packet) {
      for (PlayerArmorDamageFlag flag : packet.getFlags()) {
         if (flag != PlayerArmorDamageFlag.BODY) {
            BedrockSlot slot = BedrockSlot.armor(flag.ordinal());
            ItemData worn = this.get(slot);
            if (!worn.isNull()) {
               NbtMapBuilder tag = worn.getTag() == null ? NbtMap.builder() : worn.getTag().toBuilder();
               tag.putInt("Damage", packet.getDamage()[flag.ordinal()]);
               this.setAndShow(slot, worn.toBuilder().tag(tag.build()).build());
            }
         }
      }
   }

   public void showPlayerSlots() {
      for (int slot = 0; slot < 36; slot++) {
         this.show(BedrockSlot.player(slot));
      }

      for (int slot = 0; slot < 4; slot++) {
         this.show(BedrockSlot.armor(slot));
      }

      this.show(BedrockSlot.offhand());
      this.show(BedrockSlot.CURSOR);
   }

   private void onOpen(ContainerOpenPacket packet) {
      if (packet.getType() != ContainerType.INVENTORY) {
         BedrockNative.LOGGER
            .info(
               "Container open: id {}, type {}, at {}, entity {}",
               new Object[]{packet.getId(), packet.getType(), packet.getBlockPosition(), packet.getUniqueEntityId()}
            );
      } else {
         BedrockNative.LOGGER
            .debug(
               "Container open: id {}, type {}, at {}, entity {}",
               new Object[]{packet.getId(), packet.getType(), packet.getBlockPosition(), packet.getUniqueEntityId()}
            );
      }

      if (this.closeGuard.reopened(packet.getId())) {
         BlockPos again = new BlockPos(packet.getBlockPosition().getX(), packet.getBlockPosition().getY(), packet.getBlockPosition().getZ());
         if (this.open != null
            && this.open.id == packet.getId()
            && this.open.type == packet.getType()
            && this.open.pos.equals(again)
            && this.open.entityId == packet.getUniqueEntityId()) {
            BedrockNative.LOGGER
               .info("Container {} (id {}) opened again at once: the screen is rebuilt for the new contents", packet.getType(), packet.getId());
            ContainerTrace.client("window " + packet.getId() + " opened again at once: screen rebuilt");
         }

         this.closeGuard.keep();
         this.closeOpenWindow();
      }

      if (packet.getType() == ContainerType.INVENTORY) {
         this.inventoryId = packet.getId();
         this.inventoryOpen = true;
      } else if (packet.getType() == ContainerType.LECTERN) {
         this.lecterns.open(packet);
      } else if (BedrockBlockEditors.handles(packet.getType())) {
         this.editors.open(packet);
      } else if (packet.getType() != ContainerType.DECORATED_POT && packet.getType() != ContainerType.JUKEBOX) {
         BlockPos pos = new BlockPos(packet.getBlockPosition().getX(), packet.getBlockPosition().getY(), packet.getBlockPosition().getZ());
         this.open = new BedrockInventory.OpenContainer(packet.getId(), packet.getType(), pos, packet.getUniqueEntityId());
         this.containers.remove(this.open.id);
         if (!ContainerLayout.sizedByContent(packet.getType())) {
            this.openScreen(ContainerLayout.of(packet.getType(), this.open.id, 0, this.block(pos)), 0);
         }
      } else {
         BedrockNative.LOGGER.debug("Container {} (id {}) has no screen on Bedrock; answering with a close", packet.getType(), packet.getId());
         this.sendClose(packet.getId());
      }
   }

   private void openScreen(ContainerLayout layout, int size) {
      ClientPacketListener connection = this.minecraft.getConnection();
      if (layout != null && this.open.id > 0 && connection != null) {
         this.open.layout = layout;
         boolean actorOwned = ContainerNames.actorOwned(this.open.entityId);
         Block block = actorOwned ? Blocks.AIR : this.block(this.open.pos);
         String customName = ContainerNames.opened(
            this.open.entityId,
            actorOwned ? this.actorName(this.open.entityId) : null,
            !actorOwned && this.minecraft.level != null ? BedrockBlockNames.customNameAt(this.minecraft.level, this.open.pos) : null
         );
         Component title = (Component)(customName != null
            ? ContainerNames.title(customName)
            : (
               layout.menu() == MenuType.GENERIC_9x6 && block instanceof ChestBlock
                  ? Component.translatable("container.chestDouble")
                  : (block == Blocks.AIR ? Component.translatable("container.chest") : block.getName())
            ));
         if (actorOwned || customName != null) {
            BedrockNative.LOGGER
               .info(
                  "Container {} ({}, {} slots) of {}: titled {}",
                  new Object[]{
                     this.open.id,
                     this.open.type,
                     size,
                     actorOwned ? this.describeActor(this.open.entityId) : "the block at " + this.open.pos.toShortString(),
                     customName != null ? "\"" + UiTreeDump.escape(customName) + "\"" : "by default"
                  }
               );
         }

         ContainerTrace.client("screen opens for window " + this.open.id + " (" + this.open.type + ", " + size + " slots)");
         int id = this.open.id;
         Long owner = actorOwned ? this.open.entityId : null;
         boolean anySize = layout.menu() == BedrockChestMenu.TYPE;
         JsonUiContainers.opening(id, ContainerScreens.of(this.open.type, size, kind(block)), customName, owner, () -> {
            if (anySize) {
               BedrockChestMenu.expect(size);
            }

            try {
               connection.handleOpenScreen(new ClientboundOpenScreenPacket(id, layout.menu(), title));
            } finally {
               if (anySize) {
                  BedrockChestMenu.expect(-1);
               }
            }
         });
         layout.slots().forEach(this::show);
         this.pushCrafterState();
      } else {
         BedrockNative.LOGGER.warn("No Java menu for Bedrock container {} (id {}, {} slots); closing it", new Object[]{this.open.type, this.open.id, size});
         this.sendClose(this.open.id);
         this.open = null;
      }
   }

   private void pushCrafterState() {
      ClientPacketListener connection = this.minecraft.getConnection();
      if (this.open != null && this.open.type == ContainerType.CRAFTER && this.open.layout != null && connection != null) {
         int mask = BedrockCrafters.mask(this.open.pos);

         for (int slot = 0; slot < 9; slot++) {
            int disabled = mask >> slot & 1;
            if (this.open.crafterData[slot] != disabled) {
               this.open.crafterData[slot] = disabled;
               this.setData(connection, slot, disabled);
            }
         }
      }
   }

   public void tickCrafter() {
      if (this.open != null && this.open.type == ContainerType.CRAFTER) {
         this.pushCrafterState();
      }
   }

   @Nullable
   public ToggleCrafterSlotRequestPacket onSlotState(int slot, int containerId, boolean enabledAfter) {
      if (this.open != null && this.open.type == ContainerType.CRAFTER && this.open.id == containerId && slot >= 0 && slot < 9) {
         BedrockCrafters.toggled(this.open.pos, slot, !enabledAfter);
         this.pushCrafterState();
         return BedrockCrafters.request(this.open.pos, slot, enabledAfter);
      } else {
         return null;
      }
   }

   @Nullable
   private String actorName(long uniqueId) {
      Entity entity = this.entities.apply(uniqueId);
      BedrockNameTag tag = entity == null ? null : BedrockNameTag.of(entity);
      return tag != null && !tag.name().isEmpty() ? tag.name() : null;
   }

   private String describeActor(long uniqueId) {
      Entity entity = this.entities.apply(uniqueId);
      if (entity == null) {
         return "actor " + uniqueId + " (not known here)";
      } else {
         String type = entity instanceof BedrockActorEntity actor ? actor.bedrockIdentifier() : String.valueOf(EntityType.getKey(entity.getType()));
         return "actor " + uniqueId + " (" + type + ")";
      }
   }

   private static ContainerScreens.Block kind(Block block) {
      return switch (block) {
         case ShulkerBoxBlock ignored -> ContainerScreens.Block.SHULKER_BOX;
         case BarrelBlock ignoredx -> ContainerScreens.Block.BARREL;
         case EnderChestBlock ignoredxx -> ContainerScreens.Block.ENDER_CHEST;
         case ChestBlock ignoredxxx -> ContainerScreens.Block.CHEST;
         default -> ContainerScreens.Block.OTHER;
      };
   }

   private void openMount(int size) {
      ClientPacketListener connection = this.minecraft.getConnection();
      Entity entity = this.entities.apply(this.open.entityId);
      if (!(entity instanceof AbstractHorse) && this.equipWindow == this.open.id) {
         entity = this.entities.apply(this.equipEntity);
      }

      MountLayout.Mount mount = entity instanceof AbstractHorse horse ? MountLayout.of(horse, this.open.id, size) : null;
      if (mount != null && this.open.id > 0 && connection != null) {
         this.open.layout = mount.layout();
         connection.handleMountScreenOpen(new ClientboundMountScreenOpenPacket(this.open.id, mount.columns(), entity.getId()));
         mount.layout().slots().forEach(this::show);
      } else {
         BedrockNative.LOGGER
            .warn("No Java menu for Bedrock mount container (id {}, entity {} = {}); closing it", new Object[]{this.open.id, this.open.entityId, entity});
         this.sendClose(this.open.id);
         this.open = null;
      }
   }

   public void onUpdateEquip(UpdateEquipPacket packet) {
      this.equipWindow = packet.getWindowId();
      this.equipEntity = packet.getUniqueEntityId();
      if (MountPlan.opensWindow(packet.getWindowType(), this.equipWindow, this.open != null && this.open.id == this.equipWindow)) {
         this.open = new BedrockInventory.OpenContainer(this.equipWindow, ContainerType.HORSE, BlockPos.ZERO, this.equipEntity);
         this.containers.remove(this.open.id);
      }
   }

   public void useEntities(LongFunction<Entity> byUniqueId) {
      this.entities = byUniqueId;
   }

   public void removeDynamic(List<FullContainerName> names) {
      boolean removed = false;

      for (FullContainerName name : names) {
         Integer id = dynamicId(name);
         removed |= id != null && this.dynamic.remove(id) != null;
      }

      if (removed) {
         this.showPlayerSlots();
      }
   }

   private void onClose(ContainerClosePacket packet) {
      BedrockNative.LOGGER
         .debug("Container close from server: id {}, type {}, server initiated {}", new Object[]{packet.getId(), packet.getType(), packet.isServerInitiated()});
      LocalPlayer player = this.minecraft.player;
      if (!this.lecterns.onServerClose(packet.getId()) && !this.editors.onServerClose(packet.getId())) {
         if (WindowCloseGuard.unaskedResponse(packet.isServerInitiated(), this.open != null && packet.getId() == this.open.id)) {
            BedrockNative.LOGGER.debug("Container {} (id {}) closed by a response nobody asked for: violation reported", packet.getType(), packet.getId());
            this.send.accept(this.closeGuard.defer(packet.getId()));
         } else {
            if (this.open != null && packet.getId() == this.open.id) {
               if (packet.isServerInitiated()) {
                  ContainerClosePacket answer = new ContainerClosePacket();
                  answer.setId(packet.getId());
                  answer.setType(ContainerType.NONE);
                  answer.setServerInitiated(false);
                  this.send.accept(answer);
               }

               this.closeOpenWindow();
            } else if (packet.isServerInitiated() && packet.getType() == ContainerType.INVENTORY && this.inventoryOpen) {
               this.inventoryOpen = false;
               if (player != null && player.containerMenu == player.inventoryMenu && this.minecraft.gui.screen() != null) {
                  player.clientSideCloseContainer();
               }
            }
         }
      }
   }

   private void onData(ContainerSetDataPacket data) {
      ClientPacketListener connection = this.minecraft.getConnection();
      if (this.open != null && data.getWindowId() == this.open.id && connection != null) {
         int value = data.getValue();
         switch (this.open.type) {
            case FURNACE:
            case BLAST_FURNACE:
            case SMOKER:
               switch (data.getProperty()) {
                  case 0:
                     this.setData(connection, 3, this.open.type == ContainerType.FURNACE ? 200 : 100);
                     this.setData(connection, 2, value);
                     return;
                  case 1:
                     this.setData(connection, 0, value);
                     return;
                  case 2:
                     this.setData(connection, 1, value);
                     return;
                  default:
                     return;
               }
            case BREWING_STAND:
               switch (data.getProperty()) {
                  case 0:
                     this.setData(connection, 2, 400);
                     this.setData(connection, 0, value);
                     break;
                  case 1:
                     this.setData(connection, 3, this.open.brewFuelTotal);
                     this.setData(connection, 1, value);
                     break;
                  case 2:
                     this.open.brewFuelTotal = value;
                     this.setData(connection, 3, value);
               }
         }
      }
   }

   private void setData(ClientPacketListener connection, int id, int value) {
      connection.handleContainerSetData(new ClientboundContainerSetDataPacket(this.open.id, id, value));
   }

   private Block block(BlockPos pos) {
      return this.minecraft.level == null ? Blocks.AIR : this.minecraft.level.getBlockState(pos).getBlock();
   }

   private void select(int hotbarSlot) {
      ClientPacketListener connection = this.minecraft.getConnection();
      if (connection != null && this.minecraft.player != null && Inventory.isHotbarSlot(hotbarSlot)) {
         connection.handleSetHeldSlot(new ClientboundSetHeldSlotPacket(hotbarSlot));
      }
   }

   private static int defaultSize(int container) {
      return switch (container) {
         case 0 -> 36;
         case 119 -> 1;
         case 120 -> 4;
         case 124 -> 54;
         default -> 1;
      };
   }

   public record HeldReport(MobEquipmentPacket equipment, boolean otherItem) {
   }

   private static final class OpenContainer {
      final int id;
      final ContainerType type;
      final BlockPos pos;
      final long entityId;
      ContainerLayout layout;
      int brewFuelTotal = 20;
      final int[] crafterData = new int[]{-1, -1, -1, -1, -1, -1, -1, -1, -1};

      OpenContainer(int id, ContainerType type, BlockPos pos, long entityId) {
         this.id = id;
         this.type = type;
         this.pos = pos;
         this.entityId = entityId;
      }
   }
}
