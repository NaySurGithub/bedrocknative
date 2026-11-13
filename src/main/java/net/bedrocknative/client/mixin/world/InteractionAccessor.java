package net.bedrocknative.client.mixin.world;

import net.minecraft.world.entity.Interaction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin({Interaction.class})
public interface InteractionAccessor {
   @Invoker("setWidth")
   void bedrocknative$setWidth(float var1);

   @Invoker("setHeight")
   void bedrocknative$setHeight(float var1);

   @Invoker("setResponse")
   void bedrocknative$setResponse(boolean var1);
}
