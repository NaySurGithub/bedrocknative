package net.bedrocknative.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.PerfProbe;
import net.bedrocknative.model.entity.ClientEntityDef;
import net.bedrocknative.model.entity.ModelRegistry;
import net.bedrocknative.model.entity.VanillaMobs;
import net.bedrocknative.model.runtime.DrawSnapshot;
import net.bedrocknative.world.entity.BedrockActorEntity;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.EnderDragonRenderState;
import net.minecraft.client.renderer.entity.state.EndermanRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

public final class BedrockMobs {
   public static final RenderStateDataKey<ActorSnapshot> FRAME = RenderStateDataKey.create(() -> "bedrocknative:bedrock_mob");
   private static final float HAND_Y = -3.0F;
   private static final float HAND_Z = -3.0F;
   private static volatile boolean enabled = !Boolean.getBoolean("bedrocknative.javaMobs");
   private static final Map<String, VanillaMobs.Support> SUPPORT = new HashMap<>();
   private static final Set<String> LOGGED = new HashSet<>();
   private static final Object2IntOpenHashMap<String> SUBMITTED = new Object2IntOpenHashMap();

   private BedrockMobs() {
   }

   static boolean switchOn(String value) {
      return value != null && (value.equalsIgnoreCase("on") || value.equalsIgnoreCase("true"));
   }

   public static boolean enabled() {
      return enabled;
   }

   public static void setEnabled(boolean on) {
      enabled = on;
   }

   static boolean composing() {
      return enabled;
   }

   static void registryChanged() {
      SUPPORT.clear();
   }

   static void release() {
      SUPPORT.clear();
      LOGGED.clear();
   }

   public static VanillaMobs.Support support(ModelRegistry registry, String identifier) {
      VanillaMobs.Support support = SUPPORT.get(identifier);
      if (support == null) {
         try {
            support = VanillaMobs.decide(registry, identifier);
         } catch (RuntimeException var4) {
            BedrockNative.LOGGER.warn("Bedrock look: couldn't read what {} needs", identifier, var4);
            support = new VanillaMobs.Support(identifier, false, List.of("unreadable definition: " + var4), List.of());
         }

         SUPPORT.put(identifier, support);
         if (LOGGED.add(identifier + support.missing())) {
            BedrockNative.LOGGER.info("Bedrock look: {} {}", identifier, support.reason());
         }
      }

      return support;
   }

   public static Map<String, VanillaMobs.Support> decisions() {
      return new LinkedHashMap<>(SUPPORT);
   }

   public static boolean drawErrors() {
      return MeshDraw.anyFailed();
   }

   public static Map<String, Integer> submitted() {
      return new TreeMap<>(SUBMITTED);
   }

   public static void resetSubmitted() {
      SUBMITTED.clear();
   }

   public static void extract(Entity entity, EntityRenderState state, float partialTick) {
      if (enabled && !(entity instanceof Player) && !(entity instanceof BedrockActorEntity)) {
         ModelRegistry registry = BedrockModels.registry();
         ActorState actor = registry != null ? BedrockActors.state(entity) : null;
         if (actor != null && actor.vanillaMob()) {
            String identifier = actor.data().identifier();
            if ((entity instanceof Mob || VanillaMobs.ACTORS.contains(identifier)) && support(registry, identifier).drawn()) {
               ClientEntityDef definition = registry.entity(identifier);
               long perf = PerfProbe.start();
               ActorSnapshot snapshot = definition != null ? actor.evaluateMob(registry, definition, entity, state, partialTick) : null;
               PerfProbe.end(PerfProbe.Part.MODELS, perf);
               if (snapshot != null) {
                  state.setData(FRAME, snapshot);
               }
            }
         }
      }
   }

