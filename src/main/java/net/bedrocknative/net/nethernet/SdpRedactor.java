package net.bedrocknative.net.nethernet;

public final class SdpRedactor {
   private SdpRedactor() {
   }

   public static String redact(String sdp) {
      String eol = sdp.contains("\r\n") ? "\r\n" : "\n";
      String[] lines = sdp.split("\r\n|\n", -1);
      StringBuilder out = new StringBuilder(sdp.length());

      for (int i = 0; i < lines.length; i++) {
         if (i > 0) {
            out.append(eol);
         }

         out.append(redactLine(lines[i]));
      }

      return out.toString();
   }

   private static String redactLine(String line) {
      for (String attribute : new String[]{"a=identity:", "a=ice-pwd:"}) {
         if (line.startsWith(attribute)) {
            return attribute + "<redacted " + (line.length() - attribute.length()) + " chars>";
         }
      }

      return line;
   }
}
