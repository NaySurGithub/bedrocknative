package net.bedrocknative.content;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

public final class BundledPack implements PackSource {
   private final String root;
   private final ClassLoader loader;

   public BundledPack(String root) {
      this(root, BundledPack.class.getClassLoader());
   }

   BundledPack(String root, ClassLoader loader) {
      this.root = root.endsWith("/") ? root.substring(0, root.length() - 1) : root;
      this.loader = loader;
   }

   @Override
   public Optional<byte[]> read(String path) throws IOException {
      String normalized = PackStack.normalize(path);
      if (normalized == null) {
         return Optional.empty();
      } else {
         Optional var4;
         try (InputStream in = this.loader.getResourceAsStream(this.root + "/" + normalized)) {
            var4 = in == null ? Optional.empty() : Optional.of(in.readAllBytes());
         }

         return var4;
      }
   }

   @Override
   public String toString() {
      return "bundled:" + this.root;
   }
}
