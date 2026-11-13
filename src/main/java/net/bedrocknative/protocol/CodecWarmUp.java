package net.bedrocknative.protocol;

import java.util.function.ObjLongConsumer;

public final class CodecWarmUp {
   private CodecWarmUp() {
   }

   public static Thread start(ObjLongConsumer<String> report) {
      Thread thread = new Thread(() -> {
         long started = System.nanoTime();
         StringBuilder versions = new StringBuilder();

         for (BedrockProtocol protocol : BedrockProtocols.oldestFirst()) {
            protocol.codec();
            versions.append(versions.isEmpty() ? "" : ", ").append(protocol.shownVersion());
         }

         report.accept("codecs (" + versions + ")", (System.nanoTime() - started) / 1000000L);
      }, "Bedrock codec warm-up");
      thread.setDaemon(true);
      thread.setPriority(1);
      thread.start();
      return thread;
   }
}
