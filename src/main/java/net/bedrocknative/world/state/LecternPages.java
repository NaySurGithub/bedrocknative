package net.bedrocknative.world.state;

import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtType;

public final class LecternPages {
   public static final int BUTTON_BACK = 1;
   public static final int BUTTON_FORWARD = 2;
   public static final int BUTTON_TAKE_BOOK = 3;
   public static final int BUTTON_JUMP = 100;

   private LecternPages() {
   }

   public static int spreadOf(int javaPage) {
      return Math.max(0, javaPage) / 2;
   }

   public static int firstPageOf(int spread) {
      return Math.max(0, spread) * 2;
   }

   public static int lastPage(int pageCount) {
      return Math.max(0, pageCount - 1);
   }

   public static int clamp(int javaPage, int pageCount) {
      return Math.clamp((long)javaPage, 0, lastPage(pageCount));
   }

   public static boolean turnsPage(int button) {
      return button == 1 || button == 2 || button >= 100;
   }

   public static int target(int button, int current, int pageCount) {
      int wanted = switch (button) {
         case 1 -> current - 1;
         case 2 -> current + 1;
         default -> button >= 100 ? button - 100 : current;
      };
      return clamp(wanted, pageCount);
   }

   public static int pagesOf(NbtMap bookItem) {
      return bookItem.getCompound("tag").getList("pages", NbtType.COMPOUND).size();
   }

   public record State(boolean hasBook, int spread, int totalPages, NbtMap book) {
      public static final LecternPages.State EMPTY = new LecternPages.State(false, 0, 0, NbtMap.EMPTY);

      public static LecternPages.State of(NbtMap tag) {
         NbtMap book = tag.getCompound("book", NbtMap.EMPTY);
         boolean has = tag.getBoolean("hasBook", !book.isEmpty()) && !book.isEmpty();
         if (!has) {
            return EMPTY;
         } else {
            int pages = tag.getInt("totalPages", LecternPages.pagesOf(book));
            return new LecternPages.State(true, Math.max(0, tag.getInt("page", 0)), Math.max(0, pages), book);
         }
      }
   }
}
