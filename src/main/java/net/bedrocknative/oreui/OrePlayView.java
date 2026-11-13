package net.bedrocknative.oreui;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

public final class OrePlayView {
   static final long PING_FRAME_MS = 116L;
   private final OreDraw d;
   private final OrePlayView.Context context;
   private final Function<String, OreComponents.State> states;

   public OrePlayView(OreDraw d, OrePlayView.Context context, Function<String, OreComponents.State> states) {
      this.d = d;
      this.context = context;
      this.states = states;
   }

   public void render(OrePlayLayout.Frame frame, OrePlayModel model, @Nullable String selected, boolean listLayout, long now) {
      OreTheme theme = this.d.theme();
      this.d.fill(new OreBox(0.0F, 0.0F, frame.width(), frame.height()), theme.overlay50());
      switch (frame.tab()) {
         case 0:
            this.worlds(frame, model, listLayout);
            break;
         case 1:
            if (frame.realmsNotice() != null) {
               OreComponents.notice(this.d, frame.realmsNotice());
            }
            break;
         default:
            this.servers(frame, model, selected, now);
      }

      this.header(frame.header());

      for (OrePlayLayout.Tab tab : frame.tabs()) {
         OreComponents.tab(this.d, this.context.vars(), tab, this.states.apply("tab:" + tab.index()));
      }

      this.d.sprite("BracketOpen", frame.leftHint(), -1);
      this.d.sprite("BracketClose", frame.rightHint(), -1);
      OreComponents.button(this.d, frame.profile(), this.states.apply(frame.profile().id()));
   }

   private void header(OrePlayLayout.Header header) {
      OreTheme.Role role = this.d.theme().neutral20();
      float b2 = this.d.scale().b2();
      this.d.fill(header.bar(), role.background());
      this.d.fill(header.strip(), role.shadow());
      this.d.fill(header.bevel(), role.bevelBottom());
      OreComponents.barButton(this.d, header.back(), this.states.apply("back"));
      this.d.sprite("arrowBack", header.arrow(), -1);
      this.d
         .textCentered(
            OreText.Type.HEADER5A, this.d.text().ellipsize(OreText.Type.HEADER5A, this.context.title(), header.title().w()), header.title(), role.text(), false
         );
      OreBox divider = header.divider();
      this.d.fill(new OreBox(divider.x(), divider.y(), b2, divider.h()), role.bevelBottom());
      this.d.fill(new OreBox(divider.x() + b2, divider.y(), b2, divider.h()), -1);
      OreComponents.barButton(this.d, header.social(), this.states.apply("social"));
      this.d.icon(OreIcon.FRIENDS, null, header.socialIcon().centerX(), header.socialIcon().centerY(), role.text());
      this.d
         .text(
            OreText.Type.BODY,
            header.socialLabel(),
            header.socialText().x(),
            header.socialText().w(),
            header.socialText().y() + (header.socialText().h() - this.d.text().linePx(OreText.Type.BODY)) / 2.0F,
            OreDraw.Align.LEFT,
            role.text()
         );
   }

