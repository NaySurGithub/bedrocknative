package net.bedrocknative.client.ui;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.content.BedrockInstall;
import net.bedrocknative.content.FolderPack;
import net.bedrocknative.content.PackStack;
import net.bedrocknative.ui.ContainerData;
import net.bedrocknative.ui.HudData;
import net.bedrocknative.ui.HudRenderers;
import net.bedrocknative.ui.HudValues;
import net.bedrocknative.ui.UiControl;
import net.bedrocknative.ui.UiDefinitionSet;
import net.bedrocknative.ui.UiDrawCommand;
import net.bedrocknative.ui.UiEnvironment;
import net.bedrocknative.ui.UiScreenInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public final class DevJsonUiHudOffline {
   private static final String LOG = "Dev jsonui-hud-offline";
   private final Minecraft minecraft;
   private int passed;
   private int failed;

   private DevJsonUiHudOffline(Minecraft minecraft) {
      this.minecraft = minecraft;
   }

   public static void start() {
      Thread.ofVirtual().start(() -> {
         try {
            new DevJsonUiHudOffline(Minecraft.getInstance()).run();
         } catch (RuntimeException var4) {
            BedrockNative.LOGGER.error("{}: FAILED with {}", new Object[]{"Dev jsonui-hud-offline", var4.toString(), var4});
         } finally {
            Minecraft.getInstance().execute(() -> Minecraft.getInstance().stop());
         }
      });
   }

   private void run() {
      waitFor(() -> this.minecraft.isGameLoadFinished() && this.minecraft.gui.overlay() == null && this.minecraft.gui.screen() != null, 600000L);
      sleep(2000L);
      JsonUi.setMode(JsonUi.Mode.ON);
      this.call(() -> {
         this.fontPack();
         return null;
      });
      UiDefinitionSet definitions = null;

      for (int i = 0; i < 120 && definitions == null; i++) {
         definitions = this.call(JsonUi::definitions);
         sleep(500L);
      }

      this.check("definitions loaded", definitions != null, "install " + BedrockInstall.vanillaResourcePack());
      if (definitions != null) {
         UiDefinitionSet set = definitions;
         DevJsonUiHudOffline.HudScreen screen = this.call(() -> {
            DevJsonUiHudOffline.HudScreen hud = new DevJsonUiHudOffline.HudScreen(set);
            this.minecraft.gui.setScreen(hud);
            return hud;
         });
         this.scenario(screen, "survival", survival(), data -> {
            data.chat("§e<Steve> §fhello from the offline test");
            data.itemText("§bDiamond Sword");
            data.jukebox("Now playing: C418 - cat");
         }, this::checkSurvival);
         HudValues creative = with(survival(), HudValues.Mode.CREATIVE, 300, false, 0, 0, Set.of());
         this.scenario(screen, "creative", creative, data -> {}, hud -> {
            this.check("creative: no hearts drawn", HudRenderers.count(screen.drawn, "heart_background") == 0L, "");
            this.check("creative: no XP bar", visible(hud, "full_progress_bar").isEmpty(), "");
         });
         HudValues water = with(survival(), HudValues.Mode.SURVIVAL, 152, true, 21, 15, Set.of());
         this.scenario(screen, "water", water, data -> {}, hud -> {
            this.check("underwater: a bursting bubble", HudRenderers.count(screen.drawn, "bubble_pop") == 1L, HudRenderers.textures(screen.drawn).toString());
            this.check("riding: 10 full mount hearts", HudRenderers.count(screen.drawn, "horse_heart") == 10L, "");
         });
         HudValues hidden = with(
            survival(), HudValues.Mode.SURVIVAL, 300, false, 0, 0, Set.of(HudValues.Part.HOTBAR, HudValues.Part.HEALTH, HudValues.Part.PROGRESS_BAR)
         );
         this.scenario(screen, "sethud", hidden, data -> {}, hud -> {
            this.check("SetHud: no hotbar slots", HudRenderers.textures(screen.drawn).stream().noneMatch(path -> path.contains("/hotbar_")), "");
            this.check("SetHud: no hearts", HudRenderers.count(screen.drawn, "heart_background") == 0L, "");
         });
         BedrockNative.LOGGER.info("{}: done, {} passed, {} failed", new Object[]{"Dev jsonui-hud-offline", this.passed, this.failed});
      }
   }

   private void checkSurvival(UiScreenInstance hud) {
      List<UiControl> grids = visible(hud, "hotbar_grid");
      this.check("one hotbar shows", grids.size() == 1, grids.toString());
      if (grids.size() == 1) {
         UiControl grid = grids.getFirst();
         this.check("9 hotbar cells", grid.generated().size() == 9, String.valueOf(grid.generated().size()));
         List<Integer> selected = new ArrayList<>();

         for (int i = 0; i < grid.generated().size(); i++) {
            UiControl image = grid.generated().get(i).find("hotbar_slot_selected_image");
            if (image != null && image.visible()) {
               selected.add(i);
            }
         }

         this.check("selection on slot 2", selected.equals(List.of(2)), selected.toString());
         UiControl count = grid.generated().get(2).find("item_count");
         this.check("slot 2 count 7", count != null && "7".equals(count.text()), count == null ? "none" : count.text());
      }

      this.check(
         "7 full and 1 half heart drawn",
         HudRenderers.count(this.drawnOf(hud), "heart") == 7L && HudRenderers.count(this.drawnOf(hud), "heart_half") == 1L,
         HudRenderers.textures(this.drawnOf(hud)).toString()
      );
      this.check(
         "food 13: 6 full, 1 half",
         HudRenderers.count(this.drawnOf(hud), "hunger_full") == 6L && HudRenderers.count(this.drawnOf(hud), "hunger_half") == 1L,
         ""
      );
      this.check(
         "armour 7: 3 full, 1 half", HudRenderers.count(this.drawnOf(hud), "armor_full") == 3L && HudRenderers.count(this.drawnOf(hud), "armor_half") == 1L, ""
      );
      List<UiControl> level = visible(hud, "progress_text_label");
      this.check("level 7", level.size() == 1 && "7".equals(level.getFirst().text()), level.toString());
      UiControl chat = hud.find("chat_panel");
      List<String> lines = chat == null ? List.of() : chat.findAll("chat_text").stream().map(UiControl::text).toList();
      this.check("chat line", lines.contains("§e<Steve> §fhello from the offline test"), lines.toString());
      List<UiControl> name = visible(hud, "item_name_text");
      this.check("item name", name.size() == 1 && "§bDiamond Sword".equals(name.getFirst().find("item_text_label").text()), name.toString());
      this.check(
         "effects drawn", HudRenderers.count(this.drawnOf(hud), "speed_effect") == 1L && HudRenderers.count(this.drawnOf(hud), "weakness_effect") == 1L, ""
      );
   }

   private List<UiDrawCommand> drawnOf(UiScreenInstance hud) {
      return ((DevJsonUiHudOffline.HudScreen)this.minecraft.gui.screen()).drawn;
   }

   private void scenario(DevJsonUiHudOffline.HudScreen screen, String name, HudValues values, Consumer<HudData> events, Consumer<UiScreenInstance> checks) {
      this.call(() -> {
         screen.data.player(values);
         events.accept(screen.data);
         return null;
      });
      sleep(1200L);
      this.call(() -> {
         checks.accept(screen.hud);
         return null;
      });
      this.minecraft
         .execute(
            () -> Screenshot.grab(
               this.minecraft.gameDirectory, "jsonui-hud-offline-" + name + ".png", this.minecraft.gameRenderer.mainRenderTarget(), 1, message -> {}
            )
         );
      sleep(800L);
   }

   private static List<UiControl> visible(UiScreenInstance hud, String name) {
      return hud.findAll(name).stream().filter(UiControl::visible).toList();
   }

   private void fontPack() throws IOException {
      if (!PackStack.active().read("font/default8.png").isPresent()) {
         Path folder = Files.createTempDirectory("bn-offline-font");
         Files.createDirectories(folder.resolve("font"));

         try (InputStream in = this.minecraft.getResourceManager().getResourceOrThrow(Identifier.withDefaultNamespace("textures/font/ascii.png")).open()) {
            Files.copy(in, folder.resolve("font/default8.png"));
         }

         PackStack.useServerPacks(List.of(new FolderPack(folder)));
         JsonUi.packsChanged();
         BedrockNative.LOGGER.info("{}: no Bedrock font in the base pack, Java's ascii.png stands in", "Dev jsonui-hud-offline");
      }
   }

   static HudValues survival() {
      List<ContainerData.Slot> hotbar = new ArrayList<>(HudValues.emptyHotbar());
      hotbar.set(0, new ContainerData.Slot(1, 1561, 900, 0, 0, "Diamond Sword", 0));
      hotbar.set(2, new ContainerData.Slot(7, 0, 0, 0, 0, "Apple", 0));
      hotbar.set(3, new ContainerData.Slot(64, 0, 0, 0, 0, "Stone", 0));
      float[] cooldowns = new float[9];
      cooldowns[4] = 0.5F;
      hotbar.set(4, new ContainerData.Slot(16, 0, 0, 0, 0, "Ender Pearl", 0));
      return new HudValues(
         HudValues.Mode.SURVIVAL,
         1,
         15,
         15,
         20.0F,
         0,
         false,
         HudValues.HeartType.NORMAL,
         false,
         false,
         7,
         13,
         5.0F,
         false,
         300,
         300,
         false,
         0,
         0,
         HudValues.Bar.EXPERIENCE,
         0.4F,
         7,
         -1.0F,
         false,
         List.of(new HudValues.Effect("speed", false, true, 1200, 1, true), new HudValues.Effect("weakness", false, false, 600, 0, true)),
         2,
         hotbar,
         cooldowns,
         Set.of()
      );
   }

   private static HudValues with(HudValues v, HudValues.Mode mode, int air, boolean underwater, int mountHealth, int mountHearts, Set<HudValues.Part> hidden) {
      return new HudValues(
         mode,
         v.tick(),
         v.health(),
         v.displayHealth(),
         v.maxHealth(),
         v.absorption(),
         v.healthBlink(),
         v.heartType(),
         v.regeneration(),
         v.hardcore(),
         v.armor(),
         v.food(),
         v.saturation(),
         v.hungerEffect(),
         air,
         v.maxAir(),
         underwater,
         mountHealth,
         mountHearts,
         v.bar(),
         v.experience(),
         v.level(),
         v.jump(),
         v.jumpCooldown(),
         v.effects(),
         v.selected(),
         v.hotbar(),
         v.cooldowns(),
         hidden
      );
   }

   private void check(String what, boolean ok, String detail) {
      if (ok) {
         this.passed++;
      } else {
         this.failed++;
      }

      BedrockNative.LOGGER
         .info("{}: {}: {}{}", new Object[]{"Dev jsonui-hud-offline", what, ok ? "PASS" : "FAIL", detail.isEmpty() ? "" : " (" + detail + ")"});
   }

   private <T> T call(Callable<T> task) {
      return CompletableFuture.<T>supplyAsync(() -> {
         try {
            return task.call();
         } catch (Exception var2) {
            throw new IllegalStateException(var2);
         }
      }, this.minecraft::execute).join();
   }

   private static void waitFor(Supplier<Boolean> condition, long millis) {
      long end = System.currentTimeMillis() + millis;

      while (System.currentTimeMillis() < end && !condition.get()) {
         sleep(500L);
      }
   }

   private static void sleep(long millis) {
      try {
         Thread.sleep(millis);
      } catch (InterruptedException var3) {
         Thread.currentThread().interrupt();
      }
   }

   private final class HudScreen extends Screen {
      final HudData data;
      final UiDefinitionSet definitions;
      @Nullable
      UiScreenInstance hud;
      List<UiDrawCommand> drawn;
      private long start;

      HudScreen(UiDefinitionSet definitions) {
         Objects.requireNonNull(DevJsonUiHudOffline.this);
         super(Component.literal("JSON UI HUD"));
         this.data = new HudData();
         this.drawn = List.of();
         this.definitions = definitions;
      }

      public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
         graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), -12957094);
         UiEnvironment environment = JsonUi.environment(this.definitions);
         if (this.hud == null) {
            this.hud = UiScreenInstance.create(
               this.definitions,
               "hud.hud_screen",
               HudData.engineVariables(0.5, 1.0, 0.0),
               this.data,
               environment,
               HudData.JAVA_OWNED,
               JsonUi.uiWidth(),
               JsonUi.uiHeight()
            );
            this.hud.problems().log("hud offline");
            this.hud.fireEvent("screen.entrance_push");
            this.start = System.nanoTime();
         }

         this.hud.resize(JsonUi.uiWidth(), JsonUi.uiHeight());
         this.data.deliver(this.hud);
         this.hud.update((System.nanoTime() - this.start) / 1.0E9);
         this.data.afterUpdate(this.hud);
         List<UiDrawCommand> expanded = new ArrayList<>();

         for (UiDrawCommand command : this.hud.drawList().commands()) {
            if (command instanceof UiDrawCommand.Custom custom && HudRenderers.handles(custom.renderer())) {
               expanded.addAll(HudRenderers.expand(custom, this.data.values(), environment.textures()));
            }
         }

         this.drawn = expanded;
         BedrockFont font = JsonUi.font();
         if (font != null) {
            JsonUiCanvas.draw(
               graphics, this.hud.drawList(), font, renderer -> ItemStack.EMPTY, this.data.values(), environment.textures(), control -> false, null
            );
         }
      }

      public boolean isPauseScreen() {
         return false;
      }
   }
}
