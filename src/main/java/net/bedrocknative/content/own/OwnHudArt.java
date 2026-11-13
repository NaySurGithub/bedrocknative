package net.bedrocknative.content.own;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;
import net.bedrocknative.content.ArgbImage;

final class OwnHudArt {
   static final String UI = "textures/ui/";
   private static final int OUT = PixelArt.rgb(0);
   private static final String[] HEART = new String[]{
      "..##.##..", ".#hR#RR#.", "#hhRRRRR#", "#hRRRRRR#", "#RRRRRRs#", ".#RRRRs#.", "..#RRs#..", "...#s#...", "....#...."
   };
   private static final String[] DRUMSTICK = new String[]{
      "..##.....", ".#lm#....", "#lmmm#...", "#mmmmm#..", ".#mmms#..", "..#mss#..", "...###b##", "......#b#", "......##."
   };

   private OwnHudArt() {
   }

   static void register(Map<String, Supplier<OwnArt.Texture>> into) {
      for (int slot = 0; slot < 9; slot++) {
         int seed = slot;
         into.put("textures/ui/hotbar_" + slot, () -> OwnArt.Texture.plain(hotbarCell(seed)));
      }

      into.put("textures/ui/hotbar_start_cap", () -> new OwnArt.Texture(PixelArt.solid(1, 22, OUT), PixelArt.nineSlice(1, 1, 1, 1, 1, 22)));
      into.put("textures/ui/hotbar_end_cap", () -> new OwnArt.Texture(PixelArt.solid(1, 22, OUT), PixelArt.nineSlice(1, 1, 1, 1, 1, 22)));
      into.put("textures/ui/selected_hotbar_slot", () -> OwnArt.Texture.plain(selectedSlot()));
      heartSet(into, "heart", PixelArt.rgb(14491944), PixelArt.rgb(16748174), PixelArt.rgb(10490906));
      heartSet(into, "poison_heart", PixelArt.rgb(9676063), PixelArt.rgb(13885540), PixelArt.rgb(6714134));
      heartSet(into, "wither_heart", PixelArt.rgb(3092271), PixelArt.rgb(7237230), PixelArt.rgb(1447446));
      heartSet(into, "freeze_heart", PixelArt.rgb(7850226), PixelArt.rgb(14218239), PixelArt.rgb(3968448));
      into.put("textures/ui/absorption_heart", () -> OwnArt.Texture.plain(heart(PixelArt.rgb(15184412), PixelArt.rgb(16773260), PixelArt.rgb(11566350), false)));
      into.put(
         "textures/ui/absorption_heart_half", () -> OwnArt.Texture.plain(heart(PixelArt.rgb(15184412), PixelArt.rgb(16773260), PixelArt.rgb(11566350), true))
      );
      into.put("textures/ui/horse_heart", () -> OwnArt.Texture.plain(heart(PixelArt.rgb(13199403), PixelArt.rgb(15967850), PixelArt.rgb(9191702), false)));
      into.put("textures/ui/horse_heart_half", () -> OwnArt.Texture.plain(heart(PixelArt.rgb(13199403), PixelArt.rgb(15967850), PixelArt.rgb(9191702), true)));
      into.put("textures/ui/heart_background", () -> OwnArt.Texture.plain(container(OUT)));
      into.put("textures/ui/heart_blink", () -> OwnArt.Texture.plain(container(PixelArt.rgb(16777215))));
      into.put("textures/ui/armor_full", () -> OwnArt.Texture.plain(armor(2)));
      into.put("textures/ui/armor_half", () -> OwnArt.Texture.plain(armor(1)));
      into.put("textures/ui/armor_empty", () -> OwnArt.Texture.plain(armor(0)));
      foodSet(into, "hunger_", PixelArt.rgb(12609579), PixelArt.rgb(15176272), PixelArt.rgb(8864538));
      foodSet(into, "hunger_effect_", PixelArt.rgb(7899180), PixelArt.rgb(11123285), PixelArt.rgb(5398554));
      into.put("textures/ui/bubble", () -> OwnArt.Texture.plain(bubble(0)));
      into.put("textures/ui/bubble_pop", () -> OwnArt.Texture.plain(bubble(1)));
      into.put("textures/ui/bubble_empty", () -> OwnArt.Texture.plain(bubble(2)));
      into.put("textures/ui/experiencebarempty", () -> OwnArt.Texture.plain(bar(PixelArt.rgb(921872), new int[]{2700843, 1582108, 2503210}, true)));
      into.put("textures/ui/experiencebarfull", () -> OwnArt.Texture.plain(bar(PixelArt.rgb(921872), new int[]{7776844, 11595388, 6920772}, true)));
      into.put("textures/ui/experiencenub", () -> OwnArt.Texture.plain(PixelArt.solid(1, 5, PixelArt.rgba(2961448, 0))));
      into.put("textures/ui/horse_jump_empty", () -> OwnArt.Texture.plain(bar(PixelArt.rgb(1052948), new int[]{3159626, 1909299, 2764866}, false)));
      into.put("textures/ui/horse_jump_full", () -> OwnArt.Texture.plain(bar(PixelArt.rgb(1052948), new int[]{7120616, 11129855, 5211334}, false)));
      into.put("textures/ui/dash_cooldown", () -> OwnArt.Texture.plain(bar(PixelArt.rgb(1052948), new int[]{6974058, 9342606, 5197647}, false)));
      into.put("textures/ui/empty_progress_bar", () -> OwnArt.Texture.plain(bossBar(new int[]{5723991, 3815994, 5723991})));
      into.put("textures/ui/filled_progress_bar", () -> OwnArt.Texture.plain(bossBar(new int[]{15592941, 12105912, 9211020})));
      into.put("textures/ui/hud_tip_text_background", () -> {
         ArgbImage image = PixelArt.solid(6, 6, OUT);

         for (int[] corner : new int[][]{{0, 0}, {5, 0}, {0, 5}, {5, 5}}) {
            PixelArt.set(image, corner[0], corner[1], 0);
         }

         return OwnArt.Texture.sliced(image, 2);
      });
      into.put("textures/ui/hud_mob_effect_background", () -> OwnArt.Texture.plain(effectFrame(PixelArt.rgb(9342606), PixelArt.rgb(5789784))));
      into.put("textures/ui/hud_mob_ambient_effect_background", () -> OwnArt.Texture.plain(effectFrame(PixelArt.rgb(7316704), PixelArt.rgb(4024990))));
   }

