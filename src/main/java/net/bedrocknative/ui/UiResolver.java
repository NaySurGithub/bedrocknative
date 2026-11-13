package net.bedrocknative.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

final class UiResolver implements UiAnimation.Resolver {
   static final int MAX_DEPTH = 128;
   static final int MAX_CONTROLS = 20000;
   private static final Set<String> STRUCTURAL = Set.of(
      "controls", "variables", "bindings", "button_mappings", "modifications", "property_bag", "factory", "anims", "ignored", "type", "control_ids"
   );
   private static final Set<String> ANIMATABLE = Set.of("alpha", "offset", "size", "color", "uv", "clip_ratio");
   private final UiDefinitionSet definitions;
   private final UiProblems problems;
   private final UiScreenInstance instance;
   private Object buildingName = "";
   private Object buildingDetail = "";
   private String buildingJoin = "";
   private final Map<UiTemplate, Map<JsonObject, UiResolver.Merged>> merges = new IdentityHashMap<>();
   private final Map<JsonObject, Map<UiScope, UiScope>> scopes = new IdentityHashMap<>();

   private String building() {
      return this.buildingJoin.equals(" (")
         ? this.buildingName + " (" + this.buildingDetail + ")"
         : this.buildingName + this.buildingJoin + this.buildingDetail;
   }

   UiResolver(UiDefinitionSet definitions, UiProblems problems, UiScreenInstance instance) {
      this.definitions = definitions;
      this.problems = problems;
      this.instance = instance;
   }

   @Nullable
   UiControl root(String reference, UiScope engine) {
      UiElementDef def = this.definitions.element(reference, null);
      if (def == null) {
         this.problems.add("unresolved-base", "no screen " + reference);
         return null;
      } else {
         UiTemplate template = this.definitions.template(def, engine::get);
         if (template == null) {
            return null;
         } else {
            this.instance.contributing.or(template.packs());
            return this.build(def.name(), template.props(), template::namespaceOf, def.id(), engine, 0, this.definitions.fromServer(template.packs()), Set.of());
         }
      }
   }

   @Nullable
   UiControl spawn(UiControl parent, String reference, String namespace, @Nullable String childName, Map<String, UiValue> variables) {
      String ref = reference.startsWith("@") ? reference.substring(1) : reference;
      int at = ref.indexOf(64);
      String name = childName;
      if (at > 0) {
         name = childName == null ? ref.substring(0, at) : childName;
         ref = ref.substring(at + 1);
      }

      UiElementDef def = this.definitions.element(ref, namespace);
      if (def == null) {
         this.problems.add("unresolved-base", parent + ": no element " + reference);
         return null;
      } else {
         UiScope scope = (parent.factory != null && parent.grid == null ? childScope(parent) : parent.scope).child(variables);
         UiTemplate template = this.definitions.template(def, scope::get);
         if (template == null) {
            this.problems.add("unresolved-base", parent + ": " + reference + " has no usable inheritance chain");
            return null;
         } else {
            this.instance.contributing.or(template.packs());
            return this.build(
               name != null ? name : def.name(),
               template.props(),
               template::namespaceOf,
               def.id(),
               scope,
               depth(parent) + 1,
               parent.fromServer || this.definitions.fromServer(template.packs()),
               Set.of()
            );
         }
      }
   }

   private static UiScope childScope(UiControl factory) {
      UiScope known = factory.factoryChildScope;
      if (known == null) {
         known = factory.scope.weakened(factory.factory.weak());
         factory.factoryChildScope = known;
      }

      return known;
   }

   private static Set<String> entryVariables(JsonObject value) {
      Set<String> names = null;

      for (String key : value.keySet()) {
         if (key.startsWith("$")) {
            if (names == null) {
               names = new HashSet<>();
            }

            names.add(variableOf(key));
         }
      }

      return names == null ? Set.of() : names;
   }

   private static int depth(UiControl control) {
      int depth = 0;

      for (UiControl c = control; c.parent != null; c = c.parent) {
         depth++;
      }

      return depth;
   }

