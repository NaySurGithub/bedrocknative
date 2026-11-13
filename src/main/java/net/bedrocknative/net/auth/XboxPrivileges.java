package net.bedrocknative.net.auth;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import net.bedrocknative.BedrockNative;
import net.raphimc.minecraftauth.bedrock.BedrockAuthManager;
import net.raphimc.minecraftauth.xbl.model.XblDeviceToken;
import net.raphimc.minecraftauth.xbl.model.XblTitleToken;
import net.raphimc.minecraftauth.xbl.model.XblUserToken;
import org.jspecify.annotations.Nullable;

public final class XboxPrivileges {
   public static final URI XSTS_URL = URI.create("https://xsts.auth.xboxlive.com/xsts/authorize");
   public static final String RELYING_PARTY = "http://xboxlive.com";
   public static final int MULTIPLAYER = 254;
   public static final int USER_CREATED_CONTENT = 247;
   private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(4L);
   private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(6L);
   private static final long EXPIRY_MARGIN_MS = 60000L;
   private static final long DEFAULT_LIFE_MS = 3600000L;
   public static final long FRESH_MS = 300000L;
   public static final long UNANSWERED_MS = 300000L;
   private static final long DEV_DELAY_MS = Long.getLong("bedrocknative.xboxCheckDelay", 0L);
   private final URI url;
   private final HttpClient http;
   private final Duration requestTimeout;
   private final LongSupplier clock;
   private final Consumer<String> log;
   private final Map<String, XboxPrivileges.Kept> allowed = new ConcurrentHashMap<>();
   private final Map<String, Long> unanswered = new ConcurrentHashMap<>();
   private final Set<String> loggedUnknown = ConcurrentHashMap.newKeySet();

   public static XboxPrivileges online() {
      return new XboxPrivileges(XSTS_URL, System::currentTimeMillis, line -> BedrockNative.LOGGER.info(line));
   }

   public XboxPrivileges(URI url, LongSupplier clock, Consumer<String> log) {
      this(url, clock, log, CONNECT_TIMEOUT, REQUEST_TIMEOUT);
   }

   XboxPrivileges(URI url, LongSupplier clock, Consumer<String> log, Duration connectTimeout, Duration requestTimeout) {
      this.url = url;
      this.clock = clock;
      this.log = log;
      this.requestTimeout = requestTimeout;
      this.http = HttpClient.newBuilder().connectTimeout(connectTimeout).followRedirects(Redirect.NEVER).build();
   }

   public void forget(String accountId) {
      this.allowed.remove(accountId);
      this.unanswered.remove(accountId);
      this.loggedUnknown.remove(accountId);
   }

   public XboxPrivileges.Result check(String accountId, XboxPrivileges.TokenSource source) {
      return this.start(accountId, null, source).await();
   }

   public PrivilegeCheck start(String accountId, @Nullable XboxAccountStore.Entry account, XboxPrivileges.TokenSource source) {
      long now = this.clock.getAsLong();
      XboxPrivileges.Kept kept = this.allowed.get(accountId);
      boolean knownAllowed = kept != null && now < kept.result().validUntilMs();
      if (knownAllowed && now - kept.at() < 300000L) {
         return new PrivilegeCheck(CompletableFuture.completedFuture(kept.result()), now, false, this.clock, account);
      } else {
         Long silent = this.unanswered.get(accountId);
         boolean waited = !knownAllowed && (silent == null || now - silent >= 300000L);
         CompletableFuture<XboxPrivileges.Result> answer = new CompletableFuture<>();
         Thread.ofVirtual().name("Xbox multiplayer check").start(() -> {
            try {
               if (DEV_DELAY_MS > 0L) {
                  Thread.sleep(DEV_DELAY_MS);
               }

               answer.complete(this.record(accountId, this.ask(source)));
            } catch (Throwable var5) {
               answer.complete(XboxPrivileges.Result.unknown("unexpected " + var5.getClass().getSimpleName()));
            }
         });
         return new PrivilegeCheck(answer, now, waited, this.clock, account);
      }
   }

   private XboxPrivileges.Result ask(XboxPrivileges.TokenSource source) {
      try {
         return this.request(source.tokens(), this.clock.getAsLong());
      } catch (IOException var3) {
         return XboxPrivileges.Result.unknown("no tokens or no answer: " + var3.getClass().getSimpleName());
      } catch (InterruptedException var4) {
         Thread.currentThread().interrupt();
         return XboxPrivileges.Result.unknown("interrupted");
      } catch (RuntimeException var5) {
         return XboxPrivileges.Result.unknown("unexpected " + var5.getClass().getSimpleName());
      }
   }

