package net.bedrocknative.client.net;

import com.mojang.blaze3d.audio.ListenerTransform;
import com.mojang.blaze3d.platform.NativeImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.camera.CameraDirector;
import net.bedrocknative.camera.CameraPresetBook;
import net.bedrocknative.client.camera.BedrockCamera;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.cloudburstmc.math.vector.Vector2f;
import org.cloudburstmc.math.vector.Vector3d;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.ControlScheme;
import org.cloudburstmc.protocol.bedrock.data.camera.CameraAudioListener;
import org.cloudburstmc.protocol.bedrock.data.camera.CameraPreset;
import org.cloudburstmc.protocol.bedrock.data.camera.CameraSetInstruction;
import org.cloudburstmc.protocol.bedrock.data.camera.CameraSplineInstruction;
import org.cloudburstmc.protocol.bedrock.data.camera.CameraSplineType;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.CameraInstructionPacket;
import org.cloudburstmc.protocol.bedrock.packet.CameraPresetsPacket;
import org.cloudburstmc.protocol.bedrock.packet.ClientboundControlSchemeSetPacket;
import org.cloudburstmc.protocol.bedrock.packet.TransferPacket;
import org.cloudburstmc.protocol.common.DefinitionRegistry;
import org.cloudburstmc.protocol.common.NamedDefinition;
import org.cloudburstmc.protocol.common.util.OptionalBoolean;

final class DevCameraTest {
   private static final double HEIGHT = 30.0;
   private static volatile boolean transferred;
   private static int checks;
   private static int failed;
   private final Minecraft minecraft;
   private final Consumer<BedrockPacket> inject;
   private final MovementStats movement;
   private int walkCorrections;
   private Vec3 eye = Vec3.ZERO;

   DevCameraTest(Minecraft minecraft, Consumer<BedrockPacket> inject, MovementStats movement) {
      this.minecraft = minecraft;
      this.inject = inject;
      this.movement = movement;
   }

   void run() {
      if (transferred) {
         this.afterTransfer();
      } else {
         this.setUp();
         String devTest = System.getProperty("bedrocknative.devTest", "");
         if (devTest.startsWith("camera-")) {
            String var4 = devTest.substring("camera-".length());
            switch (var4) {
               case "free":
                  this.free();
                  this.ease();
                  this.facing();
                  break;
               case "presets":
                  this.presets();
                  break;
               case "fov":
                  this.fieldOfView();
                  break;
               case "fade":
                  this.fade();
                  break;
               case "target":
                  this.targetAndAttach();
                  break;
               case "spline":
                  this.spline();
                  break;
               case "listener":
                  this.listener();
                  break;
               case "scheme":
                  this.controlScheme();
                  break;
               case "dimension":
                  this.dimension();
                  break;
               default:
                  BedrockNative.LOGGER.info("Dev camera test: no part called {}", devTest);
            }

            this.clear();
            this.done();
         } else {
            this.receivedPresets();
            this.free();
            this.ease();
            this.facing();
            this.presets();
            this.viewOffset();
            this.fieldOfView();
            this.fade();
            this.targetAndAttach();
            this.spline();
            this.listener();
            this.controlScheme();
            this.clear();
            this.dimension();
            this.command(String.format(Locale.ROOT, "camera @s set minecraft:free pos %.2f %.2f %.2f rot 0 0", this.eye.x, this.eye.y + 5.0, this.eye.z));
            this.command("camera @s fade time 0 10 0 color 0 0 255");
            sleep(800L);
            this.check(
               "before the transfer: a free camera and a fade run ("
                  + BedrockCamera.forcedCameraType()
                  + ", fade "
                  + Integer.toHexString(BedrockCamera.fadeColour())
                  + ", received "
                  + BedrockCamera.lastInstruction().replaceAll("\\s+", " ")
                  + ")",
               this.supply(() -> BedrockCamera.forcedCameraType() != null && BedrockCamera.fadeColour() != 0)
            );
            transferred = true;
            TransferPacket transfer = new TransferPacket();
            transfer.setAddress("127.0.0.1");
            String joined = this.supply(() -> this.minecraft.getCurrentServer() == null ? "" : this.minecraft.getCurrentServer().ip);
            transfer.setPort(joined.contains(":") ? Integer.parseInt(joined.substring(joined.lastIndexOf(58) + 1)) : 19132);
            this.inject.accept(transfer);
         }
      }
   }

   private void setUp() {
      this.commands("gamemode creative", "effect @s clear", "time set noon", "weather clear", "kill @e[type=armor_stand]", "camera @s clear");
      this.commands("fill ~-3 ~29 ~-3 ~3 ~29 ~3 glass", "tp @s ~ ~30 ~ 0 0");
      sleep(2000L);
      this.minecraft.execute(() -> {
         this.minecraft.player.setYRot(0.0F);
         this.minecraft.player.setXRot(0.0F);
         this.minecraft.options.setCameraType(CameraType.FIRST_PERSON);
      });
      sleep(500L);
      this.eye = this.supply(() -> this.minecraft.player.getEyePosition());
      BedrockNative.LOGGER.info("Dev camera test: player eye at {}", this.eye);
   }

