package net.bedrocknative.oreui;

import java.nio.file.Path;
import java.util.List;
import org.jspecify.annotations.Nullable;

public final class OrePlayUi {
   static final float WHEEL_REM = 4.8F;
   private OreText text;
   private final OreLang lang;
   private final OreTheme theme;
   private final OreThemeVars vars;
   private final OrePlayModel model;
   private final OrePlayUi.Actions actions;
   private final @Nullable Path defaultWorldPicture;
   private final OreInteraction interaction = new OreInteraction();
   private int tab;
   private @Nullable String selected;
   private float worldScroll;
   private float menuScroll;
   private float detailsScroll;
   private boolean addressShown;
   private boolean listLayout;
   private int worldPage = 1;
   private @Nullable OreModal modal;
   private OreModal.@Nullable Layout modalLayout;
   private List<Runnable> modalChoices = List.of();
   private OrePlayLayout.@Nullable Frame frame;
   private float width;
   private float height;
   private @Nullable String dragging;
   private float dragStart;
   private float dragOffset;

   public OrePlayUi(
      OreText text,
      OreLang lang,
      OreTheme theme,
      OreThemeVars vars,
      OrePlayModel model,
      int tab,
      @Nullable String selected,
      @Nullable Path defaultWorldPicture,
      OrePlayUi.Actions actions
   ) {
      this.text = text;
      this.lang = lang;
      this.theme = theme;
      this.vars = vars;
      this.model = model;
      this.tab = Math.clamp(tab, 0, 2);
      this.selected = selected;
      this.defaultWorldPicture = defaultWorldPicture;
      this.actions = actions;
   }

   public int tab() {
      return this.tab;
   }

   public void text(OreText current) {
      if (current != this.text) {
         this.text = current;
         if (this.width > 0.0F) {
            this.layout(this.width, this.height);
         }
      }
   }

   public @Nullable String selected() {
      return this.selected;
   }

   public OrePlayModel model() {
      return this.model;
   }

   public boolean modalOpen() {
      return this.modal != null;
   }

   public @Nullable OreModal modal() {
      return this.modal;
   }

   public OreModal.@Nullable Layout modalLayout() {
      return this.modalLayout;
   }

   public OreInteraction interaction() {
      return this.interaction;
   }

   public OrePlayLayout.@Nullable Frame frame() {
      return this.frame;
   }

   private OreWidgets widgets() {
      if (this.modalLayout != null) {
         OreWidgets widgets = new OreWidgets();
         OreModal.widgets(this.modalLayout, widgets);
         return widgets;
      } else {
         return this.frame != null ? this.frame.widgets() : new OreWidgets();
      }
   }

   public OrePlayLayout.Frame layout(float newWidth, float newHeight) {
      this.width = newWidth;
      this.height = newHeight;
      if (this.selected != null && !this.exists(this.selected)) {
         this.selected = null;
      }

      if (this.selected == null && !this.model.featured().isEmpty()) {
         this.selected = "featured:0";
      }

      this.frame = new OrePlayLayout(this.text, this.lang, this.width, this.height)
         .layout(
            this.model,
            new OrePlayLayout.State(
               this.tab, this.selected, this.worldScroll, this.menuScroll, this.detailsScroll, this.addressShown, this.listLayout, this.worldPage
            )
         );
      if (this.tab == 0) {
         this.worldPage = this.frame.worldPage();
      }

      if (this.frame.worldScroll() != null) {
         this.worldScroll = this.frame.worldScroll().offset();
      }

      if (this.frame.menuScroll() != null) {
         this.menuScroll = this.frame.menuScroll().offset();
      }

      if (this.frame.detailsScroll() != null) {
         this.detailsScroll = this.frame.detailsScroll().offset();
      }

      this.modalLayout = this.modal == null ? null : this.modal.layout(this.text, this.width, this.height);
      this.interaction.refresh(this.widgets());
      return this.frame;
   }

   private boolean exists(String id) {
      int index = OrePlayLayout.index(id);
      if (id.startsWith("server:")) {
         return index >= 0 && index < this.model.servers().size();
      } else {
         return !id.startsWith("featured:") ? false : index >= 0 && index < this.model.featured().size();
      }
   }

