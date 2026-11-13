package net.bedrocknative.oreui;

import java.util.ArrayList;
import java.util.List;
import net.bedrocknative.net.BedrockAddress;
import net.bedrocknative.ui.PlayData;
import org.jspecify.annotations.Nullable;

public final class OrePlayLayout {
   public static final int WORLDS_PER_PAGE = 12;
   public static final int MAX_PAGE_CELLS = 7;
   public static final String PAGE_SKIP = "...";
   private final OreScale scale;
   private final OreText text;
   private final OreLang lang;
   private final float width;
   private final float height;
   private static final String[] TAB_ICONS = new String[]{"UI_Menu_WorldsTab", "UI_Menu_RealmsTab", "UI_Menu_ServerTab"};

   public OrePlayLayout(OreText text, OreLang lang, float width, float height) {
      this.scale = text.scale();
      this.text = text;
      this.lang = lang;
      this.width = width;
      this.height = height;
   }

   private float px(float rem) {
      return this.scale.px(rem);
   }

   public static float spacer(int size) {
      return switch (size) {
         case 1 -> 0.4F;
         case 2 -> 0.8F;
         case 3 -> 1.2F;
         case 4 -> 1.6F;
         case 5 -> 2.0F;
         case 6 -> 2.4F;
         case 7 -> 3.2F;
         case 8 -> 6.4F;
         default -> 0.0F;
      };
   }

   private float mu(int size) {
      return this.px(spacer(size));
   }

   public OrePlayLayout.Frame layout(OrePlayModel model, OrePlayLayout.State state) {
      OreScale.Breakpoint breakpoint = this.scale.breakpoint(this.width);
      OreWidgets widgets = new OreWidgets();
      OrePlayLayout.Header header = this.header(widgets);
      float headerBottom = this.px(4.4F) + 2.0F * this.scale.b2();
      float pad = breakpoint == OreScale.Breakpoint.DESKTOP ? this.px(2.4F) : (breakpoint == OreScale.Breakpoint.TABLET ? this.px(1.6F) : this.px(0.8F));
      float gutter = breakpoint == OreScale.Breakpoint.DESKTOP ? this.px(0.8F) : (breakpoint == OreScale.Breakpoint.TABLET ? this.px(0.4F) : this.scale.b2());
      float inner = this.width - 2.0F * pad;
      float rowWidth = Math.min(inner, this.px(128.0F));
      float marginBox = rowWidth - 2.0F * gutter;
      float rowX = pad + (inner - marginBox) / 2.0F - gutter;
      float colX0 = rowX + gutter;
      float colX1 = rowX + rowWidth - gutter;
      float barTop = headerBottom + this.mu(2);
      OreBox tabBar = new OreBox(colX0, barTop, colX1 - colX0 - this.scale.b2(), this.px(4.8F));
      List<OrePlayLayout.Tab> tabs = this.tabs(tabBar, state.tab(), model, widgets);
      float hint = this.px(2.4F);
      OreBox leftHint = new OreBox(tabBar.x() - this.px(1.2F) + this.scale.b2(), barTop + 2.0F * this.scale.b2() + this.px(0.8F), hint, hint);
      OreBox rightHint = new OreBox(tabBar.right() + this.px(1.2F) - hint, barTop + 2.0F * this.scale.b2() + this.px(0.8F), hint, hint);
      float contentTop = barTop + this.px(4.8F);
      float bottom = this.height - (this.mu(2) + this.px(4.8F) + this.mu(2));
      String profileText = this.lang.get("menu.profile");
      OrePlayLayout.Button profile = new OrePlayLayout.Button(
         "profile",
         new OreBox(this.mu(1), this.height - this.px(4.8F) - this.mu(1), Math.min(this.px(20.0F), (colX1 - colX0) / 3.0F), this.px(4.8F)),
         profileText.equals("menu.profile") ? "Profile" : profileText,
         OreTheme.Variant.SECONDARY,
         null,
         false
      );
      widgets.add(new OreWidget(profile.id(), profile.box(), profile.box(), true, false));
      OreBox contentArea = new OreBox(colX0, contentTop, colX1 - colX0, Math.max(0.0F, bottom - contentTop));
      OreBox toggle = null;
      List<OrePlayLayout.Button> headerButtons = List.of();
      OrePlayLayout.Scroll worldScroll = null;
      List<OrePlayLayout.WorldCard> cards = List.of();
      List<OrePlayLayout.WorldRow> rows = List.of();
      int columns = 0;
      int worldPage = 1;
      int worldPages = 1;
      OrePlayLayout.Pagination pagination = null;
      OrePlayLayout.Notice emptyWorlds = null;
      OrePlayLayout.Notice realms = null;
      OrePlayLayout.Scroll menuScroll = null;
      OreBox menuPanel = null;
      OrePlayLayout.Button addServer = null;
      List<OrePlayLayout.Section> sections = List.of();
      OrePlayLayout.Scroll detailsScroll = null;
      OrePlayLayout.SavedDetails saved = null;
      OrePlayLayout.FeaturedDetails featured = null;
      switch (state.tab()) {
         case 0:
            OreBox viewport = new OreBox(colX0, contentTop, colX1 - colX0, Math.max(0.0F, bottom - contentTop));
            float headerH = this.mu(2) + this.px(4.8F) + this.mu(2);
            boolean empty = model.worldsLoaded() && model.worldCount() == 0;
            if (empty) {
               float contentH = headerH + this.emptyWorlds(viewport, 0.0F).card().h();
               OrePlayLayout.Scroll scroll = this.scroll("worlds", viewport, contentH, state.worldScroll(), contentH > viewport.h());
               float y0 = viewport.y() - scroll.offset();
               headerButtons = this.headerButtons(colX0, colX1, y0 + this.mu(2), breakpoint, viewport, widgets);
               emptyWorlds = this.emptyWorlds(viewport, y0 + headerH);
               worldScroll = scroll;
            } else {
               int count = model.lan().size() + model.worlds().size();
               worldPages = Math.max(1, (count + 12 - 1) / 12);
               worldPage = Math.clamp((long)state.worldPage(), 1, worldPages);
               int from = (worldPage - 1) * 12;
               int to = Math.min(count, from + 12);
               columns = this.worldColumns(viewport.w());
               boolean list = state.listLayout() || columns == 1;
               float pagesH = worldPages > 1 ? this.px(0.4F) + this.px(4.4F) + 2.0F * this.scale.b2() : 0.0F;
               float contentW = viewport.w();
               float contentH = headerH + this.worldsHeight(list, columns, contentW, to - from) + this.mu(3) + pagesH;
               boolean overflow = contentH > viewport.h();
               if (overflow) {
                  contentW -= this.px(1.6F);
                  contentH = headerH + this.worldsHeight(list, columns, contentW, to - from) + this.mu(3) + pagesH;
               }

               OrePlayLayout.Scroll scroll = this.scroll("worlds", viewport, contentH, state.worldScroll(), overflow);
               float y0 = viewport.y() - scroll.offset();
               toggle = new OreBox(colX0, y0 + this.mu(2), this.px(4.8F), this.px(4.8F));
               widgets.add(OreWidget.clipped("toggle-layout", toggle, viewport));
               headerButtons = this.headerButtons(toggle.right(), colX0 + contentW, y0 + this.mu(2), breakpoint, viewport, widgets);
               float worldsTop = y0 + headerH;
               if (list) {
                  rows = this.worldList(model, viewport, colX0, worldsTop, contentW, from, to, widgets);
               } else {
                  cards = this.worldGrid(model, viewport, colX0, worldsTop, contentW, columns, from, to, widgets);
               }

               if (worldPages > 1) {
                  float pagesTop = worldsTop + this.worldsHeight(list, columns, contentW, to - from) + this.mu(3);
                  pagination = this.pagination(colX0, contentW, pagesTop, worldPage, worldPages, viewport, widgets);
               }

               worldScroll = scroll;
            }
            break;
         case 1:
            realms = this.realmsNotice(new OreBox(colX0, contentTop + this.mu(2), colX1 - colX0, Math.max(0.0F, bottom - contentTop)));
            break;
         default:
            float nestedX = colX0 - gutter;
            float nestedW = colX1 - colX0;
            boolean narrow = breakpoint == OreScale.Breakpoint.NARROW;
            float leftW = nestedW * (narrow ? 0.375F : 0.33333334F);
            OreBox left = new OreBox(nestedX + gutter, contentTop, leftW - 2.0F * gutter, Math.max(0.0F, bottom - contentTop));
            OreBox right = new OreBox(nestedX + leftW + gutter, contentTop, nestedW - leftW - 2.0F * gutter, Math.max(0.0F, bottom - contentTop));
            OrePlayLayout.SideMenu menu = this.sideMenu(model, state, left, breakpoint, widgets);
            menuScroll = menu.scroll;
            menuPanel = menu.panel;
            addServer = menu.add;
            sections = menu.sections;
            OrePlayLayout.Details details = this.details(model, state, right, breakpoint, widgets);
            detailsScroll = details.scroll;
            saved = details.saved;
            featured = details.featured;
      }

      return new OrePlayLayout.Frame(
         this.scale,
         breakpoint,
         this.width,
         this.height,
         header,
         tabBar,
         tabs,
         leftHint,
         rightHint,
         contentArea,
         state.tab(),
         toggle,
         headerButtons,
         worldScroll,
         cards,
         rows,
         columns,
         worldPage,
         worldPages,
         pagination,
         emptyWorlds,
         realms,
         menuScroll,
         menuPanel,
         addServer,
         sections,
         detailsScroll,
         saved,
         featured,
         profile,
         widgets
      );
   }

