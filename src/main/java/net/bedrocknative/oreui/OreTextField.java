package net.bedrocknative.oreui;

import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

public final class OreTextField {
   private final int maxLength;
   private final boolean digits;
   private String text;
   private int cursor;
   private int anchor;
   private float scroll;

   public OreTextField(String text, int maxLength, boolean digits) {
      this.maxLength = maxLength;
      this.digits = digits;
      this.text = this.filter(text, false);
      this.cursor = this.text.length();
      this.anchor = this.cursor;
   }

   public String text() {
      return this.text;
   }

   public void text(String value) {
      this.text = this.filter(value, true);
      this.cursor = Math.min(this.cursor, this.text.length());
      this.anchor = this.cursor;
   }

   public int cursor() {
      return this.cursor;
   }

   public boolean hasSelection() {
      return this.cursor != this.anchor;
   }

   private String filter(String value) {
      return this.filter(value, true);
   }

   private String filter(String value, boolean capped) {
      StringBuilder out = new StringBuilder();

      for (int i = 0; i < value.length(); i++) {
         char c = value.charAt(i);
         if (c >= ' ' && c != 127 && (!this.digits || c >= '0' && c <= '9')) {
            out.append(c);
         }
      }

      return capped && out.length() > this.maxLength ? out.substring(0, this.maxLength) : out.toString();
   }

   public boolean type(String typed) {
      String clean = this.filter(typed);
      if (clean.isEmpty()) {
         return false;
      }

      int from = Math.min(this.cursor, this.anchor);
      int to = Math.max(this.cursor, this.anchor);
      String next = this.text.substring(0, from) + clean + this.text.substring(to);
      if (next.length() > this.maxLength) {
         int room = this.maxLength - (this.text.length() - (to - from));
         if (room <= 0) {
            return false;
         }

         clean = clean.substring(0, Math.min(clean.length(), room));
         next = this.text.substring(0, from) + clean + this.text.substring(to);
      }

      this.text = next;
      this.cursor = from + clean.length();
      this.anchor = this.cursor;
      return true;
   }

   public boolean key(OreKey key, boolean shift, boolean control, Supplier<String> clipboard, Consumer<String> copy) {
      switch (key) {
         case LEFT:
            this.move(control ? this.wordStart(this.cursor) : Math.max(0, this.cursor - 1), shift);
            break;
         case RIGHT:
            this.move(control ? this.wordEnd(this.cursor) : Math.min(this.text.length(), this.cursor + 1), shift);
            break;
         case HOME:
            this.move(0, shift);
            break;
         case END:
            this.move(this.text.length(), shift);
            break;
         case BACKSPACE:
            if (this.hasSelection()) {
               this.deleteSelection();
            } else if (this.cursor > 0) {
               int from = control ? this.wordStart(this.cursor) : this.cursor - 1;
               this.text = this.text.substring(0, from) + this.text.substring(this.cursor);
               this.cursor = from;
               this.anchor = this.cursor;
            }
            break;
         case DELETE:
            if (this.hasSelection()) {
               this.deleteSelection();
            } else if (this.cursor < this.text.length()) {
               int to = control ? this.wordEnd(this.cursor) : this.cursor + 1;
               this.text = this.text.substring(0, this.cursor) + this.text.substring(to);
            }
            break;
         case SELECT_ALL:
            this.anchor = 0;
            this.cursor = this.text.length();
            break;
         case COPY:
            if (this.hasSelection()) {
               copy.accept(this.selection());
            }
            break;
         case CUT:
            if (this.hasSelection()) {
               copy.accept(this.selection());
               this.deleteSelection();
            }
            break;
         case PASTE:
            this.type(clipboard.get());
            break;
         default:
            return false;
      }

      return true;
   }

   private String selection() {
      return this.text.substring(Math.min(this.cursor, this.anchor), Math.max(this.cursor, this.anchor));
   }

   private void deleteSelection() {
      int from = Math.min(this.cursor, this.anchor);
      int to = Math.max(this.cursor, this.anchor);
      this.text = this.text.substring(0, from) + this.text.substring(to);
      this.cursor = from;
      this.anchor = from;
   }

   private void move(int to, boolean shift) {
      this.cursor = to;
      if (!shift) {
         this.anchor = to;
      }
   }

   private int wordStart(int from) {
      int i = from;

      while (i > 0 && this.text.charAt(i - 1) == ' ') {
         i--;
      }

      while (i > 0 && this.text.charAt(i - 1) != ' ') {
         i--;
      }

      return i;
   }

