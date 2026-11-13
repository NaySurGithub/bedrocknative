package net.bedrocknative.client.command;

import java.util.Collection;
import org.cloudburstmc.protocol.bedrock.data.command.CommandEnumData;

interface CommandValues {
   Collection<String> values(CommandEnumData var1);

   boolean contains(CommandEnumData var1, String var2);

   Collection<String> values(String var1);
}
