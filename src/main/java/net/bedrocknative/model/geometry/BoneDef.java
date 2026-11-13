package net.bedrocknative.model.geometry;

import java.util.List;
import org.jspecify.annotations.Nullable;

public record BoneDef(
   String name,
   @Nullable String parent,
   float[] pivot,
   float[] rotation,
   float @Nullable [] bindPoseRotation,
   boolean mirror,
   float inflate,
   boolean neverRender,
   @Nullable String binding,
   List<CubeDef> cubes,
   List<LocatorDef> locators,
   @Nullable PolyMeshDef polyMesh,
   List<TextureMeshDef> textureMeshes,
   int renderGroupId
) {
}
