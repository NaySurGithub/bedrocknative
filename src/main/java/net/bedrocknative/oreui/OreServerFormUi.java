package net.bedrocknative.oreui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.bedrocknative.net.BedrockAddress;
import org.jspecify.annotations.Nullable;

public final class OreServerFormUi {
   static final int NAME_LENGTH = 256;
   static final int ADDRESS_LENGTH = 256;
   static final int PORT_LENGTH = 5;
   private static final String FORM = "hbui.PlayScreen.serverTab.serverForm";
   private OreText text;
   private final OreLang lang;
   private final OreTheme theme;
   private final OreThemeVars vars;
   private final boolean edit;
   private final OreServerFormUi.Actions actions;
   private final OreTextField[] fields;
   private final OreInteraction interaction = new OreInteraction();
   private int focusedField = -1;
   private boolean dirty;
   @Nullable
   private OreModal modal;
   @Nullable
   private OreModal.Layout modalLayout;
   private List<Runnable> modalChoices = List.of();
   @Nullable
   private OreServerFormUi.Layout layout;
   private float width;
   private float height;
   private int lastField = -1;

   public OreServerFormUi(
      OreText text, OreLang lang, OreTheme theme, OreThemeVars vars, @Nullable String name, @Nullable BedrockAddress address, OreServerFormUi.Actions actions
   ) {
      this.text = text;
      this.lang = lang;
      this.theme = theme;
      this.vars = vars;
      this.edit = address != null;
      this.actions = actions;
      this.fields = new OreTextField[]{
         new OreTextField(name == null ? "" : name, 256, false),
         new OreTextField(address == null ? "" : address.hostBox(), 256, false),
         new OreTextField(String.valueOf(address == null ? 19132 : address.port()), 5, true)
      };
   }

   public boolean editMode() {
      return this.edit;
   }

   public void text(OreText current) {
      if (current != this.text) {
         this.text = current;
         if (this.width > 0.0F) {
            this.layout(this.width, this.height);
         }
      }
   }

   public OreTextField field(int index) {
      return this.fields[index];
   }

   public boolean modalOpen() {
      return this.modal != null;
   }

   @Nullable
   public OreModal modal() {
      return this.modal;
   }

   @Nullable
   public OreModal.Layout modalLayout() {
      return this.modalLayout;
   }

   public int focusedField() {
      return this.focusedField;
   }

   public OreInteraction interaction() {
      return this.interaction;
   }

   @Nullable
   public OreServerFormUi.Layout layout() {
      return this.layout;
   }

   private OreWidgets widgets() {
      if (this.modalLayout != null) {
         OreWidgets widgets = new OreWidgets();
         OreModal.widgets(this.modalLayout, widgets);
         return widgets;
      } else {
         return this.layout != null ? this.layout.widgets() : new OreWidgets();
      }
   }

