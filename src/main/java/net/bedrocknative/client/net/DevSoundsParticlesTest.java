package net.bedrocknative.client.net;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.particle.BedrockParticleEngine;
import net.bedrocknative.client.sound.BedrockSoundEngine;
import net.bedrocknative.client.sound.JavaSoundBridge;
import net.bedrocknative.client.world.BedrockParticles;
import net.bedrocknative.client.world.BedrockSounds;
import net.bedrocknative.particle.ParticleEffect;
import net.bedrocknative.particle.ParticleEffects;
import net.bedrocknative.sound.SoundCatalog;
import net.bedrocknative.sound.SoundDefinitions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

final class DevSoundsParticlesTest {
   private static final String CHIME = "bedrocknative.test.chime";
   private static final String SPARKLE = "bedrocknative:test_sparkle";
   private static final String GOLEM = "bedrocknative:test_golem";
   private static final String WOOD_BLOCK = "bedrocknative:test_full";
   private final Minecraft minecraft;
   private final BedrockSounds sounds;
   private final BedrockParticles particles;
   private final List<String> failures = new ArrayList<>();
   private int checks;
   private int passed;

   DevSoundsParticlesTest(Minecraft minecraft, BedrockSounds sounds, BedrockParticles particles) {
      this.minecraft = minecraft;
      this.sounds = sounds;
      this.particles = particles;
   }

