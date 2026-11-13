package net.bedrocknative.ui;

import com.google.gson.JsonArray;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Map.Entry;
import net.bedrocknative.perf.RenderSwitches;
import org.jspecify.annotations.Nullable;

public final class UiScreenInstance implements UiAnimation.Events {
   public static final int MAX_GENERATED = 4096;
   static final int VIRTUAL_AFTER = 256;
   private static final int INITIAL_WINDOW = 128;
   private static final int WINDOW_MARGIN_ROWS = 2;
   final UiDefinitionSet definitions;
   final UiDataSource data;
   final UiEnvironment environment;
   final UiProblems problems;
   final UiGaps gaps = new UiGaps();
   private final UiDataSource observed;
   final Set<String> ignoredNames;
   final BitSet contributing = new BitSet();
   private final UiResolver resolver;
   @Nullable
   private UiControl root;
   int controlCount;
   boolean cutShort;
   private double time;
   private boolean first = true;
   private long dataVersion;
   private float width;
   private float height;
   private boolean layoutDirty = true;
   private boolean drawDirty = true;
   private long treeVersion;
   private long orderedVersion = -1L;
   private final List<UiControl> ordered = new ArrayList<>();
   private UiControl[] orderedArray = new UiControl[0];
   private UiControl[] animatedArray = new UiControl[0];
   private UiDrawList drawList = UiDrawList.EMPTY;
   private final Deque<String> pendingEvents = new ArrayDeque<>();
   private final List<UiControl> pendingDestroy = new ArrayList<>();
   private long layoutEpoch;
   private long placementVersion;
   private long updates;
   private long bindPasses;
   private long layouts;
   private long drawLists;
   private int layoutRules;
   private long layoutNanos;
   @Nullable
   private UiScreenInstance.LayoutListener layoutListener;
   private boolean unsettled = true;
   private long settledTree = -1L;
   private final List<UiControl> animated = new ArrayList<>();
   private long animatedVersion = -1L;
   private final List<UiControl> rearranged = new ArrayList<>();
   private final Map<String, Integer> radioSelection = new HashMap<>();

   private UiScreenInstance(UiDefinitionSet definitions, UiDataSource data, UiEnvironment environment, Set<String> ignoredNames, float width, float height) {
      this.definitions = definitions;
      this.data = data;
      this.environment = environment;
      this.problems = new UiProblems();
      this.observed = this.gaps.observing(data);
      this.ignoredNames = ignoredNames;
      this.resolver = new UiResolver(definitions, this.problems, this);
      this.width = width;
      this.height = height;
   }

   public static UiScreenInstance create(
      UiDefinitionSet definitions,
      String reference,
      Map<String, UiValue> engineVariables,
      UiDataSource data,
      UiEnvironment environment,
      Set<String> ignoredNames,
      float width,
      float height
   ) {
      UiScreenInstance instance = new UiScreenInstance(definitions, data, environment, ignoredNames, width, height);
      instance.layoutRules = UiLayout.scopeFor(reference);
      instance.root = instance.resolver.root(reference, UiScope.root(definitions.globals(), engineVariables));
      if (instance.root != null) {
         indexLoneItems(instance.root);
         inheritItemCollections(instance.root);
      }

      instance.treeVersion++;
      return instance;
   }

   static void indexLoneItems(UiControl from) {
      from.walk(control -> {
         if (control.type == UiType.BUTTON && control.collectionIndex < 0) {
            for (UiBinding binding : control.bindings) {
               String collection = binding.collection;
               if (binding.type == UiBinding.Type.COLLECTION_DETAILS && collection != null && !collection.isEmpty() && indexed(control, collection) == null) {
                  for (UiControl c = control.parent; c != null; c = c.parent) {
                     if (c.type == UiType.INPUT_PANEL) {
                        if (c.collectionIndex < 0) {
                           c.collectionIndex = 0;
                           c.indexCollection = collection;
                        }
                        break;
                     }
                  }

                  return;
               }
            }
         }
      });
   }

   static void inheritItemCollections(UiControl from) {
      from.walk(control -> {
         if (control.collectionIndex >= 0 && control.indexCollection == null) {
            for (UiControl c = control.parent; c != null; c = c.parent) {
               if (c.collectionName != null && !c.collectionName.isEmpty()) {
                  control.indexCollection = c.collectionName;
                  return;
               }
            }

            if (control.collectionName != null && !control.collectionName.isEmpty()) {
               control.indexCollection = control.collectionName;
            }
         }
      });
   }

   public static UiScreenInstance create(
      UiDefinitionSet definitions,
      String reference,
      Map<String, UiValue> engineVariables,
      UiDataSource data,
      UiEnvironment environment,
      float width,
      float height
   ) {
      return create(definitions, reference, engineVariables, data, environment, Set.of(), width, height);
   }

