package net.bedrocknative.client.ui;

import java.util.IllegalFormatException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.bedrocknative.BedrockNative;
import net.bedrocknative.PerfProbe;
import net.bedrocknative.client.mixin.hud.HudStateAccessor;
import net.bedrocknative.client.movement.BedrockMovement;
import net.bedrocknative.text.BedrockText;
import net.bedrocknative.ui.HeartTracker;
import net.bedrocknative.ui.HudData;
import net.bedrocknative.ui.HudRenderers;
import net.bedrocknative.ui.HudValues;
import net.bedrocknative.ui.UiControl;
import net.bedrocknative.ui.UiDefinitionSet;
import net.bedrocknative.ui.UiEnvironment;
import net.bedrocknative.ui.UiScreenInstance;
import net.bedrocknative.ui.UiTreeDump;
import net.bedrocknative.ui.UiValue;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.contextualbar.ContextualBar;
import net.minecraft.client.gui.contextualbar.LocatorBar;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public final class JsonUiHud {
   public static final Identifier ID = Identifier.fromNamespaceAndPath("bedrocknative", "json_ui_hud");
   public static final Set<String> JAVA_OWNED = HudData.JAVA_OWNED;
   public static final List<Identifier> REPLACED = List.of(
      VanillaHudElements.SCOREBOARD,
      VanillaHudElements.TITLE_AND_SUBTITLE,
      VanillaHudElements.OVERLAY_MESSAGE,
      VanillaHudElements.BOSS_BAR,
      VanillaHudElements.HOTBAR,
      VanillaHudElements.HEALTH_BAR,
      VanillaHudElements.ARMOR_BAR,
      VanillaHudElements.FOOD_BAR,
      VanillaHudElements.AIR_BAR,
      VanillaHudElements.MOUNT_HEALTH,
      VanillaHudElements.INFO_BAR,
      VanillaHudElements.EXPERIENCE_LEVEL,
      VanillaHudElements.HELD_ITEM_TOOLTIP,
      VanillaHudElements.MOB_EFFECTS
   );
   private static final double MAX_FRAME_SECONDS = 0.25;
   private static final int BAR_WIDTH = 182;
   private static final int BAR_TOP = 29;
   private static final HudData DATA = new HudData();
   private static final HeartTracker HEARTS = new HeartTracker();
   @Nullable
   private static UiScreenInstance instance;
   @Nullable
   private static UiDefinitionSet instanceDefinitions;
   @Nullable
   private static Map<String, UiValue> instanceVariables;
   private static boolean failed;
   private static final boolean TRACE = "hud".equals(System.getProperty("bedrocknative.jsonUiTrace"));
   private static double nextTrace = Double.NaN;
   private static int tracesLeft;
   @Nullable
   private static JsonUiDiagnostics diagnostics;
   private static double clock;
   private static long lastFrame;
   private static ItemStack lastHighlight = ItemStack.EMPTY;
   private static boolean showCoordinates;
   private static boolean showDaysPlayed;
   private static boolean drawingLocator;
   @Nullable
   private static DeltaTracker frameDelta;
   private static volatile String lastChat = "";
   @Nullable
   private static Map<String, UiValue> builtVariables;
   private static double builtOpacity;
   private static double builtScale;
   private static double builtSpacing;
   @Nullable
   private static String positionText;
   @Nullable
   private static String positionTemplate;
   private static int positionX;
   private static int positionY;
   private static int positionZ;
   @Nullable
   private static JsonUiHud.HotbarItems hotbarItems;
   @Nullable
   private static String daysText;
   @Nullable
   private static String daysTemplate;
   private static long daysShown;

   private JsonUiHud() {
   }

   public static void register() {
      HudElementRegistry.attachElementAfter(VanillaHudElements.TITLE_AND_SUBTITLE, ID, JsonUiHud::extract);

      for (Identifier replaced : REPLACED) {
         HudElementRegistry.replaceElement(replaced, original -> (graphics, delta) -> {
            if (!owns()) {
               original.extractRenderState(graphics, delta);
            }
         });
      }

      HudElementRegistry.replaceElement(VanillaHudElements.CHAT, original -> (graphics, delta) -> {
         if (!(Minecraft.getInstance().gui.screen() instanceof JsonUiChatScreen)) {
            if (!owns() || chatFocused()) {
               original.extractRenderState(graphics, delta);
            }
         }
      });
   }

   public static HudData data() {
      return DATA;
   }

   public static void reset() {
      DATA.reset();
      HEARTS.reset();
      instance = null;
      instanceDefinitions = null;
      instanceVariables = null;
      failed = false;
      clock = 0.0;
      lastFrame = 0L;
      lastHighlight = ItemStack.EMPTY;
      showCoordinates = false;
      showDaysPlayed = false;
   }

   @Nullable
   public static UiScreenInstance instance() {
      return instance;
   }

   public static boolean owns() {
      UiScreenInstance current = instance;
      return !failed && current != null && BedrockMovement.active() && instanceDefinitions == JsonUi.definitions() && JsonUi.use("hud", current);
   }

   public static boolean failed() {
      return failed;
   }

   public static boolean drawingLocator() {
      return drawingLocator;
   }

   public static void chat(Component content) {
      String line = LegacyText.of(content);
      lastChat = line;
      JsonUiChatScreen.received(line);
      if (JsonUi.mode() != JsonUi.Mode.OFF && !failed && BedrockMovement.active()) {
         DATA.chat(line);
      }
   }

   public static String lastChat() {
      return lastChat;
   }

   public static void forgetLastChat() {
      lastChat = "";
   }

   public static void clearChat() {
      DATA.clearChat();
      JsonUiChatScreen.cleared();
   }

   public static void onGameRule(String name, Object value) {
      if (value instanceof Boolean on) {
         if (name.equalsIgnoreCase("showcoordinates")) {
            showCoordinates = on;
         } else if (name.equalsIgnoreCase("showdaysplayed")) {
            showDaysPlayed = on;
         }
      }
   }

   private static boolean chatFocused() {
      return Minecraft.getInstance().gui.hud.getChat().isChatFocused();
   }

   private static Map<String, UiValue> variables(Options options) {
      double opacity = (Double)options.textBackgroundOpacity().get();
      double scale = (Double)options.chatScale().get();
      double spacing = (Double)options.chatLineSpacing().get() * 10.0;
      Map<String, UiValue> built = builtVariables;
      if (built == null || opacity != builtOpacity || scale != builtScale || spacing != builtSpacing) {
         built = HudData.engineVariables(opacity, scale, spacing);
         builtVariables = built;
         builtOpacity = opacity;
         builtScale = scale;
         builtSpacing = spacing;
      }

      return built;
   }

   private static void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
      long perf = PerfProbe.start();
      extractFrame(graphics, delta);
      PerfProbe.end(PerfProbe.Part.HUD, perf);
   }

   private static void extractFrame(GuiGraphicsExtractor graphics, DeltaTracker delta) {
      if (!failed && BedrockMovement.active()) {
         UiDefinitionSet definitions = JsonUi.definitions();
         if (definitions != null) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null) {
               try {
                  Map<String, UiValue> variables = variables(minecraft.options);
                  UiEnvironment environment = JsonUi.environment(definitions);
                  if (instance == null || instanceDefinitions != definitions || !variables.equals(instanceVariables)) {
                     instance = UiScreenInstance.create(
                        definitions, "hud.hud_screen", variables, DATA, environment, JAVA_OWNED, JsonUi.uiWidth(), JsonUi.uiHeight()
                     );
                     instanceDefinitions = definitions;
                     instanceVariables = variables;
                     instance.problems().log("hud");
                     diagnostics = new JsonUiDiagnostics("hud", "hud", 8000L);
                     instance.fireEvent("screen.entrance_push");
                     nextTrace = clock + 6.0;
                     tracesLeft = 2;
                  }

                  UiScreenInstance hud = instance;
                  if (JsonUi.use("hud", hud)) {
                     long now = System.nanoTime();
                     if (lastFrame != 0L && !minecraft.isPaused()) {
                        clock = clock + Math.min(0.25, (now - lastFrame) / 1.0E9);
                     }

                     lastFrame = now;
                     Screen screen = minecraft.gui.screen();
                     HudValues values = HudValuesReader.read(
                        minecraft, HEARTS, delta.getGameTimeDeltaPartialTick(true), screen != null && screen.showsActiveEffects()
                     );
                     DATA.player(values);
                     itemName(minecraft, values);
                     bubbleSound(minecraft, values);
                     DATA.hudTexts(showCoordinates ? position(minecraft) : null, showDaysPlayed ? daysPlayed(minecraft) : null);
                     hud.resize(JsonUi.uiWidth(), JsonUi.uiHeight());
                     DATA.deliver(hud);
                     hud.update(clock);
                     DATA.afterUpdate(hud);
                     if (TRACE && tracesLeft > 0 && clock >= nextTrace) {
                        tracesLeft--;
                        nextTrace = clock + 24.0;
                        List<String> lines = UiTreeDump.lines(hud, environment.textures(), 3000);
                        BedrockNative.LOGGER.info("JSON UI trace hud: {} visible controls", lines.size());
                        lines.forEach(line -> BedrockNative.LOGGER.info("JSON UI trace hud | {}", line));
                        if (tracesLeft == 1) {
                           for (String namespace : System.getProperty("bedrocknative.jsonUiDefs", "").split(",")) {
                              if (!namespace.isBlank()) {
                                 definitions.namespace(namespace.trim())
                                    .values()
                                    .forEach(
                                       def -> BedrockNative.LOGGER
                                          .info(
                                             "JSON UI def {} {}",
                                             def,
                                             def.props().toString().length() > 20000 ? def.props().toString().substring(0, 20000) + "..." : def.props()
                                          )
                                    );
                              }
                           }
                        }
                     }

                     BedrockFont font = JsonUi.font();
                     if (font != null) {
                        UiControl chat = chatFocused() ? hud.find("chat_panel") : null;
                        frameDelta = delta;
                        JsonUiHud.HotbarItems items = hotbarItems;
                        if (items == null || items.minecraft() != minecraft) {
                           hotbarItems = items = new JsonUiHud.HotbarItems(minecraft);
                        }

                        JsonUiCanvas.draw(
                           graphics,
                           hud.drawList(),
                           font,
                           items,
                           values,
                           environment.textures(),
                           chat == null ? control -> false : control -> inside(control, chat),
                           JsonUiHud::locator,
                           hud.gaps()::undrawn
                        );
                     }

                     JsonUiDiagnostics notes = diagnostics;
                     if (notes != null) {
                        notes.frame(hud, environment::textures);
                     }

                     return;
                  }

                  DATA.drainEvents();
               } catch (RuntimeException var19) {
                  failed = true;
                  BedrockNative.LOGGER.warn("JSON UI HUD failed; the Java HUD is back for this session", var19);
                  return;
               } finally {
                  frameDelta = null;
               }
            }
         }
      }
   }

   private static boolean inside(UiControl control, UiControl ancestor) {
      for (UiControl c = control; c != null; c = c.parent()) {
         if (c == ancestor) {
            return true;
         }
      }

      return false;
   }

   private static void itemName(Minecraft minecraft, HudValues values) {
      Player player = HudValuesReader.cameraPlayer(minecraft);
      ItemStack selected = player.getInventory().getSelectedItem();
      if (selected.isEmpty()) {
         if (!lastHighlight.isEmpty()) {
            DATA.clearItemText();
            lastHighlight = ItemStack.EMPTY;
         }
      } else if (lastHighlight.isEmpty() || !selected.is(lastHighlight.getItem()) || !selected.getHoverName().equals(lastHighlight.getHoverName())) {
         if (values.mode() != HudValues.Mode.SPECTATOR) {
            MutableComponent name = Component.empty().append(selected.getHoverName()).withStyle(selected.getRarity().color());
            if (selected.has(DataComponents.CUSTOM_NAME)) {
               name.withStyle(ChatFormatting.ITALIC);
            }

            DATA.itemText(LegacyText.of(name));
         }

         lastHighlight = selected.copy();
      }
   }

   private static void bubbleSound(Minecraft minecraft, HudValues values) {
      if (values.mode().survivalUi() && values.airShown() && !values.hides(HudValues.Part.AIR)) {
         HudStateAccessor hud = (HudStateAccessor)minecraft.gui.hud;
         if (!values.underwater()) {
            hud.bedrocknative$setLastBubblePopSoundPlayed(0);
         }

         int bubble = HudRenderers.burstingBubble(values);
         if (bubble > 0) {
            hud.bedrocknative$playAirBubblePoppedSound(bubble, HudValuesReader.cameraPlayer(minecraft), HudRenderers.emptyBubbles(values));
         }
      }
   }

   private static String position(Minecraft minecraft) {
      Player player = HudValuesReader.cameraPlayer(minecraft);
      int x = player.getBlockX();
      int y = player.getBlockY();
      int z = player.getBlockZ();
      String template = BedrockText.lookupKey("map.position");
      String text = positionText;
      if (text == null || x != positionX || y != positionY || z != positionZ || !template.equals(positionTemplate)) {
         text = format(template, x, y, z);
         positionText = text;
         positionTemplate = template;
         positionX = x;
         positionY = y;
         positionZ = z;
      }

      return text;
   }

   private static String daysPlayed(Minecraft minecraft) {
      long days = minecraft.level == null ? 0L : minecraft.level.getOverworldClockTime() / 24000L;
      String template = BedrockText.lookupKey("hudScreen.daysPlayed");
      String text = daysText;
      if (text == null || days != daysShown || !template.equals(daysTemplate)) {
         text = format(template, days);
         daysText = text;
         daysTemplate = template;
         daysShown = days;
      }

      return text;
   }

   private static String format(String template, Object... values) {
      try {
         return String.format(Locale.ROOT, template, values);
      } catch (IllegalFormatException var3) {
         return template;
      }
   }

   private static void locator(GuiGraphicsExtractor graphics, float x, float y) {
      Minecraft minecraft = Minecraft.getInstance();
      DeltaTracker delta = frameDelta;
      ContextualBar bar = (ContextualBar)((HudStateAccessor)minecraft.gui.hud).bedrocknative$contextualInfoBar().getSecond();
      if (bar instanceof LocatorBar && delta != null) {
         graphics.pose().pushMatrix();
         drawingLocator = true;

         try {
            graphics.pose().translate(x - (graphics.guiWidth() - 182) / 2, y - (graphics.guiHeight() - 29));
            bar.extractBackground(graphics, delta);
            bar.extractRenderState(graphics, delta);
         } finally {
            drawingLocator = false;
            graphics.pose().popMatrix();
         }
      }
   }

   private record HotbarItems(Minecraft minecraft) implements ItemSource {
      @Override
      public ItemStack stack(UiControl renderer) {
         int slot = HudRenderers.slot(renderer);
         return slot < 0 ? ItemStack.EMPTY : HudValuesReader.cameraPlayer(this.minecraft).getInventory().getItem(slot);
      }

      @Override
      public int seed(UiControl renderer) {
         return HudRenderers.slot(renderer) + 1;
      }
   }
}
