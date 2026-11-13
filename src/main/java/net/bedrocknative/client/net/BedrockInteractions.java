package net.bedrocknative.client.net;

import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.bedrocknative.client.inventory.BedrockInventory;
import net.bedrocknative.client.inventory.BedrockSlot;
import net.bedrocknative.client.movement.BedrockMovement;
import net.bedrocknative.client.rules.BedrockInputLocks;
import net.bedrocknative.client.world.BedrockBlocks;
import net.bedrocknative.client.world.BedrockEntities;
import net.bedrocknative.net.client.CombatPackets;
import net.bedrocknative.net.client.DropPackets;
import net.bedrocknative.world.block.BlockMappings;
import net.bedrocknative.world.block.BlockUseRules;
import net.bedrocknative.world.block.custom.CarrierBlock;
import net.bedrocknative.world.item.BedrockItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler.ServerVerifiedState;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundPickItemFromBlockPacket;
import net.minecraft.network.protocol.game.ServerboundPickItemFromEntityPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPunchPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ItemSteerable;
import net.minecraft.world.entity.PlayerRideable;
import net.minecraft.world.entity.LivingEntity.SwingDescription;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.nautilus.AbstractNautilus;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileItem;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.protocol.bedrock.data.PlayerActionType;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.data.PlayerBlockActionData;
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityEventType;
import org.cloudburstmc.protocol.bedrock.data.inventory.HandSlot;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryActionData;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventorySource;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryTransactionType;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.LegacySetItemSlotData;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.ItemUseTransaction.PredictedResult;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.ItemUseTransaction.TriggerType;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.BlockPickRequestPacket;
import org.cloudburstmc.protocol.bedrock.packet.EntityEventPacket;
import org.cloudburstmc.protocol.bedrock.packet.EntityPickRequestPacket;
import org.cloudburstmc.protocol.bedrock.packet.InteractPacket;
import org.cloudburstmc.protocol.bedrock.packet.InventoryTransactionPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerActionPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;
import org.cloudburstmc.protocol.bedrock.packet.AnimatePacket.SwingSource;
import org.cloudburstmc.protocol.bedrock.packet.InteractPacket.Action;
import org.jspecify.annotations.Nullable;

final class BedrockInteractions {
   private static final int ACK_DELAY_TICKS = 10;
   private static final int USE_CLICK_BLOCK = 0;
   private static final int USE_CLICK_AIR = 1;
   private static final int RELEASE = 0;
   private static final int NO_FACE = -1;
   private static final int LEGACY_INVENTORY_CONTAINER = 29;
   private static final int PICK_HOTBAR_SLOT = 9;
   private static final int FIRST_EATING_EVENT_TICK = 8;
   private static int legacyRequestId = -2;
   static volatile String lastPick = "";
   static volatile String lastDrop = "";
   private static final int EATING_EVENT_INTERVAL = 4;
   private final Minecraft minecraft;
   private final BedrockInventory inventory;
   private final BedrockEntities entities;
   private final Consumer<BedrockPacket> send;
   private final List<PlayerBlockActionData> blockActions = new ArrayList<>();
   private final ArrayDeque<int[]> unacknowledged = new ArrayDeque<>();
   private long runtimeId;
   private int tick;
   private boolean missedSwing;
   private boolean usingItem;
   private boolean swingSent;
   private boolean entityInteracted;
   private boolean interactSwingDue;
   private SwingSource swingSource = SwingSource.NONE;
   private SwingDescription lastSwing;
   private BlockPos breaking;
   private BlockPos destroyed;
   private long destroyedAt;
   private BlockPos used = BlockPos.ZERO;
   private BlockPos usedResult = BlockPos.ZERO;
   private long usedAt;
   private boolean usedByJava;
   private boolean placedByJava;
   private long hovered;
   @Nullable
   private InventoryTransactionPacket pendingUse;
   private boolean pendingRepeat;
   private boolean pendingBlockTook;
   private int lastTicksUsing;
   private SwingSource useSwing = SwingSource.BUILD;
   @Nullable
   private BlockPos usingOn;
   private boolean creativeAbortPending;
   @Nullable
   private BlockPos punchAbort;
   @Nullable
   private Vector3f hoveredAt;
   private static final BlockDefinition AIR_CLICK = new BedrockInteractions.HashedBlock(0);

