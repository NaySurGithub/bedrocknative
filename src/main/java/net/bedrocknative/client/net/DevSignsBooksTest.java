package net.bedrocknative.client.net;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.edit.BedrockBooks;
import net.bedrocknative.client.edit.BedrockSigns;
import net.bedrocknative.world.block.SignEdits;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.client.gui.screens.inventory.BookSignScreen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.gui.screens.inventory.HangingSignEditScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.commands.arguments.EntityAnchorArgument.Anchor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.block.entity.HangingSignBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.cloudburstmc.nbt.NbtMap;

final class DevSignsBooksTest {
   private static final int ARROW_DOWN = 81;
   private static final int PAGE_BACK = 1073741899;
   private static final int PAGE_FORWARD = 1073741902;
   private final Minecraft minecraft;
   private int passed;
   private int failed;

   DevSignsBooksTest(Minecraft minecraft) {
      this.minecraft = minecraft;
   }

   void run() {
      BlockPos base = this.onClient(() -> this.minecraft.player.blockPosition());
      this.commands(
         "gamemode creative",
         "time set noon",
         "weather clear",
         "clear @s",
         fill(base.offset(-4, -1, -3), base.offset(6, -1, 9), "stone"),
         fill(base.offset(-4, 0, -3), base.offset(6, 5, 9), "air"),
         setblock(base.offset(2, 2, 3), "stone")
      );
      this.commands("give @s oak_sign 2", "give @s oak_hanging_sign", "give @s honeycomb", "give @s glow_ink_sac", "give @s red_dye", "give @s writable_book 2");
      sleep(2000L);

      try {
         this.signs(base);
      } catch (RuntimeException e) {
         this.fail("signs", "exception " + e);
         BedrockNative.LOGGER.error("Dev signs-books test: signs part failed", e);
      }

      try {
         this.books();
      } catch (RuntimeException e) {
         this.fail("books", "exception " + e);
         BedrockNative.LOGGER.error("Dev signs-books test: books part failed", e);
      }

      this.run(() -> this.minecraft.gui.setScreen(null));
      this.commands(fill(base.offset(-4, 0, -3), base.offset(6, 5, 9), "air"), "kill @e[type=item]", "clear @s");
      log(
         "done {} passed, {} failed ({} OpenSign opened an editor, {} sign data packets and {} book packets sent)",
         this.passed,
         this.failed,
         BedrockSigns.openedCount(),
         BedrockSigns.sentCount(),
         BedrockBooks.sentCount()
      );
   }

