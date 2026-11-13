package net.bedrocknative.client.net;

import java.lang.reflect.Field;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.inventory.BedrockLecterns;
import net.bedrocknative.client.world.BedrockDecoratedPots;
import net.bedrocknative.client.world.BedrockJigsawBlocks;
import net.bedrocknative.client.world.BedrockJukeboxDiscs;
import net.bedrocknative.world.block.JigsawEdits;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.client.gui.screens.inventory.BookSignScreen;
import net.minecraft.client.gui.screens.inventory.JigsawBlockEditScreen;
import net.minecraft.client.gui.screens.inventory.LecternScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.commands.arguments.EntityAnchorArgument.Anchor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.LecternMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity;
import net.minecraft.world.level.block.entity.JigsawBlockEntity;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity.WobbleStyle;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

final class DevContainers2Test {
   private static final int PAGE_FORWARD = 1073741902;
   private final Minecraft minecraft;
   private int passed;
   private int failed;

   DevContainers2Test(Minecraft minecraft) {
      this.minecraft = minecraft;
   }

   void run() {
      BlockPos base = this.onClient(() -> this.minecraft.player.blockPosition());
      this.commands(
         "gamemode creative",
         "time set noon",
         "weather clear",
         "clear @s",
         "kill @e[type=item]",
         fill(base.offset(-6, -1, -4), base.offset(8, -1, 8), "stone"),
         fill(base.offset(-6, 0, -4), base.offset(8, 5, 8), "air")
      );
      sleep(1500L);
      String[] parts = System.getProperty("bedrocknative.devTest", "containers2").split("-", 2);
      String only = parts.length > 1 ? parts[1] : "";
      this.runPart(only, "lectern", () -> this.lectern(base));
      this.runPart(only, "pot", () -> this.pot(base));
      this.runPart(only, "jukebox", () -> this.jukebox(base));
      this.runPart(only, "jigsaw", () -> this.jigsaw(base));
      this.run(() -> this.minecraft.gui.setScreen(null));
      this.commands("gamemode creative", fill(base.offset(-6, 0, -4), base.offset(8, 5, 8), "air"), "kill @e[type=item]", "clear @s");
      log(
         "done {} passed, {} failed ({} LecternUpdate sent, {} pot wobbles, {} jigsaw updates sent)",
         this.passed,
         this.failed,
         BedrockLecterns.updatesSent(),
         BedrockDecoratedPots.wobbleCount(),
         BedrockJigsawBlocks.sentCount()
      );
   }

   private void runPart(String only, String name, Runnable part) {
      if (only.isEmpty() || only.equals(name)) {
         try {
            part.run();
         } catch (RuntimeException e) {
            this.fail(name, "exception " + e);
            BedrockNative.LOGGER.error("Dev containers2 test: {} part failed", name, e);
         }

         this.run(() -> this.minecraft.gui.setScreen(null));
         sleep(500L);
      }
   }

