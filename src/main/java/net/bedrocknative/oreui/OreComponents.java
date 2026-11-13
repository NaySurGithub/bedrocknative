package net.bedrocknative.oreui;

import java.util.function.Function;
import org.jspecify.annotations.Nullable;

public final class OreComponents {
   private OreComponents() {
   }

   public static void button(OreDraw d, OrePlayLayout.Button button, OreComponents.State state) {
      button(d, button.box(), button.text(), button.variant(), button.icon(), state);
   }

   public static void button(OreDraw d, OreBox box, String label, OreTheme.Variant variant, @Nullable OreIcon icon, OreComponents.State state) {
      OreScale s = d.scale();
      OreTheme.Role role = d.theme().role(variant);
      OreBox face = buttonFrame(d, box, role, state);
      boolean hero = variant == OreTheme.Variant.HERO;
      OreText.Type type = hero ? OreText.Type.PRIMARY_BUTTON : OreText.Type.SECONDARY_BUTTON;
      int textColor = role.text();
      float iconSize = icon == null ? 0.0F : d.iconSize(icon, null);
      float room = Math.max(0.0F, face.w() - 2.0F * s.px(2.0F) - (icon != null ? iconSize + s.px(0.8F) : 0.0F));
      String shown = d.text().ellipsize(type, label, room);
      float labelW = d.text().width(type, shown);
      float rowW = labelW + (icon != null ? iconSize + s.px(0.8F) : 0.0F);
      float x = face.x() + (face.w() - rowW) / 2.0F;
      if (icon != null) {
         d.icon(icon, null, x + iconSize / 2.0F, face.y() + face.h() / 2.0F, textColor);
         x += iconSize + s.px(0.8F);
      }

      float lineTop = face.y() + (face.h() - d.text().linePx(type)) / 2.0F;
      d.text(type, shown, x, labelW, lineTop, OreDraw.Align.LEFT, textColor, hero && state.enabled());
      if (state.focused() && state.enabled()) {
         d.focusRing(box, role.outline(), false);
      }
   }

   static OreBox buttonFrame(OreDraw d, OreBox box, OreTheme.Role role, OreComponents.State state) {
      OreScale s = d.scale();
      float b2 = s.b2();
      boolean pressed = state.pressed() && state.enabled();
      boolean hovered = state.hovered() && state.enabled() && !pressed;
      float elevation = s.px(0.4F);
      OreBox outer = pressed ? new OreBox(box.x(), box.y() + elevation, box.w(), box.h() - elevation) : box;
      int faceColor = pressed ? role.pressed() : (hovered ? role.hovered() : role.background());
      d.border(outer, role.border(), b2);
      OreBox inner = outer.inset(b2);
      OreBox face = pressed ? inner : new OreBox(inner.x(), inner.y(), inner.w(), Math.max(0.0F, inner.h() - elevation));
      if (!pressed) {
         d.fill(new OreBox(inner.x(), face.bottom(), inner.w(), elevation), role.shadow());
      }

      d.fill(face, faceColor);
      if (state.enabled()) {
         d.lines(
            face,
            hovered ? role.specularHoveredTop() : role.specularTop(),
            hovered ? role.specularHoveredBottom() : role.specularBottom(),
            b2,
            true,
            true,
            true,
            true
         );
      }

      return face;
   }

   public static void pageArrow(OreDraw d, OreBox box, OreBox arrow, boolean forward, boolean enabled, OreComponents.State state) {
      OreComponents.State shown = enabled ? state : new OreComponents.State(false, false, false, false);
      buttonFrame(d, box, d.theme().secondary(), shown);
      float drop = shown.pressed() ? d.scale().px(0.4F) : 0.0F;
      String sprite = (forward ? "arrowForward" : "arrowBack") + (enabled ? "" : "Disabled");
      d.sprite(sprite, arrow.offset(0.0F, drop), -1);
      if (shown.focused()) {
         d.focusRing(box, d.theme().secondary().outline(), false);
      }
   }

