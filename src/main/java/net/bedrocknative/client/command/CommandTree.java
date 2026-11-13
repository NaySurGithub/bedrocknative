package net.bedrocknative.client.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import org.cloudburstmc.protocol.bedrock.data.command.CommandData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandEnumData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandOverloadData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandParamData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandParamOption;
import org.cloudburstmc.protocol.bedrock.data.command.CommandParamType;
import org.jspecify.annotations.Nullable;

final class CommandTree {
   private static final Command<ClientSuggestionProvider> RUNS = context -> 1;

   private CommandTree() {
   }

   static CommandTree.Built build(List<CommandData> commands, Predicate<CommandData> visible, CommandValues values) {
      RootCommandNode<ClientSuggestionProvider> root = new RootCommandNode();
      Map<CommandNode<ClientSuggestionProvider>, List<CommandTree.Usage>> usage = new IdentityHashMap<>();
      Map<CommandTree.Node, CommandNode<ClientSuggestionProvider>> built = new IdentityHashMap<>();
      List<CommandTree.Node> commandNodes = new ArrayList<>();
      Map<CommandEnumData, Integer> enumIds = new IdentityHashMap<>();

      for (CommandData command : commands) {
         if (visible.test(command)) {
            CommandTree.Node node = trie(command, values, enumIds);
            commandNodes.add(node);
            CommandNode<ClientSuggestionProvider> literal = brigadier(node, built, usage);
            if (root.getChild(literal.getName()) == null) {
               root.addChild(literal);
            }

            if (command.getAliases() != null) {
               for (String alias : command.getAliases().getValues().keySet()) {
                  if (!alias.equals(command.getName()) && root.getChild(alias) == null) {
                     CommandNode<ClientSuggestionProvider> aliasNode = new CommandTree.Literal(alias, node.executable ? RUNS : null);
                     literal.getChildren().forEach(aliasNode::addChild);
                     usage.put(aliasNode, usage.get(literal));
                     root.addChild(aliasNode);
                  }
               }
            }
         }
      }

      for (CommandTree.Node commandx : commandNodes) {
         link(commandx, commandx, built, root);
      }

      return new CommandTree.Built(new CommandDispatcher(root), usage);
   }

   private static CommandTree.Node trie(CommandData command, CommandValues values, Map<CommandEnumData, Integer> enumIds) {
      CommandTree.Node top = new CommandTree.Node(command.getName(), null);
      Set<String> names = command.getAliases() != null ? Set.copyOf(command.getAliases().getValues().keySet()) : Set.of(command.getName());
      String chained = command.getSubcommands().isEmpty() ? "chained command" : command.getSubcommands().getFirst().getName();

      for (CommandOverloadData overload : command.getOverloads()) {
         CommandParamData[] params = overload.getOverloads();
         CommandTree.Usage line = new CommandTree.Usage(command.getName(), names, parameters(params, chained));
         top.usage.add(line);
         CommandTree.Node current = top;
         if (params.length == 0 || params[0].isOptional()) {
            top.executable = true;
         }

         for (int i = 0; i < params.length; i++) {
            CommandParamData param = params[i];
            CommandTree.Continue next = continuation(param);
            if (next != CommandTree.Continue.NONE) {
               current.next = next;
               break;
            }

            CommandTree.Node child = current.children.computeIfAbsent(key(param, enumIds), name -> new CommandTree.Node(name, argument(param, values)));
            if (child.argument != null && kind(param) == BedrockArgument.Kind.WILDCARD_TARGET) {
               child.argument = argument(param, values);
            }

            child.usage.add(line);
            if (i + 1 == params.length || params[i + 1].isOptional()) {
               child.executable = true;
            }

            current = child;
         }
      }

      return top;
   }

   private static CommandTree.Continue continuation(CommandParamData param) {
      if (param.getEnumData() != null) {
         return CommandTree.Continue.NONE;
      } else {
         return switch (type(param)) {
            case CHAINED_COMMAND -> CommandTree.Continue.SUBCOMMAND;
            case COMMAND, SLASH_COMMAND, CODE_BUILDER_ARGS -> CommandTree.Continue.COMMAND;
            default -> CommandTree.Continue.NONE;
         };
      }
   }

   private static CommandParamType type(CommandParamData param) {
      return param.getType() != null && param.getType().getParamType() != null ? param.getType().getParamType() : CommandParamType.UNKNOWN;
   }

   private static boolean literal(CommandParamData param) {
      CommandEnumData data = param.getEnumData();
      return data != null && !data.isSoft() && data.getValues().size() == 1;
   }

