package net.bedrocknative.client.net;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.zip.GZIPInputStream;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.model.ActorPrep;
import net.bedrocknative.client.model.ActorState;
import net.bedrocknative.client.model.BedrockActors;
import net.bedrocknative.client.model.BedrockModels;
import net.bedrocknative.client.model.BedrockPlayers;
import net.bedrocknative.client.perf.FrameTimes;
import net.bedrocknative.model.prep.PrepPool;
import net.bedrocknative.model.prep.RegistryWarmer;
import net.bedrocknative.model.skin.PlayerSkin;
import net.bedrocknative.model.skin.SkinClaims;
import net.bedrocknative.model.skin.SkinModel;
import net.bedrocknative.perf.FrameStats;
import net.bedrocknative.perf.FrameWatch;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.cloudburstmc.math.vector.Vector2f;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataMap;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.data.skin.AnimationData;
import org.cloudburstmc.protocol.bedrock.data.skin.AnimationExpressionType;
import org.cloudburstmc.protocol.bedrock.data.skin.ImageData;
import org.cloudburstmc.protocol.bedrock.data.skin.SerializedSkin;
import org.cloudburstmc.protocol.bedrock.data.skin.SerializedSkin.Builder;
import org.cloudburstmc.protocol.bedrock.packet.AddEntityPacket;
import org.cloudburstmc.protocol.bedrock.packet.AddPlayerPacket;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerListPacket;
import org.cloudburstmc.protocol.bedrock.packet.RemoveEntityPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerListPacket.Action;
import org.cloudburstmc.protocol.bedrock.packet.PlayerListPacket.Entry;
import org.jspecify.annotations.Nullable;

final class DevActorBurstTest {
   private static final long FIRST_ID = 2118123520L;
   private static final int PLAYERS = 30;
   private static final double WORST_LIMIT_MS = 60.0;
   private static final double P99_LIMIT_MS = 25.0;
   private static final String[] MOBS = new String[]{
      "camel", "sniffer", "armadillo", "frog", "axolotl", "goat", "fox", "panda", "polar_bear", "llama", "parrot", "turtle"
   };
   private static final String GOLEM = "bedrocknative:test_golem";
   private final Minecraft minecraft;
   private final Consumer<BedrockPacket> inject;
   private final List<String> failures = new ArrayList<>();
   private final List<Long> injected = new ArrayList<>();
   private final List<UUID> listed = new ArrayList<>();
   private final Map<String, String> table = new LinkedHashMap<>();
   private int checks;
   private int passed;
   private long nextId = 2118123520L;

   DevActorBurstTest(Minecraft minecraft, Consumer<BedrockPacket> inject) {
      this.minecraft = minecraft;
      this.inject = inject;
   }

   void run() {
      CameraType camera = this.onClient(() -> this.minecraft.options.getCameraType());

      try {
         if (this.waitFor(30000L, () -> BedrockModels.registry() != null)) {
            this.waitFor(20000L, () -> {
               RegistryWarmer.Summary warmed = ActorPrep.warmed();
               return !ActorPrep.ENABLED || warmed != null && warmed.done();
            });
            sleep(3000L);
            log(
               "actor preparation {} ({} workers); registry preparation: {}",
               ActorPrep.ENABLED ? "on" : "off (-PactorPrep=off: the old way)",
               ActorPrep.workers(),
               describe(ActorPrep.warmed())
            );
            this.commands("gamemode creative", "time set noon", "weather clear", "kill @e[type=!player,r=48]");
            Vec3 at = this.onClient(() -> this.minecraft.player.position());
            this.commands(String.format(Locale.ROOT, "tp @s %.2f %.2f %.2f 0 15", at.x, at.y, at.z));
            this.run(() -> this.minecraft.options.setCameraType(CameraType.FIRST_PERSON));
            sleep(2000L);
            at = this.onClient(() -> this.minecraft.player.position());
            this.players(at);
            this.mobs();
            this.golems(at);
            PrepPool.Stats stats = ActorPrep.stats();
            if (stats != null) {
               log("pool totals: {}", stats);
            }

            return;
         }

         this.fail("the model registry never loaded");
      } catch (RuntimeException e) {
         this.fail("exception: " + e);
         BedrockNative.LOGGER.error("Dev actor-burst test failed with an exception", e);
         return;
      } finally {
         this.cleanup();
         this.run(() -> this.minecraft.options.setCameraType(camera));
         this.table.forEach((name, line) -> log("summary {}: {}", name, line));
         log("{} of {} checks passed{}", this.passed, this.checks, this.failures.isEmpty() ? "" : "; failed: " + this.failures);
         log("done");
      }
   }

