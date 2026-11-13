package net.bedrocknative.net;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LanGames {
   private final Map<String, LanGames.Game> games = new LinkedHashMap<>();

   public synchronized void heard(BedrockPing.Pong pong, long now) {
      String address = pong.from().getAddress().getHostAddress() + ":" + pong.joinPort();
      boolean loopback = pong.from().getAddress().isLoopbackAddress();
      String key = pong.guid() != 0L ? "guid:" + pong.guid() : "address:" + address;
      LanGames.Game known = this.games.get(key);
      if (known != null && (known.loopback() || !loopback)) {
         address = known.address();
         loopback = known.loopback();
      }

      this.games.put(key, new LanGames.Game(address, loopback, pong, now));
   }

   public synchronized void expire(long now, long forgetMillis) {
      this.games.values().removeIf(game -> now - game.seenAt() > forgetMillis);
   }

   public synchronized List<LanGames.Game> list() {
      List<LanGames.Game> list = new ArrayList<>(this.games.values());
      list.sort(Comparator.comparing(LanGames.Game::address));
      return list;
   }

   public record Game(String address, boolean loopback, BedrockPing.Pong pong, long seenAt) {
   }
}