   BedrockInteractions(Minecraft minecraft, BedrockInventory inventory, BedrockEntities entities, Consumer<BedrockPacket> send) {
      this.minecraft = minecraft;
      this.inventory = inventory;
      this.entities = entities;
      this.send = send;
   }

   void start(long runtimeId) {
      this.runtimeId = runtimeId;
      this.blockActions.clear();
      this.unacknowledged.clear();
      this.breaking = null;
      this.hovered = 0L;
      this.hoveredAt = null;
      this.pendingUse = null;
      this.usingOn = null;
      this.creativeAbortPending = false;
      this.punchAbort = null;
      this.usingItem = false;
      this.lastTicksUsing = 0;
      this.entityInteracted = false;
      this.interactSwingDue = false;
   }

   BlockPos breaking() {
      return this.breaking;
   }

   boolean breaksLocally(BlockPos pos) {
      return pos.equals(this.breaking) || pos.equals(this.destroyed) && System.currentTimeMillis() - this.destroyedAt < 2000L;
   }

   boolean usedLocally(BlockPos pos) {
      return System.currentTimeMillis() - this.usedAt < 1000L && (this.usedByJava && pos.equals(this.used) || this.placedByJava && pos.equals(this.usedResult));
   }

   boolean onJavaPacket(Packet<?> packet) {
      LocalPlayer player = this.minecraft.player;
      if (player != null && this.minecraft.level != null) {
         switch (packet) {
            case ServerboundPlayerActionPacket action:
               if (!this.onPlayerAction(player, action)) {
                  return false;
               }
               break;
            case ServerboundUseItemOnPacket use:
               if (use.hand() == InteractionHand.MAIN_HAND) {
                  this.useItemOn(player, use.hitResult());
               }

               this.acknowledgeLater(use.sequence());
               break;
            case ServerboundUseItemPacket usex:
               if (usex.hand() == InteractionHand.MAIN_HAND) {
                  this.useItem(player);
               }

               this.acknowledgeLater(usex.sequence());
               break;
            case ServerboundAttackPacket attack:
               this.swing(SwingSource.ATTACK);
               if (this.entityTransaction(player, attack.entityId(), 1)) {
                  this.swing(SwingSource.ATTACK);
                  CombatTrace.attacked();
               }
               break;
            case ServerboundInteractPacket interact:
               if (interact.hand() == InteractionHand.MAIN_HAND) {
                  this.swingSource = SwingSource.INTERACT;
                  this.entityInteracted = (!BedrockInputLocks.mountLocked() || !isRideable(this.minecraft.level.getEntity(interact.entityId())))
                     && this.entityTransaction(player, interact.entityId(), 0);
               }
               break;
            case ServerboundPickItemFromBlockPacket pick:
               this.pickBlock(player, pick);
               break;
            case ServerboundPickItemFromEntityPacket pickx:
               this.pickEntity(player, pickx);
               break;
            case ServerboundPunchPacket ignored:
               HitResult hit = this.minecraft.hitResult;
               if (hit == null || hit.getType() == Type.MISS) {
                  this.missedSwing = true;
               }

               if (!this.swingSent) {
                  this.swingSource = hit instanceof BlockHitResult ? SwingSource.MINE : SwingSource.ATTACK;
               }
               break;
            default:
               return false;
         }

         return true;
      } else {
         return false;
      }
   }

   private static boolean isRideable(@Nullable Entity entity) {
      return entity instanceof AbstractBoat
         || entity instanceof AbstractMinecart
         || entity instanceof AbstractHorse
         || entity instanceof Camel
         || entity instanceof AbstractNautilus
         || entity instanceof ItemSteerable
         || entity instanceof PlayerRideable;
   }

   private void pickBlock(LocalPlayer player, ServerboundPickItemFromBlockPacket pick) {
      BlockPickRequestPacket request = new BlockPickRequestPacket();
      request.setBlockPosition(toBedrock(pick.pos()));
      request.setAddUserData(pick.includeData() && player.getAbilities().instabuild);
      request.setHotbarSlot(9);
      lastPick = request.toString();
      this.send.accept(request);
   }

