package net.bedrocknative.client.mixin.command;

import com.mojang.brigadier.suggestion.Suggestion;
import java.util.List;
import net.minecraft.client.gui.components.CommandSuggestions.SuggestionsList;
import net.minecraft.client.renderer.Rect2i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SuggestionsList.class)
public interface SuggestionsListAccessor {
   @Accessor("suggestionList")
   List<Suggestion> bedrocknative$suggestionList();

   @Accessor("offset")
   int bedrocknative$offset();

   @Accessor("offset")
   void bedrocknative$offset(int var1);

   @Accessor("current")
   int bedrocknative$current();

   @Accessor("tabCycles")
   void bedrocknative$tabCycles(boolean var1);

   @Accessor("rect")
   Rect2i bedrocknative$rect();
}
