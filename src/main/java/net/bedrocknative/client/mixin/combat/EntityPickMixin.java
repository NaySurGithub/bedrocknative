package net.bedrocknative.client.mixin.combat;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.bedrocknative.client.movement.BedrockMovement;
import net.bedrocknative.movement.EntityPick;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;

@Mixin({ProjectileUtil.class})
public abstract class EntityPickMixin {
   @WrapMethod(
      method = {"getEntityHitResult(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;D)Lnet/minecraft/world/phys/EntityHitResult;"}
   )
   private static EntityHitResult bedrocknative$pick(
      Entity except, Vec3 from, Vec3 to, AABB box, Predicate<Entity> matching, double maxValue, Operation<EntityHitResult> original
   ) {
      if (BedrockMovement.active() && except != null && except.level().isClientSide()) {
         List<Entity> entities = new ArrayList<>(except.level().getEntities(except, box, matching));
         List<EntityPick.Candidate> candidates = new ArrayList<>(entities.size());
         Entity sourceRoot = except.getRootVehicle();

         for (Entity entity : entities) {
            candidates.add(
               new EntityPick.Candidate(entity.getBoundingBox(), entity.getPickRadius(), entity.canBePickedFromInside(), entity.getRootVehicle() == sourceRoot)
            );
         }

         EntityPick.Choice choice = EntityPick.choose(candidates, from, to, maxValue);
         return choice == null ? null : new EntityHitResult(entities.get(choice.index()), choice.point());
      } else {
         return (EntityHitResult)original.call(new Object[]{except, from, to, box, matching, maxValue});
      }
   }
}
