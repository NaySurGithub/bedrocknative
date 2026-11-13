package net.bedrocknative.camera;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.cloudburstmc.math.vector.Vector2f;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.ControlScheme;
import org.cloudburstmc.protocol.bedrock.data.camera.CameraAudioListener;
import org.cloudburstmc.protocol.bedrock.data.camera.CameraPreset;
import org.cloudburstmc.protocol.common.DefinitionRegistry;
import org.cloudburstmc.protocol.common.NamedDefinition;
import org.cloudburstmc.protocol.common.SimpleDefinitionRegistry;
import org.cloudburstmc.protocol.common.SimpleDefinitionRegistry.Builder;
import org.jspecify.annotations.Nullable;

public final class CameraPresetBook {
   public static final List<CameraPreset> VANILLA = List.of(
      CameraPreset.builder().identifier("minecraft:first_person").applyInheritedStartingRotation(true).build(),
      CameraPreset.builder()
         .identifier("minecraft:fixed_boom")
         .viewOffset(Vector2f.ZERO)
         .entityOffset(Vector3f.ZERO)
         .applyInheritedStartingRotation(true)
         .build(),
      CameraPreset.builder()
         .identifier("minecraft:follow_orbit")
         .viewOffset(Vector2f.ZERO)
         .radius(10.0F)
         .entityOffset(Vector3f.ZERO)
         .applyInheritedStartingRotation(true)
         .build(),
      CameraPreset.builder().identifier("minecraft:free").pos(Vector3f.ZERO).yaw(0.0F).pitch(0.0F).applyInheritedStartingRotation(true).build(),
      CameraPreset.builder().identifier("minecraft:third_person").applyInheritedStartingRotation(true).build(),
      CameraPreset.builder().identifier("minecraft:third_person_front").applyInheritedStartingRotation(true).build()
   );
   private final List<CameraPreset> presets = new ArrayList<>(VANILLA);
   private final Map<String, CameraPreset> byName = new HashMap<>();

   public CameraPresetBook() {
      this.index();
   }

   public static DefinitionRegistry<NamedDefinition> definitions(List<CameraPreset> presets) {
      Builder<NamedDefinition> builder = SimpleDefinitionRegistry.builder();

      for (int i = 0; i < presets.size(); i++) {
         builder.add(new CameraPresetBook.Definition(presets.get(i).getIdentifier(), i));
      }

      return builder.build();
   }

   public void replace(List<CameraPreset> sent) {
      this.presets.clear();
      this.presets.addAll(sent);
      this.index();
   }

   public void reset() {
      this.replace(VANILLA);
   }

   public List<String> names() {
      return this.presets.stream().map(CameraPreset::getIdentifier).toList();
   }

   public CameraPresetBook.@Nullable Resolved resolve(int runtimeId) {
      return runtimeId >= 0 && runtimeId < this.presets.size() ? this.resolve(this.presets.get(runtimeId).getIdentifier()) : null;
   }

   public CameraPresetBook.@Nullable Resolved resolve(String identifier) {
      CameraPreset preset = this.byName.get(identifier);
      if (preset == null) {
         CameraPresetBook.Kind kind = CameraPresetBook.Kind.ofVanilla(identifier);
         return kind == null ? null : new CameraPresetBook.Resolved(identifier, kind, true, null, null, null, null, null, null, null, null, null, null);
      }

      List<CameraPreset> chain = new ArrayList<>();
      Set<String> seen = new HashSet<>();

      for (CameraPreset link = preset; link != null && seen.add(link.getIdentifier()); link = this.byName.get(link.getParentPreset())) {
         chain.add(link);
         if (link.getParentPreset() == null || link.getParentPreset().isEmpty()) {
            break;
         }
      }

      CameraPreset root = chain.getLast();
      CameraPresetBook.Kind kind = CameraPresetBook.Kind.ofVanilla(root.getIdentifier());
      if (kind == null && root.getParentPreset() != null && !root.getParentPreset().isEmpty()) {
         kind = CameraPresetBook.Kind.ofVanilla(root.getParentPreset());
      }

      if (kind == null) {
         kind = root.getPos() == null && root.getYaw() == null ? CameraPresetBook.Kind.FIRST_PERSON : CameraPresetBook.Kind.FREE;
      }

      Vector2f starting = null;

      for (int i = chain.size() - 1; i >= 0; i--) {
         CameraPreset link = chain.get(i);
         if (!link.isApplyInheritedStartingRotation() || starting == null) {
            starting = link.getStartingRotation();
         }
      }

      return new CameraPresetBook.Resolved(
         identifier,
         kind,
         CameraPresetBook.Kind.ofVanilla(identifier) != null,
         first(chain, CameraPreset::getPos),
         first(chain, CameraPreset::getYaw),
         first(chain, CameraPreset::getPitch),
         first(chain, CameraPreset::getViewOffset),
         first(chain, CameraPreset::getEntityOffset),
         first(chain, CameraPreset::getRadius),
         first(chain, CameraPreset::getListener),
         first(chain, CameraPreset::getRotationSpeed),
         starting,
         first(chain, CameraPreset::getControlScheme)
      );
   }

   private static <T> @Nullable T first(List<CameraPreset> chain, Function<CameraPreset, @Nullable T> value) {
      for (CameraPreset link : chain) {
         T found = value.apply(link);
         if (found != null) {
            return found;
         }
      }

      return null;
   }

   private void index() {
      this.byName.clear();

      for (CameraPreset preset : this.presets) {
         this.byName.putIfAbsent(preset.getIdentifier(), preset);
      }
   }

   private record Definition(String identifier, int runtimeId) implements NamedDefinition {
      @Override
      public String getIdentifier() {
         return this.identifier;
      }

      @Override
      public int getRuntimeId() {
         return this.runtimeId;
      }
   }

   public enum Kind {
      FIRST_PERSON,
      THIRD_PERSON,
      THIRD_PERSON_FRONT,
      FREE,
      FIXED_BOOM,
      FOLLOW_ORBIT;

      static CameraPresetBook.@Nullable Kind ofVanilla(String identifier) {
         return switch (identifier) {
            case "minecraft:first_person" -> FIRST_PERSON;
            case "minecraft:third_person" -> THIRD_PERSON;
            case "minecraft:third_person_front" -> THIRD_PERSON_FRONT;
            case "minecraft:free" -> FREE;
            case "minecraft:fixed_boom" -> FIXED_BOOM;
            case "minecraft:follow_orbit", "minecraft:control_scheme_camera" -> FOLLOW_ORBIT;
            default -> null;
         };
      }
   }

   public record Resolved(
      String identifier,
      CameraPresetBook.Kind kind,
      boolean vanilla,
      @Nullable Vector3f position,
      @Nullable Float yaw,
      @Nullable Float pitch,
      @Nullable Vector2f viewOffset,
      @Nullable Vector3f entityOffset,
      @Nullable Float radius,
      @Nullable CameraAudioListener listener,
      @Nullable Float rotationSpeed,
      @Nullable Vector2f startingRotation,
      @Nullable ControlScheme controlScheme
   ) {
   }
}
