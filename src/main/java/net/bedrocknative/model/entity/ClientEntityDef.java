package net.bedrocknative.model.entity;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.bedrocknative.molang.MolangProgram;
import org.jspecify.annotations.Nullable;

public record ClientEntityDef(
   String identifier,
   boolean attachable,
   int minEngineVersion,
   int formatVersion,
   Map<String, String> materials,
   Map<String, String> textures,
   Map<String, String> geometries,
   Map<String, String> animations,
   Map<String, String> legacyControllers,
   ClientEntityDef.Scripts scripts,
   List<ClientEntityDef.RenderControllerRef> renderControllers,
   boolean enableAttachables,
   boolean hideArmor,
   @Nullable String queryableGeometry,
   @Nullable Map<String, MolangProgram> items,
   int pack,
   boolean serverPack,
   Map<String, String> particleEffects,
   Map<String, String> soundEffects
) {
   public @Nullable String geometry(String shortName) {
      return lookup(this.geometries, shortName);
   }

   public @Nullable String texture(String shortName) {
      return lookup(this.textures, shortName);
   }

   public @Nullable String material(String shortName) {
      return lookup(this.materials, shortName);
   }

   public @Nullable String animation(String shortName) {
      String id = lookup(this.animations, shortName);
      return id != null ? id : lookup(this.legacyControllers, shortName);
   }

   public @Nullable String particleEffect(String shortName) {
      return lookup(this.particleEffects, shortName);
   }

   public @Nullable String soundEffect(String shortName) {
      return lookup(this.soundEffects, shortName);
   }

   public boolean playsLegacyControllers() {
      return this.scripts.animate().isEmpty() && !this.legacyControllers.isEmpty();
   }

   private static @Nullable String lookup(Map<String, String> map, String key) {
      String value = map.get(key);
      if (value != null) {
         return value;
      }

      for (Entry<String, String> entry : map.entrySet()) {
         if (entry.getKey().equalsIgnoreCase(key)) {
            return entry.getValue();
         }
      }

      return null;
   }

   public record AnimateEntry(String name, @Nullable MolangProgram weight) {
   }

   public record RenderControllerRef(String name, @Nullable MolangProgram condition) {
   }

   public record Scripts(
      @Nullable MolangProgram initialize,
      @Nullable MolangProgram preAnimation,
      List<ClientEntityDef.AnimateEntry> animate,
      @Nullable MolangProgram scale,
      @Nullable MolangProgram scaleX,
      @Nullable MolangProgram scaleY,
      @Nullable MolangProgram scaleZ,
      Set<String> publicVariables,
      @Nullable MolangProgram parentSetup,
      @Nullable MolangProgram shouldUpdateBonesAndEffectsOffscreen,
      @Nullable MolangProgram shouldUpdateEffectsOffscreen,
      @Nullable MolangProgram hideHeldItems
   ) {
   }
}
