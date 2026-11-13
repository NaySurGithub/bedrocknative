package net.bedrocknative.oreui;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;
import net.bedrocknative.content.ArgbImage;
import net.bedrocknative.content.own.PixelArt;

final class OwnOreArt {
   static final int OUTLINE = PixelArt.rgb(1973791);
   static final int CARD = PixelArt.rgb(3224115);
   static final int FACE = PixelArt.rgb(4737354);
   static final int FACE_HOVER = PixelArt.rgb(5789786);
   static final int DARK = PixelArt.rgb(2368549);
   static final int LIGHT_EDGE = PixelArt.rgb(7237232);
   static final int MID_EDGE = PixelArt.rgb(5987165);
   static final int WHITE = PixelArt.rgb(16777215);
   static final int BLACK = PixelArt.rgb(0);

   private OwnOreArt() {
   }

   static Map<String, Supplier<ArgbImage>> all() {
      Map<String, Supplier<ArgbImage>> art = new LinkedHashMap<>();
      art.put("tabBar_neutral_default", () -> tab(FACE, LIGHT_EDGE, CARD, false));
      art.put("tabBar_neutral_hovered", () -> tab(FACE_HOVER, PixelArt.rgb(8289920), CARD, false));
      art.put("tabBar_neutral_pressed", () -> tab(CARD, OUTLINE, DARK, true));
      art.put("tabBar_neutral_default_focused", () -> focused(tab(FACE, LIGHT_EDGE, CARD, false)));
      art.put("tabBar_neutral_pressed_focused", () -> focused(tab(CARD, OUTLINE, DARK, true)));
      art.put("listItem_disabled_default", () -> framed(3, OUTLINE, CARD));
      art.put("listItem_action_default", () -> bevel3(FACE, MID_EDGE, PixelArt.rgb(3355701)));
      art.put("listItem_action_hovered", () -> bevel3(FACE_HOVER, PixelArt.rgb(6974060), PixelArt.rgb(4144960)));
      art.put("listItem_action_pressed", () -> bevel3(CARD, PixelArt.rgb(2303013), PixelArt.rgb(4671561)));
      art.put("listItem_action_focused", () -> focusedRing(bevel3(FACE, MID_EDGE, PixelArt.rgb(3355701))));
      art.put("listItem_action_pressed_focused", () -> focusedRing(bevel3(CARD, PixelArt.rgb(2303013), PixelArt.rgb(4671561))));
      art.put("listItem_disabled_focused", () -> focusedRing(framed(3, OUTLINE, CARD)));
      art.put("baseTextField_neutral80", OwnOreArt::textField);
      art.put("baseTextField_disabled", () -> {
         ArgbImage image = textField();
         return PixelArt.tint(image, 2763307);
      });
      art.put("arrowBack", () -> chevron(BLACK, false));
      art.put("arrowForward", () -> chevron(BLACK, true));
      art.put("crossWhite", () -> cross(14, WHITE));
      art.put("UI_Menu_WorldsTab", OwnOreArt::worldsTab);
      art.put("UI_Menu_ServerTab", OwnOreArt::serversTab);
      art.put("UI_Menu_RealmsTab", OwnOreArt::realmsTab);
      art.put("BracketOpen", () -> keycap(false));
      art.put("BracketClose", () -> keycap(true));
      art.put("pingGreen", () -> ping(4, PixelArt.rgb(7196312)));
      art.put("pingYellow", () -> ping(3, PixelArt.rgb(15515904)));
      art.put("pingRed", () -> ping(1, PixelArt.rgb(16736872)));
      art.put("pingAnimation", OwnOreArt::pingAnimation);
      art.put("player-online-icon", OwnOreArt::playerOnline);
      art.put("plus@1x.icon", OwnOreArt::plus);
      art.put("grid@0.5x.icon", () -> PixelArt.grid(Map.of('#', BLACK), "##.##.", "##.##.", "......", "##.##.", "##.##.", "......"));
      art.put("list@0.5x.icon", () -> PixelArt.grid(Map.of('#', BLACK), "#.######", "........", "#.######", "........", "#.######", "........", "#.######"));
      art.put("edit@0.5x.icon", OwnOreArt::pencil);
      art.put("friends@0.5x.icon", OwnOreArt::friends);
      art.put("delete", OwnOreArt::bin);
      art.put(
         "save",
         () -> PixelArt.grid(
            Map.of('#', WHITE),
            "..........#.",
            ".........##.",
            "........##..",
            ".......##...",
            "#.....##....",
            "##...##.....",
            ".##.##......",
            "..###.......",
            "...#........"
         )
      );
      art.put("import-world-icon", OwnOreArt::importWorld);
      art.put("world-preview-default", OwnOreArt::worldPreview);
      art.put("no-worlds-yet-art", () -> scene(224, 96, 11L, false));
      art.put("connection_error", () -> scene(256, 96, 23L, true));
      art.put("ClientMismatchImage", () -> scene(128, 48, 37L, false));
      return art;
   }

