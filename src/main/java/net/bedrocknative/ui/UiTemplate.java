package net.bedrocknative.ui;

import com.google.gson.JsonObject;
import java.util.BitSet;
import java.util.Map;

public record UiTemplate(UiElementDef def, JsonObject props, Map<String, String> keyNamespaces, BitSet packs, boolean dynamic) {
   public String namespaceOf(String key) {
      return this.keyNamespaces.getOrDefault(key, this.def.namespace());
   }
}
