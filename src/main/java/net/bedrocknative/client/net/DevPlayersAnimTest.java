package net.bedrocknative.client.net;

import com.google.gson.JsonObject;
import java.awt.Color;
import java.lang.runtime.SwitchBootstraps;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.model.BedrockModels;
import net.bedrocknative.client.model.BedrockPlayers;
import net.bedrocknative.client.model.PlayerAnimProbe;
import net.bedrocknative.client.world.ActorOffsets;
import net.bedrocknative.model.skin.PlayerSkin;
import net.bedrocknative.model.skin.SkinClaims;
import net.bedrocknative.net.auth.BedrockSkin;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.StartTick;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Player;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataMap;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityEventType;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.data.skin.SerializedSkin;
import org.cloudburstmc.protocol.bedrock.packet.AddPlayerPacket;
import org.cloudburstmc.protocol.bedrock.packet.AnimateEntityPacket;
import org.cloudburstmc.protocol.bedrock.packet.AnimatePacket;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.EntityEventPacket;
import org.cloudburstmc.protocol.bedrock.packet.MoveEntityAbsolutePacket;
import org.cloudburstmc.protocol.bedrock.packet.MoveEntityDeltaPacket;
import org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerListPacket;
import org.cloudburstmc.protocol.bedrock.packet.RemoveEntityPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetEntityDataPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetEntityMotionPacket;
import org.cloudburstmc.protocol.bedrock.packet.TextPacket;
import org.cloudburstmc.protocol.bedrock.packet.AnimatePacket.Action;
import org.cloudburstmc.protocol.bedrock.packet.AnimatePacket.SwingSource;
import org.cloudburstmc.protocol.bedrock.packet.PlayerListPacket.Entry;
import org.jspecify.annotations.Nullable;

final class DevPlayersAnimTest {
   private static final int AX = 60;
   private static final int AY = 100;
   private static final int AZ = 20;
   private static final String BOT = "BNBot";
   private static final long FIRST_ID = 2114977792L;
   private static final Path SKIN_PACK = DevSkinPack.folder();
   private static final double WALK = 0.21585;
   private static final double SPRINT = 0.2806;
   private static final double SNEAK = 0.06475;
   private static final float SWING_TWIST = 20.0F;
   private static final int SWING_WINDOW = 9;
   private final Minecraft minecraft;
   private final Consumer<BedrockPacket> inject;
   private final List<String> results = new ArrayList<>();
   private final List<Long> injected = new ArrayList<>();
   private final AtomicLong ticks = new AtomicLong();
   @Nullable
   private volatile Consumer<Minecraft> perTick;
   private long nextId = 2114977792L;
   private int failed;
   private volatile long botRuntimeId = -1L;
   private final Map<String, Integer> botPackets = new ConcurrentHashMap<>();
   private final List<String> botSamples = new CopyOnWriteArrayList<>();
   private final List<Long> botSwingTicks = new CopyOnWriteArrayList<>();
   private final List<Long> botMoveTicks = new CopyOnWriteArrayList<>();
   private volatile boolean recordingBot;

   DevPlayersAnimTest(Minecraft minecraft, Consumer<BedrockPacket> inject) {
      this.minecraft = minecraft;
      this.inject = inject;
   }

   void run() {
      CameraType camera = this.onClient(() -> this.minecraft.options.getCameraType());
      ClientTickEvents.START_CLIENT_TICK.register((StartTick)client -> {
         this.ticks.incrementAndGet();
         Consumer<Minecraft> action = this.perTick;
         if (action != null && client.level != null && client.player != null) {
            action.accept(client);
         }
      });
      ClientBedrockSession.devTap = this::onPacket;

      try {
         if (this.waitFor(30000L, () -> BedrockModels.registry() != null)) {
            this.commands(
               "gamemode creative",
               "time set noon",
               "weather clear",
               "effect @s clear",
               "replaceitem entity @s slot.weapon.mainhand 0 air",
               String.format(Locale.ROOT, "tp @s %.2f %d %.2f -90 8", 60.5, 100, 20.5)
            );
            sleep(2000L);
            this.commands(
               fill(57, 99, 10, 72, 99, 30, "stone"),
               fill(57, 100, 10, 72, 106, 30, "air"),
               String.format(Locale.ROOT, "tp @s %.2f %d %.2f -90 8", 60.5, 100, 20.5)
            );
            this.run(() -> this.minecraft.options.setCameraType(CameraType.FIRST_PERSON));
            sleep(1500L);
            this.injectedPlayers();
            this.bnbot();
            return;
         }

         this.check("setup", false, "the model registry never loaded");
      } catch (RuntimeException var13) {
         this.check("exception", false, String.valueOf(var13));
         BedrockNative.LOGGER.error("Dev players-anim test failed with an exception", var13);
         return;
      } finally {
         this.perTick = null;
         PlayerAnimProbe.watch(Integer.MIN_VALUE);

         for (long id : this.injected) {
            RemoveEntityPacket remove = new RemoveEntityPacket();
            remove.setUniqueEntityId(id);
            this.run(() -> this.inject.accept(remove));
         }

         this.command("scriptevent bedrocknative:pvp despawn");
         ClientBedrockSession.devTap = null;
         this.run(() -> this.minecraft.options.setCameraType(camera));
         BedrockNative.LOGGER
            .info(
               "Dev players-anim test: {} of {} checks passed\n{}",
               new Object[]{this.results.size() - this.failed, this.results.size(), String.join("\n", this.results)}
            );
         BedrockNative.LOGGER.info("Dev players-anim test: done");
         sleep(1000L);
         this.minecraft.execute(this.minecraft::stop);
      }
   }

   private void injectedPlayers() {
      SerializedSkin steve = this.classic("steve.png");
      SerializedSkin persona = SkinClaims.toSerialized(BedrockSkin.defaultSteve().claims());
      if (steve != null) {
         this.walker("classic", steve, true);
      }

      this.walker("persona", persona, false);
   }