   private static String describe(RegistryWarmer.@Nullable Summary warmed) {
      return warmed == null
         ? "none"
         : String.format(
            Locale.ROOT,
            "%d of %d definitions%s in %.0f ms, %d textures (%.1f M pixels), %d meshes",
            warmed.definitions(),
            warmed.planned(),
            warmed.done() ? " done" : "",
            warmed.nanos() / 1000000.0,
            warmed.images(),
            warmed.pixels() / 1000000.0,
            warmed.meshes()
         );
   }

   private void players(Vec3 at) {
      SerializedSkin persona = persona();
      List<DevActorBurstTest.Npc> npcs = new ArrayList<>();
      PlayerListPacket list = new PlayerListPacket();
      list.setAction(Action.ADD);

      for (int i = 0; i < 30; i++) {
         int row = i / 10;
         int column = i % 10;

         SerializedSkin skin = switch (row) {
            case 0 -> classic(i, 64, column % 2 == 1, false);
            case 1 -> classic(i, 128, column % 2 == 0, column % 3 == 0);
            default -> persona != null ? personaVariant(persona, i) : classic(i, 64, false, false);
         };
         long id = this.nextId++;
         this.injected.add(id);
         DevActorBurstTest.Npc npc = new DevActorBurstTest.Npc(
            "bn_burst_" + i, new UUID(-4693861451608096768L, id), id, at.add(-6.0 + column * 4 / 3.0, 0.0, 8 + row * 3), skin, PlayerSkin.of(skin).key()
         );
         npcs.add(npc);
         this.listed.add(npc.uuid());
         Entry entry = new Entry(npc.uuid());
         entry.setAction(Action.ADD);
         entry.setEntityId(npc.uniqueId());
         entry.setName(npc.name());
         entry.setXuid("");
         entry.setPlatformChatId("");
         entry.setSkin(skin);
         entry.setTrustedSkin(true);
         entry.setColor(Color.LIGHT_GRAY);
         list.getEntries().add(entry);
      }

      int[] meanwhile = new int[]{0, 0};
      long arrived = this.burst("players", () -> {
         this.inject.accept(list);

         for (DevActorBurstTest.Npc npcx : npcs) {
            this.inject.accept(addPlayer(npcx));
         }
      }, () -> {
         int java = 0;
         int fallback = 0;
         int ownx = 0;

         for (DevActorBurstTest.Npc npcx : npcs) {
            Player player = this.player(npcx.uuid());
            SkinModel model = player != null ? BedrockPlayers.model(player) : null;
            if (model == null) {
               java++;
            } else if (model.skin().key().equals(npcx.key())) {
               ownx++;
            } else {
               fallback++;
            }
         }

         meanwhile[0] = Math.max(meanwhile[0], java);
         meanwhile[1] = Math.max(meanwhile[1], fallback);
         return ownx == npcs.size();
      });
      sleep(1500L);
      this.screenshot("actor-burst-players");
      int own = this.onClient(() -> {
         int count = 0;

         for (DevActorBurstTest.Npc npcx : npcs) {
            Player player = this.player(npcx.uuid());
            SkinModel model = player != null ? BedrockPlayers.model(player) : null;
            count += model != null && model.drawn().key().equals(npcx.key()) ? 1 : 0;
         }

         return count;
      });
      this.check("every player is drawn with its own skin (" + own + " of " + npcs.size() + ")", own == npcs.size());
      log(
         "players: own skins of all {} after {} ms; meanwhile at most {} drawn by Java, {} by Bedrock's default Steve / Alex",
         npcs.size(),
         arrived < 0L ? "never" : arrived,
         meanwhile[0],
         meanwhile[1]
      );

      for (DevActorBurstTest.Npc npc : npcs) {
         RemoveEntityPacket remove = new RemoveEntityPacket();
         remove.setUniqueEntityId(npc.uniqueId());
         this.inject.accept(remove);
         this.injected.remove(npc.uniqueId());
      }

      this.unlistAll();
      sleep(1000L);
   }

