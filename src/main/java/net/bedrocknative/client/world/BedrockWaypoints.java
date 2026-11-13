package net.bedrocknative.client.world;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongFunction;
import net.bedrocknative.client.rules.BedrockGameRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.waypoints.ClientWaypointManager;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.waypoints.TrackedWaypoint;
import net.minecraft.world.waypoints.Waypoint.Icon;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.LocatorBarWaypoint;
import org.cloudburstmc.protocol.bedrock.packet.LocatorBarPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerLocationPacket;
import org.cloudburstmc.protocol.bedrock.packet.LocatorBarPacket.Payload;
import org.jspecify.annotations.Nullable;

public final class BedrockWaypoints {
   private static final float MAX_COORDINATE = 3.0E7F;
   private static final long UUID_MARKER = -4696972936370716672L;
   private static final int MAX_WAYPOINTS = 4096;
   private final Minecraft minecraft;
   private final Map<Long, UUID> tracked = new HashMap<>();
   private final Map<UUID, BedrockWaypoints.Server> serverWaypoints = new HashMap<>();
   private LongFunction<@Nullable Entity> entityLookup = id -> null;
   private long localUniqueId = Long.MIN_VALUE;
   private int ticks;

   public void follow(LongFunction<@Nullable Entity> lookup) {
      this.entityLookup = lookup;
   }

   public BedrockWaypoints(Minecraft minecraft) {
      this.minecraft = minecraft;
   }

   public void start(long localUniqueId) {
      this.localUniqueId = localUniqueId;
      this.tracked.clear();
      this.serverWaypoints.clear();
   }

   public void apply(LocatorBarPacket packet) {
      ClientPacketListener connection = this.minecraft.getConnection();
      if (connection != null && packet.getWaypoints() != null && BedrockGameRules.locatorBar()) {
         for (Payload payload : packet.getWaypoints()) {
            UUID id = payload.getGroupHandle();
            if (id != null && payload.getActionFlag() != null) {
               switch (payload.getActionFlag()) {
                  case ADD:
                  case UPDATE:
                     BedrockWaypoints.Server beforex = this.serverWaypoints.get(id);
                     if ((beforex != null || this.serverWaypoints.size() < 4096) && payload.getWaypoint() != null) {
                        BedrockWaypoints.Server now = (beforex != null ? beforex : new BedrockWaypoints.Server(true, null, null, null, false, false))
                           .merged(payload.getWaypoint());
                        this.serverWaypoints.put(id, now);
                        this.show(connection.getWaypointManager(), id);
                     }
                     break;
                  case REMOVE:
                     BedrockWaypoints.Server before = this.serverWaypoints.remove(id);
                     if (before != null && before.tracked()) {
                        connection.getWaypointManager().untrackWaypoint(TrackedWaypoint.empty(id));
                     }
                  case NONE:
               }
            }
         }
      }
   }

   private void show(ClientWaypointManager manager, UUID id) {
      BedrockWaypoints.Server waypoint = this.serverWaypoints.get(id);
      if (waypoint != null) {
         Entity followed = waypoint.authority() && waypoint.entity() != null ? this.entityLookup.apply(waypoint.entity()) : null;
         Vec3i at = followed != null ? followed.blockPosition() : waypoint.at();
         if (waypoint.visible() && at != null) {
            Icon icon = new Icon();
            if (waypoint.color() != null) {
               icon.color = Optional.of(waypoint.color());
            }

            TrackedWaypoint tracked = TrackedWaypoint.setPosition(id, icon, at);
            if (waypoint.tracked()) {
               manager.updateWaypoint(tracked);
            } else {
               manager.trackWaypoint(tracked);
               this.serverWaypoints.put(id, waypoint.withTracked(true));
            }
         } else {
            if (waypoint.tracked()) {
               manager.untrackWaypoint(TrackedWaypoint.empty(id));
               this.serverWaypoints.put(id, waypoint.withTracked(false));
            }
         }
      }
   }

