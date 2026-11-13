package net.bedrocknative.net.transport;

public final class TransportUnavailableException extends RuntimeException {
   private final TransportKind kind;

   public TransportUnavailableException(TransportKind kind, String message) {
      super(message);
      this.kind = kind;
   }

   public TransportKind kind() {
      return this.kind;
   }
}