   private void walker(String label, SerializedSkin skin, boolean full) {
      DevPlayersAnimTest.Walker walker = new DevPlayersAnimTest.Walker(this.newNpc("bn_anim_" + label), 64.5, 16.0, 0.0F);
      this.inject(this.list(walker.npc, skin));
      this.inject(this.addPlayer(walker.npc, walker.x, walker.z, walker.yaw));
      this.inject(this.unlist(walker.npc));
      if (!this.waitFor(15000L, () -> this.onClient(() -> {
         Player player = this.player(walker.npc.uuid);
         return player != null && BedrockPlayers.model(player) != null && BedrockPlayers.model(player).skin().key().equals(PlayerSkin.of(skin).key());
      }))) {
         this.check(label + " skin", false, "the injected player's skin model was never built (" + this.describe(walker.npc) + ")");
      }

      int javaId = this.onClient(() -> {
         Player player = this.player(walker.npc.uuid);
         return player != null ? player.getId() : Integer.MIN_VALUE;
      });
      this.run(() -> PlayerAnimProbe.watch(javaId));
      this.awaitTicks(20);
      log("{}: {}", label, this.onClient(() -> this.describe(walker.npc)));
      DevPlayersAnimTest.Segment walk = walker.move(0.0, 0.21585, 36, null);
      this.awaitTicks(24);
      walker.place(24.0, 180.0F);
      DevPlayersAnimTest.Segment sprintBack = walker.move(0.0, -0.2806, 28, EntityFlag.SPRINTING);
      this.awaitTicks(24);
      walker.place(18.0, 0.0F);
      DevPlayersAnimTest.Segment sneak = walker.move(0.0, 0.06475, 40, EntityFlag.SNEAKING);
      this.awaitTicks(24);
      List<float[]> frames = this.onClient(PlayerAnimProbe::frames);
      DevPlayersAnimTest.Stats walking = walkStats(frames, walk);
      DevPlayersAnimTest.Stats sprinting = walkStats(frames, sprintBack);
      DevPlayersAnimTest.Stats sneaking = walkStats(frames, sneak);
      this.trace(label + " walk", frames, walk);
      this.trace(label + " sprint", frames, sprintBack);
      this.check(
         label + " walk-legs",
         walking.amplitude >= 40.0F && walking.frames > 20 && walking.formulaError < 2.0F,
         walking + " (expected amplitude " + fmt(walking.expectedAmplitude) + " deg at " + fmt(walking.expectedHz) + " Hz)"
      );
      this.check(label + " walk-stops", walking.stopTicks >= 0 && walking.stopTicks <= 14, "legs still " + walking.stopTicks + " ticks after the last move");
      this.check(
         label + " sprint-faster",
         sprinting.hz > walking.hz * 1.08F && sprinting.amplitude >= walking.amplitude,
         "sprint " + fmt(sprinting.hz) + " Hz amplitude " + fmt(sprinting.amplitude) + " against walk " + fmt(walking.hz) + " Hz " + fmt(walking.amplitude)
      );
      this.check(
         label + " sneak-pose",
         Math.abs(sneaking.rootX - 28.0F) <= 3.0F && sneaking.amplitude < walking.amplitude * 0.6F,
         "root x " + fmt(sneaking.rootX) + " (vanilla's sneaking: 28), legs " + fmt(sneaking.amplitude) + " deg against walking " + fmt(walking.amplitude)
      );
      if (full) {
         this.screenshotsWhileWalking(walker, label);
         walker.place(18.5, 0.0F);
         long swimStart = walker.flag(EntityFlag.SWIMMING, true);
         DevPlayersAnimTest.Segment swim = walker.move(0.0, 0.1, 30, null);
         this.run(() -> this.screenshot("players-anim-" + label + "-swim"));
         long swimEnd = walker.flag(EntityFlag.SWIMMING, false);
         this.awaitTicks(20);
         long glideStart = walker.flag(EntityFlag.GLIDING, true);
         this.awaitTicks(16);
         this.run(() -> this.screenshot("players-anim-" + label + "-glide"));
         long glideEnd = walker.flag(EntityFlag.GLIDING, false);
         this.awaitTicks(16);
         frames = this.onClient(PlayerAnimProbe::frames);
         float swimTop = max(frames, swimStart + 14L, swim.stop, 8);
         float swimRoot = mean(frames, swimStart + 14L, swim.stop, 22);
         float swimAfter = max(frames, swimEnd + 14L, swimEnd + 20L, 8);
         this.check(
            label + " swim-pose",
            swimTop >= 0.99F && swimRoot > 80.0F && swimAfter <= 0.01F,
            "swim amount " + fmt(swimTop) + " after 14 ticks, root x " + fmt(swimRoot) + " (vanilla's swim: 90 + pitch), " + fmt(swimAfter) + " 14 ticks after"
         );
         float glideTop = max(frames, glideStart + 11L, glideEnd, 9);
         float glideAfter = max(frames, glideEnd + 2L, glideEnd + 10L, 9);
         this.check(
            label + " glide-pose",
            glideTop >= 0.99F && glideAfter == 0.0F,
            "glide " + fmt(glideTop) + " after 11 ticks, " + fmt(glideAfter) + " after it ended"
         );
         walker.place(20.0, 90.0F);
         this.swings(label + " swing-animate-rest", walker, 10, 10, false, false);
         this.swings(label + " swing-actorevent-rest", walker, 10, 10, true, false);
         walker.place(17.0, 0.0F);
         this.swings(label + " swing-animate-walking", walker, 5, 8, false, true);
         walker.place(23.0, 180.0F);
         this.swings(label + " swing-actorevent-walking", walker, 5, 8, true, true);
         walker.place(20.0, 90.0F);
         this.backToBack(label + " swing-back-to-back", walker);
         this.animateEntity(label + " animate-entity", walker);
         this.swingAtSpawn(label + " swing-at-spawn", skin);
      }
   }

