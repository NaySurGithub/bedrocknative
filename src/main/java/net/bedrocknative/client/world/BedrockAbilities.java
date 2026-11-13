package net.bedrocknative.client.world;

import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.bedrocknative.client.movement.BedrockMovement;
import net.bedrocknative.client.movement.FreezeDiagnostics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Abilities;
import org.cloudburstmc.protocol.bedrock.data.Ability;
import org.cloudburstmc.protocol.bedrock.data.AbilityLayer;
import org.cloudburstmc.protocol.bedrock.data.AbilityLayer.Type;
import org.cloudburstmc.protocol.bedrock.packet.UpdateAbilitiesPacket;
import org.jspecify.annotations.Nullable;

public final class BedrockAbilities {
   private static final float DEFAULT_FLY_SPEED = 0.05F;
   private static final float DEFAULT_VERTICAL_FLY_SPEED = 1.0F;
   private static final float DEFAULT_WALK_SPEED = 0.1F;

   private BedrockAbilities() {
   }

   public static @Nullable Boolean apply(LocalPlayer player, UpdateAbilitiesPacket packet) {
      List<AbilityLayer> layers = packet.getAbilityLayers()
         .stream()
         .filter(layer -> layer.getLayerType() != null && layer.getLayerType() != Type.CACHE && layer.getLayerType() != Type.SPECTATOR)
         .sorted(Comparator.<AbilityLayer>comparingInt(layer -> layer.getLayerType().ordinal()).reversed())
         .toList();
      Abilities abilities = player.getAbilities();
      List<AbilityLayer> flags = player.isSpectator() ? List.of() : layers;
      set(flags, Ability.MAY_FLY, value -> abilities.mayfly = value);
      set(flags, Ability.FLYING, value -> abilities.flying = value);
      set(flags, Ability.INSTABUILD, value -> abilities.instabuild = value);
      set(flags, Ability.INVULNERABLE, value -> abilities.invulnerable = value);
      set(flags, Ability.BUILD, value -> abilities.mayBuild = value);
      Boolean noClip = flag(flags, Ability.NO_CLIP);
      BedrockMovement.setNoClip(noClip != null && noClip);
      AbilityLayer fly = layer(layers, Ability.FLY_SPEED);
      abilities.setFlyingSpeed(fly == null ? 0.05F : fly.getFlySpeed());
      AbilityLayer vertical = layer(layers, Ability.VERTICAL_FLY_SPEED);
      BedrockMovement.setVerticalFlySpeed(vertical == null ? 1.0F : vertical.getVerticalFlySpeed());
      AbilityLayer walk = layer(layers, Ability.WALK_SPEED);
      abilities.setWalkingSpeed(walk == null ? 0.1F : walk.getWalkSpeed());
      List<String> on = new ArrayList<>();

      for (Ability ability : Ability.values()) {
         if (Boolean.TRUE.equals(flag(layers, ability))
            && ability != Ability.WALK_SPEED
            && ability != Ability.FLY_SPEED
            && ability != Ability.VERTICAL_FLY_SPEED) {
            on.add(ability.name());
         }
      }

      FreezeDiagnostics.abilities(on, abilities.getWalkingSpeed(), abilities.getFlyingSpeed(), BedrockMovement.verticalFlySpeed());
      return flag(layers, Ability.FLYING);
   }

   private static void set(List<AbilityLayer> layers, Ability ability, BooleanConsumer setter) {
      Boolean value = flag(layers, ability);
      if (value != null) {
         setter.accept(value);
      }
   }

   private static @Nullable Boolean flag(List<AbilityLayer> layers, Ability ability) {
      AbilityLayer layer = layer(layers, ability);
      return layer == null ? null : layer.getAbilityValues().contains(ability);
   }

   private static @Nullable AbilityLayer layer(List<AbilityLayer> layers, Ability ability) {
      for (AbilityLayer layer : layers) {
         if (layer.getAbilitiesSet().contains(ability)) {
            return layer;
         }
      }

      return null;
   }
}
