package net.bedrocknative.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import org.jspecify.annotations.Nullable;

public final class PackItemFiles {
   public static final String FOLDER = "items";
   public static final int MAX_FILE_BYTES = 262144;
   private final Map<String, PackItemFiles.ItemFile> byIdentifier;
   private final int files;
   private final int unreadable;

   private PackItemFiles(Map<String, PackItemFiles.ItemFile> byIdentifier, int files, int unreadable) {
      this.byIdentifier = byIdentifier;
      this.files = files;
      this.unreadable = unreadable;
   }

   public static PackItemFiles read(PackStack stack) {
      return read(stack.packs());
   }

   public static PackItemFiles read(List<? extends PackSource> topFirst) {
      Map<String, PackItemFiles.ItemFile> found = new HashMap<>();
      int files = 0;
      int unreadable = 0;

      for (PackSource pack : topFirst) {
         Map<String, PackItemFiles.ItemFile> local = new HashMap<>();

         List<String> paths;
         try {
            paths = pack.list("items");
         } catch (RuntimeException var14) {
            unreadable++;
            continue;
         }

         for (String path : paths) {
            if (path.toLowerCase(Locale.ROOT).endsWith(".json")) {
               files++;

               try {
                  Optional<byte[]> data = pack.read(path);
                  if (!data.isEmpty()) {
                     JsonObject root = ((byte[])data.get()).length > 262144 ? null : parseJson(data.get());
                     if (root == null) {
                        unreadable++;
                     } else {
                        PackItemFiles.Definition definition = definition(root);
                        if (definition != null) {
                           local.put(definition.identifier(), definition.file());
                        }
                     }
                  }
               } catch (RuntimeException | IOException var13) {
                  unreadable++;
               }
            }
         }

         local.forEach(found::putIfAbsent);
      }

      return new PackItemFiles(Map.copyOf(found), files, unreadable);
   }

   @Nullable
   public PackItemFiles.Icon icon(String identifier) {
      PackItemFiles.ItemFile file = this.item(identifier);
      return file == null ? null : file.icon();
   }

   @Nullable
   public PackItemFiles.ItemFile item(String identifier) {
      return this.byIdentifier.get(identifier.toLowerCase(Locale.ROOT));
   }

   public int size() {
      return (int)this.byIdentifier.values().stream().filter(file -> file.icon() != null).count();
   }

   public Map<String, PackItemFiles.ItemFile> items() {
      return this.byIdentifier;
   }

   public int files() {
      return this.files;
   }

   public int unreadable() {
      return this.unreadable;
   }

   @Nullable
   private static PackItemFiles.Definition definition(JsonObject root) {
      JsonObject item = object(root, "minecraft:item");
      String identifier = string(object(item, "description"), "identifier");
      JsonObject components = object(item, "components");
      if (identifier != null && components != null) {
         PackItemFiles.ItemFile file = new PackItemFiles.ItemFile(
            icon(components.get("minecraft:icon")),
            text(value(components.get("minecraft:hover_text_color"))),
            handEquipped(components),
            integer(value(components.get("minecraft:max_stack_size"))),
            glint(components)
         );
         return file.empty() ? null : new PackItemFiles.Definition(identifier.toLowerCase(Locale.ROOT), file);
      } else {
         return null;
      }
   }

   @Nullable
   private static PackItemFiles.Icon icon(@Nullable JsonElement icon) {
      if (icon == null) {
         return null;
      } else {
         int frame = 0;
         String name;
         if (icon instanceof JsonObject map) {
            JsonElement textures = map.get("textures");
            name = textures instanceof JsonObject byKind ? text(byKind.get("default")) : text(textures);
            if (name == null) {
               name = text(map.get("texture"));
            }

            if (map.get("frame") instanceof JsonPrimitive number && number.isNumber()) {
               frame = (int)Math.min(Math.max(number.getAsDouble(), 0.0), 2.147483647E9);
            }
         } else {
            name = text(icon);
         }

         return name == null ? null : new PackItemFiles.Icon(name, frame);
      }
   }

   @Nullable
   private static Boolean handEquipped(JsonObject components) {
      Boolean flag = bool(value(components.get("minecraft:hand_equipped")));
      if (flag != null) {
         return flag;
      } else {
         String offsets = text(components.get("minecraft:render_offsets"));
         return offsets != null && offsets.equalsIgnoreCase("tools") ? Boolean.TRUE : null;
      }
   }

