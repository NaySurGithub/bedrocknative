package net.bedrocknative.content.scan;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;
import net.bedrocknative.content.PackSource;
import net.bedrocknative.model.ModelJson;
import net.bedrocknative.ui.UiJson;
import org.jspecify.annotations.Nullable;

public final class PackIndex implements PackLookup {
   private static final List<String> TEXTURE_EXTENSIONS = List.of(".png", ".tga", ".jpg", ".jpeg");
   private static final List<String> SOUND_EXTENSIONS = List.of(".ogg", ".wav", ".fsb", ".mp3");
   private static final List<String> FONT_EXTENSIONS = List.of(".ttf", ".otf", ".png", ".fontdata");
   private static final Pattern UI_REF = Pattern.compile("@?[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)?");
   private static final Pattern UI_VARIABLE_VALUE = Pattern.compile("[a-z][a-z0-9_]*\\.[a-z][a-z0-9_]*");
   private static final Pattern LANG_CANDIDATE = Pattern.compile("[A-Za-z0-9_\\-]+(\\.[A-Za-z0-9_\\-]+)+");
   private final String name;
   private final PackSource pack;
   private final boolean contentsRead;
   private final Map<String, String> files = new LinkedHashMap<>();
   private final Map<PackIndex.Kind, Map<String, PackIndex.Def>> defs = new EnumMap<>(PackIndex.Kind.class);
   private final List<PackIndex.Ref> refs = new ArrayList<>();
   private final Map<String, String> uiNamespaces = new HashMap<>();
   private final Map<String, List<PackIndex.Def>> uiElements = new HashMap<>();
   private final List<String> unreadable = new ArrayList<>();

   private PackIndex(String name, PackSource pack, boolean contentsRead) {
      this.name = name;
      this.pack = pack;
      this.contentsRead = contentsRead;
   }

   public static PackIndex of(String name, PackSource pack, boolean readContents, Function<String, @Nullable String> lowerUiNamespaces) {
      PackIndex index = new PackIndex(name, pack, readContents);

      for (String path : pack.list("")) {
         index.files.putIfAbsent(path.toLowerCase(Locale.ROOT), path);
      }

      for (String lower : List.copyOf(index.files.keySet())) {
         if (lower.startsWith("ui/") && lower.endsWith(".json") && !lower.equals("ui/_ui_defs.json") && !lower.equals("ui/_global_variables.json")) {
            index.refs.add(new PackIndex.Ref(PackIndex.Kind.UI_FILE_PATCH, lower, lower));
         }

         if (readContents) {
            index.read(lower, lowerUiNamespaces);
         }
      }

      index.defs.forEach((kind, table) -> {
         if (kind != PackIndex.Kind.UI_ELEMENT && kind != PackIndex.Kind.LANG_KEY) {
            table.values().forEach(def -> index.refs.addAll(index.refsOf(def)));
         }
      });
      return index;
   }

   public String name() {
      return this.name;
   }

   public PackSource pack() {
      return this.pack;
   }

   public boolean contentsRead() {
      return this.contentsRead;
   }

   public Map<String, String> files() {
      return this.files;
   }

   public List<PackIndex.Ref> refs() {
      return this.refs;
   }

   public List<String> unreadable() {
      return this.unreadable;
   }

   public Map<String, PackIndex.Def> defs(PackIndex.Kind kind) {
      return this.defs.getOrDefault(kind, Map.of());
   }

   @Override
   public @Nullable String uiNamespace(String file) {
      return this.uiNamespaces.get(file.toLowerCase(Locale.ROOT));
   }

   public List<PackIndex.Def> uiElements(String file) {
      return this.uiElements.getOrDefault(file.toLowerCase(Locale.ROOT), List.of());
   }

   public @Nullable String file(String path) {
      return this.files.get(clean(path));
   }

   public Optional<byte[]> read(String path) throws IOException {
      String own = this.file(path);
      return own == null ? Optional.empty() : this.pack.read(own);
   }

