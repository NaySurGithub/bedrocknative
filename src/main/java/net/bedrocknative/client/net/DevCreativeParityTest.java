package net.bedrocknative.client.net;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Window;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javax.imageio.ImageIO;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.client.inventory.JsonUiContainerScreen;
import net.bedrocknative.client.inventory.JsonUiCreativeScreen;
import net.bedrocknative.client.ui.JsonUi;
import net.bedrocknative.client.ui.JsonUiScreen;
import net.bedrocknative.content.PackStack;
import net.bedrocknative.text.BedrockLanguage;
import net.bedrocknative.text.BedrockText;
import net.bedrocknative.ui.CreativeData;
import net.bedrocknative.ui.InventoryReference;
import net.bedrocknative.ui.UiControl;
import net.bedrocknative.ui.UiTextMeasurer;
import net.bedrocknative.ui.UiType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.jspecify.annotations.Nullable;
import org.lwjgl.sdl.SDLMouse;
import org.lwjgl.sdl.SDLVideo;

final class DevCreativeParityTest {
   private static final double MIN_SHARE = 97.0;
   private static final int TOLERANCE = 24;
   private static final long STEP = 800L;
   private final Minecraft minecraft;
   private final boolean bds;
   private int passed;
   private int failed;
   private boolean borderless;

   DevCreativeParityTest(Minecraft minecraft, boolean bds) {
      this.minecraft = minecraft;
      this.bds = bds;
   }

