package net.bedrocknative.client.lobby;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.net.BedrockAddress;
import net.bedrocknative.net.BedrockPing;
import net.bedrocknative.net.LanGames;
import net.bedrocknative.ui.PlayData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.multiplayer.ServerData.State;
import net.minecraft.client.multiplayer.ServerData.Type;
import org.jspecify.annotations.Nullable;

final class LobbyServers {
   private static final int PING_TIMEOUT_MS = 2500;
   private static final int LAN_LISTEN_MS = 1500;
   private static final long LAN_FORGET_MS = 10000L;
   private static final long LAN_PAUSE_MS = 1500L;
   private final Minecraft minecraft;
   private final ServerList list;
   private final Map<String, PlayData.Status> statuses = new ConcurrentHashMap<>();
   private final Set<String> pinging = ConcurrentHashMap.newKeySet();
   private final LanGames lan = new LanGames();
   private final AtomicInteger generation = new AtomicInteger();
   private volatile boolean discovering;
   @Nullable
   private Thread discovery;

   LobbyServers(Minecraft minecraft) {
      this.minecraft = minecraft;
      this.list = new ServerList(minecraft);
      this.list.load();
   }

   int size() {
      return this.list.size();
   }

   ServerData get(int index) {
      return this.list.get(index);
   }

   List<PlayData.Server> rows() {
      List<PlayData.Server> rows = new ArrayList<>();

      for (int i = 0; i < this.list.size(); i++) {
         ServerData server = this.list.get(i);
         rows.add(new PlayData.Server(server.name, server.ip, this.statuses.getOrDefault(server.ip, PlayData.Status.UNKNOWN)));
      }

      return rows;
   }

   void pingAll(Runnable changed) {
      for (int i = 0; i < this.list.size(); i++) {
         String address = this.list.get(i).ip;
         if (this.pinging.add(address)) {
            this.statuses.putIfAbsent(address, PlayData.Status.PINGING);
            Thread.ofVirtual().name("Bedrock lobby ping").start(() -> {
               PlayData.Status status;
               try {
                  status = LobbyStatusProbe.savedServer(address, 2500);
               } catch (RuntimeException var5) {
                  status = PlayData.Status.OFFLINE;
               }

               PlayData.Status result = status;
               this.minecraft.execute(() -> {
                  this.statuses.put(address, result);
                  this.pinging.remove(address);
                  changed.run();
               });
            });
         }
      }
   }

   int add(String name, BedrockAddress address) {
      ServerData added = new ServerData(name, address.stored(), Type.OTHER);
      ServerData hidden = this.list.unhide(added.ip);
      if (hidden != null) {
         hidden.copyNameIconFrom(added);
      } else {
         this.list.add(added, false);
      }

      this.list.save();
      return this.list.size() - 1;
   }

   void edit(int index, String name, BedrockAddress address) {
      ServerData server = this.list.get(index);
      this.statuses.remove(server.ip);
      server.name = name;
      server.ip = address.stored();
      server.setState(State.INITIAL);
      this.list.save();
   }

   void remove(int index) {
      this.list.remove(this.list.get(index));
      this.list.save();
   }

   void startDiscovery(Consumer<List<PlayData.Lan>> changed) {
      if (!this.discovering) {
         this.discovering = true;
         int round = this.generation.incrementAndGet();
         this.discovery = Thread.ofVirtual().name("Bedrock lobby LAN discovery").start(() -> {
            while (this.discovering && this.generation.get() == round) {
               try {
                  BedrockPing.discover(BedrockPing.lanTargets(19132), 1500, pong -> this.lan.heard(pong, System.currentTimeMillis()));
               } catch (RuntimeException | IOException var8) {
                  BedrockNative.LOGGER.debug("Bedrock lobby: LAN discovery round failed", var8);
               }

               long now = System.currentTimeMillis();
               this.lan.expire(now, 10000L);
               List<PlayData.Lan> games = this.lanRows();
               if (this.generation.get() == round) {
                  this.minecraft.execute(() -> changed.accept(games));
               }

               try {
                  Thread.sleep(1500L);
               } catch (InterruptedException var7) {
                  return;
               }
            }
         });
      }
   }

   List<PlayData.Lan> lanRows() {
      List<PlayData.Lan> games = new ArrayList<>();

      for (LanGames.Game game : this.lan.list()) {
         BedrockPing.Pong pong = game.pong();
         games.add(new PlayData.Lan(pong.motd(), pong.levelName(), game.address(), pong.players(), pong.maxPlayers()));
      }

      return games;
   }

   void stopDiscovery() {
      this.discovering = false;
      this.generation.incrementAndGet();
      Thread thread = this.discovery;
      if (thread != null) {
         thread.interrupt();
      }

      this.discovery = null;
   }
}
