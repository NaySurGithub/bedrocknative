package net.bedrocknative.model.skin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.Color;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import net.bedrocknative.model.geometry.TextureMesher;
import org.cloudburstmc.protocol.bedrock.data.skin.AnimatedTextureType;
import org.cloudburstmc.protocol.bedrock.data.skin.AnimationData;
import org.cloudburstmc.protocol.bedrock.data.skin.AnimationExpressionType;
import org.cloudburstmc.protocol.bedrock.data.skin.ImageData;
import org.cloudburstmc.protocol.bedrock.data.skin.PersonaPieceData;
import org.cloudburstmc.protocol.bedrock.data.skin.PersonaPieceTintData;
import org.cloudburstmc.protocol.bedrock.data.skin.SerializedSkin;
import org.jspecify.annotations.Nullable;

public final class PlayerSkin {
   public static final String STEVE_TEXTURE = "textures/entity/steve";
   public static final String ALEX_TEXTURE = "textures/entity/alex";
   public static final String WIDE_GEOMETRY = "geometry.humanoid.custom";
   public static final String SLIM_GEOMETRY = "geometry.humanoid.customSlim";
   private static final int[][] SKIN_SIZES = new int[][]{{64, 32}, {64, 64}, {128, 128}, {256, 256}};
   private static final int[][] CAPE_SIZES = new int[][]{{64, 32}};
   private static final int MAX_ANIMATION_SIDE = 1024;
   static final int MAX_GEOMETRY_CHARS = 4194304;
   private final String key;
   private final String skinId;
   private final String fullSkinId;
   private final String playFabId;
   private final TextureMesher.@Nullable Pixels image;
   private final String geometryData;
   private final String geometryEngineVersion;
   private final Map<String, String> geometryNames;
   private final boolean slim;
   private final Map<PlayerSkin.Animated, PlayerSkin.Animation> animations;
   private final TextureMesher.@Nullable Pixels cape;
   private final String capeId;
   private final boolean capeOnClassic;
   private final boolean premium;
   private final boolean persona;
   private final boolean trusted;
   private final List<PlayerSkin.PersonaPiece> pieces;
   private final List<PlayerSkin.PieceTint> tints;
   private final int skinColor;
   private final @Nullable String fallback;

   private PlayerSkin(
      String skinId,
      String fullSkinId,
      String playFabId,
      TextureMesher.@Nullable Pixels image,
      String geometryData,
      String geometryEngineVersion,
      Map<String, String> geometryNames,
      boolean slim,
      Map<PlayerSkin.Animated, PlayerSkin.Animation> animations,
      TextureMesher.@Nullable Pixels cape,
      String capeId,
      boolean capeOnClassic,
      boolean premium,
      boolean persona,
      boolean trusted,
      List<PlayerSkin.PersonaPiece> pieces,
      List<PlayerSkin.PieceTint> tints,
      int skinColor,
      @Nullable String fallback
   ) {
      this.skinId = skinId;
      this.fullSkinId = fullSkinId;
      this.playFabId = playFabId;
      this.image = image;
      this.geometryData = geometryData;
      this.geometryEngineVersion = geometryEngineVersion;
      this.geometryNames = Collections.unmodifiableMap(geometryNames);
      this.slim = slim;
      this.animations = Collections.unmodifiableMap(animations);
      this.cape = cape;
      this.capeId = capeId;
      this.capeOnClassic = capeOnClassic;
      this.premium = premium;
      this.persona = persona;
      this.trusted = trusted;
      this.pieces = List.copyOf(pieces);
      this.tints = List.copyOf(tints);
      this.skinColor = skinColor;
      this.fallback = fallback;
      this.key = image == null ? (slim ? "default_alex" : "default_steve") : this.contentKey();
   }

