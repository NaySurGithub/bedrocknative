package net.bedrocknative.world.block;

import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtMapBuilder;

public final class JigsawEdits {
   public static final String ID = "JigsawBlock";
   public static final String DEFAULT_NAME = "minecraft:empty";
   public static final String DEFAULT_FINAL_STATE = "minecraft:air";
   public static final String ROLLABLE = "rollable";
   public static final String ALIGNED = "aligned";

   private JigsawEdits() {
   }

   public static JigsawEdits.Data read(NbtMap tag) {
      return new JigsawEdits.Data(
         tag.getString("name", "minecraft:empty"),
         tag.getString("target", "minecraft:empty"),
         tag.getString("target_pool", "minecraft:empty"),
         tag.getString("final_state", "minecraft:air"),
         joint(tag.get("joint")),
         number(tag.get("selection_priority")),
         number(tag.get("placement_priority"))
      );
   }

   static String joint(Object value) {
      if (value instanceof String name) {
         return "aligned".equalsIgnoreCase(name) ? "aligned" : "rollable";
      } else {
         return value instanceof Number number && number.intValue() == 1 ? "aligned" : "rollable";
      }
   }

   private static int number(Object value) {
      return value instanceof Number number ? number.intValue() : 0;
   }

   public static NbtMap write(int x, int y, int z, JigsawEdits.Data data) {
      NbtMapBuilder tag = NbtMap.builder();
      tag.putString("id", "JigsawBlock");
      tag.putInt("x", x);
      tag.putInt("y", y);
      tag.putInt("z", z);
      tag.putString("name", data.name());
      tag.putString("target", data.target());
      tag.putString("target_pool", data.pool());
      tag.putString("final_state", data.finalState());
      tag.putString("joint", data.joint());
      tag.putInt("selection_priority", data.selectionPriority());
      tag.putInt("placement_priority", data.placementPriority());
      return tag.build();
   }

   public record Data(String name, String target, String pool, String finalState, String joint, int selectionPriority, int placementPriority) {
      public static final JigsawEdits.Data DEFAULT = new JigsawEdits.Data(
         "minecraft:empty", "minecraft:empty", "minecraft:empty", "minecraft:air", "rollable", 0, 0
      );
   }
}