   void run() {
      if (!JsonUi.drawn("creative")) {
         log("SKIP: the creative screen isn't drawn by JSON UI in this run");
      } else {
         Window window = this.minecraft.getWindow();
         int[] restore = new int[]{
            this.<Integer>onClient(window::getScreenWidth),
            this.onClient(() -> window.getScreenHeight()),
            this.onClient(() -> (Integer)this.minecraft.options.guiScale().get())
         };

         try {
            boolean sized = this.size(window);
            this.run(() -> this.minecraft.options.guiScale().set(2));
            sleep(1000L);
            int scale = this.onClient(() -> JsonUi.uiScale());
            this.check("JSON UI scale " + scale + " (the references' 2)", scale == 2);
            this.command("gamemode creative");
            this.command("clear @s");
            this.run(() -> BedrockText.useBedrockLanguage(BedrockLanguage.load(PackStack.active(), "fr_fr")));
            this.run(
               () -> this.minecraft
                  .gui
                  .setScreen(
                     new CreativeModeInventoryScreen(
                        this.minecraft.player, this.minecraft.player.connection.enabledFeatures(), (Boolean)this.minecraft.options.operatorItemsTab().get()
                     )
                  )
            );
            this.waitFor(
               "the JSON UI creative screen",
               () -> this.onClient(() -> this.minecraft.gui.screen() instanceof JsonUiCreativeScreen json && json.grid() != null),
               10000L
            );
            if (this.onClient(() -> this.minecraft.gui.screen() instanceof JsonUiCreativeScreen)) {
               boolean french = french();
               if (!french) {
                  log(
                     "SKIP the texts' places and pixels: the client's language shows '{}' where the French reference shows 'Tous les objets'",
                     BedrockText.translate("craftingScreen.tab.allItems", List.of())
                  );
               }

               if (this.onClient(() -> this.minecraft.gui.screen() instanceof JsonUiCreativeScreen json && json.layout() != CreativeData.Layout.CREATIVE)) {
                  this.toggle("creative_layout_toggle_panel");
               }

               this.toggle("search_tab_holder");
               this.run(() -> {
                  if (this.minecraft.gui.screen() instanceof JsonUiCreativeScreen json) {
                     UiControl box = json.searchBox();
                     if (box != null) {
                        json.ui().input().setText(box, "");
                     }

                     json.ui().input().focus(null);
                  }
               });
               this.park(window);
               sleep(800L);
               this.run(
                  () -> {
                     if (this.minecraft.gui.screen() instanceof JsonUiCreativeScreen json) {
                        String title = text(json, "creative_label");
                        String expected = BedrockText.translate("craftingScreen.tab.allItems", List.of());
                        this.check(
                           "the search tab's title is Bedrock's '" + expected + "' (craftingScreen.tab.allItems): '" + title + "'", expected.equals(title)
                        );
                        long grouped = json.data().cells().stream().filter(cell -> cell.kind() != CreativeData.Kind.ENTRY).count();
                        this.check(
                           "the search list has no group cells (" + grouped + " of " + json.data().cells().size() + ")",
                           grouped == 0L && !json.data().cells().isEmpty()
                        );
                     }
                  }
               );
               this.layout(InventoryReference.Layout.ITEMS, sized, french);
               this.run(() -> {
                  if (this.minecraft.gui.screen() instanceof JsonUiCreativeScreen json) {
                     UiControl help = json.ui().input().firing("button.help", null, -1);
                     if (help != null) {
                        float[] at = JsonUiScreen.toGui(help.x() + help.width() / 2.0F, help.y() + help.height() / 2.0F);
                        MouseButtonEvent event = new MouseButtonEvent(at[0], at[1], new MouseButtonInfo(1, 0));
                        json.mouseMoved(at[0], at[1]);
                        json.mouseClicked(event, false);
                        json.mouseReleased(event);
                     }
                  }
               });
               sleep(300L);
               String notice = this.onClient(
                  () -> this.minecraft.gui.screen() instanceof JsonUiCreativeScreen json && json.notice().text() != null
                     ? json.notice().text().getString()
                     : ""
               );
               this.check("the toolbar's \"?\" shows the notice '" + notice + "'", !notice.isEmpty());
               this.screenshot("creative-parity-help-notice");
               this.toggle("survival_layout_toggle_panel");
               this.park(window);
               sleep(800L);
               if (this.onClient(() -> this.minecraft.gui.screen() instanceof JsonUiCreativeScreen json && json.layout() == CreativeData.Layout.SURVIVAL)) {
                  this.layout(InventoryReference.Layout.INVENTORY, sized, french);
               } else {
                  this.check("the inventory toggle shows the inventory alone", false);
               }

               this.toggle("recipe_book_layout_toggle_panel");
               this.park(window);
               sleep(800L);
               boolean middle = this.onClient(
                  () -> this.minecraft.gui.screen() instanceof JsonUiCreativeScreen json
                     && json.layout() == CreativeData.Layout.BOTH
                     && visible(json, "recipe_book")
                     && visible(json, "player_inventory")
               );
               if (middle) {
                  this.layout(InventoryReference.Layout.BOTH, sized, french);
               } else {
                  this.check("the middle toggle shows the list beside the inventory", false);
               }

               this.toggle("creative_layout_toggle_panel");
               this.run(() -> this.minecraft.player.closeContainer());
               sleep(800L);
               return;
            }

            this.check("the creative screen is JSON UI's", false);
         } catch (RuntimeException var11) {
            BedrockNative.LOGGER.error("Dev creative parity: FAIL crashed", var11);
            this.failed++;
            return;
         } finally {
            this.run(() -> BedrockText.useBedrockLanguage(BedrockLanguage.load(PackStack.active(), this.minecraft.options.languageCode)));
            this.restore(window, restore);
            log("SUMMARY {} passed, {} failed", this.passed, this.failed);
         }
      }
   }

