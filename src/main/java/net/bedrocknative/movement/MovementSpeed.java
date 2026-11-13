package net.bedrocknative.movement;

import java.util.List;
import java.util.Locale;
import org.cloudburstmc.protocol.bedrock.data.attribute.AttributeModifierData;
import org.cloudburstmc.protocol.bedrock.data.attribute.AttributeOperation;

public final class MovementSpeed {
   public static final String SPRINT_MODIFIER = "d208fc00-42aa-4aad-9276-d5446530de43";
   public static final float SPRINT_BOOST = 0.3F;

   private MovementSpeed() {
   }

   public static float walking(float value, List<AttributeModifierData> modifiers, boolean serverSprinting) {
      boolean listed = false;

      for (AttributeModifierData modifier : modifiers) {
         if (isSprint(modifier)) {
            listed = true;
            if (modifier.getOperation() == AttributeOperation.MULTIPLY_TOTAL && modifier.getAmount() > -1.0F) {
               value /= 1.0F + modifier.getAmount();
            }
         }
      }

      if (!listed && modifiers.isEmpty() && serverSprinting) {
         value /= 1.3F;
      }

      return value;
   }

   private static boolean isSprint(AttributeModifierData modifier) {
      return "d208fc00-42aa-4aad-9276-d5446530de43".equalsIgnoreCase(modifier.getId())
         || modifier.getName() != null && modifier.getName().toLowerCase(Locale.ROOT).contains("sprint");
   }
}