   private void signs(BlockPos base) {
      BlockPos floor = base.offset(0, -1, 3);
      BlockPos signPos = base.offset(0, 0, 3);
      Vec3 front = Vec3.atBottomCenterOf(base);
      Vec3 behind = Vec3.atBottomCenterOf(base.offset(0, 0, 7));
      int opened = BedrockSigns.openedCount();
      this.useItemOn(Items.OAK_SIGN, floor, Direction.UP, Vec3.atCenterOf(floor).add(0.0, 0.5, 0.0));
      AbstractSignEditScreen editor = this.awaitScreen(AbstractSignEditScreen.class, 4000L);
      this.check(
         "oak sign: OpenSign opens the sign editor",
         editor != null && BedrockSigns.openedCount() == opened + 1,
         "screen " + this.onClient(() -> String.valueOf(this.minecraft.gui.screen())) + ", opened " + BedrockSigns.openedCount()
      );
      this.check("oak sign: front side", BedrockSigns.lastOpenedFront(), "front " + BedrockSigns.lastOpenedFront());
      this.check(
         "oak sign: standing sign block with a sign block entity",
         this.onClient(
            () -> this.minecraft.level.getBlockEntity(signPos) instanceof SignBlockEntity
               && !(this.minecraft.level.getBlockEntity(signPos) instanceof HangingSignBlockEntity)
         ),
         this.onClient(() -> String.valueOf(this.minecraft.level.getBlockState(signPos)))
      );
      this.typeLines("Hello", "World");
      sleep(300L);
      this.screenshot("signs-books-sign-editor");
      this.closeScreen();
      NbtMap sent = this.awaitSign(signPos, true, "Hello\nWorld", 4000L);
      this.check(
         "oak sign: front text kept by the server",
         sent != null && SignEdits.sideText(sent, true).equals("Hello\nWorld"),
         "server says " + (sent == null ? "nothing" : SignEdits.sideText(sent, true).replace("\n", "\\n"))
      );
      this.check(
         "oak sign: the server filled in the text owner",
         sent != null && !sent.getCompound("FrontText").getString("TextOwner", "").isEmpty(),
         "TextOwner " + (sent == null ? "?" : sent.getCompound("FrontText").getString("TextOwner", ""))
      );
      this.check(
         "oak sign: unlocked again",
         sent != null && sent.getLong("LockedForEditingBy", 0L) == -1L,
         "LockedForEditingBy " + (sent == null ? "?" : sent.getLong("LockedForEditingBy", 0L))
      );
      this.check(
         "oak sign: the Java block entity shows the lines",
         "Hello".equals(this.signLine(signPos, SignTextSlot.FRONT, 0)) && "World".equals(this.signLine(signPos, SignTextSlot.FRONT, 1)),
         this.signLine(signPos, SignTextSlot.FRONT, 0) + "/" + this.signLine(signPos, SignTextSlot.FRONT, 1)
      );
      sleep(500L);
      this.screenshot("signs-books-sign-front");
      this.teleport(behind, signPos);
      this.use(signPos, Direction.SOUTH);
      editor = this.awaitScreen(AbstractSignEditScreen.class, 4000L);
      this.check(
         "oak sign: used from behind opens the editor on the back",
         editor != null && !BedrockSigns.lastOpenedFront(),
         "screen " + this.onClient(() -> String.valueOf(this.minecraft.gui.screen())) + ", front " + BedrockSigns.lastOpenedFront()
      );
      this.typeLines("Back1", "Back2");
      this.closeScreen();
      sent = this.awaitSign(signPos, false, "Back1\nBack2", 4000L);
      this.check(
         "oak sign: back text kept by the server",
         sent != null && SignEdits.sideText(sent, false).equals("Back1\nBack2"),
         "server says " + (sent == null ? "nothing" : SignEdits.sideText(sent, false).replace("\n", "\\n"))
      );
      this.check(
         "oak sign: front text untouched by the back edit",
         sent != null && SignEdits.sideText(sent, true).equals("Hello\nWorld"),
         "front " + (sent == null ? "?" : SignEdits.sideText(sent, true).replace("\n", "\\n"))
      );
      sleep(500L);
      this.screenshot("signs-books-sign-back");
      BlockPos ceiling = base.offset(2, 2, 3);
      BlockPos hangingPos = ceiling.below();
      this.teleport(front.add(2.0, 0.0, 0.0), hangingPos);
      this.useItemOn(Items.OAK_HANGING_SIGN, ceiling, Direction.DOWN, Vec3.atBottomCenterOf(ceiling));
      Screen hanging = this.awaitScreen(HangingSignEditScreen.class, 4000L);
      this.check(
         "hanging sign: OpenSign opens the hanging sign editor", hanging != null, "screen " + this.onClient(() -> String.valueOf(this.minecraft.gui.screen()))
      );
      this.typeLines("Hang", "ing");
      sleep(300L);
      this.screenshot("signs-books-hanging-editor");
      this.closeScreen();
      sent = this.awaitSign(hangingPos, true, "Hang\ning", 4000L);
      this.check(
         "hanging sign: text kept by the server",
         sent != null && SignEdits.sideText(sent, true).equals("Hang\ning"),
         "server says " + (sent == null ? "nothing" : SignEdits.sideText(sent, true).replace("\n", "\\n"))
      );
      this.check(
         "hanging sign: block entity is a hanging sign and its data says HangingSign",
         this.onClient(() -> this.minecraft.level.getBlockEntity(hangingPos) instanceof HangingSignBlockEntity)
            && sent != null
            && "HangingSign".equals(sent.getString("id", "")),
         "id " + (sent == null ? "?" : sent.getString("id", ""))
      );
      sleep(500L);
      this.screenshot("signs-books-hanging-sign");
      this.teleport(front, signPos);
      this.useItemOn(Items.DYE.red(), signPos, Direction.NORTH, Vec3.atCenterOf(signPos).add(0.0, 0.0, -0.1));
      boolean red = this.await(
         () -> this.minecraft.level.getBlockEntity(signPos) instanceof SignBlockEntity sign && sign.getText(SignTextSlot.FRONT).getColor() == DyeColor.RED,
         4000L
      );
      this.check(
         "red dye: the server colours the front text (nearest dye to its SignTextColor)",
         red,
         "colour " + this.onClient(() -> this.signColor(signPos)) + ", server SignTextColor " + this.onClient(() -> this.signTextColorHex(signPos))
      );
      this.useItemOn(Items.GLOW_INK_SAC, signPos, Direction.NORTH, Vec3.atCenterOf(signPos).add(0.0, 0.0, -0.1));
      boolean glow = this.await(
         () -> this.minecraft.level.getBlockEntity(signPos) instanceof SignBlockEntity sign && sign.getText(SignTextSlot.FRONT).hasGlowingText(), 4000L
      );
      this.check("glow ink: the server makes the front text glow (IgnoreLighting)", glow, "glow " + glow);
      sleep(500L);
      this.screenshot("signs-books-sign-red-glow");
      this.useItemOn(Items.HONEYCOMB, signPos, Direction.NORTH, Vec3.atCenterOf(signPos).add(0.0, 0.0, -0.1));
      boolean waxed = this.await(() -> this.minecraft.level.getBlockEntity(signPos) instanceof SignBlockEntity sign && sign.isWaxed(), 4000L);
      this.check("honeycomb: the server waxes the sign (IsWaxed)", waxed, "waxed " + waxed);
      int before = BedrockSigns.openedCount();
      this.use(signPos, Direction.NORTH);
      boolean opens = this.awaitScreen(AbstractSignEditScreen.class, 2500L) != null;
      this.check("waxed sign: using it opens no editor", !opens && BedrockSigns.openedCount() == before, "editor opened " + opens);
      this.run(() -> this.minecraft.gui.setScreen(null));
   }

