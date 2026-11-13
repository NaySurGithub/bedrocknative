package net.bedrocknative.client.net;

import com.mojang.blaze3d.platform.Window;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.command.SuggestionsView;
import net.bedrocknative.client.mixin.command.ChatScreenAccessor;
import net.bedrocknative.client.ui.JsonUiChatScreen;
import net.bedrocknative.ui.ChatData;
import net.bedrocknative.ui.SuggestionWindow;
import net.bedrocknative.ui.UiControl;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.KeyEvent;

final class DevChatScrollTest {
   private static final long STEP = 400L;
   private static final KeyEvent PAGE_DOWN = new KeyEvent(78, 0, 0);
   private static final KeyEvent PAGE_UP = new KeyEvent(75, 0, 0);
   private static final KeyEvent DOWN = new KeyEvent(81, 0, 0);
   private static final KeyEvent UP = new KeyEvent(82, 0, 0);
   private static final KeyEvent TAB = new KeyEvent(43, 0, 0);
   private final Minecraft minecraft;
   private final List<String> failures = new ArrayList<>();
   private int checks;

   DevChatScrollTest(Minecraft minecraft) {
      this.minecraft = minecraft;
   }

   void run() {
      Window window = this.minecraft.getWindow();
      int width = this.onClient(window::getScreenWidth);
      int height = this.onClient(window::getScreenHeight);
      int guiScale = this.onClient(() -> this.minecraft.options.guiScale().get());

      try {
         sleep(2000L);
         this.list("small", 854, 480, 0);
         this.list("scale4", 1920, 1080, 4);
         this.resizeWhileOpen();
         this.mentions();
      } catch (RuntimeException e) {
         this.failures.add("exception: " + e);
         BedrockNative.LOGGER.error("Dev commands-scroll test failed with an exception", e);
      } finally {
         this.run(() -> {
            this.minecraft.gui.setScreen(null);
            this.minecraft.options.guiScale().set(guiScale);
            window.setWindowed(width, height);
         });
      }

      log("{} of {} checks passed{}", this.checks - this.failures.size(), this.checks, this.failures.isEmpty() ? "" : "; failed: " + this.failures);
      log("done");
   }

