package net.bedrocknative.world.block;

import java.util.ArrayList;
import java.util.List;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtMapBuilder;
import org.jspecify.annotations.Nullable;

public final class SignEdits {
   public static final int LINES = 4;

   private SignEdits() {
   }

   public static List<String> lines(String text) {
      List<String> lines = new ArrayList<>(4);
      String[] parts = text.split("\n", -1);

      for (int i = 0; i < 4; i++) {
         lines.add(i < parts.length ? parts[i] : "");
      }

      return lines;
   }

   public static String text(List<String> lines) {
      int end = Math.min(lines.size(), 4);

      while (end > 0 && lines.get(end - 1).isEmpty()) {
         end--;
      }

      return String.join("\n", lines.subList(0, end));
   }

   public static boolean waxed(NbtMap tag) {
      return tag.getBoolean("IsWaxed", false);
   }

   public static String sideText(NbtMap tag, boolean front) {
      return tag.getCompound(front ? "FrontText" : "BackText", NbtMap.EMPTY).getString("Text", "");
   }

   public static NbtMap edited(@Nullable NbtMap last, String id, int x, int y, int z, boolean front, List<String> lines) {
      NbtMap base = last != null ? last : fresh(id, x, y, z);
      String key = front ? "FrontText" : "BackText";
      NbtMap side = base.getCompound(key, NbtMap.EMPTY);
      NbtMap changed = side.toBuilder().putString("Text", text(lines)).build();
      return base.toBuilder().putCompound(key, changed).build();
   }

   public static NbtMap fresh(String id, int x, int y, int z) {
      NbtMapBuilder builder = NbtMap.builder();
      builder.putCompound("BackText", side());
      builder.putCompound("FrontText", side());
      builder.putInt("BlockEntityVersion", 0);
      builder.putByte("IsWaxed", (byte)0);
      builder.putLong("LockedForEditingBy", -1L);
      builder.putString("id", id);
      builder.putInt("x", x);
      builder.putInt("y", y);
      builder.putInt("z", z);
      return builder.build();
   }

   private static NbtMap side() {
      return NbtMap.builder()
         .putString("FilteredText", "")
         .putByte("HideGlowOutline", (byte)0)
         .putByte("IgnoreLighting", (byte)0)
         .putByte("PersistFormatting", (byte)1)
         .putInt("SignTextColor", -16777216)
         .putString("Text", "")
         .putString("TextOwner", "")
         .build();
   }
}
