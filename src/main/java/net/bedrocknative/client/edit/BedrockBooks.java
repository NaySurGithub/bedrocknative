package net.bedrocknative.client.edit;

import java.util.ArrayList;
import java.util.List;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.net.auth.ClientIdentity;
import net.bedrocknative.world.item.BookEdits;
import net.minecraft.network.protocol.game.ServerboundEditBookPacket;
import org.cloudburstmc.protocol.bedrock.packet.BookEditPacket;

public final class BedrockBooks {
   private static List<String> serverPages = List.of();
   private static final List<BookEdits.Op> OPS = new ArrayList<>();
   private static volatile int sent;

   private BedrockBooks() {
   }

   public static void begin(List<String> pages) {
      serverPages = List.copyOf(pages);
      OPS.clear();
   }

   public static void record(BookEdits.Op op) {
      OPS.add(op);
   }

   public static int sentCount() {
      return sent;
   }

   public static List<BookEditPacket> toBedrock(ServerboundEditBookPacket packet, ClientIdentity identity) {
      if (packet.slot() >= 0 && packet.slot() <= 8) {
         BookEdits.Signature signature = packet.title().map(title -> new BookEdits.Signature(title, identity.username(), identity.xuid())).orElse(null);
         List<BookEditPacket> packets = BookEdits.plan(packet.slot(), serverPages, OPS, packet.pages(), signature);
         List<String> after = new ArrayList<>(packet.pages());
         serverPages = List.copyOf(after);
         OPS.clear();
         sent = sent + packets.size();
         return packets;
      } else {
         BedrockNative.LOGGER.warn("Book edit in slot {} not sent: Bedrock edits books in the hotbar only", packet.slot());
         return List.of();
      }
   }
}
