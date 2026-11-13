package net.bedrocknative.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.List;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.PerfProbe;
import net.bedrocknative.model.entity.ModelRegistry;
import net.bedrocknative.model.entity.VanillaMobs;
import net.bedrocknative.model.runtime.FirstPersonRig;
import net.bedrocknative.model.skin.PlayerSkin;
import net.bedrocknative.model.skin.SkinModel;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.item.Items;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

public final class BedrockPlayers {
   public static final RenderStateDataKey<ActorSnapshot> FRAME = RenderStateDataKey.create(() -> "bedrocknative:bedrock_player");
   private static final float HAND_Y = -3.0F;
   private static final float HAND_Z = -3.0F;
   private static final FirstPersonRig RIG = new FirstPersonRig();
   private static volatile boolean enabled = !Boolean.getBoolean("bedrocknative.javaPlayers");
   private static @Nullable ModelRegistry decided;
   private static VanillaMobs.@Nullable Support support;
   private static boolean paperdoll;
   private static int submitted;
   private static int firstPersonSubmitted;
   private static int mapArmsSubmitted;
   private static volatile int scaleWatchId = Integer.MIN_VALUE;
   private static volatile float watchedScale = Float.NaN;
   private static final Matrix4f ANCHOR = new Matrix4f();

   private BedrockPlayers() {
   }

   public static boolean enabled() {
      return enabled;
   }

   public static void setEnabled(boolean on) {
      enabled = on;
      BedrockActors actors = BedrockActors.current();
      if (on && actors != null) {
         actors.skins().activate();
      }
   }

   static void release() {
      support = null;
      decided = null;
   }

   static boolean inPaperdoll() {
      return paperdoll;
   }

   public static void paperdoll(boolean on) {
      paperdoll = on;
   }

   public static VanillaMobs.Support support(ModelRegistry registry) {
      VanillaMobs.Support current = support;
      if (current == null || decided != registry) {
         try {
            current = SkinModel.support(registry);
         } catch (RuntimeException e) {
            current = new VanillaMobs.Support("minecraft:player", false, List.of("unreadable definition: " + e), List.of());
         }

         support = current;
         decided = registry;
         BedrockNative.LOGGER.info("Bedrock look: players {}", current.reason());
      }

      return current;
   }

   public static void extract(Entity entity, EntityRenderState state, float partialTick) {
      if (enabled && entity instanceof Player player && state instanceof AvatarRenderState render) {
         ModelRegistry registry = BedrockModels.registry();
         BedrockActors actors = BedrockActors.current();
         ActorState actor = registry != null && actors != null ? BedrockActors.state(entity) : null;
         PlayerRuntime runtime = actor != null ? actor.player() : null;
         if (runtime != null && support(registry).drawn()) {
            PlayerSkins skins = actors.skins();
            PlayerSkin skin = skins.drawnFor(player.getUUID());
            SkinModel model = skins.model(registry, skin);
            if (model != null) {
               skins.explainModel(player.getUUID(), model);
            } else {
               model = runtime.modelFor(registry);
               if (model == null && ActorPrep.ENABLED) {
                  model = skins.defaultModel(registry, skin.slim());
               }

               if (model == null) {
                  return;
               }
            }

            Minecraft minecraft = Minecraft.getInstance();
            boolean firstPerson = player == minecraft.player && !minecraft.gameRenderer.mainCamera().isDetached() && !player.isSleeping();
            long perf = PerfProbe.start();
            ActorSnapshot snapshot = runtime.evaluate(registry, model, player, render, partialTick, paperdoll, firstPerson);
            PerfProbe.end(PerfProbe.Part.MODELS, perf);
            if (snapshot != null) {
               state.setData(FRAME, snapshot);
            }
         }
      }
   }

