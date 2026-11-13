package net.bedrocknative.net.client;

import java.util.List;
import java.util.function.Consumer;
import net.bedrocknative.content.PackCache;
import net.bedrocknative.content.PackSource;
import net.fabricmc.loader.api.FabricLoader;
import org.cloudburstmc.protocol.bedrock.BedrockClientSession;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;

public interface BedrockGameListener {
   void onGamePacket(BedrockClientSession var1, BedrockPacket var2);

   void onDisconnect(String var1);

   default void onLoginSuccess() {
   }

   default void onDisconnect(SessionEnd end) {
      this.onDisconnect(end.logText());
   }

   default PackCache packCache() {
      return new PackCache(FabricLoader.getInstance().getConfigDir().resolve("bedrocknative").resolve("packs"));
   }

   default void onPackOffer(PackOffer offer, Consumer<PackChoice> answer) {
      answer.accept(PackChoice.EVERYTHING);
   }

   default boolean serverPacksDisabled() {
      return false;
   }

   default void onPackProgress(PackProgress progress) {
   }

   default void onPacksApplied(List<? extends PackSource> topFirst, String baseGameVersion, List<String> experiments) {
      topFirst.forEach(PackSource::close);
   }
}
