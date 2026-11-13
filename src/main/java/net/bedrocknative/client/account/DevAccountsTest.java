package net.bedrocknative.client.account;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Base64.Encoder;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.lobby.BedrockLobby;
import net.bedrocknative.client.lobby.JsonUiStartScreen;
import net.bedrocknative.client.ui.JsonUi;
import net.bedrocknative.client.ui.JsonUiScreen;
import net.bedrocknative.content.BedrockInstall;
import net.bedrocknative.net.auth.DeviceCodeLink;
import net.bedrocknative.net.auth.SignInActions;
import net.bedrocknative.net.auth.XboxAccount;
import net.bedrocknative.net.auth.XboxAccountStore;
import net.bedrocknative.net.auth.XboxBrowserSignIn;
import net.bedrocknative.net.auth.XboxProblem;
import net.bedrocknative.net.auth.XboxSignInFlow;
import net.bedrocknative.net.auth.XboxTitle;
import net.bedrocknative.net.auth.vault.Protection;
import net.bedrocknative.net.auth.vault.TokenVault;
import net.bedrocknative.net.auth.window.AuthCodeRequest;
import net.bedrocknative.net.auth.window.HelperCapture;
import net.bedrocknative.net.auth.window.RedirectCapture;
import net.bedrocknative.net.auth.window.SignInFailure;
import net.bedrocknative.net.auth.window.WindowSignIns;
import net.bedrocknative.ui.QrCode;
import net.bedrocknative.ui.SignInData;
import net.bedrocknative.ui.UiControl;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import net.raphimc.minecraftauth.MinecraftAuth;
import net.raphimc.minecraftauth.bedrock.BedrockAuthManager;
import net.raphimc.minecraftauth.bedrock.model.MinecraftMultiplayerToken;
import net.raphimc.minecraftauth.msa.model.MsaDeviceCode;
import net.raphimc.minecraftauth.msa.model.MsaToken;

public final class DevAccountsTest {
   private final Minecraft minecraft;
   private int passed;
   private int failed;
   private final DevAccountsTest.RecordingActions actions = new DevAccountsTest.RecordingActions();

   private DevAccountsTest(Minecraft minecraft) {
      this.minecraft = minecraft;
   }

   public static void start() {
      Thread.ofVirtual().start(() -> {
         DevAccountsTest test = new DevAccountsTest(Minecraft.getInstance());

         try {
            test.run();
         } catch (Throwable e) {
            BedrockNative.LOGGER.error("Dev accounts test: FAIL crashed", e);
            test.failed++;
         } finally {
            AccountManager.useStore(null);
            AccountManager.autoVerify(true);
            AccountManager.useSigner(XboxAccount::signIn);
            AccountManager.useSignInActions(new ClientSignInActions());
            AccountManager.devLocked(false);
            BedrockNative.LOGGER.info("Dev accounts test: SUMMARY {} passed, {} failed", test.passed, test.failed);
            test.sleep(500L);
            test.run(test.minecraft::stop);
         }
      });
   }

