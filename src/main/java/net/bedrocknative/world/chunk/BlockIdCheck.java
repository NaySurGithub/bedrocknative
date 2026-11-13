package net.bedrocknative.world.chunk;

import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2LongMap.Entry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

public final class BlockIdCheck {
   public static final int COLUMNS = 32;
   private static final int TOP_STATES = 5;
   private static final int AIR_PER_MILLE = 10;
   private static volatile BlockIdCheck.@Nullable Report last;
   private static volatile boolean misread;
   private final BlockIdCheck.Ids ids;
   private final int air;
   private final Int2LongOpenHashMap counts = new Int2LongOpenHashMap();
   private int columns;
   private int topWithoutAir;
   private int sections;
   private boolean done;

   public BlockIdCheck(BlockIdCheck.Ids ids) {
      this.ids = ids;
      this.air = ids.air();
      misread = false;
   }

   public static BlockIdCheck.@Nullable Report last() {
      return last;
   }

   public static boolean misread() {
      return misread;
   }

   public synchronized boolean wants() {
      return !this.done;
   }

   public synchronized BlockIdCheck.@Nullable Report column(List<int @Nullable []> layers) {
      if (this.done) {
         return null;
      }

      int[] top = null;

      for (int[] blocks : layers) {
         if (blocks != null) {
            this.sections++;
            this.count(blocks);
            top = blocks;
         }
      }

      if (top == null) {
         return null;
      }

      this.columns++;
      if (!contains(top, this.air)) {
         this.topWithoutAir++;
      }

      if (this.columns < 32) {
         return null;
      }

      this.done = true;

      BlockIdCheck.Report report;
      try {
         report = this.report();
      } catch (RuntimeException e) {
         report = new BlockIdCheck.Report(this.columns + " columns: the block id check failed (" + e + ")", false);
      }

      last = report;
      misread = report.suspicious();
      return report;
   }

   private void count(int[] blocks) {
      int run = blocks[0];
      int length = 0;

      for (int id : blocks) {
         if (id == run) {
            length++;
         } else {
            this.counts.addTo(run, length);
            run = id;
            length = 1;
         }
      }

      this.counts.addTo(run, length);
   }

   private static boolean contains(int[] blocks, int id) {
      for (int block : blocks) {
         if (block == id) {
            return true;
         }
      }

      return false;
   }

   private BlockIdCheck.Report report() {
      long blocks = 0L;
      long airBlocks = this.counts.get(this.air);
      long blockEntityBlocks = 0L;
      long unknownBlocks = 0L;
      int unknownIds = 0;
      int topBlockEntityId = 0;
      long topBlockEntityCount = 0L;
      List<Entry> entries = new ArrayList<>(this.counts.int2LongEntrySet());

      for (Entry entry : entries) {
         long count = entry.getLongValue();
         int id = entry.getIntKey();
         blocks += count;
         if (id != this.air) {
            if (this.ids.name(id) == null) {
               unknownIds++;
               unknownBlocks += count;
            } else if (this.ids.blockEntity(id)) {
               blockEntityBlocks += count;
               if (count > topBlockEntityCount) {
                  topBlockEntityCount = count;
                  topBlockEntityId = id;
               }
            }
         }
      }

      long nonAir = blocks - airBlocks;
      entries.sort(Comparator.comparingLong(Entry::getLongValue).reversed());
      List<String> top = new ArrayList<>(5);

      for (int i = 0; i < Math.min(5, entries.size()); i++) {
         Entry entry = entries.get(i);
         top.add(this.label(entry.getIntKey()) + " " + percent(entry.getLongValue(), blocks));
      }

      boolean noAirOnTop = this.topWithoutAir * 4 >= this.columns * 3 && airBlocks * 1000L < blocks * 10L;
      boolean blockEntities = nonAir >= 4096L && blockEntityBlocks * 20L >= nonAir;
      boolean unknown = unknownBlocks * 100L >= blocks;
      StringBuilder text = new StringBuilder();
      text.append(
         String.format(
            Locale.ROOT,
            "%d columns, %d sections, %d blocks: air %s, most common %s; blocks with a block entity %s",
            this.columns,
            this.sections,
            blocks,
            percent(airBlocks, blocks),
            top,
            percent(blockEntityBlocks, nonAir)
         )
      );
      if (blockEntityBlocks > 0L) {
         text.append(" (most ").append(this.label(topBlockEntityId)).append(')');
      }

      text.append("; unknown ids ").append(unknownIds);
      if (unknownIds > 0) {
         text.append(" (").append(percent(unknownBlocks, blocks)).append(" of the blocks)");
      }

      text.append("; top section without air in ").append(this.topWithoutAir).append(" of ").append(this.columns).append(" columns");
      List<String> why = new ArrayList<>();
      if (noAirOnTop) {
         why.add("air reads as another block");
      }

      if (blockEntities) {
         why.add("too many blocks with a block entity");
      }

      if (unknown) {
         why.add("unknown ids");
      }

      if (!why.isEmpty()) {
         String tail = why.size() == 1 && unknown
            ? "): the server numbers its blocks another way than we do, or its own blocks have states we do not know"
            : "): the server numbers its blocks another way than we do";
         text.append(" -> the ids do not read as a world (").append(String.join(", ", why)).append(tail);
      }

      return new BlockIdCheck.Report(text.toString(), !why.isEmpty());
   }

   private String label(int id) {
      String name = this.ids.name(id);
      return name != null ? name : "unknown id " + id;
   }

   private static String percent(long part, long whole) {
      return whole <= 0L ? "0%" : String.format(Locale.ROOT, "%.1f%%", part * 100.0 / whole);
   }

   public interface Ids {
      int air();

      @Nullable String name(int var1);

      boolean blockEntity(int var1);
   }

   public record Report(String text, boolean suspicious) {
   }
}
