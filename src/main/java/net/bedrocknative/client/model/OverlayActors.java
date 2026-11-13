package net.bedrocknative.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.PerfProbe;
import net.bedrocknative.model.entity.ModelRegistry;
import net.bedrocknative.model.entity.OverlayPlan;
import net.bedrocknative.model.runtime.DrawSnapshot;
import net.bedrocknative.world.entity.BedrockActorEntity;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

public final class OverlayActors {
   public static final RenderStateDataKey<ActorSnapshot> FRAME = RenderStateDataKey.create(() -> "bedrocknative:overlay");
   private static final Map<String, Optional<OverlayPlan>> PLANS = new HashMap<>();
   private static final Object2IntOpenHashMap<String> SUBMITTED = new Object2IntOpenHashMap();
   private static @Nullable ModelRegistry plansFor;

   private OverlayActors() {
   }

   public static @Nullable OverlayPlan plan(ModelRegistry registry, String identifier) {
      if (plansFor != registry) {
         PLANS.clear();
         plansFor = registry;
      }

      Optional<OverlayPlan> plan = PLANS.get(identifier);
      if (plan == null) {
         OverlayPlan made = null;
         OverlayPlan.Replacement replacement = null;

         try {
            made = OverlayPlan.of(registry, identifier);
            replacement = made == null ? OverlayPlan.replacement(registry, identifier) : null;
         } catch (RuntimeException e) {
            BedrockNative.LOGGER.warn("Couldn't read what the server's packs add to {}", identifier, e);
         }

         if (made != null) {
            BedrockNative.LOGGER.info("Server packs add render controllers {} to {}: drawn over Java's model", made.controllers(), identifier);
         } else if (replacement != null) {
            BedrockNative.LOGGER
               .info(
                  "Server packs replace {} of {} with {}: no overlay over Java's model (the Bedrock look draws the server's definition)",
                  new Object[]{replacement.base(), identifier, replacement.server()}
               );
         }

         plan = Optional.ofNullable(made);
         PLANS.put(identifier, plan);
      }

      return plan.orElse(null);
   }

   public static void extract(Entity entity, EntityRenderState state, float partialTick) {
      if (entity instanceof LivingEntity
         && !(entity instanceof BedrockActorEntity)
         && state instanceof LivingEntityRenderState
         && !BedrockPlayers.inPaperdoll()) {
         ModelRegistry registry = BedrockModels.registry();
         ActorState actor = registry != null ? BedrockActors.state(entity) : null;
         if (actor != null && !actor.carrier()) {
            OverlayPlan plan = plan(registry, actor.data().identifier());
            long perf = PerfProbe.start();
            ActorSnapshot snapshot = plan != null ? actor.evaluateOverlay(registry, plan, entity, state, partialTick) : null;
            PerfProbe.end(PerfProbe.Part.MODELS, perf);
            if (snapshot != null) {
               state.setData(FRAME, snapshot);
            }
         }
      }
   }

   public static void submit(LivingEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, float flipDegrees) {
      ActorSnapshot snapshot = (ActorSnapshot)state.getData(FRAME);
      if (snapshot != null) {
         poseStack.pushPose();
         float scale = state.scale;
         poseStack.scale(scale, scale, scale);
         poseStack.rotateDegrees(Axis.YP, 180.0F - state.bodyRot);
         if (state.deathTime > 0.0F) {
            float fall = Mth.sqrt((state.deathTime - 1.0F) / 20.0F * 1.6F);
            poseStack.rotateDegrees(Axis.ZP, Math.min(fall, 1.0F) * flipDegrees);
         }

         DrawSnapshot draw = snapshot.draw();
         float model = snapshot.actorScale() / 16.0F;
         poseStack.scale(draw.scale(0) * model, draw.scale(1) * model, draw.scale(2) * model);
         int overlay = state.hasRedOverlay ? OverlayTexture.pack(0.0F, true) : OverlayTexture.NO_OVERLAY;
         snapshot.submit(poseStack, collector, state.lightCoords, overlay, state.outlineColor, !state.isInvisible, 0.0F, state.hasRedOverlay);
         poseStack.popPose();
         SUBMITTED.addTo(snapshot.identifier(), 1);
      }
   }

   public static Map<String, Integer> submitted() {
      return new TreeMap<>(SUBMITTED);
   }

   public static void resetSubmitted() {
      SUBMITTED.clear();
   }

   static void release() {
      PLANS.clear();
      plansFor = null;
   }
}
