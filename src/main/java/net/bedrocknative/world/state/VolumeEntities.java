package net.bedrocknative.world.state;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.packet.AddVolumeEntityPacket;
import org.cloudburstmc.protocol.bedrock.packet.RemoveVolumeEntityPacket;
import org.jspecify.annotations.Nullable;

public final class VolumeEntities {
   public static final int MAX_VOLUMES = 4096;
   private final Map<Long, VolumeEntities.Volume> volumes = new LinkedHashMap<>();

   private static long key(int dimension, int id) {
      return (long)dimension << 32 | id & 4294967295L;
   }

   public void add(AddVolumeEntityPacket packet) {
      Vector3i min = packet.getMinBounds() != null ? packet.getMinBounds() : Vector3i.ZERO;
      Vector3i max = packet.getMaxBounds() != null ? packet.getMaxBounds() : min;
      Vector3i low = Vector3i.from(Math.min(min.getX(), max.getX()), Math.min(min.getY(), max.getY()), Math.min(min.getZ(), max.getZ()));
      Vector3i high = Vector3i.from(Math.max(min.getX(), max.getX()), Math.max(min.getY(), max.getY()), Math.max(min.getZ(), max.getZ()));
      long key = key(packet.getDimension(), packet.getId());
      if (this.volumes.containsKey(key) || this.volumes.size() < 4096) {
         this.volumes
            .put(
               key,
               new VolumeEntities.Volume(
                  packet.getId(),
                  packet.getDimension(),
                  packet.getIdentifier() == null ? "" : packet.getIdentifier(),
                  packet.getInstanceName() == null ? "" : packet.getInstanceName(),
                  low,
                  high
               )
            );
      }
   }

   public void remove(RemoveVolumeEntityPacket packet) {
      this.volumes.remove(key(packet.getDimension(), packet.getId()));
   }

   public VolumeEntities.@Nullable Volume get(int dimension, int id) {
      return this.volumes.get(key(dimension, id));
   }

   public List<VolumeEntities.Volume> inDimension(int dimension) {
      List<VolumeEntities.Volume> result = new ArrayList<>();

      for (VolumeEntities.Volume volume : this.volumes.values()) {
         if (volume.dimension() == dimension) {
            result.add(volume);
         }
      }

      return result;
   }

   public int size() {
      return this.volumes.size();
   }

   public void clear() {
      this.volumes.clear();
   }

   public record Volume(int id, int dimension, String identifier, String instanceName, Vector3i min, Vector3i max) {
      public boolean contains(int x, int y, int z) {
         return x >= this.min.getX() && x <= this.max.getX() && y >= this.min.getY() && y <= this.max.getY() && z >= this.min.getZ() && z <= this.max.getZ();
      }
   }
}