   static ArgbImage tab(int face, int edge, int strip, boolean pressed) {
      int height = pressed ? 5 : 7;
      ArgbImage image = PixelArt.solid(5, height, OUTLINE);
      PixelArt.fill(image, 1, 1, 3, height - 2, face);
      PixelArt.fill(image, 1, 1, 3, 1, edge);
      if (!pressed) {
         PixelArt.fill(image, 1, height - 3, 3, 2, strip);
      }

      return image;
   }

   static ArgbImage focused(ArgbImage inner) {
      ArgbImage image = PixelArt.solid(inner.width() + 2, inner.height() + 2, WHITE);
      PixelArt.draw(image, inner, 1, 1);
      return image;
   }

   static ArgbImage focusedRing(ArgbImage inner) {
      ArgbImage image = PixelArt.canvas(7, 7);
      PixelArt.outline(image, 0, 0, 7, 7, WHITE);

      for (int y = 0; y < 3; y++) {
         for (int x = 0; x < 3; x++) {
            int argb = inner.pixel(x, y);
            int sx = x == 0 ? 2 : (x == 1 ? 3 : 4);
            int sy = y == 0 ? 2 : (y == 1 ? 3 : 4);
            PixelArt.set(image, sx, sy, argb);
         }
      }

      return image;
   }

   static ArgbImage framed(int size, int frame, int face) {
      ArgbImage image = PixelArt.solid(size, size, face);
      PixelArt.outline(image, 0, 0, size, size, frame);
      return image;
   }

   static ArgbImage bevel3(int face, int light, int dark) {
      ArgbImage image = PixelArt.solid(3, 3, face);
      PixelArt.bevel(image, 0, 0, 3, 3, face, light, dark, face);
      return image;
   }

   static ArgbImage textField() {
      ArgbImage image = PixelArt.solid(6, 7, OUTLINE);
      PixelArt.fill(image, 1, 1, 4, 5, CARD);
      PixelArt.fill(image, 1, 1, 4, 2, DARK);
      return image;
   }

   static ArgbImage chevron(int colour, boolean right) {
      ArgbImage image = PixelArt.canvas(8, 16);

      for (int y = 0; y < 16; y++) {
         int distance = Math.abs(y * 2 + 1 - 16) / 2;
         int x = 1 + distance * 5 / 7;

         for (int dx = 0; dx < 2; dx++) {
            PixelArt.set(image, right ? 7 - x - dx : x + dx, y, colour);
         }
      }

      return image;
   }

   static ArgbImage cross(int size, int colour) {
      ArgbImage image = PixelArt.canvas(size, size);

      for (int i = 1; i < size - 1; i++) {
         for (int t = 0; t < 2; t++) {
            PixelArt.set(image, i + t - 1, i, colour);
            PixelArt.set(image, size - 1 - i - t + 1, i, colour);
         }
      }

      return image;
   }

   static ArgbImage iconFrame(ArgbImage scene) {
      ArgbImage image = PixelArt.solid(24, 24, OUTLINE);
      PixelArt.draw(image, scene, 2, 2);
      return image;
   }

   static ArgbImage worldsTab() {
      ArgbImage scene = PixelArt.solid(20, 20, PixelArt.rgb(12176886));
      PixelArt.fill(scene, 0, 0, 20, 6, PixelArt.rgb(10862322));
      PixelArt.fill(scene, 14, 3, 3, 3, PixelArt.rgb(16774056));
      Random random = new Random(3L);

      for (int x = 0; x < 20; x++) {
         int top = 11 - (int)Math.round(3.0 * Math.sin(x / 3.2));

         for (int y = top; y < 20; y++) {
            int colour = y < top + 2
               ? (random.nextInt(4) == 0 ? PixelArt.rgb(5147193) : PixelArt.rgb(6136643))
               : (random.nextInt(5) == 0 ? PixelArt.rgb(8017206) : PixelArt.rgb(9200190));
            PixelArt.set(scene, x, y, colour);
         }
      }

      return iconFrame(scene);
   }