   private void afterTransfer() {
      sleep(1500L);
      DevCameraTest.Sample now = this.sample();
      this.check(
         "after the transfer the player's own view is back (" + now.type + ", camera " + round(now.distance(now.eye)) + " from the eye) and no fade",
         BedrockCamera.forcedCameraType() == null && now.distance(now.eye) < 0.01 && now.fade == 0
      );
      this.check(
         "and the presets are the new session's (" + this.supply(() -> BedrockCamera.director().presets().names()) + ")",
         this.supply(() -> BedrockCamera.director().presets().names().size()) >= 6
      );
      this.screenshot("camera-transfer");
      this.command("camera @s set minecraft:third_person");
      sleep(600L);
      this.check("the new session takes camera instructions", this.sample().type == CameraType.THIRD_PERSON_BACK);
      this.command("camera @s clear");
      sleep(400L);
      this.done();
   }

   private void receivedPresets() {
      List<String> names = this.supply(() -> BedrockCamera.director().presets().names());
      BedrockNative.LOGGER
         .info(
            "Dev camera test: presets {}, splines {}, aim assist '{}'",
            new Object[]{names, this.supply(() -> BedrockCamera.director().splineCount()), BedrockCamera.lastAimAssist()}
         );
      this.check(
         "BDS sent the vanilla presets",
         names.containsAll(
            List.of(
               "minecraft:first_person",
               "minecraft:third_person",
               "minecraft:third_person_front",
               "minecraft:free",
               "minecraft:fixed_boom",
               "minecraft:follow_orbit"
            )
         )
      );
   }

   private Vec3 freeSpot() {
      return new Vec3(Math.floor(this.eye.x) + 0.5, Math.floor(this.eye.y) + 8.5, Math.floor(this.eye.z) + 0.5);
   }