   public void layoutRules(int rules) {
      if (rules != this.layoutRules) {
         this.layoutRules = rules;
         this.layoutDirty = true;
      }
   }

   int layoutRules() {
      return this.layoutRules;
   }

   public static int bedrockLayout() {
      return 7;
   }

   boolean ignored(String name) {
      if (this.ignoredNames.isEmpty()) {
         return false;
      } else if (this.ignoredNames.contains(name)) {
         return true;
      } else {
         for (String ignored : this.ignoredNames) {
            if (ignored.endsWith("*") && name.startsWith(ignored.substring(0, ignored.length() - 1))) {
               return true;
            }
         }

         return false;
      }
   }

   @Nullable
   public UiControl root() {
      return this.root;
   }

   public UiProblems problems() {
      return this.problems;
   }

   public UiGaps gaps() {
      return this.gaps;
   }

   UiDataSource dataFor(UiControl control) {
      return control.fromServer ? this.observed : this.data;
   }

   public boolean serverContributed() {
      return this.definitions.fromServer(this.contributing);
   }

   @Nullable
   public String gapsLine() {
      return this.gaps.line(this.definitions::baseGameName);
   }

   @Nullable
   public String baseGameGapsLine() {
      return this.gaps.baseGameLine(this.definitions::baseGameName);
   }

   public UiDefinitionSet definitions() {
      return this.definitions;
   }

   public BitSet contributingPacks() {
      return (BitSet)this.contributing.clone();
   }

   public double time() {
      return this.time;
   }

   public int controlCount() {
      return this.controlCount;
   }

   public boolean cutShort() {
      return this.cutShort;
   }

   public long layoutNanos() {
      return this.layoutNanos;
   }

   public UiScreenInstance.UpdateCounts updateCounts() {
      return new UiScreenInstance.UpdateCounts(this.updates, this.bindPasses, this.layouts, this.drawLists);
   }

   public float width() {
      return this.width;
   }

   public float height() {
      return this.height;
   }

   public void resize(float newWidth, float newHeight) {
      if (newWidth != this.width || newHeight != this.height) {
         this.width = newWidth;
         this.height = newHeight;
         this.layoutDirty = true;
      }
   }

   @Nullable
   public UiControl find(String name) {
      return this.root == null ? null : this.root.find(name);
   }

   public List<UiControl> findAll(String name) {
      return this.root == null ? List.of() : this.root.findAll(name);
   }

   public UiDrawList drawList() {
      return this.drawList;
   }

   public void invalidate() {
      this.layoutDirty = true;
   }

   void layoutListener(@Nullable UiScreenInstance.LayoutListener listener) {
      this.layoutListener = listener;
      this.layoutDirty = true;
   }

   List<UiControl> controls() {
      return this.ordered();
   }

   public long treeVersion() {
      return this.treeVersion;
   }

   long placementVersion() {
      return this.placementVersion;
   }

   void rearrange(UiControl control) {
      if (!this.rearranged.contains(control)) {
         this.rearranged.add(control);
      }
   }

   public int factoryEvent(String factoryName, String controlId, Map<String, UiValue> variables, boolean replace) {
      return this.factoryEvent(factoryName, controlId, variables, replace, null, -1);
   }

   public int factoryEvent(String factoryName, String controlId, Map<String, UiValue> variables, boolean replace, @Nullable String collection, int index) {
      return this.factoryEvent(factoryName, controlId, variables, replace, collection, index, Map.of());
   }

   public int factoryEvent(String factoryName, String controlId, Map<String, UiValue> variables, Map<String, UiValue> bag) {
      return this.factoryEvent(factoryName, controlId, variables, false, null, -1, bag);
   }

   private int factoryEvent(
      String factoryName, String controlId, Map<String, UiValue> variables, boolean replace, @Nullable String collection, int index, Map<String, UiValue> bag
   ) {
      if (this.root == null) {
         return 0;
      } else {
         int spawned = 0;

         for (UiControl control : List.copyOf(this.ordered())) {
            UiControl.Factory factory = control.factory;
            if (factory != null && !factory.dataDriven() && factory.name().equals(factoryName) && (control.parent != null || control == this.root)) {
               String reference = factory.reference(controlId);
               if (reference != null) {
                  if (replace) {
                     for (UiControl old : List.copyOf(control.generated)) {
                        if (old.spawnedId == null || old.spawnedId.equals(controlId)) {
                           this.destroyNow(old);
                        }
                     }
                  }

                  UiControl child = this.resolver.spawn(control, reference, factory.namespace(), null, variables);
                  if (child != null) {
                     child.spawnedId = controlId;
                     if (collection != null && index >= 0) {
                        child.indexCollection = collection;
                        child.collectionIndex = index;
                     }

                     if (!bag.isEmpty()) {
                        child.walk(made -> {
                           bag.forEach(made::seed);
                           made.dirty = true;
                        });
                     }

                     this.attach(control, child, -1);
                     control.generated.add(child);
                     if (factory.maxChildren() > 0) {
                        while (control.generated.size() > factory.maxChildren()) {
                           this.destroyNow(control.generated.getFirst());
                        }
                     }

                     spawned++;
                  }
               }
            }
         }

         return spawned;
      }
   }

