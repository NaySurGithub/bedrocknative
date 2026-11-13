package net.bedrocknative.net.nethernet;

import io.netty.channel.Channel;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class NetherNetAttempts {
   private final Set<Channel> channels = ConcurrentHashMap.newKeySet();
   private final Set<TrustPrompt> prompts = ConcurrentHashMap.newKeySet();
   private volatile boolean stopping;

   public void stop() {
      this.stopping = true;

      for (TrustPrompt prompt : this.prompts) {
         prompt.cancel();
      }

      for (Channel channel : this.channels) {
         channel.close();
      }
   }

   public boolean stopping() {
      return this.stopping;
   }

   public NetherNetAttempts.Tracked track(Channel channel) {
      this.channels.add(channel);
      if (this.stopping) {
         channel.close();
      }

      return () -> this.channels.remove(channel);
   }

   public NetherNetAttempts.Tracked track(TrustPrompt prompt) {
      this.prompts.add(prompt);
      if (this.stopping) {
         prompt.cancel();
      }

      return () -> this.prompts.remove(prompt);
   }

   public int open() {
      return this.channels.size() + this.prompts.size();
   }

   @FunctionalInterface
   public interface Tracked extends AutoCloseable {
      @Override
      void close();
   }
}
