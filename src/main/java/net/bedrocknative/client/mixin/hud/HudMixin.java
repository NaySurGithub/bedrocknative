package net.bedrocknative.client.mixin.hud;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.bedrocknative.client.camera.BedrockCamera;
import net.bedrocknative.client.hud.BedrockHud;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.world.entity.player.Player;
import org.cloudburstmc.protocol.bedrock.data.HudElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public abstract class HudMixin {
   @Inject(method = "extractCrosshair", at = @At("HEAD"), cancellable = true)
   private void bedrocknative$crosshair(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
      hide(HudElement.CROSSHAIR, ci);
   }

   @Inject(method = "extractItemHotbar", at = @At("HEAD"), cancellable = true)
   private void bedrocknative$hotbar(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
      hide(HudElement.HOTBAR, ci);
   }

   @Inject(method = "extractArmor", at = @At("HEAD"), cancellable = true)
   private static void bedrocknative$armor(
      GuiGraphicsExtractor graphics, Player player, int yLineBase, int numHealthRows, int healthRowHeight, int xLeft, CallbackInfo ci
   ) {
      hide(HudElement.ARMOR, ci);
   }

   @Inject(method = "extractHearts", at = @At("HEAD"), cancellable = true)
   private void bedrocknative$health(
      GuiGraphicsExtractor graphics,
      Player player,
      int xLeft,
      int yLineBase,
      int healthRowHeight,
      int heartOffsetIndex,
      float maxHealth,
      int currentHealth,
      int oldHealth,
      int totalAbsorption,
      boolean blink,
      CallbackInfo ci
   ) {
      hide(HudElement.HEALTH, ci);
   }

   @Inject(method = "extractFood", at = @At("HEAD"), cancellable = true)
   private void bedrocknative$food(GuiGraphicsExtractor graphics, Player player, int yLineBase, int xRight, CallbackInfo ci) {
      hide(HudElement.FOOD_BAR, ci);
   }

   @Inject(method = "extractAirBubbles", at = @At("HEAD"), cancellable = true)
   private void bedrocknative$air(GuiGraphicsExtractor graphics, Player player, int vehicleHearts, int yLineAir, int xRight, CallbackInfo ci) {
      hide(HudElement.AIR_BUBBLES_BAR, ci);
   }

   @Inject(method = "extractVehicleHealth", at = @At("HEAD"), cancellable = true)
   private void bedrocknative$vehicleHealth(GuiGraphicsExtractor graphics, CallbackInfo ci) {
      hide(HudElement.VEHICLE_HEALTH, ci);
   }

   @Inject(method = "extractEffects", at = @At("HEAD"), cancellable = true)
   private void bedrocknative$effects(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
      hide(HudElement.EFFECTS_BAR, ci);
   }

   @Inject(method = "extractSelectedItemName", at = @At("HEAD"), cancellable = true)
   private void bedrocknative$itemText(GuiGraphicsExtractor graphics, CallbackInfo ci) {
      hide(HudElement.ITEM_TEXT_POPUP, ci);
   }

   @WrapOperation(
      method = "extractHotbarAndDecorations",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gui/contextualbar/ContextualBar;extractExperienceLevel(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;I)V"
      )
   )
   private void bedrocknative$experienceLevel(GuiGraphicsExtractor graphics, Font font, int level, Operation<Void> original) {
      if (!BedrockHud.hides(HudElement.PROGRESS_BAR)) {
         original.call(new Object[]{graphics, font, level});
      }
   }

   @Inject(method = "extractRenderState", at = @At("TAIL"))
   private void bedrocknative$cameraFade(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
      int colour = BedrockCamera.fadeColour();
      if (colour != 0) {
         graphics.nextStratum();
         graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), colour);
      }
   }

   private static void hide(HudElement element, CallbackInfo ci) {
      if (BedrockHud.hides(element)) {
         ci.cancel();
      }
   }
}
