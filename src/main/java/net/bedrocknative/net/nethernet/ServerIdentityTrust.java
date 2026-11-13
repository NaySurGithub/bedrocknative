package net.bedrocknative.net.nethernet;

import java.security.interfaces.ECPublicKey;
import java.time.Clock;
import java.time.Duration;
import org.cloudburstmc.netty.util.nethernet.Identity;
import org.cloudburstmc.netty.util.nethernet.IdentityUtils;
import org.cloudburstmc.netty.util.nethernet.TokenTrust;
import org.jose4j.jwa.AlgorithmConstraints;
import org.jose4j.jwa.AlgorithmConstraints.ConstraintType;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.jwt.NumericDate;
import org.jose4j.lang.JoseException;
import org.jspecify.annotations.Nullable;

public final class ServerIdentityTrust implements TokenTrust {
   static final Duration CLEARLY_EXPIRED = Duration.ofHours(12L);
   private final NetherNetTrustStore store;
   private final boolean https;
   private final String target;
   private final Clock clock;
   @Nullable
   private volatile ServerIdentityTrust.Verified verified;

   public ServerIdentityTrust(NetherNetTrustStore store, boolean https, String target) {
      this(store, https, target, Clock.systemUTC());
   }

   ServerIdentityTrust(NetherNetTrustStore store, boolean https, String target, Clock clock) {
      this.store = store;
      this.https = https;
      this.target = target;
      this.clock = clock;
   }

   @Override
   public JwtClaims claims(Identity identity) throws Exception {
      if (identity.assertion() != null && identity.assertion().token() != null) {
         JsonWebSignature jws = new JsonWebSignature();
         jws.setCompactSerialization(identity.assertion().token());
         if (!"ES384".equals(jws.getAlgorithmHeaderValue())) {
            throw new JoseException("The server's token is not signed with ES384");
         } else {
            JwtClaims claims = JwtClaims.parse(jws.getUnverifiedPayload());
            ECPublicKey key = CpkKeys.parse(claims.getClaimValue("cpk"));
            jws.setKey(key);
            jws.setAlgorithmConstraints(new AlgorithmConstraints(ConstraintType.PERMIT, "ES384"));
            if (!jws.verifySignature()) {
               throw new JoseException("The server's token is not signed by the key it names");
            } else {
               this.checkExpiry(claims);
               claims.setClaim("cpk", IdentityUtils.encodePublicKey(key));
               String digest = CpkKeys.digest(key);
               String domain = identity.idp() != null && identity.idp().domain() != null ? identity.idp().domain() : "";
               this.verified = new ServerIdentityTrust.Verified(key, digest, domain, issuer(claims), this.store.known(digest));
               return claims;
            }
         }
      } else {
         throw new JoseException("The server's identity has no token");
      }
   }

   private void checkExpiry(JwtClaims claims) throws JoseException {
      NumericDate expiry;
      try {
         expiry = claims.getExpirationTime();
      } catch (MalformedClaimException var4) {
         throw new JoseException("The server's token has a bad expiry", var4);
      }

      if (expiry != null && expiry.getValue() < this.clock.instant().minus(CLEARLY_EXPIRED).getEpochSecond()) {
         throw new JoseException("The server's token expired at " + expiry.getValue() + " (epoch seconds)");
      }
   }

   @Nullable
   private static String issuer(JwtClaims claims) {
      try {
         return claims.getIssuer();
      } catch (MalformedClaimException var2) {
         return null;
      }
   }

   public ServerIdentityTrust.Verified verify(String answer) throws Exception {
      this.verified = null;
      IdentityUtils.validateSdp(answer, this);
      ServerIdentityTrust.Verified server = this.verified;
      if (server == null) {
         throw new JoseException("The server's identity was not checked");
      } else if (!this.https && !server.known()) {
         String before = this.store.trustedAt(this.target);
         throw new UntrustedServerException(
            server.digest(), this.target, server.domain(), server.issuer(), this.store, before != null && !before.equals(server.digest()) ? before : null
         );
      } else {
         if (server.known()) {
            this.store.joined(server.digest(), this.target);
         }

         return server;
      }
   }

   public record Verified(ECPublicKey key, String digest, String domain, @Nullable String issuer, boolean known) {
      public String shortDigest() {
         return this.digest.substring(0, 8);
      }
   }
}
