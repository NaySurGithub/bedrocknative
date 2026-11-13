package net.bedrocknative.net.auth;

import java.util.Objects;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.net.auth.window.RedirectCapture;
import net.bedrocknative.net.auth.window.SignInFailure;
import net.raphimc.minecraftauth.bedrock.BedrockAuthManager;
import net.raphimc.minecraftauth.msa.model.MsaDeviceCode;
import org.jspecify.annotations.Nullable;

public final class XboxSignInFlow {
   private final XboxAccountStore store;
   private final XboxSignInFlow.Signer signer;
   private final boolean activate;
   private final SignInActions actions;
   private final Consumer<XboxSignInFlow.Snapshot> listener;
   private volatile XboxSignInFlow.Snapshot snapshot = XboxSignInFlow.Snapshot.of(XboxSignInFlow.State.STARTING);
   private volatile boolean cancelled;
   private volatile boolean codeInstead;
   private boolean saving;
   @Nullable
   private Thread thread;
   private final XboxBrowserSignIn.Listener windowListener = new XboxBrowserSignIn.Listener() {
      {
         Objects.requireNonNull(XboxSignInFlow.this);
      }

      @Override
      public void opened(RedirectCapture.Window window) {
         XboxSignInFlow.this.publish(XboxSignInFlow.Snapshot.window(window));
      }

      @Override
      public void returned() {
         XboxSignInFlow.this.publish(XboxSignInFlow.Snapshot.of(XboxSignInFlow.State.SIGNING_IN));
      }

      @Override
      public boolean codeInstead() {
         return XboxSignInFlow.this.codeInstead;
      }

      @Override
      public boolean openInBrowser(String url) {
         try {
            return !XboxSignInFlow.this.cancelled && XboxSignInFlow.this.actions.openBrowser(url);
         } catch (RuntimeException var3) {
            BedrockNative.LOGGER.warn("Couldn't open the sign-in page: {}", var3.getClass().getSimpleName());
            return false;
         }
      }

      @Override
      public String windowTitle() {
         return XboxSignInFlow.this.actions.windowTitle();
      }

      @Nullable
      @Override
      public String language() {
         return XboxSignInFlow.this.actions.languageTag();
      }
   };

   public XboxSignInFlow(XboxAccountStore store, boolean activate, Consumer<XboxSignInFlow.Snapshot> listener) {
      this(store, XboxAccount::signIn, activate, listener);
   }

   public XboxSignInFlow(XboxAccountStore store, XboxSignInFlow.Signer signer, boolean activate, Consumer<XboxSignInFlow.Snapshot> listener) {
      this(store, signer, SignInActions.NONE, activate, listener);
   }

   public XboxSignInFlow(
      XboxAccountStore store, XboxSignInFlow.Signer signer, SignInActions actions, boolean activate, Consumer<XboxSignInFlow.Snapshot> listener
   ) {
      this.store = store;
      this.signer = signer;
      this.actions = actions;
      this.activate = activate;
      this.listener = listener;
   }

   public XboxSignInFlow.Snapshot snapshot() {
      return this.snapshot;
   }

   public synchronized void start() {
      if (this.thread != null) {
         throw new IllegalStateException("Already started");
      } else {
         this.thread = new Thread(this::run, "Xbox sign-in");
         this.thread.setDaemon(true);
         this.thread.start();
      }
   }

   public synchronized boolean cancel() {
      if (!this.saving && !this.snapshot.finished()) {
         this.cancelled = true;
         if (this.thread != null) {
            this.thread.interrupt();
         }

         return true;
      } else {
         return false;
      }
   }

   public boolean useCodeInstead() {
      if (!this.snapshot.windowPart()) {
         return false;
      } else {
         this.codeInstead = true;
         return true;
      }
   }

   public void join(long millis) throws InterruptedException {
      Thread running;
      synchronized (this) {
         running = this.thread;
      }

      if (running != null) {
         running.join(millis);
      }
   }

