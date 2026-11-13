package net.bedrocknative.particle;

import java.util.EnumMap;
import java.util.Map;
import org.cloudburstmc.protocol.bedrock.data.ParticleType;
import org.jspecify.annotations.Nullable;

public final class LegacyParticleEffects {
   private static final Map<ParticleType, String> EFFECTS = new EnumMap<>(ParticleType.class);

   private LegacyParticleEffects() {
   }

   private static void put(ParticleType type, String effect) {
      EFFECTS.put(type, effect);
   }

   public static @Nullable String of(ParticleType type) {
      return EFFECTS.get(type);
   }

   public static Map<ParticleType, String> all() {
      return Map.copyOf(EFFECTS);
   }

   static {
      put(ParticleType.BUBBLE, "minecraft:basic_bubble_particle");
      put(ParticleType.BUBBLE_MANUAL, "minecraft:basic_bubble_particle_manual");
      put(ParticleType.CRIT, "minecraft:basic_crit_particle");
      put(ParticleType.SMOKE, "minecraft:basic_smoke_particle");
      put(ParticleType.EXPLODE, "minecraft:explosion_particle");
      put(ParticleType.EVAPORATION, "minecraft:water_evaporation_manual");
      put(ParticleType.FLAME, "minecraft:basic_flame_particle");
      put(ParticleType.LAVA, "minecraft:lava_particle");
      put(ParticleType.LARGE_SMOKE, "minecraft:basic_smoke_particle");
      put(ParticleType.RED_DUST, "minecraft:redstone_wire_dust_particle");
      put(ParticleType.RISING_BORDER_DUST, "minecraft:rising_border_dust_particle");
      put(ParticleType.LARGE_EXPLODE, "minecraft:large_explosion");
      put(ParticleType.HUGE_EXPLOSION, "minecraft:huge_explosion_emitter");
      put(ParticleType.MOB_FLAME, "minecraft:mobflame_single");
      put(ParticleType.HEART, "minecraft:heart_particle");
      put(ParticleType.TOWN_AURA, "minecraft:mycelium_dust_particle");
      put(ParticleType.PORTAL, "minecraft:basic_portal_particle");
      put(ParticleType.MOB_PORTAL, "minecraft:mob_portal");
      put(ParticleType.WATER_SPLASH, "minecraft:water_splash_particle");
      put(ParticleType.WATER_SPLASH_MANUAL, "minecraft:water_splash_particle_manual");
      put(ParticleType.WATER_WAKE, "minecraft:water_wake_particle");
      put(ParticleType.DRIP_WATER, "minecraft:water_drip_particle");
      put(ParticleType.DRIP_LAVA, "minecraft:lava_drip_particle");
      put(ParticleType.DRIP_HONEY, "minecraft:honey_drip_particle");
      put(ParticleType.STALACTITE_DRIP_WATER, "minecraft:stalactite_water_drip_particle");
      put(ParticleType.STALACTITE_DRIP_LAVA, "minecraft:stalactite_lava_drip_particle");
      put(ParticleType.INK, "minecraft:ink_emitter");
      put(ParticleType.RAIN_SPLASH, "minecraft:rain_splash_particle");
      put(ParticleType.VILLAGER_ANGRY, "minecraft:villager_angry");
      put(ParticleType.VILLAGER_HAPPY, "minecraft:villager_happy");
      put(ParticleType.ENCHANTING_TABLE, "minecraft:enchanting_table_particle");
      put(ParticleType.WITCH_SPELL, "minecraft:witchspell_emitter");
      put(ParticleType.END_ROD, "minecraft:endrod");
      put(ParticleType.DRAGON_BREATH, "minecraft:dragon_breath_lingering");
      put(ParticleType.SPIT, "minecraft:llama_spit_smoke");
      put(ParticleType.TOTEM, "minecraft:totem_particle");
      put(ParticleType.BALLOON_GAS, "minecraft:balloon_gas_particle");
      put(ParticleType.SPARKLER, "minecraft:sparkler_emitter");
      put(ParticleType.CONDUIT, "minecraft:conduit_particle");
      put(ParticleType.BUBBLE_COLUMN_UP, "minecraft:bubble_column_up_particle");
      put(ParticleType.BUBBLE_COLUMN_DOWN, "minecraft:bubble_column_down_particle");
      put(ParticleType.SNEEZE, "minecraft:sneeze");
      put(ParticleType.SHULKER_BULLET, "minecraft:shulker_bullet");
      put(ParticleType.BLEACH, "minecraft:bleach");
      put(ParticleType.MYCELIUM_DUST, "minecraft:mycelium_dust_particle");
      put(ParticleType.FALLING_BORDER_DUST, "minecraft:falling_border_dust_particle");
      put(ParticleType.CAMPFIRE_SMOKE, "minecraft:campfire_smoke_particle");
      put(ParticleType.CAMPFIRE_SMOKE_TALL, "minecraft:campfire_tall_smoke_particle");
      put(ParticleType.DRAGON_BREATH_FIRE, "minecraft:dragon_breath_fire");
      put(ParticleType.DRAGON_BREATH_TRAIL, "minecraft:dragon_breath_trail");
      put(ParticleType.BLUE_FLAME, "minecraft:blue_flame_particle");
      put(ParticleType.SOUL, "minecraft:soul_particle");
      put(ParticleType.OBSIDIAN_TEAR, "minecraft:obsidian_tear_particle");
      put(ParticleType.PORTAL_REVERSE, "minecraft:portal_reverse_particle");
      put(ParticleType.SNOWFLAKE, "minecraft:snowflake_particle");
      put(ParticleType.SCULK_SENSOR_REDSTONE, "minecraft:sculk_sensor_redstone_particle");
      put(ParticleType.SPORE_BLOSSOM_SHOWER, "minecraft:spore_blossom_shower_particle");
      put(ParticleType.SPORE_BLOSSOM_AMBIENT, "minecraft:spore_blossom_ambient_particle");
      put(ParticleType.WAX, "minecraft:wax_particle");
      put(ParticleType.ELECTRIC_SPARK, "minecraft:electric_spark_particle");
      put(ParticleType.CANDLE_FLAME, "minecraft:candle_flame_particle");
      put(ParticleType.SHRIEK, "minecraft:shriek_particle");
      put(ParticleType.SCULK_SOUL, "minecraft:sculk_soul_particle");
      put(ParticleType.SONIC_EXPLOSION, "minecraft:sonic_explosion");
      put(ParticleType.CHERRY_LEAVES, "minecraft:cherry_leaves_particle");
      put(ParticleType.DUST_PLUME, "minecraft:dust_plume");
      put(ParticleType.WHITE_SMOKE, "minecraft:white_smoke_particle");
      put(ParticleType.WIND_EXPLOSION, "minecraft:wind_explosion_emitter");
      put(ParticleType.BREEZE_WIND_EXPLOSION, "minecraft:breeze_wind_explosion_emitter");
      put(ParticleType.VAULT_CONNECTION, "minecraft:vault_connection_particle");
      put(ParticleType.OMINOUS_ITEM_SPAWNER, "minecraft:ominous_spawning_particle");
      put(ParticleType.PALE_OAK_LEAVES, "minecraft:pale_oak_leaves_particle");
      put(ParticleType.EYEBLOSSOM_OPEN, "minecraft:eyeblossom_open");
      put(ParticleType.EYEBLOSSOM_CLOSE, "minecraft:eyeblossom_close");
      put(ParticleType.GREEN_FLAME, "minecraft:green_flame_particle");
      put(ParticleType.PAUSE_MOB_GROWTH, "minecraft:pause_mob_growth");
      put(ParticleType.RESET_MOB_GROWTH, "minecraft:reset_mob_growth");
      put(ParticleType.ORANGE_POPLAR_LEAVES, "minecraft:orange_poplar_leaves_particle");
      put(ParticleType.RED_POPLAR_LEAVES, "minecraft:red_poplar_leaves_particle");
      put(ParticleType.YELLOW_POPLAR_LEAVES, "minecraft:yellow_poplar_leaves_particle");
   }
}
