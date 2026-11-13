package net.bedrocknative.content.scan;

import java.util.List;
import org.jspecify.annotations.Nullable;

public interface PackLookup {
   @Nullable
   PackLookup.Hit find(PackIndex.Kind var1, String var2);

   static PackLookup layers(final List<PackIndex> topFirst) {
      return new PackLookup() {
         @Nullable
         @Override
         public PackLookup.Hit find(PackIndex.Kind kind, String key) {
            for (PackIndex layer : topFirst) {
               PackLookup.Hit hit = layer.find(kind, key);
               if (hit != null) {
                  return hit;
               }
            }

            return null;
         }

         @Nullable
         @Override
         public String uiNamespace(String file) {
            for (PackIndex layer : topFirst) {
               String namespace = layer.uiNamespace(file);
               if (namespace != null) {
                  return namespace;
               }
            }

            return null;
         }

         @Override
         public String toString() {
            return topFirst.isEmpty() ? "(none)" : topFirst.getLast().name() + (topFirst.size() > 1 ? " + " + (topFirst.size() - 1) + " layers" : "");
         }
      };
   }

   @Nullable
   default String uiNamespace(String file) {
      return null;
   }

   public record Hit(PackIndex pack, String path, @Nullable PackIndex.Def def) {
   }
}
