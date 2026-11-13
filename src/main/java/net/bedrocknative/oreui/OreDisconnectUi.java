package net.bedrocknative.oreui;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

public final class OreDisconnectUi {
   static final int INFORMATIVE_TINT = -7556097;
   static final int INFORMATIVE_TINT_TEXT = -14803425;
   static final int SERVER_WORDS_BACKGROUND = -14408667;
   static final int SERVER_WORDS_BORDER = -14803425;
   static final float SMALL_PICTURE_REM = 102.0F;
   static final String STANDARD_PICTURE = "connection_error";
   static final String UPDATE_PICTURE = "ClientMismatchImage";
   static final float WHEEL_REM = 4.8F;
   private OreText text;
   private final OreLang lang;
   private final OreTheme theme;
   private final OreDisconnect data;
   private final OreDisconnectUi.Actions actions;
   private final OreInteraction interaction = new OreInteraction();
   private OreDisconnectUi.Route route = OreDisconnectUi.Route.MAIN;
   private float messageScroll;
   private float leftScroll;
   private float rightScroll;
   private OreDisconnectUi.@Nullable Layout layout;
   private float width;
   private float height;

   public OreDisconnectUi(OreText text, OreLang lang, OreTheme theme, OreDisconnect data, OreDisconnectUi.Actions actions) {
      this.text = text;
      this.lang = lang;
      this.theme = theme;
      this.data = data;
      this.actions = actions;
   }

   public OreDisconnect data() {
      return this.data;
   }

   public OreDisconnectUi.Route route() {
      return this.route;
   }

   public OreInteraction interaction() {
      return this.interaction;
   }

   public OreDisconnectUi.@Nullable Layout layout() {
      return this.layout;
   }

   public void text(OreText current) {
      if (current != this.text) {
         this.text = current;
         if (this.width > 0.0F) {
            this.layout(this.width, this.height);
         }
      }
   }

   public void size(float newWidth, float newHeight) {
      if (newWidth != this.width || newHeight != this.height || this.layout == null) {
         this.layout(newWidth, newHeight);
      }
   }

   public OreDisconnectUi.Layout layout(float newWidth, float newHeight) {
      this.width = newWidth;
      this.height = newHeight;
      this.layout = this.route == OreDisconnectUi.Route.MAIN ? this.main() : this.details();
      this.interaction.refresh(this.layout.widgets());
      return this.layout;
   }

   private float px(float rem) {
      return this.text.scale().px(rem);
   }

   private float mu(int size) {
      return this.px(OrePlayLayout.spacer(size));
   }

   private float[] column(OreScale.Breakpoint breakpoint, int start, int span) {
      OreScale s = this.text.scale();
      float pad = breakpoint == OreScale.Breakpoint.DESKTOP ? this.px(2.4F) : (breakpoint == OreScale.Breakpoint.TABLET ? this.px(1.6F) : this.px(0.8F));
      float gutter = breakpoint == OreScale.Breakpoint.DESKTOP ? this.px(0.8F) : (breakpoint == OreScale.Breakpoint.TABLET ? this.px(0.4F) : s.b2());
      float inner = Math.max(0.0F, this.width - 2.0F * pad);
      float rowWidth = Math.min(inner, this.px(128.0F));
      float rowX = pad + (inner - (rowWidth - 2.0F * gutter)) / 2.0F - gutter;
      float unit = rowWidth / (breakpoint == OreScale.Breakpoint.NARROW ? 8 : 12);
      return new float[]{rowX + start * unit + gutter, Math.max(0.0F, span * unit - 2.0F * gutter)};
   }

