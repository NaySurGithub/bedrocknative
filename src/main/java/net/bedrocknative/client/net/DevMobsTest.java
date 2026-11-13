package net.bedrocknative.client.net;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.model.ActorSnapshot;
import net.bedrocknative.client.model.ActorState;
import net.bedrocknative.client.model.BedrockActors;
import net.bedrocknative.client.model.BedrockMobs;
import net.bedrocknative.client.model.BedrockModels;
import net.bedrocknative.client.model.ComposedTextures;
import net.bedrocknative.client.model.OverlayActors;
import net.bedrocknative.model.entity.ModelRegistry;
import net.bedrocknative.model.entity.VanillaMobs;
import net.bedrocknative.model.geometry.Skeleton;
import net.bedrocknative.model.render.RenderKind;
import net.bedrocknative.model.runtime.ActorInstance;
import net.bedrocknative.model.runtime.DrawList;
import net.bedrocknative.model.runtime.DrawSnapshot;
import net.bedrocknative.model.runtime.RuntimeEquipment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

final class DevMobsTest {
   private static final List<DevMobsTest.Spawn> ROW = List.of(
      new DevMobsTest.Spawn("zombie", -9, 5, ""),
      new DevMobsTest.Spawn("zombie", -7, 5, "minecraft:as_baby"),
      new DevMobsTest.Spawn("skeleton", -5, 5, ""),
      new DevMobsTest.Spawn("creeper", -3, 5, ""),
      new DevMobsTest.Spawn("spider", -1, 5, ""),
      new DevMobsTest.Spawn("cow", 1, 5, ""),
      new DevMobsTest.Spawn("pig", 3, 5, ""),
      new DevMobsTest.Spawn("sheep", 5, 5, ""),
      new DevMobsTest.Spawn("sheep", 7, 5, "minecraft:on_sheared"),
      new DevMobsTest.Spawn("chicken", 9, 5, ""),
      new DevMobsTest.Spawn("wolf", -9, 9, "minecraft:spawn_tame_adult"),
      new DevMobsTest.Spawn("wolf", -7, 9, "minecraft:become_angry"),
      new DevMobsTest.Spawn("cat", -5, 9, ""),
      new DevMobsTest.Spawn("cat", -3, 9, "minecraft:spawn_tame_adult"),
      new DevMobsTest.Spawn("villager_v2", -1, 9, "minecraft:spawn_farmer"),
      new DevMobsTest.Spawn("villager_v2", 1, 9, "minecraft:spawn_librarian"),
      new DevMobsTest.Spawn("villager_v2", 3, 9, "minecraft:spawn_cleric"),
      new DevMobsTest.Spawn("iron_golem", 6, 9, ""),
      new DevMobsTest.Spawn("horse", 9, 9, "minecraft:spawn_tame_adult")
   );
   private static final List<String> PROFESSIONS = Arrays.asList(
      "unskilled",
      "farmer",
      "fisherman",
      "shepherd",
      "fletcher",
      "librarian",
      "cartographer",
      "cleric",
      "armorer",
      null,
      null,
      "butcher",
      "leatherworker",
      "stonemason",
      "nitwit"
   );
   private static final double MATCH_DISTANCE = 3.0;
   private static final Set<String> LATE = Set.of("piglin", "piglin_brute");
   private static final List<DevMobsTest.Spawn> ROW2 = List.of(
      new DevMobsTest.Spawn("cod", -11, 5, ""),
      new DevMobsTest.Spawn("salmon", -9, 5, ""),
      new DevMobsTest.Spawn("pufferfish", -7, 5, ""),
      new DevMobsTest.Spawn("tropicalfish", -5, 5, ""),
      new DevMobsTest.Spawn("squid", -3, 5, ""),
      new DevMobsTest.Spawn("glow_squid", 0, 5, ""),
      new DevMobsTest.Spawn("goat", 4, 5, ""),
      new DevMobsTest.Spawn("llama", 6, 5, ""),
      new DevMobsTest.Spawn("trader_llama", 8, 5, ""),
      new DevMobsTest.Spawn("slime", 10, 5, ""),
      new DevMobsTest.Spawn("magma_cube", -10, 9, ""),
      new DevMobsTest.Spawn("panda", -7, 9, ""),
      new DevMobsTest.Spawn("parrot", -5, 9, ""),
      new DevMobsTest.Spawn("pillager", 1, 9, ""),
      new DevMobsTest.Spawn("polar_bear", 3, 9, ""),
      new DevMobsTest.Spawn("enderman", 5, 9, ""),
      new DevMobsTest.Spawn("guardian", 5, 0, ""),
      new DevMobsTest.Spawn("elder_guardian", 7, 1, ""),
      new DevMobsTest.Spawn("creeper", -10, 13, "minecraft:become_charged"),
      new DevMobsTest.Spawn("armor_stand", -8, 13, ""),
      new DevMobsTest.Spawn("ender_crystal", -6, 13, ""),
      new DevMobsTest.Spawn("xp_orb", -4, 12, ""),
      new DevMobsTest.Spawn("mooshroom", -2, 13, ""),
      new DevMobsTest.Spawn("sulfur_cube", 0, 13, ""),
      new DevMobsTest.Spawn("happy_ghast", 4, 15, ""),
      new DevMobsTest.Spawn("warden", 9, 15, ""),
      new DevMobsTest.Spawn("piglin", -3, 9, ""),
      new DevMobsTest.Spawn("piglin_brute", -1, 9, "")
   );
   private final Minecraft minecraft;
   private final List<String> failures = new ArrayList<>();
   private int checks;
   private int passed;