   @Nullable
   private UiControl build(
      String name,
      JsonObject merged,
      Function<String, String> keyNamespaces,
      String defId,
      UiScope parentScope,
      int depth,
      boolean fromServer,
      Set<String> entryVariables
   ) {
      if (depth > 128) {
         this.problems.add("limit", "deeper than 128 at " + name);
         this.instance.cutShort = true;
         return null;
      } else if (this.instance.controlCount >= 20000) {
         this.problems.add("limit", "more than 20000 controls");
         this.instance.cutShort = true;
         return null;
      } else if (this.instance.ignored(name)) {
         return null;
      } else {
         this.buildingName = name;
         this.buildingJoin = " (";
         this.buildingDetail = defId;
         UiScope scope = this.cachedScope(merged, parentScope);
         JsonElement ignored = merged.get("ignored");
         if (ignored != null && this.condition(ignored, scope::get)) {
            return null;
         } else {
            UiType type = UiType.of(this.eval(merged.get("type"), scope::get).stringOrNull());
            if (type == null) {
               if (merged.has("anim_type")) {
                  this.problems.add("element", name + " (" + defId + ") is an animation used as a control");
                  return null;
               }

               if (merged.has("type")) {
                  this.problems.add("unknown-type", name + " (" + defId + "): " + merged.get("type"));
                  this.instance.gaps.unknownType(String.valueOf(this.eval(merged.get("type"), scope::get).stringOrNull()));
               }

               type = UiType.PANEL;
            }

            UiControl control = new UiControl(name, type, defId, scope, keyNamespaces);
            control.fromServer = fromServer;
            this.instance.controlCount++;

            for (Entry<String, JsonElement> entry : merged.entrySet()) {
               String key = entry.getKey();
               if (!key.startsWith("$") && !STRUCTURAL.contains(key)) {
                  control.props.put(key, this.eval(entry.getValue(), scope::get));
               }
            }

            JsonObject var22 = this.eval(merged.get("property_bag"), scope::get).object();
            if (var22 instanceof JsonObject) {
               for (Entry<String, JsonElement> entryx : var22.entrySet()) {
                  control.seed(entryx.getKey(), this.eval(entryx.getValue(), scope::get));
               }
            }

            control.collectionName = string(control.props.get("collection_name"));
            UiValue explicitIndex = control.props.get("collection_index");
            if (explicitIndex != null && explicitIndex.isNumber()) {
               control.collectionIndex = (int)explicitIndex.asNumber();
            }

            control.bindings = this.bindings(merged.get("bindings"), scope, control);

            for (UiBinding binding : control.bindings) {
               if (binding.type == UiBinding.Type.COLLECTION_DETAILS && binding.collection == null) {
                  control.contextual = true;
               }
            }

            control.buttonMappings = this.substituted(merged.get("button_mappings"), scope);
            control.factory = this.factory(control, merged, scope, keyNamespaces, entryVariables);
            if (type == UiType.GRID) {
               control.grid = this.grid(control, keyNamespaces);
            }

            JsonElement controls = merged.get("controls");
            if (controls != null) {
               UiValue controlsValue = this.eval(controls, scope::get);
               JsonArray array = controlsValue.array();
               if (array == null && controlsValue.stringOrNull() != null) {
                  this.problems
                     .note(
                        "controls-text",
                        name + " (" + defId + "): controls is the text \"" + controlsValue.stringOrNull() + "\", no children, read as Bedrock reads it"
                     );
               } else if (array != null) {
                  String namespace = keyNamespaces.apply("controls");

                  for (JsonElement entryx : array) {
                     UiControl child = this.child(control, entryx, namespace, depth + 1);
                     if (child != null) {
                        this.instance.attach(control, child, -1);
                     }
                  }
               } else {
                  this.problems.add("element", name + " (" + defId + "): controls is not an array");
               }
            }

            if (type == UiType.COLLECTION_PANEL) {
               for (int i = 0; i < control.children.size(); i++) {
                  UiControl child = control.children.get(i);
                  if (child.collectionIndex < 0) {
                     child.collectionIndex = i;
                     child.indexCollection = control.collectionName;
                  }
               }
            }

            if (type == UiType.GRID && control.grid.template() == null && control.collectionName != null) {
               indexGridChildren(control);
            }

            this.animations(control, merged, scope);
            return control;
         }
      }
   }

   private static void indexGridChildren(UiControl grid) {
      UiControl.Grid layout = grid.grid;

      for (int i = 0; i < grid.children.size(); i++) {
         UiControl child = grid.children.get(i);
         if (child.collectionIndex < 0) {
            float[] position = floats(child.props.get("grid_position"));
            int index = i;
            if (position != null && position.length >= 2) {
               int x = (int)position[0];
               int y = (int)position[1];
               index = layout.vertical() ? x * Math.max(1, layout.rows()) + y : y * Math.max(1, layout.columns()) + x;
            }

            child.collectionIndex = Math.max(0, index);
            child.indexCollection = grid.collectionName;
         }
      }
   }

