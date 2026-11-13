package net.bedrocknative.client.net;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.InputConstants.Key;
import java.lang.runtime.SwitchBootstraps;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.world.BedrockLocalPlayer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.StartTick;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityEventType;
import org.cloudburstmc.protocol.bedrock.packet.AddEntityPacket;
import org.cloudburstmc.protocol.bedrock.packet.AddPlayerPacket;
import org.cloudburstmc.protocol.bedrock.packet.AnimatePacket;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.EntityEventPacket;
import org.cloudburstmc.protocol.bedrock.packet.TextPacket;
import org.cloudburstmc.protocol.bedrock.packet.AnimatePacket.Action;
import org.jspecify.annotations.Nullable;

final class DevPvpTest {
   private static final int AX = 60;
   private static final int AY = 100;
   private static final int AZ = 20;
   private static final String BOT = "BNBot";
   private static final float EAST = -90.0F;
   private static final Pattern MOVED = Pattern.compile("moved BNBot \\((-?[0-9.]+), (-?[0-9.]+), (-?[0-9.]+)\\)");
   private static final Pattern DAMAGE = Pattern.compile("hurt BNBot damage ([0-9.]+)");
   private final Minecraft minecraft;
   private final MovementStats stats;
   private final AtomicLong ticks = new AtomicLong();
   private final AtomicInteger botHurts = new AtomicInteger();
   private final AtomicInteger botCrits = new AtomicInteger();
   private final List<DevPvpTest.Line> server = new CopyOnWriteArrayList<>();
   private final List<String> results = new ArrayList<>();
   private volatile long botRuntimeId = -1L;
   private volatile @Nullable Consumer<Minecraft> perTick;
   private boolean simulatedPlayer;
   private int failed;
   private double standKnockback = Double.NaN;
   private double normalDamage = Double.NaN;
   private volatile long teleportTick = -1L;
   private final List<Long> teleports = new CopyOnWriteArrayList<>();
   private volatile long settledTick = -1L;
   private final Map<Long, String> trace = new ConcurrentHashMap<>();
   private final Set<String> only;

   DevPvpTest(Minecraft minecraft, MovementStats stats) {
      this(minecraft, stats, Set.of());
   }

   DevPvpTest(Minecraft minecraft, MovementStats stats, Set<String> only) {
      this.minecraft = minecraft;
      this.stats = stats;
      this.only = only;
   }

   void run() {
      ClientTickEvents.START_CLIENT_TICK.register((StartTick)client -> {
         DevLoadWatch.tick(this.perTick != null);
         this.ticks.incrementAndGet();
         Consumer<Minecraft> action = this.perTick;
         if (action != null && client.player != null) {
            action.accept(client);
         }
      });
      ClientBedrockSession.devTap = this::onPacket;
      CombatTrace.reset();
      CombatTrace.enabled = true;
      this.commands(
         List.of("gamemode survival", "clear @s", "effect @s clear", "time set noon", "weather clear", "tp @s 60 101 20", "effect @s saturation 9999 255 true")
      );
      sleep(2000L);
      this.commands(
         List.of(
            fill(57, 99, 14, 76, 99, 26, "stone"),
            fill(57, 100, 14, 76, 106, 26, "air"),
            "give @s diamond_sword",
            "give @s bow",
            "give @s arrow 64",
            "give @s fishing_rod",
            "kill @e[type=husk]"
         )
      );
      this.spawnBot();
      if (!this.botPresent()) {
         BedrockNative.LOGGER.warn("Dev pvp test: no bot, scenarios skipped");
         this.finish();
      } else {
         this.scenario("reach-2.9", this::reachHit);
         this.scenario("reach-3.1", this::reachMiss);
         this.scenario("reach-edge-2.95", this::reachEdge);
         this.scenario("sprint-hit", this::sprintHit);
         this.scenario("w-tap", () -> this.sprintReset("w-tap", "r", 2));
         this.scenario("s-tap", () -> this.sprintReset("s-tap", "sr", 3));
         this.scenario("strafe-hit", this::strafeHit);
         this.scenario("jump-crit", this::jumpCrit);
         this.scenario("fast-clicking", this::fastClicking);
         this.scenario("being-hit", this::beingHit);
         this.scenario("jump-reset", this::jumpReset);
         this.scenario("overlap", this::overlap);
         this.scenario("overlap-mob", this::overlapMob);
         this.scenario("bow", this::bow);
         this.scenario("rod", this::rod);
         this.scenario("shield", this::shield);
         this.finish();
      }
   }

   private void finish() {
      this.command("scriptevent bedrocknative:pvp despawn");
      this.command("kill @e[type=husk]");
      BedrockNative.LOGGER
         .info(
            "Dev pvp test: bot {}, client sent {} attacks, {} missed swings, {} attack swings, {} stabs",
            new Object[]{
               this.simulatedPlayer ? "simulated player" : "husk",
               CombatTrace.ATTACKS.get(),
               CombatTrace.MISSED_SWINGS.get(),
               CombatTrace.ATTACK_SWINGS.get(),
               CombatTrace.STABS.get()
            }
         );
      BedrockNative.LOGGER.info("Dev pvp test: {} checks, {} failed\n{}", new Object[]{this.results.size(), this.failed, String.join("\n", this.results)});
      ClientBedrockSession.devTap = null;
      CombatTrace.enabled = false;
      this.perTick = null;
      BedrockNative.LOGGER.info("Dev pvp test: done");
      sleep(1000L);
      this.minecraft.execute(this.minecraft::stop);
   }

