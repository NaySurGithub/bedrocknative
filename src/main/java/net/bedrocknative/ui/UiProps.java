package net.bedrocknative.ui;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.jspecify.annotations.Nullable;

final class UiProps {
   private static final String[] BUTTON_STATES = new String[]{"default_control", "hover_control", "pressed_control", "locked_control", "indent_control"};
   private static final String[] TOGGLE_STATES = new String[]{
      "checked_control",
      "unchecked_control",
      "checked_hover_control",
      "unchecked_hover_control",
      "checked_locked_control",
      "unchecked_locked_control",
      "checked_locked_hover_control",
      "unchecked_locked_hover_control"
   };

   private UiProps() {
   }

   static boolean apply(UiControl c, UiScreenInstance instance) {
      UiProps.Reader read = new UiProps.Reader(c, instance);
      UiAnimation.Track alphaTrack = null;
      UiAnimation.Track offsetTrack = null;
      UiAnimation.Track sizeTrack = null;
      UiAnimation.Track colorTrack = null;
      UiAnimation.Track uvTrack = null;
      UiAnimation.Track clipTrack = null;

      for (UiAnimation.Track track : c.tracks) {
         if (track.property() != null && (track.value() != null || track.lengths() != null)) {
            String size = track.property();
            switch (size) {
               case "alpha":
                  alphaTrack = later(alphaTrack, track);
                  break;
               case "offset":
                  offsetTrack = later(offsetTrack, track);
                  break;
               case "size":
                  sizeTrack = later(sizeTrack, track);
                  break;
               case "color":
                  colorTrack = later(colorTrack, track);
                  break;
               case "uv":
                  uvTrack = later(uvTrack, track);
                  break;
               case "clip_ratio":
                  clipTrack = later(clipTrack, track);
            }
         }
      }

      UiValue boundVisible = read.bag("#visible");
      boolean ownVisible = !c.stateHidden
         && !c.inputHidden
         && (boundVisible != null ? boundVisible.truthy() : c.stateActive || !c.props.containsKey("visible") || read.prop("visible").truthy());
      UiLength[] size = sizeTrack != null && sizeTrack.lengths() != null ? sizeTrack.lengths() : pair(read.prop("size"), UiLength.DEFAULT);
      UiLength[] offset = offsetTrack != null && offsetTrack.lengths() != null ? offsetTrack.lengths() : pair(read.over("#offset", "offset"), UiLength.ZERO);
      UiLength[] minSize = UiLength.pair(read.prop("min_size"));
      UiLength[] maxSize = UiLength.pair(read.prop("max_size"));
      UiAnchor anchorFrom = UiAnchor.of(read.prop("anchor_from").stringOrNull());
      UiAnchor anchorTo = UiAnchor.of(read.prop("anchor_to").stringOrNull());
      boolean horizontal = "horizontal".equals(read.prop("orientation").stringOrNull());
      boolean useChildAnchors = read.prop("use_child_anchors").truthy();
      boolean inheritMaxWidth = read.prop("inherit_max_sibling_width").truthy();
      boolean inheritMaxHeight = read.prop("inherit_max_sibling_height").truthy();
      boolean useAnchoredOffset = read.prop("use_anchored_offset").truthy();
      float anchoredX = read.bagNumber("#anchored_offset_value_x");
      float anchoredY = read.bagNumber("#anchored_offset_value_y");
      float sizeBindingX = read.bagNumber("#size_binding_x");
      float sizeBindingY = read.bagNumber("#size_binding_y");
      boolean clipsChildren = read.prop("clips_children").truthy();
      UiValue allowClipping = read.prop("allow_clipping");
      float[] clipOffset = read.prop("clip_offset").floats();
      String text = c.text;
      UiFont font = c.font;
      float linePadding = c.linePadding;
      if (c.type == UiType.LABEL) {
         UiValue raw = read.over("#text", "text");
         String value = raw.isNull() ? "" : raw.asString();
         UiValue localize = read.prop("localize");
         text = !localize.isNull() && !localize.truthy() ? value : instance.environment.localize(value);
         String fontType = read.prop("font_type").stringOrNull();
         UiValue scaleFactor = read.prop("font_scale_factor");
         font = new UiFont(
            fontType == null ? "default" : fontType,
            UiFont.sizeFactor(read.prop("font_size").stringOrNull()) * (scaleFactor.isNull() ? 1.0F : (float)scaleFactor.asNumber())
         );
         linePadding = (float)read.prop("line_padding").asNumber();
         c.shadow = read.prop("shadow").truthy();
         c.alignment = read.prop("text_alignment").stringOrNull();
      }

      if (c.type == UiType.IMAGE) {
         UiValue texture = read.over("#texture", "texture");
         c.texture = texture.isNull() ? "" : texture.asString();
         UiValue fileSystem = read.over("#texture_file_system", "texture_file_system");
         c.fileSystem = fileSystem.isNull() ? "InUserPackage" : fileSystem.asString();
         c.uv = uvTrack != null ? uvTrack.value().floats() : read.prop("uv").floats();
         c.uvSize = read.prop("uv_size").floats();
         UiValue slices = read.prop("nineslice_size");
         c.nineslice = slices.isNull() ? null : UiTextureInfo.slices(slices.toJson());
         UiValue keepRatio = read.prop("keep_ratio");
         c.keepRatio = (keepRatio.isNull() || keepRatio.truthy()) && !read.prop("fill").truthy();
         UiValue tiled = read.prop("tiled");
         c.tiled = tiled.isString() ? tiled.asString() : (tiled.truthy() ? "xy" : null);
         float[] tiledScale = read.prop("tiled_scale").floats();
         c.tiledScale = tiledScale != null && tiledScale.length >= 2 ? tiledScale : new float[]{1.0F, 1.0F};
         c.bilinear = read.prop("bilinear").truthy();
         c.grayscale = read.prop("grayscale").truthy();
         UiValue clipRatio = read.bag("#clip_ratio");
         if (clipRatio == null) {
            clipRatio = clipTrack != null ? clipTrack.value() : read.prop("clip_ratio");
         }

         c.clipRatio = clamp((float)clipRatio.asNumber());
         String direction = read.prop("clip_direction").stringOrNull();
         c.clipDirection = direction == null ? "left" : direction;
      }

      if (c.type == UiType.CUSTOM) {
         c.renderer = read.prop("renderer").stringOrNull();
      }

      if (c.type == UiType.LABEL || c.type == UiType.IMAGE || c.type == UiType.CUSTOM) {
         UiValue color = colorTrack != null ? colorTrack.value() : read.over("#color", "color");
         c.color = color(color);
      }

      UiValue alpha = read.bag("#alpha");
      if (alpha == null) {
         UiValue own = read.prop("alpha");
         if (alphaTrack == null) {
            alpha = own;
         } else if (alphaTrack.current().scaleFromStartingAlpha() && own.isNumber()) {
            alpha = UiValue.of(own.asNumber() * alphaTrack.value().asNumber());
         } else {
            alpha = alphaTrack.value();
         }
      }

      c.alpha = alpha.isNull() ? 1.0F : clamp((float)alpha.asNumber());
      UiValue propagate = read.bag("#propagateAlpha");
      c.propagateAlpha = (propagate != null ? propagate : read.prop("propagate_alpha")).truthy();
      UiValue enabled = read.over("#enabled", "enabled");
      c.ownEnabled = enabled.isNull() || enabled.truthy();
      c.enabled = c.ownEnabled && (c.parent == null || c.parent.enabled);
      if (c.type == UiType.BUTTON
         || c.type == UiType.TOGGLE
         || c.type == UiType.DROPDOWN
         || c.type == UiType.EDIT_BOX
         || c.type == UiType.SLIDER
         || c.type == UiType.SLIDER_BOX) {
         states(c, read);
      }

      c.layer = (float)read.prop("layer").asNumber();
      c.allowClipping = allowClipping.isNull() || allowClipping.truthy();
      c.clipsChildren = clipsChildren;
      c.clipOffsetX = clipOffset != null && clipOffset.length >= 2 ? clipOffset[0] : 0.0F;
      c.clipOffsetY = clipOffset != null && clipOffset.length >= 2 ? clipOffset[1] : 0.0F;
      boolean textChanged = !text.equals(c.text) || !font.equals(c.font) || linePadding != c.linePadding;
      boolean offsetChanged = !Arrays.equals(offset, c.offset, UiProps::sameLength);
      boolean sizeChanged = !Arrays.equals(size, c.size, UiProps::sameLength);
      boolean visibleChanged = ownVisible != c.ownVisible;
      c.offsetChanged = offsetChanged;
      c.sizeChanged = sizeChanged;
      c.visibleChanged = visibleChanged;
      boolean changed = visibleChanged
         || sizeChanged
         || offsetChanged
         || !sameLengths(minSize, c.minSize)
         || !sameLengths(maxSize, c.maxSize)
         || anchorFrom != c.anchorFrom
         || anchorTo != c.anchorTo
         || horizontal != c.horizontal
         || useChildAnchors != c.useChildAnchors
         || inheritMaxWidth != c.inheritMaxWidth
         || inheritMaxHeight != c.inheritMaxHeight
         || useAnchoredOffset != c.useAnchoredOffset
         || !same(anchoredX, c.anchoredX)
         || !same(anchoredY, c.anchoredY)
         || !same(sizeBindingX, c.sizeBindingX)
         || !same(sizeBindingY, c.sizeBindingY);
      if (textChanged) {
         c.textDirty = true;
      }

      c.ownVisible = ownVisible;
      c.size = size;
      c.offset = offset;
      c.minSize = minSize;
      c.maxSize = maxSize;
      c.anchorFrom = anchorFrom;
      c.anchorTo = anchorTo;
      c.horizontal = horizontal;
      c.useChildAnchors = useChildAnchors;
      c.inheritMaxWidth = inheritMaxWidth;
      c.inheritMaxHeight = inheritMaxHeight;
      c.useAnchoredOffset = useAnchoredOffset;
      c.anchoredX = anchoredX;
      c.anchoredY = anchoredY;
      c.sizeBindingX = sizeBindingX;
      c.sizeBindingY = sizeBindingY;
      c.text = text;
      c.font = font;
      c.linePadding = linePadding;
      return changed;
   }