   private void animateEntity(String name, DevPlayersAnimTest.Walker walker) {
      long[] at = new long[]{-1L, -1L};
      this.perTick = client -> {
         this.perTick = null;
         at[0] = client.level.getGameTime();
         this.inject.accept(animateEntity(walker.npc.uniqueId, "animation.humanoid.celebrating", "0"));
      };
      this.awaitTicks(12);
      this.perTick = client -> {
         this.perTick = null;
         at[1] = client.level.getGameTime();
         this.inject.accept(animateEntity(walker.npc.uniqueId, "animation.humanoid.celebrating", "1"));
      };
      this.awaitTicks(12);
      List<float[]> frames = this.onClient(PlayerAnimProbe::frames);
      float playing = mean(frames, at[0] + 3L, at[1], 17);
      float after = mean(frames, at[1] + 4L, at[1] + 10L, 17);
      this.check(
         name,
         Math.abs(playing - 153.0F) < 15.0F && Math.abs(after) < 15.0F,
         String.format(Locale.ROOT, "right arm z %.1f while it plays (celebrating: 153), %.1f after its controller stopped", playing, after)
      );
   }

   private static AnimateEntityPacket animateEntity(long runtimeId, String animation, String stop) {
      AnimateEntityPacket packet = new AnimateEntityPacket();
      packet.setAnimation(animation);
      packet.setNextState("default");
      packet.setStopExpression(stop);
      packet.setStopExpressionVersion(0);
      packet.setController("bedrocknative:players_anim");
      packet.setBlendOutTime(0.0F);
      packet.getRuntimeEntityIds().add(runtimeId);
      return packet;
   }

   private void swings(String name, DevPlayersAnimTest.Walker walker, int count, int spacing, boolean actorEvent, boolean walking) {
      List<Long> at = new CopyOnWriteArrayList<>();
      int[] tick = new int[]{0};
      this.perTick = client -> {
         if (walking) {
            walker.patrol(client, 0.21585);
         }

         if (tick[0] % spacing == 2 && at.size() < count) {
            at.add(client.level.getGameTime());
            this.inject.accept((BedrockPacket)(actorEvent ? attackStart(walker.npc.uniqueId) : swing(walker.npc.uniqueId)));
         }

         if (tick[0] == 5) {
            this.screenshot("players-anim-" + name.replace(' ', '-'));
         }

         tick[0]++;
      };
      this.awaitTicks(count * spacing + 4);
      this.perTick = null;
      this.awaitTicks(11);
      List<float[]> frames = this.onClient(PlayerAnimProbe::frames);
      int shown = 0;
      StringBuilder each = new StringBuilder();

      for (long start : at) {
         float twist = maxAbs(frames, start, start + 9L, 16);
         float attack = max(frames, start, start + 9L, 7);
         boolean visible = twist >= 20.0F && attack >= 0.3F;
         shown += visible ? 1 : 0;
         each.append(String.format(Locale.ROOT, " [%s %.0f deg]", visible ? "shown" : "MISSING", twist));
      }

      this.check(name, shown == at.size() && at.size() == count, shown + " of " + at.size() + " swings shown (right arm twist, attack_time):" + each);
   }

   private void backToBack(String name, DevPlayersAnimTest.Walker walker) {
      List<Long> at = new CopyOnWriteArrayList<>();
      int[] tick = new int[]{0};
      this.perTick = client -> {
         if (tick[0] % 2 == 0 && at.size() < 10) {
            at.add(client.level.getGameTime());
            this.inject.accept(swing(walker.npc.uniqueId));
         }

         tick[0]++;
      };
      this.awaitTicks(24);
      this.perTick = null;
      this.awaitTicks(12);
      List<float[]> frames = this.onClient(PlayerAnimProbe::frames);
      long from = at.getFirst() + 2L;
      long to = at.getLast();
      int swinging = 0;
      int total = 0;

      for (float[] frame : frames) {
         if (frame[1] >= (float)from && frame[1] < (float)to) {
            total++;
            swinging += frame[7] > 0.0F ? 1 : 0;
         }
      }

      int restarts = countRises(frames, at.getFirst(), to + 6L);
      this.check(
         name,
         total > 0 && swinging >= total * 0.85F && restarts >= 4,
         String.format(
            Locale.ROOT,
            "attack_time above 0 in %d of %d frames, %d swing starts for 10 packets 2 ticks apart (a new one every 4 ticks expected)",
            swinging,
            total,
            restarts
         )
      );
   }

   private void swingAtSpawn(String name, SerializedSkin skin) {
      DevPlayersAnimTest.Npc npc = this.newNpc("bn_anim_spawn");
      long[] at = new long[]{-1L};
      float[] javaSwing = new float[]{0.0F};
      this.perTick = client -> {
         this.inject.accept(this.list(npc, skin));
         this.inject.accept(this.addPlayer(npc, 64.5, 21.5, 270.0F));
         this.inject.accept(this.unlist(npc));
         this.inject.accept(swing(npc.uniqueId));
         at[0] = client.level.getGameTime();
         Player spawned = this.player(npc.uuid);
         PlayerAnimProbe.watch(spawned != null ? spawned.getId() : Integer.MIN_VALUE);
         this.perTick = later -> {
            Player player = this.player(npc.uuid);
            if (player != null) {
               javaSwing[0] = Math.max(javaSwing[0], player.getSwingAnimation(0.5F));
            }
         };
      };
      this.awaitTicks(13);
      this.perTick = null;
      List<float[]> frames = this.onClient(PlayerAnimProbe::frames);
      float attack = max(frames, at[0], at[0] + 9L, 7);
      float twist = maxAbs(frames, at[0], at[0] + 9L, 16);
      boolean runtime = !frames.isEmpty();
      this.check(
         name,
         runtime ? attack >= 0.3F && twist >= 20.0F : javaSwing[0] >= 0.3F,
         String.format(
            Locale.ROOT,
            "attack_time up to %.2f, right arm twist %.0f deg, %d frames drawn by the runtime, Java's swing up to %.2f (%s)",
            attack,
            twist,
            frames.size(),
            javaSwing[0],
            this.onClient(() -> this.describe(npc))
         )
      );
   }