   private void worlds(OrePlayLayout.Frame frame, OrePlayModel model, boolean listLayout) {
      OrePlayLayout.Scroll scroll = frame.worldScroll();
      if (scroll != null) {
         this.d.painter().pushClip(scroll.viewport());

         try {
            if (frame.layoutToggle() != null) {
               OreComponents.toggle(this.d, frame.layoutToggle(), listLayout ? OreIcon.LIST : OreIcon.GRID, this.states.apply("toggle-layout"));
            }

            for (OrePlayLayout.Button button : frame.headerButtons()) {
               OreComponents.button(this.d, button, this.states.apply(button.id()));
            }

            if (frame.emptyWorlds() != null) {
               OreComponents.notice(this.d, frame.emptyWorlds());
            }

            for (OrePlayLayout.WorldCard card : frame.worldCards()) {
               if (card.card().bottom() >= scroll.viewport().y() && card.card().y() <= scroll.viewport().bottom()) {
                  this.worldCard(card, model);
               }
            }

            for (OrePlayLayout.WorldRow row : frame.worldRows()) {
               if (row.row().bottom() >= scroll.viewport().y() && row.row().y() <= scroll.viewport().bottom()) {
                  this.worldRow(row, model);
               }
            }

            OrePlayLayout.Pagination pages = frame.pagination();
            if (pages != null) {
               OreComponents.pageArrow(this.d, pages.previous(), pages.previousArrow(), false, pages.previousEnabled(), this.states.apply("page-prev"));
               OreComponents.pages(this.d, pages, this.states);
               OreComponents.pageArrow(this.d, pages.next(), pages.nextArrow(), true, pages.nextEnabled(), this.states.apply("page-next"));
            }
         } finally {
            this.d.painter().popClip();
         }

         OreComponents.scrollbar(this.d, scroll);
      }
   }

   @Nullable
   private Path worldPicture(boolean lan, int index, OrePlayModel model) {
      if (!lan) {
         int world = index - model.lan().size();
         if (world >= 0 && world < model.worlds().size() && model.worlds().get(world).icon() != null) {
            return model.worlds().get(world).icon();
         }
      }

      return this.context.defaultWorldPicture();
   }

   private void worldRow(OrePlayLayout.WorldRow row, OrePlayModel model) {
      OreTheme theme = this.d.theme();
      float b2 = this.d.scale().b2();
      OreThemeVars vars = this.context.vars();
      this.d.borderImage(orDefault(vars.borderImage("listItemBaseDefault"), "listItem_disabled_default"), row.row());
      OreComponents.State action = this.states.apply("world:" + row.index());
      this.d.borderImage(orDefault(vars.borderImage(listState("listItemAction", action)), listSprite(action)), row.action());
      this.d.border(row.pictureBox(), theme.neutral().border(), b2);
      Path picture = this.worldPicture(row.lan(), row.index(), model);
      if (picture != null) {
         this.d.painter().picture(picture, row.image(), -1);
      }

      this.d.text(OreText.Type.BODY, row.titleText(), row.title().x(), row.title().w(), row.title().y(), OreDraw.Align.LEFT, theme.text());
      if (row.tag() != null) {
         this.d.fill(row.tag(), theme.neutral80().background());
         this.d.text(OreText.Type.BODY, row.tagText(), row.tag().x() + this.d.scale().px(0.4F), row.tag().w(), row.tag().y(), OreDraw.Align.LEFT, theme.text());
      }

      this.d.lines(OreText.Type.CAPTION_SHORT, row.metaLines(), row.meta().x(), row.meta().w(), row.meta().y(), OreDraw.Align.RIGHT, theme.dimmest());
      if (row.players() != null) {
         this.players(row.players(), row.playersText());
      }

      if (action.focused()) {
         this.d.focusRing(row.action(), -1, false);
      }

      if (!row.lan() && row.edit().w() > 0.0F) {
         OreComponents.State edit = this.states.apply("world-edit:" + row.index());
         this.d.borderImage(orDefault(vars.borderImage(listState("listItemAdditionalAction", edit)), listSprite(edit)), row.edit());
         this.d.icon(OreIcon.EDIT, null, row.editIcon().centerX(), row.editIcon().centerY(), theme.text());
         if (edit.focused()) {
            this.d.focusRing(row.edit(), -1, false);
         }
      }
   }

