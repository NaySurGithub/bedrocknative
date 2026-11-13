package net.bedrocknative.oreui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.content.BedrockInstall;
import org.jspecify.annotations.Nullable;

public final class OreInstall {
   private static final Pattern HASHED = Pattern.compile("(.+)-([0-9a-f]{16,40})\\.(png|jpg|jpeg|gif|otf|ttf)", 2);
   private static final Pattern LINK = Pattern.compile("href=\"/hbui/([^\"]+\\.css)\"");
   private static final Pattern FONT_FACE = Pattern.compile("@font-face\\{([^}]*)\\}");
   private static final Pattern FONT_FAMILY = Pattern.compile("font-family:([^;}]+)");
   private static final Pattern FONT_SRC = Pattern.compile("src:url\\(ui://hbui/fonts/([^)]+)\\)");
   private static final Pattern FONT_WEIGHT = Pattern.compile("font-weight:(\\d+)");
   private static final Pattern FONT_STYLE = Pattern.compile("font-style:(\\w+)");
   private static final long MAX_TEXT_BYTES = 8388608L;
   private final Path folder;
   private final Map<String, OreInstall.Sprite> sprites;
   private final Map<String, Path> pictures;
   private final Map<String, Path> fontFaces;
   private final OreThemeVars theme;
   private final Map<Path, byte[]> generated;
   private final Map<String, OreFont> ownFonts;
   @Nullable
   static volatile Set<String> asked;

   private OreInstall(Path folder, Map<String, OreInstall.Sprite> sprites, Map<String, Path> pictures, Map<String, Path> fontFaces, OreThemeVars theme) {
      this(folder, sprites, pictures, fontFaces, theme, Map.of(), Map.of());
   }

   private OreInstall(
      Path folder,
      Map<String, OreInstall.Sprite> sprites,
      Map<String, Path> pictures,
      Map<String, Path> fontFaces,
      OreThemeVars theme,
      Map<Path, byte[]> generated,
      Map<String, OreFont> ownFonts
   ) {
      this.folder = folder;
      this.sprites = Map.copyOf(sprites);
      this.pictures = Map.copyOf(pictures);
      this.fontFaces = Map.copyOf(fontFaces);
      this.theme = theme;
      this.generated = Map.copyOf(generated);
      this.ownFonts = Map.copyOf(ownFonts);
   }

   static OreInstall own(
      Path folder, Map<String, byte[]> pngs, Map<String, int[]> sizes, Set<String> loosePictures, Map<String, OreFont> fonts, OreThemeVars theme
   ) {
      Map<String, OreInstall.Sprite> sprites = new HashMap<>();
      Map<String, Path> pictures = new HashMap<>();
      Map<Path, byte[]> files = new HashMap<>();
      pngs.forEach((name, png) -> {
         Path file = folder.resolve(name.replace('@', '_') + ".png").toAbsolutePath().normalize();
         int[] size = sizes.get(name);
         files.put(file, png);
         sprites.put(name, new OreInstall.Sprite(file, 0, 0, size[0], size[1], false));
         if (loosePictures.contains(name)) {
            pictures.put(name, file);
         }
      });
      Map<String, OreFont> faces = new HashMap<>();
      fonts.forEach((key, font) -> faces.put(key.toLowerCase(Locale.ROOT), font));
      return new OreInstall(folder, sprites, pictures, Map.of(), theme, files, faces);
   }

   public boolean own() {
      return !this.generated.isEmpty();
   }

   public Optional<byte[]> generated(Path file) {
      byte[] png = this.generated.get(file.toAbsolutePath().normalize());
      return png == null ? Optional.empty() : Optional.of(png);
   }

   @Nullable
   public OreFont ownFont(String family, int weight) {
      OreFont exact = this.ownFonts.get(faceKey(family, weight, "normal"));
      return exact != null ? exact : this.ownFonts.get(faceKey(family, 400, "normal"));
   }

   public static Path defaultFolder() {
      return BedrockInstall.dataFolder().resolve("gui").resolve("dist").resolve("hbui");
   }

