package net.bedrocknative.client.model;

import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.RenderPipeline.Snippet;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.mixin.model.RenderTypeInvoker;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

final class SeeThroughTypes {
   private static RenderPipeline noCull;
   private static RenderPipeline cull;
   private static final Map<Identifier, RenderType> NO_CULL_TYPES = new HashMap<>();
   private static final Map<Identifier, RenderType> CULL_TYPES = new HashMap<>();

   private SeeThroughTypes() {
   }

   static RenderType renderType(Identifier texture, boolean culled) {
      Map<Identifier, RenderType> types = culled ? CULL_TYPES : NO_CULL_TYPES;
      return types.computeIfAbsent(texture, id -> {
         BedrockNative.LOGGER.info("Bedrock actor material without a depth test: {} drawn through walls{}", id, culled ? " (culled)" : "");
         RenderSetup setup = RenderSetup.builder(pipeline(culled)).withTexture("Sampler0", id).useLightmap().useOverlay().sortOnUpload().createRenderSetup();
         return RenderTypeInvoker.bedrocknative$create(culled ? "bedrocknative_entity_see_through_cull" : "bedrocknative_entity_see_through", setup);
      });
   }

   private static RenderPipeline pipeline(boolean culled) {
      if (culled) {
         if (cull == null) {
            cull = withoutDepth(RenderPipelines.ENTITY_TRANSLUCENT_CULL, "pipeline/entity_see_through_cull");
            ShaderPacks.assignTranslucentEntity(cull);
         }

         return cull;
      } else {
         if (noCull == null) {
            noCull = withoutDepth(RenderPipelines.ENTITY_TRANSLUCENT, "pipeline/entity_see_through");
            ShaderPacks.assignTranslucentEntity(noCull);
         }

         return noCull;
      }
   }

   static RenderPipeline withoutDepth(RenderPipeline base, String location) {
      ColorTargetState[] colours = base.getColorTargetStates().toArray(ColorTargetState[]::new);
      VertexFormat[] formats = base.getVertexFormatBindings().toArray(VertexFormat[]::new);
      Snippet snippet = new Snippet(
         base.getShaders(),
         Optional.of(base.getShaderDefines()),
         Optional.of(base.getBindGroupLayouts()),
         colours,
         colours.length,
         Optional.empty(),
         Optional.of(base.getPolygonMode()),
         Optional.of(base.isCull()),
         formats,
         Optional.of(base.getPrimitiveTopology()),
         base.pushConstantSize()
      );
      return RenderPipeline.builder(snippet)
         .withLocation(Identifier.fromNamespaceAndPath("bedrocknative", location))
         .withDepthStencilState(Optional.empty())
         .build();
   }

   static void clear() {
      NO_CULL_TYPES.clear();
      CULL_TYPES.clear();
   }
}
