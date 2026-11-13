package net.bedrocknative.ui;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

final class UiLayout {
   static final int STACK_OFFSETS = 1;
   static final int FILL_OUTSIDE_STACKS = 2;
   static final int CONTENT_NO_ORIGIN = 4;
   static final int BEDROCK = 7;
   static volatile int rules = "legacy".equalsIgnoreCase(System.getProperty("bedrocknative.jsonUiLayout", "").trim()) ? 0 : 7;
   private final UiEnvironment environment;
   private final long epoch;
   static volatile boolean everywhere = "all".equalsIgnoreCase(System.getProperty("bedrocknative.jsonUiLayout", "").trim());
   private final int active;

   static int scopeFor(String reference) {
      return !everywhere && !"start.start_screen".equals(reference) && !"pause.pause_screen".equals(reference) ? 0 : 7;
   }

   private UiLayout(UiEnvironment environment, long epoch, int scope) {
      this.environment = environment;
      this.epoch = epoch;
      this.active = rules & scope;
   }

   static void layout(UiControl root, float width, float height, UiEnvironment environment, long epoch, int scope) {
      UiLayout layout = new UiLayout(environment, epoch, scope);
      layout.measure(root, width, height, false);
      layout.arrange(root, 0.0F, 0.0F, width, height, null, 0.0F, true);
   }

   static boolean reflowText(UiControl c, UiEnvironment environment) {
      if (c.type == UiType.LABEL && !(c.w < 0.0F) && !(c.h < 0.0F)) {
         UiLength sx = c.size[0];
         UiLength sy = c.size[1];
         boolean widthFromText = sx.isDefault() && Float.isNaN(c.forcedWidth) && Float.isNaN(c.inputWidth) && Float.isNaN(c.sizeBindingX);
         boolean heightFromText = sy.isDefault() && Float.isNaN(c.forcedHeight) && Float.isNaN(c.inputHeight) && Float.isNaN(c.sizeBindingY);
         float wrapWidth = widthFromText ? Float.NaN : c.w;
         c.lines = environment.measurer().wrap(c.text, wrapWidth, c.font);
         float lineHeight = environment.measurer().lineHeight(c.font);
         float textHeight = c.lines.size() * lineHeight + Math.max(0, c.lines.size() - 1) * c.linePadding;
         float textWidth = 0.0F;
         if (widthFromText) {
            for (String line : c.lines) {
               textWidth = Math.max(textWidth, environment.measurer().width(line, c.font));
            }
         }

         if (!heightFromText) {
            if (c.lines.size() > 1 && c.h < lineHeight * 2.0F - 0.01F) {
               c.lines = List.of(environment.measurer().ellipsize(c.text, wrapWidth, c.font));
            } else {
               int fit = UiTextMeasurer.linesThatFit(c.h, lineHeight, c.linePadding);
               if (c.lines.size() > fit) {
                  c.lines = environment.measurer().cut(c.lines, fit, wrapWidth, c.font);
               }
            }
         }

         boolean widthChanged = widthFromText && Math.abs(textWidth - c.w) > 0.01F;
         boolean heightChanged = heightFromText && Math.abs(textHeight - c.h) > 0.01F;
         if (widthChanged) {
            c.w = textWidth;
         }

         if (heightChanged) {
            c.h = textHeight;
         }

         return widthChanged || heightChanged;
      } else {
         return true;
      }
   }

   static boolean rearrange(UiControl c, UiEnvironment environment, long epoch, int scope) {
      UiControl parent = c.parent;
      if (parent == null || parent.type == UiType.STACK_PANEL || c.gridItem) {
         return false;
      } else if (parent.effectiveVisible && c.ownVisible) {
         float[] clip = parent.clip;
         if (parent.clipsChildren) {
            float[] own = new float[]{parent.x + parent.clipOffsetX, parent.y + parent.clipOffsetY, parent.w, parent.h};
            clip = clip == null ? own : intersect(clip, own);
         }

         new UiLayout(environment, epoch, scope).arrange(c, parent.x, parent.y, parent.w, parent.h, clip, parent.z, true);
         return true;
      } else {
         return true;
      }
   }

   private static boolean same(float a, float b) {
      return Float.isNaN(a) ? Float.isNaN(b) : a == b;
   }

   private static float known(float value) {
      return Float.isNaN(value) ? 0.0F : value;
   }

