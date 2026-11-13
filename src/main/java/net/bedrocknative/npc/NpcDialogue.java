package net.bedrocknative.npc;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import org.cloudburstmc.protocol.bedrock.data.NpcRequestType;
import org.cloudburstmc.protocol.bedrock.packet.NpcDialoguePacket;
import org.cloudburstmc.protocol.bedrock.packet.NpcRequestPacket;
import org.jspecify.annotations.Nullable;

public record NpcDialogue(long entityUniqueId, String npcName, String text, String sceneName, List<NpcDialogue.Action> actions) {
   public static final int MAX_ACTIONS = 64;
   public static final int MAX_TEXT = 16384;
   public static final int MODE_BUTTON = 0;
   public static final int MODE_ON_EXIT = 1;
   public static final int MODE_ON_ENTER = 2;

   public static NpcDialogue of(NpcDialoguePacket packet) {
      return new NpcDialogue(
         packet.getUniqueEntityId(),
         limited(packet.getNpcName()),
         limited(packet.getDialogue()),
         orEmpty(packet.getSceneName()),
         parseActions(packet.getActionJson())
      );
   }

   public static List<NpcDialogue.Action> parseActions(@Nullable String json) {
      List<NpcDialogue.Action> actions = new ArrayList<>();
      if (json != null && !json.isBlank()) {
         try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonArray()) {
               return actions;
            } else {
               JsonArray array = root.getAsJsonArray();

               for (int i = 0; i < array.size() && actions.size() < 64; i++) {
                  if (array.get(i).isJsonObject()) {
                     JsonObject object = array.get(i).getAsJsonObject();
                     List<String> commands = new ArrayList<>();
                     if (object.has("data") && object.get("data").isJsonArray()) {
                        for (JsonElement entry : object.getAsJsonArray("data")) {
                           if (entry.isJsonObject() && entry.getAsJsonObject().has("cmd_line") && entry.getAsJsonObject().get("cmd_line").isJsonPrimitive()) {
                              commands.add(entry.getAsJsonObject().get("cmd_line").getAsString());
                           }
                        }
                     }

                     actions.add(
                        new NpcDialogue.Action(i, string(object, "button_name"), integer(object, "mode"), integer(object, "type"), List.copyOf(commands))
                     );
                  }
               }

               return actions;
            }
         } catch (RuntimeException var9) {
            return new ArrayList<>(actions);
         }
      } else {
         return actions;
      }
   }

   public List<NpcDialogue.Action> buttons() {
      return this.actions.stream().filter(NpcDialogue.Action::isButton).toList();
   }

   public NpcRequestPacket request(NpcRequestType type, long runtimeEntityId, int actionIndex) {
      NpcRequestPacket request = new NpcRequestPacket();
      request.setRuntimeEntityId(runtimeEntityId);
      request.setRequestType(type);
      request.setCommand("");
      request.setActionType(actionIndex);
      request.setSceneName(this.sceneName);
      return request;
   }

   private static String limited(@Nullable String text) {
      return text == null ? "" : (text.length() > 16384 ? text.substring(0, 16384) : text);
   }

   private static String orEmpty(@Nullable String text) {
      return text == null ? "" : text;
   }

   private static String string(JsonObject object, String key) {
      JsonElement element = object.get(key);
      return element != null && element.isJsonPrimitive() ? element.getAsString() : "";
   }

   private static int integer(JsonObject object, String key) {
      JsonElement element = object.get(key);

      try {
         return element != null && element.isJsonPrimitive() ? element.getAsInt() : 0;
      } catch (NumberFormatException var4) {
         return 0;
      }
   }

   public record Action(int index, String name, int mode, int type, List<String> commands) {
      public boolean isButton() {
         return this.mode == 0;
      }
   }
}
