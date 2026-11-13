package net.bedrocknative.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import net.bedrocknative.content.PackSource;
import net.bedrocknative.content.PackStack;
import org.jspecify.annotations.Nullable;

public final class UiDefinitionSet {
   public static final String DEFS = "ui/_ui_defs.json";
   public static final String GLOBALS = "ui/_global_variables.json";
   public static final int MAX_FILE_BYTES = 8388608;
   public static final int MAX_INHERITANCE = 64;
   private final Map<String, Map<String, UiElementDef>> namespaces;
   private final Map<String, UiValue> globals;
   private final List<String> files;
   private final List<String> packs;
   private final UiProblems problems;
   private final int serverFrom;
   private final Set<String> baseNames;
   private final Map<String, UiTemplate> templates = new HashMap<>();
   private final Map<String, UiExpression> expressions = new ConcurrentHashMap<>();

   private UiDefinitionSet(
      Map<String, Map<String, UiElementDef>> namespaces,
      Map<String, UiValue> globals,
      List<String> files,
      List<String> packs,
      UiProblems problems,
      int serverFrom,
      Set<String> baseNames
   ) {
      this.namespaces = namespaces;
      this.globals = globals;
      this.files = files;
      this.packs = packs;
      this.problems = problems;
      this.serverFrom = serverFrom;
      this.baseNames = baseNames;
   }

   public static UiDefinitionSet load(PackStack stack) {
      int server = (int)stack.packs().stream().filter(PackStack::isServerPack).count();
      return new UiDefinitionSet.Loader(stack, server).load();
   }

   public static UiDefinitionSet load(List<? extends PackSource> bottomFirst) {
      return load(bottomFirst, 0);
   }

   public static UiDefinitionSet load(List<? extends PackSource> bottomFirst, int serverPacks) {
      PackStack stack = new PackStack();
      bottomFirst.forEach(stack::pushTop);
      return new UiDefinitionSet.Loader(stack, serverPacks).load();
   }

   public boolean fromServer(BitSet packIndices) {
      return this.serverFrom < this.packs.size() && packIndices.nextSetBit(this.serverFrom) >= 0;
   }

   public List<String> serverPacks(BitSet packIndices) {
      List<String> names = new ArrayList<>();

      for (int i = packIndices.nextSetBit(this.serverFrom); i >= 0 && i < this.packs.size(); i = packIndices.nextSetBit(i + 1)) {
         names.add(this.packs.get(i));
      }

      return names;
   }

   public boolean baseGameName(String name) {
      return this.baseNames.contains(name);
   }

   public UiProblems problems() {
      return this.problems;
   }

   public Map<String, UiValue> globals() {
      return this.globals;
   }

   public List<String> files() {
      return this.files;
   }

   public List<String> packs() {
      return this.packs;
   }

   public Set<String> namespaces() {
      return this.namespaces.keySet();
   }

   public Map<String, UiElementDef> namespace(String namespace) {
      return this.namespaces.getOrDefault(namespace, Map.of());
   }

   public int elementCount() {
      return this.namespaces.values().stream().mapToInt(Map::size).sum();
   }

   @Nullable
   public UiElementDef element(String reference, @Nullable String contextNamespace) {
      String ref = reference.startsWith("@") ? reference.substring(1) : reference;
      int dot = ref.indexOf(46);
      if (dot > 0) {
         Map<String, UiElementDef> table = this.namespaces.get(ref.substring(0, dot));
         if (table != null) {
            UiElementDef def = table.get(ref.substring(dot + 1));
            if (def != null) {
               return def;
            }
         }
      }

      if (contextNamespace != null) {
         Map<String, UiElementDef> table = this.namespaces.get(contextNamespace);
         if (table != null) {
            return table.get(ref);
         }
      }

      return null;
   }

   public UiExpression expression(String source) {
      UiExpression cached = this.expressions.get(source);
      if (cached == null) {
         cached = UiExpression.parse(source);
         if (this.expressions.size() < 100000) {
            this.expressions.put(source, cached);
         }
      }

      return cached;
   }

   @Nullable
   public UiTemplate template(UiElementDef def, Function<String, UiValue> scope) {
      return this.template(def, scope, 0, new LinkedHashSet<>());
   }

