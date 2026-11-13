package net.bedrocknative.net.auth;

import org.jspecify.annotations.Nullable;

public interface SignInActions {
   SignInActions NONE = new SignInActions() {
      @Override
      public boolean openBrowser(String uri) {
         return false;
      }

      @Override
      public void copyToClipboard(String text) {
      }
   };

   boolean openBrowser(String var1);

   void copyToClipboard(String var1);

   default String windowTitle() {
      return "Sign In";
   }

   default @Nullable String languageTag() {
      return null;
   }
}