   public static void pages(OreDraw d, OrePlayLayout.Pagination pagination, Function<String, OreComponents.State> states) {
      OreTheme.Role role = d.theme().neutral();
      float b2 = d.scale().b2();
      d.border(pagination.frame(), role.border(), b2);

      for (OrePlayLayout.PageCell cell : pagination.cells()) {
         OreComponents.State state = states.apply(cell.id());
         boolean live = !cell.selected();
         int face = live && state.pressed() ? role.pressed() : (live && state.hovered() ? role.hovered() : role.background());
         d.fill(cell.box(), face);
         if (live) {
            d.lines(cell.box(), role.bevelTop(), role.bevelBottom(), b2, true, true, true, true);
         } else {
            d.fill(cell.underline(), role.text());
         }

         d.textCentered(OreText.Type.BODY, cell.label(), cell.box(), role.text(), false);
      }

      for (OrePlayLayout.PageCell cell : pagination.cells()) {
         if (states.apply(cell.id()).focused()) {
            d.focusRing(cell.box(), role.outline(), false);
         }
      }
   }

   public static void barButton(OreDraw d, OreBox box, OreComponents.State state) {
      OreTheme.Role role = d.theme().neutral20();
      if (state.pressed()) {
         d.fill(box, role.pressed());
      } else if (state.hovered()) {
         d.fill(box, role.hovered());
      }

      if (state.focused()) {
         d.focusRing(box, role.outline(), true);
      }
   }

   public static void toggle(OreDraw d, OreBox box, OreIcon icon, OreComponents.State state) {
      OreTheme.Role role = d.theme().neutral60();
      float b2 = d.scale().b2();
      d.border(box, role.border(), b2);
      OreBox face = box.inset(b2);
      d.fill(face, state.pressed() ? role.pressed() : (state.hovered() ? role.hovered() : role.background()));
      d.lines(face, role.bevelTop(), role.bevelBottom(), b2, true, true, true, true);
      d.icon(icon, 24.0F, face.centerX(), face.centerY(), role.text());
      if (state.focused()) {
         d.focusRing(box, role.outline(), false);
      }
   }

   public static void tab(OreDraw d, OreThemeVars theme, OrePlayLayout.Tab tab, OreComponents.State state) {
      String prefix;
      if (tab.selected()) {
         prefix = state.focused() ? "tabBarNeutralPressedFocused" : "tabBarNeutralPressed";
      } else if (state.focused()) {
         prefix = "tabBarNeutralFocused";
      } else if (state.hovered()) {
         prefix = "tabBarNeutralHovered";
      } else {
         prefix = "tabBarNeutralDefault";
      }

      OreBorderImage image = theme.borderImage(prefix);
      if (image == null) {
         image = fallbackTab(tab.selected(), state);
      }

      d.borderImage(image, tab.box());
      d.sprite(OrePlayLayout.tabIcon(tab.index()), tab.icon(), -1);
      d.textCentered(OreText.Type.BODY, tab.text(), tab.label(), d.theme().text(), false);
      if (tab.selected()) {
         d.fill(tab.indicator(), -1);
      }
   }

   static OreBorderImage fallbackTab(boolean selected, OreComponents.State state) {
      if (selected) {
         return new OreBorderImage(
            "tabBar_neutral_pressed", 2, 2, 2, 2, true, new float[]{0.4F, 0.4F, 0.4F, 0.4F}, new float[4], new float[]{0.4F, 0.4F, 0.4F, 0.4F}
         );
      } else {
         String sprite = state.hovered() ? "tabBar_neutral_hovered" : "tabBar_neutral_default";
         return new OreBorderImage(sprite, 2, 2, 4, 2, true, new float[]{0.4F, 0.4F, 0.8F, 0.4F}, new float[4], new float[]{0.4F, 0.4F, 0.8F, 0.4F});
      }
   }