   private void scenario(String name, Runnable body) {
      if (this.only.isEmpty() || this.only.contains(name) || this.only.size() == 1 && this.only.contains("trace")) {
         int fpsLimit = this.call(() -> this.minecraft.options.framerateLimit().get());
         int attempt = 1;

         while (true) {
            int resultsBefore = this.results.size();
            int failedBefore = this.failed;
            DevLoadWatch.reset();
            this.play(name, body);
            boolean invalid = DevLoadWatch.overloaded();
            BedrockNative.LOGGER
               .info(
                  "Dev pvp load {}: attempt {}, largest client tick gap {} ms, largest frame {} ms, fps limit {} -> {}",
                  new Object[]{
                     name,
                     attempt,
                     String.format(Locale.ROOT, "%.0f", DevLoadWatch.maxTickMs()),
                     String.format(Locale.ROOT, "%.0f", DevLoadWatch.maxFrameMs()),
                     fpsLimit,
                     invalid ? "INVALID-LOAD (a tick gap over 80 ms: " + DevLoadWatch.late() + ")" : "valid"
                  }
               );
            if (!invalid) {
               return;
            }

            while (this.results.size() > resultsBefore && attempt == 1) {
               BedrockNative.LOGGER.info("Dev pvp test: attempt 1 discarded (INVALID-LOAD): {}", this.results.removeLast());
            }

            this.failed = failedBefore;
            if (attempt >= 2) {
               for (int i = resultsBefore; i < this.results.size(); i++) {
                  this.results
                     .set(
                        i,
                        this.results
                           .get(i)
                           .replace("Dev pvp test PASS ", "Dev pvp test INVALID-LOAD(PASS) ")
                           .replace("Dev pvp test FAIL ", "Dev pvp test INVALID-LOAD(FAIL) ")
                     );
               }

               return;
            }

            attempt++;
         }
      }
   }

   private void play(String name, Runnable body) {
      BedrockNative.LOGGER.info("Dev pvp scenario: {}", name);
      this.perTick = null;
      this.release();

      try {
         body.run();
      } catch (RuntimeException e) {
         this.check(name, false, "threw " + e);
         BedrockNative.LOGGER.error("Dev pvp scenario {} failed", name, e);
      }

      this.perTick = null;
      this.release();
      this.command("effect @s instant_health 1 4 true");
      sleep(1200L);
   }

   private void screenshot() {
      DevLoadWatch.ignoreFor(1500L);
      this.minecraft.execute(() -> Screenshot.grab(this.minecraft, false));
   }

   private void check(String name, boolean pass, String details) {
      String line = String.format(Locale.ROOT, "Dev pvp test %s %s: %s", pass ? "PASS" : "FAIL", name, details);
      if (!pass) {
         this.failed++;
      }

      this.results.add(line);
      BedrockNative.LOGGER.info(line);
   }

   private List<MovementStats.Correction> movementCorrections(long startTick) {
      return this.stats.since(startTick).stream().filter(c -> this.teleports.stream().noneMatch(t -> c.tick() >= t && c.tick() <= t + 12L)).toList();
   }

   private static String describe(List<MovementStats.Correction> corrections) {
      StringBuilder text = new StringBuilder();

      for (MovementStats.Correction c : corrections) {
         text.append(
            String.format(
               Locale.ROOT,
               "[t%d@%d dist %.3f sΔ(%.3f,%.3f,%.3f) cΔ%s ground %s] ",
               c.tick(),
               c.receivedAt(),
               c.distance(),
               c.serverDelta().x,
               c.serverDelta().y,
               c.serverDelta().z,
               c.clientDelta() == null ? "?" : String.format(Locale.ROOT, "(%.3f,%.3f,%.3f)", c.clientDelta().x, c.clientDelta().y, c.clientDelta().z),
               c.serverOnGround()
            )
         );
      }

      return text.toString().trim();
   }

   private void reachHit() {
      DevPvpTest.Reach reach = this.standAndClick(3.3);
      this.check("reach-2.9", reach.entity && reach.attacks == 1 && reach.hurts >= 1, reach.describe());
      this.normalDamage = this.lastNumber(DAMAGE, reach.sinceTick);
      this.standKnockback = this.horizontalMove(reach.sinceTick);
      String attack = CombatTrace.lastAttack;
      BedrockNative.LOGGER.info("Dev pvp test: attack as sent: {}", attack);
      this.screenshot();
      Vec3 click = vector(attack, "clickPosition");
      Vec3 eye = vector(attack, "playerPosition");
      boolean onGrownBox = click != null && Math.abs(click.x - 63.4) < 0.01;
      boolean eyeLevel = eye != null && Math.abs(eye.y - 101.62001) < 1.0E-4;
      this.check("attack-fields", onGrownBox && eyeLevel, "click " + click + " eye " + eye);
   }

   private void reachMiss() {
      DevPvpTest.Reach reach = this.standAndClick(3.5);
      this.check("reach-3.1", !reach.entity && reach.attacks == 0 && reach.missed >= 1 && reach.hurts == 0, reach.describe());
   }

   private void reachEdge() {
      DevPvpTest.Reach reach = this.standAndClick(3.35);
      this.check("reach-edge-2.95", reach.entity && reach.attacks == 1 && reach.hurts >= 1, reach.describe());
   }

   private DevPvpTest.Reach standAndClick(double centreDistance) {
      this.placeBot(60.5 + centreDistance, 100.0, 20.5, 90.0F);
      this.placeSelf(60.5, 100.0, 20.5, -90.0F, 0.0F);
      this.awaitTicks(6);
      HitResult hit = this.call(() -> this.minecraft.hitResult);
      Entity bot = this.bot();
      boolean entity = hit instanceof EntityHitResult entityHit && bot != null && entityHit.getEntity() == bot;
      double distance = hit == null ? Double.NaN : hit.getLocation().distanceTo(this.call(() -> this.minecraft.player.getEyePosition()));
      int attacks = CombatTrace.ATTACKS.get();
      int missed = CombatTrace.MISSED_SWINGS.get();
      int hurts = this.botHurts.get();
      long since = this.ticks.get();
      this.click(this.minecraft.options.keyAttack);
      this.awaitTicks(20);
      return new DevPvpTest.Reach(
         entity,
         distance,
         CombatTrace.ATTACKS.get() - attacks,
         CombatTrace.MISSED_SWINGS.get() - missed,
         this.botHurts.get() - hurts,
         since,
         this.serverLines(since)
      );
   }

