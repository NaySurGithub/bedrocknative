package net.bedrocknative.ui;

import com.google.gson.JsonObject;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

public final class UiControl {
   String name;
   final UiType type;
   @Nullable UiControl parent;
   final List<UiControl> children = new ArrayList<>();
   final String defId;
   final UiScope scope;
   final Function<String, String> keyNamespaces;
   final Map<String, UiValue> props = new HashMap<>();
   final Map<String, UiValue> bag = new HashMap<>();
   private @Nullable Set<String> seeded;
   private final Map<String, UiExpression> propExpressions = new HashMap<>();
   List<UiBinding> bindings = List.of();
   List<JsonObject> buttonMappings = List.of();
   final List<UiAnimation.Track> tracks = new ArrayList<>(0);
   @Nullable String collectionName;
   int collectionIndex = -1;
   @Nullable String indexCollection;
   UiControl.@Nullable Factory factory;
   final List<UiControl> generated = new ArrayList<>(0);
   final List<String> generatedKinds = new ArrayList<>(0);
   boolean fromServer;
   @Nullable String spawnedId;
   UiControl.@Nullable Grid grid;
   UiLength[] size = new UiLength[]{UiLength.DEFAULT, UiLength.DEFAULT};
   UiLength @Nullable [] minSize;
   UiLength @Nullable [] maxSize;
   UiLength[] offset = new UiLength[]{UiLength.ZERO, UiLength.ZERO};
   UiAnchor anchorFrom = UiAnchor.CENTER;
   UiAnchor anchorTo = UiAnchor.CENTER;
   float layer;
   float alpha = 1.0F;
   boolean propagateAlpha;
   boolean ownVisible = true;
   boolean lastOwnVisible = true;
   boolean stateHidden;
   String state = "default";
   boolean becameVisible;
   boolean dirty = true;
   boolean readsData;
   boolean contextual;
   private @Nullable UiItemPath itemPath;
   boolean applied;
   boolean bindingsRun;
   int gridColumns;
   int gridRows;
   int gridColumnsUsed;
   int gridRowsUsed;
   float gridCellW;
   float gridCellH;
   boolean gridItem;
   int gridCount;
   boolean gridVirtual;
   boolean gridRowsSized;
   int windowFirst;
   int windowLast;
   boolean enabled = true;
   boolean ownEnabled = true;
   boolean clipsChildren;
   boolean allowClipping = true;
   float clipOffsetX;
   float clipOffsetY;
   boolean horizontal;
   boolean useChildAnchors;
   boolean inheritMaxWidth;
   boolean inheritMaxHeight;
   boolean useAnchoredOffset;
   float anchoredX = Float.NaN;
   float anchoredY = Float.NaN;
   float sizeBindingX = Float.NaN;
   float sizeBindingY = Float.NaN;
   String text = "";
   boolean textDirty;
   boolean offsetChanged;
   boolean sizeChanged;
   boolean visibleChanged;
   UiFont font = UiFont.DEFAULT;
   boolean shadow;
   float[] color = new float[]{1.0F, 1.0F, 1.0F, 1.0F};
   @Nullable String alignment;
   float linePadding;
   String texture = "";
   String fileSystem = "InUserPackage";
   float @Nullable [] uv;
   float @Nullable [] uvSize;
   float @Nullable [] nineslice;
   boolean keepRatio = true;
   @Nullable String tiled;
   float[] tiledScale = new float[]{1.0F, 1.0F};
   boolean bilinear;
   boolean grayscale;
   float clipRatio;
   String clipDirection = "left";
   @Nullable String renderer;
   float x;
   float y;
   float w;
   float h;
   float z;
   float drawAlpha = 1.0F;
   UiDrawCommand.@Nullable Image drawnImage;
   UiDrawList.@Nullable ImageInputs drawnInputs;
   float @Nullable [] clip;
   boolean effectiveVisible = true;
   long measureEpoch = -1L;
   float measuredParentW = Float.NaN;
   float measuredParentH = Float.NaN;
   float forcedWidth = Float.NaN;
   float forcedHeight = Float.NaN;
   float siblingMaxW;
   float siblingMaxH;
   List<String> lines = List.of();
   float inputShiftX;
   float inputShiftY;
   float inputWidth = Float.NaN;
   float inputHeight = Float.NaN;
   boolean inputHidden;
   boolean stateActive;
   float scroll;
   float scrollContentH = Float.NaN;
   @Nullable UiControl clipArea;
   boolean radioManaged;
   @Nullable UiScope factoryChildScope;