   @Override
   public PackLookup.@Nullable Hit find(PackIndex.Kind kind, String key) {
      String path = switch (kind) {
         case TEXTURE -> this.withExtension(key, TEXTURE_EXTENSIONS);
         case SOUND_FILE -> this.withExtension(key, SOUND_EXTENSIONS);
         case UI_FILE, UI_FILE_PATCH -> this.file(key);
         case FILE -> this.file(key) != null ? this.file(key) : (key.startsWith("font/") ? this.withExtension(key, FONT_EXTENSIONS) : null);
         default -> null;
      };
      if (path != null) {
         return new PackLookup.Hit(this, path, null);
      }

      PackIndex.Def def = this.defs(kind == PackIndex.Kind.UI_VARIABLE_ELEMENT ? PackIndex.Kind.UI_ELEMENT : kind).get(key);
      return def != null ? new PackLookup.Hit(this, this.file(def.file()) != null ? this.file(def.file()) : def.file(), def) : null;
   }

   private @Nullable String withExtension(String key, List<String> extensions) {
      String lower = clean(key);

      for (String extension : extensions) {
         if (lower.endsWith(extension)) {
            return this.files.get(lower);
         }
      }

      for (String extension : extensions) {
         String found = this.files.get(lower + extension);
         if (found != null) {
            return found;
         }
      }

      return null;
   }

   public List<PackIndex.Ref> refsOf(PackIndex.Def def) {
      List<PackIndex.Ref> found = new ArrayList<>();
      Consumer<PackIndex.Ref> sink = found::add;
      JsonElement body = def.body();
      String file = def.file();
      switch (def.kind()) {
         case TERRAIN_KEY:
         case ITEM_TEXTURE_KEY:
            textureEntry(body, file, sink);
            break;
         case GEOMETRY:
         case MATERIAL:
            if (body instanceof JsonPrimitive parent) {
               for (String name : parent.getAsString().split(":")) {
                  add(sink, def.kind(), name, file);
               }
            }
         case ANIMATION:
         case RENDER_CONTROLLER:
         case SOUND_FILE:
         case FOG:
         case UI_VARIABLE_ELEMENT:
         case UI_FILE:
         case UI_FILE_PATCH:
         default:
            break;
         case PARTICLE:
            particle(body, file, sink);
            break;
         case SOUND_EVENT:
            soundDefinition(body, file, sink);
            break;
         case BLOCK_SOUND:
            walkSounds(body, file, sink);
            break;
         case UI_ELEMENT:
            uiTree(def.namespace(), body, file, sink);
            break;
         case FONT:
            if (body instanceof JsonPrimitive fontFile) {
               add(sink, PackIndex.Kind.FILE, fontFile.getAsString(), file);
            } else if (body instanceof JsonArray) {
               for (JsonElement font : (JsonArray)body) {
                  add(sink, PackIndex.Kind.FONT, font.getAsString(), file);
               }
            }
      }

      return found;
   }

   private void read(String lower, Function<String, @Nullable String> lowerUiNamespaces) {
      String folder = lower.indexOf(47) > 0 ? lower.substring(0, lower.indexOf(47)) : "";
      boolean json = lower.endsWith(".json");
      if (lower.endsWith(".lang") && folder.equals("texts")) {
         this.readLang(lower);
      } else if (json || lower.endsWith(".material")) {
         if (!lower.equals("manifest.json")
            && !lower.equals("contents.json")
            && !lower.endsWith("/contents.json")
            && !lower.endsWith("texture_list.json")
            && !lower.startsWith("subpacks/")) {
            JsonElement root = this.parse(lower);
            if (root != null) {
               Consumer<PackIndex.Ref> sink = this.refs::add;
               switch (folder) {
                  case "entity":
                     entity(object(root, "minecraft:client_entity"), lower, sink);
                     break;
                  case "attachables":
                     entity(object(root, "minecraft:attachable"), lower, sink);
                     break;
                  case "models":
                     this.geometries(root, lower);
                     break;
                  case "animations":
                     this.define(PackIndex.Kind.ANIMATION, object(root, "animations"), lower);
                     break;
                  case "animation_controllers":
                     this.define(PackIndex.Kind.ANIMATION, object(root, "animation_controllers"), lower);
                     break;
                  case "render_controllers":
                     this.define(PackIndex.Kind.RENDER_CONTROLLER, object(root, "render_controllers"), lower);
                     break;
                  case "materials":
                     this.materials(root, lower);
                     break;
                  case "particles":
                     this.particleFile(root, lower, sink);
                     break;
                  case "fogs":
                     this.fogFile(root, lower);
                     break;
                  case "ui":
                     this.uiFile(root, lower, lowerUiNamespaces, sink);
                     break;
                  case "font":
                     this.fontFile(root, lower);
                     break;
                  case "items":
                     item(root, lower, sink);
                     break;
                  case "blocks":
                     behaviourBlock(root, lower, sink);
                     break;
                  case "sounds":
                     if (lower.equals("sounds/sound_definitions.json")) {
                        this.soundDefinitions(root, lower);
                     }
                     break;
                  case "textures":
                     switch (lower) {
                        case "textures/terrain_texture.json":
                           this.textureKeys(PackIndex.Kind.TERRAIN_KEY, root, lower);
                           return;
                        case "textures/item_texture.json":
                           this.textureKeys(PackIndex.Kind.ITEM_TEXTURE_KEY, root, lower);
                           return;
                        case "textures/flipbook_textures.json":
                           flipbooks(root, lower, sink);
                           return;
                        default:
                           walkTextures(root, lower, sink);
                           return;
                     }
                  default:
                     switch (lower) {
                        case "blocks.json":
                           blocksJson(root, lower, sink);
                           break;
                        case "sounds.json":
                           this.soundsJson(root, lower, sink);
                           break;
                        default:
                           walkTextures(root, lower, sink);
                           walkKey(root, "fog_identifier", value -> add(sink, PackIndex.Kind.FOG, value, lower));
                     }
               }
            }
         }
      }
   }