   private int wordEnd(int from) {
      int i = from;

      while (i < this.text.length() && this.text.charAt(i) == ' ') {
         i++;
      }

      while (i < this.text.length() && this.text.charAt(i) != ' ') {
         i++;
      }

      return i;
   }

   public void click(OreText layout, float x, boolean shift) {
      float size = layout.sizePx(OreText.Type.TEXT_FIELD);
      float spacing = layout.letterSpacingPx(OreText.Type.TEXT_FIELD);
      int best = 0;
      float bestDistance = Math.abs(x);
      int offset = 0;

      for (OreText.Placed glyph : layout.place(OreText.Type.TEXT_FIELD, this.text)) {
         offset += Character.charCount(glyph.codePoint());
         float end = glyph.x() + glyph.font().advance(glyph.codePoint()) * size + spacing;
         if (Math.abs(x - end) < bestDistance) {
            bestDistance = Math.abs(x - end);
            best = offset;
         }
      }

      this.move(best, shift);
   }

   public static OreBox content(OreBox box, OreScale scale) {
      return box.inset(scale.px(0.2F) + scale.px(1.4F), scale.px(0.6F) + scale.px(1.2F), scale.px(0.2F) + scale.px(1.2F), scale.px(0.2F) + scale.px(0.8F));
   }

   public static float height(OreScale scale) {
      return scale.px(5.6F);
   }

   public void render(OreDraw d, OreThemeVars vars, OreBox box, String placeholder, boolean focused, boolean hovered, long now) {
      OreScale s = d.scale();
      OreBorderImage image = vars.borderImage(focused ? "baseTextFieldFocused" : (hovered ? "baseTextFieldHovered" : "baseTextFieldDefault"));
      if (image == null) {
         image = new OreBorderImage(
            "baseTextField_neutral80", 3, 1, 1, 1, true, new float[]{0.6F, 0.2F, 0.2F, 0.2F}, new float[4], new float[]{0.6F, 0.2F, 0.2F, 0.2F}
         );
      }

      d.borderImage(image, box);
      OreBox content = content(box, s);
      OreText layout = d.text();
      float lineTop = content.y() + (content.h() - layout.linePx(OreText.Type.TEXT_FIELD)) / 2.0F;
      float caretX = layout.width(OreText.Type.TEXT_FIELD, this.text.substring(0, this.cursor));
      if (caretX - this.scroll > content.w()) {
         this.scroll = caretX - content.w();
      } else if (caretX - this.scroll < 0.0F) {
         this.scroll = caretX;
      }

      this.scroll = Math.max(0.0F, Math.min(this.scroll, Math.max(0.0F, layout.width(OreText.Type.TEXT_FIELD, this.text) - content.w() + s.b2())));
      d.painter().pushClip(content.outset(s.b2()));

      try {
         if (this.text.isEmpty()) {
            d.text(
               OreText.Type.TEXT_FIELD,
               placeholder,
               content.x(),
               content.w(),
               lineTop,
               OreDraw.Align.LEFT,
               vars.color("baseTextFieldPlaceholderColor", -5131595)
            );
         } else {
            if (focused && this.hasSelection()) {
               float x0 = layout.width(OreText.Type.TEXT_FIELD, this.text.substring(0, Math.min(this.cursor, this.anchor))) - this.scroll;
               float x1 = layout.width(OreText.Type.TEXT_FIELD, this.text.substring(0, Math.max(this.cursor, this.anchor))) - this.scroll;
               d.fill(new OreBox(content.x() + x0, lineTop + s.px(0.4F), x1 - x0, layout.linePx(OreText.Type.TEXT_FIELD) - s.px(0.8F)), -1725010971);
            }

            d.text(
               OreText.Type.TEXT_FIELD,
               this.text,
               content.x() - this.scroll,
               content.w() + this.scroll,
               lineTop,
               OreDraw.Align.LEFT,
               vars.color("baseTextFieldColor", -1)
            );
         }

         if (focused && now / 500L % 2L == 0L) {
            float x = content.x() + caretX - this.scroll;
            d.fill(
               new OreBox(x, lineTop + s.px(0.4F), s.b2(), layout.linePx(OreText.Type.TEXT_FIELD) - s.px(0.8F)),
               vars.color("baseTextFieldCaretColor", -9649335)
            );
         }
      } finally {
         d.painter().popClip();
      }
   }

   public float textX(OreBox box, OreScale scale, double x) {
      return (float)x - content(box, scale).x() + this.scroll;
   }

   @Override
   public String toString() {
      return this.text;
   }

   public @Nullable String selected() {
      return this.hasSelection() ? this.selection() : null;
   }
}
