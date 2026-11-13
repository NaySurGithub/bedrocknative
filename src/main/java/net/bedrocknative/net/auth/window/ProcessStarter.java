package net.bedrocknative.net.auth.window;

import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.util.List;

@FunctionalInterface
public interface ProcessStarter {
   ProcessStarter SYSTEM = command -> new ProcessBuilder(command).redirectOutput(Redirect.DISCARD).redirectError(Redirect.DISCARD).start();

   Process start(List<String> var1) throws IOException;
}