   UiControl(String name, UiType type, String defId, UiScope scope, Function<String, String> keyNamespaces) {
      this.name = name;
      this.type = type;
      this.defId = defId;
      this.scope = scope;
      this.keyNamespaces = keyNamespaces;
   }

   public String name() {
      return this.name;
   }

   public UiType type() {
      return this.type;
   }

   public String definition() {
      return this.defId;
   }

   public boolean fromServer() {
      return this.fromServer;
   }

   public @Nullable UiControl parent() {
      return this.parent;
   }

   public List<UiControl> children() {
      return Collections.unmodifiableList(this.children);
   }

   public UiScope scope() {
      return this.scope;
   }

   public @Nullable UiControl child(String childName) {
      for (UiControl child : this.children) {
         if (child.name.equals(childName)) {
            return child;
         }
      }

      return null;
   }

   public @Nullable UiControl find(String controlName) {
      Deque<UiControl> queue = new ArrayDeque<>();
      queue.add(this);

      while (!queue.isEmpty()) {
         UiControl control = queue.poll();
         if (control.name.equals(controlName)) {
            return control;
         }

         queue.addAll(control.children);
      }

      return null;
   }

   public List<UiControl> findAll(String controlName) {
      List<UiControl> found = new ArrayList<>();
      this.walk(control -> {
         if (control.name.equals(controlName)) {
            found.add(control);
         }
      });
      return found;
   }

   public void walk(Consumer<UiControl> visitor) {
      visitor.accept(this);

      for (int i = 0; i < this.children.size(); i++) {
         this.children.get(i).walk(visitor);
      }
   }

   public int count() {
      int[] count = new int[]{0};
      this.walk(control -> count[0]++);
      return count[0];
   }

   public @Nullable UiValue prop(String key) {
      return this.props.get(key);
   }

   void seed(String key, UiValue value) {
      this.bag.put(key, value);
      if (this.seeded == null) {
         this.seeded = new HashSet<>(4);
      }

      this.seeded.add(key);
   }

   boolean isSeeded(@Nullable String key) {
      return key != null && this.seeded != null && this.seeded.contains(key);
   }

   void unseed(String key) {
      if (this.seeded != null) {
         this.seeded.remove(key);
      }
   }

   public @Nullable UiValue bag(String key) {
      return this.bag.get(key);
   }

   public Map<String, UiValue> bagView() {
      return Collections.unmodifiableMap(this.bag);
   }

   public List<UiBinding> bindings() {
      return Collections.unmodifiableList(this.bindings);
   }

   public List<JsonObject> buttonMappings() {
      return Collections.unmodifiableList(this.buttonMappings);
   }

   public List<UiAnimation.Track> tracks() {
      return Collections.unmodifiableList(this.tracks);
   }

   public int collectionIndex() {
      return this.collectionIndex;
   }

   public @Nullable String collectionName() {
      return this.collectionName;
   }

   public @Nullable String itemCollection() {
      UiControl item = UiScreenInstance.indexed(this, null);
      return item == null ? null : item.indexCollection;
   }

   public int itemIndex() {
      UiControl item = UiScreenInstance.indexed(this, null);
      return item == null ? -1 : item.collectionIndex;
   }

   public boolean followsCursor() {
      UiValue value = this.props.get("follows_cursor");
      return value != null && value.truthy();
   }

   public float x() {
      return this.x;
   }

   public float y() {
      return this.y;
   }

   public float width() {
      return this.w;
   }

   public float height() {
      return this.h;
   }

   public float z() {
      return this.z;
   }

   public float alpha() {
      return this.alpha;
   }

