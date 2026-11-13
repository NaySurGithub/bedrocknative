package net.bedrocknative.world.block.custom;

import com.google.gson.JsonObject;
import net.bedrocknative.model.geometry.ItemDisplayTransforms;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

public final class BlockItemDisplay {
   private static final Matrix4f TO_BLOCKS = new Matrix4f().scaling(0.0625F);
   private static final Matrix4f TO_PIXELS = new Matrix4f().scaling(16.0F);
   private final ItemDisplayTransforms transforms;
   private static final ItemDisplayTransforms DEFAULTS = ItemDisplayTransforms.of((JsonObject)null);
   private final Matrix4fc[] cached = new Matrix4fc[ItemDisplayContext.values().length];

   private BlockItemDisplay(ItemDisplayTransforms transforms) {
      this.transforms = transforms;
   }

   public static BlockItemDisplay of(JsonObject itemDisplayTransforms) {
      return new BlockItemDisplay(ItemDisplayTransforms.of(itemDisplayTransforms));
   }

   @Nullable
   public static ItemDisplayTransforms.Context context(ItemDisplayContext context) {
      return switch (context) {
         case GUI -> ItemDisplayTransforms.Context.GUI;
         case FIRST_PERSON_RIGHT_HAND -> ItemDisplayTransforms.Context.FIRST_PERSON_RIGHT_HAND;
         case FIRST_PERSON_LEFT_HAND -> ItemDisplayTransforms.Context.FIRST_PERSON_LEFT_HAND;
         case THIRD_PERSON_RIGHT_HAND -> ItemDisplayTransforms.Context.THIRD_PERSON_RIGHT_HAND;
         case THIRD_PERSON_LEFT_HAND -> ItemDisplayTransforms.Context.THIRD_PERSON_LEFT_HAND;
         case GROUND -> ItemDisplayTransforms.Context.GROUND;
         case FIXED -> ItemDisplayTransforms.Context.FIXED;
         case HEAD -> ItemDisplayTransforms.Context.HEAD;
         case ON_SHELF -> ItemDisplayTransforms.Context.SHELF;
         case NONE -> null;
         default -> throw new MatchException(null, null);
      };
   }

   public ItemDisplayTransforms.Transform transform(ItemDisplayTransforms.Context context) {
      return this.transforms.get(context);
   }

   public Matrix4fc cachedLocal(ItemDisplayContext javaContext, BlockItemDisplay.JavaTransform java, @Nullable Vector3fc[] extents) {
      Matrix4fc local = this.cached[javaContext.ordinal()];
      if (local == null) {
         local = this.local(javaContext, java, extents);
         this.cached[javaContext.ordinal()] = local;
      }

      return local;
   }

   public Matrix4f local(ItemDisplayContext javaContext, BlockItemDisplay.JavaTransform java, @Nullable Vector3fc[] extents) {
      ItemDisplayTransforms.Context context = context(javaContext);
      if (context == null) {
         return new Matrix4f();
      } else {
         boolean leftHand = javaContext.leftHand();
         Matrix4f change = new Matrix4f(TO_BLOCKS)
            .mul(this.transforms.get(context).matrix(leftHand))
            .mul(new Matrix4f(DEFAULTS.get(context).matrix(leftHand)).invert())
            .mul(TO_PIXELS);
         Matrix4f javaFull = javaMatrix(java, leftHand);
         Matrix4f local = new Matrix4f(javaFull).invert().mul(change).mul(javaFull);
         if (context == ItemDisplayTransforms.Context.GUI && this.transforms.get(context).fitToFrame() && extents != null && extents.length > 0) {
            local = fit(javaMatrix(java, false), local, extents);
         }

         return local;
      }
   }

   public static Matrix4f javaMatrix(BlockItemDisplay.JavaTransform java, boolean leftHand) {
      float mirror = leftHand ? -1.0F : 1.0F;
      return new Matrix4f()
         .translation(mirror * java.translation().x(), java.translation().y(), java.translation().z())
         .rotateXYZ(
            (float)Math.toRadians(java.rotation().x()),
            (float)Math.toRadians(mirror * java.rotation().y()),
            (float)Math.toRadians(mirror * java.rotation().z())
         )
         .scale(java.scale().x(), java.scale().y(), java.scale().z())
         .translate(-0.5F, -0.5F, -0.5F);
   }

   static Matrix4f fit(Matrix4f java, Matrix4f local, Vector3fc[] extents) {
      Matrix4f drawn = new Matrix4f(java).mul(local);
      float[] bounds = bounds(drawn, extents);
      float[] frame = bounds(java, cubeCorners());
      float width = bounds[2] - bounds[0];
      float height = bounds[3] - bounds[1];
      if (!(width <= 1.0E-6F) && !(height <= 1.0E-6F)) {
         float scale = Math.min((frame[2] - frame[0]) / width, (frame[3] - frame[1]) / height);
         float frameX = (frame[0] + frame[2]) / 2.0F;
         float frameY = (frame[1] + frame[3]) / 2.0F;
         float drawnX = (bounds[0] + bounds[2]) / 2.0F;
         float drawnY = (bounds[1] + bounds[3]) / 2.0F;
         Matrix4f screen = new Matrix4f().translation(frameX - scale * drawnX, frameY - scale * drawnY, 0.0F).scale(scale);
         return new Matrix4f(java).invert().mul(screen).mul(drawn);
      } else {
         return local;
      }
   }

   private static float[] bounds(Matrix4f matrix, Vector3fc[] points) {
      float[] bounds = new float[]{Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
      Vector3f scratch = new Vector3f();

      for (Vector3fc point : points) {
         matrix.transformPosition(point, scratch);
         bounds[0] = Math.min(bounds[0], scratch.x);
         bounds[1] = Math.min(bounds[1], scratch.y);
         bounds[2] = Math.max(bounds[2], scratch.x);
         bounds[3] = Math.max(bounds[3], scratch.y);
      }

      return bounds;
   }

   private static Vector3fc[] cubeCorners() {
      Vector3fc[] corners = new Vector3fc[8];

      for (int i = 0; i < 8; i++) {
         corners[i] = new Vector3f(i & 1, i >> 1 & 1, i >> 2 & 1);
      }

      return corners;
   }

   public record JavaTransform(Vector3fc rotation, Vector3fc translation, Vector3fc scale) {
   }
}
