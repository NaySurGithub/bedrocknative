package net.bedrocknative.client.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.bedrocknative.client.hud.BedrockHud;
import net.bedrocknative.client.inventory.JsonUiContainerScreen;
import net.bedrocknative.client.mixin.hud.HudStateAccessor;
import net.bedrocknative.ui.ContainerData;
import net.bedrocknative.ui.HeartTracker;
import net.bedrocknative.ui.HudValues;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PlayerRideableJumping;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import org.cloudburstmc.protocol.bedrock.data.HudElement;

final class HudValuesReader {
   private HudValuesReader() {
   }

   static Player cameraPlayer(Minecraft minecraft) {
      return minecraft.getCameraEntity() instanceof Player player ? player : minecraft.player;
   }

   static HudValues read(Minecraft minecraft, HeartTracker hearts, float partialTick, boolean screenShowsEffects) {
      LocalPlayer local = minecraft.player;
      Player player = cameraPlayer(minecraft);
      int tick = minecraft.gui.hud.getGuiTicks();
      int health = Mth.ceil(player.getHealth());
      hearts.update(tick, Util.getMillis(), health, player.damageCooldownTime > 0);
      int display = hearts.displayHealth();
      float maxHealth = Math.max((float)player.getAttributeValue(Attributes.MAX_HEALTH), Math.max(display, health));
      HudValues.HeartType heartType = player.hasEffect(MobEffects.POISON)
         ? HudValues.HeartType.POISONED
         : (
            player.hasEffect(MobEffects.WITHER)
               ? HudValues.HeartType.WITHERED
               : (player.isFullyFrozen() ? HudValues.HeartType.FROZEN : HudValues.HeartType.NORMAL)
         );
      int mountHealth = 0;
      int mountHearts = 0;
      if (player.getVehicle() instanceof LivingEntity vehicle && vehicle.showVehicleHealth()) {
         mountHearts = Math.min(30, (int)(vehicle.getMaxHealth() + 0.5F) / 2);
         mountHealth = (int)Math.ceil(vehicle.getHealth());
      }

      PlayerRideableJumping jumping = local.jumpableVehicle();
      float jump = jumping == null ? -1.0F : local.getJumpRidingScale();
      boolean jumpCooldown = jumping != null && jumping.getJumpCooldown() > 0;
      List<ContainerData.Slot> hotbar = new ArrayList<>(9);
      float[] cooldowns = new float[9];

      for (int slot = 0; slot < 9; slot++) {
         ItemStack stack = player.getInventory().getItem(slot);
         hotbar.add(JsonUiContainerScreen.describe(stack));
         cooldowns[slot] = stack.isEmpty() ? 0.0F : player.getCooldowns().getCooldownPercent(stack, partialTick);
      }

      Set<HudValues.Part> hidden = hidden(screenShowsEffects);
      return new HudValues(
         mode(minecraft),
         tick,
         health,
         display,
         maxHealth,
         Mth.ceil(player.getAbsorptionAmount()),
         hearts.blink(),
         heartType,
         player.hasEffect(MobEffects.REGENERATION),
         player.level().getLevelData().isHardcore(),
         player.getArmorValue(),
         player.getFoodData().getFoodLevel(),
         player.getFoodData().getSaturationLevel(),
         player.hasEffect(MobEffects.HUNGER),
         Math.clamp(player.getAirSupply(), 0, player.getMaxAirSupply()),
         player.getMaxAirSupply(),
         player.isEyeInFluid(FluidTags.WATER),
         mountHealth,
         mountHearts,
         bar(minecraft),
         player.experienceProgress,
         player.experienceLevel,
         jump,
         jumpCooldown,
         effects(player),
         player.getInventory().getSelectedSlot(),
         hotbar,
         cooldowns,
         hidden
      );
   }

   private static HudValues.Mode mode(Minecraft minecraft) {
      GameType type = minecraft.gameMode == null ? GameType.SURVIVAL : minecraft.gameMode.getPlayerMode();

      return switch (type) {
         case CREATIVE -> HudValues.Mode.CREATIVE;
         case ADVENTURE -> HudValues.Mode.ADVENTURE;
         case SPECTATOR -> HudValues.Mode.SPECTATOR;
         default -> HudValues.Mode.SURVIVAL;
      };
   }

   private static HudValues.Bar bar(Minecraft minecraft) {
      Object state = ((HudStateAccessor)minecraft.gui.hud).bedrocknative$contextualInfoBar().getFirst();

      return switch (String.valueOf(state)) {
         case "EXPERIENCE" -> HudValues.Bar.EXPERIENCE;
         case "LOCATOR" -> HudValues.Bar.LOCATOR;
         case "JUMPABLE_VEHICLE" -> HudValues.Bar.JUMP;
         default -> HudValues.Bar.NONE;
      };
   }

   private static List<HudValues.Effect> effects(Player player) {
      List<MobEffectInstance> active = new ArrayList<>(player.getActiveEffects());
      if (active.isEmpty()) {
         return List.of();
      }

      active.sort(Comparator.reverseOrder());
      List<HudValues.Effect> effects = new ArrayList<>(active.size());

      for (MobEffectInstance instance : active) {
         Holder<MobEffect> effect = instance.getEffect();
         String id = effect.unwrapKey().map(ResourceKey::identifier).map(Object::toString).orElse("unknown");
         effects.add(
            new HudValues.Effect(
               id,
               instance.isAmbient(),
               effect.value().getCategory() == MobEffectCategory.BENEFICIAL,
               instance.isInfiniteDuration() ? -1 : instance.getDuration(),
               instance.getAmplifier(),
               instance.showIcon()
            )
         );
      }

      return effects;
   }

   private static Set<HudValues.Part> hidden(boolean screenShowsEffects) {
      Set<HudValues.Part> hidden = EnumSet.noneOf(HudValues.Part.class);
      hide(hidden, HudElement.HOTBAR, HudValues.Part.HOTBAR);
      hide(hidden, HudElement.HEALTH, HudValues.Part.HEALTH);
      hide(hidden, HudElement.ARMOR, HudValues.Part.ARMOR);
      hide(hidden, HudElement.FOOD_BAR, HudValues.Part.FOOD);
      hide(hidden, HudElement.AIR_BUBBLES_BAR, HudValues.Part.AIR);
      hide(hidden, HudElement.VEHICLE_HEALTH, HudValues.Part.MOUNT_HEALTH);
      hide(hidden, HudElement.PROGRESS_BAR, HudValues.Part.PROGRESS_BAR);
      hide(hidden, HudElement.EFFECTS_BAR, HudValues.Part.EFFECTS);
      hide(hidden, HudElement.ITEM_TEXT_POPUP, HudValues.Part.ITEM_TEXT);
      if (screenShowsEffects) {
         hidden.add(HudValues.Part.EFFECTS);
      }

      return hidden;
   }

   private static void hide(Set<HudValues.Part> hidden, HudElement element, HudValues.Part part) {
      if (BedrockHud.hides(element)) {
         hidden.add(part);
      }
   }
}
