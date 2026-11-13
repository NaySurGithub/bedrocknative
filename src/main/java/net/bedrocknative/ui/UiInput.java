package net.bedrocknative.ui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Map.Entry;
import java.util.function.Predicate;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.perf.RenderSwitches;
import org.jspecify.annotations.Nullable;

public final class UiInput implements UiScreenInstance.LayoutListener {
   public static final String SELECT = "button.menu_select";
   public static final String OK = "button.menu_ok";
   public static final String CANCEL = "button.menu_cancel";
   public static final String EXIT = "button.menu_exit";
   public static final String DROPDOWN_EXIT = "button.dropdown_exit";
   static final float MIN_BOX = 13.0F;
   static final String SCROLL_BAR_VISIBLE = "#scroll_bar_visible";
   static final int MAX_STEP_MARKS = 64;
   private static final float EPSILON = 0.01F;
   private final UiScreenInstance instance;
   private UiInput.@Nullable TextEditor editor;
   private UiInput.@Nullable Sounds sounds;
   private @Nullable UiControl hovered;
   private @Nullable UiControl hoveredThrough;
   private @Nullable UiControl captured;
   private @Nullable UiControl focused;
   private @Nullable UiControl modalAtPress;
   private UiInput.Drag drag = UiInput.Drag.NONE;
   private float grab;
   private float cursorX = Float.NaN;
   private float cursorY = Float.NaN;
   private final List<UiControl> followers = new ArrayList<>();
   private long followersVersion = -1L;
   private List<UiControl> topmost = List.of();
   private long topmostPlacement = -1L;
   private long topmostTree = -1L;
   private @Nullable UiControl topModal;
   private boolean topModalKnown;
   private @Nullable UiControl withinFor;
   private List<UiControl> withinOrder = List.of();
   private long withinPlacement = -1L;
   private long withinTree = -1L;
   private final Map<UiControl, String> stepMarks = new IdentityHashMap<>();
   private @Nullable UiDrawList keyedList;
   private long listKey = -2L;

   private UiInput(UiScreenInstance instance) {
      this.instance = instance;
   }

   public static UiInput attach(UiScreenInstance instance) {
      UiInput input = new UiInput(instance);
      instance.layoutListener(input);
      return input;
   }

   public void editor(UiInput.@Nullable TextEditor textEditor) {
      this.editor = textEditor;
   }

   public void sounds(UiInput.@Nullable Sounds player) {
      this.sounds = player;
   }

   public @Nullable UiControl hovered() {
      return this.hovered;
   }

   public @Nullable UiControl hoveredThrough() {
      return this.hoveredThrough;
   }

   public @Nullable UiControl focused() {
      return this.focused;
   }

   public void mouseMoved(float x, float y) {
      this.follow(x, y);
      if (this.drag == UiInput.Drag.SLIDER && this.captured != null) {
         this.slide(this.captured, x, y);
      } else if (this.drag == UiInput.Drag.BOX && this.captured != null) {
         this.dragBox(this.captured, y);
      } else {
         UiControl modal = this.topModal();
         UiControl target = owner(this.pick(x, y, modal));
         UiControl hover = target != null && hoverable(target) && target.enabled ? target : null;
         if (hover != this.hovered) {
            if (this.hovered != null && this.hovered != this.captured && this.hovered != this.focused) {
               this.state(this.hovered, "default");
            }

            this.hovered = hover;
         }

         if (this.hovered != null && this.hovered != this.captured && this.hovered != this.focused) {
            this.state(this.hovered, "hover");
         }

         if (this.captured != null && this.drag == UiInput.Drag.PRESS) {
            this.state(this.captured, target == this.captured ? "pressed" : "default");
         }

         UiControl passing = this.passingThrough(x, y, modal, target);
         if (passing != this.hoveredThrough) {
            if (this.hoveredThrough != null) {
               this.state(this.hoveredThrough, "default");
            }

            this.hoveredThrough = passing;
         }

         if (passing != null) {
            this.state(passing, "hover");
         }
      }
   }

   public void hover(UiControl button, boolean on) {
      if (button != this.captured && button != this.focused) {
         if (on) {
            this.state(button, "hover");
         } else if (button != this.hovered) {
            this.state(button, "default");
         }
      }
   }