   private OrePlayLayout.Header header(OreWidgets widgets) {
      float barH = this.px(4.4F);
      float b2 = this.scale.b2();
      OreBox bar = new OreBox(0.0F, 0.0F, this.width, barH);
      OreBox strip = new OreBox(0.0F, barH, this.width, 2.0F * b2);
      OreBox bevel = new OreBox(0.0F, barH + 2.0F * b2, this.width, b2);
      OreBox back = new OreBox(this.px(0.2F), (barH - this.px(4.0F)) / 2.0F, this.px(4.0F), this.px(4.0F));
      float arrowW = 4.0F * b2;
      float arrowH = 8.0F * b2;
      OreBox arrow = new OreBox(back.x() + (back.w() - arrowW) / 2.0F, back.y() + (back.h() - arrowH - b2) / 2.0F + b2, arrowW, arrowH);
      OreBox title = new OreBox(this.px(6.0F), 0.0F, Math.max(0.0F, this.width - this.px(12.0F)), barH);
      String label = this.lang.get("hbui.NavigationBarLayout.FriendsButton.social") + " (0)";
      float icon = this.px(OreIcon.FRIENDS.elementRem(null));
      float labelW = this.text.width(OreText.Type.BODY, label);
      float socialW = this.px(2.0F) + icon + this.px(0.4F) + labelW + this.px(0.4F) + this.px(0.8F) + this.px(0.8F);
      OreBox social = new OreBox(this.width - this.px(0.2F) - socialW, (barH - this.px(4.0F)) / 2.0F, socialW, this.px(4.0F));
      OreBox socialIcon = new OreBox(social.x() + this.px(2.0F), social.y() + (social.h() - icon) / 2.0F, icon, icon);
      OreBox socialText = new OreBox(socialIcon.right() + this.px(0.4F), social.y(), labelW, social.h());
      OreBox divider = new OreBox(social.x() - this.px(0.4F), 0.0F, this.px(0.4F), barH);
      widgets.add(OreWidget.of("back", back));
      widgets.add(OreWidget.of("social", social));
      return new OrePlayLayout.Header(bar, strip, bevel, back, arrow, title, divider, social, socialIcon, socialText, label);
   }

   public static String tabIcon(int tab) {
      return TAB_ICONS[Math.clamp((long)tab, 0, 2)];
   }

   private List<OrePlayLayout.Tab> tabs(OreBox bar, int selected, OrePlayModel model, OreWidgets widgets) {
      String[] labels = new String[]{
         this.lang.get("hbui.PlayScreen.allWorlds", model.worldCount()), this.lang.get("hbui.PlayScreen.realms"), this.lang.get("hbui.PlayScreen.servers")
      };
      List<OrePlayLayout.Tab> tabs = new ArrayList<>();
      int n = labels.length;
      float b2 = this.scale.b2();
      float tabW = (bar.w() + n * b2) / n;

      for (int i = 0; i < n; i++) {
         boolean on = i == selected;
         float x = bar.x() + i * (tabW - b2);
         OreBox box = on ? new OreBox(x, bar.y() + this.px(0.4F), tabW, this.px(4.4F)) : new OreBox(x, bar.y(), tabW, this.px(4.8F));
         OreBox content = on ? box.inset(this.px(0.4F)) : box.inset(this.px(0.4F), this.px(0.4F), this.px(0.4F), this.px(0.8F));
         float icon = 24.0F * this.scale.b1();
         float room = Math.max(0.0F, content.w() - 2.0F * this.px(2.0F) - icon - this.mu(2));
         String label = this.text.ellipsize(OreText.Type.BODY, labels[i], room);
         float labelW = this.text.width(OreText.Type.BODY, label);
         float rowW = icon + this.mu(2) + labelW;
         float rowX = content.x() + (content.w() - rowW) / 2.0F;
         OreBox iconBox = new OreBox(rowX, content.y() + (content.h() - icon) / 2.0F, icon, icon);
         OreBox labelBox = new OreBox(iconBox.right() + this.mu(2), content.y(), labelW, content.h());
         OreBox indicator = new OreBox(box.centerX() - this.px(2.4F), box.bottom() - this.px(0.4F) + this.px(0.2F) - b2, this.px(4.8F), b2);
         tabs.add(new OrePlayLayout.Tab(i, box, on, iconBox, labelBox, label, indicator));
         widgets.add(OreWidget.of("tab:" + i, box));
      }

      return tabs;
   }

   private List<OrePlayLayout.Button> headerButtons(float x0, float x1, float top, OreScale.Breakpoint breakpoint, OreBox viewport, OreWidgets widgets) {
      String create = this.lang.get("hbui.PlayScreen.AllWorldsTab.ButtonHeader.createNewWorld");
      String importWorld = this.lang.get("hbui.PlayScreen.AllWorldsTab.ButtonHeader.importWorld");
      float gap = breakpoint == OreScale.Breakpoint.NARROW ? this.mu(2) : this.mu(4);
      float room = x1 - x0;
      float each = Math.min(this.px(25.6F), Math.max(0.0F, (room - gap) / 2.0F));
      float importX = x1 - each;
      float createX = importX - gap - each;
      OrePlayLayout.Button createButton = new OrePlayLayout.Button(
         "create-world", new OreBox(createX, top, each, this.px(4.8F)), create, OreTheme.Variant.PRIMARY, null, false
      );
      OrePlayLayout.Button importButton = new OrePlayLayout.Button(
         "import-world", new OreBox(importX, top, each, this.px(4.8F)), importWorld, OreTheme.Variant.SECONDARY, OreIcon.IMPORT_WORLD, false
      );
      widgets.add(OreWidget.clipped(createButton.id(), createButton.box(), viewport));
      widgets.add(OreWidget.clipped(importButton.id(), importButton.box(), viewport));
      return List.of(createButton, importButton);
   }

   int worldColumns(float viewportWidth) {
      float width = viewportWidth - this.px(0.8F);
      if (width >= this.px(columnsWidth(4))) {
         return 4;
      } else if (width >= this.px(columnsWidth(3))) {
         return 3;
      } else {
         return width >= this.px(columnsWidth(2) + 12.5F) ? 2 : 1;
      }
   }

   private static float columnsWidth(int columns) {
      return 25 * columns + 1.6F * (columns - 1);
   }

