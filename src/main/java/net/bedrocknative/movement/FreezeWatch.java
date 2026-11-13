package net.bedrocknative.movement;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

public final class FreezeWatch {
   static final int LINES_PER_WINDOW = 12;
   static final long WINDOW_MS = 10000L;
   private static final String[] LOCK_NAMES = new String[]{
      null, "camera", "movement", null, "lateral_movement", "sneak", "jump", "mount", "dismount", "move_forward", "move_backward", "move_left", "move_right"
   };
   static final int MOVE_LOCKS = 7764;
   private static final Map<String, Float> ATTRIBUTE_DEFAULTS = Map.of(
      "minecraft:movement", 0.1F, "minecraft:underwater_movement", 0.02F, "minecraft:lava_movement", 0.02F, "minecraft:knockback_resistance", 0.0F
   );
   private static final Map<Integer, String> EFFECTS = Map.of(1, "speed", 2, "slowness", 8, "jump_boost", 25, "levitation", 27, "slow_falling");
   private static final float SPRINT_FACTOR = 1.3F;
   private final LongSupplier clock;
   private final Consumer<String> out;
   private final FreezeWatch.Limiter changes = new FreezeWatch.Limiter();
   private final FreezeWatch.Limiter motions = new FreezeWatch.Limiter();
   private boolean noAi;
   private boolean gravity = true;
   private boolean gravitySeen;
   private boolean collision = true;
   private boolean flagsSeen;
   private int locks;
   private String abilities;
   private float walkSpeed = Float.NaN;
   private final Map<String, Float> attributes = new TreeMap<>();
   private final Map<String, Integer> effects = new TreeMap<>();

   public FreezeWatch(LongSupplier clock, Consumer<String> out) {
      this.clock = clock;
      this.out = out;
   }

   public synchronized void reset() {
      this.noAi = false;
      this.gravity = true;
      this.gravitySeen = false;
      this.collision = true;
      this.flagsSeen = false;
      this.locks = 0;
      this.abilities = null;
      this.walkSpeed = Float.NaN;
      this.attributes.clear();
      this.effects.clear();
      this.changes.reset();
      this.motions.reset();
   }

   public synchronized void flags(boolean noAi, boolean gravity, boolean collision) {
      List<String> changed = new ArrayList<>();
      if (noAi != this.noAi) {
         changed.add("NO_AI " + onOff(noAi) + (noAi ? " (held still: no input, no move, no knockback)" : " (moves again)"));
      }

      if (gravity != this.gravity || !this.flagsSeen && !gravity) {
         changed.add("HAS_GRAVITY " + onOff(gravity));
      }

      if (collision != this.collision || !this.flagsSeen && !collision) {
         changed.add("HAS_COLLISION " + onOff(collision));
      }

      this.noAi = noAi;
      this.gravity = gravity;
      this.gravitySeen |= gravity;
      this.collision = collision;
      this.flagsSeen = true;
      if (!changed.isEmpty()) {
         this.change("flags " + String.join(", ", changed));
      }
   }

   public synchronized void inputLocks(int locks) {
      if (locks != this.locks) {
         int old = this.locks;
         this.locks = locks;
         this.change(String.format(Locale.ROOT, "input locks 0x%x %s -> 0x%x %s", old, lockNames(old), locks, lockNames(locks)));
      }
   }

   public synchronized void abilities(Collection<String> on, float walkSpeed, float flySpeed, float verticalFlySpeed) {
      String summary = String.format(
         Locale.ROOT, "%s walk %s fly %s vertical fly %s", new TreeSet<>(on), number(walkSpeed), number(flySpeed), number(verticalFlySpeed)
      );
      if (!summary.equals(this.abilities)) {
         String old = this.abilities;
         this.abilities = summary;
         this.walkSpeed = walkSpeed;
         this.change("abilities " + (old == null ? "" : old + " -> ") + summary);
      }
   }

   public synchronized void attribute(String name, float value) {
      Float normal = ATTRIBUTE_DEFAULTS.get(name);
      if (normal != null) {
         Float old = this.attributes.put(name, value);
         if (old == null ? value != normal : old != value && !sprintFold(old, value)) {
            this.change(String.format(Locale.ROOT, "attribute %s %s -> %s", name, old == null ? "default " + number(normal) : number(old), number(value)));
         }
      }
   }

