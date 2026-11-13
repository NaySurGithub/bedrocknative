package net.bedrocknative.model.geometry;

import com.google.gson.JsonObject;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import net.bedrocknative.model.ModelJson;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

public final class ItemDisplayTransforms {
   private static final float[] ZERO = new float[]{0.0F, 0.0F, 0.0F};
   private static final Map<ItemDisplayTransforms.Context, ItemDisplayTransforms.Transform> DEFAULTS = defaults();
   private final Map<ItemDisplayTransforms.Context, ItemDisplayTransforms.Transform> transforms;

   private ItemDisplayTransforms(Map<ItemDisplayTransforms.Context, ItemDisplayTransforms.Transform> transforms) {
      this.transforms = transforms;
   }

   private static Map<ItemDisplayTransforms.Context, ItemDisplayTransforms.Transform> defaults() {
      Map<ItemDisplayTransforms.Context, ItemDisplayTransforms.Transform> defaults = new EnumMap<>(ItemDisplayTransforms.Context.class);
      defaults.put(ItemDisplayTransforms.Context.GUI, transform(ZERO, new float[]{30.0F, 45.0F, 0.0F}, 0.625F, true));
      defaults.put(ItemDisplayTransforms.Context.FIRST_PERSON_RIGHT_HAND, transform(ZERO, new float[]{0.0F, 45.0F, 0.0F}, 0.4F, false));
      defaults.put(ItemDisplayTransforms.Context.FIRST_PERSON_LEFT_HAND, transform(ZERO, new float[]{0.0F, -135.0F, 0.0F}, 0.4F, false));
      defaults.put(
         ItemDisplayTransforms.Context.THIRD_PERSON_RIGHT_HAND, transform(new float[]{0.0F, 2.5F, 0.0F}, new float[]{70.0F, 45.0F, 0.0F}, 0.375F, false)
      );
      defaults.put(
         ItemDisplayTransforms.Context.THIRD_PERSON_LEFT_HAND, transform(new float[]{0.0F, 2.5F, 0.0F}, new float[]{70.0F, 45.0F, 0.0F}, 0.375F, false)
      );
      defaults.put(ItemDisplayTransforms.Context.GROUND, transform(new float[]{0.0F, 3.0F, 0.0F}, ZERO, 0.25F, false));
      defaults.put(ItemDisplayTransforms.Context.FIXED, transform(ZERO, ZERO, 0.5F, false));
      defaults.put(ItemDisplayTransforms.Context.HEAD, transform(ZERO, ZERO, 1.0F, false));
      defaults.put(ItemDisplayTransforms.Context.EMBEDDED, transform(ZERO, ZERO, 0.75F, false));
      defaults.put(ItemDisplayTransforms.Context.SHELF, transform(ZERO, ZERO, 0.5F, false));
      return defaults;
   }

   private static ItemDisplayTransforms.Transform transform(float[] translation, float[] rotation, float scale, boolean fitToFrame) {
      return new ItemDisplayTransforms.Transform(translation, rotation, new float[]{scale, scale, scale}, ZERO, ZERO, fitToFrame);
   }

   public static ItemDisplayTransforms of(@Nullable JsonObject json) {
      Map<ItemDisplayTransforms.Context, ItemDisplayTransforms.Transform> transforms = new EnumMap<>(DEFAULTS);
      if (json != null) {
         for (ItemDisplayTransforms.Context context : ItemDisplayTransforms.Context.values()) {
            JsonObject entry = ModelJson.object(json, context.key());
            if (entry != null) {
               ItemDisplayTransforms.Transform fallback = DEFAULTS.get(context);
               transforms.put(
                  context,
                  new ItemDisplayTransforms.Transform(
                     ModelJson.vec(entry, "translation", fallback.translation()),
                     ModelJson.vec(entry, "rotation", fallback.rotation()),
                     ModelJson.vec(entry, "scale", fallback.scale()),
                     ModelJson.vec(entry, "rotation_pivot", ZERO),
                     ModelJson.vec(entry, "scale_pivot", ZERO),
                     entry.has("fit_to_frame") ? entry.get("fit_to_frame").getAsBoolean() : fallback.fitToFrame()
                  )
               );
            }
         }
      }

      return new ItemDisplayTransforms(transforms);
   }

   public static ItemDisplayTransforms of(GeometryDef geometry) {
      ItemDisplayTransforms parsed = geometry.itemDisplayTransforms();
      return parsed != null ? parsed : of((JsonObject)null);
   }

   public ItemDisplayTransforms.Transform get(ItemDisplayTransforms.Context context) {
      return this.transforms.get(context);
   }

   public static ItemDisplayTransforms.@Nullable Context context(String key) {
      for (ItemDisplayTransforms.Context context : ItemDisplayTransforms.Context.values()) {
         if (context.key.equals(key.toLowerCase(Locale.ROOT))) {
            return context;
         }
      }

      return null;
   }

   public enum Context {
      GUI("gui"),
      FIRST_PERSON_RIGHT_HAND("firstperson_righthand"),
      FIRST_PERSON_LEFT_HAND("firstperson_lefthand"),
      THIRD_PERSON_RIGHT_HAND("thirdperson_righthand"),
      THIRD_PERSON_LEFT_HAND("thirdperson_lefthand"),
      GROUND("ground"),
      FIXED("fixed"),
      HEAD("head"),
      EMBEDDED("embedded"),
      SHELF("shelf");

      private final String key;

      Context(String key) {
         this.key = key;
      }

      public String key() {
         return this.key;
      }

      public boolean leftHand() {
         return this == FIRST_PERSON_LEFT_HAND || this == THIRD_PERSON_LEFT_HAND;
      }
   }

   public record Transform(float[] translation, float[] rotation, float[] scale, float[] rotationPivot, float[] scalePivot, boolean fitToFrame) {
      public Matrix4f matrix(boolean leftHand) {
         float mirror = leftHand ? -1.0F : 1.0F;
         Matrix4f matrix = new Matrix4f().translate(mirror * this.translation[0], this.translation[1], this.translation[2]);
         matrix.translate(mirror * this.rotationPivot[0] * 16.0F, this.rotationPivot[1] * 16.0F, this.rotationPivot[2] * 16.0F);
         matrix.rotateXYZ(
            (float)Math.toRadians(this.rotation[0]), (float)Math.toRadians(mirror * this.rotation[1]), (float)Math.toRadians(mirror * this.rotation[2])
         );
         matrix.translate(-mirror * this.rotationPivot[0] * 16.0F, -this.rotationPivot[1] * 16.0F, -this.rotationPivot[2] * 16.0F);
         matrix.translate(mirror * this.scalePivot[0] * 16.0F, this.scalePivot[1] * 16.0F, this.scalePivot[2] * 16.0F);
         matrix.scale(this.scale[0], this.scale[1], this.scale[2]);
         matrix.translate(-mirror * this.scalePivot[0] * 16.0F, -this.scalePivot[1] * 16.0F, -this.scalePivot[2] * 16.0F);
         return matrix;
      }
   }
}