   private float worldsHeight(boolean list, int columns, float contentW, int n) {
      if (n <= 0) {
         return 0.0F;
      } else if (list) {
         return n * (this.listRowHeight() + this.px(0.8F)) - this.px(0.8F);
      } else {
         int rows = (n + columns - 1) / columns;
         return rows * this.gridRowHeight(contentW, columns) - this.px(1.6F);
      }
   }

   private float gridRowHeight(float contentW, int columns) {
      float b2 = this.scale.b2();
      float cardW = (contentW + 2.0F * this.px(0.8F)) / columns - 2.0F * this.px(0.8F);
      float imageH = (cardW - 2.0F * b2) * 9.0F / 16.0F;
      return b2 + imageH + this.px(6.8F) - b2 + this.px(1.6F);
   }

   private List<OrePlayLayout.WorldCard> worldGrid(
      OrePlayModel model, OreBox viewport, float x0, float top, float contentW, int columns, int from, int to, OreWidgets widgets
   ) {
      float itemW = (contentW + 2.0F * this.px(0.8F)) / columns;
      float cardW = itemW - 2.0F * this.px(0.8F);
      float b2 = this.scale.b2();
      float imageW = cardW - 2.0F * b2;
      float imageH = imageW * 9.0F / 16.0F;
      float rowH = this.gridRowHeight(contentW, columns);
      List<OrePlayLayout.WorldCard> cards = new ArrayList<>();

      for (int i = from; i < to; i++) {
         int slot = i - from;
         int row = slot / columns;
         int column = slot % columns;
         float x = x0 - this.px(0.8F) + column * itemW + this.px(0.8F);
         float y = top + row * rowH;
         boolean lan = i < model.lan().size();
         OrePlayLayout.WorldCard card;
         if (lan) {
            PlayData.Lan game = model.lan().get(i);
            card = this.card(
               i,
               true,
               x,
               y,
               cardW,
               imageW,
               imageH,
               game.name(),
               game.world(),
               List.of(),
               List.of(new String[]{this.lang.get("hbui.WorldTag.lanWorld"), OrePlayLayout.TagRole.INFORMATIVE_TINT.name()}),
               game.online() + "/" + game.max()
            );
         } else {
            OrePlayModel.World world = model.worlds().get(i - model.lan().size());
            OrePlayLayout.TagRole role = world.modeTag().endsWith(".hardcore") ? OrePlayLayout.TagRole.DESTRUCTIVE_TINT : OrePlayLayout.TagRole.NEUTRAL80;
            card = this.card(
               i,
               false,
               x,
               y,
               cardW,
               imageW,
               imageH,
               world.name(),
               world.date(),
               List.of(new String[]{this.lang.get(world.modeTag()), role.name()}),
               List.of(),
               ""
            );
         }

         cards.add(card);
         widgets.add(OreWidget.clipped("world:" + i, card.action(), viewport));
         widgets.add(new OreWidget("world-image:" + i, card.imageBox(), card.imageBox().intersect(viewport), true, false));
         if (!lan) {
            widgets.add(OreWidget.clipped("world-edit:" + i, card.edit(), viewport));
         }
      }

      return cards;
   }

   private OrePlayLayout.WorldCard card(
      int index,
      boolean lan,
      float x,
      float y,
      float cardW,
      float imageW,
      float imageH,
      String title,
      String subtitle,
      List<String[]> bottom,
      List<String[]> top,
      String players
   ) {
      float b2 = this.scale.b2();
      OreBox card = new OreBox(x, y, cardW, b2 + imageH + this.px(6.8F));
      OreBox imageBox = new OreBox(x, y, cardW, b2 + imageH);
      OreBox image = new OreBox(x + b2, y + b2, imageW, imageH);
      List<OrePlayLayout.Tag> bottomTags = this.tags(bottom, image.x(), image.bottom() - this.px(2.0F), imageW);
      List<OrePlayLayout.Tag> topTags = this.tags(top, image.x(), image.y(), imageW);
      OreBox bottomFrame = bottomTags.isEmpty()
         ? null
         : new OreBox(image.x(), image.bottom() - this.px(2.0F) - b2, bottomTags.getLast().box().right() + b2 - image.x(), this.px(2.0F) + b2);
      OreBox topFrame = topTags.isEmpty() ? null : new OreBox(image.x(), image.y(), topTags.getLast().box().right() + b2 - image.x(), this.px(2.0F) + b2);
      OreBox info = new OreBox(x, image.bottom(), cardW, this.px(6.8F));
      OreBox infoInner = info.inset(b2);
      float editW = lan ? 0.0F : this.px(6.0F) + 2.0F * b2;
      OreBox action = new OreBox(infoInner.x(), infoInner.y(), infoInner.w() - editW, infoInner.h());
      OreBox edit = new OreBox(action.right(), infoInner.y(), editW, infoInner.h());
      float icon = this.px(2.4F);
      OreBox editIcon = new OreBox(edit.x() + (edit.w() - icon) / 2.0F, edit.y() + (edit.h() - icon) / 2.0F, icon, icon);
      OreBox textArea = action.inset(b2 + this.px(0.6F));
      float playersW = players.isEmpty() ? 0.0F : this.px(2.4F) + this.px(0.4F) + this.text.width(OreText.Type.BODY, players);
      float textW = Math.max(0.0F, textArea.w() - playersW);
      float lines = subtitle.isEmpty() ? 1.0F : 2.0F;
      float linesTop = textArea.y() + (textArea.h() - lines * this.px(2.0F)) / 2.0F;
      String titleText = this.text.ellipsize(OreText.Type.BODY, title, textW);
      String subtitleText = this.text.ellipsize(OreText.Type.BODY, subtitle, textW);
      OreBox titleBox = new OreBox(textArea.x(), linesTop, textW, this.px(2.0F));
      OreBox subtitleBox = new OreBox(textArea.x(), linesTop + this.px(2.0F), textW, this.px(2.0F));
      OreBox playersBox = players.isEmpty() ? null : new OreBox(textArea.right() - playersW, textArea.y(), playersW, textArea.h());
      return new OrePlayLayout.WorldCard(
         index,
         lan,
         card,
         imageBox,
         image,
         topFrame,
         topTags,
         bottomFrame,
         bottomTags,
         info,
         action,
         edit,
         editIcon,
         titleBox,
         subtitleBox,
         titleText,
         subtitleText,
         playersBox,
         players
      );
   }

   private List<OrePlayLayout.Tag> tags(List<String[]> words, float x, float y, float maxW) {
      List<OrePlayLayout.Tag> tags = new ArrayList<>();
      float cursor = x;

      for (String[] tag : words) {
         if (!tag[0].isEmpty()) {
            float room = x + maxW - cursor - this.scale.b2();
            if (room <= this.px(0.8F)) {
               break;
            }

            String shown = this.text.ellipsize(OreText.Type.BODY, tag[0], room - this.px(0.8F));
            float w = Math.min(room, this.px(0.4F) + this.text.width(OreText.Type.BODY, shown) + this.px(0.4F));
            tags.add(new OrePlayLayout.Tag(new OreBox(cursor, y, w, this.px(2.0F)), shown, OrePlayLayout.TagRole.valueOf(tag[1])));
            cursor += w + this.scale.b2();
         }
      }

      return tags;
   }

   private float listRowHeight() {
      return Math.max(this.px(8.4F) + 4.0F * this.scale.b2(), this.rowContentHeight());
   }