   public static void listItem(OreDraw d, OrePlayLayout.ListItem item, boolean selected, OreComponents.State state) {
      OreTheme.Role role = d.theme().neutralAlpha60();
      float b2 = d.scale().b2();
      OreBox box = item.box();
      if (state.pressed()) {
         d.fill(box, role.pressed());
         d.lines(box, role.pressedBevelTop(), role.pressedBevelBottom(), b2, true, false, true, false);
      } else if (selected) {
         d.fill(box, role.selected());
         d.lines(box, role.pressedBevelTop(), role.pressedBevelBottom(), b2, true, false, true, false);
      } else if (state.hovered()) {
         d.fill(box, role.hovered());
         d.lines(box, role.bevelTop(), role.bevelBottom(), b2, true, false, true, false);
      }

      d.text(OreText.Type.BODY, item.titleText(), item.title().x(), item.title().w(), item.title().y(), OreDraw.Align.LEFT, d.theme().text());
      if (item.subtitle() != null) {
         d.text(
            OreText.Type.CAPTION_SHORT,
            item.subtitleText(),
            item.subtitle().x(),
            item.subtitle().w(),
            item.subtitle().y(),
            OreDraw.Align.LEFT,
            d.theme().dimmer()
         );
      }

      if (item.icon() != null) {
         d.border(item.icon(), d.theme().neutral().border(), b2);
      }

      if (item.tag() != null) {
         tag(d, item.tag());
      }

      if (state.focused()) {
         d.focusRing(box.inset(2.0F * b2), -1, false);
      }
   }

   public static void tag(OreDraw d, OrePlayLayout.Tag tag) {
      OreTheme theme = d.theme();

      int background = switch (tag.role()) {
         case NEUTRAL80 -> theme.neutral80().background();
         case INFORMATIVE_TINT -> -7556097;
         case DESTRUCTIVE_TINT -> -32640;
      };
      int color = tag.role() == OrePlayLayout.TagRole.NEUTRAL80 ? theme.text() : -14803425;
      d.fill(tag.box(), background);
      d.text(OreText.Type.BODY, tag.text(), tag.box().x() + d.scale().px(0.4F), tag.box().w(), tag.box().y(), OreDraw.Align.LEFT, color);
   }

   public static void scrollbar(OreDraw d, OrePlayLayout.Scroll scroll) {
      if (scroll.track() != null && scroll.thumb() != null) {
         OreScale s = d.scale();
         OreBox track = scroll.track();
         float groove = s.px(0.4F);
         d.fill(new OreBox(track.x() + (track.w() - groove) / 2.0F, track.y(), groove, track.h()), -10987430);
         OreBox thumb = scroll.thumb();
         d.fill(new OreBox(thumb.x(), thumb.bottom(), thumb.w(), s.b2()), 855638016);
         d.border(thumb, -16777216, s.b2());
         d.fill(thumb.inset(s.b2()), -1644309);
      }
   }

   public static void notice(OreDraw d, OrePlayLayout.Notice notice) {
      OreTheme.Role role = d.theme().neutral80();
      float b2 = d.scale().b2();
      d.fill(notice.card(), role.background());
      d.border(notice.card(), role.border(), b2);
      d.text(
         OreText.Type.SECONDARY_BUTTON, notice.titleText(), notice.title().x(), notice.title().w(), notice.title().y(), OreDraw.Align.CENTER, d.theme().text()
      );
      if (notice.art() != null && notice.artSprite() != null) {
         d.sprite(notice.artSprite(), notice.art(), -1);
      }

      d.lines(OreText.Type.CAPTION_SHORT, notice.lines(), notice.text().x(), notice.text().w(), notice.text().y(), OreDraw.Align.CENTER, d.theme().dimmest());
   }

   public record State(boolean hovered, boolean pressed, boolean focused, boolean enabled) {
      public static final OreComponents.State IDLE = new OreComponents.State(false, false, false, true);
   }
}
