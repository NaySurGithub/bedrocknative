package net.bedrocknative.client.content;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

final class ReloadChain {
   private CompletableFuture<Void> last = CompletableFuture.completedFuture(null);

   synchronized CompletableFuture<Void> enqueue(
      Executor executor, Consumer<CompletableFuture<Void>> action, BiConsumer<CompletableFuture<Void>, Throwable> onError
   ) {
      CompletableFuture<Void> result = new CompletableFuture<>();
      CompletableFuture<Void> previous = this.last;
      this.last = result;
      previous.whenCompleteAsync((ignored, error) -> {
         try {
            action.accept(result);
         } catch (Throwable var10) {
            Throwable e = var10;

            try {
               onError.accept(result, e);
            } finally {
               result.complete(null);
            }
         }
      }, executor);
      return result;
   }
}
