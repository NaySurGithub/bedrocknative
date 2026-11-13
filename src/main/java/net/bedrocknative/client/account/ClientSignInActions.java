package net.bedrocknative.client.account;

import com.mojang.blaze3d.Blaze3D;
import java.net.URI;
import java.util.Locale;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.net.auth.DeviceCodeLink;
import net.bedrocknative.net.auth.SignInActions;
import net.bedrocknative.net.auth.XboxProblem;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

final class ClientSignInActions implements SignInActions {
   @Override
   public boolean openBrowser(String uri) {
      if (!DeviceCodeLink.isMicrosoftPage(uri) && !XboxProblem.isFixedLink(uri)) {
         BedrockNative.LOGGER.warn("Not opening a sign-in page that is not on a Microsoft host");
         return false;
      } else {
         try {
            Blaze3D.openUri(URI.create(uri));
            return true;
         } catch (RuntimeException var3) {
            BedrockNative.LOGGER.warn("Couldn't open the sign-in page: {}", var3.getClass().getSimpleName());
            return false;
         }
      }
   }

   @Override
   public void copyToClipboard(String text) {
      Minecraft minecraft = Minecraft.getInstance();
      minecraft.execute(() -> minecraft.keyboardHandler.setClipboard(text));
   }

   @Override
   public String windowTitle() {
      return AccountProblems.bedrockOr("xbox.signin", "bedrocknative.accounts.window.title").getString();
   }

   @Nullable
   @Override
   public String languageTag() {
      return languageTag(Minecraft.getInstance().options.languageCode);
   }

   @Nullable
   static String languageTag(@Nullable String javaCode) {
      return javaCode != null
            && javaCode.matches("[a-z]{2,3}_[a-z]{2}")
            && !javaCode.equals("lol_us")
            && !javaCode.equals("en_pt")
            && !javaCode.equals("en_ud")
         ? javaCode.substring(0, javaCode.indexOf(95)) + "-" + javaCode.substring(javaCode.indexOf(95) + 1).toUpperCase(Locale.ROOT)
         : null;
   }
}