   private XboxPrivileges.Result record(String accountId, XboxPrivileges.Result result) {
      long now = this.clock.getAsLong();
      if (result.outcome() == XboxPrivileges.Outcome.ALLOWED) {
         this.allowed.put(accountId, new XboxPrivileges.Kept(result, now));
         this.unanswered.remove(accountId);
         this.loggedUnknown.remove(accountId);
         this.log.accept("Xbox multiplayer check: " + result);
      } else if (result.outcome() == XboxPrivileges.Outcome.UNKNOWN) {
         this.unanswered.put(accountId, now);
         if (this.loggedUnknown.add(accountId)) {
            this.log.accept("Xbox multiplayer check unavailable, joining anyway: " + result);
         }
      } else {
         this.allowed.remove(accountId);
         this.unanswered.remove(accountId);
         this.log.accept("Xbox multiplayer check: " + result + ", no join");
      }

      return result;
   }

   private XboxPrivileges.Result request(XboxPrivileges.Tokens tokens, long now) throws IOException, InterruptedException {
      InetAddress.getAllByName(this.url.getHost());
      JsonObject properties = new JsonObject();
      properties.addProperty("SandboxId", "RETAIL");
      properties.addProperty("DeviceToken", tokens.device());
      JsonArray users = new JsonArray();
      users.add(tokens.user());
      properties.add("UserTokens", users);
      if (tokens.title() != null) {
         properties.addProperty("TitleToken", tokens.title());
      }

      JsonObject body = new JsonObject();
      body.add("Properties", properties);
      body.addProperty("RelyingParty", "http://xboxlive.com");
      body.addProperty("TokenType", "JWT");
      HttpRequest request = HttpRequest.newBuilder(this.url)
         .timeout(this.requestTimeout)
         .header("Content-Type", "application/json")
         .header("Accept", "application/json")
         .header("x-xbl-contract-version", "1")
         .POST(BodyPublishers.ofString(body.toString()))
         .build();
      HttpResponse<String> response = this.http.send(request, BodyHandlers.ofString());
      return read(response.statusCode(), response.headers().firstValue("X-Err").orElse(null), response.body(), now);
   }

   static XboxPrivileges.Result read(int status, @Nullable String xErrHeader, @Nullable String body, long now) {
      JsonObject json = object(body);
      if (status != 200) {
         Long code = XboxProblem.parseCode(xErrHeader);
         if (code == null && json != null) {
            JsonElement var18 = json.get("XErr");
            if (var18 instanceof JsonElement && var18.isJsonPrimitive()) {
               code = XboxProblem.parseCode(var18.getAsString());
            }
         }

         if (code == null) {
            return XboxPrivileges.Result.unknown("answer " + status);
         } else {
            XboxProblem problem = XboxProblem.fromXErr(code).problem();
            if (problem == XboxProblem.BANNED) {
               return new XboxPrivileges.Result(XboxPrivileges.Outcome.BANNED, null, Set.of(), code, 0L, "refused");
            } else {
               return problem == XboxProblem.PARENTAL
                  ? new XboxPrivileges.Result(XboxPrivileges.Outcome.BLOCKED, null, Set.of(), code, 0L, "refused")
                  : new XboxPrivileges.Result(XboxPrivileges.Outcome.UNKNOWN, null, Set.of(), code, 0L, "refused");
            }
         }
      } else if (json == null) {
         return XboxPrivileges.Result.unknown("answer without JSON");
      } else {
         JsonObject claims = firstUser(json);
         if (claims != null) {
            JsonElement privileges = claims.get("prv");
            if (privileges instanceof JsonElement && privileges.isJsonPrimitive()) {
               Set<Integer> privilegesx = new TreeSet<>();

               for (String id : privileges.getAsString().trim().split("\\s+")) {
                  if (!id.isEmpty()) {
                     try {
                        privilegesx.add(Integer.parseInt(id));
                     } catch (NumberFormatException var14) {
                        return XboxPrivileges.Result.unknown("unreadable privilege claims");
                     }
                  }
               }

               JsonElement var21 = claims.get("agg");
               String ageGroup = var21 instanceof JsonElement && var21.isJsonPrimitive() ? var21.getAsString() : null;
               long validUntil = notAfter(json, now);
               Set<Integer> kept = Collections.unmodifiableSet(privilegesx);
               return privilegesx.contains(254)
                  ? new XboxPrivileges.Result(
                     XboxPrivileges.Outcome.ALLOWED,
                     ageGroup,
                     kept,
                     0L,
                     validUntil,
                     privilegesx.contains(247) ? "multiplayer allowed" : "multiplayer allowed, no user-created content"
                  )
                  : new XboxPrivileges.Result(XboxPrivileges.Outcome.BLOCKED, ageGroup, kept, 0L, validUntil, "no multiplayer privilege");
            }
         }

         return XboxPrivileges.Result.unknown("no privilege claims");
      }
   }

