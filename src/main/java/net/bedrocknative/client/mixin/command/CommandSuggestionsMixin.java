package net.bedrocknative.client.mixin.command;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.brigadier.context.SuggestionContext;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.bedrocknative.client.command.BedrockCommands;
import net.bedrocknative.client.command.Mentions;
import net.bedrocknative.client.command.SuggestionsView;
import net.bedrocknative.ui.SuggestionWindow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.CommandSuggestions.SuggestionsList;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({CommandSuggestions.class})
public abstract class CommandSuggestionsMixin implements SuggestionsView {
   @Unique
   private static final int LIST_BOTTOM_FROM_SCREEN_BOTTOM = 15;
   @Unique
   private static final int TOP_GAP = 2;
   @Shadow
   @Final
   private Minecraft minecraft;
   @Shadow
   @Final
   private EditBox input;
   @Shadow
   @Nullable
   private CompletableFuture<Suggestions> pendingSuggestions;
   @Shadow
   private SuggestionsList suggestions;
   @Shadow
   private boolean allowSuggestions;
   @Shadow
   private boolean currentParseIsCommand;
   @Shadow
   @Final
   private Screen screen;
   @Shadow
   @Final
   private boolean anchorToBottom;
   @Mutable
   @Shadow
   @Final
   private int suggestionLineLimit;
   @Shadow
   @Final
   private List<FormattedCharSequence> commandUsage;
   @Shadow
   private int commandUsagePosition;
   @Unique
   private int bedrocknative$javaLimit = -1;
   @Unique
   private int bedrocknative$hostRows;

   @Shadow
   public abstract void showSuggestions(boolean var1);

   @Inject(
      method = {"<init>"},
      at = {@At("RETURN")}
   )
   private void bedrocknative$keepJavaLimit(CallbackInfo ci) {
      this.bedrocknative$javaLimit = this.suggestionLineLimit;
   }

   @Unique
   private boolean bedrocknative$scrolling() {
      return BedrockCommands.active() != null && this.anchorToBottom && this.screen instanceof ChatScreen;
   }

   @Unique
   private int bedrocknative$rowLimit() {
      if (!this.bedrocknative$scrolling()) {
         return this.bedrocknative$javaLimit > 0 ? this.bedrocknative$javaLimit : this.suggestionLineLimit;
      } else {
         return this.bedrocknative$hostRows > 0 ? this.bedrocknative$hostRows : SuggestionWindow.rows(this.screen.height - 15 - 2, 12.0);
      }
   }

   @Unique
   private void bedrocknative$applyRows() {
      int rows = this.bedrocknative$rowLimit();
      if (rows != this.suggestionLineLimit) {
         this.suggestionLineLimit = rows;
         if (this.suggestions != null) {
            SuggestionsListAccessor list = (SuggestionsListAccessor)this.suggestions;
            SuggestionWindow window = this.bedrocknative$window();
            if (window != null) {
               window.resize(rows);
               list.bedrocknative$offset(window.offset());
               Rect2i rect = list.bedrocknative$rect();
               int bottom = rect.getY() + rect.getHeight();
               int height = window.visible() * 12;
               if (this.anchorToBottom) {
                  rect.setY(bottom - height);
               }

               rect.setHeight(height);
            }
         }
      }
   }

   @Unique
   private void bedrocknative$applyWindow(SuggestionWindow window) {
      if (this.suggestions != null) {
         SuggestionsListAccessor list = (SuggestionsListAccessor)this.suggestions;
         if (window.selected() != list.bedrocknative$current()) {
            this.suggestions.select(window.selected());
         }

         list.bedrocknative$offset(window.offset());
         list.bedrocknative$tabCycles(false);
      }
   }

   @Inject(
      method = {"showSuggestions"},
      at = {@At("HEAD")}
   )
   private void bedrocknative$rowsBeforeList(boolean immediateNarration, CallbackInfo ci) {
      this.bedrocknative$applyRows();
   }

