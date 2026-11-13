package net.bedrocknative.client.command;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.bedrocknative.BedrockNative;
import org.cloudburstmc.protocol.bedrock.data.command.ChainedSubCommandData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandEnumData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandOverloadData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandParamData;
import org.cloudburstmc.protocol.bedrock.packet.AvailableCommandsPacket;

final class CommandDump {
   private CommandDump() {
   }

   static void write(Path gameDirectory, AvailableCommandsPacket packet) {
      StringBuilder out = new StringBuilder();
      Map<String, CommandEnumData> enums = new LinkedHashMap<>();

      for (CommandData command : packet.getCommands()) {
         out.append('/').append(command.getName()).append(" perm=").append(command.getPermission()).append(" flags=").append(command.getFlags());
         if (command.getAliases() != null) {
            out.append(" aliases=").append(command.getAliases().getName()).append(command.getAliases().getValues().keySet());
         }

         out.append(" desc=").append(command.getDescription()).append('\n');

         for (ChainedSubCommandData sub : command.getSubcommands()) {
            out.append("  sub ").append(sub.getName()).append(' ').append(sub.getValues()).append('\n');
         }

         for (CommandOverloadData overload : command.getOverloads()) {
            out.append(overload.isChaining() ? "  chain" : "  ");

            for (CommandParamData param : overload.getOverloads()) {
               out.append(' ').append((char)(param.isOptional() ? '[' : '<')).append(param.getName()).append(": ");
               if (param.getEnumData() != null) {
                  CommandEnumData data = param.getEnumData();
                  enums.putIfAbsent(data.getName(), data);
                  out.append(data.isSoft() ? "soft " : "enum ").append(data.getName());
               } else if (param.getType() != null) {
                  out.append(param.getType().getParamType());
               }

               if (param.getPostfix() != null) {
                  out.append(" postfix=").append(param.getPostfix());
               }

               if (!param.getOptions().isEmpty()) {
                  out.append(' ').append(param.getOptions());
               }

               out.append((char)(param.isOptional() ? ']' : '>'));
            }

            out.append('\n');
         }
      }

      out.append("\nENUMS\n");
      enums.values()
         .forEach(
            datax -> {
               String values = datax.getValues().keySet().toString();
               out.append(datax.isSoft() ? "soft " : "enum ")
                  .append(datax.getName())
                  .append(" (")
                  .append(datax.getValues().size())
                  .append(") ")
                  .append(values.length() > 600 ? values.substring(0, 600) + "..." : values)
                  .append('\n');
               datax.getValues().forEach((value, constraints) -> {
                  if (!constraints.isEmpty()) {
                     out.append("    ").append(value).append(' ').append(constraints).append('\n');
                  }
               });
            }
         );

      try {
         Files.writeString(gameDirectory.resolve("bedrock-commands.txt"), out);
      } catch (IOException e) {
         BedrockNative.LOGGER.warn("Could not write the command dump", e);
      }
   }
}
