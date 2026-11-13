package net.bedrocknative.net;

import io.netty.channel.ChannelPipeline;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.cloudburstmc.protocol.bedrock.BedrockPeer;
import org.cloudburstmc.protocol.bedrock.netty.codec.encryption.BedrockEncryptionDecoder;
import org.cloudburstmc.protocol.bedrock.netty.codec.encryption.BedrockEncryptionEncoder;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.lang.JoseException;

public final class BedrockCrypto {
   private static final SecureRandom RANDOM = new SecureRandom();

   private BedrockCrypto() {
   }

   public static KeyPair generateSessionKeyPair() {
      try {
         KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
         generator.initialize(new ECGenParameterSpec("secp384r1"), RANDOM);
         return generator.generateKeyPair();
      } catch (GeneralSecurityException var1) {
         throw new IllegalStateException("P-384 key generation unavailable", var1);
      }
   }

   public static byte[] randomBytes(int length) {
      byte[] bytes = new byte[length];
      RANDOM.nextBytes(bytes);
      return bytes;
   }

   public static String encodePublicKey(PublicKey key) {
      return Base64.getEncoder().encodeToString(key.getEncoded());
   }

   public static ECPublicKey decodePublicKey(String base64) throws GeneralSecurityException {
      return (ECPublicKey)KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(base64)));
   }

   public static SecretKey deriveKey(PrivateKey local, PublicKey remote, byte[] salt) throws GeneralSecurityException {
      KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
      agreement.init(local);
      agreement.doPhase(remote, true);
      MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
      sha256.update(salt);
      sha256.update(agreement.generateSecret());
      return new SecretKeySpec(sha256.digest(), "AES");
   }

   public static String signJwt(KeyPair keyPair, String jsonPayload) {
      JsonWebSignature jws = new JsonWebSignature();
      jws.setAlgorithmHeaderValue("ES384");
      jws.setHeader("x5u", encodePublicKey(keyPair.getPublic()));
      jws.setPayload(jsonPayload);
      jws.setKey(keyPair.getPrivate());

      try {
         return jws.getCompactSerialization();
      } catch (JoseException var4) {
         throw new IllegalStateException("Unable to sign JWT", var4);
      }
   }

   public static BedrockCrypto.SelfSignedJwt verifySelfSignedJwt(String compact) throws GeneralSecurityException {
      try {
         JsonWebSignature jws = new JsonWebSignature();
         jws.setCompactSerialization(compact);
         ECPublicKey key = decodePublicKey(jws.getHeader("x5u"));
         jws.setKey(key);
         if (!jws.verifySignature()) {
            throw new GeneralSecurityException("Invalid JWT signature");
         } else {
            return new BedrockCrypto.SelfSignedJwt(key, jws.getUnverifiedPayload());
         }
      } catch (JoseException var3) {
         throw new GeneralSecurityException("Malformed JWT", var3);
      }
   }

   public static void enableEncryption(BedrockPeer peer, SecretKey key) {
      ChannelPipeline pipeline = peer.getChannel().pipeline();
      String anchor = pipeline.get("frame-id-codec") != null ? "frame-id-codec" : "nethernet-frame-codec";
      pipeline.addAfter(anchor, "bedrock-encryption-encoder", new BedrockEncryptionEncoder(key, ctrCipher(key, true)));
      pipeline.addAfter(anchor, "bedrock-encryption-decoder", new BedrockEncryptionDecoder(key, ctrCipher(key, false)));
   }

   private static Cipher ctrCipher(SecretKey key, boolean encrypt) {
      try {
         byte[] iv = new byte[16];
         System.arraycopy(key.getEncoded(), 0, iv, 0, 12);
         iv[15] = 2;
         Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
         cipher.init(encrypt ? 1 : 2, key, new IvParameterSpec(iv));
         return cipher;
      } catch (GeneralSecurityException var4) {
         throw new IllegalStateException("AES-CTR unavailable", var4);
      }
   }

   public record SelfSignedJwt(ECPublicKey signerKey, String payload) {
   }
}
