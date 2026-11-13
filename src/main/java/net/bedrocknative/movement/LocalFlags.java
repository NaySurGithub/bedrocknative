package net.bedrocknative.movement;

import java.util.Map;
import java.util.Set;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.jspecify.annotations.Nullable;

public final class LocalFlags {
   private LocalFlags() {
   }

   @Nullable
   public static Boolean gravity(Map<EntityFlag, Boolean> received) {
      return received.get(EntityFlag.HAS_GRAVITY);
   }

   @Nullable
   public static Boolean collision(Map<EntityFlag, Boolean> received) {
      return received.get(EntityFlag.HAS_COLLISION);
   }

   public static void merge(Set<EntityFlag> kept, Map<EntityFlag, Boolean> received) {
      received.forEach((flag, set) -> {
         if (Boolean.TRUE.equals(set)) {
            kept.add(flag);
         } else {
            kept.remove(flag);
         }
      });
   }
}
