package net.bedrocknative.world.block;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import org.jspecify.annotations.Nullable;

public final class BlockUpdateLog {
   static final int FIRST_IDS = 20;
   static final int LINE_AFTER = 200;
   private static final int LIQUID_LAYER_MIN = 5;
   private final Map<Long, int[]> counts = new LinkedHashMap<>();
   private int updates;
   private int liquidLayer;
   private int liquidLayerOdd;
   private int liquidLayerOtherOdd;
   private final List<String> liquidExamples = new ArrayList<>();
   private boolean idsLogged;
   private boolean warned;

   public synchronized BlockUpdateLog.Lines record(int networkId, int layer, String numbering, BlockUpdateLog.Names names) {
      this.updates++;
      long key = (long)networkId << 1 | layer & 1;
      int[] count = this.counts.get(key);
      if (count == null && this.counts.size() < 20) {
         this.counts.put(key, count = new int[1]);
      }

      if (count != null) {
         count[0]++;
      }

      String warning = null;
      if (layer == 1) {
         this.liquidLayer++;
         if (!names.airOrLiquid(networkId)) {
            this.liquidLayerOdd++;
            if (this.liquidExamples.size() < 3) {
               this.liquidExamples.add(networkId + " -> " + names.state(networkId));
            }
         }

         if (names.otherState(networkId) != null && !names.otherAirOrLiquid(networkId)) {
            this.liquidLayerOtherOdd++;
         }

         if (!this.warned && this.liquidLayer >= 5 && this.liquidLayerOdd * 2 > this.liquidLayer) {
            this.warned = true;
            warning = String.format(
               "Block update ids look numbered differently from the server's: %d of %d liquid-layer updates name neither air nor a liquid (e.g. %s), ids %s%s",
               this.liquidLayerOdd,
               this.liquidLayer,
               this.liquidExamples,
               numbering,
               names.otherState(networkId) != null ? "; numbered with every vanilla block, " + this.liquidLayerOtherOdd + " of them would" : ""
            );
         }
      }

      String ids = null;
      if (!this.idsLogged && (this.counts.size() >= 20 && count != null && count[0] == 1 || this.updates >= 200)) {
         this.idsLogged = true;
         ids = this.describe(numbering, names);
      }

      return new BlockUpdateLog.Lines(ids, warning);
   }

   private String describe(String numbering, BlockUpdateLog.Names names) {
      StringJoiner joiner = new StringJoiner(", ");
      this.counts
         .forEach(
            (key, count) -> {
               int id = (int)(key >> 1);
               String other = names.otherState(id);
               joiner.add(
                  id
                     + " -> "
                     + names.state(id)
                     + " (layer "
                     + (key & 1L)
                     + ", "
                     + count[0]
                     + "x"
                     + (other != null ? "; every vanilla block numbered: " + other : "")
                     + ")"
               );
            }
         );
      return String.format(
         "First block update ids (%d updates, ids %s; liquid layer: %d updates, %d naming neither air nor a liquid): %s",
         this.updates,
         numbering,
         this.liquidLayer,
         this.liquidLayerOdd,
         joiner
      );
   }

   public record Lines(@Nullable String ids, @Nullable String warning) {
   }

   public interface Names {
      String state(int var1);

      @Nullable
      String otherState(int var1);

      boolean airOrLiquid(int var1);

      boolean otherAirOrLiquid(int var1);
   }
}
