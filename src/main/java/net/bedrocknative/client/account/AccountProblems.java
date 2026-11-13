package net.bedrocknative.client.account;

import java.util.Locale;
import java.util.Map;
import net.bedrocknative.net.auth.XboxAccountStore;
import net.bedrocknative.net.auth.XboxProblem;
import net.bedrocknative.net.auth.XboxProblemText;
import net.minecraft.client.Minecraft;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

final class AccountProblems {
   @Nullable
   private static volatile Map<String, String> bedrock;
   @Nullable
   private static volatile String bedrockLanguage;

   private AccountProblems() {
   }

   static XboxProblemText.Text text(XboxProblem.Diagnosis diagnosis, @Nullable XboxAccountStore.Entry account) {
      Map<String, String> install = bedrockTexts();
      Language language = Language.getInstance();
      return XboxProblemText.of(diagnosis, XboxProblemText.texts(install::get, language::getOrDefault), AccountManager.name(account).getString());
   }

   static Component message(XboxProblem.Diagnosis diagnosis, @Nullable XboxAccountStore.Entry account) {
      return Component.literal(text(diagnosis, account).full());
   }

   static Component shortLine(XboxProblem.Diagnosis diagnosis, @Nullable XboxAccountStore.Entry account) {
      return Component.literal(text(diagnosis, account).body());
   }

   static Component bedrockOr(String bedrockKey, String modKey) {
      String text = bedrockTexts().get(bedrockKey);
      return text != null ? Component.literal(text) : Component.translatable(modKey);
   }

   private static Map<String, String> bedrockTexts() {
      String code = Minecraft.getInstance().options.languageCode.toLowerCase(Locale.ROOT);
      Map<String, String> current = bedrock;
      if (current == null || !code.equals(bedrockLanguage)) {
         current = XboxProblemText.installTexts(code);
         bedrock = current;
         bedrockLanguage = code;
      }

      return current;
   }
}
