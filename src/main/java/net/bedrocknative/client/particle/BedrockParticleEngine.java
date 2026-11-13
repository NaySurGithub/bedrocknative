package net.bedrocknative.client.particle;

import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.content.LoadQueue;
import net.bedrocknative.content.PackSource;
import net.bedrocknative.content.PackStack;
import net.bedrocknative.molang.MolangProgram;
import net.bedrocknative.molang.VariableStore;
import net.bedrocknative.particle.ParticleAnchor;
import net.bedrocknative.particle.ParticleCamera;
import net.bedrocknative.particle.ParticleEffect;
import net.bedrocknative.particle.ParticleEffects;
import net.bedrocknative.particle.ParticleEmitter;
import net.bedrocknative.particle.ParticleSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.ParticlesRenderState;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

public final class BedrockParticleEngine {
   private static final boolean VANILLA = !Boolean.getBoolean("bedrocknative.javaParticles");
   private static final int MAX_PARTICLES = 16384;
   private static final int MAX_EMITTERS = 2048;
   private static final ClientParticleWorld WORLD = new ClientParticleWorld();
   private static final ParticleSystem SYSTEM = new ParticleSystem(WORLD, 16384, 2048);
   private static final BedrockParticleRenderState STATE = new BedrockParticleRenderState();
   private static final ParticleCamera CAMERA = new ParticleCamera();
   private static volatile @Nullable ParticleEffects effects;
   private static final LoadQueue WAITING = new LoadQueue(32);
   private static int generation;
   private static boolean active;
   private static int lastQuads;
   private static final ArrayDeque<String> RECENT = new ArrayDeque<>();
   private static final int RECENT_KEPT = 64;

   private BedrockParticleEngine() {
   }

   public static boolean vanilla() {
      return VANILLA;
   }

   public static @Nullable ParticleEffects effects() {
      return effects;
   }

   public static boolean active() {
      return active;
   }

   public static void reload() {
      active = true;
      int current = ++generation;
      List<PackSource> packs = List.copyOf(PackStack.active().packs());
      int serverPacks = Math.min(PackStack.serverPackCount(), packs.size());
      long started = Util.getMillis();
      WAITING.begin();
      CompletableFuture.<ParticleEffects>supplyAsync(() -> ParticleEffects.load(packs, serverPacks), Util.backgroundExecutor())
         .whenCompleteAsync(
            (loaded, error) -> {
               if (current == generation) {
                  if (error != null) {
                     BedrockNative.LOGGER.warn("Couldn't load the Bedrock particles of the pack stack", error);
                     WAITING.abandon();
                  } else {
                     SYSTEM.clear();
                     STATE.forgetTextures();
                     effects = loaded;
                     long server = loaded.all().values().stream().filter(ParticleEffect::serverPack).count();
                     BedrockNative.LOGGER
                        .info(
                           "Bedrock particles ready after {} ms: {} effects ({} from the server) from {} files, {} compiled files unreadable, {} Molang errors; vanilla particles {}",
                           new Object[]{
                              Util.getMillis() - started,
                              loaded.size(),
                              server,
                              loaded.files(),
                              loaded.unreadable(),
                              loaded.molangErrors(),
                              VANILLA ? "Bedrock's" : "Java's"
                           }
                        );
                     WAITING.finish(e -> BedrockNative.LOGGER.warn("A server particle that waited for the effects failed", e));
                  }
               }
            },
            Minecraft.getInstance()
         );
   }

   public static boolean whenLoaded(String key, Runnable task) {
      return effects == null && WAITING.offer(key, task);
   }

   public static void packsChanged() {
      if (active) {
         reload();
      }
   }

   public static void release() {
      active = false;
      generation++;
      effects = null;
      WAITING.abandon();
      SYSTEM.clear();
      STATE.forgetTextures();
      WORLD.clear();
   }

   public static @Nullable ParticleEmitter spawn(
      ParticleEffect effect,
      double x,
      double y,
      double z,
      @Nullable ParticleAnchor anchor,
      @Nullable MolangProgram preEffect,
      @Nullable Consumer<VariableStore> setup
   ) {
      if (!active) {
         return null;
      }

      remember(effect);
      return SYSTEM.spawn(effect, x, y, z, anchor, preEffect, setup);
   }

   private static void remember(ParticleEffect effect) {
      if (RECENT.size() >= 64) {
         RECENT.pollFirst();
      }

      RECENT.addLast(effect.identifier());
   }

   public static List<String> recent() {
      return List.copyOf(RECENT);
   }

   public static boolean emitManual(ParticleEffect effect, double x, double y, double z) {
      if (!active) {
         return false;
      }

      remember(effect);
      return SYSTEM.emitManual(effect, x, y, z, 0.0F, 0.0F, 0.0F);
   }

   public static void tick() {
      if (active && SYSTEM.emitterCount() != 0) {
         Vec3 viewer = Minecraft.getInstance().gameRenderer.mainCamera().position();
         SYSTEM.tick(viewer.x, viewer.y, viewer.z);
      }
   }

   public static void extract(ParticlesRenderState particles, Frustum frustum, Camera camera, float partialTick) {
      ClientLevel level = Minecraft.getInstance().level;
      if (active && level != null && SYSTEM.particleCount() != 0) {
         STATE.clear();
         Vec3 position = camera.position();
         Vector3fc forward = camera.forwardVector();
         Vector3fc up = camera.upVector();
         CAMERA.set(position.x, position.y, position.z, forward.x(), forward.y(), forward.z(), up.x(), up.y(), up.z());
         STATE.begin(frustum, level);
         SYSTEM.render(CAMERA, partialTick, STATE);
         lastQuads = STATE.quadCount();
         if (!STATE.isEmpty()) {
            particles.add(STATE);
         }
      } else {
         lastQuads = 0;
      }
   }

   public static void clearParticles() {
      SYSTEM.clear();
      WORLD.clear();
   }

   public static int particleCount() {
      return SYSTEM.particleCount();
   }

   public static int emitterCount() {
      return SYSTEM.emitterCount();
   }

   public static int particleCount(String identifier) {
      return SYSTEM.particleCount(identifier);
   }

   public static int droppedCount() {
      return SYSTEM.droppedCount();
   }

   public static int lastFrameQuads() {
      return lastQuads;
   }
}
