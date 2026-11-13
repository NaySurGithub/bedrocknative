package net.bedrocknative.net.nethernet;

import java.net.ConnectException;
import org.jspecify.annotations.Nullable;

public final class NetherNetConnectException extends ConnectException {
   private final NetherNetConnectException.Stage stage;
   private final int offeredCandidates;
   private final int answerCandidates;
   private final long millis;

   public NetherNetConnectException(
      NetherNetConnectException.Stage stage, String message, @Nullable Throwable cause, int offeredCandidates, int answerCandidates, long millis
   ) {
      super(message);
      this.stage = stage;
      this.offeredCandidates = offeredCandidates;
      this.answerCandidates = answerCandidates;
      this.millis = millis;
      if (cause != null) {
         this.initCause(cause);
      }
   }

   public NetherNetConnectException.Stage stage() {
      return this.stage;
   }

   public int offeredCandidates() {
      return this.offeredCandidates;
   }

   public int answerCandidates() {
      return this.answerCandidates;
   }

   public long millis() {
      return this.millis;
   }

   @Nullable
   public static NetherNetConnectException in(@Nullable Throwable failure) {
      for (Throwable cause = failure; cause != null; cause = cause.getCause() == cause ? null : cause.getCause()) {
         if (cause instanceof NetherNetConnectException netherNet) {
            return netherNet;
         }
      }

      return null;
   }

   public static enum Stage {
      UNAVAILABLE,
      OFFER,
      SIGNALING,
      LINK;
   }
}
