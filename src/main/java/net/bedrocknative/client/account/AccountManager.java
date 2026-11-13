package net.bedrocknative.client.account;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.net.auth.SignInActions;
import net.bedrocknative.net.auth.XboxAccount;
import net.bedrocknative.net.auth.XboxAccountStore;
import net.bedrocknative.net.auth.XboxPrivileges;
import net.bedrocknative.net.auth.XboxProblem;
import net.bedrocknative.net.auth.XboxSignInFlow;
import net.bedrocknative.net.auth.window.SignInCleanup;
import net.bedrocknative.net.auth.window.WindowSignIns;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.ClientStopping;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

public final class AccountManager {
   private static final Map<String, AccountManager.Status> STATUS = new ConcurrentHashMap<>();
   private static final AtomicInteger CHANGES = new AtomicInteger();
   @Nullable
   private static volatile XboxAccountStore store;
   private static volatile boolean autoVerify = true;
   private static volatile XboxSignInFlow.Signer signer = XboxAccount::signIn;
   private static volatile SignInActions signInActions = new ClientSignInActions();
   private static volatile boolean devLocked;

   private AccountManager() {
   }

   public static XboxAccountStore store() {
      XboxAccountStore current = store;
      if (current == null) {
         synchronized (AccountManager.class) {
            current = store;
            if (current == null) {
               current = new XboxAccountStore(FabricLoader.getInstance().getConfigDir().resolve("bedrocknative"), XboxAccountStore.standardVault());
               current.usePrivileges(XboxPrivileges.online());
               store = current;
            }
         }
      }

      return current;
   }

   static boolean storeLoaded() {
      return store != null;
   }

   @Nullable
   static String activeIdIfLoaded() {
      XboxAccountStore current = store;
      return current == null ? null : current.active().map(XboxAccountStore.Entry::id).orElse(null);
   }

   public static synchronized void useStore(@Nullable XboxAccountStore other) {
      store = other;
      STATUS.clear();
      CHANGES.incrementAndGet();
      AccountWarmUp.WARM_UP.forget();
   }

   public static void autoVerify(boolean on) {
      autoVerify = on;
   }

   public static boolean autoVerify() {
      return autoVerify;
   }

   public static XboxSignInFlow.Signer signer() {
      return signer;
   }

   public static void registerWindowSignIn() {
      Thread cleanup = new Thread(() -> WindowSignIns.cleanUpAtStart(BedrockNative.LOGGER::info), "Xbox sign-in leftovers");
      cleanup.setDaemon(true);
      cleanup.start();
      ClientLifecycleEvents.CLIENT_STOPPING.register((ClientStopping)client -> SignInCleanup.closeAll());
   }

   public static void useSigner(XboxSignInFlow.Signer other) {
      signer = other;
   }

   public static SignInActions signInActions() {
      return signInActions;
   }

   public static void useSignInActions(SignInActions other) {
      signInActions = other;
   }

   public static void devLocked(boolean locked) {
      devLocked = locked;
   }

   public static int changes() {
      return CHANGES.get();
   }

   public static AccountManager.Status status(String id) {
      return STATUS.getOrDefault(id, AccountManager.Status.UNKNOWN);
   }

   static void record(String id, AccountManager.Status status) {
      STATUS.put(id, status);
      CHANGES.incrementAndGet();
   }

   public static boolean canSwitch(Minecraft minecraft) {
      return !devLocked && minecraft.level == null && minecraft.getConnection() == null;
   }

   public static XboxAccountStore.Prepared prepareActive() throws XboxAccountStore.AccountException {
      XboxAccountStore accounts = store();

      try {
         XboxAccountStore.Prepared prepared = exclusive(accounts::prepareActive);
         accounts.active().ifPresent(entry -> {
            record(entry.id(), AccountManager.Status.OK);
            prepared.privileges().whenRefused(refusal -> record(entry.id(), statusOf(refusal)));
            AccountWarmUp.known(entry.id(), prepared.account());
         });
         return prepared;
      } catch (XboxAccountStore.AccountException var2) {
         if (var2.account() != null) {
            record(var2.account().id(), statusOf(var2));
         }

         throw var2;
      }
   }

   private static <T> T exclusive(Callable<T> step) throws XboxAccountStore.AccountException {
      try {
         return AccountWarmUp.WARM_UP.exclusive(step);
      } catch (RuntimeException | XboxAccountStore.AccountException var2) {
         throw var2;
      } catch (Exception var3) {
         throw new IllegalStateException(var3);
      }
   }

   public static void verifyAsync(String id) {
      if (status(id).check() != AccountManager.Check.CHECKING) {
         record(id, AccountManager.Status.CHECKING);
         XboxAccountStore accounts = store();
         Thread thread = new Thread(() -> {
            try {
               exclusive(() -> accounts.authenticate(id));
               record(id, AccountManager.Status.OK);
            } catch (XboxAccountStore.AccountException var3) {
               record(id, statusOf(var3));
            } catch (RuntimeException var4) {
               record(id, new AccountManager.Status(AccountManager.Check.FAILED, String.valueOf(var4.getMessage())));
            }
         }, "Xbox account check");
         thread.setDaemon(true);
         thread.start();
      }
   }

   static AccountManager.Status statusOf(XboxAccountStore.AccountException e) {
      return new AccountManager.Status(
         e.problem() == XboxAccountStore.Problem.SIGN_IN_REQUIRED ? AccountManager.Check.SIGN_IN_REQUIRED : AccountManager.Check.FAILED,
         String.valueOf(e.getMessage()),
         e.diagnosis()
      );
   }

   public static Component name(@Nullable XboxAccountStore.Entry entry) {
      return entry != null && entry.gamertag() != null ? Component.literal(entry.gamertag()) : Component.translatable("bedrocknative.accounts.unnamed");
   }

   public static Component message(XboxAccountStore.AccountException e) {
      if (e.diagnosis() != null) {
         return AccountProblems.message(e.diagnosis(), e.account());
      } else {
         return switch (e.problem()) {
            case NONE_ACTIVE -> Component.translatable("bedrocknative.accounts.message.none");
            case SIGN_IN_REQUIRED -> Component.translatable("bedrocknative.accounts.message.expired", new Object[]{name(e.account())});
            case FAILED -> Component.translatable("bedrocknative.accounts.message.failed", new Object[]{name(e.account()), String.valueOf(e.getMessage())});
         };
      }
   }

   public static Component buttonLabel() {
      return store()
         .active()
         .map(entry -> Component.translatable("bedrocknative.accounts.button", new Object[]{name(entry)}))
         .orElseGet(() -> Component.translatable("bedrocknative.accounts.button.none"));
   }

   public static enum Check {
      UNKNOWN,
      CHECKING,
      OK,
      SIGN_IN_REQUIRED,
      FAILED;
   }

   public record Status(AccountManager.Check check, String detail, @Nullable XboxProblem.Diagnosis diagnosis) {
      static final AccountManager.Status UNKNOWN = new AccountManager.Status(AccountManager.Check.UNKNOWN, "");
      static final AccountManager.Status CHECKING = new AccountManager.Status(AccountManager.Check.CHECKING, "");
      static final AccountManager.Status OK = new AccountManager.Status(AccountManager.Check.OK, "");

      public Status(AccountManager.Check check, String detail) {
         this(check, detail, null);
      }
   }
}
