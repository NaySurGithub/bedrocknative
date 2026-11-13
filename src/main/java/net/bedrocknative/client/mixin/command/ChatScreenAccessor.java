package net.bedrocknative.client.mixin.command;

import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChatScreen.class)
public interface ChatScreenAccessor {
   @Accessor("commandSuggestions")
   CommandSuggestions bedrocknative$commandSuggestions();

   @Accessor("initial")
   String bedrocknative$initial();

   @Accessor("isDraft")
   boolean bedrocknative$isDraft();

   @Accessor("closeOnSubmit")
   boolean bedrocknative$closeOnSubmit();
}