   private void sprintHit() {
      this.placeBot(69.5, 100.0, 20.5, 90.0F);
      this.placeSelf(60.5, 100.0, 20.5, -90.0F, 0.0F);
      this.awaitTicks(4);
      long startTick = this.stats.lastTick();
      long since = this.ticks.get();
      int hurts = this.botHurts.get();
      double[] speedBefore = new double[]{Double.NaN};
      double[] speedAfter = new double[]{Double.NaN};
      boolean[] sprintingAfter = new boolean[]{false};
      int[] phase = new int[]{0};
      int[] after = new int[]{0};
      this.perTick = client -> {
         LocalPlayer player = client.player;
         this.keys("wr");
         Entity bot = this.bot();
         double speed = player.getDeltaMovement().horizontalDistance();
         if (phase[0] == 0 && client.hitResult instanceof EntityHitResult hit && hit.getEntity() == bot && player.isSprinting()) {
            speedBefore[0] = speed;
            KeyMapping.click(key(client.options.keyAttack));
            phase[0] = 1;
         } else if (phase[0] == 1) {
            after[0]++;
            if (after[0] == 2) {
               speedAfter[0] = speed;
            }

            if (after[0] == 4) {
               sprintingAfter[0] = player.isSprinting();
               phase[0] = 2;
            }
         }
      };
      this.awaitTicks(40);
      this.perTick = null;
      this.release();
      this.awaitTicks(15);
      List<MovementStats.Correction> corrections = this.movementCorrections(startTick);
      String lines = this.serverLines(since);
      boolean serverSprinting = lines.contains("-> BNBot sprinting true");
      this.check(
         "sprint-hit-keeps-sprint", phase[0] == 2 && sprintingAfter[0], "sprinting 4 ticks after the hit " + sprintingAfter[0] + " (phase " + phase[0] + ")"
      );
      this.check(
         "sprint-hit-no-slowdown",
         speedAfter[0] >= speedBefore[0] * 0.9,
         String.format(Locale.ROOT, "speed before %.4f, a tick after %.4f (Java: x0.6)", speedBefore[0], speedAfter[0])
      );
      double sprintKnockback = this.horizontalMove(since);
      this.check(
         "sprint-hit-server",
         this.botHurts.get() > hurts && serverSprinting,
         String.format(
            Locale.ROOT,
            "server saw a sprinting hit %s, BNBot pushed %.3f in 10 ticks (standing hit %.3f), corrections %d, server: %s",
            serverSprinting,
            sprintKnockback,
            this.standKnockback,
            corrections.size(),
            lines
         )
      );
      this.check(
         "sprint-hit-corrections",
         corrections.isEmpty(),
         corrections.size() + " corrections" + (corrections.isEmpty() ? "" : ", first " + corrections.getFirst()) + ", inputs " + CombatTrace.since(startTick)
      );
   }

   private void sprintReset(String name, String releaseKeys, int releaseTicks) {
      this.placeBot(67.5, 100.0, 20.5, 90.0F);
      this.placeSelf(60.5, 100.0, 20.5, -90.0F, 0.0F);
      this.awaitTicks(4);
      long startTick = this.stats.lastTick();
      long since = this.ticks.get();
      int starts = CombatTrace.SPRINT_STARTS.get();
      int stops = CombatTrace.SPRINT_STOPS.get();
      int[] phase = new int[]{0};
      int[] counter = new int[]{0};
      this.perTick = client -> {
         LocalPlayer player = client.player;
         Entity bot = this.bot();
         boolean onBot = client.hitResult instanceof EntityHitResult hit && hit.getEntity() == bot;
         counter[0]++;
         switch (phase[0]) {
            case 0:
               this.keys("wr");
               if (onBot && player.isSprinting()) {
                  KeyMapping.click(key(client.options.keyAttack));
                  phase[0] = 1;
                  counter[0] = 0;
               }
               break;
            case 1:
               this.keys(releaseKeys);
               if (counter[0] >= releaseTicks) {
                  phase[0] = 2;
               }
               break;
            case 2:
               this.keys("wr");
               if (counter[0] >= 12 && onBot && player.isSprinting()) {
                  KeyMapping.click(key(client.options.keyAttack));
                  phase[0] = 3;
                  counter[0] = 0;
               }
               break;
            case 3:
               this.keys("wr");
               if (counter[0] >= 4) {
                  phase[0] = 4;
               }
               break;
            default:
               this.keys("");
         }
      };
      this.awaitTicks(70);
      this.perTick = null;
      this.release();
      this.awaitTicks(15);
      String lines = this.serverLines(since);
      int sprintHits = count(lines, "-> BNBot sprinting true");
      List<MovementStats.Correction> corrections = this.movementCorrections(startTick);
      this.check(
         name,
         phase[0] == 4 && CombatTrace.SPRINT_STOPS.get() > stops && CombatTrace.SPRINT_STARTS.get() > starts && sprintHits >= 2 && corrections.isEmpty(),
         String.format(
            Locale.ROOT,
            "phase %d, sprint stops %d starts %d, server sprint hits %d, corrections %d, inputs %s, server: %s",
            phase[0],
            CombatTrace.SPRINT_STOPS.get() - stops,
            CombatTrace.SPRINT_STARTS.get() - starts,
            sprintHits,
            corrections.size(),
            CombatTrace.since(startTick),
            lines
         )
      );
   }