   public synchronized void effect(int id, String event, int amplifier, int durationTicks) {
      String name = EFFECTS.get(id);
      if (name != null) {
         if ("REMOVE".equals(event)) {
            if (this.effects.remove(name) != null) {
               this.change("effect " + name + " removed");
            }
         } else {
            Integer old = this.effects.put(name, amplifier);
            if (!Objects.equals(old, amplifier) || "ADD".equals(event)) {
               this.change(String.format(Locale.ROOT, "effect %s %s amplifier %d for %d ticks", name, event, amplifier, durationTicks));
            }
         }
      }
   }

   public synchronized void motion(String packet, float x, float y, float z, long tick, String outcome) {
      if (this.held()) {
         String line = String.format(Locale.ROOT, "%s (%.4f, %.4f, %.4f) tick %d while held by %s: %s", packet, x, y, z, tick, this.heldBy(), outcome);
         String skipped = this.motions.admit(this.clock.getAsLong());
         if (skipped != null) {
            this.out.accept(line + skipped);
         }
      }
   }

   public synchronized boolean held() {
      return !this.reasons().isEmpty();
   }

   public synchronized String heldBy() {
      return String.join(", ", this.reasons());
   }

   private List<String> reasons() {
      List<String> reasons = new ArrayList<>();
      if (this.noAi) {
         reasons.add("NO_AI");
      }

      if (this.gravitySeen && !this.gravity) {
         reasons.add("no HAS_GRAVITY");
      }

      if ((this.locks & 7764) != 0) {
         reasons.add("input locks " + lockNames(this.locks & 7764));
      }

      Float movement = this.attributes.get("minecraft:movement");
      if (movement != null && movement < 0.02F) {
         reasons.add("movement " + number(movement));
      }

      if (this.walkSpeed < 0.01F) {
         reasons.add("walk speed " + number(this.walkSpeed));
      }

      Integer slowness = this.effects.get("slowness");
      if (slowness != null && slowness >= 4) {
         reasons.add("slowness " + slowness);
      }

      if (this.effects.containsKey("levitation")) {
         reasons.add("levitation " + this.effects.get("levitation"));
      }

      Integer jump = this.effects.get("jump_boost");
      if (jump != null && jump >= 128) {
         reasons.add("jump boost " + jump);
      }

      return reasons;
   }

   private void change(String line) {
      String skipped = this.changes.admit(this.clock.getAsLong());
      if (skipped != null) {
         this.out.accept(line + skipped);
      }
   }

   private static boolean sprintFold(float old, float value) {
      return Math.abs(value - old * 1.3F) < 1.0E-4F || Math.abs(old - value * 1.3F) < 1.0E-4F;
   }

   static String lockNames(int locks) {
      List<String> names = new ArrayList<>();

      for (int bit = 0; bit < 32; bit++) {
         if ((locks & 1 << bit) != 0) {
            names.add(bit < LOCK_NAMES.length && LOCK_NAMES[bit] != null ? LOCK_NAMES[bit] : "bit" + bit);
         }
      }

      return names.toString();
   }

   private static String onOff(boolean value) {
      return value ? "on" : "off";
   }

   private static String number(float value) {
      return Float.isNaN(value) ? "?" : String.format(Locale.ROOT, "%.4f", value).replaceAll("0+$", "").replaceAll("\\.$", ".0");
   }

   private static final class Limiter {
      private long windowStart = Long.MIN_VALUE;
      private int lines;
      private int skipped;

      void reset() {
         this.windowStart = Long.MIN_VALUE;
         this.lines = 0;
         this.skipped = 0;
      }

      String admit(long now) {
         if (this.windowStart == Long.MIN_VALUE || now - this.windowStart >= 10000L) {
            this.windowStart = now;
            this.lines = 0;
         }

         if (this.lines >= 12) {
            this.skipped++;
            return null;
         } else {
            this.lines++;
            String suffix = this.skipped == 0 ? "" : " (" + this.skipped + " similar lines skipped before)";
            this.skipped = 0;
            return suffix;
         }
      }
   }
}