   void respawn(UiControl factoryControl, List<Entry<String, Map<String, UiValue>>> children) {
      UiControl.Factory factory = factoryControl.factory;
      if (factory != null && !factory.dataDriven()) {
         List.copyOf(factoryControl.generated).forEach(this::destroyNow);

         for (Entry<String, Map<String, UiValue>> child : children) {
            String reference = factory.reference(child.getKey());
            UiControl spawned = reference == null ? null : this.resolver.spawn(factoryControl, reference, factory.namespace(), null, child.getValue());
            if (spawned != null) {
               this.attach(factoryControl, spawned, -1);
               factoryControl.generated.add(spawned);
            }
         }
      }
   }

   public void clearFactory(String factoryName) {
      for (UiControl control : List.copyOf(this.ordered())) {
         if (control.factory != null && !control.factory.dataDriven() && control.factory.name().equals(factoryName)) {
            List.copyOf(control.generated).forEach(this::destroyNow);
         }
      }
   }

   public boolean factoryEmpty(String factoryName) {
      for (UiControl control : this.ordered()) {
         if (control.factory != null && control.factory.name().equals(factoryName) && !control.generated.isEmpty()) {
            return false;
         }
      }

      return true;
   }

   public void fireEvent(String name) {
      this.pendingEvents.add(name);
   }

   @Override
   public void event(String name) {
      this.pendingEvents.add(name);
   }

   @Override
   public void destroy(UiControl from, String name) {
      for (UiControl control = from; control != null; control = control.parent) {
         if (control.name.equals(name) || control.spawnedId != null && control.defId.endsWith("." + name)) {
            this.pendingDestroy.add(control);
            return;
         }
      }
   }

   void attach(UiControl parent, UiControl child, int index) {
      child.parent = parent;
      if (child.factory == null && parent.generated.isEmpty() && parent.child(child.name) != null) {
         String base = child.name;
         int suffix = 2;

         while (parent.child(base + "#" + suffix) != null) {
            suffix++;
         }

         this.problems.add("duplicate-name", parent + ": " + base);
         child.name = base + "#" + suffix;
      }

      if (index >= 0 && index <= parent.children.size()) {
         parent.children.add(index, child);
      } else {
         parent.children.add(child);
      }

      if (this.root != null && this.connected(parent)) {
         indexLoneItems(child);
         inheritItemCollections(child);
      }

      this.treeVersion++;
      this.layoutDirty = true;
   }

   private boolean connected(UiControl control) {
      UiControl top = control;

      while (top.parent != null) {
         top = top.parent;
      }

      return top == this.root;
   }

   private void destroyNow(UiControl control) {
      UiControl parent = control.parent;
      if (parent != null) {
         parent.children.remove(control);
         int generatedIndex = parent.generated.indexOf(control);
         if (generatedIndex >= 0) {
            if (parent.factory != null && parent.factory.dataDriven()) {
               parent.generated.set(generatedIndex, null);
            } else {
               parent.generated.remove(generatedIndex);
            }
         }

         control.parent = null;
         this.controlCount = this.controlCount - control.count();
         this.treeVersion++;
         this.layoutDirty = true;
      }
   }

   private List<UiControl> ordered() {
      if (this.orderedVersion != this.treeVersion) {
         this.ordered.clear();
         if (this.root != null) {
            this.root.walk(this.ordered::add);
         }

         this.orderedArray = this.ordered.toArray(new UiControl[0]);
         this.orderedVersion = this.treeVersion;
      }

      return this.ordered;
   }

   private UiControl[] orderedSnapshot() {
      this.ordered();
      return this.orderedArray;
   }

   public void update(double now) {
      this.time = now;
      if (this.root != null) {
         this.updates++;
         long version = this.data.version();
         boolean dataChanged = this.first || version != this.dataVersion;
         this.dataVersion = version;
         if (dataChanged || this.unsettled || this.settledTree != this.treeVersion) {
            this.bindPasses++;
            this.bind(dataChanged);
         }

         this.animate(now);
         this.first = false;
         if (!this.layoutDirty && !this.rearranged.isEmpty()) {
            this.placementVersion++;

            for (UiControl control : this.rearranged) {
               if (control.parent != null && !UiLayout.rearrange(control, this.environment, this.layoutEpoch, this.layoutRules)) {
                  this.layoutDirty = true;
               }
            }

            this.drawDirty = true;
         }

         this.rearranged.clear();
         if (this.layoutDirty) {
            this.layouts++;
            long start = System.nanoTime();
            this.layout();

            for (int pass = 0; pass < 3 && this.realizeVirtualGrids(); pass++) {
               this.bind(false);
               this.layout();
            }

            this.layoutNanos = System.nanoTime() - start;
            this.layoutDirty = false;
            this.drawDirty = true;
         }

         if (this.drawDirty) {
            this.drawLists++;
            this.drawList = UiDrawList.build(this.root, this.environment);
            this.drawDirty = false;
         }
      }
   }

