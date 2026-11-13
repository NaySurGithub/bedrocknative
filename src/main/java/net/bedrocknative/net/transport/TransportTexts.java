package net.bedrocknative.net.transport;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

public final class TransportTexts {
   private TransportTexts() {
   }

   public static String connecting(TransportKind kind) {
      return switch (kind) {
         case RAKNET -> "Connecting over Bedrock...";
         case NETHERNET -> "Connecting over Bedrock...";
         case JAVA -> "Connecting to the Java server...";
      };
   }

   public static String tcpFindings(@Nullable JavaStatus java, int javaPort, @Nullable ProbeResult netherNet, int netherNetPort) {
      boolean askedJava = java != null && !java.answered();
      boolean askedNetherNet = netherNet != null && !netherNet.supported() && netherNet.reason() != ProbeResult.Reason.UNAVAILABLE;
      if (!askedJava && !askedNetherNet) {
         return "";
      } else {
         boolean javaSilent = !askedJava || java.problem().equals("refused") || java.problem().startsWith("no answer");
         boolean netherNetSilent = !askedNetherNet || netherNet.nothingListens();
         if (!javaSilent || !netherNetSilent || askedJava && askedNetherNet && javaPort != netherNetPort) {
            List<String> found = new ArrayList<>();
            if (askedJava) {
               found.add("no Java server on TCP port " + javaPort + " (" + java.problem() + ")");
            }

            if (askedNetherNet) {
               found.add("no NetherNet on TCP port " + netherNetPort + " (" + reason(netherNet) + ")");
            }

            return "; " + String.join(", ", found);
         } else {
            return "; nothing answers on TCP port " + (askedJava ? javaPort : netherNetPort) + " either";
         }
      }
   }

   public static String nothingAnsweredSuffix(
      boolean bedrockAnswered, @Nullable JavaStatus java, int javaPort, @Nullable ProbeResult netherNet, int netherNetPort, @Nullable String typedAddress
   ) {
      return !bedrockAnswered && (java == null || !java.answered()) && (netherNet == null || !netherNet.supported())
         ? tcpFindings(java, javaPort, netherNet, netherNetPort) + (typedAddress == null ? "" : javaHint(typedAddress))
         : "";
   }

   public static String javaHint(String typedAddress) {
      return "; if this is a Java Edition server (its status may be switched off), put java:// in front of the address (java://"
         + typedAddress
         + ") to join it as one";
   }

   private static String reason(ProbeResult probe) {
      return probe.reason() == ProbeResult.Reason.HTTP_STATUS ? "HTTP " + probe.httpStatus() : probe.reason().name().toLowerCase(Locale.ROOT);
   }
}