   private void lectern(BlockPos base) {
      BlockPos pos = base.offset(2, 0, 2);
      this.commands("give @s writable_book");
      if (this.writeBook()) {
         this.commands("setblock " + at(pos) + " lectern");
         sleep(800L);
         this.check("lectern: no book yet", !BedrockLecterns.hasBook(pos), "has book " + BedrockLecterns.hasBook(pos));
         this.useItemOn(Items.WRITTEN_BOOK, pos, Direction.UP, Vec3.atCenterOf(pos).add(0.0, 0.4, 0.0));
         boolean placed = this.await(() -> BedrockLecterns.hasBook(pos), 5000L);
         this.check("lectern: the book put on it arrives in the block entity data", placed, "has book " + BedrockLecterns.hasBook(pos));
         this.check(
            "lectern: the block shows the book",
            this.onClient(() -> this.minecraft.level.getBlockState(pos).getValue(BlockStateProperties.HAS_BOOK)),
            this.onClient(() -> String.valueOf(this.minecraft.level.getBlockState(pos)))
         );
         this.check(
            "lectern: creative keeps the book in the hand (the server consumes nothing in creative)",
            this.onClient(() -> this.hasInInventory(Items.WRITTEN_BOOK)),
            "inventory book " + this.onClient(() -> this.hasInInventory(Items.WRITTEN_BOOK))
         );
         if (placed) {
            this.use(pos, Direction.UP);
            LecternScreen screen = this.awaitScreen(LecternScreen.class, 5000L);
            this.check("lectern: using it opens the reader", screen != null, "screen " + this.onClient(() -> String.valueOf(this.minecraft.gui.screen())));
            if (screen != null) {
               LecternMenu menu = this.onClient(screen::getMenu);
               WrittenBookContent content = this.onClient(() -> menu.getBook().get(DataComponents.WRITTEN_BOOK_CONTENT));
               this.check(
                  "lectern: the reader holds the written book with its four pages",
                  content != null && content.pages().size() == 4 && "Dev Lectern".equals(content.title().raw()),
                  content == null
                     ? "book " + this.onClient(() -> String.valueOf(menu.getBook()))
                     : content.title().raw() + ", " + content.pages().size() + " pages"
               );
               this.check("lectern: it starts on the first page", this.onClient(menu::getPage) == 0, "page " + this.onClient(menu::getPage));
               sleep(500L);
               this.screenshot("containers2-lectern-page-1");
               int sent = BedrockLecterns.updatesSent();
               this.turn(menu, 2);
               this.check(
                  "lectern: forward moves one Java page and sends nothing inside a spread",
                  this.onClient(menu::getPage) == 1 && BedrockLecterns.updatesSent() == sent,
                  "page " + this.onClient(menu::getPage) + ", updates " + (BedrockLecterns.updatesSent() - sent)
               );
               this.turn(menu, 2);
               this.check(
                  "lectern: the next spread sends one LecternUpdate",
                  this.onClient(menu::getPage) == 2 && BedrockLecterns.updatesSent() == sent + 1,
                  "page " + this.onClient(menu::getPage) + ", updates " + (BedrockLecterns.updatesSent() - sent)
               );
               boolean echoed = this.await(() -> BedrockLecterns.serverSpread(pos) == 1, 4000L);
               this.check("lectern: the server shows spread 1 in its block entity data", echoed, "server spread " + BedrockLecterns.serverSpread(pos));
               sleep(300L);
               this.screenshot("containers2-lectern-page-3");
               this.turn(menu, 2);
               this.turn(menu, 2);
               this.turn(menu, 2);
               this.check("lectern: forward stops on the last page", this.onClient(menu::getPage) == 3, "page " + this.onClient(menu::getPage));
               this.turn(menu, 1);
               this.turn(menu, 1);
               this.turn(menu, 1);
               this.check(
                  "lectern: back returns to the first spread (a second LecternUpdate)",
                  this.onClient(menu::getPage) == 0 && BedrockLecterns.updatesSent() >= sent + 2,
                  "page " + this.onClient(menu::getPage) + ", updates " + (BedrockLecterns.updatesSent() - sent)
               );
               this.check(
                  "lectern: the server follows back to spread 0",
                  this.await(() -> BedrockLecterns.serverSpread(pos) == 0, 4000L),
                  "server spread " + BedrockLecterns.serverSpread(pos)
               );
               int beforeClose = BedrockLecterns.updatesSent();
               this.run(() -> this.minecraft.gui.screen().onClose());
               sleep(500L);
               this.check(
                  "lectern: Done closes the reader and says the spread once more",
                  this.onClient(() -> this.minecraft.gui.screen()) == null && BedrockLecterns.updatesSent() == beforeClose + 1,
                  "screen " + this.onClient(() -> String.valueOf(this.minecraft.gui.screen())) + ", updates " + (BedrockLecterns.updatesSent() - beforeClose)
               );
               this.use(pos, Direction.UP);
               screen = this.awaitScreen(LecternScreen.class, 5000L);
               this.check("lectern: it opens again", screen != null, "screen " + this.onClient(() -> String.valueOf(this.minecraft.gui.screen())));
               if (screen != null) {
                  LecternMenu again = this.onClient(screen::getMenu);
                  this.run(() -> this.minecraft.gameMode.handleInventoryButtonClick(again.containerId, 3));
                  this.check(
                     "lectern: Take Book (creative) empties the lectern on the server",
                     this.await(() -> !BedrockLecterns.hasBook(pos), 5000L),
                     "has book " + BedrockLecterns.hasBook(pos)
                  );
                  this.check(
                     "lectern: the reader closed",
                     this.await(() -> this.minecraft.gui.screen() == null, 2000L),
                     "screen " + this.onClient(() -> String.valueOf(this.minecraft.gui.screen()))
                  );
                  this.check(
                     "lectern: the block is still a lectern without the book",
                     this.onClient(
                        () -> this.minecraft.level.getBlockState(pos).is(Blocks.LECTERN)
                           && !this.minecraft.level.getBlockState(pos).getValue(BlockStateProperties.HAS_BOOK)
                     ),
                     this.onClient(() -> String.valueOf(this.minecraft.level.getBlockState(pos)))
                  );
                  boolean back = this.await(() -> this.hasInInventory(Items.WRITTEN_BOOK) || this.droppedItem(Items.WRITTEN_BOOK) != null, 5000L);
                  this.check(
                     "lectern: the server gave the book back (picked up or dropped)",
                     back,
                     "inventory "
                        + this.onClient(() -> this.hasInInventory(Items.WRITTEN_BOOK))
                        + ", dropped "
                        + (this.onClient(() -> this.droppedItem(Items.WRITTEN_BOOK)) != null)
                  );
                  this.screenshot("containers2-lectern-taken");
                  if (!this.await(() -> this.hasInInventory(Items.WRITTEN_BOOK), 5000L)) {
                     log("the book was not picked up; no survival take");
                  } else {
                     this.commands("gamemode survival");
                     sleep(800L);
                     this.useItemOn(Items.WRITTEN_BOOK, pos, Direction.UP, Vec3.atCenterOf(pos).add(0.0, 0.4, 0.0));
                     this.check(
                        "lectern (survival): the book is on it again",
                        this.await(() -> BedrockLecterns.hasBook(pos), 5000L),
                        "has book " + BedrockLecterns.hasBook(pos)
                     );
                     this.check(
                        "lectern (survival): the written book left the hand",
                        this.await(() -> !this.hasInInventory(Items.WRITTEN_BOOK), 3000L),
                        "inventory book " + this.onClient(() -> this.hasInInventory(Items.WRITTEN_BOOK))
                     );
                     this.use(pos, Direction.UP);
                     screen = this.awaitScreen(LecternScreen.class, 5000L);
                     this.check(
                        "lectern (survival): the reader opens", screen != null, "screen " + this.onClient(() -> String.valueOf(this.minecraft.gui.screen()))
                     );
                     if (screen != null) {
                        LecternMenu inSurvival = this.onClient(screen::getMenu);
                        this.run(() -> this.minecraft.gameMode.handleInventoryButtonClick(inSurvival.containerId, 3));
                        this.check(
                           "lectern (survival): Take Book empties it on the server",
                           this.await(() -> !BedrockLecterns.hasBook(pos), 5000L),
                           "has book " + BedrockLecterns.hasBook(pos)
                        );
                        this.check(
                           "lectern (survival): the lectern was not broken",
                           this.onClient(() -> this.minecraft.level.getBlockState(pos).is(Blocks.LECTERN)),
                           this.onClient(() -> String.valueOf(this.minecraft.level.getBlockState(pos)))
                        );
                        this.check(
                           "lectern (survival): the book came back",
                           this.await(() -> this.hasInInventory(Items.WRITTEN_BOOK) || this.droppedItem(Items.WRITTEN_BOOK) != null, 5000L),
                           "inventory " + this.onClient(() -> this.hasInInventory(Items.WRITTEN_BOOK))
                        );
                        this.commands("gamemode creative");
                     }
                  }
               }
            }
         }
      }
   }

