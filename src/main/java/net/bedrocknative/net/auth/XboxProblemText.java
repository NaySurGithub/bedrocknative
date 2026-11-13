package net.bedrocknative.net.auth;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import net.bedrocknative.oreui.OreInstall;
import net.bedrocknative.text.BedrockLanguage;
import org.jspecify.annotations.Nullable;

public final class XboxProblemText {
   static final String ERROR_CODE_KEY = "bedrocknative.accounts.problem.error_code";
   static final String TRY_AGAIN_KEY = "bedrocknative.accounts.problem.action.try_again";
   static final String SIGN_IN_KEY = "bedrocknative.accounts.signin_again";
   static final String OPEN_KEY = "bedrocknative.accounts.problem.action.open";
   static final String BEDROCK_ERROR_CODE = "xbox.failedsignin.error";
   static final String BEDROCK_MORE_INFO = "xbox.failedsignin.moreInfo";
   static final String BEDROCK_LEARN_MORE = "hbui.PlayerBannedModalRoute.close";
   static final String BEDROCK_BAN_BODY_2 = "hbui.PlayerBannedModalRoute.xboxLiveBody2";
   public static final String BEDROCK_SIGNING_IN = "xbox.signingin";
   public static final String BEDROCK_SIGN_IN = "xbox.signin";
   public static final String BEDROCK_WELCOME = "xbox.signin.welcome";
   public static final String BEDROCK_CANCEL = "gui.cancel";
   public static final String BEDROCK_CLOSE = "gui.close";
   public static final String BEDROCK_OK = "gui.ok";

   private XboxProblemText() {
   }

   public static XboxProblemText.Text of(XboxProblem.Diagnosis diagnosis, XboxProblemText.Texts texts, String accountName) {
      XboxProblem problem = diagnosis.problem();
      String action = actionLabel(problem, texts);
      String title = problem.bedrockTitleKey() == null ? null : texts.bedrock(problem.bedrockTitleKey());
      String body = null;
      if (problem.bedrockBodyKey() != null) {
         body = texts.bedrock(problem.bedrockBodyKey());
         if (body != null && problem == XboxProblem.BANNED) {
            String second = texts.bedrock("hbui.PlayerBannedModalRoute.xboxLiveBody2", action == null ? "" : action);
            body = second == null ? body : body + " " + second;
         }
      }

      if (body == null) {
         body = texts.mod(problem.langKey(), accountName);
      }

      String errorLine = null;
      if (diagnosis.errorCode() != null) {
         String label = texts.bedrock("xbox.failedsignin.error");
         errorLine = (label == null ? texts.mod("bedrocknative.accounts.problem.error_code") : label) + " " + diagnosis.errorCode();
      }

      return new XboxProblemText.Text(title, body, errorLine, action);
   }

   @Nullable
   private static String actionLabel(XboxProblem problem, XboxProblemText.Texts texts) {
      return switch (problem.action()) {
         case NONE -> null;
         case TRY_AGAIN -> texts.mod("bedrocknative.accounts.problem.action.try_again");
         case SIGN_IN -> texts.mod("bedrocknative.accounts.signin_again");
         case LINK -> {
            String bedrock = problem == XboxProblem.BANNED
               ? texts.bedrock("hbui.PlayerBannedModalRoute.close")
               : (problem == XboxProblem.OTHER ? texts.bedrock("xbox.failedsignin.moreInfo") : null);
            yield bedrock != null ? bedrock : texts.mod("bedrocknative.accounts.problem.action.open", host(problem.link()));
         }
      };
   }

   static String host(@Nullable String link) {
      if (link == null) {
         return "";
      } else {
         try {
            String host = URI.create(link).getHost();
            return host == null ? link : (host.startsWith("www.") ? host.substring(4) : host);
         } catch (IllegalArgumentException var2) {
            return link;
         }
      }
   }

   public static Set<String> bedrockKeys() {
      Set<String> keys = new TreeSet<>(
         List.of(
            "xbox.failedsignin.error",
            "xbox.failedsignin.moreInfo",
            "hbui.PlayerBannedModalRoute.close",
            "hbui.PlayerBannedModalRoute.xboxLiveBody2",
            "xbox.signingin",
            "xbox.signin",
            "xbox.signin.welcome",
            "gui.cancel",
            "gui.close",
            "gui.ok"
         )
      );

      for (XboxProblem problem : XboxProblem.values()) {
         if (problem.bedrockTitleKey() != null) {
            keys.add(problem.bedrockTitleKey());
         }

         if (problem.bedrockBodyKey() != null) {
            keys.add(problem.bedrockBodyKey());
         }
      }

      return keys;
   }

   public static Map<String, String> installTexts(Optional<Path> vanillaTexts, Path oreTexts, String javaCode) {
      Map<String, String> all = new HashMap<>();
      vanillaTexts.filter(x$0 -> Files.isDirectory(x$0)).ifPresent(folder -> all.putAll(BedrockLanguage.load(folder, javaCode)));
      if (Files.isDirectory(oreTexts)) {
         all.putAll(BedrockLanguage.load(oreTexts, javaCode));
      }

      Map<String, String> kept = new HashMap<>();

      for (String key : bedrockKeys()) {
         String text = all.get(key);
         if (text != null && !text.isBlank()) {
            kept.put(key, text);
         }
      }

      return Map.copyOf(kept);
   }

   public static Map<String, String> installTexts(String javaCode) {
      return installTexts(BedrockLanguage.textsFolder(), OreInstall.textsFolder(), javaCode);
   }

   public static XboxProblemText.Texts texts(final Function<String, String> bedrockRaw, final Function<String, String> modRaw) {
      return new XboxProblemText.Texts() {
         @Nullable
         @Override
         public String bedrock(String key, Object... args) {
            String raw = bedrockRaw.apply(key);
            return raw == null ? null : XboxProblemText.fill(raw, args);
         }

         @Override
         public String mod(String key, Object... args) {
            return XboxProblemText.fill(modRaw.apply(key), args);
         }
      };
   }

   static String fill(String text, Object... args) {
      if (text.indexOf(37) < 0) {
         return text;
      } else {
         StringBuilder out = new StringBuilder();
         int next = 0;

         for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '%' && i + 1 < text.length()) {
               char d = text.charAt(i + 1);
               if (d == '%') {
                  out.append('%');
                  i++;
               } else {
                  int j = i + 1;

                  while (j < text.length() && Character.isDigit(text.charAt(j))) {
                     j++;
                  }

                  int index;
                  int end;
                  if (j <= i + 1 || j + 1 >= text.length() || text.charAt(j) != '$' || text.charAt(j + 1) != 's' && text.charAt(j + 1) != 'd') {
                     if (d != 's' && d != 'd') {
                        out.append(c);
                        continue;
                     }

                     index = next++;
                     end = i + 1;
                  } else {
                     index = Integer.parseInt(text.substring(i + 1, j)) - 1;
                     end = j + 1;
                  }

                  out.append(index >= 0 && index < args.length ? String.valueOf(args[index]) : "");
                  i = end;
               }
            } else {
               out.append(c);
            }
         }

         return out.toString();
      }
   }

   public record Text(@Nullable String title, String body, @Nullable String errorLine, @Nullable String action) {
      public String full() {
         StringBuilder out = new StringBuilder();
         if (this.title != null) {
            out.append(this.title).append('\n');
         }

         out.append(this.body);
         if (this.errorLine != null) {
            out.append('\n').append(this.errorLine);
         }

         return out.toString();
      }
   }

   public interface Texts {
      @Nullable
      String bedrock(String var1, Object... var2);

      String mod(String var1, Object... var2);
   }
}
