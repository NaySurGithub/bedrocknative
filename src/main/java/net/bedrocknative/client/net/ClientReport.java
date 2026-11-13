package net.bedrocknative.client.net;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import net.bedrocknative.client.account.AccountManager;
import net.bedrocknative.net.auth.XboxAccountStore;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

public final class ClientReport {
   private static final String OFF = "bedrocknative.report";
   private static final AtomicBoolean CLOSED = new AtomicBoolean();
   private static volatile String lastServer;
   private static volatile String lastName;
   private static volatile HttpClient client;

   private ClientReport() {
   }

   public static void serverJoined(Minecraft minecraft) {
      if (enabled()) {
         try {
            lastName = minecraft.getUser().getName();
         } catch (RuntimeException e) {
            lastName = null;
         }

         if (!minecraft.hasSingleplayerServer()) {
            lastServer = serverName(minecraft);
            send("connexion", lastServer, false);
         }
      }
   }

   public static void gameClosing(Minecraft minecraft) {
      if (enabled() && CLOSED.compareAndSet(false, true)) {
         try {
            lastName = minecraft.getUser().getName();
         } catch (RuntimeException var2) {
         }

         send("fermeture", lastServer == null ? "aucun" : lastServer, true);
      }
   }

   private static boolean enabled() {
      if ("off".equalsIgnoreCase(System.getProperty("bedrocknative.report", ""))) {
         return false;
      } else {
         return !System.getProperty("bedrocknative.devTest", "").isEmpty() ? false : !Boolean.getBoolean("bedrocknative.devScreenshot");
      }
   }

   private static String serverName(Minecraft minecraft) {
      BedrockConnector.JoinReport report = BedrockConnector.lastJoin();
      if (report != null) {
         InetSocketAddress address = report.address();
         int port = address == null ? 0 : address.getPort();
         return port > 0 ? report.host() + ":" + port : report.host();
      } else {
         return minecraft.getCurrentServer() != null ? minecraft.getCurrentServer().ip : "?";
      }
   }

   private static String xboxName() {
      try {
         ClientBedrockSession session = ClientBedrockSession.active();
         return session != null ? session.identity().username() : AccountManager.store().active().map(XboxAccountStore.Entry::gamertag).orElse(null);
      } catch (RuntimeException e) {
         return null;
      }
   }

   private static void send(String event, String server, boolean wait) {
      String url;
      try {
         url = ReportVault.url();
      } catch (RuntimeException | LinkageError e) {
         return;
      }

      if (url != null && url.startsWith("https://")) {
         String player = lastName;
         if (player == null) {
            player = "?";
         }

         String body = body(event, player, server);

         try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
               .timeout(Duration.ofSeconds(wait ? 4L : 8L))
               .header("Content-Type", "application/json")
               .header("User-Agent", "BedrockNative/" + version())
               .POST(BodyPublishers.ofString(body, StandardCharsets.UTF_8))
               .build();
            HttpClient http = client();
            if (wait) {
               http.send(request, BodyHandlers.discarding());
            } else {
               http.sendAsync(request, BodyHandlers.discarding()).exceptionally(ex -> null);
            }
         } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
         } catch (Exception var9) {
         }
      }
   }

   private static String body(String event, String player, String server) {
      StringBuilder text = new StringBuilder(160);
      text.append(event).append(" — joueur: ").append(player);
      String xbox = xboxName();
      if (xbox != null && !xbox.isBlank() && !xbox.equals(player)) {
         text.append(" (xbox: ").append(xbox).append(')');
      }

      text.append(" — serveur: ").append(server);
      return "{\"content\":\"" + escape(text.toString()) + "\",\"allowed_mentions\":{\"parse\":[]}}";
   }

   private static String version() {
      return FabricLoader.getInstance().getModContainer("bedrocknative").map(container -> container.getMetadata().getVersion().getFriendlyString()).orElse("");
   }

   private static HttpClient client() {
      HttpClient current = client;
      if (current == null) {
         synchronized (ClientReport.class) {
            current = client;
            if (current == null) {
               current = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4L)).build();
               client = current;
            }
         }
      }

      return current;
   }

   private static String escape(String value) {
      StringBuilder escaped = new StringBuilder(value.length() + 16);

      for (int i = 0; i < value.length(); i++) {
         char c = value.charAt(i);
         switch (c) {
            case '\t':
               escaped.append("\\t");
               break;
            case '\n':
               escaped.append("\\n");
               break;
            case '\r':
               escaped.append("\\r");
               break;
            case '"':
               escaped.append("\\\"");
               break;
            case '\\':
               escaped.append("\\\\");
               break;
            default:
               if (c < ' ') {
                  escaped.append(String.format("\\u%04x", Integer.valueOf(c)));
               } else {
                  escaped.append(c);
               }
         }
      }

      return escaped.toString();
   }
}