   private void run() {
      this.publish(XboxSignInFlow.Snapshot.of(XboxSignInFlow.State.STARTING));

      try {
         BedrockAuthManager account = this.signer.signIn(new XboxAccount.CodePrompt() {
            {
               Objects.requireNonNull(XboxSignInFlow.this);
            }

            @Override
            public void show(XboxTitle title, MsaDeviceCode code) {
               this.show(title, code, DeviceCodeLink.direct(code.getVerificationUri(), code.getUserCode(), null));
            }

            @Override
            public void show(XboxTitle title, MsaDeviceCode code, String directUri) {
               XboxSignInFlow.this.codeReady(code, directUri);
            }

            @Override
            public XboxBrowserSignIn.Listener window() {
               return XboxSignInFlow.this.windowListener;
            }

            @Override
            public void requestingCode() {
               XboxSignInFlow.this.publish(XboxSignInFlow.Snapshot.of(XboxSignInFlow.State.REQUESTING_CODE));
            }
         });
         if (!this.beginSaving()) {
            this.publish(XboxSignInFlow.Snapshot.of(XboxSignInFlow.State.CANCELLED));
            return;
         }

         this.publish(XboxSignInFlow.Snapshot.of(XboxSignInFlow.State.SAVING));
         XboxAccountStore.Entry entry = this.store.add(account, this.activate);
         this.publish(new XboxSignInFlow.Snapshot(XboxSignInFlow.State.DONE, null, null, null, false, entry, null, null, null));
      } catch (Exception var4) {
         if (!this.cancelled
            && !(var4 instanceof InterruptedException)
            && !(var4 instanceof SignInFailure window && window.reason() == SignInFailure.Reason.CLOSED)) {
            XboxProblem.Diagnosis problem = XboxProblem.classify(var4);
            BedrockNative.LOGGER.warn("Xbox sign-in failed: {} ({})", problem, var4.getClass().getSimpleName());
            this.publish(
               XboxSignInFlow.Snapshot.failed(
                  var4 instanceof TimeoutException ? "The code expired before it was entered" : String.valueOf(var4.getMessage()), problem
               )
            );
         } else {
            this.publish(XboxSignInFlow.Snapshot.of(XboxSignInFlow.State.CANCELLED));
         }
      }
   }

   private void codeReady(MsaDeviceCode code, String directUri) {
      boolean opened = false;
      if (!this.cancelled) {
         try {
            opened = this.actions.openBrowser(directUri);
         } catch (RuntimeException var6) {
            BedrockNative.LOGGER.warn("Couldn't open the sign-in page: {}", var6.getMessage());
         }

         try {
            this.actions.copyToClipboard(code.getUserCode());
         } catch (RuntimeException var5) {
            BedrockNative.LOGGER.warn("Couldn't copy the sign-in code: {}", var5.getMessage());
         }
      }

      this.publish(
         new XboxSignInFlow.Snapshot(
            XboxSignInFlow.State.WAITING_FOR_CODE_ENTRY, code.getVerificationUri(), directUri, code.getUserCode(), opened, null, null, null, null
         )
      );
   }

   private synchronized boolean beginSaving() {
      if (this.cancelled) {
         return false;
      } else {
         this.saving = true;
         return true;
      }
   }

   private void publish(XboxSignInFlow.Snapshot next) {
      if (!this.snapshot.finished()) {
         this.snapshot = next;
         this.listener.accept(next);
      }
   }

   public interface Signer {
      BedrockAuthManager signIn(XboxAccount.CodePrompt var1) throws Exception;
   }

   public record Snapshot(
      XboxSignInFlow.State state,
      @Nullable String uri,
      @Nullable String directUri,
      @Nullable String code,
      boolean browserOpened,
      @Nullable XboxAccountStore.Entry account,
      @Nullable String error,
      @Nullable XboxProblem.Diagnosis problem,
      @Nullable RedirectCapture.Window window
   ) {
      public boolean finished() {
         return this.state == XboxSignInFlow.State.DONE || this.state == XboxSignInFlow.State.FAILED || this.state == XboxSignInFlow.State.CANCELLED;
      }

      public boolean windowPart() {
         return this.state == XboxSignInFlow.State.STARTING || this.state == XboxSignInFlow.State.WAITING_FOR_WINDOW;
      }

      static XboxSignInFlow.Snapshot of(XboxSignInFlow.State state) {
         return new XboxSignInFlow.Snapshot(state, null, null, null, false, null, null, null, null);
      }

      static XboxSignInFlow.Snapshot window(RedirectCapture.Window window) {
         return new XboxSignInFlow.Snapshot(XboxSignInFlow.State.WAITING_FOR_WINDOW, null, null, null, false, null, null, null, window);
      }

      static XboxSignInFlow.Snapshot failed(String error, XboxProblem.Diagnosis problem) {
         return new XboxSignInFlow.Snapshot(XboxSignInFlow.State.FAILED, null, null, null, false, null, error, problem, null);
      }
   }

   public static enum State {
      STARTING,
      WAITING_FOR_WINDOW,
      SIGNING_IN,
      REQUESTING_CODE,
      WAITING_FOR_CODE_ENTRY,
      SAVING,
      DONE,
      FAILED,
      CANCELLED;
   }
}
