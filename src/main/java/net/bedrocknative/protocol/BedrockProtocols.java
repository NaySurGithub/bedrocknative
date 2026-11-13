package net.bedrocknative.protocol;

import java.util.List;
import org.jspecify.annotations.Nullable;

public final class BedrockProtocols {
   public static final BedrockProtocol V26_52 = new BedrockProtocol(2193, "1.26.52", "26.50-26.52", "bedrock-26.50", CodecFixes::v2193);
   public static final BedrockProtocol V26_63 = new BedrockProtocol(2225, "1.26.63", "26.60-26.63", "bedrock-26.60", CodecFixes::v2225);
   private static final List<BedrockProtocol> NEWEST_FIRST = List.of(V26_63, V26_52);
   private static final List<BedrockProtocol> OLDEST_FIRST = List.of(V26_52, V26_63);

   private BedrockProtocols() {
   }

   public static List<BedrockProtocol> all() {
      return NEWEST_FIRST;
   }

   public static List<BedrockProtocol> oldestFirst() {
      return OLDEST_FIRST;
   }

   public static BedrockProtocol latest() {
      return V26_63;
   }

   @Nullable
   public static BedrockProtocol exact(int protocol) {
      for (BedrockProtocol candidate : NEWEST_FIRST) {
         if (candidate.protocol() == protocol) {
            return candidate;
         }
      }

      return null;
   }

   public static BedrockProtocol forPong(int protocol) {
      BedrockProtocol exact = exact(protocol);
      return exact != null ? exact : latest();
   }

   public static String describe(int protocol) {
      BedrockProtocol exact = exact(protocol);
      return exact != null ? exact.shownVersion() : "protocol " + protocol;
   }
}
