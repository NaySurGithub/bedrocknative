package net.bedrocknative.net.nethernet;

import java.io.IOException;

public final class ServerIdentityException extends IOException {
   public ServerIdentityException(Throwable cause) {
      super("The server's identity could not be verified: " + SignalingHttp.describe(cause), cause);
   }
}