   private void bind(boolean dataChanged) {
      this.unsettled = false;
      boolean changed = dataChanged;

      for (int pass = 0; pass < 4; pass++) {
         long before = this.treeVersion;
         UiControl[] controls = this.orderedSnapshot();

         for (UiControl control : controls) {
            if (changed && control.readsData) {
               control.dirty = true;
            }

            this.dataBindings(control, changed || !control.bindingsRun);
         }

         for (UiControl control : controls) {
            this.viewBindings(control);
         }

         this.applyAll(controls);

         for (UiControl control : controls) {
            if (control.parent != null || control == this.root) {
               if (control.factory != null && control.factory.dataDriven()) {
                  if (control.effectiveVisible) {
                     this.syncFactory(control);
                  }
               } else if (control.grid != null) {
                  this.syncGrid(control);
               }
            }
         }

         for (UiControl controlx : controls) {
            controlx.bindingsRun = true;
         }

         if (before == this.treeVersion) {
            break;
         }

         changed = false;
      }

      this.settledTree = this.treeVersion;
   }

   private void layout() {
      this.layoutEpoch++;
      this.placementVersion++;
      UiLayout.layout(this.root, this.width, this.height, this.environment, this.layoutEpoch, this.layoutRules);

      for (int pass = 0; pass < 3 && this.layoutListener != null && this.layoutListener.afterLayout(); pass++) {
         this.applyAll(this.orderedSnapshot(), false);
         this.layoutEpoch++;
         UiLayout.layout(this.root, this.width, this.height, this.environment, this.layoutEpoch, this.layoutRules);
      }
   }

   private void dataBindings(UiControl control, boolean refresh) {
      List<UiBinding> bindings = control.bindings;

      for (int i = 0; i < bindings.size(); i++) {
         UiBinding binding = bindings.get(i);
         if (binding.type != UiBinding.Type.VIEW && binding.type != UiBinding.Type.COLLECTION_DETAILS) {
            boolean run = switch (binding.condition) {
               case ONCE -> !control.bindingsRun;
               case VISIBILITY_CHANGED -> !control.bindingsRun;
               case VISIBLE, ALWAYS_WHEN_VISIBLE -> (refresh || control.becameVisible) && control.effectiveVisible;
               default -> refresh;
            };
            if (run) {
               this.readBinding(control, binding);
            }
         }
      }
   }

   private void readBinding(UiControl control, UiBinding binding) {
      UiDataSource data = this.dataFor(control);
      UiValue value;
      if (binding.type == UiBinding.Type.COLLECTION) {
         UiControl indexed = indexed(control, binding.collection);
         if (indexed == null) {
            String collection = binding.collection;
            if (collection != null && !collection.isEmpty() && (binding.name != null || binding.expression != null)) {
               if (binding.expression != null) {
                  value = binding.expression.eval(name -> data.item(collection, -1, name, -1), this.problems);
               } else {
                  value = data.item(collection, -1, binding.name, -1);
               }
            } else {
               value = UiValue.NULL;
            }
         } else {
            String collection = binding.collection != null ? binding.collection : indexed.indexCollection;
            int index = indexed.collectionIndex;
            int outer = outerIndex(indexed);
            if (collection == null) {
               value = UiValue.NULL;
            } else if (binding.expression != null) {
               value = binding.expression.eval(name -> data.item(collection, index, name, outer), this.problems);
            } else {
               value = data.item(collection, index, binding.name, outer);
            }
         }
      } else if (control.contextual) {
         UiItemPath path = control.itemPath();
         value = binding.expression != null ? binding.expression.eval(name -> data.global(name, path), this.problems) : data.global(binding.name, path);
      } else if (binding.expression != null) {
         value = binding.expression.eval(data::global, this.problems);
      } else {
         value = data.global(binding.name);
      }

      if (value.isNull() && radioState(control, binding)) {
         control.radioManaged = true;
         value = UiValue.of(this.radioChecked(control));
      } else if (binding.type == UiBinding.Type.NONE && value.isNull()) {
         return;
      }

      if (binding.type != UiBinding.Type.GLOBAL
         || binding.expression != null
         || !value.isNull()
         || !control.isSeeded(binding.target)
         || !(control.bag.get(binding.target) instanceof UiValue held && !held.isNull())) {
         binding.reads++;
         this.write(control, binding.target, value);
      }
   }

