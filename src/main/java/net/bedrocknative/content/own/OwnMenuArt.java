package net.bedrocknative.content.own;

import java.util.Map;
import java.util.function.Supplier;
import net.bedrocknative.content.ArgbImage;
import org.jspecify.annotations.Nullable;

final class OwnMenuArt {
   static final String UI = "textures/ui/";
   static final int LIGHT_FACE = PixelArt.rgb(13027014);
   static final int LIGHT_TOP = PixelArt.rgb(16053492);
   static final int LIGHT_SHADE = PixelArt.rgb(6776423);
   static final int LIGHT_CORNER = PixelArt.rgb(12566207);
   static final int DARK_FACE = PixelArt.rgb(3881787);
   static final int DARK_TOP = PixelArt.rgb(5197647);
   static final int DARK_SHADE = PixelArt.rgb(2236962);
   static final int DARK_CORNER = PixelArt.rgb(3684408);
   static final int GREEN_TOP = PixelArt.rgb(1951757);
   static final int GREEN_FACE = PixelArt.rgb(2392584);
   static final int GREEN_SHADE = PixelArt.rgb(151298);
   static final int GREEN_DARK_TOP = PixelArt.rgb(5612119);
   static final int GREEN_DARK_FACE = PixelArt.rgb(151045);
   static final int GREEN_DARK_SHADE = PixelArt.rgb(154117);
   static final int PURPLE_TOP = PixelArt.rgb(10643184);
   static final int PURPLE_FACE = PixelArt.rgb(7620579);
   static final int PURPLE_SHADE = PixelArt.rgb(4923050);
   static final int OUTLINE = PixelArt.rgb(1973791);
   static final int TEXT_GREY = PixelArt.rgb(5000268);

   private OwnMenuArt() {
   }

