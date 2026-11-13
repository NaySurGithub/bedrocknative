package net.bedrocknative.net.auth;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.net.BedrockCrypto;
import net.bedrocknative.net.nethernet.IdpDomain;
import net.bedrocknative.protocol.BedrockProtocol;
import net.bedrocknative.protocol.BedrockProtocols;
import net.bedrocknative.text.BedrockLocale;
import net.raphimc.minecraftauth.bedrock.BedrockAuthManager;
import net.raphimc.minecraftauth.bedrock.model.MinecraftMultiplayerToken;
import org.cloudburstmc.netty.util.nethernet.OperatorIdentity;
import org.cloudburstmc.protocol.bedrock.data.auth.AuthType;
import org.cloudburstmc.protocol.bedrock.data.auth.TokenPayload;
import org.cloudburstmc.protocol.bedrock.packet.LoginPacket;
import org.jspecify.annotations.Nullable;
import oshi.SystemInfo;
import oshi.hardware.ComputerSystem;
import oshi.hardware.HardwareAbstractionLayer;

public final class ClientIdentity {
   private static final String MULTIPLAYER_AUDIENCE = "api://auth-minecraft-services/multiplayer";
   private final String username;
   private final String xuid;
   private final UUID uuid;
   private final KeyPair sessionKeyPair;
   private final String deviceId;
   private final int deviceOs;
   private final long clientRandomId;
   private final BedrockSkin skin;
   private final TokenPayload auth;
   private final Supplier<ClientIdentity.Settings> settings;

   private ClientIdentity(
      String username,
      String xuid,
      KeyPair sessionKeyPair,
      UUID deviceId,
      int deviceOs,
      BedrockSkin skin,
      String token,
      AuthType authType,
      Supplier<ClientIdentity.Settings> settings
   ) {
      this.username = username;
      this.xuid = xuid;
      this.uuid = UUID.nameUUIDFromBytes(("pocket-auth-1-xuid:" + xuid).getBytes(StandardCharsets.UTF_8));
      this.sessionKeyPair = sessionKeyPair;
      this.deviceId = clientDeviceId(deviceId);
      this.deviceOs = deviceOs;
      this.clientRandomId = fnv1a64(deviceId + username);
      this.skin = skin;
      this.auth = new TokenPayload(token, authType);
      this.settings = settings;
   }

   public static ClientIdentity xbox(BedrockAuthManager account, BedrockSkin skin) throws IOException {
      return xbox(account, skin, () -> ClientIdentity.Settings.DEFAULT);
   }

   public static ClientIdentity xbox(BedrockAuthManager account, BedrockSkin skin, Supplier<ClientIdentity.Settings> settings) throws IOException {
      MinecraftMultiplayerToken token = account.getMinecraftMultiplayerToken().getUpToDate();
      return new ClientIdentity(
         token.getDisplayName(),
         token.getXuid(),
         account.getSessionKeyPair(),
         account.getDeviceId(),
         ClientIdentity.Device.OS,
         skin,
         token.getToken(),
         AuthType.FULL,
         settings
      );
   }

   public static ClientIdentity offline(String username, BedrockSkin skin) {
      return offline(username, skin, () -> ClientIdentity.Settings.DEFAULT);
   }

   public static ClientIdentity offline(String username, BedrockSkin skin, Supplier<ClientIdentity.Settings> settings) {
      String xuid = Long.toString(fnv1a64(username) & Long.MAX_VALUE);
      KeyPair keys = BedrockCrypto.generateSessionKeyPair();
      UUID deviceId = UUID.nameUUIDFromBytes(("bedrocknative-device:" + username).getBytes(StandardCharsets.UTF_8));
      return new ClientIdentity(
         username, xuid, keys, deviceId, ClientIdentity.Device.OS, skin, selfSignedToken(keys, username, xuid), AuthType.SELF_SIGNED, settings
      );
   }

   public static int memoryTier() {
      return ClientIdentity.Device.MEMORY_TIER;
   }

