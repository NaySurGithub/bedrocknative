package net.bedrocknative.partner;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import net.bedrocknative.content.UrlGuard;

public final class PartnerGuard {
   public static final PartnerGuard PUBLIC_HTTPS = new PartnerGuard(false);
   static final PartnerGuard DEV_LOOPBACK = new PartnerGuard(true);
   static final String DEV_TEST_PROPERTY = "bedrocknative.devTest";
   private final boolean loopbackOnly;

   private PartnerGuard(boolean loopbackOnly) {
      this.loopbackOnly = loopbackOnly;
   }

   public static PartnerGuard forThisRun() {
      return "partners".equals(System.getProperty("bedrocknative.devTest", "")) ? DEV_LOOPBACK : PUBLIC_HTTPS;
   }

   public boolean devLoopback() {
      return this.loopbackOnly;
   }

   public void checkForm(URI uri) throws IOException {
      String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
      boolean schemeOk = scheme.equals("https") || this.loopbackOnly && scheme.equals("http");
      if (!schemeOk) {
         throw new IOException("only https URLs are fetched");
      } else if (uri.getHost() == null || uri.getHost().isEmpty() || uri.getRawUserInfo() != null || uri.getRawFragment() != null) {
         throw new IOException("the URL needs a plain host (no user info, no fragment)");
      }
   }

   public void check(URI uri) throws IOException {
      this.checkForm(uri);
      if (!this.loopbackOnly) {
         UrlGuard.PUBLIC_ONLY.check(uri);
      }

      InetAddress[] addresses;
      try {
         addresses = InetAddress.getAllByName(uri.getHost());
      } catch (UnknownHostException e) {
         throw new IOException("unknown host " + uri.getHost());
      }

      for (InetAddress address : addresses) {
         if (this.loopbackOnly ? !address.isLoopbackAddress() : !isPublic(address)) {
            throw new IOException("refusing " + uri.getHost() + ": not a public address");
         }
      }
   }

   public static boolean isPublic(InetAddress address) {
      return UrlGuard.isPublic(address) && publicByOwnRules(address);
   }

   private static boolean publicByOwnRules(InetAddress address) {
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
         } else if ((b[0] & 255) == 254 && (b[1] & 192) == 192) {
            return false;
         } else {
            boolean zeroPrefix = isZero(b, 0, 10);
            if (zeroPrefix && b[10] == 0 && b[11] == 0) {
               return false;
            } else if (zeroPrefix && (b[10] & 255) == 255 && (b[11] & 255) == 255) {
               return publicV4(b[12] & 255, b[13] & 255, b[14] & 255);
            } else if ((b[0] & 255) == 32 && b[1] == 2) {
               return publicV4(b[2] & 255, b[3] & 255, b[4] & 255);
            } else {
               return b[0] == 0 && (b[1] & 255) == 100 && (b[2] & 255) == 255 && (b[3] & 255) == 155
                  ? publicV4(b[12] & 255, b[13] & 255, b[14] & 255)
                  : (b[0] & 255) != 32 || b[1] != 1 || b[2] != 13 || (b[3] & 255) != 184;
            }
         }
      } else {
         return false;
      }
   }

   private static boolean isZero(byte[] bytes, int from, int to) {
      for (int i = from; i < to; i++) {
         if (bytes[i] != 0) {
            return false;
         }
      }

      return true;
   }

   private static boolean publicV4(int a, int b, int c) {
      if (a != 0 && a != 10 && a != 127 && a < 224) {
         if (a == 100 && (b & 192) == 64) {
            return false;
         } else if ((a != 169 || b != 254) && (a != 172 || (b & 240) != 16) && (a != 192 || b != 168)) {
            return a != 192 || b != 0 || c != 0 && c != 2
               ? (a != 198 || (b & 254) != 18) && (a != 198 || b != 51 || c != 100) && (a != 203 || b != 0 || c != 113)
               : false;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }
}
