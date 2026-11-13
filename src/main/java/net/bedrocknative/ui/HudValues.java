package net.bedrocknative.ui;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public record HudValues(
   HudValues.Mode mode,
   int tick,
   int health,
   int displayHealth,
   float maxHealth,
   int absorption,
   boolean healthBlink,
   HudValues.HeartType heartType,
   boolean regeneration,
   boolean hardcore,
   int armor,
   int food,
   float saturation,
   boolean hungerEffect,
   int air,
   int maxAir,
   boolean underwater,
   int mountHealth,
   int mountHearts,
   HudValues.Bar bar,
   float experience,
   int level,
   float jump,
   boolean jumpCooldown,
   List<HudValues.Effect> effects,
   int selected,
   List<ContainerData.Slot> hotbar,
   float[] cooldowns,
   Set<HudValues.Part> hidden
) {
   public static final int HOTBAR_SIZE = 9;
   public static final HudValues EMPTY = new HudValues(
      HudValues.Mode.SURVIVAL,
      0,
      20,
      20,
      20.0F,
      0,
      false,
      HudValues.HeartType.NORMAL,
      false,
      false,
      0,
      20,
      5.0F,
      false,
      300,
      300,
      false,
      0,
      0,
      HudValues.Bar.EXPERIENCE,
      0.0F,
      0,
      -1.0F,
      false,
      List.of(),
      0,
      emptyHotbar(),
      new float[9],
      Set.of()
   );

   public HudValues {
      effects = List.copyOf(effects);
      hotbar = List.copyOf(hotbar);
      cooldowns = (float[])cooldowns.clone();
      hidden = Set.copyOf(hidden);
   }

   public static List<ContainerData.Slot> emptyHotbar() {
      return Collections.nCopies(9, ContainerData.Slot.EMPTY);
   }

   public boolean hides(HudValues.Part part) {
      return this.hidden.contains(part);
   }

   public ContainerData.Slot slot(int index) {
      return index >= 0 && index < this.hotbar.size() ? this.hotbar.get(index) : ContainerData.Slot.EMPTY;
   }

   public float cooldown(int index) {
      return index >= 0 && index < this.cooldowns.length ? this.cooldowns[index] : 0.0F;
   }

   public int healthRows() {
      return (int)Math.ceil((this.maxHealth + this.absorption) / 2.0F / 10.0F);
   }

   public int healthRowHeight() {
      return Math.max(10 - (this.healthRows() - 2), 3);
   }

   public int mountRows() {
      return this.mountHearts <= 0 ? 0 : (int)Math.ceil(this.mountHearts / 10.0);
   }

   public boolean airShown() {
      return this.underwater || this.air < this.maxAir;
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return other instanceof HudValues values
         && this.mode == values.mode
         && this.tick == values.tick
         && this.health == values.health
         && this.displayHealth == values.displayHealth
         && Float.compare(this.maxHealth, values.maxHealth) == 0
         && this.absorption == values.absorption
         && this.healthBlink == values.healthBlink
         && this.heartType == values.heartType
         && this.regeneration == values.regeneration
         && this.hardcore == values.hardcore
         && this.armor == values.armor
         && this.food == values.food
         && Float.compare(this.saturation, values.saturation) == 0
         && this.hungerEffect == values.hungerEffect
         && this.air == values.air
         && this.maxAir == values.maxAir
         && this.underwater == values.underwater
         && this.mountHealth == values.mountHealth
         && this.mountHearts == values.mountHearts
         && this.bar == values.bar
         && Float.compare(this.experience, values.experience) == 0
         && this.level == values.level
         && Float.compare(this.jump, values.jump) == 0
         && this.jumpCooldown == values.jumpCooldown
         && this.effects.equals(values.effects)
         && this.selected == values.selected
         && this.hotbar.equals(values.hotbar)
         && Arrays.equals(this.cooldowns, values.cooldowns)
         && this.hidden.equals(values.hidden);
   }

   @Override
   public int hashCode() {
      return Objects.hash(
         this.mode,
         this.tick,
         this.health,
         this.displayHealth,
         this.maxHealth,
         this.absorption,
         this.level,
         this.food,
         this.armor,
         this.air,
         this.selected,
         this.hotbar,
         this.effects
      );
   }

   public enum Bar {
      EXPERIENCE,
      LOCATOR,
      JUMP,
      NONE;
   }

   public record Effect(String id, boolean ambient, boolean beneficial, int duration, int amplifier, boolean icon) {
      public boolean endingSoon() {
         return this.duration >= 0 && this.duration <= 200;
      }
   }

   public enum HeartType {
      NORMAL,
      POISONED,
      WITHERED,
      FROZEN;
   }

   public enum Mode {
      SURVIVAL,
      CREATIVE,
      ADVENTURE,
      SPECTATOR;

      public boolean survivalUi() {
         return this == SURVIVAL || this == ADVENTURE;
      }
   }

   public enum Part {
      HOTBAR,
      HEALTH,
      ARMOR,
      FOOD,
      AIR,
      MOUNT_HEALTH,
      PROGRESS_BAR,
      EFFECTS,
      ITEM_TEXT;
   }
}