   public OreServerFormUi.Layout layout(float newWidth, float newHeight) {
      this.width = newWidth;
      this.height = newHeight;
      OreScale s = this.text.scale();
      float b2 = s.b2();
      OreScale.Breakpoint breakpoint = s.breakpoint(this.width);
      OreWidgets widgets = new OreWidgets();
      float barH = s.px(4.4F);
      OreBox bar = new OreBox(0.0F, 0.0F, this.width, barH);
      OreBox back = new OreBox(s.px(0.2F), (barH - s.px(4.0F)) / 2.0F, s.px(4.0F), s.px(4.0F));
      OreBox arrow = new OreBox(back.x() + (back.w() - 4.0F * b2) / 2.0F, back.y() + (back.h() - 8.0F * b2 - b2) / 2.0F + b2, 4.0F * b2, 8.0F * b2);
      OreBox title = new OreBox(s.px(6.0F), 0.0F, Math.max(0.0F, this.width - s.px(12.0F)), barH);
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
      widgets.add(OreWidget.of("back", back));
      float top = barH + 2.0F * b2;
      float pad = breakpoint == OreScale.Breakpoint.DESKTOP ? s.px(2.4F) : (breakpoint == OreScale.Breakpoint.TABLET ? s.px(1.6F) : s.px(0.8F));
      float gutter = breakpoint == OreScale.Breakpoint.DESKTOP ? s.px(0.8F) : (breakpoint == OreScale.Breakpoint.TABLET ? s.px(0.4F) : b2);
      float inner = this.width - 2.0F * pad;
      float rowWidth = Math.min(inner, s.px(128.0F));
      float rowX = pad + (inner - (rowWidth - 2.0F * gutter)) / 2.0F - gutter;
      float unit = rowWidth / (breakpoint == OreScale.Breakpoint.NARROW ? 8 : 12);
      float columnX = rowX + unit + gutter;
      float columnW = rowWidth - 2.0F * unit - 2.0F * gutter;
      float rowH = s.px(1.2F) + s.px(2.0F) + s.px(0.4F) + OreTextField.height(s) + s.px(0.4F) + s.px(1.2F);
      float footerH = 2.0F * b2 + 2.0F * s.px(1.6F) + s.px(4.8F);
      OreBox box = new OreBox(columnX, top + s.px(0.8F), columnW, 2.0F * b2 + 3.0F * rowH + footerH);
      List<OreBox> rows = new ArrayList<>();
      List<OreBox> labels = new ArrayList<>();
      List<OreBox> fieldBoxes = new ArrayList<>();
      float cursor = box.y() + b2;

      for (int i = 0; i < 3; i++) {
         OreBox row = new OreBox(box.x() + b2, cursor, box.w() - 2.0F * b2, rowH);
         OreBox content = row.inset(s.px(2.4F), s.px(1.2F), s.px(2.4F), s.px(1.2F));
         OreBox label = new OreBox(content.x(), content.y(), content.w(), s.px(2.0F));
         OreBox field = new OreBox(content.x(), label.bottom() + s.px(0.4F), content.w(), OreTextField.height(s));
         rows.add(row);
         labels.add(label);
         fieldBoxes.add(field);
         widgets.add(OreWidget.of("field:" + i, field));
         cursor += rowH;
      }

      OreBox footer = new OreBox(box.x() + b2, cursor, box.w() - 2.0F * b2, footerH);
      OreBox buttonsArea = footer.inset(s.px(1.6F), b2 + s.px(1.6F), s.px(1.6F), b2 + s.px(1.6F));
      float each = (buttonsArea.w() - s.px(0.8F)) / 2.0F;
      List<OrePlayLayout.Button> buttons = List.of(
         new OrePlayLayout.Button(
            "secondary",
            new OreBox(buttonsArea.x(), buttonsArea.y(), each, s.px(4.8F)),
            this.lang.get("hbui.PlayScreen.serverTab.serverForm" + (this.edit ? ".deleteServerButton" : ".addServerButton")),
            this.edit ? OreTheme.Variant.DESTRUCTIVE : OreTheme.Variant.SECONDARY,
            this.edit ? OreIcon.DELETE : null,
            false
         ),
         new OrePlayLayout.Button(
            "primary",
            new OreBox(buttonsArea.x() + each + s.px(0.8F), buttonsArea.y(), each, s.px(4.8F)),
            this.lang.get("hbui.PlayScreen.serverTab.serverForm" + (this.edit ? ".saveChangesButton" : ".addAndPlayButton")),
            OreTheme.Variant.PRIMARY,
            this.edit ? OreIcon.SAVE : null,
            false
         )
      );

      for (OrePlayLayout.Button button : buttons) {
         widgets.add(OreWidget.of(button.id(), button.box()));
      }

      this.layout = new OreServerFormUi.Layout(header, box, rows, labels, fieldBoxes, footer, buttons, widgets);
      this.modalLayout = this.modal == null ? null : this.modal.layout(this.text, this.width, this.height);
      this.interaction.refresh(this.widgets());
      return this.layout;
   }

   public void size(float newWidth, float newHeight) {
      if (newWidth != this.width || newHeight != this.height || this.layout == null) {
         this.layout(newWidth, newHeight);
      }
   }