   private void screenshotsWhileWalking(DevPlayersAnimTest.Walker walker, String label) {
      walker.place(24.0, 180.0F);
      int[] tick = new int[]{0};
      this.perTick = client -> {
         walker.step(client, 0.0, -0.21585);
         tick[0]++;
         if (tick[0] >= 12 && tick[0] <= 22 && tick[0] % 2 == 0) {
            this.screenshot("players-anim-" + label + "-walk-" + tick[0]);
         }

         if (tick[0] >= 36) {
            this.perTick = null;
         }
      };
      this.awaitTicks(38);
      this.perTick = null;
      this.awaitTicks(20);
   }

   private void bnbot() {
      this.commands("kick BNBot");
      this.command(String.format(Locale.ROOT, "scriptevent bedrocknative:pvp spawn %.1f %d %.1f 0", 66.5, 100, 14.5));
      if (!this.waitFor(8000L, () -> this.bot() != null)) {
         this.check("bnbot", false, "no BNBot (the PvP pack needs testing/bds/install.sh --pvp and the world's Beta APIs)");
      } else {
         sleep(1500L);
         Entity bot = this.bot();
         if (this.botRuntimeId < 0L && bot != null) {
            this.botRuntimeId = bot.getId();
         }

         this.run(() -> PlayerAnimProbe.watch(bot.getId()));
         this.commands("gamemode survival", "effect @s resistance 9999 255 true", "effect @s regeneration 9999 255 true", "effect @s saturation 9999 255 true");
         this.botPackets.clear();
         this.botSamples.clear();
         this.botMoveTicks.clear();
         this.recordingBot = true;
         this.placeSelf();
         this.place(66.5, 14.5, 0.0F);
         long walkStart = this.gameTime();
         this.command("scriptevent bedrocknative:pvp fight 200");
         this.awaitTicks(70);
         this.command("scriptevent bedrocknative:pvp fight off");
         long walkEnd = this.gameTime();
         Map<String, Integer> walkPackets = new TreeMap<>(this.botPackets);
         this.command("scriptevent bedrocknative:pvp sprint on");
         this.placeSelf();
         this.place(66.5, 14.5, 0.0F);
         long sprintStart = this.gameTime();
         this.command("scriptevent bedrocknative:pvp fight 200");
         this.awaitTicks(60);
         this.command("scriptevent bedrocknative:pvp fight off");
         long sprintEnd = this.gameTime();
         this.command("scriptevent bedrocknative:pvp sprint off");
         this.recordingBot = false;
         List<float[]> frames = this.onClient(PlayerAnimProbe::frames);
         DevPlayersAnimTest.Segment walk = moving(frames, walkStart, walkEnd);
         DevPlayersAnimTest.Segment sprint = moving(frames, sprintStart, sprintEnd);
         DevPlayersAnimTest.Stats walking = walkStats(frames, walk);
         DevPlayersAnimTest.Stats sprinting = walkStats(frames, sprint);
         this.trace("BNBot walk", frames, walk);
         this.trace("BNBot sprint", frames, sprint);
         log(
            "BNBot movement packets while walking {}, in all {}; walk {}; samples {}",
            walkPackets,
            new TreeMap<>(this.botPackets),
            this.arrivals(walk.start, walk.stop),
            this.botSamples
         );
         this.check(
            "BNBot walk-legs",
            walking.amplitude >= 40.0F && walking.frames > 20 && walking.formulaError < 2.0F,
            walking + " (expected amplitude " + fmt(walking.expectedAmplitude) + " deg at " + fmt(walking.expectedHz) + " Hz)"
         );
         this.check("BNBot walk-stops", walking.stopTicks >= 0 && walking.stopTicks <= 14, "legs still " + walking.stopTicks + " ticks after the last move");
         this.check(
            "BNBot sprint-faster",
            sprinting.hz > walking.hz * 1.08F,
            "sprint "
               + fmt(sprinting.hz)
               + " Hz amplitude "
               + fmt(sprinting.amplitude)
               + " against walk "
               + fmt(walking.hz)
               + " Hz "
               + fmt(walking.amplitude)
               + "; speeds "
               + fmt(sprinting.blocksPerTick)
               + " / "
               + fmt(walking.blocksPerTick)
               + " blocks a tick"
         );
         this.botSwingTicks.clear();
         this.recordingBot = true;
         List<Long> commanded = new ArrayList<>();

         for (int i = 0; i < 10; i++) {
            this.placeSelf();
            this.place(62.5, 20.5, 90.0F);
            commanded.add(this.gameTime());
            this.command("scriptevent bedrocknative:pvp hitme");
            this.awaitTicks(8);
         }

         this.awaitTicks(11);
         this.botSwings("BNBot swing-rest", commanded);
         this.placeSelf();
         this.place(62.5, 20.5, 90.0F);
         this.botSwingTicks.clear();
         List<Long> fighting = new ArrayList<>();
         fighting.add(this.gameTime());
         this.command("scriptevent bedrocknative:pvp fight 4");
         this.awaitTicks(44);
         this.command("scriptevent bedrocknative:pvp fight off");
         this.awaitTicks(11);
         this.recordingBot = false;
         this.botSwings("BNBot swing-back-to-back", fighting);
         log("BNBot packets while swinging: {}", new TreeMap<>(this.botPackets));
         this.commands("gamemode creative", "effect @s clear");
         this.placeSelf();
      }
   }

   private void placeSelf() {
      this.command(String.format(Locale.ROOT, "tp @s %.2f %d %.2f -90 8", 60.5, 100, 20.5));
      this.awaitTicks(4);
   }