   void survival() {
      if (!JsonUi.drawn("crafting.inventory_screen")) {
         log("SKIP the survival inventory: not drawn by JSON UI in this run");
      } else {
         Window window = this.minecraft.getWindow();
         int[] restore = new int[]{
            this.<Integer>onClient(window::getScreenWidth),
            this.onClient(() -> window.getScreenHeight()),
            this.onClient(() -> (Integer)this.minecraft.options.guiScale().get())
         };

         try {
            boolean sized = this.size(window);
            this.run(() -> this.minecraft.options.guiScale().set(2));
            this.run(() -> BedrockText.useBedrockLanguage(BedrockLanguage.load(PackStack.active(), "fr_fr")));
            sleep(1000L);
            this.run(() -> this.minecraft.gui.setScreen(new InventoryScreen(this.minecraft.player)));
            this.waitFor("the JSON UI inventory", () -> this.onClient(() -> this.minecraft.gui.screen() instanceof JsonUiContainerScreen), 10000L);
            this.park(window);
            sleep(800L);
            if (this.onClient(() -> this.minecraft.gui.screen() instanceof JsonUiContainerScreen)) {
               if (!sized) {
                  log("SKIP the survival inventory's rects: the window isn't 1920x1080");
               } else {
                  boolean french = french();

                  for (InventoryReference.Result result : this.onClient(
                     () -> {
                        if (!(this.minecraft.gui.screen() instanceof JsonUiContainerScreen json)) {
                           return List.of();
                        } else {
                           UiControl book = json.ui().instance().find("recipe_book");
                           boolean beside = book != null && book.visible();
                           log("the survival inventory opens {}", beside ? "beside the recipe book" : "alone");
                           UiTextMeasurer measurer = JsonUi.environment(json.ui().instance().definitions()).measurer();
                           return InventoryReference.compare(
                              new InventoryReference.Found(json.ui().instance(), measurer), InventoryReference.survivalInventory(beside), JsonUi.uiScale()
                           );
                        }
                     }
                  )) {
                     if (result.rect().what().startsWith("heading text") && !french) {
                        log("SKIP survival {} (not French)", result);
                     } else {
                        this.check("survival " + result, result.ok());
                     }
                  }
               }

               this.screenshot("inventory-parity-survival");
               this.run(() -> this.minecraft.player.closeContainer());
               sleep(800L);
               return;
            }

            this.check("the survival inventory is JSON UI's", false);
         } catch (RuntimeException var11) {
            BedrockNative.LOGGER.error("Dev creative parity: FAIL crashed", var11);
            this.failed++;
            return;
         } finally {
            this.run(() -> BedrockText.useBedrockLanguage(BedrockLanguage.load(PackStack.active(), this.minecraft.options.languageCode)));
            this.restore(window, restore);
            log("SUMMARY {} passed, {} failed", this.passed, this.failed);
         }
      }
   }

   private void layout(InventoryReference.Layout layout, boolean sized, boolean french) {
      String name = layout.name().toLowerCase(Locale.ROOT);
      if (!sized) {
         log("SKIP the {} rects and pixels: the window isn't 1920x1080", name);
      } else {
         for (InventoryReference.Result result : this.onClient(() -> {
            if (this.minecraft.gui.screen() instanceof JsonUiCreativeScreen json) {
               UiTextMeasurer var4x = JsonUi.environment(json.ui().instance().definitions()).measurer();
               return InventoryReference.compare(new InventoryReference.Found(json.ui().instance(), var4x), InventoryReference.of(layout), JsonUi.uiScale());
            } else {
               return List.of();
            }
         })) {
            boolean text = result.rect().what().startsWith("title text") || result.rect().what().startsWith("heading text");
            if (text && !french) {
               log("SKIP {} {} (not French)", name, result);
            } else if (result.rect().known()) {
               log("{} {} {}", result.ok() ? "PASS" : "KNOWN", name, result);
            } else {
               this.check(name + " " + result, result.ok());
            }
         }

         this.screenshot("creative-parity-" + name);
         this.pixels(layout, french);
      }
   }