   private void measure(UiControl c, float parentW, float parentH, boolean evenIfHidden) {
      if (!c.ownVisible && !evenIfHidden) {
         c.w = 0.0F;
         c.h = 0.0F;
      } else if (c.measureEpoch != this.epoch
         || !same(parentW, c.measuredParentW)
         || !same(parentH, c.measuredParentH)
         || !Float.isNaN(c.forcedWidth)
         || !Float.isNaN(c.forcedHeight)) {
         c.measureEpoch = this.epoch;
         c.measuredParentW = parentW;
         c.measuredParentH = parentH;
         UiLength sx = c.size[0];
         UiLength sy = c.size[1];
         float w = this.direct(c, true, sx, parentW);
         float h = this.direct(c, false, sy, parentH);
         if (!Float.isNaN(c.forcedWidth)) {
            w = c.forcedWidth;
         }

         if (!Float.isNaN(c.forcedHeight)) {
            h = c.forcedHeight;
         }

         if (!Float.isNaN(c.inputWidth)) {
            w = c.inputWidth;
         }

         if (!Float.isNaN(c.inputHeight)) {
            h = c.inputHeight;
         }

         if (!Float.isNaN(c.sizeBindingX) && !Float.isNaN(parentW)) {
            w = c.sizeBindingX * parentW;
         }

         if (!Float.isNaN(c.sizeBindingY) && !Float.isNaN(parentH)) {
            h = c.sizeBindingY * parentH;
         }

         if (Float.isNaN(w) && sx.usesOther(true) && !sx.usesChildren() && !Float.isNaN(h)) {
            w = terms(c, true, sx, parentW, 0.0F, 0.0F, h);
         }

         if (Float.isNaN(h) && sy.usesOther(false) && !sy.usesChildren() && !Float.isNaN(w)) {
            h = terms(c, false, sy, parentH, 0.0F, 0.0F, w);
         }

         if (Float.isNaN(w) && Float.isNaN(h) && sx.usesOther(true) && sy.usesOther(false) && !sx.usesChildren() && !sy.usesChildren()) {
            w = terms(c, true, sx, parentW, 0.0F, 0.0F, 0.0F);
            h = terms(c, false, sy, parentH, 0.0F, 0.0F, w);
         }

         if (c.type == UiType.LABEL) {
            float childrenWidth = 0.0F;
            if (sx.usesChildren() && !c.children.isEmpty()) {
               this.measureChildren(c, Float.NaN, Float.NaN);
               childrenWidth = this.union(c, true);
            }

            if (Float.isNaN(w)) {
               float textWidth = 0.0F;

               for (String line : c.text.split("\n", -1)) {
                  textWidth = Math.max(textWidth, this.environment.measurer().width(line, c.font));
               }

               float held = Math.max(textWidth, childrenWidth);
               w = sx.kind == UiLength.Kind.TERMS ? terms(c, true, sx, parentW, held, held, known(h)) : textWidth;
            }

            w = this.clamp(c, true, w, parentW, h);
            float wrapWidth = sx.isDefault() && Float.isNaN(c.forcedWidth) && Float.isNaN(c.sizeBindingX) && c.maxSize == null ? Float.NaN : w;
            c.lines = this.environment.measurer().wrap(c.text, wrapWidth, c.font);
            float lineHeight = this.environment.measurer().lineHeight(c.font);
            boolean heightFromText = Float.isNaN(h);
            if (heightFromText) {
               float textHeight = c.lines.size() * lineHeight + Math.max(0, c.lines.size() - 1) * c.linePadding;
               h = sy.kind == UiLength.Kind.TERMS ? terms(c, false, sy, parentH, textHeight, textHeight, w) : textHeight;
            }

            h = this.clamp(c, false, h, parentH, w);
            if (!heightFromText && c.lines.size() > 1 && h < lineHeight * 2.0F - 0.01F) {
               c.lines = List.of(this.environment.measurer().ellipsize(c.text, wrapWidth, c.font));
            } else {
               int fit = UiTextMeasurer.linesThatFit(h, lineHeight, c.linePadding);
               if (c.lines.size() > fit) {
                  c.lines = this.environment.measurer().cut(c.lines, fit, wrapWidth, c.font);
               }
            }

            c.w = Math.max(0.0F, w);
            c.h = Math.max(0.0F, h);
            this.measureChildren(c, c.w, c.h);
         } else {
            if (Float.isNaN(w) || Float.isNaN(h)) {
               this.measureChildren(c, w, h);
               if (Float.isNaN(w)) {
                  w = this.fromChildren(c, true, sx, parentW, h);
               }

               if (Float.isNaN(h)) {
                  h = this.fromChildren(c, false, sy, parentH, w);
               }

               if (Float.isNaN(w) && sx.usesOther(true)) {
                  w = terms(c, true, sx, parentW, this.extent(c, true), largest(c, true), known(h));
               }
            }

            w = this.clamp(c, true, known(w), parentW, h);
            h = this.clamp(c, false, known(h), parentH, w);
            c.w = Math.max(0.0F, w);
            c.h = Math.max(0.0F, h);
            this.measureChildren(c, c.w, c.h);
            if (c.gridRowsSized) {
               c.h = c.gridCellH * c.gridRowsUsed;
            }
         }
      }
   }