   private void botSwings(String name, List<Long> commanded) {
      List<float[]> frames = this.onClient(PlayerAnimProbe::frames);
      List<Long> sent = new ArrayList<>(this.botSwingTicks);
      int shown = 0;
      StringBuilder each = new StringBuilder();

      for (long start : sent) {
         float twist = maxAbs(frames, start, start + 9L, 16);
         float attack = max(frames, start, start + 9L, 7);
         boolean visible = twist >= 20.0F && attack >= 0.3F;
         shown += visible ? 1 : 0;
         each.append(String.format(Locale.ROOT, " [%s %.0f deg]", visible ? "shown" : "MISSING", twist));
      }

      this.check(
         name,
         !sent.isEmpty() && shown == sent.size(),
         commanded.size() + " swings asked, the BDS sent " + sent.size() + " swing packets, " + shown + " shown:" + each
      );
   }

   private void onPacket(BedrockPacket packet) {
      long id = this.botRuntimeId;
      Objects.requireNonNull(packet);
      BedrockPacket var4 = packet;
      byte var5 = 0;

      while (true) {
         switch (SwitchBootstraps.typeSwitch<"typeSwitch",AddPlayerPacket,MovePlayerPacket,MoveEntityDeltaPacket,MoveEntityAbsolutePacket,SetEntityMotionPacket,AnimatePacket,EntityEventPacket,TextPacket>(
            var4, var5
         )) {
            case 0:
               AddPlayerPacket add = (AddPlayerPacket)var4;
               if (!"BNBot".equals(add.getUsername())) {
                  var5 = 1;
                  break;
               }

               this.botRuntimeId = add.getRuntimeEntityId();
               return;
            case 1:
               MovePlayerPacket move = (MovePlayerPacket)var4;
               if (move.getRuntimeEntityId() == id && this.recordingBot) {
                  this.botPackets.merge("MovePlayer " + move.getMode(), 1, Integer::sum);
                  this.sample(
                     "MovePlayer "
                        + move.getMode()
                        + " tick "
                        + move.getTick()
                        + " ground "
                        + move.isOnGround()
                        + " at "
                        + move.getPosition()
                        + " rotation "
                        + move.getRotation()
                        + " (game time "
                        + this.gameTimeNow()
                        + ")"
                  );
                  return;
               }

               var5 = 2;
               break;
            case 2:
               MoveEntityDeltaPacket move = (MoveEntityDeltaPacket)var4;
               if (move.getRuntimeEntityId() == id && this.recordingBot) {
                  this.botPackets
                     .merge(
                        "MoveActorDelta ticks "
                           + move.getTicks()
                           + (move.isForceMove() ? " forceMove" : "")
                           + (move.isForceCompletion() ? " forceCompletion" : ""),
                        1,
                        Integer::sum
                     );
                  this.sample(
                     "MoveActorDelta "
                        + move.getFlags()
                        + " ground "
                        + move.isOnGround()
                        + " ticks "
                        + move.getTicks()
                        + " at "
                        + move.getX()
                        + " "
                        + move.getY()
                        + " "
                        + move.getZ()
                        + " yaw "
                        + move.getYaw()
                        + " head "
                        + move.getHeadYaw()
                        + " (game time "
                        + this.gameTimeNow()
                        + ")"
                  );
                  return;
               }

               var5 = 3;
               break;
            case 3:
               MoveEntityAbsolutePacket move = (MoveEntityAbsolutePacket)var4;
               if (move.getRuntimeEntityId() == id && this.recordingBot) {
                  this.botPackets.merge("MoveActorAbsolute", 1, Integer::sum);
                  this.sample("MoveActorAbsolute at " + move.getPosition() + " rotation " + move.getRotation() + " ground " + move.isOnGround());
                  return;
               }

               var5 = 4;
               break;
            case 4:
               SetEntityMotionPacket motion = (SetEntityMotionPacket)var4;
               if (motion.getRuntimeEntityId() == id && this.recordingBot) {
                  this.botPackets.merge("SetActorMotion", 1, Integer::sum);
                  return;
               }

               var5 = 5;
               break;
            case 5:
               AnimatePacket animate = (AnimatePacket)var4;
               if (animate.getRuntimeEntityId() == id && this.recordingBot) {
                  this.botPackets.merge("Animate " + animate.getAction() + " " + animate.getSwingSource(), 1, Integer::sum);
                  if (animate.getAction() == Action.SWING_ARM) {
                     this.botSwingTicks.add(this.gameTimeNow());
                  }

                  return;
               }

               var5 = 6;
               break;
            case 6:
               EntityEventPacket event = (EntityEventPacket)var4;
               if (event.getRuntimeEntityId() == id && this.recordingBot) {
                  this.botPackets.merge("ActorEvent " + event.getType(), 1, Integer::sum);
                  if (event.getType() == EntityEventType.ATTACK_START) {
                     this.botSwingTicks.add(this.gameTimeNow());
                  }

                  return;
               }

               var5 = 7;
               break;
            case 7:
               TextPacket text = (TextPacket)var4;
               if (text.getMessage() == null || !text.getMessage().contains("[bnpvp]")) {
                  var5 = 8;
                  break;
               } else {
                  log("server: {}", text.getMessage());
                  return;
               }
            default:
               return;
         }
      }
   }

   private void sample(String text) {
      this.botMoveTicks.add(this.gameTimeNow());
      if (this.botSamples.size() < 12) {
         this.botSamples.add(text);
      }
   }

   private String arrivals(long from, long to) {
      Map<Long, Integer> perTick = new TreeMap<>();

      for (long tick : this.botMoveTicks) {
         if (tick >= from && tick <= to) {
            perTick.merge(tick, 1, Integer::sum);
         }
      }

      int[] histogram = new int[4];

      for (long tickx = from; tickx <= to; tickx++) {
         histogram[Math.min(3, perTick.getOrDefault(tickx, 0))]++;
      }

      return String.format(Locale.ROOT, "ticks with 0/1/2/3+ move packets: %d/%d/%d/%d", histogram[0], histogram[1], histogram[2], histogram[3]);
   }

