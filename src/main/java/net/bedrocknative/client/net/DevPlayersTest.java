package net.bedrocknative.client.net;

import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
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
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.model.ActorSnapshot;
import net.bedrocknative.client.model.ActorState;
import net.bedrocknative.client.model.BedrockActors;
import net.bedrocknative.client.model.BedrockMobs;
import net.bedrocknative.client.model.BedrockModels;
import net.bedrocknative.client.model.BedrockPlayers;
import net.bedrocknative.client.model.FirstPersonProbe;
import net.bedrocknative.client.model.PlayerSkins;
import net.bedrocknative.client.model.SkinTextures;
import net.bedrocknative.model.entity.ModelRegistry;
import net.bedrocknative.model.geometry.MeshedGeometry;
import net.bedrocknative.model.skin.PlayerSkin;
import net.bedrocknative.model.skin.SkinClaims;
import net.bedrocknative.model.skin.SkinModel;
import net.bedrocknative.net.auth.BedrockSkin;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.SwingAnimation;
import org.cloudburstmc.protocol.bedrock.data.skin.ImageData;
import org.cloudburstmc.protocol.bedrock.data.skin.SerializedSkin;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerSkinPacket;
import org.jspecify.annotations.Nullable;

final class DevPlayersTest {
   private static final String WAVE = "4c8ae710-df2e-47cd-814d-cc7bf21a3d67";
   private static final int[] FIRST_PERSON_PITCHES = new int[]{-80, -40, 0, 40, 80};
   private static final int FIRST_PERSON_TOLERANCE = 2;
   private static final Path SKIN_PACK = DevSkinPack.folder();
   private final Minecraft minecraft;
   private final Consumer<BedrockPacket> send;
   private final List<String> failures = new ArrayList<>();
   private int checks;
   private int passed;

   DevPlayersTest(Minecraft minecraft, Consumer<BedrockPacket> send) {
      this.minecraft = minecraft;
      this.send = send;
   }

   void run() {
      this.run(false);
   }

   void run(boolean firstPersonOnly) {
      boolean original = BedrockPlayers.enabled();
      CameraType camera = this.onClient(() -> this.minecraft.options.getCameraType());

      try {
         if (firstPersonOnly) {
            this.firstPersonOnly();
         } else {
            this.test();
         }
      } catch (RuntimeException e) {
         this.fail("exception: " + e);
         BedrockNative.LOGGER.error("Dev players test failed with an exception", e);
      } finally {
         this.restoreSkin();
         this.run(() -> {
            BedrockPlayers.setEnabled(original);
            this.minecraft.options.setCameraType(camera);
         });
         this.finish();
      }
   }

