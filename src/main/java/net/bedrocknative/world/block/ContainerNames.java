package net.bedrocknative.world.block;

import net.bedrocknative.text.BedrockText;
import net.minecraft.network.chat.Component;
import org.cloudburstmc.nbt.NbtMap;
import org.jspecify.annotations.Nullable;

public final class ContainerNames {
   public static final long NO_ACTOR = -1L;

   private ContainerNames() {
   }

   @Nullable
   public static String customName(NbtMap blockEntity) {
      String name = blockEntity.getString("CustomName", "");
      return name.isEmpty() ? null : name;
   }

   @Nullable
   public static String pick(@Nullable String opened, @Nullable String partner) {
      return opened != null ? opened : partner;
   }

   public static boolean actorOwned(long actorUniqueId) {
      return actorUniqueId != -1L && actorUniqueId != 0L;
   }

   @Nullable
   public static String opened(long actorUniqueId, @Nullable String actorName, @Nullable String blockEntityName) {
      if (actorOwned(actorUniqueId)) {
         return actorName != null && !actorName.isEmpty() ? actorName : null;
      } else {
         return blockEntityName != null && !blockEntityName.isEmpty() ? blockEntityName : null;
      }
   }

   public static Component title(String customName) {
      return BedrockText.ui(customName);
   }
}