   private @Nullable JsonElement parse(String lower) {
      byte[] data;
      try {
         data = this.read(lower).orElse(null);
      } catch (IOException e) {
         this.unreadable.add(lower + ": " + e.getMessage());
         return null;
      }

      if (data != null && data.length != 0) {
         try {
            return UiJson.parse(data);
         } catch (UiJson.Error e) {
            JsonObject lenient = ModelJson.parse(data, this.name + " " + lower);
            if (lenient == null) {
               this.unreadable.add(lower + ": " + e.getMessage());
            }

            return lenient;
         }
      } else {
         return null;
      }
   }

   private void readLang(String lower) {
      try {
         byte[] data = this.read(lower).orElse(null);
         if (data == null) {
            return;
         }

         for (String line : new String(data, StandardCharsets.UTF_8).split("\n")) {
            int equals = line.indexOf(61);
            String trimmed = line.strip();
            if (equals > 0 && !trimmed.startsWith("#")) {
               String key = line.substring(0, equals).strip().toLowerCase(Locale.ROOT);
               if (key.startsWith("\ufeff")) {
                  key = key.substring(1);
               }

               Map<String, PackIndex.Def> keys = this.defs.computeIfAbsent(PackIndex.Kind.LANG_KEY, k -> new HashMap<>());
               if (lower.equals("texts/en_us.lang") || !keys.containsKey(key)) {
                  keys.put(key, new PackIndex.Def(PackIndex.Kind.LANG_KEY, key, lower, null, ""));
               }
            }
         }
      } catch (IOException e) {
         this.unreadable.add(lower + ": " + e.getMessage());
      }
   }

   private static void entity(@Nullable JsonObject root, String file, Consumer<PackIndex.Ref> sink) {
      JsonObject description = object(root, "description");
      if (description != null) {
         values(object(description, "materials"), valuex -> add(sink, PackIndex.Kind.MATERIAL, valuex, file));
         values(object(description, "textures"), valuex -> add(sink, PackIndex.Kind.TEXTURE, valuex, file));
         values(object(description, "geometry"), valuex -> add(sink, PackIndex.Kind.GEOMETRY, valuex, file));
         values(object(description, "animations"), valuex -> add(sink, PackIndex.Kind.ANIMATION, valuex, file));
         values(object(description, "particle_effects"), valuex -> add(sink, PackIndex.Kind.PARTICLE, valuex, file));
         values(object(description, "particle_emitters"), valuex -> add(sink, PackIndex.Kind.PARTICLE, valuex, file));
         JsonElement var5 = description.get("animation_controllers");
         if (var5 instanceof JsonArray) {
            for (JsonElement entry : (JsonArray)var5) {
               if (entry instanceof JsonObject map) {
                  values(map, valuex -> add(sink, PackIndex.Kind.ANIMATION, valuex, file));
               }
            }
         }

         var5 = description.get("render_controllers");
         if (var5 instanceof JsonArray) {
            for (JsonElement entry : (JsonArray)var5) {
               if (entry instanceof JsonPrimitive id) {
                  add(sink, PackIndex.Kind.RENDER_CONTROLLER, id.getAsString(), file);
               } else if (entry instanceof JsonObject conditional) {
                  conditional.keySet().forEach(id -> add(sink, PackIndex.Kind.RENDER_CONTROLLER, id, file));
               }
            }
         }

         if (description.get("sound_effects") instanceof JsonObject sounds) {
            for (JsonElement value : sounds.asMap().values()) {
               if (value instanceof JsonPrimitive event) {
                  add(sink, PackIndex.Kind.SOUND_EVENT, event.getAsString(), file);
               } else if (value instanceof JsonObject effect && string(effect, "effect") != null) {
                  add(sink, PackIndex.Kind.SOUND_EVENT, string(effect, "effect"), file);
               }
            }
         }

         JsonObject egg = object(description, "spawn_egg");
         if (string(egg, "texture") != null) {
            add(sink, PackIndex.Kind.ITEM_TEXTURE_KEY, string(egg, "texture"), file);
         }
      }
   }

