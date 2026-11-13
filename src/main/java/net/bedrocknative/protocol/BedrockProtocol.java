package net.bedrocknative.protocol;

import java.util.function.Supplier;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;

public final class BedrockProtocol {
   private final int protocol;
   private final String gameVersion;
   private final String shownVersion;
   private final String blockPalette;
   private final Supplier<BedrockCodec> codecFactory;
   private volatile BedrockCodec codec;

   BedrockProtocol(int protocol, String gameVersion, String shownVersion, String blockPalette, Supplier<BedrockCodec> codecFactory) {
      this.protocol = protocol;
      this.gameVersion = gameVersion;
      this.shownVersion = shownVersion;
      this.blockPalette = blockPalette;
      this.codecFactory = codecFactory;
   }

   public int protocol() {
      return this.protocol;
   }

   public String gameVersion() {
      return this.gameVersion;
   }

   public String shownVersion() {
      return this.shownVersion;
   }

   public String blockPalette() {
      return this.blockPalette;
   }

   public BedrockCodec codec() {
      BedrockCodec built = this.codec;
      if (built == null) {
         synchronized (this) {
            built = this.codec;
            if (built == null) {
               this.codec = built = this.codecFactory.get();
            }
         }
      }

      return built;
   }

   public boolean atLeast(BedrockProtocol other) {
      return this.protocol >= other.protocol;
   }

   @Override
   public String toString() {
      return this.shownVersion + " (protocol " + this.protocol + ")";
   }
}
