package net.bedrocknative.client.command;

import com.mojang.brigadier.tree.CommandNode;
import java.lang.runtime.SwitchBootstraps;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Map.Entry;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.mixin.command.ClientPacketListenerAccessor;
import net.bedrocknative.text.BedrockText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import org.cloudburstmc.protocol.bedrock.data.SimpleEventType;
import org.cloudburstmc.protocol.bedrock.data.command.CommandData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandEnumConstraint;
import org.cloudburstmc.protocol.bedrock.data.command.CommandEnumData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandOverloadData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandParamData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandPermission;
import org.cloudburstmc.protocol.bedrock.data.command.CommandData.Flag;
import org.cloudburstmc.protocol.bedrock.packet.AvailableCommandsPacket;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetCommandsEnabledPacket;
import org.cloudburstmc.protocol.bedrock.packet.SimpleEventPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateAbilitiesPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateSoftEnumPacket;
import org.jspecify.annotations.Nullable;

public final class BedrockCommands implements CommandValues {
   @Nullable
   private static volatile BedrockCommands active;
   private final Minecraft minecraft;
   private List<CommandData> commands = List.of();
   private final Map<String, String> descriptions = new HashMap<>();
   private final Map<String, CommandEnumData> namedEnums = new HashMap<>();
   private final Map<CommandEnumData, Map<String, Set<CommandEnumConstraint>>> lowerCaseEnums = new IdentityHashMap<>();
   private final Map<String, Set<String>> softEnums = new HashMap<>();
   private Map<CommandNode<ClientSuggestionProvider>, List<CommandTree.Usage>> usage = Map.of();
   private CommandPermission permission = CommandPermission.ANY;
   private boolean cheats = true;
   private long uniqueEntityId;

   public BedrockCommands(Minecraft minecraft) {
      this.minecraft = minecraft;
   }

   @Nullable
   public static BedrockCommands active() {
      return active;
   }

   public static boolean handles(BedrockPacket packet) {
      return packet instanceof AvailableCommandsPacket
         || packet instanceof UpdateSoftEnumPacket
         || packet instanceof SetCommandsEnabledPacket
         || packet instanceof SimpleEventPacket;
   }

   public void start(long uniqueEntityId, boolean cheats) {
      this.uniqueEntityId = uniqueEntityId;
      this.cheats = cheats;
      active = this;
      this.install();
   }

   public void close() {
      if (active == this) {
         active = null;
      }
   }

   public void apply(BedrockPacket packet) {
      Objects.requireNonNull(packet);
      BedrockPacket var2 = packet;
      byte var3 = 0;

      while (true) {
         switch (SwitchBootstraps.typeSwitch<"typeSwitch",AvailableCommandsPacket,UpdateSoftEnumPacket,SetCommandsEnabledPacket,SimpleEventPacket>(var2, var3)) {
            case 0:
               AvailableCommandsPacket available = (AvailableCommandsPacket)var2;
               this.commands = List.copyOf(available.getCommands());
               this.collectDescriptions();
               this.collectEnums();
               this.install();
               if ("commands".equals(System.getProperty("bedrocknative.devTest"))) {
                  CommandDump.write(this.minecraft.gameDirectory.toPath(), available);
               }

               return;
            case 1:
               UpdateSoftEnumPacket update = (UpdateSoftEnumPacket)var2;
               CommandEnumData data = update.getSoftEnum();
               Set<String> values = this.softEnums.computeIfAbsent(data.getName(), name -> new LinkedHashSet<>());
               switch (update.getType()) {
                  case ADD:
                     values.addAll(data.getValues().keySet());
                     break;
                  case REMOVE:
                     values.removeAll(data.getValues().keySet());
                     break;
                  case REPLACE:
                     values.clear();
                     values.addAll(data.getValues().keySet());
               }

               BedrockNative.LOGGER.debug("Soft enum {} {} -> {}", new Object[]{data.getName(), update.getType(), values});
               return;
            case 2:
               SetCommandsEnabledPacket enabled = (SetCommandsEnabledPacket)var2;
               this.cheats = enabled.isCommandsEnabled();
               this.install();
               return;
            case 3:
               SimpleEventPacket event = (SimpleEventPacket)var2;
               if (event.getEvent() != SimpleEventType.ENABLE_COMMANDS && event.getEvent() != SimpleEventType.DISABLE_COMMANDS) {
                  var3 = 4;
                  break;
               } else {
                  this.cheats = event.getEvent() == SimpleEventType.ENABLE_COMMANDS;
                  this.install();
                  return;
               }
            default:
               return;
         }
      }
   }

   public void onAbilities(UpdateAbilitiesPacket abilities) {
      if (abilities.getUniqueEntityId() == this.uniqueEntityId
         && abilities.getCommandPermission() != null
         && abilities.getCommandPermission() != this.permission) {
         this.permission = abilities.getCommandPermission();
         this.install();
      }
   }

