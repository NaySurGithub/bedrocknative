package net.bedrocknative.model.geometry;

public record TextureMeshDef(String texture, float[] position, float[] localPivot, float[] rotation, float[] scale, boolean usePixelDepth) {
}