   private UiScope scope(JsonObject merged, UiScope parentScope) {
      UiScope.Builder builder = parentScope.builder();
      Map<String, Integer> unassigned = new HashMap<>();

      for (String key : merged.keySet()) {
         if (key.startsWith("$")) {
            unassigned.merge(variableOf(key), 1, Integer::sum);
         }
      }

      List<Entry<String, JsonElement>> waiting = new ArrayList<>();

      for (Entry<String, JsonElement> entry : merged.entrySet()) {
         String keyx = entry.getKey();
         if (keyx.startsWith("$")) {
            if (this.readsLater(entry.getValue(), variableOf(keyx), unassigned)) {
               waiting.add(entry);
            } else {
               this.assign(builder, merged, entry, unassigned);
            }
         }
      }

      for (int round = 0; round < 8 && !waiting.isEmpty(); round++) {
         boolean last = round == 7;
         List<Entry<String, JsonElement>> still = new ArrayList<>();

         for (Entry<String, JsonElement> entryx : waiting) {
            if (!last && this.readsLater(entryx.getValue(), variableOf(entryx.getKey()), unassigned)) {
               still.add(entryx);
            } else {
               this.assign(builder, merged, entryx, unassigned);
            }
         }

         if (still.size() == waiting.size()) {
            still.forEach(entryxx -> this.assign(builder, merged, entryxx, unassigned));
            still.clear();
         }

         waiting = still;
      }

      JsonElement variables = merged.get("variables");
      if (variables != null) {
         UiValue value = this.eval(variables, builder::get);
         List<JsonObject> blocks = new ArrayList<>();
         if (value.object() != null) {
            blocks.add(value.object());
         } else if (value.array() != null) {
            for (JsonElement block : value.array()) {
               if (block instanceof JsonObject object) {
                  blocks.add(object);
               }
            }
         }

         for (JsonObject blockx : blocks) {
            JsonElement requires = blockx.get("requires");
            if (requires == null || this.condition(requires, builder::get)) {
               for (Entry<String, JsonElement> entryxx : blockx.entrySet()) {
                  String keyx = entryxx.getKey();
                  if (keyx.startsWith("$")) {
                     if (keyx.endsWith("|default")) {
                        String variable = keyx.substring(0, keyx.length() - "|default".length());
                        if (builder.defaultApplies(variable)) {
                           builder.putDefault(variable, this.eval(entryxx.getValue(), builder::get));
                        }
                     } else {
                        builder.put(keyx, this.eval(entryxx.getValue(), builder::get));
                     }
                  }
               }
            }
         }
      }

      return builder.build();
   }

   private static String variableOf(String key) {
      return key.endsWith("|default") ? key.substring(0, key.length() - "|default".length()) : key;
   }