   public static PlayerSkin defaultSkin(boolean slim, @Nullable String reason) {
      Map<String, String> names = new LinkedHashMap<>();
      names.put("default", slim ? "geometry.humanoid.customSlim" : "geometry.humanoid.custom");
      return new PlayerSkin(
         slim ? "Standard_Alex" : "Standard_Steve",
         "",
         "",
         null,
         "",
         "0.0.0",
         names,
         slim,
         new EnumMap<>(PlayerSkin.Animated.class),
         null,
         "",
         false,
         false,
         false,
         true,
         List.of(),
         List.of(),
         0,
         reason
      );
   }

   public static PlayerSkin of(@Nullable SerializedSkin skin) {
      if (skin == null) {
         return defaultSkin(false, "no skin");
      }

      boolean slim = "slim".equalsIgnoreCase(skin.getArmSize());

      try {
         ImageData data = skin.getSkinData();
         TextureMesher.Pixels image = data != null ? image(data.getWidth(), data.getHeight(), data.getImage(), SKIN_SIZES) : null;
         if (image == null) {
            return defaultSkin(
               slim,
               data == null
                  ? "no skin image"
                  : "skin image "
                     + data.getWidth()
                     + "x"
                     + data.getHeight()
                     + " ("
                     + (data.getImage() != null ? data.getImage().length : 0)
                     + " bytes) is not a size Bedrock accepts"
            );
         }

         Map<String, String> names = geometryNames(rawPatch(skin));
         if (names.get("default") == null) {
            return defaultSkin(slim, "the resource patch names no default geometry");
         }

         String geometry = skin.getGeometryData() != null ? skin.getGeometryData() : "";
         if (geometry.length() > 4194304) {
            return defaultSkin(slim, "geometry data of " + geometry.length() + " characters is oversized");
         }

         Map<PlayerSkin.Animated, PlayerSkin.Animation> animations = new EnumMap<>(PlayerSkin.Animated.class);
         if (skin.getAnimations() != null) {
            for (AnimationData animation : skin.getAnimations()) {
               PlayerSkin.Animated type = animation != null ? PlayerSkin.Animated.of(animation.getTextureType()) : null;
               ImageData frames = animation != null ? animation.getImage() : null;
               TextureMesher.Pixels pixels = type != null && frames != null ? image(frames.getWidth(), frames.getHeight(), frames.getImage(), null) : null;
               if (pixels != null && !animations.containsKey(type)) {
                  animations.put(
                     type,
                     new PlayerSkin.Animation(
                        type, pixels, Math.max(1, Math.round(animation.getFrames())), animation.getExpressionType() == AnimationExpressionType.BLINKING
                     )
                  );
               }
            }
         }

         ImageData capeData = skin.getCapeData();
         TextureMesher.Pixels cape = capeData != null && capeData.getImage() != null && capeData.getImage().length > 0
            ? image(capeData.getWidth(), capeData.getHeight(), capeData.getImage(), CAPE_SIZES)
            : null;
         List<PlayerSkin.PersonaPiece> pieces = new ArrayList<>();
         if (skin.getPersonaPieces() != null) {
            for (PersonaPieceData piece : skin.getPersonaPieces()) {
               pieces.add(
                  new PlayerSkin.PersonaPiece(
                     String.valueOf(piece.getId()),
                     piece.getPieceType() != null ? piece.getType() : "unknown",
                     piece.getPackUuid() != null ? piece.getPackId() : "",
                     piece.isDefault(),
                     String.valueOf(piece.getProductId())
                  )
               );
            }
         }

         List<PlayerSkin.PieceTint> tints = new ArrayList<>();
         if (skin.getTintColors() != null) {
            for (PersonaPieceTintData tint : skin.getTintColors()) {
               tints.add(new PlayerSkin.PieceTint(tint.getPieceType() != null ? tint.getType() : "unknown", tintColors(tint)));
            }
         }

         Color color = skin.getColor();
         return new PlayerSkin(
            nonNull(skin.getSkinId()),
            nonNull(skin.getFullSkinId()),
            nonNull(skin.getPlayFabId()),
            image,
            geometry,
            nonNull(skin.getGeometryDataEngineVersion()),
            names,
            slim,
            animations,
            cape,
            nonNull(skin.getCapeId()),
            skin.isCapeOnClassic(),
            skin.isPremium(),
            skin.isPersona(),
            skin.isTrusted(),
            pieces,
            tints,
            color != null ? color.getRGB() : 0,
            null
         );
      } catch (RuntimeException e) {
         return defaultSkin(slim, "unreadable skin: " + e);
      }
   }

