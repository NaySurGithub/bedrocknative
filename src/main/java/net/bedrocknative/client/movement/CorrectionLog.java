package net.bedrocknative.client.movement;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.LogBudget;
import net.bedrocknative.movement.CorrectionTrail;
import net.bedrocknative.movement.InputPacing;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.CorrectPlayerMovePredictionPacket;
import org.cloudburstmc.protocol.bedrock.packet.MobEffectPacket;
import org.cloudburstmc.protocol.bedrock.packet.MotionPredictionHintsPacket;
import org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket;
import org.cloudburstmc.protocol.bedrock.packet.MovementEffectPacket;
import org.cloudburstmc.protocol.bedrock.packet.NetworkStackLatencyPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetEntityDataPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetEntityMotionPacket;
import org.cloudburstmc.protocol.bedrock.packet.StartGamePacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateAbilitiesPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateAdventureSettingsPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateAttributesPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdatePlayerGameTypePacket;
import org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket.Mode;

public final class CorrectionLog {
   public static final boolean TRACE = Boolean.getBoolean("bedrocknative.moveTrace");
   private static final int TRACE_CHARS = 700;
   private static final int TRAIL_BEFORE = 6;
   private static final int TRAIL_AFTER = 3;
   private static final double CEILING_SEARCH = 1.5;
   private final CorrectionTrail trail = new CorrectionTrail();
   private final InputPacing pacing = new InputPacing();
   private final LogBudget budget = new LogBudget(20, 3000L);
   private final Map<Mode, Integer> moves = new EnumMap<>(Mode.class);
   private final Map<String, LongAdder> serverPackets = new ConcurrentHashMap<>();
   private int jumps;
   private int autoJumps;
   private int predictions;
   private int predictionsAfterJump;
   private int setBacksAfterJump;
   private volatile boolean ended;

   public static void startGame(StartGamePacket start) {
      BedrockNative.LOGGER
         .info(
            "Movement settings: rewind history {} ticks (ours {}), server-authoritative block breaking {}",
            new Object[]{start.getRewindHistorySize(), 64, start.isServerAuthoritativeBlockBreaking()}
         );
   }

   public void sent(LocalPlayer player, long tick, Set<PlayerAuthInputData> flags, boolean autoJump) {
      Vec3 position = player.position();
      Vec3 velocity = player.getDeltaMovement();
      CorrectionTrail.Sent entry = new CorrectionTrail.Sent(
         tick,
         position.x,
         position.y,
         position.z,
         velocity.x,
         velocity.y,
         velocity.z,
         flags.contains(PlayerAuthInputData.VERTICAL_COLLISION),
         flags,
         autoJump && flags.contains(PlayerAuthInputData.START_JUMPING)
      );
      this.trail.record(entry);
      this.pacing.record(tick, System.nanoTime());
      if (entry.jumped()) {
         this.jumps++;
         if (entry.autoJump()) {
            this.autoJumps++;
         }
      }

      if (TRACE) {
         BedrockNative.LOGGER.info("Input {}: feet {} v {} {}", new Object[]{tick, vector(position), vector(velocity), entry.flagNames()});
      }
   }

