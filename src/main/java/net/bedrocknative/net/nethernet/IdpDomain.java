package net.bedrocknative.net.nethernet;

import java.net.URI;
import java.net.URISyntaxException;
import net.bedrocknative.net.auth.MultiplayerTokenClaims;

public record IdpDomain(String domain, boolean fromToken) {
   public static final String DEFAULT = "https://authorization.franchise.minecraft-services.net/";

   public static IdpDomain of(String token) {
      String issuer = MultiplayerTokenClaims.of(token).iss();
      if (issuer != null && issuer.startsWith("https://")) {
         try {
            URI uri = new URI(issuer);
            if (uri.getHost() != null && !uri.getHost().isEmpty()) {
               return new IdpDomain(issuer, true);
            }
         } catch (URISyntaxException var3) {
         }
      }

      return new IdpDomain("https://authorization.franchise.minecraft-services.net/", false);
   }

   public String describe() {
      return "NetherNet identity domain: "
         + this.domain
         + " ("
         + (this.fromToken ? "from token iss" : "default")
         + "; default equal: "
         + ("https://authorization.franchise.minecraft-services.net/".equals(this.domain) ? "yes" : "no")
         + ")";
   }
}
