package net.bedrocknative.net.auth;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import net.bedrocknative.net.auth.window.SignInFailure;
import net.lenni0451.commons.httpclient.HttpClient;
import net.raphimc.minecraftauth.MinecraftAuth;
import net.raphimc.minecraftauth.bedrock.BedrockAuthManager;
import net.raphimc.minecraftauth.msa.exception.MsaRequestException;
import net.raphimc.minecraftauth.msa.model.MsaDeviceCode;
import net.raphimc.minecraftauth.msa.model.MsaToken;
import net.raphimc.minecraftauth.util.holder.Holder;
import org.jspecify.annotations.Nullable;

public final class XboxAccount {
   private static final HttpClient HTTP = MinecraftAuth.createHttpClient("BedrockNative");

   private XboxAccount() {
   }

   public static BedrockAuthManager signIn(XboxAccount.CodePrompt prompt) throws IOException, InterruptedException, TimeoutException {
      return signIn(XboxTitle.DEFAULT, prompt);
   }

   public static BedrockAuthManager signIn(XboxTitle title, XboxAccount.CodePrompt prompt) throws IOException, InterruptedException, TimeoutException {
      XboxBrowserSignIn.Listener window = prompt.window();
      if (title == XboxTitle.ANDROID && window != null) {
         Optional<String> refreshToken = XboxBrowserSignIn.standard().signIn(window);
         if (refreshToken.isPresent()) {
            BedrockAuthManager account = withRefreshToken(title, refreshToken.get());
            account.getMinecraftMultiplayerToken().getUpToDate();
            return account;
         }
      }

      prompt.requestingCode();
      BedrockAuthManager account = BedrockAuthManager.create(HTTP, "1.26.63")
         .msaApplicationConfig(title.msaConfig())
         .deviceType(title.deviceType())
         .login((http, config) -> new BrowserDeviceCodeAuthService(http, config, (code, directUri) -> prompt.show(title, code, directUri)));
      account.getMinecraftMultiplayerToken().getUpToDate();
      return account;
   }

   static BedrockAuthManager withRefreshToken(XboxTitle title, String refreshToken) throws IOException {
      try {
         return BedrockAuthManager.create(HTTP, "1.26.63").msaApplicationConfig(title.msaConfig()).deviceType(title.deviceType()).login(refreshToken);
      } catch (MsaRequestException e) {
         throw new SignInFailure(SignInFailure.Reason.INCOMPLETE, "Microsoft refused the new sign-in's first renewal", e.getError());
      }
   }

   public static BedrockAuthManager load(Path file) throws IOException {
      return fromJson(Files.readAllBytes(file));
   }

   public static void save(BedrockAuthManager account, Path file) throws IOException {
      Files.createDirectories(file.toAbsolutePath().getParent());
      Path temp = file.resolveSibling(file.getFileName() + ".tmp");
      Files.write(temp, toJson(account));
      Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
   }

   public static BedrockAuthManager fromJson(byte[] json) {
      JsonObject object = JsonParser.parseString(new String(json, StandardCharsets.UTF_8)).getAsJsonObject();
      return BedrockAuthManager.fromJson(HTTP, "1.26.63", object);
   }

   public static byte[] toJson(BedrockAuthManager account) {
      return BedrockAuthManager.toJson(account).toString().getBytes(StandardCharsets.UTF_8);
   }

   public static long freshness(byte[] json) {
      try {
         Holder<MsaToken> token = fromJson(json).getMsaToken();
         return token.hasValue() ? token.getCached().getExpireTimeMs() : 0L;
      } catch (RuntimeException e) {
         return 0L;
      }
   }

   public static String describe(BedrockAuthManager account) {
      XboxTitle title = XboxTitle.of(account);
      String claims = account.getMinecraftMultiplayerToken().hasValue()
         ? MultiplayerTokenClaims.of(account.getMinecraftMultiplayerToken().getCached().getToken()).toString()
         : "none yet";
      return title + " title (Xbox device " + account.getDeviceType() + "), multiplayer token " + claims;
   }

   public interface CodePrompt {
      void show(XboxTitle var1, MsaDeviceCode var2);

      default void show(XboxTitle title, MsaDeviceCode code, String directUri) {
         this.show(title, code);
      }

      default XboxBrowserSignIn.@Nullable Listener window() {
         return null;
      }

      default void requestingCode() {
      }
   }
}
