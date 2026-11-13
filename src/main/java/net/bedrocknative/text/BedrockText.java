package net.bedrocknative.text;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.bedrocknative.ui.UiFormatting;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

public final class BedrockText {
   private static final Pattern KEY = Pattern.compile("%([A-Za-z][A-Za-z0-9_\\-]*(?:\\.[A-Za-z0-9_\\-]+)+)");
   private static final Pattern BARE_KEY = Pattern.compile("%?[A-Za-z][A-Za-z0-9_\\-]*(?:\\.[A-Za-z0-9_\\-]+)+");
   private static final Pattern ARGUMENT = Pattern.compile("%(?:(\\d+)\\$)?([sd])|%(\\d+)(?![\\d$])");
   private static final int[] COLOURS = new int[128];
   private static final Map<String, String> ALIASES = Map.ofEntries(
      Map.entry("commands.generic.error.permissions", "commands.help.failed"),
      Map.entry("commands.generic.unknown", "command.unknown.command"),
      Map.entry("commands.players.list", "commands.list.players"),
      Map.entry("commands.kill.successful", "commands.kill.success.single"),
      Map.entry("commands.weather.clear", "commands.weather.set.clear"),
      Map.entry("commands.weather.rain", "commands.weather.set.rain"),
      Map.entry("commands.weather.thunder", "commands.weather.set.thunder"),
      Map.entry("commands.time.added", "commands.time.set"),
      Map.entry("createWorldScreen.gameMode.survival", "gameMode.survival"),
      Map.entry("createWorldScreen.gameMode.creative", "gameMode.creative"),
      Map.entry("createWorldScreen.gameMode.adventure", "gameMode.adventure"),
      Map.entry("createWorldScreen.gameMode.spectator", "gameMode.spectator")
   );
   private static volatile Map<String, String> bedrockLanguage;

   private BedrockText() {
   }

   public static void useBedrockLanguage(Map<String, String> texts) {
      bedrockLanguage = Map.copyOf(texts);
   }

   public static MutableComponent message(String text, List<String> parameters, boolean translate) {
      return format(translate ? translate(text, parameters) : text);
   }

   public static String translate(String text, List<String> parameters) {
      List<String> args = new ArrayList<>(parameters.size());

      for (String parameter : parameters) {
         args.add(parameter.startsWith("%") ? translateParameter(parameter) : parameter);
      }

      String bare = text.startsWith("%") ? text.substring(1) : text;
      String whole = lookup(bare);
      if (whole != null) {
         return substitute(whole, args);
      }

      Matcher matcher = KEY.matcher(text);
      StringBuilder out = new StringBuilder();
      boolean any = false;

      while (matcher.find()) {
         String translated = lookup(matcher.group(1));
         if (translated != null) {
            matcher.appendReplacement(out, Matcher.quoteReplacement(substitute(translated, args)));
            any = true;
         }
      }

      matcher.appendTail(out);
      if (any) {
         return out.toString();
      } else {
         return BARE_KEY.matcher(text).matches() && !args.isEmpty() ? String.join(", ", args) : substitute(text, args);
      }
   }

   private static String translateParameter(String parameter) {
      String translated = translate(parameter, List.of());
      return translated.equals(parameter) ? parameter.substring(1) : translated;
   }

   public static MutableComponent format(String text) {
      MutableComponent result = Component.empty();
      Style style = Style.EMPTY;
      StringBuilder run = new StringBuilder();

      for (int i = 0; i < text.length(); i++) {
         char c = text.charAt(i);
         if (c == 167 && i + 1 < text.length()) {
            append(result, run, style);
            style = applyCode(style, Character.toLowerCase(text.charAt(++i)));
         } else if (c != '\r') {
            run.append(c);
         }
      }

      append(result, run, style);
      return result;
   }

   public static List<MutableComponent> lines(String text) {
      return lines(format(text));
   }

   public static List<MutableComponent> lines(Component text) {
      List<MutableComponent> lines = new ArrayList<>();
      MutableComponent[] line = new MutableComponent[]{Component.empty()};
      text.visit((style, part) -> {
         int start = 0;

         for (int end = part.indexOf(10); end >= 0; end = part.indexOf(10, start)) {
            if (end > start) {
               line[0].append(Component.literal(part.substring(start, end)).withStyle(style));
            }

            lines.add(line[0]);
            line[0] = Component.empty();
            start = end + 1;
         }

         if (start < part.length()) {
            line[0].append(Component.literal(part.substring(start)).withStyle(style));
         }

         return Optional.empty();
      }, Style.EMPTY);
      lines.add(line[0]);
      return lines;
   }

