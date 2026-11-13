package net.bedrocknative.model.animation;

public record Keyframe(float time, Vec3Value pre, Vec3Value post, boolean catmullRom, boolean split) {
}