   private void pickEntity(LocalPlayer player, ServerboundPickItemFromEntityPacket pick) {
      Entity entity = this.minecraft.level.getEntity(pick.id());
      long target = entity == null ? -1L : this.entities.uniqueIdOf(entity);
      if (player.getAbilities().instabuild && target != -1L) {
         EntityPickRequestPacket request = new EntityPickRequestPacket();
         request.setRuntimeEntityId(target);
         request.setHotbarSlot(9);
         request.setWithData(pick.includeData());
         lastPick = request.toString();
         this.send.accept(request);
      }
   }

   private void destroyed(BlockPos pos) {
      this.destroyed = pos;
      this.destroyedAt = System.currentTimeMillis();
      BedrockBlocks.noteLocalBreak(pos);
   }

   private boolean onPlayerAction(LocalPlayer player, ServerboundPlayerActionPacket action) {
      BlockPos pos = action.getPos();
      int face = action.getDirection().get3DDataValue();
      switch (action.getAction()) {
         case START_DESTROY_BLOCK:
            this.blockAction(PlayerActionType.START_BREAK, pos, face);
            BlockState now = this.minecraft.level.getBlockState(pos);
            if (player.getAbilities().instabuild) {
               this.creativeDestroy(pos, face);
            }

            if (!player.getAbilities().instabuild && !now.isAir() && !(now.getBlock() instanceof LiquidBlock)) {
               this.breaking = pos;
            } else {
               this.blockAction(PlayerActionType.BLOCK_PREDICT_DESTROY, pos, face);
               this.destroyed(pos);
               this.breaking = null;
            }

            this.acknowledgeLater(action.getSequence());
            break;
         case ABORT_DESTROY_BLOCK:
            this.blockAction(PlayerActionType.ABORT_BREAK, pos, 0);
            this.breaking = null;
            break;
         case STOP_DESTROY_BLOCK:
            this.blockAction(PlayerActionType.BLOCK_CONTINUE_DESTROY, pos, face);
            this.blockAction(PlayerActionType.BLOCK_PREDICT_DESTROY, pos, face);
            this.destroyed(pos);
            this.breaking = null;
            this.acknowledgeLater(action.getSequence());
         case CHANGE_DESTROY_DIRECTION:
            break;
         case STAB:
            this.stab(player);
            break;
         case DROP_ITEM:
         case DROP_ALL_ITEMS:
            this.drop(player);
            break;
         case RELEASE_USE_ITEM:
            InventoryTransactionPacket release = new InventoryTransactionPacket();
            release.setTransactionType(InventoryTransactionType.ITEM_RELEASE);
            release.setActionType(0);
            release.setHotbarSlot(this.inventory.selectedSlot());
            release.setHand(handOf(player.getUsedItemHand()));
            release.setItemInHand(BedrockInventory.withoutStackId(this.inventory.held()));
            release.setHeadPosition(eyePosition(player));
            this.predictShot(player, release);
            this.sendTransaction(release);
            break;
         default:
            return false;
      }

      return true;
   }

   private void predictShot(LocalPlayer player, InventoryTransactionPacket release) {
      ItemStack bow = player.getUseItem();
      if (bow.getItem() instanceof BowItem
         && !player.hasInfiniteMaterials()
         && player.getUsedItemHand() == InteractionHand.MAIN_HAND
         && !(BowItem.getPowerForTime(bow.getUseDuration(player) - player.getUseItemRemainingTicks()) < 0.1F)) {
         ItemStack arrow = player.getProjectile(bow);
         if (!arrow.isEmpty()) {
            boolean infinite = arrow.is(Items.ARROW) && bow.getEnchantments().keySet().stream().anyMatch(enchantment -> enchantment.is(Enchantments.INFINITY));
            int arrowSlot = player.getInventory().getNonEquipmentItems().indexOf(arrow);
            BedrockSlot slot = arrowSlot >= 0 ? BedrockSlot.player(arrowSlot) : (player.getOffhandItem() == arrow ? BedrockSlot.offhand() : null);
            if (slot != null && !infinite) {
               ItemData before = this.inventory.get(slot);
               ItemData after = before.getCount() > 1 ? before.toBuilder().count(before.getCount() - 1).build() : ItemData.AIR;
               release.getActions()
                  .add(
                     new InventoryActionData(
                        InventorySource.fromContainerWindowId(slot.container()),
                        slot.slot(),
                        BedrockInventory.withoutStackId(before),
                        BedrockInventory.withoutStackId(after)
                     )
                  );
               this.inventory.setAndShow(slot, after);
            }

            if (bow.isDamageableItem()) {
               ItemData before = this.inventory.held();
               NbtMap tag = before.getTag();
               NbtMap worn = (tag == null ? NbtMap.builder() : tag.toBuilder()).putInt("Damage", (tag == null ? 0 : tag.getInt("Damage")) + 1).build();
               release.getActions()
                  .add(
                     new InventoryActionData(
                        InventorySource.fromContainerWindowId(0),
                        this.inventory.selectedSlot(),
                        BedrockInventory.withoutStackId(before),
                        BedrockInventory.withoutStackId(before.toBuilder().tag(worn).build())
                     )
                  );
               this.inventory.setAndShow(BedrockSlot.player(this.inventory.selectedSlot()), before.toBuilder().tag(worn).build());
            }
         }
      }
   }