   private void strafeHit() {
      long startTick = this.stats.lastTick();
      long since = this.ticks.get();
      int hurts = this.botHurts.get();

      for (int pass = 0; pass < 3; pass++) {
         this.placeBot(63.1, 100.0, 20.5, 90.0F);
         this.placeSelf(60.5, 100.0, 20.5, -90.0F, 0.0F);
         this.awaitTicks(4);
         int[] counter = new int[]{0};
         String strafe = pass % 2 == 0 ? "d" : "a";
         this.perTick = client -> {
            LocalPlayer player = client.player;
            Entity bot = this.bot();
            counter[0]++;
            if (bot != null) {
               Vec3 to = bot.position().subtract(player.position());
               float yaw = (float)(Math.atan2(-to.x, to.z) * 180.0 / Math.PI);
               player.setYRot(yaw);
               player.setYHeadRot(yaw);
            }

            this.keys(counter[0] <= 8 ? strafe : "");
            if (counter[0] == 8) {
               DevPvpTest.Target aimed = this.target(client);
               BedrockNative.LOGGER.info("Dev pvp test: strafe-hit click, crosshair on BNBot {}: {}", aimed.onBot(), aimed.text());
               KeyMapping.click(key(client.options.keyAttack));
            }
         };
         this.awaitTicks(20);
         this.perTick = null;
         this.release();
         this.awaitTicks(5);
      }

      this.awaitTicks(10);
      List<MovementStats.Correction> corrections = this.movementCorrections(startTick);
      int hurt = this.botHurts.get() - hurts;
      this.check(
         "strafe-hit",
         hurt >= 2 && corrections.isEmpty(),
         String.format(
            Locale.ROOT, "server hurts %d of 3, corrections %d %s, server: %s", hurt, corrections.size(), describe(corrections), this.serverLines(since)
         )
      );
   }

   private void jumpReset() {
      double[] plain = new double[1];
      double[] jumped = new double[1];
      long startTick = this.stats.lastTick();
      long since = this.ticks.get();

      for (int round = 0; round < 2; round++) {
         boolean jump = round == 1;
         this.placeBot(62.7, 100.0, 20.5, 90.0F);
         this.placeSelf(60.5, 100.0, 20.5, -90.0F, 0.0F);
         this.awaitTicks(6);
         Vec3[] hitAt = new Vec3[1];
         Vec3[] end = new Vec3[1];
         int[] counter = new int[]{-1};
         this.perTick = client -> {
            LocalPlayer player = client.player;
            this.keys(counter[0] >= 0 && counter[0] > 4 ? "" : "w");
            client.options.keyJump.setDown(false);
            if (this.only.contains("trace")) {
               Entity traced = this.bot();
               BedrockNative.LOGGER
                  .info(
                     "Dev pvp trace jump-reset jump {} tick {} input {} player x {} y {} dx {} onGround {} hurt {} bot x {} distance {}",
                     new Object[]{
                        jump,
                        this.ticks.get(),
                        this.stats.lastTick(),
                        String.format(Locale.ROOT, "%.4f", player.getX()),
                        String.format(Locale.ROOT, "%.4f", player.getY()),
                        String.format(Locale.ROOT, "%.4f", player.getDeltaMovement().x),
                        player.onGround(),
                        player.hurtTime,
                        traced == null ? "none" : String.format(Locale.ROOT, "%.4f", traced.getX()),
                        traced == null ? "-" : String.format(Locale.ROOT, "%.4f", traced.getX() - player.getX())
                     }
                  );
            }

            if (counter[0] < 0 && player.hurtTime == 10) {
               counter[0] = 0;
               hitAt[0] = player.position();
               if (jump) {
                  client.options.keyJump.setDown(true);
               }
            } else if (counter[0] >= 0 && ++counter[0] == 10) {
               end[0] = player.position();
            }
         };
         this.command(this.simulatedPlayer ? "scriptevent bedrocknative:pvp hitme" : "damage @s 1 entity_attack entity @e[name=BNBot,c=1]");
         this.awaitTicks(25);
         this.perTick = null;
         this.release();
         double moved = hitAt[0] != null && end[0] != null ? hitAt[0].x - end[0].x : Double.NaN;
         if (jump) {
            jumped[0] = moved;
         } else {
            plain[0] = moved;
         }

         this.awaitTicks(10);
      }

      List<MovementStats.Correction> corrections = this.movementCorrections(startTick);
      this.check(
         "jump-reset",
         corrections.size() <= 2,
         String.format(
            Locale.ROOT,
            "pushed back %.3f walking into the hit, %.3f jumping on the hit tick, corrections %d (at most one per hit) %s, inputs %s, server: %s",
            plain[0],
            jumped[0],
            corrections.size(),
            describe(corrections),
            CombatTrace.since(startTick),
            this.serverLines(since)
         )
      );
   }

   private void aimAtBot(Minecraft client, LocalPlayer player) {
      Entity bot = this.bot();
      if (bot != null) {
         Vec3 to = bot.position().add(0.0, bot.getBbHeight() * 0.6, 0.0).subtract(player.getEyePosition());
         float yaw = (float)(Math.atan2(-to.x, to.z) * 180.0 / Math.PI);
         float pitch = (float)(-(Math.atan2(to.y, Math.hypot(to.x, to.z)) * 180.0 / Math.PI));
         player.setYRot(yaw);
         player.setXRot(pitch);
         player.setYHeadRot(yaw);
      }
   }

