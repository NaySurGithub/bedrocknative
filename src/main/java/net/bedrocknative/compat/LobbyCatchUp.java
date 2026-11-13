package net.bedrocknative.compat;

public final class LobbyCatchUp {
   public static final int MAX_CATCHES = 4;
   public static final long WINDOW_MS = 10000L;

   private LobbyCatchUp() {
   }

   public static LobbyCatchUp.Action decide(
      LobbyCatchUp.Kind kind, boolean inWorld, boolean overlay, boolean lobbyOn, boolean definitionsReady, boolean triedThisScreen, int recentCatches
   ) {
      if (kind == LobbyCatchUp.Kind.OTHER || kind == LobbyCatchUp.Kind.LOBBY || inWorld || !lobbyOn || triedThisScreen || recentCatches >= 4) {
         return LobbyCatchUp.Action.NONE;
      } else if (!overlay && definitionsReady) {
         return switch (kind) {
            case JAVA_TITLE -> LobbyCatchUp.Action.START;
            case JAVA_MULTIPLAYER -> LobbyCatchUp.Action.PLAY_SERVERS;
            case JAVA_WORLDS -> LobbyCatchUp.Action.PLAY_WORLDS;
            default -> LobbyCatchUp.Action.NONE;
         };
      } else {
         return LobbyCatchUp.Action.WAIT;
      }
   }

   public static enum Action {
      NONE,
      WAIT,
      START,
      PLAY_SERVERS,
      PLAY_WORLDS;
   }

   public static enum Kind {
      JAVA_TITLE,
      JAVA_MULTIPLAYER,
      JAVA_WORLDS,
      LOBBY,
      OTHER;
   }
}