   private long gameTimeNow() {
      ClientLevel level = this.minecraft.level;
      return level != null ? level.getGameTime() : -1L;
   }

   private void place(double x, double z, float yaw) {
      this.command(String.format(Locale.ROOT, "scriptevent bedrocknative:pvp place %.2f %d %.2f %.1f", x, 100, z, yaw));
      this.awaitTicks(16);
   }

   @Nullable
   private Entity bot() {
      return this.onClient(() -> {
         if (this.minecraft.level == null) {
            return null;
         } else {
            for (Entity entity : this.minecraft.level.entitiesForRendering()) {
               if (entity != this.minecraft.player && entity instanceof Player player && "BNBot".equals(player.getGameProfile().name())) {
                  return entity;
               }
            }

            return null;
         }
      });
   }

   private static DevPlayersAnimTest.Segment moving(List<float[]> frames, long start, long end) {
      float first = Float.NaN;
      float last = Float.NaN;
      float[] previous = null;

      for (float[] frame : frames) {
         float tick = frame[1];
         if (!(tick < (float)start) && !(tick > (float)end)) {
            if (previous != null) {
               double moved = Math.hypot(frame[2] - previous[2], frame[4] - previous[4]);
               double dt = tick - previous[1];
               if (dt > 0.0 && moved / dt > 0.02) {
                  if (Float.isNaN(first)) {
                     first = previous[1];
                  }

                  last = tick;
               }
            }

            previous = frame;
         }
      }

      return Float.isNaN(first) ? new DevPlayersAnimTest.Segment(start, start) : new DevPlayersAnimTest.Segment((long)Math.floor(first), (long)Math.ceil(last));
   }

   private static DevPlayersAnimTest.Stats walkStats(List<float[]> frames, DevPlayersAnimTest.Segment segment) {
      float legMin = Float.MAX_VALUE;
      float legMax = -Float.MAX_VALUE;
      float formulaError = 0.0F;
      int crossings = 0;
      int count = 0;
      float firstTick = Float.NaN;
      float lastTick = Float.NaN;
      double speedSum = 0.0;
      double speedSquares = 0.0;
      double rootSum = 0.0;
      float[] previous = null;
      float[] first = null;
      float[] last = null;

      for (float[] frame : frames) {
         float tick = frame[1];
         if (!(tick < (float)(segment.start + 10L)) && !(tick > (float)segment.stop)) {
            count++;
            if (first == null) {
               first = frame;
            }

            last = frame;
            float leg = frame[13];
            legMin = Math.min(legMin, leg);
            legMax = Math.max(legMax, leg);
            float speed = frame[6];
            speedSum += speed;
            speedSquares += speed * speed;
            rootSum += frame[22];
            float expected = (float)(Math.cos(Math.toRadians(frame[5] * 38.17)) * speed * 57.3 * 1.4);
            float swing = frameSneaking(frame) ? leg + 28.0F : leg;
            formulaError = Math.max(formulaError, Math.abs(expected - swing));
            if (previous != null && Math.signum(swingOf(previous)) != Math.signum(swing) && swing != 0.0F) {
               crossings++;
               if (Float.isNaN(firstTick)) {
                  firstTick = tick;
               }

               lastTick = tick;
            }

            previous = frame;
         }
      }

      float amplitude = count > 0 ? (legMax - legMin) / 2.0F : 0.0F;
      float hz = crossings >= 2 ? (crossings - 1) / 2.0F / ((lastTick - firstTick) / 20.0F) : 0.0F;
      float meanSpeed = count > 0 ? (float)(speedSum / count) : 0.0F;
      float jitter = count > 0 ? (float)Math.sqrt(Math.max(0.0, speedSquares / count - meanSpeed * meanSpeed)) : 0.0F;
      float blocksPerTick = first != null && last != null && last[1] > first[1]
         ? (float)(Math.hypot(last[2] - first[2], last[4] - first[4]) / (last[1] - first[1]))
         : 0.0F;
      float expectedHz = meanSpeed * 20.0F / 9.431491F;
      int stopTicks = -1;
      float stillSince = Float.NaN;

      for (float[] framex : frames) {
         float tick = framex[1];
         if (!(tick < (float)segment.stop)) {
            if (Math.abs(framex[13]) > 3.0F) {
               stillSince = Float.NaN;
            } else if (Float.isNaN(stillSince)) {
               stillSince = tick;
            }

            if (!Float.isNaN(stillSince) && tick - stillSince >= 5.0F) {
               stopTicks = Math.round(stillSince - (float)segment.stop);
               break;
            }
         }
      }

      return new DevPlayersAnimTest.Stats(
         count,
         amplitude,
         hz,
         meanSpeed * 57.3F * 1.4F,
         expectedHz,
         formulaError,
         stopTicks,
         count > 0 ? (float)(rootSum / count) : 0.0F,
         blocksPerTick,
         jitter
      );
   }

   private static boolean frameSneaking(float[] frame) {
      return Math.abs(frame[22] - 28.0F) < 1.0F;
   }

   private static float swingOf(float[] frame) {
      return frameSneaking(frame) ? frame[13] + 28.0F : frame[13];
   }

   private void trace(String name, List<float[]> frames, DevPlayersAnimTest.Segment segment) {
      StringBuilder out = new StringBuilder();
      long lastTick = Long.MIN_VALUE;

      for (float[] frame : frames) {
         long tick = (long)Math.floor(frame[1]);
         if (tick >= segment.start - 2L && tick <= segment.stop + 12L && tick != lastTick) {
            lastTick = tick;
            out.append(
               String.format(
                  Locale.ROOT,
                  "%n  t%+d x %.3f z %.3f dist %.2f speed %.3f leg %.1f arm %.1f body %.1f head %.1f root %.1f ground %.2f",
                  tick - segment.start,
                  frame[2],
                  frame[4],
                  frame[5],
                  frame[6],
                  frame[13],
                  frame[15],
                  frame[10],
                  frame[11],
                  frame[22],
                  frame[23]
               )
            );
         }
      }

      log("trace {} (ticks {} to {}):{}", name, segment.start, segment.stop, out);
   }

