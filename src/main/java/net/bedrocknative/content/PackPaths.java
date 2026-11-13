package net.bedrocknative.content;

public final class PackPaths {
   private PackPaths() {
   }

   public static boolean escapes(String path) {
      if (path.indexOf(58) < 0 && path.indexOf(0) < 0) {
         String clean = path.replace('\\', '/');
         if (clean.startsWith("/")) {
            return true;
         } else {
            for (String segment : clean.split("/", -1)) {
               if (segment.trim().equals("..")) {
                  return true;
               }
            }

            return false;
         }
      } else {
         return true;
      }
   }
}
