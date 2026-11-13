package net.bedrocknative.model.geometry;

import java.util.List;
import org.jspecify.annotations.Nullable;

public record GeometryDef(
   String identifier,
   int formatVersion,
   float textureWidth,
   float textureHeight,
   float visibleBoundsWidth,
   float visibleBoundsHeight,
   float[] visibleBoundsOffset,
   List<BoneDef> bones,
   @Nullable ItemDisplayTransforms itemDisplayTransforms
) {
}