   private String signLine(BlockPos pos, SignTextSlot slot, int line) {
      return this.onClient(
         () -> this.minecraft.level.getBlockEntity(pos) instanceof SignBlockEntity sign ? sign.getText(slot).getMessages(false).get(line).getString() : "?"
      );
   }

   private String signColor(BlockPos pos) {
      return this.minecraft.level.getBlockEntity(pos) instanceof SignBlockEntity sign ? sign.getText(SignTextSlot.FRONT).getColor().getName() : "?";
   }

   private String signTextColorHex(BlockPos pos) {
      NbtMap last = BedrockSigns.last(this.minecraft.level, pos);
      return last == null ? "?" : String.format("%08X", last.getCompound("FrontText", NbtMap.EMPTY).getInt("SignTextColor", 0));
   }

   private NbtMap awaitSign(BlockPos pos, boolean front, String text, long millis) {
      long end = System.currentTimeMillis() + millis;
      NbtMap last = null;

      while (System.currentTimeMillis() < end) {
         last = this.onClient(() -> BedrockSigns.last(this.minecraft.level, pos));
         if (last != null && SignEdits.sideText(last, front).equals(text) && last.getLong("LockedForEditingBy", 0L) == -1L) {
            return last;
         }

         sleep(100L);
      }

      return last;
   }

   private void typeLines(String... lines) {
      for (int i = 0; i < lines.length; i++) {
         this.type(lines[i]);
         if (i < lines.length - 1) {
            this.run(() -> this.minecraft.gui.screen().keyPressed(new KeyEvent(81, 0, 0)));
         }
      }
   }

   private void type(String text) {
      for (char c : text.toCharArray()) {
         this.run(() -> this.minecraft.gui.screen().charTyped(new CharacterEvent(c)));
         sleep(15L);
      }
   }

   private void closeScreen() {
      this.run(() -> {
         if (this.minecraft.gui.screen() != null) {
            this.minecraft.gui.screen().onClose();
         }
      });
      sleep(300L);
   }