   DevMobsTest(Minecraft minecraft) {
      this.minecraft = minecraft;
   }

   void run() {
      boolean original = BedrockMobs.enabled();

      try {
         this.test();
      } catch (RuntimeException e) {
         this.fail("exception: " + e);
         BedrockNative.LOGGER.error("Dev mobs test failed with an exception", e);
      } finally {
         this.run(() -> BedrockMobs.setEnabled(original));
         this.finish();
      }
   }

   private void test() {
      if (!this.waitFor(30000L, () -> BedrockModels.registry() != null)) {
         this.fail("the model registry never loaded");
      } else {
         ModelRegistry registry = this.onClient(BedrockModels::registry);
         this.check("the base game's client entities are loaded (the player's Bedrock install)", registry.hasBaseGame());
         BlockPos at = this.onClient(() -> this.minecraft.player.blockPosition());
         int x = at.getX();
         int y = at.getY();
         int z = at.getZ();
         this.commands(
            "gamemode creative",
            "difficulty easy",
            "time set noon",
            "weather clear",
            "kill @e[type=!player,r=40]",
            String.format(Locale.ROOT, "fill %d %d %d %d %d %d air", x - 12, y, z - 8, x + 12, y + 5, z + 12),
            String.format(Locale.ROOT, "fill %d %d %d %d %d %d grass_block", x - 12, y - 1, z - 8, x + 12, y - 1, z + 12)
         );

         for (DevMobsTest.Spawn spawn : ROW) {
            this.send(
               150L,
               String.format(
                  Locale.ROOT,
                  "summon %s %d %d %d 180 0%s",
                  spawn.summonId(),
                  x + spawn.x(),
                  y,
                  z + spawn.z(),
                  spawn.event().isEmpty() ? (spawn.type().equals("zombie") ? " minecraft:as_adult" : "") : " " + spawn.event()
               )
            );
            this.send(
               100L,
               String.format(Locale.ROOT, "effect @e[type=%s,x=%d,y=%d,z=%d,r=2] slowness 120 255 true", spawn.summonId(), x + spawn.x(), y, z + spawn.z()),
               String.format(Locale.ROOT, "effect @e[type=%s,x=%d,y=%d,z=%d,r=2] fire_resistance 120 0 true", spawn.summonId(), x + spawn.x(), y, z + spawn.z())
            );
         }

         this.commands(
            "replaceitem entity @e[type=zombie,r=40] slot.weapon.mainhand 0 iron_sword",
            "replaceitem entity @e[type=zombie,r=40] slot.armor.head 0 iron_helmet",
            "replaceitem entity @e[type=skeleton,r=40] slot.armor.head 0 leather_helmet",
            "replaceitem entity @e[type=horse,r=40] slot.saddle 0 saddle",
            "effect @e[type=!player,r=40] slowness 120 255 true",
            String.format(Locale.ROOT, "tp @s %d %d %d 0 15", x, y, z - 6)
         );
         sleep(3000L);
         this.run(() -> {
            BedrockMobs.setEnabled(false);
            BedrockMobs.resetSubmitted();
         });
         sleep(1000L);
         this.screenshot("mobs-java");
         Map<String, Integer> whileOff = this.onClient(BedrockMobs::submitted);
         this.check("with the switch off the runtime's submit drew no mob (Java's models stayed)", whileOff.isEmpty());
         this.run(() -> {
            BedrockMobs.setEnabled(true);
            BedrockMobs.resetSubmitted();
         });
         this.commands(String.format(Locale.ROOT, "event entity @e[type=sheep,x=%d,y=%d,z=%d,r=1.2] minecraft:on_sheared", x + 7, y, z + 5));
         sleep(1500L);
         this.screenshot("mobs-bedrock");
         this.report();
         this.looks(x, z);
         this.closeUps(ROW, x, y, z);
         this.commands(String.format(Locale.ROOT, "tp @s %d %d %d 0 15", x, y, z - 6));
         this.commands("effect @e[type=!player,r=40] clear");
         sleep(2500L);
         this.screenshot("mobs-bedrock-walking");
         this.check(
            "no runtime-drawn mob failed to evaluate",
            this.onClient(() -> BedrockActors.current() == null || BedrockActors.current().states().stream().noneMatch(ActorState::failed))
         );
         this.check("no model group failed to draw", !this.onClient(BedrockMobs::drawErrors));
         log("composed textures: {}", this.onClient(ComposedTextures::size));
         this.commands("kill @e[type=!player,r=40]");
         this.roundTwo(x, y, z);
         this.projectiles(x, y, z);
         this.bosses(x, y, z);
         this.cleanUp();
      }
   }

   private void cleanUp() {
      for (int i = 0; i < 3; i++) {
         this.commands("kill @e[type=!player,r=80]");
         sleep(700L);
      }
   }

