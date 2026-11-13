package net.bedrocknative.client.mixin.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientPacketListener.class)
public interface ClientPacketListenerAccessor {
   @Accessor("commands")
   void bedrocknative$setCommands(CommandDispatcher<ClientSuggestionProvider> var1);
}