   private void free() {
      Vec3 spot = this.freeSpot();
      float[] look = this.playerLook();
      this.command(String.format(Locale.ROOT, "camera @s set minecraft:free pos %.2f %.2f %.2f rot 30 90", spot.x, spot.y, spot.z));
      this.waitFor(() -> BedrockCamera.forcedCameraType() != null, 3000L);
      sleep(400L);
      BedrockNative.LOGGER.info("Dev camera test: free set received as {}", BedrockCamera.lastInstruction());
      DevCameraTest.Sample now = this.sample();
      this.check("free camera at " + vec(spot) + ": rendered at " + vec(now.camera) + " (" + round(now.distance(spot)) + " off)", now.distance(spot) < 0.01);
      this.check(
         "free camera rot 30 90 is pitch 30, yaw 90: rendered pitch " + round(now.pitch) + ", yaw " + round(now.yaw),
         Math.abs(now.pitch - 30.0F) < 0.1 && Math.abs(Mth.wrapDegrees(now.yaw - 90.0F)) < 0.1
      );
      this.check(
         "the player's body shows (third person, detached: " + now.type + ", " + now.detached + ")", now.type == CameraType.THIRD_PERSON_BACK && now.detached
      );
      float[] after = this.playerLook();
      this.check("the player's own rotation (what PlayerAuthInput reports) is untouched", look[0] == after[0] && look[1] == after[1]);
      this.minecraft.execute(() -> this.minecraft.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
      sleep(200L);
      this.check("the perspective key does nothing while a preset is set (" + this.sample().type + ")", this.sample().type == CameraType.THIRD_PERSON_BACK);
      this.minecraft.execute(() -> this.minecraft.player.setYRot(this.minecraft.player.getYRot() + 45.0F));
      sleep(300L);
      DevCameraTest.Sample turned = this.sample();
      this.check(
         "turning the player leaves the free camera where it is (" + round(turned.distance(spot)) + " off, yaw " + round(turned.yaw) + ")",
         turned.distance(spot) < 0.01 && Math.abs(Mth.wrapDegrees(turned.yaw - 90.0F)) < 0.1
      );
      this.minecraft.execute(() -> this.minecraft.player.setYRot(0.0F));
      this.screenshot("camera-free");
   }

   private void ease() {
      Vec3 from = this.freeSpot();
      Vec3 to = from.add(6.0, 0.0, 0.0);
      this.command(String.format(Locale.ROOT, "camera @s set minecraft:free ease 2 in_out_sine pos %.2f %.2f %.2f rot 0 0", to.x, to.y, to.z));
      this.waitFor(() -> BedrockCamera.director().easing(System.nanoTime() / 1.0E9), 3000L);
      long started = System.currentTimeMillis();
      BedrockNative.LOGGER.info("Dev camera test: ease received as {}", BedrockCamera.lastInstruction());
      sleep(1000L - (System.currentTimeMillis() - started));
      DevCameraTest.Sample mid = this.sample();
      this.screenshot("camera-ease-mid");
      this.check(
         "half way through the ease the camera is between the two spots ("
            + round(mid.distance(from))
            + " from the start, "
            + round(mid.distance(to))
            + " from the end, "
            + vec(mid.camera)
            + ")",
         mid.distance(from) > 1.0 && mid.distance(to) > 1.0 && Math.abs(mid.camera.y - from.y) < 0.01 && Math.abs(mid.camera.z - from.z) < 0.01
      );
      this.check("and turning (yaw " + round(mid.yaw) + " between 90 and 0)", mid.yaw > 5.0F && mid.yaw < 85.0F);
      sleep(1600L);
      DevCameraTest.Sample end = this.sample();
      this.check(
         "the ease ends on the new spot (" + round(end.distance(to)) + " off) and rotation (yaw " + round(end.yaw) + ", pitch " + round(end.pitch) + ")",
         end.distance(to) < 0.01 && Math.abs(end.yaw) < 0.1 && Math.abs(end.pitch) < 0.1
      );
      this.screenshot("camera-ease-end");
   }

   private void facing() {
      Vec3 spot = this.freeSpot();
      this.command(
         String.format(
            Locale.ROOT, "camera @s set minecraft:free pos %.2f %.2f %.2f facing %.2f %.2f %.2f", spot.x, spot.y, spot.z, spot.x + 10.0, spot.y, spot.z
         )
      );
      sleep(600L);
      DevCameraTest.Sample east = this.sample();
      this.check(
         "facing a point 10 blocks east turns the camera to yaw -90 (" + round(east.yaw) + "), pitch 0 (" + round(east.pitch) + ")",
         Math.abs(Mth.wrapDegrees(east.yaw + 90.0F)) < 0.5 && Math.abs(east.pitch) < 0.5
      );
      this.command(
         String.format(
            Locale.ROOT, "camera @s set minecraft:free pos %.2f %.2f %.2f facing %.2f %.2f %.2f", spot.x, spot.y, spot.z, spot.x, spot.y - 10.0, spot.z + 0.01
         )
      );
      sleep(600L);
      DevCameraTest.Sample down = this.sample();
      this.check("facing a point below looks straight down (pitch " + round(down.pitch) + ")", down.pitch > 89.0F);
      this.screenshot("camera-facing-down");
   }

   private void presets() {
      this.command("camera @s set minecraft:third_person");
      sleep(700L);
      DevCameraTest.Sample back = this.sample();
      this.check(
         "third_person: behind the player (" + round(back.ahead()) + " along the look, " + round(back.distance(back.eye)) + " away), " + back.type,
         back.type == CameraType.THIRD_PERSON_BACK && back.ahead() < -1.0 && back.distance(back.eye) <= 4.05
      );
      this.screenshot("camera-third-person");
      this.command("camera @s set minecraft:third_person_front");
      sleep(700L);
      DevCameraTest.Sample front = this.sample();
      this.check(
         "third_person_front: in front of the player looking back (" + round(front.ahead()) + " along the look), " + front.type,
         front.type == CameraType.THIRD_PERSON_FRONT && front.ahead() > 1.0 && front.distance(front.eye) <= 4.05
      );
      this.screenshot("camera-third-person-front");
      this.command("camera @s set minecraft:follow_orbit");
      sleep(700L);
      DevCameraTest.Sample orbit = this.sample();
      this.check(
         "follow_orbit: behind the player at its radius 10 (" + round(orbit.distance(orbit.eye)) + " away, " + round(orbit.ahead()) + " along the look)",
         orbit.ahead() < -2.0 && orbit.distance(orbit.eye) <= 10.05 && orbit.distance(orbit.eye) > 2.0
      );
      this.minecraft.execute(() -> this.minecraft.player.setYRot(90.0F));
      sleep(400L);
      DevCameraTest.Sample turned = this.sample();
      this.check(
         "follow_orbit turns with the player (camera yaw " + round(turned.yaw) + ", " + round(turned.ahead()) + " along the look)",
         Math.abs(Mth.wrapDegrees(turned.yaw - 90.0F)) < 0.5 && turned.ahead() < -2.0
      );
      this.screenshot("camera-follow-orbit");
      this.command("camera @s set minecraft:fixed_boom");
      sleep(700L);
      DevCameraTest.Sample boom = this.sample();
      this.minecraft.execute(() -> this.minecraft.player.setYRot(180.0F));
      sleep(400L);
      DevCameraTest.Sample boomTurned = this.sample();
      BedrockNative.LOGGER
         .info(
            "Dev camera test: fixed_boom at {} yaw {} pitch {}, after the player turned at {} yaw {}",
            new Object[]{vec(boom.camera), round(boom.yaw), round(boom.pitch), vec(boomTurned.camera), round(boomTurned.yaw)}
         );
      this.check(
         "fixed_boom keeps its rotation when the player turns (yaw "
            + round(boom.yaw)
            + " then "
            + round(boomTurned.yaw)
            + ") and its distance ("
            + round(boomTurned.distance(boomTurned.eye))
            + ")",
         Math.abs(Mth.wrapDegrees(boom.yaw - boomTurned.yaw)) < 0.1 && boomTurned.distance(boomTurned.eye) <= 10.05
      );
      this.screenshot("camera-fixed-boom");
      this.minecraft.execute(() -> this.minecraft.player.setYRot(0.0F));
      this.command("camera @s set minecraft:first_person");
      sleep(700L);
      DevCameraTest.Sample first = this.sample();
      this.check(
         "first_person: at the eye (" + round(first.distance(first.eye)) + " off), " + first.type,
         first.type == CameraType.FIRST_PERSON && first.distance(first.eye) < 0.01 && Math.abs(Mth.wrapDegrees(first.yaw - first.playerYaw)) < 0.01
      );
      this.minecraft.execute(() -> this.minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK));
      sleep(200L);
      this.check(
         "first_person keeps the player in first person (perspective key locked: " + this.sample().type + ")", this.sample().type == CameraType.FIRST_PERSON
      );
   }

