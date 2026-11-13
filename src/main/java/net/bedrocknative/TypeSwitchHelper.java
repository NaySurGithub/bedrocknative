package net.bedrocknative;

// stands in for SwitchBootstraps.typeSwitch, which the decompiler left as raw
// bytecode: the first type at or after start that the value is an instance of,
// -1 for null, types.length when none matches
public final class TypeSwitchHelper {
   private TypeSwitchHelper() {
   }

   public static int index(Class<?>[] types, Object value, int start) {
      if (value == null) {
         return -1;
      }

      for (int i = Math.max(start, 0); i < types.length; i++) {
         if (types[i].isInstance(value)) {
            return i;
         }
      }

      return types.length;
   }
}
