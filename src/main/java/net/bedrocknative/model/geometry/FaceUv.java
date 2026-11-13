package net.bedrocknative.model.geometry;

import org.jspecify.annotations.Nullable;

public record FaceUv(float u, float v, float uSize, float vSize, int rotation, @Nullable String materialInstance) {
}
