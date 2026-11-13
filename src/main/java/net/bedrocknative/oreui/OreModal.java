package net.bedrocknative.oreui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

public final class OreModal {
   private final String title;
   private final List<String> paragraphs;
   private final List<OreModal.Choice> choices;
   private final boolean horizontal;

   public OreModal(String title, List<String> paragraphs, List<OreModal.Choice> choices, boolean horizontal) {
      this.title = title;
      this.paragraphs = List.copyOf(paragraphs);
      this.choices = List.copyOf(choices);
      this.horizontal = horizontal;
   }

   public String title() {
      return this.title;
   }

   public List<OreModal.Choice> choices() {
      return this.choices;
   }

   public OreModal.Layout layout(OreText text, float width, float height) {
      OreScale s = text.scale();
      float b2 = s.b2();
      float boxW = Math.min(s.px(47.6F), Math.max(0.0F, width - 2.0F * s.px(1.6F)));
      float innerW = boxW - 2.0F * b2;
      float barH = s.px(4.8F);
      float textInnerW = innerW - 2.0F * s.px(1.6F);
      List<List<String>> wrapped = new ArrayList<>();
      float contentH = 0.0F;

      for (String paragraph : this.paragraphs) {
         List<String> lines = text.wrap(OreText.Type.CAPTION_LONG, paragraph, textInnerW, 12);
         wrapped.add(lines);
         contentH += 2.0F * s.px(1.6F) + lines.size() * text.linePx(OreText.Type.CAPTION_LONG);
      }

      float buttonH = s.px(4.8F);
      float gap = s.px(0.4F);
      float buttonsH = 2.0F * s.px(1.6F)
         + (!this.horizontal && !this.choices.isEmpty() ? this.choices.size() * buttonH + (this.choices.size() - 1) * gap : buttonH);
      float boxH = 2.0F * b2 + barH + b2 + contentH + buttonsH;
      float x = (width - boxW) / 2.0F;
      float y = Math.max(s.px(1.2F), (height - boxH) / 2.0F);
      OreBox box = new OreBox(x, y, boxW, boxH);
      OreBox titleBar = new OreBox(x + b2, y + b2, innerW, barH);
      OreBox close = new OreBox(titleBar.right() - s.px(0.4F) - s.px(4.0F), titleBar.y() + (barH - s.px(4.0F)) / 2.0F, s.px(4.0F), s.px(4.0F));
      float cross = 7.0F * b2;
      OreBox closeIcon = new OreBox(close.centerX() - cross / 2.0F, close.centerY() - cross / 2.0F, cross, cross);
      OreBox titleBox = new OreBox(titleBar.x() + s.px(0.4F) + s.px(4.0F), titleBar.y(), titleBar.w() - 2.0F * (s.px(0.4F) + s.px(4.0F)), barH);
      float cursor = titleBar.bottom() + b2;
      OreBox content = new OreBox(x + b2, cursor, innerW, contentH);
      List<OreBox> paragraphBoxes = new ArrayList<>();

      for (List<String> lines : wrapped) {
         float h = lines.size() * text.linePx(OreText.Type.CAPTION_LONG);
         paragraphBoxes.add(new OreBox(content.x() + s.px(1.6F), cursor + s.px(1.6F), textInnerW, h));
         cursor += 2.0F * s.px(1.6F) + h;
      }

      OreBox buttonsArea = new OreBox(x + b2, cursor, innerW, buttonsH);
      List<OrePlayLayout.Button> buttons = new ArrayList<>();
      float bx = buttonsArea.x() + s.px(1.6F);
      float by = buttonsArea.y() + s.px(1.6F);
      float bw = buttonsArea.w() - 2.0F * s.px(1.6F);

      for (int i = 0; i < this.choices.size(); i++) {
         OreModal.Choice choice = this.choices.get(i);
         OreBox buttonBox;
         if (this.horizontal) {
            float each = (bw - (this.choices.size() - 1) * gap) / this.choices.size();
            buttonBox = new OreBox(bx + i * (each + gap), by, each, buttonH);
         } else {
            buttonBox = new OreBox(bx, by + i * (buttonH + gap), bw, buttonH);
         }

         buttons.add(new OrePlayLayout.Button("modal:" + i, buttonBox, choice.text(), choice.variant(), null, choice.variant() == OreTheme.Variant.HERO));
      }

      return new OreModal.Layout(
         new OreBox(0.0F, 0.0F, width, height), box, titleBar, titleBox, close, closeIcon, content, wrapped, paragraphBoxes, buttonsArea, buttons
      );
   }

   public static void widgets(OreModal.Layout layout, OreWidgets widgets) {
      for (OrePlayLayout.Button button : layout.buttons()) {
         widgets.add(OreWidget.of(button.id(), button.box()));
      }

      widgets.add(OreWidget.of("modal:close", layout.close()));
   }

   public void render(OreDraw d, OreModal.Layout layout, Function<String, OreComponents.State> states) {
      OreTheme theme = d.theme();
      float b2 = d.scale().b2();
      d.fill(layout.overlay(), -1291845632);
      d.border(layout.box(), theme.neutral().border(), b2);
      OreTheme.Role bar = theme.neutral();
      d.fill(layout.titleBar(), bar.background());
      d.lines(layout.titleBar(), bar.specularTop(), bar.specularBottom(), b2, true, true, true, true);
      d.fill(new OreBox(layout.titleBar().x(), layout.titleBar().bottom(), layout.titleBar().w(), b2), bar.shadow());
      d.textCentered(OreText.Type.BODY, d.text().ellipsize(OreText.Type.BODY, this.title, layout.title().w()), layout.title(), theme.text(), false);
      OreComponents.State close = states.apply("modal:close");
      if (close.pressed()) {
         d.fill(layout.close(), bar.pressed());
      } else if (close.hovered()) {
         d.fill(layout.close(), bar.hovered());
      }

      d.sprite("crossWhite", layout.closeIcon(), -1);
      if (close.focused()) {
         d.focusRing(layout.close(), bar.outline(), true);
      }

      d.fill(layout.content(), theme.neutral80().background());

      for (int i = 0; i < layout.paragraphs().size(); i++) {
         OreBox box = layout.paragraphBoxes().get(i);
         d.lines(OreText.Type.CAPTION_LONG, layout.paragraphs().get(i), box.x(), box.w(), box.y(), OreDraw.Align.CENTER, theme.text());
      }

      d.fill(layout.buttonsArea(), bar.background());
      d.lines(layout.buttonsArea(), bar.bevelTop(), bar.bevelBottom(), b2, true, false, true, false);

      for (OrePlayLayout.Button button : layout.buttons()) {
         OreComponents.button(d, button, states.apply(button.id()));
      }
   }

   public static int choice(@Nullable String id) {
      if (id != null && id.startsWith("modal:") && !id.equals("modal:close")) {
         try {
            return Integer.parseInt(id.substring("modal:".length()));
         } catch (NumberFormatException e) {
            return -1;
         }
      } else {
         return -1;
      }
   }

   public record Choice(String text, OreTheme.Variant variant) {
   }

   public record Layout(
      OreBox overlay,
      OreBox box,
      OreBox titleBar,
      OreBox title,
      OreBox close,
      OreBox closeIcon,
      OreBox content,
      List<List<String>> paragraphs,
      List<OreBox> paragraphBoxes,
      OreBox buttonsArea,
      List<OrePlayLayout.Button> buttons
   ) {
   }
}
