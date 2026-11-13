package net.bedrocknative.client.command;

import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.SharedSuggestionProvider.TextCoordinates;
import org.cloudburstmc.protocol.bedrock.data.command.CommandEnumData;
import org.jspecify.annotations.Nullable;

final class BedrockArgument implements ArgumentType<String> {
   private static final DynamicCommandExceptionType EXPECTED = new DynamicCommandExceptionType(what -> new LiteralMessage("Expected " + what));
   private static final Pattern INTEGER = Pattern.compile("[-+]?\\d+");
   private static final Pattern NUMBER = Pattern.compile("[-+]?(\\d+\\.?\\d*|\\.\\d+)");
   private static final List<String> OPERATORS = List.of("%=", "*=", "+=", "-=", "/=", "<", "=", ">", "><");
   private static final List<String> COMPARE_OPERATORS = List.of("<", "<=", "=", ">", ">=");
   private final BedrockArgument.Kind kind;
   private final String typeName;
   @Nullable
   private final CommandEnumData enumData;
   @Nullable
   private final String postfix;
   private final CommandValues values;

   BedrockArgument(BedrockArgument.Kind kind, String typeName, @Nullable CommandEnumData enumData, @Nullable String postfix, CommandValues values) {
      this.kind = kind;
      this.typeName = typeName;
      this.enumData = enumData;
      this.postfix = postfix;
      this.values = values;
   }

   public String parse(StringReader reader) throws CommandSyntaxException {
      int start = reader.getCursor();
      switch (this.kind) {
         case TARGET:
         case WILDCARD_TARGET:
            target(reader, this.kind == BedrockArgument.Kind.WILDCARD_TARGET);
            break;
         case POSITION:
            this.coordinates(reader);
            break;
         case STRING:
         case SOFT_ENUM:
            if (reader.canRead() && reader.peek() == '"') {
               reader.readQuotedString();
            } else {
               word(reader);
            }
            break;
         case MESSAGE:
            reader.setCursor(reader.getTotalLength());
            break;
         case JSON:
            if (!reader.canRead() || reader.peek() != '{' && reader.peek() != '[') {
               word(reader);
            } else {
               balanced(reader);
            }
            break;
         case BLOCK_STATES:
            if (!reader.canRead() || reader.peek() != '[') {
               throw this.fail(reader, start);
            }

            balanced(reader);
            break;
         case ENUM:
         default:
            String word = word(reader);
            if (!this.accepts(word)) {
               throw this.fail(reader, start);
            }
      }

      if (reader.getCursor() == start) {
         throw this.fail(reader, start);
      } else {
         return reader.getString().substring(start, reader.getCursor());
      }
   }

   private boolean accepts(String word) {
      return switch (this.kind) {
         case INT -> INTEGER.matcher(word).matches();
         case FLOAT -> NUMBER.matcher(word).matches();
         case VALUE -> word.startsWith("~") ? word.length() == 1 || NUMBER.matcher(word.substring(1)).matches() : NUMBER.matcher(word).matches();
         case WILDCARD_INT -> word.equals("*") || INTEGER.matcher(word).matches();
         case POSTFIX -> this.postfix != null
            && word.length() > this.postfix.length()
            && word.toLowerCase(Locale.ROOT).endsWith(this.postfix.toLowerCase(Locale.ROOT))
            && INTEGER.matcher(word.substring(0, word.length() - this.postfix.length())).matches();
         case OPERATOR -> OPERATORS.contains(word);
         case COMPARE_OPERATOR -> COMPARE_OPERATORS.contains(word);
         default -> !word.isEmpty();
         case ENUM -> this.enumData != null && this.values.contains(this.enumData, word);
      };
   }

   private CommandSyntaxException fail(StringReader reader, int start) {
      reader.setCursor(start);
      return EXPECTED.createWithContext(reader, this.typeName);
   }

   private static String word(StringReader reader) {
      int start = reader.getCursor();

      while (reader.canRead() && reader.peek() != ' ') {
         reader.skip();
      }

      return reader.getString().substring(start, reader.getCursor());
   }

   private static void balanced(StringReader reader) {
      int depth = 0;
      boolean quoted = false;

      while (reader.canRead()) {
         char c = reader.read();
         if (quoted) {
            if (c == '\\' && reader.canRead()) {
               reader.skip();
            } else if (c == '"') {
               quoted = false;
            }
         } else if (c == '"') {
            quoted = true;
         } else if (c != '{' && c != '[') {
            if (c == '}' || c == ']') {
               if (--depth == 0) {
                  return;
               }
            }
         } else {
            depth++;
         }
      }
   }

