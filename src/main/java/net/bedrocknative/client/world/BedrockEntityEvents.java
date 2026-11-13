package net.bedrocknative.client.world;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityEventType;

public final class BedrockEntityEvents {
   private static final Map<EntityEventType, Byte> JAVA = new EnumMap<>(EntityEventType.class);
   private static final Set<EntityEventType> CONSUMED = EnumSet.of(
      EntityEventType.PLAY_AMBIENT,
      EntityEventType.WITCH_DRINK_POTION,
      EntityEventType.WITCH_THROW_POTION,
      EntityEventType.PRIME_CREEPER,
      EntityEventType.PLAYER_ADD_XP_LEVELS,
      EntityEventType.AGENT_ARM_SWING,
      EntityEventType.DRAGON_FLAMING,
      EntityEventType.COMPLETE_TRADE,
      EntityEventType.REMOVE_LEASH,
      EntityEventType.CARAVAN,
      EntityEventType.CHECK_TREASURE_HUNTER_ACHIEVEMENT,
      EntityEventType.BALLOON_POP,
      EntityEventType.SUMMON_AGENT,
      EntityEventType.FINISHED_CHARGING_ITEM,
      EntityEventType.LANDED_ON_GROUND,
      EntityEventType.DRINK_MILK,
      EntityEventType.FISH_HOOK_POSITION,
      EntityEventType.FISH_HOOK_TIME,
      EntityEventType.FISH_HOOK_TEASE
   );

   private BedrockEntityEvents() {
   }

   public static byte javaEvent(EntityEventType type) {
      Byte event = JAVA.get(type);
      return event == null ? -1 : event;
   }

   public static boolean consumed(EntityEventType type) {
      return CONSUMED.contains(type);
   }

   static {
      JAVA.put(EntityEventType.ATTACK_START, (byte)4);
      JAVA.put(EntityEventType.ATTACK_STOP, (byte)5);
      JAVA.put(EntityEventType.SHAKE_WETNESS, (byte)8);
      JAVA.put(EntityEventType.SHAKE_WETNESS_STOP, (byte)56);
      JAVA.put(EntityEventType.EAT_GRASS, (byte)10);
      JAVA.put(EntityEventType.GOLEM_FLOWER_OFFER, (byte)11);
      JAVA.put(EntityEventType.GOLEM_FLOWER_WITHDRAW, (byte)34);
      JAVA.put(EntityEventType.ZOMBIE_VILLAGER_CURE, (byte)16);
      JAVA.put(EntityEventType.WITCH_HAT_MAGIC, (byte)15);
      JAVA.put(EntityEventType.SILVERFISH_MERGE_WITH_STONE, (byte)20);
      JAVA.put(EntityEventType.GUARDIAN_ATTACK_ANIMATION, (byte)21);
      JAVA.put(EntityEventType.SQUID_FLEEING, (byte)19);
      JAVA.put(EntityEventType.TREASURE_HUNT, (byte)38);
      JAVA.put(EntityEventType.KINETIC_DAMAGE_DEALT, (byte)2);
      JAVA.put(EntityEventType.PRIME_TNT_MINECART, (byte)10);
   }
}
