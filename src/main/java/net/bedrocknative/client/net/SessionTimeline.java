package net.bedrocknative.client.net;

import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.perf.FrameClock;
import net.bedrocknative.perf.PhaseClock;
import org.jspecify.annotations.Nullable;

public final class SessionTimeline {
   public static final String CONNECT = "connect";
   public static final String ADDRESS = "address";
   public static final String PING = "ping";
   public static final String ACCOUNT = "account";
   public static final String TRANSPORT = "transport";
   public static final String LOGIN = "login";
   public static final String PACKS = "packs";
   public static final String TEXTURES_PREPARED = "textures prepared";
   public static final String TEXTURES_UNCHANGED = "textures unchanged";
   public static final String ATLASES = "atlases reloaded";
   public static final String START_GAME = "StartGame";
   public static final String REGISTRIES = "registries";
   public static final String CONTENT = "content";
   public static final String JAVA_LEVEL = "Java level";
   public static final String LEVEL = "level";
   public static final String FIRST_CHUNK = "first chunk";
   public static final String SPAWN = "spawn";
   public static final String SHOWN = "shown";
   public static final String ENDED = "ended";
   public static final String RELEASED = "released";
   public static final String FONT = "font";
   public static final String PACKS_DROPPED = "packs dropped";
   public static final String LEVEL_GONE = "level gone";
   public static final String RENDER_DATA = "render data";
   public static final String START_SCREEN = "start screen";
   public static final String PLAY_SCREEN = "play screen";
   public static final String SCREEN = "screen";
   private static volatile @Nullable PhaseClock join;
   private static volatile @Nullable PhaseClock lastJoin;
   private static volatile @Nullable PhaseClock leave;
   private static volatile @Nullable PhaseClock lastLeave;
   private static volatile int joins;
   private static volatile int leaves;
   private static final long LEAVE_OPEN_MS = 30000L;

   private SessionTimeline() {
   }

   public static void joinStarted(String target) {
      PhaseClock open = leave;
      if (open != null) {
         leave = null;
         BedrockNative.LOGGER.info("Leave timeline (ended by the next join, no screen of its own): {}", open.line());
      }

      PhaseClock clock = PhaseClock.start(target);
      clock.mark("connect");
      join = clock;
   }

   public static void mark(String phase) {
      PhaseClock clock = join;
      if (clock != null) {
         clock.mark(phase);
      }
   }

   public static void joinShown() {
      PhaseClock clock = join;
      if (clock != null) {
         clock.mark("shown");
         join = null;
         lastJoin = clock;
         joins++;
         BedrockNative.LOGGER.info("Join timeline: {}", clock.line());
      }
   }

   public static void joinAbandoned() {
      join = null;
   }

   public static void leaveStarted() {
      join = null;
      PhaseClock open = leave;
      if (open == null || open.has("screen") || open.elapsedMillis() >= 30000L) {
         PhaseClock clock = PhaseClock.start("leave");
         clock.mark("ended");
         leave = clock;
      }
   }

   public static void leaveMark(String phase) {
      PhaseClock clock = leave;
      if (clock != null) {
         clock.mark(phase);
      }
   }

   public static void leaveScreen(String name, String textures) {
      PhaseClock clock = leave;
      if (clock != null && !clock.has("screen")) {
         clock.mark("screen");
         FrameClock.afterNextFrame(() -> {
            clock.mark("shown");
            leave = null;
            lastLeave = clock;
            leaves++;
            BedrockNative.LOGGER.info("Leave timeline: {} ({} drawn); Bedrock textures: {}", new Object[]{clock.line(), name, textures});
         });
      }
   }

   public static @Nullable PhaseClock lastJoin() {
      return lastJoin;
   }

   public static @Nullable PhaseClock lastLeave() {
      return lastLeave;
   }

   public static int joins() {
      return joins;
   }

   public static int leaves() {
      return leaves;
   }
}
