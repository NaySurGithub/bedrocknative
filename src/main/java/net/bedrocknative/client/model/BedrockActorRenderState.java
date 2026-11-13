package net.bedrocknative.client.model;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.jspecify.annotations.Nullable;

public final class BedrockActorRenderState extends LivingEntityRenderState {
   public @Nullable ActorSnapshot snapshot;
   public float shadow;
}