   private float direct(UiControl c, boolean horizontal, UiLength length, float parent) {
      return switch (length.kind) {
         case DEFAULT -> c.type != UiType.LABEL && (c.type != UiType.GRID || ownCells(c)) && (c.type != UiType.STACK_PANEL || c.horizontal != horizontal)
            ? known(parent)
            : Float.NaN;
         case FILL -> (this.active & 2) != 0 && c.parent != null && !sharedOut(c, horizontal) && !acrossStack(c, horizontal) ? 0.0F : known(parent);
         case TERMS -> !length.usesChildren() && !length.usesOther(horizontal) ? terms(c, horizontal, length, parent, 0.0F, 0.0F, 0.0F) : Float.NaN;
      };
   }

   private static boolean sharedOut(UiControl c, boolean horizontal) {
      return c.parent != null && c.parent.type == UiType.STACK_PANEL && c.parent.horizontal == horizontal;
   }

   private static boolean acrossStack(UiControl c, boolean horizontal) {
      return c.parent != null && c.parent.type == UiType.STACK_PANEL && c.parent.horizontal != horizontal;
   }

   private static float terms(UiControl c, boolean horizontal, UiLength length, float parent, float children, float largestChild, float other) {
      if (length.kind != UiLength.Kind.TERMS) {
         return known(parent);
      } else {
         float sibling = horizontal ? c.siblingMaxW : c.siblingMaxH;
         return length.px
            + length.parent * known(parent)
            + length.children * children
            + length.largestChild * largestChild
            + length.largestSibling * sibling
            + (horizontal ? length.height : length.width) * other;
      }
   }

   private float fromChildren(UiControl c, boolean horizontal, UiLength length, float parent, float other) {
      if (length.kind == UiLength.Kind.DEFAULT) {
         return this.extent(c, horizontal);
      } else {
         return length.kind != UiLength.Kind.TERMS || !length.usesChildren() && (!length.usesOther(horizontal) || Float.isNaN(other))
            ? Float.NaN
            : terms(c, horizontal, length, parent, this.extent(c, horizontal), largest(c, horizontal), known(other));
      }
   }

   private float clamp(UiControl c, boolean horizontal, float value, float parent, float other) {
      float result = value;
      int axis = horizontal ? 0 : 1;
      if (c.maxSize != null && c.maxSize[axis].kind == UiLength.Kind.TERMS) {
         result = Math.min(value, terms(c, horizontal, c.maxSize[axis], parent, this.extent(c, horizontal), largest(c, horizontal), known(other)));
      }

      if (c.minSize != null && c.minSize[axis].kind == UiLength.Kind.TERMS) {
         result = Math.max(result, terms(c, horizontal, c.minSize[axis], parent, this.extent(c, horizontal), largest(c, horizontal), known(other)));
      }

      return result;
   }