   static ArgbImage serversTab() {
      ArgbImage scene = PixelArt.solid(20, 20, PixelArt.rgb(3034508));
      int sea = PixelArt.rgb(5942224);
      int land = PixelArt.rgb(8240464);
      int landDark = PixelArt.rgb(6200379);

      for (int y = 0; y < 20; y++) {
         for (int x = 0; x < 20; x++) {
            double dx = x - 9.5;
            double dy = y - 9.5;
            if (!(dx * dx + dy * dy > 90.0)) {
               boolean ground = Math.sin(x * 0.7) + Math.cos(y * 0.55 + x * 0.2) > 0.6;
               PixelArt.set(scene, x, y, ground ? (y > 12 ? landDark : land) : (y < 6 ? PixelArt.rgb(10736874) : sea));
            }
         }
      }

      return iconFrame(scene);
   }

   static ArgbImage realmsTab() {
      ArgbImage scene = PixelArt.solid(20, 20, PixelArt.rgb(1512223));

      for (int y = 0; y < 20; y++) {
         for (int x = 0; x < 20; x++) {
            double dx = (x - 9.5) / 8.0;
            double dy = (y - 9.5) / 9.0;
            double d = dx * dx + dy * dy;
            if (d < 1.0) {
               PixelArt.set(scene, x, y, d < 0.25 ? PixelArt.rgb(11561970) : (d < 0.6 ? PixelArt.rgb(9059549) : PixelArt.rgb(5974684)));
            }
         }
      }

      return iconFrame(scene);
   }

   static ArgbImage keycap(boolean close) {
      ArgbImage image = PixelArt.canvas(36, 36);
      PixelArt.fill(image, 2, 0, 32, 36, PixelArt.rgb(5921372));
      PixelArt.fill(image, 0, 2, 36, 32, PixelArt.rgb(5921372));
      PixelArt.fill(image, 2, 2, 32, 30, PixelArt.rgb(16250871));
      PixelArt.fill(image, 2, 28, 32, 4, PixelArt.rgb(11776947));
      PixelArt.fill(image, 4, 4, 28, 22, PixelArt.rgb(15132390));
      int x = close ? 19 : 15;
      PixelArt.fill(image, x, 9, 2, 14, BLACK);
      PixelArt.fill(image, close ? 15 : 15, 9, 6, 2, BLACK);
      PixelArt.fill(image, close ? 15 : 15, 21, 6, 2, BLACK);
      return image;
   }

   static ArgbImage ping(int lit, int colour) {
      ArgbImage image = PixelArt.canvas(20, 20);

      for (int bar = 0; bar < 4; bar++) {
         int height = 4 + bar * 4;
         PixelArt.fill(image, 1 + bar * 5, 18 - height, 3, height, bar < lit ? colour : PixelArt.rgb(13685204));
      }

      return image;
   }

   static ArgbImage pingAnimation() {
      ArgbImage image = PixelArt.canvas(112, 20);

      for (int frame = 0; frame < 6; frame++) {
         int x0 = Math.round(frame * 112.0F / 6.0F);

         for (int bar = 0; bar < 4; bar++) {
            if (bar <= frame % 5) {
               int height = 4 + bar * 4;
               PixelArt.fill(image, x0 + 1 + bar * 4, 18 - height, 3, height, PixelArt.rgb(13685204));
            }
         }
      }

      return image;
   }

   static ArgbImage playerOnline() {
      return PixelArt.grid(
         Map.of('#', WHITE, 'g', PixelArt.rgb(7127881)),
         "....###..ggg",
         "...#...#.g.g",
         "...#...#.ggg",
         "...#...#....",
         "....###.....",
         "............",
         "..#######...",
         ".#.......#..",
         ".#.......#..",
         ".#.......#..",
         "............",
         "............"
      );
   }

   static ArgbImage plus() {
      ArgbImage image = PixelArt.canvas(24, 24);
      PixelArt.fill(image, 10, 2, 4, 20, BLACK);
      PixelArt.fill(image, 2, 10, 20, 4, BLACK);
      return image;
   }

   static ArgbImage pencil() {
      return PixelArt.grid(
         Map.of('#', BLACK),
         "........##..",
         ".......####.",
         "......#..##.",
         ".....#..#...",
         "....#..#....",
         "...#..#.....",
         "..#..#......",
         ".#..#.......",
         ".##.#.......",
         ".###........",
         "............",
         "............"
      );
   }