   @Nullable
   private UiTemplate template(UiElementDef def, Function<String, UiValue> scope, int depth, Set<UiElementDef> visiting) {
      String base = def.base;
      boolean dynamic = false;
      if (base != null && base.startsWith("$")) {
         UiValue value = scope.apply(base);
         String resolved = value == null ? null : value.stringOrNull();
         if (resolved == null || resolved.isEmpty()) {
            this.problems.add("unresolved-base", def + ": " + base + " is " + value);
            return null;
         }

         base = resolved;
         dynamic = true;
      }

      String key = def.id() + "@" + base;
      UiTemplate cached = this.templates.get(key);
      if (cached != null) {
         return cached;
      } else if (depth <= 64 && visiting.add(def)) {
         try {
            BitSet contributing = (BitSet)def.packs.clone();
            boolean deeperDynamic = false;
            JsonObject props;
            Map<String, String> keyNamespaces;
            if (base == null) {
               props = def.props;
               keyNamespaces = Map.of();
            } else {
               UiElementDef baseDef = this.element(base, def.namespace());
               if (baseDef == null) {
                  this.problems.add("unresolved-base", def + ": no element " + base);
                  return null;
               }

               UiTemplate baseTemplate = this.template(baseDef, scope, depth + 1, visiting);
               if (baseTemplate == null) {
                  return null;
               }

               deeperDynamic = baseTemplate.dynamic();
               props = new JsonObject();
               keyNamespaces = new HashMap<>();

               for (Entry<String, JsonElement> entry : baseTemplate.props().entrySet()) {
                  props.add(entry.getKey(), entry.getValue());
                  keyNamespaces.put(entry.getKey(), baseTemplate.namespaceOf(entry.getKey()));
               }

               for (Entry<String, JsonElement> entry : def.props.entrySet()) {
                  if (!entry.getKey().equals("modifications")) {
                     props.add(entry.getKey(), entry.getValue());
                     keyNamespaces.put(entry.getKey(), def.namespace());
                  }
               }

               contributing.or(baseTemplate.packs());
            }

            if (def.props.get("modifications") instanceof JsonArray pending) {
               props = this.applyPending(props, pending, def.toString());
            }

            UiTemplate template = new UiTemplate(def, props, keyNamespaces, contributing, dynamic || deeperDynamic);
            if (!deeperDynamic) {
               this.templates.put(key, template);
            }

            return template;
         } finally {
            visiting.remove(def);
         }
      } else {
         this.problems.add("inheritance-cycle", def + " via " + visiting);
         return null;
      }
   }

   JsonObject applyPending(JsonObject props, JsonArray pending, String where) {
      JsonObject copy = new JsonObject();

      for (Entry<String, JsonElement> entry : props.entrySet()) {
         if (!entry.getKey().equals("modifications")) {
            copy.add(entry.getKey(), entry.getValue());
         }
      }

      for (JsonElement element : pending) {
         if (element instanceof JsonObject modification) {
            String arrayName = UiModifications.arrayName(modification);
            if (copy.get(arrayName) instanceof JsonArray array) {
               JsonArray own = new JsonArray();
               array.forEach(own::add);
               copy.add(arrayName, own);
               UiModifications.apply(own, arrayName.equals("controls"), modification, this.problems, where);
            } else {
               this.problems.add("modification-skipped", where + ": no array " + arrayName);
            }
         }
      }

      return copy;
   }

   private static final class Loader {
      private final PackStack stack;
      private final List<PackSource> bottomFirst;
      private final int serverPacks;
      private final Map<PackSource, Integer> index = new IdentityHashMap<>();
      private final Map<String, Map<String, UiElementDef>> namespaces = new LinkedHashMap<>();
      private final Map<String, String> fileNamespaces = new HashMap<>();
      private final UiProblems problems = new UiProblems();

      Loader(PackStack stack, int serverPacks) {
         this.stack = stack;
         this.bottomFirst = new ArrayList<>(stack.packs().reversed());
         this.serverPacks = Math.max(0, Math.min(serverPacks, this.bottomFirst.size()));

         for (int i = 0; i < this.bottomFirst.size(); i++) {
            this.index.put(this.bottomFirst.get(i), i);
         }
      }

      UiDefinitionSet load() {
         List<String> files = this.defsUnion();
         Map<String, UiValue> globals = this.globals();
         List<List<UiDefinitionSet.Loader.Layer>> perPack = new ArrayList<>();

         for (int i = 0; i < this.bottomFirst.size(); i++) {
            perPack.add(new ArrayList<>());
         }

         for (String file : files) {
            for (PackStack.PackFile copy : this.stack.readAll(file)) {
               Integer pack = this.index.get(copy.pack());
               if (pack != null) {
                  if (copy.data().length > 8388608) {
                     this.problems.add("limit", copy.pack() + " " + file + " is larger than 8388608 bytes");
                  } else {
                     try {
                        perPack.get(pack).add(new UiDefinitionSet.Loader.Layer(file, UiJson.parseObject(copy.data())));
                     } catch (UiJson.Error var10) {
                        this.problems.add("parse", copy.pack() + " " + file + ": " + var10.getMessage());
                     }
                  }
               }
            }
         }

         Set<String> baseNames = new HashSet<>();
         int serverFrom = this.bottomFirst.size() - this.serverPacks;

         for (int pack = 0; pack < perPack.size(); pack++) {
            for (UiDefinitionSet.Loader.Layer layer : perPack.get(pack)) {
               if (pack < serverFrom) {
                  names(layer.root, baseNames);
               }

               this.merge(pack, layer);
            }
         }

         List<String> packNames = this.bottomFirst.stream().map(Object::toString).toList();
         return new UiDefinitionSet(this.namespaces, Map.copyOf(globals), List.copyOf(files), packNames, this.problems, serverFrom, Set.copyOf(baseNames));
      }