   private static void states(UiControl c, UiProps.Reader read) {
      String active;
      String[] names;
      if (c.type != UiType.TOGGLE && c.type != UiType.DROPDOWN) {
         names = BUTTON_STATES;
         String state = c.enabled ? c.state : "locked";
         active = read.prop(state + "_control").stringOrNull();
         if (active == null || active.isEmpty()) {
            active = read.prop("default_control").stringOrNull();
         }
      } else {
         names = TOGGLE_STATES;
         UiValue checked = read.bag("#toggle_state");
         String prefix = checked != null && checked.truthy() ? "checked" : "unchecked";
         String locked = c.enabled ? "" : "_locked";
         String hover = c.state.equals("hover") ? "_hover" : "";
         active = read.prop(prefix + locked + hover + "_control").stringOrNull();
         if (active == null || active.isEmpty()) {
            active = read.prop(prefix + "_control").stringOrNull();
         }
      }

      Set<String> stateNames = new HashSet<>();

      for (String name : names) {
         String value = read.prop(name).stringOrNull();
         if (value != null && !value.isEmpty()) {
            stateNames.add(value);
         }
      }

      for (UiControl child : c.children) {
         boolean hidden = stateNames.contains(child.name) && !child.name.equals(active);
         boolean shown = child.name.equals(active);
         if (hidden != child.stateHidden || shown != child.stateActive) {
            child.stateHidden = hidden;
            child.stateActive = shown;
            child.dirty = true;
         }
      }
   }