   public static String clientDeviceId(UUID accountDeviceId) {
      try {
         byte[] hash = MessageDigest.getInstance("SHA-256").digest(("bedrocknative-device-id:" + accountDeviceId).getBytes(StandardCharsets.UTF_8));
         return HexFormat.of().formatHex(hash, 0, 16);
      } catch (NoSuchAlgorithmException e) {
         throw new IllegalStateException(e);
      }
   }

   public String username() {
      return this.username;
   }

   public UUID uuid() {
      return this.uuid;
   }

   public String xuid() {
      return this.xuid;
   }

   public String deviceId() {
      return this.deviceId;
   }

   public KeyPair sessionKeyPair() {
      return this.sessionKeyPair;
   }

   public BedrockSkin skin() {
      return this.skin;
   }

   public MultiplayerTokenClaims tokenClaims() {
      return MultiplayerTokenClaims.of(this.auth.getToken());
   }

   public OperatorIdentity netherNetIdentity() {
      IdpDomain domain = IdpDomain.of(this.auth.getToken());
      if (!domain.fromToken()) {
         BedrockNative.LOGGER.warn("NetherNet identity domain: token has no usable iss, using the default");
      }

      BedrockNative.LOGGER.info("{}", domain.describe());
      return OperatorIdentity.fromToken(this.sessionKeyPair, this.auth.getToken(), domain.domain());
   }

   public LoginPacket createLoginPacket(String serverHost, int serverPort) {
      return this.createLoginPacket(serverHost, serverPort, BedrockProtocols.latest());
   }

   public LoginPacket createLoginPacket(String serverHost, int serverPort, BedrockProtocol protocol) {
      LoginPacket login = new LoginPacket();
      login.setProtocolVersion(protocol.protocol());
      login.setAuthPayload(this.auth);
      login.setClientJwt(BedrockCrypto.signJwt(this.sessionKeyPair, sorted(this.clientData(serverHost, serverPort, protocol.gameVersion())).toString()));
      return login;
   }

   private static String selfSignedToken(KeyPair keys, String username, String xuid) {
      long now = System.currentTimeMillis() / 1000L;
      JsonObject claims = new JsonObject();
      claims.addProperty("aud", "api://auth-minecraft-services/multiplayer");
      claims.addProperty("cpk", BedrockCrypto.encodePublicKey(keys.getPublic()));
      claims.addProperty("leguuid", UUID.nameUUIDFromBytes(("pocket-auth-1-xuid:" + xuid).getBytes(StandardCharsets.UTF_8)).toString());
      claims.addProperty("mid", Long.toHexString(fnv1a64(username)).toUpperCase(Locale.ROOT));
      claims.addProperty("nid", "");
      claims.addProperty("nname", "");
      claims.addProperty("pid", "");
      claims.addProperty("pname", "");
      claims.addProperty("xid", xuid);
      claims.addProperty("xname", username);
      claims.addProperty("iat", now);
      claims.addProperty("exp", now + 31536000L);
      return BedrockCrypto.signJwt(keys, claims.toString());
   }

   JsonObject clientData(String serverHost, int serverPort) {
      return this.clientData(serverHost, serverPort, "1.26.63");
   }

