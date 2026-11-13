package net.bedrocknative.world.state;

import java.util.ArrayList;
import java.util.List;
import net.bedrocknative.content.FogDefinition;
import net.bedrocknative.content.FogDefinitions;
import org.cloudburstmc.protocol.bedrock.packet.PlayerFogPacket;
import org.jspecify.annotations.Nullable;

public final class FogStack {
   private static final int MAX_ENTRIES = 256;
   private volatile List<String> identifiers = List.of();

   public void apply(PlayerFogPacket packet) {
      List<String> stack = new ArrayList<>();

      for (String identifier : packet.getFogStack()) {
         if (identifier != null && !identifier.isBlank() && stack.size() < 256) {
            stack.add(identifier.trim());
         }
      }

      this.identifiers = List.copyOf(stack);
   }

   public List<String> identifiers() {
      return this.identifiers;
   }

   public boolean isEmpty() {
      return this.identifiers.isEmpty();
   }

   public FogDefinition.@Nullable Distance resolve(FogDefinition.Medium medium, FogDefinitions definitions) {
      for (int i = this.identifiers.size() - 1; i >= 0; i--) {
         FogDefinition fog = definitions.get(this.identifiers.get(i));
         if (fog != null) {
            FogDefinition.Distance distance = fog.distances().get(medium);
            if (distance != null) {
               return distance;
            }
         }
      }

      return null;
   }

   public void clear() {
      this.identifiers = List.of();
   }
}