   private void jumpCrit() {
      this.placeBot(63.3, 100.0, 20.5, 90.0F);
      this.placeSelf(60.5, 100.0, 20.5, -90.0F, 0.0F);
      this.awaitTicks(6);
      long since = this.ticks.get();
      int crits = this.botCrits.get();
      int[] phase = new int[]{0};
      this.perTick = client -> {
         LocalPlayer player = client.player;
         this.aimAtBot(client, player);
         if (phase[0] == 0) {
            client.options.keyJump.setDown(true);
            phase[0] = 1;
         } else if (phase[0] == 1) {
            client.options.keyJump.setDown(false);
            if (!player.onGround() && player.getDeltaMovement().y < -0.25) {
               DevPvpTest.Target aimed = this.target(client);
               BedrockNative.LOGGER.info("Dev pvp test: jump-crit click, crosshair on BNBot {}: {}", aimed.onBot(), aimed.text());
               KeyMapping.click(key(client.options.keyAttack));
               phase[0] = 2;
            }
         }
      };
      this.awaitTicks(30);
      this.perTick = null;
      double damage = this.lastNumber(DAMAGE, since);
      String lines = this.serverLines(since);
      boolean crit = this.botCrits.get() > crits;
      this.check(
         "jump-crit",
         phase[0] == 2 && (crit || damage >= this.normalDamage * 1.4),
         String.format(
            Locale.ROOT, "crit animations %d, damage %.2f (standing hit %.2f), server: %s", this.botCrits.get() - crits, damage, this.normalDamage, lines
         )
      );
   }

   private void fastClicking() {
      this.command(fill(64, 100, 19, 64, 102, 21, "stone"));
      this.placeBot(63.7, 100.0, 20.5, 90.0F);
      this.placeSelf(60.5, 100.0, 20.5, -90.0F, 0.0F);
      this.awaitTicks(6);
      int attacks = CombatTrace.ATTACKS.get();
      int hurts = this.botHurts.get();
      long since = this.ticks.get();
      int[] clicks = new int[]{0};
      this.perTick = client -> {
         if (clicks[0] < 40) {
            KeyMapping.click(key(client.options.keyAttack));
            clicks[0]++;
         }
      };
      this.awaitTicks(50);
      this.perTick = null;
      int sent = CombatTrace.ATTACKS.get() - attacks;
      int hurt = this.botHurts.get() - hurts;
      this.check(
         "fast-clicking",
         sent >= 38 && hurt >= 3 && hurt <= 5,
         String.format(
            Locale.ROOT,
            "clicks %d, attacks sent %d, server hurts %d (expected 4 or 5 in 40 ticks), server: %s",
            clicks[0],
            sent,
            hurt,
            this.serverLines(since)
         )
      );
      this.command(fill(64, 100, 19, 64, 102, 21, "air"));
   }

   private void beingHit() {
      long startTick = this.stats.lastTick();
      long since = this.ticks.get();

      for (int i = 0; i < 4; i++) {
         this.placeBot(62.5, 100.0, 20.5, 90.0F);
         this.placeSelf(60.5, 100.0, 20.5, -90.0F, 0.0F);
         this.awaitTicks(10);
         this.command(this.simulatedPlayer ? "scriptevent bedrocknative:pvp hitme" : "damage @s 1 entity_attack entity @e[name=BNBot,c=1]");
         this.awaitTicks(25);
      }

      String lines = this.serverLines(since);
      int hits = this.simulatedPlayer ? count(lines, "hurt ") - count(lines, "hurt BNBot") : 4;
      List<MovementStats.Correction> corrections = this.movementCorrections(startTick);
      this.check(
         "being-hit",
         hits >= 3 && corrections.size() <= hits,
         String.format(
            Locale.ROOT, "hits %d, corrections %d (at most one per knockback) %s, server: %s", hits, corrections.size(), describe(corrections), lines
         )
      );
   }

   private void overlap() {
      long startTick = this.stats.lastTick();
      this.placeBot(62.5, 100.0, 20.5, 90.0F);
      this.placeSelf(62.3, 100.0, 20.5, -90.0F, 0.0F);
      this.awaitTicks(10);
      long standTick = this.stats.lastTick();
      Vec3 standStart = this.call(() -> this.minecraft.player.position());
      this.awaitTicks(40);
      Vec3 standEnd = this.call(() -> this.minecraft.player.position());
      List<MovementStats.Correction> standing = this.movementCorrections(standTick);
      this.minecraft.execute(() -> this.keys("w"));
      this.awaitTicks(30);
      this.release();
      Vec3 walkEnd = this.call(() -> this.minecraft.player.position());
      this.awaitTicks(10);
      List<MovementStats.Correction> corrections = this.movementCorrections(startTick);
      this.check(
         "overlap",
         corrections.isEmpty(),
         String.format(
            Locale.ROOT,
            "standing inside BNBot moved %.3f blocks, walking through it ended at x %.3f, corrections %d (standing %d) %s",
            standEnd.subtract(standStart).horizontalDistance(),
            walkEnd.x,
            corrections.size(),
            standing.size(),
            describe(corrections)
         )
      );
   }

   private void overlapMob() {
      long startTick = this.stats.lastTick();
      this.placeSelf(60.5, 100.0, 20.5, -90.0F, 0.0F);
      this.command("kill @e[type=cow]");
      this.command(String.format(Locale.ROOT, "summon cow %.2f %d %.2f", 60.7, 100, 20.5));
      this.awaitTicks(50);
      this.command("kill @e[type=cow]");
      List<MovementStats.Correction> corrections = this.movementCorrections(startTick);
      this.check(
         "overlap-mob",
         corrections.isEmpty(),
         String.format(Locale.ROOT, "standing inside a cow, corrections %d %s", corrections.size(), describe(corrections))
      );
   }

