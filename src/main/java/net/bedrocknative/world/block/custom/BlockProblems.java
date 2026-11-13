package net.bedrocknative.world.block.custom;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public final class BlockProblems {
   public static final String PREFIX = "Bedrock block problem";
   private final Map<String, BlockProblems.Problem> problems = new LinkedHashMap<>();
   public static final int LINES = 16;
   private static final boolean ALL_LINES = "all".equalsIgnoreCase(System.getProperty("bedrocknative.blockProblems", "").trim());

   public synchronized void add(String block, BlockProblems.Reason reason, String detail) {
      this.problems
         .merge(
            block + "|" + reason.name(),
            new BlockProblems.Problem(block, reason, detail, 0),
            (first, again) -> new BlockProblems.Problem(first.block(), first.reason(), first.detail(), first.more() + 1)
         );
   }

   public synchronized List<BlockProblems.Problem> list() {
      return List.copyOf(this.problems.values());
   }

   public synchronized boolean isEmpty() {
      return this.problems.isEmpty();
   }

   public synchronized Map<BlockProblems.Reason, Integer> counts() {
      Map<BlockProblems.Reason, Integer> counts = new EnumMap<>(BlockProblems.Reason.class);
      this.problems.values().forEach(problem -> counts.merge(problem.reason(), 1, Integer::sum));
      return counts;
   }

   public synchronized int blocksWithMissingTextures() {
      return (int)this.problems.values().stream().filter(problem -> problem.reason().missingTexture()).map(BlockProblems.Problem::block).distinct().count();
   }

   public String summary() {
      Map<BlockProblems.Reason, Integer> counts = this.counts();
      if (counts.isEmpty()) {
         return "none";
      }

      List<String> parts = new ArrayList<>();
      counts.forEach((reason, count) -> parts.add(reason.label() + ": " + count + (count == 1 ? " block" : " blocks")));
      return "{" + String.join(", ", parts) + "}";
   }

   public void log(Set<String> logged, Consumer<String> out) {
      this.log(logged, out, ALL_LINES ? Integer.MAX_VALUE : 16);
   }

   void log(Set<String> logged, Consumer<String> out, int lines) {
      Map<BlockProblems.Reason, Integer> unwritten = new EnumMap<>(BlockProblems.Reason.class);

      for (BlockProblems.Problem problem : this.list()) {
         if (logged.add(problem.block() + "|" + problem.reason().name())) {
            if (logged.size() <= lines) {
               out.accept(problem.line());
            } else {
               unwritten.merge(problem.reason(), 1, Integer::sum);
            }
         }
      }

      if (!unwritten.isEmpty()) {
         List<String> parts = new ArrayList<>();
         unwritten.forEach((reason, count) -> parts.add(reason.label() + ": " + count));
         out.accept(
            "Bedrock block problem lines: "
               + unwritten.values().stream().mapToInt(Integer::intValue).sum()
               + " more problems not written one by one ("
               + String.join(", ", parts)
               + "; -Dbedrocknative.blockProblems=all writes them); every problem of the session by reason "
               + this.summary()
         );
      }
   }

   public record Problem(String block, BlockProblems.Reason reason, String detail, int more) {
      public String line() {
         return "Bedrock block problem [" + this.reason.label() + "] " + this.block + ": " + this.detail + (this.more > 0 ? " (+" + this.more + " more)" : "");
      }
   }

   public enum Reason {
      GEOMETRY_MISSING("geometry not in any pack"),
      NO_TEXTURE_NAMED("no texture named"),
      TEXTURE_KEY_MISSING("missing texture key"),
      TEXTURE_FILE_MISSING("missing texture file"),
      TEXTURE_UNREADABLE("unreadable texture"),
      ENCRYPTED_PACK("encrypted pack not readable"),
      UNKNOWN_RENDER_METHOD("unknown render method"),
      CULLING_MISSING("culling rules not in any pack"),
      NO_ATLAS_SLOT("no atlas slot"),
      NO_CARRIER_SLOT("no carrier state");

      private final String label;

      Reason(String label) {
         this.label = label;
      }

      public String label() {
         return this.label;
      }

      public boolean missingTexture() {
         return switch (this) {
            case NO_TEXTURE_NAMED, TEXTURE_KEY_MISSING, TEXTURE_FILE_MISSING, TEXTURE_UNREADABLE, ENCRYPTED_PACK, NO_ATLAS_SLOT -> true;
            default -> false;
         };
      }
   }
}
