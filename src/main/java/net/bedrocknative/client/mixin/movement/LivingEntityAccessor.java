package net.bedrocknative.client.mixin.movement;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
   @Accessor("jumping")
   boolean bedrocknative$isJumping();

   @Accessor("jumping")
   void bedrocknative$setJumping(boolean var1);

   @Invoker("travelRidden")
   void bedrocknative$travelRidden(Player var1, Vec3 var2);
}