   public void render(OrePainter painter, long now) {
      OreServerFormUi.Layout current = this.layout(this.width, this.height);
      OreDraw d = new OreDraw(painter, this.text, this.theme);
      OreScale s = this.text.scale();
      float b2 = s.b2();
      d.fill(new OreBox(0.0F, 0.0F, this.width, this.height), this.theme.overlay50());
      OreTheme.Role bar = this.theme.neutral20();
      OrePlayLayout.Header header = current.header();
      d.fill(header.bar(), bar.background());
      d.fill(header.strip(), bar.shadow());
      d.fill(header.bevel(), bar.bevelBottom());
      OreComponents.barButton(d, header.back(), this.interaction.state("back"));
      d.sprite("arrowBack", header.arrow(), -1);
      d.textCentered(OreText.Type.HEADER5A, d.text().ellipsize(OreText.Type.HEADER5A, this.title(), header.title().w()), header.title(), bar.text(), false);
      d.border(current.box(), this.theme.neutral().border(), b2);
      String[] labelKeys = new String[]{".serverNameLabel", ".serverAddressLabel", ".serverPortLabel"};
      String[] placeholders = new String[]{
         this.lang.get("hbui.PlayScreen.serverTab.serverForm.serverNamePlaceholder"),
         this.lang.get("hbui.PlayScreen.serverTab.serverForm.serverAddressPlaceholder"),
         ""
      };

      for (int i = 0; i < 3; i++) {
         OreBox row = current.rows().get(i);
         d.fill(row, this.theme.neutral().background());
         d.lines(row, this.theme.neutral().bevelTop(), this.theme.neutral().bevelBottom(), b2, true, false, true, false);
         OreBox label = current.labels().get(i);
         d.text(
            OreText.Type.BODY,
            this.lang.get("hbui.PlayScreen.serverTab.serverForm" + labelKeys[i]),
            label.x(),
            label.w(),
            label.y(),
            OreDraw.Align.LEFT,
            this.theme.text()
         );
         OreComponents.State state = this.interaction.state("field:" + i);
         this.fields[i].render(d, this.vars, current.fields().get(i), placeholders[i], this.focusedField == i, state.hovered(), now);
         if (state.focused() && this.focusedField != i) {
            d.focusRing(current.fields().get(i), -1, false);
         }
      }

      OreBox footer = current.footer();
      d.fill(footer, this.theme.neutral().background());
      d.border(footer, this.theme.neutral().border(), b2, true, false, true, false);
      d.lines(footer.inset(0.0F, b2, 0.0F, b2), this.theme.neutral().bevelTop(), this.theme.neutral().bevelBottom(), b2, true, false, true, false);

      for (OrePlayLayout.Button button : current.buttons()) {
         OreComponents.button(d, button, this.interaction.state(button.id()));
      }

      if (this.modal != null && this.modalLayout != null) {
         this.modal.render(d, this.modalLayout, this.interaction::state);
      }
   }

   private String title() {
      return this.lang.get("hbui.PlayScreen.serverTab.serverForm" + (this.edit ? ".editServerTitle" : ".newServerTitle"));
   }

   public void mouseMoved(double x, double y) {
      this.interaction.moved(this.widgets(), x, y);
   }

   public void mousePressed(double x, double y, boolean shift) {
      String id = this.interaction.press(this.widgets(), x, y);
      if (id == null) {
         if (this.modal == null) {
            this.focusField(-1);
         }
      } else if (id.startsWith("field:") && this.layout != null) {
         int index = OrePlayLayout.index(id);
         this.focusField(index);
         OreBox box = this.layout.fields().get(index);
         this.fields[index].click(this.text, this.fields[index].textX(box, this.text.scale(), x), shift);
         this.interaction.cancelPress();
      } else {
         this.actions.click();
      }
   }

   public void mouseReleased(double x, double y) {
      String activated = this.interaction.release(this.widgets(), x, y);
      if (activated != null) {
         this.activate(activated);
      }
   }

   public boolean typed(String characters) {
      if (this.modal == null && this.focusedField >= 0) {
         if (this.fields[this.focusedField].type(characters)) {
            this.dirty = true;
         }

         return true;
      } else {
         return false;
      }
   }

