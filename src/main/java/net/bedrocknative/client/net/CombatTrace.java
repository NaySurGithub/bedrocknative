package net.bedrocknative.client.net;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;

final class CombatTrace {
   private static final Set<PlayerAuthInputData> COMBAT_FLAGS = Set.of(
      PlayerAuthInputData.START_SPRINTING,
      PlayerAuthInputData.STOP_SPRINTING,
      PlayerAuthInputData.MISSED_SWING,
      PlayerAuthInputData.START_JUMPING,
      PlayerAuthInputData.START_SNEAKING,
      PlayerAuthInputData.STOP_SNEAKING
   );
   private static final int RECENT = 400;
   static final AtomicInteger ATTACKS = new AtomicInteger();
   static final AtomicInteger MISSED_SWINGS = new AtomicInteger();
   static final AtomicInteger ATTACK_SWINGS = new AtomicInteger();
   static final AtomicInteger STABS = new AtomicInteger();
   static final AtomicInteger SPRINT_STARTS = new AtomicInteger();
   static final AtomicInteger SPRINT_STOPS = new AtomicInteger();
   static volatile String lastAttack = "";
   private static final Deque<String> recent = new ConcurrentLinkedDeque<>();
   private static volatile boolean attackThisTick;
   static volatile boolean enabled;

   private CombatTrace() {
   }

   static void reset() {
      ATTACKS.set(0);
      MISSED_SWINGS.set(0);
      ATTACK_SWINGS.set(0);
      STABS.set(0);
      SPRINT_STARTS.set(0);
      SPRINT_STOPS.set(0);
      lastAttack = "";
      recent.clear();
   }

   static void attacked() {
      ATTACKS.incrementAndGet();
      attackThisTick = true;
   }

   static void input(PlayerAuthInputPacket input) {
      if (enabled) {
         Set<PlayerAuthInputData> flags = input.getInputData();
         if (flags.contains(PlayerAuthInputData.START_SPRINTING)) {
            SPRINT_STARTS.incrementAndGet();
         }

         if (flags.contains(PlayerAuthInputData.STOP_SPRINTING)) {
            SPRINT_STOPS.incrementAndGet();
         }

         List<String> marked = new ArrayList<>();

         for (PlayerAuthInputData flag : flags) {
            if (COMBAT_FLAGS.contains(flag)) {
               marked.add(flag.name());
            }
         }

         boolean attack = attackThisTick;
         attackThisTick = false;
         if (!marked.isEmpty() || attack) {
            recent.add(
               input.getTick()
                  + " "
                  + String.join(",", marked)
                  + (attack ? " ATTACK" : "")
                  + (flags.contains(PlayerAuthInputData.SPRINTING) ? " (sprint key)" : "")
            );

            while (recent.size() > 400) {
               recent.poll();
            }
         }
      }
   }

   static String since(long tick) {
      List<String> lines = new ArrayList<>();

      for (String line : recent) {
         if (Long.parseLong(line.substring(0, line.indexOf(32))) >= tick) {
            lines.add(line);
         }
      }

      return lines.isEmpty() ? "none" : String.join(" | ", lines);
   }
}
