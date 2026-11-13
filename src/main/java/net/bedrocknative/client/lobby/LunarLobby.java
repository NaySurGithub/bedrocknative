package net.bedrocknative.client.lobby;

import java.util.ArrayDeque;
import java.util.Deque;
import net.bedrocknative.client.compat.LunarCompat;
import net.bedrocknative.client.oreui.OreUi;
import net.bedrocknative.compat.LobbyCatchUp;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import org.jspecify.annotations.Nullable;

final class LunarLobby {
   private static final long NOTE_MS = 60000L;
   @Nullable
   private static Screen tried;
   private static final Deque<Long> CATCHES = new ArrayDeque<>();

   private LunarLobby() {
   }

   static LobbyCatchUp.Kind kind(@Nullable Screen screen) {
      if (screen == null) {
         return LobbyCatchUp.Kind.OTHER;
      } else if (screen instanceof LobbyScreen) {
         return LobbyCatchUp.Kind.LOBBY;
      } else if (screen instanceof TitleScreen) {
         return LobbyCatchUp.Kind.JAVA_TITLE;
      } else if (screen instanceof JoinMultiplayerScreen) {
         return LobbyCatchUp.Kind.JAVA_MULTIPLAYER;
      } else {
         return screen instanceof SelectWorldScreen ? LobbyCatchUp.Kind.JAVA_WORLDS : LobbyCatchUp.Kind.OTHER;
      }
   }

   static void tick(Minecraft minecraft) {
      Screen screen = minecraft.gui.screen();
      LobbyCatchUp.Kind kind = kind(screen);
      if (kind != LobbyCatchUp.Kind.OTHER && kind != LobbyCatchUp.Kind.LOBBY && minecraft.level == null) {
         long now = System.currentTimeMillis();

         while (!CATCHES.isEmpty() && now - CATCHES.peekFirst() > 10000L) {
            CATCHES.removeFirst();
         }

         boolean lobbyOn = BedrockLobby.enabled();
         LobbyCatchUp.Action action = LobbyCatchUp.decide(
            kind, false, minecraft.gui.overlay() != null, lobbyOn, lobbyOn && BedrockLobby.definitions() != null, screen == tried, CATCHES.size()
         );
         if (action != LobbyCatchUp.Action.NONE && action != LobbyCatchUp.Action.WAIT) {
            tried = screen;

            Screen next = switch (action) {
               case START -> BedrockLobby.start(screen);
               case PLAY_SERVERS -> OreUi.available() ? BedrockLobby.play(2, screen) : screen;
               case PLAY_WORLDS -> OreUi.available() ? BedrockLobby.play(0, screen) : screen;
               default -> screen;
            };
            if (next != screen && next instanceof LobbyScreen) {
               CATCHES.addLast(now);
               LunarCompat.diagnose(
                  "lobby",
                  30,
                  "{} showing outside a world got past the screen swap: replaced by {} ({} ms after the last session's end)",
                  screen.getClass().getName(),
                  next.getClass().getSimpleName(),
                  BedrockLobby.sinceSessionEnd()
               );
               minecraft.gui.setScreen(next);
            } else {
               LunarCompat.diagnose("lobby", 30, "{} showing outside a world: the lobby could not take it", screen.getClass().getName());
            }
         } else {
            if (action == LobbyCatchUp.Action.NONE && screen != tried && lobbyOn && CATCHES.size() >= 4) {
               LunarCompat.diagnose("lobby", 30, "{} keeps coming back over the lobby: left as it is", screen.getClass().getName());
               tried = screen;
            }
         }
      }
   }

   static void note(Screen requested, Screen result, boolean inWorld, boolean justEnded) {
      long since = BedrockLobby.sinceSessionEnd();
      if (since >= 0L && since <= 60000L && (!inWorld || justEnded)) {
         String why;
         if (result != requested) {
            why = "swapped";
         } else if (!BedrockLobby.enabled()) {
            why = "kept: the lobby is off or failed";
         } else if (kind(requested) == LobbyCatchUp.Kind.OTHER || kind(requested) == LobbyCatchUp.Kind.LOBBY) {
            why = "kept: not one of Java's lobby screens";
         } else if (requested.getClass() != TitleScreen.class
            && requested.getClass() != JoinMultiplayerScreen.class
            && requested.getClass() != SelectWorldScreen.class) {
            why = "kept: a subclass of Java's screen (the swap takes exact classes)";
         } else if (BedrockLobby.definitions() == null) {
            why = "kept: the lobby's definitions are loading";
         } else {
            why = "kept: Ore UI missing or the screen failed";
         }

         LunarCompat.diagnose(
            "screens", 40, "{} ms after the session's end: {} -> {} ({})", since, requested.getClass().getName(), result.getClass().getName(), why
         );
      }
   }
}
