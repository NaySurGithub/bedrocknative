package net.bedrocknative.client.lobby;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.content.BedrockTextures;
import net.bedrocknative.ui.PlayData;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelStorageException;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.LevelSummary;
import net.minecraft.world.level.storage.LevelStorageSource.LevelCandidates;

final class LobbyWorlds {
   static final float FRAME_ASPECT = 1.7391F;
   static final String DEFAULT_PICTURE = "textures/ui/default_world";
   private static final Map<String, String> SIZES = new ConcurrentHashMap<>();

   private LobbyWorlds() {
   }

   static CompletableFuture<List<LevelSummary>> load(Minecraft minecraft) {
      LevelStorageSource source = minecraft.getLevelSource();

      LevelCandidates candidates;
      try {
         candidates = source.findLevelCandidates();
      } catch (LevelStorageException var4) {
         BedrockNative.LOGGER.warn("Bedrock lobby: couldn't list the worlds", var4);
         return CompletableFuture.completedFuture(List.of());
      }

      return source.loadLevelSummaries(candidates).exceptionally(e -> {
         BedrockNative.LOGGER.warn("Bedrock lobby: couldn't read the worlds", e);
         return List.of();
      }).thenApply(levels -> {
         List<LevelSummary> sorted = new ArrayList<>(levels);
         sorted.sort(null);
         return sorted;
      });
   }

   static List<PlayData.World> rows(Minecraft minecraft, List<LevelSummary> levels, Runnable sizeCounted) {
      List<PlayData.World> rows = new ArrayList<>();
      DateFormat date = DateFormat.getDateInstance(3, Locale.getDefault());

      for (LevelSummary level : levels) {
         String picture = "textures/ui/default_world";
         String source = "InUserPackage";
         Path icon = level.getIcon();
         if (icon != null && Files.isRegularFile(icon)) {
            Path absolute = icon.toAbsolutePath().normalize();
            BedrockTextures.allowFile(absolute, 1.7391F);
            picture = absolute.toString();
            source = "RawPath";
         }

         rows.add(
            new PlayData.World(
               level.getLevelId(),
               level.getLevelName(),
               level.getLastPlayed() > 0L ? date.format(new Date(level.getLastPlayed())) : "",
               mode(level),
               size(minecraft, level.getLevelId(), sizeCounted),
               picture,
               source,
               level.isLocked()
            )
         );
      }

      return rows;
   }

   static String mode(LevelSummary level) {
      if (level.isHardcore()) {
         return "selectWorld.gameMode.hardcore";
      } else {
         GameType mode = level.getGameMode();
         return "createWorldScreen.gameMode." + mode.getName();
      }
   }

   private static String size(Minecraft minecraft, String levelId, Runnable counted) {
      String known = SIZES.get(levelId);
      if (known != null) {
         return known;
      } else {
         Path folder = minecraft.getLevelSource().getLevelPath(levelId);
         SIZES.put(levelId, "");
         Thread.ofVirtual().name("Bedrock lobby world size").start(() -> {
            long bytes = 0L;

            try (Stream<Path> files = Files.walk(folder)) {
               bytes = files.filter(x$0 -> Files.isRegularFile(x$0)).mapToLong(file -> {
                  try {
                     return Files.size(file);
                  } catch (IOException var2x) {
                     return 0L;
                  }
               }).sum();
            } catch (UncheckedIOException | IOException var11) {
               BedrockNative.LOGGER.debug("Bedrock lobby: couldn't measure world {}", levelId, var11);
            }

            SIZES.put(levelId, format(bytes));
            minecraft.execute(counted);
         });
         return "";
      }
   }

   static String format(long bytes) {
      if (bytes < 1048576L) {
         return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
      } else {
         return bytes < 1073741824L ? String.format(Locale.ROOT, "%.1f MB", bytes / 1048576.0) : String.format(Locale.ROOT, "%.1f GB", bytes / 1.0737418E9F);
      }
   }

   static void forgetSizes() {
      SIZES.clear();
   }
}
