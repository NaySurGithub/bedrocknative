package net.bedrocknative.client.command;

import java.util.List;
import net.bedrocknative.ui.SuggestionWindow;
import org.jspecify.annotations.Nullable;

public interface SuggestionsView {
   boolean bedrocknative$listShown();

   List<String> bedrocknative$suggestions();

   List<String> bedrocknative$usage();

   List<String> bedrocknative$shown();

   void bedrocknative$use(int var1);

   List<String> bedrocknative$entries();

   @Nullable SuggestionWindow bedrocknative$window();

   void bedrocknative$rows(int var1);

   void bedrocknative$scroll(double var1);
}