   private void geometries(JsonElement root, String file) {
      if (root instanceof JsonObject object) {
         JsonElement key = object.get("minecraft:geometry");
         if (key instanceof JsonArray) {
            for (JsonElement entry : (JsonArray)key) {
               String identifier = string(object(entry instanceof JsonObject o ? o : null, "description"), "identifier");
               if (identifier != null) {
                  this.defineGeometry(identifier, entry, file);
               }
            }
         }

         for (String keyx : object.keySet()) {
            if (keyx.toLowerCase(Locale.ROOT).startsWith("geometry.")) {
               this.defineGeometry(keyx, object.get(keyx), file);
            }
         }
      }
   }

   private void defineGeometry(String identifier, JsonElement body, String file) {
      int colon = identifier.indexOf(58);
      String own = colon > 0 && !identifier.startsWith("minecraft:") ? identifier.substring(0, colon) : identifier;
      String parent = colon > 0 && !identifier.startsWith("minecraft:") ? identifier.substring(colon + 1) : null;
      this.put(new PackIndex.Def(PackIndex.Kind.GEOMETRY, own.toLowerCase(Locale.ROOT), file, parent != null ? new JsonPrimitive(parent) : null, ""));
   }

   private void define(PackIndex.Kind kind, @Nullable JsonObject table, String file) {
      if (table != null) {
         table.asMap().forEach((id, body) -> this.put(new PackIndex.Def(kind, id.toLowerCase(Locale.ROOT), file, body, "")));
      }
   }

   private void materials(JsonElement root, String file) {
      JsonObject table = object(root instanceof JsonObject o ? o : null, "materials");
      if (table != null) {
         for (String key : table.keySet()) {
            if (!key.equals("version")) {
               int colon = key.indexOf(58);
               String own = colon > 0 ? key.substring(0, colon) : key;
               this.put(
                  new PackIndex.Def(
                     PackIndex.Kind.MATERIAL, own.toLowerCase(Locale.ROOT), file, colon > 0 ? new JsonPrimitive(key.substring(colon + 1)) : null, ""
                  )
               );
            }
         }
      }
   }

   private void particleFile(JsonElement root, String file, Consumer<PackIndex.Ref> sink) {
      JsonObject effect = object(root instanceof JsonObject o ? o : null, "particle_effect");
      String identifier = string(object(effect, "description"), "identifier");
      if (identifier != null) {
         this.put(new PackIndex.Def(PackIndex.Kind.PARTICLE, identifier.toLowerCase(Locale.ROOT), file, effect, ""));
      }

      particle(effect, file, sink);
   }

   private static void particle(@Nullable JsonElement body, String file, Consumer<PackIndex.Ref> sink) {
      JsonObject effect = body instanceof JsonObject o ? o : null;
      JsonObject render = object(object(effect, "description"), "basic_render_parameters");
      if (string(render, "texture") != null) {
         add(sink, PackIndex.Kind.TEXTURE, string(render, "texture"), file);
      }

      if (string(render, "material") != null) {
         add(sink, PackIndex.Kind.MATERIAL, string(render, "material"), file);
      }

      JsonObject events = object(effect, "events");
      if (events != null) {
         walk(events, (key, value) -> {
            if (key.equals("particle_effect") && value instanceof JsonObject reference && string(reference, "effect") != null) {
               add(sink, PackIndex.Kind.PARTICLE, string(reference, "effect"), file);
            } else if (key.equals("sound_effect") && value instanceof JsonObject reference && string(reference, "event_name") != null) {
               add(sink, PackIndex.Kind.SOUND_EVENT, string(reference, "event_name"), file);
            }
         });
      }
   }

