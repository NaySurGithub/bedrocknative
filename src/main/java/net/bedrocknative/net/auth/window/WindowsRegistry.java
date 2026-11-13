package net.bedrocknative.net.auth.window;

import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.WinReg;
import com.sun.jna.platform.win32.WinReg.HKEY;
import java.io.IOException;
import org.jspecify.annotations.Nullable;

public final class WindowsRegistry implements Registry {
   @Override
   public boolean keyExists(String key) throws IOException {
      try {
         return Advapi32Util.registryKeyExists(WinReg.HKEY_CURRENT_USER, key);
      } catch (RuntimeException | LinkageError e) {
         throw failure(e);
      }
   }

   @Override
   public @Nullable String get(String key, String name) throws IOException {
      return read(WinReg.HKEY_CURRENT_USER, key, name);
   }

   @Override
   public void set(String key, String name, String value) throws IOException {
      try {
         Advapi32Util.registryCreateKey(WinReg.HKEY_CURRENT_USER, key);
         Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, key, name, value);
      } catch (RuntimeException | LinkageError e) {
         throw failure(e);
      }
   }

   @Override
   public void deleteKey(String key) throws IOException {
      try {
         Advapi32Util.registryDeleteKey(WinReg.HKEY_CURRENT_USER, key);
      } catch (RuntimeException | LinkageError e) {
         throw failure(e);
      }
   }

   @Override
   public @Nullable String getMachine(String key, String name) throws IOException {
      return read(WinReg.HKEY_LOCAL_MACHINE, key, name);
   }

   private static @Nullable String read(HKEY root, String key, String name) throws IOException {
      try {
         return Advapi32Util.registryKeyExists(root, key) && Advapi32Util.registryValueExists(root, key, name)
            ? Advapi32Util.registryGetStringValue(root, key, name)
            : null;
      } catch (RuntimeException | LinkageError e) {
         throw failure(e);
      }
   }

   private static IOException failure(Throwable e) {
      return new IOException("registry: " + e.getClass().getSimpleName());
   }
}
