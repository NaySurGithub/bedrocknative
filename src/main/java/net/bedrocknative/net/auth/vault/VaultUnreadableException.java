package net.bedrocknative.net.auth.vault;

import java.io.IOException;

public final class VaultUnreadableException extends IOException {
   public VaultUnreadableException(String message) {
      super(message);
   }
}
