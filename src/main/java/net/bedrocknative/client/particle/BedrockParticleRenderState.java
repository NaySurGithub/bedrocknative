package net.bedrocknative.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.bedrocknative.client.content.BedrockTextures;
import net.bedrocknative.particle.ParticleEffect;
import net.bedrocknative.particle.QuadSink;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle.Layer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import org.jspecify.annotations.Nullable;

final class BedrockParticleRenderState extends QuadParticleRenderState implements QuadSink {
   private static final int FLOATS = 13;
   private static final int UNLIT = 15728640;
   private final Map<Layer, BedrockParticleRenderState.Batch> batches = new HashMap<>();
   private final Map<ParticleEffect, Optional<BedrockParticleRenderState.Batch>> byEffect = new IdentityHashMap<>();
   private final Map<String, Layer> layersByKey = new HashMap<>();
   private final Set<Layer> active = new LinkedHashSet<>();
   private final MutableBlockPos cursor = new MutableBlockPos();
   @Nullable
   private BedrockParticleRenderState.Batch current;
   @Nullable
   private Frustum frustum;
   @Nullable
   private ClientLevel level;
   private int total;

   void begin(Frustum frustum, ClientLevel level) {
      this.frustum = frustum;
      this.level = level;
   }

   void forgetTextures() {
      this.byEffect.clear();
      this.layersByKey.clear();
      this.batches.clear();
      this.active.clear();
      this.current = null;
   }

   @Override
   public boolean begin(ParticleEffect effect) {
      Optional<BedrockParticleRenderState.Batch> batch = this.byEffect.get(effect);
      if (batch == null) {
         batch = Optional.ofNullable(this.layer(effect)).map(layer -> this.batches.computeIfAbsent(layer, BedrockParticleRenderState.Batch::new));
         this.byEffect.put(effect, batch);
      }

      this.current = batch.orElse(null);
      return this.current != null;
   }

   @Nullable
   private Layer layer(ParticleEffect effect) {
      String texture = effect.texture();
      String key = texture + "|" + effect.material();
      Layer known = this.layersByKey.get(key);
      if (known != null) {
         return known;
      } else {
         Identifier location;
         if (texture.equalsIgnoreCase("atlas.terrain")) {
            location = TextureAtlas.LOCATION_BLOCKS;
         } else if (texture.equalsIgnoreCase("atlas.items")) {
            location = TextureAtlas.LOCATION_ITEMS;
         } else {
            Optional<BedrockTextures.Texture> loaded = texture.isEmpty() ? Optional.empty() : BedrockTextures.pack(texture);
            if (loaded.isEmpty()) {
               return null;
            }

            location = loaded.get().id();
         }

         boolean blended = effect.material() == ParticleEffect.Material.BLEND || effect.material() == ParticleEffect.Material.ADD;
         Layer layer = blended
            ? new Layer(true, location, RenderPipelines.TRANSLUCENT_PARTICLE, RenderPipelines.OIT_PARTICLE)
            : new Layer(false, location, RenderPipelines.OPAQUE_PARTICLE);
         this.layersByKey.put(key, layer);
         return layer;
      }
   }

   @Override
   public boolean visible(double x, double y, double z) {
      Frustum culling = this.frustum;
      return culling == null || culling.pointInFrustum(x, y, z);
   }

   @Override
   public void quad(
      float x,
      float y,
      float z,
      float rightX,
      float rightY,
      float rightZ,
      float upX,
      float upY,
      float upZ,
      float u0,
      float v0,
      float u1,
      float v1,
      int argb,
      double worldX,
      double worldY,
      double worldZ,
      boolean lit
   ) {
      BedrockParticleRenderState.Batch batch = this.current;
      if (batch != null) {
         float normalX = rightY * upZ - rightZ * upY;
         float normalY = rightZ * upX - rightX * upZ;
         float normalZ = rightX * upY - rightY * upX;
         if (normalX * -x + normalY * -y + normalZ * -z < 0.0F) {
            rightX = -rightX;
            rightY = -rightY;
            rightZ = -rightZ;
            float swap = u0;
            u0 = u1;
            u1 = swap;
         }

         if (batch.count * 13 + 13 > batch.floats.length) {
            batch.floats = Arrays.copyOf(batch.floats, batch.floats.length * 2);
            batch.ints = Arrays.copyOf(batch.ints, batch.ints.length * 2);
         }

         float[] floats = batch.floats;
         int at = batch.count * 13;
         floats[at] = x;
         floats[at + 1] = y;
         floats[at + 2] = z;
         floats[at + 3] = rightX;
         floats[at + 4] = rightY;
         floats[at + 5] = rightZ;
         floats[at + 6] = upX;
         floats[at + 7] = upY;
         floats[at + 8] = upZ;
         floats[at + 9] = u0;
         floats[at + 10] = v0;
         floats[at + 11] = u1;
         floats[at + 12] = v1;
         batch.ints[batch.count * 2] = argb;
         batch.ints[batch.count * 2 + 1] = lit ? this.light(worldX, worldY, worldZ) : 15728880;
         batch.count++;
         if (batch.count == 1) {
            this.active.add(batch.layer);
         }

         this.total++;
      }
   }

   private int light(double x, double y, double z) {
      ClientLevel current = this.level;
      if (current == null) {
         return 15728880;
      } else {
         this.cursor.set(x, y, z);
         return current.hasChunkAt(this.cursor) ? LightCoordsUtil.getLightCoords(current, this.cursor) : 15728640;
      }
   }

   public boolean isEmpty() {
      return this.total == 0;
   }

   public Set<Layer> layers() {
      return this.active;
   }

   public void buildLayer(Layer layer, VertexConsumer builder) {
      BedrockParticleRenderState.Batch batch = this.batches.get(layer);
      if (batch != null) {
         float[] floats = batch.floats;

         for (int i = 0; i < batch.count; i++) {
            int at = i * 13;
            float x = floats[at];
            float y = floats[at + 1];
            float z = floats[at + 2];
            float rx = floats[at + 3];
            float ry = floats[at + 4];
            float rz = floats[at + 5];
            float ux = floats[at + 6];
            float uy = floats[at + 7];
            float uz = floats[at + 8];
            float u0 = floats[at + 9];
            float v0 = floats[at + 10];
            float u1 = floats[at + 11];
            float v1 = floats[at + 12];
            int color = batch.ints[i * 2];
            int light = batch.ints[i * 2 + 1];
            builder.addVertex(x + rx - ux, y + ry - uy, z + rz - uz).setUv(u1, v1).setColor(color).setLight(light);
            builder.addVertex(x + rx + ux, y + ry + uy, z + rz + uz).setUv(u1, v0).setColor(color).setLight(light);
            builder.addVertex(x - rx + ux, y - ry + uy, z - rz + uz).setUv(u0, v0).setColor(color).setLight(light);
            builder.addVertex(x - rx - ux, y - ry - uy, z - rz - uz).setUv(u0, v1).setColor(color).setLight(light);
         }
      }
   }

   public void clear() {
      for (Layer layer : this.active) {
         BedrockParticleRenderState.Batch batch = this.batches.get(layer);
         if (batch != null) {
            batch.count = 0;
         }
      }

      this.active.clear();
      this.total = 0;
      this.current = null;
   }

   public void submit(SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
      if (this.total > 0) {
         submitNodeCollector.submitQuadParticleGroup(this);
      }
   }

   int quadCount() {
      return this.total;
   }

   private static final class Batch {
      final Layer layer;
      float[] floats = new float[3328];
      int[] ints = new int[512];
      int count;

      Batch(Layer layer) {
         this.layer = layer;
      }
   }
}