   private static void target(StringReader reader, boolean wildcard) throws CommandSyntaxException {
      if (reader.canRead()) {
         if (reader.peek() == '@') {
            reader.skip();

            while (reader.canRead() && Character.isLetter(reader.peek())) {
               reader.skip();
            }

            if (reader.canRead() && reader.peek() == '[') {
               balanced(reader);
            }
         } else if (reader.peek() == '"') {
            reader.readQuotedString();
         } else if (wildcard && reader.peek() == '*') {
            reader.skip();
         } else if (reader.peek() != '~' && reader.peek() != '^') {
            word(reader);
         }
      }
   }

   private void coordinates(StringReader reader) throws CommandSyntaxException {
      int start = reader.getCursor();

      for (int axis = 0; axis < 3; axis++) {
         if (axis > 0) {
            while (reader.canRead() && reader.peek() == ' ') {
               reader.skip();
            }
         }

         int coordinate = reader.getCursor();
         boolean relative = reader.canRead() && (reader.peek() == '~' || reader.peek() == '^');
         if (relative) {
            reader.skip();
         }

         while (reader.canRead() && (Character.isDigit(reader.peek()) || reader.peek() == '.' || reader.peek() == '-' || reader.peek() == '+')) {
            reader.skip();
         }

         String number = reader.getString().substring(relative ? coordinate + 1 : coordinate, reader.getCursor());
         if ((!relative || !number.isEmpty()) && !NUMBER.matcher(number).matches()) {
            throw this.fail(reader, start);
         }
      }
   }

   public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
      String remaining = builder.getRemaining();
      switch (this.kind) {
         case WILDCARD_INT:
            suggestValues(builder, List.of("*"));
         case POSTFIX:
         case STRING:
         case JSON:
         case BLOCK_STATES:
         default:
            break;
         case OPERATOR:
            suggestValues(builder, OPERATORS);
            break;
         case COMPARE_OPERATOR:
            suggestValues(builder, COMPARE_OPERATORS);
            break;
         case TARGET:
         case WILDCARD_TARGET:
            Selectors.suggest(
               builder,
               (Collection<String>)(context.getSource() instanceof SharedSuggestionProvider source ? source.getOnlinePlayerNames() : List.of()),
               this.kind == BedrockArgument.Kind.WILDCARD_TARGET,
               this.values
            );
            break;
         case POSITION:
            if (context.getSource() instanceof SharedSuggestionProvider source) {
               List<TextCoordinates> coordinates = new ArrayList<>();
               if (remaining.startsWith("^")) {
                  coordinates.add(TextCoordinates.DEFAULT_LOCAL);
               } else {
                  coordinates.addAll(source.getRelevantCoordinates());
                  if (!coordinates.contains(TextCoordinates.DEFAULT_GLOBAL)) {
                     coordinates.add(TextCoordinates.DEFAULT_GLOBAL);
                  }
               }

               return SharedSuggestionProvider.suggestCoordinates(remaining, coordinates, builder, text -> true);
            }
            break;
         case MESSAGE:
            if (context.getSource() instanceof SharedSuggestionProvider source) {
               return Mentions.suggest(builder.createOffset(builder.getStart() + Mentions.lastWord(remaining)), source.getOnlinePlayerNames());
            }
            break;
         case ENUM:
         case SOFT_ENUM:
            suggestValues(builder, (Collection<String>)(this.enumData == null ? List.of() : this.values.values(this.enumData)));
      }

      return builder.buildFuture();
   }

   static void suggestValues(SuggestionsBuilder builder, Collection<String> candidates) {
      String typed = builder.getRemaining().toLowerCase(Locale.ROOT);
      boolean namespaced = typed.indexOf(58) >= 0;
      Set<String> lower = namespaced ? Set.of() : lowerCase(candidates);

      for (String value : candidates) {
         String key = value.toLowerCase(Locale.ROOT);
         int colon = key.indexOf(58);
         boolean twin = !namespaced && colon >= 0 && lower.contains(key.substring(colon + 1));
         if (!twin && (key.startsWith(typed) || !namespaced && colon >= 0 && key.startsWith(typed, colon + 1))) {
            builder.suggest(value.indexOf(32) >= 0 ? "\"" + value + "\"" : value);
         }
      }
   }

   private static Set<String> lowerCase(Collection<String> values) {
      Set<String> result = new HashSet<>(values.size() * 2);

      for (String value : values) {
         result.add(value.toLowerCase(Locale.ROOT));
      }

      return result;
   }

   public Collection<String> getExamples() {
      return List.of();
   }

   static enum Kind {
      INT,
      FLOAT,
      VALUE,
      WILDCARD_INT,
      POSTFIX,
      OPERATOR,
      COMPARE_OPERATOR,
      TARGET,
      WILDCARD_TARGET,
      POSITION,
      STRING,
      MESSAGE,
      JSON,
      BLOCK_STATES,
      ENUM,
      SOFT_ENUM;
   }
}
