package net.bedrocknative.client.model;

import java.util.ArrayDeque;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.model.entity.ActorData;
import net.bedrocknative.model.entity.ModelRegistry;
import net.bedrocknative.model.runtime.ActorInstance;
import net.bedrocknative.model.runtime.DrawList;
import net.bedrocknative.model.runtime.RuntimeEquipment;
import net.bedrocknative.model.skin.BedrockSwing;
import net.bedrocknative.model.skin.EmoteLibrary;
import net.bedrocknative.model.skin.PlayerEngine;
import net.bedrocknative.model.skin.RemotePlayerPose;
import net.bedrocknative.model.skin.SkinModel;
import net.bedrocknative.molang.MolangEnv;
import net.bedrocknative.molang.MolangProgram;
import net.bedrocknative.molang.MolangVersion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.FirstPersonHandsAndItems;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.equipment.Equippable;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.jspecify.annotations.Nullable;

final class PlayerRuntime {
   private static final long SAME_FRAME_NANOS = 100000000L;
   private static final int LEATHER = -6265536;
   private static final String EMOTE = "bedrocknative:emote";
   private static final String EMOTE_STOP = "query.any_animation_finished || query.ground_speed > 1.0";
   private static final String[] EQUIPMENT_SLOTS = new String[]{
      "slot.weapon.mainhand", "slot.weapon.offhand", "slot.armor.head", "slot.armor.chest", "slot.armor.legs", "slot.armor.feet", "slot.armor.body"
   };
   private static final EquipmentSlot[] JAVA_SLOTS = new EquipmentSlot[]{
      EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.BODY
   };
   private static final long PENDING_ANIMATE_NANOS = 500000000L;
   private static final int PENDING_ANIMATE_LIMIT = 8;
   private final ActorState state;
   private final PlayerEngine engine = new PlayerEngine();
   private final PlayerRuntime.View world = new PlayerRuntime.View();
   private final PlayerRuntime.View paperdoll = new PlayerRuntime.View();
   private final BedrockSwing swing = new BedrockSwing();
   private final RemotePlayerPose remotePose = new RemotePlayerPose();
   private float glide;
   private final ArrayDeque<PlayerRuntime.PendingAnimate> pendingAnimates = new ArrayDeque<>();
   private float capeFlap;
   private boolean firstPerson;
   @Nullable
   private SkinModel model;
   @Nullable
   private String pendingEmote;
   private long emoteEnds = Long.MAX_VALUE;
   private long evaluations;

   PlayerRuntime(ActorState state) {
      this.state = state;
   }

   void swing(long gameTime) {
      this.swing.swing(gameTime);
   }

   void animate(String animation, String nextState, String stopExpression, int stopExpressionVersion, String controller, float blendOutTime) {
      ActorInstance running = this.world.instance;
      if (running == null) {
         if (this.pendingAnimates.size() >= 8) {
            this.pendingAnimates.removeFirst();
         }

         this.pendingAnimates
            .addLast(new PlayerRuntime.PendingAnimate(animation, nextState, stopExpression, stopExpressionVersion, controller, blendOutTime, System.nanoTime()));
      } else {
         if (!running.animate(animation, nextState, stopExpression, stopExpressionVersion, controller, blendOutTime)) {
            BedrockNative.LOGGER.debug("AnimateEntity: no animation {} for a player", animation);
         }
      }
   }

   private void pendingAnimate(ActorInstance instance, long now) {
      PlayerRuntime.PendingAnimate pending;
      while ((pending = this.pendingAnimates.pollFirst()) != null) {
         if (now - pending.nanos() < 500000000L) {
            instance.animate(
               pending.animation(),
               pending.nextState(),
               pending.stopExpression(),
               pending.stopExpressionVersion(),
               pending.controller(),
               pending.blendOutTime()
            );
         }
      }
   }

   void unbind() {
      this.world.registry = null;
      this.paperdoll.registry = null;
   }

   void release() {
      for (PlayerRuntime.View view : new PlayerRuntime.View[]{this.world, this.paperdoll}) {
         if (view.model != null) {
            SkinTextures.release(view.model.drawn());
            view.model = null;
         }

         view.instance = null;
         view.equipment = null;
         view.last = null;
      }
   }