   private void collectDescriptions() {
      this.descriptions.clear();

      for (CommandData command : this.commands) {
         String description = command.getDescription();
         if (description != null && !description.isBlank()) {
            String localized = BedrockText.translate(description, List.of());
            this.descriptions.putIfAbsent(command.getName().toLowerCase(Locale.ROOT), localized);
            if (command.getAliases() != null) {
               for (String alias : command.getAliases().getValues().keySet()) {
                  this.descriptions.putIfAbsent(alias.toLowerCase(Locale.ROOT), localized);
               }
            }
         }
      }
   }

   @Nullable
   public String description(String name) {
      if (name == null) {
         return null;
      } else {
         String key = name.toLowerCase(Locale.ROOT);
         if (key.startsWith("/")) {
            key = key.substring(1);
         }

         return this.descriptions.get(key);
      }
   }

   private void collectEnums() {
      this.namedEnums.clear();
      this.lowerCaseEnums.clear();
      this.softEnums.clear();

      for (CommandData command : this.commands) {
         for (CommandOverloadData overload : command.getOverloads()) {
            for (CommandParamData param : overload.getOverloads()) {
               CommandEnumData data = param.getEnumData();
               if (data != null) {
                  if (data.isSoft()) {
                     this.softEnums.computeIfAbsent(data.getName(), name -> new LinkedHashSet<>(data.getValues().keySet()));
                  } else {
                     this.namedEnums.putIfAbsent(data.getName(), data);
                  }
               }
            }
         }
      }
   }

   private boolean visible(CommandData command) {
      return !command.getFlags().contains(Flag.HIDDEN_FROM_PLAYER)
         && command.getPermission().ordinal() <= this.permission.ordinal()
         && (this.cheats || command.getFlags().contains(Flag.NOT_CHEAT));
   }

   private void install() {
      ClientPacketListener connection = this.minecraft.getConnection();
      if (connection != null && active == this && !this.commands.isEmpty()) {
         CommandTree.Built built = CommandTree.build(this.commands, this::visible, this);
         this.usage = built.usage();
         ((ClientPacketListenerAccessor)connection).bedrocknative$setCommands(built.dispatcher());
         BedrockNative.LOGGER
            .info(
               "Bedrock commands: {} of {} shown (command permission {}, cheats {})",
               new Object[]{built.dispatcher().getRoot().getChildren().size(), this.commands.size(), this.permission, this.cheats}
            );
      }
   }

   public List<String> usage(CommandNode<ClientSuggestionProvider> node, String typed) {
      List<CommandTree.Usage> lines = this.usage.get(node);
      if (lines == null) {
         return List.of();
      } else {
         String name = typed.toLowerCase(Locale.ROOT);
         return lines.stream().map(line -> line.line(name)).distinct().sorted().toList();
      }
   }

   @Override
   public Collection<String> values(CommandEnumData data) {
      return (Collection<String>)(data.isSoft()
         ? this.softEnums.getOrDefault(data.getName(), data.getValues().keySet())
         : data.getValues().entrySet().stream().filter(value -> this.allowed(value.getValue())).map(Entry::getKey).toList());
   }

   @Override
   public boolean contains(CommandEnumData data, String value) {
      Map<String, Set<CommandEnumConstraint>> lower = this.lowerCaseEnums.computeIfAbsent(data, BedrockCommands::lowerCase);
      Set<CommandEnumConstraint> constraints = lower.get(value.toLowerCase(Locale.ROOT));
      return constraints != null && this.allowed(constraints);
   }

   @Override
   public Collection<String> values(String enumName) {
      Collection<String> soft = this.softEnums.get(enumName);
      if (soft != null) {
         return soft;
      } else {
         CommandEnumData data = this.namedEnums.get(enumName);
         return (Collection<String>)(data != null ? this.values(data) : List.of());
      }
   }

   private static Map<String, Set<CommandEnumConstraint>> lowerCase(CommandEnumData data) {
      Map<String, Set<CommandEnumConstraint>> lower = new HashMap<>();
      data.getValues().forEach((value, constraints) -> lower.putIfAbsent(value.toLowerCase(Locale.ROOT), (Set<CommandEnumConstraint>)constraints));
      return lower;
   }

   private boolean allowed(Set<CommandEnumConstraint> constraints) {
      for (CommandEnumConstraint constraint : constraints) {
         boolean holds = switch (constraint) {
            case CHEATS_ENABLED -> this.cheats;
            case OPERATOR_PERMISSIONS -> this.permission.ordinal() >= CommandPermission.GAME_DIRECTORS.ordinal();
            case HOST_PERMISSIONS -> this.permission.ordinal() >= CommandPermission.HOST.ordinal();
            case ALLOW_ALIASES -> true;
         };
         if (!holds) {
            return false;
         }
      }

      return true;
   }
}
