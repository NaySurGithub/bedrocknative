package net.bedrocknative.net.auth;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeoutException;
import java.util.function.LongSupplier;
import net.raphimc.minecraftauth.msa.data.MsaEnvironment;
import net.raphimc.minecraftauth.msa.model.MsaApplicationConfig;
import net.raphimc.minecraftauth.msa.model.MsaDeviceCode;
import net.raphimc.minecraftauth.msa.model.MsaToken;
import net.raphimc.minecraftauth.msa.service.MsaAuthService;
import org.jspecify.annotations.Nullable;

public final class BrowserDeviceCodeAuthService extends MsaAuthService {
   private static final long MIN_INTERVAL_MS = 1000L;
   private static final long SLOW_DOWN_MS = 5000L;
   private final BrowserDeviceCodeAuthService.Endpoints endpoints;
   private final BrowserDeviceCodeAuthService.Listener listener;
   private final BrowserDeviceCodeAuthService.Pause pause;
   private final LongSupplier clock;
   private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15L)).build();

   public BrowserDeviceCodeAuthService(
      net.lenni0451.commons.httpclient.HttpClient httpClient, MsaApplicationConfig config, BrowserDeviceCodeAuthService.Listener listener
   ) {
      this(httpClient, config, BrowserDeviceCodeAuthService.Endpoints.of(config.getEnvironment()), listener, Thread::sleep, System::currentTimeMillis);
   }

   public BrowserDeviceCodeAuthService(
      net.lenni0451.commons.httpclient.HttpClient httpClient,
      MsaApplicationConfig config,
      BrowserDeviceCodeAuthService.Endpoints endpoints,
      BrowserDeviceCodeAuthService.Listener listener,
      BrowserDeviceCodeAuthService.Pause pause,
      LongSupplier clock
   ) {
      super(httpClient, config);
      this.endpoints = endpoints;
      this.listener = listener;
      this.pause = pause;
      this.clock = clock;
   }

   @Override
   public MsaToken acquireToken() throws IOException, InterruptedException, TimeoutException {
      JsonObject reply = this.post(
         this.endpoints.deviceCodeUrl(),
         Map.of("client_id", this.applicationConfig.getClientId(), "scope", this.applicationConfig.getScope(), "response_type", "device_code")
      );
      if (reply.has("error")) {
         throw new IOException(describe(reply));
      } else {
         String deviceCode = text(reply, "device_code");
         String userCode = text(reply, "user_code");
         String verificationUri = text(reply, "verification_uri");
         if (deviceCode != null && userCode != null && verificationUri != null) {
            String complete = text(reply, "verification_uri_complete");
            long intervalMs = Math.max(1000L, number(reply, "interval", 5L) * 1000L);
            long expires = this.clock.getAsLong() + number(reply, "expires_in", 900L) * 1000L;
            MsaDeviceCode code = new MsaDeviceCode(expires, intervalMs, deviceCode, userCode, verificationUri);
            this.listener.codeReady(code, DeviceCodeLink.direct(verificationUri, userCode, complete));
            return this.poll(code);
         } else {
            throw new IOException("Microsoft's device code answer is incomplete");
         }
      }
   }

   private MsaToken poll(MsaDeviceCode code) throws IOException, InterruptedException, TimeoutException {
      long interval = code.getIntervalMs();

      while (this.clock.getAsLong() < code.getExpireTimeMs()) {
         JsonObject reply = this.post(
            this.endpoints.tokenUrl(),
            Map.of("client_id", this.applicationConfig.getClientId(), "grant_type", "device_code", "device_code", code.getDeviceCode())
         );
         String error = text(reply, "error");
         if (error == null) {
            String access = text(reply, "access_token");
            if (access == null) {
               throw new IOException("Microsoft's token answer has no access token");
            }

            return new MsaToken(this.clock.getAsLong() + number(reply, "expires_in", 3600L) * 1000L, access, text(reply, "refresh_token"));
         }

         switch (error) {
            case "slow_down":
               interval += 5000L;
            case "authorization_pending":
               this.pause.sleep(interval);
               break;
            case "expired_token":
            case "bad_verification_code":
               throw new TimeoutException("The code expired before it was entered");
            case "authorization_declined":
            case "access_denied":
               throw new BrowserDeviceCodeAuthService.DeclinedException();
            default:
               throw new IOException(describe(reply));
         }
      }

      throw new TimeoutException("The code expired before it was entered");
   }

   private JsonObject post(String url, Map<String, String> form) throws IOException, InterruptedException {
      StringBuilder body = new StringBuilder();

      for (Entry<String, String> field : form.entrySet()) {
         if (!body.isEmpty()) {
            body.append('&');
         }

         body.append(URLEncoder.encode(field.getKey(), StandardCharsets.UTF_8)).append('=').append(URLEncoder.encode(field.getValue(), StandardCharsets.UTF_8));
      }

      HttpRequest request = HttpRequest.newBuilder(URI.create(url))
         .timeout(Duration.ofSeconds(30L))
         .header("Content-Type", "application/x-www-form-urlencoded")
         .header("Accept", "application/json")
         .header("User-Agent", "BedrockNative")
         .POST(BodyPublishers.ofString(body.toString()))
         .build();
      HttpResponse<String> response = this.http.send(request, BodyHandlers.ofString());

      try {
         JsonElement parsed = JsonParser.parseString(response.body());
         if (parsed.isJsonObject()) {
            return parsed.getAsJsonObject();
         }
      } catch (RuntimeException var7) {
      }

      throw new IOException("Microsoft answered " + response.statusCode() + " without JSON");
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

   private static String describe(JsonObject reply) {
      String description = text(reply, "error_description");
      return text(reply, "error") + (description == null ? "" : ": " + description);
   }

   public static final class DeclinedException extends IOException {
      public DeclinedException() {
         super("The sign-in was declined on the Microsoft page");
      }
   }

   public record Endpoints(String deviceCodeUrl, String tokenUrl) {
      public static BrowserDeviceCodeAuthService.Endpoints of(MsaEnvironment environment) {
         return new BrowserDeviceCodeAuthService.Endpoints(environment.getDeviceCodeUrl(), environment.getTokenUrl());
      }
   }

   public interface Listener {
      void codeReady(MsaDeviceCode var1, String var2);
   }

   public interface Pause {
      void sleep(long var1) throws InterruptedException;
   }
}