   private void fogFile(JsonElement root, String file) {
      String identifier = string(object(object(root instanceof JsonObject o ? o : null, "minecraft:fog_settings"), "description"), "identifier");
      if (identifier != null) {
         this.put(new PackIndex.Def(PackIndex.Kind.FOG, identifier.toLowerCase(Locale.ROOT), file, null, ""));
      }
   }

   private void fontFile(JsonElement root, String file) {
      if (file.equals("font/font_metadata.json") && root instanceof JsonObject object) {
         JsonElement var5 = object.get("fonts");
         if (var5 instanceof JsonArray) {
            for (JsonElement entry : (JsonArray)var5) {
               String name = string(entry instanceof JsonObject o ? o : null, "font_name");
               String fontFile = string(entry instanceof JsonObject o ? o : null, "font_file");
               if (name != null && fontFile != null) {
                  this.put(new PackIndex.Def(PackIndex.Kind.FONT, name.toLowerCase(Locale.ROOT), file, new JsonPrimitive(fontFile), ""));
               }
            }
         }

         if (object.get("font_aliases") instanceof JsonArray aliases) {
            for (JsonElement entry : aliases) {
               JsonObject alias = entry instanceof JsonObject o ? o : null;
               String name = string(alias, "alias");
               if (name != null) {
                  JsonArray targets = new JsonArray();
                  JsonElement var11 = alias.get("fonts");
                  if (var11 instanceof JsonArray) {
                     for (JsonElement reference : (JsonArray)var11) {
                        String target = string(reference instanceof JsonObject o ? o : null, "font_reference");
                        if (target != null) {
                           targets.add(target);
                        }
                     }
                  }

                  this.put(new PackIndex.Def(PackIndex.Kind.FONT, name.toLowerCase(Locale.ROOT), file, targets, ""));
               }
            }
         }
      }
   }

   private void soundDefinitions(JsonElement root, String file) {
      if (root instanceof JsonObject object) {
         JsonObject table = object.get("sound_definitions") instanceof JsonObject wrapped ? wrapped : object;
         table.asMap().forEach((event, body) -> {
            if (!event.equals("format_version")) {
               this.put(new PackIndex.Def(PackIndex.Kind.SOUND_EVENT, event.toLowerCase(Locale.ROOT), file, body, ""));
            }
         });
      }
   }

   private static void soundDefinition(@Nullable JsonElement body, String file, Consumer<PackIndex.Ref> sink) {
      if (body instanceof JsonObject definition) {
         JsonElement var5 = definition.get("sounds");
         if (var5 instanceof JsonArray) {
            for (JsonElement sound : (JsonArray)var5) {
               if (sound instanceof JsonPrimitive path) {
                  add(sink, PackIndex.Kind.SOUND_FILE, path.getAsString(), file);
               } else if (sound instanceof JsonObject entry && string(entry, "name") != null) {
                  add(sink, PackIndex.Kind.SOUND_FILE, string(entry, "name"), file);
               }
            }
         }
      }
   }

   private void soundsJson(JsonElement root, String file, Consumer<PackIndex.Ref> sink) {
      walkSounds(root, file, sink);
      if (root instanceof JsonObject object) {
         this.defineGroups(object(object, "block_sounds"), file);
         this.defineGroups(object(object(object, "interactive_sounds"), "block_sounds"), file);
      }
   }

   private void defineGroups(@Nullable JsonObject groups, String file) {
      if (groups != null) {
         groups.asMap().forEach((group, body) -> this.put(new PackIndex.Def(PackIndex.Kind.BLOCK_SOUND, group.toLowerCase(Locale.ROOT), file, body, "")));
      }
   }

   private static void walkSounds(@Nullable JsonElement root, String file, Consumer<PackIndex.Ref> sink) {
      if (root != null) {
         walk(root, (key, value) -> {
            if (key.equals("sound") && value instanceof JsonPrimitive event && !event.getAsString().isBlank()) {
               add(sink, PackIndex.Kind.SOUND_EVENT, event.getAsString(), file);
            }
         });
         walk(root, (key, value) -> {
            if (key.equals("events") && value instanceof JsonObject events) {
               events.asMap().values().forEach(event -> {
                  if (event instanceof JsonPrimitive name && !name.getAsString().isBlank()) {
                     add(sink, PackIndex.Kind.SOUND_EVENT, name.getAsString(), file);
                  }
               });
            }
         });
      }
   }

