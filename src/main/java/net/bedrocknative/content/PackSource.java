package net.bedrocknative.content;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public interface PackSource extends AutoCloseable {
   Optional<byte[]> read(String var1) throws IOException;

   default boolean has(String path) throws IOException {
      return this.read(path).isPresent();
   }

   default List<String> list(String folder) {
      return List.of();
   }

   default List<String> listNames(String folder) throws IOException {
      return List.of();
   }

   @Override
   default void close() {
   }
}