   private boolean readsLater(JsonElement value, String self, Map<String, Integer> unassigned) {
      if (value instanceof JsonPrimitive primitive && primitive.isString()) {
         String text = primitive.getAsString();
         if (text.length() > 1 && text.charAt(0) == '$' && UiControl.isName(text, 1)) {
            return !text.equals(self) && unassigned.containsKey(text);
         } else if (text.indexOf(36) >= 0 && UiExpression.looksLikeExpression(text)) {
            UiExpression expression = this.definitions.expression(text);
            if (!expression.valid()) {
               return false;
            } else {
               for (String name : expression.dollarNames()) {
                  if (!name.equals(self) && unassigned.containsKey(name)) {
                     return true;
                  }
               }

               return false;
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private void assign(UiScope.Builder builder, JsonObject merged, Entry<String, JsonElement> entry, Map<String, Integer> unassigned) {
      String key = entry.getKey();
      String variable = variableOf(key);
      if (key.endsWith("|default")) {
         if (builder.defaultApplies(variable) && !merged.has(variable)) {
            builder.putDefault(variable, this.evalAssigned(entry.getValue(), builder::get));
         }
      } else {
         builder.put(key, this.evalAssigned(entry.getValue(), builder::get));
      }

      unassigned.computeIfPresent(variable, (name, count) -> count > 1 ? count - 1 : null);
   }

   private UiValue evalAssigned(JsonElement value, Function<String, UiValue> variables) {
      if (value instanceof JsonPrimitive primitive && primitive.isString()) {
         String text = primitive.getAsString();
         if (text.length() > 1 && text.charAt(0) == '$' && UiControl.isName(text, 1) && variables.apply(text) == null) {
            this.problems.note("optional-variable", text + " in " + this.building() + ": assigned to a variable, left unset like Bedrock");
            return UiValue.NULL;
         }
      }

      return this.eval(value, variables);
   }

   @Nullable
   private UiControl child(UiControl parent, JsonElement entry, String namespace, int depth) {
      if (entry instanceof JsonObject object && object.size() == 1) {
         Entry<String, JsonElement> only = (Entry<String, JsonElement>)object.entrySet().iterator().next();
         String key = only.getKey();
         JsonObject value = only.getValue() instanceof JsonObject inner ? inner : new JsonObject();
         int at = key.indexOf(64);
         String name = at < 0 ? key : key.substring(0, at);
         String base = at < 0 ? null : key.substring(at + 1);
         if (name.startsWith("$")) {
            UiValue resolved = parent.scope.get(name);
            if (resolved != null && !resolved.isNull()) {
               String text = resolved.stringOrNull();
               if (text == null || text.isEmpty()) {
                  if (!this.ignoredHere(value, parent.scope)) {
                     this.problems.add("undefined-variable", parent + ": child name " + name + " is " + resolved);
                  }

                  return null;
               }

               name = text;
            } else if (!this.ignoredHere(value, parent.scope)) {
               this.problems.note("literal-child-name", parent + ": child name " + name + " is not set: kept as its literal name, as Bedrock keeps it");
            }
         }

         if (base != null && base.startsWith("$")) {
            UiValue resolved = parent.scope.get(base);
            String text = resolved == null ? null : resolved.stringOrNull();
            if (text == null || text.isEmpty()) {
               if (!this.ignoredHere(value, parent.scope)) {
                  this.problems.add("unresolved-base", parent + ": child " + name + " base " + base + " is " + resolved + " (key " + key + ")");
               }

               return null;
            }

            base = text;
         }

         if (base != null && base.startsWith("@")) {
            base = base.substring(1);
         }

         String defId = parent.defId;
         if (base == null) {
            JsonObject merged = value;
            Function<String, String> keyNamespaces = k -> namespace;
            if (value.get("modifications") instanceof JsonArray pending) {
               merged = this.definitions.applyPending(value, pending, parent + "/" + name);
            }

            return this.build(name, merged, keyNamespaces, defId, parent.scope, depth, parent.fromServer, Set.of());
         } else {
            UiElementDef def = this.definitions.element(base, namespace);
            if (def == null) {
               def = this.definitions.element(base, parent.defId.substring(0, Math.max(0, parent.defId.indexOf(46))));
            }

            if (def == null) {
               if (!this.ignoredHere(value, parent.scope)) {
                  this.problems
                     .add("unresolved-base", parent + ": child " + name + " has no element " + base + (key.endsWith("@" + base) ? "" : " (key " + key + ")"));
               }

               return null;
            } else {
               UiTemplate template = this.definitions.template(def, parent.scope::get);
               if (template == null) {
                  this.problems.add("unresolved-base", parent + ": child " + name + " (" + def.id() + ") has no usable inheritance chain");
                  return null;
               } else {
                  this.instance.contributing.or(template.packs());
                  defId = def.id();
                  boolean serverMade = parent.fromServer || this.definitions.fromServer(template.packs());
                  UiResolver.Merged known = this.merges.computeIfAbsent(template, unused -> new IdentityHashMap<>()).get(value);
                  if (known != null && known.namespace().equals(namespace)) {
                     return this.build(name, known.merged(), known.keyNamespaces(), defId, parent.scope, depth, serverMade, entryVariables(value));
                  } else {
                     long problemsBefore = this.problems.changes();
                     JsonObject merged;
                     Function<String, String> keyNamespaces;
                     if (value.isEmpty()) {
                        merged = template.props();
                        keyNamespaces = template::namespaceOf;
                     } else {
                        merged = new JsonObject();

                        for (Entry<String, JsonElement> property : template.props().entrySet()) {
                           merged.add(property.getKey(), property.getValue());
                        }

                        for (Entry<String, JsonElement> property : value.entrySet()) {
                           merged.add(property.getKey(), property.getValue());
                        }

                        keyNamespaces = k -> value.has(k) ? namespace : template.namespaceOf(k);
                     }

                     if (merged.get("modifications") instanceof JsonArray pending) {
                        merged = this.definitions.applyPending(merged, pending, parent + "/" + name);
                     }

                     if (this.problems.changes() == problemsBefore) {
                        this.merges.get(template).put(value, new UiResolver.Merged(namespace, merged, keyNamespaces));
                     }

                     return this.build(name, merged, keyNamespaces, defId, parent.scope, depth, serverMade, entryVariables(value));
                  }
               }
            }
         }
      } else {
         this.problems.add("element", parent + ": a controls entry is not a single-key object");
         return null;
      }
   }

   private UiScope cachedScope(JsonObject merged, UiScope parentScope) {
      Map<UiScope, UiScope> byParent = this.scopes.computeIfAbsent(merged, key -> new IdentityHashMap<>());
      UiScope known = byParent.get(parentScope);
      if (known != null) {
         return known;
      } else {
         long problemsBefore = this.problems.changes();
         UiScope made = this.scope(merged, parentScope);
         if (this.problems.changes() == problemsBefore) {
            byParent.put(parentScope, made);
         }

         return made;
      }
   }

   private boolean ignoredHere(JsonObject value, UiScope scope) {
      JsonElement ignored = value.get("ignored");
      return ignored != null && this.condition(ignored, scope::get);
   }

   private List<UiBinding> bindings(@Nullable JsonElement raw, UiScope scope, Object where) {
      if (raw == null) {
         return List.of();
      } else {
         JsonArray array = this.eval(raw, scope::get).array();
         if (array == null) {
            return List.of();
         } else {
            List<UiBinding> bindings = new ArrayList<>(array.size());

            for (JsonElement element : array) {
               if (element instanceof JsonObject object) {
                  Map<String, UiValue> fields = new HashMap<>();

                  for (Entry<String, JsonElement> entry : object.entrySet()) {
                     fields.put(entry.getKey(), this.eval(entry.getValue(), scope::get));
                  }

                  if (!fields.containsKey("ignored") || !fields.get("ignored").truthy()) {
                     UiBinding binding = this.binding(fields, scope, where);
                     if (binding != null) {
                        bindings.add(binding);
                     }
                  }
               }
            }

            return bindings;
         }
      }
   }

   private void invalidName(String name, UiExpression expression, @Nullable String target, Object where) {
      if (name.indexOf(35) >= 0 || UiExpression.looksLikeExpression(name)) {
         this.problems
            .add(
               "expression",
               "binding dropped on " + where + (target != null ? " (target " + target + ")" : "") + ": binding_name " + name + ": " + expression.error()
            );
      }
   }

   private void lenient(UiExpression expression, String field, Object where) {
      if (expression.repairs() > 0) {
         String place = String.valueOf(where);
         this.problems
            .note(
               "expression-lenient",
               (place.isEmpty() ? "" : place + " ")
                  + field
                  + " "
                  + expression.source()
                  + ": "
                  + expression.repairs()
                  + " unbalanced parenthes"
                  + (expression.repairs() == 1 ? "is" : "es")
                  + " read as Bedrock reads them"
            );
      }
   }

   private UiExpression nameExpression(String name, UiScope scope) {
      return this.definitions.expression(name).bind(scope::get);
   }

   private static boolean plainText(String name, UiExpression expression) {
      return expression.valid() && expression.constant() && !name.trim().startsWith("(");
   }

   @Nullable
   private UiBinding binding(Map<String, UiValue> fields, UiScope scope, Object where) {
      String typeName = text(fields.get("binding_type"));
      String name = text(fields.get("binding_name"));
      String override = text(fields.get("binding_name_override"));
      String collection = text(fields.get("binding_collection_name"));
      UiBinding.Condition condition = UiBinding.Condition.of(text(fields.get("binding_condition")));
      UiBinding.Type type;
      if (typeName == null) {
         type = name != null ? UiBinding.Type.GLOBAL : null;
      } else {
         type = switch (typeName) {
            case "global" -> UiBinding.Type.GLOBAL;
            case "collection" -> UiBinding.Type.COLLECTION;
            case "collection_details" -> UiBinding.Type.COLLECTION_DETAILS;
            case "view" -> UiBinding.Type.VIEW;
            case "none" -> name != null ? UiBinding.Type.NONE : null;
            default -> null;
         };
      }

      if (type == null) {
         if (typeName != null && !typeName.equals("none")) {
            this.problems.add("binding", "unknown binding_type " + typeName);
         }

         return null;
      } else {
         switch (type) {
            case GLOBAL:
            case NONE:
               if (name == null) {
                  return null;
               } else {
                  UiExpression expression = null;
                  if (!name.startsWith("#") || !UiControl.isName(name, 1)) {
                     expression = this.nameExpression(name, scope);
                     if (!expression.valid() || plainText(name, expression)) {
                        this.invalidName(name, expression, override, where);
                        return null;
                     }

                     this.lenient(expression, "binding_name", where);
                  }

                  String target = override != null ? override : (expression == null ? name : null);
                  if (target == null) {
                     return null;
                  }

                  return new UiBinding(type, condition, name, expression, target, null, null, false);
               }
            case COLLECTION:
               if (name == null) {
                  return null;
               } else {
                  UiExpression expression = null;
                  if (!name.startsWith("#") || !UiControl.isName(name, 1)) {
                     expression = this.nameExpression(name, scope);
                     if (!expression.valid() || plainText(name, expression)) {
                        this.invalidName(name, expression, override, where);
                        return null;
                     }

                     this.lenient(expression, "binding_name", where);
                  }

                  String target = override != null ? override : (expression == null ? name : null);
                  return target == null ? null : new UiBinding(type, condition, name, expression, target, collection, null, false);
               }
            case COLLECTION_DETAILS:
               return new UiBinding(type, condition, name, null, null, collection, null, false);
            case VIEW:
               String source = text(fields.get("source_property_name"));
               String target = text(fields.get("target_property_name"));
               if (source != null && target != null) {
                  UiExpression expression = this.definitions.expression(source).bind(scope::get);
                  if (!expression.valid()) {
                     this.problems
                        .add(
                           "expression",
                           "view binding dropped on " + where + " (target " + target + "): source_property_name " + source + ": " + expression.error()
                        );
                     return null;
                  }

                  this.lenient(expression, "source_property_name", where);
                  UiValue sibling = fields.get("resolve_sibling_scope");
                  return new UiBinding(
                     type, condition, null, expression, target, null, text(fields.get("source_control_name")), sibling != null && sibling.truthy()
                  );
               }

               return null;
            default:
               return null;
         }
      }
   }

   private List<JsonObject> substituted(@Nullable JsonElement raw, UiScope scope) {
      if (raw == null) {
         return List.of();
      } else {
         JsonArray array = this.eval(raw, scope::get).array();
         if (array == null) {
            return List.of();
         } else {
            List<JsonObject> mappings = new ArrayList<>(array.size());

            for (JsonElement element : array) {
               if (element instanceof JsonObject object) {
                  JsonObject copy = new JsonObject();
                  boolean ignored = false;

                  for (Entry<String, JsonElement> entry : object.entrySet()) {
                     UiValue value = entry.getKey().equals("ignored")
                        ? UiValue.of(this.condition(entry.getValue(), scope::get))
                        : this.eval(entry.getValue(), scope::get);
                     if (entry.getKey().equals("ignored")) {
                        ignored = value.truthy();
                     }

                     copy.add(entry.getKey(), value.toJson());
                  }

                  if (!ignored) {
                     mappings.add(copy);
                  }
               }
            }

            return mappings;
         }
      }
   }

   @Nullable
   private UiControl.Factory factory(UiControl control, JsonObject merged, UiScope scope, Function<String, String> keyNamespaces, Set<String> entryVariables) {
      JsonObject spec = this.eval(merged.get("factory"), scope::get).object();
      if (control.type == UiType.FACTORY) {
         String name = spec != null && text(UiValue.of(spec.get("name"))) != null ? spec.get("name").getAsString() : control.name;
         JsonObject ids = this.eval(merged.get("control_ids"), scope::get).object();
         if (ids == null && spec != null && spec.get("control_ids") instanceof JsonObject inner) {
            ids = inner;
         }

         String controlName = string(control.props.get("control_name"));
         if (controlName == null && spec != null) {
            controlName = text(UiValue.of(spec.get("control_name")));
         }

         UiValue max = control.props.get("max_children_size");
         JsonElement passed = merged.has("factory_variables") ? merged.get("factory_variables") : (spec == null ? null : spec.get("factory_variables"));
         return new UiControl.Factory(
            name,
            this.idMap(ids, scope),
            controlName,
            max == null ? 0 : (int)max.asNumber(),
            false,
            keyNamespaces.apply(merged.has("control_ids") ? "control_ids" : "factory"),
            this.weakForChildren(entryVariables, passed, scope)
         );
      } else if (spec == null) {
         return null;
      } else {
         String namex = text(this.eval(spec.get("name"), scope::get));
         JsonObject idsx = this.eval(spec.get("control_ids"), scope::get).object();
         String controlName = text(this.eval(spec.get("control_name"), scope::get));
         UiValue max = this.eval(spec.get("max_children_size"), scope::get);
         return new UiControl.Factory(
            namex != null ? namex : control.name,
            this.idMap(idsx, scope),
            controlName,
            (int)max.asNumber(),
            control.collectionName != null || lengthBound(control),
            keyNamespaces.apply("factory"),
            this.weakForChildren(entryVariables, spec.get("factory_variables"), scope)
         );
      }
   }

   private Set<String> weakForChildren(Set<String> entryVariables, @Nullable JsonElement factoryVariables, UiScope scope) {
      if (entryVariables.isEmpty()) {
         return Set.of();
      } else {
         Set<String> weak = new HashSet<>(entryVariables);
         JsonArray names = factoryVariables == null
            ? null
            : (factoryVariables instanceof JsonArray array ? array : this.eval(factoryVariables, scope::get).array());
         if (names != null) {
            for (JsonElement entry : names) {
               if (entry instanceof JsonPrimitive primitive && primitive.isString()) {
                  weak.remove(primitive.getAsString());
               }
            }
         }

         return weak.isEmpty() ? Set.of() : Set.copyOf(weak);
      }
   }

   private static boolean lengthBound(UiControl control) {
      for (UiBinding binding : control.bindings) {
         if ("#collection_length".equals(binding.target)) {
            return true;
         }
      }

      return false;
   }

   private Map<String, String> idMap(@Nullable JsonObject ids, UiScope scope) {
      if (ids == null) {
         return Map.of();
      } else {
         Map<String, String> map = new LinkedHashMap<>();

         for (Entry<String, JsonElement> entry : ids.entrySet()) {
            String reference = text(this.eval(entry.getValue(), scope::get));
            if (reference != null) {
               map.put(entry.getKey(), reference);
            }
         }

         return map;
      }
   }

   private UiControl.Grid grid(UiControl control, Function<String, String> keyNamespaces) {
      String template = string(control.props.get("grid_item_template"));
      float[] dimensions = floats(control.props.get("grid_dimensions"));
      int columns = dimensions != null && dimensions.length >= 2 ? (int)dimensions[0] : 0;
      int rows = dimensions != null && dimensions.length >= 2 ? (int)dimensions[1] : 0;
      String binding = string(control.props.get("grid_dimension_binding"));
      UiValue maximum = control.props.get("maximum_grid_items");
      String rescaling = string(control.props.get("grid_rescaling_type"));
      if ("none".equals(rescaling)) {
         rescaling = null;
      }

      boolean vertical = "vertical".equals(string(control.props.get("grid_fill_direction")));
      return new UiControl.Grid(
         template,
         keyNamespaces.apply("grid_item_template"),
         columns,
         rows,
         binding,
         maximum != null && maximum.isNumber() ? (int)maximum.asNumber() : -1,
         rescaling,
         vertical
      );
   }

   private void animations(UiControl control, JsonObject merged, UiScope scope) {
      for (Entry<String, UiValue> entry : control.props.entrySet()) {
         String reference = entry.getValue().stringOrNull();
         if (reference != null && reference.startsWith("@") && ANIMATABLE.contains(entry.getKey())) {
            UiAnimation step = this.resolve(reference, control);
            if (step != null) {
               control.tracks.add(new UiAnimation.Track(control, entry.getKey(), step, this.instance.time()));
            }
         }
      }

      JsonElement anims = merged.get("anims");
      if (anims != null) {
         UiValue value = this.eval(anims, scope::get);
         List<String> references = new ArrayList<>();
         if (value.array() != null) {
            for (JsonElement element : value.array()) {
               String reference = UiModifications.string(element);
               if (reference != null) {
                  references.add(reference);
               }
            }
         } else if (value.stringOrNull() != null) {
            references.add(value.stringOrNull());
         }

         for (String reference : references) {
            UiAnimation step = this.resolve(reference, control);
            if (step != null) {
               control.tracks.add(new UiAnimation.Track(control, step.type().property(), step, this.instance.time()));
            }
         }
      }
   }

   @Override
   public UiAnimation resolve(String reference, UiControl control) {
      String ref = reference.startsWith("@") ? reference.substring(1) : reference;
      UiElementDef def = this.definitions.element(ref, control.defId.substring(0, Math.max(0, control.defId.indexOf(46))));
      if (def == null) {
         this.problems.add("animation", control + ": no animation " + reference);
         return null;
      } else {
         UiTemplate template = this.definitions.template(def, control.scope::get);
         if (template == null) {
            return null;
         } else {
            JsonObject props = template.props();
            this.buildingName = control;
            this.buildingJoin = " animation ";
            this.buildingDetail = reference;
            Function<String, UiValue> scope = this.cachedScope(props, control.scope)::get;
            UiAnimation.Type type = UiAnimation.Type.of(this.eval(props.get("anim_type"), scope).stringOrNull());
            if (type == null) {
               this.problems.add("animation", control + ": " + reference + " has no known anim_type");
               return null;
            } else {
               float[] initialUv = floats(this.eval(props.get("initial_uv"), scope));
               return new UiAnimation(
                  def.id(),
                  type,
                  this.eval(props.get("duration"), scope).asNumber(),
                  this.eval(props.get("from"), scope),
                  this.eval(props.get("to"), scope),
                  UiEasing.of(this.eval(props.get("easing"), scope).stringOrNull()),
                  text(this.eval(props.get("next"), scope)),
                  text(this.eval(props.get("destroy_at_end"), scope)),
                  text(this.eval(props.get("play_event"), scope)),
                  text(this.eval(props.get("end_event"), scope)),
                  text(this.eval(props.get("reset_event"), scope)),
                  this.eval(props.get("looping"), scope).truthy(),
                  this.eval(props.get("fps"), scope).asNumber(),
                  (int)this.eval(props.get("frame_count"), scope).asNumber(),
                  (float)this.eval(props.get("frame_step"), scope).asNumber(),
                  initialUv,
                  this.eval(props.get("reversible"), scope).truthy(),
                  this.eval(props.get("scale_from_starting_alpha"), scope).truthy()
               );
            }
         }
      }
   }

   UiValue eval(@Nullable JsonElement value, Function<String, UiValue> variables) {
      if (value == null) {
         return UiValue.NULL;
      } else if (value instanceof JsonPrimitive primitive && primitive.isString()) {
         String text = primitive.getAsString();
         if (text.length() > 1 && text.charAt(0) == '$' && UiControl.isName(text, 1)) {
            UiValue resolved = variables.apply(text);
            if (resolved == null) {
               this.problems.add("undefined-variable", text + " in " + this.building());
               return UiValue.NULL;
            } else {
               return resolved;
            }
         } else if (!text.isEmpty() && text.charAt(0) != '@' && (text.charAt(0) != '#' || !UiControl.isName(text, 1))) {
            if (text.indexOf(36) >= 0 && !hashOperand(text) && UiExpression.looksLikeExpression(text)) {
               UiExpression expression = this.definitions.expression(text);
               if (expression.valid()) {
                  this.lenient(expression, "value", "");
                  if (expression.bind(variables).usesHash()) {
                     return UiValue.of(text);
                  }

                  return expression.eval(name -> null, variables, this.problems);
               }
            }

            return UiValue.of(text);
         } else {
            return UiValue.of(text);
         }
      } else {
         return UiValue.of(value);
      }
   }

   static boolean hashOperand(String text) {
      boolean quoted = false;

      for (int i = 0; i < text.length(); i++) {
         char c = text.charAt(i);
         if (c == '\'') {
            quoted = !quoted;
         } else if (c == '#' && !quoted) {
            return true;
         }
      }

      return false;
   }

   boolean condition(JsonElement value, Function<String, UiValue> variables) {
      if (value instanceof JsonPrimitive primitive && primitive.isString()) {
         String text = primitive.getAsString().trim();
         if (text.length() > 1 && text.charAt(0) == '$' && UiControl.isName(text, 1)) {
            UiValue resolved = variables.apply(text);
            return resolved != null && resolved.truthy();
         } else {
            UiExpression expression = this.definitions.expression(text);
            if (expression.valid()) {
               this.lenient(expression, "condition", "");
               return expression.eval(name -> null, variables, this.problems).truthy();
            } else {
               this.problems.add("expression", "condition " + text + ": " + expression.error() + " (taken as false)");
               return false;
            }
         }
      } else {
         return UiValue.of(value).truthy();
      }
   }

   @Nullable
   static String text(@Nullable UiValue value) {
      if (value != null && !value.isNull()) {
         String text = value.asString();
         return text.isEmpty() ? null : text;
      } else {
         return null;
      }
   }

   @Nullable
   static String string(@Nullable UiValue value) {
      return value == null ? null : value.stringOrNull();
   }

   @Nullable
   static float[] floats(@Nullable UiValue value) {
      return value == null ? null : value.floats();
   }

   private record Merged(String namespace, JsonObject merged, Function<String, String> keyNamespaces) {
   }
}