   private List<OrePlayLayout.WorldRow> worldList(OrePlayModel model, OreBox viewport, float x0, float top, float width, int from, int to, OreWidgets widgets) {
      float rowH = this.listRowHeight();
      List<OrePlayLayout.WorldRow> rows = new ArrayList<>();

      for (int i = from; i < to; i++) {
         float y = top + (i - from) * (rowH + this.px(0.8F));
         boolean lan = i < model.lan().size();
         OrePlayLayout.WorldRow row;
         if (lan) {
            PlayData.Lan game = model.lan().get(i);
            row = this.worldRow(i, true, x0, y, width, rowH, game.name(), this.lang.get("hbui.WorldTag.lan"), List.of(), game.online() + "/" + game.max());
         } else {
            OrePlayModel.World world = model.worlds().get(i - model.lan().size());
            List<String> meta = new ArrayList<>();
            if (!world.size().isEmpty()) {
               meta.add(world.size());
            }

            if (!world.date().isEmpty()) {
               meta.add(world.date());
            }

            row = this.worldRow(i, false, x0, y, width, rowH, world.name(), this.lang.get(world.modeTag()), meta, "");
         }

         rows.add(row);
         widgets.add(OreWidget.clipped("world:" + i, row.action(), viewport));
         if (!lan) {
            widgets.add(OreWidget.clipped("world-edit:" + i, row.edit(), viewport));
         }
      }

      return rows;
   }

   private OrePlayLayout.Pagination pagination(float x0, float contentW, float top, int page, int pages, OreBox viewport, OreWidgets widgets) {
      float b2 = this.scale.b2();
      int count = Math.min(pages, 7);
      float buttonW = this.px(4.8F);
      float buttonH = this.px(4.4F) + 2.0F * b2;
      float frameW = count * this.px(4.0F) + 2.0F * b2;
      float rowW = buttonW + this.mu(4) + frameW + this.mu(4) + buttonW;
      float x = x0 + (contentW - rowW) / 2.0F;
      OreBox previous = new OreBox(x, top + this.px(0.4F), buttonW, buttonH);
      OreBox frame = new OreBox(previous.right() + this.mu(4), top + b2, frameW, this.px(4.0F) + 2.0F * b2);
      OreBox next = new OreBox(frame.right() + this.mu(4), top + this.px(0.4F), buttonW, buttonH);
      OreBox previousArrow = this.arrow(previous, 0.0F);
      OreBox nextArrow = this.arrow(next, this.px(0.4F));
      boolean previousEnabled = page > 1;
      boolean nextEnabled = page < pages;
      if (previousEnabled) {
         widgets.add(OreWidget.clipped("page-prev", previous, viewport));
      }

      List<OrePlayLayout.PageCell> cells = new ArrayList<>();

      for (int i = 0; i < count; i++) {
         String label = pageLabel(pages, page, i, 7);
         OreBox cell = new OreBox(frame.x() + b2 + i * this.px(4.0F), frame.y() + b2, this.px(4.0F), this.px(4.0F));
         OreBox underline = new OreBox(cell.centerX() - this.px(1.0F), cell.bottom() - b2, this.px(2.0F), b2);
         OrePlayLayout.PageCell pageCell = new OrePlayLayout.PageCell(
            "page:" + i, cell, label, pageTarget(label, i, page, pages, 7), label.equals(String.valueOf(page)), underline
         );
         cells.add(pageCell);
         widgets.add(OreWidget.clipped(pageCell.id(), cell, viewport));
      }

      if (nextEnabled) {
         widgets.add(OreWidget.clipped("page-next", next, viewport));
      }

      return new OrePlayLayout.Pagination(page, pages, previous, previousArrow, previousEnabled, next, nextArrow, nextEnabled, frame, cells);
   }

   private OreBox arrow(OreBox button, float marginBottom) {
      float b2 = this.scale.b2();
      float faceTop = button.y() + b2;
      float block = b2 + 8.0F * b2 + marginBottom;
      return new OreBox(button.centerX() - 2.0F * b2, faceTop + (this.px(4.0F) - block) / 2.0F + b2, 4.0F * b2, 8.0F * b2);
   }

   public static String pageLabel(int pages, int page, int index, int cells) {
      String plain = String.valueOf(index + 1);
      if (index == 0) {
         return plain;
      } else if (index == cells - 1) {
         return String.valueOf(pages);
      } else if (pages <= cells) {
         return plain;
      } else {
         int middle = cells - 4;
         int half = middle / 2;
         int before = middle % 2 == 0 ? half - 1 : half;
         boolean skipBefore = page > before + 3;
         boolean skipAfter = page < pages - half - 2;
         if ((index != 1 || !skipBefore) && (index != cells - 2 || !skipAfter)) {
            if (skipBefore && skipAfter) {
               return String.valueOf(page + index - before - 2);
            } else {
               return skipBefore ? String.valueOf(pages - cells + index + 1) : plain;
            }
         } else {
            return "...";
         }
      }
   }

   public static int pageTarget(String label, int index, int page, int pages, int cells) {
      if ("...".equals(label)) {
         return index == 1 ? Math.max(page - cells + 2, 1) : Math.min(page + cells - 2, pages);
      } else {
         return Integer.parseInt(label);
      }
   }

   private float rowContentHeight() {
      float b2 = this.scale.b2();
      float picture = (this.px(12.0F) - 2.0F * b2) * 9.0F / 16.0F + 2.0F * b2;
      return picture + 2.0F * this.px(0.6F) + 4.0F * b2;
   }

   private OrePlayLayout.WorldRow worldRow(
      int index, boolean lan, float x, float y, float w, float h, String title, String tag, List<String> meta, String players
   ) {
      float b2 = this.scale.b2();
      OreBox row = new OreBox(x, y, w, h);
      OreBox inner = row.inset(b2);
      float editW = lan ? 0.0F : this.px(6.8F);
      OreBox action = new OreBox(inner.x(), inner.y(), inner.w() - editW, inner.h());
      OreBox edit = new OreBox(action.right(), inner.y(), editW, inner.h());
      float icon = this.px(2.4F);
      OreBox editIcon = new OreBox(edit.x() + (edit.w() - icon) / 2.0F, edit.y() + (edit.h() - icon) / 2.0F, icon, icon);
      OreBox content = action.inset(b2 + this.px(0.6F), b2 + this.px(0.6F), b2 + this.px(1.0F), b2 + this.px(0.6F));
      float pictureH = (this.px(12.0F) - 2.0F * b2) * 9.0F / 16.0F + 2.0F * b2;
      OreBox pictureBox = new OreBox(content.x(), content.y() + (content.h() - pictureH) / 2.0F, this.px(12.0F), pictureH);
      OreBox image = pictureBox.inset(b2);
      float metaW = 0.0F;
      List<String> metaLines = new ArrayList<>();

      for (String line : meta) {
         metaW = Math.max(metaW, this.text.width(OreText.Type.CAPTION_SHORT, line));
         metaLines.add(line);
      }

      float playersW = players.isEmpty() ? 0.0F : this.px(2.4F) + this.mu(1) + this.text.width(OreText.Type.BODY, players);
      OreBox metaBox = new OreBox(
         content.right() - metaW - playersW, content.y() + (content.h() - metaLines.size() * this.px(2.0F)) / 2.0F, metaW, metaLines.size() * this.px(2.0F)
      );
      OreBox playersBox = players.isEmpty()
         ? null
         : new OreBox(content.right() - playersW, content.y() + (content.h() - this.px(2.0F)) / 2.0F, playersW, this.px(2.0F));
      float mainX = pictureBox.right() + this.px(0.8F);
      float mainW = Math.max(0.0F, metaBox.x() - this.px(0.8F) - mainX);
      float mainH = this.px(2.0F) + (tag.isEmpty() ? 0.0F : this.px(0.4F) + this.px(2.0F));
      float mainY = content.y() + (content.h() - mainH) / 2.0F;
      OreBox titleBox = new OreBox(mainX, mainY, mainW, this.px(2.0F));
      OreBox tagBox = null;
      if (!tag.isEmpty()) {
         float tagW = Math.min(mainW, this.px(0.4F) + this.text.width(OreText.Type.BODY, tag) + this.px(0.4F));
         tagBox = new OreBox(mainX, titleBox.bottom() + this.px(0.4F), tagW, this.px(2.0F));
      }

      return new OrePlayLayout.WorldRow(
         index,
         lan,
         row,
         action,
         pictureBox,
         image,
         titleBox,
         this.text.ellipsize(OreText.Type.BODY, title, mainW),
         tagBox,
         tag,
         metaBox,
         metaLines,
         edit,
         editIcon,
         playersBox,
         players
      );
   }