   private void worldCard(OrePlayLayout.WorldCard card, OrePlayModel model) {
      OreTheme theme = this.d.theme();
      float b2 = this.d.scale().b2();
      this.d.fill(card.imageBox(), theme.neutral().background());
      this.d.border(card.imageBox(), theme.neutral().border(), b2, true, true, false, true);
      Path picture = this.worldPicture(card.lan(), card.index(), model);
      if (picture != null) {
         this.d.painter().picture(picture, card.image(), -1);
      }

      this.tagFrame(card.bottomFrame(), card.bottomTags(), true);
      this.tagFrame(card.topFrame(), card.topTags(), false);
      OreThemeVars vars = this.context.vars();
      this.d.borderImage(orDefault(vars.borderImage("listItemBaseDefault"), "listItem_disabled_default"), card.info());
      OreComponents.State action = this.states.apply("world:" + card.index());
      this.d.borderImage(orDefault(vars.borderImage(listState("listItemAction", action)), listSprite(action)), card.action());
      this.d.text(OreText.Type.BODY, card.titleText(), card.title().x(), card.title().w(), card.title().y(), OreDraw.Align.LEFT, theme.text());
      if (!card.subtitleText().isEmpty()) {
         this.d.text(OreText.Type.BODY, card.subtitleText(), card.subtitle().x(), card.subtitle().w(), card.subtitle().y(), OreDraw.Align.LEFT, theme.dimmer());
      }

      if (card.players() != null) {
         this.players(card.players(), card.playersText());
      }

      if (action.focused()) {
         this.d.focusRing(card.action(), -1, false);
      }

      if (!card.lan() && card.edit().w() > 0.0F) {
         OreComponents.State edit = this.states.apply("world-edit:" + card.index());
         this.d.borderImage(orDefault(vars.borderImage(listState("listItemAdditionalAction", edit)), listSprite(edit)), card.edit());
         this.d.icon(OreIcon.EDIT, null, card.editIcon().centerX(), card.editIcon().centerY(), theme.text());
         if (edit.focused()) {
            this.d.focusRing(card.edit(), -1, false);
         }
      }
   }

   private void tagFrame(@Nullable OreBox frame, List<OrePlayLayout.Tag> tags, boolean bottom) {
      if (frame != null) {
         OreTheme theme = this.d.theme();
         float b2 = this.d.scale().b2();
         this.d.fill(frame, -1291845632);
         this.d.border(frame, theme.neutral80().border(), b2, bottom, true, !bottom, false);

         for (int i = 0; i < tags.size(); i++) {
            OrePlayLayout.Tag tag = tags.get(i);

            int background = switch (tag.role()) {
               case NEUTRAL80 -> theme.neutral80().background();
               case INFORMATIVE_TINT -> -7556097;
               case DESTRUCTIVE_TINT -> -32640;
            };
            int color = tag.role() == OrePlayLayout.TagRole.NEUTRAL80 ? theme.text() : -14803425;
            this.d.fill(tag.box(), background);
            if (i < tags.size() - 1) {
               this.d.fill(new OreBox(tag.box().right(), tag.box().y(), b2, tag.box().h()), theme.neutral80().border());
            }

            this.d.text(OreText.Type.BODY, tag.text(), tag.box().x() + this.d.scale().px(0.4F), tag.box().w(), tag.box().y(), OreDraw.Align.LEFT, color);
         }
      }
   }

   private static String listState(String prefix, OreComponents.State state) {
      return prefix + (state.pressed() ? "Pressed" : (state.hovered() ? "Hovered" : "Default"));
   }

   private static String listSprite(OreComponents.State state) {
      return state.pressed() ? "listItem_action_pressed" : (state.hovered() ? "listItem_action_hovered" : "listItem_action_default");
   }

   private static OreBorderImage orDefault(@Nullable OreBorderImage image, String sprite) {
      if (image != null) {
         return image;
      } else {
         float[] widths = new float[]{0.2F, 0.2F, 0.2F, 0.2F};
         return new OreBorderImage(sprite, 1, 1, 1, 1, true, widths, new float[4], widths);
      }
   }

