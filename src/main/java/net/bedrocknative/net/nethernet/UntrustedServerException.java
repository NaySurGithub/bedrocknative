package net.bedrocknative.net.nethernet;

import org.jspecify.annotations.Nullable;

public final class UntrustedServerException extends Exception {
   private final String digest;
   private final String target;
   private final String domain;
   private final @Nullable String issuer;
   private final @Nullable String previousDigest;
   private final transient @Nullable NetherNetTrustStore store;

   public UntrustedServerException(String digest, String target, String domain, @Nullable String issuer) {
      this(digest, target, domain, issuer, null, null);
   }

   UntrustedServerException(
      String digest, String target, String domain, @Nullable String issuer, @Nullable NetherNetTrustStore store, @Nullable String previousDigest
   ) {
      super(message(digest, target, previousDigest));
      this.digest = digest;
      this.target = target;
      this.domain = domain;
      this.issuer = issuer;
      this.previousDigest = previousDigest;
      this.store = store;
   }

   private static String message(String digest, String target, @Nullable String previousDigest) {
      String shown = PromptText.plain(target, 80);
      String key = "sha256 " + digest.substring(0, Math.min(8, digest.length()));
      return previousDigest == null
         ? "The server's key " + key + " is not trusted yet (" + shown + ", plain HTTP signaling)"
         : "The server's key at "
            + shown
            + " changed: "
            + key
            + " answers where "
            + previousDigest.substring(0, Math.min(8, previousDigest.length()))
            + " was trusted";
   }

   public void trust() {
      if (this.store == null) {
         throw new IllegalStateException("No trust store to keep the key in");
      }

      if (this.previousDigest != null) {
         this.store.replace(this.previousDigest, this.digest, this.target, this.domain);
      } else {
         this.store.trust(this.digest, this.target, this.domain);
      }
   }

   public String digest() {
      return this.digest;
   }

   public String shortDigest() {
      return this.digest.substring(0, Math.min(8, this.digest.length()));
   }

   public String target() {
      return this.target;
   }

   public String domain() {
      return this.domain;
   }

   public @Nullable String issuer() {
      return this.issuer;
   }

   public @Nullable String previousDigest() {
      return this.previousDigest;
   }

   public @Nullable String shortPreviousDigest() {
      return this.previousDigest == null ? null : this.previousDigest.substring(0, Math.min(8, this.previousDigest.length()));
   }

   public boolean keyChanged() {
      return this.previousDigest != null;
   }

   public String displayTarget() {
      return PromptText.plain(this.target, 80);
   }

   public String displayDomain() {
      return PromptText.plain(this.domain, 64);
   }

   public String displayIssuer() {
      return PromptText.plain(this.issuer, 64);
   }

   public static @Nullable UntrustedServerException in(@Nullable Throwable failure) {
      for (Throwable cause = failure; cause != null; cause = cause.getCause() == cause ? null : cause.getCause()) {
         if (cause instanceof UntrustedServerException untrusted) {
            return untrusted;
         }
      }

      return null;
   }
}