   static ArgbImage friends() {
      return PixelArt.grid(
         Map.of('#', BLACK),
         "....###.....",
         "...#...#....",
         "...#...#....",
         ".###...#....",
         "#..#####....",
         "#..........",
         "#.#######...",
         ".#.......#..",
         ".#.......#..",
         ".#.......#..",
         "............",
         "............"
      );
   }

   static ArgbImage bin() {
      ArgbImage image = PixelArt.canvas(24, 24);
      PixelArt.fill(image, 9, 2, 6, 2, WHITE);
      PixelArt.fill(image, 4, 4, 16, 2, WHITE);
      PixelArt.fill(image, 6, 7, 12, 15, WHITE);

      for (int x = 8; x < 17; x += 3) {
         PixelArt.fill(image, x, 9, 2, 11, PixelArt.rgba(16777215, 0));
      }

      return image;
   }

   static ArgbImage importWorld() {
      ArgbImage image = PixelArt.canvas(24, 24);
      PixelArt.outline(image, 3, 9, 18, 13, OUTLINE);
      PixelArt.outline(image, 4, 10, 16, 11, OUTLINE);
      PixelArt.fill(image, 11, 1, 2, 12, OUTLINE);

      for (int i = 0; i < 4; i++) {
         PixelArt.fill(image, 8 + i, 9 - i, 2, 1, OUTLINE);
         PixelArt.fill(image, 14 - i, 9 - i, 2, 1, OUTLINE);
      }

      return image;
   }

   static ArgbImage worldPreview() {
      ArgbImage image = PixelArt.canvas(160, 90);

      for (int y = 0; y < 90; y++) {
         int sky = blendRgb(8827391, 13229055, y / 55.0F);
         PixelArt.fill(image, 0, y, 160, 1, PixelArt.rgb(sky));
      }

      Random random = new Random(7L);

      for (int x = 0; x < 160; x++) {
         int top = 56 + (int)Math.round(4.0 * Math.sin(x / 13.0) + 2.0 * Math.sin(x / 5.0));

         for (int y = top; y < 90; y++) {
            int colour = y < top + 3 ? (random.nextInt(6) == 0 ? 5147193 : 6268228) : (random.nextInt(6) == 0 ? 7622959 : 9068604);
            PixelArt.set(image, x, y, PixelArt.rgb(colour));
         }
      }

      PixelArt.fill(image, 118, 14, 12, 12, PixelArt.rgb(16774832));
      return image;
   }

   static ArgbImage scene(int width, int height, long seed, boolean broken) {
      ArgbImage image = PixelArt.canvas(width, height);
      Random random = new Random(seed);
      int top = height * 5 / 8;
      int texel = Math.max(2, width / 64);

      for (int x = texel * 2; x < width - texel * 2; x += texel) {
         if (!broken || Math.abs(x + texel / 2 - width / 2) >= texel * 3) {
            int depth = height - top - texel - random.nextInt(Math.max(1, (height - top) / 2 / texel)) * texel;

            for (int y = top; y < top + depth; y += texel) {
               int colour;
               if (y == top) {
                  colour = random.nextInt(5) == 0 ? 5017393 : 6271037;
               } else if (y == top + texel) {
                  colour = random.nextInt(3) == 0 ? 6271037 : 9333316;
               } else {
                  colour = random.nextInt(4) == 0 ? 6045479 : (random.nextInt(3) == 0 ? 8017466 : 7031344);
               }

               PixelArt.fill(image, x, y, texel, texel, PixelArt.rgb(colour));
            }
         }
      }

      for (int i = 0; i < width / 24; i++) {
         int x = texel * 2 + random.nextInt(Math.max(1, width - texel * 4)) / texel * texel;
         if (!broken || Math.abs(x - width / 2) >= texel * 4) {
            PixelArt.fill(image, x, top - texel, texel, texel, PixelArt.rgb(7127881));
         }
      }

      return image;
   }

   private static int blendRgb(int a, int b, float t) {
      float k = Math.max(0.0F, Math.min(1.0F, t));
      int r = Math.round((a >> 16 & 0xFF) * (1.0F - k) + (b >> 16 & 0xFF) * k);
      int g = Math.round((a >> 8 & 0xFF) * (1.0F - k) + (b >> 8 & 0xFF) * k);
      int bl = Math.round((a & 0xFF) * (1.0F - k) + (b & 0xFF) * k);
      return r << 16 | g << 8 | bl;
   }
}
