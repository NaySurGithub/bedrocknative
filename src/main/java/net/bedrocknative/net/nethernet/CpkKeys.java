package net.bedrocknative.net.nethernet;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import org.jose4j.jwk.EllipticCurveJsonWebKey;
import org.jose4j.jwk.JsonWebKey.Factory;
import org.jose4j.lang.JoseException;

public final class CpkKeys {
   private CpkKeys() {
   }

   public static ECPublicKey parse(Object claim) throws GeneralSecurityException {
      PublicKey key;
      if (claim instanceof Map<?, ?> jwk) {
         try {
            if (!(Factory.newJwk((Map<String, Object>)jwk) instanceof EllipticCurveJsonWebKey ec && "P-384".equals(ec.getCurveName()))) {
               throw new GeneralSecurityException("cpk is not a P-384 key");
            }

            key = ec.getECPublicKey();
         } catch (RuntimeException | JoseException var7) {
            throw new GeneralSecurityException("cpk is not a readable JSON Web Key", var7);
         }
      } else {
         if (!(claim instanceof String base64)) {
            throw new GeneralSecurityException(claim == null ? "no cpk claim" : "cpk is neither a key object nor a key string");
         }

         try {
            key = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(base64.trim())));
         } catch (IllegalArgumentException var6) {
            throw new GeneralSecurityException("cpk is not base64", var6);
         }
      }

      if (key instanceof ECPublicKey ec && ec.getParams().getCurve().getField().getFieldSize() == 384) {
         return ec;
      } else {
         throw new GeneralSecurityException("cpk is not a P-384 key");
      }
   }

   public static String digest(PublicKey key) {
      try {
         return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.getEncoded()));
      } catch (GeneralSecurityException var2) {
         throw new IllegalStateException(var2);
      }
   }
}