   public boolean key(OreKey key, boolean shift, boolean control, Supplier<String> clipboard, Consumer<String> copy) {
      if (this.modal != null) {
         if (key == OreKey.ESCAPE) {
            this.closeModal();
            return true;
         }
      } else if (this.focusedField >= 0) {
         switch (key) {
            case ESCAPE:
            case ENTER:
               this.focusField(-1);
               this.interaction.focus("field:" + Math.max(0, this.lastField), true);
               return true;
            case TAB:
               int next = Math.floorMod(this.focusedField + (shift ? -1 : 1), 3);
               this.focusField(next);
               this.interaction.focus("field:" + next, true);
               return true;
            default:
               String before = this.fields[this.focusedField].text();
               if (this.fields[this.focusedField].key(key, shift, control, clipboard, copy)) {
                  if (!before.equals(this.fields[this.focusedField].text())) {
                     this.dirty = true;
                  }

                  return true;
               }
         }
      } else if (key == OreKey.ESCAPE) {
         this.actions.click();
         this.back();
         return true;
      }

      if (key == OreKey.ENTER || key == OreKey.SPACE) {
         String focused = this.interaction.focused();
         if (focused != null && this.widgets().get(focused) != null) {
            if (!focused.startsWith("field:")) {
               this.actions.click();
            }

            this.activate(focused);
            return true;
         } else {
            return false;
         }
      } else if (key != OreKey.TAB && key != OreKey.LEFT && key != OreKey.RIGHT && key != OreKey.UP && key != OreKey.DOWN) {
         return false;
      } else {
         this.interaction.navigate(this.widgets(), key, shift);
         return true;
      }
   }

   public void focusField(int index) {
      if (this.focusedField == 2 && index != 2) {
         this.fixPort();
      }

      if (index >= 0) {
         this.lastField = index;
      }

      this.focusedField = index;
   }

   void fixPort() {
      String port = this.fields[2].text();
      if (port.isEmpty()) {
         this.fields[2].text(String.valueOf(19132));
      } else if (port.length() > 5 || Long.parseLong(port) > 65535L) {
         this.fields[2].text("65535");
      }
   }

   public void activate(String id) {
      if (this.modal != null) {
         int choice = OreModal.choice(id);
         if (id.equals("modal:close")) {
            this.closeModal();
         } else if (choice >= 0 && choice < this.modalChoices.size()) {
            Runnable run = this.modalChoices.get(choice);
            this.closeModal();
            run.run();
         }
      } else {
         switch (id) {
            case "back":
               this.back();
               break;
            case "secondary":
               if (this.edit) {
                  this.confirmDelete();
               } else {
                  this.submit(false);
               }
               break;
            case "primary":
               this.submit(true);
               break;
            default:
               if (id.startsWith("field:")) {
                  this.focusField(OrePlayLayout.index(id));
               }
         }
      }
   }

   public void back() {
      if (!this.dirty) {
         this.actions.back();
      } else {
         String prefix = "hbui.PlayScreen.serverTab." + (this.edit ? "unsavedChanges" : "unsavedAddChanges");
         if (this.edit) {
            this.openModal(
               new OreModal(
                  this.lang.get(prefix + ".title"),
                  List.of(this.lang.get(prefix + ".message")),
                  List.of(
                     new OreModal.Choice(this.lang.get(prefix + ".discardChangesButton"), OreTheme.Variant.SECONDARY),
                     new OreModal.Choice(this.lang.get(prefix + ".saveChangesButton"), OreTheme.Variant.PRIMARY)
                  ),
                  false
               ),
               List.of(this.actions::back, () -> this.submit(true))
            );
         } else {
            this.openModal(
               new OreModal(
                  this.lang.get(prefix + ".title"),
                  List.of(this.lang.get(prefix + ".message")),
                  List.of(
                     new OreModal.Choice(this.lang.get(prefix + ".discardChangesButton"), OreTheme.Variant.SECONDARY),
                     new OreModal.Choice(this.lang.get(prefix + ".saveChangesButton"), OreTheme.Variant.PRIMARY)
                  ),
                  false
               ),
               List.of(this.actions::back, () -> {})
            );
         }
      }
   }

   private void confirmDelete() {
      String prefix = "hbui.PlayScreen.serverTab.confirmDeletion";
      this.openModal(
         new OreModal(
            this.lang.get(prefix + ".title"),
            List.of(this.lang.get(prefix + ".message")),
            List.of(
               new OreModal.Choice(this.lang.get(prefix + ".goBackButton"), OreTheme.Variant.SECONDARY),
               new OreModal.Choice(this.lang.get(prefix + ".deleteButton"), OreTheme.Variant.DESTRUCTIVE)
            ),
            false
         ),
         List.of(() -> {}, this.actions::delete)
      );
   }

