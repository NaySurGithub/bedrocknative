package net.bedrocknative.content;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class PackCrypto {
   public static final int KEY_LENGTH = 32;
   static final int MAGIC = -1680885252;
   static final int HEADER = 256;

   private PackCrypto() {
   }

   public static boolean isEncryptedContents(byte[] contents) {
      return contents.length >= 256 && ByteBuffer.wrap(contents).order(ByteOrder.LITTLE_ENDIAN).getInt(4) == -1680885252;
   }

   public static Map<String, String> readContents(byte[] contents, String contentKey) throws IOException {
      if (!isEncryptedContents(contents)) {
         throw new IOException("contents.json is not encrypted");
      }

      byte[] json = decrypt(contents, 256, contents.length - 256, contentKey);
      Map<String, String> keys = new HashMap<>();

      try {
         JsonObject root = JsonParser.parseString(new String(json, StandardCharsets.UTF_8)).getAsJsonObject();

         for (JsonElement element : root.getAsJsonArray("content")) {
            JsonObject entry = element.getAsJsonObject();
            JsonElement key = entry.get("key");
            if (key != null && !key.isJsonNull() && entry.get("path") != null) {
               keys.put(entry.get("path").getAsString(), key.getAsString());
            }
         }

         return keys;
      } catch (RuntimeException e) {
         throw new IOException("contents.json can't be decrypted with this content key");
      }
   }

   public static byte[] decrypt(byte[] data, String key) throws IOException {
      return decrypt(data, 0, data.length, key);
   }

   private static byte[] decrypt(byte[] data, int offset, int length, String key) throws IOException {
      byte[] keyBytes = key.getBytes(StandardCharsets.US_ASCII);
      if (keyBytes.length != 32) {
         throw new IOException("Content keys are 32 characters");
      }

      try {
         Cipher cipher = Cipher.getInstance("AES/CFB8/NoPadding");
         cipher.init(2, new SecretKeySpec(keyBytes, "AES"), new IvParameterSpec(keyBytes, 0, 16));
         return cipher.doFinal(data, offset, length);
      } catch (GeneralSecurityException e) {
         throw new IOException("Couldn't decrypt pack content", e);
      }
   }
}
