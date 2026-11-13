package net.bedrocknative.client.world;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Entity.RemovalReason;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.packet.SpawnExperienceOrbPacket;

public final class ExperienceOrbs {
   private static final int MAX_ORBS = 256;
   private static final double PICKUP_DISTANCE_SQUARED = 1.0;
   private static final float MAX_COORDINATE = 3.0E7F;
   private final Minecraft minecraft;
   private final BedrockEntities entities;
   private final List<Entity> orbs = new ArrayList<>();
   private int collected;

   public ExperienceOrbs(Minecraft minecraft, BedrockEntities entities) {
      this.minecraft = minecraft;
      this.entities = entities;
   }

   public void apply(SpawnExperienceOrbPacket packet) {
      Vector3f at = packet.getPosition();
      if (at != null
         && Math.abs(at.getX()) < 3.0E7F
         && Math.abs(at.getY()) < 3.0E7F
         && Math.abs(at.getZ()) < 3.0E7F
         && packet.getAmount() > 0
         && this.orbs.size() < 256) {
         Entity orb = this.entities.spawnUnaddressed(EntityTypes.EXPERIENCE_ORB, at);
         if (orb instanceof ExperienceOrb experienceOrb) {
            experienceOrb.setValue(packet.getAmount());
            this.orbs.add(orb);
         }
      }
   }

   public void tick() {
      LocalPlayer player = this.minecraft.player;
      if (!this.orbs.isEmpty() && player != null && this.minecraft.getConnection() != null) {
         this.orbs
            .removeIf(
               orb -> {
                  if (orb.isRemoved()) {
                     return true;
                  } else if (player.isAlive()
                     && !player.isSpectator()
                     && orb.distanceToSqr(player.getX(), player.getY() + player.getBbHeight() / 2.0F, player.getZ()) < 1.0) {
                     this.minecraft.getConnection().handleTakeItemEntity(new ClientboundTakeItemEntityPacket(orb.getId(), player.getId(), 1));
                     if (this.minecraft.level != null) {
                        this.minecraft.level.removeEntity(orb.getId(), RemovalReason.DISCARDED);
                     }

                     this.collected++;
                     return true;
                  } else {
                     return false;
                  }
               }
            );
      }
   }

   public int collectedCount() {
      return this.collected;
   }

   public int count() {
      return this.orbs.size();
   }

   public void clear() {
      this.orbs.clear();
   }
}