   private OrePlayLayout.Notice emptyWorlds(OreBox viewport, float y) {
      return this.notice(
         viewport.x(),
         y,
         viewport.w(),
         this.lang.get("hbui.PlayScreen.allWorlds.emptyStateTitle"),
         "no-worlds-yet-art",
         this.lang.get("hbui.PlayScreen.allWorlds.emptyStateText.trialMode")
      );
   }

   OrePlayLayout.Notice notice(float x, float y, float w, String title, @Nullable String art, String body) {
      float b2 = this.scale.b2();
      float pad = this.px(1.6F);
      float innerX = x + b2 + pad;
      float innerW = Math.max(0.0F, w - 2.0F * (b2 + pad));
      float cursor = y + b2 + pad;
      OreBox titleBox = new OreBox(innerX, cursor, innerW, this.px(2.0F));
      cursor += this.px(2.0F);
      OreBox artBox = null;
      if (art != null) {
         float artW = 224.0F * this.scale.b1();
         float artH = 96.0F * this.scale.b1();
         cursor += this.mu(4);
         artBox = new OreBox(innerX + (innerW - artW) / 2.0F, cursor, artW, artH);
         cursor += artH + this.mu(4);
      }

      List<String> lines = this.text.wrap(OreText.Type.CAPTION_SHORT, body, innerW, 6);
      OreBox textBox = new OreBox(innerX, cursor, innerW, lines.size() * this.px(2.0F));
      cursor += textBox.h() + pad + b2;
      return new OrePlayLayout.Notice(new OreBox(x, y, w, cursor - y), titleBox, title, artBox, art, lines, textBox);
   }

   private OrePlayLayout.Notice realmsNotice(OreBox area) {
      String title = this.lang.get("hbui.PlayScreen.realmsTab.realmsTabErrorStates.realmsServiceError.title");
      String body = this.lang.get("bedrocknative.ore.realmsUnavailable");
      float w = Math.min(area.w(), this.px(64.0F));
      return this.notice(area.x() + (area.w() - w) / 2.0F, area.y(), w, title, null, body);
   }

   private OrePlayLayout.SideMenu sideMenu(OrePlayModel model, OrePlayLayout.State state, OreBox column, OreScale.Breakpoint breakpoint, OreWidgets widgets) {
      float b2 = this.scale.b2();
      boolean narrow = breakpoint == OreScale.Breakpoint.NARROW;
      float top = column.y() + this.mu(2);
      OreBox viewport = new OreBox(column.x(), top, column.w(), Math.max(0.0F, column.bottom() - top));
      float inset = narrow ? this.mu(2) : this.mu(4);
      List<OrePlayLayout.PendingSection> pending = new ArrayList<>();
      if (!model.featured().isEmpty()) {
         List<String[]> items = new ArrayList<>();

         for (OrePlayModel.Featured partner : model.featured()) {
            items.add(
               new String[]{
                  "featured:" + items.size(),
                  partner.server().name(),
                  partner.server().message(),
                  partner.server().sponsored() ? "sponsored" : "",
                  partner.server().icon() != null ? "icon" : ""
               }
            );
         }

         pending.add(new OrePlayLayout.PendingSection(this.lang.get("hbui.PlayScreen.serverTab.featuredServer", model.featured().size()), items, true));
      }

      if (!model.servers().isEmpty()) {
         List<String[]> items = new ArrayList<>();

         for (PlayData.Server server : model.servers()) {
            items.add(new String[]{"server:" + items.size(), server.name(), this.serverMessage(server.status())});
         }

         pending.add(new OrePlayLayout.PendingSection(this.lang.get("hbui.PlayScreen.serverTab.externalServer", model.servers().size()), items, false));
      }

      float contentH = b2 + inset + this.px(4.8F);

      for (OrePlayLayout.PendingSection section : pending) {
         contentH += this.px(4.8F) + b2;

         for (String[] item : section.items) {
            contentH += section.subtitles ? this.featuredItemHeight(item) : this.px(4.8F);
         }
      }

      contentH += inset + b2;
      boolean overflow = contentH > viewport.h();
      OrePlayLayout.Scroll scroll = this.scroll("menu", viewport, contentH, state.menuScroll(), overflow);
      boolean desktop = breakpoint == OreScale.Breakpoint.DESKTOP;
      float panelW = viewport.w() - (overflow && desktop ? this.px(1.6F) : 0.0F);
      float y = viewport.y() - scroll.offset();
      OreBox panel = new OreBox(viewport.x(), y, panelW, contentH);
      float cursor = y + b2 + inset;
      float itemPad = this.mu(4);
      OrePlayLayout.Button add = new OrePlayLayout.Button(
         "add-server",
         new OreBox(panel.x() + b2 + itemPad, cursor, panelW - 2.0F * b2 - 2.0F * itemPad, this.px(4.8F)),
         this.lang.get("hbui.PlayScreen.serverTab.addServer"),
         OreTheme.Variant.SECONDARY,
         OreIcon.PLUS,
         false
      );
      widgets.add(OreWidget.clipped(add.id(), add.box(), viewport));
      cursor += this.px(4.8F);
      List<OrePlayLayout.Section> sections = new ArrayList<>();
      float innerX = panel.x() + b2;
      float innerW = panelW - 2.0F * b2;

      for (OrePlayLayout.PendingSection section : pending) {
         OreBox labelBox = new OreBox(innerX + itemPad, cursor + this.px(4.8F) - this.mu(2) - this.px(2.0F), innerW - 2.0F * itemPad, this.px(2.0F));
         cursor += this.px(4.8F);
         OreBox line = new OreBox(innerX, cursor + b2 - 2.0F * b2, innerW, 2.0F * b2);
         cursor += b2;
         List<OrePlayLayout.ListItem> items = new ArrayList<>();

         for (String[] entry : section.items) {
            float itemW = innerW - (desktop ? 0.0F : this.px(1.6F));
            OrePlayLayout.ListItem item = section.subtitles ? this.featuredItem(entry, innerX, cursor, itemW) : this.savedItem(entry, innerX, cursor, itemW);
            items.add(item);
            widgets.add(OreWidget.clipped(item.id(), item.box(), viewport));
            cursor += item.box().h();
         }

         sections.add(new OrePlayLayout.Section(labelBox, this.text.ellipsize(OreText.Type.CAPTION_SHORT, section.label, labelBox.w()), line, items));
      }

      return new OrePlayLayout.SideMenu(scroll, panel, add, sections);
   }

   String serverMessage(PlayData.Status status) {
      if (status.state() == PlayData.State.ONLINE && !status.motd().isBlank()) {
         return status.motd();
      } else {
         return status.state() == PlayData.State.ONLINE && !status.countsKnown() ? "" : this.lang.get("progressScreen.message.locating");
      }
   }

   private float iconSize() {
      return 40.0F * this.scale.b1();
   }

   private float featuredItemHeight(String[] entry) {
      float lines = (entry[2].isEmpty() ? 1 : 2) * this.px(2.0F);
      float content = entry.length > 4 && !entry[4].isEmpty() ? Math.max(lines, this.iconSize()) : lines;
      return 2.0F * this.scale.b2() + 2.0F * this.mu(2) + content;
   }

