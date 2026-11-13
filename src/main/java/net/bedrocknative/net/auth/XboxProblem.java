package net.bedrocknative.net.auth;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.util.Locale;
import java.util.concurrent.TimeoutException;
import javax.net.ssl.SSLException;
import net.bedrocknative.net.auth.window.SignInFailure;
import net.lenni0451.commons.httpclient.HttpResponse;
import net.lenni0451.commons.httpclient.exceptions.HttpRequestException;
import net.raphimc.minecraftauth.msa.exception.MsaRequestException;
import net.raphimc.minecraftauth.xbl.exception.XblRequestException;
import org.jspecify.annotations.Nullable;

public enum XboxProblem {
   CODE_EXPIRED("code_expired", null, null, null, XboxProblem.Action.TRY_AGAIN),
   DECLINED("declined", null, null, null, XboxProblem.Action.TRY_AGAIN),
   SIGN_IN_INCOMPLETE("sign_in_incomplete", null, null, null, XboxProblem.Action.TRY_AGAIN),
   SIGN_IN_TIMEOUT("sign_in_timeout", null, null, null, XboxProblem.Action.TRY_AGAIN),
   OFFLINE("offline", "xbox.connection.error", null, null, XboxProblem.Action.TRY_AGAIN),
   SIGN_IN_EXPIRED("expired", "xbox.signinquestion", null, null, XboxProblem.Action.SIGN_IN),
   BANNED("banned", null, "hbui.PlayerBannedModalRoute.xboxLiveBody", "https://enforcement.xbox.com", XboxProblem.Action.LINK, 2148916227L, 2148916228L),
   PARENTAL("parental", null, "xbox.disconectionscreen.multiplayerNotAllowed", "https://account.microsoft.com/family", XboxProblem.Action.LINK, 2148916229L),
   NO_PROFILE("no_profile", null, null, "https://www.xbox.com/live", XboxProblem.Action.LINK, 2148916233L),
   TERMS("terms", null, null, "https://www.xbox.com/live", XboxProblem.Action.LINK, 2148916234L),
   REGION("region", null, null, null, XboxProblem.Action.NONE, 2148916235L),
   AGE_VERIFICATION("age_verification", null, null, "https://account.microsoft.com", XboxProblem.Action.LINK, 2148916236L),
   SCREEN_TIME("screen_time", null, null, "https://account.microsoft.com/family", XboxProblem.Action.LINK, 2148916237L),
   CHILD_FAMILY("child_family", null, null, "https://account.microsoft.com/family", XboxProblem.Action.LINK, 2148916238L),
   GAMERTAG("gamertag", null, null, "https://support.xbox.com/help/account-profile/profile/change-xbox-live-gamertag", XboxProblem.Action.LINK, 2148916243L),
   DEVICES("devices", null, null, null, XboxProblem.Action.TRY_AGAIN, 2148916246L, 2148916254L),
   MULTIPLAYER_BLOCKED("multiplayer_blocked", null, "networkWorld.multiplayerPrivilegesBlocked", "https://account.xbox.com/Settings", XboxProblem.Action.LINK),
   MULTIPLAYER_BANNED("multiplayer_banned", null, "networkWorld.multiplayerPrivilegesBlockedBanned", "https://enforcement.xbox.com", XboxProblem.Action.LINK),
   SERVICES("services", null, "disconnectionScreen.notAuthenticated", null, XboxProblem.Action.TRY_AGAIN),
   WIN32_TITLE("win32_title", null, null, null, XboxProblem.Action.NONE),
   OTHER("other", null, "xbox.failedsignin.body", "https://support.xbox.com", XboxProblem.Action.LINK);

   private final String id;
   @Nullable
   private final String bedrockTitleKey;
   @Nullable
   private final String bedrockBodyKey;
   @Nullable
   private final String link;
   private final XboxProblem.Action action;
   private final long[] codes;

   private XboxProblem(
      String id, @Nullable String bedrockTitleKey, @Nullable String bedrockBodyKey, @Nullable String link, XboxProblem.Action action, long... codes
   ) {
      this.id = id;
      this.bedrockTitleKey = bedrockTitleKey;
      this.bedrockBodyKey = bedrockBodyKey;
      this.link = link;
      this.action = action;
      this.codes = codes;
   }

   public String id() {
      return this.id;
   }

   public String langKey() {
      return "bedrocknative.accounts.problem." + this.id;
   }

   @Nullable
   public String bedrockTitleKey() {
      return this.bedrockTitleKey;
   }

   @Nullable
   public String bedrockBodyKey() {
      return this.bedrockBodyKey;
   }

   @Nullable
   public String link() {
      return this.link;
   }

   public XboxProblem.Action action() {
      return this.action;
   }

   public long[] codes() {
      return (long[])this.codes.clone();
   }

   public static boolean isFixedLink(@Nullable String link) {
      if (link == null) {
         return false;
      } else {
         for (XboxProblem problem : values()) {
            if (link.equals(problem.link)) {
               return true;
            }
         }

         return false;
      }
   }

