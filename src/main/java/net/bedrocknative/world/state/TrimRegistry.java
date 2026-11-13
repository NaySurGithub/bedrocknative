package net.bedrocknative.world.state;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.cloudburstmc.protocol.bedrock.data.TrimMaterial;
import org.cloudburstmc.protocol.bedrock.data.TrimPattern;
import org.cloudburstmc.protocol.bedrock.packet.TrimDataPacket;
import org.jspecify.annotations.Nullable;

public final class TrimRegistry {
   private final Map<String, TrimPattern> patternsByItem = new LinkedHashMap<>();
   private final Map<String, TrimMaterial> materialsById = new LinkedHashMap<>();
   private final Map<String, TrimMaterial> materialsByItem = new LinkedHashMap<>();

   public void apply(TrimDataPacket packet) {
      this.clear();

      for (TrimPattern pattern : packet.getPatterns()) {
         if (pattern != null && pattern.getItemName() != null) {
            this.patternsByItem.put(pattern.getItemName(), pattern);
         }
      }

      for (TrimMaterial material : packet.getMaterials()) {
         if (material != null && material.getMaterialId() != null) {
            this.materialsById.put(material.getMaterialId(), material);
            if (material.getItemName() != null) {
               this.materialsByItem.put(material.getItemName(), material);
            }
         }
      }
   }

   @Nullable
   public TrimPattern patternOfTemplate(String itemName) {
      return this.patternsByItem.get(itemName);
   }

   @Nullable
   public TrimMaterial materialOfItem(String itemName) {
      return this.materialsByItem.get(itemName);
   }

   @Nullable
   public TrimMaterial material(String materialId) {
      return this.materialsById.get(materialId);
   }

   public List<TrimPattern> patterns() {
      return List.copyOf(this.patternsByItem.values());
   }

   public List<TrimMaterial> materials() {
      return List.copyOf(this.materialsById.values());
   }

   public void clear() {
      this.patternsByItem.clear();
      this.materialsById.clear();
      this.materialsByItem.clear();
   }
}