   private void pixels(InventoryReference.Layout layout, boolean french) {
      File reference = new File(new File(this.minecraft.gameDirectory, "user-screenshots"), layout.picture);
      if (!reference.isFile()) {
         log("SKIP the {} pixels: no {}", layout, reference);
      } else {
         InventoryReference.Mask mask = this.onClient(() -> {
            if (this.minecraft.gui.screen() instanceof JsonUiCreativeScreen json) {
               InventoryReference.Mask var9x = InventoryReference.mask(layout, json.ui().instance());
               if (french) {
                  return var9x;
               } else {
                  List<float[]> exclude = new ArrayList<>(var9x.exclude());

                  for (String label : List.of("creative_label", "crafting_label")) {
                     UiControl control = json.ui().instance().find(label);
                     if (control != null && control.visible()) {
                        exclude.add(new float[]{control.x(), control.y(), control.x() + control.width(), control.y() + control.height()});
                     }
                  }

                  return new InventoryReference.Mask(var9x.include(), exclude);
               }
            } else {
               return null;
            }
         });
         int[][] frame = this.frame();
         if (mask != null && frame != null) {
            try {
               BufferedImage real = ImageIO.read(reference);
               long compared = 0L;
               long matching = 0L;

               for (int y = 0; y < real.getHeight(); y++) {
                  for (int x = 0; x < real.getWidth(); x++) {
                     int fx = x + layout.frameX;
                     int fy = y + layout.frameY;
                     if (fy < frame.length && fx < frame[fy].length && mask.compared(fx, fy)) {
                        compared++;
                        int a = frame[fy][fx];
                        int b = real.getRGB(x, y);
                        int distance = Math.max(
                           Math.abs((a >> 16 & 0xFF) - (b >> 16 & 0xFF)),
                           Math.max(Math.abs((a >> 8 & 0xFF) - (b >> 8 & 0xFF)), Math.abs((a & 0xFF) - (b & 0xFF)))
                        );
                        if (distance <= 24) {
                           matching++;
                        }
                     }
                  }
               }

               double share = 100.0 * matching / Math.max(1L, compared);
               this.check(
                  String.format(Locale.ROOT, "%s vs %s: %.2f%% of %d pixels match (at least %.0f%%)", layout, layout.picture, share, compared, 97.0),
                  compared > 10000L && share >= 97.0
               );
            } catch (IOException var18) {
               this.check(layout + " pixels: " + var18, false);
            }
         } else {
            this.check(layout + " pixels: the frame was read", false);
         }
      }
   }

   @Nullable
   private int[][] frame() {
      CompletableFuture<int[][]> pixels = new CompletableFuture<>();
      this.minecraft.execute(() -> Screenshot.takeScreenshot(this.minecraft.gameRenderer.mainRenderTarget(), image -> {
         try {
            NativeImage frame = image;

            try {
               int[][] rows = new int[frame.getHeight()][frame.getWidth()];

               for (int y = 0; y < frame.getHeight(); y++) {
                  for (int x = 0; x < frame.getWidth(); x++) {
                     rows[y][x] = frame.getPixel(x, y);
                  }
               }

               pixels.complete(rows);
            } catch (Throwable var7) {
               if (frame != null) {
                  try {
                     frame.close();
                  } catch (Throwable var6) {
                     var7.addSuppressed(var6);
                  }
               }

               throw var7;
            }

            if (frame != null) {
               frame.close();
            }
         } catch (RuntimeException var8) {
            pixels.completeExceptionally(var8);
         }
      }));

      try {
         return pixels.get(10L, TimeUnit.SECONDS);
      } catch (InterruptedException var3) {
         Thread.currentThread().interrupt();
         return null;
      } catch (TimeoutException | ExecutionException var4) {
         log("the frame could not be read back: {}", var4.toString());
         return null;
      }
   }

   private static boolean visible(JsonUiCreativeScreen json, String name) {
      UiControl control = json.ui().instance().find(name);
      return control != null && control.visible();
   }

   private static String text(JsonUiCreativeScreen json, String label) {
      UiControl control = json.ui().instance().find(label);
      return control == null ? "" : control.text();
   }

   private void park(Window window) {
      this.run(() -> {
         SDLMouse.SDL_WarpMouseInWindow(window.handle(), 2.0F, 2.0F);
         if (this.minecraft.gui.screen() != null) {
            this.minecraft.gui.screen().mouseMoved(1.0, 1.0);
         }

         if (this.minecraft.gui.screen() instanceof JsonUiCreativeScreen json) {
            json.ui().input().focus(null);
         }
      });
   }

