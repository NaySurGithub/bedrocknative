package net.bedrocknative.client.net;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.model.ActorSnapshot;
import net.bedrocknative.client.model.ActorState;
import net.bedrocknative.client.model.AttachableProbe;
import net.bedrocknative.client.model.BedrockActors;
import net.bedrocknative.client.model.BedrockMobs;
import net.bedrocknative.client.model.BedrockModels;
import net.bedrocknative.client.model.BedrockPlayers;
import net.bedrocknative.client.model.WornAttachables;
import net.bedrocknative.model.entity.ModelRegistry;
import net.bedrocknative.model.runtime.ActorInstance;
import net.bedrocknative.model.runtime.DrawList;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

final class DevAttachablesTest {
   private static final String[] ARMOR = new String[]{"helmet", "chestplate", "leggings", "boots"};
   private static final String WAND = "bedrocknative:test_wand";
   private static final float TOLERANCE = 0.002F;
   private final Minecraft minecraft;
   private final List<String> failures = new ArrayList<>();
   private int checks;
   private int passed;

   DevAttachablesTest(Minecraft minecraft) {
      this.minecraft = minecraft;
   }

   void run() {
      if (!this.waitFor(30000L, () -> BedrockModels.registry() != null)) {
         this.fail("the model registry never loaded");
         this.finish();
      } else {
         ModelRegistry registry = this.onClient(BedrockModels::registry);

         for (String piece : ARMOR) {
            this.check("the pack stack has the attachable bedrocknative:test_" + piece, registry.attachableByIdentifier("bedrocknative:test_" + piece) != null);
         }

         this.check("the pack stack has the attachable bedrocknative:test_wand", registry.attachableByIdentifier("bedrocknative:test_wand") != null);
         boolean baseGame = registry.hasBaseGame() && registry.mesh("geometry.humanoid.armor.helmet") != null;
         this.check("the base game defines geometry.humanoid.armor.helmet (the Bedrock install's vanilla pack)", baseGame);
         boolean playersWere = this.onClient(BedrockPlayers::enabled);
         boolean mobsWere = this.onClient(BedrockMobs::enabled);
         this.run(() -> {
            BedrockPlayers.setEnabled(false);
            BedrockMobs.setEnabled(false);
         });
         this.commands(
            "gamemode creative",
            "clear @s",
            "kill @e[type=armor_stand]",
            "kill @e[type=zombie]",
            "time set midnight",
            "fill ~-4 ~ ~-4 ~4 ~3 ~6 air",
            "fill ~-4 ~-1 ~-4 ~4 ~-1 ~6 stone"
         );
         int armorBefore = this.onClient(() -> this.minecraft.player.getArmorValue());
         this.commands(
            "replaceitem entity @s slot.armor.head 0 bedrocknative:test_helmet",
            "replaceitem entity @s slot.armor.chest 0 bedrocknative:test_chestplate",
            "replaceitem entity @s slot.armor.legs 0 bedrocknative:test_leggings",
            "replaceitem entity @s slot.armor.feet 0 bedrocknative:test_boots",
            "replaceitem entity @s slot.weapon.mainhand 0 bedrocknative:test_wand"
         );
         int armorAfter = this.onClient(() -> this.minecraft.player.getArmorValue());
         log("armour value {} before, {} with the test armour", armorBefore, armorAfter);
         this.check("the armour bar counts the test armour's protection (2 + 5 + 4 + 1 = 12)", armorAfter == 12);
         this.run(() -> {
            this.minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            this.pose(0.0F, 30.0F);
            AttachableProbe.reset();
         });
         sleep(1500L);
         this.run(() -> this.pose(0.0F, 30.0F));
         sleep(500L);

         for (String slot : new String[]{"head", "chest", "legs", "feet"}) {
            String definition = this.onClient(
               () -> WornAttachables.definition(this.minecraft.player, WornAttachables.Slot.valueOf(slot.toUpperCase(Locale.ROOT)), false)
            );
            log("{}: attachable {}", slot, definition);
         }

         this.probe("helmet top-front (0, 33, -5) on the head pitched 30 degrees", "head", "head", 0.0F, 33.0F, -5.0F, 0.0F, 1.7174F, 0.5174F);
         this.probe("chestplate front centre (0, 18, -3) on the body", "chest", "body", 0.0F, 18.0F, -3.0F, 0.0F, 1.0556F, 0.1758F);
         this.probe("boot toe (-1.9, -1, -3) on the right leg", "feet", "rightleg", -1.9F, -1.0F, -3.0F, -0.116F, -0.0577F, 0.1758F);
         this.drawnBones("chestplate", "chest", "body", "rightarm", "leftarm");
         this.drawnBones("leggings", "legs", "rightleg", "leftleg");
         this.drawnBones("boots", "feet", "rightleg", "leftleg");
         this.heldThirdPerson();
         this.screenshot("attachables-back");
         this.run(() -> this.minecraft.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
         sleep(800L);
         this.screenshot("attachables-front");
         this.run(() -> {
            this.minecraft.options.setCameraType(CameraType.FIRST_PERSON);
            this.pose(0.0F, 10.0F);
         });
         sleep(1000L);
         this.heldFirstPerson();
         this.screenshot("attachables-first-person");
         this.otherWearers();
         this.run(() -> {
            BedrockPlayers.setEnabled(playersWere);
            BedrockMobs.setEnabled(mobsWere);
         });
         if (playersWere || mobsWere) {
            this.bedrockLook(playersWere, mobsWere);
         }

         this.run(() -> this.minecraft.options.setCameraType(CameraType.FIRST_PERSON));
         this.commands("clear @s", "kill @e[type=armor_stand]", "kill @e[type=zombie]");
         this.finish();
      }
   }

   private void pose(float yaw, float pitch) {
      this.minecraft.player.setYRot(yaw);
      this.minecraft.player.yRotO = yaw;
      this.minecraft.player.setXRot(pitch);
      this.minecraft.player.xRotO = pitch;
      this.minecraft.player.setYHeadRot(yaw);
      this.minecraft.player.yHeadRotO = yaw;
      this.minecraft.player.setYBodyRot(yaw);
      this.minecraft.player.yBodyRotO = yaw;
   }

   private void probe(String what, String slot, String bone, float x, float y, float z, float ex, float ey, float ez) {
      Vector3f offset = this.onClient(() -> AttachableProbe.offset(slot, bone, x, y, z, false));
      if (offset == null) {
         this.fail(what + ": bone " + bone + " of the " + slot + " attachable was not drawn");
      } else {
         boolean close = Math.abs(offset.x - ex) <= 0.002F && Math.abs(offset.y - ey) <= 0.002F && Math.abs(offset.z - ez) <= 0.002F;
         this.check(
            String.format(Locale.ROOT, "%s at (%.4f, %.4f, %.4f) from the feet, expected (%.4f, %.4f, %.4f)", what, offset.x, offset.y, offset.z, ex, ey, ez),
            close
         );
      }
   }

   private void drawnBones(String piece, String slot, String... bones) {
      AttachableProbe.Sample sample = this.onClient(() -> AttachableProbe.sample(slot));
      if (sample == null) {
         this.fail(piece + ": not drawn");
      } else {
         List<String> missing = new ArrayList<>();

         for (String bone : bones) {
            if (!sample.bones().containsKey(bone)) {
               missing.add(bone);
            }
         }

         log("{} drew bones {}", piece, sample.bones().keySet());
         this.check(piece + " drawn on " + String.join(", ", bones), missing.isEmpty());
      }
   }

   private void heldThirdPerson() {
      String definition = this.onClient(() -> WornAttachables.definition(this.minecraft.player, WornAttachables.Slot.MAIN_HAND, false));
      this.check("the wand in the main hand runs its attachable in third person", "bedrocknative:test_wand".equals(definition));
      AttachableProbe.Sample sample = this.onClient(() -> AttachableProbe.sample("main_hand"));
      if (sample == null) {
         this.fail("the wand was not drawn in the hand (third person)");
      } else {
         log("wand bones (third person): {}", sample.bones().keySet());
         Vector3f grip = this.onClient(() -> AttachableProbe.offset("main_hand", "sprite", 0.0F, 24.0F, 0.0F, false));
         Vector3f hand = this.onClient(() -> AttachableProbe.javaPoint("main_hand", "rightItem", -6.0F, 15.0F, 1.0F));
         log("wand grip at {}, the right arm's rightItem pivot at {}", grip, hand);
         this.check("the wand's grip is on the right hand's item pivot (within 0.005 blocks)", grip != null && hand != null && grip.distance(hand) < 0.005F);
         Vector3f gem = this.onClient(() -> AttachableProbe.offset("main_hand", "gem", 10.5F, 34.5F, 0.0F, false));
         Vec3 look = this.onClient(() -> Vec3.directionFromRotation(0.0F, this.minecraft.player.getYRot()));
         this.check("the wand points forward in third person", grip != null && gem != null && (gem.x - grip.x) * look.x + (gem.z - grip.z) * look.z > 0.4);
      }
   }

   private void heldFirstPerson() {
      String definition = this.onClient(() -> WornAttachables.definition(this.minecraft.player, WornAttachables.Slot.MAIN_HAND, true));
      this.check("the wand runs its first-person attachable (c.is_first_person = 1)", "bedrocknative:test_wand".equals(definition));
      Vector3f grip = this.onClient(() -> AttachableProbe.offset("first_person_main_hand", "sprite", 0.0F, 24.0F, 0.0F, true));
      if (grip == null) {
         this.fail("the wand was not drawn in first person");
      } else {
         Vec3 look = this.onClient(() -> this.minecraft.player.getViewVector(1.0F));
         float ahead = (float)(grip.x * look.x + grip.y * look.y + grip.z * look.z);
         log("first person wand grip {} from the camera, {} blocks ahead", grip, ahead);
         this.check(
            "the first-person wand's grip is at Bedrock's first-person hand (0.95 ahead, 1.32 away, +-0.08)",
            Math.abs(ahead - 0.951F) < 0.08F && Math.abs(grip.length() - 1.322F) < 0.08F
         );
      }
   }

   private void otherWearers() {
      this.commands(
         "summon armor_stand ~ ~ ~3",
         "replaceitem entity @e[type=armor_stand,c=1] slot.armor.head 0 bedrocknative:test_helmet",
         "replaceitem entity @e[type=armor_stand,c=1] slot.weapon.mainhand 0 bedrocknative:test_wand",
         "summon zombie ~2 ~ ~3",
         "replaceitem entity @e[type=zombie,c=1] slot.weapon.mainhand 0 bedrocknative:test_wand"
      );
      this.run(() -> {
         this.minecraft.options.setCameraType(CameraType.FIRST_PERSON);
         this.pose(0.0F, 15.0F);
      });
      sleep(500L);
      long since = System.nanoTime();
      sleep(1000L);
      LivingEntity stand = this.onClient(() -> this.nearest(EntityTypes.ARMOR_STAND));
      if (stand == null) {
         this.fail("no armour stand after /summon");
      } else {
         this.check(
            "the armour stand's helmet runs its attachable",
            "bedrocknative:test_helmet".equals(this.onClient(() -> WornAttachables.definition(stand, WornAttachables.Slot.HEAD, false)))
         );
         this.check(
            "the armour stand's wand runs its attachable",
            "bedrocknative:test_wand".equals(this.onClient(() -> WornAttachables.definition(stand, WornAttachables.Slot.MAIN_HAND, false)))
         );
         this.check(
            "the armour stand's helmet was submitted to the renderer in the last second", AttachableProbe.lastSubmit("minecraft:armor_stand", "head") > since
         );
         this.check(
            "the armour stand's wand was submitted to the renderer in the last second",
            AttachableProbe.lastSubmit("minecraft:armor_stand", "main_hand") > since
         );
      }

      LivingEntity zombie = this.onClient(() -> this.nearest(EntityTypes.ZOMBIE));
      if (zombie == null) {
         log("no zombie after /summon (peaceful difficulty?): mob wearers not checked");
      } else {
         this.check(
            "the zombie's wand runs its attachable",
            "bedrocknative:test_wand".equals(this.onClient(() -> WornAttachables.definition(zombie, WornAttachables.Slot.MAIN_HAND, false)))
         );
         ActorState drawn = this.onClient(() -> BedrockMobs.enabled() ? BedrockActors.state(zombie) : null);
         if (drawn != null && this.onClient(() -> drawn.vanillaMob() && drawn.equipment() != null && drawn.lastSnapshot() != null)) {
            this.check(
               "the runtime-drawn zombie carries the wand as its attachable and draws its geometry",
               this.onClient(
                  () -> {
                     ActorInstance instance = drawn.instance();
                     DrawList list = instance != null ? instance.lastDrawList() : null;
                     return "bedrocknative:test_wand".equals(drawn.equipment().definition(0))
                        && drawn.equipment().attached(0)
                        && list != null
                        && list.calls().stream().anyMatch(call -> "geometry.bedrocknative.test_wand".equals(call.geometry()));
                  }
               )
            );
         } else {
            this.check(
               "the zombie's wand was submitted to the renderer in the last second", AttachableProbe.lastSubmit("minecraft:zombie", "main_hand") > since
            );
         }
      }

      this.screenshot("attachables-wearers");
   }

   private void bedrockLook(boolean players, boolean mobs) {
      this.run(() -> {
         this.minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
         this.pose(0.0F, 30.0F);
      });
      sleep(1500L);
      this.run(() -> this.pose(0.0F, 30.0F));
      sleep(500L);
      if (players) {
         this.waitFor(10000L, () -> this.onClient(() -> BedrockPlayers.lastFrame(this.minecraft.player) != null));
         List<String> geometries = this.onClient(() -> {
            ActorSnapshot frame = BedrockPlayers.lastFrame(this.minecraft.player);
            List<String> names = new ArrayList<>();

            for (int call = 0; frame != null && call < frame.draw().calls(); call++) {
               names.add(frame.draw().call(call).mesh().identifier());
            }

            return names;
         });
         log("the player's runtime frame draws {}", geometries);

         for (String piece : ARMOR) {
            this.check("the model runtime draws the player's " + piece + " attachable", geometries.contains("geometry.humanoid.armor." + piece));
         }

         this.check("the model runtime draws the wand in the player's hand", geometries.contains("geometry.bedrocknative.test_wand"));
         this.screenshot("attachables-bedrock-back");
         this.run(() -> this.minecraft.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
         sleep(800L);
         this.screenshot("attachables-bedrock-front");
      }

      if (mobs) {
         this.run(() -> {
            this.minecraft.options.setCameraType(CameraType.FIRST_PERSON);
            this.pose(0.0F, 15.0F);
         });
         sleep(1500L);
         LivingEntity stand = this.onClient(() -> this.nearest(EntityTypes.ARMOR_STAND));
         ActorState state = stand == null ? null : this.onClient(() -> BedrockActors.state(stand));
         if (state == null || !this.onClient(state::vanillaMob)) {
            log("no runtime-drawn armour stand: not checked");
            return;
         }

         List<String> geometries = this.onClient(() -> {
            ActorInstance instance = state.instance();
            DrawList list = instance != null ? instance.lastDrawList() : null;
            List<String> names = new ArrayList<>();

            for (int call = 0; list != null && call < list.calls().size(); call++) {
               names.add(list.calls().get(call).geometry());
            }

            return names;
         });
         log("the armour stand's runtime frame draws {}", geometries);
         this.check("the model runtime draws the armour stand's helmet attachable", geometries.contains("geometry.humanoid.armor.helmet"));
         this.check("the model runtime draws the wand in the armour stand's hand", geometries.contains("geometry.bedrocknative.test_wand"));
      }
   }

   @Nullable
   private LivingEntity nearest(EntityType<?> type) {
      LivingEntity nearest = null;

      for (Entity entity : this.minecraft.level.entitiesForRendering()) {
         if (entity.getType() == type
            && entity instanceof LivingEntity living
            && (nearest == null || entity.distanceToSqr(this.minecraft.player) < nearest.distanceToSqr(this.minecraft.player))) {
            nearest = living;
         }
      }

      return nearest;
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
      BedrockNative.LOGGER.warn("Dev attachables test FAILED: {}", what);
   }

   private void finish() {
      log("{} of {} checks passed{}", this.passed, this.checks, this.failures.isEmpty() ? "" : "; failed: " + this.failures);
      log("done");
   }

   private static void log(String message, Object... arguments) {
      BedrockNative.LOGGER.info("Dev attachables test: " + message, arguments);
   }

   private void commands(String... commands) {
      for (String command : commands) {
         this.run(() -> this.minecraft.getConnection().sendCommand(command));
         sleep(400L);
      }

      sleep(1500L);
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
      } catch (InterruptedException var3) {
         Thread.currentThread().interrupt();
      }
   }
}