   private float extent(UiControl c, boolean horizontal) {
      if (c.type == UiType.GRID) {
         float cell = horizontal ? c.gridCellW : c.gridCellH;
         int cells = Math.max(1, horizontal ? c.gridColumnsUsed : c.gridRowsUsed);
         if (ownCells(c) && Float.isNaN(cell)) {
            return largest(c, horizontal) * cells;
         } else {
            return horizontal ? c.gridCellW * c.gridColumnsUsed : c.gridCellH * c.gridRowsUsed;
         }
      } else if (c.type != UiType.LABEL) {
         if (c.type == UiType.STACK_PANEL && c.horizontal == horizontal) {
            float sum = 0.0F;

            for (UiControl child : c.children) {
               if (child.ownVisible) {
                  sum += horizontal ? child.w : child.h;
               }
            }

            return sum;
         } else {
            return c.type == UiType.STACK_PANEL ? largest(c, horizontal) : this.union(c, horizontal);
         }
      } else if (!horizontal) {
         int lines = c.text.isEmpty() ? 0 : Math.max(1, c.lines.size());
         return lines * this.environment.measurer().lineHeight(c.font) + Math.max(0, lines - 1) * c.linePadding;
      } else {
         float width = 0.0F;

         for (String line : c.text.split("\n", -1)) {
            width = Math.max(width, this.environment.measurer().width(line, c.font));
         }

         return Math.max(width, c.children.isEmpty() ? 0.0F : this.union(c, true));
      }
   }

   private float union(UiControl c, boolean horizontal) {
      boolean origin = (this.active & 4) == 0;
      float min = origin ? 0.0F : Float.POSITIVE_INFINITY;
      float max = origin ? 0.0F : Float.NEGATIVE_INFINITY;

      for (UiControl child : c.children) {
         if (child.ownVisible) {
            float size = horizontal ? child.w : child.h;
            UiLength offset = child.offset[horizontal ? 0 : 1];
            float offsetPx = offset.kind == UiLength.Kind.TERMS ? offset.px + offset.width * child.w + offset.height * child.h : 0.0F;
            float start = -size * (horizontal ? child.anchorTo.x : child.anchorTo.y) + offsetPx;
            min = Math.min(min, start);
            max = Math.max(max, start + size);
         }
      }

      return max > min ? max - min : 0.0F;
   }

   private static float largest(UiControl c, boolean horizontal) {
      float largest = 0.0F;

      for (UiControl child : c.children) {
         if (child.ownVisible) {
            largest = Math.max(largest, horizontal ? child.w : child.h);
         }
      }

      return largest;
   }

   private void measureChildren(UiControl c, float w, float h) {
      if (!c.children.isEmpty()) {
         if (c.grid != null) {
            this.measureGrid(c, w, h);
         }

         boolean stack = c.type == UiType.STACK_PANEL;
         boolean cells = ownCells(c);
         List<UiControl> deferredSiblings = null;
         List<UiControl> fills = null;

         for (UiControl child : c.children) {
            if (child.ownVisible && !child.gridItem) {
               child.forcedWidth = Float.NaN;
               child.forcedHeight = Float.NaN;
               if (cells) {
                  this.measure(child, c.gridCellW, c.gridCellH, false);
               } else if (stack && (c.horizontal ? child.size[0] : child.size[1]).isFill()) {
                  fills = add(fills, child);
               } else if (!child.size[0].usesSiblings() && !child.size[1].usesSiblings() && !child.inheritMaxWidth && !child.inheritMaxHeight) {
                  this.measure(child, w, h, false);
               } else {
                  deferredSiblings = add(deferredSiblings, child);
               }
            }
         }

         if (deferredSiblings != null) {
            for (UiControl childx : deferredSiblings) {
               childx.siblingMaxW = siblingMax(c, childx, true);
               childx.siblingMaxH = siblingMax(c, childx, false);
               this.measure(childx, w, h, false);
            }

            for (UiControl childx : deferredSiblings) {
               if (childx.inheritMaxWidth || childx.inheritMaxHeight) {
                  childx.forcedWidth = childx.inheritMaxWidth ? Math.max(childx.w, siblingMax(c, childx, true)) : Float.NaN;
                  childx.forcedHeight = childx.inheritMaxHeight ? Math.max(childx.h, siblingMax(c, childx, false)) : Float.NaN;
                  this.measure(childx, w, h, false);
               }
            }
         }

         if (fills != null) {
            float along = c.horizontal ? w : h;
            float used = 0.0F;

            for (UiControl childxx : c.children) {
               if (childxx.ownVisible && !fills.contains(childxx)) {
                  used += c.horizontal ? childxx.w : childxx.h;
               }
            }

            float share = Float.isNaN(along) ? 0.0F : Math.max(0.0F, (along - used) / fills.size());

            for (UiControl childxxx : fills) {
               if (c.horizontal) {
                  childxxx.forcedWidth = share;
               } else {
                  childxxx.forcedHeight = share;
               }

               this.measure(childxxx, w, h, false);
            }

            for (UiControl childxxx : fills) {
               boolean across = c.horizontal ? childxxx.inheritMaxHeight : childxxx.inheritMaxWidth;
               if (across) {
                  float largest = siblingMax(c, childxxx, !c.horizontal);
                  if (c.horizontal) {
                     childxxx.forcedHeight = Math.max(childxxx.h, largest);
                  } else {
                     childxxx.forcedWidth = Math.max(childxxx.w, largest);
                  }

                  this.measure(childxxx, w, h, false);
               }
            }
         }
      }
   }