   private static List<Integer> tintColors(PersonaPieceTintData tint) {
      List<Integer> colors = new ArrayList<>();

      try {
         List<Color> list = tint.getColorsNew();
         if (list != null && !list.isEmpty()) {
            list.forEach(color -> colors.add(color.getRGB()));
            return List.copyOf(colors);
         }

         List<String> texts = tint.getColors();
         if (texts != null) {
            for (String text : texts) {
               String hex = text.startsWith("#") ? text.substring(1) : text;
               colors.add((int)Long.parseLong(hex, 16));
            }
         }
      } catch (RuntimeException var7) {
      }

      return List.copyOf(colors);
   }

   private static @Nullable String rawPatch(SerializedSkin skin) {
      try {
         return skin.getSkinResourcePatch();
      } catch (RuntimeException e) {
         return null;
      }
   }

   static Map<String, String> geometryNames(@Nullable String patch) {
      Map<String, String> names = new LinkedHashMap<>();
      if (patch != null && !patch.isBlank()) {
         try {
            JsonElement root = JsonParser.parseString(patch);
            if ((root.isJsonObject() ? root.getAsJsonObject().get("geometry") : null) instanceof JsonObject object) {
               for (Entry<String, JsonElement> entry : object.entrySet()) {
                  if (entry.getValue().isJsonPrimitive() && !entry.getValue().getAsString().isBlank()) {
                     names.put(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue().getAsString());
                  }
               }
            }
         } catch (RuntimeException var7) {
         }

         return names;
      } else {
         return names;
      }
   }

   static TextureMesher.@Nullable Pixels image(int width, int height, byte @Nullable [] rgba, int @Nullable [][] allowed) {
      if (rgba != null && width > 0 && height > 0 && width <= 1024 && height <= 1024 && (long)width * height * 4L == rgba.length) {
         if (allowed != null) {
            boolean ok = false;

            for (int[] size : allowed) {
               ok |= size[0] == width && size[1] == height;
            }

            if (!ok) {
               return null;
            }
         }

         int[] argb = new int[width * height];
         int i = 0;

         for (int p = 0; i < argb.length; p += 4) {
            argb[i] = (rgba[p + 3] & 255) << 24 | (rgba[p] & 255) << 16 | (rgba[p + 1] & 255) << 8 | rgba[p + 2] & 255;
            i++;
         }

         return new TextureMesher.Pixels(width, height, argb);
      } else {
         return null;
      }
   }

   private String contentKey() {
      long hash = -3750763034362895579L;
      hash = mix(hash, this.slim ? 1 : 2);
      hash = mix(hash, this.image.width());
      hash = mix(hash, this.image.height());

      for (int pixel : this.image.argb()) {
         hash = mix(hash, pixel);
      }

      hash = mix(hash, this.geometryData);
      hash = mix(hash, this.geometryNames.toString());
      if (this.cape != null) {
         for (int pixel : this.cape.argb()) {
            hash = mix(hash, pixel);
         }
      }

      for (PlayerSkin.Animation animation : this.animations.values()) {
         hash = mix(hash, animation.type().ordinal() * 31 + animation.frames() * 7 + (animation.blinking() ? 1 : 0));

         for (int pixel : animation.image().argb()) {
            hash = mix(hash, pixel);
         }
      }

      return Long.toHexString(hash);
   }

   private static long mix(long hash, int value) {
      for (int shift = 0; shift < 32; shift += 8) {
         hash ^= value >>> shift & 0xFF;
         hash *= 1099511628211L;
      }

      return hash;
   }

