package net.bedrocknative.model.geometry;

public record PolyMeshDef(boolean normalizedUvs, float[] positions, float[] normals, float[] uvs, int[][] polygons) {
}