   private static String key(CommandParamData param, Map<CommandEnumData, Integer> enumIds) {
      CommandEnumData data = param.getEnumData();
      if (literal(param)) {
         return data.getValues().keySet().iterator().next();
      } else if (data != null) {
         return data.isSoft() ? "<soft " + data.getName() + ">" : "<enum " + enumIds.computeIfAbsent(data, unused -> enumIds.size()) + ">";
      } else if (param.getPostfix() != null) {
         return "<postfix " + param.getPostfix().toLowerCase(Locale.ROOT) + ">";
      } else {
         BedrockArgument.Kind kind = kind(param);
         return "<" + (kind == BedrockArgument.Kind.WILDCARD_TARGET ? BedrockArgument.Kind.TARGET : kind) + ">";
      }
   }

   @Nullable
   private static BedrockArgument argument(CommandParamData param, CommandValues values) {
      return literal(param) ? null : new BedrockArgument(kind(param), typeName(param), param.getEnumData(), param.getPostfix(), values);
   }

   private static BedrockArgument.Kind kind(CommandParamData param) {
      CommandEnumData data = param.getEnumData();
      if (data != null) {
         return data.isSoft() ? BedrockArgument.Kind.SOFT_ENUM : BedrockArgument.Kind.ENUM;
      } else if (param.getPostfix() != null) {
         return BedrockArgument.Kind.POSTFIX;
      } else {
         return switch (type(param)) {
            case INT -> BedrockArgument.Kind.INT;
            case FLOAT -> BedrockArgument.Kind.FLOAT;
            case VALUE, R_VALUE -> BedrockArgument.Kind.VALUE;
            case WILDCARD_INT -> BedrockArgument.Kind.WILDCARD_INT;
            case OPERATOR -> BedrockArgument.Kind.OPERATOR;
            case COMPARE_OPERATOR -> BedrockArgument.Kind.COMPARE_OPERATOR;
            case TARGET, STANDALONE_TARGET, NON_ID_TARGET -> BedrockArgument.Kind.TARGET;
            case WILDCARD_TARGET -> BedrockArgument.Kind.WILDCARD_TARGET;
            case POSITION, COORD_X_FLOAT, COORD_Y_FLOAT, COORD_Z_FLOAT, BLOCK_POSITION, COORD_X_INT, COORD_Y_INT, COORD_Z_INT -> BedrockArgument.Kind.POSITION;
            case MESSAGE, MESSAGE_ROOT, MESSAGE_XP, TEXT, TEXT_CONT, CODE_BUILDER_ARG -> BedrockArgument.Kind.MESSAGE;
            case JSON, JSON_VALUE, JSON_FIELD, JSON_OBJECT_FIELDS, JSON_OBJECT_CONT, JSON_ARRAY, JSON_ARRAY_VALUES, JSON_ARRAY_CONT -> BedrockArgument.Kind.JSON;
            case BLOCK_STATE, BLOCK_STATE_KEY, BLOCK_STATE_VALUE, BLOCK_STATE_VALUES, BLOCK_STATES, BLOCK_STATES_CONT -> BedrockArgument.Kind.BLOCK_STATES;
            default -> BedrockArgument.Kind.STRING;
         };
      }
   }

   private static String typeName(CommandParamData param) {
      if (param.getEnumData() != null) {
         return param.getEnumData().isSoft() ? "string" : param.getEnumData().getName();
      } else if (param.getPostfix() != null) {
         return "int";
      } else {
         return switch (type(param)) {
            case COMMAND, SLASH_COMMAND, CODE_BUILDER_ARGS -> "command";
            case INT -> "int";
            case FLOAT -> "float";
            case VALUE, R_VALUE -> "value";
            case WILDCARD_INT -> "wildcard int";
            case OPERATOR -> "operator";
            case COMPARE_OPERATOR -> "compare operator";
            case TARGET, STANDALONE_TARGET, NON_ID_TARGET, WILDCARD_TARGET -> "target";
            case POSITION, BLOCK_POSITION -> "x y z";
            default -> type(param).name().toLowerCase(Locale.ROOT).replace('_', ' ');
            case MESSAGE, MESSAGE_ROOT, MESSAGE_XP -> "message";
            case TEXT, TEXT_CONT -> "text";
            case JSON -> "json";
            case BLOCK_STATES -> "block states";
            case STRING -> "string";
            case FILE_PATH -> "filepath";
            case INT_RANGE, INT_RANGE_FULL -> "integer range";
            case EQUIPMENT_SLOTS -> "equipment slots";
         };
      }
   }

