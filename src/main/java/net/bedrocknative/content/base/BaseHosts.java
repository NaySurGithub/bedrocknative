package net.bedrocknative.content.base;

import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import java.util.Set;
import net.bedrocknative.content.UrlGuard;

public record BaseHosts(Set<String> hosts, boolean httpsOnly, UrlGuard guard) {
   public static final BaseHosts GITHUB = new BaseHosts(
      Set.of("raw.githubusercontent.com", "github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com"), true, UrlGuard.PUBLIC_ONLY
   );

   public void check(URI uri) throws IOException {
      String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
      if (scheme.equals("https") || !this.httpsOnly && scheme.equals("http")) {
         String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
         if (!this.hosts.contains(host) || uri.getUserInfo() != null) {
            throw new IOException("Base download refuses host " + host);
         } else if (this.httpsOnly && uri.getPort() != -1 && uri.getPort() != 443) {
            throw new IOException("Base download refuses port " + uri.getPort());
         } else {
            this.guard.check(uri);
         }
      } else {
         throw new IOException("Base download refuses " + scheme + " URLs");
      }
   }
}