   private void viewOffset() {
      this.command("camera @s set minecraft:follow_orbit view_offset 1.5 0.5");
      sleep(700L);
      DevCameraTest.Sample shifted = this.sample();
      Vec3 behind = shifted.eye.subtract(Vec3.directionFromRotation(shifted.playerPitch, shifted.playerYaw).scale(10.0));
      Vector3d right = CameraDirector.right(shifted.playerYaw);
      Vector3d up = right.cross(CameraDirector.forward(shifted.playerYaw, shifted.playerPitch));
      Vec3 wanted = behind.add(right.getX() * 1.5 + up.getX() * 0.5, right.getY() * 1.5 + up.getY() * 0.5, right.getZ() * 1.5 + up.getZ() * 0.5);
      this.check(
         "view_offset 1.5 0.5 moves the follow_orbit camera right and up (BDS refuses an offset on the other presets) ("
            + round(shifted.distance(wanted))
            + " from "
            + vec(wanted)
            + ")",
         shifted.distance(wanted) < 0.05
      );
      this.screenshot("camera-view-offset");
   }

   private void fieldOfView() {
      this.command("camera @s set minecraft:first_person");
      sleep(500L);
      float own = this.sample().fov;
      int option = this.supply(() -> (Integer)this.minecraft.options.fov().get());
      this.command("camera @s fov_set 30 0.5 linear");
      sleep(1200L);
      BedrockNative.LOGGER.info("Dev camera test: field of view received as {}", BedrockCamera.lastInstruction());
      float narrow = this.sample().fov;
      this.check(
         "fov_set 30: rendered " + round(narrow) + " (own " + round(own) + " at option " + option + ")", Math.abs(narrow - own * 30.0F / option) < own * 0.03F
      );
      this.screenshot("camera-fov-30");
      this.command("camera @s fov_clear 0.5 linear");
      sleep(1200L);
      float back = this.sample().fov;
      this.check("fov_clear: back to the player's own (" + round(back) + ")", Math.abs(back - own) < own * 0.03F);
   }

   private void fade() {
      double before = this.redShare();
      this.check("before the fade the frame is not red (" + percent(before) + " of the pixels read back)", before < 0.05);
      this.command("camera @s fade time 0.5 2 0.5 color 255 0 0");
      this.waitFor(() -> BedrockCamera.fadeColour() != 0, 2000L);
      BedrockNative.LOGGER.info("Dev camera test: fade received as {}", BedrockCamera.lastInstruction());
      sleep(700L);
      int colour = this.sample().fade;
      this.check(
         "fade holds a red screen (ARGB " + Integer.toHexString(colour) + ")", colour >>> 24 >= 250 && (colour >> 16 & 0xFF) >= 250 && (colour & 65535) == 0
      );
      double during = this.redShare();
      this.check("the frame the game rendered is red during the hold (" + percent(during) + " of the pixels read back)", during > 0.95);
      this.screenshot("camera-fade-red");
      sleep(2400L);
      this.check("and the fade is gone after 3 s (" + Integer.toHexString(this.sample().fade) + ")", this.sample().fade == 0);
      double after = this.redShare();
      this.check("and the rendered frame is not red any more (" + percent(after) + " of the pixels read back)", after < 0.05);
      this.command("camera @s fade");
      sleep(300L);
      BedrockNative.LOGGER
         .info("Dev camera test: a bare fade arrived as {} (colour now {})", BedrockCamera.lastInstruction(), Integer.toHexString(this.sample().fade));
      this.check("a bare /camera fade fades", BedrockCamera.lastInstruction().contains("fadeInstruction=CameraFadeInstruction"));
      this.waitFor(() -> BedrockCamera.fadeColour() == 0, 10000L);
   }

