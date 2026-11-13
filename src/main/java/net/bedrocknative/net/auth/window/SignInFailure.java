package net.bedrocknative.net.auth.window;

import java.io.IOException;
import org.jspecify.annotations.Nullable;

public final class SignInFailure extends IOException {
   private final SignInFailure.Reason reason;
   private final @Nullable String oauthError;

   public SignInFailure(SignInFailure.Reason reason, String description) {
      this(reason, description, null);
   }

   public SignInFailure(SignInFailure.Reason reason, String description, @Nullable String oauthError) {
      super(description + (oauthError == null ? "" : " (" + oauthError(oauthError) + ")"));
      this.reason = reason;
      this.oauthError = oauthError == null ? null : oauthError(oauthError);
   }

   public SignInFailure.Reason reason() {
      return this.reason;
   }

   public @Nullable String oauthError() {
      return this.oauthError;
   }

   static String oauthError(String error) {
      if (!error.isEmpty() && error.length() <= 64) {
         for (int i = 0; i < error.length(); i++) {
            char c = error.charAt(i);
            if ((c < 'a' || c > 'z') && (c < 'A' || c > 'Z') && (c < '0' || c > '9') && c != '_') {
               return "other";
            }
         }

         return error;
      } else {
         return "other";
      }
   }

   public enum Reason {
      CLOSED,
      DECLINED,
      INCOMPLETE,
      TIMED_OUT;
   }
}
