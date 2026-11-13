package net.bedrocknative.client.model;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.content.PackSource;
import net.bedrocknative.content.PackStack;
import net.bedrocknative.model.entity.ModelRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

public final class BedrockModels {
   private static volatile @Nullable ModelRegistry registry;
   private static int generation;
   private static boolean active;
   private static boolean loading;

   private BedrockModels() {
   }

   public static @Nullable ModelRegistry registry() {
      return registry;
   }

   public static boolean loading() {
      return loading;
   }

   public static void reload() {
      active = true;
      loading = true;
      int current = ++generation;
      List<PackSource> packs = List.copyOf(PackStack.active().packs());
      int serverPacks = Math.min(PackStack.serverPackCount(), packs.size());
      long started = Util.getMillis();
      Minecraft minecraft = Minecraft.getInstance();
      CompletableFuture.<ModelRegistry>supplyAsync(() -> ModelRegistry.load(packs, serverPacks), Util.backgroundExecutor())
         .whenCompleteAsync(
            (loaded, error) -> {
               if (current == generation) {
                  loading = false;
                  if (error != null) {
                     BedrockNative.LOGGER.warn("Couldn't load the Bedrock models of the pack stack", error);
                  } else {
                     registry = loaded;
                     ActorPrep.registryLoaded(loaded);
                     WornAttachables.clear();
                     ComposedTextures.clear();
                     MaterialTypes.clear();
                     BedrockMobs.registryChanged();
                     BedrockNative.LOGGER
                        .info(
                           "Bedrock models ready after {} ms ({} packs, {} from the server)",
                           new Object[]{Util.getMillis() - started, packs.size(), serverPacks}
                        );
                     BedrockActors actors = BedrockActors.current();
                     if (actors != null) {
                        actors.registryChanged(loaded);
                     }
                  }
               }
            },
            minecraft
         );
   }

   public static void packsChanged() {
      if (active) {
         reload();
      }
   }

   public static void release() {
      active = false;
      loading = false;
      generation++;
      registry = null;
      ActorPrep.sessionEnded();
      WornAttachables.clear();
      ComposedTextures.clear();
      MaterialTypes.clear();
      BedrockMobs.release();
      OverlayActors.release();
      BedrockPlayers.release();
   }
}