   private static <T> List<T> add(@Nullable List<T> list, T value) {
      List<T> result = (List<T>)(list == null ? new ArrayList<>(2) : list);
      result.add(value);
      return result;
   }

   private static float siblingMax(UiControl parent, UiControl self, boolean horizontal) {
      float max = 0.0F;

      for (UiControl sibling : parent.children) {
         if (sibling != self && sibling.ownVisible) {
            max = Math.max(max, horizontal ? sibling.w : sibling.h);
         }
      }

      return max;
   }

   static boolean ownCells(UiControl c) {
      return c.grid != null && c.grid.template() == null;
   }

   private void measureGrid(UiControl grid, float w, float h) {
      List<UiControl> items = grid.generated;
      int count = grid.gridVirtual ? Math.max(items.size(), grid.gridCount) : items.size();
      grid.gridCellW = 0.0F;
      grid.gridCellH = 0.0F;
      if (ownCells(grid)) {
         grid.gridColumnsUsed = Math.max(1, grid.gridColumns);
         grid.gridRowsUsed = Math.max(1, grid.gridRows);
         grid.gridCellW = Float.isNaN(w) ? Float.NaN : w / grid.gridColumnsUsed;
         grid.gridCellH = Float.isNaN(h) ? Float.NaN : h / grid.gridRowsUsed;
      } else if (count == 0) {
         grid.gridColumnsUsed = Math.max(0, grid.gridColumns);
         grid.gridRowsUsed = Math.max(0, grid.gridRows);
      } else {
         UiControl first = items.getFirst();
         this.measure(first, w, h, true);
         grid.gridCellW = first.w;
         grid.gridCellH = first.h;
         int columns = Math.max(1, grid.gridColumns);
         int rows = Math.max(1, grid.gridRows);
         String rescaling = grid.grid.rescaling();
         if ("horizontal".equals(rescaling) && !Float.isNaN(w) && grid.gridCellW > 0.0F) {
            columns = Math.max(1, (int)Math.floor(w / grid.gridCellW));
            rows = Math.max(1, (count + columns - 1) / columns);
         } else if ("vertical".equals(rescaling) && !Float.isNaN(h) && grid.gridCellH > 0.0F) {
            rows = Math.max(1, (int)Math.floor(h / grid.gridCellH));
            columns = Math.max(1, (count + rows - 1) / rows);
         }

         grid.gridColumnsUsed = columns;
         grid.gridRowsUsed = rows;

         for (UiControl item : items) {
            item.forcedWidth = Float.NaN;
            item.forcedHeight = Float.NaN;
            this.measure(item, w, h, item == first);
         }
      }
   }

   private void arrange(UiControl c, float parentX, float parentY, float parentW, float parentH, @Nullable float[] clip, float parentZ, boolean visible) {
      this.arrange(c, parentX, parentY, parentW, parentH, clip, parentZ, visible, true);
   }