   @Nullable
   private static Boolean glint(JsonObject components) {
      Boolean glint = bool(value(components.get("minecraft:glint")));
      return glint != null ? glint : bool(value(components.get("minecraft:foil")));
   }

   @Nullable
   private static JsonElement value(@Nullable JsonElement element) {
      return element instanceof JsonObject object ? object.get("value") : element;
   }

   @Nullable
   private static Boolean bool(@Nullable JsonElement element) {
      if (element instanceof JsonPrimitive primitive) {
         if (primitive.isBoolean()) {
            return primitive.getAsBoolean();
         }

         if (primitive.isString() && (primitive.getAsString().equalsIgnoreCase("true") || primitive.getAsString().equalsIgnoreCase("false"))) {
            return Boolean.parseBoolean(primitive.getAsString());
         }
      }

      return null;
   }

   @Nullable
   private static Integer integer(@Nullable JsonElement element) {
      if (element instanceof JsonPrimitive primitive && primitive.isNumber()) {
         double number = primitive.getAsDouble();
         return number >= 1.0 && number <= 2.147483647E9 ? (int)number : null;
      } else {
         return null;
      }
   }

   @Nullable
   static Entry<String, PackItemFiles.Icon> parseFile(byte[] data) {
      Entry<String, PackItemFiles.ItemFile> item = parseItem(data);
      return item != null && item.getValue().icon() != null ? Map.entry(item.getKey(), item.getValue().icon()) : null;
   }

   @Nullable
   static Entry<String, PackItemFiles.ItemFile> parseItem(byte[] data) {
      JsonObject root = data.length > 262144 ? null : parseJson(data);
      PackItemFiles.Definition definition = root == null ? null : definition(root);
      return definition == null ? null : Map.entry(definition.identifier(), definition.file());
   }

   @Nullable
   private static JsonObject parseJson(byte[] data) {
      String text = new String(data, StandardCharsets.UTF_8);
      if (!text.isEmpty() && text.charAt(0) == '\ufeff') {
         text = text.substring(1);
      }

      try {
         JsonReader reader = new JsonReader(new StringReader(text));

         JsonObject var8;
         try {
            reader.setStrictness(Strictness.LENIENT);
            var8 = JsonParser.parseReader(reader) instanceof JsonObject root ? root : null;
         } catch (Throwable var6) {
            try {
               reader.close();
            } catch (Throwable var5) {
               var6.addSuppressed(var5);
            }

            throw var6;
         }

         reader.close();
         return var8;
      } catch (RuntimeException | IOException var7) {
         return null;
      }
   }

   @Nullable
   private static JsonObject object(@Nullable JsonObject parent, String key) {
      return parent != null && parent.get(key) instanceof JsonObject child ? child : null;
   }

   @Nullable
   private static String string(@Nullable JsonObject parent, String key) {
      return parent == null ? null : text(parent.get(key));
   }

   @Nullable
   private static String text(@Nullable JsonElement element) {
      if (element instanceof JsonPrimitive primitive && primitive.isString() && !primitive.getAsString().isBlank()) {
         return primitive.getAsString().trim();
      } else {
         return element instanceof JsonArray array && !array.isEmpty() ? text(array.get(0)) : null;
      }
   }

   private record Definition(String identifier, PackItemFiles.ItemFile file) {
   }

   public record Icon(String name, int frame) {
   }

   public record ItemFile(
      @Nullable PackItemFiles.Icon icon,
      @Nullable String hoverTextColor,
      @Nullable Boolean handEquipped,
      @Nullable Integer maxStackSize,
      @Nullable Boolean glint
   ) {
      public List<String> components() {
         List<String> names = new ArrayList<>();
         if (this.hoverTextColor != null) {
            names.add("hover_text_color");
         }

         if (this.handEquipped != null) {
            names.add("hand_equipped");
         }

         if (this.maxStackSize != null) {
            names.add("max_stack_size");
         }

         if (this.glint != null) {
            names.add("glint");
         }

         return names;
      }

      boolean empty() {
         return this.icon == null && this.components().isEmpty();
      }
   }
}