   private void textureKeys(PackIndex.Kind kind, JsonElement root, String file) {
      JsonObject data = object(root instanceof JsonObject o ? o : null, "texture_data");
      if (data != null) {
         data.asMap().forEach((key, body) -> this.put(new PackIndex.Def(kind, key.toLowerCase(Locale.ROOT), file, body, "")));
      }
   }

   private static void textureEntry(@Nullable JsonElement body, String file, Consumer<PackIndex.Ref> sink) {
      JsonElement textures = body instanceof JsonObject entry ? entry.get("textures") : null;
      List<JsonElement> list = new ArrayList<>();
      if (textures instanceof JsonArray array) {
         array.forEach(list::add);
      } else if (textures != null) {
         list.add(textures);
      }

      for (JsonElement texture : list) {
         if (texture instanceof JsonPrimitive path) {
            add(sink, PackIndex.Kind.TEXTURE, path.getAsString(), file);
         } else if (texture instanceof JsonObject object) {
            if (string(object, "path") != null) {
               add(sink, PackIndex.Kind.TEXTURE, string(object, "path"), file);
            }

            JsonElement var10 = object.get("variations");
            if (var10 instanceof JsonArray) {
               for (JsonElement variation : (JsonArray)var10) {
                  String path = string(variation instanceof JsonObject o ? o : null, "path");
                  if (path != null) {
                     add(sink, PackIndex.Kind.TEXTURE, path, file);
                  }
               }
            }
         }
      }
   }

   private static void flipbooks(JsonElement root, String file, Consumer<PackIndex.Ref> sink) {
      if (root instanceof JsonArray) {
         for (JsonElement entry : (JsonArray)root) {
            JsonObject flipbook = entry instanceof JsonObject o ? o : null;
            if (string(flipbook, "flipbook_texture") != null) {
               add(sink, PackIndex.Kind.TEXTURE, string(flipbook, "flipbook_texture"), file);
            }

            if (string(flipbook, "atlas_tile") != null) {
               add(sink, PackIndex.Kind.TERRAIN_KEY, string(flipbook, "atlas_tile"), file);
            }
         }
      }
   }

   private static void blocksJson(JsonElement root, String file, Consumer<PackIndex.Ref> sink) {
      if (root instanceof JsonObject object) {
         for (Entry<String, JsonElement> block : object.entrySet()) {
            if (block.getValue() instanceof JsonObject entry) {
               for (String key : List.of("textures", "carried_textures")) {
                  JsonElement textures = entry.get(key);
                  if (textures instanceof JsonPrimitive name) {
                     add(sink, PackIndex.Kind.TERRAIN_KEY, name.getAsString(), file);
                  } else if (textures instanceof JsonObject faces) {
                     values(faces, namex -> add(sink, PackIndex.Kind.TERRAIN_KEY, namex, file));
                  }
               }

               if (string(entry, "sound") != null) {
                  add(sink, PackIndex.Kind.BLOCK_SOUND, string(entry, "sound"), file);
               }
            }
         }
      }
   }

   private static void item(JsonElement root, String file, Consumer<PackIndex.Ref> sink) {
      JsonObject components = object(object(root instanceof JsonObject o ? o : null, "minecraft:item"), "components");
      JsonElement icon = components != null ? components.get("minecraft:icon") : null;
      if (icon instanceof JsonPrimitive key) {
         add(sink, PackIndex.Kind.ITEM_TEXTURE_KEY, key.getAsString(), file);
      } else if (icon instanceof JsonObject object) {
         if (string(object, "texture") != null) {
            add(sink, PackIndex.Kind.ITEM_TEXTURE_KEY, string(object, "texture"), file);
         }

         values(object(object, "textures"), key -> add(sink, PackIndex.Kind.ITEM_TEXTURE_KEY, key, file));
      }

      String name = string(object(components, "minecraft:display_name"), "value");
      if (name != null && LANG_CANDIDATE.matcher(name).matches()) {
         add(sink, PackIndex.Kind.LANG_KEY, name, file);
      }
   }