   private void creativeDestroy(BlockPos pos, int face) {
      this.swing(SwingSource.MINE);
      this.playerAction(PlayerActionType.DIMENSION_CHANGE_REQUEST_OR_CREATIVE_DESTROY_BLOCK, pos, BlockPos.ZERO, face);
      this.swing(SwingSource.MINE);
      this.creativeAbortPending = true;
   }

   void punchBlock(BlockPos pos) {
      LocalPlayer player = this.minecraft.player;
      if (player != null) {
         int face = Direction.UP.get3DDataValue();
         this.swing(SwingSource.MINE);
         this.blockAction(PlayerActionType.START_BREAK, pos, face);
         if (player.getAbilities().instabuild) {
            this.playerAction(PlayerActionType.DIMENSION_CHANGE_REQUEST_OR_CREATIVE_DESTROY_BLOCK, pos, BlockPos.ZERO, face);
            this.swing(SwingSource.MINE);
         }

         this.punchAbort = pos;
      }
   }

   private void drop(LocalPlayer player) {
      int slot = this.inventory.selectedSlot();
      ItemData before = this.inventory.held();
      if (!player.isSpectator() && !DropPackets.locked(before)) {
         ItemData after = this.inventory.predictedHeld();
         InventoryTransactionPacket drop = DropPackets.hotbarDrop(slot, before, after);
         if (drop != null) {
            this.inventory.predict(slot, after);
            this.swingSource = SwingSource.DROP_ITEM;
            lastDrop = drop.toString();
            this.sendTransaction(drop);
         }
      } else {
         if (!before.isNull()) {
            this.inventory.setAndShow(BedrockSlot.player(slot), before);
         }
      }
   }

   private void useItemOn(LocalPlayer player, BlockHitResult hit) {
      BlockPos pos = hit.getBlockPos();
      BlockPos result = hit.isInside() ? pos : pos.relative(hit.getDirection());
      int face = hit.getDirection().get3DDataValue();
      Vec3 click = hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
      this.used = pos;
      this.usedResult = result;
      this.usedAt = System.currentTimeMillis();
      this.usedByJava = !(this.minecraft.level.getBlockState(pos).getBlock() instanceof CarrierBlock);
      BlockState placed = this.minecraft.level.getBlockState(result);
      this.placedByJava = !placed.isAir() && !(placed.getBlock() instanceof CarrierBlock) && !player.getMainHandItem().is(BedrockItems.CUSTOM);
      this.flushUse(false);
      boolean repeat = this.usingOn != null && this.minecraft.options.keyUse.isDown();
      if (!repeat) {
         this.stopUseOn();
         this.playerAction(PlayerActionType.START_ITEM_USE_ON, pos, result, face);
      }

      InventoryTransactionPacket use = this.itemUse(player, 0);
      use.setTriggerType(repeat ? TriggerType.SIMULATION_TICK : TriggerType.PLAYER_INPUT);
      use.setBlockPosition(toBedrock(pos));
      use.setBlockFace(face);
      use.setClickPosition(Vector3f.from(click.x, click.y, click.z));
      use.setBlockDefinition(new BedrockInteractions.HashedBlock(BlockMappings.get().toBedrock(this.serverState(pos))));
      boolean blockTook = BedrockBlockUse.blockTook();
      if (blockTook) {
         BedrockBlocks.noteLocalBreak(pos);
      }

      this.useSwing = !blockTook && player.getMainHandItem().getItem() instanceof BlockItem ? SwingSource.BUILD : SwingSource.INTERACT;
      this.pendingUse = use;
      this.pendingRepeat = repeat;
      this.pendingBlockTook = blockTook;
      this.usingOn = result;
   }