   private void run() throws Exception {
      AccountManager.useSignInActions(this.actions);
      this.waitFor(
         "the title screen",
         () -> this.onClient(
            () -> this.minecraft.isGameLoadFinished()
               && this.minecraft.gui.overlay() == null
               && this.minecraft.gui.screen() instanceof TitleScreen
               && (!BedrockLobby.enabled() || this.minecraft.gui.screen() instanceof JsonUiStartScreen)
         )
      );
      this.sleep(1500L);
      Path configFolder = this.onClient(() -> FabricLoader.getInstance().getConfigDir().resolve("bedrocknative"));
      String realBefore = fingerprint(configFolder);
      XboxAccountStore real = AccountManager.store();
      log(
         "the player's real list: {} account(s), in use: {} (read only; tokens untouched)",
         real.accounts().size(),
         real.active().map(entry -> String.valueOf(entry.gamertag())).orElse("none")
      );
      log(
         "the old account.json {} the list's first account",
         Files.exists(configFolder.resolve("account.json"))
            ? (real.accounts().isEmpty() ? "EXISTS BUT IS NOT" : "is")
            : "does not exist, so nothing to migrate for"
      );
      this.check("a real account.json is migrated into the list", !Files.exists(configFolder.resolve("account.json")) || !real.accounts().isEmpty());
      TitleScreen title = this.onClient(() -> (TitleScreen)this.minecraft.gui.screen());
      log("the title screen is {}", title instanceof JsonUiStartScreen ? "the Bedrock lobby's start screen" : "Java's (-PbedrockLobby=off or no lobby)");
      this.screenshot("accounts-title-real");
      Path scratch = this.minecraft.gameDirectory.toPath().resolve("devtest-accounts");
      deleteTree(scratch);
      Files.createDirectories(scratch);
      XboxAccount.save(synthetic("LegacyPlayer", "1001"), scratch.resolve("account.json"));
      XboxAccountStore store = new XboxAccountStore(scratch);
      this.check(
         "account.json migrates into the list as the first account",
         store.accounts().size() == 1 && store.active().isPresent() && "LegacyPlayer".equals(store.active().get().gamertag())
      );
      XboxAccountStore.Entry legacy = store.active().orElseThrow();
      XboxAccountStore.Entry second = store.add(synthetic("SecondPlayer", "2002"), false);
      XboxAccountStore.Entry third = store.add(synthetic("ThirdPlayer", "3003"), false);
      AccountManager.useStore(store);
      AccountManager.autoVerify(false);
      Screen multiplayer;
      if (title instanceof JsonUiStartScreen start) {
         multiplayer = start;
         this.sleep(300L);
         this.check("the lobby's start screen names the active account", "LegacyPlayer".equals(this.onClient(() -> start.data().gamertag())));
         this.screenshot("accounts-title");
         UiControl profile = this.onClient(() -> start.ui().input().firing("button.menu_profile", null, -1));
         this.check("the lobby's start screen has the Profile button", profile != null);
         if (profile != null) {
            this.onClient(() -> start.ui().input().reveal(profile));
            this.sleep(200L);
            this.run(() -> {
               float x = profile.x() + profile.width() / 2.0F;
               float y = profile.y() + profile.height() / 2.0F;
               MouseButtonEvent event = new MouseButtonEvent(x, y, new MouseButtonInfo(1, 0));
               start.mouseMoved(x, y);
               start.mouseClicked(event, false);
               start.mouseReleased(event);
            });
         }

         this.sleep(800L);
         this.check("the Profile button opens the account screen", this.screen() instanceof AccountsScreen);
      } else {
         AccountScreenHooks.AccountButton titleButton = this.accountButton(title);
         this.check("the title screen has the account button", titleButton != null);
         this.check("the button names the active account", titleButton != null && titleButton.getMessage().getString().contains("LegacyPlayer"));
         this.screenshot("accounts-title");
         JoinMultiplayerScreen java = this.onClient(() -> {
            JoinMultiplayerScreen screen = new JoinMultiplayerScreen(title);
            this.minecraft.gui.setScreen(screen);
            return screen;
         });
         multiplayer = java;
         this.sleep(800L);
         AccountScreenHooks.AccountButton multiplayerButton = this.accountButton(java);
         this.check("the multiplayer screen has the account button", multiplayerButton != null);
         this.screenshot("accounts-multiplayer");
         if (multiplayerButton != null) {
            this.onClient(() -> {
               press(multiplayerButton);
               return null;
            });
         }

         this.sleep(500L);
         this.check("the multiplayer button opens the account screen", this.screen() instanceof AccountsScreen);
      }

      if (!(this.screen() instanceof AccountsScreen)) {
         this.onClient(() -> {
            this.minecraft.gui.setScreen(new AccountsScreen(multiplayer));
            return null;
         });
         this.sleep(500L);
      }

      AccountsScreen accounts = (AccountsScreen)this.screen();
      this.check("the list shows the three accounts in order", this.names(accounts).equals(List.of("LegacyPlayer", "SecondPlayer", "ThirdPlayer")));
      this.check("the active account is selected", accounts.selected() != null && accounts.selected().id().equals(legacy.id()));
      this.check("Use is off for the account already in use", !this.onClient(() -> accounts.useButton().active));
      this.screenshot("accounts-list");
      this.onClient(() -> {
         accounts.select(second.id());
         return null;
      });
      this.sleep(300L);
      this.check("Use is on for another account", this.onClient(() -> accounts.useButton().active));
      this.onClient(() -> {
         press(accounts.useButton());
         return null;
      });
      this.sleep(500L);
      this.check("switching changes the active account at once", store.active().orElseThrow().id().equals(second.id()));
      this.check("the switch is saved", new XboxAccountStore(scratch).active().orElseThrow().id().equals(second.id()));
      this.check("the switch says so", accounts.notice().getString().contains("SecondPlayer"));
      this.check("Use is off again", !this.onClient(() -> accounts.useButton().active));
      this.screenshot("accounts-switched");
      AccountManager.record(third.id(), new AccountManager.Status(AccountManager.Check.SIGN_IN_REQUIRED, "invalid_grant"));
      AccountManager.record(legacy.id(), new AccountManager.Status(AccountManager.Check.FAILED, "Couldn't reach Microsoft"));
      AccountManager.record(second.id(), new AccountManager.Status(AccountManager.Check.OK, ""));
      this.sleep(500L);
      this.screenshot("accounts-statuses");
      AccountManager.devLocked(true);
      this.onClient(() -> {
         accounts.select(legacy.id());
         return null;
      });
      this.sleep(500L);
      this.check("while connected, Use is off", !this.onClient(() -> accounts.useButton().active));
      this.onClient(() -> {
         accounts.select(second.id());
         return null;
      });
      this.sleep(300L);
      this.check("while connected, the active account can't be removed", !this.onClient(() -> accounts.removeButton().active));
      this.onClient(() -> {
         accounts.select(third.id());
         return null;
      });
      this.sleep(300L);
      this.check("while connected, another account can still be removed", this.onClient(() -> accounts.removeButton().active));
      this.screenshot("accounts-locked");
      AccountManager.devLocked(false);
      this.onClient(() -> {
         press(accounts.removeButton());
         return null;
      });
      this.sleep(500L);
      this.check("Remove asks first", this.screen() instanceof ConfirmScreen);
      this.screenshot("accounts-remove-confirm");
      this.onClient(
         () -> {
            Screen confirm = this.minecraft.gui.screen();
            Button yes = Screens.getWidgets(confirm)
               .stream()
               .filter(Button.class::isInstance)
               .map(Button.class::cast)
               .filter(button -> button.getMessage().getString().equals(Component.translatable("bedrocknative.accounts.remove").getString()))
               .findFirst()
               .orElseThrow();
            press(yes);
            return null;
         }
      );
      this.sleep(500L);
      this.check("confirming returns to the accounts", this.screen() == accounts);
      this.check(
         "the account is gone from the list and from disk",
         this.names(accounts).equals(List.of("LegacyPlayer", "SecondPlayer"))
            && store.find(third.id()).isEmpty()
            && !Files.exists(scratch.resolve(third.file()))
      );
      this.check("the other accounts keep their tokens", Files.exists(scratch.resolve(legacy.file())) && Files.exists(scratch.resolve(second.file())));
      CompletableFuture<Boolean> hold = new CompletableFuture<>();
      AtomicBoolean cancelInterrupted = new AtomicBoolean();
      AccountManager.useSigner(waiting("AB12CD34", hold, cancelInterrupted, "CancelledPlayer", "5005"));
      this.onClient(() -> {
         press(this.button(accounts, "bedrocknative.accounts.add"));
         return null;
      });
      this.waitFor("the sign-in code", () -> this.onClient(accounts::signingIn) && this.enabled(accounts, "bedrocknative.accounts.copy_code"));
      this.sleep(500L);
      this.check("Add shows the sign-in panel with the code", accounts.signingIn());
      XboxSignInFlow.Snapshot panel = this.onClient(accounts::signInSnapshot);
      this.check(
         "the panel has the code Microsoft gave", panel != null && "AB12CD34".equals(panel.code()) && "https://www.microsoft.com/link".equals(panel.uri())
      );
      this.check(
         "the browser was asked once, on the link with the code filled in", this.actions.opened.equals(List.of("https://www.microsoft.com/link?otc=AB12CD34"))
      );
      this.check("the code was copied to the clipboard when the sign-in started", this.actions.copied.equals(List.of("AB12CD34")));
      this.check(
         "the panel knows the browser opened",
         panel != null && panel.browserOpened() && "https://www.microsoft.com/link?otc=AB12CD34".equals(panel.directUri())
      );
      this.check(
         "the panel has the open page, copy and cancel buttons",
         this.enabled(accounts, "bedrocknative.accounts.open_link")
            && this.enabled(accounts, "bedrocknative.accounts.copy_code")
            && this.enabled(accounts, "gui.cancel")
      );
      this.screenshot("accounts-signin-code");
      this.onClient(() -> {
         press(this.button(accounts, "bedrocknative.accounts.open_link"));
         return null;
      });
      this.check(
         "Open the page asks the browser again for the same link",
         this.actions.opened.equals(List.of("https://www.microsoft.com/link?otc=AB12CD34", "https://www.microsoft.com/link?otc=AB12CD34"))
      );
      this.onClient(() -> {
         press(this.button(accounts, "bedrocknative.accounts.copy_code"));
         return null;
      });
      this.check("Copy the code puts it on the clipboard", "AB12CD34".equals(this.onClient(() -> this.minecraft.keyboardHandler.getClipboard())));
      this.screenshot("accounts-signin-copied");
      this.onClient(() -> {
         press(this.button(accounts, "gui.cancel"));
         return null;
      });
      this.check("Cancel interrupts the sign-in thread", this.waitUntil(cancelInterrupted::get, 2000L));
      this.check("Cancel ends the sign-in thread", this.waitUntil(() -> !signInThreadAlive(), 2000L));
      this.check("Cancel returns to the list", !this.onClient(accounts::signingIn));
      hold.complete(true);
      this.sleep(500L);
      this.check(
         "a cancelled sign-in adds nothing, not even when its code is entered later",
         store.accounts().size() == 2
            && store.accounts().stream().noneMatch(entry -> "CancelledPlayer".equals(entry.gamertag()))
            && this.names(accounts).equals(List.of("LegacyPlayer", "SecondPlayer"))
      );
      this.screenshot("accounts-signin-cancelled");
      this.actions.opened.clear();
      this.actions.copied.clear();
      this.actions.browserWorks = false;
      CompletableFuture<Boolean> holdNoBrowser = new CompletableFuture<>();
      AccountManager.useSigner(waiting("NB34RW56", holdNoBrowser, new AtomicBoolean(), "NoBrowserPlayer", "6006"));
      this.onClient(() -> {
         press(this.button(accounts, "bedrocknative.accounts.add"));
         return null;
      });
      this.waitFor("the sign-in code without a browser", () -> this.onClient(accounts::signingIn) && this.enabled(accounts, "bedrocknative.accounts.copy_code"));
      this.sleep(500L);
      XboxSignInFlow.Snapshot noBrowser = this.onClient(accounts::signInSnapshot);
      this.check(
         "a browser that does not open is known, the code is still copied and shown",
         noBrowser != null && !noBrowser.browserOpened() && "NB34RW56".equals(noBrowser.code()) && this.actions.copied.equals(List.of("NB34RW56"))
      );
      this.screenshot("accounts-signin-nobrowser");
      this.onClient(() -> {
         press(this.button(accounts, "gui.cancel"));
         return null;
      });
      holdNoBrowser.complete(true);
      this.sleep(500L);
      this.actions.browserWorks = true;
      AccountManager.useSigner(prompt -> {
         prompt.show(XboxTitle.ANDROID, new MsaDeviceCode(System.currentTimeMillis() + 900000L, 5000L, "device", "EF56GH78", "https://www.microsoft.com/link"));
         Thread.sleep(1500L);
         return synthetic("FourthPlayer", "4004");
      });
      this.onClient(() -> {
         press(this.button(accounts, "bedrocknative.accounts.add"));
         return null;
      });
      this.waitFor("the made-up sign-in to finish", () -> !this.onClient(accounts::signingIn));
      this.sleep(500L);
      this.check(
         "a finished sign-in adds the account and selects it",
         this.names(accounts).equals(List.of("LegacyPlayer", "SecondPlayer", "FourthPlayer"))
            && accounts.selected() != null
            && "FourthPlayer".equals(accounts.selected().gamertag())
      );
      this.check("the new account is the one in use", "FourthPlayer".equals(store.active().orElseThrow().gamertag()));
      this.screenshot("accounts-signin-done");
      XboxAccountStore.Entry fourth = store.active().orElseThrow();
      this.onClient(
         () -> {
            this.minecraft
               .gui
               .setScreen(
                  AccountsScreen.forFailure(
                     multiplayer, new XboxAccountStore.AccountException(XboxAccountStore.Problem.SIGN_IN_REQUIRED, fourth, "invalid_grant", null)
                  )
               );
            return null;
         }
      );
      this.sleep(600L);
      AccountsScreen expired = (AccountsScreen)this.screen();
      this.check(
         "an expired sign-in opens the account screen on that account with the message",
         expired.selected() != null
            && expired.selected().id().equals(fourth.id())
            && expired.notice().getString().contains("FourthPlayer")
            && !expired.signingIn()
      );
      this.screenshot("accounts-expired");
      this.onClient(
         () -> {
            this.minecraft
               .gui
               .setScreen(
                  AccountsScreen.forFailure(
                     multiplayer, new XboxAccountStore.AccountException(XboxAccountStore.Problem.FAILED, fourth, "Couldn't reach Microsoft", null)
                  )
               );
            return null;
         }
      );
      this.sleep(600L);
      this.screenshot("accounts-renew-failed");
      CompletableFuture<Boolean> leave = new CompletableFuture<>();
      AtomicBoolean leaveInterrupted = new AtomicBoolean();
      AccountManager.useSigner(waiting("JK90LM12", leave, leaveInterrupted, "Never", "0"));
      this.onClient(
         () -> {
            this.minecraft
               .gui
               .setScreen(
                  AccountsScreen.forFailure(multiplayer, new XboxAccountStore.AccountException(XboxAccountStore.Problem.NONE_ACTIVE, null, "none", null))
               );
            return null;
         }
      );
      this.waitFor(
         "the sign-in opened for a missing account",
         () -> this.onClient(() -> this.minecraft.gui.screen() instanceof AccountsScreen shown && shown.signingIn())
      );
      this.sleep(500L);
      this.check(
         "joining without an account opens the sign-in", this.onClient(() -> this.minecraft.gui.screen() instanceof AccountsScreen shown && shown.signingIn())
      );
      this.screenshot("accounts-none");
      this.onClient(() -> {
         this.minecraft.gui.setScreen(title);
         return null;
      });
      this.check("leaving the screen while signing in interrupts the sign-in thread", this.waitUntil(leaveInterrupted::get, 2000L));
      this.check("leaving the screen ends the sign-in thread", this.waitUntil(() -> !signInThreadAlive(), 2000L));
      leave.complete(true);
      this.sleep(500L);
      this.check(
         "leaving the screen while signing in cancels it, nothing is added",
         store.accounts().size() == 3 && store.accounts().stream().noneMatch(entry -> "Never".equals(entry.gamertag()))
      );
      this.windowPanel(title, store);
      this.bedrockScreens(title, store);
      this.vaultInGame();
      this.helperOnLocalPage();
      AccountManager.useStore(null);
      AccountManager.useSigner(XboxAccount::signIn);
      this.check("the player's real list is unchanged", realBefore.equals(fingerprint(configFolder)));
      deleteTree(scratch);
      this.onClient(() -> {
         this.minecraft.gui.setScreen(title);
         return null;
      });
      this.sleep(500L);
      this.screenshot("accounts-title-after");
   }

