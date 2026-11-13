package net.bedrocknative.model.geometry;

import java.util.Map;
import org.jspecify.annotations.Nullable;

public record CubeDef(
   float[] origin,
   float[] size,
   float[] rotation,
   @Nullable float[] pivot,
   float inflate,
   @Nullable Boolean mirror,
   @Nullable float[] boxUv,
   @Nullable Map<Face, FaceUv> faces
) {
}