   @Nullable
   ActorSnapshot evaluate(
      ModelRegistry registry, SkinModel skin, Player entity, AvatarRenderState render, float partialTick, boolean inventory, boolean firstPersonCamera
   ) {
      PlayerRuntime.View view = inventory ? this.paperdoll : this.world;
      if (view.registry != registry || view.model != skin) {
         this.bind(view, registry, skin);
      }

      ActorInstance instance = view.instance;
      if (instance != null && !view.failed) {
         long frame = BedrockActors.frame();
         long now = System.nanoTime();
         boolean first = !inventory && firstPersonCamera;
         if (frame == view.lastFrame && view.last != null && now - view.lastNanos < 100000000L && view.firstPerson == first) {
            return view.last;
         } else {
            float deltaTime = view.lastNanos == 0L ? 0.0F : Math.min((float)(now - view.lastNanos) / 1.0E9F, 1.0F);
            view.lastNanos = now;
            view.lastFrame = frame;
            view.firstPerson = first;

            try {
               this.model = skin;
               this.firstPerson = first;
               this.capeFlap = PlayerEngine.capeFlapAmount(render.capeFlap, render.capeLean);
               ClientActorView actorView = this.state.view();
               actorView.prepare(entity, render, partialTick, this.state.lifeTime(), null);
               actorView.player(this);
               this.engineValues(entity, render, partialTick, first, inventory);
               this.engine.apply(instance, skin);
               if (!inventory) {
                  this.emotes(instance, registry, now);
                  this.pendingAnimate(instance, now);
               }

               boolean[] attached = this.equipment(view, entity, actorView);
               MolangEnv env = this.state.env();
               env.effects = inventory ? null : this.state.playerEffects(instance.definition(), entity);

               DrawList list;
               try {
                  list = instance.frame(env, actorView, deltaTime, partialTick);
               } finally {
                  env.effects = null;
               }

               ActorSnapshot snapshot = view.snapshots[view.next];
               view.next ^= 1;
               snapshot.identifier("minecraft:player");
               snapshot.fill(list, this.state.data().scale(), true);
               snapshot.draw().setUvAnimation(true);
               snapshot.hands(instance.drawnSkeleton(), attached);
               snapshot.glide(this.glide);
               if (!inventory && PlayerAnimProbe.watching(entity.getId())) {
                  PlayerAnimProbe.record(entity, instance, actorView, this.engine, partialTick, this.glide);
               }

               view.last = snapshot;
               this.evaluations++;
               return snapshot;
            } catch (RuntimeException var24) {
               view.failed = true;
               view.last = null;
               BedrockNative.LOGGER.error("The Bedrock model of player {} failed and is no longer drawn", entity.getName().getString(), var24);
               return null;
            }
         }
      } else {
         return null;
      }
   }

   private void bind(PlayerRuntime.View view, ModelRegistry registry, SkinModel skin) {
      ActorInstance previous = view.instance;
      SkinTextures.acquire(skin.drawn());
      if (view.model != null) {
         SkinTextures.release(view.model.drawn());
      }

      view.instance = null;
      view.equipment = null;
      view.last = null;
      view.lastFrame = -1L;
      view.failed = false;
      view.registry = registry;
      view.model = skin;

      try {
         ActorInstance created = new ActorInstance(registry, skin.definition(), true, skin::mesh);
         if (previous != null) {
            created.adoptVariables(previous);
         }

         view.instance = created;
         view.equipment = new RuntimeEquipment(registry, created);
      } catch (RuntimeException var6) {
         view.failed = true;
         BedrockNative.LOGGER.warn("Couldn't set up the Bedrock model of skin {}", skin.drawn().key(), var6);
      }
   }

   private void engineValues(Player entity, AvatarRenderState render, float partialTick, boolean first, boolean inventory) {
      this.engine.firstPerson = first;
      this.engine.paperdoll = inventory;
      if (entity instanceof LocalPlayer) {
         this.engine.attackTime = entity.getSwingAnimation(partialTick);
         this.engine.swimAmount = render.swimAmount;
         this.engine.glidingSpeed = render.speedValue;
         this.glide = 0.0F;
      } else {
         long gameTime = entity.level().getGameTime();
         this.swing.catchUp(gameTime);
         this.engine.attackTime = this.swing.attackTime(partialTick);
         ActorData data = this.state.data();
         this.remotePose.catchUp(gameTime, data.flag(EntityFlag.SWIMMING) || data.flag(EntityFlag.CRAWLING), data.flag(EntityFlag.GLIDING));
         this.engine.swimAmount = this.remotePose.swimAmount(partialTick);
         double dx = entity.getX() - entity.xo;
         double dy = entity.getY() - entity.yo;
         double dz = entity.getZ() - entity.zo;
         this.engine.glidingSpeed = this.remotePose.glidingSpeedValue(dx * dx + dy * dy + dz * dz);
         this.glide = this.remotePose.glideScale(partialTick);
      }

      this.engine.holdingRight = !entity.getMainHandItem().isEmpty();
      this.engine.holdingLeft = !entity.getOffhandItem().isEmpty();
      this.engine.xRotation = render.xRot;
      this.engine.sneaking = render.isCrouching;
      ItemUseAnimation use = entity.isUsingItem() ? entity.getUseItem().getUseAnimation() : ItemUseAnimation.NONE;
      float used = entity.isUsingItem() ? entity.getTicksUsingItem() + partialTick : 0.0F;
      this.engine.eatingTicks = use != ItemUseAnimation.EAT && use != ItemUseAnimation.DRINK ? -1.0F : used;
      this.engine.holdingSpyglass = use == ItemUseAnimation.SPYGLASS;
      this.engine.tootingGoatHorn = use == ItemUseAnimation.TOOT_HORN;
      this.engine.usingBrush = use == ItemUseAnimation.BRUSH;
      this.engine.brandishingSpear = use == ItemUseAnimation.TRIDENT;
      this.engine.chargeAmount = use == ItemUseAnimation.TRIDENT ? Math.min(1.0F, used / 10.0F) : 0.0F;
      this.engine.armHeight = 1.0F;
      this.engine.offhandArmHeight = 1.0F;
      if (first && entity instanceof LocalPlayer local) {
         FirstPersonHandsAndItems hands = local.firstPersonHandsAndItems();
         this.engine.armHeight = Mth.lerp(partialTick, hands.oMainHandHeight, hands.mainHandHeight);
         this.engine.offhandArmHeight = Mth.lerp(partialTick, hands.oOffHandHeight, hands.offHandHeight);
      }
   }