   private void test() {
      if (!this.waitFor(30000L, () -> BedrockModels.registry() != null)) {
         this.fail("the model registry never loaded");
      } else {
         ModelRegistry registry = this.onClient(BedrockModels::registry);
         this.check("the base game's minecraft:player client entity is loaded (the player's Bedrock install)", registry.entity("minecraft:player") != null);
         String support = this.onClient(() -> BedrockPlayers.support(registry).reason());
         this.check("the runtime can draw players: " + support, this.onClient(() -> BedrockPlayers.support(registry).drawn()));
         PlayerSkins skins = this.onClient(() -> BedrockActors.current().skins());
         UUID uuid = this.onClient(() -> this.minecraft.player.getUUID());
         if (!this.onClient(BedrockPlayers::enabled)) {
            this.check(
               "launched with the switch off no skin was decoded or kept (" + this.onClient(skins::decoded) + " decoded)",
               this.onClient(skins::decoded) == 0 && this.onClient(() -> skins.entry(uuid)) == null
            );
         }

         log("PlayerSkin packets received so far: {} ({} about the local player)", this.onClient(skins::received), this.onClient(skins::receivedOwn));
         BlockPos at = this.onClient(() -> this.minecraft.player.blockPosition());
         this.commands(
            "gamemode creative",
            "time set noon",
            "weather clear",
            "replaceitem entity @s slot.weapon.mainhand 0 air",
            "replaceitem entity @s slot.weapon.offhand 0 air",
            String.format(Locale.ROOT, "tp @s %d %d %d 0 0", at.getX(), at.getY(), at.getZ())
         );
         this.run(() -> {
            BedrockPlayers.setEnabled(false);
            BedrockPlayers.resetSubmitted();
            this.minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
         });
         sleep(1500L);
         this.screenshot("players-java");
         this.check("with the switch off no player went through the runtime (Java's model)", this.onClient(BedrockPlayers::submitted) == 0);
         this.run(() -> {
            BedrockPlayers.setEnabled(true);
            BedrockPlayers.resetSubmitted();
         });
         this.check(
            "the local player has its login skin (" + this.onClient(() -> describe(skins.entry(uuid))) + ")", this.onClient(() -> skins.entry(uuid) != null)
         );
         if (!this.waitFor(15000L, () -> this.onClient(() -> BedrockPlayers.model(this.minecraft.player) != null))) {
            this.fail("the local player's skin model was never built");
         } else {
            sleep(1000L);
            this.screenshot("players-third-back");
            this.check("the local player is drawn by the runtime in third person", this.onClient(BedrockPlayers::submitted) > 0);
            String loginKey = this.onClient(() -> skins.drawn(skins.entry(uuid)).key());
            this.drawnSkin("the dev account's skin", loginKey);
            String loginTexture = this.baseTexture();
            this.check(
               "the frame draws the skin's own image (" + loginTexture + ")",
               loginTexture.startsWith("bedrocknative:skin/" + loginKey + "/")
                  || loginTexture.equals("textures/entity/steve")
                  || loginTexture.equals("textures/entity/alex")
            );
            this.run(() -> this.minecraft.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
            sleep(1000L);
            this.screenshot("players-third-front");
            SerializedSkin slim = this.classic("alex.png", true, false);
            if (slim == null) {
               this.fail("no vanilla skin pack in the install (" + SKIN_PACK + ")");
            } else {
               int echoes = this.onClient(skins::receivedOwn);
               this.changeSkin(skins, uuid, slim);
               String key = PlayerSkin.of(slim).key();
               this.drawnSkin("slim Alex after the skin change", key);
               this.check("slim arms are 3 pixels wide", this.armWidth() == 3.0F);
               this.check("the frame draws Alex's image now", this.baseTexture().startsWith("bedrocknative:skin/" + key + "/"));
               this.screenshot("players-slim");
               log("server echoes of the local skin change: {}", this.onClient(skins::receivedOwn) - echoes);
            }

            SerializedSkin caped = this.classic("steve.png", false, true);
            if (caped != null) {
               this.changeSkin(skins, uuid, caped);
               String key = PlayerSkin.of(caped).key();
               this.drawnSkin("wide Steve with a cape", key);
               this.check("wide arms are 4 pixels wide", this.armWidth() == 4.0F);
               this.check("the skin model has the cape", this.onClient(() -> BedrockPlayers.model(this.minecraft.player).cape()));
               this.run(() -> this.minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK));
               sleep(1000L);
               this.check("the cape render controller draws the cape with the skin's cape image", this.onClient(() -> {
                  ActorSnapshot frame = BedrockPlayers.lastFrame(this.minecraft.player);

                  for (int c = 0; frame != null && c < frame.draw().calls(); c++) {
                     if (frame.draw().call(c).controller().equals("controller.render.player.cape") && frame.draw().call(c).groups() > 0) {
                        return ("bedrocknative:skin/" + key + "/cape").equals(frame.texturePath(c));
                     }
                  }

                  return false;
               }));
               this.screenshot("players-cape");
            }

            this.run(() -> this.minecraft.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
            this.waitFor(10000L, () -> !this.onClient(BedrockPlayers::emotes).isEmpty());
            log("emotes of the install: {}", this.onClient(BedrockPlayers::emotes));
            boolean started = this.onClient(() -> BedrockPlayers.emote(this.minecraft.player, "4c8ae710-df2e-47cd-814d-cc7bf21a3d67"));
            sleep(700L);
            this.check("the install's wave emote plays on the local player", started && this.onClient(() -> BedrockPlayers.emoting(this.minecraft.player)));
            this.screenshot("players-emote");
            sleep(2000L);
            this.run(() -> {
               this.minecraft.options.setCameraType(CameraType.FIRST_PERSON);
               BedrockPlayers.resetSubmitted();
            });
            sleep(1500L);
            this.check("the local player's frame runs in first person", this.onClient(BedrockPlayers::localFirstPerson));
            this.check("the empty first-person arm is drawn by the runtime", this.onClient(BedrockPlayers::firstPersonSubmitted) > 0);
            this.screenshot("players-first-person");
            this.run(() -> this.minecraft.player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false));
            sleep(120L);
            this.screenshot("players-first-person-swing");
            this.firstPersonAtPitches();
            this.report();
            this.check("no player's model failed", this.onClient(() -> BedrockActors.current().states().stream().noneMatch(ActorState::failed)));
            this.check("no model group failed to draw", !this.onClient(BedrockMobs::drawErrors));
            log("skin images in use: {}, uploaded uncomposed: {}", this.onClient(SkinTextures::size), this.onClient(SkinTextures::uploaded));
         }
      }
   }

