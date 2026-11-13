package net.bedrocknative.model.animation;

import org.jspecify.annotations.Nullable;

public record BoneTimeline(
   String bone, String key, Keyframe @Nullable [] rotation, Keyframe @Nullable [] position, Keyframe @Nullable [] scale, boolean relativeToEntity
) {
}