   public void prediction(LocalPlayer player, long tick, Vec3 serverFeet, Vec3 serverVelocity, boolean serverGround, boolean rewound) {
      this.predictions++;
      int sinceJump = this.trail.ticksSinceJump(tick, 16);
      if (sinceJump >= 0) {
         this.predictionsAfterJump++;
      }

      if (this.budget.take(System.nanoTime())) {
         CorrectionTrail.Sent ours = this.trail.at(tick);
         StringBuilder line = new StringBuilder();
         line.append(
            String.format(
               Locale.ROOT,
               "Movement correction %d (CorrectPlayerMovePrediction) for input %d (latest %d, %s): server feet %s v %s ground %s",
               this.predictions + this.setBacks(),
               tick,
               this.trail.newest(),
               rewound ? "rewound and replayed" : "too old to rewind, set as it came",
               vector(serverFeet),
               vector(serverVelocity),
               serverGround
            )
         );
         if (ours == null) {
            line.append("; our input of that tick is not in the trail");
         } else {
            line.append(
               String.format(
                  Locale.ROOT,
                  "; ours %s v %s ground %s; off by %s (%.4f) v %s; that tick %s",
                  vector(ours.x(), ours.y(), ours.z()),
                  vector(ours.vx(), ours.vy(), ours.vz()),
                  ours.onGround(),
                  delta(serverFeet.x - ours.x(), serverFeet.y - ours.y(), serverFeet.z - ours.z()),
                  serverFeet.distanceTo(new Vec3(ours.x(), ours.y(), ours.z())),
                  delta(serverVelocity.x - ours.vx(), serverVelocity.y - ours.vy(), serverVelocity.z - ours.vz()),
                  ours.flagNames()
               )
            );
         }

         line.append("; ").append(this.jumpText(tick, sinceJump));
         Vec3 at = ours != null ? new Vec3(ours.x(), ours.y(), ours.z()) : serverFeet;
         line.append("; ").append(context(player, at));
         long to = Math.min(this.trail.newest(), tick + 3L);
         line.append("; ").append(this.trail.trail(tick - 6L, to));
         line.append("; ").append(this.pacing.spacing(Math.max(1L, tick - 6L), to));
         this.log(line);
      }
   }

   public void movePlayer(LocalPlayer player, Mode mode, Vec3 serverFeet, boolean serverGround, long serverTick) {
      this.moves.merge(mode, 1, Integer::sum);
      long newest = this.trail.newest();
      CorrectionTrail.Match match = this.trail.nearestSetBack(serverFeet.x, serverFeet.y, serverFeet.z);
      CorrectionTrail.Sent refused = match != null && match.exact() ? this.trail.at(match.tick() + 1L) : null;
      int sinceJump = refused != null ? this.trail.ticksSinceJump(refused.tick(), 16) : -1;
      if (mode == Mode.RESPAWN && sinceJump >= 0) {
         this.setBacksAfterJump++;
      }

      if (this.budget.take(System.nanoTime())) {
         StringBuilder line = new StringBuilder();
         Vec3 now = player.position();
         line.append(
            String.format(
               Locale.ROOT,
               "Movement correction %d (MovePlayer %s%s, tick %d): server feet %s ground %s; we are at %s (input %d), off by %s",
               this.predictions + this.setBacks(),
               mode,
               mode == Mode.RESPAWN ? ", a set-back" : "",
               serverTick,
               vector(serverFeet),
               serverGround,
               vector(now),
               newest,
               delta(serverFeet.x - now.x, serverFeet.y - now.y, serverFeet.z - now.z)
            )
         );
         if (match == null) {
            line.append("; nothing sent yet");
            this.log(line);
         } else {
            if (!match.exact()) {
               line.append(String.format(Locale.ROOT, "; no input of ours was there (nearest: input %d, %.4f away)", match.tick(), match.distance()));
            } else {
               line.append(String.format(Locale.ROOT, "; back to our input %d (%d inputs ago)", match.tick(), newest - match.tick()));
               if (refused == null) {
                  line.append(", the newest");
               } else {
                  CorrectionTrail.Sent before = this.trail.at(match.tick());
                  line.append(
                     String.format(
                        Locale.ROOT,
                        ", so input %d was refused: %s moved %s ground %s v %s; %s",
                        refused.tick(),
                        refused.flagNames(),
                        delta(refused.x() - before.x(), refused.y() - before.y(), refused.z() - before.z()),
                        refused.onGround(),
                        vector(refused.vx(), refused.vy(), refused.vz()),
                        this.jumpText(refused.tick(), sinceJump)
                     )
                  );
               }
            }

            CorrectionTrail.Sent around = refused != null ? refused : this.trail.at(match.tick());
            Vec3 at = around != null ? new Vec3(around.x(), around.y(), around.z()) : serverFeet;
            line.append("; ").append(context(player, at));
            long from = Math.max(0L, match.tick() - 3L);
            long to = Math.min(newest, match.tick() + 6L + 3L);
            line.append("; ").append(this.trail.trail(from, to));
            line.append("; ").append(this.pacing.spacing(Math.max(1L, from), to));
            this.log(line);
         }
      }
   }

