package net.bedrocknative.model.runtime;

import java.util.Arrays;
import net.bedrocknative.model.entity.ClientEntityDef;
import net.bedrocknative.model.entity.ModelRegistry;
import net.bedrocknative.molang.MolangActor;
import net.bedrocknative.molang.MolangEnv;
import net.bedrocknative.molang.MolangNames;
import net.bedrocknative.molang.MolangProgram;
import net.bedrocknative.molang.VariableStore;
import org.jspecify.annotations.Nullable;

public final class RuntimeEquipment {
   public static final String[] SLOTS = new String[]{"main_hand", "off_hand", "head", "chest", "legs", "feet", "body"};
   public static final int MAIN_HAND = 0;
   public static final int OFF_HAND = 1;
   private static final int ITEM_SLOT = MolangNames.id("item_slot");
   private static final int IS_FIRST_PERSON = MolangNames.id("is_first_person");
   private static final int OWNING_ENTITY = MolangNames.id("owning_entity");
   private static final int IS_ENCHANTED = MolangNames.id("is_enchanted");
   private static final int HAS_TRIM = MolangNames.id("has_trim");
   private final ModelRegistry registry;
   private final ActorInstance wearer;
   private final String[] items = new String[SLOTS.length];
   private final @Nullable ActorInstance[] attached = new ActorInstance[SLOTS.length];
   private final VariableStore scratch = new VariableStore(8);

   public RuntimeEquipment(ModelRegistry registry, ActorInstance wearer) {
      this.registry = registry;
      this.wearer = wearer;
      Arrays.fill(this.items, "");
   }

   public boolean update(int slot, String itemId, boolean enchanted, int color, MolangActor owner, MolangEnv env) {
      if (!itemId.equals(this.items[slot])) {
         this.items[slot] = itemId;
         ActorInstance previous = this.attached[slot];
         if (previous != null) {
            this.wearer.detach(previous);
            this.attached[slot] = null;
         }

         ClientEntityDef definition = !itemId.isEmpty() && this.allows(slot)
            ? WornAttachable.resolve(this.registry, itemId, SLOTS[slot], false, owner, env, this.scratch)
            : null;
         if (definition != null) {
            ActorInstance instance = this.wearer.attach(definition);
            instance.context().set(ITEM_SLOT, 0.0F, SLOTS[slot]);
            instance.context().set(IS_FIRST_PERSON, 0.0F, null);
            this.attached[slot] = instance;
         }
      }

      ActorInstance instance = this.attached[slot];
      if (instance == null) {
         return false;
      }

      instance.context().set(OWNING_ENTITY, 0.0F, owner);
      instance.variables().set(IS_ENCHANTED, enchanted ? 1.0F : 0.0F, null);
      instance.variables().set(HAS_TRIM, 0.0F, null);
      instance.setChangeColor(color);
      return true;
   }

   public boolean attached(int slot) {
      return this.attached[slot] != null;
   }

   public @Nullable String definition(int slot) {
      ActorInstance instance = this.attached[slot];
      return instance != null ? instance.definition().identifier() : null;
   }

   private boolean allows(int slot) {
      ClientEntityDef definition = this.wearer.definition();
      if (!definition.enableAttachables()) {
         return false;
      }

      if (slot >= 2) {
         return !definition.hideArmor();
      }

      MolangProgram hide = definition.scripts().hideHeldItems();
      return hide == null || !hide.isConstant() || hide.constantValue() == 0.0F;
   }
}
