package net.bedrocknative.client.model;

import net.bedrocknative.model.runtime.JavaSkeleton;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import org.jspecify.annotations.Nullable;

final class JavaSkeletonAdapter {
   private final JavaSkeleton skeleton = new JavaSkeleton();

   JavaSkeleton pose(HumanoidModel<?> model) {
      this.skeleton.clear();
      this.part(JavaSkeleton.Part.ROOT, model.root());
      this.part(JavaSkeleton.Part.HEAD, model.head);
      this.part(JavaSkeleton.Part.HAT, model.hat);
      this.part(JavaSkeleton.Part.BODY, model.body);
      this.part(JavaSkeleton.Part.RIGHT_ARM, model.rightArm);
      this.part(JavaSkeleton.Part.LEFT_ARM, model.leftArm);
      this.part(JavaSkeleton.Part.RIGHT_LEG, model.rightLeg);
      this.part(JavaSkeleton.Part.LEFT_LEG, model.leftLeg);
      if (model instanceof PlayerModel player) {
         this.part(JavaSkeleton.Part.JACKET, player.jacket);
         this.part(JavaSkeleton.Part.RIGHT_SLEEVE, player.rightSleeve);
         this.part(JavaSkeleton.Part.LEFT_SLEEVE, player.leftSleeve);
         this.part(JavaSkeleton.Part.RIGHT_PANTS, player.rightPants);
         this.part(JavaSkeleton.Part.LEFT_PANTS, player.leftPants);
      }

      this.skeleton.compute();
      return this.skeleton;
   }

   JavaSkeleton last() {
      return this.skeleton;
   }

   private void part(JavaSkeleton.Part part, @Nullable ModelPart model) {
      if (model != null) {
         this.skeleton.setPart(part, model.x, model.y, model.z, model.xRot, model.yRot, model.zRot, model.xScale, model.yScale, model.zScale);
      }
   }
}
