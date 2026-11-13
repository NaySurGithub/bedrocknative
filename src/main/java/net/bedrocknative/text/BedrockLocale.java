package net.bedrocknative.text;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class BedrockLocale {
   public static final List<String> LANGUAGES = List.of(
      "en_US",
      "en_GB",
      "de_DE",
      "es_ES",
      "es_MX",
      "fr_FR",
      "fr_CA",
      "it_IT",
      "ja_JP",
      "ko_KR",
      "pt_BR",
      "pt_PT",
      "ru_RU",
      "zh_CN",
      "zh_TW",
      "nl_NL",
      "bg_BG",
      "cs_CZ",
      "da_DK",
      "el_GR",
      "fi_FI",
      "hu_HU",
      "id_ID",
      "nb_NO",
      "pl_PL",
      "sk_SK",
      "sv_SE",
      "tr_TR",
      "uk_UA"
   );
   public static final String FALLBACK = "en_US";
   private static final Map<String, String> NEAREST = Map.ofEntries(
      Map.entry("en_au", "en_GB"),
      Map.entry("en_nz", "en_GB"),
      Map.entry("en_ie", "en_GB"),
      Map.entry("es_ar", "es_MX"),
      Map.entry("es_cl", "es_MX"),
      Map.entry("es_ec", "es_MX"),
      Map.entry("es_uy", "es_MX"),
      Map.entry("es_ve", "es_MX"),
      Map.entry("zh_hk", "zh_TW"),
      Map.entry("nn_no", "nb_NO"),
      Map.entry("no_no", "nb_NO")
   );

   private BedrockLocale() {
   }

   public static String of(String javaCode) {
      String code = javaCode == null ? "" : javaCode.trim().toLowerCase(Locale.ROOT).replace('-', '_');

      for (String language : LANGUAGES) {
         if (language.toLowerCase(Locale.ROOT).equals(code)) {
            return language;
         }
      }

      String nearest = NEAREST.get(code);
      if (nearest != null) {
         return nearest;
      }

      int split = code.indexOf(95);
      String base = split < 0 ? code : code.substring(0, split);

      for (String language : LANGUAGES) {
         if (language.startsWith(base + "_")) {
            return language;
         }
      }

      return "en_US";
   }
}