   public static MutableComponent ui(String text) {
      if (text.startsWith("{")) {
         try {
            JsonElement json = JsonParser.parseString(text);
            if (json.isJsonObject() && (json.getAsJsonObject().has("rawtext") || json.getAsJsonObject().has("translate"))) {
               return format(rawText(json));
            }
         } catch (RuntimeException var3) {
         }
      }

      String key = text.startsWith("%") ? text.substring(1) : text;
      String translated = BARE_KEY.matcher(text).matches() ? lookup(key) : null;
      return format(translated != null ? translated : text);
   }

   public static String rawTextString(String json) {
      try {
         return rawText(JsonParser.parseString(json));
      } catch (RuntimeException e) {
         return json;
      }
   }

   public static MutableComponent rawText(String json) {
      try {
         return format(rawText(JsonParser.parseString(json)));
      } catch (RuntimeException e) {
         return format(json);
      }
   }

   private static String rawText(JsonElement element) {
      if (!element.isJsonObject()) {
         return element.isJsonPrimitive() ? element.getAsString() : "";
      }

      JsonObject object = element.getAsJsonObject();
      StringBuilder out = new StringBuilder();
      if (object.has("rawtext")) {
         for (JsonElement part : object.getAsJsonArray("rawtext")) {
            out.append(rawText(part));
         }
      }

      if (object.has("text")) {
         out.append(object.get("text").getAsString());
      }

      if (object.has("translate")) {
         List<String> with = new ArrayList<>();
         JsonElement withElement = object.get("with");
         if (withElement != null && withElement.isJsonArray()) {
            for (JsonElement arg : withElement.getAsJsonArray()) {
               with.add(rawText(arg));
            }
         } else if (withElement != null && withElement.isJsonObject()) {
            JsonArray parts = withElement.getAsJsonObject().getAsJsonArray("rawtext");
            if (parts != null) {
               for (JsonElement arg : parts) {
                  with.add(rawText(arg));
               }
            }
         }

         out.append(translate(object.get("translate").getAsString(), with));
      }

      if (object.has("selector")) {
         out.append(object.get("selector").getAsString());
      }

      return out.toString();
   }

   public static String lookupKey(String key) {
      return lookup(key);
   }

   private static String lookup(String key) {
      String bedrock = bedrockLanguage.get(key);
      if (bedrock != null) {
         return bedrock;
      }

      Language language = Language.getInstance();
      if (language.has(key)) {
         return language.getOrDefault(key);
      }

      String alias = ALIASES.get(key);
      return alias != null && language.has(alias) ? language.getOrDefault(alias) : null;
   }

   private static String substitute(String format, List<String> args) {
      Matcher matcher = ARGUMENT.matcher(format);
      StringBuilder out = new StringBuilder();
      int next = 0;

      while (matcher.find()) {
         int index;
         if (matcher.group(3) != null) {
            index = Integer.parseInt(matcher.group(3)) - 1;
         } else if (matcher.group(1) != null) {
            index = Integer.parseInt(matcher.group(1)) - 1;
         } else {
            index = next++;
         }

         String value = index >= 0 && index < args.size() ? args.get(index) : "";
         matcher.appendReplacement(out, Matcher.quoteReplacement(value));
      }

      matcher.appendTail(out);
      return out.toString();
   }

   private static Style applyCode(Style style, char code) {
      int colour = code < COLOURS.length ? COLOURS[code] : -1;
      if (colour >= 0) {
         return style.withColor(colour);
      }

      return switch (code) {
         case 'k' -> style.withObfuscated(true);
         case 'l' -> style.withBold(true);
         default -> style;
         case 'o' -> style.withItalic(true);
         case 'r' -> Style.EMPTY;
      };
   }

   private static void append(MutableComponent target, StringBuilder run, Style style) {
      if (!run.isEmpty()) {
         target.append(Component.literal(run.toString()).withStyle(style));
         run.setLength(0);
      }
   }

   static {
      for (char code = 0; code < COLOURS.length; code++) {
         COLOURS[code] = UiFormatting.color(code);
      }

      bedrockLanguage = Map.of();
   }
}