   static int outerIndex(UiControl indexed) {
      for (UiControl c = indexed.parent; c != null; c = c.parent) {
         if (c.collectionIndex >= 0 && !Objects.equals(c.indexCollection, indexed.indexCollection)) {
            return c.collectionIndex;
         }
      }

      return -1;
   }

   @Nullable
   static UiControl indexed(UiControl control, @Nullable String collection) {
      for (UiControl c = control; c != null; c = c.parent) {
         if (c.collectionIndex >= 0 && (collection == null || c.indexCollection == null || collection.equals(c.indexCollection))) {
            return c;
         }
      }

      return null;
   }

   private static boolean radioState(UiControl control, UiBinding binding) {
      return control.type == UiType.TOGGLE
         && "#toggle_state".equals(binding.target)
         && "#toggle_state".equals(binding.name)
         && (binding.type == UiBinding.Type.GLOBAL || binding.type == UiBinding.Type.NONE)
         && radio(control);
   }

   static boolean radio(UiControl control) {
      UiValue group = control.props.get("radio_toggle_group");
      return group != null && group.truthy() && toggleName(control) != null;
   }

   @Nullable
   static String toggleName(UiControl control) {
      UiValue name = control.props.get("toggle_name");
      String text = name == null ? null : name.stringOrNull();
      return text != null && !text.isEmpty() ? text : null;
   }

   public int radioSelection(String toggleName) {
      UiValue fromData = this.data.radioSelection(toggleName);
      if (fromData.isNumber()) {
         return (int)fromData.asNumber();
      } else {
         Integer own = this.radioSelection.get(toggleName);
         if (own != null) {
            return own;
         } else {
            int selected = 0;

            for (UiControl control : this.ordered()) {
               if (control.type == UiType.TOGGLE && toggleName.equals(toggleName(control)) && radio(control)) {
                  UiValue fallback = control.props.get("toggle_group_default_selected");
                  selected = fallback != null && fallback.isNumber() ? (int)fallback.asNumber() : 0;
                  break;
               }
            }

            this.radioSelection.put(toggleName, selected);
            return selected;
         }
      }
   }

   boolean radioChecked(UiControl toggle) {
      String name = toggleName(toggle);
      int index = this.radioIndex(toggle);
      return name != null && index >= 0 && this.radioSelection(name) == index;
   }

   public int radioIndex(UiControl toggle) {
      UiValue forced = toggle.props.get("toggle_group_forced_index");
      if (forced != null && forced.isNumber() && forced.asNumber() >= 0.0) {
         return (int)forced.asNumber();
      } else {
         String binding = forced == null ? null : forced.stringOrNull();
         if (binding != null && binding.startsWith("#")) {
            UiValue found = null;

            for (UiControl c = toggle; c != null && found == null; c = c.parent) {
               found = c.bag.get(binding);
            }

            if (found == null) {
               UiControl item = indexed(toggle, null);
               found = item != null && item.indexCollection != null
                  ? this.data.item(item.indexCollection, item.collectionIndex, binding, outerIndex(item))
                  : this.data.global(binding);
            }

            if (found.isNumber() && found.asNumber() >= 0.0) {
               return (int)found.asNumber();
            }
         }

         String name = toggleName(toggle);
         int position = 0;

         for (UiControl control : this.ordered()) {
            if (control == toggle) {
               return position;
            }

            if (control.type == UiType.TOGGLE && name != null && name.equals(toggleName(control)) && radio(control)) {
               position++;
            }
         }

         return -1;
      }
   }

   void selectRadio(UiControl toggle) {
      String name = toggleName(toggle);
      int index = this.radioIndex(toggle);
      if (name != null && index >= 0) {
         this.selectRadio(name, index);
      }
   }

   public void selectRadio(String toggleName, int index) {
      this.radioSelection.put(toggleName, index);

      for (UiControl control : this.ordered()) {
         if (control.radioManaged && toggleName.equals(toggleName(control))) {
            this.write(control, "#toggle_state", UiValue.of(this.radioIndex(control) == index));
         }
      }

      this.data.radioSelected(toggleName, index);
      this.unsettled = true;
   }

   private void viewBindings(UiControl control) {
      List<UiBinding> bindings = control.bindings;

      for (int i = 0; i < bindings.size(); i++) {
         UiBinding binding = bindings.get(i);
         if (binding.type == UiBinding.Type.VIEW
            && (binding.condition != UiBinding.Condition.ONCE || !control.bindingsRun)
            && (binding.condition != UiBinding.Condition.VISIBILITY_CHANGED || !control.bindingsRun)) {
            UiControl source = this.source(control, binding);
            if (source == null) {
               this.problems.add("view-source", control + ": no control " + binding.sourceControl);
            } else {
               UiDataSource reads = this.dataFor(control);
               UiValue value = binding.expression.eval(name -> source.read(name, reads), this.problems);
               binding.reads++;
               this.write(control, binding.target, value);
            }
         }
      }
   }