   private void arrange(
      UiControl c, float parentX, float parentY, float parentW, float parentH, @Nullable float[] clip, float parentZ, boolean visible, boolean anchored
   ) {
      if (c.ownVisible && visible) {
         boolean offsets = anchored || (this.active & 1) == 0;
         float offsetX = offsets ? offset(c.offset[0], parentW, c) : 0.0F;
         float offsetY = offsets ? offset(c.offset[1], parentH, c) : 0.0F;
         if (c.useAnchoredOffset) {
            if (!Float.isNaN(c.anchoredX)) {
               offsetX = c.anchoredX * (parentW - c.w);
            }

            if (!Float.isNaN(c.anchoredY)) {
               offsetY = c.anchoredY * (parentH - c.h);
            }
         }

         UiAnchor from = anchored ? c.anchorFrom : UiAnchor.TOP_LEFT;
         UiAnchor to = anchored ? c.anchorTo : UiAnchor.TOP_LEFT;
         c.x = parentX + parentW * from.x - c.w * to.x + offsetX + c.inputShiftX;
         c.y = parentY + parentH * from.y - c.h * to.y + offsetY + c.inputShiftY;
         c.z = parentZ + c.layer;
         UiControl area = c.clipArea;
         if (area != null) {
            float[] rect = new float[]{area.x, area.y, area.w, area.h};
            clip = area.clip == null ? rect : intersect(area.clip, rect);
         }

         c.clip = c.allowClipping ? clip : null;
         float[] childClip = c.clip;
         if (c.clipsChildren) {
            float[] own = new float[]{c.x + c.clipOffsetX, c.y + c.clipOffsetY, c.w, c.h};
            childClip = c.clip == null ? own : intersect(c.clip, own);
         }

         if (c.type == UiType.STACK_PANEL) {
            float cursor = 0.0F;

            for (UiControl child : c.children) {
               if (child.ownVisible) {
                  if (c.horizontal) {
                     this.arrange(child, c.x + cursor, c.y, child.w, c.h, childClip, c.z, true, c.useChildAnchors);
                     cursor += child.w;
                  } else {
                     this.arrange(child, c.x, c.y + cursor, c.w, child.h, childClip, c.z, true, c.useChildAnchors);
                     cursor += child.h;
                  }
               }
            }
         } else {
            if (c.grid != null) {
               float startX = c.x;
               if ("horizontal".equals(c.grid.rescaling()) && c.gridCellW > 0.0F && c.w > c.gridColumnsUsed * c.gridCellW) {
                  startX += (c.w - c.gridColumnsUsed * c.gridCellW) / 2.0F;
               }

               int place = 0;

               for (UiControl item : c.generated) {
                  int index = item.collectionIndex >= 0 ? item.collectionIndex : place;
                  place++;
                  float[] position = item.props.get("grid_position") != null ? item.props.get("grid_position").floats() : null;
                  int column;
                  int row;
                  if (position != null && position.length >= 2) {
                     column = (int)position[0];
                     row = (int)position[1];
                  } else if (c.grid.vertical()) {
                     column = index / Math.max(1, c.gridRowsUsed);
                     row = index % Math.max(1, c.gridRowsUsed);
                  } else {
                     column = index % Math.max(1, c.gridColumnsUsed);
                     row = index / Math.max(1, c.gridColumnsUsed);
                  }

                  this.arrange(item, startX + column * c.gridCellW, c.y + row * c.gridCellH, c.gridCellW, c.gridCellH, childClip, c.z, true);
               }

               if (ownCells(c)) {
                  int columns = Math.max(1, c.gridColumnsUsed);
                  int rows = Math.max(1, c.gridRowsUsed);

                  for (int i = 0; i < c.children.size(); i++) {
                     UiControl childx = c.children.get(i);
                     float[] position = childx.props.get("grid_position") != null ? childx.props.get("grid_position").floats() : null;
                     boolean placed = position != null && position.length >= 2;
                     int column = placed ? (int)position[0] : (c.grid.vertical() ? i / rows : i % columns);
                     int row = placed ? (int)position[1] : (c.grid.vertical() ? i % rows : i / columns);
                     this.arrange(childx, c.x + column * c.gridCellW, c.y + row * c.gridCellH, c.gridCellW, c.gridCellH, childClip, c.z, true);
                  }

                  return;
               }
            }

            for (UiControl childx : c.children) {
               if (!childx.gridItem) {
                  this.arrange(childx, c.x, c.y, c.w, c.h, childClip, c.z, true);
               }
            }
         }
      }
   }

   private static float offset(UiLength length, float parent, UiControl c) {
      return length.kind != UiLength.Kind.TERMS ? 0.0F : length.px + length.parent * parent + length.width * c.w + length.height * c.h;
   }

   static float[] intersect(float[] a, float[] b) {
      float x = Math.max(a[0], b[0]);
      float y = Math.max(a[1], b[1]);
      float right = Math.min(a[0] + a[2], b[0] + b[2]);
      float bottom = Math.min(a[1] + a[3], b[1] + b[3]);
      return new float[]{x, y, Math.max(0.0F, right - x), Math.max(0.0F, bottom - y)};
   }
}