   private boolean writeBook() {
      this.select(Items.WRITABLE_BOOK);
      this.run(() -> this.minecraft.gameMode.useItem(this.minecraft.player, InteractionHand.MAIN_HAND));
      BookEditScreen editor = this.awaitScreen(BookEditScreen.class, 4000L);
      this.check("lectern: the book and quill opens its editor", editor != null, "screen " + this.onClient(() -> String.valueOf(this.minecraft.gui.screen())));
      if (editor == null) {
         return false;
      } else {
         this.type("page one");
         this.key(1073741902);
         this.type("page two");
         this.key(1073741902);
         this.type("page three");
         this.key(1073741902);
         this.type("page four");
         sleep(300L);
         this.press(this.findButton(BookEditScreen.class, "book.signButton"));
         BookSignScreen signScreen = this.awaitScreen(BookSignScreen.class, 3000L);
         if (signScreen == null) {
            this.fail("lectern: the signing screen", "screen " + this.onClient(() -> String.valueOf(this.minecraft.gui.screen())));
            return false;
         } else {
            this.type("Dev Lectern");
            sleep(300L);
            this.press(this.findButton(BookSignScreen.class, "book.finalizeButton"));
            boolean written = this.await(() -> this.minecraft.player.getMainHandItem().is(Items.WRITTEN_BOOK), 5000L);
            this.check(
               "lectern: the signed book is a written book", written, "held " + this.onClient(() -> String.valueOf(this.minecraft.player.getMainHandItem()))
            );
            return written;
         }
      }
   }

