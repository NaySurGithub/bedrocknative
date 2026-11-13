package net.bedrocknative.content.base;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

final class BaseStore {
   private static final AtomicLong PARTS = new AtomicLong();
   private final Path folder;

   BaseStore(Path folder) {
      this.folder = folder;
   }

   Path folder() {
      return this.folder;
   }

   Path file(BaseTable.Entry entry) {
      return this.folder.resolve(entry.path());
   }

   boolean present(BaseTable.Entry entry) {
      try {
         Path file = this.file(entry);
         return Files.isRegularFile(file) && Files.size(file) == entry.size();
      } catch (RuntimeException | IOException var3) {
         return false;
      }
   }

   boolean verified(BaseTable.Entry entry) {
      try {
         Path file = this.file(entry);
         return Files.isRegularFile(file) && Files.size(file) == entry.size() ? BaseTable.blobSha1(Files.readAllBytes(file)).equals(entry.sha1()) : false;
      } catch (RuntimeException | IOException var3) {
         return false;
      }
   }

   List<BaseTable.Entry> missing(Collection<BaseTable.Entry> entries) {
      List<BaseTable.Entry> missing = new ArrayList<>();

      for (BaseTable.Entry entry : entries) {
         if (!this.present(entry)) {
            missing.add(entry);
         }
      }

      return missing;
   }

   void write(BaseTable.Entry entry, byte[] data, int length) throws IOException {
      if (length != entry.size()) {
         throw new BaseStore.Mismatch("size " + length + " instead of " + entry.size());
      } else {
         String sha1 = BaseTable.blobSha1(data, length);
         if (!sha1.equals(entry.sha1())) {
            throw new BaseStore.Mismatch("hash " + sha1 + " instead of " + entry.sha1());
         } else {
            Path file = this.file(entry);
            if (!file.normalize().startsWith(this.folder.normalize())) {
               throw new IOException("path leaves the base folder");
            } else {
               Files.createDirectories(file.getParent());
               Path part = file.resolveSibling(file.getFileName() + "." + ProcessHandle.current().pid() + "-" + PARTS.incrementAndGet() + ".part");

               try {
                  try (OutputStream out = Files.newOutputStream(part)) {
                     out.write(data, 0, length);
                  }

                  try {
                     Files.move(part, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                  } catch (AtomicMoveNotSupportedException var16) {
                     Files.move(part, file, StandardCopyOption.REPLACE_EXISTING);
                  }
               } finally {
                  Files.deleteIfExists(part);
               }
            }
         }
      }
   }

   static final class Mismatch extends IOException {
      Mismatch(String message) {
         super(message);
      }
   }
}
