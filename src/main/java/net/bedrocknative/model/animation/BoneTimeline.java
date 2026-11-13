package net.bedrocknative.model.animation;

import org.jspecify.annotations.Nullable;

public record BoneTimeline(
   String bone, String key, @Nullable Keyframe[] rotation, @Nullable Keyframe[] position, @Nullable Keyframe[] scale, boolean relativeToEntity
) {
}
