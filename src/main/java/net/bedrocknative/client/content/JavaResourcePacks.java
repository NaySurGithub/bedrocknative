package net.bedrocknative.client.content;

import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jspecify.annotations.Nullable;

final class JavaResourcePacks {
   private JavaResourcePacks() {
   }

   static boolean isPlayers(Resource resource) {
      return isPlayers(resource.source());
   }

   static boolean isPlayers(PackResources pack) {
      return pack.location().source() == PackSource.DEFAULT;
   }

   static @Nullable Resource below(ResourceManager manager, Identifier file) {
      List<Resource> stack = manager.getResourceStack(file);

      for (int i = stack.size() - 1; i >= 0; i--) {
         if (!isPlayers(stack.get(i))) {
            return stack.get(i);
         }
      }

      return null;
   }
}
