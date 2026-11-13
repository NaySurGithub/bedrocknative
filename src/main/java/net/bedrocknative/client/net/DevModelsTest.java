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
import net.bedrocknative.client.model.BedrockActors;
import net.bedrocknative.client.model.BedrockModels;
import net.bedrocknative.model.entity.ActorPropertyTypes;
import net.bedrocknative.model.entity.ModelRegistry;
import net.bedrocknative.model.runtime.ActorInstance;
import net.bedrocknative.model.runtime.DrawList;
import net.bedrocknative.world.entity.BedrockActorEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

final class DevModelsTest {
   private static final String GOLEM = "bedrocknative:test_golem";
   private static final String POSED = "bedrocknative:posed";
   private static final float TOLERANCE = 0.002F;
   private final Minecraft minecraft;
   private final List<String> failures = new ArrayList<>();
   private int checks;
   private int passed;

   DevModelsTest(Minecraft minecraft) {
      this.minecraft = minecraft;
   }

   void run() {
      if (!this.waitFor(30000L, () -> BedrockModels.registry() != null)) {
         this.fail("the model registry never loaded");
         this.finish();
      } else {
         ModelRegistry registry = this.onClient(BedrockModels::registry);
         BedrockActors actors = this.onClient(BedrockActors::current);
         this.check("the pack stack defines bedrocknative:test_golem", registry.entity("bedrocknative:test_golem") != null);
         this.check(
            "the server lists bedrocknative:test_golem in AvailableActorIdentifiers",
            this.onClient(() -> actors.identifiers().containsKey("bedrocknative:test_golem"))
         );
         List<ActorPropertyTypes.Property> properties = this.onClient(() -> actors.propertyTypes().properties("bedrocknative:test_golem"));
         log("SyncActorProperty for {}: {}", "bedrocknative:test_golem", properties);
         this.check(
            "SyncActorProperty gave bedrocknative:posed as a bool at the join",
            properties.stream().anyMatch(property -> property.name().equals("bedrocknative:posed") && property.kind() == ActorPropertyTypes.Kind.BOOL)
         );
         this.commands(
            "gamemode creative",
            "kill @e[type=bedrocknative:test_golem]",
            "fill ~-5 ~ ~-9 ~5 ~5 ~-1 air",
            "fill ~-5 ~-1 ~-9 ~5 ~-1 ~-1 stone",
            "summon bedrocknative:test_golem ~ ~ ~-4 0 0"
         );
         this.run(() -> {
            this.minecraft.player.setYRot(180.0F);
            this.minecraft.player.setXRot(10.0F);
         });
         sleep(2000L);
         Entity first = this.onClient(() -> this.nearestGolem(null));
         if (first == null) {
            this.fail("no bedrocknative:test_golem carrier near the player after /summon");
            this.finish();
         } else {
            ActorState rest = this.onClient(() -> BedrockActors.state(first));
            this.check("the golem is a drawn carrier", first instanceof BedrockActorEntity && rest != null && rest.instance() != null);
            this.check("the golem's collision box is 1 x 2", Math.abs(first.getBbWidth() - 1.0F) < 0.001F && Math.abs(first.getBbHeight() - 2.0F) < 0.001F);
            this.drawn("at rest", first, "test_golem_gray", false);
            this.hand("summoned at yaw 0", first, -0.4375F, 0.75F, 0.0F);
            this.screenshot("models-rest");
            this.commands("event entity @e[type=bedrocknative:test_golem] bedrocknative:pose");
            boolean posed = this.waitFor(3000L, () -> Boolean.TRUE.equals(this.onClient(() -> {
               ActorState state = BedrockActors.state(first);
               return state != null ? state.data().property("bedrocknative:posed") : null;
            })));
            this.check("SetActorData carried bedrocknative:posed = true", posed);
            sleep(1200L);
            this.hand("posed for 1.2 s", first, -0.4375F, 1.375F, 0.625F);
            this.screenshot("models-posed");
            this.commands("summon bedrocknative:test_golem ~3 ~ ~-4 90 0");
            sleep(2000L);
            Entity second = this.onClient(() -> this.nearestGolem(first));
            if (second == null) {
               this.fail("no second bedrocknative:test_golem after /summon at yaw 90");
            } else {
               log("second golem body yaw {}", this.onClient(() -> {
                  ActorState state = BedrockActors.state(second);
                  return state != null ? state.lastBodyYaw() : Float.NaN;
               }));
               this.hand("summoned at yaw 90, at rest", second, 0.0F, 0.75F, -0.4375F);
            }

            this.commands("event entity @e[type=bedrocknative:test_golem] bedrocknative:red");
            sleep(1000L);
            this.drawn("after bedrocknative:red", first, "test_golem_red", true);
            if (second != null) {
               this.drawn("after bedrocknative:red (second golem)", second, "test_golem_red", true);
            }

            this.screenshot("models-red");
            int animateBefore = this.onClient(() -> actors.animateLog().size());
            this.run(() -> this.minecraft.getConnection().sendCommand("playanimation @e[type=bedrocknative:test_golem] animation.bedrocknative.test_golem.nod"));
            int most = 0;
            long sent = System.currentTimeMillis();
            long seen = 0L;
            long gone = 0L;

            while (System.currentTimeMillis() - sent < 3000L) {
               int running = this.onClient(() -> {
                  ActorState state = BedrockActors.state(first);
                  ActorInstance instance = state != null ? state.instance() : null;
                  return instance != null ? instance.runtimeControllers() : -1;
               });
               if (running > 0 && seen == 0L) {
                  seen = System.currentTimeMillis();
               }

               most = Math.max(most, running);
               if (seen != 0L && running == 0) {
                  gone = System.currentTimeMillis();
                  break;
               }

               sleep(20L);
            }

            List<String> animate = this.onClient(() -> actors.animateLog());
            log("AnimateEntity as BDS sent it: {}", animate.subList(Math.min(animateBefore, animate.size()), animate.size()));
            this.check("/playanimation started one runtime controller (" + (seen == 0L ? "never seen" : seen - sent + " ms after the command") + ")", most == 1);
            this.check(
               "the runtime controller was gone within 1 s (" + (gone == 0L ? "still running" : gone - seen + " ms") + ")", gone != 0L && gone - seen <= 1000L
            );
            this.probeScreenshots(first);
            this.commands("kill @e[type=bedrocknative:test_golem]");
            this.finish();
         }
      }
   }

