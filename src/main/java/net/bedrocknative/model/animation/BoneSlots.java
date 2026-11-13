package net.bedrocknative.model.animation;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.bedrocknative.model.geometry.MeshedGeometry;

public final class BoneSlots {
   private final Map<String, Integer> slots = new HashMap<>();

   public int slot(String name) {
      return this.slots.computeIfAbsent(name.toLowerCase(Locale.ROOT), key -> this.slots.size());
   }

   public int find(String name) {
      return this.slots.getOrDefault(name.toLowerCase(Locale.ROOT), -1);
   }

   public int size() {
      return this.slots.size();
   }

   public void addAll(MeshedGeometry geometry) {
      for (int i = 0; i < geometry.boneCount(); i++) {
         this.slot(geometry.bone(i).key());
      }
   }

   public int[] map(MeshedGeometry geometry) {
      int[] map = new int[geometry.boneCount()];

      for (int i = 0; i < map.length; i++) {
         map[i] = this.slot(geometry.bone(i).key());
      }

      return map;
   }

   public int[] map(AnimationDef animation) {
      List<BoneTimeline> bones = animation.bones();
      int[] map = new int[bones.size()];
      Arrays.fill(map, -1);

      for (int i = 0; i < map.length; i++) {
         map[i] = this.find(bones.get(i).key());
      }

      return map;
   }
}
