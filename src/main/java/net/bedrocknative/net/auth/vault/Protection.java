package net.bedrocknative.net.auth.vault;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Locale;

public interface Protection {
   int kind();

   byte[] seal(byte[] var1) throws IOException;

   byte[] open(byte[] var1) throws IOException;

   default void restrict(Path path) {
      try {
         if (Files.getFileStore(path).supportsFileAttributeView("posix")) {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(Files.isDirectory(path) ? "rwx------" : "rw-------"));
         }
      } catch (UnsupportedOperationException | IOException var3) {
      }
   }

   static Protection standard() {
      return (Protection)(System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win") ? new DpapiProtection() : ownerOnly());
   }

   static Protection ownerOnly() {
      return new Protection() {
         @Override
         public int kind() {
            return 2;
         }

         @Override
         public byte[] seal(byte[] plain) {
            return (byte[])plain.clone();
         }

         @Override
         public byte[] open(byte[] sealed) {
            return (byte[])sealed.clone();
         }
      };
   }
}
