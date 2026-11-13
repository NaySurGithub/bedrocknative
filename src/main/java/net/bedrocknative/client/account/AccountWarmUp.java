package net.bedrocknative.client.account;

import java.util.Locale;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.net.BedrockUse;
import net.bedrocknative.net.auth.TokenWarmUp;
import net.bedrocknative.net.auth.XboxAccountStore;
import net.bedrocknative.perf.StartupClock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.raphimc.minecraftauth.bedrock.BedrockAuthManager;
import net.raphimc.minecraftauth.bedrock.model.MinecraftMultiplayerToken;
import net.raphimc.minecraftauth.util.holder.Holder;
import org.jspecify.annotations.Nullable;

public final class AccountWarmUp {
   private static final boolean ENABLED = !"off".equalsIgnoreCase(System.getProperty("bedrocknative.accountWarmUp", "on").trim())
      && !"accounts".equals(System.getProperty("bedrocknative.devTest", ""));
   private static final int CHECK_TICKS = 600;
   static final TokenWarmUp WARM_UP = new TokenWarmUp(
      System::currentTimeMillis, work -> Thread.ofVirtual().name("Xbox account warm-up").start(work), AccountWarmUp::renew, BedrockNative.LOGGER::info
   );
   private static boolean firstDone;
   private static int ticks;

   private AccountWarmUp() {
   }

   public static void tick(Minecraft minecraft) {
      if (ENABLED
         && StartupClock.titleMillis() >= 0L
         && minecraft.level == null
         && minecraft.getConnection() == null
         && !(minecraft.gui.screen() instanceof ConnectScreen)) {
         if (!BedrockUse.known()) {
            BedrockUse.detectInBackground();
         } else if (!firstDone || ++ticks >= 600) {
            ticks = 0;
            if (AccountManager.storeLoaded()) {
               WARM_UP.ahead(AccountManager.activeIdIfLoaded(), firstDone ? "menus" : "title screen");
               firstDone = true;
            }
         }
      }
   }

   public static boolean running() {
      return ENABLED && WARM_UP.running();
   }

   private static TokenWarmUp.Result renew(String accountId) throws Exception {
      XboxAccountStore store = AccountManager.store();
      if (!store.active().map(entry -> entry.id().equals(accountId)).orElse(false)) {
         throw new IllegalStateException("another account is in use now");
      } else {
         BedrockAuthManager account = store.load(accountId);
         Holder<MinecraftMultiplayerToken> token = account.getMinecraftMultiplayerToken();
         boolean expired = !token.hasValue() || token.isExpired();
         XboxAccountStore.Prepared prepared = store.prepare(accountId);
         return new TokenWarmUp.Result(expiryOf(prepared.account()), expired);
      }
   }

   static long expiryOf(BedrockAuthManager account) {
      Holder<MinecraftMultiplayerToken> token = account.getMinecraftMultiplayerToken();
      return token.hasValue() ? token.getCached().getExpireTimeMs() : 0L;
   }

   public static String describe() {
      return !ENABLED ? "off" : (WARM_UP.running() ? "running" : String.format(Locale.ROOT, "%d done", WARM_UP.warmUps()));
   }

   static void known(@Nullable String accountId, BedrockAuthManager account) {
      if (accountId != null) {
         WARM_UP.known(accountId, expiryOf(account));
      }
   }
}