   private static void behaviourBlock(JsonElement root, String file, Consumer<PackIndex.Ref> sink) {
      JsonObject block = object(root instanceof JsonObject o ? o : null, "minecraft:block");
      if (block != null) {
         List<JsonObject> components = new ArrayList<>();
         if (object(block, "components") != null) {
            components.add(object(block, "components"));
         }

         JsonElement component = block.get("permutations");
         if (component instanceof JsonArray) {
            for (JsonElement permutation : (JsonArray)component) {
               JsonObject found = object(permutation instanceof JsonObject o ? o : null, "components");
               if (found != null) {
                  components.add(found);
               }
            }
         }

         for (JsonObject componentx : components) {
            JsonObject instances = object(componentx, "minecraft:material_instances");
            if (instances != null) {
               for (JsonElement instance : instances.asMap().values()) {
                  String texture = string(instance instanceof JsonObject o ? o : null, "texture");
                  if (texture != null) {
                     add(sink, PackIndex.Kind.TERRAIN_KEY, texture, file);
                  }
               }
            }

            JsonElement geometry = componentx.get("minecraft:geometry");
            if (geometry instanceof JsonPrimitive id) {
               add(sink, PackIndex.Kind.GEOMETRY, id.getAsString(), file);
            } else if (string(geometry instanceof JsonObject o ? o : null, "identifier") != null) {
               add(sink, PackIndex.Kind.GEOMETRY, string((JsonObject)geometry, "identifier"), file);
            }
         }
      }
   }

   private void uiFile(JsonElement root, String file, Function<String, @Nullable String> lowerUiNamespaces, Consumer<PackIndex.Ref> sink) {
      if (root instanceof JsonObject object) {
         if (file.equals("ui/_ui_defs.json")) {
            JsonElement var17 = object.get("ui_defs");
            if (var17 instanceof JsonArray) {
               for (JsonElement entry : (JsonArray)var17) {
                  if (entry instanceof JsonPrimitive path) {
                     add(sink, PackIndex.Kind.UI_FILE, path.getAsString(), file);
                  }
               }
            }
         } else if (file.equals("ui/_global_variables.json")) {
            uiTree("", object, file, sink);
         } else {
            String namespace = string(object, "namespace");
            if (namespace == null || namespace.isBlank()) {
               namespace = lowerUiNamespaces.apply(file);
            }

            if (namespace == null) {
               namespace = "";
            }

            namespace = namespace.toLowerCase(Locale.ROOT);
            this.uiNamespaces.put(file, namespace);
            List<PackIndex.Def> elements = this.uiElements.computeIfAbsent(file, keyx -> new ArrayList<>());

            for (Entry<String, JsonElement> entry : object.entrySet()) {
               String key = entry.getKey();
               if (!key.equals("namespace")) {
                  int at = key.indexOf(64);
                  String own = at >= 0 ? key.substring(0, at) : key;
                  JsonObject body = new JsonObject();
                  body.add(key, entry.getValue());
                  if (own.indexOf(47) < 0) {
                     PackIndex.Def def = new PackIndex.Def(PackIndex.Kind.UI_ELEMENT, namespace + "." + own.toLowerCase(Locale.ROOT), file, body, namespace);
                     this.put(def);
                     elements.add(def);
                  }

                  uiTree(namespace, body, file, sink);
               }
            }
         }
      }
   }

   private static void uiTree(String namespace, @Nullable JsonElement body, String file, Consumer<PackIndex.Ref> sink) {
      if (body != null) {
         walk(body, (key, value) -> {
            int at = key.indexOf(64);
            if (at >= 0) {
               uiRef(namespace, key.substring(at + 1), file, sink);
            }

            if (value instanceof JsonPrimitive primitive && primitive.isString()) {
               String text = primitive.getAsString();
               if (key.startsWith("$") && UI_VARIABLE_VALUE.matcher(text).matches()) {
                  add(sink, PackIndex.Kind.UI_VARIABLE_ELEMENT, text, file);
               }

               switch (key) {
                  case "sound_name":
                     add(sink, PackIndex.Kind.SOUND_EVENT, text, file);
                     break;
                  case "font_type":
                     add(sink, PackIndex.Kind.FONT, text, file);
                     break;
                  case "text":
                     if (LANG_CANDIDATE.matcher(text).matches()) {
                        add(sink, PackIndex.Kind.LANG_KEY, text, file);
                     }
               }
            }
         });
         strings(body, text -> {
            if (text.startsWith("@") && UI_REF.matcher(text).matches()) {
               uiRef(namespace, text.substring(1), file, sink);
            } else if (text.toLowerCase(Locale.ROOT).startsWith("textures/")) {
               add(sink, PackIndex.Kind.TEXTURE, text, file);
            }
         });
      }
   }