   private void projectiles(int x, int y, int z) {
      this.run(() -> {
         BedrockMobs.setEnabled(true);
         BedrockMobs.resetSubmitted();
      });
      this.commands(
         String.format(Locale.ROOT, "fill %d %d %d %d %d %d stone", x + 5, y, z - 4, x + 5, y + 3, z),
         String.format(Locale.ROOT, "setblock %d %d %d dispenser [\"facing_direction\"=5]", x - 6, y + 1, z - 2),
         String.format(Locale.ROOT, "replaceitem block %d %d %d slot.container 0 arrow 4", x - 6, y + 1, z - 2),
         String.format(Locale.ROOT, "tp @s %d %d %d 0 25", x - 1, y + 1, z - 8),
         String.format(Locale.ROOT, "setblock %d %d %d redstone_block", x - 7, y + 1, z - 2)
      );
      sleep(1500L);
      this.screenshot("mobs2-arrow");
      Boolean east = this.onClient(() -> {
         Boolean all = null;

         for (Entity entity : this.minecraft.level.entitiesForRendering()) {
            ActorState state = BedrockActors.state(entity);
            ActorInstance instance = state != null && state.data().identifier().equals("minecraft:arrow") ? state.instance() : null;
            Skeleton skeleton = instance != null ? instance.drawnSkeleton() : null;
            ActorSnapshot snapshot = state != null ? state.lastSnapshot() : null;
            int body = skeleton != null ? skeleton.geometry().bone("body") : -1;
            if (body >= 0 && snapshot != null && !(entity.distanceTo(this.minecraft.player) > 20.0F)) {
               Vector3f tail = Skeleton.toWorld(skeleton.transform(body, new Vector3f(0.0F, 0.0F, 13.0F)), snapshot.bodyYaw(), 1.0F);
               Vector3f tip = Skeleton.toWorld(skeleton.transform(body, new Vector3f(0.0F, 0.0F, -3.0F)), snapshot.bodyYaw(), 1.0F).sub(tail).normalize();
               log("arrow at {}: yaw {} pitch {} as received, the runtime's tip points {}", entity.position(), entity.getYRot(), entity.getXRot(), tip);
               all = (all == null || all) && tip.x > 0.8F;
            }
         }

         return all;
      });
      if (east == null) {
         log("SKIP the arrow fired east: no arrow drawn by the runtime in range (did the dispenser fire?)");
      } else {
         this.check("the arrow fired east points east through the runtime (target rotations as the server sends them)", east);
      }

      this.run(() -> this.minecraft.getConnection().sendCommand(String.format(Locale.ROOT, "summon fireworks_rocket %d %d %d", x, y + 1, z + 10)));
      this.run(() -> this.minecraft.getConnection().sendCommand("tp @s ~ ~ ~ 0 -30"));
      sleep(500L);
      String rocket = this.types().get("minecraft:fireworks_rocket");
      if (rocket == null) {
         log("SKIP minecraft:fireworks_rocket: none in range");
      } else {
         this.check("minecraft:fireworks_rocket drawn through the runtime (PASS/FAIL per type)", "drawn by the runtime".equals(rocket));
      }

      this.commands("kill @e[type=arrow,r=40]", String.format(Locale.ROOT, "fill %d %d %d %d %d %d air", x - 7, y, z - 4, x + 5, y + 3, z));
   }

