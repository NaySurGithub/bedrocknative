package net.bedrocknative.world.state;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.cloudburstmc.protocol.bedrock.data.debugshape.DebugShape;
import org.cloudburstmc.protocol.bedrock.packet.DebugDrawerPacket;

public final class DebugShapes {
   public static final int ANY_DIMENSION = -1;
   public static final int UNDEFINED_DIMENSION = 3;
   public static final int MAX_SHAPES = 8192;
   private final Map<Long, DebugShapes.Live> shapes = new LinkedHashMap<>();

   public void apply(DebugDrawerPacket packet, long nowMillis) {
      for (DebugShape shape : packet.getShapes()) {
         if (shape != null) {
            if (shape.getType() == null) {
               this.shapes.remove(shape.getId());
            } else {
               DebugShapes.Live previous = this.shapes.get(shape.getId());
               boolean update = previous != null && previous.shape().getType() == shape.getType();
               if (update) {
                  inherit(shape, previous.shape());
               }

               Float seconds = shape.getTotalTimeLeft();
               long expires = seconds != null
                  ? (seconds > 0.0F && Float.isFinite(seconds) ? nowMillis + (long)(seconds * 1000.0F) : Long.MAX_VALUE)
                  : (update ? previous.expiresAtMillis() : Long.MAX_VALUE);
               if (previous != null || this.shapes.size() < 8192) {
                  this.shapes.put(shape.getId(), new DebugShapes.Live(shape, expires));
               }
            }
         }
      }
   }

   private static void inherit(DebugShape shape, DebugShape old) {
      if (shape.getPosition() == null) {
         shape.setPosition(old.getPosition());
      }

      if (shape.getScale() == null) {
         shape.setScale(old.getScale());
      }

      if (shape.getRotation() == null) {
         shape.setRotation(old.getRotation());
      }

      if (shape.getColor() == null) {
         shape.setColor(old.getColor());
      }

      if (shape.getAttachedToEntityId() == null) {
         shape.setAttachedToEntityId(old.getAttachedToEntityId());
      }

      if (shape.getMaximumRenderDistance() == null) {
         shape.setMaximumRenderDistance(old.getMaximumRenderDistance());
      }
   }

   public List<DebugShape> live(long nowMillis, int dimension) {
      this.shapes.values().removeIf(livex -> livex.expiresAtMillis() <= nowMillis);
      List<DebugShape> result = new ArrayList<>();

      for (DebugShapes.Live live : this.shapes.values()) {
         int shapeDimension = live.shape().getDimension();
         if (shapeDimension == -1 || shapeDimension == 3 || shapeDimension == dimension) {
            result.add(live.shape());
         }
      }

      return result;
   }

   public int size() {
      return this.shapes.size();
   }

   public void clear() {
      this.shapes.clear();
   }

   public record Live(DebugShape shape, long expiresAtMillis) {
   }
}
