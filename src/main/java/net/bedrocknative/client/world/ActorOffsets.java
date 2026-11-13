package net.bedrocknative.client.world;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Player;

public final class ActorOffsets {
   public static final double ITEM = 0.125;
   public static final double TNT = 0.49;
   public static final double MINECART = 0.35;

   private ActorOffsets() {
   }

   public static double spawn(EntityType<?> type) {
      if (type == EntityTypes.ITEM) {
         return 0.125;
      } else if (type == EntityTypes.TNT) {
         return 0.49;
      } else {
         String path = BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath();
         if (path.contains("minecart")) {
            return 0.35;
         } else {
            return !path.endsWith("boat") && !path.endsWith("raft") ? 0.0 : 0.375;
         }
      }
   }

   public static double addPlayer() {
      return 0.0;
   }

   public static double move(Entity entity) {
      return entity instanceof Player ? 1.62001F : spawn(entity.getType());
   }

   public static double move(EntityType<?> type) {
      return type == EntityTypes.PLAYER ? 1.62001F : spawn(type);
   }
}