   public boolean mouseDown(float x, float y, int button) {
      if (button != 0) {
         return false;
      }

      UiControl modal = this.topModal();
      UiControl target = this.pick(x, y, modal);
      if (modal != null && target == null && !contains(modal, x, y)) {
         this.focus(null);
         this.global(modal, "button.menu_select");
         return true;
      }

      UiControl owner = owner(target);
      if (this.focused != null && owner != this.focused) {
         this.focus(null);
      }

      this.modalAtPress = modal;
      if (owner == null) {
         return modal != null;
      }

      if (!owner.enabled) {
         return true;
      }

      switch (owner.type) {
         case EDIT_BOX:
            this.focus(owner);
            break;
         case SLIDER:
            this.captured = owner;
            this.drag = UiInput.Drag.SLIDER;
            this.state(owner, "hover");
            this.slide(owner, x, y);
            break;
         case SCROLLBAR_BOX:
            this.captured = owner;
            this.drag = UiInput.Drag.BOX;
            this.grab = y - owner.y;
            break;
         case SCROLL_TRACK:
         case SCROLLBAR_TRACK:
            this.jump(owner, y);
            this.drag = UiInput.Drag.TRACK;
            break;
         default:
            this.captured = owner;
            this.drag = UiInput.Drag.PRESS;
            this.state(owner, "pressed");
      }

      return true;
   }

   public boolean mouseUp(float x, float y, int button) {
      if (button != 0) {
         return false;
      }

      UiControl control = this.captured;
      UiInput.Drag was = this.drag;
      this.captured = null;
      this.drag = UiInput.Drag.NONE;
      boolean handled = control != null;
      if (control != null && was == UiInput.Drag.PRESS) {
         boolean inside = owner(this.pick(x, y, this.modalAtPress)) == control;
         this.state(control, inside ? "hover" : "default");
         if (inside && control.enabled) {
            this.activate(control);
         }
      } else if (control != null && was == UiInput.Drag.SLIDER) {
         this.slide(control, x, y);
         this.state(control, "default");
      }

      UiControl modal = this.modalAtPress;
      this.modalAtPress = null;
      if (modal != null && modal.effectiveVisible && (was == UiInput.Drag.PRESS || was == UiInput.Drag.NONE)) {
         this.global(modal, "button.menu_select");
         handled = true;
      }

      this.hovered = null;
      this.mouseMoved(x, y);
      return handled;
   }

   public boolean mouseDragged(float x, float y, int button) {
      if (button != 0) {
         this.follow(x, y);
         return false;
      } else {
         this.mouseMoved(x, y);
         return this.captured != null;
      }
   }

   public @Nullable UiControl topmost(float x, float y) {
      return owner(this.pick(x, y, this.topModal()));
   }

   private void follow(float x, float y) {
      this.cursorX = x;
      this.cursorY = y;

      for (UiControl control : this.followers()) {
         if (control.effectiveVisible && this.centre(control)) {
            this.instance.rearrange(control);
         }
      }
   }

   private boolean centre(UiControl control) {
      if (Float.isNaN(this.cursorX)) {
         return false;
      }

      float baseX = control.x - control.inputShiftX + control.w / 2.0F;
      float baseY = control.y - control.inputShiftY + control.h / 2.0F;
      return shift(control, this.cursorX - baseX, this.cursorY - baseY);
   }

   private List<UiControl> followers() {
      if (this.followersVersion != this.instance.treeVersion()) {
         this.followers.clear();
         UiControl root = this.instance.root();
         if (root != null) {
            root.walk(control -> {
               if (control.followsCursor()) {
                  this.followers.add(control);
               }
            });
         }

         this.followersVersion = this.instance.treeVersion();
      }

      return this.followers;
   }

   public boolean scroll(float x, float y, float notches) {
      UiControl view = this.scrollViewAt(x, y, this.topModal());
      if (view == null) {
         return false;
      }

      float speed = (float)number(view.props.get("scroll_speed"), 15.0);
      view.scroll -= notches * speed;
      this.instance.invalidate();
      return true;
   }