   static ArgbImage hotbarCell(int seed) {
      Random random = new Random(1000L + seed * 31L);
      ArgbImage image = PixelArt.canvas(20, 22);

      for (int y = 0; y < 22; y++) {
         for (int x = 0; x < 20; x++) {
            int edge = Math.min(Math.min(x, 19 - x), Math.min(y, 21 - y));
            int base;
            if (edge == 0) {
               base = 9342606;
            } else if (edge == 1) {
               base = 7303023;
            } else if (edge == 2) {
               base = 3092523;
            } else {
               base = 5001019;
            }

            int grain = random.nextInt(9) - 4;
            PixelArt.set(image, x, y, PixelArt.rgb(shift(base, grain)));
         }
      }

      return image;
   }

   static ArgbImage selectedSlot() {
      ArgbImage image = PixelArt.canvas(24, 24);
      PixelArt.outline(image, 0, 0, 24, 24, PixelArt.rgb(2763815));
      PixelArt.outline(image, 1, 1, 22, 22, PixelArt.rgb(15332066));
      PixelArt.outline(image, 2, 2, 20, 20, PixelArt.rgb(12571573));
      PixelArt.outline(image, 3, 3, 18, 18, PixelArt.rgb(6187607));
      PixelArt.set(image, 1, 1, PixelArt.rgb(16777215));
      PixelArt.set(image, 22, 22, PixelArt.rgb(10334353));
      return image;
   }