   private void roundTwo(int x, int y, int z) {
      this.commands(
         "time set midnight",
         "effect @s night_vision 300 0 true",
         String.format(Locale.ROOT, "fill %d %d %d %d %d %d air", x - 12, y, z - 8, x + 12, y + 8, z + 18),
         String.format(Locale.ROOT, "fill %d %d %d %d %d %d grass_block", x - 12, y - 1, z - 8, x + 12, y - 1, z + 18),
         String.format(Locale.ROOT, "fill %d %d %d %d %d %d stone", x - 13, y - 3, z + 3, x + 3, y - 1, z + 7),
         String.format(Locale.ROOT, "fill %d %d %d %d %d %d water", x - 12, y - 2, z + 4, x + 2, y - 1, z + 6),
         String.format(Locale.ROOT, "fill %d %d %d %d %d %d stone", x + 3, y - 3, z - 3, x + 9, y - 1, z + 3),
         String.format(Locale.ROOT, "fill %d %d %d %d %d %d water", x + 4, y - 2, z - 2, x + 8, y - 1, z + 2)
      );
      this.commands(250L, summons(ROW2.stream().filter(spawn -> !LATE.contains(spawn.type())).toList(), x, y, z));
      this.commands("effect @e[type=!player,r=40] slowness 120 255 true", String.format(Locale.ROOT, "tp @s %d %d %d 0 20", x, y + 1, z - 6));
      sleep(2500L);
      this.run(() -> {
         BedrockMobs.setEnabled(false);
         BedrockMobs.resetSubmitted();
         OverlayActors.resetSubmitted();
      });
      sleep(1000L);
      this.screenshot("mobs2-java");
      this.check(
         "with the switch off the test pack's mooshroom shows its marker over Java's model (overlay mode)",
         this.onClient(
            () -> OverlayActors.submitted().getOrDefault("minecraft:mooshroom", 0) > 0
               && this.overlayDraws("minecraft:mooshroom", "controller.render.bedrocknative.overlay_marker")
         )
      );
      this.check("with the switch off the runtime's submit drew no vanilla actor", this.onClient(BedrockMobs::submitted).isEmpty());
      this.run(() -> {
         BedrockMobs.setEnabled(true);
         BedrockMobs.resetSubmitted();
      });
      this.commands(250L, summons(ROW2.stream().filter(spawn -> LATE.contains(spawn.type())).toList(), x, y, z));
      Set<String> submitted = new HashSet<>();
      Map<String, String> types = Map.of();

      for (int round = 0; round < 8; round++) {
         this.commands(
            ROW2.stream()
               .filter(spawn -> List.of("enderman", "parrot", "happy_ghast").contains(spawn.type()))
               .map(spawn -> String.format(Locale.ROOT, "tp @e[type=%s,r=40] %d %d %d", spawn.type(), x + spawn.x(), y, z + spawn.z()))
               .toArray(String[]::new)
         );
         sleep(500L);
         submitted.addAll(this.onClient(BedrockMobs::submitted).keySet());
         if (round == 2) {
            this.screenshot("mobs2-bedrock");
            types = this.types();
         }
      }

      types.forEach((identifier, look) -> log("round 2: {}: {}", identifier, look));

      for (String type : ROW2.stream().map(spawn -> "minecraft:" + spawn.type()).distinct().toList()) {
         if (!types.containsKey(type)) {
            log("SKIP {}: none in range (the server couldn't summon it here?)", type);
         } else {
            this.check(type + " drawn through the runtime (PASS/FAIL per type)", "drawn by the runtime".equals(types.get(type)));
            this.check(type + " submitted by the runtime (Java's model skipped)", submitted.contains(type));
         }
      }

      this.polish();
      this.check(
         "no runtime-drawn actor failed to evaluate (round 2)",
         this.onClient(() -> BedrockActors.current() == null || BedrockActors.current().states().stream().noneMatch(ActorState::failed))
      );
      this.check("no model group failed to draw (round 2)", !this.onClient(BedrockMobs::drawErrors));
      this.closeUps(ROW2, x, y, z);
      this.commands(String.format(Locale.ROOT, "tp @s %d %d %d 0 20", x, y + 1, z - 6));
      this.commands("effect @e[type=!player,r=40] clear");
      sleep(2000L);
      this.screenshot("mobs2-bedrock-moving");
      this.commands("kill @e[type=!player,r=40]", "time set noon");
   }

   private void polish() {
      this.lookOf("minecraft:creeper", "the charged creeper's armour draws as an energy swirl with its uv_anim", snapshot -> {
         for (int c = 0; c < snapshot.draw().calls(); c++) {
            DrawSnapshot.Call call = snapshot.draw().call(c);

            for (int g = 0; g < call.groups(); g++) {
               if (call.kind(g) == RenderKind.ADDITIVE && call.uvAnimated(g)) {
                  return true;
               }
            }
         }

         return false;
      });
      this.lookOf("minecraft:glow_squid", "the glow squid glows (a full-bright emissive pass)", snapshot -> {
         for (int c = 0; c < snapshot.draw().calls(); c++) {
            for (int g = 0; g < snapshot.draw().call(c).groups(); g++) {
               if (snapshot.glows(c, g)) {
                  return true;
               }
            }
         }

         return false;
      });
      this.lookOf("minecraft:warden", "the warden's glowing layers fade by their colour's alpha", snapshot -> {
         for (int c = 0; c < snapshot.draw().calls(); c++) {
            float alpha = snapshot.draw().call(c).colorAlpha();
            if (alpha > 0.0F && alpha < 1.0F) {
               return true;
            }
         }

         return false;
      });
      this.lookOf(
         "minecraft:tropicalfish",
         "the tropical fish's pattern takes its second colour (COLOR_2)",
         snapshot -> snapshot.draw().calls() > 0 && snapshot.draw().call(0).tint2() != -1
      );
   }

   private void lookOf(String type, String what, Predicate<ActorSnapshot> test) {
      Boolean result = this.onClient(
         () -> {
            for (ActorState state : BedrockActors.current() != null ? BedrockActors.current().states() : List.<ActorState>of()) {
               ActorSnapshot snapshot = state.data().identifier().equals(type) ? state.lastSnapshot() : null;
               if (snapshot != null && state.instance() != null && test.test(snapshot)) {
                  return true;
               }
            }

            return BedrockActors.current() != null && BedrockActors.current().states().stream().anyMatch(statex -> statex.data().identifier().equals(type))
               ? Boolean.FALSE
               : null;
         }
      );
      if (result == null) {
         log("SKIP {}: no {} in range", what, type);
      } else {
         this.check(what, result);
      }
   }

   private boolean overlayDraws(String type, String controller) {
      for (ActorState state : BedrockActors.current() != null ? BedrockActors.current().states() : List.<ActorState>of()) {
         ActorInstance overlay = state.data().identifier().equals(type) ? state.overlay() : null;
         if (overlay != null && overlay.lastDrawList().calls().stream().anyMatch(call -> call.controller().equals(controller))) {
            return true;
         }
      }

      return false;
   }