   private void mobs() {
      Vec3 at = this.onClient(() -> this.minecraft.player.position());
      List<String> summons = new ArrayList<>();

      for (int i = 0; i < MOBS.length * 2; i++) {
         int column = i % 12;
         int row = i / 12;
         summons.add(String.format(Locale.ROOT, "summon %s %.1f %.1f %.1f", MOBS[i % MOBS.length], at.x - 8.0 + column * 16 / 11.0, at.y, at.z + 9.0 + row * 4));
      }

      int before = this.onClient(this::drawnMobs);
      long arrived = this.burst(
         "mobs",
         () -> this.run(() -> summons.forEach(command -> this.minecraft.getConnection().sendCommand(command))),
         () -> this.drawnMobs() - before >= summons.size() * 3 / 4
      );
      sleep(1000L);
      this.screenshot("actor-burst-mobs");
      int drawn = this.onClient(this::drawnMobs) - before;
      this.check("the summoned mobs are drawn by the Bedrock look (" + drawn + " of " + summons.size() + ")", drawn >= summons.size() * 3 / 4);
      log("mobs: {} of {} drawn after {} ms", drawn, summons.size(), arrived < 0L ? "never" : arrived);
      this.commands("kill @e[type=!player,r=48]");
   }

   private int drawnMobs() {
      int count = 0;

      for (ActorState state : BedrockActors.current().states()) {
         count += state.vanillaMob() && state.evaluations() > 0L ? 1 : 0;
      }

      return count;
   }

   private void golems(Vec3 at) {
      if (this.onClient(() -> BedrockModels.registry().entity("bedrocknative:test_golem")) == null) {
         log("SKIP golems: the test pack's golem is not in the pack stack (testing/bds/install.sh)");
      } else {
         List<Long> golems = new ArrayList<>();
         long arrived = this.burst("golems", () -> {
            for (int i = 0; i < 16; i++) {
               long idx = this.nextId++;
               this.injected.add(idx);
               golems.add(idx);
               AddEntityPacket add = new AddEntityPacket();
               add.setIdentifier("bedrocknative:test_golem");
               add.setUniqueEntityId(idx);
               add.setRuntimeEntityId(idx);
               add.setPosition(Vector3f.from(at.x - 7.5 + i % 8 * 2, at.y, at.z + 7.0 + i / 8 * 3));
               add.setMotion(Vector3f.ZERO);
               add.setRotation(Vector2f.from(0.0F, 180.0F));
               add.setHeadRotation(180.0F);
               add.setBodyRotation(180.0F);
               EntityDataMap data = add.getMetadata();
               data.put(EntityDataTypes.WIDTH, 1.0F);
               data.put(EntityDataTypes.HEIGHT, 2.0F);
               this.inject.accept(add);
            }
         }, () -> {
            int drawn = 0;

            for (ActorState state : BedrockActors.current().states()) {
               drawn += golems.contains(state.runtimeId()) && state.evaluations() > 0L ? 1 : 0;
            }

            return drawn == golems.size();
         });
         sleep(1000L);
         this.screenshot("actor-burst-golems");
         this.check("every golem is drawn from its definition", arrived >= 0L);
         log("golems: all {} drawn after {} ms", golems.size(), arrived < 0L ? "never" : arrived);

         for (long id : golems) {
            RemoveEntityPacket remove = new RemoveEntityPacket();
            remove.setUniqueEntityId(id);
            this.inject.accept(remove);
            this.injected.remove(id);
         }

         sleep(500L);
      }
   }

   private long burst(String name, Runnable action, BooleanSupplier done) {
      this.onClient(() -> {
         FrameTimes.watch().startRecording(name);
         return null;
      });
      long started = System.nanoTime();
      action.run();
      long took = -1L;

      while (System.nanoTime() - started < 30000000000L) {
         if (this.onClient(done::getAsBoolean)) {
            took = (System.nanoTime() - started) / 1000000L;
            break;
         }

         sleep(50L);
      }

      sleep(1000L);
      FrameWatch.Recording recording = this.onClient(() -> FrameTimes.watch().stopRecording());
      this.report(name, recording, took);
      return took;
   }

