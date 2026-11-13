package net.bedrocknative.sound;

import java.util.EnumMap;
import java.util.Map;
import org.cloudburstmc.protocol.bedrock.data.LevelEvent;
import org.jspecify.annotations.Nullable;

public final class LevelEventSounds {
   private static final Map<LevelEvent, LevelEventSounds.Sound> SOUNDS = new EnumMap<>(LevelEvent.class);

   private LevelEventSounds() {
   }

   private static void put(LevelEvent event, String name, float pitch, boolean pitchFromData) {
      SOUNDS.put(event, new LevelEventSounds.Sound(name, pitch, pitchFromData));
   }

   public static LevelEventSounds.@Nullable Sound of(LevelEvent event) {
      return SOUNDS.get(event);
   }

   public static Map<LevelEvent, LevelEventSounds.Sound> all() {
      return Map.copyOf(SOUNDS);
   }

   static {
      put(LevelEvent.SOUND_CLICK, "random.click", 1.0F, true);
      put(LevelEvent.SOUND_CLICK_FAIL, "random.click", 1.2F, false);
      put(LevelEvent.SOUND_LAUNCH, "random.bow", 1.2F, false);
      put(LevelEvent.SOUND_DOOR_OPEN, "random.door_open", 1.0F, true);
      put(LevelEvent.SOUND_FIZZ, "random.fizz", 1.0F, false);
      put(LevelEvent.SOUND_FUSE, "random.fuse", 1.0F, false);
      put(LevelEvent.SOUND_GHAST_WARNING, "mob.ghast.charge", 1.0F, false);
      put(LevelEvent.SOUND_GHAST_FIREBALL, "mob.ghast.fireball", 1.0F, false);
      put(LevelEvent.SOUND_BLAZE_FIREBALL, "mob.blaze.shoot", 1.0F, false);
      put(LevelEvent.SOUND_ZOMBIE_DOOR_BUMP, "mob.zombie.wood", 1.0F, false);
      put(LevelEvent.SOUND_ZOMBIE_DOOR_CRASH, "mob.zombie.woodbreak", 1.0F, false);
      put(LevelEvent.SOUND_ZOMBIE_INFECTED, "mob.zombie.unfect", 1.0F, false);
      put(LevelEvent.SOUND_ZOMBIE_CONVERTED, "mob.zombie.remedy", 1.0F, false);
      put(LevelEvent.SOUND_ENDERMAN_TELEPORT, "mob.endermen.portal", 1.0F, false);
      put(LevelEvent.SOUND_ANVIL_BROKEN, "random.anvil_break", 1.0F, false);
      put(LevelEvent.SOUND_ANVIL_USED, "random.anvil_use", 1.0F, false);
      put(LevelEvent.SOUND_ANVIL_LAND, "random.anvil_land", 1.0F, false);
      put(LevelEvent.SOUND_INFINITY_ARROW_PICKUP, "random.pop", 1.0F, false);
      put(LevelEvent.SOUND_TELEPORT_ENDERPEARL, "mob.endermen.portal", 1.0F, false);
      put(LevelEvent.SOUND_ITEMFRAME_ITEM_ADD, "block.itemframe.add_item", 1.0F, false);
      put(LevelEvent.SOUND_ITEMFRAME_BREAK, "block.itemframe.break", 1.0F, false);
      put(LevelEvent.SOUND_ITEMFRAME_PLACE, "block.itemframe.place", 1.0F, false);
      put(LevelEvent.SOUND_ITEMFRAME_ITEM_REMOVE, "block.itemframe.remove_item", 1.0F, false);
      put(LevelEvent.SOUND_ITEMFRAME_ITEM_ROTATE, "block.itemframe.rotate_item", 1.0F, false);
      put(LevelEvent.SOUND_CAMERA, "camera.take_picture", 1.0F, false);
      put(LevelEvent.SOUND_EXPERIENCE_ORB_PICKUP, "random.orb", 1.0F, false);
      put(LevelEvent.SOUND_TOTEM_USED, "random.totem", 1.0F, false);
      put(LevelEvent.SOUND_ARMOR_STAND_BREAK, "mob.armor_stand.break", 1.0F, false);
      put(LevelEvent.SOUND_ARMOR_STAND_HIT, "mob.armor_stand.hit", 1.0F, false);
      put(LevelEvent.SOUND_ARMOR_STAND_LAND, "mob.armor_stand.land", 1.0F, false);
      put(LevelEvent.SOUND_ARMOR_STAND_PLACE, "mob.armor_stand.place", 1.0F, false);
      put(LevelEvent.SOUND_POINTED_DRIPSTONE_LAND, "land.pointed_dripstone", 1.0F, false);
      put(LevelEvent.SOUND_DYE_USED, "sign.dye.use", 1.0F, false);
      put(LevelEvent.SOUND_INK_SACE_USED, "sign.ink_sac.use", 1.0F, false);
      put(LevelEvent.SOUND_AMETHYST_RESONATE, "resonate.amethyst_block", 1.0F, false);
      put(LevelEvent.CAULDRON_FILL_WATER, "cauldron.fillwater", 1.0F, false);
      put(LevelEvent.CAULDRON_TAKE_WATER, "cauldron.takewater", 1.0F, false);
      put(LevelEvent.CAULDRON_FILL_POTION, "cauldron.fillpotion", 1.0F, false);
      put(LevelEvent.CAULDRON_TAKE_POTION, "cauldron.takepotion", 1.0F, false);
      put(LevelEvent.CAULDRON_ADD_DYE, "cauldron.adddye", 1.0F, false);
      put(LevelEvent.CAULDRON_DYE_ARMOR, "cauldron.dyearmor", 1.0F, false);
      put(LevelEvent.CAULDRON_CLEAN_ARMOR, "cauldron.cleanarmor", 1.0F, false);
      put(LevelEvent.CAULDRON_CLEAN_BANNER, "cauldron.cleanbanner", 1.0F, false);
      put(LevelEvent.CAULDRON_EXPLODE, "cauldron.explode", 1.0F, false);
      put(LevelEvent.CAULDRON_FILL_LAVA, "bucket.empty_lava", 1.0F, false);
      put(LevelEvent.CAULDRON_TAKE_LAVA, "bucket.fill_lava", 1.0F, false);
      put(LevelEvent.CAULDRON_FILL_POWDER_SNOW, "bucket.empty_powder_snow", 1.0F, false);
      put(LevelEvent.CAULDRON_TAKE_POWDER_SNOW, "bucket.fill_powder_snow", 1.0F, false);
      put(LevelEvent.ANIMATION_VAULT_ACTIVATE, "vault.activate", 1.0F, false);
      put(LevelEvent.ANIMATION_VAULT_DEACTIVATE, "vault.deactivate", 1.0F, false);
      put(LevelEvent.ANIMATION_VAULT_EJECT_ITEM, "vault.eject_item", 1.0F, false);
      put(LevelEvent.SCULK_CATALYST_BLOOM, "bloom.sculk_catalyst", 1.0F, false);
   }

   public record Sound(String name, float pitch, boolean pitchFromData) {
      public float pitch(int data) {
         return this.pitchFromData && data > 0 ? data / 1000.0F : this.pitch;
      }
   }
}