   public void render(OrePainter painter, long now) {
      OrePlayLayout.Frame current = this.layout(this.width, this.height);
      OreDraw draw = new OreDraw(painter, this.text, this.theme);
      OrePlayView view = new OrePlayView(
         draw, new OrePlayView.Context(this.lang.get("hbui.PlayScreen.title"), this.vars, this.defaultWorldPicture), this.interaction::state
      );
      view.render(current, this.model, this.selected, this.listLayout, now);
      if (this.modal != null && this.modalLayout != null) {
         this.modal.render(draw, this.modalLayout, this.interaction::state);
      }
   }

   public void size(float newWidth, float newHeight) {
      if (newWidth != this.width || newHeight != this.height || this.frame == null) {
         this.layout(newWidth, newHeight);
      }
   }

   public void mouseMoved(double x, double y) {
      this.interaction.moved(this.widgets(), x, y);
   }

   public void mousePressed(double x, double y) {
      if (this.interaction.press(this.widgets(), x, y) != null) {
         this.actions.click();
      } else {
         this.dragging = null;
         if (this.frame != null && this.modal == null) {
            for (OrePlayLayout.Scroll scroll : new OrePlayLayout.Scroll[]{this.frame.worldScroll(), this.frame.menuScroll(), this.frame.detailsScroll()}) {
               if (scroll != null && scroll.track() != null && scroll.track().outset(this.text.scale().b2()).contains(x, y)) {
                  this.dragging = scroll.id();
                  this.dragStart = (float)y;
                  OreBox thumb = scroll.thumb();
                  if (thumb != null && !thumb.contains(x, y)) {
                     this.setOffset(scroll.id(), offsetFor(scroll, (float)y - thumb.h() / 2.0F));
                  }

                  this.dragOffset = this.offset(scroll.id());
                  break;
               }
            }
         }
      }
   }

   public void mouseDragged(double x, double y) {
      if (this.dragging != null && this.frame != null) {
         OrePlayLayout.Scroll scroll = this.scroll(this.dragging);
         if (scroll != null && scroll.track() != null && scroll.thumb() != null) {
            float travel = scroll.track().h() - scroll.thumb().h();
            if (travel > 0.0F) {
               this.setOffset(this.dragging, this.dragOffset + ((float)y - this.dragStart) * scroll.maxOffset() / travel);
            }
         }
      }
   }

   private OrePlayLayout.@Nullable Scroll scroll(String id) {
      if (this.frame == null) {
         return null;
      }

      return switch (id) {
         case "worlds" -> this.frame.worldScroll();
         case "menu" -> this.frame.menuScroll();
         default -> this.frame.detailsScroll();
      };
   }

   private static float offsetFor(OrePlayLayout.Scroll scroll, float thumbY) {
      if (scroll.track() != null && scroll.thumb() != null) {
         float travel = scroll.track().h() - scroll.thumb().h();
         return travel <= 0.0F ? 0.0F : (thumbY - scroll.track().y()) / travel * scroll.maxOffset();
      } else {
         return scroll.offset();
      }
   }

   private float offset(String id) {
      return switch (id) {
         case "worlds" -> this.worldScroll;
         case "menu" -> this.menuScroll;
         default -> this.detailsScroll;
      };
   }

   private void setOffset(String id, float value) {
      switch (id) {
         case "worlds":
            this.worldScroll = value;
            break;
         case "menu":
            this.menuScroll = value;
            break;
         default:
            this.detailsScroll = value;
      }

      this.layout(this.width, this.height);
   }

   public void mouseReleased(double x, double y) {
      this.dragging = null;
      String activated = this.interaction.release(this.widgets(), x, y);
      if (activated != null) {
         this.activate(activated);
      }
   }

   public boolean scrolled(double x, double y, double delta) {
      if (this.frame != null && this.modal == null) {
         float step = this.text.scale().px(4.8F) * (float)(-delta);
         if (this.frame.worldScroll() != null && this.frame.worldScroll().viewport().contains(x, y)) {
            this.worldScroll += step;
         } else if (this.frame.menuScroll() != null && this.frame.menuScroll().viewport().contains(x, y)) {
            this.menuScroll += step;
         } else {
            if (this.frame.detailsScroll() == null || !this.frame.detailsScroll().viewport().contains(x, y)) {
               return false;
            }

            this.detailsScroll += step;
         }

         this.layout(this.width, this.height);
         return true;
      } else {
         return false;
      }
   }