   private static String[] summons(List<DevMobsTest.Spawn> spawns, int x, int y, int z) {
      return spawns.stream()
         .flatMap(
            spawn -> {
               int spawnY = y
                  + (
                     !spawn.type().contains("fish")
                           && !spawn.type().contains("squid")
                           && !spawn.type().equals("cod")
                           && !spawn.type().equals("salmon")
                           && !spawn.type().endsWith("guardian")
                        ? 0
                        : -2
                  );
               return Stream.of(
                  String.format(
                     Locale.ROOT,
                     "summon %s %d %d %d 180 0%s",
                     spawn.type(),
                     x + spawn.x(),
                     spawnY,
                     z + spawn.z(),
                     spawn.event().isEmpty() ? "" : " " + spawn.event()
                  ),
                  String.format(Locale.ROOT, "effect @e[type=%s,x=%d,y=%d,z=%d,r=3] slowness 120 255 true", spawn.type(), x + spawn.x(), spawnY, z + spawn.z())
               );
            }
         )
         .toArray(String[]::new);
   }

   private static String mergeLooks(String a, String b) {
      String drawn = "drawn by the runtime";
      return a.equals(b) ? a : (!a.equals(drawn) && !b.equals(drawn) ? a + "; " + b : drawn);
   }

   private Map<String, String> types() {
      return this.types(48.0);
   }

   private Map<String, String> types(double range) {
      return this.onClient(() -> {
         Map<String, String> seen = new TreeMap<>();
         ModelRegistry registry = BedrockModels.registry();

         for (Entity entity : this.minecraft.level.entitiesForRendering()) {
            ActorState state = BedrockActors.state(entity);
            if (registry != null && state != null && state.vanillaMob() && !(entity.distanceTo(this.minecraft.player) > range)) {
               String identifier = state.data().identifier();
               VanillaMobs.Support support = BedrockMobs.support(registry, identifier);
               String look;
               if (!(entity instanceof Mob) && !VanillaMobs.ACTORS.contains(identifier)) {
                  look = "drawn by Java (not an actor the look takes over)";
               } else if (support.drawn()) {
                  look = state.instance() != null && state.evaluations() > 0L ? "drawn by the runtime" : "not evaluated (off screen?)";
               } else {
                  look = support.reason();
               }

               seen.merge(identifier, look, DevMobsTest::mergeLooks);
            }
         }

         return seen;
      });
   }

   private void bosses(int x, int y, int z) {
      for (String boss : List.of("wither", "ender_dragon")) {
         String type = "minecraft:" + boss;
         this.run(BedrockMobs::resetSubmitted);
         this.commands(
            String.format(Locale.ROOT, "summon %s %d %d %d 180 0", boss, x, y + 4, z + 14), String.format(Locale.ROOT, "tp @s %d %d %d 0 0", x, y + 4, z - 4)
         );
         sleep(700L);
         Map<String, String> types = this.types(200.0);
         this.screenshot("mobs2-" + boss.replace("ender_", ""));
         if (!types.containsKey(type)) {
            log("SKIP {}: none in range", type);
         } else {
            log("round 2: {}: {}", type, types.get(type));
            this.check(type + " drawn through the runtime (PASS/FAIL per type)", "drawn by the runtime".equals(types.get(type)));
            this.check(type + " submitted by the runtime (Java's model skipped)", this.onClient(BedrockMobs::submitted).getOrDefault(type, 0) > 0);
         }

         this.commands("kill @e[type=" + boss + "]");
         sleep(500L);
      }
   }

   private void report() {
      Map<String, String> types = this.onClient(
         () -> {
            Map<String, String> seen = new TreeMap<>();
            ModelRegistry registry = BedrockModels.registry();

            for (Entity entity : this.minecraft.level.entitiesForRendering()) {
               ActorState state = entity instanceof Mob ? BedrockActors.state(entity) : null;
               if (registry != null && state != null && !(entity.distanceTo(this.minecraft.player) > 40.0F)) {
                  String identifier = state.data().identifier();
                  VanillaMobs.Support support = BedrockMobs.support(registry, identifier);
                  String look = support.drawn()
                     ? (state.instance() != null && state.evaluations() > 0L ? "drawn by the runtime" : "not evaluated (off screen?)")
                     : support.reason();
                  seen.merge(identifier, look, DevMobsTest::mergeLooks);
               }
            }

            return seen;
         }
      );
      types.forEach((identifier, look) -> log("{}: {}", identifier, look));
      Map<String, Integer> submitted = this.onClient(BedrockMobs::submitted);

      for (String type : ROW.stream().map(spawn -> "minecraft:" + spawn.type()).distinct().toList()) {
         this.check(type + " drawn through the runtime", "drawn by the runtime".equals(types.get(type)));
         this.check(type + " submitted by the runtime (Java's model skipped)", submitted.getOrDefault(type, 0) > 0);
      }
   }