   public static Path textsFolder() {
      return BedrockInstall.dataFolder().resolve("resource_packs").resolve("oreui").resolve("texts");
   }

   @Nullable
   public static OreInstall read(Path folder) {
      try {
         if (Files.isRegularFile(folder.resolve("index.html")) && Files.isRegularFile(folder.resolve("atlas.json"))) {
            String index = text(folder.resolve("index.html"));
            Path themeCss = null;
            Path pageCss = null;
            Matcher links = LINK.matcher(index);

            while (links.find()) {
               String name = links.group(1);
               if (!name.contains("/") && !name.contains("\\") && !name.contains("..")) {
                  if (name.startsWith("menus-theme-")) {
                     themeCss = folder.resolve(name);
                  } else if (name.startsWith("index-")) {
                     pageCss = folder.resolve(name);
                  }
               }
            }

            if (themeCss != null && pageCss != null && Files.isRegularFile(themeCss) && Files.isRegularFile(pageCss)) {
               Map<String, OreInstall.Sprite> sprites = new HashMap<>();
               Map<String, Path> pictures = new HashMap<>();
               readLooseAssets(folder.resolve("assets"), sprites, pictures);
               readAtlas(folder, sprites);
               Map<String, Path> faces = readFontFaces(folder.resolve("fonts"), text(pageCss));
               if (faces.isEmpty()) {
                  return null;
               } else {
                  OreThemeVars theme = OreThemeVars.parse(text(themeCss));
                  return new OreInstall(folder, sprites, pictures, faces, theme);
               }
            } else {
               return null;
            }
         } else {
            return null;
         }
      } catch (RuntimeException | IOException var9) {
         BedrockNative.LOGGER.warn("Ore UI: couldn't read the install's {}", folder, var9);
         return null;
      }
   }

   public Path folder() {
      return this.folder;
   }

   public Optional<OreInstall.Sprite> sprite(String name) {
      Set<String> recording = asked;
      if (recording != null) {
         recording.add(name);
      }

      return Optional.ofNullable(this.sprites.get(name));
   }

   public boolean has(String name) {
      return this.sprites.containsKey(name);
   }

   public Optional<Path> picture(String name) {
      return Optional.ofNullable(this.pictures.get(name));
   }

   public Optional<Path> font(String family, int weight, String style) {
      Path exact = this.fontFaces.get(faceKey(family, weight, style));
      return exact != null ? Optional.of(exact) : Optional.ofNullable(this.fontFaces.get(faceKey(family, 400, "normal")));
   }

   public OreThemeVars theme() {
      return this.theme;
   }

   public int spriteCount() {
      return this.sprites.size();
   }

   static String faceKey(String family, int weight, String style) {
      return family.trim().toLowerCase(Locale.ROOT) + "|" + (weight >= 600 ? 700 : 400) + "|" + style.trim().toLowerCase(Locale.ROOT);
   }

   @Nullable
   static String nameOf(String fileName) {
      Matcher matcher = HASHED.matcher(fileName);
      return matcher.matches() ? matcher.group(1) : null;
   }