      private static void names(JsonElement element, Set<String> into) {
         if (element instanceof JsonObject object) {
            for (Entry<String, JsonElement> entry : object.entrySet()) {
               names(entry.getValue(), into);
            }
         } else if (element instanceof JsonArray) {
            for (JsonElement item : (JsonArray)element) {
               names(item, into);
            }
         } else if (element instanceof JsonPrimitive primitive && primitive.isString()) {
            String text = primitive.getAsString();

            for (int at = text.indexOf(35); at >= 0 && at < text.length(); at = text.indexOf(35, at + 1)) {
               int end = at + 1;

               while (end < text.length() && (Character.isLetterOrDigit(text.charAt(end)) || text.charAt(end) == '_' || text.charAt(end) == '.')) {
                  end++;
               }

               if (end > at + 1) {
                  into.add(text.substring(at, end));
               }
            }
         }
      }

      private List<String> defsUnion() {
         Set<String> files = new LinkedHashSet<>();

         for (PackStack.PackFile copy : this.stack.readAll("ui/_ui_defs.json")) {
            try {
               JsonElement parsed = UiJson.parse(copy.data());
               if (parsed instanceof JsonArray bare && bare.isEmpty()) {
                  this.problems.note("empty-ui-defs", copy.pack() + " ui/_ui_defs.json is an empty list: no UI file of that pack is listed");
               } else {
                  if (!(parsed instanceof JsonObject defs)) {
                     throw new UiJson.Error("not a JSON object");
                  }

                  JsonElement var7 = defs.get("ui_defs");
                  if (var7 instanceof JsonArray) {
                     for (JsonElement path : (JsonArray)var7) {
                        String file = UiModifications.string(path);
                        if (file != null && !file.isBlank()) {
                           files.add(file.trim().replace('\\', '/'));
                        }
                     }
                  }
               }
            } catch (UiJson.Error var10) {
               this.problems.add("parse", copy.pack() + " ui/_ui_defs.json: " + var10.getMessage());
            }
         }

         return new ArrayList<>(files);
      }

      private Map<String, UiValue> globals() {
         Map<String, UiValue> globals = new LinkedHashMap<>();

         for (PackStack.PackFile copy : this.stack.readAll("ui/_global_variables.json")) {
            try {
               for (Entry<String, JsonElement> entry : UiJson.parseObject(copy.data()).entrySet()) {
                  if (entry.getKey().startsWith("$")) {
                     globals.put(entry.getKey(), UiValue.of(entry.getValue()));
                  }
               }
            } catch (UiJson.Error var6) {
               this.problems.add("parse", copy.pack() + " ui/_global_variables.json: " + var6.getMessage());
            }
         }

         return globals;
      }

      private void merge(int pack, UiDefinitionSet.Loader.Layer layer) {
         String namespace = UiModifications.string(layer.root.get("namespace"));
         if (namespace != null && !namespace.isBlank()) {
            this.fileNamespaces.put(layer.file, namespace);
         } else {
            namespace = this.fileNamespaces.get(layer.file);
            if (namespace == null) {
               this.problems.add("parse", this.bottomFirst.get(pack) + " " + layer.file + ": no namespace and no lower copy of this file to take one from");
               return;
            }
         }

         Map<String, UiElementDef> table = this.namespaces.computeIfAbsent(namespace, keyx -> new LinkedHashMap<>());
         List<Entry<String, JsonElement>> pathKeys = new ArrayList<>();
         List<UiDefinitionSet.Loader.Deferred> deferred = new ArrayList<>();

         for (Entry<String, JsonElement> entry : layer.root.entrySet()) {
            String key = entry.getKey();
            if (!key.equals("namespace")) {
               if (entry.getValue() instanceof JsonObject object) {
                  if (key.indexOf(47) > 0 && key.indexOf(64) < 0) {
                     pathKeys.add(entry);
                  } else {
                     int at = key.indexOf(64);
                     String name = at < 0 ? key : key.substring(0, at);
                     String base = at < 0 ? null : key.substring(at + 1);
                     if (!name.isEmpty() && (base == null || !base.isEmpty())) {
                        if (base != null && base.startsWith("@")) {
                           base = base.substring(1);
                        }

                        UiElementDef existing = table.get(name);
                        if (existing == null) {
                           UiElementDef def = new UiElementDef(namespace, name, object, base, layer.file);
                           def.packs.set(pack);
                           table.put(name, def);
                        } else {
                           this.mergeInto(existing.props, object, existing + " from " + this.bottomFirst.get(pack), true, false);
                           deferred.add(new UiDefinitionSet.Loader.Deferred(existing.props, object, existing + " from " + this.bottomFirst.get(pack)));
                           if (base != null) {
                              existing.base = base;
                           }

                           existing.packs.set(pack);
                        }
                     } else {
                        this.problems.add("element", "bad key " + key + " in " + layer.file);
                     }
                  }
               } else {
                  this.problems.add("element", namespace + "." + key + " in " + layer.file + " is not an object");
               }
            }
         }

         this.applyPathKeys(table, namespace, pathKeys, deferred, pack, layer.file);
      }