   private boolean[] equipment(PlayerRuntime.View view, Player entity, ClientActorView actorView) {
      boolean[] hands = view.hands;
      hands[0] = false;
      hands[1] = false;
      RuntimeEquipment running = view.equipment;
      if (running == null) {
         return hands;
      } else {
         boolean local = entity == Minecraft.getInstance().player;

         for (int slot = 0; slot < EQUIPMENT_SLOTS.length; slot++) {
            ItemStack stack = entity.getItemBySlot(JAVA_SLOTS[slot]);
            String item = local ? WearerView.itemIdentifier(stack) : this.state.data().item(EQUIPMENT_SLOTS[slot]);
            Equippable worn = (Equippable)stack.get(DataComponents.EQUIPPABLE);
            if (slot <= 1 && worn != null && worn.slot().isArmor()) {
               item = "";
            }

            DyedItemColor dyed = (DyedItemColor)stack.get(DataComponents.DYED_COLOR);

            boolean drawn;
            try {
               drawn = running.update(slot, item, stack.hasFoil(), dyed != null ? 0xFF000000 | dyed.rgb() : -6265536, actorView, this.state.env());
            } catch (RuntimeException var14) {
               BedrockNative.LOGGER.warn("Couldn't set up the attachable of {} on a player", item, var14);
               drawn = false;
            }

            if (slot <= 1) {
               hands[slot] = drawn;
            }
         }

         return hands;
      }
   }

   void emote(String pieceId, int durationTicks) {
      this.pendingEmote = pieceId;
      this.emoteEnds = durationTicks > 0 ? System.nanoTime() + durationTicks * 50000000L : Long.MAX_VALUE;
   }

   private void emotes(ActorInstance instance, ModelRegistry registry, long now) {
      String pieceId = this.pendingEmote;
      if (pieceId != null && PlayerSkins.emotesLoaded()) {
         this.pendingEmote = null;
         EmoteLibrary.Emote emote = PlayerSkins.emotes().emote(pieceId);
         if (emote == null) {
            BedrockActors.logMissingEmote(pieceId);
            return;
         }

         MolangProgram stop = registry.molang().compile("query.any_animation_finished || query.ground_speed > 1.0", MolangVersion.LATEST);
         instance.play(emote.animation(), "bedrocknative:emote", stop, 0.2F);
         BedrockNative.LOGGER.debug("Emote {} ({}) on {}", new Object[]{emote.name(), emote.pieceId(), this.state});
      } else if (now > this.emoteEnds && instance.playing("bedrocknative:emote")) {
         instance.stop("bedrocknative:emote");
         this.emoteEnds = Long.MAX_VALUE;
      }
   }

   boolean emoting() {
      ActorInstance instance = this.world.instance;
      return instance != null && instance.playing("bedrocknative:emote");
   }

   @Nullable
   ActorInstance instance() {
      return this.world.instance;
   }

   @Nullable
   SkinModel boundModel() {
      return this.world.model;
   }

   @Nullable
   SkinModel modelFor(ModelRegistry registry) {
      return this.world.registry == registry && !this.world.failed ? this.world.model : null;
   }

   @Nullable
   ActorSnapshot lastSnapshot() {
      return this.world.last;
   }

   boolean lastFirstPerson() {
      return this.world.firstPerson;
   }

   long evaluations() {
      return this.evaluations;
   }

   boolean failed() {
      return this.world.failed;
   }

   SkinModel model() {
      return this.model;
   }

   boolean firstPerson() {
      return this.firstPerson;
   }

   float capeFlap() {
      return this.capeFlap;
   }

   private record PendingAnimate(
      String animation, String nextState, String stopExpression, int stopExpressionVersion, String controller, float blendOutTime, long nanos
   ) {
   }

   private static final class View {
      final ActorSnapshot[] snapshots = new ActorSnapshot[]{new ActorSnapshot(), new ActorSnapshot()};
      @Nullable
      ActorInstance instance;
      @Nullable
      RuntimeEquipment equipment;
      @Nullable
      ModelRegistry registry;
      @Nullable
      SkinModel model;
      @Nullable
      ActorSnapshot last;
      int next;
      long lastFrame = -1L;
      long lastNanos;
      boolean firstPerson;
      boolean failed;
      final boolean[] hands = new boolean[2];
   }
}
