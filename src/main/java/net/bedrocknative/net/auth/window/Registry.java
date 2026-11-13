package net.bedrocknative.net.auth.window;

import java.io.IOException;
import org.jspecify.annotations.Nullable;

public interface Registry {
   boolean keyExists(String var1) throws IOException;

   @Nullable
   String get(String var1, String var2) throws IOException;

   void set(String var1, String var2, String var3) throws IOException;

   void deleteKey(String var1) throws IOException;

   @Nullable
   String getMachine(String var1, String var2) throws IOException;
}