   private static float max(List<float[]> frames, long from, long to, int field) {
      float max = 0.0F;

      for (float[] frame : frames) {
         if (frame[1] >= (float)from && frame[1] <= (float)to) {
            max = Math.max(max, frame[field]);
         }
      }

      return max;
   }

   private static float maxAbs(List<float[]> frames, long from, long to, int field) {
      float max = 0.0F;

      for (float[] frame : frames) {
         if (frame[1] >= (float)from && frame[1] <= (float)to && !Float.isNaN(frame[field])) {
            max = Math.max(max, Math.abs(frame[field]));
         }
      }

      return max;
   }

   private static float mean(List<float[]> frames, long from, long to, int field) {
      double sum = 0.0;
      int count = 0;

      for (float[] frame : frames) {
         if (frame[1] >= (float)from && frame[1] <= (float)to) {
            sum += frame[field];
            count++;
         }
      }

      return count > 0 ? (float)(sum / count) : Float.NaN;
   }

   private static int countRises(List<float[]> frames, long from, long to) {
      int rises = 0;
      float previous = 0.0F;

      for (float[] frame : frames) {
         if (!(frame[1] < (float)from) && !(frame[1] > (float)to)) {
            float attack = frame[7];
            if (previous == 0.0F && attack > 0.0F || previous - attack > 0.3F) {
               rises++;
            }

            previous = attack;
         }
      }

      return rises;
   }

   private DevPlayersAnimTest.Npc newNpc(String name) {
      long id = this.nextId++;
      this.injected.add(id);
      return new DevPlayersAnimTest.Npc(name, new UUID(-4694439661580320768L, id), id);
   }

   private PlayerListPacket list(DevPlayersAnimTest.Npc npc, SerializedSkin skin) {
      Entry entry = new Entry(npc.uuid());
      entry.setAction(org.cloudburstmc.protocol.bedrock.packet.PlayerListPacket.Action.ADD);
      entry.setEntityId(npc.uniqueId());
      entry.setName(npc.name());
      entry.setXuid("");
      entry.setPlatformChatId("");
      entry.setSkin(skin);
      entry.setTrustedSkin(true);
      entry.setColor(Color.LIGHT_GRAY);
      PlayerListPacket packet = new PlayerListPacket();
      packet.setAction(org.cloudburstmc.protocol.bedrock.packet.PlayerListPacket.Action.ADD);
      packet.getEntries().add(entry);
      return packet;
   }

   private PlayerListPacket unlist(DevPlayersAnimTest.Npc npc) {
      Entry entry = new Entry(npc.uuid());
      entry.setAction(org.cloudburstmc.protocol.bedrock.packet.PlayerListPacket.Action.REMOVE);
      PlayerListPacket packet = new PlayerListPacket();
      packet.setAction(org.cloudburstmc.protocol.bedrock.packet.PlayerListPacket.Action.REMOVE);
      packet.getEntries().add(entry);
      return packet;
   }

   private AddPlayerPacket addPlayer(DevPlayersAnimTest.Npc npc, double x, double z, float yaw) {
      AddPlayerPacket add = new AddPlayerPacket();
      add.setUuid(npc.uuid());
      add.setUsername(npc.name());
      add.setUniqueEntityId(npc.uniqueId());
      add.setRuntimeEntityId(npc.uniqueId());
      add.setPlatformChatId("");
      add.setPosition(Vector3f.from(x, 100.0, z));
      add.setMotion(Vector3f.ZERO);
      add.setRotation(Vector3f.from(0.0F, yaw, yaw));
      add.setHand(ItemData.AIR);
      add.setDeviceId("");
      return add;
   }

   private static AnimatePacket swing(long runtimeId) {
      AnimatePacket animate = new AnimatePacket();
      animate.setAction(Action.SWING_ARM);
      animate.setRuntimeEntityId(runtimeId);
      animate.setSwingSource(SwingSource.ATTACK);
      return animate;
   }

   private static EntityEventPacket attackStart(long runtimeId) {
      EntityEventPacket event = new EntityEventPacket();
      event.setRuntimeEntityId(runtimeId);
      event.setType(EntityEventType.ATTACK_START);
      return event;
   }

   @Nullable
   private Player player(UUID uuid) {
      if (this.minecraft.level == null) {
         return null;
      } else {
         for (Entity entity : this.minecraft.level.entitiesForRendering()) {
            if (entity instanceof Player player && player.getUUID().equals(uuid)) {
               return player;
            }
         }

         return null;
      }
   }

   private String describe(DevPlayersAnimTest.Npc npc) {
      Player player = this.player(npc.uuid);
      String described = player != null ? BedrockPlayers.describe(player) : null;
      return described != null ? described : "not drawn by the runtime";
   }

   @Nullable
   private SerializedSkin classic(String texture) {
      if (!Files.isRegularFile(SKIN_PACK.resolve(texture))) {
         return null;
      } else {
         try {
            JsonObject claims = BedrockSkin.classicFromSkinPack(SKIN_PACK, texture, false).claims();
            return SkinClaims.toSerialized(claims);
         } catch (Exception var3) {
            log("reading {} from the skin pack: {}", texture, var3);
            return null;
         }
      }
   }

   private void inject(BedrockPacket packet) {
      this.run(() -> this.inject.accept(packet));
   }

   private long gameTime() {
      return this.onClient(() -> this.minecraft.level.getGameTime());
   }

   private void check(String name, boolean pass, String details) {
      String line = String.format(Locale.ROOT, "Dev players-anim test %s %s: %s", pass ? "PASS" : "FAIL", name, details);
      if (!pass) {
         this.failed++;
      }

      this.results.add(line);
      BedrockNative.LOGGER.info(line);
   }