   private void list(String name, int width, int height, int scale) {
      Window window = this.minecraft.getWindow();
      this.run(() -> {
         this.minecraft.gui.setScreen(null);
         window.setWindowed(width, height);
         this.minecraft.options.guiScale().set(scale);
      });

      for (int i = 0; i < 20 && this.onClient(window::getWidth) != width; i++) {
         sleep(250L);
      }

      sleep(1000L);
      this.openList("/");
      SuggestionWindow start = this.window();
      if (start == null) {
         this.check(name + ": the command list opens", false);
      } else {
         boolean bedrockChat = this.onClient(() -> this.minecraft.gui.screen() instanceof JsonUiChatScreen);
         int expected = this.expectedRows();
         log(
            "{}: window {}x{} GUI {} ({}): {} entries, {} rows (expected {}), first shown {}",
            name,
            this.onClient(window::getWidth),
            this.onClient(window::getHeight),
            this.onClient(window::getGuiScale),
            bedrockChat ? "Bedrock's chat" : "Java's chat",
            start.size(),
            start.rows(),
            expected,
            this.shown().subList(0, Math.min(3, this.shown().size()))
         );
         this.check(name + ": rows fill the free height (" + start.rows() + ", expected " + expected + ")", start.rows() == expected);
         this.check(name + ": at least 6 rows (" + start.rows() + ")", start.rows() >= 6);
         this.check(name + ": the list is longer than the window (" + start.size() + " > " + start.rows() + ")", start.size() > start.rows());
         this.check(name + ": every row shows (" + this.shown().size() + " of " + start.visible() + ")", this.shown().size() == start.visible());
         this.check(name + ": the first entry is chosen", start.selected() == 0 && start.offset() == 0);
         this.screenshot("chat-scroll-" + name + "-top");
         List<String> all = this.onClient(() -> this.view().bedrocknative$entries());

         for (int i = 0; i < 100 && this.window() != null && this.window().selected() < start.size() - 1; i++) {
            this.key(PAGE_DOWN);
         }

         SuggestionWindow bottom = this.window();
         this.check(
            name + ": PageDown reaches the last entry",
            bottom != null && bottom.selected() == start.size() - 1 && bottom.offset() == start.size() - start.rows()
         );
         List<String> rows = this.shown();
         this.check(
            name + ": the last row shown is the last entry (" + (rows.isEmpty() ? "none" : rows.getLast()) + ")",
            !rows.isEmpty() && !all.isEmpty() && rows.getLast().equals(all.getLast())
         );
         if (bedrockChat) {
            this.check(name + ": the chosen row shows its focus border", this.chosenHighlighted(bottom));
         }

         this.screenshot("chat-scroll-" + name + "-bottom");
         this.key(DOWN);
         SuggestionWindow wrapped = this.window();
         this.check(name + ": Down at the end wraps to the first", wrapped != null && wrapped.selected() == 0 && wrapped.offset() == 0);
         this.key(UP);
         SuggestionWindow back = this.window();
         this.check(
            name + ": Up at the start wraps to the last", back != null && back.selected() == start.size() - 1 && back.offset() == start.size() - start.rows()
         );
         this.run(() -> this.view().bedrocknative$scroll(1.0));
         sleep(400L);
         SuggestionWindow wheeled = this.window();
         this.check(
            name + ": the wheel moves the window up 3 rows, the chosen entry in sight",
            wheeled != null && wheeled.offset() == start.size() - start.rows() - 3 && wheeled.shows(wheeled.selected())
         );
         this.key(PAGE_UP);
         SuggestionWindow paged = this.window();
         this.check(
            name + ": PageUp jumps a window up",
            paged != null && wheeled != null && paged.selected() == Math.max(0, wheeled.selected() - start.rows()) && paged.shows(paged.selected())
         );
         String expectedText = paged == null ? "" : "/" + all.get(paged.selected());
         this.key(TAB);
         String typed = this.onClient(() -> this.input().getValue());
         this.check(name + ": Tab takes the chosen entry ('" + typed + "', expected '" + expectedText + "')", typed.equals(expectedText));
         this.run(() -> this.minecraft.gui.setScreen(null));
         sleep(400L);
      }
   }

   private void resizeWhileOpen() {
      Window window = this.minecraft.getWindow();
      this.run(() -> {
         window.setWindowed(1280, 720);
         this.minecraft.options.guiScale().set(2);
      });
      sleep(1500L);
      this.openList("/");

      for (int i = 0; i < 100 && this.window() != null && this.window().selected() < this.window().size() - 1; i++) {
         this.key(PAGE_DOWN);
      }

      SuggestionWindow before = this.window();
      this.run(() -> this.minecraft.options.guiScale().set(4));
      sleep(1200L);
      SuggestionWindow after = this.window();
      if (after == null) {
         this.run(() -> this.suggestions().showSuggestions(false));
         sleep(400L);
         after = this.window();
      }

      log(
         "resize while open: {} rows -> {} rows, chosen {} -> {}",
         before == null ? -1 : before.rows(),
         after == null ? -1 : after.rows(),
         before == null ? -1 : before.selected(),
         after == null ? -1 : after.selected()
      );
      int expected = this.expectedRows();
      this.check("resize while open: fewer rows at the bigger scale", before != null && after != null && after.rows() < before.rows());
      this.check(
         "resize while open: rows fill the new free height (" + (after == null ? -1 : after.rows()) + ", expected " + expected + ")",
         after != null && after.rows() == expected
      );
      SuggestionWindow shownAfter = after;
      this.check(
         "resize while open: the chosen entry in sight, every row shown",
         shownAfter != null && shownAfter.shows(shownAfter.selected()) && this.shown().size() == shownAfter.visible()
      );
      this.screenshot("chat-scroll-resized");
      this.run(() -> this.minecraft.gui.setScreen(null));
      sleep(400L);
   }

