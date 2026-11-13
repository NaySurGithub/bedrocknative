package net.bedrocknative.content;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public final class JavaLatinFont implements PackSource {
   public static final JavaLatinFont INSTANCE = new JavaLatinFont();
   public static final String PATH = "font/default8.png";
   static final String JAVA_PAGE = "assets/minecraft/textures/font/ascii.png";
   private volatile byte @Nullable [] page;
   private volatile boolean missing;

   private JavaLatinFont() {
   }

   @Override
   public Optional<byte[]> read(String path) throws IOException {
      if (!"font/default8.png".equalsIgnoreCase(PackStack.normalize(path))) {
         return Optional.empty();
      }

      byte[] bytes = this.page;
      if (bytes == null && !this.missing) {
         try (InputStream in = JavaLatinFont.class.getClassLoader().getResourceAsStream("assets/minecraft/textures/font/ascii.png")) {
            if (in == null) {
               this.missing = true;
               return Optional.empty();
            }

            bytes = in.readAllBytes();
            this.page = bytes;
         }
      }

      return Optional.ofNullable(bytes);
   }

   @Override
   public String toString() {
      return "java-font:assets/minecraft/textures/font/ascii.png";
   }
}