   private void servers(OrePlayLayout.Frame frame, OrePlayModel model, @Nullable String selected, long now) {
      OreTheme theme = this.d.theme();
      float b2 = this.d.scale().b2();
      OrePlayLayout.Scroll menu = frame.menuScroll();
      if (menu != null && frame.menuPanel() != null) {
         this.d.painter().pushClip(menu.viewport());

         try {
            OreTheme.Role role = theme.neutralAlpha60();
            this.d.fill(frame.menuPanel(), role.background());
            if (frame.addServer() != null) {
               OreComponents.button(this.d, frame.addServer(), this.states.apply("add-server"));
            }

            for (OrePlayLayout.Section section : frame.sections()) {
               this.d
                  .text(
                     OreText.Type.CAPTION_SHORT,
                     section.labelText(),
                     section.label().x(),
                     section.label().w(),
                     section.label().y(),
                     OreDraw.Align.LEFT,
                     theme.dimmer()
                  );
               OreBox line = section.line();
               this.d.fill(new OreBox(line.x(), line.y(), line.w(), b2), role.bevelTop());
               this.d.fill(new OreBox(line.x(), line.y() + b2, line.w(), b2), role.bevelBottom());

               for (OrePlayLayout.ListItem item : section.items()) {
                  OreComponents.listItem(this.d, item, item.id().equals(selected), this.states.apply(item.id()));
                  if (item.icon() != null && item.id().startsWith("featured:")) {
                     int index = OrePlayLayout.index(item.id());
                     Path icon = index >= 0 && index < model.featured().size() ? model.featured().get(index).server().icon() : null;
                     if (icon != null) {
                        this.d.painter().picture(icon, item.icon().inset(b2), -1);
                     }
                  }
               }
            }
         } finally {
            this.d.painter().popClip();
         }

         OreComponents.scrollbar(this.d, menu);
      }

      OrePlayLayout.Scroll details = frame.detailsScroll();
      if (details != null) {
         this.d.painter().pushClip(details.viewport());

         try {
            if (frame.saved() != null) {
               this.saved(frame.saved(), now);
            } else if (frame.featured() != null && selected != null) {
               int index = OrePlayLayout.index(selected);
               this.featured(frame.featured(), index >= 0 && index < model.featured().size() ? model.featured().get(index) : null, now);
            }
         } finally {
            this.d.painter().popClip();
         }

         OreComponents.scrollbar(this.d, details);
      }
   }

   private void saved(OrePlayLayout.SavedDetails saved, long now) {
      OreTheme theme = this.d.theme();
      float b2 = this.d.scale().b2();
      this.d.fill(saved.bar(), theme.neutral100().background());
      this.ping(saved.ping(), saved.pingSprite(), saved.pingText(), now);
      this.players(saved.players(), saved.playersText());
      OreComponents.button(this.d, saved.play(), this.states.apply("play"));
      OreTheme.Role card = theme.neutral80();

      for (OrePlayLayout.FieldRow row : saved.rows()) {
         this.d.fill(row.box(), card.background());
         this.d.lines(row.box(), card.bevelTop(), card.bevelBottom(), b2, true, false, true, false);
         this.d.text(OreText.Type.BODY, row.valueText(), row.value().x(), row.value().w(), row.value().y(), OreDraw.Align.LEFT, theme.text());
         this.d.text(OreText.Type.CAPTION_SHORT, row.labelText(), row.label().x(), row.label().w(), row.label().y(), OreDraw.Align.LEFT, theme.dimmer());
         if (row.toggle() != null) {
            OreComponents.button(this.d, row.toggle(), this.states.apply(row.toggle().id()));
         }
      }

      OreBox footer = saved.footer();
      this.d.fill(footer, theme.neutral().background());
      this.d.border(footer, theme.neutral().border(), b2, false, true, true, true);
      this.d.lines(footer.inset(b2, 0.0F, b2, b2), theme.neutral().bevelTop(), theme.neutral().bevelBottom(), b2, true, false, true, false);
      OreComponents.button(this.d, saved.edit(), this.states.apply("edit-server"));
   }

