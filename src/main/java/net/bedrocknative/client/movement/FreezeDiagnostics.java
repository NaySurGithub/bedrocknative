package net.bedrocknative.client.movement;

import java.util.Collection;
import java.util.Set;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.movement.FreezeWatch;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.cloudburstmc.protocol.bedrock.packet.MobEffectPacket;

public final class FreezeDiagnostics {
   private static final FreezeWatch WATCH = new FreezeWatch(System::currentTimeMillis, line -> BedrockNative.LOGGER.info("Freeze watch: {}", line));

   private FreezeDiagnostics() {
   }

   static void reset() {
      WATCH.reset();
   }

   public static void flags(Set<EntityFlag> flags) {
      WATCH.flags(flags.contains(EntityFlag.NO_AI), flags.contains(EntityFlag.HAS_GRAVITY), flags.contains(EntityFlag.HAS_COLLISION));
   }

   public static void inputLocks(int raw) {
      WATCH.inputLocks(raw);
   }

   public static void abilities(Collection<String> on, float walkSpeed, float flySpeed, float verticalFlySpeed) {
      WATCH.abilities(on, walkSpeed, flySpeed, verticalFlySpeed);
   }

   public static void attribute(String name, float value) {
      WATCH.attribute(name, value);
   }

   public static void effect(MobEffectPacket packet) {
      if (packet.getEvent() != null) {
         WATCH.effect(packet.getEffectId(), packet.getEvent().name(), packet.getAmplifier(), packet.getDuration());
      }
   }

   public static void motion(String packet, Vector3f motion, long tick, String outcome) {
      if (motion != null) {
         WATCH.motion(packet, motion.getX(), motion.getY(), motion.getZ(), tick, outcome);
      }
   }

   public static String motionOutcome() {
      return BedrockMovement.immobile() ? "not moved: held still by NO_AI (velocity cleared at the tick)" : "applied as velocity";
   }
}