   private OreDisconnectUi.Layout main() {
      OreScale s = this.text.scale();
      float b2 = s.b2();
      OreScale.Breakpoint breakpoint = s.breakpoint(this.width);
      boolean narrow = breakpoint == OreScale.Breakpoint.NARROW;
      float[] column = this.column(breakpoint, narrow ? 0 : 1, narrow ? 8 : 10);
      float pad = this.px(1.6F);
      float cardX = column[0];
      float cardW = column[1];
      float innerX = cardX + b2 + pad;
      float innerW = Math.max(0.0F, cardW - 2.0F * (b2 + pad));
      List<OreDisconnectUi.TextBlock> texts = new ArrayList<>();
      float y = b2 + pad;
      y = this.block(texts, OreText.Type.SECONDARY_BUTTON, this.data.title(), innerX, innerW, y, OreDraw.Align.CENTER, this.theme.text(), 4);
      OreBox image = null;
      String imageSprite = null;
      OreDisconnectUi.ScrollBox messageBox = null;
      if (this.data.variant() == OreDisconnect.Variant.SERVER) {
         y += this.mu(4);
         messageBox = this.messageBox(innerX, y, innerW);
         y = messageBox.box().bottom() + this.mu(2);
      } else {
         boolean small = s.widthRem(this.width) <= 102.0F;
         float imageW = (small ? 260 : 520) * s.b1();
         float imageH = (small ? 90 : 180) * s.b1();
         y += this.mu(4);
         image = new OreBox(innerX + (innerW - imageW) / 2.0F, y, imageW, imageH);
         imageSprite = this.data.variant() == OreDisconnect.Variant.CLIENT_MISMATCH ? "ClientMismatchImage" : "connection_error";
         y += imageH + this.mu(4);
         y = this.block(texts, OreText.Type.CAPTION_SHORT, this.data.message(), innerX, innerW, y, OreDraw.Align.CENTER, this.theme.dimmest(), 12);
         if (this.data.hasCodeword()) {
            y += this.mu(5);
            y = this.block(
               texts,
               OreText.Type.CAPTION_SHORT,
               this.lang.get("hbui.ConnectionErrorRoute.standard.codewordHelp", this.data.codeword()),
               innerX,
               innerW,
               y,
               OreDraw.Align.CENTER,
               this.theme.dimmest(),
               4
            );
         }
      }

      y += this.mu(2);
      List<OreDisconnectUi.Button> buttons = this.buttons(innerX, innerW, y, breakpoint);
      y += this.px(4.4F) + 2.0F * b2;
      float cardH = y + pad + b2;
      float top = Math.max(0.0F, (this.height - cardH) / 2.0F);
      OreBox card = new OreBox(cardX, top, cardW, cardH);
      List<OreDisconnectUi.TextBlock> placed = new ArrayList<>();

      for (OreDisconnectUi.TextBlock block : texts) {
         placed.add(block.offset(top));
      }

      List<OreDisconnectUi.Button> placedButtons = new ArrayList<>();
      OreWidgets widgets = new OreWidgets();

      for (OreDisconnectUi.Button button : buttons) {
         OreDisconnectUi.Button moved = button.offset(top);
         placedButtons.add(moved);
         widgets.add(OreWidget.of(moved.id(), moved.box()));
      }

      if (messageBox != null) {
         messageBox = this.messageBox(innerX, messageBox.box().y() + top, innerW);
      }

      return new OreDisconnectUi.Layout(
         OreDisconnectUi.Route.MAIN,
         breakpoint,
         card,
         placed,
         image == null ? null : image.offset(0.0F, top),
         imageSprite,
         messageBox,
         placedButtons,
         null,
         null,
         null,
         null,
         widgets
      );
   }

   private float block(
      List<OreDisconnectUi.TextBlock> out, OreText.Type type, String value, float x, float w, float y, OreDraw.Align align, int color, int maxLines
   ) {
      if (value != null && !value.isEmpty()) {
         List<String> lines = this.text.wrap(type, value, w, maxLines);
         float h = lines.size() * this.text.linePx(type);
         out.add(new OreDisconnectUi.TextBlock(type, lines, new OreBox(x, y, w, h), align, color));
         return y + h;
      } else {
         return y;
      }
   }