   public static boolean submit(AvatarRenderState state, PoseStack poseStack, SubmitNodeCollector collector, float flipDegrees) {
      ActorSnapshot snapshot = (ActorSnapshot)state.getData(FRAME);
      if (snapshot == null) {
         return false;
      }

      boolean watched = state.id == scaleWatchId;
      float base = watched ? yScale(poseStack.last().pose()) : 0.0F;
      poseStack.pushPose();
      float scale = snapshot.actorScale();
      if (state.isCrouching) {
         poseStack.translate(0.0F, scale * 2.0F / 16.0F, 0.0F);
      }

      rotations(state, poseStack, flipDegrees, snapshot.glide());
      float model = scale / 16.0F;
      poseStack.scale(snapshot.draw().scale(0) * model, snapshot.draw().scale(1) * model, snapshot.draw().scale(2) * model);
      if (watched && base > 0.0F && snapshot.draw().scale(1) != 0.0F) {
         watchedScale = yScale(poseStack.last().pose()) / base * 16.0F / snapshot.draw().scale(1);
      }

      snapshot.submit(
         poseStack,
         collector,
         state.lightCoords,
         LivingEntityRenderer.getOverlayCoords(state, 0.0F),
         state.outlineColor,
         !state.isInvisible,
         0.0F,
         state.hasRedOverlay
      );
      heldItem(snapshot, 0, state.rightHandItemState, state, poseStack, collector);
      heldItem(snapshot, 1, state.leftHandItemState, state, poseStack, collector);
      poseStack.popPose();
      submitted++;
      return true;
   }

   private static void rotations(AvatarRenderState state, PoseStack poseStack, float flipDegrees, float remoteGlide) {
      poseStack.rotateDegrees(Axis.YP, 180.0F - state.bodyRot);
      if (state.deathTime > 0.0F) {
         float fall = Mth.sqrt((state.deathTime - 1.0F) / 20.0F * 1.6F);
         poseStack.rotateDegrees(Axis.ZP, Math.min(fall, 1.0F) * flipDegrees);
      } else if (state.isAutoSpinAttack) {
         poseStack.rotateDegrees(Axis.XP, -90.0F - state.xRot);
         poseStack.rotateDegrees(Axis.YP, state.ageInTicks * -75.0F);
      }

      if (state.isFallFlying || remoteGlide > 0.0F) {
         float glide = state.isFallFlying ? state.fallFlyingScale() : remoteGlide;
         if (!state.isAutoSpinAttack) {
            poseStack.rotateDegrees(Axis.XP, glide * (-90.0F - state.xRot));
         }

         if (state.shouldApplyFlyingYRot) {
            poseStack.rotate(Axis.YP.rotation(state.flyingYRot));
         }
      }
   }

   private static void heldItem(
      ActorSnapshot snapshot, int hand, ItemStackRenderState item, AvatarRenderState state, PoseStack poseStack, SubmitNodeCollector collector
   ) {
      if (!item.isEmpty() && snapshot.javaHand(hand)) {
         float[] pivot = snapshot.handPivot(hand);
         poseStack.pushPose();
         poseStack.mulPose(snapshot.handMatrix(hand));
         poseStack.translate(pivot[0], pivot[1] + -3.0F, pivot[2] + -3.0F);
         poseStack.rotateDegrees(Axis.ZP, 180.0F);
         poseStack.scale(16.0F, 16.0F, 16.0F);
         poseStack.rotateDegrees(Axis.XP, -90.0F);
         poseStack.rotateDegrees(Axis.YP, 180.0F);
         item.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
         poseStack.popPose();
      }
   }

   public static boolean submitFirstPersonArm(@Nullable AvatarRenderState state, PoseStack poseStack, SubmitNodeCollector collector, int light) {
      ActorSnapshot snapshot = state != null && enabled ? (ActorSnapshot)state.getData(FRAME) : null;
      Minecraft minecraft = Minecraft.getInstance();
      if (snapshot != null && minecraft.player != null && firstPersonFrame(minecraft) && !holdsMap(minecraft.player)) {
         poseStack.pushPose();
         poseStack.mulPose(RIG.bone("root"));
         snapshot.submitOwn(poseStack, collector, light, OverlayTexture.NO_OVERLAY);
         if (FirstPersonProbe.enabled()) {
            FirstPersonProbe.arm(poseStack.last().pose(), snapshot);
         }

         poseStack.popPose();
         firstPersonSubmitted++;
         return true;
      } else {
         return false;
      }
   }