      private void applyPathKeys(
         Map<String, UiElementDef> table,
         String namespace,
         List<Entry<String, JsonElement>> pathKeys,
         List<UiDefinitionSet.Loader.Deferred> deferred,
         int pack,
         String file
      ) {
         List<Entry<String, JsonElement>> late = new ArrayList<>();

         for (Entry<String, JsonElement> entry : pathKeys) {
            if (!this.applyPathKey(table, namespace, entry.getKey(), entry.getValue().getAsJsonObject(), pack, file, true, false)) {
               late.add(entry);
            }
         }

         for (UiDefinitionSet.Loader.Deferred later : deferred) {
            this.mergeInto(later.target, later.higher, later.where, false, true);
         }

         for (Entry<String, JsonElement> entryx : pathKeys) {
            if (!late.contains(entryx)) {
               this.applyPathKey(table, namespace, entryx.getKey(), entryx.getValue().getAsJsonObject(), pack, file, false, true);
            }
         }

         for (Entry<String, JsonElement> entryxx : late) {
            this.applyPathKey(table, namespace, entryxx.getKey(), entryxx.getValue().getAsJsonObject(), pack, file, true, true);
         }
      }

      private void mergeInto(JsonObject target, JsonObject higher, String where) {
         this.mergeInto(target, higher, where, true, true);
      }

      private void mergeInto(JsonObject target, JsonObject higher, String where, boolean properties, boolean applyModifications) {
         JsonArray modifications = null;

         for (Entry<String, JsonElement> entry : higher.entrySet()) {
            if (entry.getKey().equals("modifications")) {
               if (entry.getValue() instanceof JsonArray array) {
                  modifications = array;
               }
            } else if (properties) {
               target.add(entry.getKey(), entry.getValue());
            }
         }

         if (modifications != null && applyModifications) {
            for (JsonElement element : modifications) {
               if (element instanceof JsonObject modification && !UiModifications.apply(target, modification, this.problems, where)) {
                  JsonArray pending = target.get("modifications") instanceof JsonArray existing ? existing : new JsonArray();
                  pending.add(modification);
                  target.add("modifications", pending);
               }
            }
         }
      }

      private boolean applyPathKey(
         Map<String, UiElementDef> table,
         String namespace,
         String key,
         JsonObject object,
         int pack,
         String file,
         boolean properties,
         boolean applyModifications
      ) {
         boolean last = properties && applyModifications;
         String[] path = key.split("/");
         UiElementDef def = table.get(path[0]);
         if (def == null) {
            if (last) {
               this.problems.add("path-key", namespace + "." + key + " in " + file + ": no element " + path[0]);
            }

            return false;
         } else {
            JsonObject current = def.props;

            for (int i = 1; i < path.length; i++) {
               JsonObject child = null;
               if (current.get("controls") instanceof JsonArray controls) {
                  int at = UiModifications.controlIndex(controls, path[i]);
                  if (at >= 0) {
                     JsonObject entry = controls.get(at).getAsJsonObject();
                     if (((Entry)entry.entrySet().iterator().next()).getValue() instanceof JsonObject inner) {
                        child = inner;
                     }
                  }
               }

               if (child == null) {
                  if (last) {
                     this.problems.add("path-key", namespace + "." + key + " in " + file + ": no child " + path[i]);
                  }

                  return false;
               }

               current = child;
            }

            this.mergeInto(current, object, namespace + "." + key, properties, applyModifications);
            def.packs.set(pack);
            return true;
         }
      }

      private record Deferred(JsonObject target, JsonObject higher, String where) {
      }

      private record Layer(String file, JsonObject root) {
      }
   }
}