   private void probeScreenshots(Entity golem) {
      Vec3 at = this.onClient(golem::position);

      for (int side = -1; side <= 1; side += 2) {
         double x = at.x + 2.5 * side;
         double z = at.z + 2.5;
         float yaw = (float)Math.toDegrees(Math.atan2(at.z - z, at.x - x)) - 90.0F;
         this.commands(String.format(Locale.ROOT, "tp @s %.2f %.2f %.2f %.1f 20", x, at.y, z, yaw));
         sleep(1000L);
         this.screenshot(side > 0 ? "models-probe-east" : "models-probe-west");
      }
   }

   private void drawn(String when, Entity golem, String texture, boolean markerVisible) {
      String[] seen = this.onClient(
         () -> {
            ActorState state = BedrockActors.state(golem);
            ActorSnapshot snapshot = state != null ? state.lastSnapshot() : null;
            ActorInstance instance = state != null ? state.instance() : null;
            if (snapshot != null && instance != null && !instance.lastDrawList().calls().isEmpty()) {
               DrawList.DrawCall call = instance.lastDrawList().calls().getFirst();
               int marker = call.mesh().bone("marker_red");
               return new String[]{
                  String.valueOf(snapshot.texturePath(0)),
                  marker >= 0 ? String.valueOf(call.boneVisible()[marker]) : "no bone",
                  String.valueOf(state.evaluations())
               };
            } else {
               return null;
            }
         }
      );
      if (seen == null) {
         this.fail(when + ": nothing drawn yet (is the golem on screen?)");
      } else {
         log("{}: texture {}, marker_red visible {}, {} frames evaluated", when, seen[0], seen[1], seen[2]);
         this.check(when + ": texture ends with " + texture, seen[0].endsWith(texture));
         this.check(when + ": marker_red " + (markerVisible ? "visible" : "hidden"), String.valueOf(markerVisible).equals(seen[1]));
      }
   }

   private void hand(String when, Entity golem, float x, float y, float z) {
      Vector3f offset = this.onClient(() -> {
         ActorState state = BedrockActors.state(golem);
         return state != null ? state.locatorOffset("hand") : null;
      });
      if (offset == null) {
         this.fail(when + ": no hand locator (not drawn yet?)");
      } else {
         boolean close = Math.abs(offset.x - x) <= 0.002F && Math.abs(offset.y - y) <= 0.002F && Math.abs(offset.z - z) <= 0.002F;
         this.check(
            String.format(Locale.ROOT, "%s: hand at (%.4f, %.4f, %.4f), expected (%.4f, %.4f, %.4f)", when, offset.x, offset.y, offset.z, x, y, z), close
         );
      }
   }

   @Nullable
   private Entity nearestGolem(@Nullable Entity except) {
      Entity nearest = null;

      for (Entity entity : this.minecraft.level.entitiesForRendering()) {
         if (entity instanceof BedrockActorEntity carrier
            && carrier.bedrockIdentifier().equals("bedrocknative:test_golem")
            && entity != except
            && (nearest == null || entity.distanceToSqr(this.minecraft.player) < nearest.distanceToSqr(this.minecraft.player))) {
            nearest = entity;
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
      BedrockNative.LOGGER.warn("Dev models test FAILED: {}", what);
   }

   private void finish() {
      log("{} of {} checks passed{}", this.passed, this.checks, this.failures.isEmpty() ? "" : "; failed: " + this.failures);
      log("done");
   }

   private static void log(String message, Object... arguments) {
      BedrockNative.LOGGER.info("Dev models test: " + message, arguments);
   }

   private void commands(String... commands) {
      this.commands(1500L, commands);
   }

   private void commands(long settle, String... commands) {
      for (String command : commands) {
         this.run(() -> this.minecraft.getConnection().sendCommand(command));
         sleep(400L);
      }

      sleep(settle);
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
