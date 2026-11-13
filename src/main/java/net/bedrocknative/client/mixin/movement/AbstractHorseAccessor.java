package net.bedrocknative.client.mixin.movement;

import net.minecraft.world.entity.animal.equine.AbstractHorse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractHorse.class)
public interface AbstractHorseAccessor {
   @Accessor("playerJumpPendingScale")
   float bedrocknative$playerJumpPendingScale();

   @Accessor("playerJumpPendingScale")
   void bedrocknative$setPlayerJumpPendingScale(float var1);
}