   private void mentions() {
      this.openList("hello @");
      SuggestionWindow window = this.window();
      this.check("mentions: the list opens on '@'", window != null);
      if (window != null) {
         log("mentions: {} entries {}, {} rows", window.size(), this.shown(), window.rows());
         this.check(
            "mentions: every entry shows (" + this.shown().size() + " of " + window.size() + ")", !window.scrolls() && this.shown().size() == window.size()
         );
      }

      this.screenshot("chat-scroll-mentions");
      this.run(() -> this.minecraft.gui.setScreen(null));
      sleep(400L);
   }

   private void openList(String text) {
      this.run(() -> this.minecraft.gui.setScreen(new ChatScreen("", false)));
      sleep(400L);
      this.run(() -> ((ChatScreen)this.minecraft.gui.screen()).insertText(text, true));
      sleep(400L);
      if (this.window() == null) {
         this.run(() -> this.suggestions().showSuggestions(false));
         sleep(400L);
      }
   }

   private int expectedRows() {
      return this.onClient(
         () -> this.minecraft.gui.screen() instanceof JsonUiChatScreen chat
            ? ChatData.autocompleteRows(chat.ui().instance())
            : SuggestionWindow.rows(this.minecraft.gui.screen().height - 17, 12.0)
      );
   }

   private boolean chosenHighlighted(SuggestionWindow window) {
      return window != null && this.onClient(() -> {
         if (!(this.minecraft.gui.screen() instanceof JsonUiChatScreen chat)) {
            return false;
         } else {
            UiControl button = ChatData.rowButton(chat.ui().instance(), window.selected() - window.offset());
            return button != null && button.state().equals("hover");
         }
      });
   }

   private void key(KeyEvent key) {
      this.run(() -> this.minecraft.gui.screen().keyPressed(key));
      sleep(150L);
   }

   private CommandSuggestions suggestions() {
      return ((ChatScreenAccessor)this.minecraft.gui.screen()).bedrocknative$commandSuggestions();
   }

   private SuggestionsView view() {
      return (SuggestionsView)this.suggestions();
   }

   private SuggestionWindow window() {
      return this.onClient(() -> this.minecraft.gui.screen() instanceof ChatScreen ? this.view().bedrocknative$window() : null);
   }

   private List<String> shown() {
      return this.onClient(() -> {
         if (this.minecraft.gui.screen() instanceof JsonUiChatScreen chat) {
            return chat.data().lines().stream().filter(ChatData.Line::suggestion).map(ChatData.Line::text).toList();
         } else {
            return this.minecraft.gui.screen() instanceof ChatScreen ? this.view().bedrocknative$shown() : List.of();
         }
      });
   }

   private EditBox input() {
      return (EditBox)this.minecraft.gui.screen().getFocused();
   }

   private void screenshot(String name) {
      sleep(300L);
      this.run(() -> Screenshot.grab(this.minecraft.gameDirectory, name + ".png", this.minecraft.gameRenderer.mainRenderTarget(), 1, message -> {}));
      sleep(400L);
   }

   private void check(String what, boolean ok) {
      this.checks++;
      if (ok) {
         log("PASS {}", what);
      } else {
         this.failures.add(what);
         BedrockNative.LOGGER.warn("Dev commands-scroll test FAIL {}", what);
      }
   }

   private <T> T onClient(Supplier<T> task) {
      return CompletableFuture.supplyAsync(task, this.minecraft::execute).join();
   }

   private void run(Runnable task) {
      this.onClient(() -> {
         task.run();
         return null;
      });
   }

   private static void log(String message, Object... args) {
      BedrockNative.LOGGER.info("Dev commands-scroll test: " + message, args);
   }

   private static void sleep(long millis) {
      try {
         Thread.sleep(millis);
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
      }
   }
}