   private OrePlayLayout.ListItem featuredItem(String[] entry, float x, float y, float w) {
      float b2 = this.scale.b2();
      boolean subtitle = !entry[2].isEmpty();
      boolean sponsored = entry.length > 3 && !entry[3].isEmpty();
      boolean hasIcon = entry.length > 4 && !entry[4].isEmpty();
      float h = this.featuredItemHeight(entry);
      OreBox box = new OreBox(x, y, w, h);
      float contentTop = y + b2 + this.mu(2);
      float contentH = h - 2.0F * b2 - 2.0F * this.mu(2);
      float textX = x + this.mu(4);
      float textW = w - 2.0F * this.mu(4);
      OreBox icon = null;
      if (hasIcon) {
         icon = new OreBox(textX, contentTop + (contentH - this.iconSize()) / 2.0F, this.iconSize(), this.iconSize());
         textX += this.iconSize() + this.mu(2);
         textW -= this.iconSize() + this.mu(2);
      }

      float linesH = (subtitle ? 2 : 1) * this.px(2.0F);
      float linesTop = contentTop + (contentH - linesH) / 2.0F;
      OrePlayLayout.Tag tag = null;
      float titleW = Math.max(0.0F, textW);
      if (sponsored) {
         String label = this.lang.get("bedrocknative.partner.sponsored");
         float tagW = Math.min(Math.max(0.0F, textW), this.px(0.4F) + this.text.width(OreText.Type.BODY, label) + this.px(0.4F));
         tag = new OrePlayLayout.Tag(
            new OreBox(textX + textW - tagW, linesTop, tagW, this.px(2.0F)),
            this.text.ellipsize(OreText.Type.BODY, label, Math.max(0.0F, tagW - this.px(0.8F))),
            OrePlayLayout.TagRole.INFORMATIVE_TINT
         );
         titleW = Math.max(0.0F, textW - tagW - this.mu(2));
      }

      OreBox title = new OreBox(textX, linesTop, titleW, this.px(2.0F));
      OreBox sub = subtitle ? new OreBox(textX, title.bottom(), Math.max(0.0F, textW), this.px(2.0F)) : null;
      return new OrePlayLayout.ListItem(
         entry[0],
         box,
         true,
         title,
         this.text.ellipsize(OreText.Type.BODY, entry[1], titleW),
         sub,
         subtitle ? this.text.ellipsize(OreText.Type.CAPTION_SHORT, entry[2], Math.max(0.0F, textW)) : "",
         icon,
         tag
      );
   }

   private OrePlayLayout.ListItem savedItem(String[] entry, float x, float y, float w) {
      float h = this.px(4.8F);
      OreBox box = new OreBox(x, y, w, h);
      boolean subtitle = !entry[2].isEmpty();
      float contentH = 2.0F * this.mu(2) + (subtitle ? 2 : 1) * this.px(2.0F);
      float top = y + (h - contentH) / 2.0F + this.mu(2);
      float textX = x + this.mu(4);
      float textW = w - 2.0F * this.mu(4);
      OreBox title = new OreBox(textX, top, textW, this.px(2.0F));
      OreBox sub = subtitle ? new OreBox(textX, top + this.px(2.0F), textW, this.px(2.0F)) : null;
      return new OrePlayLayout.ListItem(
         entry[0],
         box,
         false,
         title,
         this.text.ellipsize(OreText.Type.BODY, entry[1], textW),
         sub,
         subtitle ? this.text.ellipsize(OreText.Type.CAPTION_SHORT, entry[2], textW) : "",
         null,
         null
      );
   }

   private OrePlayLayout.Details details(OrePlayModel model, OrePlayLayout.State state, OreBox column, OreScale.Breakpoint breakpoint, OreWidgets widgets) {
      String selected = state.selected();
      if (selected == null) {
         return new OrePlayLayout.Details(null, null, null);
      } else {
         float top = column.y() + this.mu(2);
         OreBox viewport = new OreBox(column.x(), top, column.w(), Math.max(0.0F, column.bottom() - top));
         if (selected.startsWith("server:")) {
            int index = index(selected);
            if (index >= 0 && index < model.servers().size()) {
               float contentH = this.savedBarHeight() + 3.0F * this.px(7.2F) + this.footerHeight();
               boolean overflow = contentH > viewport.h();
               OrePlayLayout.Scroll scroll = this.scroll("details", viewport, contentH, state.detailsScroll(), overflow);
               float w = viewport.w() - (overflow && breakpoint == OreScale.Breakpoint.DESKTOP ? this.px(1.6F) : 0.0F);
               OrePlayLayout.SavedDetails saved = this.saved(
                  model.servers().get(index), viewport.x(), viewport.y() - scroll.offset(), w, state.addressShown(), breakpoint
               );
               widgets.add(OreWidget.clipped(saved.play().id(), saved.play().box(), viewport));

               for (OrePlayLayout.FieldRow row : saved.rows()) {
                  if (row.toggle() != null) {
                     widgets.add(OreWidget.clipped(row.toggle().id(), row.toggle().box(), viewport));
                  }
               }

               widgets.add(OreWidget.clipped(saved.edit().id(), saved.edit().box(), viewport));
               return new OrePlayLayout.Details(scroll, saved, null);
            } else {
               return new OrePlayLayout.Details(null, null, null);
            }
         } else if (!selected.startsWith("featured:")) {
            return new OrePlayLayout.Details(null, null, null);
         } else {
            int index = index(selected);
            if (index >= 0 && index < model.featured().size()) {
               OrePlayModel.Featured partner = model.featured().get(index);
               OrePlayLayout.FeaturedDetails first = this.featured(partner, viewport.x(), viewport.y(), viewport.w(), breakpoint);
               float contentH = first.card().h() + (breakpoint == OreScale.Breakpoint.NARROW ? this.mu(2) : this.mu(4));
               boolean overflow = contentH > viewport.h();
               OrePlayLayout.Scroll scroll = this.scroll("details", viewport, contentH, state.detailsScroll(), overflow);
               float w = viewport.w() - (overflow && breakpoint == OreScale.Breakpoint.DESKTOP ? this.px(1.6F) : 0.0F);
               OrePlayLayout.FeaturedDetails details = this.featured(partner, viewport.x(), viewport.y() - scroll.offset(), w, breakpoint);
               widgets.add(OreWidget.clipped(details.play().id(), details.play().box(), viewport));
               return new OrePlayLayout.Details(scroll, null, details);
            } else {
               return new OrePlayLayout.Details(null, null, null);
            }
         }
      }
   }

   public static int index(String id) {
      try {
         return Integer.parseInt(id.substring(id.indexOf(58) + 1));
      } catch (IndexOutOfBoundsException | NumberFormatException var2) {
         return -1;
      }
   }

   private float savedBarHeight() {
      return 2.0F * this.px(0.8F) + this.px(4.4F) + 2.0F * this.scale.b2();
   }

   private float footerHeight() {
      return 2.0F * this.px(1.6F) + 2.0F * this.scale.b2() + this.px(4.4F) + 2.0F * this.scale.b2();
   }

