package net.bedrocknative.world.item;

import java.util.ArrayList;
import java.util.List;
import org.cloudburstmc.protocol.bedrock.packet.BookEditPacket;
import org.cloudburstmc.protocol.bedrock.packet.BookEditPacket.Action;
import org.jspecify.annotations.Nullable;

public final class BookEdits {
   public static final int MAX_PAGES = 50;
   public static final int MAX_PAGE_LENGTH = 256;

   private BookEdits() {
   }

   public static List<BookEditPacket> plan(int slot, List<String> before, List<BookEdits.Op> ops, List<String> after, @Nullable BookEdits.Signature sign) {
      List<BookEditPacket> packets = new ArrayList<>();
      List<String> server = new ArrayList<>(before);
      after = withoutTrailingBlanks(clamp(after));

      for (BookEdits.Op op : ops) {
         List<String> held = clamp(op.pages());

         int keep = Math.max(withoutTrailingBlanks(held).size(), switch (op) {
            case BookEdits.Op.Insert insert -> insert.page();
            case BookEdits.Op.Delete delete -> delete.page() + 1;
            case BookEdits.Op.Swap swap -> Math.max(swap.first(), swap.second()) + 1;
            default -> throw new MatchException(null, null);
         });
         held = held.subList(0, Math.min(keep, held.size()));

         for (int i = 0; i < held.size(); i++) {
            if (i >= server.size()) {
               packets.add(replace(slot, i, held.get(i)));
               server.add(held.get(i));
            } else if (!server.get(i).equals(held.get(i))) {
               packets.add(replace(slot, i, held.get(i)));
               server.set(i, held.get(i));
            }
         }

         switch (op) {
            case BookEdits.Op.Insert insert:
               if (server.size() < 50) {
                  if (insert.page() < server.size()) {
                     packets.add(page(slot, Action.ADD_PAGE, insert.page(), ""));
                     server.add(insert.page(), "");
                  } else {
                     packets.add(replace(slot, server.size(), ""));
                     server.add("");
                  }
               }
               break;
            case BookEdits.Op.Delete delete:
               if (delete.page() < server.size()) {
                  BookEditPacket packet = base(slot, Action.DELETE_PAGE);
                  packet.setPageNumber(delete.page());
                  packets.add(packet);
                  server.remove(delete.page());
               }
               break;
            case BookEdits.Op.Swap swap:
               if (swap.first() < server.size() && swap.second() < server.size()) {
                  BookEditPacket packet = base(slot, Action.SWAP_PAGES);
                  packet.setPageNumber(swap.first());
                  packet.setSecondaryPageNumber(swap.second());
                  packets.add(packet);
                  String moved = server.get(swap.first());
                  server.set(swap.first(), server.get(swap.second()));
                  server.set(swap.second(), moved);
               }
               break;
            default:
               throw new MatchException(null, null);
         }
      }

      for (int ix = 0; ix < after.size(); ix++) {
         if (ix >= server.size()) {
            packets.add(replace(slot, ix, after.get(ix)));
            server.add(after.get(ix));
         } else if (!server.get(ix).equals(after.get(ix))) {
            packets.add(replace(slot, ix, after.get(ix)));
            server.set(ix, after.get(ix));
         }
      }

      for (int ixx = after.size(); ixx < server.size(); ixx++) {
         if (!server.get(ixx).isEmpty()) {
            packets.add(replace(slot, ixx, ""));
            server.set(ixx, "");
         }
      }

      if (sign != null) {
         BookEditPacket packet = base(slot, Action.SIGN_BOOK);
         packet.setTitle(sign.title());
         packet.setAuthor(sign.author());
         packet.setXuid(sign.xuid());
         packets.add(packet);
      }

      return packets;
   }

   private static List<String> clamp(List<String> pages) {
      List<String> clamped = new ArrayList<>(Math.min(pages.size(), 50));

      for (int i = 0; i < pages.size() && i < 50; i++) {
         String text = pages.get(i);
         if (text.codePointCount(0, text.length()) > 256) {
            text = text.substring(0, text.offsetByCodePoints(0, 256));
         }

         clamped.add(text);
      }

      return clamped;
   }

   private static List<String> withoutTrailingBlanks(List<String> pages) {
      int end = pages.size();

      while (end > 0 && pages.get(end - 1).isEmpty()) {
         end--;
      }

      return pages.subList(0, end);
   }

   private static BookEditPacket replace(int slot, int page, String text) {
      return page(slot, Action.REPLACE_PAGE, page, text);
   }

   private static BookEditPacket page(int slot, Action action, int page, String text) {
      BookEditPacket packet = base(slot, action);
      packet.setPageNumber(page);
      packet.setText(text);
      return packet;
   }

   private static BookEditPacket base(int slot, Action action) {
      BookEditPacket packet = new BookEditPacket();
      packet.setAction(action);
      packet.setInventorySlot(slot);
      packet.setText("");
      packet.setPhotoName("");
      packet.setTitle("");
      packet.setAuthor("");
      packet.setXuid("");
      return packet;
   }

   public sealed interface Op permits BookEdits.Op.Insert, BookEdits.Op.Delete, BookEdits.Op.Swap {
      List<String> pages();

      public record Delete(int page, List<String> pages) implements BookEdits.Op {
      }

      public record Insert(int page, List<String> pages) implements BookEdits.Op {
      }

      public record Swap(int first, int second, List<String> pages) implements BookEdits.Op {
      }
   }

   public record Signature(String title, String author, String xuid) {
   }
}