   static void register(Map<String, Supplier<OwnArt.@Nullable Texture>> into) {
      into.put("textures/ui/White", () -> OwnArt.Texture.sliced(PixelArt.solid(2, 2, PixelArt.rgb(16777215)), 1));
      into.put("textures/ui/Black", () -> OwnArt.Texture.sliced(PixelArt.solid(2, 2, PixelArt.rgb(0)), 1));
      into.put("textures/ui/white_background", () -> OwnArt.Texture.plain(PixelArt.solid(1, 1, PixelArt.rgb(16777215))));
      into.put("textures/ui/black_border", () -> frame(3, PixelArt.rgb(0)));
      into.put("textures/ui/focus_border_white", () -> frame(3, PixelArt.rgb(16777215)));
      into.put("textures/ui/bn_outline", () -> frame(3, OUTLINE));
      into.put("textures/ui/screen_background", () -> new OwnArt.Texture(PixelArt.solid(1, 1, PixelArt.rgb(0)), PixelArt.nineSlice(0, 1, 1)));
      into.put("textures/ui/menubackground", () -> OwnArt.Texture.sliced(PixelArt.solid(2, 2, PixelArt.rgba(0, 153)), 1));
      into.put("textures/ui/button_borderless_light", () -> bevel(LIGHT_FACE, LIGHT_TOP, LIGHT_SHADE, LIGHT_CORNER));
      into.put("textures/ui/button_borderless_lighthover", () -> bevel(GREEN_FACE, GREEN_TOP, GREEN_SHADE, GREEN_FACE));
      into.put("textures/ui/button_borderless_lightpressed", () -> bevel(GREEN_FACE, GREEN_SHADE, GREEN_TOP, GREEN_FACE));
      into.put(
         "textures/ui/button_borderless_lightpressednohover",
         () -> bevel(PixelArt.rgb(9145227), PixelArt.rgb(5986907), PixelArt.rgb(14606046), PixelArt.rgb(11316140))
      );
      into.put("textures/ui/button_borderless_dark", () -> bevel(DARK_FACE, DARK_TOP, DARK_SHADE, DARK_CORNER));
      into.put("textures/ui/button_borderless_darkhover", () -> bevel(GREEN_DARK_FACE, GREEN_DARK_TOP, GREEN_DARK_SHADE, PixelArt.rgb(29445)));
      into.put("textures/ui/button_borderless_darkpressed", () -> bevel(PixelArt.rgb(29445), GREEN_DARK_SHADE, GREEN_DARK_TOP, PixelArt.rgb(29445)));
      into.put("textures/ui/button_borderless_darkpressednohover", () -> bevel(PixelArt.rgb(2763306), DARK_SHADE, DARK_TOP, DARK_FACE));
      into.put("textures/ui/disabledButtonNoBorder", () -> OwnArt.Texture.sliced(PixelArt.solid(2, 2, PixelArt.rgb(7763574)), 1));
      into.put("textures/ui/disabledButton", () -> {
         ArgbImage image = PixelArt.solid(3, 3, PixelArt.rgb(7763574));
         PixelArt.outline(image, 0, 0, 3, 3, PixelArt.rgb(1315860));
         return OwnArt.Texture.sliced(image, 1);
      });
      into.put("textures/ui/realms_button_borderless_default", () -> bevel(PURPLE_FACE, PURPLE_TOP, PURPLE_SHADE, PURPLE_FACE));
      into.put(
         "textures/ui/realms_button_borderless_hover", () -> bevel(PixelArt.rgb(6172101), PixelArt.rgb(2690922), PixelArt.rgb(11299310), PixelArt.rgb(6172101))
      );
      into.put(
         "textures/ui/realms_button_borderless_pressed",
         () -> bevel(PixelArt.rgb(4923050), PixelArt.rgb(2690922), PixelArt.rgb(9198312), PixelArt.rgb(4923050))
      );
      into.put("textures/ui/bn_divider", () -> OwnArt.Texture.plain(PixelArt.solid(1, 1, PixelArt.rgba(0, 102))));
      into.put("textures/ui/socialbuttonicon/social-default-icon", () -> OwnArt.Texture.plain(OwnMenuArt.StartIcons.social(TEXT_GREY)));
      into.put("textures/ui/socialbuttonicon/social-hover-icon", () -> OwnArt.Texture.plain(OwnMenuArt.StartIcons.social(PixelArt.rgb(16777215))));
      into.put("textures/ui/bn_mail", () -> OwnArt.Texture.plain(OwnMenuArt.StartIcons.mail()));
      into.put("textures/ui/icon_alex", () -> OwnArt.Texture.plain(OwnMenuArt.StartIcons.head()));
      into.put("textures/ui/bn_question", () -> OwnArt.Texture.plain(OwnMenuArt.StartIcons.question()));
      into.put("textures/ui/bn_camera", () -> OwnArt.Texture.plain(OwnMenuArt.StartIcons.camera()));
      into.put("textures/ui/bn_book", () -> OwnArt.Texture.plain(OwnMenuArt.StartIcons.book()));
      into.put("textures/ui/bn_gear", () -> OwnArt.Texture.plain(OwnMenuArt.StartIcons.gear()));
      into.put("textures/ui/bn_close", () -> OwnArt.Texture.plain(OwnMenuArt.StartIcons.cross(PixelArt.rgb(16777215))));
      into.put("textures/ui/bn_close_dark", () -> OwnArt.Texture.plain(OwnMenuArt.StartIcons.cross(TEXT_GREY)));
      into.put("textures/ui/bn_arrow_left", () -> OwnArt.Texture.plain(OwnMenuArt.StartIcons.arrowLeft(PixelArt.rgb(16777215))));
      into.put("textures/ui/bn_arrow_right", () -> OwnArt.Texture.plain(PixelArt.mirror(OwnMenuArt.StartIcons.arrowLeft(PixelArt.rgb(16777215)))));
   }

   static OwnArt.Texture bevel(int face, int top, int shade, int corner) {
      ArgbImage image = PixelArt.canvas(4, 4);
      PixelArt.bevel(image, 0, 0, 4, 4, face, top, shade, corner);
      return OwnArt.Texture.sliced(image, 1);
   }