   @Nullable
   private static JsonObject object(@Nullable String body) {
      if (body != null && !body.isBlank()) {
         try {
            JsonElement parsed = JsonParser.parseString(body);
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
         } catch (RuntimeException var2) {
            return null;
         }
      } else {
         return null;
      }
   }

   @Nullable
   private static JsonObject firstUser(JsonObject json) {
      return json.get("DisplayClaims") instanceof JsonObject display
            && display.get("xui") instanceof JsonArray xui
            && !xui.isEmpty()
            && xui.get(0) instanceof JsonObject first
         ? first
         : null;
   }

   private static long notAfter(JsonObject json, long now) {
      JsonElement until = json.get("NotAfter");
      if (until instanceof JsonElement) {
         JsonElement value = until;
         if (until.isJsonPrimitive()) {
            try {
               long untilx = Instant.parse(value.getAsString()).toEpochMilli() - 60000L;
               if (untilx > now) {
                  return untilx;
               }
            } catch (DateTimeParseException var6) {
            }
         }
      }

      return now + 3600000L;
   }

   private record Kept(XboxPrivileges.Result result, long at) {
   }

   public static enum Outcome {
      ALLOWED,
      BLOCKED,
      BANNED,
      UNKNOWN;
   }

   public record Result(XboxPrivileges.Outcome outcome, @Nullable String ageGroup, Set<Integer> privileges, long code, long validUntilMs, String reason) {
      public boolean blocksJoining() {
         return this.outcome == XboxPrivileges.Outcome.BLOCKED || this.outcome == XboxPrivileges.Outcome.BANNED;
      }

      @Nullable
      public XboxProblem.Diagnosis diagnosis() {
         return switch (this.outcome) {
            case BLOCKED -> new XboxProblem.Diagnosis(XboxProblem.MULTIPLAYER_BLOCKED, this.code);
            case BANNED -> new XboxProblem.Diagnosis(XboxProblem.MULTIPLAYER_BANNED, this.code);
            default -> null;
         };
      }

      @Override
      public String toString() {
         return this.outcome
            + " ("
            + this.reason
            + (this.ageGroup == null ? "" : ", age group " + this.ageGroup)
            + (this.privileges.isEmpty() ? "" : ", " + this.privileges.size() + " privileges")
            + (this.code == 0L ? "" : ", " + XboxProblem.hex(this.code))
            + ")";
      }

      static XboxPrivileges.Result unknown(String reason) {
         return new XboxPrivileges.Result(XboxPrivileges.Outcome.UNKNOWN, null, Set.of(), 0L, 0L, reason);
      }
   }

   public interface TokenSource {
      XboxPrivileges.Tokens tokens() throws IOException;
   }

   public record Tokens(String device, String user, @Nullable String title) {
      @Override
      public String toString() {
         return "Tokens[" + (this.title == null ? "device, user" : "device, user, title") + "]";
      }

      public static XboxPrivileges.Tokens of(BedrockAuthManager account) throws IOException {
         XblDeviceToken device = account.getXblDeviceToken().getUpToDate();
         XblUserToken user = account.getXblUserToken().getUpToDate();
         XblTitleToken title = account.getXblTitleToken().getUpToDate();
         return new XboxPrivileges.Tokens(device.getToken(), user.getToken(), title == null ? null : title.getToken());
      }

      public static boolean renewalNeeded(BedrockAuthManager account) {
         return account.getXblDeviceToken().isExpired() || account.getXblUserToken().isExpired() || account.getXblTitleToken().isExpired();
      }
   }
}