   private static String fmt(float value) {
      return String.format(Locale.ROOT, "%.2f", value);
   }

   private static void log(String message, Object... arguments) {
      BedrockNative.LOGGER.info("Dev players-anim test: " + message, arguments);
   }

   private void screenshot(String name) {
      Screenshot.grab(this.minecraft.gameDirectory, name + ".png", this.minecraft.gameRenderer.mainRenderTarget(), 1, message -> {});
   }

   private void commands(String... commands) {
      for (String command : commands) {
         this.command(command);
      }

      sleep(600L);
   }

   private void command(String command) {
      this.run(() -> {
         if (this.minecraft.getConnection() != null) {
            this.minecraft.getConnection().sendCommand(command);
         }
      });
      sleep(250L);
   }

   private static String fill(int x1, int y1, int z1, int x2, int y2, int z2, String block) {
      return "fill " + x1 + " " + y1 + " " + z1 + " " + x2 + " " + y2 + " " + z2 + " " + block;
   }

   private void awaitTicks(int count) {
      long target = this.ticks.get() + count;
      long started = System.currentTimeMillis();

      while (this.ticks.get() < target && System.currentTimeMillis() - started < count * 200L + 2000L) {
         sleep(5L);
      }
   }

   private boolean waitFor(long millis, BooleanSupplier condition) {
      long started = System.currentTimeMillis();

      while (System.currentTimeMillis() - started < millis) {
         if (condition.getAsBoolean()) {
            return true;
         }

         sleep(100L);
      }

      return condition.getAsBoolean();
   }

   private void run(Runnable task) {
      this.minecraft.execute(task);
   }

   private <T> T onClient(Supplier<T> task) {
      return CompletableFuture.supplyAsync(task, this.minecraft::execute).join();
   }

   private static void sleep(long millis) {
      try {
         Thread.sleep(millis);
      } catch (InterruptedException var3) {
         Thread.currentThread().interrupt();
      }
   }

   private record Npc(String name, UUID uuid, long uniqueId) {
   }

   private record Segment(long start, long stop) {
   }

   private record Stats(
      int frames,
      float amplitude,
      float hz,
      float expectedAmplitude,
      float expectedHz,
      float formulaError,
      int stopTicks,
      float rootX,
      float blocksPerTick,
      float speedJitter
   ) {
      @Override
      public String toString() {
         return String.format(
            Locale.ROOT,
            "%d frames, legs +-%.1f deg at %.2f Hz, move speed jitter %.3f, %.3f blocks a tick, formula error %.2f deg",
            this.frames,
            this.amplitude,
            this.hz,
            this.speedJitter,
            this.blocksPerTick,
            this.formulaError
         );
      }
   }

   private final class Walker {
      final DevPlayersAnimTest.Npc npc;
      double x;
      double z;
      float yaw;
      final EntityDataMap flags;

      Walker(DevPlayersAnimTest.Npc npc, double x, double z, float yaw) {
         Objects.requireNonNull(DevPlayersAnimTest.this);
         super();
         this.flags = new EntityDataMap();
         this.npc = npc;
         this.x = x;
         this.z = z;
         this.yaw = yaw;
      }

      DevPlayersAnimTest.Segment move(double dx, double dz, int ticks, @Nullable EntityFlag flag) {
         long[] span = new long[]{-1L, -1L};
         int[] left = new int[]{ticks};
         if (flag != null) {
            this.flag(flag, true);
         }

         DevPlayersAnimTest.this.perTick = client -> {
            if (left[0]-- <= 0) {
               DevPlayersAnimTest.this.perTick = null;
            } else {
               long now = client.level.getGameTime();
               if (span[0] < 0L) {
                  span[0] = now;
               }

               span[1] = now;
               this.step(client, dx, dz);
            }
         };
         DevPlayersAnimTest.this.awaitTicks(ticks + 2);
         if (flag != null) {
            this.flag(flag, false);
         }

         return new DevPlayersAnimTest.Segment(span[0], span[1]);
      }

      void step(Minecraft client, double dx, double dz) {
         this.x += dx;
         this.z += dz;
         this.send(false);
      }

      void patrol(Minecraft client, double speed) {
         double dz = Math.abs(Mth.wrapDegrees(this.yaw)) < 90.0F ? speed : -speed;
         if (Math.abs(this.z + dz - 20.5) > 4.0) {
            this.yaw = Mth.wrapDegrees(this.yaw + 180.0F);
            dz = -dz;
         }

         this.step(client, 0.0, dz);
      }

      void place(double toZ, float toYaw) {
         this.z = toZ;
         this.yaw = toYaw;
         DevPlayersAnimTest.this.run(() -> this.send(true));
         DevPlayersAnimTest.this.awaitTicks(10);
      }

      private void send(boolean teleport) {
         MoveEntityAbsolutePacket move = new MoveEntityAbsolutePacket();
         move.setRuntimeEntityId(this.npc.uniqueId);
         move.setPosition(Vector3f.from(this.x, 100.0 + ActorOffsets.move(EntityTypes.PLAYER), this.z));
         move.setRotation(Vector3f.from(0.0F, this.yaw, this.yaw));
         move.setOnGround(true);
         move.setTeleported(teleport);
         DevPlayersAnimTest.this.inject.accept(move);
      }

      long flag(EntityFlag flag, boolean on) {
         return DevPlayersAnimTest.this.onClient(() -> {
            this.flags.setFlag(flag, on);
            SetEntityDataPacket data = new SetEntityDataPacket();
            data.setRuntimeEntityId(this.npc.uniqueId);
            EntityDataMap copy = new EntityDataMap();
            copy.putAll(this.flags);
            data.setMetadata(copy);
            DevPlayersAnimTest.this.inject.accept(data);
            return DevPlayersAnimTest.this.minecraft.level.getGameTime();
         });
      }
   }
}
