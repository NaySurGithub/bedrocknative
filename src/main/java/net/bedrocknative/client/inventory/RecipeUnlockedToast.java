package net.bedrocknative.client.inventory;

import java.util.Map;
import java.util.Optional;
import net.bedrocknative.client.content.BedrockTextures;
import net.bedrocknative.text.BedrockText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.gui.components.toasts.Toast.Visibility;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

final class RecipeUnlockedToast implements Toast {
   private static final long DISPLAY_MS = 5000L;
   private static final String BACKGROUND = "textures/ui/greyBorder";
   private static final int TITLE_COLOR = -1;
   private static final int SECOND_LINE_COLOR = -3881788;
   private static final int FALLBACK_BACKGROUND = -533712848;
   private static final int WHITE = -1;
   private static final int PADDING = 9;
   private static final int ICON = 18;
   private static final int GAP = 6;
   private static final int MAX_TEXT = 200;
   private static final Map<String, String> DESCRIPTIONS = Map.of(
      "crafting_table",
      "recipeToast.newUnlock.descriptionCraftingTable",
      "stonecutter",
      "recipeToast.newUnlock.descriptionStonecutterTable",
      "smithing_table",
      "recipeToast.newUnlock.descriptionSmithingTable",
      "cartography_table",
      "recipeToast.newUnlock.descriptionCartographyTable",
      "furnace",
      "recipeToast.newUnlock.descriptionFurnace",
      "blast_furnace",
      "recipeToast.newUnlock.descriptionBlastFurnace",
      "smoker",
      "recipeToast.newUnlock.descriptionSmoker"
   );
   private final ItemStack icon;
   private final Component title;
   private final @Nullable Component secondLine;
   private final int width;
   private Visibility visibility = Visibility.SHOW;

   private RecipeUnlockedToast(ItemStack icon, Component title, @Nullable Component secondLine, Font font) {
      this.icon = icon;
      this.title = title;
      this.secondLine = secondLine;
      int text = Math.max(font.width(title), secondLine == null ? 0 : font.width(secondLine));
      this.width = 33 + Math.min(200, text) + 9;
   }

   static @Nullable String description(String tag) {
      return DESCRIPTIONS.get(tag);
   }

   static void show(Minecraft minecraft, ItemStack result, String tag, boolean needsTable, int more) {
      Component title = BedrockText.ui("recipeToast.newUnlock.title");
      Component second;
      if (more > 0) {
         second = Component.literal("+" + more).append(BedrockText.ui("recipeToast.newUnlock.moreRecipes"));
      } else {
         String key = "crafting_table".equals(tag) && !needsTable ? null : description(tag);
         second = key == null ? null : BedrockText.ui(key);
      }

      minecraft.gui.toastManager().addToast(new RecipeUnlockedToast(result, title, second, minecraft.font));
   }

   @Override
   public Visibility getWantedVisibility() {
      return this.visibility;
   }

   @Override
   public void update(ToastManager manager, long fullyVisibleForMs) {
      this.visibility = fullyVisibleForMs >= 5000.0 * manager.getNotificationDisplayTimeMultiplier() ? Visibility.HIDE : Visibility.SHOW;
   }

   @Override
   public int width() {
      return this.width;
   }

   @Override
   public void extractRenderState(GuiGraphicsExtractor graphics, Font font, long fullyVisibleForMs) {
      background(graphics, this.width(), this.height());
      graphics.fakeItem(this.icon, 10, (this.height() - 16) / 2);
      int x = 33;
      if (this.secondLine == null) {
         graphics.text(font, this.title, x, (this.height() - 9) / 2, -1, false);
      } else {
         graphics.text(font, this.title, x, 7, -1, false);
         graphics.text(font, this.secondLine, x, 18, -3881788, false);
      }
   }

   private static void background(GuiGraphicsExtractor graphics, int width, int height) {
      Optional<BedrockTextures.NineSlice> found = BedrockTextures.nineSlice("textures/ui/greyBorder");
      if (found.isEmpty()) {
         graphics.fill(0, 0, width, height, -533712848);
      } else {
         BedrockTextures.NineSlice slice = found.get();
         BedrockTextures.Texture texture = slice.texture();
         int tw = texture.width();
         int th = texture.height();
         int left = Math.min(slice.left(), width / 2);
         int right = Math.min(slice.right(), width - left);
         int top = Math.min(slice.top(), height / 2);
         int bottom = Math.min(slice.bottom(), height - top);
         int[] xs = new int[]{0, left, width - right, width};
         int[] ys = new int[]{0, top, height - bottom, height};
         int[] us = new int[]{0, slice.left(), tw - slice.right(), tw};
         int[] vs = new int[]{0, slice.top(), th - slice.bottom(), th};

         for (int column = 0; column < 3; column++) {
            for (int row = 0; row < 3; row++) {
               int w = xs[column + 1] - xs[column];
               int h = ys[row + 1] - ys[row];
               int sourceWidth = us[column + 1] - us[column];
               int sourceHeight = vs[row + 1] - vs[row];
               if (w > 0 && h > 0 && sourceWidth > 0 && sourceHeight > 0) {
                  graphics.blit(
                     RenderPipelines.GUI_TEXTURED, texture.id(), xs[column], ys[row], us[column], vs[row], w, h, sourceWidth, sourceHeight, tw, th, -1
                  );
               }
            }
         }
      }
   }
}