   private OreDisconnectUi.ScrollBox messageBox(float x, float y, float w) {
      float b2 = this.text.scale().b2();
      OreText.Type type = OreText.Type.CAPTION_LONG;
      float innerW = Math.max(0.0F, w - 2.0F * b2);
      float minInner = Math.max(0.0F, this.px(8.0F) - 2.0F * b2);
      float maxInner = Math.max(minInner, this.px(32.0F) - 2.0F * b2);
      List<String> lines = this.text.wrap(type, this.data.serverMessage(), innerW, 400);
      float textH = lines.size() * this.text.linePx(type);
      boolean overflow = textH > maxInner + 0.5F;
      if (overflow) {
         lines = this.text.wrap(type, this.data.serverMessage(), Math.max(0.0F, innerW - this.px(1.6F)), 400);
         textH = lines.size() * this.text.linePx(type);
      }

      float viewH = Math.clamp(textH, minInner, maxInner);
      OreBox box = new OreBox(x, y, w, viewH + 2.0F * b2);
      OreBox viewport = box.inset(b2);
      OrePlayLayout.Scroll scroll = this.scroll("message", viewport, textH, this.messageScroll, overflow);
      this.messageScroll = scroll.offset();
      float textTop = viewport.y() + Math.max(0.0F, (viewport.h() - textH) / 2.0F);
      float textW = overflow ? Math.max(0.0F, viewport.w() - this.px(1.6F)) : viewport.w();
      OreDisconnectUi.TextBlock block = new OreDisconnectUi.TextBlock(
         type, lines, new OreBox(viewport.x(), textTop, textW, textH), OreDraw.Align.LEFT, this.theme.dimmer()
      );
      return new OreDisconnectUi.ScrollBox("message", box, viewport, List.of(block), scroll);
   }

   private List<OreDisconnectUi.Button> buttons(float innerX, float innerW, float y, OreScale.Breakpoint breakpoint) {
      List<String[]> choices = new ArrayList<>();
      String route = "hbui.ConnectionErrorRoute";
      switch (this.data.variant()) {
         case STANDARD:
            choices.add(new String[]{"back", this.lang.get(route + ".standard.backToMenu")});
            if (this.data.reconnect()) {
               choices.add(new String[]{"reconnect", this.lang.get(route + ".standard.tryReconnect")});
            }

            if (this.data.hasDetails()) {
               choices.add(new String[]{"details", this.lang.get(route + ".standard.showDetails")});
            }
            break;
         case SERVER:
            choices.add(new String[]{"back", this.lang.get(route + ".server.backToMenu")});
            if (this.data.hasDetails()) {
               choices.add(new String[]{"details", this.lang.get(route + ".server.showDetails")});
            }
            break;
         case CLIENT_MISMATCH:
            choices.add(new String[]{"back", this.lang.get(route + ".client.goBack")});
      }

      OreScale s = this.text.scale();
      int count = choices.size();
      float wrapperW = count == 1 ? Math.min(innerW * 0.7F, this.px(32.0F)) : Math.min(innerW, this.px(count == 2 ? 52.0F : 87.2F));
      float wrapperX = innerX + (innerW - wrapperW) / 2.0F;
      float gap = this.mu(3);
      float each = count <= 1 ? wrapperW : Math.max(0.0F, (wrapperW - (count - 1) * gap) / count);
      float buttonH = this.px(4.4F) + 2.0F * s.b2();
      boolean linebreak = count <= 2 || breakpoint != OreScale.Breakpoint.NARROW;
      List<OreDisconnectUi.Button> buttons = new ArrayList<>();

      for (int i = 0; i < count; i++) {
         OreBox box = new OreBox(wrapperX + i * (each + gap), y, each, buttonH);
         float labelW = Math.max(0.0F, each - 2.0F * s.b2() - 2.0F * this.px(2.0F));
         List<String> lines = linebreak
            ? this.text.wrap(OreText.Type.SECONDARY_BUTTON, choices.get(i)[1], labelW, 2)
            : List.of(this.text.ellipsize(OreText.Type.SECONDARY_BUTTON, choices.get(i)[1], labelW));
         buttons.add(new OreDisconnectUi.Button(choices.get(i)[0], box, lines));
      }

      return buttons;
   }

