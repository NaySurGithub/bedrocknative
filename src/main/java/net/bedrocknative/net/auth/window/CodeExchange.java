package net.bedrocknative.net.auth.window;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public final class CodeExchange {
   public static final URI TOKEN_URL = URI.create("https://login.live.com/oauth20_token.srf");
   private static final int MAX_ANSWER = 65536;
   private final HttpClient http;
   private final URI tokenUrl;
   private final Duration timeout;

   CodeExchange(HttpClient http, URI tokenUrl, Duration timeout) {
      this.http = http;
      this.tokenUrl = tokenUrl;
      this.timeout = timeout;
   }

   public static CodeExchange standard() {
      return new CodeExchange(
         HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15L)).followRedirects(Redirect.NEVER).build(), TOKEN_URL, Duration.ofSeconds(30L)
      );
   }

   URI tokenUrl() {
      return this.tokenUrl;
   }

   public CodeExchange.Tokens exchange(AuthCodeRequest request, AuthCodeRequest.Redirect redirect) throws IOException, InterruptedException {
      Map<String, String> form = new LinkedHashMap<>();
      form.put("client_id", request.clientId());
      form.put("grant_type", "authorization_code");
      form.put("code", redirect.code());
      form.put("code_verifier", request.verifier());
      form.put("redirect_uri", "ms-xal-0000000048183522://auth");
      form.put("scope", request.scope());
      HttpRequest post = HttpRequest.newBuilder(this.tokenUrl)
         .timeout(this.timeout)
         .header("Content-Type", "application/x-www-form-urlencoded")
         .header("Accept", "application/json")
         .header("User-Agent", "BedrockNative")
         .POST(BodyPublishers.ofString(AuthCodeRequest.form(form)))
         .build();
      HttpResponse<InputStream> response = this.http.send(post, BodyHandlers.ofInputStream());

      byte[] body;
      try (InputStream in = response.body()) {
         body = in.readNBytes(65537);
      }

      if (body.length > 65536) {
         throw new SignInFailure(SignInFailure.Reason.INCOMPLETE, "Microsoft's token answer is too long");
      } else {
         JsonObject json = object(body);
         if (json == null) {
            throw new SignInFailure(SignInFailure.Reason.INCOMPLETE, "Microsoft's token endpoint answered " + response.statusCode() + " without JSON");
         } else {
            String error = text(json, "error");
            if (error != null) {
               throw new SignInFailure(SignInFailure.Reason.INCOMPLETE, "Microsoft refused the sign-in code", error);
            } else if (response.statusCode() / 100 != 2) {
               throw new SignInFailure(SignInFailure.Reason.INCOMPLETE, "Microsoft's token endpoint answered " + response.statusCode());
            } else {
               String access = text(json, "access_token");
               String refresh = text(json, "refresh_token");
               if (access != null && !access.isEmpty() && refresh != null && !refresh.isEmpty()) {
                  return new CodeExchange.Tokens(access, refresh, number(json, "expires_in", 3600L));
               } else {
                  throw new SignInFailure(SignInFailure.Reason.INCOMPLETE, "Microsoft's token answer has no refresh token");
               }
            }
         }
      }
   }

   private static @Nullable JsonObject object(byte[] body) {
      try {
         JsonElement parsed = JsonParser.parseString(new String(body, StandardCharsets.UTF_8));
         return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
      } catch (RuntimeException e) {
         return null;
      }
   }

   private static @Nullable String text(JsonObject json, String key) {
      JsonElement value = json.get(key);
      return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
   }

   private static long number(JsonObject json, String key, long fallback) {
      JsonElement value = json.get(key);

      try {
         return value != null && value.isJsonPrimitive() ? value.getAsLong() : fallback;
      } catch (NumberFormatException e) {
         return fallback;
      }
   }

   public record Tokens(String accessToken, String refreshToken, long expiresInSeconds) {
      @Override
      public String toString() {
         return "Tokens[hidden, expires in " + this.expiresInSeconds + " s]";
      }
   }
}
