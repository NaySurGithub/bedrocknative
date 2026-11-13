package net.bedrocknative.molang;

abstract class Node {
   abstract float eval(MolangEnv var1);

   boolean isConstant() {
      return false;
   }

   boolean mayBeObject() {
      return true;
   }

   static float fix(float value) {
      return Float.isFinite(value) ? value : 0.0F;
   }

   static boolean truthy(float value, MolangEnv env) {
      if (value != 0.0F) {
         return true;
      } else {
         Object object = env.object;
         if (object == null) {
            return false;
         } else if (object instanceof String text) {
            return !text.isEmpty();
         } else {
            return object instanceof MolangActor actor ? actor.valid() : true;
         }
      }
   }
}