   private void closeUps(List<DevMobsTest.Spawn> row, int x, int y, int z) {
      this.commands("time set noon", "weather clear");
      Map<DevMobsTest.Spawn, Entity> placed = this.placedEntities(row, x, z);
      boolean hudWasHidden = this.onClient(() -> this.minecraft.gui.hud.isHidden());
      this.run(() -> {
         this.minecraft.gui.hud.getChat().clearMessages(false);
         if (!this.minecraft.gui.hud.isHidden()) {
            this.minecraft.gui.hud.toggle();
         }
      });
      Set<String> names = new HashSet<>();

      for (Entry<DevMobsTest.Spawn, Entity> entry : placed.entrySet()) {
         DevMobsTest.Spawn spawn = entry.getKey();
         Entity entity = entry.getValue();
         String name = "mob-" + spawn.type() + (spawn.event().isEmpty() ? "" : "-" + shortEvent(spawn.event()));
         if (!names.add(name)) {
            name = name + "-" + names.size();
            names.add(name);
         }

         double height = this.onClient(() -> (double)entity.getBbHeight());
         double distance = height > 2.2 ? 3.0 : 2.5;
         double[] at = this.onClient(() -> new double[]{entity.getX(), entity.getY(), entity.getZ()});
         double feet = Math.max(at[1], y);
         float pitch = (float)Math.toDegrees(Math.atan2(feet + 1.62 - (at[1] + height / 2.0), distance));
         this.run(
            () -> this.minecraft.getConnection().sendCommand(String.format(Locale.ROOT, "tp @s %.2f %.2f %.2f 0 %.1f", at[0], feet, at[2] - distance, pitch))
         );
         sleep(900L);
         this.screenshot(name);
         ActorState state = this.onClient(() -> BedrockActors.state(entity));
         log(
            "close-up {} ({} {}): {}",
            name,
            spawn.type(),
            spawn.event().isEmpty() ? "default" : spawn.event(),
            state != null ? this.onClient(() -> describe(state)) : "no actor state"
         );
      }

      List<String> missing = row.stream().filter(spawnx -> !placed.containsKey(spawnx)).map(spawnx -> spawnx.type() + " " + spawnx.event()).toList();
      if (!missing.isEmpty()) {
         log("close-ups skipped (not found where summoned): {}", missing);
      }

      this.run(() -> {
         if (this.minecraft.gui.hud.isHidden() != hudWasHidden) {
            this.minecraft.gui.hud.toggle();
         }
      });
   }

   private static String shortEvent(String event) {
      String name = event.substring(event.indexOf(58) + 1);

      for (String prefix : List.of("spawn_", "become_", "as_", "on_")) {
         if (name.startsWith(prefix)) {
            return name.substring(prefix.length());
         }
      }

      return name;
   }

   private static String describe(ActorState state) {
      ActorSnapshot snapshot = state.lastSnapshot();
      if (snapshot == null) {
         return "not drawn by the runtime (Java's model)";
      }

      DrawSnapshot draw = snapshot.draw();
      StringBuilder text = new StringBuilder();
      text.append(
         String.format(
            Locale.ROOT,
            "variant %d mark %d skin %d, SCALE %.2f x scripts %.3f, %d calls (%d own), drawn %s",
            (int)state.data().number(EntityDataTypes.VARIANT),
            (int)state.data().number(EntityDataTypes.MARK_VARIANT),
            (int)state.data().number(EntityDataTypes.SKIN_ID),
            snapshot.actorScale(),
            draw.scale(1),
            draw.calls(),
            draw.ownCalls(),
            height(draw, snapshot.actorScale())
         )
      );

      for (int c = 0; c < draw.calls(); c++) {
         DrawSnapshot.Call call = draw.call(c);
         text.append(" | ").append(c).append(' ').append(call.controller()).append(" geo ").append(call.mesh().identifier()).append(" tex");

         for (int layer = 0; layer < call.layers(); layer++) {
            String path = call.layer(layer);
            text.append(layer == 0 ? " " : "+").append(path == null ? "-" : path.substring(path.lastIndexOf(47) + 1));
         }

         for (int g = 0; g < call.groups(); g++) {
            text.append(' ')
               .append(call.material(g).name())
               .append('/')
               .append(call.kind(g))
               .append('(')
               .append(call.groupStart(g + 1) - call.groupStart(g))
               .append(')');
         }
      }

      return text.toString();
   }

   private static String height(DrawSnapshot draw, float actorScale) {
      Matrix4f pose = new Matrix4f().rotateY((float) Math.PI).scale(draw.scale(0) * actorScale, draw.scale(1) * actorScale, draw.scale(2) * actorScale);
      Matrix3f normal = pose.normal(new Matrix3f());
      float[] bounds = new float[]{Float.MAX_VALUE, -Float.MAX_VALUE};

      for (int c = 0; c < draw.ownCalls(); c++) {
         for (int g = 0; g < draw.call(c).groups(); g++) {
            draw.emit(c, g, pose, normal, (vx, vy, vz, u, v, nx, ny, nz) -> {
               bounds[0] = Math.min(bounds[0], vy);
               bounds[1] = Math.max(bounds[1], vy);
            });
         }
      }

      return bounds[0] > bounds[1]
         ? "nothing"
         : String.format(Locale.ROOT, "y %.1f..%.1f px (%.2f blocks)", bounds[0], bounds[1], (bounds[1] - bounds[0]) / 16.0F);
   }

