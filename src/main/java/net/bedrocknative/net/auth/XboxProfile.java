package net.bedrocknative.net.auth;

import java.net.URI;
import java.util.Locale;
import java.util.Map;
import net.raphimc.minecraftauth.bedrock.BedrockAuthManager;
import net.raphimc.minecraftauth.xbl.model.XblUserProfile;
import net.raphimc.minecraftauth.xbl.request.XblUserProfileSettingsRequest;
import org.jspecify.annotations.Nullable;

public record XboxProfile(@Nullable String gamerscore, @Nullable String pictureUrl) {
   public static final XboxProfile NONE = new XboxProfile(null, null);
   private static final int MAX_URL = 2048;

   public static XboxProfile fetch(BedrockAuthManager account) throws Exception {
      XblUserProfile profile = account.getHttpClient()
         .executeAndHandle(new XblUserProfileSettingsRequest(account.getXboxLiveXstsToken().getUpToDate(), "me", "Gamerscore", "GameDisplayPicRaw"));
      return of(profile.getSettings());
   }

   static XboxProfile of(Map<String, String> settings) {
      String score = settings.get("Gamerscore");
      if (score != null && !score.matches("[0-9]{1,10}")) {
         score = null;
      }

      return new XboxProfile(score, picture(settings.get("GameDisplayPicRaw")));
   }

   @Nullable
   static String picture(@Nullable String raw) {
      if (raw != null && raw.length() <= 2048) {
         try {
            URI uri = URI.create(raw);
            String host = uri.getHost();
            if ("https".equalsIgnoreCase(uri.getScheme())
               && host != null
               && uri.getUserInfo() == null
               && uri.getPort() == -1
               && host.toLowerCase(Locale.ROOT).endsWith(".xboxlive.com")) {
               String query = uri.getRawQuery();
               return query != null && !query.contains("&w=") && !query.startsWith("w=") ? raw + "&w=64&h=64" : raw;
            } else {
               return null;
            }
         } catch (IllegalArgumentException var4) {
            return null;
         }
      } else {
         return null;
      }
   }
}