   private OreDisconnectUi.Layout details() {
      OreScale s = this.text.scale();
      float b2 = s.b2();
      OreScale.Breakpoint breakpoint = s.breakpoint(this.width);
      OreWidgets widgets = new OreWidgets();
      float barH = this.px(4.4F);
      OreBox bar = new OreBox(0.0F, 0.0F, this.width, barH);
      OreBox back = new OreBox(this.px(0.2F), (barH - this.px(4.0F)) / 2.0F, this.px(4.0F), this.px(4.0F));
      OreBox arrow = new OreBox(back.x() + (back.w() - 4.0F * b2) / 2.0F, back.y() + (back.h() - 8.0F * b2 - b2) / 2.0F + b2, 4.0F * b2, 8.0F * b2);
      OreBox title = new OreBox(this.px(6.0F), 0.0F, Math.max(0.0F, this.width - this.px(12.0F)), barH);
      OrePlayLayout.Header header = new OrePlayLayout.Header(
         bar,
         new OreBox(0.0F, barH, this.width, 2.0F * b2),
         new OreBox(0.0F, barH + 2.0F * b2, this.width, b2),
         back,
         arrow,
         title,
         OreBox.EMPTY,
         OreBox.EMPTY,
         OreBox.EMPTY,
         OreBox.EMPTY,
         ""
      );
      widgets.add(OreWidget.of("details-back", back));
      float top = barH + 2.0F * b2;
      float blockH = this.px(28.8F);
      float blockY = top + Math.max(0.0F, (this.height - top - blockH) / 2.0F);
      boolean narrow = breakpoint == OreScale.Breakpoint.NARROW;
      OreDisconnectUi.Tab tab = null;
      OreDisconnectUi.ScrollBox left = null;
      if (!narrow) {
         float[] column = this.column(breakpoint, 0, 5);
         float boxY = blockY;
         if (this.data.variant() == OreDisconnect.Variant.SERVER) {
            String label = this.data.serverName().isEmpty()
               ? this.lang.get("hbui.ConnectionErrorRoute.details.fromServerNoName")
               : this.lang.get("hbui.ConnectionErrorRoute.details.fromServer", this.data.serverName());
            float tabH = this.px(3.0F);
            float maxText = Math.max(0.0F, column[1] - 2.0F * b2 - 2.0F * this.px(0.8F) - this.px(1.0F));
            String shown = this.text.ellipsize(OreText.Type.CAPTION_SHORT, label, maxText);
            float labelW = 2.0F * b2 + 2.0F * this.px(0.8F) + this.text.width(OreText.Type.CAPTION_SHORT, shown);
            OreBox labelBox = new OreBox(column[0], blockY, labelW, tabH);
            OreBox strip = new OreBox(
               labelBox.right() - b2, blockY + tabH - this.px(0.8F), Math.max(0.0F, column[0] + column[1] - (labelBox.right() - b2)), this.px(0.8F)
            );
            OreDisconnectUi.TextBlock labelText = new OreDisconnectUi.TextBlock(
               OreText.Type.CAPTION_SHORT,
               List.of(shown),
               new OreBox(
                  labelBox.x() + b2 + this.px(0.8F),
                  blockY + 2.0F * b2,
                  labelW - 2.0F * b2 - 2.0F * this.px(0.8F),
                  this.text.linePx(OreText.Type.CAPTION_SHORT)
               ),
               OreDraw.Align.LEFT,
               -14803425
            );
            tab = new OreDisconnectUi.Tab(labelBox, strip, labelText);
            boxY = blockY + tabH - b2;
         }

         String words = this.data.variant() == OreDisconnect.Variant.SERVER
            ? this.data.serverMessage()
            : this.lang.get("hbui.ConnectionErrorRoute.details.errorDetailsHelp");
         left = this.scrollBox(
            "left", new OreBox(column[0], boxY, column[1], blockY + blockH - boxY), OreText.Type.PARAGRAPHS, words, this.theme.dimmest(), true, this.leftScroll
         );
         this.leftScroll = left.scroll().offset();
      }

      float[] rightColumn = narrow ? this.column(breakpoint, 0, 8) : this.column(breakpoint, 5, 7);
      OreDisconnectUi.ScrollBox right = this.scrollBox(
         "right",
         new OreBox(rightColumn[0], blockY, rightColumn[1], blockH),
         OreText.Type.CAPTION_LONG,
         this.data.errorDetails(),
         this.theme.text(),
         false,
         this.rightScroll
      );
      this.rightScroll = right.scroll().offset();
      return new OreDisconnectUi.Layout(
         OreDisconnectUi.Route.DETAILS, breakpoint, null, List.of(), null, null, null, List.of(), header, tab, left, right, widgets
      );
   }