   private Map<DevMobsTest.Spawn, Entity> placedEntities(List<DevMobsTest.Spawn> row, int baseX, int baseZ) {
      return this.onClient(() -> {
         Map<DevMobsTest.Spawn, Entity> found = new LinkedHashMap<>();
         Set<Entity> used = new HashSet<>();

         for (DevMobsTest.Spawn spawn : row) {
            String identifier = "minecraft:" + spawn.type();
            double wantedX = baseX + spawn.x() + 0.5;
            double wantedZ = baseZ + spawn.z() + 0.5;
            Entity nearest = null;
            double best = 36.0;

            for (Entity entity : this.minecraft.level.entitiesForRendering()) {
               ActorState state = BedrockActors.state(entity);
               if (state != null && !used.contains(entity) && state.data().identifier().equals(identifier)) {
                  double dx = entity.getX() - wantedX;
                  double dz = entity.getZ() - wantedZ;
                  if (dx * dx + dz * dz < best) {
                     best = dx * dx + dz * dz;
                     nearest = entity;
                  }
               }
            }

            if (nearest != null) {
               used.add(nearest);
               found.put(spawn, nearest);
            }
         }

         return found;
      });
   }

   private Map<DevMobsTest.Spawn, ActorState> placed(int baseX, int baseZ) {
      record Pair(DevMobsTest.Spawn spawn, ActorState state, double distance) {
      }

      return this.onClient(
         () -> {
            List<Pair> pairs = new ArrayList<>();

            for (DevMobsTest.Spawn spawn : ROW) {
               String identifier = "minecraft:" + spawn.type();
               double wantedX = baseX + spawn.x() + 0.5;
               double wantedZ = baseZ + spawn.z() + 0.5;

               for (Entity entity : this.minecraft.level.entitiesForRendering()) {
                  ActorState state = entity instanceof Mob ? BedrockActors.state(entity) : null;
                  if (state != null && state.data().identifier().equals(identifier)) {
                     double distance = Math.hypot(entity.getX() - wantedX, entity.getZ() - wantedZ);
                     if (distance < 3.0) {
                        pairs.add(new Pair(spawn, state, distance));
                     }
                  }
               }
            }

            pairs.sort(Comparator.comparingDouble(Pair::distance));
            Map<DevMobsTest.Spawn, ActorState> matched = new HashMap<>();
            Set<ActorState> used = new HashSet<>();

            for (Pair pair : pairs) {
               if (!matched.containsKey(pair.spawn()) && used.add(pair.state())) {
                  matched.put(pair.spawn(), pair.state());
               }
            }

            Map<DevMobsTest.Spawn, ActorState> found = new LinkedHashMap<>();

            for (DevMobsTest.Spawn spawn : ROW) {
               String identifier = "minecraft:" + spawn.type();
               double wantedX = baseX + spawn.x() + 0.5;
               double wantedZ = baseZ + spawn.z() + 0.5;
               ActorState nearest = matched.get(spawn);
               if (nearest != null) {
                  found.put(spawn, nearest);
               } else {
                  StringBuilder around = new StringBuilder();

                  for (Entity entity : this.minecraft.level.entitiesForRendering()) {
                     ActorState state = BedrockActors.state(entity);
                     if (Math.abs(entity.getX() - wantedX) < 6.0 && Math.abs(entity.getZ() - wantedZ) < 6.0) {
                        around.append(
                           String.format(
                              Locale.ROOT,
                              " [%s %s mob=%b state=%s at %.2f %.2f %.2f]",
                              entity.getType().getDescriptionId(),
                              state == null ? "-" : state.data().identifier(),
                              entity instanceof Mob,
                              state == null ? "none" : "yes",
                              entity.getX(),
                              entity.getY(),
                              entity.getZ()
                           )
                        );
                     }
                  }

                  BedrockNative.LOGGER
                     .info(
                        "Dev mobs test: no {} {} near {} {} (nearest within {}): around:{}",
                        new Object[]{identifier, spawn.event(), wantedX, wantedZ, 3.0, around}
                     );
               }
            }

            return found;
         }
      );
   }

