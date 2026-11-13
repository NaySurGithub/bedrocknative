package net.bedrocknative.client.model;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.PerfProbe;
import net.bedrocknative.model.entity.ClientEntityDef;
import net.bedrocknative.model.entity.ModelRegistry;
import net.bedrocknative.model.runtime.DrawList;
import net.bedrocknative.model.runtime.WornAttachable;
import net.bedrocknative.molang.MolangEnv;
import net.bedrocknative.molang.VariableStore;
import net.bedrocknative.world.item.BedrockItems;
import net.bedrocknative.world.item.CustomItem;
import net.bedrocknative.world.item.ItemMappings;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public final class WornAttachables {
   public static final RenderStateDataKey<WornAttachables.Worn> WORN = RenderStateDataKey.create(() -> "bedrocknative:worn_attachables");
   public static final RenderStateDataKey<WornAttachables.Frame> HELD = RenderStateDataKey.create(() -> "bedrocknative:held_attachable");
   private static final long SAME_FRAME_NANOS = 100000000L;
   private static final Map<LivingEntity, WornAttachables.Wearer> wearers = new WeakHashMap<>();
   private static final Set<EntityType<?>> heldLayerTypes = new HashSet<>();
   private static final MolangEnv env = new MolangEnv();
   private static final VariableStore scratch = new VariableStore(8);
   private static final WearerView view = new WearerView();
   private static boolean loggedFailure;

   private static void closeEffects(@Nullable WornAttachables.Running running) {
      if (running != null) {
         running.close();
      }
   }

   private WornAttachables() {
   }

   public static void addHeldLayerType(EntityType<?> type) {
      heldLayerTypes.add(type);
   }

   public static boolean drawsHeld(EntityType<?> type) {
      return heldLayerTypes.contains(type);
   }

   public static void clear() {
      for (WornAttachables.Wearer wearer : wearers.values()) {
         for (WornAttachables.Running running : wearer.thirdPerson) {
            closeEffects(running);
         }

         for (WornAttachables.Running running : wearer.firstPerson) {
            closeEffects(running);
         }
      }

      wearers.clear();
   }

   @Nullable
   public static WornAttachables.Worn extractArmor(LivingEntity entity, float partialTick) {
      ModelRegistry registry = BedrockModels.registry();
      if (registry != null && wearsCarrier(entity)) {
         WornAttachables.Wearer wearer = wearers.computeIfAbsent(entity, key -> new WornAttachables.Wearer());
         boolean any = false;

         for (WornAttachables.Slot slot : WornAttachables.Slot.ARMOR) {
            ItemStack stack = entity.getItemBySlot(slot.equipment());
            WornAttachables.Frame frame = fits(stack, slot) ? evaluate(registry, wearer, entity, slot, stack, false, partialTick) : null;
            wearer.worn.slots[slot.ordinal()] = frame;
            any |= frame != null;
         }

         return any ? wearer.worn : null;
      } else {
         return null;
      }
   }

   @Nullable
   public static WornAttachables.Frame extractHeld(
      LivingEntity entity, WornAttachables.Slot slot, ItemStack stack, boolean firstPerson, boolean rightArm, float partialTick
   ) {
      ModelRegistry registry = BedrockModels.registry();
      if (registry != null && stack.is(BedrockItems.CUSTOM)) {
         WornAttachables.Frame frame = evaluate(
            registry, wearers.computeIfAbsent(entity, key -> new WornAttachables.Wearer()), entity, slot, stack, firstPerson, partialTick
         );
         if (frame != null) {
            frame.rightArm = rightArm;
         }

         return frame;
      } else {
         return null;
      }
   }

   public static boolean drawsArmor(LivingEntity entity, ItemStack stack) {
      ModelRegistry registry = BedrockModels.registry();
      if (registry != null && stack.is(BedrockItems.CUSTOM) && fits(stack, WornAttachables.Slot.HEAD)) {
         WornAttachables.Wearer wearer = wearers.computeIfAbsent(entity, key -> new WornAttachables.Wearer());
         WornAttachables.Running running = running(registry, wearer, entity, WornAttachables.Slot.HEAD, stack, false);
         return running != null && running.attachable != null;
      } else {
         return false;
      }
   }

   @Nullable
   public static WornAttachables.Frame frame(LivingEntity entity, WornAttachables.Slot slot, boolean firstPerson) {
      WornAttachables.Wearer wearer = wearers.get(entity);
      WornAttachables.Running running = wearer == null ? null : (firstPerson ? wearer.firstPerson : wearer.thirdPerson)[slot.ordinal()];
      return running != null && running.attachable != null ? running.frame : null;
   }

   @Nullable
   public static String definition(LivingEntity entity, WornAttachables.Slot slot, boolean firstPerson) {
      WornAttachables.Wearer wearer = wearers.get(entity);
      WornAttachables.Running running = wearer == null ? null : (firstPerson ? wearer.firstPerson : wearer.thirdPerson)[slot.ordinal()];
      return running != null && running.attachable != null ? running.attachable.definition().identifier() : null;
   }

   private static boolean fits(ItemStack stack, WornAttachables.Slot slot) {
      CustomItem custom = ItemMappings.custom(stack);
      return custom == null || custom.wearSlot() == null || custom.wearSlot() == slot.equipment();
   }

   private static boolean wearsCarrier(LivingEntity entity) {
      for (WornAttachables.Slot slot : WornAttachables.Slot.ARMOR) {
         if (entity.getItemBySlot(slot.equipment()).is(BedrockItems.CUSTOM)) {
            return true;
         }
      }

      return false;
   }

   @Nullable
   private static WornAttachables.Frame evaluate(
      ModelRegistry registry,
      WornAttachables.Wearer wearer,
      LivingEntity entity,
      WornAttachables.Slot slot,
      ItemStack stack,
      boolean firstPerson,
      float partialTick
   ) {
      WornAttachables.Running running = running(registry, wearer, entity, slot, stack, firstPerson);
      if (running != null && running.attachable != null && !running.failed) {
         WornAttachables.Frame frame = running.frame;
         long counter = BedrockActors.frame();
         long now = System.nanoTime();
         if (counter == frame.frame && frame.last != null && now - frame.nanos < 100000000L) {
            return frame;
         } else {
            float deltaTime = frame.nanos == 0L ? 0.0F : Math.min((float)(now - frame.nanos) / 1.0E9F, 1.0F);
            frame.nanos = now;
            frame.frame = counter;
            long perf = PerfProbe.start();

            Object snapshot;
            try {
               view.prepare(entity, firstPerson, partialTick);
               env.effects = running.effects(entity);
               DrawList list = running.attachable.frame(env, view, stack.hasFoil(), deltaTime, partialTick);
               ActorSnapshot snapshotx = frame.snapshots[frame.next];
               frame.next ^= 1;
               snapshotx.fill(list, 1.0F);
               frame.last = snapshotx;
               PerfProbe.end(PerfProbe.Part.MODELS, perf);
               return frame;
            } catch (RuntimeException var22) {
               running.failed = true;
               frame.last = null;
               if (!loggedFailure) {
                  loggedFailure = true;
                  BedrockNative.LOGGER.error("The attachable of {} failed and is no longer drawn", running.itemId, var22);
               }

               snapshot = null;
            } finally {
               env.effects = null;
               view.detach();
            }

            return (WornAttachables.Frame)snapshot;
         }
      } else {
         return null;
      }
   }

   @Nullable
   private static WornAttachables.Running running(
      ModelRegistry registry, WornAttachables.Wearer wearer, LivingEntity entity, WornAttachables.Slot slot, ItemStack stack, boolean firstPerson
   ) {
      WornAttachables.Running[] slots = firstPerson ? wearer.firstPerson : wearer.thirdPerson;
      CustomItem custom = ItemMappings.custom(stack);
      if (custom == null) {
         closeEffects(slots[slot.ordinal()]);
         slots[slot.ordinal()] = null;
         return null;
      } else {
         WornAttachables.Running running = slots[slot.ordinal()];
         if (running != null && running.registry == registry && running.itemId.equals(custom.identifier())) {
            return running;
         } else {
            closeEffects(running);
            WornAttachable attachable = null;
            if (allows(registry, entity, slot)) {
               try {
                  view.prepare(entity, firstPerson, 0.0F);
                  ClientEntityDef definition = WornAttachable.resolve(registry, custom.identifier(), slot.bedrock(), firstPerson, view, env, scratch);
                  if (definition != null) {
                     attachable = new WornAttachable(registry, definition, custom.identifier(), slot.bedrock(), firstPerson);
                  }
               } catch (RuntimeException var14) {
                  BedrockNative.LOGGER.warn("Couldn't set up the attachable of {}", custom.identifier(), var14);
               } finally {
                  view.detach();
               }
            }

            running = new WornAttachables.Running(custom.identifier(), registry, attachable, slot, firstPerson);
            slots[slot.ordinal()] = running;
            return running;
         }
      }
   }

   private static boolean allows(ModelRegistry registry, LivingEntity entity, WornAttachables.Slot slot) {
      ActorState state = BedrockActors.state(entity);
      ClientEntityDef definition = registry.entity(WearerView.identifier(entity, state != null ? state.data() : null));
      return definition == null ? true : definition.enableAttachables() && (!slot.armor() || !definition.hideArmor());
   }

   public static final class Frame {
      private final ActorSnapshot[] snapshots = new ActorSnapshot[]{new ActorSnapshot(), new ActorSnapshot()};
      @Nullable
      private ActorSnapshot last;
      private int next;
      private long frame = -1L;
      private long nanos;
      private final WornAttachables.Slot slot;
      private final boolean firstPerson;
      private boolean rightArm = true;

      Frame(WornAttachables.Slot slot, boolean firstPerson) {
         this.slot = slot;
         this.firstPerson = firstPerson;
      }

      @Nullable
      public ActorSnapshot snapshot() {
         return this.last;
      }

      public WornAttachables.Slot slot() {
         return this.slot;
      }

      public boolean firstPerson() {
         return this.firstPerson;
      }

      public boolean rightArm() {
         return this.rightArm;
      }
   }

   private static final class Running {
      final String itemId;
      final ModelRegistry registry;
      @Nullable
      final WornAttachable attachable;
      final WornAttachables.Frame frame;
      boolean failed;
      @Nullable
      private ActorEffects effects;

      Running(String itemId, ModelRegistry registry, @Nullable WornAttachable attachable, WornAttachables.Slot slot, boolean firstPerson) {
         this.itemId = itemId;
         this.registry = registry;
         this.attachable = attachable;
         this.frame = new WornAttachables.Frame(slot, firstPerson);
      }

      @Nullable
      ActorEffects effects(LivingEntity wearer) {
         if (this.effects == null && this.attachable != null) {
            this.effects = new ActorEffects(this.attachable.definition(), wearer);
         }

         return this.effects;
      }

      void close() {
         if (this.effects != null) {
            this.effects.close();
            this.effects = null;
         }
      }
   }

   public static enum Slot {
      HEAD("head", EquipmentSlot.HEAD),
      CHEST("chest", EquipmentSlot.CHEST),
      LEGS("legs", EquipmentSlot.LEGS),
      FEET("feet", EquipmentSlot.FEET),
      MAIN_HAND("main_hand", EquipmentSlot.MAINHAND),
      OFF_HAND("off_hand", EquipmentSlot.OFFHAND);

      static final WornAttachables.Slot[] ARMOR = new WornAttachables.Slot[]{HEAD, CHEST, LEGS, FEET};
      private final String bedrock;
      private final EquipmentSlot equipment;

      private Slot(String bedrock, EquipmentSlot equipment) {
         this.bedrock = bedrock;
         this.equipment = equipment;
      }

      public String bedrock() {
         return this.bedrock;
      }

      public EquipmentSlot equipment() {
         return this.equipment;
      }

      public boolean armor() {
         return this != MAIN_HAND && this != OFF_HAND;
      }

      @Nullable
      public static WornAttachables.Slot of(EquipmentSlot slot) {
         for (WornAttachables.Slot value : values()) {
            if (value.equipment == slot) {
               return value;
            }
         }

         return null;
      }
   }

   private static final class Wearer {
      final WornAttachables.Running[] thirdPerson = new WornAttachables.Running[WornAttachables.Slot.values().length];
      final WornAttachables.Running[] firstPerson = new WornAttachables.Running[WornAttachables.Slot.values().length];
      final WornAttachables.Worn worn = new WornAttachables.Worn();
   }

   public static final class Worn {
      private final WornAttachables.Frame[] slots = new WornAttachables.Frame[WornAttachables.Slot.values().length];

      @Nullable
      public WornAttachables.Frame slot(WornAttachables.Slot slot) {
         return this.slots[slot.ordinal()];
      }

      boolean any() {
         for (WornAttachables.Frame frame : this.slots) {
            if (frame != null) {
               return true;
            }
         }

         return false;
      }
   }
}
