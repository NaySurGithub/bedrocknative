package net.bedrocknative.net.auth;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.net.auth.window.AuthCodeRequest;
import net.bedrocknative.net.auth.window.CodeExchange;
import net.bedrocknative.net.auth.window.HelperCapture;
import net.bedrocknative.net.auth.window.RedirectCapture;
import net.bedrocknative.net.auth.window.SignInFailure;
import net.bedrocknative.net.auth.window.WindowSignIns;
import org.jspecify.annotations.Nullable;

public final class XboxBrowserSignIn {
   @Nullable
   private static volatile XboxBrowserSignIn standard;
   private final List<RedirectCapture> ways;
   private final CodeExchange exchange;
   private final SecureRandom random;
   private final Consumer<String> log;

   XboxBrowserSignIn(List<RedirectCapture> ways, CodeExchange exchange, SecureRandom random, Consumer<String> log) {
      this.ways = List.copyOf(ways);
      this.exchange = exchange;
      this.random = random;
      this.log = log;
   }

   public static XboxBrowserSignIn standard() {
      XboxBrowserSignIn current = standard;
      if (current == null) {
         synchronized (XboxBrowserSignIn.class) {
            current = standard;
            if (current == null) {
               Consumer<String> log = BedrockNative.LOGGER::info;
               List<RedirectCapture> ways = new ArrayList<>(WindowSignIns.system(log));
               String only = System.getProperty("bedrocknative.signInWay", "auto");
               if (only.equals("browser")) {
                  ways.removeIf(HelperCapture.class::isInstance);
               } else if (only.equals("code")) {
                  ways.clear();
               }

               current = new XboxBrowserSignIn(ways, CodeExchange.standard(), new SecureRandom(), log);
               standard = current;
            }
         }
      }

      return current;
   }

   public Optional<String> signIn(XboxBrowserSignIn.Listener listener) throws IOException, InterruptedException {
      AuthCodeRequest request = AuthCodeRequest.create(XboxTitle.ANDROID.clientId(), "service::user.auth.xboxlive.com::MBI_SSL", this.random);
      RedirectCapture.Control control = control(listener, request);

      for (RedirectCapture way : this.ways) {
         checkNotInterrupted();
         if (listener.codeInstead()) {
            break;
         }

         String unavailable = way.unavailable();
         if (unavailable != null) {
            this.log.accept("Xbox sign-in: " + way.name() + " is not used (" + unavailable + ")");
         } else {
            RedirectCapture.Outcome outcome = way.capture(request.url(), control);
            checkNotInterrupted();
            switch (outcome.kind()) {
               case FAILED:
                  this.log.accept("Xbox sign-in: " + way.name() + " could not show Microsoft's page (" + outcome.detail() + ")");
                  break;
               case CLOSED:
                  throw new SignInFailure(SignInFailure.Reason.CLOSED, "the player closed the sign-in page");
               case TIMED_OUT:
                  throw new SignInFailure(SignInFailure.Reason.TIMED_OUT, "the sign-in page stayed open too long");
               case CODE_INSTEAD:
                  this.log.accept("Xbox sign-in: the player chose a code instead");
                  return Optional.empty();
               case REDIRECT:
                  AuthCodeRequest.Redirect redirect = request.read(outcome.redirect());
                  listener.returned();
                  CodeExchange.Tokens tokens = this.exchange.exchange(request, redirect);
                  this.log.accept("Xbox sign-in: Microsoft accepted the sign-in in " + way.name());
                  return Optional.of(tokens.refreshToken());
            }
         }
      }

      if (!listener.codeInstead()) {
         this.log.accept("Xbox sign-in: no window could show Microsoft's page here; using a code");
      }

      return Optional.empty();
   }

   private static void checkNotInterrupted() throws InterruptedException {
      if (Thread.interrupted()) {
         throw new InterruptedException("the sign-in was cancelled");
      }
   }

   private static RedirectCapture.Control control(final XboxBrowserSignIn.Listener listener, final AuthCodeRequest request) {
      return new RedirectCapture.Control() {
         @Override
         public void opened(RedirectCapture.Window window) {
            listener.opened(window);
         }

         @Override
         public boolean codeInstead() {
            return listener.codeInstead();
         }

         @Override
         public boolean ours(String redirect) {
            return request.ours(redirect);
         }

         @Override
         public boolean openInBrowser(String url) {
            return listener.openInBrowser(url);
         }

         @Override
         public String windowTitle() {
            return listener.windowTitle();
         }

         @Nullable
         @Override
         public String language() {
            return listener.language();
         }
      };
   }

   public interface Listener {
      void opened(RedirectCapture.Window var1);

      void returned();

      boolean codeInstead();

      boolean openInBrowser(String var1);

      String windowTitle();

      @Nullable
      String language();
   }
}