   private static void uiRef(String namespace, String reference, String file, Consumer<PackIndex.Ref> sink) {
      String ref = reference.startsWith("@") ? reference.substring(1) : reference;
      if (!ref.isEmpty() && UI_REF.matcher(ref).matches()) {
         add(sink, PackIndex.Kind.UI_ELEMENT, ref.indexOf(46) > 0 ? ref : namespace + "." + ref, file);
      }
   }

   private static void walkTextures(JsonElement root, String file, Consumer<PackIndex.Ref> sink) {
      strings(root, text -> {
         if (text.toLowerCase(Locale.ROOT).startsWith("textures/")) {
            add(sink, PackIndex.Kind.TEXTURE, text, file);
         }
      });
   }

   private static void walk(JsonElement element, PackIndex.Visitor visitor) {
      if (element instanceof JsonObject object) {
         for (Entry<String, JsonElement> entry : object.entrySet()) {
            visitor.visit(entry.getKey(), entry.getValue());
            walk(entry.getValue(), visitor);
         }
      } else if (element instanceof JsonArray) {
         for (JsonElement child : (JsonArray)element) {
            walk(child, visitor);
         }
      }
   }

   private static void walkKey(JsonElement root, String key, Consumer<String> action) {
      walk(root, (name, value) -> {
         if (name.equals(key) && value instanceof JsonPrimitive primitive) {
            action.accept(primitive.getAsString());
         }
      });
   }

   private static void strings(JsonElement element, Consumer<String> action) {
      if (element instanceof JsonPrimitive primitive && primitive.isString()) {
         action.accept(primitive.getAsString());
      } else if (element instanceof JsonObject object) {
         object.asMap().values().forEach(child -> strings(child, action));
      } else if (element instanceof JsonArray array) {
         array.forEach(child -> strings(child, action));
      }
   }

   private static void values(@Nullable JsonObject map, Consumer<String> action) {
      if (map != null) {
         for (JsonElement value : map.asMap().values()) {
            if (value instanceof JsonPrimitive primitive && primitive.isString()) {
               action.accept(primitive.getAsString());
            }
         }
      }
   }

   private static @Nullable JsonObject object(@Nullable JsonElement parent, String key) {
      return parent instanceof JsonObject object && object.get(key) instanceof JsonObject child ? child : null;
   }

   private static @Nullable String string(@Nullable JsonObject parent, String key) {
      return parent != null && parent.get(key) instanceof JsonPrimitive primitive ? primitive.getAsString() : null;
   }

   private static void add(Consumer<PackIndex.Ref> sink, PackIndex.Kind kind, @Nullable String key, String file) {
      if (key != null) {
         String clean = key.strip();
         if (!clean.isEmpty() && clean.indexOf(36) < 0 && !clean.startsWith("#") && clean.indexOf(32) < 0 && clean.indexOf(63) < 0) {
            sink.accept(
               new PackIndex.Ref(
                  kind,
                  kind != PackIndex.Kind.TEXTURE && kind != PackIndex.Kind.SOUND_FILE && kind != PackIndex.Kind.FILE && kind != PackIndex.Kind.UI_FILE
                     ? clean.toLowerCase(Locale.ROOT)
                     : clean(clean),
                  file
               )
            );
         }
      }
   }

   private void put(PackIndex.Def def) {
      this.defs.computeIfAbsent(def.kind(), key -> new HashMap<>()).putIfAbsent(def.key(), def);
   }

   static String clean(String path) {
      String lower = path.strip().replace('\\', '/').toLowerCase(Locale.ROOT);

      while (lower.startsWith("./") || lower.startsWith("/")) {
         lower = lower.substring(lower.startsWith("./") ? 2 : 1);
      }

      return lower;
   }

   @Override
   public String toString() {
      return this.name;
   }

   public record Def(PackIndex.Kind kind, String key, String file, @Nullable JsonElement body, String namespace) {
   }

   public enum Kind {
      TEXTURE,
      TERRAIN_KEY,
      ITEM_TEXTURE_KEY,
      GEOMETRY,
      ANIMATION,
      RENDER_CONTROLLER,
      MATERIAL,
      PARTICLE,
      SOUND_EVENT,
      SOUND_FILE,
      BLOCK_SOUND,
      FOG,
      UI_ELEMENT,
      UI_VARIABLE_ELEMENT,
      UI_FILE,
      UI_FILE_PATCH,
      FONT,
      FILE,
      LANG_KEY;
   }

   public record Ref(PackIndex.Kind kind, String key, String file) {
   }

   private interface Visitor {
      void visit(String var1, JsonElement var2);
   }
}
