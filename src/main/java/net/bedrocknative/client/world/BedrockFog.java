package net.bedrocknative.client.world;

import java.util.HashSet;
import java.util.Set;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.content.FogDefinition;
import net.bedrocknative.content.FogDefinitions;
import net.bedrocknative.content.PackStack;
import net.bedrocknative.world.state.FogStack;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.util.Util;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import org.cloudburstmc.protocol.bedrock.packet.PlayerFogPacket;
import org.jspecify.annotations.Nullable;

public final class BedrockFog {
   private static final float RAIN_FOG_LEVEL = 0.01F;
   private static volatile @Nullable BedrockFog active;
   private final FogStack stack = new FogStack();
   private FogDefinition.@Nullable Medium lastMedium;
   private long enteredMillis;
   private final Set<FogDefinition.Transition> logged = new HashSet<>();
   private volatile BedrockFog.@Nullable Definitions definitions;

   public static void modify(FogData fog, Camera camera, float renderDistanceBlocks, ClientLevel level) {
      BedrockFog current = active;
      if (current != null) {
         current.apply(fog, camera, renderDistanceBlocks, level);
      }
   }

   public void start() {
      this.stack.clear();
      this.lastMedium = null;
      this.logged.clear();
      active = this;
   }

   public void close() {
      this.stack.clear();
      this.definitions = null;
      if (active == this) {
         active = null;
      }
   }

   public void apply(PlayerFogPacket packet) {
      this.stack.apply(packet);
      if (!this.stack.isEmpty()) {
         this.definitions();
      }
   }

   public FogStack stack() {
      return this.stack;
   }

   private void apply(FogData fog, Camera camera, float renderDistanceBlocks, ClientLevel level) {
      if (!this.stack.isEmpty()) {
         FogDefinition.Medium medium = medium(camera, level);
         long now = Util.getMillis();
         if (medium != this.lastMedium) {
            this.lastMedium = medium;
            this.enteredMillis = now;
         }

         if (medium != null) {
            FogDefinitions fogs = this.definitions();
            FogDefinition.Distance distance = this.stack.resolve(medium, fogs);
            if (distance == null && medium == FogDefinition.Medium.WEATHER) {
               distance = this.stack.resolve(FogDefinition.Medium.AIR, fogs);
            }

            if (distance != null) {
               FogDefinition.Transition transition = distance.transition();
               if (transition != null && this.logged.add(transition)) {
                  BedrockNative.LOGGER
                     .info(
                        "Bedrock fog: {} fog eases in over {} s (progress {} at once, {} after {} s), from {}-{} blocks",
                        new Object[]{
                           medium.key(),
                           transition.maxSeconds(),
                           transition.minPercent(),
                           transition.midPercent(),
                           transition.midSeconds(),
                           transition.init().startBlocks(renderDistanceBlocks),
                           transition.init().endBlocks(renderDistanceBlocks)
                        }
                     );
               }

               float[] at = distance.at((float)(now - this.enteredMillis) / 1000.0F, renderDistanceBlocks);
               int rgb = (int)at[2];
               fog.environmentalStart = at[0];
               fog.environmentalEnd = Math.max(fog.environmentalStart, at[1]);
               fog.color.set((rgb >> 16 & 0xFF) / 255.0F, (rgb >> 8 & 0xFF) / 255.0F, (rgb & 0xFF) / 255.0F, 1.0F);
            }
         }
      }
   }

   static FogDefinition.@Nullable Medium medium(Camera camera, ClientLevel level) {
      FogType type = camera.getFluidInCamera();

      return switch (type) {
         case WATER -> FogDefinition.Medium.WATER;
         case POWDER_SNOW -> FogDefinition.Medium.POWDER_SNOW;
         case LAVA -> {
            if (camera.entity() instanceof LivingEntity living && living.hasEffect(MobEffects.FIRE_RESISTANCE)) {
               yield FogDefinition.Medium.LAVA_RESISTANCE;
            }

            yield FogDefinition.Medium.LAVA;
         }
         case ATMOSPHERIC, NONE -> level.getRainLevel(1.0F) > 0.01F ? FogDefinition.Medium.WEATHER : FogDefinition.Medium.AIR;
      };
   }

   private FogDefinitions definitions() {
      PackStack current = PackStack.active();
      BedrockFog.Definitions cached = this.definitions;
      if (cached == null || cached.stack() != current) {
         cached = new BedrockFog.Definitions(current, FogDefinitions.read(current));
         this.definitions = cached;
      }

      return cached.fogs();
   }

   private record Definitions(PackStack stack, FogDefinitions fogs) {
   }
}