   private void firstPersonOnly() {
      if (!this.waitFor(30000L, () -> BedrockModels.registry() != null)) {
         this.fail("the model registry never loaded");
      } else {
         BlockPos at = this.onClient(() -> this.minecraft.player.blockPosition());
         this.commands(
            "gamemode creative",
            "time set noon",
            "weather clear",
            "replaceitem entity @s slot.weapon.offhand 0 air",
            String.format(Locale.ROOT, "tp @s %d %d %d 0 0", at.getX(), at.getY(), at.getZ())
         );
         this.run(() -> BedrockPlayers.setEnabled(true));
         if (!this.waitFor(15000L, () -> this.onClient(() -> BedrockPlayers.model(this.minecraft.player) != null))) {
            this.fail("the local player's skin model was never built");
         } else {
            this.firstPersonAtPitches();
            this.check("no player's model failed", this.onClient(() -> BedrockActors.current().states().stream().noneMatch(ActorState::failed)));
            this.check("no model group failed to draw", !this.onClient(BedrockMobs::drawErrors));
         }
      }
   }

   private void firstPersonAtPitches() {
      if (!this.onClient(BedrockPlayers::enabled)) {
         this.run(() -> BedrockPlayers.setEnabled(true));
      }

      this.run(() -> this.minecraft.options.setCameraType(CameraType.FIRST_PERSON));

      for (String hand : new String[]{"empty", "sword"}) {
         boolean empty = hand.equals("empty");
         this.commands("replaceitem entity @s slot.weapon.mainhand 0 " + (empty ? "air" : "diamond_sword"));
         if (!this.waitFor(5000L, () -> this.onClient(() -> this.minecraft.player.getMainHandItem().isEmpty() == empty))) {
            this.fail("first person: the main hand never became " + hand);
         } else {
            sleep(1500L);
            this.run(BedrockPlayers::resetSubmitted);
            Map<Integer, FirstPersonProbe.Box> boxes = new LinkedHashMap<>();

            for (int pitch : FIRST_PERSON_PITCHES) {
               float xRot = pitch;
               this.run(() -> {
                  this.minecraft.player.setXRot(xRot);
                  this.minecraft.player.xRotO = xRot;
               });
               sleep(1200L);
               long since = System.nanoTime();
               this.waitFor(2000L, () -> {
                  FirstPersonProbe.Box boxx = this.onClient(() -> empty ? FirstPersonProbe.arm() : FirstPersonProbe.item());
                  return boxx != null && boxx.nanos() > since;
               });
               FirstPersonProbe.Box box = this.onClient(() -> empty ? FirstPersonProbe.arm() : FirstPersonProbe.item());
               boxes.put(pitch, box != null && box.nanos() > since ? box : null);
               this.screenshot("first-person-" + hand + "-" + (pitch < 0 ? "m" + -pitch : String.valueOf(pitch)));
            }

            this.run(() -> {
               this.minecraft.player.setXRot(0.0F);
               this.minecraft.player.xRotO = 0.0F;
            });
            if (empty) {
               this.check("the empty first-person arm is drawn by the runtime", this.onClient(BedrockPlayers::firstPersonSubmitted) > 0);
               this.handFixedWithoutBobbing(boxes.get(0));
            } else {
               this.check(
                  "holding a sword the runtime draws no first-person arm (Java's item, the arm hidden as on Bedrock)",
                  this.onClient(BedrockPlayers::firstPersonSubmitted) == 0
               );
            }

            FirstPersonProbe.Box reference = boxes.get(0);
            int largest = 0;
            boolean seen = reference != null;

            for (FirstPersonProbe.Box box : boxes.values()) {
               seen &= box != null;
               if (box != null && reference != null) {
                  largest = Math.max(largest, box.distance(reference));
               }
            }

            boolean pass = seen && largest <= 2;
            String line = String.format(
               Locale.ROOT,
               "first person %s %s: %s box %s at pitch 0, largest move %d px over pitches %s (boxes %s)",
               pass ? "PASS" : "FAIL",
               hand,
               empty ? "arm" : "item",
               reference,
               largest,
               boxes.keySet(),
               boxes.values()
            );
            log("{}", line);
            this.check("first person " + hand + ": fixed on screen at every pitch (within 2 px)", pass);
         }
      }

      this.commands("replaceitem entity @s slot.weapon.mainhand 0 air");
   }