   private void windowPanel(TitleScreen title, XboxAccountStore store) throws Exception {
      AccountsScreen screen = this.onClient(() -> {
         AccountsScreen opened = new AccountsScreen(title);
         this.minecraft.gui.setScreen(opened);
         return opened;
      });
      this.sleep(500L);
      AtomicBoolean sawCodeInstead = new AtomicBoolean();
      AtomicBoolean codeInterrupted = new AtomicBoolean();
      AccountManager.useSigner(
         prompt -> {
            XboxBrowserSignIn.Listener window = prompt.window();
            window.opened(RedirectCapture.Window.HELPER);

            while (!window.codeInstead()) {
               Thread.sleep(50L);
            }

            sawCodeInstead.set(true);
            prompt.requestingCode();
            prompt.show(
               XboxTitle.ANDROID,
               new MsaDeviceCode(System.currentTimeMillis() + 900000L, 5000L, "device", "WX12YZ34", "https://www.microsoft.com/link"),
               DeviceCodeLink.direct("https://www.microsoft.com/link", "WX12YZ34", null)
            );

            try {
               Thread.sleep(600000L);
            } catch (InterruptedException e) {
               codeInterrupted.set(true);
               throw e;
            }

            return synthetic("Never", "0");
         }
      );
      this.onClient(() -> {
         press(this.button(screen, "bedrocknative.accounts.add"));
         return null;
      });
      this.waitFor(
         "the window panel",
         () -> this.onClient(() -> screen.signInSnapshot() != null && screen.signInSnapshot().state() == XboxSignInFlow.State.WAITING_FOR_WINDOW)
      );
      this.sleep(500L);
      XboxSignInFlow.Snapshot waiting = this.onClient(screen::signInSnapshot);
      this.check("Add opens the window part: Signing in, with the sign-in window named", waiting != null && waiting.window() == RedirectCapture.Window.HELPER);
      this.check(
         "the window panel has Use a code instead and Cancel, no code buttons",
         this.enabled(screen, "bedrocknative.accounts.code_instead")
            && this.enabled(screen, "gui.cancel")
            && !this.enabled(screen, "bedrocknative.accounts.copy_code")
      );
      this.screenshot("accounts-window");
      this.onClient(() -> {
         press(this.button(screen, "bedrocknative.accounts.code_instead"));
         return null;
      });
      this.waitFor("the code panel after Use a code instead", () -> this.enabled(screen, "bedrocknative.accounts.copy_code"));
      this.sleep(500L);
      this.check(
         "Use a code instead reaches the sign-in and the code panel follows",
         sawCodeInstead.get() && "WX12YZ34".equals(this.onClient(screen::signInSnapshot).code())
      );
      this.screenshot("accounts-window-code");
      this.onClient(() -> {
         press(this.button(screen, "gui.cancel"));
         return null;
      });
      this.check("Cancel after the switch ends the code wait", this.waitUntil(codeInterrupted::get, 2000L) && this.waitUntil(() -> !signInThreadAlive(), 2000L));
      AtomicBoolean windowInterrupted = new AtomicBoolean();
      AccountManager.useSigner(prompt -> {
         prompt.window().opened(RedirectCapture.Window.BROWSER_APP);

         try {
            Thread.sleep(600000L);
         } catch (InterruptedException e) {
            windowInterrupted.set(true);
            throw e;
         }

         return synthetic("Never", "0");
      });
      this.onClient(() -> {
         press(this.button(screen, "bedrocknative.accounts.add"));
         return null;
      });
      this.waitFor(
         "the browser panel",
         () -> this.onClient(() -> screen.signInSnapshot() != null && screen.signInSnapshot().window() == RedirectCapture.Window.BROWSER_APP)
      );
      this.sleep(500L);
      this.screenshot("accounts-window-browser");
      this.onClient(() -> {
         press(this.button(screen, "gui.cancel"));
         return null;
      });
      this.check(
         "Cancel during the window part interrupts it (the real window closes then)",
         this.waitUntil(windowInterrupted::get, 2000L) && this.waitUntil(() -> !signInThreadAlive(), 2000L) && !this.onClient(screen::signingIn)
      );
      AccountManager.useSigner(prompt -> {
         prompt.window().opened(RedirectCapture.Window.HELPER);
         throw new SignInFailure(SignInFailure.Reason.INCOMPLETE, "the redirect belongs to another sign-in (state)");
      });
      this.onClient(() -> {
         press(this.button(screen, "bedrocknative.accounts.add"));
         return null;
      });
      this.waitFor("the failed window sign-in", () -> !this.onClient(screen::signingIn));
      this.sleep(500L);
      this.check(
         "a window failure shows its row of the message table and Try again",
         screen.noticeProblem() != null && screen.noticeProblem().problem() == XboxProblem.SIGN_IN_INCOMPLETE && screen.problemButton() != null
      );
      this.screenshot("accounts-window-incomplete");
      this.check("no window sign-in added an account", store.accounts().stream().noneMatch(entry -> "Never".equals(entry.gamertag())));
   }

