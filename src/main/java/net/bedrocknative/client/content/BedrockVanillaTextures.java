package net.bedrocknative.client.content;

import java.util.concurrent.CompletableFuture;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.net.SessionTimeline;
import net.bedrocknative.content.BedrockTextureMap;
import net.bedrocknative.content.BedrockTextureSet;
import net.bedrocknative.content.PackStack;
import net.bedrocknative.world.block.custom.SlotPlan;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.data.AtlasIds;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

public final class BedrockVanillaTextures {
   private static final int JAVA_SPRITES = 600;
   private static final long OVERLAY_GAP_MILLIS = 1000L;
   private static final long PREPARE_TIMEOUT_MILLIS = 120000L;
   private static final long RELOAD_TIMEOUT_MILLIS = 120000L;
   private static final Object UNCHANGED = new Object();
   @Nullable
   private static volatile BedrockVanillaTextures.Armed armed;
   @Nullable
   private static BedrockTextureMap blockMap;
   @Nullable
   private static BedrockTextureMap itemMap;
   private static boolean session;
   private static boolean restorePending;
   private static boolean enabled = !Boolean.getBoolean("bedrocknative.javaTextures");
   private static int generation;
   @Nullable
   private static CompletableFuture<?> preparing;
   private static long preparingSince;
   @Nullable
   private static CompletableFuture<Void> reloading;
   private static long reloadRequestedAt;
   private static long overlaySeenAt;
   private static volatile BedrockVanillaTextures.Timings timings = new BedrockVanillaTextures.Timings(0L, 0L, false);

   private BedrockVanillaTextures() {
   }

   @Nullable
   public static BedrockTextureSet armed(BedrockTextureMap.Atlas atlas) {
      BedrockVanillaTextures.Armed current = armed;
      return current == null ? null : (atlas == BedrockTextureMap.Atlas.BLOCKS ? current.blocks() : current.items());
   }

   static void armForTests(@Nullable BedrockTextureSet blocks, @Nullable BedrockTextureSet items) {
      armed = blocks == null && items == null ? null : new BedrockVanillaTextures.Armed(blocks, items, "test");
   }

   public static void start() {
      session = true;
   }

   public static void startGame() {
      if (!session) {
         session = true;
         refresh();
      }
   }

   public static void packsChanged() {
      if (session) {
         refresh();
      }
   }

   public static void release() {
      session = false;
      generation++;
      preparing = null;
      if (armed != null && !restorePending) {
         restorePending = true;
         BedrockNative.LOGGER
            .info("Bedrock textures kept in the atlases after leaving (no reload); Java's come back when a world that is not Bedrock's starts");
      }
   }

   public static void restoreForJavaWorld(String what) {
      if (restorePending && !session) {
         disarm("left the server, " + what + " starts");
      }
   }

   public static boolean restorePending() {
      return restorePending;
   }

   public static void setEnabled(boolean on) {
      enabled = on;
      if (session) {
         refresh();
      }
   }

   public static boolean enabled() {
      return enabled;
   }

   public static boolean isArmed() {
      return armed != null;
   }

   public static BedrockVanillaTextures.Timings timings() {
      return timings;
   }

   public static boolean busy() {
      CompletableFuture<?> prepare = preparing;
      if (session && prepare != null && !prepare.isDone()) {
         return Util.getMillis() - preparingSince < 120000L;
      } else {
         CompletableFuture<Void> reload = reloading;
         if (reload != null && !reload.isDone()) {
            long now = Util.getMillis();
            if (Minecraft.getInstance().gui.overlay() instanceof LoadingOverlay) {
               overlaySeenAt = now;
               return true;
            } else {
               return AtlasReload.running() ? now - reloadRequestedAt < 120000L : now - Math.max(overlaySeenAt, reloadRequestedAt) < 1000L;
            }
         } else {
            return false;
         }
      }
   }

   private static void refresh() {
      int current = ++generation;
      restorePending = false;
      BedrockBlockModels.requestRememberedSlots();
      if (!enabled) {
         disarm("Bedrock textures off");
      } else {
         PackStack stack = PackStack.active();
         if (stack.packs().isEmpty()) {
            disarm("no Bedrock install");
         } else {
            Minecraft minecraft = Minecraft.getInstance();
            BedrockVanillaTextures.Packing blockPacking = packing(minecraft, BedrockTextureMap.Atlas.BLOCKS);
            BedrockVanillaTextures.Packing itemPacking = packing(minecraft, BedrockTextureMap.Atlas.ITEMS);
            BedrockVanillaTextures.Armed previous = armed;
            String previousKey = previous == null ? null : previous.key();
            long started = Util.getMillis();
            preparingSince = started;
            preparing = CompletableFuture.<Object>supplyAsync(() -> prepare(stack, blockPacking, itemPacking, previousKey), Util.backgroundExecutor())
               .whenCompleteAsync((prepared, error) -> {
                  if (current == generation) {
                     long prepareMillis = Util.getMillis() - started;
                     if (error != null) {
                        BedrockNative.LOGGER.warn("Couldn't prepare the Bedrock textures; the atlases stay as they are", error);
                     } else if (prepared == UNCHANGED) {
                        timings = new BedrockVanillaTextures.Timings(prepareMillis, 0L, false);
                        SessionTimeline.mark("textures unchanged");
                        BedrockNative.LOGGER.info("Bedrock textures: the stack changed but no texture did ({} ms); no reload", prepareMillis);
                     } else if (prepared instanceof BedrockVanillaTextures.Armed next && next.blocks().sprites().isEmpty() && next.items().sprites().isEmpty()) {
                        disarm("the stack has none of the mapped textures");
                     } else if (prepared instanceof BedrockVanillaTextures.Armed next) {
                        armed = next;
                        SessionTimeline.mark("textures prepared");
                        reload("Bedrock textures armed", prepareMillis);
                     }

                     preparing = null;
                     BedrockBlockModels.checkSlots();
                  }
               }, minecraft);
         }
      }
   }