   private void featured(OrePlayLayout.FeaturedDetails details, @Nullable OrePlayModel.Featured partner, long now) {
      OreTheme theme = this.d.theme();
      float b2 = this.d.scale().b2();
      OreTheme.Role card = theme.neutral80();
      this.d.fill(details.card(), card.background());
      this.d.border(details.card(), card.border(), b2, true, true, false, true);
      if (partner != null && partner.server().banner() != null) {
         this.d.painter().picture(partner.server().banner(), details.banner(), -1);
      } else {
         this.d.fill(details.banner(), theme.neutral().background());
      }

      this.tagFrame(details.sponsorFrame(), details.sponsorTags(), false);
      this.d.fill(details.overlay(), -1291845632);
      if (!details.pingText().isEmpty()) {
         this.ping(details.ping(), details.pingSprite(), details.pingText(), now);
      }

      if (!details.playersText().isEmpty()) {
         this.players(details.players(), details.playersText());
      }

      this.d.lines(details.nameRow(), card.bevelTop(), card.bevelBottom(), b2, true, false, true, false);
      this.d.text(OreText.Type.BODY, details.nameText(), details.name().x(), details.name().w(), details.name().y(), OreDraw.Align.LEFT, theme.text());
      OreComponents.button(this.d, details.play(), this.states.apply("play"));
      this.d.lines(details.descriptionRow(), card.bevelTop(), card.bevelBottom(), b2, true, false, true, false);
      this.d
         .text(
            OreText.Type.BODY,
            details.descriptionTitleText(),
            details.descriptionTitle().x(),
            details.descriptionTitle().w(),
            details.descriptionTitle().y(),
            OreDraw.Align.LEFT,
            theme.text()
         );
      this.d
         .lines(
            OreText.Type.CAPTION_SHORT,
            details.descriptionLines(),
            details.descriptionText().x(),
            details.descriptionText().w(),
            details.descriptionText().y(),
            OreDraw.Align.LEFT,
            theme.dimmer()
         );
   }

   private void ping(OreBox box, @Nullable String sprite, String label, long now) {
      OreScale s = this.d.scale();
      float iconW = OrePlayLayout.pingIconWidth(s, sprite);
      float lineH = this.d.text().linePx(OreText.Type.BODY);
      if (sprite != null) {
         float size = 20.0F * s.b1();
         this.d.sprite(sprite, new OreBox(box.x(), box.y() + (lineH - size) / 2.0F, size, size), -1);
      } else if (this.d.hasSprite("pingAnimation")) {
         int frame = (int)(now / 116L % 6L);
         float frameTexels = 18.666666F;
         float frameW = s.px(11.2F) / 6.0F;
         float frameH = s.px(2.0F);
         this.d
            .painter()
            .sprite(
               "pingAnimation",
               frame * frameTexels,
               0.0F,
               frameTexels,
               20.0F,
               new OreBox(box.x() + s.b2(), box.y() + (lineH - frameH) / 2.0F, frameW, frameH),
               -1
            );
      }

      this.d.text(OreText.Type.BODY, label, box.x() + iconW + s.px(0.4F), box.w(), box.y(), OreDraw.Align.LEFT, this.d.theme().dimmer());
   }

   private void players(OreBox box, String count) {
      OreScale s = this.d.scale();
      float icon = s.px(2.4F);
      this.d.sprite("player-online-icon", new OreBox(box.x(), box.y() + (box.h() - icon) / 2.0F, icon, icon), -1);
      this.d
         .text(
            OreText.Type.BODY,
            count,
            box.x() + icon + s.px(0.4F),
            box.w(),
            box.y() + (box.h() - this.d.text().linePx(OreText.Type.BODY)) / 2.0F,
            OreDraw.Align.LEFT,
            this.d.theme().dimmer()
         );
   }

   public record Context(String title, OreThemeVars vars, @Nullable Path defaultWorldPicture) {
   }
}