   private void bedrockScreens(TitleScreen title, XboxAccountStore store) throws Exception {
      if (BedrockInstall.vanillaResourcePack().isEmpty()) {
         log("no Bedrock install: Bedrock's sign-in screens are not checked");
      } else {
         JsonUi.Mode before = JsonUi.mode();
         this.run(() -> {
            JsonUi.setMode(JsonUi.Mode.ON);
            JsonUi.definitions();
         });

         try {
            this.waitFor("the JSON UI definitions", () -> this.onClient(() -> JsonUi.definitions() != null));
            AccountsScreen screen = this.onClient(() -> {
               AccountsScreen opened = new AccountsScreen(title);
               this.minecraft.gui.setScreen(opened);
               return opened;
            });
            this.sleep(500L);
            AtomicBoolean codeInterrupted = new AtomicBoolean();
            AccountManager.useSigner(
               prompt -> {
                  XboxBrowserSignIn.Listener window = prompt.window();
                  window.opened(RedirectCapture.Window.HELPER);

                  while (!window.codeInstead()) {
                     Thread.sleep(50L);
                  }

                  prompt.requestingCode();
                  prompt.show(
                     XboxTitle.ANDROID,
                     new MsaDeviceCode(System.currentTimeMillis() + 900000L, 5000L, "device", "QR12AB34", "https://www.microsoft.com/link"),
                     DeviceCodeLink.direct("https://www.microsoft.com/link", "QR12AB34", null)
                  );

                  try {
                     Thread.sleep(600000L);
                  } catch (InterruptedException e) {
                     codeInterrupted.set(true);
                     throw e;
                  }

                  return synthetic("Never", "0");
               }
            );
            this.onClient(() -> {
               press(this.button(screen, "bedrocknative.accounts.add"));
               return null;
            });
            this.waitFor("Bedrock's waiting modal", () -> this.onClient(() -> screen.bedrockView() == SignInData.View.WAITING));
            this.sleep(700L);
            SignInData waiting = this.onClient(screen::bedrockData);
            this.check("the window part shows Bedrock's modal with Signing in and where to finish", waiting != null && waiting.body().contains("\n\n"));
            this.screenshot("accounts-bedrock-waiting");
            this.clickBedrock(screen, "left");
            this.waitFor("the QR code screen after Use a code instead", () -> this.onClient(() -> screen.bedrockView() == SignInData.View.CODE));
            this.sleep(700L);
            SignInData code = this.onClient(screen::bedrockData);
            QrCode qr = code == null ? null : code.qr();
            QrCode expected = QrCode.encode("https://www.microsoft.com/link?otc=QR12AB34", SignInData.QR_LEVEL);
            this.check("the QR code screen's code is the link with the code filled in", qr != null && sameModules(qr, expected));
            this.check(
               "the address and the code show on Bedrock's screen",
               this.onClient(() -> screen.bedrockControl("url_label") != null && screen.bedrockControl("code_label") != null)
            );
            this.screenshot("accounts-bedrock-qr");
            this.run(() -> this.minecraft.keyboardHandler.setClipboard(""));
            this.clickBedrock(screen, "banner_2");
            this.check("a click on the code copies it", "QR12AB34".equals(this.onClient(() -> this.minecraft.keyboardHandler.getClipboard())));
            this.run(() -> screen.keyPressed(new KeyEvent(41, 0, 0)));
            this.check(
               "Esc on the QR code screen cancels the sign-in",
               this.waitUntil(codeInterrupted::get, 2000L)
                  && this.waitUntil(() -> !signInThreadAlive(), 2000L)
                  && this.waitUntil(() -> this.onClient(() -> !screen.signingIn() && screen.bedrockView() == null), 2000L)
            );
            AccountManager.useSigner(
               prompt -> {
                  prompt.show(
                     XboxTitle.ANDROID, new MsaDeviceCode(System.currentTimeMillis() + 900000L, 5000L, "device", "WE12LC34", "https://www.microsoft.com/link")
                  );
                  Thread.sleep(1500L);
                  return synthetic("WelcomePlayer", "7007");
               }
            );
            this.onClient(() -> {
               press(this.button(screen, "bedrocknative.accounts.add"));
               return null;
            });
            this.waitFor("Bedrock's welcome screen", () -> this.onClient(() -> screen.bedrockView() == SignInData.View.WELCOME));
            this.sleep(700L);
            SignInData welcome = this.onClient(screen::bedrockData);
            this.check("a finished sign-in shows Bedrock's welcome screen with the gamertag", welcome != null && "WelcomePlayer".equals(welcome.gamertag()));
            this.screenshot("accounts-bedrock-welcome");
            this.clickBedrock(screen, "play");
            this.waitFor("the list after Let's Play", () -> this.onClient(() -> screen.bedrockView() == null));
            this.check("Let's Play goes to the list with the new account", this.names(screen).contains("WelcomePlayer"));
            AccountManager.useSigner(prompt -> {
               prompt.window().opened(RedirectCapture.Window.HELPER);
               throw new SignInFailure(SignInFailure.Reason.INCOMPLETE, "the redirect belongs to another sign-in (state)");
            });
            this.onClient(() -> {
               press(this.button(screen, "bedrocknative.accounts.add"));
               return null;
            });
            this.waitFor("Bedrock's problem modal", () -> this.onClient(() -> screen.bedrockView() == SignInData.View.PROBLEM));
            this.sleep(700L);
            SignInData problem = this.onClient(screen::bedrockData);
            this.check("a failed sign-in shows Bedrock's modal with the message table's text", problem != null && !problem.body().isBlank());
            this.screenshot("accounts-bedrock-problem");
            this.clickBedrock(screen, "right");
            this.waitFor("the list after Close", () -> this.onClient(() -> screen.bedrockView() == null));
            this.check("Close goes to the list with the problem's notice and button", screen.noticeProblem() != null && screen.problemButton() != null);
            this.screenshot("accounts-bedrock-closed");
         } finally {
            this.run(() -> JsonUi.setMode(before));
         }
      }
   }

