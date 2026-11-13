package net.bedrocknative.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Map.Entry;
import org.jspecify.annotations.Nullable;

public final class UiModifications {
   private UiModifications() {
   }

   public static boolean apply(JsonObject object, JsonObject modification, UiProblems problems, String where) {
      String arrayName = arrayName(modification);
      if (object.get(arrayName) instanceof JsonArray array) {
         apply(array, arrayName.equals("controls"), modification, problems, where);
         return true;
      } else {
         return false;
      }
   }

   public static String arrayName(JsonObject modification) {
      String arrayName = string(modification.get("array_name"));
      return arrayName != null ? arrayName : "controls";
   }

   public static void apply(JsonArray array, boolean controls, JsonObject modification, UiProblems problems, String where) {
      String operation = string(modification.get("operation"));
      if (operation == null) {
         problems.add("modification", where + ": no operation");
      } else {
         JsonElement value = modification.get("value");
         switch (operation) {
            case "insert_back":
               insert(array, array.size(), value, controls);
               break;
            case "insert_front":
               insert(array, 0, value, controls);
               break;
            case "insert_after":
            case "insert_before":
               int indexx = find(array, controls, modification);
               if (indexx < 0) {
                  skipped(problems, where, operation);
               } else {
                  insert(array, operation.equals("insert_after") ? indexx + 1 : indexx, value, controls);
               }
               break;
            case "move_back":
            case "move_front":
               int indexx2 = find(array, controls, modification);
               if (indexx2 < 0 && value != null) {
                  indexx2 = indexOf(array, value);
               }

               if (indexx2 < 0) {
                  skipped(problems, where, operation);
               } else {
                  JsonElement entry = array.remove(indexx2);
                  if (operation.equals("move_back")) {
                     array.add(entry);
                  } else {
                     insert(array, 0, entry, false);
                  }
               }
               break;
            case "move_after":
            case "move_before":
               int indexxxxx = find(array, controls, modification);
               int targetx = target(array, controls, modification);
               if (indexxxxx >= 0 && targetx >= 0 && indexxxxx != targetx) {
                  JsonElement entry = array.remove(indexxxxx);
                  if (indexxxxx < targetx) {
                     targetx--;
                  }

                  insert(array, operation.equals("move_after") ? targetx + 1 : targetx, entry, controls);
               } else {
                  skipped(problems, where, operation);
               }
               break;
            case "swap":
               int indexxxx = find(array, controls, modification);
               int target = target(array, controls, modification);
               if (indexxxx >= 0 && target >= 0) {
                  JsonElement first = array.get(indexxxx);
                  array.set(indexxxx, array.get(target));
                  array.set(target, first);
               } else {
                  skipped(problems, where, operation);
               }
               break;
            case "replace":
               int indexxx = find(array, controls, modification);
               if (indexxx >= 0 && value != null) {
                  array.remove(indexxx);
                  insert(array, indexxx, value, controls);
               } else {
                  skipped(problems, where, operation);
               }
               break;
            case "remove":
               int index = find(array, controls, modification);
               if (index < 0) {
                  skipped(problems, where, operation);
               } else {
                  array.remove(index);
               }
               break;
            default:
               problems.add("modification", where + ": unknown operation " + operation);
         }
      }
   }

   private static void skipped(UiProblems problems, String where, String operation) {
      problems.add("modification-skipped", where + ": " + operation + " matched nothing");
   }

   private static void insert(JsonArray array, int index, @Nullable JsonElement value, boolean controls) {
      if (value != null) {
         int at = Math.min(index, array.size());
         JsonElement copy = value.deepCopy();
         if (copy.isJsonArray()) {
            for (JsonElement element : copy.getAsJsonArray()) {
               if (!(controls && element instanceof JsonObject entry) || entry.size() != 1 || controlIndex(array, (String)entry.keySet().iterator().next()) < 0
                  )
                {
                  array.asList().add(at++, element.deepCopy());
               }
            }
         } else {
            array.asList().add(at, copy);
         }
      }
   }

   private static int find(JsonArray array, boolean controls, JsonObject modification) {
      String name = string(modification.get("control_name"));
      if (name != null) {
         return controlIndex(array, name);
      } else {
         return modification.get("where") instanceof JsonObject where ? matchIndex(array, controls, where) : -1;
      }
   }

   private static int target(JsonArray array, boolean controls, JsonObject modification) {
      JsonElement target = modification.has("target") ? modification.get("target") : modification.get("target_control");
      if (target == null) {
         return -1;
      } else {
         String name = string(target);
         if (name != null) {
            return controlIndex(array, name);
         } else {
            return target instanceof JsonObject where ? matchIndex(array, controls, where) : -1;
         }
      }
   }

   static int controlIndex(JsonArray array, String name) {
      for (int i = 0; i < array.size(); i++) {
         if (array.get(i) instanceof JsonObject entry && entry.size() == 1) {
            String key = (String)entry.keySet().iterator().next();
            int at = key.indexOf(64);
            if ((at < 0 ? key : key.substring(0, at)).equals(name)) {
               return i;
            }
         }
      }

      return -1;
   }

   private static int matchIndex(JsonArray array, boolean controls, JsonObject where) {
      for (int i = 0; i < array.size(); i++) {
         if (array.get(i) instanceof JsonObject entry) {
            JsonObject fields = entry;
            if (controls && entry.size() == 1 && ((Entry)entry.entrySet().iterator().next()).getValue() instanceof JsonObject inner) {
               fields = inner;
            }

            if (matches(fields, where)) {
               return i;
            }
         }
      }

      return -1;
   }

   private static boolean matches(JsonObject entry, JsonObject where) {
      for (Entry<String, JsonElement> field : where.entrySet()) {
         if (!field.getValue().equals(entry.get(field.getKey()))) {
            return false;
         }
      }

      return true;
   }

   private static int indexOf(JsonArray array, JsonElement value) {
      for (int i = 0; i < array.size(); i++) {
         if (array.get(i).equals(value)) {
            return i;
         }
      }

      return -1;
   }

   static @Nullable String string(@Nullable JsonElement element) {
      return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString() ? element.getAsString() : null;
   }
}
