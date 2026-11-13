package net.bedrocknative.net.auth;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Base64.Encoder;
import java.util.zip.GZIPInputStream;
import javax.imageio.ImageIO;

public final class BedrockSkin {
   private static final String DEFAULT_STEVE = "/assets/bedrocknative/skins/steve.json.gz";
   public static final List<String> KEYS = List.of(
      "AnimatedImageData",
      "ArmSize",
      "CapeData",
      "CapeId",
      "CapeImageHeight",
      "CapeImageWidth",
      "CapeOnClassicSkin",
      "PersonaPieces",
      "PersonaSkin",
      "PieceTintColors",
      "PremiumSkin",
      "ProfileHash",
      "SkinAnimationData",
      "SkinColor",
      "SkinData",
      "SkinGeometryData",
      "SkinGeometryDataEngineVersion",
      "SkinId",
      "SkinImageHeight",
      "SkinImageWidth",
      "SkinResourcePatch",
      "TrustedSkin"
   );
   private final JsonObject claims;
   private final boolean standIn;

   private BedrockSkin(JsonObject claims, boolean standIn) {
      this.claims = claims;
      this.standIn = standIn;
   }

   public static BedrockSkin defaultSteve() {
      try {
         BedrockSkin var2;
         try (InputStream raw = BedrockSkin.class.getResourceAsStream("/assets/bedrocknative/skins/steve.json.gz")) {
            if (raw == null) {
               throw new IllegalStateException("Missing bundled resource /assets/bedrocknative/skins/steve.json.gz");
            }

            try (InputStreamReader reader = new InputStreamReader(new GZIPInputStream(raw), StandardCharsets.UTF_8)) {
               var2 = new BedrockSkin(JsonParser.parseReader(reader).getAsJsonObject(), true);
            }
         }

         return var2;
      } catch (IOException var8) {
         throw new UncheckedIOException(var8);
      }
   }

   public static BedrockSkin classicFromSkinPack(Path skinPack, String textureName, boolean slim) throws IOException {
      BufferedImage image = ImageIO.read(skinPack.resolve(textureName).toFile());
      byte[] rgba = new byte[image.getWidth() * image.getHeight() * 4];
      int y = 0;

      for (int i = 0; y < image.getHeight(); y++) {
         for (int x = 0; x < image.getWidth(); x++) {
            int argb = image.getRGB(x, y);
            rgba[i++] = (byte)(argb >> 16);
            rgba[i++] = (byte)(argb >> 8);
            rgba[i++] = (byte)argb;
            rgba[i++] = (byte)(argb >>> 24);
         }
      }

      String geometry = JsonParser.parseString(Files.readString(skinPack.resolve("geometry.json"))).toString();
      Encoder b64 = Base64.getEncoder();
      JsonObject c = new JsonObject();
      c.addProperty("SkinId", UUID.nameUUIDFromBytes(rgba).toString());
      c.addProperty("SkinData", b64.encodeToString(rgba));
      c.addProperty("SkinImageWidth", image.getWidth());
      c.addProperty("SkinImageHeight", image.getHeight());
      c.addProperty("SkinGeometryData", b64.encodeToString(geometry.getBytes(StandardCharsets.UTF_8)));
      c.addProperty("SkinGeometryDataEngineVersion", b64.encodeToString("0.0.0".getBytes(StandardCharsets.UTF_8)));
      c.addProperty(
         "SkinResourcePatch",
         b64.encodeToString(("{\"geometry\":{\"default\":\"geometry.humanoid.custom" + (slim ? "Slim" : "") + "\"}}").getBytes(StandardCharsets.UTF_8))
      );
      c.addProperty("SkinAnimationData", "");
      c.addProperty("SkinColor", "#0");
      c.addProperty("PremiumSkin", false);
      c.addProperty("PersonaSkin", false);
      c.addProperty("ArmSize", slim ? "slim" : "wide");
      c.add("AnimatedImageData", new JsonArray());
      c.add("PersonaPieces", new JsonArray());
      c.add("PieceTintColors", new JsonArray());
      c.addProperty("CapeId", "");
      c.addProperty("CapeData", "");
      c.addProperty("CapeImageWidth", 0);
      c.addProperty("CapeImageHeight", 0);
      c.addProperty("CapeOnClassicSkin", false);
      return new BedrockSkin(c, true);
   }

