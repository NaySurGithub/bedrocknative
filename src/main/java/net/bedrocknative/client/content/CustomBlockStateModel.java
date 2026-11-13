package net.bedrocknative.client.content;

import java.util.List;
import java.util.function.Predicate;
import net.bedrocknative.world.block.custom.BlockCullingRules;
import net.bedrocknative.world.block.custom.CarrierBlock;
import net.bedrocknative.world.block.custom.CustomBlockState;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.ShadeMode;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.BakedQuad.MaterialFlags;
import net.minecraft.client.resources.model.sprite.Material.Baked;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;
import net.minecraft.util.LightCoordsUtil.BrightnessGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

final class CustomBlockStateModel implements BlockStateModel {
   private final CustomBlockStateModel.Quad[] quads;
   private final BlockStateModelPart part;
   private final String block;
   private final @Nullable String layer;

   CustomBlockStateModel(CustomBlockStateModel.Quad[] quads, BlockStateModelPart part, String block, @Nullable String layer) {
      this.quads = quads;
      this.part = part;
      this.block = block;
      this.layer = layer;
   }

   @Override
   public void collectParts(RandomSource random, List<BlockStateModelPart> output) {
      output.add(this.part);
   }

   @Override
   public Baked particleMaterial() {
      return this.part.particleMaterial();
   }

   @Override
   public @MaterialFlags int materialFlags() {
      return this.part.materialFlags();
   }

   int quadCount() {
      return this.quads.length;
   }

   public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, Predicate<Direction> cullTest) {
      MutableBlockPos neighbourPos = null;

      for (CustomBlockStateModel.Quad quad : this.quads) {
         Direction cullFace = quad.cullFace();
         if (cullFace == null || !cullTest.test(cullFace)) {
            if (!quad.checks().isEmpty()) {
               if (neighbourPos == null) {
                  neighbourPos = new MutableBlockPos();
               }

               if (this.culled(quad.checks(), level, pos, neighbourPos, state, cullTest)) {
                  continue;
               }
            }

            emitter.cullFace(cullFace);
            emitter.fromBakedQuad(quad.baked());
            Direction front = quad.backOf();
            if (front != null) {
               if (neighbourPos == null) {
                  neighbourPos = new MutableBlockPos();
               }

               int light = LightCoordsUtil.max(
                  LightCoordsUtil.getLightCoords(BrightnessGetter.DEFAULT, level, state, pos),
                  LightCoordsUtil.getLightCoords(BrightnessGetter.DEFAULT, level, state, neighbourPos.setWithOffset(pos, front))
               );
               emitter.lightmap(light, light, light, light);
               emitter.ambientOcclusion(TriState.FALSE);
            } else {
               emitter.ambientOcclusion(quad.ambientOcclusion() ? TriState.DEFAULT : TriState.FALSE);
            }

            emitter.shadeMode(ShadeMode.VANILLA);
            emitter.emit();
         }
      }
   }

   private boolean culled(
      List<BlockCullingRules.Check> checks,
      BlockAndTintGetter level,
      BlockPos pos,
      MutableBlockPos neighbourPos,
      BlockState state,
      Predicate<Direction> cullTest
   ) {
      for (BlockCullingRules.Check check : checks) {
         BlockState neighbour = level.getBlockState(neighbourPos.setWithOffset(pos, check.direction()));
         if (check.culls(neighbour(state, neighbour, check.direction(), cullTest), this.block, this.layer)) {
            return true;
         }
      }

      return false;
   }

   static BlockCullingRules.Neighbour neighbour(BlockState state, BlockState neighbour, Direction direction, Predicate<Direction> cullTest) {
      CustomBlockState custom = CarrierBlock.data(neighbour);
      return new BlockCullingRules.Neighbour(
         cullTest.test(direction), custom != null ? custom.state().name() : null, neighbour == state, custom != null ? custom.geometry().cullingLayer() : null
      );
   }

   record Quad(BakedQuad baked, @Nullable Direction cullFace, boolean ambientOcclusion, List<BlockCullingRules.Check> checks, @Nullable Direction backOf) {
   }
}