   @Inject(
      method = {"keyPressed"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void bedrocknative$pageKeys(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
      if (this.suggestions != null && this.bedrocknative$scrolling() && (event.key() == 75 || event.key() == 78)) {
         this.bedrocknative$applyRows();
         SuggestionWindow window = this.bedrocknative$window();
         if (window != null) {
            window.page(event.key() == 75 ? -1 : 1);
            this.bedrocknative$applyWindow(window);
         }

         cir.setReturnValue(true);
      }
   }

   @Inject(
      method = {"mouseScrolled"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void bedrocknative$wheel(double scroll, CallbackInfoReturnable<Boolean> cir) {
      if (this.suggestions != null && this.bedrocknative$scrolling()) {
         int x = (int)this.minecraft.mouseHandler.getScaledXPos(this.minecraft.getWindow());
         int y = (int)this.minecraft.mouseHandler.getScaledYPos(this.minecraft.getWindow());
         if (((SuggestionsListAccessor)this.suggestions).bedrocknative$rect().contains(x, y)) {
            this.bedrocknative$scroll(scroll);
            cir.setReturnValue(true);
         }
      }
   }

   @Override
   public List<String> bedrocknative$entries() {
      return this.suggestions == null
         ? List.of()
         : ((SuggestionsListAccessor)this.suggestions).bedrocknative$suggestionList().stream().<String>map(Suggestion::getText).toList();
   }

   @Nullable
   @Override
   public SuggestionWindow bedrocknative$window() {
      if (this.suggestions == null) {
         return null;
      } else {
         SuggestionsListAccessor list = (SuggestionsListAccessor)this.suggestions;
         return new SuggestionWindow(
            list.bedrocknative$suggestionList().size(), this.suggestionLineLimit, list.bedrocknative$offset(), list.bedrocknative$current()
         );
      }
   }

   @Override
   public void bedrocknative$rows(int rows) {
      this.bedrocknative$hostRows = Math.max(0, rows);
      this.bedrocknative$applyRows();
   }

   @Override
   public void bedrocknative$scroll(double notches) {
      SuggestionWindow window = this.bedrocknative$window();
      if (window != null && notches != 0.0) {
         window.scroll(SuggestionWindow.wheelLines(notches));
         this.bedrocknative$applyWindow(window);
      }
   }

   @Override
   public boolean bedrocknative$listShown() {
      return this.suggestions != null;
   }

   @Override
   public List<String> bedrocknative$suggestions() {
      return this.pendingSuggestions != null && this.pendingSuggestions.isDone()
         ? this.pendingSuggestions.join().getList().stream().<String>map(Suggestion::getText).toList()
         : List.of();
   }

   @Override
   public List<String> bedrocknative$shown() {
      if (this.suggestions == null) {
         return List.of();
      } else {
         SuggestionsListAccessor list = (SuggestionsListAccessor)this.suggestions;
         List<Suggestion> all = list.bedrocknative$suggestionList();
         int from = Math.max(0, Math.min(list.bedrocknative$offset(), all.size()));
         return all.subList(from, Math.min(all.size(), from + this.suggestionLineLimit)).stream().<String>map(Suggestion::getText).toList();
      }
   }

   @Override
   public void bedrocknative$use(int shownIndex) {
      if (this.suggestions != null) {
         int index = ((SuggestionsListAccessor)this.suggestions).bedrocknative$offset() + shownIndex;
         this.suggestions.select(index);
         this.suggestions.useSuggestion();
      }
   }

   @Override
   public List<String> bedrocknative$usage() {
      return this.commandUsage.stream().map(line -> {
         StringBuilder text = new StringBuilder();
         line.accept((index, style, codepoint) -> {
            text.appendCodePoint(codepoint);
            return true;
         });
         return text.toString();
      }).toList();
   }

   @Inject(
      method = {"fillNodeUsage"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void bedrocknative$bedrockUsage(
      SuggestionContext<ClientSuggestionProvider> context, Style format, CallbackInfoReturnable<List<FormattedCharSequence>> cir
   ) {
      BedrockCommands commands = BedrockCommands.active();
      if (commands != null) {
         String typed = this.input.getValue().startsWith("/") ? this.input.getValue().substring(1) : this.input.getValue();
         List<String> lines = commands.usage(context.parent, typed.split(" ", 2)[0]);
         cir.setReturnValue(lines.stream().map(line -> FormattedCharSequence.forward(line, format)).toList());
      }
   }

   @Inject(
      method = {"updateUsageInfo"},
      at = {@At("TAIL")}
   )
   private void bedrocknative$usageAtLeft(CallbackInfo ci) {
      if (BedrockCommands.active() != null) {
         this.commandUsagePosition = 0;
      }
   }

   @WrapOperation(
      method = {"updateCommandInfo"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/commands/SharedSuggestionProvider;suggest(Ljava/lang/Iterable;Lcom/mojang/brigadier/suggestion/SuggestionsBuilder;)Ljava/util/concurrent/CompletableFuture;"
      )}
   )
   private CompletableFuture<Suggestions> bedrocknative$mentions(
      Iterable<String> names, SuggestionsBuilder builder, Operation<CompletableFuture<Suggestions>> original
   ) {
      return BedrockCommands.active() != null && this.minecraft.player != null
         ? Mentions.suggest(builder, this.minecraft.player.connection.getSuggestionsProvider().getOnlinePlayerNames())
         : (CompletableFuture)original.call(new Object[]{names, builder});
   }

   @Inject(
      method = {"updateCommandInfo"},
      at = {@At("TAIL")}
   )
   private void bedrocknative$showMentions(CallbackInfo ci) {
      if (BedrockCommands.active() != null
         && !this.currentParseIsCommand
         && this.suggestions == null
         && this.allowSuggestions
         && this.pendingSuggestions != null
         && this.pendingSuggestions.isDone()
         && !this.pendingSuggestions.join().isEmpty()
         && (Boolean)this.minecraft.options.autoSuggestions().get()) {
         this.showSuggestions(false);
      }
   }
}