   public void tick() {
      if (BedrockGameRules.locatorBar()) {
         ClientPacketListener connection = this.minecraft.getConnection();
         if (!this.serverWaypoints.isEmpty() && connection != null && ++this.ticks % 10 == 0) {
            for (UUID id : List.copyOf(this.serverWaypoints.keySet())) {
               BedrockWaypoints.Server waypoint = this.serverWaypoints.get(id);
               if (waypoint != null && waypoint.authority() && waypoint.entity() != null) {
                  this.show(connection.getWaypointManager(), id);
               }
            }
         }
      } else {
         if (!this.tracked.isEmpty() || !this.serverWaypoints.isEmpty()) {
            this.clear();
         }
      }
   }

   public int serverCount() {
      return this.serverWaypoints.size();
   }

   public void apply(PlayerLocationPacket packet) {
      ClientPacketListener connection = this.minecraft.getConnection();
      if (connection != null && packet.getType() != null && packet.getTargetEntityId() != this.localUniqueId && BedrockGameRules.locatorBar()) {
         ClientWaypointManager manager = connection.getWaypointManager();
         long target = packet.getTargetEntityId();
         UUID id = this.tracked.get(target);
         switch (packet.getType()) {
            case COORDINATES:
               Vector3f at = packet.getPosition();
               if (at == null || !finite(at)) {
                  return;
               }

               Vec3i position = new Vec3i((int)Math.floor(at.getX()), (int)Math.floor(at.getY()), (int)Math.floor(at.getZ()));
               if (id == null) {
                  if (this.tracked.size() >= 4096) {
                     return;
                  }

                  id = new UUID(-4696972936370716672L, target);
                  this.tracked.put(target, id);
                  manager.trackWaypoint(TrackedWaypoint.setPosition(id, new Icon(), position));
               } else {
                  manager.updateWaypoint(TrackedWaypoint.setPosition(id, new Icon(), position));
               }
               break;
            case HIDE:
               if (id != null) {
                  manager.untrackWaypoint(TrackedWaypoint.empty(id));
                  this.tracked.remove(target);
               }
         }
      }
   }

   public void clear() {
      ClientPacketListener connection = this.minecraft.getConnection();
      if (connection != null) {
         for (UUID id : this.tracked.values()) {
            connection.getWaypointManager().untrackWaypoint(TrackedWaypoint.empty(id));
         }
      }

      if (connection != null) {
         for (UUID id : this.serverWaypoints.keySet()) {
            connection.getWaypointManager().untrackWaypoint(TrackedWaypoint.empty(id));
         }
      }

      this.serverWaypoints.clear();
      this.tracked.clear();
   }

   public int count() {
      return this.tracked.size();
   }

   static boolean finite(Vector3f v) {
      return Math.abs(v.getX()) < 3.0E7F && Math.abs(v.getY()) < 3.0E7F && Math.abs(v.getZ()) < 3.0E7F;
   }

   record Server(boolean visible, @Nullable Vec3i at, @Nullable Integer color, @Nullable Long entity, boolean authority, boolean tracked) {
      BedrockWaypoints.Server merged(LocatorBarWaypoint waypoint) {
         Vec3i position = this.at;
         if (waypoint.getWorldPosition() != null
            && waypoint.getWorldPosition().getPosition() != null
            && BedrockWaypoints.finite(waypoint.getWorldPosition().getPosition())) {
            Vector3f p = waypoint.getWorldPosition().getPosition();
            position = new Vec3i((int)Math.floor(p.getX()), (int)Math.floor(p.getY()), (int)Math.floor(p.getZ()));
         }

         return new BedrockWaypoints.Server(
            waypoint.getVisible() != null ? waypoint.getVisible() : this.visible,
            position,
            waypoint.getColor() != null ? waypoint.getColor().getRGB() & 16777215 : this.color,
            waypoint.getEntityUniqueId() != null ? waypoint.getEntityUniqueId() : this.entity,
            waypoint.getClientPositionAuthority() != null ? waypoint.getClientPositionAuthority() : this.authority,
            this.tracked
         );
      }

      BedrockWaypoints.Server withTracked(boolean now) {
         return new BedrockWaypoints.Server(this.visible, this.at, this.color, this.entity, this.authority, now);
      }
   }
}