   private static UiAnimation.Track later(@Nullable UiAnimation.Track current, UiAnimation.Track candidate) {
      return current != null && !(candidate.activatedAt() >= current.activatedAt()) ? current : candidate;
   }

   private static UiLength[] pair(UiValue value, UiLength fallback) {
      UiLength[] pair = UiLength.pair(value);
      return pair != null ? pair : new UiLength[]{fallback, fallback};
   }

   private static boolean sameLengths(@Nullable UiLength[] a, @Nullable UiLength[] b) {
      return a == null ? b == null : b != null && Arrays.equals(a, b, UiProps::sameLength);
   }

   private static int sameLength(UiLength a, UiLength b) {
      return a.kind == b.kind
            && a.px == b.px
            && a.parent == b.parent
            && a.children == b.children
            && a.largestChild == b.largestChild
            && a.largestSibling == b.largestSibling
            && a.width == b.width
            && a.height == b.height
         ? 0
         : 1;
   }

   private static boolean same(float a, float b) {
      return Float.isNaN(a) ? Float.isNaN(b) : a == b;
   }

   static float clamp(float value) {
      return Math.max(0.0F, Math.min(1.0F, value));
   }

   static float[] color(@Nullable UiValue value) {
      float[] parts = value == null ? null : value.floats();
      return parts != null && parts.length >= 3
         ? new float[]{clamp(parts[0]), clamp(parts[1]), clamp(parts[2]), parts.length >= 4 ? clamp(parts[3]) : 1.0F}
         : new float[]{1.0F, 1.0F, 1.0F, 1.0F};
   }

   private static final class Reader {
      private final UiControl control;
      private final UiScreenInstance instance;

      Reader(UiControl control, UiScreenInstance instance) {
         this.control = control;
         this.instance = instance;
      }

      UiValue prop(String key) {
         return this.control.resolved(key, this.instance.dataFor(this.control), this.instance.problems, this.instance.definitions);
      }

      @Nullable
      UiValue bag(String key) {
         return this.control.bag.get(key);
      }

      float bagNumber(String key) {
         UiValue value = this.control.bag.get(key);
         return value != null && !value.isNull() ? (float)value.asNumber() : Float.NaN;
      }

      UiValue over(String bagKey, String propKey) {
         UiValue value = this.control.bag.get(bagKey);
         return value != null ? value : this.prop(propKey);
      }
   }
}