   public static void traceServer(BedrockPacket packet, long localRuntimeId) {
      boolean ours = switch (packet) {
         case MovePlayerPacket move -> move.getRuntimeEntityId() == localRuntimeId;
         case SetEntityMotionPacket motion -> motion.getRuntimeEntityId() == localRuntimeId;
         case UpdateAttributesPacket attributes -> attributes.getRuntimeEntityId() == localRuntimeId;
         case MobEffectPacket effect -> effect.getRuntimeEntityId() == localRuntimeId;
         case SetEntityDataPacket data -> data.getRuntimeEntityId() == localRuntimeId;
         case MovementEffectPacket effect -> effect.getEntityRuntimeId() == localRuntimeId;
         case CorrectPlayerMovePredictionPacket ignored -> true;
         case UpdateAbilitiesPacket ignored -> true;
         case UpdatePlayerGameTypePacket ignored -> true;
         default -> false;
      };
      if (ours) {
         String text = packet.toString();
         BedrockNative.LOGGER.info("Server movement packet: {}", text.length() <= 700 ? text : text.substring(0, 700) + "... (" + text.length() + " chars)");
      }
   }

   public void countServer(BedrockPacket packet, long localRuntimeId) {
      boolean counted = switch (packet) {
         case NetworkStackLatencyPacket latency -> latency.isFromServer();
         case MovePlayerPacket move -> move.getRuntimeEntityId() == localRuntimeId;
         case SetEntityMotionPacket motion -> motion.getRuntimeEntityId() == localRuntimeId;
         case UpdateAttributesPacket attributes -> attributes.getRuntimeEntityId() == localRuntimeId;
         case MobEffectPacket effect -> effect.getRuntimeEntityId() == localRuntimeId;
         case SetEntityDataPacket data -> data.getRuntimeEntityId() == localRuntimeId;
         case MovementEffectPacket effect -> effect.getEntityRuntimeId() == localRuntimeId;
         case MotionPredictionHintsPacket hints -> hints.getRuntimeEntityId() == localRuntimeId;
         case CorrectPlayerMovePredictionPacket ignored -> true;
         case UpdateAbilitiesPacket ignored -> true;
         case UpdatePlayerGameTypePacket ignored -> true;
         case UpdateAdventureSettingsPacket ignored -> true;
         default -> false;
      };
      if (counted) {
         this.serverPackets.computeIfAbsent(packet.getClass().getSimpleName().replace("Packet", ""), name -> new LongAdder()).increment();
      }
   }

   public void sessionEnded() {
      if (!this.ended) {
         this.ended = true;
         if (this.pacing.inputs() > 0) {
            BedrockNative.LOGGER
               .info(
                  "Movement inputs this session: {}; {} jumps, {} of them the game's auto-jump",
                  new Object[]{this.pacing.summary(), this.jumps, this.autoJumps}
               );
            BedrockNative.LOGGER.info("Server packets about our movement this session: {}", new TreeMap<>(this.serverPackets));
         }

         int setBacks = this.setBacks();
         if (this.predictions > 0 || setBacks > 0) {
            BedrockNative.LOGGER
               .info(
                  "Movement corrections this session: {} CorrectPlayerMovePrediction ({} within {} ticks after a jump), MovePlayer {} ({} set-backs refusing an input within {} ticks after a jump), {} lines not logged",
                  new Object[]{this.predictions, this.predictionsAfterJump, 15, this.moves, this.setBacksAfterJump, 15, this.budget.takeHeld()}
               );
         }
      }
   }