   void run() {
      boolean vanillaSounds = BedrockSoundEngine.vanilla();
      boolean vanillaParticles = BedrockParticleEngine.vanilla();
      log(
         "switches: vanilla sounds {}, vanilla particles {}",
         vanillaSounds ? "Bedrock's" : "Java's (-PjavaSounds)",
         vanillaParticles ? "Bedrock's" : "Java's (-PjavaParticles)"
      );
      if (!this.waitFor(30000L, () -> this.onClient(() -> BedrockSoundEngine.catalog() != null && BedrockParticleEngine.effects() != null))) {
         this.fail("the sound catalog or the particle effects never loaded");
         this.finish();
      } else {
         SoundCatalog catalog = this.onClient(BedrockSoundEngine::catalog);
         ParticleEffects effects = this.onClient(BedrockParticleEngine::effects);
         log(
            "catalog: {} definitions (install: {}), {} sounds.json entries, {} block sound types, {} music entries",
            catalog.definitions().size(),
            catalog.baseGame(),
            catalog.events().size(),
            catalog.blockSoundCount(),
            catalog.music().size()
         );
         log(
            "particles: {} effects from {} files, {} compiled files unreadable, {} Molang errors",
            effects.size(),
            effects.files(),
            effects.unreadable(),
            effects.molangErrors()
         );
         this.check("the install's sound definitions are in the catalog (more than 1500)", catalog.baseGame() && catalog.definitions().size() > 1500);
         SoundDefinitions.Definition chime = catalog.definitions().get("bedrocknative.test.chime");
         this.check("the server pack defines bedrocknative.test.chime", chime != null && chime.serverPack());
         this.check("the install's particle effects decode (more than 150, none unreadable)", effects.size() > 150 && effects.unreadable() == 0);
         ParticleEffect sparkle = effects.get("bedrocknative:test_sparkle");
         this.check(
            "the server pack defines bedrocknative:test_sparkle with its curve and event",
            sparkle != null && sparkle.serverPack() && !sparkle.curves().isEmpty() && sparkle.events().containsKey("chime")
         );
         if (this.onClient(() -> this.minecraft.player.isDeadOrDying())) {
            this.minecraft.execute(() -> this.minecraft.player.respawn());
            sleep(4000L);
         }

         this.commands("gamemode creative", "tp @s -41.5 102 169.5");
         sleep(3000L);
         this.commands("fill -60 86 150 -24 106 190 air replace water", "fill -60 86 150 -24 106 190 air replace flowing_water");
         sleep(1500L);
         this.commands(
            "gamemode creative",
            "time set noon",
            "weather clear",
            "kill @e[type=bedrocknative:test_golem]",
            "kill @e[type=pig]",
            "fill ~-4 ~ ~-8 ~4 ~4 ~-1 air",
            "fill ~-4 ~-1 ~-8 ~4 ~-1 ~-1 stone"
         );
         this.run(() -> {
            this.minecraft.player.setYRot(180.0F);
            this.minecraft.player.setXRot(5.0F);
         });
         sleep(1000L);
         int chimes = count(this.onClient(BedrockSoundEngine::recent), "bedrocknative.test.chime");
         this.commands(1000L, "playsound bedrocknative.test.chime @s");
         this.check(
            "server sound bedrocknative.test.chime played the Bedrock way (WAV from the server pack)",
            this.waitFor(2000L, () -> count(this.onClient(BedrockSoundEngine::recent), "bedrocknative.test.chime") > chimes)
         );
         this.soundStep(vanillaSounds, "random.levelup", "playsound random.levelup @s");
         this.soundStep(vanillaSounds, "note.pling", "playsound note.pling @s ~ ~ ~ 1 1.5");
         if (vanillaSounds) {
            this.commands(1500L, "playsound music.game @s");
            this.check(
               "music.game streams the Bedrock way (Ogg from the install's music pack)", this.onClient(BedrockSoundEngine::recent).contains("music.game")
            );
            this.commands(1000L, "stopsound @s");
            List<String> started = List.of("music.game", "bedrocknative.test.chime", "random.levelup", "note.pling");
            boolean stopped = this.waitFor(4000L, () -> this.onClient(BedrockSoundEngine::playingNames).stream().noneMatch(started::contains));
            log("playing after /stopsound: {}", this.onClient(BedrockSoundEngine::playingNames));
            this.check("/stopsound stopped the music and every sound the test played", stopped);
            this.commands(1500L, "summon pig ~2 ~ ~-3");
            this.commands(1000L, "damage @e[type=pig,c=1] 1");
            List<String> heard = this.onClient(BedrockSoundEngine::recent);
            this.check(
               "the pig's hurt sound came from sounds.json (mob.pig.say or its variant's mob.mini_pig.hurt, mob.big_pig.hurt, mob.baby_pig.hurt)",
               heard.contains("mob.pig.say")
                  || heard.contains("mob.mini_pig.hurt")
                  || heard.contains("mob.big_pig.hurt")
                  || heard.contains("mob.baby_pig.hurt")
            );
         }

         this.javaSounds(vanillaSounds);
         int chimesBefore = count(this.onClient(BedrockSoundEngine::recent), "bedrocknative.test.chime");
         this.commands(400L, "particle bedrocknative:test_sparkle ~ ~1.2 ~-3");
         boolean shown = this.waitFor(
            2000L,
            () -> this.onClient(() -> BedrockParticleEngine.particleCount() > 0 && BedrockParticleEngine.recent().contains("bedrocknative:test_sparkle"))
         );
         this.check("server particle bedrocknative:test_sparkle runs the Bedrock way", shown);
         int quads = this.peakQuads(500L);
         log(
            "sparkle: {} particles alive in {} emitters, at most {} billboards in a frame",
            this.onClient(BedrockParticleEngine::particleCount),
            this.onClient(BedrockParticleEngine::emitterCount),
            quads
         );
         this.check("the sparkle is drawn (billboards in the frame)", quads > 0);
         this.screenshot("sounds-particles-sparkle");
         this.check(
            "the sparkle's creation event played the chime through sounds.json",
            this.waitFor(1500L, () -> count(this.onClient(BedrockSoundEngine::recent), "bedrocknative.test.chime") > chimesBefore)
         );
         boolean clear = this.waitFor(5000L, () -> this.onClient(BedrockParticleEngine::particleCount) == 0);
         log("Bedrock particles gone before the vanilla ones: {}", clear);
         int javaParticles = this.particles.shownCount();
         CompletableFuture<Integer> vanillaPeak = CompletableFuture.supplyAsync(() -> this.peakQuads(2000L));
         this.burst(
            "particle minecraft:villager_happy ~-1 ~1.5 ~-3",
            "particle minecraft:basic_flame_particle ~1 ~1 ~-3",
            "particle minecraft:critical_hit_emitter ~ ~1 ~-4"
         );
         sleep(200L);
         this.screenshot("sounds-particles-vanilla");
         int vanillaQuads = vanillaPeak.join();
         if (vanillaParticles) {
            this.check(
               "vanilla /particle effects run the Bedrock way (install's compiled effects)",
               this.onClient(
                  () -> BedrockParticleEngine.recent().contains("minecraft:villager_happy")
                     && BedrockParticleEngine.recent().contains("minecraft:critical_hit_emitter")
               )
            );
            this.check("vanilla particles are drawn (" + vanillaQuads + " billboards at most in a frame)", clear && vanillaQuads > 0);
         } else {
            this.check(
               "vanilla /particle effects stay Java's with the switch off",
               this.particles.shownCount() > javaParticles && !this.onClient(BedrockParticleEngine::recent).contains("minecraft:villager_happy")
            );
         }

         sleep(1000L);
         this.commands(2000L, "summon bedrocknative:test_golem ~ ~ ~-4 0 0");
         int golemChimes = count(this.onClient(BedrockSoundEngine::recent), "bedrocknative.test.chime");
         int sparkles = count(this.onClient(BedrockParticleEngine::recent), "bedrocknative:test_sparkle");
         this.commands(800L, "event entity @e[type=bedrocknative:test_golem] bedrocknative:pose");
         boolean started = this.waitFor(3000L, () -> count(this.onClient(BedrockParticleEngine::recent), "bedrocknative:test_sparkle") > sparkles);
         this.check("the golem's posed state started its sparkle (controller particle_effects)", started);
         this.check(
            "the golem's posed state played its chime (controller sound_effects)",
            this.waitFor(1500L, () -> count(this.onClient(BedrockSoundEngine::recent), "bedrocknative.test.chime") > golemChimes)
         );
         this.screenshot("sounds-particles-golem");
         this.commands(1000L, "event entity @e[type=bedrocknative:test_golem] bedrocknative:rest");
         this.check(
            "leaving the state ended the golem's sparkle",
            this.waitFor(2500L, () -> this.onClient(() -> BedrockParticleEngine.particleCount("bedrocknative:test_sparkle")) == 0)
         );
         this.commands(500L, "kill @e[type=bedrocknative:test_golem]", "kill @e[type=pig]");
         log(
            "counters: Bedrock sounds played {}, out of range {}, not playable {}; Java-mapped sounds {}, Bedrock-way sounds {}; Java particle events {}, Bedrock-way particle events {}, dropped by the caps {}",
            BedrockSoundEngine.playedCount(),
            BedrockSoundEngine.outOfRangeCount(),
            BedrockSoundEngine.notPlayableCount(),
            this.sounds.playedCount(),
            this.sounds.playedBedrockCount(),
            this.particles.shownCount(),
            this.particles.shownBedrockCount(),
            BedrockParticleEngine.droppedCount()
         );
         log("Java sounds played the Bedrock way {}, silenced {}", this.onClient(JavaSoundBridge::bridgedCount), this.onClient(JavaSoundBridge::silencedCount));
         log("recent Bedrock sounds {}", this.onClient(BedrockSoundEngine::recent));
         log("recent Bedrock effects {}", this.onClient(BedrockParticleEngine::recent));
         this.finish();
      }
   }