   private void report(String name, FrameWatch.Recording recording, long took) {
      FrameStats frames = recording.inGame();
      boolean smooth = frames.count() == 0 || frames.maxMillis() < 60.0 && frames.percentileMillis(99.0) < 25.0;
      String line = String.format(
         Locale.ROOT,
         "frames %s, over 50 ms %d; done after %s; GC pauses %.0f ms; client thread: skins %.1f ms, actor prep %.1f ms, entity setup %.1f ms, Java add-entity %.1f ms, actor spawn %.1f ms, models %.1f ms; loading screen frames %d",
         frames.summary(),
         frames.over(50000000L),
         took < 0L ? "never" : took + " ms",
         recording.gcMillis(),
         recording.bucketMillis(FrameWatch.Bucket.SKINS),
         recording.bucketMillis(FrameWatch.Bucket.ACTOR_PREP),
         recording.bucketMillis(FrameWatch.Bucket.ENTITIES),
         recording.bucketMillis(FrameWatch.Bucket.JAVA_ADD),
         recording.bucketMillis(FrameWatch.Bucket.ACTOR_SPAWN),
         recording.bucketMillis(FrameWatch.Bucket.MODELS),
         recording.loading().count()
      );
      log("{}: {}", name, line);
      BedrockNative.LOGGER
         .info("Dev actor-burst test {} {} (worst frame under {} ms and p99 under {} ms)", new Object[]{smooth ? "PASS" : "FAIL", name, 60, 25});
      this.table
         .put(
            name,
            String.format(
               Locale.ROOT,
               "%s, worst %.1f ms, p99 %.1f ms, over 50 ms %d, done after %s",
               smooth ? "PASS" : "FAIL",
               frames.maxMillis(),
               frames.percentileMillis(99.0),
               frames.over(50000000L),
               took < 0L ? "never" : took + " ms"
            )
         );
      int shown = 0;

      for (FrameWatch.Hitch hitch : recording.worst()) {
         if (shown++ == 3) {
            break;
         }

         log(String.format(Locale.ROOT, "%s hitch %.0f ms%s: %s", name, hitch.frameMillis(), hitch.loading() ? " (loading screen)" : "", hitch.causes()));
      }
   }

   private static AddPlayerPacket addPlayer(DevActorBurstTest.Npc npc) {
      AddPlayerPacket add = new AddPlayerPacket();
      add.setUuid(npc.uuid());
      add.setUsername(npc.name());
      add.setUniqueEntityId(npc.uniqueId());
      add.setRuntimeEntityId(npc.uniqueId());
      add.setPlatformChatId("");
      add.setPosition(Vector3f.from(npc.position().x, npc.position().y, npc.position().z));
      add.setMotion(Vector3f.ZERO);
      add.setRotation(Vector3f.from(0.0F, 180.0F, 180.0F));
      add.setHand(ItemData.AIR);
      add.setDeviceId("");
      return add;
   }

   private void unlistAll() {
      if (!this.listed.isEmpty()) {
         PlayerListPacket packet = new PlayerListPacket();
         packet.setAction(Action.REMOVE);

         for (UUID uuid : this.listed) {
            Entry entry = new Entry(uuid);
            entry.setAction(Action.REMOVE);
            packet.getEntries().add(entry);
         }

         this.inject.accept(packet);
         this.listed.clear();
      }
   }

   private void cleanup() {
      for (long id : List.copyOf(this.injected)) {
         RemoveEntityPacket remove = new RemoveEntityPacket();
         remove.setUniqueEntityId(id);
         this.inject.accept(remove);
      }

      this.injected.clear();
      this.unlistAll();
   }

   private static SerializedSkin classic(int seed, int size, boolean slim, boolean cape) {
      float hue = seed * 0.137F % 1.0F;
      int body = Color.HSBtoRGB(hue, 0.65F, 0.9F);
      int legs = Color.HSBtoRGB((hue + 0.33F) % 1.0F, 0.5F, 0.6F);
      int face = Color.HSBtoRGB(hue, 0.3F, 0.5F);
      byte[] rgba = new byte[size * size * 4];
      int unit = size / 64;

      for (int y = 0; y < size; y++) {
         for (int x = 0; x < size; x++) {
            int u = x / Math.max(1, unit);
            int v = y / Math.max(1, unit);
            int color = v < 16 ? (u >= 8 && u < 16 && v >= 8 ? face : body) : (v < 32 ? (u < 16 ? legs : body) : (u < 16 ? legs : body));
            boolean overlay = u >= 32 && v < 16 || v >= 32 && v < 48 || v >= 48 && (u < 16 || u >= 48);
            int p = (y * size + x) * 4;
            rgba[p] = (byte)(color >> 16);
            rgba[p + 1] = (byte)(color >> 8);
            rgba[p + 2] = (byte)color;
            rgba[p + 3] = (byte)(overlay ? 0 : 255);
         }
      }

      Builder builder = SerializedSkin.builder()
         .skinId("bn_burst_" + seed)
         .playFabId("")
         .skinResourcePatch("{\"geometry\":{\"default\":\"geometry.humanoid.custom" + (slim ? "Slim" : "") + "\"}}")
         .skinData(ImageData.of(size, size, rgba))
         .geometryData("")
         .armSize(slim ? "slim" : "wide")
         .fullSkinId("bn_burst_" + seed)
         .capeId(cape ? "bn_burst_cape_" + seed : "")
         .premium(false)
         .persona(false)
         .trusted(true);
      if (cape) {
         byte[] capeRgba = new byte[8192];
         int i = 0;

         for (int p = 0; i < 2048; p += 4) {
            capeRgba[p] = (byte)(legs >> 16);
            capeRgba[p + 1] = (byte)(legs >> 8);
            capeRgba[p + 2] = (byte)legs;
            capeRgba[p + 3] = -1;
            i++;
         }

         builder.capeData(ImageData.of(64, 32, capeRgba));
      }

      return builder.build();
   }