   private OreDisconnectUi.ScrollBox scrollBox(String id, OreBox box, OreText.Type type, String words, int color, boolean spaced, float offset) {
      float b2 = this.text.scale().b2();
      OreBox viewport = box.inset(b2 + this.px(1.6F));
      float spacer = spaced ? this.mu(4) : 0.0F;
      List<String> lines = words.isEmpty() ? List.of() : this.text.wrap(type, words, viewport.w(), 400);
      float contentH = 2.0F * spacer + lines.size() * this.text.linePx(type);
      boolean overflow = contentH > viewport.h() + 0.5F;
      float textW = viewport.w();
      if (overflow) {
         textW = Math.max(0.0F, viewport.w() - this.px(1.6F));
         lines = words.isEmpty() ? List.of() : this.text.wrap(type, words, textW, 400);
         contentH = 2.0F * spacer + lines.size() * this.text.linePx(type);
      }

      OrePlayLayout.Scroll scroll = this.scroll(id, viewport, contentH, offset, overflow);
      List<OreDisconnectUi.TextBlock> content = lines.isEmpty()
         ? List.of()
         : List.of(
            new OreDisconnectUi.TextBlock(
               type, lines, new OreBox(viewport.x(), viewport.y() + spacer, textW, lines.size() * this.text.linePx(type)), OreDraw.Align.LEFT, color
            )
         );
      return new OreDisconnectUi.ScrollBox(id, box, viewport, content, scroll);
   }

   private OrePlayLayout.Scroll scroll(String id, OreBox viewport, float contentH, float offset, boolean overflow) {
      float max = Math.max(0.0F, contentH - viewport.h());
      float clamped = Math.clamp(offset, 0.0F, max);
      if (overflow && !(viewport.h() <= 0.0F)) {
         OreBox track = new OreBox(viewport.right() - this.px(1.2F), viewport.y() + this.px(0.8F), this.px(1.2F), Math.max(0.0F, viewport.h() - this.px(1.6F)));
         float thumbH = Math.max(this.px(2.4F), track.h() * viewport.h() / contentH);
         float thumbY = track.y() + (max <= 0.0F ? 0.0F : (track.h() - thumbH) * clamped / max);
         return new OrePlayLayout.Scroll(id, viewport, contentH, clamped, track, new OreBox(track.x(), thumbY, track.w(), thumbH));
      } else {
         return new OrePlayLayout.Scroll(id, viewport, contentH, clamped, null, null);
      }
   }