   static String parameters(CommandParamData[] params, String chained) {
      StringBuilder line = new StringBuilder();

      for (CommandParamData param : params) {
         line.append(' ');
         CommandEnumData data = param.getEnumData();
         if (literal(param)) {
            line.append(data.getValues().keySet().iterator().next());
         } else {
            line.append((char)(param.isOptional() ? '[' : '<'));
            if (data != null && !data.isSoft() && param.getOptions().contains(CommandParamOption.SUPPRESS_ENUM_AUTOCOMPLETION)) {
               line.append(String.join("|", data.getValues().keySet()));
            } else {
               line.append(param.getName()).append(": ").append(continuation(param) == CommandTree.Continue.SUBCOMMAND ? chained : typeName(param));
            }

            line.append((char)(param.isOptional() ? ']' : '>'));
            if (param.getPostfix() != null) {
               line.append(param.getPostfix().toUpperCase(Locale.ROOT));
            }
         }
      }

      return line.toString();
   }

   private static CommandNode<ClientSuggestionProvider> brigadier(
      CommandTree.Node node,
      Map<CommandTree.Node, CommandNode<ClientSuggestionProvider>> built,
      Map<CommandNode<ClientSuggestionProvider>, List<CommandTree.Usage>> usage
   ) {
      CommandNode<ClientSuggestionProvider> existing = built.get(node);
      if (existing != null) {
         return existing;
      } else {
         Command<ClientSuggestionProvider> command = node.executable ? RUNS : null;
         CommandNode<ClientSuggestionProvider> result = (CommandNode<ClientSuggestionProvider>)(node.argument == null
            ? new CommandTree.Literal(node.name, command)
            : new CommandTree.Argument(node.name, node.argument, command));
         built.put(node, result);
         usage.put(result, List.copyOf(node.usage));

         for (CommandTree.Node child : node.children.values()) {
            result.addChild(brigadier(child, built, usage));
         }

         return result;
      }
   }

   private static void link(
      CommandTree.Node node,
      CommandTree.Node command,
      Map<CommandTree.Node, CommandNode<ClientSuggestionProvider>> built,
      RootCommandNode<ClientSuggestionProvider> root
   ) {
      CommandNode<ClientSuggestionProvider> target = built.get(node);

      CommandNode<ClientSuggestionProvider> from = (CommandNode<ClientSuggestionProvider>)(switch (node.next) {
         case NONE -> null;
         case SUBCOMMAND -> (CommandNode)built.get(command);
         case COMMAND -> root;
      });
      if (from != null) {
         for (CommandNode<ClientSuggestionProvider> child : List.copyOf(from.getChildren())) {
            if (target.getChild(child.getName()) == null) {
               target.addChild(child);
            }
         }
      }

      for (CommandTree.Node childx : node.children.values()) {
         link(childx, command, built, root);
      }
   }

   private static final class Argument extends ArgumentCommandNode<ClientSuggestionProvider, String> {
      Argument(String name, BedrockArgument type, @Nullable Command<ClientSuggestionProvider> command) {
         super(name, type, command, source -> true, null, null, false, null);
      }

      public boolean equals(Object other) {
         return this == other;
      }

      public int hashCode() {
         return System.identityHashCode(this);
      }
   }

   record Built(CommandDispatcher<ClientSuggestionProvider> dispatcher, Map<CommandNode<ClientSuggestionProvider>, List<CommandTree.Usage>> usage) {
   }

   private static enum Continue {
      NONE,
      SUBCOMMAND,
      COMMAND;
   }

   private static final class Literal extends LiteralCommandNode<ClientSuggestionProvider> {
      Literal(String literal, @Nullable Command<ClientSuggestionProvider> command) {
         super(literal, command, source -> true, null, null, false);
      }

      public boolean equals(Object other) {
         return this == other;
      }

      public int hashCode() {
         return System.identityHashCode(this);
      }
   }

   private static final class Node {
      final String name;
      @Nullable
      BedrockArgument argument;
      final Map<String, CommandTree.Node> children = new LinkedHashMap<>();
      final Set<CommandTree.Usage> usage = new LinkedHashSet<>();
      boolean executable;
      CommandTree.Continue next = CommandTree.Continue.NONE;

      Node(String name, @Nullable BedrockArgument argument) {
         this.name = name;
         this.argument = argument;
      }
   }

   record Usage(String command, Set<String> names, String parameters) {
      String line(String typed) {
         return "/" + (this.names.contains(typed) ? typed : this.command) + this.parameters;
      }
   }
}