   static ArgbImage heart(int face, int light, int shade, boolean half) {
      ArgbImage image = PixelArt.grid(Map.of('#', OUT, 'R', face, 'h', light, 's', shade), HEART);
      if (half) {
         PixelArt.fill(image, 5, 0, 4, 9, 0);

         for (int y = 1; y < 8; y++) {
            if (image.pixel(4, y) >>> 24 != 0) {
               PixelArt.set(image, 4, y, image.pixel(4, y) == OUT ? OUT : shade);
            }
         }
      }

      return image;
   }

   static ArgbImage container(int outline) {
      return PixelArt.grid(
         Map.of('#', outline, 'd', PixelArt.rgb(2829099)),
         "..##.##..",
         ".#dd#dd#.",
         "#ddddddd#",
         "#ddddddd#",
         "#ddddddd#",
         ".#ddddd#.",
         "..#ddd#..",
         "...#d#...",
         "....#...."
      );
   }

   private static void heartSet(Map<String, Supplier<OwnArt.Texture>> into, String name, int face, int light, int shade) {
      into.put("textures/ui/" + name, () -> OwnArt.Texture.plain(heart(face, light, shade, false)));
      into.put("textures/ui/" + name + "_half", () -> OwnArt.Texture.plain(heart(face, light, shade, true)));
      into.put(
         "textures/ui/" + name + "_flash", () -> OwnArt.Texture.plain(heart(PixelArt.rgb(15921906), PixelArt.rgb(16777215), PixelArt.rgb(12895428), false))
      );
      into.put(
         "textures/ui/" + name + "_flash_half", () -> OwnArt.Texture.plain(heart(PixelArt.rgb(15921906), PixelArt.rgb(16777215), PixelArt.rgb(12895428), true))
      );
   }

   static ArgbImage armor(int filled) {
      ArgbImage image = PixelArt.grid(
         Map.of('#', OUT, 'w', PixelArt.rgb(14474460), 'g', PixelArt.rgb(10724259), 'l', PixelArt.rgb(16777215)),
         ".##...##.",
         "#lw#.#ww#",
         "#wwg#wwg#",
         ".#wwwwg#.",
         ".#lwwwg#.",
         ".#wwwwg#.",
         ".#wwwwg#.",
         ".#wggwg#.",
         "..#####.."
      );
      if (filled == 2) {
         return image;
      } else {
         int dark = PixelArt.rgb(3947580);

         for (int y = 0; y < 9; y++) {
            for (int x = filled == 1 ? 5 : 0; x < 9; x++) {
               int argb = image.pixel(x, y);
               if (argb >>> 24 != 0 && argb != OUT) {
                  PixelArt.set(image, x, y, dark);
               }
            }
         }

         return image;
      }
   }

   static ArgbImage drumstick(int meat, int light, int shade, int bone, boolean half, boolean empty) {
      int dark = PixelArt.rgb(2829099);
      ArgbImage image = PixelArt.grid(
         Map.of('#', OUT, 'm', empty ? dark : meat, 'l', empty ? dark : light, 's', empty ? dark : shade, 'b', empty ? dark : bone), DRUMSTICK
      );
      if (half) {
         PixelArt.fill(image, 0, 0, 3, 9, 0);
      }

      return image;
   }

   private static void foodSet(Map<String, Supplier<OwnArt.Texture>> into, String prefix, int meat, int light, int shade) {
      int bone = PixelArt.rgb(15854038);
      into.put("textures/ui/" + prefix + "full", () -> OwnArt.Texture.plain(drumstick(meat, light, shade, bone, false, false)));
      into.put("textures/ui/" + prefix + "half", () -> OwnArt.Texture.plain(drumstick(meat, light, shade, bone, true, false)));
      into.put("textures/ui/" + prefix + "background", () -> OwnArt.Texture.plain(drumstick(meat, light, shade, bone, false, true)));
   }