   public boolean key(OreKey key, boolean shift) {
      if (this.modal != null) {
         if (key == OreKey.ESCAPE) {
            this.closeModal();
            return true;
         }
      } else {
         switch (key) {
            case ESCAPE:
               this.actions.click();
               this.actions.back();
               return true;
            case PREVIOUS_TAB:
               this.switchTab(this.tab - 1);
               return true;
            case NEXT_TAB:
               this.switchTab(this.tab + 1);
               return true;
         }
      }

      if (key == OreKey.ENTER || key == OreKey.SPACE) {
         String focused = this.interaction.focused();
         if (focused != null && this.widgets().get(focused) != null) {
            this.actions.click();
            this.activate(focused);
            return true;
         } else {
            return false;
         }
      } else {
         if (key != OreKey.TAB && key != OreKey.LEFT && key != OreKey.RIGHT && key != OreKey.UP && key != OreKey.DOWN) {
            return false;
         }

         OreWidget next = this.interaction.navigate(this.widgets(), key, shift);
         if (next != null) {
            this.reveal(next);
         }

         return true;
      }
   }

   private void reveal(OreWidget widget) {
      OrePlayLayout.Scroll scroll = this.scrollOf(widget.id());
      if (scroll != null) {
         OreBox view = scroll.viewport();
         float delta = 0.0F;
         if (widget.box().y() < view.y()) {
            delta = widget.box().y() - view.y();
         } else if (widget.box().bottom() > view.bottom()) {
            delta = Math.min(widget.box().bottom() - view.bottom(), widget.box().y() - view.y());
         }

         if (delta != 0.0F) {
            switch (scroll.id()) {
               case "worlds":
                  this.worldScroll += delta;
                  break;
               case "menu":
                  this.menuScroll += delta;
                  break;
               default:
                  this.detailsScroll += delta;
            }

            this.layout(this.width, this.height);
         }
      }
   }

   private OrePlayLayout.@Nullable Scroll scrollOf(String id) {
      if (this.frame == null) {
         return null;
      }

      return switch (id.contains(":") ? id.substring(0, id.indexOf(58)) : id) {
         case "world", "world-image", "world-edit", "toggle-layout", "create-world", "import-world", "page", "page-prev", "page-next" -> this.frame
            .worldScroll();
         case "add-server", "server", "featured" -> this.frame.menuScroll();
         case "play", "toggle-address", "edit-server" -> this.frame.detailsScroll();
         default -> null;
      };
   }

   private void switchTab(int newTab) {
      int clamped = Math.clamp(newTab, 0, 2);
      if (clamped != this.tab) {
         this.actions.click();
         this.openTab(clamped);
      }
   }

   public void openTab(int newTab) {
      int clamped = Math.clamp(newTab, 0, 2);
      if (clamped != this.tab) {
         this.tab = clamped;
         this.worldScroll = 0.0F;
         this.worldPage = 1;
         this.interaction.focus(null, false);
         this.actions.tabChanged(this.tab);
         this.layout(this.width, this.height);
      }
   }

   public void select(@Nullable String id) {
      if (id != null && !id.equals(this.selected)) {
         this.selected = id;
         this.detailsScroll = 0.0F;
         this.addressShown = false;
         this.actions.selected(id);
         this.layout(this.width, this.height);
      }
   }

   public int worldPage() {
      return this.worldPage;
   }

   public void page(int newPage) {
      int pages = this.frame == null ? 1 : this.frame.worldPages();
      int clamped = Math.clamp(newPage, 1, Math.max(1, pages));
      if (clamped != this.worldPage) {
         this.worldPage = clamped;
         this.worldScroll = 0.0F;
         this.layout(this.width, this.height);
         if (this.frame != null) {
            String first = !this.frame.worldCards().isEmpty()
               ? "world:" + this.frame.worldCards().getFirst().index()
               : (!this.frame.worldRows().isEmpty() ? "world:" + this.frame.worldRows().getFirst().index() : null);
            if (first != null) {
               this.interaction.focus(first, this.interaction.focusVisible());
            }
         }
      }
   }