   private void handFixedWithoutBobbing(FirstPersonProbe.@Nullable Box rest) {
      boolean bobbing = this.onClient(() -> this.minecraft.options.bobView().get());
      this.run(() -> this.minecraft.options.bobView().set(false));

      try {
         this.run(BedrockPlayers::resetSubmitted);
         float yRot = this.onClient(() -> this.minecraft.player.getYRot());
         this.run(() -> {
            this.minecraft.player.setYRot(yRot + 60.0F);
            this.minecraft.player.yRotO = yRot + 60.0F;
         });
         int largest = 0;
         boolean seen = rest != null;

         for (int sample = 0; sample < 2; sample++) {
            sleep(sample == 0 ? 50L : 1200L);
            long since = System.nanoTime();
            this.waitFor(2000L, () -> {
               FirstPersonProbe.Box boxx = this.onClient(FirstPersonProbe::arm);
               return boxx != null && boxx.nanos() > since;
            });
            FirstPersonProbe.Box box = this.onClient(FirstPersonProbe::arm);
            seen &= box != null && box.nanos() > since;
            if (box != null && rest != null) {
               largest = Math.max(largest, box.distance(rest));
            }
         }

         boolean pass = seen && largest <= 2;
         log(
            "{}",
            String.format(Locale.ROOT, "first person %s no bobbing: largest move %d px during and after a 60 degree turn", pass ? "PASS" : "FAIL", largest)
         );
         this.check("first person: with View Bobbing off the hand is fixed during a turn (within 2 px)", pass);
         this.run(() -> {
            this.minecraft.player.setYRot(yRot);
            this.minecraft.player.yRotO = yRot;
         });
      } finally {
         this.run(() -> this.minecraft.options.bobView().set(bobbing));
      }
   }

   private void changeSkin(PlayerSkins skins, UUID uuid, SerializedSkin skin) {
      PlayerSkinPacket packet = new PlayerSkinPacket();
      packet.setUuid(uuid);
      packet.setSkin(skin);
      packet.setNewSkinName("");
      packet.setOldSkinName("");
      packet.setTrustedSkin(skin.isTrusted());
      String key = PlayerSkin.of(skin).key();
      this.run(() -> {
         this.send.accept(packet);
         skins.setOwn(uuid, skin);
      });
      this.waitFor(15000L, () -> this.onClient(() -> {
         SkinModel model = BedrockPlayers.model(this.minecraft.player);
         return model != null && model.skin().key().equals(key);
      }));
      sleep(1500L);
   }

   private void restoreSkin() {
      try {
         BedrockActors actors = this.onClient(BedrockActors::current);
         if (actors == null || this.minecraft.player == null) {
            return;
         }

         SerializedSkin login = this.onClient(() -> actors.skins().localSkin());
         UUID uuid = this.onClient(() -> this.minecraft.player.getUUID());
         if (login != null && this.onClient(() -> actors.skins().changes()) > 0) {
            this.changeSkin(actors.skins(), uuid, login);
            this.check("the login skin is back", this.onClient(() -> {
               SkinModel model = BedrockPlayers.model(this.minecraft.player);
               return model != null && model.skin().key().equals(PlayerSkin.of(login).key());
            }));
            this.screenshot("players-restored");
         }
      } catch (RuntimeException e) {
         this.fail("putting the login skin back: " + e);
      }
   }