   private void turn(LecternMenu menu, int button) {
      this.run(() -> this.minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button));
      sleep(350L);
   }

   private void pot(BlockPos base) {
      BlockPos pos = base.offset(-2, 0, 2);
      this.commands("setblock " + at(pos) + " decorated_pot", "give @s diamond 5", "give @s cobblestone 2", "gamemode survival");
      sleep(800L);
      this.check(
         "pot: the block has a pot block entity",
         this.onClient(() -> this.minecraft.level.getBlockEntity(pos) instanceof DecoratedPotBlockEntity),
         this.onClient(() -> String.valueOf(this.minecraft.level.getBlockEntity(pos)))
      );
      int wobbles = BedrockDecoratedPots.wobbleCount();
      int diamonds = this.onClient(() -> this.countInInventory(Items.DIAMOND));
      this.useItemOn(Items.DIAMOND, pos, Direction.UP, Vec3.atCenterOf(pos).add(0.0, 0.4, 0.0));
      this.check(
         "pot: an item put in makes it wobble",
         this.await(() -> BedrockDecoratedPots.wobbleCount() > wobbles, 4000L),
         "wobbles " + (BedrockDecoratedPots.wobbleCount() - wobbles)
      );
      this.check(
         "pot: the wobble is the positive one",
         this.onClient(() -> this.minecraft.level.getBlockEntity(pos) instanceof DecoratedPotBlockEntity pot && pot.lastWobbleStyle == WobbleStyle.POSITIVE),
         this.onClient(() -> this.minecraft.level.getBlockEntity(pos) instanceof DecoratedPotBlockEntity pot ? String.valueOf(pot.lastWobbleStyle) : "no pot")
      );
      this.check(
         "pot: it holds the diamond",
         this.await(() -> this.minecraft.level.getBlockEntity(pos) instanceof DecoratedPotBlockEntity pot && pot.getTheItem().is(Items.DIAMOND), 4000L),
         this.onClient(() -> this.minecraft.level.getBlockEntity(pos) instanceof DecoratedPotBlockEntity pot ? String.valueOf(pot.getTheItem()) : "no pot")
      );
      this.check(
         "pot: one diamond left the hand",
         this.await(() -> this.countInInventory(Items.DIAMOND) == diamonds - 1, 3000L),
         "diamonds " + diamonds + " -> " + this.onClient(() -> this.countInInventory(Items.DIAMOND))
      );
      this.check(
         "pot: no screen opened",
         this.onClient(() -> this.minecraft.gui.screen() == null && this.minecraft.player.containerMenu == this.minecraft.player.inventoryMenu),
         "screen "
            + this.onClient(() -> String.valueOf(this.minecraft.gui.screen()))
            + ", menu "
            + this.onClient(() -> this.minecraft.player.containerMenu.getClass().getSimpleName())
      );
      this.screenshot("containers2-pot-wobble");
      int afterFirst = BedrockDecoratedPots.wobbleCount();
      this.useItemOn(Items.COBBLESTONE, pos, Direction.UP, Vec3.atCenterOf(pos).add(0.0, 0.4, 0.0));
      this.check(
         "pot: another item wobbles it the other way",
         this.await(() -> BedrockDecoratedPots.wobbleCount() > afterFirst, 4000L)
            && this.onClient(
               () -> this.minecraft.level.getBlockEntity(pos) instanceof DecoratedPotBlockEntity pot && pot.lastWobbleStyle == WobbleStyle.NEGATIVE
            ),
         "wobbles "
            + (BedrockDecoratedPots.wobbleCount() - afterFirst)
            + ", style "
            + this.onClient(
               () -> this.minecraft.level.getBlockEntity(pos) instanceof DecoratedPotBlockEntity pot ? String.valueOf(pot.lastWobbleStyle) : "no pot"
            )
      );
      this.check(
         "pot: it still holds only the diamond",
         this.onClient(() -> this.minecraft.level.getBlockEntity(pos) instanceof DecoratedPotBlockEntity pot && pot.getTheItem().is(Items.DIAMOND)),
         this.onClient(() -> this.minecraft.level.getBlockEntity(pos) instanceof DecoratedPotBlockEntity pot ? String.valueOf(pot.getTheItem()) : "no pot")
      );
      this.commands("setblock " + at(pos) + " air");
   }

   private void jukebox(BlockPos base) {
      BlockPos pos = base.offset(0, 0, 3);
      this.commands("setblock " + at(pos) + " jukebox", "give @s music_disc_cat", "gamemode survival");
      sleep(800L);
      this.check("jukebox: empty at first", !BedrockJukeboxDiscs.hasRecord(pos), "has record " + BedrockJukeboxDiscs.hasRecord(pos));
      this.useItemOn(Items.MUSIC_DISC_CAT, pos, Direction.UP, Vec3.atCenterOf(pos).add(0.0, 0.4, 0.0));
      this.check(
         "jukebox: the disc put in arrives in the block entity data",
         this.await(() -> BedrockJukeboxDiscs.hasRecord(pos), 5000L),
         "has record " + BedrockJukeboxDiscs.hasRecord(pos)
      );
      this.check(
         "jukebox: the block has a record",
         this.await(() -> this.minecraft.level.getBlockState(pos).getValue(BlockStateProperties.HAS_RECORD), 3000L),
         this.onClient(() -> String.valueOf(this.minecraft.level.getBlockState(pos)))
      );
      this.check(
         "jukebox: the disc left the inventory",
         this.await(() -> !this.hasInInventory(Items.MUSIC_DISC_CAT), 3000L),
         "inventory disc " + this.onClient(() -> this.hasInInventory(Items.MUSIC_DISC_CAT))
      );
      this.check(
         "jukebox: no screen opened",
         this.onClient(() -> this.minecraft.gui.screen() == null && this.minecraft.player.containerMenu == this.minecraft.player.inventoryMenu),
         "screen " + this.onClient(() -> String.valueOf(this.minecraft.gui.screen()))
      );
      this.screenshot("containers2-jukebox");
      this.use(pos, Direction.UP);
      this.check(
         "jukebox: using it with an empty hand takes the disc out",
         this.await(() -> !BedrockJukeboxDiscs.hasRecord(pos), 5000L),
         "has record " + BedrockJukeboxDiscs.hasRecord(pos)
      );
      this.check(
         "jukebox: the block has no record again",
         this.await(() -> !this.minecraft.level.getBlockState(pos).getValue(BlockStateProperties.HAS_RECORD), 3000L),
         this.onClient(() -> String.valueOf(this.minecraft.level.getBlockState(pos)))
      );
      this.check(
         "jukebox: the disc is back (picked up or dropped)",
         this.await(() -> this.hasInInventory(Items.MUSIC_DISC_CAT) || this.droppedItem(Items.MUSIC_DISC_CAT) != null, 5000L),
         "inventory " + this.onClient(() -> this.hasInInventory(Items.MUSIC_DISC_CAT))
      );
      this.commands("setblock " + at(pos) + " air", "kill @e[type=item]");
   }

   private void jigsaw(BlockPos base) {
      BlockPos pos = base.offset(0, 0, -3);
      this.commands("gamemode creative", "setblock " + at(pos) + " jigsaw");
      sleep(1000L);
      this.check(
         "jigsaw: the block has a jigsaw block entity",
         this.onClient(() -> this.minecraft.level.getBlockEntity(pos) instanceof JigsawBlockEntity),
         this.onClient(() -> String.valueOf(this.minecraft.level.getBlockEntity(pos)))
      );
      this.check("jigsaw: the server's data reached the block", BedrockJigsawBlocks.serverData(pos) != null, "data " + BedrockJigsawBlocks.serverData(pos));
      this.use(pos, Direction.UP);
      JigsawBlockEditScreen screen = this.awaitScreen(JigsawBlockEditScreen.class, 5000L);
      this.check(
         "jigsaw: using it as an operator opens the jigsaw screen",
         screen != null,
         "screen " + this.onClient(() -> String.valueOf(this.minecraft.gui.screen()))
      );
      if (screen != null) {
         sleep(500L);
         this.screenshot("containers2-jigsaw-screen");
         this.run(() -> {
            set(screen, "nameEdit", "bn:door");
            set(screen, "targetEdit", "bn:room");
            set(screen, "poolEdit", "bn:rooms");
            set(screen, "finalStateEdit", "minecraft:stone");
            set(screen, "selectionPriorityEdit", "3");
            set(screen, "placementPriorityEdit", "5");
         });
         int sent = BedrockJigsawBlocks.sentCount();
         this.press(this.findButton(JigsawBlockEditScreen.class, "gui.done"));
         this.check(
            "jigsaw: Done sends the block entity data",
            this.await(() -> BedrockJigsawBlocks.sentCount() == sent + 1, 3000L),
            "sent " + (BedrockJigsawBlocks.sentCount() - sent)
         );
         this.check(
            "jigsaw: the screen closed",
            this.await(() -> this.minecraft.gui.screen() == null, 2000L),
            "screen " + this.onClient(() -> String.valueOf(this.minecraft.gui.screen()))
         );
         BlockPos copy = pos.offset(4, 0, 0);
         sleep(1000L);
         this.commands("structure save bn_jigsaw_probe " + at(pos) + " " + at(pos) + " false memory", "structure load bn_jigsaw_probe " + at(copy));
         boolean held = this.await(() -> {
            JigsawEdits.Data datax = BedrockJigsawBlocks.serverData(copy);
            return datax != null && "bn:door".equals(datax.name()) && "bn:rooms".equals(datax.pool());
         }, 6000L);
         this.check(
            "jigsaw: the server holds the new name and pool in its block entity data",
            held,
            "copy data " + BedrockJigsawBlocks.serverData(copy) + ", own " + BedrockJigsawBlocks.serverData(pos)
         );
         JigsawEdits.Data data = BedrockJigsawBlocks.serverData(copy);
         this.commands("setblock " + at(copy) + " air", "structure delete bn_jigsaw_probe");
         this.check(
            "jigsaw: target, final state and priorities too",
            data != null
               && "bn:room".equals(data.target())
               && "minecraft:stone".equals(data.finalState())
               && data.selectionPriority() == 3
               && data.placementPriority() == 5,
            "data " + data
         );
         this.use(pos, Direction.UP);
         JigsawBlockEditScreen second = this.awaitScreen(JigsawBlockEditScreen.class, 5000L);
         this.check(
            "jigsaw: opened again it shows the saved name",
            second != null && "bn:door".equals(this.onClient(() -> value(second, "nameEdit"))),
            "name " + (second == null ? "no screen" : this.onClient(() -> value(second, "nameEdit")))
         );
         this.screenshot("containers2-jigsaw-saved");
         this.commands("setblock " + at(pos) + " air");
      }
   }

   private static void set(Screen screen, String field, String value) {
      try {
         Field f = JigsawBlockEditScreen.class.getDeclaredField(field);
         f.setAccessible(true);
         ((EditBox)f.get(screen)).setValue(value);
      } catch (ReflectiveOperationException e) {
         throw new IllegalStateException("jigsaw screen field " + field, e);
      }
   }

   private static String value(Screen screen, String field) {
      try {
         Field f = JigsawBlockEditScreen.class.getDeclaredField(field);
         f.setAccessible(true);
         return ((EditBox)f.get(screen)).getValue();
      } catch (ReflectiveOperationException e) {
         throw new IllegalStateException("jigsaw screen field " + field, e);
      }
   }

   private boolean hasInInventory(Item item) {
      return this.countInInventory(item) > 0;
   }

   private int countInInventory(Item item) {
      int count = 0;
      Inventory inventory = this.minecraft.player.getInventory();

      for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
         ItemStack stack = inventory.getItem(slot);
         if (stack.is(item)) {
            count += stack.getCount();
         }
      }

      return count;
   }

   private ItemStack droppedItem(Item item) {
      for (Entity entity : this.minecraft.level.entitiesForRendering()) {
         if (entity instanceof ItemEntity drop && drop.getItem().is(item)) {
            return drop.getItem();
         }
      }

      return null;
   }

   private void key(int shortcut) {
      this.run(() -> this.minecraft.gui.screen().keyPressed(new KeyEvent(0, shortcut, 0)));
      sleep(200L);
   }

   private void type(String text) {
      for (char c : text.toCharArray()) {
         this.run(() -> this.minecraft.gui.screen().charTyped(new CharacterEvent(c)));
         sleep(15L);
      }
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

         BedrockNative.LOGGER.warn("Dev containers2 test: {} is not in the hotbar", item);
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
      sleep(300L);
   }

   private void use(BlockPos pos, Direction face) {
      this.run(() -> this.minecraft.player.getInventory().setSelectedSlot(8));
      sleep(700L);
      Vec3 hit = Vec3.atCenterOf(pos).add(0.0, 0.4, 0.0);
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
      sleep(300L);
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
         BedrockNative.LOGGER.info("Dev containers2 test PASS {}: {}", name, detail);
      } else {
         this.fail(name, detail);
      }
   }

   private void fail(String name, String detail) {
      this.failed++;
      BedrockNative.LOGGER.info("Dev containers2 test FAIL {}: {}", name, detail);
   }

   private static String at(BlockPos pos) {
      return pos.getX() + " " + pos.getY() + " " + pos.getZ();
   }

   private static String fill(BlockPos from, BlockPos to, String block) {
      return "fill " + at(from) + " " + at(to) + " " + block;
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
      BedrockNative.LOGGER.info("Dev containers2 test: " + message, args);
   }

   private static void sleep(long millis) {
      try {
         Thread.sleep(millis);
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
      }
   }
}