   public static boolean submitMapArm(@Nullable AvatarRenderState state, HumanoidArm arm, PoseStack poseStack, SubmitNodeCollector collector, int light) {
      ActorSnapshot snapshot = state != null && enabled ? (ActorSnapshot)state.getData(FRAME) : null;
      Minecraft minecraft = Minecraft.getInstance();
      if (snapshot != null && firstPersonFrame(minecraft)) {
         boolean right = arm == HumanoidArm.RIGHT;
         if (!snapshot.armAnchor(
            right ? "rightArm" : "leftArm", right ? -5.0F : 5.0F, state.skin.model() == PlayerModelType.SLIM ? 2.5F : 2.0F, right ? 0.1F : -0.1F, ANCHOR
         )) {
            return false;
         }

         poseStack.pushPose();
         poseStack.mulPose(ANCHOR);
         snapshot.submitSubtree(poseStack, collector, light, right ? "rightArm" : "leftArm");
         poseStack.popPose();
         mapArmsSubmitted++;
         return true;
      } else {
         return false;
      }
   }

   public static boolean localFirstPerson() {
      return firstPersonFrame(Minecraft.getInstance());
   }

   private static boolean firstPersonFrame(Minecraft minecraft) {
      ActorState local = minecraft.player != null ? BedrockActors.state(minecraft.player) : null;
      PlayerRuntime runtime = local != null ? local.player() : null;
      return runtime != null && runtime.lastFirstPerson();
   }

   private static boolean holdsMap(Player player) {
      return player.getMainHandItem().is(Items.FILLED_MAP) || player.getOffhandItem().is(Items.FILLED_MAP);
   }

   public static int submitted() {
      return submitted;
   }

   public static int firstPersonSubmitted() {
      return firstPersonSubmitted;
   }

   public static int mapArmsSubmitted() {
      return mapArmsSubmitted;
   }

   public static void watchScale(int entityId) {
      watchedScale = Float.NaN;
      scaleWatchId = entityId;
   }

   public static float watchedScale() {
      return watchedScale;
   }

   private static float yScale(Matrix4f pose) {
      return (float)Math.sqrt(pose.m10() * pose.m10() + pose.m11() * pose.m11() + pose.m12() * pose.m12());
   }

   public static void resetSubmitted() {
      submitted = 0;
      firstPersonSubmitted = 0;
      mapArmsSubmitted = 0;
   }

   public static @Nullable String describe(Player player) {
      ActorState state = BedrockActors.state(player);
      PlayerRuntime runtime = state != null ? state.player() : null;
      SkinModel model = runtime != null ? runtime.boundModel() : null;
      return model == null
         ? null
         : model.drawn().describe()
            + " geometry "
            + model.defaultGeometry()
            + (model.cape() ? " cape" : "")
            + (model.blinking() ? " blinking" : "")
            + (runtime.failed() ? " FAILED" : "")
            + ", "
            + runtime.evaluations()
            + " frames"
            + (runtime.emoting() ? ", emoting" : "");
   }

   public static @Nullable SkinModel model(Player player) {
      ActorState state = BedrockActors.state(player);
      PlayerRuntime runtime = state != null ? state.player() : null;
      return runtime != null ? runtime.boundModel() : null;
   }

   public static @Nullable ActorSnapshot lastFrame(Player player) {
      ActorState state = BedrockActors.state(player);
      PlayerRuntime runtime = state != null ? state.player() : null;
      return runtime != null ? runtime.lastSnapshot() : null;
   }

   public static boolean emote(Player player, String pieceId) {
      ActorState state = BedrockActors.state(player);
      PlayerRuntime runtime = state != null ? state.player() : null;
      if (runtime != null && PlayerSkins.emotes().emote(pieceId) != null) {
         runtime.emote(pieceId, 0);
         return true;
      } else {
         return false;
      }
   }

   public static boolean emoting(Player player) {
      ActorState state = BedrockActors.state(player);
      PlayerRuntime runtime = state != null ? state.player() : null;
      return runtime != null && runtime.emoting();
   }

   public static List<String> emotes() {
      return PlayerSkins.emotes().describe();
   }
}
