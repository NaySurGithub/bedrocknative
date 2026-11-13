package net.bedrocknative.client.mixin.world;

import net.bedrocknative.client.world.ItemSmoothing;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin extends Entity {
   @Shadow
   private int pickupDelay;
   @Shadow
   private int age;

   protected ItemEntityMixin(EntityType<?> type, Level level) {
      super(type, level);
   }

   @Override
   protected InterpolationHandler createInterpolationHandler() {
      return ItemSmoothing.createHandler(this);
   }

   @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
   private void bedrocknative$serverMoves(CallbackInfo ci) {
      ItemEntity self = (ItemEntity)(Object)this;
      if (this.level().isClientSide() && ItemSmoothing.active() && !self.getItem().isEmpty()) {
         this.baseTick();
         if (this.pickupDelay > 0 && this.pickupDelay != 32767) {
            this.pickupDelay--;
         }

         if (this.age != -32768) {
            this.age++;
         }

         ci.cancel();
      }
   }
}