   public static XboxProblem.Diagnosis fromXErr(long code) {
      long unsigned = code & 4294967295L;

      for (XboxProblem problem : values()) {
         for (long known : problem.codes) {
            if (known == unsigned) {
               return new XboxProblem.Diagnosis(problem, unsigned);
            }
         }
      }

      return new XboxProblem.Diagnosis(OTHER, unsigned);
   }

   @Nullable
   public static Long parseCode(@Nullable String text) {
      if (text == null) {
         return null;
      } else {
         String value = text.strip();

         try {
            if (!value.startsWith("0x") && !value.startsWith("0X")) {
               if (value.length() == 8
                  && value.chars().anyMatch(c -> Character.toUpperCase(c) >= 65 && Character.toUpperCase(c) <= 70)
                  && value.chars().allMatch(c -> Character.digit(c, 16) >= 0)) {
                  return Long.parseLong(value, 16);
               } else {
                  long number = Long.parseLong(value);
                  return number >= -2147483648L && number <= 4294967295L ? number & 4294967295L : null;
               }
            } else {
               return Long.parseLong(value.substring(2), 16) & 4294967295L;
            }
         } catch (NumberFormatException var4) {
            return null;
         }
      }
   }

   public static String hex(long code) {
      return "0x" + Long.toHexString(code & 4294967295L).toUpperCase(Locale.ROOT);
   }

   public static XboxProblem.Diagnosis classify(Throwable failure) {
      return classify(failure, null);
   }

   public static XboxProblem.Diagnosis classify(Throwable failure, @Nullable XboxTitle title) {
      for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
         if (cause instanceof SignInFailure window) {
            return XboxProblem.Diagnosis.of(switch (window.reason()) {
               case CLOSED, DECLINED -> DECLINED;
               case INCOMPLETE -> SIGN_IN_INCOMPLETE;
               case TIMED_OUT -> SIGN_IN_TIMEOUT;
            });
         }

         if (cause instanceof TimeoutException) {
            return XboxProblem.Diagnosis.of(CODE_EXPIRED);
         }

         if (cause instanceof BrowserDeviceCodeAuthService.DeclinedException) {
            return XboxProblem.Diagnosis.of(DECLINED);
         }

         if (cause instanceof XblRequestException xbl) {
            return fromXErr(xbl.getErrorCode());
         }

         if (cause instanceof MsaRequestException msa) {
            return temporaryOAuth(msa.getError()) ? XboxProblem.Diagnosis.of(OTHER) : XboxProblem.Diagnosis.of(SIGN_IN_EXPIRED);
         }

         if (cause instanceof HttpRequestException http) {
            return fromResponse(http, title);
         }

         if (offline(cause)) {
            return XboxProblem.Diagnosis.of(OFFLINE);
         }
      }

      return XboxProblem.Diagnosis.of(OTHER);
   }

   static boolean temporaryOAuth(@Nullable String error) {
      return error != null && (error.equals("temporarily_unavailable") || error.equals("server_error") || error.equals("slow_down"));
   }

   private static boolean offline(Throwable cause) {
      return cause instanceof UnknownHostException
         || cause instanceof ConnectException
         || cause instanceof NoRouteToHostException
         || cause instanceof SocketTimeoutException
         || cause instanceof SocketException
         || cause instanceof HttpTimeoutException
         || cause instanceof SSLException;
   }

   private static XboxProblem.Diagnosis fromResponse(HttpRequestException http, @Nullable XboxTitle title) {
      HttpResponse response = http.getResponse();
      if (response == null) {
         return XboxProblem.Diagnosis.of(OTHER);
      } else {
         Long code = response.getFirstHeader("X-Err").map(XboxProblem::parseCode).orElse(null);
         if (code != null) {
            return fromXErr(code);
         } else {
            String host = host(response.getURL());
            if (!host.endsWith("playfabapi.com") && !host.endsWith("minecraft-services.net")) {
               return title == XboxTitle.WIN32 && response.getStatusCode() == 401 && host.endsWith("xboxlive.com")
                  ? XboxProblem.Diagnosis.of(WIN32_TITLE)
                  : XboxProblem.Diagnosis.of(OTHER);
            } else {
               return XboxProblem.Diagnosis.of(SERVICES);
            }
         }
      }
   }

   private static String host(@Nullable URL url) {
      return url != null && url.getHost() != null ? url.getHost().toLowerCase(Locale.ROOT) : "";
   }

   public static enum Action {
      NONE,
      TRY_AGAIN,
      SIGN_IN,
      LINK;
   }

   public record Diagnosis(XboxProblem problem, long code) {
      public static XboxProblem.Diagnosis of(XboxProblem problem) {
         return new XboxProblem.Diagnosis(problem, 0L);
      }

      @Nullable
      public String errorCode() {
         return this.code == 0L ? null : XboxProblem.hex(this.code);
      }

      @Override
      public String toString() {
         return this.code == 0L ? this.problem.name() : this.problem.name() + " " + XboxProblem.hex(this.code);
      }
   }
}