   private OrePlayLayout.SavedDetails saved(PlayData.Server server, float x, float y, float w, boolean addressShown, OreScale.Breakpoint breakpoint) {
      float b2 = this.scale.b2();
      OreBox bar = new OreBox(x, y, w, this.savedBarHeight());
      OreBox inner = bar.inset(this.px(2.4F), this.px(0.8F), this.px(2.4F), this.px(0.8F));
      OrePlayLayout.PingLabel ping = this.pingLabel(server.status());
      float pingW = pingIconWidth(this.scale, ping.sprite) + this.mu(1) + this.text.width(OreText.Type.BODY, ping.text);
      String players = players(server.status());
      float playersW = this.px(2.4F) + this.mu(1) + this.text.width(OreText.Type.BODY, players);
      boolean narrow = breakpoint == OreScale.Breakpoint.NARROW;
      OreBox pingBox = new OreBox(inner.x(), inner.y() + (inner.h() - this.px(2.0F)) / 2.0F, pingW, this.px(2.0F));
      OreBox playersBox = new OreBox(pingBox.right() + (narrow ? this.mu(1) : this.mu(6)), pingBox.y(), playersW, this.px(2.0F));
      float playW = Math.min(this.px(32.0F), Math.max(0.0F, inner.right() - playersBox.right() - this.mu(4)));
      OrePlayLayout.Button play = new OrePlayLayout.Button(
         "play",
         new OreBox(inner.right() - playW, inner.y(), playW, this.px(4.4F) + 2.0F * b2),
         this.lang.get("hbui.PlayScreen.serverTab.Play"),
         OreTheme.Variant.HERO,
         null,
         true
      );
      List<OrePlayLayout.FieldRow> rows = new ArrayList<>();
      float cursor = bar.bottom();
      BedrockAddress address = BedrockAddress.parse(server.address());
      String[][] fields = new String[][]{
         {server.name(), this.lang.get("hbui.PlayScreen.serverTab.externalServerDetails.name")},
         {
               addressShown ? address.host() : this.lang.get("hbui.PlayScreen.serverTab.externalServerDetails.placeholder"),
               this.lang.get("hbui.PlayScreen.serverTab.externalServerDetails.address")
         },
         {String.valueOf(address.port()), this.lang.get("hbui.PlayScreen.serverTab.externalServerDetails.port")}
      };

      for (int i = 0; i < fields.length; i++) {
         OreBox row = new OreBox(x, cursor, w, this.px(7.2F));
         OreBox content = row.inset(this.px(2.4F), this.px(1.2F), this.px(2.4F), this.px(1.2F));
         OrePlayLayout.Button toggle = null;
         float valueW = content.w();
         if (i == 1) {
            String label = this.lang
               .get(addressShown ? "hbui.PlayScreen.serverTab.externalServerDetails.hideButton" : "hbui.PlayScreen.serverTab.externalServerDetails.showButton");
            float buttonW = Math.min(
               this.px(12.0F), Math.max(this.px(6.0F), this.text.width(OreText.Type.SECONDARY_BUTTON, label) + 2.0F * this.px(2.0F) + 2.0F * b2)
            );
            toggle = new OrePlayLayout.Button(
               "toggle-address",
               new OreBox(content.right() - buttonW, content.y() + (content.h() - this.px(4.8F)) / 2.0F, buttonW, this.px(4.8F)),
               label,
               OreTheme.Variant.SECONDARY,
               null,
               false
            );
            valueW = Math.max(0.0F, content.w() - buttonW - this.px(1.6F));
         }

         OreBox value = new OreBox(content.x(), content.y(), valueW, this.px(2.0F));
         OreBox label = new OreBox(content.x(), content.y() + this.px(2.0F) + this.mu(2), valueW, this.px(2.0F));
         rows.add(
            new OrePlayLayout.FieldRow(
               row,
               value,
               this.text.ellipsize(OreText.Type.BODY, fields[i][0], valueW),
               label,
               this.text.ellipsize(OreText.Type.CAPTION_SHORT, fields[i][1], valueW),
               toggle
            )
         );
         cursor += row.h();
      }

      OreBox footer = new OreBox(x, cursor, w, this.footerHeight());
      OreBox footerInner = footer.inset(b2 + this.px(6.4F), b2 + this.px(1.6F), b2 + this.px(6.4F), b2 + this.px(1.6F));
      String editText = this.lang.get("hbui.PlayScreen.serverTab.externalServerDetails.editButton");
      float editW = Math.min(
         this.px(32.0F), Math.min(footerInner.w(), this.text.width(OreText.Type.SECONDARY_BUTTON, editText) + 2.0F * this.px(2.0F) + 2.0F * b2)
      );
      OrePlayLayout.Button edit = new OrePlayLayout.Button(
         "edit-server",
         new OreBox(footerInner.x() + (footerInner.w() - editW) / 2.0F, footerInner.y(), editW, this.px(4.8F)),
         editText,
         OreTheme.Variant.SECONDARY,
         null,
         false
      );
      return new OrePlayLayout.SavedDetails(bar, pingBox, ping.sprite, ping.text, playersBox, players, play, rows, footer, edit);
   }

   private OrePlayLayout.FeaturedDetails featured(OrePlayModel.Featured partner, float x, float y, float w, OreScale.Breakpoint breakpoint) {
      float b2 = this.scale.b2();
      float innerW = w - 2.0F * b2;
      OreBox banner = new OreBox(x + b2, y + b2, innerW, innerW * 3.0F / 10.0F);
      OreBox overlay = new OreBox(banner.x(), banner.bottom() - this.px(6.0F), banner.w(), this.px(6.0F));
      OreBox overlayInner = overlay.inset(this.px(2.4F), this.px(1.2F), this.px(2.4F), this.px(1.2F));
      boolean measured = partner.status().state() != PlayData.State.UNKNOWN;
      OrePlayLayout.PingLabel ping = measured ? this.pingLabel(partner.status()) : new OrePlayLayout.PingLabel(null, "");
      float pingW = measured ? pingIconWidth(this.scale, ping.sprite) + this.mu(1) + this.text.width(OreText.Type.BODY, ping.text) : 0.0F;
      OreBox pingBox = new OreBox(overlayInner.x(), overlayInner.y() + (overlayInner.h() - this.px(2.0F)) / 2.0F, pingW, this.px(2.0F));
      String players = measured ? players(partner.status()) : "";
      OreBox playersBox = new OreBox(
         pingBox.right() + (breakpoint == OreScale.Breakpoint.NARROW ? this.mu(1) : this.mu(6)),
         pingBox.y(),
         measured ? this.px(2.4F) + this.mu(1) + this.text.width(OreText.Type.BODY, players) : 0.0F,
         this.px(2.0F)
      );
      float rowH = 2.0F * this.mu(3) + this.px(4.4F) + 2.0F * b2;
      OreBox nameRow = new OreBox(x + b2, banner.bottom(), innerW, rowH);
      OreBox rowInner = nameRow.inset(this.px(2.4F), this.mu(3), this.px(2.4F), this.mu(3));
      float playW = Math.min(this.px(32.0F), rowInner.w() / 2.0F);
      OrePlayLayout.Button play = new OrePlayLayout.Button(
         "play",
         new OreBox(rowInner.right() - playW, rowInner.y(), playW, this.px(4.4F) + 2.0F * b2),
         this.lang.get("hbui.PlayScreen.serverTab.Play"),
         OreTheme.Variant.HERO,
         null,
         true
      );
      float nameW = Math.max(0.0F, rowInner.w() - playW - this.mu(1));
      String name = this.text.ellipsize(OreText.Type.BODY, partner.server().name(), nameW);
      OreBox nameBox = new OreBox(rowInner.x(), rowInner.y() + (rowInner.h() - this.px(2.0F)) / 2.0F, nameW, this.px(2.0F));
      float descInnerW = innerW - 2.0F * this.px(2.4F);
      List<String> lines = this.text.wrap(OreText.Type.CAPTION_SHORT, partner.server().description(), descInnerW, 12);
      float descH = 2.0F * this.mu(3) + this.px(2.0F) + this.mu(2) + lines.size() * this.px(2.0F);
      OreBox descRow = new OreBox(x + b2, nameRow.bottom(), innerW, descH);
      OreBox descTitle = new OreBox(descRow.x() + this.px(2.4F), descRow.y() + this.mu(3), descInnerW, this.px(2.0F));
      OreBox descText = new OreBox(descTitle.x(), descTitle.bottom() + this.mu(2), descInnerW, lines.size() * this.px(2.0F));
      OreBox card = new OreBox(x, y, w, descRow.bottom() - y);
      List<OrePlayLayout.Tag> sponsorTags = partner.server().sponsored()
         ? this.tags(
            List.of(new String[]{this.lang.get("bedrocknative.partner.sponsored"), OrePlayLayout.TagRole.INFORMATIVE_TINT.name()}),
            banner.x(),
            banner.y(),
            banner.w()
         )
         : List.of();
      OreBox sponsorFrame = sponsorTags.isEmpty()
         ? null
         : new OreBox(banner.x(), banner.y(), sponsorTags.getLast().box().right() + b2 - banner.x(), this.px(2.0F) + b2);
      return new OrePlayLayout.FeaturedDetails(
         card,
         banner,
         overlay,
         pingBox,
         ping.sprite,
         ping.text,
         playersBox,
         players,
         nameRow,
         nameBox,
         name,
         play,
         descRow,
         descTitle,
         this.lang.get("hbui.PlayScreen.serverTab.ServerDescription.title"),
         lines,
         descText,
         sponsorFrame,
         sponsorTags
      );
   }

