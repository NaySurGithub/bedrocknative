package net.bedrocknative.movement;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.StringJoiner;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.jspecify.annotations.Nullable;

public final class CorrectionTrail {
   public static final int HISTORY = 256;
   public static final double MATCH_TOLERANCE = 0.002;
   public static final int SET_BACK_TICKS = 40;
   public static final int AFTER_JUMP_TICKS = 15;
   private static final Set<PlayerAuthInputData> QUIET = EnumSet.of(
      PlayerAuthInputData.BLOCK_BREAKING_DELAY_ENABLED,
      PlayerAuthInputData.JUMP_DOWN,
      PlayerAuthInputData.SNEAK_DOWN,
      PlayerAuthInputData.SPRINT_DOWN,
      PlayerAuthInputData.WANT_UP,
      PlayerAuthInputData.WANT_DOWN,
      PlayerAuthInputData.JUMP_CURRENT_RAW,
      PlayerAuthInputData.SNEAK_CURRENT_RAW,
      PlayerAuthInputData.JUMP_PRESSED_RAW,
      PlayerAuthInputData.JUMP_RELEASED_RAW,
      PlayerAuthInputData.SNEAK_PRESSED_RAW,
      PlayerAuthInputData.SNEAK_RELEASED_RAW
   );
   private final CorrectionTrail.Sent[] sent = new CorrectionTrail.Sent[256];
   private long newest = -1L;

   public void record(CorrectionTrail.Sent entry) {
      this.sent[index(entry.tick())] = entry;
      this.newest = entry.tick();
   }

   public void clear() {
      Arrays.fill(this.sent, null);
      this.newest = -1L;
   }

   public long newest() {
      return this.newest;
   }

   public CorrectionTrail.@Nullable Sent at(long tick) {
      if (tick >= 0L && tick <= this.newest && this.newest - tick < 256L) {
         CorrectionTrail.Sent entry = this.sent[index(tick)];
         return entry != null && entry.tick() == tick ? entry : null;
      } else {
         return null;
      }
   }

   public CorrectionTrail.@Nullable Match nearest(double x, double y, double z, int maxBack) {
      CorrectionTrail.Match best = null;

      for (long tick = this.newest; tick >= 0L && this.newest - tick < Math.min(maxBack, 256); tick--) {
         CorrectionTrail.Sent entry = this.at(tick);
         if (entry != null) {
            double distance = entry.distanceTo(x, y, z);
            if (distance <= 0.002) {
               return new CorrectionTrail.Match(tick, distance);
            }

            if (best == null || distance < best.distance()) {
               best = new CorrectionTrail.Match(tick, distance);
            }
         }
      }

      return best;
   }

   public CorrectionTrail.@Nullable Match nearestSetBack(double x, double y, double z) {
      return this.nearest(x, y, z, 40);
   }

   public int ticksSinceJump(long tick, int maxBack) {
      for (int back = 0; back < Math.min(maxBack, 256) && tick - back >= 0L; back++) {
         CorrectionTrail.Sent entry = this.at(tick - back);
         if (entry != null && entry.jumped()) {
            return back;
         }
      }

      return -1;
   }

   public String trail(long from, long to) {
      StringBuilder text = new StringBuilder(String.format(Locale.ROOT, "y %d-%d:", from, to));
      CorrectionTrail.Sent previous = null;

      for (long tick = from; tick <= to; tick++) {
         CorrectionTrail.Sent entry = this.at(tick);
         text.append(' ');
         if (entry == null) {
            text.append('?');
            previous = null;
         } else {
            text.append(String.format(Locale.ROOT, "%.4f", entry.y()));
            if (entry.jumped()) {
               text.append(entry.autoJump() ? "JA" : "J");
            }

            if (entry.onGround()) {
               text.append('g');
            }

            if (previous != null) {
               text.append(String.format(Locale.ROOT, "/%.3f", Math.hypot(entry.x() - previous.x(), entry.z() - previous.z())));
            }

            previous = entry;
         }
      }

      return text.toString();
   }

   private static int index(long tick) {
      return (int)Math.floorMod(tick, 256L);
   }

   public record Match(long tick, double distance) {
      public boolean exact() {
         return this.distance <= 0.002;
      }
   }

   public record Sent(
      long tick, double x, double y, double z, double vx, double vy, double vz, boolean onGround, Set<PlayerAuthInputData> flags, boolean autoJump
   ) {
      public Sent {
         flags = flags.isEmpty() ? EnumSet.noneOf(PlayerAuthInputData.class) : EnumSet.copyOf(flags);
      }

      public Sent(long tick, double x, double y, double z, double vx, double vy, double vz, boolean onGround, Set<PlayerAuthInputData> flags) {
         this(tick, x, y, z, vx, vy, vz, onGround, flags, false);
      }

      public boolean jumped() {
         return this.flags.contains(PlayerAuthInputData.START_JUMPING);
      }

      public String flagNames() {
         StringJoiner names = new StringJoiner(", ", "[", "]");

         for (PlayerAuthInputData flag : this.flags) {
            if (!CorrectionTrail.QUIET.contains(flag)) {
               names.add(flag.name());
            }
         }

         return names.toString();
      }

      double distanceTo(double px, double py, double pz) {
         double dx = this.x - px;
         double dy = this.y - py;
         double dz = this.z - pz;
         return Math.sqrt(dx * dx + dy * dy + dz * dz);
      }
   }
}