   public boolean button(String id) {
      UiControl box = this.focused;
      if (box != null) {
         for (JsonObject mapping : box.buttonMappings) {
            if (id.equals(string(mapping.get("from_button_id")))
               && truthy(mapping.get("handle_deselect"))
               && ("global".equals(string(mapping.get("mapping_type"))) || "pressed".equals(string(mapping.get("mapping_type"))))) {
               this.focus(null);
               if (!mapping.has("consume_event") || truthy(mapping.get("consume_event"))) {
                  return true;
               }
               break;
            }
         }
      }

      UiControl modal = this.topModal();
      if (modal != null) {
         this.global(modal, id);
         return true;
      }

      for (UiControl control : this.topmostFirst(null)) {
         for (JsonObject mapping : control.buttonMappings) {
            if (id.equals(string(mapping.get("from_button_id")))
               && "global".equals(string(mapping.get("mapping_type")))
               && !mapping.has("handle_select")
               && !mapping.has("handle_deselect")) {
               this.dispatch(string(mapping.get("to_button_id")), control, mapping);
               if (!mapping.has("consume_event") || truthy(mapping.get("consume_event"))) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   public void focus(@Nullable UiControl editBox) {
      if (this.focused != editBox) {
         UiControl old = this.focused;
         this.focused = null;
         if (old != null) {
            this.state(old, "default");
            if (this.editor != null) {
               this.editor.end(old);
            }
         }

         if (editBox != null && editBox.type == UiType.EDIT_BOX && editBox.enabled) {
            this.focused = editBox;
            this.state(editBox, "pressed");
            if (this.editor != null) {
               this.editor.begin(editBox, this.text(editBox), this.maxLength(editBox));
            }
         }
      }
   }

   public String text(UiControl editBox) {
      UiControl label = this.textControl(editBox);
      UiValue value = label == null ? null : label.bag.get("#item_name");
      return value != null && !value.isNull() ? value.asString() : "";
   }

   public void setText(UiControl editBox, String text) {
      UiControl label = this.textControl(editBox);
      if (label != null) {
         String value = cut(filter(text, string(editBox.props.get("text_type"))), this.maxLength(editBox));
         this.write(label, "#item_name", UiValue.of(value));
      }
   }

   static String filter(String text, @Nullable String textType) {
      if (!"NumberChars".equals(textType)) {
         return text;
      }

      StringBuilder digits = new StringBuilder(text.length());
      text.chars().filter(c -> c >= 48 && c <= 57).forEach(c -> digits.append((char)c));
      return digits.toString();
   }

   public void type(String text) {
      if (this.focused != null) {
         this.setText(this.focused, this.text(this.focused) + text);
      }
   }

   public void erase() {
      if (this.focused != null) {
         String text = this.text(this.focused);
         if (!text.isEmpty()) {
            this.setText(this.focused, text.substring(0, text.offsetByCodePoints(text.length(), -1)));
         }
      }
   }

   public int maxLength(UiControl editBox) {
      double length = number(editBox.props.get("max_length"), 0.0);
      return length > 0.0 ? (int)Math.min(length, 32767.0) : 32767;
   }

   public float @Nullable [] caret(int cursor) {
      UiControl box = this.focused;
      UiControl label = box == null ? null : this.textControl(box);
      if (label != null && label.effectiveVisible) {
         String text = label.text;
         String before = text.substring(0, Math.max(0, Math.min(cursor, text.length())));
         UiTextMeasurer measurer = this.instance.environment.measurer();
         float width = measurer.width(before, label.font);
         float full = measurer.width(text, label.font);

         float x = switch (label.alignment == null ? "left" : label.alignment) {
            case "center" -> label.x + (label.w - full) / 2.0F;
            case "right" -> label.x + label.w - full;
            default -> label.x;
         };
         return new float[]{x + width, label.y, 1.0F, measurer.lineHeight(label.font)};
      } else {
         return null;
      }
   }

   public @Nullable UiControl textControl(UiControl editBox) {
      String name = string(editBox.props.get("text_control"));
      return find(editBox, name == null ? "display_text" : name);
   }

   private static String cut(String text, int maxLength) {
      return text.length() <= maxLength ? text : text.substring(0, Character.isHighSurrogate(text.charAt(maxLength - 1)) ? maxLength - 1 : maxLength);
   }

   public boolean reveal(UiControl control) {
      for (UiControl view = control.parent; view != null; view = view.parent) {
         if (view.type == UiType.SCROLL_VIEW) {
            UiControl port = part(view, "scroll_view_port");
            if (port != null) {
               float before = view.scroll;
               if (control.y < port.y) {
                  view.scroll = view.scroll - (port.y - control.y);
               } else if (control.y + control.h > port.y + port.h) {
                  view.scroll = view.scroll + Math.min(control.y - port.y, control.y + control.h - port.y - port.h);
               }

               if (view.scroll != before) {
                  this.instance.invalidate();
                  return true;
               }
            }
         }
      }

      return false;
   }

   public boolean revealItem(UiControl grid, int index) {
      UiControl view = ancestor(grid, UiType.SCROLL_VIEW);
      UiControl port = view == null ? null : part(view, "scroll_view_port");
      if (port != null && !(grid.gridCellH <= 0.0F)) {
         int columns = Math.max(1, grid.gridColumnsUsed);
         float top = grid.y + index / columns * grid.gridCellH;
         float bottom = top + grid.gridCellH;
         float before = view.scroll;
         if (top < port.y) {
            view.scroll = view.scroll - (port.y - top);
         } else if (bottom > port.y + port.h) {
            view.scroll = view.scroll + (bottom - port.y - port.h);
         }

         if (view.scroll != before) {
            this.instance.invalidate();
            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   @Override
   public boolean afterLayout() {
      UiControl root = this.instance.root();
      return root != null && this.place(root);
   }

   private boolean place(UiControl c) {
      if (!c.ownVisible) {
         return false;
      }

      boolean changed = c.followsCursor() && this.centre(c);
      if (c.type == UiType.SCROLL_VIEW) {
         changed |= this.scrollView(c);
      } else if (c.type == UiType.SLIDER) {
         changed |= this.slider(c);
      } else if (c.type == UiType.DROPDOWN) {
         changed |= dropdownArea(c);
      }

      for (int i = 0; i < c.children.size(); i++) {
         changed |= this.place(c.children.get(i));
      }

      return changed;
   }

   private boolean scrollView(UiControl view) {
      UiControl content = part(view, "scroll_content");
      UiControl port = part(view, "scroll_view_port");
      if (content != null && port != null) {
         float max = Math.max(0.0F, content.h - port.h);
         if (content.h != view.scrollContentH) {
            if (truthy(view.props.get("jump_to_bottom_on_update"))) {
               view.scroll = max;
            }

            view.scrollContentH = content.h;
         }

         view.scroll = Math.max(0.0F, Math.min(view.scroll, max));
         float anchoredTop = content.y - content.inputShiftY - port.y;
         boolean changed = shift(content, content.inputShiftX, max > 0.0F ? -view.scroll - anchoredTop : 0.0F);
         UiControl bar = part(view, "scroll_box_and_track_panel");
         boolean hide = max < 0.5F && !truthy(view.props.get("scrollbar_always_visible"));
         if (bar != null && bar.inputHidden != hide) {
            bar.inputHidden = hide;
            this.changed(bar);
            changed = true;
         }

         UiValue barShown = view.bag.get("#scroll_bar_visible");
         if (barShown == null || barShown.truthy() == hide) {
            view.bag.put("#scroll_bar_visible", UiValue.of(!hide));
            this.changed(view);
            changed = true;
         }

         UiControl box = part(view, "scrollbar_box");
         if (box != null && box.parent != null && !hide) {
            float track = box.parent.h;
            float size = content.h > 0.0F ? Math.max(Math.min(13.0F, track), Math.min(track, track * port.h / content.h)) : track;
            float top = max > 0.0F ? (track - size) * view.scroll / max : 0.0F;
            changed |= size(box, size);
            changed |= shift(box, box.inputShiftX, top - (box.y - box.inputShiftY - box.parent.y));
         }

         return changed;
      } else {
         return false;
      }
   }

   private boolean slider(UiControl slider) {
      float ratio = clamp(value(slider));
      boolean vertical = "vertical".equals(string(slider.props.get("slider_direction")));
      boolean changed = false;
      UiControl box = find(slider, string(slider.props.get("slider_box_control")));
      if (box != null) {
         if (vertical) {
            float base = box.y - box.inputShiftY + box.h / 2.0F;
            changed = shift(box, box.inputShiftX, slider.y + ratio * slider.h - base);
         } else {
            float base = box.x - box.inputShiftX + box.w / 2.0F;
            changed = shift(box, slider.x + ratio * slider.w - base, box.inputShiftY);
         }
      }

      for (String key : new String[]{"progress_control", "progress_hover_control"}) {
         UiControl progress = find(slider, string(slider.props.get(key)));
         if (progress != null) {
            changed |= this.bag(progress, "#clip_ratio", UiValue.of(1.0F - ratio));
         }
      }

      return this.stepMarks(slider, ratio, vertical) || changed;
   }

   private boolean stepMarks(UiControl slider, float ratio, boolean vertical) {
      UiControl.Factory factory = slider.factory;
      if (factory != null && !factory.dataDriven() && !(slider.w <= 0.0F)) {
         int steps = steps(slider);
         String hover = slider.state.equals("default") ? "" : "_hover";
         List<Entry<String, Map<String, UiValue>>> children = new ArrayList<>();
         StringBuilder signature = new StringBuilder();
         if (steps > 1 && steps <= 64) {
            int at = Math.round(ratio * (steps - 1));
            float length = vertical ? slider.h : slider.w;

            for (int i = 0; i < steps; i++) {
               String id = (i <= at ? "slider_step_progress" : "slider_step") + hover;
               float offset = ((float)i / (steps - 1) - 0.5F) * length;
               children.add(Map.entry(id, Map.of("$step_offset", vertical ? UiValue.array(0.0, offset) : UiValue.array(offset, 0.0))));
               signature.append(id).append('@').append(Math.round(offset * 4.0F)).append(';');
            }
         }

         String key = signature.toString();
         if (key.equals(this.stepMarks.getOrDefault(slider, ""))) {
            return false;
         }

         this.stepMarks.put(slider, key);
         this.instance.respawn(slider, children);
         this.instance.touch();
         return true;
      } else {
         return false;
      }
   }

   private static boolean dropdownArea(UiControl dropdown) {
      String content = string(dropdown.props.get("dropdown_content_control"));
      String areaName = string(dropdown.props.get("dropdown_area"));
      UiControl panel = dropdown.parent != null && content != null ? dropdown.parent.child(content) : null;
      if (panel != null && areaName != null && !areaName.isEmpty()) {
         UiControl area = null;

         for (UiControl c = dropdown.parent; c != null && area == null; c = c.parent) {
            if (c.name.equals(areaName)) {
               area = c;
            }
         }

         if (area != null && panel.clipArea != area) {
            panel.clipArea = area;
            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private static float value(UiControl slider) {
      UiValue value = slider.bag.get("#slider_value");
      return value != null && !value.isNull() ? (float)value.asNumber() : 0.0F;
   }

   private static boolean shift(UiControl control, float x, float y) {
      if (Math.abs(control.inputShiftX - x) < 0.01F && Math.abs(control.inputShiftY - y) < 0.01F) {
         return false;
      }

      control.inputShiftX = x;
      control.inputShiftY = y;
      return true;
   }

   private static boolean size(UiControl control, float height) {
      if (!Float.isNaN(control.inputHeight) && Math.abs(control.inputHeight - height) < 0.01F) {
         return false;
      }

      control.inputHeight = height;
      return true;
   }

   private boolean bag(UiControl control, String name, UiValue value) {
      UiValue previous = control.bag.put(name, value);
      if (previous != null && previous.isNumber() && !(Math.abs(previous.asNumber() - value.asNumber()) > 1.0E-4)) {
         return false;
      }

      this.changed(control);
      return true;
   }

   private @Nullable UiControl scrollViewAt(float x, float y, @Nullable UiControl modal) {
      UiControl fallback = null;

      for (UiControl control : this.topmostFirst(modal)) {
         if (control.type == UiType.SCROLL_VIEW && contains(control, x, y)) {
            UiControl content = part(control, "scroll_content");
            UiControl port = part(control, "scroll_view_port");
            if (content != null && port != null && content.h - port.h >= 0.5F) {
               return control;
            }

            if (fallback == null) {
               fallback = control;
            }
         }
      }

      return fallback;
   }

   private void dragBox(UiControl box, float y) {
      UiControl view = ancestor(box, UiType.SCROLL_VIEW);
      UiControl content = view == null ? null : part(view, "scroll_content");
      UiControl port = view == null ? null : part(view, "scroll_view_port");
      if (content != null && port != null && box.parent != null) {
         float max = Math.max(0.0F, content.h - port.h);
         float room = box.parent.h - box.h;
         view.scroll = room > 0.0F ? (y - this.grab - box.parent.y) / room * max : 0.0F;
         this.instance.invalidate();
      }
   }

   private void jump(UiControl track, float y) {
      UiControl view = ancestor(track, UiType.SCROLL_VIEW);
      UiControl box = view == null ? null : part(view, "scrollbar_box");
      if (box != null) {
         this.grab = box.h / 2.0F;
         this.dragBox(box, y);
      }
   }

   private void activate(UiControl control) {
      String sound = string(control.props.get("sound_name"));
      if (sound != null && !sound.isEmpty() && this.sounds != null) {
         this.sounds.play(sound, (float)number(control.props.get("sound_volume"), 1.0), (float)number(control.props.get("sound_pitch"), 1.0));
      }

      switch (control.type) {
         case TOGGLE:
         case DROPDOWN:
            this.toggle(control);
         default:
            for (JsonObject mapping : control.buttonMappings) {
               if ("button.menu_select".equals(string(mapping.get("from_button_id"))) && "pressed".equals(string(mapping.get("mapping_type")))) {
                  this.dispatch(string(mapping.get("to_button_id")), control, mapping);
               }
            }
      }
   }

   private void toggle(UiControl toggle) {
      boolean checked = truthy(toggle.bag.get("#toggle_state"));
      if (toggle.radioManaged) {
         this.instance.selectRadio(toggle);
         this.changed(toggle);
      } else {
         if (toggle.type == UiType.TOGGLE && truthy(toggle.props.get("radio_toggle_group"))) {
            for (UiControl other : this.group(toggle)) {
               if (other != toggle && truthy(other.bag.get("#toggle_state"))) {
                  this.write(other, "#toggle_state", UiValue.FALSE);
               }
            }

            this.write(toggle, "#toggle_state", UiValue.TRUE);
         } else {
            this.write(toggle, "#toggle_state", UiValue.of(!checked));
         }
      }
   }

   private List<UiControl> group(UiControl toggle) {
      String name = string(toggle.props.get("toggle_name"));
      String collection = string(toggle.props.get("toggle_grid_collection_name"));
      UiControl scope = this.instance.root();
      if (collection != null && !collection.isEmpty()) {
         for (UiControl c = toggle.parent; c != null; c = c.parent) {
            if (collection.equals(c.collectionName)) {
               scope = c;
               break;
            }
         }
      }

      List<UiControl> members = new ArrayList<>();
      if (scope != null) {
         scope.walk(c -> {
            if (c.type == UiType.TOGGLE && truthy(c.props.get("radio_toggle_group")) && Objects.equals(name, string(c.props.get("toggle_name")))) {
               members.add(c);
            }
         });
      }

      return members;
   }

   private void slide(UiControl slider, float x, float y) {
      boolean vertical = "vertical".equals(string(slider.props.get("slider_direction")));
      float extent = vertical ? slider.h : slider.w;
      float ratio = extent > 0.0F ? clamp(vertical ? (y - slider.y) / extent : (x - slider.x) / extent) : 0.0F;
      int steps = steps(slider);
      if (steps > 1) {
         ratio = (float)Math.round(ratio * (steps - 1)) / (steps - 1);
      }

      this.write(slider, "#slider_value", UiValue.of(ratio));
      this.instance.invalidate();
   }

   static int steps(UiControl slider) {
      UiValue bound = slider.bag.get("#slider_steps");
      double steps = bound != null && bound.isNumber() ? bound.asNumber() : number(slider.props.get("slider_steps"), 1.0);
      return (int)Math.max(1.0, Math.min(steps, 10000.0));
   }

   private void dispatch(@Nullable String id, UiControl control, JsonObject mapping) {
      if (id != null && !id.isEmpty()) {
         if (id.equals("button.dropdown_exit")) {
            this.closeDropdowns(control);
         } else {
            if (control.fromServer) {
               this.instance.gaps.fired(id);
               BedrockNative.LOGGER.debug("JSON UI server control {} fired {}", control, id);
            }

            String collection = collectionOf(control, mapping);
            if (collection == null && control.contextual) {
               this.instance.data.event(id, control.itemPath());
            } else {
               int index = -1;
               if (collection != null) {
                  UiControl indexed = UiScreenInstance.indexed(control, collection);
                  index = indexed == null ? -1 : indexed.collectionIndex;
               }

               this.instance.data.event(id, collection, index);
            }
         }
      }
   }

   private static @Nullable String collectionOf(UiControl control, JsonObject mapping) {
      if (mapping.get("collection_index") instanceof JsonPrimitive primitive && primitive.isString() && !primitive.getAsString().isEmpty()) {
         return primitive.getAsString();
      } else {
         for (UiBinding binding : control.bindings) {
            if (binding.type == UiBinding.Type.COLLECTION_DETAILS && binding.collection != null) {
               return binding.collection;
            }
         }

         return null;
      }
   }

   private boolean global(UiControl control, String id) {
      boolean fired = false;

      for (JsonObject mapping : control.buttonMappings) {
         if (id.equals(string(mapping.get("from_button_id"))) && "global".equals(string(mapping.get("mapping_type")))) {
            this.dispatch(string(mapping.get("to_button_id")), control, mapping);
            fired = true;
         }
      }

      return fired;
   }

   private void closeDropdowns(UiControl from) {
      List<UiControl> open = new ArrayList<>();
      UiControl root = this.instance.root();
      if (root != null) {
         root.walk(c -> {
            if (c.type == UiType.DROPDOWN && truthy(c.bag.get("#toggle_state"))) {
               open.add(c);
            }
         });
         List<UiControl> matching = new ArrayList<>();

         for (UiControl dropdown : open) {
            String content = string(dropdown.props.get("dropdown_content_control"));
            UiControl panel = dropdown.parent != null && content != null ? dropdown.parent.child(content) : null;
            if (panel != null && from.isDescendantOf(panel)) {
               matching.add(dropdown);
            }
         }

         for (UiControl dropdown : matching.isEmpty() ? open : matching) {
            this.write(dropdown, "#toggle_state", UiValue.FALSE);
         }
      }
   }

   private void write(UiControl control, String target, UiValue value) {
      UiValue previous = control.bag.put(target, value);
      if (!value.equals(previous)) {
         this.changed(control);
      }

      for (UiBinding binding : control.bindings) {
         if (target.equals(binding.target) && binding.name != null && binding.expression == null) {
            switch (binding.type) {
               case COLLECTION:
                  UiControl indexed = UiScreenInstance.indexed(control, binding.collection);
                  String collection = binding.collection != null ? binding.collection : (indexed == null ? null : indexed.indexCollection);
                  if (indexed != null && collection != null) {
                     this.instance.data.write(collection, indexed.collectionIndex, binding.name, value, UiScreenInstance.outerIndex(indexed));
                  } else if (collection != null && !collection.isEmpty()) {
                     this.instance.data.write(collection, -1, binding.name, value, -1);
                  }
                  break;
               case GLOBAL:
               case NONE:
                  this.instance.data.write(null, -1, binding.name, value, -1);
            }
         }
      }
   }

   private void state(UiControl control, String state) {
      if (!control.state.equals(state)) {
         control.state = state;
         this.changed(control);
      }

      if (control.type == UiType.SLIDER) {
         UiControl box = find(control, string(control.props.get("slider_box_control")));
         if (box != null && !box.state.equals(state.equals("pressed") ? "hover" : state)) {
            box.state = state.equals("pressed") ? "hover" : state;
            this.changed(box);
         }
      }
   }

   private void changed(UiControl control) {
      control.dirty = true;
      this.instance.touch();
   }

   public List<String> fireOthers(UiControl control, String fromButtonId, Predicate<String> done) {
      List<String> fired = new ArrayList<>(0);

      for (JsonObject mapping : List.copyOf(control.buttonMappings)) {
         String to = string(mapping.get("to_button_id"));
         if (to != null
            && !to.isEmpty()
            && !done.test(to)
            && fromButtonId.equals(string(mapping.get("from_button_id")))
            && "pressed".equals(string(mapping.get("mapping_type")))) {
            this.dispatch(to, control, mapping);
            fired.add(to);
         }
      }

      return fired;
   }

   public @Nullable UiControl firing(String buttonId, @Nullable String collection, int index) {
      for (UiControl control : this.topmostFirst(null)) {
         for (JsonObject mapping : control.buttonMappings) {
            if ("button.menu_select".equals(string(mapping.get("from_button_id")))
               && "pressed".equals(string(mapping.get("mapping_type")))
               && buttonId.equals(string(mapping.get("to_button_id")))) {
               String itemCollection = collectionOf(control, mapping);
               UiControl indexed = itemCollection == null ? null : UiScreenInstance.indexed(control, itemCollection);
               int itemIndex = indexed == null ? -1 : indexed.collectionIndex;
               if (Objects.equals(collection, itemCollection) && (index < 0 || index == itemIndex)) {
                  return control;
               }
            }
         }
      }

      return null;
   }

   @Nullable UiControl pick(float x, float y, @Nullable UiControl modal) {
      for (UiControl control : this.topmostFirst(modal)) {
         if (interactive(control) && contains(control, x, y) && !passesThrough(control)) {
            return control;
         }
      }

      return null;
   }

   private @Nullable UiControl passingThrough(float x, float y, @Nullable UiControl modal, @Nullable UiControl below) {
      for (UiControl control : this.topmostFirst(modal)) {
         if (control == below) {
            return null;
         }

         if (interactive(control) && contains(control, x, y) && passesThrough(control)) {
            return control;
         }
      }

      return null;
   }

   private static boolean passesThrough(UiControl control) {
      UiValue consume = control.props.get("consume_event");
      if (consume != null && !consume.isNull() && !consume.truthy()) {
         return true;
      }

      UiValue hover = control.props.get("consume_hover_events");
      return control.type == UiType.BUTTON && hover != null && !hover.isNull() && !hover.truthy() && control.buttonMappings.isEmpty();
   }

   private long orderKey() {
      if (RenderSwitches.inputOrderCache) {
         return this.instance.placementVersion();
      }

      UiDrawList list = this.instance.drawList();
      if (list != this.keyedList) {
         this.keyedList = list;
         this.listKey--;
      }

      return this.listKey;
   }

   private List<UiControl> topmostFirst(@Nullable UiControl within) {
      long placement = this.orderKey();
      long tree = this.instance.treeVersion();
      if (within == null && this.topmostPlacement == placement && this.topmostTree == tree) {
         return this.topmost;
      }

      if (within != null && within == this.withinFor && this.withinPlacement == placement && this.withinTree == tree) {
         return this.withinOrder;
      }

      UiControl root = within != null ? within : this.instance.root();
      List<UiControl> list = new ArrayList<>();
      if (root != null) {
         collect(root, list);
      }

      List<UiControl> ordered = new ArrayList<>(list);
      Map<UiControl, Integer> order = new IdentityHashMap<>();

      for (int i = 0; i < list.size(); i++) {
         order.put(list.get(i), i);
      }

      ordered.sort(Comparator.<UiControl>comparingDouble(c -> c.z).thenComparing(order::get).reversed());
      if (within == null) {
         this.topmost = ordered;
         this.topmostPlacement = placement;
         this.topmostTree = tree;
         this.topModalKnown = false;
      } else {
         this.withinFor = within;
         this.withinOrder = ordered;
         this.withinPlacement = placement;
         this.withinTree = tree;
      }

      return ordered;
   }

   private static void collect(UiControl control, List<UiControl> list) {
      if (control.ownVisible && control.effectiveVisible && !control.followsCursor()) {
         list.add(control);

         for (int i = 0; i < control.children.size(); i++) {
            collect(control.children.get(i), list);
         }
      }
   }

   private @Nullable UiControl topModal() {
      List<UiControl> ordered = this.topmostFirst(null);
      if (this.topModalKnown) {
         return this.topModal;
      }

      UiControl top = null;

      for (UiControl control : ordered) {
         if (truthy(control.props.get("modal"))) {
            top = control;
            break;
         }
      }

      this.topModal = top;
      this.topModalKnown = true;
      return top;
   }

   static boolean contains(UiControl control, float x, float y) {
      if (!(x < control.x) && !(y < control.y) && !(x >= control.x + control.w) && !(y >= control.y + control.h)) {
         float[] clip = control.clip;
         return clip == null || x >= clip[0] && y >= clip[1] && x < clip[0] + clip[2] && y < clip[1] + clip[3];
      } else {
         return false;
      }
   }

   private static boolean interactive(UiControl control) {
      return switch (control.type) {
         case EDIT_BOX, SLIDER, SCROLLBAR_BOX, SCROLL_TRACK, SCROLLBAR_TRACK, TOGGLE, DROPDOWN, BUTTON, SLIDER_BOX -> true;
         default -> pressedMapping(control);
      };
   }

   private static boolean hoverable(UiControl control) {
      return switch (control.type) {
         case EDIT_BOX, SLIDER, TOGGLE, DROPDOWN, BUTTON -> true;
         default -> pressedMapping(control);
      };
   }

   private static boolean pressedMapping(UiControl control) {
      for (JsonObject mapping : control.buttonMappings) {
         if ("button.menu_select".equals(string(mapping.get("from_button_id"))) && "pressed".equals(string(mapping.get("mapping_type")))) {
            return true;
         }
      }

      return false;
   }

   private static @Nullable UiControl owner(@Nullable UiControl hit) {
      if (hit != null && hit.type == UiType.SLIDER_BOX) {
         UiControl slider = ancestor(hit, UiType.SLIDER);
         return slider != null ? slider : hit;
      } else {
         return hit;
      }
   }

   private static @Nullable UiControl ancestor(UiControl control, UiType type) {
      for (UiControl c = control.parent; c != null; c = c.parent) {
         if (c.type == type) {
            return c;
         }
      }

      return null;
   }

   static @Nullable UiControl part(UiControl view, String property) {
      String name = string(view.props.get(property));
      if (name != null && !name.isEmpty()) {
         Deque<UiControl> queue = new ArrayDeque<>(view.children);

         while (!queue.isEmpty()) {
            UiControl control = queue.poll();
            if (control.name.equals(name)) {
               return control;
            }

            if (control.type != UiType.SCROLL_VIEW) {
               queue.addAll(control.children);
            }
         }

         return null;
      } else {
         return null;
      }
   }

   private static @Nullable UiControl find(UiControl from, @Nullable String name) {
      return name != null && !name.isEmpty() ? from.find(name) : null;
   }

   private static @Nullable String string(@Nullable Object value) {
      if (value instanceof UiValue ui) {
         return ui.isNull() ? null : ui.asString();
      } else {
         return value instanceof JsonPrimitive primitive ? primitive.getAsString() : null;
      }
   }

   private static boolean truthy(@Nullable Object value) {
      if (value instanceof UiValue ui) {
         return ui.truthy();
      } else {
         return value instanceof JsonElement json ? UiValue.of(json).truthy() : false;
      }
   }

   private static double number(@Nullable UiValue value, double fallback) {
      return value != null && value.isNumber() ? value.asNumber() : fallback;
   }

   private static float clamp(float value) {
      return Math.max(0.0F, Math.min(1.0F, value));
   }

   private enum Drag {
      NONE,
      PRESS,
      SLIDER,
      BOX,
      TRACK;
   }

   public interface Sounds {
      void play(String var1, float var2, float var3);
   }

   public interface TextEditor {
      void begin(UiControl var1, String var2, int var3);

      void end(UiControl var1);
   }
}