   private void targetAndAttach() {
      Vec3 stand = this.eye.add(2.0, -1.62, 2.0);
      this.commands(String.format(Locale.ROOT, "summon armor_stand %.2f %.2f %.2f", stand.x, stand.y, stand.z));
      sleep(800L);
      Vec3 spot = this.freeSpot();
      this.command(String.format(Locale.ROOT, "camera @s set minecraft:free pos %.2f %.2f %.2f rot 0 0", spot.x, spot.y, spot.z));
      sleep(400L);
      this.command("camera @s target_entity @e[type=armor_stand,c=1]");
      sleep(800L);
      BedrockNative.LOGGER.info("Dev camera test: target received as {}", BedrockCamera.lastInstruction());
      if (BedrockCamera.lastInstruction().contains("targetInstruction=CameraTargetInstruction")) {
         DevCameraTest.Sample aimed = this.sample();
         Vec3 centre = this.supply(
            () -> {
               Entity found = (Entity)this.minecraft
                  .level
                  .getEntities((Entity)null, this.minecraft.player.getBoundingBox().inflate(20.0), entity -> entity.getType() == EntityTypes.ARMOR_STAND)
                  .stream()
                  .findFirst()
                  .orElse(null);
               return found == null ? null : found.getBoundingBox().getCenter();
            }
         );
         double angle = centre == null
            ? 180.0
            : Math.toDegrees(Math.acos(Mth.clamp(Vec3.directionFromRotation(aimed.pitch, aimed.yaw).dot(centre.subtract(aimed.camera).normalize()), -1.0, 1.0)));
         this.check("target_entity turns the free camera onto the armor stand (" + round(angle) + " degrees off)", angle < 2.0);
         this.screenshot("camera-target");
         this.command("camera @s remove_target");
         sleep(600L);
         DevCameraTest.Sample free = this.sample();
         this.check(
            "remove_target: the free camera keeps its own rotation again (yaw " + round(free.yaw) + ", pitch " + round(free.pitch) + ")",
            Math.abs(free.yaw) < 0.1 && Math.abs(free.pitch) < 0.1
         );
      } else {
         BedrockNative.LOGGER.info("Dev camera test: BDS sent no target instruction (command refused?), target not checked");
      }

      this.command("camera @s set minecraft:first_person");
      sleep(300L);
      this.command("camera @s attach_to_entity @e[type=armor_stand,c=1]");
      sleep(800L);
      BedrockNative.LOGGER.info("Dev camera test: attach received as {}", BedrockCamera.lastInstruction());
      if (BedrockCamera.lastInstruction().contains("attachInstruction=CameraAttachToEntityInstruction")) {
         DevCameraTest.Sample attached = this.sample();
         Vec3 standEye = this.supply(
            () -> this.minecraft
               .level
               .getEntities((Entity)null, this.minecraft.player.getBoundingBox().inflate(20.0), entity -> entity.getType() == EntityTypes.ARMOR_STAND)
               .stream()
               .findFirst()
               .<Vec3>map(Entity::getEyePosition)
               .orElse(Vec3.ZERO)
         );
         this.check(
            "attach_to_entity: the first person camera sits at the armor stand's eye (" + round(attached.distance(standEye)) + " off)",
            attached.distance(standEye) < 0.2
         );
         this.screenshot("camera-attached");
         this.command("camera @s detach_from_entity");
         sleep(600L);
         DevCameraTest.Sample detached = this.sample();
         this.check("detach_from_entity: back at the player's eye (" + round(detached.distance(detached.eye)) + " off)", detached.distance(detached.eye) < 0.01);
      } else {
         BedrockNative.LOGGER.info("Dev camera test: BDS sent no attach instruction (command refused?), attach not checked");
      }

      this.commands("kill @e[type=armor_stand]");
   }

   private void spline() {
      Vec3 a = this.eye.add(0.0, 4.0, -6.0);
      Vec3 b = this.eye.add(6.0, 4.0, -6.0);
      Vec3 c = this.eye.add(6.0, 4.0, 0.0);
      CameraInstructionPacket packet = new CameraInstructionPacket();
      packet.setSplineInstruction(new CameraSplineInstruction(4.0F, CameraSplineType.LINEAR, List.of(of(a), of(b), of(c)), List.of(), List.of()));
      this.inject.accept(packet);
      sleep(2000L);
      DevCameraTest.Sample mid = this.sample();
      this.check("spline: half way along it the camera is at the middle point (" + round(mid.distance(b)) + " off " + vec(b) + ")", mid.distance(b) < 0.4);
      this.screenshot("camera-spline");
      sleep(2500L);
      DevCameraTest.Sample end = this.sample();
      this.check("spline: it ends on the last point (" + round(end.distance(c)) + " off)", end.distance(c) < 0.01);
   }

   private void listener() {
      List<CameraPreset> presets = new ArrayList<>(CameraPresetBook.VANILLA);
      presets.add(CameraPreset.builder().identifier("bn:ears_player").parentPreset("minecraft:free").listener(CameraAudioListener.PLAYER).build());
      presets.add(CameraPreset.builder().identifier("bn:ears_camera").parentPreset("minecraft:free").listener(CameraAudioListener.CAMERA).build());
      this.sendPresets(presets);
      DefinitionRegistry<NamedDefinition> registry = CameraPresetBook.definitions(presets);
      Vec3 spot = this.freeSpot();
      this.setFree(registry, "bn:ears_player", spot);
      sleep(800L);
      ListenerTransform initial = ListenerTransform.INITIAL;
      ListenerTransform heard = this.supply(() -> this.minecraft.getSoundManager().getListenerTransform());
      if (heard.position().equals(initial.position())) {
         BedrockNative.LOGGER.info("Dev camera test: the sound engine is not running (no listener moved), the audio listener is not checked");
      } else {
         this.check(
            "a preset with the player as audio listener is heard from the player's eye ("
               + round(heard.position().distanceTo(this.eye))
               + " off, the camera "
               + round(spot.distanceTo(this.eye))
               + " away)",
            heard.position().distanceTo(this.eye) < 0.1
         );
         this.setFree(registry, "bn:ears_camera", spot);
         sleep(800L);
         Vec3 cameraEars = this.supply(() -> this.minecraft.getSoundManager().getListenerTransform().position());
         this.check(
            "and a preset with the camera as audio listener is heard from the camera (" + round(cameraEars.distanceTo(spot)) + " off)",
            cameraEars.distanceTo(spot) < 0.1
         );
      }

      this.injectClear();
      sleep(600L);
      this.sendPresets(CameraPresetBook.VANILLA);
   }

