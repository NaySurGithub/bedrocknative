package net.bedrocknative.client.model;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.jspecify.annotations.Nullable;

public final class BedrockActorRenderState extends LivingEntityRenderState {
   @Nullable
   public ActorSnapshot snapshot;
   public float shadow;
}
