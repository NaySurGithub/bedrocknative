package net.bedrocknative.molang;

import org.jspecify.annotations.Nullable;

public final class QueryArgs {
   static final QueryArgs NONE = new QueryArgs(new Node[0]);
   private final Node[] nodes;

   QueryArgs(Node[] nodes) {
      this.nodes = nodes;
   }

   public int count() {
      return this.nodes.length;
   }

   public float number(MolangEnv env, int index) {
      return index < this.nodes.length ? this.nodes[index].eval(env) : 0.0F;
   }

   public int integer(MolangEnv env, int index) {
      return (int)this.number(env, index);
   }

   @Nullable
   public Object object(MolangEnv env, int index) {
      if (index >= this.nodes.length) {
         return null;
      } else {
         this.nodes[index].eval(env);
         return env.object;
      }
   }

   public String string(MolangEnv env, int index) {
      return this.object(env, index) instanceof String text ? text : "";
   }

   public String slot(MolangEnv env, int index) {
      float number = this.number(env, index);
      if (env.object instanceof String text) {
         return text;
      } else {
         return number == 1.0F ? "off_hand" : "main_hand";
      }
   }
}