   public int predictions() {
      return this.predictions;
   }

   public int setBacks() {
      return this.moves.getOrDefault(Mode.RESPAWN, 0);
   }

   private void log(StringBuilder line) {
      int held = this.budget.takeHeld();
      if (held > 0) {
         line.append(" (").append(held).append(" corrections not logged before this one)");
      }

      BedrockNative.LOGGER.info(line.toString());
   }

   private String jumpText(long tick, int sinceJump) {
      if (sinceJump < 0) {
         return "no jump in the 16 ticks before";
      }

      CorrectionTrail.Sent jump = this.trail.at(tick - sinceJump);
      return (sinceJump == 0 ? "the jump's own tick" : "jump " + sinceJump + " ticks before")
         + (jump != null && jump.autoJump() ? " (the game's auto-jump, no key)" : "");
   }

   private static String context(LocalPlayer player, Vec3 feet) {
      Level level = player.level();
      StringJoiner text = new StringJoiner(", ");
      text.add("under " + blockName(level, BlockPos.containing(feet.x, feet.y - 0.1, feet.z)));
      text.add("feet in " + blockName(level, BlockPos.containing(feet.x, feet.y + 0.001, feet.z)));
      AABB box = player.getDimensions(player.getPose()).makeBoundingBox(feet);
      AABB above = new AABB(box.minX, box.maxY, box.minZ, box.maxX, box.maxY + 1.5, box.maxZ);
      double room = Double.NaN;
      BlockPos ceiling = null;

      for (VoxelShape shape : level.getBlockCollisions(player, above)) {
         for (AABB part : shape.toAabbs()) {
            if (part.maxY > box.maxY + 1.0E-7 && part.intersects(above)) {
               double gap = Math.max(0.0, part.minY - box.maxY);
               if (Double.isNaN(room) || gap < room) {
                  room = gap;
                  ceiling = BlockPos.containing(part.getCenter().x, Math.max(part.minY, box.maxY) + 1.0E-4, part.getCenter().z);
               }
            }
         }
      }

      text.add(ceiling == null ? "head room over 1.5" : String.format(Locale.ROOT, "head room %.4f (%s)", room, blockName(level, ceiling)));
      text.add(
         "pose "
            + player.getPose().name().toLowerCase(Locale.ROOT)
            + (player.isSprinting() ? " sprinting" : "")
            + (player.isShiftKeyDown() ? " sneaking" : "")
            + (player.getAbilities().flying ? " flying" : "")
            + (player.isInWater() ? " in water" : "")
            + (player.onClimbable() ? " climbing" : "")
      );
      StringJoiner effects = new StringJoiner(" ", "[", "]");

      for (MobEffectInstance effect : player.getActiveEffects()) {
         effects.add(effect.getEffect().getRegisteredName() + " " + effect.getAmplifier());
      }

      text.add("effects " + effects);
      text.add(
         String.format(
            Locale.ROOT, "speed %.4f jump %.4f", player.getAttributeValue(Attributes.MOVEMENT_SPEED), player.getAttributeValue(Attributes.JUMP_STRENGTH)
         )
      );
      text.add("auto-jump " + (player.isAutoJumpEnabled() ? "on" : "off"));
      return text.toString();
   }

   private static String blockName(Level level, BlockPos pos) {
      return BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString();
   }

   private static String vector(Vec3 value) {
      return vector(value.x, value.y, value.z);
   }

   private static String vector(double x, double y, double z) {
      return String.format(Locale.ROOT, "(%.4f, %.4f, %.4f)", x, y, z);
   }

   private static String delta(double x, double y, double z) {
      return String.format(Locale.ROOT, "(%+.4f, %+.4f, %+.4f)", x, y, z);
   }
}
