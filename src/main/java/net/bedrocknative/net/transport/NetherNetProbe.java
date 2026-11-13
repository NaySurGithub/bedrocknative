package net.bedrocknative.net.transport;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;

public interface NetherNetProbe {
   CompletableFuture<ProbeResult> probe(String var1, InetSocketAddress var2, @Nullable SignalingScheme var3, Duration var4);

   default boolean available() {
      return true;
   }
}
