package net.bedrocknative.net.auth;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.jspecify.annotations.Nullable;

public record MultiplayerTokenClaims(@Nullable Integer ap, @Nullable String ipt, @Nullable String tid, @Nullable String iss, @Nullable String aud) {
   public static MultiplayerTokenClaims of(String token) {
      JsonObject claims = payload(token);
      return new MultiplayerTokenClaims(integer(claims, "ap"), string(claims, "ipt"), string(claims, "tid"), string(claims, "iss"), string(claims, "aud"));
   }

   static JsonObject payload(String token) {
      String[] parts = token == null ? new String[0] : token.split("\\.");
      if (parts.length != 3) {
         return new JsonObject();
      }

      try {
         JsonElement payload = JsonParser.parseString(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8));
         return payload.isJsonObject() ? payload.getAsJsonObject() : new JsonObject();
      } catch (RuntimeException e) {
         return new JsonObject();
      }
   }

   private static @Nullable Integer integer(JsonObject claims, String key) {
      JsonElement value = claims.get(key);
      return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber() ? value.getAsInt() : null;
   }

   private static @Nullable String string(JsonObject claims, String key) {
      JsonElement value = claims.get(key);
      return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
   }

   @Override
   public String toString() {
      return "ap=" + this.ap + " ipt=" + this.ipt + " tid=" + this.tid + " iss=" + this.iss + " aud=" + this.aud;
   }
}