   private void bow() {
      this.placeBot(70.5, 100.0, 20.5, 90.0F);
      this.placeSelf(60.5, 100.0, 20.5, -90.0F, -2.0F);
      this.select(Items.BOW);
      this.awaitTicks(6);
      int hurts = this.botHurts.get();
      long since = this.ticks.get();
      this.minecraft.execute(() -> this.minecraft.options.keyUse.setDown(true));
      this.awaitTicks(25);
      this.minecraft.execute(() -> this.minecraft.options.keyUse.setDown(false));
      this.awaitTicks(30);
      String lines = this.serverLines(since);
      this.check(
         "bow",
         this.botHurts.get() > hurts || lines.contains("projectile minecraft:arrow"),
         "server hurts " + (this.botHurts.get() - hurts) + ", server: " + lines
      );
   }

   private void rod() {
      this.placeBot(65.5, 100.0, 20.5, 90.0F);
      this.placeSelf(60.5, 100.0, 20.5, -90.0F, -8.0F);
      this.select(Items.FISHING_ROD);
      this.awaitTicks(6);
      long since = this.ticks.get();
      this.click(this.minecraft.options.keyUse);
      this.awaitTicks(20);
      FishingHook hook = this.call(() -> {
         for (Entity entity : this.minecraft.level.entitiesForRendering()) {
            if (entity instanceof FishingHook found) {
               return found;
            }
         }

         return null;
      });
      boolean owned = hook != null && this.call(() -> hook.getPlayerOwner() == this.minecraft.player);
      this.screenshot();
      Vec3 before = this.botPosition();
      this.click(this.minecraft.options.keyUse);
      this.awaitTicks(20);
      Vec3 after = this.botPosition();
      double pulled = before != null && after != null ? before.x - after.x : Double.NaN;
      this.check(
         "rod",
         owned,
         String.format(
            Locale.ROOT,
            "hook %s, owner is us %s, BNBot pulled %.3f toward us after reeling, server: %s",
            hook == null ? "none" : "shown",
            owned,
            pulled,
            this.serverLines(since)
         )
      );
   }

   private void shield() {
      this.command("replaceitem entity @s slot.weapon.offhand 0 shield");
      this.select(Items.DIAMOND_SWORD);
      this.placeBot(62.5, 100.0, 20.5, 90.0F);
      this.placeSelf(60.5, 100.0, 20.5, -90.0F, 0.0F);
      this.awaitTicks(6);
      this.minecraft.execute(() -> this.minecraft.options.keyUse.setDown(true));
      this.awaitTicks(10);
      boolean usingFromUseButton = this.call(() -> this.minecraft.player.isUsingItem());
      this.minecraft.execute(() -> this.minecraft.options.keyUse.setDown(false));
      this.check("shield-use-button", !usingFromUseButton, "using an item after holding the use button: " + usingFromUseButton);
      this.minecraft.execute(() -> this.minecraft.options.keyShift.setDown(true));
      this.awaitTicks(15);
      boolean blocking = BedrockLocalPlayer.serverBlocking();
      this.screenshot();
      long since = this.ticks.get();
      if (this.simulatedPlayer) {
         this.command("scriptevent bedrocknative:pvp hitme");
      }

      this.awaitTicks(15);
      this.minecraft.execute(() -> this.minecraft.options.keyShift.setDown(false));
      String lines = this.serverLines(since);
      this.check("shield-sneak", blocking, "server BLOCKING while sneaking " + blocking + ", BNBot's hit: " + lines);
      this.command("replaceitem entity @s slot.weapon.offhand 0 air");
   }

   private void spawnBot() {
      this.command("scriptevent bedrocknative:pvp spawn 63.8 100 20.5 90");
      long started = System.currentTimeMillis();

      while (System.currentTimeMillis() - started < 8000L && this.bot() == null) {
         sleep(250L);
      }

      this.simulatedPlayer = this.bot() instanceof Player;
      if (!this.simulatedPlayer) {
         BedrockNative.LOGGER.warn("Dev pvp test: no simulated player (PvP pack or Beta APIs missing: testing/bds/install.sh --pvp); a still husk instead");
         this.command("summon husk 63.8 100 20.5 90 0 minecraft:entity_spawned BNBot");
         this.command("effect @e[name=BNBot] slowness 9999 255 true");
         this.command("effect @e[name=BNBot] weakness 9999 255 true");
         long again = System.currentTimeMillis();

         while (System.currentTimeMillis() - again < 4000L && this.bot() == null) {
            sleep(250L);
         }
      }

      Entity joined = this.bot();
      if (this.botRuntimeId < 0L && joined != null) {
         this.botRuntimeId = joined.getId();
      }

      BedrockNative.LOGGER
         .info("Dev pvp test: bot {} runtime id {} ({})", new Object[]{this.bot(), this.botRuntimeId, this.simulatedPlayer ? "simulated player" : "husk"});
   }

   private boolean botPresent() {
      return this.bot() != null;
   }

   private @Nullable Entity bot() {
      return this.call(
         () -> {
            if (this.minecraft.level == null) {
               return null;
            }

            for (Entity entity : this.minecraft.level.entitiesForRendering()) {
               if (entity != this.minecraft.player
                  && (
                     entity instanceof Player player && "BNBot".equals(player.getGameProfile().name())
                        || entity instanceof LivingEntity && entity.getType() == EntityTypes.HUSK
                  )) {
                  return entity;
               }
            }

            return null;
         }
      );
   }

   private @Nullable Vec3 botPosition() {
      Entity bot = this.bot();
      return bot == null ? null : this.call(bot::position);
   }

   private void placeBot(double x, double y, double z, float yaw) {
      if (this.simulatedPlayer) {
         this.command(String.format(Locale.ROOT, "scriptevent bedrocknative:pvp place %.3f %.3f %.3f %.1f", x, y, z, yaw));
      } else {
         this.command(String.format(Locale.ROOT, "tp @e[name=%s] %.3f %.3f %.3f %.1f 0", "BNBot", x, y, z, yaw));
         this.command("effect @e[name=BNBot] instant_damage 1 4 true");
      }

      sleep(400L);
   }

