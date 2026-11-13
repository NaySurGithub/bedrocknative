package net.bedrocknative.model.runtime;

import net.bedrocknative.model.entity.ClientEntityDef;
import net.bedrocknative.model.entity.ModelRegistry;
import net.bedrocknative.model.geometry.MeshedGeometry;
import net.bedrocknative.molang.MolangActor;
import net.bedrocknative.molang.MolangEnv;
import net.bedrocknative.molang.MolangNames;
import net.bedrocknative.molang.VariableStore;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

public final class WornAttachable {
   public static final String MAIN_HAND = "main_hand";
   public static final String OFF_HAND = "off_hand";
   public static final String HEAD = "head";
   public static final String CHEST = "chest";
   public static final String LEGS = "legs";
   public static final String FEET = "feet";
   public static final WearerBones DEFERRED = new WearerBones() {
      private final Matrix4f identity = new Matrix4f();

      @Override
      public Matrix4f bone(String name) {
         return this.identity.identity();
      }

      @Override
      public boolean has(String name) {
         return JavaSkeleton.humanoidBone(name) != null;
      }

      @Override
      public Matrix4f bind(String name, MeshedGeometry.Bone root, Matrix4f out) {
         return out.identity();
      }
   };
   private static final int ITEM_SLOT = MolangNames.id("item_slot");
   private static final int IS_FIRST_PERSON = MolangNames.id("is_first_person");
   private static final int OWNING_ENTITY = MolangNames.id("owning_entity");
   private static final int IS_ENCHANTED = MolangNames.id("is_enchanted");
   private static final int HAS_TRIM = MolangNames.id("has_trim");
   private final ClientEntityDef definition;
   private final String itemId;
   private final String itemSlot;
   private final boolean firstPerson;
   private final ActorInstance instance;

   public WornAttachable(ModelRegistry registry, ClientEntityDef definition, String itemId, String itemSlot, boolean firstPerson) {
      this.definition = definition;
      this.itemId = itemId;
      this.itemSlot = itemSlot;
      this.firstPerson = firstPerson;
      this.instance = new ActorInstance(registry, definition);
      this.instance.setWearer(DEFERRED, null);
      context(this.instance.context(), itemSlot, firstPerson, MolangActor.NONE);
   }

   @Nullable
   public static ClientEntityDef resolve(
      ModelRegistry registry, String itemId, String itemSlot, boolean firstPerson, MolangActor wearer, MolangEnv env, VariableStore scratch
   ) {
      scratch.clear();
      context(scratch, itemSlot, firstPerson, wearer);
      env.actor = wearer;
      env.context = scratch;
      env.variables = scratch;
      env.geometry = null;
      env.controller = null;
      env.resources = null;
      env.attachable = true;
      return registry.attachable(itemId, env);
   }

   private static void context(VariableStore context, String itemSlot, boolean firstPerson, MolangActor wearer) {
      context.set(ITEM_SLOT, 0.0F, itemSlot);
      context.set(IS_FIRST_PERSON, firstPerson ? 1.0F : 0.0F, null);
      context.set(OWNING_ENTITY, 0.0F, wearer);
   }

   public DrawList frame(MolangEnv env, MolangActor wearer, boolean enchanted, float deltaTime, float frameAlpha) {
      VariableStore variables = this.instance.variables();
      variables.set(IS_ENCHANTED, enchanted ? 1.0F : 0.0F, null);
      variables.set(HAS_TRIM, 0.0F, null);
      this.instance.context().set(OWNING_ENTITY, 0.0F, wearer);
      return this.instance.frame(env, wearer, deltaTime, frameAlpha);
   }

   public ActorInstance instance() {
      return this.instance;
   }

   public ClientEntityDef definition() {
      return this.definition;
   }

   public String itemId() {
      return this.itemId;
   }

   public String itemSlot() {
      return this.itemSlot;
   }

   public boolean firstPerson() {
      return this.firstPerson;
   }
}