   private void looks(int baseX, int baseZ) {
      Map<DevMobsTest.Spawn, ActorState> placed = this.placed(baseX, baseZ);
      this.check("every summoned mob was found where it was summoned (" + placed.size() + " of " + ROW.size() + ")", placed.size() == ROW.size());
      this.look(placed, "zombie", "", "an adult zombie draws the adult geometry", state -> !this.geometry(state).contains("baby"));
      this.look(placed, "zombie", "minecraft:as_baby", "a baby zombie draws the baby geometry", state -> this.geometry(state).contains("baby"));
      this.look(placed, "sheep", "", "an unsheared sheep draws the woolly geometry", state -> !this.geometry(state).contains("sheared"));
      this.look(placed, "sheep", "minecraft:on_sheared", "a sheared sheep draws the sheared geometry", state -> this.geometry(state).contains("sheared"));
      this.look(placed, "wolf", "minecraft:spawn_tame_adult", "a tamed wolf wears the tame texture", state -> this.texture(state, 0, 0).endsWith("_tame"));
      this.look(placed, "wolf", "minecraft:become_angry", "an angry wolf wears the angry texture", state -> this.texture(state, 0, 0).endsWith("_angry"));

      for (String job : List.of("farmer", "librarian", "cleric")) {
         this.look(placed, "villager_v2", "minecraft:spawn_" + job, "a " + job + " villager wears the profession layer of its variant", state -> {
            int variant = (int)state.data().number(EntityDataTypes.VARIANT);
            String name = variant >= 0 && variant < PROFESSIONS.size() ? PROFESSIONS.get(variant) : null;
            String layer = this.texture(state, 1, 1);
            return name != null ? layer.endsWith("professions/" + name) : layer.startsWith("textures/entity/villager2/professions/");
         });
      }

      this.look(placed, "horse", "minecraft:spawn_tame_adult", "the saddled horse shows its saddle", state -> this.onClient(() -> {
         ActorInstance instance = state.instance();
         DrawList list = instance != null ? instance.lastDrawList() : null;
         if (list != null && !list.calls().isEmpty()) {
            int saddle = list.calls().getFirst().mesh().bone("Saddle");
            return saddle >= 0 && list.calls().getFirst().boneVisible()[saddle];
         } else {
            return false;
         }
      }));

      for (String event : List.of("", "minecraft:as_baby")) {
         String which = event.isEmpty() ? "adult" : "baby";
         this.look(placed, "zombie", event, "the " + which + " zombie's helmet is its iron_helmet attachable", state -> this.onClient(() -> {
            RuntimeEquipment equipment = state.equipment();
            return equipment != null && "minecraft:iron_helmet".equals(equipment.definition(2));
         }));
         this.look(placed, "zombie", event, "the " + which + " zombie's sword goes in Java's item model at its rightItem bone", state -> this.onClient(() -> {
            ActorSnapshot snapshot = state.lastSnapshot();
            return snapshot != null && snapshot.javaHandItem(0) && !state.data().item("slot.weapon.mainhand").isEmpty();
         }));
      }

      List<String> cats = placed.entrySet().stream().filter(entry -> entry.getKey().type().equals("cat")).map(entry -> {
         ActorState state = entry.getValue();
         return "variant " + (int)state.data().number(EntityDataTypes.VARIANT) + " " + this.texture(state, 0, 0);
      }).toList();
      log("cats: {}", cats);
      log(
         "villagers: {}",
         placed.entrySet()
            .stream()
            .filter(entry -> entry.getKey().type().equals("villager_v2"))
            .map(
               entry -> {
                  ActorState state = entry.getValue();
                  StringBuilder layers = new StringBuilder();

                  for (int call = 0; call < 3; call++) {
                     for (int layer = 0; layer < 4; layer++) {
                        String path = this.texture(state, call, layer);
                        if (!path.isEmpty()) {
                           layers.append(" [").append(call).append('/').append(layer).append("] ").append(path);
                        }
                     }
                  }

                  return entry.getKey().event()
                     + " variant "
                     + (int)state.data().number(EntityDataTypes.VARIANT)
                     + " mark "
                     + (int)state.data().number(EntityDataTypes.MARK_VARIANT)
                     + layers;
               }
            )
            .toList()
      );
   }

   private void look(Map<DevMobsTest.Spawn, ActorState> placed, String type, String event, String what, Predicate<ActorState> test) {
      DevMobsTest.Spawn spawn = ROW.stream().filter(candidate -> candidate.type().equals(type) && candidate.event().equals(event)).findFirst().orElseThrow();
      ActorState state = placed.get(spawn);
      boolean ok = state != null && state.instance() != null && test.test(state);
      if (!ok) {
         log(
            "look FAILED detail: {} {} geometry [{}] instance {}",
            type,
            event,
            state == null ? "no mob" : this.geometry(state),
            state == null ? "-" : state.instance() != null
         );
      }

      this.check(what, ok);
   }

   private String geometry(ActorState state) {
      return this.onClient(() -> {
         ActorInstance instance = state.instance();
         DrawList list = instance != null ? instance.lastDrawList() : null;
         return list != null && !list.calls().isEmpty() ? list.calls().getFirst().geometry() : "";
      });
   }

   private String texture(ActorState state, int call, int layer) {
      return this.onClient(() -> {
         ActorSnapshot snapshot = state.lastSnapshot();
         String path = snapshot != null ? snapshot.layerPath(call, layer) : null;
         return path != null ? path : "";
      });
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
      BedrockNative.LOGGER.warn("Dev mobs test FAILED: {}", what);
   }

   private void finish() {
      log("{} of {} checks passed{}", this.passed, this.checks, this.failures.isEmpty() ? "" : "; failed: " + this.failures);
      log("done");
   }

   private static void log(String message, Object... arguments) {
      BedrockNative.LOGGER.info("Dev mobs test: " + message, arguments);
   }

   private void commands(String... commands) {
      this.commands(400L, commands);
   }

   private void commands(long gap, String... commands) {
      this.send(gap, commands);
      sleep(1000L);
   }

   private void send(long gap, String... commands) {
      for (String command : commands) {
         this.run(() -> this.minecraft.getConnection().sendCommand(command));
         sleep(gap);
      }
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

   private record Spawn(String type, int x, int z, String event) {
      String summonId() {
         return this.type.equals("villager_v2") ? "villager" : this.type;
      }
   }
}