   @Nullable
   private UiControl source(UiControl control, UiBinding binding) {
      if (binding.sourceControl == null) {
         return control;
      } else if (binding.resolvedSource != null && binding.resolvedTreeVersion == this.treeVersion) {
         return binding.resolvedSource;
      } else {
         UiControl found = null;
         if (binding.siblingScope && control.parent != null) {
            found = below(control.parent, binding.sourceControl);
         }

         if (found == null) {
            found = below(control, binding.sourceControl);
         }

         for (UiControl c = control.parent; found == null && c != null; c = c.parent) {
            if (c.name.equals(binding.sourceControl)) {
               found = c;
            }
         }

         if (found == null && this.root != null) {
            found = this.root.find(binding.sourceControl);
         }

         binding.resolvedSource = found;
         binding.resolvedTreeVersion = this.treeVersion;
         return found;
      }
   }

   @Nullable
   private static UiControl below(UiControl from, String name) {
      Deque<UiControl> queue = new ArrayDeque<>(from.children);

      while (!queue.isEmpty()) {
         UiControl control = queue.poll();
         if (control.name.equals(name)) {
            return control;
         }

         queue.addAll(control.children);
      }

      return null;
   }

   private void write(UiControl control, @Nullable String key, UiValue value) {
      if (key != null) {
         UiValue previous = control.bag.get(key);
         if (!value.equals(previous)) {
            control.bag.put(key, value);
            control.dirty = true;
            this.unsettled = true;
         }

         control.unseed(key);
      }
   }

   void touch() {
      this.unsettled = true;
   }

   private void applyAll(UiControl[] controls) {
      this.applyAll(controls, true);
   }

   private void applyAll(UiControl[] controls, boolean fresh) {
      for (UiControl control : controls) {
         UiControl parent = control.parent;
         boolean parentVisible = parent == null || parent.effectiveVisible;
         if (control.dirty || !control.applied) {
            boolean changed = UiProps.apply(control, this);
            if (control.textDirty && control.type == UiType.LABEL && !control.sizeChanged && UiLayout.reflowText(control, this.environment)) {
               this.layoutDirty = true;
            }

            if (changed) {
               if (!this.layoutDirty && control.offsetChanged && !control.sizeChanged && !control.visibleChanged) {
                  this.rearrange(control);
               } else {
                  this.layoutDirty = true;
               }
            }

            control.textDirty = false;
            control.applied = true;
            control.dirty = false;
            this.drawDirty = true;
         }

         boolean enabled = control.ownEnabled && (parent == null || parent.enabled);
         if (enabled != control.enabled) {
            if (UiProps.apply(control, this)) {
               this.layoutDirty = true;
            }

            this.drawDirty = true;
            this.unsettled = true;
         }

         boolean visible = parentVisible && control.ownVisible;
         control.becameVisible = visible && !control.effectiveVisible || !fresh && control.becameVisible;
         if (visible != control.effectiveVisible) {
            control.effectiveVisible = visible;
            this.layoutDirty = true;
            this.drawDirty = true;
            this.unsettled = true;
         }

         if (control.ownVisible != control.lastOwnVisible) {
            control.lastOwnVisible = control.ownVisible;
            if (control.bindingsRun) {
               this.visibilityChanged(control);
            }
         }
      }
   }

   private void visibilityChanged(UiControl control) {
      List<UiBinding> bindings = control.bindings;

      for (int i = 0; i < bindings.size(); i++) {
         UiBinding binding = bindings.get(i);
         if (binding.condition == UiBinding.Condition.VISIBILITY_CHANGED) {
            if (binding.type == UiBinding.Type.VIEW) {
               UiControl source = this.source(control, binding);
               if (source != null) {
                  binding.reads++;
                  UiDataSource reads = this.dataFor(control);
                  this.write(control, binding.target, binding.expression.eval(name -> source.read(name, reads), this.problems));
               }
            } else if (binding.type != UiBinding.Type.COLLECTION_DETAILS) {
               this.readBinding(control, binding);
            }
         }
      }

      if (control.dirty) {
         if (UiProps.apply(control, this)) {
            this.layoutDirty = true;
         }

         control.dirty = false;
         this.drawDirty = true;
      }
   }