   private static boolean sameModules(QrCode a, QrCode b) {
      if (a.size() != b.size()) {
         return false;
      }

      for (int y = 0; y < a.size(); y++) {
         for (int x = 0; x < a.size(); x++) {
            if (a.dark(x, y) != b.dark(x, y)) {
               return false;
            }
         }
      }

      return true;
   }

   private void clickBedrock(AccountsScreen screen, String name) {
      UiControl control = this.onClient(() -> screen.bedrockControl(name));
      if (control == null) {
         this.check("Bedrock's sign-in screen has " + name, false);
      } else {
         this.run(() -> {
            float[] gui = JsonUiScreen.toGui(control.x() + control.width() / 2.0F, control.y() + control.height() / 2.0F);
            MouseButtonEvent event = new MouseButtonEvent(gui[0], gui[1], new MouseButtonInfo(1, 0));
            screen.mouseMoved(gui[0], gui[1]);
            screen.mouseClicked(event, false);
            screen.mouseReleased(event);
         });
         this.sleep(300L);
      }
   }

   private void vaultInGame() throws Exception {
      Path folder = this.minecraft.gameDirectory.toPath().resolve("devtest-accounts").resolve("vaulted");
      Path vaultRoot = this.minecraft.gameDirectory.toPath().resolve("devtest-accounts").resolve("vault");
      Files.createDirectories(folder);
      XboxAccount.save(synthetic("VaultPlayer", "8008"), folder.resolve("account.json"));
      TokenVault vault = TokenVault.at(vaultRoot, Protection.standard(), XboxAccount::freshness);
      XboxAccountStore store = new XboxAccountStore(folder, vault);
      XboxAccountStore.Entry entry = store.active().orElse(null);
      this.check(
         "the game's store moves an old token file into the vault",
         entry != null && "vault:xuid-8008".equals(entry.file()) && !Files.exists(folder.resolve("account.json")) && vault.hasBackup("xuid-8008")
      );

      boolean loads;
      try {
         loads = entry != null && "VaultPlayer".equals(store.load(entry.id()).getMinecraftMultiplayerToken().getCached().getDisplayName());
      } catch (XboxAccountStore.AccountException e) {
         loads = false;
      }

      this.check(
         "the vault copy opens in the game ("
            + (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win") ? "Windows DPAPI" : "owner-only file")
            + ")",
         loads
      );
      String stored = Files.exists(vault.file("xuid-8008")) ? new String(Files.readAllBytes(vault.file("xuid-8008")), StandardCharsets.ISO_8859_1) : "";
      this.check(
         "no token is readable in the vault file",
         !stored.isEmpty() && !stored.contains("made-up") && !stored.contains("VaultPlayer")
            || !System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")
      );
   }

   private void helperOnLocalPage() throws Exception {
      for (RedirectCapture way : WindowSignIns.system(line -> {})) {
         String reason = way.unavailable();
         log("on this PC, {}: {}", way.name(), reason == null ? "available" : "not used (" + reason + ")");
      }

      HelperCapture capture = WindowSignIns.devHelper();
      String unavailable = capture.unavailable();
      if (unavailable != null) {
         log("the sign-in window: SKIP ({})", unavailable);
      } else {
         AuthCodeRequest request = AuthCodeRequest.create(XboxTitle.ANDROID.clientId(), "service::user.auth.xboxlive.com::MBI_SSL", new SecureRandom());
         String state = request.url().replaceAll(".*[?&]state=([^&]+).*", "$1");
         AtomicInteger redirects = new AtomicInteger();
         HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
         server.createContext(
            "/",
            exchange -> {
               byte[] pagex = ("<!doctype html><html><head><meta charset=\"utf-8\"><title>BedrockNative test page</title></head><body style=\"font-family:sans-serif;background:#20242a;color:#e8e8e8;text-align:center;padding-top:90px\"><h2>BedrockNative sign-in window test</h2><p>A local test page on 127.0.0.1.<br>No Microsoft page, no sign-in.</p>"
                     + (
                        exchange.getRequestURI().getPath().equals("/stay")
                           ? "<p>This page stays until the game cancels.</p>"
                           : "<p>Back to the game in 8 seconds...</p><script>setTimeout(function(){location.href='/redirect'},8000)</script>"
                     )
                     + "</body></html>")
                  .getBytes(StandardCharsets.UTF_8);
               exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
               exchange.sendResponseHeaders(200, pagex.length);
               exchange.getResponseBody().write(pagex);
               exchange.close();
            }
         );
         server.createContext("/redirect", exchange -> {
            redirects.incrementAndGet();
            exchange.getResponseHeaders().add("Location", "ms-xal-0000000048183522://auth?code=DEV-TEST-NOT-A-REAL-CODE&state=" + state);
            exchange.sendResponseHeaders(302, -1L);
            exchange.close();
         });
         server.start();

         try {
            String page = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
            DevAccountsTest.DevControl control = new DevAccountsTest.DevControl(request::ours);
            long started = System.currentTimeMillis();
            RedirectCapture.Outcome outcome = capture.capture(page, control);
            log("the sign-in window ended after {} ms: {}", System.currentTimeMillis() - started, outcome);
            String code = null;

            try {
               code = outcome.kind() == RedirectCapture.Outcome.Kind.REDIRECT ? request.read(outcome.redirect()).code() : null;
            } catch (SignInFailure e) {
               log("the handed-over address was refused: {}", e.getMessage());
            }

            this.check(
               "the sign-in window caught the 302 to ms-xal and handed it over with this sign-in's state",
               "DEV-TEST-NOT-A-REAL-CODE".equals(code) && redirects.get() == 1 && control.opened.get()
            );
            this.check("the sign-in window closed by itself and left no folder", this.waitUntil(() -> helpersRunning() == 0L, 8000L) && runFolders() == 0L);
            AtomicReference<Throwable> thrown = new AtomicReference<>();
            DevAccountsTest.DevControl stay = new DevAccountsTest.DevControl(request::ours);
            Thread waiting = Thread.ofVirtual().start(() -> {
               try {
                  capture.capture(page + "stay", stay);
               } catch (Throwable e) {
                  thrown.set(e);
               }
            });
            this.waitUntil(stay.opened::get, 10000L);
            this.sleep(4000L);
            log("cancelling the open sign-in window");
            waiting.interrupt();
            waiting.join(10000L);
            this.check(
               "Cancel closes the real sign-in window",
               thrown.get() instanceof InterruptedException
                  && this.waitUntil(() -> helpersRunning() == 0L, 8000L)
                  && this.waitUntil(() -> runFolders() == 0L, 8000L)
            );
            DevAccountsTest.DevControl last = new DevAccountsTest.DevControl(request::ours);
            Thread.ofVirtual().start(() -> {
               try {
                  capture.capture(page + "stay", last);
               } catch (Throwable var4x) {
               }
            });
            this.waitUntil(last.opened::get, 10000L);
            this.sleep(2000L);
            log("a sign-in window stays open for the game's exit ({} running)", helpersRunning());
         } finally {
            server.stop(0);
         }
      }
   }

   private static long helpersRunning() {
      return ProcessHandle.allProcesses().filter(process -> process.info().command().map(command -> command.endsWith("AuthWebView.exe")).orElse(false)).count();
   }

   private static long runFolders() {
      try (DirectoryStream<Path> list = Files.newDirectoryStream(Path.of(System.getProperty("java.io.tmpdir")), "bnsi-" + ProcessHandle.current().pid() + "-*")) {
         long count = 0L;

         for (Path ignored : list) {
            count++;
         }

         return count;
      } catch (IOException e) {
         return -1L;
      }
   }

   private static XboxSignInFlow.Signer waiting(String code, CompletableFuture<Boolean> release, AtomicBoolean interrupted, String gamertag, String xuid) {
      return prompt -> {
         prompt.show(
            XboxTitle.ANDROID,
            new MsaDeviceCode(System.currentTimeMillis() + 900000L, 5000L, "device", code, "https://www.microsoft.com/link"),
            DeviceCodeLink.direct("https://www.microsoft.com/link", code, null)
         );

         try {
            release.get();
         } catch (InterruptedException e) {
            interrupted.set(true);
            throw e;
         }

         return synthetic(gamertag, xuid);
      };
   }

   private static boolean signInThreadAlive() {
      return Thread.getAllStackTraces().keySet().stream().anyMatch(thread -> thread.isAlive() && thread.getName().equals("Xbox sign-in"));
   }

   private boolean waitUntil(BooleanSupplier condition, long millis) {
      long deadline = System.currentTimeMillis() + millis;

      while (!condition.getAsBoolean()) {
         if (System.currentTimeMillis() > deadline) {
            return false;
         }

         this.sleep(50L);
      }

      return true;
   }

   private AccountScreenHooks.AccountButton accountButton(Screen screen) {
      return this.onClient(
         () -> Screens.getWidgets(screen)
            .stream()
            .filter(AccountScreenHooks.AccountButton.class::isInstance)
            .map(AccountScreenHooks.AccountButton.class::cast)
            .findFirst()
            .orElse(null)
      );
   }

   private Button button(AccountsScreen screen, String key) {
      return this.onClient(
         () -> Screens.getWidgets(screen)
            .stream()
            .filter(Button.class::isInstance)
            .map(Button.class::cast)
            .filter(button -> button.getMessage().getString().equals(Component.translatable(key).getString()))
            .findFirst()
            .orElseThrow()
      );
   }

   private static void press(AbstractWidget button) {
      ((Button)button).onPress(new MouseButtonEvent(0.0, 0.0, new MouseButtonInfo(0, 0)));
   }

   private Screen screen() {
      return this.onClient(() -> this.minecraft.gui.screen());
   }

   private List<String> names(AccountsScreen screen) {
      return this.onClient(() -> screen.listed().stream().map(XboxAccountStore.Entry::gamertag).toList());
   }

   private boolean enabled(AccountsScreen screen, String key) {
      return this.onClient(
         () -> {
            try {
               return Screens.getWidgets(screen)
                  .stream()
                  .filter(Button.class::isInstance)
                  .map(Button.class::cast)
                  .anyMatch(button -> button.active && button.getMessage().getString().equals(Component.translatable(key).getString()));
            } catch (RuntimeException e) {
               return false;
            }
         }
      );
   }

   private static BedrockAuthManager synthetic(String gamertag, String xuid) {
      BedrockAuthManager account = BedrockAuthManager.create(MinecraftAuth.createHttpClient("BedrockNative dev test"), "1.26.63")
         .msaApplicationConfig(XboxTitle.ANDROID.msaConfig())
         .deviceType(XboxTitle.ANDROID.deviceType())
         .login(new MsaToken(System.currentTimeMillis() + 3600000L, "made-up-access-token", "made-up-refresh-token"));
      Encoder encoder = Base64.getUrlEncoder().withoutPadding();
      String jwt = encoder.encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8))
         + "."
         + encoder.encodeToString(
            ("{\"xname\":\"" + gamertag + "\",\"xid\":\"" + xuid + "\",\"exp\":" + (System.currentTimeMillis() / 1000L + 3600L) + "}")
               .getBytes(StandardCharsets.UTF_8)
         )
         + "."
         + encoder.encodeToString(new byte[]{1, 2, 3});
      account.getMinecraftMultiplayerToken().set(new MinecraftMultiplayerToken(System.currentTimeMillis() + 3600000L, jwt));
      return account;
   }

   private static String fingerprint(Path configFolder) throws Exception {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      List<Path> files = new ArrayList<>();

      for (String name : List.of("accounts.json", "account.json")) {
         files.add(configFolder.resolve(name));
      }

      Path folder = configFolder.resolve("accounts");
      if (Files.isDirectory(folder)) {
         try (Stream<Path> stream = Files.list(folder)) {
            stream.sorted().forEach(files::add);
         }
      }

      for (Path file : files) {
         digest.update(file.getFileName().toString().getBytes(StandardCharsets.UTF_8));
         if (Files.isRegularFile(file)) {
            digest.update(Files.readAllBytes(file));
         }
      }

      return HexFormat.of().formatHex(digest.digest());
   }

   private static void deleteTree(Path folder) throws IOException {
      if (Files.exists(folder)) {
         try (Stream<Path> stream = Files.walk(folder)) {
            for (Path path : stream.sorted(Comparator.reverseOrder()).toList()) {
               Files.delete(path);
            }
         }
      }
   }

   private void check(String what, boolean ok) {
      if (ok) {
         this.passed++;
      } else {
         this.failed++;
      }

      BedrockNative.LOGGER.info("Dev accounts test: {} {}", ok ? "PASS" : "FAIL", what);
   }

   private void screenshot(String name) {
      this.run(() -> Screenshot.grab(this.minecraft.gameDirectory, name + ".png", this.minecraft.gameRenderer.mainRenderTarget(), 1, message -> {}));
      this.sleep(500L);
   }

   private void waitFor(String what, BooleanSupplier done) {
      long started = System.currentTimeMillis();

      while (!done.getAsBoolean()) {
         if (System.currentTimeMillis() - started > 1200000L) {
            BedrockNative.LOGGER.info("Dev accounts test: FAIL waited too long for {}", what);
            this.failed++;
            return;
         }

         this.sleep(100L);
      }
   }

   private <T> T onClient(Supplier<T> task) {
      return this.minecraft.isSameThread() ? task.get() : CompletableFuture.supplyAsync(task, this.minecraft::execute).join();
   }

   private void run(Runnable task) {
      this.onClient(() -> {
         task.run();
         return null;
      });
   }

   private void sleep(long millis) {
      try {
         Thread.sleep(millis);
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
      }
   }

   private static void log(String message, Object... args) {
      BedrockNative.LOGGER.info("Dev accounts test: " + message, args);
   }

   private static final class DevControl implements RedirectCapture.Control {
      final AtomicBoolean opened = new AtomicBoolean();
      private final Predicate<String> ours;

      DevControl(Predicate<String> ours) {
         this.ours = ours;
      }

      @Override
      public void opened(RedirectCapture.Window window) {
         this.opened.set(true);
         DevAccountsTest.log("the sign-in window is open ({})", window);
      }

      @Override
      public boolean codeInstead() {
         return false;
      }

      @Override
      public boolean ours(String redirect) {
         return this.ours.test(redirect);
      }

      @Override
      public boolean openInBrowser(String url) {
         return false;
      }

      @Override
      public String windowTitle() {
         return "BedrockNative test";
      }

      @Override
      public String language() {
         return "en-US";
      }
   }

   private static final class RecordingActions implements SignInActions {
      final List<String> opened = new CopyOnWriteArrayList<>();
      final List<String> copied = new CopyOnWriteArrayList<>();
      volatile boolean browserWorks = true;

      @Override
      public boolean openBrowser(String uri) {
         this.opened.add(uri);
         return this.browserWorks;
      }

      @Override
      public void copyToClipboard(String text) {
         this.copied.add(text);
      }
   }
}