   private static @Nullable SerializedSkin persona() {
      try (InputStream raw = DevActorBurstTest.class.getResourceAsStream("/assets/bedrocknative/skins/steve.json.gz")) {
         if (raw == null) {
            return null;
         }

         try (InputStreamReader reader = new InputStreamReader(new GZIPInputStream(raw), StandardCharsets.UTF_8)) {
            JsonObject claims = JsonParser.parseReader(reader).getAsJsonObject();
            return SkinClaims.toSerialized(claims);
         }
      } catch (IOException | RuntimeException e) {
         BedrockNative.LOGGER.warn("Dev actor-burst test: the bundled persona skin is unreadable", e);
         return null;
      }
   }

   private static SerializedSkin personaVariant(SerializedSkin persona, int seed) {
      ImageData image = persona.getSkinData();
      byte[] tinted = (byte[])image.getImage().clone();
      int tint = Color.HSBtoRGB(seed * 0.071F % 1.0F, 0.45F, 1.0F);

      for (int p = 0; p < tinted.length; p += 4) {
         tinted[p] = (byte)((tinted[p] & 255) * (tint >> 16 & 0xFF) / 255);
         tinted[p + 1] = (byte)((tinted[p + 1] & 255) * (tint >> 8 & 0xFF) / 255);
         tinted[p + 2] = (byte)((tinted[p + 2] & 255) * (tint & 0xFF) / 255);
      }

      List<AnimationData> animations = new ArrayList<>();
      if (persona.getAnimations() != null) {
         for (AnimationData animation : persona.getAnimations()) {
            animations.add(new AnimationData(animation.getImage(), animation.getTextureType(), animation.getFrames(), AnimationExpressionType.LINEAR));
         }
      }

      return persona.toBuilder()
         .skinId("bn_burst_persona_" + seed)
         .fullSkinId("bn_burst_persona_" + seed)
         .skinData(ImageData.of(image.getWidth(), image.getHeight(), tinted))
         .animations(animations)
         .build();
   }

   private @Nullable Player player(UUID uuid) {
      for (Entity entity : this.minecraft.level.entitiesForRendering()) {
         if (entity instanceof Player player && player.getUUID().equals(uuid)) {
            return player;
         }
      }

      return null;
   }

   private void check(String what, boolean ok) {
      this.checks++;
      if (ok) {
         this.passed++;
         log("ok: {}", what);
      } else {
         this.fail(what);
      }
   }

   private void fail(String what) {
      this.failures.add(what);
      BedrockNative.LOGGER.warn("Dev actor-burst test FAILED: {}", what);
   }

   private static void log(String message, Object... arguments) {
      BedrockNative.LOGGER.info("Dev actor-burst test: " + message, arguments);
   }

   private void commands(String... commands) {
      for (String command : commands) {
         this.run(() -> this.minecraft.getConnection().sendCommand(command));
         sleep(400L);
      }

      sleep(1000L);
   }

   private void screenshot(String name) {
      this.run(() -> Screenshot.grab(this.minecraft.gameDirectory, name + ".png", this.minecraft.gameRenderer.mainRenderTarget(), 1, message -> {}));
      sleep(500L);
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
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
      }
   }

   private record Npc(String name, UUID uuid, long uniqueId, Vec3 position, SerializedSkin skin, String key) {
   }
}
