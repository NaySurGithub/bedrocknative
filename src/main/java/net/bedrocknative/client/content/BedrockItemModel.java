package net.bedrocknative.client.content;

import com.mojang.serialization.MapCodec;
import net.bedrocknative.client.model.FirstPersonAttachableRenderer;
import net.bedrocknative.client.model.WornAttachables;
import net.bedrocknative.world.block.custom.BlockItemDisplay;
import net.bedrocknative.world.item.CustomItem;
import net.bedrocknative.world.item.ItemMappings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemModels;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.MissingItemModel;
import net.minecraft.client.renderer.item.ModelRenderProperties;
import net.minecraft.client.renderer.item.ItemModel.BakingContext;
import net.minecraft.client.renderer.item.ItemStackRenderState.FoilType;
import net.minecraft.client.renderer.item.ItemStackRenderState.LayerRenderState;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.client.resources.model.ResolvableModel.Resolver;
import net.minecraft.client.resources.model.cuboid.ItemTransform;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.jspecify.annotations.Nullable;

public final class BedrockItemModel implements ItemModel {
   public static final Identifier TYPE = Identifier.fromNamespaceAndPath("bedrocknative", "bedrock");
   private static final Identifier GENERATED = Identifier.withDefaultNamespace("item/generated");
   private static final Identifier HANDHELD = Identifier.withDefaultNamespace("item/handheld");
   private static final Identifier BLOCK = Identifier.withDefaultNamespace("block/block");
   private final ModelRenderProperties generated;
   private final ModelRenderProperties handheld;
   private final ModelRenderProperties block;
   private final MissingItemModel missing;
   private final Matrix4fc transformation;

   private BedrockItemModel(
      ModelRenderProperties generated, ModelRenderProperties handheld, ModelRenderProperties block, MissingItemModel missing, Matrix4fc transformation
   ) {
      this.generated = generated;
      this.handheld = handheld;
      this.block = block;
      this.missing = missing;
      this.transformation = new Matrix4f(transformation);
   }

   public static void register() {
      ItemModels.ID_MAPPER.put(TYPE, BedrockItemModel.Unbaked.MAP_CODEC);
   }

   @Override
   public void update(
      ItemStackRenderState output,
      ItemStack stack,
      ItemModelResolver resolver,
      ItemDisplayContext displayContext,
      @Nullable ClientLevel level,
      @Nullable ItemOwner owner,
      int seed
   ) {
      CustomItem item = ItemMappings.custom(stack);
      if (item == null || displayContext != ItemDisplayContext.HEAD || item.wearSlot() != EquipmentSlot.HEAD) {
         if (item == null || item.wearSlot() != null || !held(output, stack, displayContext, owner)) {
            BedrockBlockModels.ItemLook look = item != null && item.block() != null && item.icon() == null ? BedrockBlockModels.item(item.block()) : null;
            if (look != null) {
               output.appendModelIdentityElement(look);
               LayerRenderState layer = output.newLayer();
               if (stack.hasFoil()) {
                  layer.setFoilType(FoilType.STANDARD);
                  output.setAnimated();
                  output.appendModelIdentityElement(FoilType.STANDARD);
               }

               layer.setExtents(look.extents());
               this.block.applyToLayer(layer, displayContext);
               if (look.display() != null) {
                  ItemTransform java = this.block.transforms().getTransform(displayContext);
                  Matrix4fc local = look.display()
                     .cachedLocal(displayContext, new BlockItemDisplay.JavaTransform(java.rotation(), java.translation(), java.scale()), look.points());
                  layer.setLocalTransform(new Matrix4f(this.transformation).mul(local));
               } else {
                  layer.setLocalTransform(this.transformation);
               }

               layer.setQuads(look.quads());
            } else {
               BedrockItemIcons.Icon icon = item == null ? null : BedrockItemIcons.icon(item.identifier());
               if (icon == null) {
                  this.missing.update(output, stack, resolver, displayContext, level, owner, seed);
               } else {
                  output.appendModelIdentityElement(icon);
                  LayerRenderState layer = output.newLayer();
                  if (stack.hasFoil()) {
                     layer.setFoilType(FoilType.STANDARD);
                     output.setAnimated();
                     output.appendModelIdentityElement(FoilType.STANDARD);
                  }

                  layer.setExtents(icon.extents());
                  layer.setLocalTransform(this.transformation);
                  ModelRenderProperties properties = icon.cube() ? this.block : (item.handEquipped() ? this.handheld : this.generated);
                  properties.applyToLayer(layer, displayContext);
                  layer.setQuads(icon.quads());
                  if (icon.animated()) {
                     output.setAnimated();
                  }
               }
            }
         }
      }
   }

   private static boolean held(ItemStackRenderState output, ItemStack stack, ItemDisplayContext context, @Nullable ItemOwner owner) {
      boolean firstPerson = context.firstPerson();
      if (!firstPerson && context != ItemDisplayContext.THIRD_PERSON_LEFT_HAND && context != ItemDisplayContext.THIRD_PERSON_RIGHT_HAND) {
         return false;
      }

      LivingEntity living = owner != null ? owner.asLivingEntity() : null;
      if (living != null && (firstPerson || WornAttachables.drawsHeld(living.getType()))) {
         HumanoidArm arm = context.leftHand() ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
         WornAttachables.Slot slot = arm == living.getMainArm() ? WornAttachables.Slot.MAIN_HAND : WornAttachables.Slot.OFF_HAND;
         float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
         WornAttachables.Frame frame = WornAttachables.extractHeld(living, slot, stack, firstPerson, arm == HumanoidArm.RIGHT, partialTick);
         if (frame == null) {
            return false;
         }

         if (firstPerson) {
            output.newLayer().setupSpecialModel(FirstPersonAttachableRenderer.INSTANCE, frame);
         } else {
            output.setData(WornAttachables.HELD, frame);
         }

         return true;
      } else {
         return false;
      }
   }

   public record Unbaked() implements net.minecraft.client.renderer.item.ItemModel.Unbaked {
      public static final MapCodec<BedrockItemModel.Unbaked> MAP_CODEC = MapCodec.unit(BedrockItemModel.Unbaked::new);

      @Override
      public MapCodec<BedrockItemModel.Unbaked> type() {
         return MAP_CODEC;
      }

      @Override
      public void resolveDependencies(Resolver resolver) {
         resolver.markDependency(BedrockItemModel.GENERATED);
         resolver.markDependency(BedrockItemModel.HANDHELD);
         resolver.markDependency(BedrockItemModel.BLOCK);
      }

      @Override
      public ItemModel bake(BakingContext context, Matrix4fc transformation) {
         ModelBaker baker = context.blockModelBaker();
         return new BedrockItemModel(
            properties(baker, BedrockItemModel.GENERATED),
            properties(baker, BedrockItemModel.HANDHELD),
            properties(baker, BedrockItemModel.BLOCK),
            context.missingItemModel(transformation),
            transformation
         );
      }

      private static ModelRenderProperties properties(ModelBaker baker, Identifier model) {
         ResolvedModel resolved = baker.getModel(model);
         return ModelRenderProperties.fromResolvedModel(baker, resolved, resolved.getTopTextureSlots());
      }
   }
}
