package net.bedrocknative.model.geometry;

import java.util.Map;
import org.jspecify.annotations.Nullable;

public record CubeDef(
   float[] origin,
   float[] size,
   float[] rotation,
   float @Nullable [] pivot,
   float inflate,
   @Nullable Boolean mirror,
   float @Nullable [] boxUv,
   @Nullable Map<Face, FaceUv> faces
) {
}