   private void flushUse(boolean swung) {
      InventoryTransactionPacket use = this.pendingUse;
      if (use != null) {
         this.pendingUse = null;
         if (swung && !this.swingSent) {
            this.swing(this.useSwing);
         }

         use.setClientInteractPrediction(swung ? PredictedResult.SUCCESS : PredictedResult.FAILURE);
         this.sendTransaction(use);
         if (BlockUseRules.clickInTheAirFollows(swung, this.pendingRepeat, this.pendingBlockTook, !this.inventory.held().isNull())) {
            this.sendTransaction(this.clickAir(this.minecraft.player));
         }
      }
   }

   private void stopUseOn() {
      if (this.usingOn != null) {
         this.playerAction(PlayerActionType.STOP_ITEM_USE_ON, this.usingOn, BlockPos.ZERO, 0);
         this.usingOn = null;
      }
   }

   private void useItem(LocalPlayer player) {
      ItemStack held = player.getMainHandItem();
      if (player.isFallFlying() && held.is(Items.FIREWORK_ROCKET)) {
         Fireworks fireworks = (Fireworks)held.get(DataComponents.FIREWORKS);
         BedrockMovement.startGlideBoost(20 * ((fireworks == null ? 1 : fireworks.flightDuration()) + 1) + 6);
      }

      this.flushUse(false);
      this.swingSource = !(held.getItem() instanceof ProjectileItem) && !held.is(Items.ENDER_PEARL) ? SwingSource.USE_ITEM : SwingSource.THROW_ITEM;
      this.sendTransaction(this.clickAir(player));
   }

   private void stab(LocalPlayer player) {
      this.flushUse(false);
      InventoryTransactionPacket stab = this.clickAir(player);
      stab.setActionType(3);
      this.swingSource = SwingSource.ATTACK;
      this.sendTransaction(stab);
      CombatTrace.STABS.incrementAndGet();
   }

   private InventoryTransactionPacket clickAir(LocalPlayer player) {
      InventoryTransactionPacket use = this.itemUse(player, 1);
      use.setTriggerType(TriggerType.UNKNOWN);
      use.setClientInteractPrediction(PredictedResult.FAILURE);
      use.setBlockPosition(Vector3i.ZERO);
      use.setBlockFace(-1);
      use.setClickPosition(Vector3f.ZERO);
      use.setBlockDefinition(AIR_CLICK);
      return use;
   }

   private InventoryTransactionPacket itemUse(LocalPlayer player, int actionType) {
      int slot = this.inventory.selectedSlot();
      ItemData before = this.inventory.held();
      ItemData after = this.inventory.predictedHeld();
      InventoryTransactionPacket use = new InventoryTransactionPacket();
      use.setTransactionType(InventoryTransactionType.ITEM_USE);
      use.setActionType(actionType);
      use.setHotbarSlot(slot);
      use.setHand(HandSlot.MAINHAND);
      use.setItemInHand(BedrockInventory.withoutStackId(before));
      use.setPlayerPosition(eyePosition(player));
      if (after.getCount() != before.getCount() || after.isNull() != before.isNull()) {
         use.getActions()
            .add(
               new InventoryActionData(
                  InventorySource.fromContainerWindowId(0), slot, BedrockInventory.withoutStackId(before), BedrockInventory.withoutStackId(after)
               )
            );
         this.inventory.predict(slot, after);
      }

      return use;
   }

   private void playerAction(PlayerActionType type, BlockPos pos, BlockPos result, int face) {
      PlayerActionPacket action = new PlayerActionPacket();
      action.setRuntimeEntityId(this.runtimeId);
      action.setAction(type);
      action.setBlockPosition(toBedrock(pos));
      action.setResultPosition(toBedrock(result));
      action.setFace(face);
      this.send.accept(action);
   }