   private static void readLooseAssets(Path assets, Map<String, OreInstall.Sprite> sprites, Map<String, Path> pictures) throws IOException {
      if (Files.isDirectory(assets)) {
         try (Stream<Path> files = Files.list(assets)) {
            for (Path file : files.sorted().toList()) {
               String fileName = file.getFileName().toString();
               String name = nameOf(fileName);
               if (name != null) {
                  String lower = fileName.toLowerCase(Locale.ROOT);
                  if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")) {
                     pictures.putIfAbsent(name, file);
                  }

                  if (lower.endsWith(".png")) {
                     int[] size = pngSize(file);
                     if (size != null) {
                        sprites.putIfAbsent(name, new OreInstall.Sprite(file, 0, 0, size[0], size[1], false));
                     }
                  }
               }
            }
         }
      }
   }

   private static void readAtlas(Path folder, Map<String, OreInstall.Sprite> sprites) throws IOException {
      if (JsonParser.parseString(text(folder.resolve("atlas.json"))) instanceof JsonArray sheets) {
         for (JsonElement element : sheets) {
            if (element instanceof JsonObject sheet) {
               JsonElement sheetName = sheet.get("name");
               if (sheetName instanceof JsonElement && sheetName.isJsonPrimitive() && sheet.get("coordinates") instanceof JsonObject coordinates) {
                  String sheetNamex = sheetName.getAsString();
                  if (!sheetNamex.contains("/") && !sheetNamex.contains("\\") && !sheetNamex.contains("..")) {
                     Path sheetFile = folder.resolve(sheetNamex);
                     int[] sheetSize = Files.isRegularFile(sheetFile) ? pngSize(sheetFile) : null;
                     if (sheetSize != null) {
                        for (Entry<String, JsonElement> entry : coordinates.entrySet()) {
                           String key = entry.getKey();
                           String name = nameOf(key.substring(key.lastIndexOf(47) + 1));
                           if (name != null && entry.getValue() instanceof JsonObject rect) {
                              int x = integer(rect, "x");
                              int y = integer(rect, "y");
                              int width = integer(rect, "width");
                              int height = integer(rect, "height");
                              if (x >= 0 && y >= 0 && width > 0 && height > 0 && x + width <= sheetSize[0] && y + height <= sheetSize[1]) {
                                 sprites.put(name, new OreInstall.Sprite(sheetFile, x, y, width, height, true));
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static Map<String, Path> readFontFaces(Path fonts, String css) {
      Map<String, Path> faces = new HashMap<>();
      Matcher rules = FONT_FACE.matcher(css);

      while (rules.find()) {
         String body = rules.group(1);
         Matcher family = FONT_FAMILY.matcher(body);
         Matcher src = FONT_SRC.matcher(body);
         if (family.find() && src.find()) {
            String file = src.group(1);
            if (!file.contains("/") && !file.contains("\\") && !file.contains("..")) {
               Path path = fonts.resolve(file);
               if (Files.isRegularFile(path)) {
                  Matcher weight = FONT_WEIGHT.matcher(body);
                  Matcher style = FONT_STYLE.matcher(body);
                  String familyName = family.group(1).replace("\"", "").replace("'", "");
                  faces.putIfAbsent(
                     faceKey(familyName, weight.find() ? Integer.parseInt(weight.group(1)) : 400, style.find() ? style.group(1) : "normal"), path
                  );
               }
            }
         }
      }

      return faces;
   }

   private static int integer(JsonObject object, String key) {
      JsonElement value = object.get(key);
      return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber() ? value.getAsInt() : -1;
   }

   @Nullable
   static int[] pngSize(Path file) {
      try {
         Object width;
         try (InputStream in = Files.newInputStream(file)) {
            byte[] header = in.readNBytes(24);
            if (header.length >= 24 && (header[0] & 255) == 137 && header[1] == 80 && header[2] == 78 && header[3] == 71) {
               int widthx = (header[16] & 255) << 24 | (header[17] & 255) << 16 | (header[18] & 255) << 8 | header[19] & 255;
               int height = (header[20] & 255) << 24 | (header[21] & 255) << 16 | (header[22] & 255) << 8 | header[23] & 255;
               return widthx > 0 && height > 0 && widthx <= 8192 && height <= 8192 ? new int[]{widthx, height} : null;
            }

            width = null;
         }

         return (int[])width;
      } catch (IOException var8) {
         return null;
      }
   }

   private static String text(Path file) throws IOException {
      if (Files.size(file) > 8388608L) {
         throw new IOException(file + " is too large");
      } else {
         return Files.readString(file, StandardCharsets.UTF_8);
      }
   }

   public List<String> spriteNames() {
      return this.sprites.keySet().stream().sorted().toList();
   }

   public record Sprite(Path file, int x, int y, int width, int height, boolean inAtlas) {
   }
}
