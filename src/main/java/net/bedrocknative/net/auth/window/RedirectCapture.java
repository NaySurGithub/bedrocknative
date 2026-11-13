package net.bedrocknative.net.auth.window;

import org.jspecify.annotations.Nullable;

public interface RedirectCapture {
   String name();

   @Nullable String unavailable();

   RedirectCapture.Outcome capture(String var1, RedirectCapture.Control var2) throws InterruptedException;

   interface Control {
      void opened(RedirectCapture.Window var1);

      boolean codeInstead();

      boolean ours(String var1);

      boolean openInBrowser(String var1);

      String windowTitle();

      @Nullable String language();
   }

   record Outcome(RedirectCapture.Outcome.Kind kind, @Nullable String redirect, String detail) {
      public static RedirectCapture.Outcome redirect(@Nullable String redirect) {
         return new RedirectCapture.Outcome(RedirectCapture.Outcome.Kind.REDIRECT, redirect, "redirect received");
      }

      public static RedirectCapture.Outcome of(RedirectCapture.Outcome.Kind kind, String detail) {
         return new RedirectCapture.Outcome(kind, null, detail);
      }

      @Override
      public String toString() {
         return this.kind + " (" + this.detail + ")";
      }

      public enum Kind {
         REDIRECT,
         CLOSED,
         CODE_INSTEAD,
         TIMED_OUT,
         FAILED;
      }
   }

   enum Window {
      HELPER,
      BROWSER_APP,
      BROWSER;
   }
}
