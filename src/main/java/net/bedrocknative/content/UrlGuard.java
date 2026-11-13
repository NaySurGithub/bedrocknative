package net.bedrocknative.content;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;

public final class UrlGuard {
   public static final UrlGuard PUBLIC_ONLY = new UrlGuard(Set.of(), false);
   public static final UrlGuard ANY = new UrlGuard(Set.of(), true);
   private final Set<InetAddress> trusted;
   private final boolean anything;

   private UrlGuard(Set<InetAddress> trusted, boolean anything) {
      this.trusted = trusted;
      this.anything = anything;
   }

   public static UrlGuard forServer(InetAddress server) {
      return new UrlGuard(Set.of(server), false);
   }

   public void check(URI uri) throws IOException {
      String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
      if ((scheme.equals("http") || scheme.equals("https")) && uri.getHost() != null && uri.getUserInfo() == null) {
         if (!this.anything) {
            InetAddress[] addresses;
            try {
               addresses = InetAddress.getAllByName(uri.getHost());
            } catch (UnknownHostException var8) {
               throw new IOException("Unknown host " + uri.getHost());
            }

            for (InetAddress address : addresses) {
               if (!this.trusted.contains(address) && !isPublic(address)) {
                  throw new IOException("Refusing a URL that points at a local or private address");
               }
            }
         }
      } else {
         throw new IOException("URLs must be plain http(s)");
      }
   }

   public static boolean isPublic(InetAddress address) {
      if (!address.isAnyLocalAddress()
         && !address.isLoopbackAddress()
         && !address.isLinkLocalAddress()
         && !address.isSiteLocalAddress()
         && !address.isMulticastAddress()) {
         byte[] b = address.getAddress();
         if (b.length == 4) {
            return publicV4(b[0] & 255, b[1] & 255, b[2] & 255);
         } else if ((b[0] & 254) == 252) {
            return false;
         } else {
            boolean zeroPrefix = true;

            for (int i = 0; i < 10; i++) {
               zeroPrefix &= b[i] == 0;
            }

            if (zeroPrefix && b[10] == 0 && b[11] == 0) {
               return false;
            } else if (b[0] == 0 && b[1] == 100 && (b[2] & 255) == 255 && (b[3] & 255) == 155) {
               return embedded(b, 12);
            } else {
               return b[0] == 32 && b[1] == 2 ? embedded(b, 2) : true;
            }
         }
      } else {
         return false;
      }
   }

   private static boolean embedded(byte[] address, int offset) {
      try {
         return isPublic(InetAddress.getByAddress(new byte[]{address[offset], address[offset + 1], address[offset + 2], address[offset + 3]}));
      } catch (UnknownHostException var3) {
         return false;
      }
   }

   private static boolean publicV4(int a, int b, int c) {
      if (a != 0 && a < 240) {
         if (a == 100 && (b & 192) == 64) {
            return false;
         } else {
            return a == 192 && b == 0 && c == 0 ? false : a != 198 || (b & 254) != 18;
         }
      } else {
         return false;
      }
   }
}
