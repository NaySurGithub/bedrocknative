package net.bedrocknative.net.auth.window;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import org.jspecify.annotations.Nullable;

public final class AuthCodeRequest {
   public static final String AUTHORIZE_URL = "https://login.live.com/oauth20_authorize.srf";
   public static final String REDIRECT_URI = "ms-xal-0000000048183522://auth";
   public static final String SCHEME_PREFIX = "ms-xal-0000000048183522:";
   public static final int MAX_REDIRECT_LENGTH = 8192;
   private static final int MAX_CODE_LENGTH = 2048;
   private static final int STATE_BYTES = 16;
   private static final int VERIFIER_BYTES = 32;
   private final String clientId;
   private final String scope;
   private final String state;
   private final String verifier;

   private AuthCodeRequest(String clientId, String scope, String state, String verifier) {
      this.clientId = clientId;
      this.scope = scope;
      this.state = state;
      this.verifier = verifier;
   }

   public static AuthCodeRequest create(String clientId, String scope, SecureRandom random) {
      return new AuthCodeRequest(clientId, scope, randomText(random, 16), randomText(random, 32));
   }

   static AuthCodeRequest of(String clientId, String scope, String state, String verifier) {
      return new AuthCodeRequest(clientId, scope, state, verifier);
   }

   public static String challenge(String verifier) {
      try {
         byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
         return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
      } catch (NoSuchAlgorithmException e) {
         throw new IllegalStateException(e);
      }
   }

   public String url() {
      Map<String, String> query = new LinkedHashMap<>();
      query.put("client_id", this.clientId);
      query.put("response_type", "code");
      query.put("scope", this.scope);
      query.put("redirect_uri", "ms-xal-0000000048183522://auth");
      query.put("state", this.state);
      query.put("code_challenge", challenge(this.verifier));
      query.put("code_challenge_method", "S256");
      query.put("prompt", "select_account");
      return "https://login.live.com/oauth20_authorize.srf?" + form(query);
   }

   public String clientId() {
      return this.clientId;
   }

   public String scope() {
      return this.scope;
   }

   String verifier() {
      return this.verifier;
   }

   String state() {
      return this.state;
   }

   public boolean ours(@Nullable String redirect) {
      Map<String, String> query = query(redirect);
      String got = query == null ? null : query.get("state");
      return got != null && MessageDigest.isEqual(got.getBytes(StandardCharsets.UTF_8), this.state.getBytes(StandardCharsets.UTF_8));
   }

   public AuthCodeRequest.Redirect read(@Nullable String redirect) throws SignInFailure {
      if (redirect != null && redirect.length() <= 8192) {
         Map<String, String> query = query(redirect);
         if (query == null) {
            throw new SignInFailure(SignInFailure.Reason.INCOMPLETE, "the redirect is not the registered address or is malformed");
         }

         if (!this.ours(redirect)) {
            throw new SignInFailure(SignInFailure.Reason.INCOMPLETE, "the redirect belongs to another sign-in (state)");
         }

         String error = query.get("error");
         if (error == null) {
            String code = query.get("code");
            if (code != null && !code.isEmpty() && code.length() <= 2048 && printable(code)) {
               return new AuthCodeRequest.Redirect(code);
            } else {
               throw new SignInFailure(SignInFailure.Reason.INCOMPLETE, "the redirect has no usable code");
            }
         } else if (error.equals("access_denied")) {
            throw new SignInFailure(SignInFailure.Reason.DECLINED, "the player declined on Microsoft's page");
         } else {
            throw new SignInFailure(SignInFailure.Reason.INCOMPLETE, "Microsoft answered with an error", SignInFailure.oauthError(error));
         }
      } else {
         throw new SignInFailure(SignInFailure.Reason.INCOMPLETE, "the redirect is missing or too long");
      }
   }

   static @Nullable Map<String, String> query(@Nullable String redirect) {
      if (redirect != null && redirect.length() <= 8192 && printable(redirect)) {
         int mark = redirect.indexOf(63);
         if (mark >= 0 && redirect.indexOf(35) < 0) {
            String target = redirect.substring(0, mark).toLowerCase(Locale.ROOT);
            if (!target.equals("ms-xal-0000000048183522://auth") && !target.equals("ms-xal-0000000048183522://auth/")) {
               return null;
            }

            Map<String, String> fields = new HashMap<>();

            for (String pair : redirect.substring(mark + 1).split("&")) {
               if (!pair.isEmpty()) {
                  int equals = pair.indexOf(61);

                  try {
                     String name = URLDecoder.decode(equals < 0 ? pair : pair.substring(0, equals), StandardCharsets.UTF_8);
                     String value = equals < 0 ? "" : URLDecoder.decode(pair.substring(equals + 1), StandardCharsets.UTF_8);
                     if (fields.put(name, value) != null) {
                        return null;
                     }
                  } catch (IllegalArgumentException e) {
                     return null;
                  }
               }
            }

            return fields;
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   private static boolean printable(String text) {
      for (int i = 0; i < text.length(); i++) {
         char c = text.charAt(i);
         if (c < '!' || c > '~') {
            return false;
         }
      }

      return true;
   }

   static String form(Map<String, String> fields) {
      StringBuilder out = new StringBuilder();

      for (Entry<String, String> field : fields.entrySet()) {
         if (!out.isEmpty()) {
            out.append('&');
         }

         out.append(URLEncoder.encode(field.getKey(), StandardCharsets.UTF_8)).append('=').append(URLEncoder.encode(field.getValue(), StandardCharsets.UTF_8));
      }

      return out.toString();
   }

   private static String randomText(SecureRandom random, int bytes) {
      byte[] value = new byte[bytes];
      random.nextBytes(value);
      return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
   }

   @Override
   public String toString() {
      return "AuthCodeRequest[client " + this.clientId + ", state and verifier hidden]";
   }

   public record Redirect(String code) {
      @Override
      public String toString() {
         return "Redirect[code hidden]";
      }
   }
}