   private void syncFactory(UiControl control) {
      UiControl.Factory factory = control.factory;
      String collection = control.collectionName;
      UiValue lengthValue = control.bag.get("#collection_length");
      JsonArray kinds = lengthValue != null ? lengthValue.array() : null;
      int length = kinds != null ? kinds.size() : (lengthValue != null ? (int)lengthValue.asNumber() : (collection != null ? this.data.length(collection) : 0));
      if (length > 4096) {
         this.problems.add("limit", control + ": " + length + " factory items, keeping 4096");
         length = 4096;
      }

      if (factory.maxChildren() > 0) {
         length = Math.min(length, factory.maxChildren());
      }

      length = Math.max(0, length);

      for (int i = 0; i < length; i++) {
         String listed = kinds != null ? UiModifications.string(kinds.get(i)) : null;
         String kind = listed != null ? listed : this.data.kind(collection, i);
         if (i < control.generatedKinds.size()) {
            if (!Objects.equals(kind, control.generatedKinds.get(i))) {
               UiControl old = control.generated.get(i);
               int position = old != null ? control.children.indexOf(old) : this.positionFor(control, i);
               if (old != null) {
                  this.destroyNow(old);
               }

               control.generatedKinds.set(i, kind);
               UiControl child = this.spawnItem(control, factory.reference(kind), factory.namespace(), null, i);
               control.generated.set(i, child);
               if (child != null) {
                  this.attach(control, child, position);
               }
            }
         } else {
            control.generatedKinds.add(kind);
            UiControl child = this.spawnItem(control, factory.reference(kind), factory.namespace(), null, i);
            control.generated.add(child);
            if (child != null) {
               this.attach(control, child, -1);
            }
         }
      }

      while (control.generatedKinds.size() > length) {
         int last = control.generatedKinds.size() - 1;
         control.generatedKinds.remove(last);
         UiControl oldx = control.generated.get(last);
         if (oldx != null) {
            this.destroyNow(oldx);
         }

         control.generated.remove(last);
      }
   }

   private int positionFor(UiControl control, int index) {
      for (int i = index - 1; i >= 0; i--) {
         UiControl previous = control.generated.get(i);
         if (previous != null) {
            return control.children.indexOf(previous) + 1;
         }
      }

      return control.children.size() - (int)control.generated.stream().filter(Objects::nonNull).count();
   }

   private void syncGrid(UiControl control) {
      UiControl.Grid grid = control.grid;
      int columns = grid.columns();
      int rows = grid.rows();
      UiValue gridSize = control.bag.get("#get_grid_size");
      control.gridRowsSized = grid.template() != null && (grid.dimensionBinding() != null || gridSize != null);
      if (grid.dimensionBinding() != null || gridSize != null) {
         float[] dimensions = (grid.dimensionBinding() != null ? control.read(grid.dimensionBinding(), this.data) : gridSize).floats();
         if (dimensions != null && dimensions.length >= 2) {
            columns = (int)dimensions[0];
            rows = (int)dimensions[1];
         }
      }

      control.gridColumns = Math.max(0, columns);
      control.gridRows = Math.max(0, rows);
      if (grid.template() != null) {
         UiValue length = control.bag.get("#collection_length");
         UiValue maximum = control.bag.get("#maximum_grid_items");
         int count;
         if (length != null) {
            count = (int)length.asNumber();
         } else if (maximum != null) {
            count = (int)maximum.asNumber();
         } else if (grid.maximumItems() >= 0) {
            count = grid.maximumItems();
         } else {
            count = columns * rows;
         }

         int cap = grid.rescaling() != null ? 4096 : Math.min(4096, Math.max(0, columns) * Math.max(0, rows));
         count = Math.max(0, Math.min(count, cap));
         control.gridCount = count;
         UiControl view = count > 256 && grid.rescaling() != null ? scrollViewOf(control) : null;
         if (view != null) {
            this.syncVirtualGrid(control, grid, count);
         } else {
            if (control.gridVirtual) {
               control.gridVirtual = false;
               List.copyOf(control.generated).forEach(this::destroyNow);
            }

            while (control.generated.size() < count) {
               int index = control.generated.size();
               UiControl item = this.spawnItem(control, grid.template(), grid.namespace(), control.name + "_item" + index, index);
               if (item == null) {
                  break;
               }

               item.gridItem = true;
               control.generated.add(item);
               this.attach(control, item, -1);
            }

            while (control.generated.size() > count) {
               this.destroyNow(control.generated.getLast());
            }
         }
      }
   }

   private void syncVirtualGrid(UiControl control, UiControl.Grid grid, int count) {
      if (!control.gridVirtual) {
         List.copyOf(control.generated).forEach(this::destroyNow);
         control.gridVirtual = true;
         control.windowFirst = 0;
         control.windowLast = Math.min(count, 128);
      }

      int first = Math.min(control.windowFirst, count);
      int last = Math.min(Math.max(first, control.windowLast), count);
      BitSet present = new BitSet();

      for (UiControl item : List.copyOf(control.generated)) {
         if (item.collectionIndex >= first && item.collectionIndex < last) {
            present.set(item.collectionIndex);
         } else {
            this.destroyNow(item);
         }
      }

      for (int index = first; index < last; index++) {
         if (!present.get(index)) {
            UiControl itemx = this.spawnItem(control, grid.template(), grid.namespace(), control.name + "_item" + index, index);
            if (itemx == null) {
               break;
            }

            itemx.gridItem = true;
            int at = 0;

            while (at < control.generated.size() && control.generated.get(at).collectionIndex < index) {
               at++;
            }

            control.generated.add(at, itemx);
            this.attach(control, itemx, -1);
         }
      }
   }