   public void render(OrePainter painter, long now) {
      OreDisconnectUi.Layout current = this.layout(this.width, this.height);
      OreDraw d = new OreDraw(painter, this.text, this.theme);
      float b2 = this.text.scale().b2();
      d.fill(new OreBox(0.0F, 0.0F, this.width, this.height), this.theme.overlay50());
      if (current.route() == OreDisconnectUi.Route.MAIN) {
         OreTheme.Role card = this.theme.neutral80();
         if (current.card() != null) {
            d.fill(current.card(), card.background());
            d.border(current.card(), card.border(), b2);
         }

         for (OreDisconnectUi.TextBlock block : current.texts()) {
            this.draw(d, block, 0.0F);
         }

         if (current.image() != null && current.imageSprite() != null) {
            d.sprite(current.imageSprite(), current.image(), -1);
         }

         if (current.messageBox() != null) {
            this.box(d, current.messageBox());
         }

         for (OreDisconnectUi.Button button : current.buttons()) {
            this.button(d, button, this.interaction.state(button.id()));
         }
      } else {
         this.header(d, current);
         if (current.tab() != null) {
            OreDisconnectUi.Tab tab = current.tab();
            d.fill(tab.label(), -7556097);
            d.border(tab.label(), -14803425, b2, true, true, false, true);
            d.fill(tab.strip(), -7556097);
            d.border(tab.strip(), -14803425, b2, true, true, false, false);
            this.draw(d, tab.text(), 0.0F);
         }

         if (current.left() != null) {
            this.box(d, current.left());
         }

         if (current.right() != null) {
            this.box(d, current.right());
         }
      }
   }

   private void header(OreDraw d, OreDisconnectUi.Layout current) {
      OrePlayLayout.Header header = current.header();
      if (header != null) {
         OreTheme.Role bar = this.theme.neutral20();
         d.fill(header.bar(), bar.background());
         d.fill(header.strip(), bar.shadow());
         d.fill(header.bevel(), bar.bevelBottom());
         OreComponents.barButton(d, header.back(), this.interaction.state("details-back"));
         d.sprite("arrowBack", header.arrow(), -1);
         String title = this.lang.get("hbui.ConnectionErrorRoute.details.header");
         d.textCentered(OreText.Type.HEADER5A, d.text().ellipsize(OreText.Type.HEADER5A, title, header.title().w()), header.title(), bar.text(), false);
      }
   }

   private void box(OreDraw d, OreDisconnectUi.ScrollBox box) {
      OreTheme.Role role = this.theme.neutral80();
      boolean words = box.id().equals("message");
      float b2 = this.text.scale().b2();
      d.fill(box.box(), words ? -14408667 : role.background());
      d.border(box.box(), words ? -14803425 : role.border(), b2);
      d.painter().pushClip(box.viewport());

      try {
         for (OreDisconnectUi.TextBlock block : box.content()) {
            this.draw(d, block, -box.scroll().offset());
         }
      } finally {
         d.painter().popClip();
      }

      OreComponents.scrollbar(d, box.scroll());
   }

   private void draw(OreDraw d, OreDisconnectUi.TextBlock block, float dy) {
      d.lines(block.type(), block.lines(), block.box().x(), block.box().w(), block.box().y() + dy, block.align(), block.color());
   }

   private void button(OreDraw d, OreDisconnectUi.Button button, OreComponents.State state) {
      OreTheme.Role role = this.theme.secondary();
      OreBox face = OreComponents.buttonFrame(d, button.box(), role, state);
      OreText.Type type = OreText.Type.SECONDARY_BUTTON;
      float lineH = this.text.linePx(type);
      float top = face.y() + (face.h() - button.lines().size() * lineH) / 2.0F;
      d.lines(type, button.lines(), face.x(), face.w(), top, OreDraw.Align.CENTER, role.text());
      if (state.focused() && state.enabled()) {
         d.focusRing(button.box(), role.outline(), false);
      }
   }

   private OreWidgets widgets() {
      return this.layout != null ? this.layout.widgets() : new OreWidgets();
   }

   public void mouseMoved(double x, double y) {
      this.interaction.moved(this.widgets(), x, y);
   }

   public void mousePressed(double x, double y) {
      if (this.interaction.press(this.widgets(), x, y) != null) {
         this.actions.click();
      }
   }

   public void mouseReleased(double x, double y) {
      String activated = this.interaction.release(this.widgets(), x, y);
      if (activated != null) {
         this.activate(activated);
      }
   }