   private void injectClear() {
      CameraInstructionPacket packet = new CameraInstructionPacket();
      packet.setClear(OptionalBoolean.of(true));
      this.inject.accept(packet);
   }

   private void controlScheme() {
      Vec3 stand = this.supply(() -> this.minecraft.player.position());
      Vec3 spot = this.freeSpot();
      List<CameraPreset> presets = new ArrayList<>(CameraPresetBook.VANILLA);
      presets.add(CameraPreset.builder().identifier("bn:topdown").parentPreset("minecraft:free").controlScheme(ControlScheme.CAMERA_RELATIVE).build());
      presets.add(CameraPreset.builder().identifier("bn:plain").parentPreset("minecraft:free").build());
      this.sendPresets(presets);
      DefinitionRegistry<NamedDefinition> registry = CameraPresetBook.definitions(presets);
      this.setFree(registry, "minecraft:free", spot);
      sleep(600L);
      Vec3 along = this.walk(stand, true, false, false, false);
      this.check("default scheme: W walks along the player's look, +z (" + vec(along) + ")", along.z > 1.0 && Math.abs(along.x) < 0.3);
      this.sendScheme(ControlScheme.CAMERA_RELATIVE_STRAFE);
      this.check(
         "the server's camera relative strafe scheme is the one in force (" + this.supply(() -> BedrockCamera.director().controlScheme()) + ")",
         this.supply(() -> BedrockCamera.director().controlScheme()) == ControlScheme.CAMERA_RELATIVE_STRAFE
      );
      Vec3 forward = this.walk(stand, true, false, false, false);
      this.check("camera relative strafe: W walks where the camera looks, -x (" + vec(forward) + ")", forward.x < -1.0 && Math.abs(forward.z) < 0.3);
      Vec3 right = this.walk(stand, false, false, false, true);
      this.check("D walks to the camera's right, -z (" + vec(right) + ")", right.z < -1.0 && Math.abs(right.x) < 0.3);
      Vec3 back = this.walk(stand, false, true, false, false);
      this.check("S walks away from where the camera looks, +x (" + vec(back) + ")", back.x > 1.0 && Math.abs(back.z) < 0.3);
      Vec3 diagonal = this.walk(stand, true, false, true, false);
      this.check("W and A walk forward and to the camera's left, -x +z (" + vec(diagonal) + ")", diagonal.x < -0.8 && diagonal.z > 0.8);
      float[] after = this.playerLook();
      this.check("the player's own rotation is untouched by the scheme (yaw " + after[0] + ", pitch " + after[1] + ")", after[0] == 0.0F && after[1] == 0.0F);
      this.sendScheme(ControlScheme.LOCKED_PLAYER_RELATIVE_STRAFE);
      Vec3 locked = this.walk(stand, true, false, false, false);
      this.check("back to the default scheme: W walks along the player's look again (" + vec(locked) + ")", locked.z > 1.0 && Math.abs(locked.x) < 0.3);
      this.setFree(registry, "bn:topdown", spot);
      sleep(600L);
      this.check(
         "a preset's own camera relative scheme is in force while it is set (" + this.supply(() -> BedrockCamera.director().controlScheme()) + ")",
         this.supply(() -> BedrockCamera.director().controlScheme()) == ControlScheme.CAMERA_RELATIVE
      );
      Vec3 preset = this.walk(stand, true, false, false, false);
      this.check("so W walks where the camera looks (" + vec(preset) + ")", preset.x < -1.0 && Math.abs(preset.z) < 0.3);
      this.injectClear();
      sleep(600L);
      Vec3 cleared = this.walk(stand, true, false, false, false);
      this.check("after a clear the player's controls are back (" + vec(cleared) + ")", cleared.z > 1.0 && Math.abs(cleared.x) < 0.3);
      this.check("the strict BDS took every walk without a correction (" + this.walkCorrections + " while walking)", this.walkCorrections == 0);
      this.sendPresets(CameraPresetBook.VANILLA);
      this.command(String.format(Locale.ROOT, "tp @s %.3f %.3f %.3f 0 0", stand.x, stand.y, stand.z));
      sleep(700L);
   }