   @Nullable
   private static UiControl scrollViewOf(UiControl control) {
      for (UiControl c = control.parent; c != null; c = c.parent) {
         if (c.type == UiType.SCROLL_VIEW) {
            return c;
         }
      }

      return null;
   }

   private boolean realizeVirtualGrids() {
      boolean changed = false;

      for (UiControl grid : this.orderedSnapshot()) {
         if (grid.gridVirtual && grid.grid != null && grid.parent != null && grid.effectiveVisible && !(grid.gridCellH <= 0.0F)) {
            UiControl view = scrollViewOf(grid);
            UiControl port = view == null ? null : UiInput.part(view, "scroll_view_port");
            if (port != null) {
               int columns = Math.max(1, grid.gridColumnsUsed);
               int firstRow = Math.max(0, (int)Math.floor((port.y - grid.y) / grid.gridCellH) - 2);
               int lastRow = Math.max(firstRow, (int)Math.ceil((port.y + port.h - grid.y) / grid.gridCellH) + 2);
               int first = Math.min(grid.gridCount, firstRow * columns);
               int last = Math.min(grid.gridCount, lastRow * columns);
               if (first != grid.windowFirst || last != grid.windowLast) {
                  grid.windowFirst = first;
                  grid.windowLast = last;
                  this.syncVirtualGrid(grid, grid.grid, grid.gridCount);
                  changed = true;
               }
            }
         }
      }

      return changed;
   }

   @Nullable
   private UiControl spawnItem(UiControl parent, @Nullable String reference, String namespace, @Nullable String name, int index) {
      if (reference == null) {
         return null;
      } else {
         UiControl item = this.resolver.spawn(parent, reference, namespace, name, Map.of());
         if (item != null) {
            item.collectionIndex = index;
            item.indexCollection = parent.collectionName;
         }

         return item;
      }
   }

   private UiControl[] animated() {
      if (this.animatedVersion != this.treeVersion) {
         this.animated.clear();

         for (UiControl control : this.orderedSnapshot()) {
            if (!control.tracks.isEmpty()) {
               this.animated.add(control);
            }
         }

         this.animatedArray = this.animated.toArray(new UiControl[0]);
         this.animatedVersion = this.treeVersion;
      }

      return this.animatedArray;
   }

   private void animate(double now) {
      for (int round = 0; round < 8; round++) {
         Set<UiControl> touched = this.pendingEvents.isEmpty() ? Set.of() : Collections.newSetFromMap(new IdentityHashMap<>());

         while (!this.pendingEvents.isEmpty()) {
            String event = this.pendingEvents.poll();

            for (UiControl control : this.animated()) {
               List<UiAnimation.Track> tracks = control.tracks;

               for (int i = 0; i < tracks.size(); i++) {
                  if (tracks.get(i).event(event, now)) {
                     touched.add(control);
                  }
               }
            }
         }

         for (UiControl control : this.animated()) {
            if (control.parent != null || control == this.root) {
               boolean moved = touched.contains(control);
               List<UiAnimation.Track> tracks = control.tracks;

               for (int ix = 0; ix < tracks.size(); ix++) {
                  UiAnimation.Track track = tracks.get(ix);
                  boolean wasPlaying = track.playing();
                  boolean changed = track.advance(now, this, this.resolver);
                  moved |= RenderSwitches.animationChanges ? changed : wasPlaying || track.playing();
               }

               if (moved) {
                  if (UiProps.apply(control, this)) {
                     if (!this.layoutDirty && control.offsetChanged && !control.sizeChanged && !control.visibleChanged) {
                        this.rearrange(control);
                     } else {
                        this.layoutDirty = true;
                     }
                  }

                  this.drawDirty = true;
               }
            }
         }

         if (!this.pendingDestroy.isEmpty()) {
            for (UiControl controlx : List.copyOf(this.pendingDestroy)) {
               this.destroyNow(controlx);
            }

            this.pendingDestroy.clear();
         }

         if (this.pendingEvents.isEmpty()) {
            break;
         }
      }
   }

   interface LayoutListener {
      boolean afterLayout();
   }

   public record UpdateCounts(long updates, long bindPasses, long layouts, long drawLists) {
      public UiScreenInstance.UpdateCounts since(UiScreenInstance.UpdateCounts before) {
         return new UiScreenInstance.UpdateCounts(
            this.updates - before.updates, this.bindPasses - before.bindPasses, this.layouts - before.layouts, this.drawLists - before.drawLists
         );
      }

      @Override
      public String toString() {
         return this.updates + " frames: binding passes " + this.bindPasses + ", layouts " + this.layouts + ", draw lists " + this.drawLists;
      }
   }
}