   public float drawAlpha() {
      return this.drawAlpha;
   }

   public float @Nullable [] clip() {
      return this.clip;
   }

   public boolean visible() {
      return this.effectiveVisible;
   }

   public boolean ownVisible() {
      return this.ownVisible;
   }

   public String state() {
      return this.state;
   }

   public boolean enabled() {
      return this.enabled;
   }

   public float scrollPosition() {
      return this.scroll;
   }

   public String text() {
      return this.text;
   }

   public List<String> lines() {
      return this.lines;
   }

   public String texture() {
      return this.texture;
   }

   public UiFont font() {
      return this.font;
   }

   public float[] color() {
      return (float[])this.color.clone();
   }

   public @Nullable String renderer() {
      return this.renderer;
   }

   public UiLength[] size() {
      return (UiLength[])this.size.clone();
   }

   public UiLength[] offset() {
      return (UiLength[])this.offset.clone();
   }

   public boolean horizontal() {
      return this.horizontal;
   }

   public List<UiControl> generated() {
      return Collections.unmodifiableList(this.generated);
   }

   public int gridCount() {
      return this.gridVirtual ? this.gridCount : this.generated.size();
   }

   public @Nullable UiControl gridItem(int index) {
      for (UiControl item : this.generated) {
         if (item != null && item.collectionIndex == index) {
            return item;
         }
      }

      return null;
   }

   public boolean isFactory() {
      return this.factory != null;
   }

   public @Nullable String factoryName() {
      return this.factory == null ? null : this.factory.name();
   }

   boolean isDescendantOf(UiControl ancestor) {
      for (UiControl control = this; control != null; control = control.parent) {
         if (control == ancestor) {
            return true;
         }
      }

      return false;
   }

   UiValue resolved(String key, UiDataSource data, UiProblems problems, UiDefinitionSet definitions) {
      UiValue value = this.props.get(key);
      if (value == null) {
         return UiValue.NULL;
      }

      String text = value.stringOrNull();
      if (text == null || text.isEmpty() || text.charAt(0) == '@') {
         return value;
      }

      if (text.charAt(0) == '#' && isName(text, 1)) {
         return this.read(text, data);
      }

      if ((text.indexOf(35) >= 0 || text.indexOf(36) >= 0) && UiExpression.looksLikeExpression(text)) {
         UiExpression expression = this.propExpressions.computeIfAbsent(key, k -> definitions.expression(text).bind(this.scope::get));
         if (expression.valid()) {
            return expression.eval(name -> this.read(name, data), problems);
         }
      }

      return value;
   }

   UiValue read(String name, UiDataSource data) {
      UiValue value = this.bag.get(name);
      if (value != null) {
         return value;
      }

      this.readsData = true;
      return this.contextual ? data.global(name, this.itemPath()) : data.global(name);
   }

   public UiItemPath itemPath() {
      UiItemPath path = this.itemPath;
      if (path == null) {
         path = UiItemPath.of(this);
         this.itemPath = path;
      }

      return path;
   }

   public boolean contextual() {
      return this.contextual;
   }

   static boolean isName(String text, int from) {
      if (text.length() <= from) {
         return false;
      }

      for (int i = from; i < text.length(); i++) {
         char c = text.charAt(i);
         if (!Character.isLetterOrDigit(c) && c != '_' && c != '.') {
            return false;
         }
      }

      return true;
   }

   @Override
   public String toString() {
      return this.name + "(" + this.type.name().toLowerCase() + " " + this.defId + ")";
   }

   record Factory(
      String name, Map<String, String> controlIds, @Nullable String controlName, int maxChildren, boolean dataDriven, String namespace, Set<String> weak
   ) {
      @Nullable String reference(@Nullable String kind) {
         if (kind != null) {
            String id = this.controlIds.get(kind);
            if (id != null) {
               return id;
            }
         }

         return this.controlName;
      }
   }

   record Grid(
      @Nullable String template,
      String namespace,
      int columns,
      int rows,
      @Nullable String dimensionBinding,
      int maximumItems,
      @Nullable String rescaling,
      boolean vertical
   ) {
   }
}
