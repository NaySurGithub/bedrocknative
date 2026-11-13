package net.bedrocknative.net.auth;

import java.util.Locale;
import net.raphimc.minecraftauth.bedrock.BedrockAuthManager;
import net.raphimc.minecraftauth.msa.model.MsaApplicationConfig;

public enum XboxTitle {
   WIN32("0000000040159362", "Win32"),
   ANDROID("0000000048183522", "Android"),
   OTHER(null, null);

   public static final XboxTitle DEFAULT = ANDROID;
   private final String clientId;
   private final String deviceType;

   XboxTitle(String clientId, String deviceType) {
      this.clientId = clientId;
      this.deviceType = deviceType;
   }

   public String clientId() {
      return this.clientId;
   }

   public String deviceType() {
      return this.deviceType;
   }

   public MsaApplicationConfig msaConfig() {
      if (this.clientId == null) {
         throw new IllegalStateException("No sign-in for " + this);
      } else {
         return new MsaApplicationConfig(this.clientId, "service::user.auth.xboxlive.com::MBI_SSL");
      }
   }

   public static XboxTitle of(BedrockAuthManager account) {
      return ofClientId(account.getMsaApplicationConfig().getClientId());
   }

   public static XboxTitle ofClientId(String clientId) {
      for (XboxTitle title : values()) {
         if (title.clientId != null && title.clientId.equalsIgnoreCase(clientId)) {
            return title;
         }
      }

      return OTHER;
   }

   public static XboxTitle named(String name) {
      if (name != null && !name.isBlank()) {
         XboxTitle title = valueOf(name.trim().toUpperCase(Locale.ROOT));
         if (title == OTHER) {
            throw new IllegalArgumentException("Not a sign-in title: " + name);
         } else {
            return title;
         }
      } else {
         return DEFAULT;
      }
   }
}
