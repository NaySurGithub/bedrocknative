package net.bedrocknative.client.movement;

import java.util.List;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.cloudburstmc.protocol.bedrock.packet.MobEffectPacket;
import org.jspecify.annotations.Nullable;

public final class BedrockEffects {
   private static final List<Holder<MobEffect>> BY_ID = List.of(
      MobEffects.SPEED,
      MobEffects.SLOWNESS,
      MobEffects.HASTE,
      MobEffects.MINING_FATIGUE,
      MobEffects.STRENGTH,
      MobEffects.INSTANT_HEALTH,
      MobEffects.INSTANT_DAMAGE,
      MobEffects.JUMP_BOOST,
      MobEffects.NAUSEA,
      MobEffects.REGENERATION,
      MobEffects.RESISTANCE,
      MobEffects.FIRE_RESISTANCE,
      MobEffects.WATER_BREATHING,
      MobEffects.INVISIBILITY,
      MobEffects.BLINDNESS,
      MobEffects.NIGHT_VISION,
      MobEffects.HUNGER,
      MobEffects.WEAKNESS,
      MobEffects.POISON,
      MobEffects.WITHER,
      MobEffects.HEALTH_BOOST,
      MobEffects.ABSORPTION,
      MobEffects.SATURATION,
      MobEffects.LEVITATION,
      MobEffects.POISON,
      MobEffects.CONDUIT_POWER,
      MobEffects.SLOW_FALLING,
      MobEffects.BAD_OMEN,
      MobEffects.HERO_OF_THE_VILLAGE,
      MobEffects.DARKNESS,
      MobEffects.TRIAL_OMEN,
      MobEffects.WIND_CHARGED,
      MobEffects.WEAVING,
      MobEffects.OOZING,
      MobEffects.INFESTED,
      MobEffects.RAID_OMEN,
      MobEffects.BREATH_OF_THE_NAUTILUS
   );

   private BedrockEffects() {
   }

   public static boolean apply(LocalPlayer player, MobEffectPacket packet) {
      Holder<MobEffect> effect = byId(packet.getEffectId());
      if (effect == null) {
         return false;
      }

      switch (packet.getEvent()) {
         case ADD:
         case MODIFY:
            player.forceAddEffect(
               new MobEffectInstance(effect, packet.getDuration(), packet.getAmplifier(), packet.isAmbient(), packet.isParticles(), true), null
            );
            break;
         case REMOVE:
            player.removeEffectNoUpdate(effect);
            break;
         default:
            return false;
      }

      return effect == MobEffects.JUMP_BOOST || effect == MobEffects.LEVITATION || effect == MobEffects.SLOW_FALLING;
   }

   public static int idOf(@Nullable Holder<MobEffect> effect) {
      return effect == null ? 0 : BY_ID.indexOf(effect) + 1;
   }

   public static @Nullable Holder<MobEffect> byId(int id) {
      return id >= 1 && id <= BY_ID.size() ? BY_ID.get(id - 1) : null;
   }
}
