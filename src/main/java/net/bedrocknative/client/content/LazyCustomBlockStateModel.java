package net.bedrocknative.client.content;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.bedrocknative.world.block.custom.CustomBlockLooks;
import net.bedrocknative.world.block.custom.CustomBlockState;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad.MaterialFlags;
import net.minecraft.client.resources.model.sprite.Material.Baked;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

final class LazyCustomBlockStateModel implements BlockStateModel {
   private final CustomBlockState state;
   private final CustomBlockLooks.Look look;
   private final Map<String, BedrockBlockModels.Placed> placed;
   private final TextureAtlasSprite missing;
   @Nullable
   private CustomBlockStateModel delegate;

   LazyCustomBlockStateModel(CustomBlockState state, CustomBlockLooks.Look look, Map<String, BedrockBlockModels.Placed> placed, TextureAtlasSprite missing) {
      this.state = state;
      this.look = look;
      this.placed = placed;
      this.missing = missing;
   }

   private synchronized CustomBlockStateModel model() {
      CustomBlockStateModel built = this.delegate;
      if (built == null) {
         built = (CustomBlockStateModel)BedrockBlockModels.model(this.state, this.look, this.placed, this.missing);
         this.look.releaseQuads();
         this.delegate = built;
      }

      return built;
   }

   public void collectParts(RandomSource random, List<BlockStateModelPart> output) {
      this.model().collectParts(random, output);
   }

   public Baked particleMaterial() {
      return this.model().particleMaterial();
   }

   @MaterialFlags
   public int materialFlags() {
      return this.model().materialFlags();
   }

   public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, Predicate<Direction> cullTest) {
      this.model().emitQuads(emitter, level, pos, state, random, cullTest);
   }
}
