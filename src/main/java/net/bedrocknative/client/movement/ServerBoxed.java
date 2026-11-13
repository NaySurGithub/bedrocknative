package net.bedrocknative.client.movement;

import net.bedrocknative.movement.ServerBox;
import org.jspecify.annotations.Nullable;

public interface ServerBoxed {
   @Nullable ServerBox bedrocknative$serverBox();

   void bedrocknative$setServerBox(@Nullable ServerBox var1);
}