   public OrePlayLayout.PingLabel pingLabel(PlayData.Status status) {
      if (status.state() != PlayData.State.ONLINE) {
         return new OrePlayLayout.PingLabel(null, this.lang.get("hbui.PlayScreen.ServerPing.loadingPing"));
      } else {
         int ms = status.latencyMillis();
         if (ms < 150) {
            return new OrePlayLayout.PingLabel("pingGreen", this.lang.get("hbui.PlayScreen.ServerPing.lowPing"));
         } else {
            return ms < 300
               ? new OrePlayLayout.PingLabel("pingYellow", this.lang.get("hbui.PlayScreen.ServerPing.mediumPing"))
               : new OrePlayLayout.PingLabel("pingRed", this.lang.get("hbui.PlayScreen.ServerPing.highPing"));
         }
      }
   }

   public static float pingIconWidth(OreScale scale, @Nullable String sprite) {
      return sprite != null ? 20.0F * scale.b1() : scale.px(11.2F) / 6.0F + 2.0F * scale.b2();
   }

   static String players(PlayData.Status status) {
      if (status.state() != PlayData.State.ONLINE) {
         return "0";
      } else {
         return status.countsKnown() ? String.valueOf(status.online()) : "-";
      }
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

   public record Button(String id, OreBox box, String text, OreTheme.Variant variant, @Nullable OreIcon icon, boolean hero) {
   }

   private record Details(@Nullable OrePlayLayout.Scroll scroll, @Nullable OrePlayLayout.SavedDetails saved, @Nullable OrePlayLayout.FeaturedDetails featured) {
   }

   public record FeaturedDetails(
      OreBox card,
      OreBox banner,
      OreBox overlay,
      OreBox ping,
      @Nullable String pingSprite,
      String pingText,
      OreBox players,
      String playersText,
      OreBox nameRow,
      OreBox name,
      String nameText,
      OrePlayLayout.Button play,
      OreBox descriptionRow,
      OreBox descriptionTitle,
      String descriptionTitleText,
      List<String> descriptionLines,
      OreBox descriptionText,
      @Nullable OreBox sponsorFrame,
      List<OrePlayLayout.Tag> sponsorTags
   ) {
   }

   public record FieldRow(OreBox box, OreBox value, String valueText, OreBox label, String labelText, @Nullable OrePlayLayout.Button toggle) {
   }

   public record Frame(
      OreScale scale,
      OreScale.Breakpoint breakpoint,
      float width,
      float height,
      OrePlayLayout.Header header,
      OreBox tabBar,
      List<OrePlayLayout.Tab> tabs,
      OreBox leftHint,
      OreBox rightHint,
      OreBox contentArea,
      int tab,
      @Nullable OreBox layoutToggle,
      List<OrePlayLayout.Button> headerButtons,
      @Nullable OrePlayLayout.Scroll worldScroll,
      List<OrePlayLayout.WorldCard> worldCards,
      List<OrePlayLayout.WorldRow> worldRows,
      int columns,
      int worldPage,
      int worldPages,
      @Nullable OrePlayLayout.Pagination pagination,
      @Nullable OrePlayLayout.Notice emptyWorlds,
      @Nullable OrePlayLayout.Notice realmsNotice,
      @Nullable OrePlayLayout.Scroll menuScroll,
      @Nullable OreBox menuPanel,
      @Nullable OrePlayLayout.Button addServer,
      List<OrePlayLayout.Section> sections,
      @Nullable OrePlayLayout.Scroll detailsScroll,
      @Nullable OrePlayLayout.SavedDetails saved,
      @Nullable OrePlayLayout.FeaturedDetails featured,
      OrePlayLayout.Button profile,
      OreWidgets widgets
   ) {
   }

   public record Header(
      OreBox bar,
      OreBox strip,
      OreBox bevel,
      OreBox back,
      OreBox arrow,
      OreBox title,
      OreBox divider,
      OreBox social,
      OreBox socialIcon,
      OreBox socialText,
      String socialLabel
   ) {
   }

   public record ListItem(
      String id,
      OreBox box,
      boolean subtitleLayout,
      OreBox title,
      String titleText,
      @Nullable OreBox subtitle,
      String subtitleText,
      @Nullable OreBox icon,
      @Nullable OrePlayLayout.Tag tag
   ) {
   }

   public record Notice(OreBox card, OreBox title, String titleText, @Nullable OreBox art, @Nullable String artSprite, List<String> lines, OreBox text) {
   }

   public record PageCell(String id, OreBox box, String label, int target, boolean selected, OreBox underline) {
   }

   public record Pagination(
      int page,
      int pages,
      OreBox previous,
      OreBox previousArrow,
      boolean previousEnabled,
      OreBox next,
      OreBox nextArrow,
      boolean nextEnabled,
      OreBox frame,
      List<OrePlayLayout.PageCell> cells
   ) {
   }

   private record PendingSection(String label, List<String[]> items, boolean subtitles) {
   }

   public record PingLabel(@Nullable String sprite, String text) {
   }

   public record SavedDetails(
      OreBox bar,
      OreBox ping,
      @Nullable String pingSprite,
      String pingText,
      OreBox players,
      String playersText,
      OrePlayLayout.Button play,
      List<OrePlayLayout.FieldRow> rows,
      OreBox footer,
      OrePlayLayout.Button edit
   ) {
   }

   public record Scroll(String id, OreBox viewport, float contentHeight, float offset, @Nullable OreBox track, @Nullable OreBox thumb) {
      public boolean scrollable() {
         return this.contentHeight > this.viewport.h() + 0.5F;
      }

      public float maxOffset() {
         return Math.max(0.0F, this.contentHeight - this.viewport.h());
      }
   }

   public record Section(OreBox label, String labelText, OreBox line, List<OrePlayLayout.ListItem> items) {
   }

   private record SideMenu(OrePlayLayout.Scroll scroll, OreBox panel, OrePlayLayout.Button add, List<OrePlayLayout.Section> sections) {
   }

   public record State(
      int tab, @Nullable String selected, float worldScroll, float menuScroll, float detailsScroll, boolean addressShown, boolean listLayout, int worldPage
   ) {
      public State(int tab, @Nullable String selected, float worldScroll, float menuScroll, float detailsScroll, boolean addressShown, boolean listLayout) {
         this(tab, selected, worldScroll, menuScroll, detailsScroll, addressShown, listLayout, 1);
      }
   }

   public record Tab(int index, OreBox box, boolean selected, OreBox icon, OreBox label, String text, OreBox indicator) {
   }

   public record Tag(OreBox box, String text, OrePlayLayout.TagRole role) {
   }

   public static enum TagRole {
      NEUTRAL80,
      INFORMATIVE_TINT,
      DESTRUCTIVE_TINT;
   }

   public record WorldCard(
      int index,
      boolean lan,
      OreBox card,
      OreBox imageBox,
      OreBox image,
      @Nullable OreBox topFrame,
      List<OrePlayLayout.Tag> topTags,
      @Nullable OreBox bottomFrame,
      List<OrePlayLayout.Tag> bottomTags,
      OreBox info,
      OreBox action,
      OreBox edit,
      OreBox editIcon,
      OreBox title,
      OreBox subtitle,
      String titleText,
      String subtitleText,
      @Nullable OreBox players,
      String playersText
   ) {
   }

   public record WorldRow(
      int index,
      boolean lan,
      OreBox row,
      OreBox action,
      OreBox pictureBox,
      OreBox image,
      OreBox title,
      String titleText,
      @Nullable OreBox tag,
      String tagText,
      OreBox meta,
      List<String> metaLines,
      OreBox edit,
      OreBox editIcon,
      @Nullable OreBox players,
      String playersText
   ) {
   }
}