   public void activate(String id) {
      if (this.modal != null) {
         int choice = OreModal.choice(id);
         if (id.equals("modal:close")) {
            this.closeModal();
         } else if (choice >= 0) {
            List<Runnable> choices = this.modalChoices;
            this.closeModal();
            if (choice < choices.size()) {
               choices.get(choice).run();
            }
         }
      } else {
         int index = OrePlayLayout.index(id);
         switch (id.contains(":") ? id.substring(0, id.indexOf(58)) : id) {
            case "back":
               this.actions.back();
               break;
            case "social":
               this.actions.notice("social");
               break;
            case "profile":
               this.actions.profile();
               break;
            case "tab":
               this.openTab(index);
               break;
            case "page-prev":
               this.page(this.worldPage - 1);
               break;
            case "page-next":
               this.page(this.worldPage + 1);
               break;
            case "page":
               OrePlayLayout.Pagination pages = this.frame == null ? null : this.frame.pagination();
               if (pages != null && index >= 0 && index < pages.cells().size() && !pages.cells().get(index).selected()) {
                  this.page(pages.cells().get(index).target());
               }
               break;
            case "toggle-layout":
               this.listLayout = !this.listLayout;
               this.layout(this.width, this.height);
               break;
            case "create-world":
               this.actions.createWorld();
               break;
            case "import-world":
               this.actions.notice("import");
               break;
            case "world":
            case "world-image":
               if (index < this.model.lan().size()) {
                  this.actions.joinLan(index);
               } else {
                  this.actions.playWorld(index - this.model.lan().size());
               }
               break;
            case "world-edit":
               this.actions.editWorld(index - this.model.lan().size());
               break;
            case "add-server":
               this.actions.addServer();
               break;
            case "server":
            case "featured":
               this.select(id);
               break;
            case "play":
               if (this.selected != null && this.selected.startsWith("server:")) {
                  this.actions.joinServer(OrePlayLayout.index(this.selected));
               } else if (this.selected != null && this.selected.startsWith("featured:")) {
                  this.actions.joinFeatured(OrePlayLayout.index(this.selected));
               }
               break;
            case "toggle-address":
               if (this.addressShown) {
                  this.addressShown = false;
                  this.layout(this.width, this.height);
               } else {
                  this.showAddressWarning();
               }
               break;
            case "edit-server":
               if (this.selected != null && this.selected.startsWith("server:")) {
                  this.actions.editServer(OrePlayLayout.index(this.selected));
               }
         }
      }
   }

   private void showAddressWarning() {
      String prefix = "hbui.PlayScreen.serverTab.ShowServerAddressModal";
      this.openModal(
         new OreModal(
            this.lang.get(prefix + ".title"),
            List.of(this.lang.get(prefix + ".firstParagraph"), this.lang.get(prefix + ".secondParagraph")),
            List.of(
               new OreModal.Choice(this.lang.get(prefix + ".primaryButton"), OreTheme.Variant.PRIMARY),
               new OreModal.Choice(this.lang.get(prefix + ".secondaryButton"), OreTheme.Variant.SECONDARY)
            ),
            false
         ),
         () -> {
            this.addressShown = true;
            this.layout(this.width, this.height);
         }
      );
   }

   public void askPartnerConsent(String host, Runnable accept, Runnable refuse) {
      String prefix = "bedrocknative.partner.consent.";
      this.openModal(
         new OreModal(
            this.lang.get(prefix + "title"),
            List.of(this.lang.get(prefix + "what"), this.lang.get(prefix + "privacy", host), this.lang.get(prefix + "change")),
            List.of(
               new OreModal.Choice(this.lang.get(prefix + "accept"), OreTheme.Variant.SECONDARY),
               new OreModal.Choice(this.lang.get(prefix + "refuse"), OreTheme.Variant.SECONDARY)
            ),
            true
         ),
         List.of(accept, refuse)
      );
   }

   public void openModal(OreModal newModal, @Nullable Runnable confirm) {
      this.openModal(newModal, confirm == null ? List.of() : List.of(confirm));
   }

   public void openModal(OreModal newModal, List<Runnable> choices) {
      this.modal = newModal;
      this.modalChoices = List.copyOf(choices);
      this.interaction.cancelPress();
      this.layout(this.width, this.height);
      this.interaction.focus("modal:0", false);
   }

   public void closeModal() {
      this.modal = null;
      this.modalLayout = null;
      this.modalChoices = List.of();
      this.interaction.cancelPress();
      this.layout(this.width, this.height);
   }

   public boolean addressShown() {
      return this.addressShown;
   }

   public boolean listLayout() {
      return this.listLayout;
   }

   public interface Actions {
      void back();

      void tabChanged(int var1);

      void notice(String var1);

      void createWorld();

      void playWorld(int var1);

      void editWorld(int var1);

      void joinLan(int var1);

      void addServer();

      default void profile() {
      }

      void editServer(int var1);

      void joinServer(int var1);

      void joinFeatured(int var1);

      default void selected(@Nullable String id) {
      }

      void click();
   }
}
