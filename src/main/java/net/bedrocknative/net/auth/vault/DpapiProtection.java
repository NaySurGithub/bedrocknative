package net.bedrocknative.net.auth.vault;

import com.sun.jna.platform.win32.Crypt32Util;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class DpapiProtection implements Protection {
   public static final int KIND = 1;
   static final byte[] ENTROPY = "BedrockNative Xbox token vault 1".getBytes(StandardCharsets.US_ASCII);
   private static final String DESCRIPTION = "BedrockNative Xbox sign-in";
   private final byte[] entropy;

   public DpapiProtection() {
      this(ENTROPY);
   }

   DpapiProtection(byte[] entropy) {
      this.entropy = (byte[])entropy.clone();
   }

   @Override
   public int kind() {
      return 1;
   }

   @Override
   public byte[] seal(byte[] plain) throws IOException {
      try {
         return Crypt32Util.cryptProtectData(plain, this.entropy, 1, "BedrockNative Xbox sign-in", null);
      } catch (RuntimeException | LinkageError e) {
         throw new IOException("Windows could not protect the sign-in (" + e.getClass().getSimpleName() + ")");
      }
   }

   @Override
   public byte[] open(byte[] sealed) throws IOException {
      try {
         return Crypt32Util.cryptUnprotectData(sealed, this.entropy, 1, null);
      } catch (RuntimeException | LinkageError e) {
         throw new VaultUnreadableException("Windows could not open the saved sign-in for this Windows user (" + e.getClass().getSimpleName() + ")");
      }
   }
}