   public static boolean submit(
      LivingEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, float flipDegrees, float whiteOverlay, boolean shaking
   ) {
      ActorSnapshot snapshot = (ActorSnapshot)state.getData(FRAME);
      if (snapshot == null) {
         return false;
      } else {
         poseStack.pushPose();
         float scale = state.scale;
         poseStack.scale(scale, scale, scale);
         rotations(state, poseStack, scale, flipDegrees, shaking);
         if (state instanceof EndermanRenderState enderman) {
            carriedBlock(enderman, poseStack, collector);
         }

         DrawSnapshot draw = snapshot.draw();
         float model = snapshot.actorScale() / 16.0F;
         poseStack.scale(draw.scale(0) * model, draw.scale(1) * model, draw.scale(2) * model);
         snapshot.submit(
            poseStack,
            collector,
            state.lightCoords,
            LivingEntityRenderer.getOverlayCoords(state, whiteOverlay),
            state.outlineColor,
            !state.isInvisible,
            whiteOverlay,
            state.hasRedOverlay
         );
         if (state instanceof ArmedEntityRenderState armed) {
            heldItem(snapshot, 0, armed.rightHandItemState, armed, poseStack, collector);
            heldItem(snapshot, 1, armed.leftHandItemState, armed, poseStack, collector);
         }

         poseStack.popPose();
         SUBMITTED.addTo(snapshot.identifier(), 1);
         return true;
      }
   }

   public static boolean submitActor(EntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector) {
      ActorSnapshot snapshot = (ActorSnapshot)state.getData(FRAME);
      if (snapshot == null) {
         return false;
      } else {
         poseStack.pushPose();
         poseStack.rotateDegrees(Axis.YP, 180.0F - snapshot.bodyYaw());
         DrawSnapshot draw = snapshot.draw();
         float model = snapshot.actorScale() / 16.0F;
         poseStack.scale(draw.scale(0) * model, draw.scale(1) * model, draw.scale(2) * model);
         boolean red = state instanceof EnderDragonRenderState dragon && dragon.hasRedOverlay;
         int overlay = red ? OverlayTexture.pack(0.0F, true) : OverlayTexture.NO_OVERLAY;
         snapshot.submit(poseStack, collector, state.lightCoords, overlay, state.outlineColor, true, 0.0F, red);
         poseStack.popPose();
         SUBMITTED.addTo(snapshot.identifier(), 1);
         return true;
      }
   }

   private static void rotations(LivingEntityRenderState state, PoseStack poseStack, float scale, float flipDegrees, boolean shaking) {
      float bodyRot = state.bodyRot;
      if (shaking) {
         bodyRot += (float)(Math.cos(Mth.floor(state.ageInTicks) * 3.25F) * Math.PI * 0.4F);
      }

      poseStack.rotateDegrees(Axis.YP, 180.0F - bodyRot);
      if (state.deathTime > 0.0F) {
         float fall = Mth.sqrt((state.deathTime - 1.0F) / 20.0F * 1.6F);
         poseStack.rotateDegrees(Axis.ZP, Math.min(fall, 1.0F) * flipDegrees);
      } else if (state.isUpsideDown) {
         poseStack.translate(0.0F, (state.boundingBoxHeight + 0.1F) / scale, 0.0F);
         poseStack.rotateDegrees(Axis.ZP, 180.0F);
      }
   }

   private static void carriedBlock(EndermanRenderState state, PoseStack poseStack, SubmitNodeCollector collector) {
      if (!state.carriedBlock.isEmpty()) {
         poseStack.pushPose();
         poseStack.scale(-1.0F, -1.0F, 1.0F);
         poseStack.translate(0.0F, -1.501F, 0.0F);
         poseStack.translate(0.0F, 0.6875F, -0.75F);
         poseStack.rotateDegrees(Axis.XP, 20.0F);
         poseStack.rotateDegrees(Axis.YP, 45.0F);
         poseStack.translate(0.25F, 0.1875F, 0.25F);
         poseStack.scale(-0.5F, -0.5F, 0.5F);
         poseStack.rotateDegrees(Axis.YP, 90.0F);
         state.carriedBlock.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
         poseStack.popPose();
      }
   }

   private static void heldItem(
      ActorSnapshot snapshot, int hand, ItemStackRenderState item, ArmedEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector
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
}