   private void toggle(String panel) {
      this.run(() -> {
         if (this.minecraft.gui.screen() instanceof JsonUiCreativeScreen json) {
            UiControl var7 = json.ui().instance().find(panel);
            UiControl[] toggle = new UiControl[]{null};
            if (var7 != null) {
               var7.walk(control -> {
                  if (toggle[0] == null && control.visible() && control.type() == UiType.TOGGLE) {
                     toggle[0] = control;
                  }
               });
            }

            if (toggle[0] == null) {
               this.check("a toggle in " + panel, false);
            } else {
               float[] at = JsonUiScreen.toGui(toggle[0].x() + toggle[0].width() / 2.0F, toggle[0].y() + toggle[0].height() / 2.0F);
               MouseButtonEvent event = new MouseButtonEvent(at[0], at[1], new MouseButtonInfo(1, 0));
               json.mouseMoved(at[0], at[1]);
               json.mouseClicked(event, false);
               json.mouseReleased(event);
            }
         }
      });
      sleep(800L);
   }

   private boolean size(Window window) {
      this.run(() -> window.setWindowed(1920, 1080));
      this.waitFor("a 1920x1080 window", () -> this.onClient(() -> window.getWidth() == 1920 && window.getHeight() == 1080), 5000L);
      if (this.onClient(() -> window.getWidth() != 1920 || window.getHeight() != 1080)) {
         log("the framed window is {}x{}; trying it borderless at 0,0", this.onClient(window::getWidth), this.onClient(window::getHeight));
         this.run(() -> {
            this.borderless = true;
            SDLVideo.SDL_SetWindowBordered(window.handle(), false);
            SDLVideo.SDL_SetWindowPosition(window.handle(), 0, 0);
            SDLVideo.SDL_SetWindowSize(window.handle(), 1920, 1080);
            SDLVideo.SDL_SyncWindow(window.handle());
         });
         this.waitFor("a 1920x1080 borderless window", () -> this.onClient(() -> window.getWidth() == 1920 && window.getHeight() == 1080), 5000L);
      }

      sleep(1000L);
      int width = this.<Integer>onClient(window::getWidth);
      int height = this.<Integer>onClient(window::getHeight);
      boolean ok = width == 1920 && height == 1080;
      this.check("the window is 1920x1080 (" + width + "x" + height + (this.borderless ? ", borderless" : "") + ")", ok);
      return ok;
   }

   private void restore(Window window, int[] restore) {
      if (restore[0] > 0) {
         this.run(() -> {
            this.minecraft.options.guiScale().set(restore[2]);
            if (this.borderless) {
               SDLVideo.SDL_SetWindowBordered(window.handle(), true);
            }

            window.setWindowed(restore[0], restore[1]);
         });
         sleep(500L);
      }
   }

   private void screenshot(String name) {
      this.run(() -> Screenshot.grab(this.minecraft.gameDirectory, name + ".png", this.minecraft.gameRenderer.mainRenderTarget(), 1, message -> {}));
      sleep(400L);
   }

   private void command(String command) {
      this.run(() -> {
         log("command /{}", command);
         this.minecraft.getConnection().sendCommand(command);
      });
      sleep(this.bds ? 600L : 1200L);
   }

   private void check(String what, boolean ok) {
      if (ok) {
         this.passed++;
      } else {
         this.failed++;
      }

      log("{} {}", ok ? "PASS" : "FAIL", what);
   }

   private void waitFor(String what, BooleanSupplier condition, long millis) {
      long end = System.currentTimeMillis() + millis;

      while (System.currentTimeMillis() < end) {
         if (condition.getAsBoolean()) {
            return;
         }

         sleep(100L);
      }

      log("waited {} ms for {}", millis, what);
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
      BedrockNative.LOGGER.info("Dev creative parity: " + message, args);
   }

   private static void sleep(long millis) {
      try {
         Thread.sleep(millis);
      } catch (InterruptedException var3) {
         Thread.currentThread().interrupt();
      }
   }

   private static boolean french() {
      return "Tous les objets".equals(BedrockText.translate("craftingScreen.tab.allItems", List.of()));
   }
}