   private static Object prepare(
      PackStack stack, BedrockVanillaTextures.Packing blockPacking, BedrockVanillaTextures.Packing itemPacking, @Nullable String previousKey
   ) {
      long started = System.nanoTime();
      BedrockTextureSet.Resolved blocks = BedrockTextureSet.resolve(map(BedrockTextureMap.Atlas.BLOCKS), stack, PackStack::isServerPack);
      BedrockTextureSet.Resolved items = BedrockTextureSet.resolve(map(BedrockTextureMap.Atlas.ITEMS), stack, PackStack::isServerPack);
      String key = blocks.key() + "/" + items.key() + "/" + blockPacking + "/" + itemPacking;
      if (key.equals(previousKey)) {
         return UNCHANGED;
      } else {
         long resolved = System.nanoTime();
         BedrockTextureSet blockSet = blocks.build().fitTo(blockPacking.maxPixels(), blockPacking.padding(), blockPacking.granularity());
         BedrockTextureSet itemSet = items.build().fitTo(itemPacking.maxPixels(), itemPacking.padding(), itemPacking.granularity());
         BedrockNative.LOGGER
            .info(
               "Bedrock textures {}; {} (files read in {} ms, decoded in {} ms)",
               new Object[]{blockSet.stats(), itemSet.stats(), (resolved - started) / 1000000L, (System.nanoTime() - resolved) / 1000000L}
            );
         return new BedrockVanillaTextures.Armed(blockSet, itemSet, key);
      }
   }

   private static synchronized BedrockTextureMap map(BedrockTextureMap.Atlas atlas) {
      if (atlas == BedrockTextureMap.Atlas.BLOCKS) {
         if (blockMap == null) {
            blockMap = BedrockTextureMap.load(atlas);
         }

         return blockMap;
      } else {
         if (itemMap == null) {
            itemMap = BedrockTextureMap.load(atlas);
         }

         return itemMap;
      }
   }

   private static BedrockVanillaTextures.Packing packing(Minecraft minecraft, BedrockTextureMap.Atlas atlas) {
      boolean blocks = atlas == BedrockTextureMap.Atlas.BLOCKS;
      AtlasPacking packing = AtlasPacking.of(minecraft, blocks ? AtlasIds.BLOCKS : AtlasIds.ITEMS, blocks);
      long reserved = 600L * BedrockTextureSet.cost(16, 16, packing.padding(), packing.granularity());
      if (blocks) {
         reserved += Math.max(SlotPlan.budget(packing.maxSide()), SlotPlan.cost(SlotPlan.BASE, packing.padding(), packing.granularity()));
      }

      long side = packing.maxSide();
      return new BedrockVanillaTextures.Packing(packing.padding(), packing.granularity(), Math.max(side * side * 3L / 4L - reserved, 1048576L));
   }

   private static void disarm(String why) {
      preparing = null;
      restorePending = false;
      if (armed != null) {
         armed = null;
         reload("Java textures back: " + why, 0L);
      }
   }

   private static void reload(String why, long prepareMillis) {
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft.isRunning()) {
         long started = Util.getMillis();
         reloadRequestedAt = started;
         BedrockNative.LOGGER.info("{}: restitching the block and item atlases", why);
         CompletableFuture<Void> reload = AtlasReload.run(minecraft, why);
         reloading = reload;
         reload.thenRun(
            () -> {
               long millis = Util.getMillis() - started;
               timings = new BedrockVanillaTextures.Timings(prepareMillis, millis, true);
               if (armed != null) {
                  SessionTimeline.mark("atlases reloaded");
                  BedrockNative.LOGGER
                     .info(
                        "{}: atlases rebuilt in {} ms (prepared in {} ms); block atlas {}; item atlas {}",
                        new Object[]{
                           why,
                           millis,
                           prepareMillis,
                           BedrockSpriteSource.last(BedrockTextureMap.Atlas.BLOCKS),
                           BedrockSpriteSource.last(BedrockTextureMap.Atlas.ITEMS)
                        }
                     );
               } else {
                  BedrockNative.LOGGER.info("{}: atlases rebuilt in {} ms", why, millis);
               }
            }
         );
      }
   }

   private record Armed(BedrockTextureSet blocks, BedrockTextureSet items, String key) {
   }

   private record Packing(int padding, int granularity, long maxPixels) {
   }

   public record Timings(long prepareMillis, long reloadMillis, boolean reloaded) {
   }
}
