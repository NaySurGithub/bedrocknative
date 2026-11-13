package net.bedrocknative.client.particle;

import net.bedrocknative.client.model.ActorState;
import net.bedrocknative.client.model.BedrockActors;
import net.bedrocknative.molang.MolangActor;
import net.bedrocknative.particle.ParticleAnchor;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

public final class EntityParticleAnchor implements ParticleAnchor {
   private final Entity entity;
   private final @Nullable String locator;
   private final double offsetX;
   private final double offsetY;
   private final double offsetZ;

   public EntityParticleAnchor(Entity entity, @Nullable String locator, double offsetX, double offsetY, double offsetZ) {
      this.entity = entity;
      this.locator = locator;
      this.offsetX = offsetX;
      this.offsetY = offsetY;
      this.offsetZ = offsetZ;
   }

   @Override
   public boolean valid() {
      return !this.entity.isRemoved() && this.entity.level() == Minecraft.getInstance().level;
   }

   @Override
   public void position(double[] out) {
      out[0] = this.entity.getX() + this.offsetX;
      out[1] = this.entity.getY() + this.offsetY;
      out[2] = this.entity.getZ() + this.offsetZ;
      if (this.locator != null) {
         ActorState state = BedrockActors.state(this.entity);
         Vector3f point = state != null ? state.locatorOffset(this.locator) : null;
         if (point != null) {
            out[0] += point.x;
            out[1] += point.y;
            out[2] += point.z;
         }
      }
   }

   @Override
   public float yaw() {
      return this.entity instanceof LivingEntity living ? living.yBodyRot : this.entity.getYRot();
   }

   @Override
   public float scale() {
      return this.entity instanceof LivingEntity living ? living.getScale() : 1.0F;
   }

   @Override
   public void bounds(double[] out) {
      AABB box = this.entity.getBoundingBox();
      out[0] = box.minX - this.entity.getX();
      out[1] = box.minY - this.entity.getY();
      out[2] = box.minZ - this.entity.getZ();
      out[3] = box.maxX - this.entity.getX();
      out[4] = box.maxY - this.entity.getY();
      out[5] = box.maxZ - this.entity.getZ();
   }

   @Override
   public MolangActor actor() {
      ActorState state = BedrockActors.state(this.entity);
      return state != null ? state.view() : MolangActor.NONE;
   }
}
