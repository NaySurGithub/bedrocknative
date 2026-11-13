package net.bedrocknative.content;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public final class JavaLatinFont implements PackSource {
   public static final JavaLatinFont INSTANCE = new JavaLatinFont();
   public static final String PATH = "font/default8.png";
   static final String JAVA_PAGE = "assets/minecraft/textures/font/ascii.png";
   @Nullable
   private volatile byte[] page;
   private volatile boolean missing;

   private JavaLatinFont() {
   }

   @Override
   public Optional<byte[]> read(String path) throws IOException {
      if (!"font/default8.png".equalsIgnoreCase(PackStack.normalize(path))) {
         return Optional.empty();
      } else {
         byte[] bytes = this.page;
         if (bytes == null && !this.missing) {
            Optional var4;
            try (InputStream in = JavaLatinFont.class.getClassLoader().getResourceAsStream("assets/minecraft/textures/font/ascii.png")) {
               if (in != null) {
                  bytes = in.readAllBytes();
                  this.page = bytes;
                  return Optional.ofNullable(bytes);
               }

               this.missing = true;
               var4 = Optional.empty();
            }

            return var4;
         } else {
            return Optional.ofNullable(bytes);
         }
      }
   }

   @Override
   public String toString() {
      return "java-font:assets/minecraft/textures/font/ascii.png";
   }
}