   private BlockState serverState(BlockPos pos) {
      ServerVerifiedState known = (ServerVerifiedState)this.minecraft.level.getBlockStatePredictionHandler().serverVerifiedStates.get(pos.asLong());
      return known != null ? known.blockState : this.minecraft.level.getBlockState(pos);
   }

   private boolean entityTransaction(LocalPlayer player, int javaId, int actionType) {
      long target = this.entities.runtimeIdOf(javaId);
      if (target == -1L) {
         return false;
      } else {
         Entity entity = this.minecraft.level.getEntity(javaId);
         Vec3 click = this.minecraft.hitResult instanceof EntityHitResult hit && hit.getEntity().getId() == javaId
            ? hit.getLocation()
            : (entity != null ? entity.position() : Vec3.ZERO);
         InventoryTransactionPacket use = CombatPackets.onEntity(
            actionType,
            target,
            this.inventory.selectedSlot(),
            BedrockInventory.withoutStackId(this.inventory.held()),
            eyePosition(player),
            Vector3f.from(click.x, click.y, click.z)
         );
         if (actionType == 1) {
            CombatTrace.lastAttack = use.toString();
         }

         this.sendTransaction(use);
         return true;
      }
   }

   private void sendTransaction(InventoryTransactionPacket transaction) {
      legacyRequestId -= 2;
      ByteArrayList slots = new ByteArrayList();

      for (InventoryActionData action : transaction.getActions()) {
         InventorySource source = action.getSource();
         if (source.getType() == org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventorySource.Type.CONTAINER
            && source.getContainerId() == 0
            && !action.getToItem().isNull()) {
            slots.add((byte)action.getSlot());
         }
      }

      if (!slots.isEmpty()) {
         transaction.setLegacyRequestId(legacyRequestId);
         transaction.getLegacySlots().add(new LegacySetItemSlotData(29, slots.toByteArray()));
      }

      this.send.accept(transaction);
   }

   void onTickEnd(PlayerAuthInputPacket input) {
      this.tick++;
      LocalPlayer player = this.minecraft.player;
      SwingDescription swing = player.getCurrentSwing();
      this.flushUse(swing != null && swing != this.lastSwing);
      if (this.usingOn != null && !this.minecraft.options.keyUse.isDown()) {
         this.stopUseOn();
      }

      if (this.creativeAbortPending && !this.minecraft.options.keyAttack.isDown()) {
         this.creativeAbortPending = false;
         this.blockAction(
            PlayerActionType.ABORT_BREAK,
            this.minecraft.hitResult instanceof BlockHitResult hit && hit.getType() == Type.BLOCK
               ? hit.getBlockPos()
               : (this.destroyed != null ? this.destroyed : player.blockPosition()),
            0
         );
      }

      if (this.punchAbort != null && !this.blockActions.stream().anyMatch(action -> action.getAction() == PlayerActionType.START_BREAK)) {
         this.blockAction(PlayerActionType.ABORT_BREAK, this.punchAbort, 0);
         this.punchAbort = null;
      }

      if (!this.blockActions.isEmpty()) {
         input.getInputData().add(PlayerAuthInputData.PERFORM_BLOCK_ACTIONS);
         input.getPlayerActions().addAll(this.blockActions);
         this.blockActions.clear();
      }

      if (this.missedSwing) {
         input.getInputData().add(PlayerAuthInputData.MISSED_SWING);
         this.missedSwing = false;
         CombatTrace.MISSED_SWINGS.incrementAndGet();
      }

      if (player.isUsingItem() && (!this.usingItem || player.getTicksUsingItem() < this.lastTicksUsing)) {
         input.getInputData().add(PlayerAuthInputData.START_USING_ITEM);
      }

      this.reportEating(player);
      completeConsumption(player);
      this.usingItem = player.isUsingItem();
      this.lastTicksUsing = player.getTicksUsingItem();
      if (this.interactSwingDue) {
         this.interactSwingDue = false;
         this.swing(SwingSource.INTERACT);
      }

      if (swing != null && swing != this.lastSwing && !this.swingSent) {
         if (this.entityInteracted && this.swingSource == SwingSource.INTERACT) {
            this.interactSwingDue = true;
         } else {
            this.swing(this.breaking != null ? SwingSource.MINE : (this.swingSource == SwingSource.NONE ? SwingSource.ATTACK : this.swingSource));
         }
      }

      this.entityInteracted = false;
      this.lastSwing = swing;
      this.swingSent = false;
      this.swingSource = SwingSource.NONE;
      this.updateHovered();
      this.acknowledgePredictions();
      CombatTrace.input(input);
   }