   static OwnArt.Texture frame(int size, int argb) {
      ArgbImage image = PixelArt.canvas(size, size);
      PixelArt.outline(image, 0, 0, size, size, argb);
      return OwnArt.Texture.sliced(image, 1);
   }

   static final class StartIcons {
      static final String SOCIAL = "textures/ui/socialbuttonicon/social-default-icon";
      static final String SOCIAL_HOVER = "textures/ui/socialbuttonicon/social-hover-icon";

      private StartIcons() {
      }

      static ArgbImage social(int colour) {
         return PixelArt.grid(
            Map.of('#', colour),
            ".....###....",
            "....#...#...",
            "....#...#...",
            "..###...#...",
            ".#..#...#...",
            ".#...###....",
            ".#..........",
            "#..#######..",
            "#.#.......#.",
            "#.#.......#.",
            "..#.......#.",
            "..#########."
         );
      }

      static ArgbImage mail() {
         int paper = PixelArt.rgb(15921906);
         int line = PixelArt.rgb(5921370);
         int shade = PixelArt.rgb(13224393);
         return PixelArt.grid(
            Map.of('w', paper, '#', line, 's', shade),
            "##############",
            "#ww#wwwwww#ww#",
            "#wwww#ww#wwww#",
            "#wwwww##wwwww#",
            "#wwwwwwwwwwww#",
            "#wwwwwwwwwwww#",
            "#wwwwwwwwwwww#",
            "#ssssssssssss#",
            "##############"
         );
      }

      static ArgbImage head() {
         int hair = PixelArt.rgb(13134125);
         int hairDark = PixelArt.rgb(10768927);
         int skin = PixelArt.rgb(15781286);
         int skinShade = PixelArt.rgb(14858382);
         int eyeWhite = PixelArt.rgb(16777215);
         int eye = PixelArt.rgb(4025135);
         int mouth = PixelArt.rgb(13206394);
         return PixelArt.grid(
            Map.of('h', hair, 'H', hairDark, 's', skin, 'S', skinShade, 'w', eyeWhite, 'e', eye, 'm', mouth),
            "hhHhhHhh",
            "hHhhhhHh",
            "hhssshhh",
            "ssssssss",
            "weSsSSew",
            "ssssssss",
            "SssmmssS",
            "SsssssSS"
         );
      }

      static ArgbImage question() {
         return PixelArt.grid(Map.of('#', PixelArt.rgb(5000268)), ".####.", "##..##", "....##", "...##.", "..##..", "..##..", "......", "..##..");
      }

      static ArgbImage camera() {
         int body = PixelArt.rgb(5000268);
         int lens = PixelArt.rgb(14342874);
         return PixelArt.grid(
            Map.of('#', body, 'o', lens),
            "....###....",
            "###########",
            "#####o#####",
            "####ooo####",
            "###oo#oo###",
            "####ooo####",
            "#####o#####",
            "###########"
         );
      }

      static ArgbImage book() {
         int cover = PixelArt.rgb(11546668);
         int page = PixelArt.rgb(16051160);
         int line = PixelArt.rgb(9206370);
         return PixelArt.grid(
            Map.of('c', cover, 'p', page, 'l', line),
            ".ppppp.ppppp.",
            "cpllpp.pllppc",
            "cppppp.ppppcc",
            "cpllpp.pllppc",
            "cppppp.ppppcc",
            "cpllpp.pllppc",
            "ccccccccccccc",
            ".ccccc.ccccc."
         );
      }

      static ArgbImage gear() {
         return PixelArt.grid(
            Map.of('#', PixelArt.rgb(5000268)), "...##...", ".######.", ".##..##.", "###..###", "###..###", ".##..##.", ".######.", "...##..."
         );
      }

      static ArgbImage cross(int colour) {
         return PixelArt.grid(Map.of('#', colour), "##....##", "###..###", ".######.", "..####..", "..####..", ".######.", "###..###", "##....##");
      }

      static ArgbImage arrowLeft(int colour) {
         return PixelArt.grid(Map.of('#', colour), "...#.....", "..##.....", ".########", "#########", ".########", "..##.....", "...#.....");
      }
   }
}