   private void placeSelf(double x, double y, double z, float yaw, float pitch) {
      this.teleportTick = this.stats.lastTick();
      this.teleports.add(this.teleportTick);
      this.command(String.format(Locale.ROOT, "tp @s %.3f %.3f %.3f %.1f %.1f", x, y, z, yaw, pitch));
      sleep(500L);
      this.minecraft.execute(() -> {
         LocalPlayer player = this.minecraft.player;
         player.setYRot(yaw);
         player.setXRot(pitch);
         player.setOldRot();
         player.setYHeadRot(yaw);
      });
      sleep(200L);
      this.settledTick = this.stats.lastTick();
   }

   private void aimAtBot(Minecraft client) {
      LocalPlayer player = client.player;
      Entity bot = this.bot();
      if (player != null && bot != null) {
         Vec3 eye = player.getEyePosition();
         AABB box = bot.getBoundingBox();
         double targetY = Math.max(box.minY + 0.3, Math.min(eye.y, box.minY + 1.5));
         double dx = bot.getX() - eye.x;
         double dz = bot.getZ() - eye.z;
         float yaw = (float)(Math.atan2(-dx, dz) * 180.0 / Math.PI);
         float pitch = (float)(-(Math.atan2(targetY - eye.y, Math.hypot(dx, dz)) * 180.0 / Math.PI));
         player.setYRot(yaw);
         player.setXRot(pitch);
         player.setOldRot();
         player.setYHeadRot(yaw);
      }
   }

   private DevPvpTest.Target target(Minecraft client) {
      LocalPlayer player = client.player;
      Entity bot = this.bot();
      if (player != null && bot != null) {
         HitResult hit = client.hitResult;
         Vec3 eye = player.getEyePosition();
         AABB box = bot.getBoundingBox();
         Optional<Vec3> own = box.inflate(0.1).clip(eye, eye.add(player.getViewVector(1.0F).scale(6.0)));
         boolean onBot = hit instanceof EntityHitResult entityHit && entityHit.getEntity() == bot;
         String crosshair = hit == null
            ? "nothing"
            : (hit instanceof EntityHitResult entityHit ? "entity " + entityHit.getEntity().getName().getString() : hit.getType().name());
         return new DevPvpTest.Target(
            onBot,
            String.format(
               Locale.ROOT,
               "eye (%.2f, %.2f, %.2f) pitch %.1f yaw %.1f, BNBot feet (%.2f, %.2f, %.2f) box y %.2f..%.2f, crosshair %s at %.2f blocks, own ray to the box grown by 0.1: %s",
               eye.x,
               eye.y,
               eye.z,
               player.getXRot(),
               player.getYRot(),
               bot.getX(),
               bot.getY(),
               bot.getZ(),
               box.minY,
               box.maxY,
               crosshair,
               hit == null ? Double.NaN : hit.getLocation().distanceTo(eye),
               own.<String>map(point -> String.format(Locale.ROOT, "hits at %.2f blocks", point.distanceTo(eye))).orElse("misses")
            )
         );
      } else {
         return new DevPvpTest.Target(false, "no player or no bot");
      }
   }

   private void note(Minecraft client) {
      LocalPlayer player = client.player;
      Entity bot = this.bot();
      if (player != null && bot != null) {
         this.trace
            .put(
               this.stats.lastTick(),
               String.format(
                  Locale.ROOT,
                  "x %.2f y %.2f ground %s hurt %d, bot x %.2f gap %.2f",
                  player.getX(),
                  player.getY(),
                  player.onGround(),
                  player.hurtTime,
                  bot.getX(),
                  bot.getX() - player.getX()
               )
            );
      }
   }

   private String report(long from) {
      List<MovementStats.Correction> all = this.stats.since(from);
      if (all.isEmpty()) {
         return "none";
      }

      StringBuilder text = new StringBuilder();

      for (MovementStats.Correction c : all) {
         String near = this.trace.get(c.receivedAt());
         text.append(
            String.format(
               Locale.ROOT,
               "[t%d@%d dist %.3f sΔ(%.3f,%.3f,%.3f) cΔ%s ground %s%s] ",
               c.tick(),
               c.receivedAt(),
               c.distance(),
               c.serverDelta().x,
               c.serverDelta().y,
               c.serverDelta().z,
               c.clientDelta() == null ? "?" : String.format(Locale.ROOT, "(%.3f,%.3f,%.3f)", c.clientDelta().x, c.clientDelta().y, c.clientDelta().z),
               c.serverOnGround(),
               near == null ? "" : " around: " + near
            )
         );
      }

      return text.toString().trim();
   }

   private void onPacket(BedrockPacket packet) {
      BedrockPacket var2 = packet;
      byte var3 = 0;

      while (true) {
         switch (net.bedrocknative.TypeSwitchHelper.index(new Class<?>[]{AddPlayerPacket.class, AddEntityPacket.class, EntityEventPacket.class, AnimatePacket.class, TextPacket.class}, var2, var3)) {
            case 0:
               AddPlayerPacket add = (AddPlayerPacket)var2;
               if (!"BNBot".equals(add.getUsername())) {
                  var3 = 1;
                  break;
               }

               this.botRuntimeId = add.getRuntimeEntityId();
               return;
            case 1:
               AddEntityPacket add2 = (AddEntityPacket)var2;
               if (!"minecraft:husk".equals(add2.getIdentifier())) {
                  var3 = 2;
                  break;
               }

               this.botRuntimeId = add2.getRuntimeEntityId();
               return;
            case 2:
               EntityEventPacket event = (EntityEventPacket)var2;
               if (event.getRuntimeEntityId() == this.botRuntimeId && event.getType() == EntityEventType.HURT) {
                  this.botHurts.incrementAndGet();
                  return;
               }

               var3 = 3;
               break;
            case 3:
               AnimatePacket animate = (AnimatePacket)var2;
               if (animate.getRuntimeEntityId() == this.botRuntimeId && animate.getAction() == Action.CRITICAL_HIT) {
                  this.botCrits.incrementAndGet();
                  return;
               }

               var3 = 4;
               break;
            case 4:
               TextPacket text = (TextPacket)var2;
               if (text.getMessage() == null || !text.getMessage().contains("[bnpvp]")) {
                  var3 = 5;
                  break;
               } else {
                  this.server.add(new DevPvpTest.Line(this.ticks.get(), text.getMessage()));
                  BedrockNative.LOGGER.info("Dev pvp server: {}", text.getMessage());
                  return;
               }
            default:
               return;
         }
      }
   }