   private @Nullable SerializedSkin classic(String texture, boolean slim, boolean cape) {
      if (!Files.isRegularFile(SKIN_PACK.resolve(texture))) {
         return null;
      }

      try {
         JsonObject claims = BedrockSkin.classicFromSkinPack(SKIN_PACK, texture, slim).claims();
         SerializedSkin skin = SkinClaims.toSerialized(claims);
         if (cape) {
            skin = skin.toBuilder()
               .capeData(ImageData.of(64, 32, capeImage()))
               .capeId("bedrocknative-dev-cape")
               .capeOnClassic(true)
               .fullSkinId(skin.getSkinId() + "bedrocknative-dev-cape")
               .build();
         }

         return skin;
      } catch (Exception e) {
         this.fail("reading " + texture + " from the skin pack: " + e);
         return null;
      }
   }

   private static byte[] capeImage() {
      byte[] rgba = new byte[8192];

      for (int y = 0; y < 32; y++) {
         for (int x = 0; x < 64; x++) {
            boolean cross = Math.abs(x % 11 - 5) <= 1 || Math.abs(y % 17 - 8) <= 1;
            int p = (y * 64 + x) * 4;
            rgba[p] = (byte)(cross ? 240 : 24);
            rgba[p + 1] = (byte)(cross ? 200 : 48);
            rgba[p + 2] = (byte)(cross ? 32 : 160);
            rgba[p + 3] = -1;
         }
      }

      return rgba;
   }

   private void drawnSkin(String what, String key) {
      String described = this.onClient(() -> BedrockPlayers.describe(this.minecraft.player));
      this.check("the local player draws " + what + " (" + described + ")", this.onClient(() -> {
         SkinModel model = BedrockPlayers.model(this.minecraft.player);
         return model != null && model.drawn().key().equals(key);
      }));
   }

   private String baseTexture() {
      return this.onClient(() -> {
         ActorSnapshot frame = BedrockPlayers.lastFrame(this.minecraft.player);
         String path = frame != null ? frame.layerPath(0, 0) : null;
         return path != null ? path : "";
      });
   }

   private float armWidth() {
      return this.onClient(() -> {
         SkinModel model = BedrockPlayers.model(this.minecraft.player);
         ModelRegistry registry = BedrockModels.registry();
         if (model != null && registry != null) {
            MeshedGeometry mesh = model.mesh(model.defaultGeometry());
            if (mesh == null) {
               mesh = registry.mesh(model.defaultGeometry());
            }

            int arm = mesh != null ? mesh.bone("rightArm") : -1;
            if (arm < 0) {
               return -1.0F;
            }

            float[] quads = mesh.bone(arm).quads();
            float min = Float.MAX_VALUE;
            float max = -Float.MAX_VALUE;

            for (int i = 0; i < quads.length; i += 8) {
               min = Math.min(min, quads[i]);
               max = Math.max(max, quads[i]);
            }

            float width = max - min;
            log("right arm of {}: {} pixels wide", model.defaultGeometry(), width);
            return Math.round(width * 100.0F) / 100.0F;
         } else {
            return -1.0F;
         }
      });
   }

   private void report() {
      List<String> players = this.onClient(() -> {
         List<String> lines = new ArrayList<>();

         for (Entity entity : this.minecraft.level.entitiesForRendering()) {
            if (entity instanceof Player player) {
               lines.add(player.getName().getString() + (player == this.minecraft.player ? " (local)" : "") + ": " + BedrockPlayers.describe(player));
            }
         }

         return lines;
      });
      players.forEach(line -> log("{}", line));
      log("emote packets: {}", this.onClient(() -> BedrockActors.current().emoteLog()));
   }

   private static String describe(PlayerSkins.@Nullable Entry entry) {
      return entry == null ? "none" : entry.skin().describe() + (entry.trusted() ? ", trusted" : ", untrusted");
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
      BedrockNative.LOGGER.warn("Dev players test FAILED: {}", what);
   }

   private void finish() {
      log("{} of {} checks passed{}", this.passed, this.checks, this.failures.isEmpty() ? "" : "; failed: " + this.failures);
      log("done");
   }

   private static void log(String message, Object... arguments) {
      BedrockNative.LOGGER.info("Dev players test: " + message, arguments);
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
}