   private void books() {
      this.select(Items.WRITABLE_BOOK);
      this.run(() -> this.minecraft.gameMode.useItem(this.minecraft.player, InteractionHand.MAIN_HAND));
      BookEditScreen editor = this.awaitScreen(BookEditScreen.class, 4000L);
      this.check("book and quill: using it opens the editor", editor != null, "screen " + this.onClient(() -> String.valueOf(this.minecraft.gui.screen())));
      if (editor != null) {
         int sentBefore = BedrockBooks.sentCount();
         this.type("page one");
         this.key(1073741902);
         this.type("page two");
         this.key(1073741902);
         this.type("page three");
         this.key(1073741899);
         sleep(300L);
         this.screenshot("signs-books-book-editor");
         this.check(
            "book editor: the extra page buttons are there",
            this.findButton(BookEditScreen.class, "bedrocknative.book.delete_page") != null
               && this.findButton(BookEditScreen.class, "bedrocknative.book.insert_page") != null
               && this.findButton(BookEditScreen.class, "bedrocknative.book.move_back") != null
               && this.findButton(BookEditScreen.class, "bedrocknative.book.move_forward") != null,
            "buttons"
         );
         this.press(this.findButton(BookEditScreen.class, "bedrocknative.book.delete_page"));
         sleep(300L);
         this.press(this.findButton(BookEditScreen.class, "book.signButton"));
         BookSignScreen signScreen = this.awaitScreen(BookSignScreen.class, 3000L);
         this.check(
            "book editor: Sign opens the signing screen", signScreen != null, "screen " + this.onClient(() -> String.valueOf(this.minecraft.gui.screen()))
         );
         if (signScreen != null) {
            this.type("Test Book");
            sleep(300L);
            this.screenshot("signs-books-book-sign");
            this.press(this.findButton(BookSignScreen.class, "book.finalizeButton"));
            boolean written = this.await(() -> this.minecraft.player.getMainHandItem().is(Items.WRITTEN_BOOK), 5000L);
            this.check(
               "signed book: the server turned the held item into a written book",
               written,
               "held " + this.onClient(() -> String.valueOf(this.minecraft.player.getMainHandItem()))
            );
            WrittenBookContent content = this.onClient(() -> this.minecraft.player.getMainHandItem().get(DataComponents.WRITTEN_BOOK_CONTENT));
            this.check(
               "signed book: title, author and the two pages left after the delete",
               content != null
                  && "Test Book".equals(content.title().raw())
                  && List.of("page one", "page three").equals(content.getPages(false).stream().map(Component::getString).toList()),
               content == null
                  ? "no content"
                  : content.title().raw() + " by " + content.author() + " " + content.getPages(false).stream().map(Component::getString).toList()
            );
            this.check(
               "signed book: author is the player's name",
               content != null && this.minecraft.player.getPlainTextName().equals(content.author()),
               content == null ? "no content" : content.author() + " vs " + this.minecraft.player.getPlainTextName()
            );
            log("book packets sent for the first book: {}", BedrockBooks.sentCount() - sentBefore);
            this.run(() -> this.minecraft.gameMode.useItem(this.minecraft.player, InteractionHand.MAIN_HAND));
            Screen reader = this.awaitScreen(BookViewScreen.class, 3000L);
            this.check("written book: using it opens the reader", reader != null, "screen " + this.onClient(() -> String.valueOf(this.minecraft.gui.screen())));
            sleep(500L);
            this.screenshot("signs-books-book-view");
            this.run(() -> this.minecraft.gui.setScreen(null));
            sleep(500L);
            this.select(Items.WRITABLE_BOOK);
            this.run(() -> this.minecraft.gameMode.useItem(this.minecraft.player, InteractionHand.MAIN_HAND));
            editor = this.awaitScreen(BookEditScreen.class, 4000L);
            this.check("second book: editor opens", editor != null, "screen " + this.onClient(() -> String.valueOf(this.minecraft.gui.screen())));
            if (editor != null) {
               this.type("A");
               this.key(1073741902);
               this.type("B");
               this.press(this.findButton(BookEditScreen.class, "bedrocknative.book.move_back"));
               sleep(200L);
               this.press(this.findButton(BookEditScreen.class, "bedrocknative.book.insert_page"));
               sleep(200L);
               this.type("C");
               sleep(300L);
               this.screenshot("signs-books-book-edited");
               this.press(this.findButton(BookEditScreen.class, "gui.done"));
               sleep(1500L);
               this.run(() -> this.minecraft.gameMode.dropItem(this.minecraft.player, true));
               ItemStack dropped = this.awaitDroppedBook(Items.WRITABLE_BOOK, 5000L);
               WritableBookContent pages = dropped == null ? null : dropped.get(DataComponents.WRITABLE_BOOK_CONTENT);
               List<String> text = pages == null ? List.of() : pages.getPages(false).toList();
               this.check("second book: the server has C, B, A (insert, swap and replace)", List.of("C", "B", "A").equals(text), "dropped pages " + text);
            }
         }
      }
   }

   private ItemStack awaitDroppedBook(Item item, long millis) {
      long end = System.currentTimeMillis() + millis;

      while (System.currentTimeMillis() < end) {
         ItemStack found = this.onClient(() -> {
            for (Entity entity : this.minecraft.level.entitiesForRendering()) {
               if (entity instanceof ItemEntity drop && drop.getItem().is(item)) {
                  return drop.getItem();
               }
            }

            return null;
         });
         if (found != null) {
            return found;
         }

         sleep(150L);
      }

      return null;
   }

   private void key(int shortcut) {
      this.run(() -> this.minecraft.gui.screen().keyPressed(new KeyEvent(0, shortcut, 0)));
      sleep(200L);
   }

   private Button findButton(Class<? extends Screen> type, String translationKey) {
      return this.onClient(
         () -> {
            Screen screen = this.minecraft.gui.screen();
            if (screen != null && type.isInstance(screen)) {
               for (GuiEventListener child : screen.children()) {
                  if (child instanceof Button button
                     && button.getMessage().getContents() instanceof TranslatableContents contents
                     && contents.getKey().equals(translationKey)) {
                     return button;
                  }
               }

               return null;
            } else {
               return null;
            }
         }
      );
   }