   public boolean scrolled(double x, double y, double delta) {
      if (this.layout == null) {
         return false;
      }

      float step = this.px(4.8F) * (float)(-delta);

      for (OreDisconnectUi.ScrollBox box : new OreDisconnectUi.ScrollBox[]{this.layout.messageBox(), this.layout.left(), this.layout.right()}) {
         if (box != null && box.box().contains(x, y) && box.scroll().scrollable()) {
            switch (box.id()) {
               case "message":
                  this.messageScroll += step;
                  break;
               case "left":
                  this.leftScroll += step;
                  break;
               default:
                  this.rightScroll += step;
            }

            this.layout(this.width, this.height);
            return true;
         }
      }

      return false;
   }

   public boolean key(OreKey key, boolean shift) {
      switch (key) {
         case ESCAPE:
            this.actions.click();
            if (this.route == OreDisconnectUi.Route.DETAILS) {
               this.openRoute(OreDisconnectUi.Route.MAIN);
            } else {
               this.actions.back();
            }

            return true;
         case ENTER:
         case SPACE:
            String focused = this.interaction.focused();
            if (focused != null && this.widgets().get(focused) != null) {
               this.actions.click();
               this.activate(focused);
               return true;
            }

            return false;
         case TAB:
         case LEFT:
         case RIGHT:
         case UP:
         case DOWN:
            this.interaction.navigate(this.widgets(), key, shift);
            return true;
         default:
            return false;
      }
   }

   public void activate(String id) {
      switch (id) {
         case "back":
            this.actions.back();
            break;
         case "reconnect":
            this.actions.reconnect();
            break;
         case "details":
            this.openRoute(OreDisconnectUi.Route.DETAILS);
            break;
         case "details-back":
            this.openRoute(OreDisconnectUi.Route.MAIN);
      }
   }

   public void openRoute(OreDisconnectUi.Route next) {
      this.route = next;
      this.leftScroll = 0.0F;
      this.rightScroll = 0.0F;
      this.interaction.cancelPress();
      this.interaction.focus(null, false);
      if (this.width > 0.0F) {
         this.layout(this.width, this.height);
      }
   }

   public interface Actions {
      void back();

      void reconnect();

      void click();
   }

   public record Button(String id, OreBox box, List<String> lines) {
      OreDisconnectUi.Button offset(float dy) {
         return new OreDisconnectUi.Button(this.id, this.box.offset(0.0F, dy), this.lines);
      }
   }

   public record Layout(
      OreDisconnectUi.Route route,
      OreScale.Breakpoint breakpoint,
      @Nullable OreBox card,
      List<OreDisconnectUi.TextBlock> texts,
      @Nullable OreBox image,
      @Nullable String imageSprite,
      OreDisconnectUi.@Nullable ScrollBox messageBox,
      List<OreDisconnectUi.Button> buttons,
      OrePlayLayout.@Nullable Header header,
      OreDisconnectUi.@Nullable Tab tab,
      OreDisconnectUi.@Nullable ScrollBox left,
      OreDisconnectUi.@Nullable ScrollBox right,
      OreWidgets widgets
   ) {
      public OreDisconnectUi.@Nullable Button button(String id) {
         for (OreDisconnectUi.Button button : this.buttons) {
            if (button.id().equals(id)) {
               return button;
            }
         }

         return null;
      }
   }

   public enum Route {
      MAIN,
      DETAILS;
   }

   public record ScrollBox(String id, OreBox box, OreBox viewport, List<OreDisconnectUi.TextBlock> content, OrePlayLayout.Scroll scroll) {
   }

   public record Tab(OreBox label, OreBox strip, OreDisconnectUi.TextBlock text) {
   }

   public record TextBlock(OreText.Type type, List<String> lines, OreBox box, OreDraw.Align align, int color) {
      OreDisconnectUi.TextBlock offset(float dy) {
         return new OreDisconnectUi.TextBlock(this.type, this.lines, this.box.offset(0.0F, dy), this.align, this.color);
      }
   }
}