   private void setFree(DefinitionRegistry<NamedDefinition> registry, String preset, Vec3 at) {
      CameraSetInstruction set = new CameraSetInstruction();
      set.setPreset(registry.getDefinition(preset));
      set.setPos(of(at));
      set.setRot(Vector2f.from(0.0F, 90.0F));
      CameraInstructionPacket packet = new CameraInstructionPacket();
      packet.setSetInstruction(set);
      this.inject.accept(packet);
   }

   private void sendPresets(List<CameraPreset> presets) {
      CameraPresetsPacket packet = new CameraPresetsPacket();
      packet.getPresets().addAll(presets);
      this.inject.accept(packet);
      sleep(300L);
   }

   private void sendScheme(ControlScheme scheme) {
      ClientboundControlSchemeSetPacket packet = new ClientboundControlSchemeSetPacket();
      packet.setScheme(scheme);
      this.inject.accept(packet);
      sleep(300L);
   }

   private Vec3 walk(Vec3 stand, boolean forward, boolean back, boolean left, boolean right) {
      this.command(String.format(Locale.ROOT, "tp @s %.3f %.3f %.3f 0 0", stand.x, stand.y, stand.z));
      sleep(900L);
      Vec3 from = this.supply(() -> this.minecraft.player.position());
      int before = this.movement.count();
      this.minecraft.execute(() -> {
         this.minecraft.options.keyUp.setDown(forward);
         this.minecraft.options.keyDown.setDown(back);
         this.minecraft.options.keyLeft.setDown(left);
         this.minecraft.options.keyRight.setDown(right);
      });
      sleep(450L);
      this.minecraft.execute(() -> {
         this.minecraft.options.keyUp.setDown(false);
         this.minecraft.options.keyDown.setDown(false);
         this.minecraft.options.keyLeft.setDown(false);
         this.minecraft.options.keyRight.setDown(false);
      });
      sleep(500L);
      this.walkCorrections = this.walkCorrections + (this.movement.count() - before);
      return this.supply(() -> this.minecraft.player.position()).subtract(from);
   }