   private static long mix(long hash, String text) {
      byte[] bytes = text.getBytes(StandardCharsets.UTF_8);

      for (byte b : bytes) {
         hash ^= b & 0xFF;
         hash *= 1099511628211L;
      }

      return mix(hash, bytes.length);
   }

   private static String nonNull(@Nullable String text) {
      return text != null ? text : "";
   }

   public String key() {
      return this.key;
   }

   public String skinId() {
      return this.skinId;
   }

   public String fullSkinId() {
      return this.fullSkinId;
   }

   public String playFabId() {
      return this.playFabId;
   }

   public boolean isDefault() {
      return this.image == null;
   }

   public @Nullable String fallback() {
      return this.fallback;
   }

   public TextureMesher.@Nullable Pixels image() {
      return this.image;
   }

   public String defaultTexture() {
      return this.slim ? "textures/entity/alex" : "textures/entity/steve";
   }

   public String geometryData() {
      return this.geometryData;
   }

   public String geometryEngineVersion() {
      return this.geometryEngineVersion;
   }

   public Map<String, String> geometryNames() {
      return this.geometryNames;
   }

   public boolean slim() {
      return this.slim;
   }

   public Map<PlayerSkin.Animated, PlayerSkin.Animation> animations() {
      return this.animations;
   }

   public TextureMesher.@Nullable Pixels cape() {
      return this.cape;
   }

   public boolean hasCape() {
      return this.cape != null;
   }

   public String capeId() {
      return this.capeId;
   }

   public boolean capeOnClassic() {
      return this.capeOnClassic;
   }

   public boolean premium() {
      return this.premium;
   }

   public boolean persona() {
      return this.persona;
   }

   public boolean personaOrPremium() {
      return this.persona || this.premium;
   }

   public boolean trusted() {
      return this.trusted;
   }

   public List<PlayerSkin.PersonaPiece> pieces() {
      return this.pieces;
   }

   public List<PlayerSkin.PieceTint> tints() {
      return this.tints;
   }

   public int skinColor() {
      return this.skinColor;
   }

   public String describe() {
      return this.image == null
         ? "default " + (this.slim ? "Alex" : "Steve") + (this.fallback != null ? " (" + this.fallback + ")" : "")
         : (this.persona ? "persona " : (this.premium ? "premium " : "classic "))
            + this.image.width()
            + "x"
            + this.image.height()
            + " "
            + (this.slim ? "slim" : "wide")
            + " "
            + this.geometryNames.get("default")
            + (this.cape != null ? " with a cape" : "")
            + (this.animations.isEmpty() ? "" : " animated " + this.animations.keySet());
   }

   @Override
   public String toString() {
      return this.describe() + " [" + this.key + "]";
   }

   public enum Animated {
      FACE("animated_face", "animation_frames_face", "face"),
      BODY_32("animated_32x32", "animation_frames_32x32", "body32"),
      BODY_128("animated_128x128", "animation_frames_128x128", "body128");

      private final String geometry;
      private final String framesVariable;
      private final String part;

      Animated(String geometry, String framesVariable, String part) {
         this.geometry = geometry;
         this.framesVariable = framesVariable;
         this.part = part;
      }

      public String geometry() {
         return this.geometry;
      }

      public String framesVariable() {
         return this.framesVariable;
      }

      public String part() {
         return this.part;
      }

      static PlayerSkin.@Nullable Animated of(@Nullable AnimatedTextureType type) {
         return switch (type) {
            case FACE -> FACE;
            case BODY_32X32 -> BODY_32;
            case BODY_128X128 -> BODY_128;
            case null, default -> null;
         };
      }
   }

   public record Animation(PlayerSkin.Animated type, TextureMesher.Pixels image, int frames, boolean blinking) {
   }

   public record PersonaPiece(String id, String type, String packId, boolean isDefault, String productId) {
   }

   public record PieceTint(String type, List<Integer> colors) {
   }
}
