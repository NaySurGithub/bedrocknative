package net.bedrocknative.client.mixin.movement;

import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractBoat.class)
public interface AbstractBoatAccessor {
   @Accessor("deltaRotation")
   void bedrocknative$setDeltaRotation(float var1);
}
