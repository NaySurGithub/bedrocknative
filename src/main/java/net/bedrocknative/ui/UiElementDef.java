package net.bedrocknative.ui;

import com.google.gson.JsonObject;
import java.util.BitSet;
import org.jspecify.annotations.Nullable;

public final class UiElementDef {
   private final String namespace;
   private final String name;
   final JsonObject props;
   @Nullable
   String base;
   private final String file;
   final BitSet packs = new BitSet();

   UiElementDef(String namespace, String name, JsonObject props, @Nullable String base, String file) {
      this.namespace = namespace;
      this.name = name;
      this.props = props;
      this.base = base;
      this.file = file;
   }

   public String namespace() {
      return this.namespace;
   }

   public String name() {
      return this.name;
   }

   public String id() {
      return this.namespace + "." + this.name;
   }

   public JsonObject props() {
      return this.props;
   }

   @Nullable
   public String base() {
      return this.base;
   }

   public String file() {
      return this.file;
   }

   public BitSet packs() {
      return (BitSet)this.packs.clone();
   }

   public boolean isAnimation() {
      return this.props.has("anim_type");
   }

   @Override
   public String toString() {
      return this.base == null ? this.id() : this.id() + "@" + this.base;
   }
}