   static ArgbImage bubble(int state) {
      int blue = PixelArt.rgb(4165590);
      int light = PixelArt.rgb(13495295);
      int dark = PixelArt.rgb(2051968);

      return switch (state) {
         case 0 -> PixelArt.grid(
            Map.of('#', dark, 'b', blue, 'l', light),
            ".........",
            "..#####..",
            ".#bbblb#.",
            ".#bbbbl#.",
            ".#bbbbb#.",
            ".#bbbbb#.",
            ".#bbbbb#.",
            "..#####..",
            "........."
         );
         case 1 -> PixelArt.grid(
            Map.of('#', dark, 'l', light), "....l....", ".#..l..#.", "..#...#..", ".........", "ll.....ll", ".........", "..#...#..", ".#..l..#.", "....l...."
         );
         default -> PixelArt.grid(
            Map.of('#', OUT, 'd', PixelArt.rgb(2829099), 'b', PixelArt.rgb(4023168)),
            ".........",
            ".........",
            "...###...",
            "..#bbd#..",
            "..#bdd#..",
            "..#ddd#..",
            "...###...",
            ".........",
            "........."
         );
      };
   }

   static ArgbImage bar(int outline, int[] rows, boolean notched) {
      ArgbImage image = PixelArt.canvas(182, 5);
      PixelArt.fill(image, 1, 0, 180, 1, outline);
      PixelArt.fill(image, 1, 4, 180, 1, outline);
      PixelArt.fill(image, 0, 1, 1, 3, outline);
      PixelArt.fill(image, 181, 1, 1, 3, outline);

      for (int row = 0; row < 3; row++) {
         PixelArt.fill(image, 1, 1 + row, 180, 1, PixelArt.rgb(rows[row]));
      }

      if (notched) {
         for (int x = 18; x < 181; x += 18) {
            PixelArt.fill(image, x, 1, 1, 3, PixelArt.rgb(shift(rows[1], -14)));
         }
      }

      return image;
   }

   static ArgbImage bossBar(int[] rows) {
      ArgbImage image = PixelArt.canvas(182, 5);
      int outline = PixelArt.rgb(2500134);
      PixelArt.fill(image, 1, 0, 180, 1, outline);
      PixelArt.fill(image, 1, 4, 180, 1, outline);
      PixelArt.fill(image, 0, 1, 1, 3, outline);
      PixelArt.fill(image, 181, 1, 1, 3, outline);

      for (int row = 0; row < 3; row++) {
         PixelArt.fill(image, 1, 1 + row, 180, 1, PixelArt.rgb(rows[row]));
      }

      return image;
   }

   static ArgbImage effectFrame(int light, int dark) {
      ArgbImage image = PixelArt.solid(24, 24, PixelArt.rgba(1973790, 230));
      PixelArt.outline(image, 0, 0, 24, 24, dark);
      PixelArt.fill(image, 1, 0, 22, 1, light);
      PixelArt.fill(image, 0, 1, 1, 22, light);

      for (int[] corner : new int[][]{{0, 0}, {23, 0}, {0, 23}, {23, 23}}) {
         PixelArt.set(image, corner[0], corner[1], 0);
      }

      return image;
   }

   static int shift(int rgb, int delta) {
      int r = Math.clamp((long)((rgb >> 16 & 0xFF) + delta), 0, 255);
      int g = Math.clamp((long)((rgb >> 8 & 0xFF) + delta), 0, 255);
      int b = Math.clamp((long)((rgb & 0xFF) + delta), 0, 255);
      return r << 16 | g << 8 | b;
   }

   static Map<String, Boolean> names() {
      Map<String, Supplier<OwnArt.Texture>> all = new HashMap<>();
      register(all);
      Map<String, Boolean> names = new HashMap<>();
      all.keySet().forEach(name -> names.put(name, true));
      return names;
   }
}
