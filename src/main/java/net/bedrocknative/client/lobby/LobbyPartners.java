package net.bedrocknative.client.lobby;

import java.nio.file.Path;
import net.bedrocknative.partner.PartnerServers;
import net.fabricmc.loader.api.FabricLoader;

public final class LobbyPartners {
   private static PartnerServers service;

   private LobbyPartners() {
   }

   public static synchronized PartnerServers service() {
      if (service == null) {
         service = PartnerServers.create(folder());
      }

      return service;
   }

   static Path folder() {
      return FabricLoader.getInstance().getConfigDir().resolve("bedrocknative");
   }

   static synchronized void reset() {
      service = null;
   }
}