   JsonObject clientData(String serverHost, int serverPort, String gameVersion) {
      ClientIdentity.Settings settings = this.settings.get();
      JsonObject c = this.skin.claims();
      if (!c.has("SkinGeometryDataEngineVersion")) {
         c.addProperty("SkinGeometryDataEngineVersion", Base64.getEncoder().encodeToString("0.0.0".getBytes(StandardCharsets.UTF_8)));
      }

      if (!c.has("TrustedSkin")) {
         c.addProperty("TrustedSkin", false);
      }

      if (!c.has("ProfileHash")) {
         c.addProperty("ProfileHash", "");
      }

      c.addProperty("OverrideSkin", false);
      c.addProperty("ServerAddress", serverHost + ":" + serverPort);
      c.addProperty("ThirdPartyName", this.username);
      c.addProperty("GameVersion", gameVersion);
      c.addProperty("LanguageCode", settings.languageCode());
      c.addProperty("GraphicsMode", settings.graphicsMode());
      c.addProperty("GuiScale", settings.guiScale());
      c.addProperty("UIProfile", 0);
      c.addProperty("ClientRandomId", this.clientRandomId);
      c.addProperty("SelfSignedId", "");
      c.addProperty("ClientEditorConnectionIntent", this.deviceOs == 8 ? 2 : 0);
      c.addProperty("ClientIsEditorCapable", this.deviceOs == 8);
      c.addProperty("FilterProfanity", true);
      c.addProperty("DeviceId", this.deviceId);
      c.addProperty("DeviceModel", ClientIdentity.Device.MODEL);
      c.addProperty("DeviceOS", this.deviceOs);
      c.addProperty("CurrentInputMode", 1);
      c.addProperty("DefaultInputMode", 1);
      c.addProperty("MemoryTier", ClientIdentity.Device.MEMORY_TIER);
      c.addProperty("MaxViewDistance", 76);
      c.addProperty("CompatibleWithClientSideChunkGen", false);
      c.addProperty("PlatformType", 0);
      c.addProperty("PlatformOfflineId", "");
      c.addProperty("PlatformOnlineId", "");
      return c;
   }

   static JsonElement sorted(JsonElement element) {
      if (element instanceof JsonObject object) {
         JsonObject sorted = new JsonObject();
         object.keySet().stream().sorted().forEach(key -> sorted.add(key, sorted(object.get(key))));
         return sorted;
      } else if (element instanceof JsonArray array) {
         JsonArray sorted = new JsonArray();
         array.forEach(item -> sorted.add(sorted(item)));
         return sorted;
      } else {
         return element;
      }
   }

   private static long fnv1a64(String text) {
      long hash = -3750763034362895579L;

      for (byte b : text.getBytes(StandardCharsets.UTF_8)) {
         hash ^= b & 0xFF;
         hash *= 1099511628211L;
      }

      return hash;
   }

   static final class Device {
      static final int WIN32 = 8;
      static final int OS = os();
      private static final @Nullable HardwareAbstractionLayer HARDWARE = hardware();
      static final String MODEL = model();
      static final int MEMORY_TIER = memoryTier(totalMemory());
      static final int MAX_VIEW_DISTANCE = 76;

      private static int os() {
         String name = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
         return name.startsWith("windows") ? 8 : (name.startsWith("mac") ? 3 : 15);
      }

      private static @Nullable HardwareAbstractionLayer hardware() {
         try {
            return new SystemInfo().getHardware();
         } catch (RuntimeException | LinkageError e) {
            return null;
         }
      }

      private static String model() {
         try {
            ComputerSystem system = HARDWARE.getComputerSystem();
            return system.getModel() + " " + system.getManufacturer() + " (Unknown)";
         } catch (RuntimeException | LinkageError e) {
            return "Unknown";
         }
      }

      private static long totalMemory() {
         try {
            return HARDWARE.getMemory().getTotal();
         } catch (RuntimeException | LinkageError e) {
            return -1L;
         }
      }

      static int memoryTier(long totalBytes) {
         if (totalBytes <= 0L) {
            return 4;
         }

         double gib = totalBytes / 1.0737418E9F;
         return gib <= 1.5 ? 0 : (gib <= 2.0 ? 1 : (gib <= 4.0 ? 2 : (gib <= 8.0 ? 3 : 4)));
      }
   }

   public record Settings(String languageCode, int graphicsMode, int guiScale) {
      public static final ClientIdentity.Settings DEFAULT = new ClientIdentity.Settings("en_US", 1, -2);
      static final int SMALLEST_GUI_SCALE = -2;

      public static ClientIdentity.Settings of(String javaLanguage, boolean fastGraphics, boolean autoGuiScale, int guiScale, int autoScale) {
         return new ClientIdentity.Settings(BedrockLocale.of(javaLanguage), fastGraphics ? 0 : 1, guiScaleOffset(autoGuiScale, guiScale, autoScale));
      }

      public static int guiScaleOffset(boolean auto, int scale, int autoScale) {
         return auto ? 0 : Math.clamp(scale - autoScale, -2, 0);
      }
   }
}
