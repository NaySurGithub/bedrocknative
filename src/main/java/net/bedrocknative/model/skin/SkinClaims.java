package net.bedrocknative.model.skin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.awt.Color;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.cloudburstmc.protocol.bedrock.data.skin.AnimatedTextureType;
import org.cloudburstmc.protocol.bedrock.data.skin.AnimationData;
import org.cloudburstmc.protocol.bedrock.data.skin.AnimationExpressionType;
import org.cloudburstmc.protocol.bedrock.data.skin.ImageData;
import org.cloudburstmc.protocol.bedrock.data.skin.PersonaPieceData;
import org.cloudburstmc.protocol.bedrock.data.skin.PersonaPieceTintData;
import org.cloudburstmc.protocol.bedrock.data.skin.PersonaPieceType;
import org.cloudburstmc.protocol.bedrock.data.skin.SerializedSkin;
import org.jspecify.annotations.Nullable;

public final class SkinClaims {
   private SkinClaims() {
   }

   public static SerializedSkin toSerialized(JsonObject claims) {
      List<AnimationData> animations = new ArrayList<>();
      JsonElement tints = claims.get("AnimatedImageData");
      if (tints instanceof JsonArray) {
         for (JsonElement element : (JsonArray)tints) {
            if (element instanceof JsonObject animation) {
               int type = integer(animation, "Type", 0);
               int expression = integer(animation, "AnimationExpression", 0);
               animations.add(
                  new AnimationData(
                     ImageData.of(integer(animation, "ImageWidth", 0), integer(animation, "ImageHeight", 0), bytes(animation, "Image")),
                     AnimatedTextureType.from(Math.clamp(type, 0, AnimatedTextureType.values().length - 1)),
                     (float)number(animation, "Frames", 1.0),
                     AnimationExpressionType.from(Math.clamp(expression, 0, 1))
                  )
               );
            }
         }
      }

      List<PersonaPieceData> pieces = new ArrayList<>();
      if (claims.get("PersonaPieces") instanceof JsonArray list) {
         for (JsonElement element : list) {
            if (element instanceof JsonObject piece) {
               try {
                  pieces.add(
                     new PersonaPieceData(
                        string(piece, "PieceId"),
                        string(piece, "PieceType"),
                        string(piece, "PackId"),
                        piece.has("IsDefault") && piece.get("IsDefault").getAsBoolean(),
                        string(piece, "ProductId")
                     )
                  );
               } catch (RuntimeException var12) {
               }
            }
         }
      }

      List<PersonaPieceTintData> tintsx = new ArrayList<>();
      if (claims.get("PieceTintColors") instanceof JsonArray list) {
         for (JsonElement element : list) {
            if (element instanceof JsonObject tint && tint.get("Colors") instanceof JsonArray colors) {
               List<Color> values = new ArrayList<>();

               for (int i = 0; i < 4; i++) {
                  values.add(i < colors.size() ? color(colors.get(i).getAsString()) : new Color(0, true));
               }

               try {
                  tintsx.add(new PersonaPieceTintData(PersonaPieceType.fromName(string(tint, "PieceType")), values));
               } catch (RuntimeException var11) {
               }
            }
         }
      }

      String skinColor = string(claims, "SkinColor");
      Color color = color(skinColor);
      return SerializedSkin.builder()
         .skinId(string(claims, "SkinId"))
         .playFabId(string(claims, "PlayFabId"))
         .skinResourcePatch(text(claims, "SkinResourcePatch"))
         .skinData(ImageData.of(integer(claims, "SkinImageWidth", 0), integer(claims, "SkinImageHeight", 0), bytes(claims, "SkinData")))
         .animations(animations)
         .capeData(ImageData.of(integer(claims, "CapeImageWidth", 0), integer(claims, "CapeImageHeight", 0), bytes(claims, "CapeData")))
         .geometryData(text(claims, "SkinGeometryData"))
         .geometryDataEngineVersion(claims.has("SkinGeometryDataEngineVersion") ? text(claims, "SkinGeometryDataEngineVersion") : "0.0.0")
         .animationData(text(claims, "SkinAnimationData"))
         .premium(bool(claims, "PremiumSkin"))
         .persona(bool(claims, "PersonaSkin"))
         .capeOnClassic(bool(claims, "CapeOnClassicSkin"))
         .primaryUser(true)
         .capeId(string(claims, "CapeId"))
         .fullSkinId(string(claims, "SkinId") + string(claims, "CapeId"))
         .armSize(claims.has("ArmSize") && !string(claims, "ArmSize").isEmpty() ? string(claims, "ArmSize") : "wide")
         .skinColor(skinColor.isEmpty() ? "#0" : skinColor)
         .color(color)
         .personaPieces(pieces)
         .tintColors(tintsx)
         .trusted(bool(claims, "TrustedSkin"))
         .build();
   }

   private static Color color(String text) {
      if (!text.isEmpty() && !text.equals("#0")) {
         try {
            return new Color((int)Long.parseLong(text.startsWith("#") ? text.substring(1) : text, 16), true);
         } catch (NumberFormatException e) {
            return new Color(0, true);
         }
      } else {
         return new Color(0, true);
      }
   }

   private static String string(JsonObject json, String key) {
      JsonElement value = json.get(key);
      return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
   }

   private static String text(JsonObject json, String key) {
      byte[] bytes = bytes(json, key);
      return new String(bytes, StandardCharsets.UTF_8);
   }

   private static byte[] bytes(JsonObject json, String key) {
      String value = string(json, key);
      if (value.isEmpty()) {
         return new byte[0];
      }

      try {
         return Base64.getDecoder().decode(value);
      } catch (IllegalArgumentException e) {
         return new byte[0];
      }
   }

   private static int integer(JsonObject json, String key, int fallback) {
      return (int)number(json, key, fallback);
   }

   private static double number(JsonObject json, String key, double fallback) {
      JsonElement value = json.get(key);

      try {
         return value != null && value.isJsonPrimitive() ? value.getAsDouble() : fallback;
      } catch (NumberFormatException e) {
         return fallback;
      }
   }

   private static boolean bool(JsonObject json, String key) {
      JsonElement value = json.get(key);
      return value != null && value.isJsonPrimitive() && value.getAsBoolean();
   }

   public static String describe(@Nullable JsonObject claims) {
      return claims == null ? "none" : PlayerSkin.of(toSerialized(claims)).describe();
   }
}