   void submit(boolean primary) {
      this.fixPort();
      OreServerFormUi.Error error = this.check();
      if (error != null) {
         this.openModal(
            new OreModal(
               this.lang.get("hbui.PlayScreen.serverTab.addServerError.title"),
               List.of(this.lang.get(error.key())),
               List.of(new OreModal.Choice(this.lang.get("hbui.PlayScreen.serverTab.addServerError.closeButton"), OreTheme.Variant.SECONDARY)),
               false
            ),
            List.of(() -> {})
         );
      } else {
         BedrockAddress address = this.address();
         String name = this.fields[0].text().strip();
         if (this.edit) {
            this.actions.save(name, address);
         } else {
            this.actions.add(name, address, primary);
         }
      }
   }

   public BedrockAddress address() {
      int port = BedrockAddress.typedPort(this.fields[2].text());
      return BedrockAddress.fromForm(this.fields[1].text(), port < 0 ? 19132 : port);
   }

   @Nullable
   public OreServerFormUi.Error check() {
      if (this.fields[0].text().isBlank()) {
         return OreServerFormUi.Error.NAME_EMPTY;
      } else {
         String host = BedrockAddress.withoutPrefix(this.fields[1].text());
         if (host.isEmpty()) {
            return OreServerFormUi.Error.ADDRESS_EMPTY;
         } else if (!validHost(host)) {
            return OreServerFormUi.Error.INVALID_ADDRESS;
         } else if (BedrockAddress.typedPort(this.fields[2].text()) < 0) {
            return OreServerFormUi.Error.INVALID_PORT;
         } else {
            BedrockAddress address = this.address();

            for (BedrockAddress other : this.actions.others()) {
               if (other.sameServer(address)) {
                  return OreServerFormUi.Error.DUPLICATE;
               }
            }

            return null;
         }
      }
   }

   static boolean validHost(String typed) {
      if (typed.length() > 2 && typed.startsWith("[") && typed.endsWith("]")) {
         String host = typed.substring(1, typed.length() - 1);
         return host.indexOf(58) >= 0 && host.chars().allMatch(c -> Character.digit(c, 16) >= 0 || c == 58 || c == 46);
      } else if (typed.indexOf(32) < 0 && typed.indexOf(91) < 0 && typed.indexOf(93) < 0 && typed.indexOf(47) < 0) {
         int colons = (int)typed.chars().filter(c -> c == 58).count();
         if (colons == 1) {
            return false;
         } else {
            return colons > 1
               ? typed.chars().allMatch(c -> Character.digit(c, 16) >= 0 || c == 58 || c == 46)
               : typed.chars().allMatch(c -> Character.isLetterOrDigit(c) || c == 46 || c == 45 || c == 95);
         }
      } else {
         return false;
      }
   }

   public void openModal(OreModal newModal, List<Runnable> choices) {
      this.modal = newModal;
      this.modalChoices = List.copyOf(choices);
      this.interaction.cancelPress();
      this.focusedField = -1;
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

   public boolean dirty() {
      return this.dirty;
   }

   public interface Actions {
      void add(String var1, BedrockAddress var2, boolean var3);

      void save(String var1, BedrockAddress var2);

      void delete();

      void back();

      List<BedrockAddress> others();

      void click();
   }

   public static enum Error {
      NAME_EMPTY(".nameError"),
      ADDRESS_EMPTY(".addressError"),
      INVALID_ADDRESS(".ipAddressError"),
      INVALID_PORT(".portError"),
      DUPLICATE(".duplicateAddressAndPortError");

      private final String key;

      private Error(String key) {
         this.key = key;
      }

      public String key() {
         return "hbui.PlayScreen.serverTab.addServerError" + this.key;
      }
   }

   public record Layout(
      OrePlayLayout.Header header,
      OreBox box,
      List<OreBox> rows,
      List<OreBox> labels,
      List<OreBox> fields,
      OreBox footer,
      List<OrePlayLayout.Button> buttons,
      OreWidgets widgets
   ) {
   }
}
