package net.bedrocknative.net.auth;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

public final class DeviceCodeLink {
   private DeviceCodeLink() {
   }

   public static String direct(String verificationUri, String userCode, @Nullable String completeUri) {
      if (completeUri != null && !completeUri.isBlank()) {
         return completeUri;
      }

      String separator = verificationUri.contains("?") ? "&" : "?";
      return verificationUri + separator + "otc=" + URLEncoder.encode(userCode, StandardCharsets.UTF_8);
   }

   public static boolean isMicrosoftPage(String link) {
      try {
         URI uri = URI.create(link);
         String host = uri.getHost();
         if (host != null && "https".equalsIgnoreCase(uri.getScheme())) {
            host = host.toLowerCase(Locale.ROOT);
            return host.equals("microsoft.com")
               || host.endsWith(".microsoft.com")
               || host.equals("live.com")
               || host.endsWith(".live.com")
               || host.equals("microsoftonline.com")
               || host.endsWith(".microsoftonline.com");
         } else {
            return false;
         }
      } catch (IllegalArgumentException e) {
         return false;
      }
   }
}
