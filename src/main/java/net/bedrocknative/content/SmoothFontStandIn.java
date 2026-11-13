package net.bedrocknative.content;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.font.LineMetrics;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.bedrocknative.BedrockNative;
import org.jspecify.annotations.Nullable;

public final class SmoothFontStandIn implements PackSource {
   public static final SmoothFontStandIn INSTANCE = new SmoothFontStandIn();
   public static final String PROPERTY = "bedrocknative.smoothFont";
   static final String METADATA = "font/font_metadata.json";
   static final String FILE = "font/bedrocknative_smooth_stand_in";
   static final float LINE_HEIGHT = 1.25F;
   private static final int[] PROBES = new int[]{97, 109, 87, 49, 233};
   private static final double[] RATIOS = new double[]{0.41189, 0.68649, 0.68282, 0.41997, 0.4141};
   private static final double TOLERANCE = 5.0E-4;
   private static final int MAX_BYTES = 1048576;
   private static final List<String> ALIASES = List.of("SmoothFontLatin", "SmoothFontWithHangulFallback", "SmoothWithEmoticons", "SmoothSmall");
   private volatile @Nullable Optional<byte[]> font;

   private SmoothFontStandIn() {
   }

   public boolean available() {
      return this.fontBytes().isPresent();
   }

   @Override
   public Optional<byte[]> read(String path) {
      String normalized = PackStack.normalize(path);
      if (normalized == null) {
         return Optional.empty();
      } else {
         normalized = normalized.toLowerCase(Locale.ROOT);
         if (normalized.equals("font/font_metadata.json")) {
            return this.fontBytes().map(bytes -> metadata());
         } else {
            return normalized.equals("font/bedrocknative_smooth_stand_in.ttf") ? this.fontBytes().map(rec$ -> (byte[])rec$.clone()) : Optional.empty();
         }
      }
   }

   private static byte[] metadata() {
      StringBuilder json = new StringBuilder("{\"fonts\":[{\"font_format\":\"ttf\",\"font_name\":\"OldSmoothFont\",\"font_file\":\"")
         .append("font/bedrocknative_smooth_stand_in")
         .append("\",\"bedrocknative_line_height\":")
         .append(1.25F)
         .append("}],\"font_aliases\":[");

      for (int i = 0; i < ALIASES.size(); i++) {
         json.append(i == 0 ? "" : ",").append("{\"alias\":\"").append(ALIASES.get(i)).append("\",\"fonts\":[{\"font_reference\":\"OldSmoothFont\"}]}");
      }

      return json.append("]}").toString().getBytes(StandardCharsets.UTF_8);
   }

   public Optional<byte[]> fontBytes() {
      Optional<byte[]> known = this.font;
      if (known == null) {
         synchronized (this) {
            known = this.font;
            if (known == null) {
               known = find();
               this.font = known;
            }
         }
      }

      return known;
   }

   private static Optional<byte[]> find() {
      for (Path candidate : candidates()) {
         try {
            if (Files.isRegularFile(candidate) && Files.size(candidate) <= 1048576L) {
               byte[] bytes = Files.readAllBytes(candidate);
               String refused = check(bytes);
               if (refused == null) {
                  BedrockNative.LOGGER.info("Smooth font: Bedrock's smooth font is not in the base; Noto Sans Regular from {} stands in", candidate);
                  return Optional.of(bytes);
               }

               BedrockNative.LOGGER.info("Smooth font: {} not used ({})", candidate, refused);
            }
         } catch (Exception e) {
            BedrockNative.LOGGER.info("Smooth font: {} not readable ({})", candidate, e.toString());
         }
      }

      BedrockNative.LOGGER.info("Smooth font: no Noto Sans Regular on this PC; the bitmap font draws smooth text");
      return Optional.empty();
   }

   static List<Path> candidates() {
      List<Path> paths = new ArrayList<>();
      String configured = System.getProperty("bedrocknative.smoothFont");
      if (configured != null && !configured.isBlank()) {
         paths.add(Path.of(configured.trim()));
         return paths;
      }

      String windows = System.getenv("WINDIR");
      paths.add(Path.of(windows != null ? windows : "C:/Windows", "Fonts", "NotoSans-Regular.ttf"));
      String local = System.getenv("LOCALAPPDATA");
      if (local != null) {
         paths.add(Path.of(local, "Microsoft", "Windows", "Fonts", "NotoSans-Regular.ttf"));
      }

      String home = System.getProperty("user.home", "");
      paths.add(Path.of("/usr/share/fonts/truetype/noto/NotoSans-Regular.ttf"));
      paths.add(Path.of("/usr/share/fonts/noto/NotoSans-Regular.ttf"));
      paths.add(Path.of("/usr/share/fonts/google-noto/NotoSans-Regular.ttf"));
      paths.add(Path.of("/Library/Fonts/NotoSans-Regular.ttf"));
      if (!home.isEmpty()) {
         paths.add(Path.of(home, ".local", "share", "fonts", "NotoSans-Regular.ttf"));
         paths.add(Path.of(home, "Library", "Fonts", "NotoSans-Regular.ttf"));
      }

      return paths;
   }

   static @Nullable String check(byte[] bytes) {
      if (bytes.length >= 12 && bytes.length <= 1048576 && bytes[0] == 0 && bytes[1] == 1 && bytes[2] == 0 && bytes[3] == 0) {
         try {
            Font reference = Font.createFont(0, new ByteArrayInputStream(bytes)).deriveFont(1000.0F);
            FontRenderContext context = new FontRenderContext(null, true, true);
            LineMetrics line = reference.getLineMetrics("Hg", context);
            float lineHeight = line.getAscent() + line.getDescent();
            if (lineHeight <= 0.0F) {
               return "no line height";
            }

            for (int i = 0; i < PROBES.length; i++) {
               if (!reference.canDisplay(PROBES[i])) {
                  return "no " + Character.toString(PROBES[i]);
               }

               double ratio = reference.createGlyphVector(context, Character.toString(PROBES[i])).getGlyphMetrics(0).getAdvance() / lineHeight;
               if (Math.abs(ratio - RATIOS[i]) > 5.0E-4) {
                  return String.format(Locale.ROOT, "%s advance %.5f of the line, Bedrock's smooth font %.5f", Character.toString(PROBES[i]), ratio, RATIOS[i]);
               }
            }

            return null;
         } catch (Exception e) {
            return "unreadable: " + e.getMessage();
         }
      } else {
         return "not a TrueType file";
      }
   }

   void forget() {
      this.font = null;
   }

   @Override
   public String toString() {
      return "smooth-font-stand-in";
   }
}
