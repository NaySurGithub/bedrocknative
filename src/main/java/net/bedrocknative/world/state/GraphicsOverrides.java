package net.bedrocknative.world.state;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.GraphicsOverrideParameterType;
import org.cloudburstmc.protocol.bedrock.packet.GraphicsParameterOverridePacket;
import org.jspecify.annotations.Nullable;

public final class GraphicsOverrides {
   public static final String WORLD = "";
   public static final int MAX_OVERRIDES = 4096;
   private final Map<GraphicsOverrides.Key, GraphicsOverrides.Value> values = new HashMap<>();

   public void apply(GraphicsParameterOverridePacket packet) {
      if (packet.getParameterType() != null) {
         String biome = packet.getBiomeIdentifier() == null ? "" : packet.getBiomeIdentifier();
         GraphicsOverrides.Key key = new GraphicsOverrides.Key(biome, packet.getParameterType());
         if (packet.isReset()) {
            this.values.remove(key);
         } else {
            TreeMap<Float, Vector3f> overDay = new TreeMap<>();
            if (packet.getValues() != null) {
               packet.getValues().forEach((time, colour) -> {
                  if (time != null && colour != null) {
                     overDay.put(time, colour);
                  }
               });
            }

            if (this.values.containsKey(key) || this.values.size() < 4096) {
               this.values.put(key, new GraphicsOverrides.Value(overDay, packet.getVec3Value(), packet.getFloatValue()));
            }
         }
      }
   }

   public GraphicsOverrides.@Nullable Value get(String biome, GraphicsOverrideParameterType type) {
      return this.values.get(new GraphicsOverrides.Key(biome, type));
   }

   public int size() {
      return this.values.size();
   }

   public void clear() {
      this.values.clear();
   }

   private record Key(String biome, GraphicsOverrideParameterType type) {
   }

   public record Value(TreeMap<Float, Vector3f> overDay, @Nullable Vector3f vector, @Nullable Float number) {
   }
}
