package net.bedrocknative.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public final class HudRenderers {
   public static final String HOTBAR = "hotbar_renderer";
   public static final String COOLDOWN = "hotbar_cooldown_renderer";
   public static final String HEARTS = "heart_renderer";
   public static final String MOUNT_HEARTS = "horse_heart_renderer";
   public static final String ARMOR = "armor_renderer";
   public static final String HUNGER = "hunger_renderer";
   public static final String BUBBLES = "bubbles_renderer";
   public static final String JUMP = "horse_jump_renderer";
   public static final String DASH = "dash_renderer";
   public static final String EFFECTS = "mob_effects_renderer";
   public static final String LOCATOR = "locator_bar";
   public static final Set<String> NAMES = Set.of(
      "hotbar_renderer",
      "hotbar_cooldown_renderer",
      "heart_renderer",
      "horse_heart_renderer",
      "armor_renderer",
      "hunger_renderer",
      "bubbles_renderer",
      "horse_jump_renderer",
      "dash_renderer",
      "mob_effects_renderer"
   );
   public static final String UI = "textures/ui/";
   public static final int COOLDOWN_COLOR = Integer.MAX_VALUE;
   static final int ICON = 9;
   static final int ICON_STEP = 8;
   static final int ROW = 10;
   static final int ITEM = 16;
   static final int EFFECT_FRAME = 24;
   static final int EFFECT_ICON = 18;
   static final int EFFECT_STEP = 25;
   static final int EFFECT_HARMFUL_ROW = 26;
   static final int JUMP_PIXELS = 182;

   private HudRenderers() {
   }

   public static boolean handles(String renderer) {
      return NAMES.contains(renderer);
   }

   public static List<UiDrawCommand> expand(UiDrawCommand.Custom command, HudValues values, UiTextures textures) {
      HudRenderers.Out out = new HudRenderers.Out(command, textures);
      switch (command.renderer()) {
         case "hotbar_renderer":
            hotbarSlot(out, values);
            break;
         case "hotbar_cooldown_renderer":
            cooldown(out, values);
            break;
         case "heart_renderer":
            hearts(out, values);
            break;
         case "armor_renderer":
            armor(out, values);
            break;
         case "hunger_renderer":
            hunger(out, values);
            break;
         case "bubbles_renderer":
            bubbles(out, values);
            break;
         case "horse_heart_renderer":
            mountHearts(out, values);
            break;
         case "horse_jump_renderer":
            jump(out, values);
            break;
         case "dash_renderer":
            dash(out, values);
            break;
         case "mob_effects_renderer":
            effects(out, values);
      }

      return out.commands;
   }

   public static int slot(UiControl control) {
      String collection = control.itemCollection();
      return collection != null && collection.startsWith("hotbar") ? control.itemIndex() : -1;
   }

   private static void hotbarSlot(HudRenderers.Out out, HudValues values) {
      int slot = slot(out.command.control());
      if (slot >= 0 && !values.hides(HudValues.Part.HOTBAR)) {
         out.image("textures/ui/hotbar_" + Math.min(slot, 8), out.x(), out.y(), out.w(), out.h());
      }
   }

   private static void cooldown(HudRenderers.Out out, HudValues values) {
      int slot = slot(out.command.control());
      float left = slot < 0 ? 0.0F : values.cooldown(slot);
      if (!(left <= 0.0F) && !values.hides(HudValues.Part.HOTBAR)) {
         float itemX = out.x() + (out.w() - 16.0F) / 2.0F;
         float itemY = out.y() + (out.h() - 16.0F) / 2.0F;
         float top = itemY + (float)Math.floor(16.0F * (1.0F - left));
         out.fill(itemX, top, 16.0F, (float)Math.ceil(16.0F * left), Integer.MAX_VALUE);
      }
   }

   private static String[] heartTextures(HudValues.HeartType type) {
      String base = switch (type) {
         case POISONED -> "poison_heart";
         case WITHERED -> "wither_heart";
         case FROZEN -> "freeze_heart";
         case NORMAL -> "heart";
      };
      return new String[]{"textures/ui/" + base, "textures/ui/" + base + "_half", "textures/ui/" + base + "_flash", "textures/ui/" + base + "_flash_half"};
   }

   private static void hearts(HudRenderers.Out out, HudValues values) {
      if (values.mode().survivalUi() && !values.hides(HudValues.Part.HEALTH)) {
         Random random = new Random(values.tick() * 312871L);
         int health = values.health();
         int old = values.displayHealth();
         int absorption = values.absorption();
         boolean blink = values.healthBlink();
         int containers = (int)Math.ceil(values.maxHealth() / 2.0);
         int absorptionContainers = (int)Math.ceil(absorption / 2.0);
         int maxHalves = containers * 2;
         int rowHeight = values.healthRowHeight();
         int wave = values.regeneration() ? values.tick() % (int)Math.ceil(values.maxHealth() + 5.0F) : -1;
         String[] kind = heartTextures(values.heartType());
         String[] absorbing = values.heartType() == HudValues.HeartType.WITHERED
            ? kind
            : new String[]{"textures/ui/absorption_heart", "textures/ui/absorption_heart_half"};

         for (int i = containers + absorptionContainers - 1; i >= 0; i--) {
            float x = out.x() + i % 10 * 8;
            float y = out.y() - i / 10 * rowHeight;
            if (health + absorption <= 4) {
               y += random.nextInt(2);
            }

            if (i < containers && i == wave) {
               y -= 2.0F;
            }

            out.icon(blink ? "textures/ui/heart_blink" : "textures/ui/heart_background", x, y);
            int halves = i * 2;
            if (i >= containers) {
               int absorbed = halves - maxHalves;
               if (absorbed < absorption) {
                  out.icon(absorbed + 1 == absorption ? absorbing[1] : absorbing[0], x, y);
               }
            }

            if (blink && halves < old) {
               out.icon(halves + 1 == old ? kind[3] : kind[2], x, y);
            }

            if (halves < health) {
               out.icon(halves + 1 == health ? kind[1] : kind[0], x, y);
            }
         }
      }
   }

   private static void armor(HudRenderers.Out out, HudValues values) {
      int armor = values.armor();
      if (armor > 0 && values.mode().survivalUi() && !values.hides(HudValues.Part.ARMOR)) {
         float y = out.y() - (values.healthRows() - 1) * values.healthRowHeight() - 10.0F;

         for (int i = 0; i < 10; i++) {
            String texture = i * 2 + 1 < armor ? "armor_full" : (i * 2 + 1 == armor ? "armor_half" : "armor_empty");
            out.icon("textures/ui/" + texture, out.x() + i * 8, y);
         }
      }
   }

   private static void hunger(HudRenderers.Out out, HudValues values) {
      if (values.mode().survivalUi() && !values.hides(HudValues.Part.FOOD)) {
         Random random = new Random(values.tick() * 312871L);
         int food = values.food();
         String prefix = values.hungerEffect() ? "textures/ui/hunger_effect_" : "textures/ui/hunger_";
         float right = out.x() + out.w();

         for (int i = 0; i < 10; i++) {
            float y = out.y();
            if (values.saturation() <= 0.0F && values.tick() % (food * 3 + 1) == 0) {
               y += random.nextInt(3) - 1;
            }

            float x = right - i * 8 - 9.0F;
            out.icon(prefix + "background", x, y);
            if (i * 2 + 1 < food) {
               out.icon(prefix + "full", x, y);
            } else if (i * 2 + 1 == food) {
               out.icon(prefix + "half", x, y);
            }
         }
      }
   }

   private static void bubbles(HudRenderers.Out out, HudValues values) {
      if (values.mode().survivalUi() && values.airShown() && !values.hides(HudValues.Part.AIR)) {
         Random random = new Random(values.tick() * 312871L);
         int max = Math.max(1, values.maxAir());
         int air = Math.clamp(values.air(), 0, max);
         boolean underwater = values.underwater();
         int full = bubble(air, max, -2);
         int popping = bubble(air, max, 0);
         int empty = 10 - bubble(air, max, underwater && air != 0 ? 1 : 0);
         boolean bursting = full != popping;
         float right = out.x() + out.w();
         float y = out.y() - values.mountRows() * 10;

         for (int bubble = 1; bubble <= 10; bubble++) {
            float x = right - (bubble - 1) * 8 - 9.0F;
            if (bubble <= full) {
               out.icon("textures/ui/bubble", x, y);
            } else if (bursting && bubble == popping && underwater) {
               out.icon("textures/ui/bubble_pop", x, y);
            } else if (bubble > 10 - empty) {
               int wobble = empty == 10 && values.tick() % 2 == 0 ? random.nextInt(2) : 0;
               out.icon("textures/ui/bubble_empty", x, y + wobble);
            }
         }
      }
   }

   static int bubble(int air, int max, int offset) {
      return (int)Math.ceil((float)((air + offset) * 10) / max);
   }

   public static int burstingBubble(HudValues values) {
      int max = Math.max(1, values.maxAir());
      int air = Math.clamp(values.air(), 0, max);
      int full = bubble(air, max, -2);
      int popping = bubble(air, max, 0);
      return values.underwater() && values.airShown() && full != popping ? popping : 0;
   }

   public static int emptyBubbles(HudValues values) {
      int max = Math.max(1, values.maxAir());
      int air = Math.clamp(values.air(), 0, max);
      return 10 - bubble(air, max, values.underwater() && air != 0 ? 1 : 0);
   }

   private static void mountHearts(HudRenderers.Out out, HudValues values) {
      int hearts = Math.min(values.mountHearts(), 30);
      if (hearts > 0 && !values.hides(HudValues.Part.MOUNT_HEALTH)) {
         int health = values.mountHealth();
         float right = out.x() + out.w();
         float y = out.y();

         for (int base = 0; hearts > 0; base += 20) {
            int row = Math.min(hearts, 10);
            hearts -= row;

            for (int i = 0; i < row; i++) {
               float x = right - i * 8 - 9.0F;
               out.icon("textures/ui/heart_background", x, y);
               if (i * 2 + 1 + base < health) {
                  out.icon("textures/ui/horse_heart", x, y);
               } else if (i * 2 + 1 + base == health) {
                  out.icon("textures/ui/horse_heart_half", x, y);
               }
            }

            y -= 10.0F;
         }
      }
   }

   private static void jump(HudRenderers.Out out, HudValues values) {
      float jump = values.jump();
      if (!(jump < 0.0F) && !values.jumpCooldown()) {
         out.image("textures/ui/horse_jump_empty", out.x(), out.y(), out.w(), out.h());
         float scale = Math.clamp(jump, 0.0F, 1.0F);
         int pixels = (int)Math.floor(scale * 181.0F) + (scale > 0.0F ? 1 : 0);
         if (pixels > 0) {
            out.part("textures/ui/horse_jump_full", out.x(), out.y(), out.w(), out.h(), pixels / 182.0F);
         }
      }
   }

   private static void dash(HudRenderers.Out out, HudValues values) {
      if (!(values.jump() < 0.0F) && values.jumpCooldown()) {
         out.image("textures/ui/dash_cooldown", out.x(), out.y(), out.w(), out.h());
      }
   }

   private static void effects(HudRenderers.Out out, HudValues values) {
      if (!values.effects().isEmpty() && !values.hides(HudValues.Part.EFFECTS)) {
         float right = out.x() + out.w();
         int beneficial = 0;
         int other = 0;

         for (HudValues.Effect effect : values.effects()) {
            if (effect.icon()) {
               float y = out.y() + 1.0F;
               float x;
               if (effect.beneficial()) {
                  beneficial++;
                  x = right - 25 * beneficial;
               } else {
                  other++;
                  x = right - 25 * other;
                  y += 26.0F;
               }

               out.image("textures/ui/" + (effect.ambient() ? "hud_mob_ambient_effect_background" : "hud_mob_effect_background"), x, y, 24.0F, 24.0F);
               float alpha = effect.ambient() ? 1.0F : iconAlpha(effect);
               out.image("textures/ui/" + effectTexture(effect.id()), x + 3.0F, y + 3.0F, 18.0F, 18.0F, alpha);
            }
         }
      }
   }

   static float iconAlpha(HudValues.Effect effect) {
      if (!effect.endingSoon()) {
         return 1.0F;
      }

      int duration = effect.duration();
      int used = 10 - duration / 20;
      float alpha = Math.clamp(duration / 10.0F / 5.0F * 0.5F, 0.0F, 0.5F)
         + (float)Math.cos(duration * Math.PI / 5.0) * Math.clamp(used / 10.0F * 0.25F, 0.0F, 0.25F);
      return Math.clamp(alpha, 0.0F, 1.0F);
   }

   public static String effectTexture(String id) {
      String name = id.toLowerCase(Locale.ROOT);
      int colon = name.indexOf(58);
      if (colon >= 0) {
         name = name.substring(colon + 1);
      }
      return switch (name) {
         case "hero_of_the_village" -> "village_hero_effect";
         case "hunger" -> "hunger_effect";
         case "absorption" -> "absorption_effect";
         default -> name + "_effect";
      };
   }

   public static List<String> textures(List<UiDrawCommand> commands) {
      List<String> paths = new ArrayList<>();

      for (UiDrawCommand command : commands) {
         if (command instanceof UiDrawCommand.Image image) {
            paths.add(image.texture().path());
         }
      }

      return paths;
   }

   public static long count(List<UiDrawCommand> commands, @Nullable String ending) {
      return commands.stream()
         .filter(
            command -> command instanceof UiDrawCommand.Image image
               && (ending == null || image.texture().path().endsWith("/" + ending) || image.texture().path().endsWith("/" + ending + ".png"))
         )
         .count();
   }

   private static final class Out {
      final UiDrawCommand.Custom command;
      final UiTextures textures;
      final List<UiDrawCommand> commands = new ArrayList<>();

      Out(UiDrawCommand.Custom command, UiTextures textures) {
         this.command = command;
         this.textures = textures;
      }

      float x() {
         return this.command.x();
      }

      float y() {
         return this.command.y();
      }

      float w() {
         return this.command.w();
      }

      float h() {
         return this.command.h();
      }

      void icon(String texture, float x, float y) {
         this.image(texture, x, y, 9.0F, 9.0F);
      }

      void image(String texture, float x, float y, float w, float h) {
         this.image(texture, x, y, w, h, 1.0F);
      }

      void image(String texture, float x, float y, float w, float h, float alpha) {
         UiTextureInfo info = this.textures.info(texture, "InUserPackage");
         if (info != null) {
            this.commands
               .add(
                  new UiDrawCommand.Image(
                     this.command.control(),
                     info,
                     List.of(new UiDrawCommand.Quad(x, y, w, h, 0.0F, 0.0F, info.width(), info.height())),
                     this.argb(alpha),
                     false,
                     false,
                     this.command.clip(),
                     this.command.z()
                  )
               );
         }
      }

      void part(String texture, float x, float y, float w, float h, float fraction) {
         UiTextureInfo info = this.textures.info(texture, "InUserPackage");
         if (info != null) {
            this.commands
               .add(
                  new UiDrawCommand.Image(
                     this.command.control(),
                     info,
                     List.of(new UiDrawCommand.Quad(x, y, w * fraction, h, 0.0F, 0.0F, info.width() * fraction, info.height())),
                     this.argb(1.0F),
                     false,
                     false,
                     this.command.clip(),
                     this.command.z()
                  )
               );
         }
      }

      void fill(float x, float y, float w, float h, int argb) {
         int alpha = Math.round((argb >>> 24) * ((this.command.argb() >>> 24) / 255.0F));
         this.commands.add(new UiDrawCommand.Fill(this.command.control(), x, y, w, h, alpha << 24 | argb & 16777215, this.command.clip(), this.command.z()));
      }

      private int argb(float alpha) {
         int a = Math.round((this.command.argb() >>> 24) * Math.clamp(alpha, 0.0F, 1.0F));
         return a << 24 | this.command.argb() & 16777215;
      }
   }
}