   public static BedrockSkin fromClientData(JsonObject clientData) {
      JsonObject skin = new JsonObject();

      for (String key : KEYS) {
         JsonElement value = clientData.get(key);
         if (value != null) {
            skin.add(key, value.deepCopy());
         }
      }

      return validated(skin);
   }

   public static Optional<BedrockSkin> fromRealClientLogin(String authJson, String clientDataJwt, String ownXuid, String ownDeviceId) {
      JsonElement auth = JsonParser.parseString(authJson);
      if (auth.isJsonObject()) {
         JsonElement xuid = auth.getAsJsonObject().get("Token");
         if (xuid instanceof JsonElement && xuid.isJsonPrimitive()) {
            xuid = MultiplayerTokenClaims.payload(xuid.getAsString()).get("xid");
            if (xuid != null && xuid.isJsonPrimitive() && xuid.getAsString().equals(ownXuid)) {
               JsonObject clientData = MultiplayerTokenClaims.payload(clientDataJwt);
               JsonElement deviceId = clientData.get("DeviceId");
               if (deviceId != null && deviceId.isJsonPrimitive() && !deviceId.getAsString().equalsIgnoreCase(ownDeviceId)) {
                  return Optional.of(fromClientData(clientData));
               }

               return Optional.empty();
            }

            return Optional.empty();
         }
      }

      return Optional.empty();
   }

   public static BedrockSkin load(Path file) throws IOException {
      JsonElement json;
      try {
         json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
      } catch (RuntimeException var4) {
         throw new IOException("Not a skin file: " + file, var4);
      }

      if (!json.isJsonObject()) {
         throw new IOException("Not a skin file: " + file);
      } else {
         try {
            return fromClientData(json.getAsJsonObject());
         } catch (IllegalArgumentException var3) {
            throw new IOException("Not a usable skin in " + file + ": " + var3.getMessage(), var3);
         }
      }
   }

   public void save(Path file) throws IOException {
      Files.createDirectories(file.toAbsolutePath().getParent());
      Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
      Files.writeString(temporary, new GsonBuilder().disableHtmlEscaping().create().toJson(this.claims), StandardCharsets.UTF_8);

      try {
         Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException var4) {
         Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
      }
   }

   public JsonObject claims() {
      return this.claims.deepCopy();
   }

   public String skinId() {
      return this.claims.get("SkinId").getAsString();
   }

   public boolean standIn() {
      return this.standIn;
   }

   public boolean persona() {
      JsonElement var2 = this.claims.get("PersonaSkin");
      return var2 instanceof JsonElement && var2.isJsonPrimitive() && var2.getAsBoolean();
   }

   public String describe() {
      return (this.persona() ? "persona" : "classic") + " skin " + this.claims.get("SkinImageWidth") + "x" + this.claims.get("SkinImageHeight");
   }

   private static BedrockSkin validated(JsonObject skin) {
      JsonElement data = skin.get("SkinId");
      if (data instanceof JsonElement && data.isJsonPrimitive() && !data.getAsString().isEmpty()) {
         JsonElement width = skin.get("SkinData");
         if (width instanceof JsonElement) {
            data = width;
            if (width.isJsonPrimitive()) {
               JsonElement expected = skin.get("SkinImageWidth");
               if (expected instanceof JsonElement) {
                  expected = skin.get("SkinImageHeight");
                  if (expected instanceof JsonElement && expected.isJsonPrimitive() && expected.isJsonPrimitive()) {
                     long expectedx = (long)expected.getAsInt() * expected.getAsInt() * 4L;

                     int actual;
                     try {
                        actual = Base64.getDecoder().decode(data.getAsString()).length;
                     } catch (IllegalArgumentException var9) {
                        throw new IllegalArgumentException("SkinData is not base64");
                     }

                     if (expectedx > 0L && actual == expectedx) {
                        return new BedrockSkin(skin, false);
                     }

                     throw new IllegalArgumentException("SkinData holds " + actual + " bytes for a " + expected + "x" + expected + " image");
                  }
               }

               throw new IllegalArgumentException("no image size");
            }
         }

         throw new IllegalArgumentException("no SkinData");
      } else {
         throw new IllegalArgumentException("no SkinId");
      }
   }
}