   private String serverLines(long sinceTick) {
      List<String> lines = new ArrayList<>();

      for (DevPvpTest.Line line : this.server) {
         if (line.tick() >= sinceTick && !line.text().contains("placed ")) {
            lines.add(line.text().replace("[bnpvp] ", ""));
         }
      }

      return lines.isEmpty() ? "none" : String.join(" | ", lines);
   }

   private double lastNumber(Pattern pattern, long sinceTick) {
      double value = Double.NaN;

      for (DevPvpTest.Line line : this.server) {
         Matcher matcher = pattern.matcher(line.text());
         if (line.tick() >= sinceTick && matcher.find()) {
            value = Double.parseDouble(matcher.group(1));
         }
      }

      return value;
   }

   private double horizontalMove(long sinceTick) {
      double value = Double.NaN;

      for (DevPvpTest.Line line : this.server) {
         Matcher matcher = MOVED.matcher(line.text());
         if (line.tick() >= sinceTick && matcher.find()) {
            value = Math.hypot(Double.parseDouble(matcher.group(1)), Double.parseDouble(matcher.group(3)));
         }
      }

      return value;
   }

   private static int count(String text, String part) {
      int count = 0;

      for (int at = text.indexOf(part); at >= 0; at = text.indexOf(part, at + 1)) {
         count++;
      }

      return count;
   }

   private static @Nullable Vec3 vector(String packet, String field) {
      Matcher matcher = Pattern.compile(field + "=\\((-?[0-9.E-]+), (-?[0-9.E-]+), (-?[0-9.E-]+)\\)").matcher(packet);
      return matcher.find() ? new Vec3(Double.parseDouble(matcher.group(1)), Double.parseDouble(matcher.group(2)), Double.parseDouble(matcher.group(3))) : null;
   }

   private void keys(String keys) {
      this.minecraft.options.keyUp.setDown(keys.contains("w"));
      this.minecraft.options.keyDown.setDown(keys.contains("s"));
      this.minecraft.options.keyLeft.setDown(keys.contains("a"));
      this.minecraft.options.keyRight.setDown(keys.contains("d"));
      this.minecraft.options.keySprint.setDown(keys.contains("r"));
   }

   private void release() {
      this.minecraft.execute(() -> {
         this.keys("");
         this.minecraft.options.keyJump.setDown(false);
         this.minecraft.options.keyShift.setDown(false);
         this.minecraft.options.keyUse.setDown(false);
         this.minecraft.options.keyAttack.setDown(false);
      });
   }

   private void click(KeyMapping mapping) {
      this.minecraft.execute(() -> {
         Key key = key(mapping);
         KeyMapping.set(key, true);
         KeyMapping.click(key);
      });
      this.awaitTicks(1);
      this.minecraft.execute(() -> KeyMapping.set(key(mapping), false));
   }

   private static Key key(KeyMapping mapping) {
      return InputConstants.getKey(mapping.saveString());
   }

   private void select(Item item) {
      this.minecraft.execute(() -> {
         Inventory inventory = this.minecraft.player.getInventory();

         for (int slot = 0; slot < 9; slot++) {
            if (inventory.getItem(slot).is(item)) {
               inventory.setSelectedSlot(slot);
               return;
            }
         }

         BedrockNative.LOGGER.warn("Dev pvp test: no {} in the hotbar", item);
      });
      sleep(300L);
   }

   private void awaitTicks(int count) {
      long target = this.ticks.get() + count;
      long started = System.currentTimeMillis();

      while (this.ticks.get() < target && System.currentTimeMillis() - started < count * 200L + 2000L) {
         sleep(10L);
      }
   }

   private <T> T call(Supplier<T> supplier) {
      CompletableFuture<T> future = new CompletableFuture<>();
      this.minecraft.execute(() -> {
         try {
            future.complete(supplier.get());
         } catch (RuntimeException e) {
            future.completeExceptionally(e);
         }
      });
      return future.join();
   }

   private void commands(List<String> commands) {
      for (String command : commands) {
         this.command(command);
      }
   }

   private void command(String command) {
      DevLoadWatch.doing("command " + command);
      this.minecraft.execute(() -> this.minecraft.getConnection().sendCommand(command));
      sleep(200L);
   }

   private static String fill(int x1, int y1, int z1, int x2, int y2, int z2, String block) {
      return "fill " + x1 + " " + y1 + " " + z1 + " " + x2 + " " + y2 + " " + z2 + " " + block;
   }

   private static void sleep(long millis) {
      try {
         Thread.sleep(millis);
      } catch (InterruptedException var3) {
      }
   }

   private record Line(long tick, String text) {
   }

   private record Reach(boolean entity, double distance, int attacks, int missed, int hurts, long sinceTick, String server) {
      String describe() {
         return String.format(
            Locale.ROOT,
            "crosshair %s (%.3f blocks), attacks sent %d, missed swings %d, server hurts %d, server: %s",
            this.entity ? "on BNBot" : "not on BNBot",
            this.distance,
            this.attacks,
            this.missed,
            this.hurts,
            this.server
         );
      }
   }

   private record Target(boolean onBot, String text) {
   }
}