   private void press(Button button) {
      if (button == null) {
         this.fail("button", "a button was not found on " + this.onClient(() -> String.valueOf(this.minecraft.gui.screen())));
      } else {
         this.run(() -> button.onPress(new KeyEvent(257, 0, 0)));
         sleep(300L);
      }
   }

   private void select(Item item) {
      this.run(() -> {
         Inventory inventory = this.minecraft.player.getInventory();

         for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
            if (inventory.getItem(slot).is(item)) {
               inventory.setSelectedSlot(slot);
               return;
            }
         }

         BedrockNative.LOGGER.warn("Dev signs-books test: {} is not in the hotbar", item);
      });
      sleep(700L);
   }

   private void useItemOn(Item item, BlockPos pos, Direction face, Vec3 hit) {
      this.select(item);
      this.run(() -> this.minecraft.player.lookAt(Anchor.EYES, hit));
      sleep(400L);
      this.run(
         () -> {
            InteractionResult result = this.minecraft
               .gameMode
               .useItemOn(this.minecraft.player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, face, pos, false));
            if (result.consumesAction()) {
               this.minecraft.player.swing(InteractionHand.MAIN_HAND, this.minecraft.player.getMainHandItem().getInteractAnimation(), false);
            }

            log("{} on {} {}: {}", item, pos, face, result);
         }
      );
   }

   private void use(BlockPos pos, Direction face) {
      this.run(() -> this.minecraft.player.getInventory().setSelectedSlot(8));
      sleep(700L);
      Vec3 hit = Vec3.atCenterOf(pos);
      this.run(() -> this.minecraft.player.lookAt(Anchor.EYES, hit));
      sleep(400L);
      this.run(
         () -> {
            InteractionResult result = this.minecraft
               .gameMode
               .useItemOn(this.minecraft.player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, face, pos, false));
            if (result.consumesAction()) {
               this.minecraft.player.swing(InteractionHand.MAIN_HAND, this.minecraft.player.getMainHandItem().getInteractAnimation(), false);
            }

            log("empty hand on {} {}: {}", pos, face, result);
         }
      );
   }

   private void teleport(Vec3 to, BlockPos facing) {
      this.commands(
         String.format(
            Locale.ROOT, "tp @s %.2f %.2f %.2f facing %.1f %.1f %.1f", to.x, to.y, to.z, facing.getX() + 0.5, facing.getY() + 0.5, facing.getZ() + 0.5
         )
      );
      sleep(1000L);
   }

   private <T extends Screen> T awaitScreen(Class<T> type, long millis) {
      long end = System.currentTimeMillis() + millis;

      while (System.currentTimeMillis() < end) {
         Screen screen = this.onClient(() -> this.minecraft.gui.screen());
         if (type.isInstance(screen)) {
            return type.cast(screen);
         }

         sleep(100L);
      }

      return null;
   }

   private boolean await(Supplier<Boolean> condition, long millis) {
      long end = System.currentTimeMillis() + millis;

      while (System.currentTimeMillis() < end) {
         if (this.onClient(condition)) {
            return true;
         }

         sleep(100L);
      }

      return false;
   }

   private void check(String name, boolean ok, String detail) {
      if (ok) {
         this.passed++;
         BedrockNative.LOGGER.info("Dev signs-books test PASS {}: {}", name, detail);
      } else {
         this.fail(name, detail);
      }
   }

   private void fail(String name, String detail) {
      this.failed++;
      BedrockNative.LOGGER.info("Dev signs-books test FAIL {}: {}", name, detail);
   }

   private static String setblock(BlockPos pos, String block) {
      return "setblock " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + block;
   }

   private static String fill(BlockPos from, BlockPos to, String block) {
      return "fill " + from.getX() + " " + from.getY() + " " + from.getZ() + " " + to.getX() + " " + to.getY() + " " + to.getZ() + " " + block;
   }

   private void commands(String... commands) {
      for (String command : commands) {
         this.run(() -> this.minecraft.getConnection().sendCommand(command));
         sleep(400L);
      }

      sleep(600L);
   }

   private void screenshot(String name) {
      this.run(() -> Screenshot.grab(this.minecraft.gameDirectory, name + ".png", this.minecraft.gameRenderer.mainRenderTarget(), 1, message -> {}));
      sleep(500L);
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
      BedrockNative.LOGGER.info("Dev signs-books test: " + message, args);
   }

   private static void sleep(long millis) {
      try {
         Thread.sleep(millis);
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
      }
   }
}