   private void javaSounds(boolean vanilla) {
      int bridged = this.onClient(JavaSoundBridge::bridgedCount);
      int clicks = count(this.onClient(BedrockSoundEngine::recent), "random.click");
      this.run(() -> this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F)));
      sleep(500L);
      if (vanilla) {
         this.check("Java's menu click played as Bedrock's random.click", count(this.onClient(BedrockSoundEngine::recent), "random.click") > clicks);
      } else {
         this.check("Java's menu click stayed Java's with the switch off", this.onClient(JavaSoundBridge::bridgedCount) == bridged);
      }

      this.commands(800L, "setblock ~ ~-1 ~ stone");
      int stoneSteps = count(this.onClient(BedrockSoundEngine::recent), "step.stone");
      this.step();
      if (vanilla) {
         this.check("Java's step on stone played as Bedrock's step.stone", count(this.onClient(BedrockSoundEngine::recent), "step.stone") > stoneSteps);
      } else {
         this.check("Java's step on stone stayed Java's with the switch off", count(this.onClient(BedrockSoundEngine::recent), "step.stone") == stoneSteps);
      }

      this.commands(800L, "setblock ~ ~-1 ~ bedrocknative:test_full");
      int woodSteps = count(this.onClient(BedrockSoundEngine::recent), "step.wood");
      this.step();
      this.check(
         "a step on bedrocknative:test_full played its blocks.json sound type (step.wood)",
         count(this.onClient(BedrockSoundEngine::recent), "step.wood") > woodSteps
      );
      this.commands(500L, "setblock ~ ~-1 ~ stone");
   }

   private void step() {
      this.run(
         () -> {
            SoundEvent sound = this.minecraft.level.getBlockState(this.minecraft.player.blockPosition().below()).getSoundType().getStepSound();
            this.minecraft
               .level
               .playLocalSound(
                  this.minecraft.player.getX(), this.minecraft.player.getY(), this.minecraft.player.getZ(), sound, SoundSource.PLAYERS, 0.15F, 1.0F, false
               );
         }
      );
      sleep(500L);
   }

   private void soundStep(boolean vanilla, String name, String command) {
      int bedrock = count(this.onClient(BedrockSoundEngine::recent), name);
      int java = this.sounds.playedCount();
      this.commands(1000L, command);
      if (vanilla) {
         this.check(name + " played the Bedrock way (from the install)", count(this.onClient(BedrockSoundEngine::recent), name) > bedrock);
      } else {
         this.check(
            name + " played through Java's mapping with the switch off",
            this.sounds.playedCount() > java && count(this.onClient(BedrockSoundEngine::recent), name) == bedrock
         );
      }
   }

   private static int count(List<String> list, String value) {
      return Collections.frequency(list, value);
   }

   private void check(String what, boolean ok) {
      this.checks++;
      if (ok) {
         this.passed++;
         log("PASS {}", what);
      } else {
         this.fail(what);
      }
   }

   private void fail(String what) {
      this.failures.add(what);
      BedrockNative.LOGGER.warn("Dev sounds-particles test: FAIL {}", what);
   }

   private void finish() {
      log("{} of {} checks passed{}", this.passed, this.checks, this.failures.isEmpty() ? "" : "; failed: " + this.failures);
      log("done");
   }

   private static void log(String message, Object... arguments) {
      BedrockNative.LOGGER.info("Dev sounds-particles test: " + message, arguments);
   }

   private void commands(String... commands) {
      this.commands(1500L, commands);
   }

   private void commands(long settle, String... commands) {
      for (String command : commands) {
         log("/{}", command);
         this.run(() -> this.minecraft.getConnection().sendCommand(command));
         sleep(400L);
      }

      sleep(settle);
   }

   private void burst(String... commands) {
      for (String command : commands) {
         log("/{}", command);
      }

      this.run(() -> {
         for (String commandx : commands) {
            this.minecraft.getConnection().sendCommand(commandx);
         }
      });
   }

   private int peakQuads(long millis) {
      int peak = 0;
      long started = System.currentTimeMillis();

      while (System.currentTimeMillis() - started < millis) {
         peak = Math.max(peak, this.onClient(BedrockParticleEngine::lastFrameQuads));
         sleep(40L);
      }

      return peak;
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