   private void reportEating(LocalPlayer player) {
      if (player.isUsingItem() && player.getUseItem().getUseAnimation() == ItemUseAnimation.EAT) {
         int used = player.getTicksUsingItem();
         ItemData held = this.inventory.held();
         if (used >= 8 && (used - 8) % 4 == 0 && !held.isNull()) {
            EntityEventPacket eating = new EntityEventPacket();
            eating.setRuntimeEntityId(this.runtimeId);
            eating.setType(EntityEventType.EATING_ITEM);
            eating.setData(held.getDefinition().getRuntimeId() << 16 | held.getDamage() & 65535);
            this.send.accept(eating);
         }
      }
   }

   private static void completeConsumption(LocalPlayer player) {
      if (player.isUsingItem() && player.getUseItemRemainingTicks() <= 0 && consumes(player.getUseItem())) {
         player.stopUsingItem();
      }
   }

   private static boolean consumes(ItemStack stack) {
      ItemUseAnimation animation = stack.getUseAnimation();
      return animation == ItemUseAnimation.EAT || animation == ItemUseAnimation.DRINK;
   }

   void serverCompletedUse() {
      LocalPlayer player = this.minecraft.player;
      if (player != null && player.isUsingItem() && consumes(player.getUseItem())) {
         player.stopUsingItem();
         this.minecraft.rightClickDelay = 5;
      }
   }

   private void swing(SwingSource source) {
      this.send.accept(CombatPackets.swing(this.runtimeId, source));
      this.swingSent = true;
      if (source == SwingSource.ATTACK) {
         CombatTrace.ATTACK_SWINGS.incrementAndGet();
      }
   }

   private void updateHovered() {
      long target = this.minecraft.hitResult instanceof EntityHitResult hit ? this.entities.runtimeIdOf(hit.getEntity().getId()) : 0L;
      if (target != -1L && target != this.hovered) {
         this.hovered = target;
         Vec3 at = this.minecraft.hitResult.getLocation();
         this.hoveredAt = target == 0L ? null : Vector3f.from(at.x, at.y, at.z);
         this.resendHover();
      }
   }

   void resendHover() {
      InteractPacket mouseover = new InteractPacket();
      mouseover.setAction(Action.MOUSEOVER);
      mouseover.setRuntimeEntityId(this.hovered);
      mouseover.setMousePosition(this.hoveredAt);
      this.send.accept(mouseover);
   }

   private void blockAction(PlayerActionType type, BlockPos pos, int face) {
      PlayerBlockActionData action = new PlayerBlockActionData();
      action.setAction(type);
      action.setBlockPosition(toBedrock(pos));
      action.setFace(face);
      this.blockActions.add(action);
   }

   private void acknowledgeLater(int sequence) {
      if (sequence > 0) {
         this.unacknowledged.add(new int[]{sequence, this.tick});
      }
   }

   private void acknowledgePredictions() {
      boolean answered = BedrockBlocks.takeServerAnswer();
      int sequence = -1;

      while (!this.unacknowledged.isEmpty() && (answered || ((int[])this.unacknowledged.peek())[1] + 10 <= this.tick)) {
         sequence = this.unacknowledged.poll()[0];
      }

      if (sequence > 0 && this.minecraft.level != null) {
         this.minecraft.level.handleBlockChangedAck(sequence);
      }
   }

   private static Vector3i toBedrock(BlockPos pos) {
      return Vector3i.from(pos.getX(), pos.getY(), pos.getZ());
   }

   private static HandSlot handOf(InteractionHand hand) {
      return hand == InteractionHand.OFF_HAND ? HandSlot.OFFHAND : HandSlot.MAINHAND;
   }

   private static Vector3f eyePosition(LocalPlayer player) {
      return Vector3f.from(player.getX(), player.getY() + 1.62001F, player.getZ());
   }

   private record HashedBlock(int getRuntimeId) implements BlockDefinition {
   }
}