   private void clear() {
      this.command(String.format(Locale.ROOT, "camera @s set minecraft:free pos %.2f %.2f %.2f rot 10 10", this.eye.x, this.eye.y + 3.0, this.eye.z));
      sleep(500L);
      this.command("camera @s clear");
      sleep(600L);
      BedrockNative.LOGGER.info("Dev camera test: clear received as {}", BedrockCamera.lastInstruction());
      DevCameraTest.Sample now = this.sample();
      this.check(
         "clear: the player's own first person view again (" + now.type + ", camera " + round(now.distance(now.eye)) + " from the eye)",
         BedrockCamera.forcedCameraType() == null && now.type == CameraType.FIRST_PERSON && now.distance(now.eye) < 0.01
      );
      this.minecraft.execute(() -> this.minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK));
      sleep(200L);
      this.check("and the perspective key works again (" + this.sample().type + ")", this.sample().type == CameraType.THIRD_PERSON_BACK);
      this.minecraft.execute(() -> this.minecraft.options.setCameraType(CameraType.FIRST_PERSON));
      sleep(200L);
      this.screenshot("camera-clear");
   }

   private void dimension() {
      Vec3 home = this.supply(() -> this.minecraft.player.position());
      this.command(String.format(Locale.ROOT, "camera @s set minecraft:free pos %.2f %.2f %.2f rot 0 0", this.eye.x, this.eye.y + 5.0, this.eye.z));
      this.command("camera @s fov_set 40");
      sleep(600L);
      this.check(
         "a free camera and a field of view run before the nether",
         BedrockCamera.forcedCameraType() != null
            && this.supply(() -> BedrockCamera.fovFactor(((Integer)this.minecraft.options.fov().get()).intValue())) < 0.99F
      );
      this.commands("execute in nether run tp @s 0 100 0");
      this.waitFor(() -> this.minecraft.level.dimension() == Level.NETHER && this.minecraft.getConnection().hasClientLoaded(), 60000L);
      sleep(1000L);
      DevCameraTest.Sample nether = this.sample();
      this.check(
         "in the nether the player's own view is back ("
            + nether.type
            + ", "
            + round(nether.distance(nether.eye))
            + " from the eye) and the field of view ("
            + round(this.supply(() -> BedrockCamera.fovFactor(((Integer)this.minecraft.options.fov().get()).intValue())).floatValue())
            + ")",
         BedrockCamera.forcedCameraType() == null
            && nether.distance(nether.eye) < 0.5
            && this.supply(() -> BedrockCamera.fovFactor(((Integer)this.minecraft.options.fov().get()).intValue())) == 1.0F
      );
      this.command("camera @s set minecraft:third_person");
      sleep(600L);
      this.check("the nether takes camera instructions", this.sample().type == CameraType.THIRD_PERSON_BACK);
      this.command("camera @s clear");
      this.commands(String.format(Locale.ROOT, "execute in overworld run tp @s %.2f %.2f %.2f", home.x, home.y, home.z));
      this.waitFor(() -> this.minecraft.level.dimension() == Level.OVERWORLD && this.minecraft.getConnection().hasClientLoaded(), 60000L);
      sleep(3000L);
   }

   private void done() {
      BedrockNative.LOGGER.info("Dev camera test summary: {} checks, {} failed", checks, failed);
      BedrockNative.LOGGER.info("Dev camera test: done, {} checks, {} failed", checks, failed);
   }

   private DevCameraTest.Sample sample() {
      return this.supply(
         () -> {
            Camera camera = this.minecraft.gameRenderer.mainCamera();
            return new DevCameraTest.Sample(
               camera.position(),
               Mth.wrapDegrees(camera.yRot()),
               camera.xRot(),
               this.minecraft.player.getEyePosition(),
               this.minecraft.player.getYRot(),
               this.minecraft.player.getXRot(),
               this.minecraft.options.getCameraType(),
               camera.isDetached(),
               camera.getFov(),
               BedrockCamera.fadeColour()
            );
         }
      );
   }

   private float[] playerLook() {
      return this.supply(() -> new float[]{this.minecraft.player.getYRot(), this.minecraft.player.getXRot()});
   }

   private static Vector3f of(Vec3 vec) {
      return Vector3f.from(vec.x, vec.y, vec.z);
   }

   private static String vec(Vec3 vec) {
      return String.format(Locale.ROOT, "(%.2f, %.2f, %.2f)", vec.x, vec.y, vec.z);
   }

   private void check(String what, boolean ok) {
      checks++;
      if (!ok) {
         failed++;
      }

      BedrockNative.LOGGER.info("Dev camera test {}: {}", ok ? "ok" : "FAILED", what);
   }

   private static double round(double value) {
      return Math.round(value * 1000.0) / 1000.0;
   }

   private static String percent(double share) {
      return Math.round(share * 1000.0) / 10.0 + "%";
   }

   private double redShare() {
      CompletableFuture<Double> share = new CompletableFuture<>();
      this.minecraft.execute(() -> Screenshot.takeScreenshot(this.minecraft.gameRenderer.mainRenderTarget(), image -> {
         try {
            NativeImage twrVar0$ = image;

            try {
               int red = 0;
               int samples = 0;

               for (int y = 0; y < image.getHeight(); y += 8) {
                  for (int x = 0; x < image.getWidth(); x += 8) {
                     int argb = image.getPixel(x, y);
                     samples++;
                     if ((argb >> 16 & 0xFF) >= 240 && (argb >> 8 & 0xFF) <= 20 && (argb & 0xFF) <= 20) {
                        red++;
                     }
                  }
               }

               share.complete(samples == 0 ? Double.NaN : (double)red / samples);
            } catch (Throwable var9) {
               if (image != null) {
                  try {
                     twrVar0$.close();
                  } catch (Throwable var8) {
                     var9.addSuppressed(var8);
                  }
               }

               throw var9;
            }

            if (image != null) {
               image.close();
            }
         } catch (RuntimeException var10) {
            share.completeExceptionally(var10);
         }
      }));

      try {
         return share.get(10L, TimeUnit.SECONDS);
      } catch (InterruptedException var3) {
         Thread.currentThread().interrupt();
         return Double.NaN;
      } catch (TimeoutException | ExecutionException var4) {
         BedrockNative.LOGGER.info("Dev camera test: the frame could not be read back: {}", var4.toString());
         return Double.NaN;
      }
   }

   private void waitFor(BooleanSupplier condition, long timeout) {
      long end = System.currentTimeMillis() + timeout;

      while (System.currentTimeMillis() < end && !this.supply(condition::getAsBoolean)) {
         sleep(50L);
      }
   }

   private <T> T supply(Supplier<T> value) {
      return CompletableFuture.supplyAsync(value, this.minecraft::execute).join();
   }

   private void command(String command) {
      this.minecraft.execute(() -> {
         BedrockNative.LOGGER.info("Dev test command: /{}", command);
         this.minecraft.getConnection().sendCommand(command);
      });
   }

   private void commands(String... commands) {
      for (String command : commands) {
         this.command(command);
         sleep(400L);
      }

      sleep(300L);
   }

   private void screenshot(String name) {
      this.minecraft.execute(() -> this.minecraft.gui.hud.getChat().clearMessages(false));
      sleep(250L);
      this.minecraft
         .execute(() -> Screenshot.grab(this.minecraft.gameDirectory, name + ".png", this.minecraft.gameRenderer.mainRenderTarget(), 1, message -> {}));
      sleep(500L);
   }

   private static void sleep(long millis) {
      if (millis > 0L) {
         try {
            Thread.sleep(millis);
         } catch (InterruptedException var3) {
            Thread.currentThread().interrupt();
         }
      }
   }

   private record Sample(
      Vec3 camera, float yaw, float pitch, Vec3 eye, float playerYaw, float playerPitch, CameraType type, boolean detached, float fov, int fade
   ) {
      double distance(Vec3 to) {
         return this.camera.distanceTo(to);
      }

      double ahead() {
         Vec3 look = Vec3.directionFromRotation(this.playerPitch, this.playerYaw);
         return this.camera.subtract(this.eye).dot(look);
      }
   }
}
