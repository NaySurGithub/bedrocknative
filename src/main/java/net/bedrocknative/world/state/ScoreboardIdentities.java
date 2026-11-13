package net.bedrocknative.world.state;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.cloudburstmc.protocol.bedrock.packet.SetScoreboardIdentityPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetScoreboardIdentityPacket.Action;
import org.cloudburstmc.protocol.bedrock.packet.SetScoreboardIdentityPacket.Entry;

public final class ScoreboardIdentities {
   public static final long UNKNOWN = Long.MIN_VALUE;
   public static final int MAX_IDENTITIES = 100000;
   private final Map<Long, Long> players = new HashMap<>();

   public List<Long> apply(SetScoreboardIdentityPacket packet) {
      List<Long> removed = new ArrayList<>();
      if (packet.getAction() == null) {
         return removed;
      } else {
         for (Entry entry : packet.getEntries()) {
            if (packet.getAction() == Action.ADD) {
               if (this.players.containsKey(entry.getScoreboardId()) || this.players.size() < 100000) {
                  this.players.put(entry.getScoreboardId(), entry.getPlayerId());
               }
            } else {
               this.players.remove(entry.getScoreboardId());
               removed.add(entry.getScoreboardId());
            }
         }

         return removed;
      }
   }

   public long playerOf(long scoreboardId) {
      return this.players.getOrDefault(scoreboardId, Long.MIN_VALUE);
   }

   public int size() {
      return this.players.size();
   }

   public void clear() {
      this.players.clear();
   }
}
