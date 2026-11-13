package net.bedrocknative.client.world;

import java.util.ArrayList;
import java.util.List;
import net.bedrocknative.client.movement.BedrockEffects;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.effect.MobEffect;
import org.cloudburstmc.protocol.bedrock.packet.OnScreenTextureAnimationPacket;
import org.jspecify.annotations.Nullable;

public final class BedrockScreenAnimations {
   private static final Identifier ELEMENT = Identifier.fromNamespaceAndPath("bedrocknative", "screen_animation");
   private static final int DURATION_TICKS = 30;
   private static final int MAX_ANIMATIONS = 8;
   private static final int ICON_SIZE = 48;
   @Nullable
   private static volatile BedrockScreenAnimations active;
   private final Minecraft minecraft;
   private final List<BedrockScreenAnimations.Animation> animations = new ArrayList<>();
   private int played;

   public BedrockScreenAnimations(Minecraft minecraft) {
      this.minecraft = minecraft;
   }

   public static void register() {
      HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, ELEMENT, BedrockScreenAnimations::extract);
   }

   public void start() {
      this.animations.clear();
      active = this;
   }

   public void close() {
      this.animations.clear();
      if (active == this) {
         active = null;
      }
   }

   public void apply(OnScreenTextureAnimationPacket packet) {
      long id = packet.getEffectId();
      Holder<MobEffect> effect = id >= 0L && id <= 2147483647L ? BedrockEffects.byId((int)id) : null;
      if (effect != null && this.animations.size() < 8) {
         this.animations.add(new BedrockScreenAnimations.Animation(effect, this.minecraft.level == null ? 0L : this.minecraft.level.getGameTime()));
         this.played++;
      }
   }

   public int playedCount() {
      return this.played;
   }

   private static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
      BedrockScreenAnimations current = active;
      if (current != null) {
         current.draw(graphics, deltaTracker);
      }
   }

   private void draw(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
      if (!this.animations.isEmpty() && this.minecraft.level != null) {
         float now = (float)this.minecraft.level.getGameTime() + deltaTracker.getGameTimeDeltaPartialTick(false);
         this.animations.removeIf(animationx -> now - (float)animationx.startedAt() >= 30.0F);

         for (BedrockScreenAnimations.Animation animation : this.animations) {
            float progress = Math.clamp((now - (float)animation.startedAt()) / 30.0F, 0.0F, 1.0F);
            float scale = 0.5F + progress * 1.5F;
            float alpha = progress < 0.5F ? 1.0F : 1.0F - (progress - 0.5F) * 2.0F;
            int size = Math.round(48.0F * scale);
            graphics.blitSprite(
               RenderPipelines.GUI_TEXTURED,
               Hud.getMobEffectSprite(animation.effect()),
               (graphics.guiWidth() - size) / 2,
               (graphics.guiHeight() - size) / 2 - 20,
               size,
               size,
               ARGB.white(alpha)
            );
         }
      }
   }

   private record Animation(Holder<MobEffect> effect, long startedAt) {
   }
}
