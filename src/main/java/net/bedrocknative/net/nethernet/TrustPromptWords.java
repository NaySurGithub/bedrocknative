package net.bedrocknative.net.nethernet;

public final class TrustPromptWords {
   public static final String CHANGED_TITLE = "The server's key changed";
   public static final String CHANGED_CANCEL = "Cancel";
   public static final String CHANGED_TRUST = "Trust the new key and replace the old one";

   private TrustPromptWords() {
   }

   public static String changedMessage(UntrustedServerException untrusted) {
      return untrusted.displayTarget()
         + " is answering with a different key than the one you trusted before. The server may have been reinstalled, or someone else may be answering in its place. Only continue if you expect this.";
   }

   public static String keyLine(UntrustedServerException untrusted) {
      String domain = untrusted.displayDomain();
      StringBuilder line = new StringBuilder("Server key sha256 ").append(untrusted.shortDigest());
      if (untrusted.keyChanged()) {
         line.append(" (trusted before: ").append(untrusted.shortPreviousDigest()).append(')');
      }

      if (!domain.isBlank()) {
         line.append(", ").append(domain);
      }

      return line.toString();
   }
}
